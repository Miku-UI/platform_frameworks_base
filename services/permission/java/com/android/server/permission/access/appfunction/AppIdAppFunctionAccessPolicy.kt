/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.permission.access.appfunction

import android.Manifest.permission.EXECUTE_APP_FUNCTIONS
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_ALL
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE
import android.app.appfunctions.AppFunctionService
import android.content.pm.SignedPackage
import android.os.UserHandle
import android.util.Slog
import com.android.modules.utils.BinaryXmlPullParser
import com.android.modules.utils.BinaryXmlSerializer
import com.android.server.permission.access.AccessState
import com.android.server.permission.access.AppFunctionAccessUri
import com.android.server.permission.access.GetStateScope
import com.android.server.permission.access.MutableAccessState
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.SchemePolicy
import com.android.server.permission.access.UidUri
import com.android.server.permission.access.WriteMode
import com.android.server.permission.access.collection.*
import com.android.server.permission.access.immutable.*
import com.android.server.permission.access.util.andInv
import com.android.server.permission.access.util.hasAnyBit
import com.android.server.permission.access.util.hasBits
import com.android.server.pm.pkg.PackageState

class AppIdAppFunctionAccessPolicy : SchemePolicy() {
    override val subjectScheme: String
        get() = UidUri.SCHEME

    override val objectScheme: String
        get() = AppFunctionAccessUri.SCHEME

    private val persistence = AppIdAppFunctionAccessPersistence()

    private val upgrade = AppIdAppFunctionAccessUpgrade(this)

    fun GetStateScope.getAccessRequestState(
        agentAppId: Int,
        agentUserId: Int,
        targetAppId: Int,
        targetUserId: Int,
    ): Int {
        if (
            !anyInstalledPackageInUid(agentAppId, agentUserId) { isValidAgent(it, agentUserId) } ||
                !anyInstalledPackageInUid(targetAppId, targetUserId) {
                    isValidTarget(it, targetUserId)
                }
        ) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE
        }

        val flags = getAccessFlags(agentAppId, agentUserId, targetAppId, targetUserId)
        return if (isAccessGranted(flags)) {
            ACCESS_REQUEST_STATE_GRANTED
        } else {
            ACCESS_REQUEST_STATE_DENIED
        }
    }

    fun GetStateScope.getAccessFlags(
        agentAppId: Int,
        agentUserId: Int,
        targetAppId: Int,
        targetUserId: Int,
    ): Int {
        val targetUid = UserHandle.getUid(targetUserId, targetAppId)
        return state.userStates[agentUserId]
            ?.appIdAppFunctionAccessFlags[agentAppId]
            ?.get(targetUid) ?: 0
    }

    fun MutateStateScope.updateAccessFlags(
        agentAppId: Int,
        agentUserId: Int,
        targetAppId: Int,
        targetUserId: Int,
        flagMask: Int,
        flags: Int,
    ): Boolean {
        validateFlags(flags, flagMask)
        val targetUid = UserHandle.getUid(targetUserId, targetAppId)
        if (agentUserId !in newState.userStates || targetUserId !in newState.userStates) {
            // Despite that we check UserManagerInternal.exists() in PermissionService, we may still
            // sometimes get race conditions between that check and the actual mutateState() call.
            // This should rarely happen but at least we should not crash.
            Slog.e(LOG_TAG, "Unable to update permission flags for missing user $agentUserId")
            return false
        }

        if (
            !anyInstalledPackageInUid(agentAppId, agentUserId) { isValidAgent(it, agentUserId) } ||
                !anyInstalledPackageInUid(targetAppId, targetUserId) {
                    isValidTarget(it, targetUserId)
                }
        ) {
            return false
        }

        val existingAgentState =
            newState.userStates[agentUserId]?.appIdAppFunctionAccessFlags[agentAppId]
        val oldFlags = existingAgentState?.get(targetUid) ?: 0
        val newFlags = (oldFlags andInv flagMask) or (flags and flagMask)
        if (oldFlags == newFlags) {
            return false
        }
        val appIdAppFunctionAccessFlags =
            newState.mutateUserState(agentUserId)!!.mutateAppIdAppFunctionAccessFlags()
        val flags = appIdAppFunctionAccessFlags.mutateOrPut(agentAppId) { MutableIntIntMap() }
        flags.putWithDefault(targetUid, newFlags, 0)
        return true
    }

    private fun validateFlags(flags: Int, flagMask: Int) {
        require(!flags.hasAnyBit(ACCESS_FLAG_MASK_ALL.inv())) {
            "Invalid flag(s) ${flags andInv ACCESS_FLAG_MASK_ALL} specified in call to " +
                "updateAppFunctionAccessFlags"
        }
        require((flags and flagMask) == flags) {
            "Flags ${flags and flagMask} specified in flags $flags for " +
                "updateAppFunctionAccessFlags, but is not included in the mask $flagMask"
        }
        validateOpposingFlagPair(flags, flagMask, ACCESS_FLAG_MASK_USER, "ACCESS_FLAGS_USER")
        validateOpposingFlagPair(flags, flagMask, ACCESS_FLAG_MASK_OTHER, "ACCESS_FLAGS_OTHER")
    }

    // If setting a granted/denied flag, then the flags must contain only one of the pair, not both,
    // and the mask must contain both
    private fun validateOpposingFlagPair(
        flags: Int,
        flagMask: Int,
        opposingFlagPairMask: Int,
        flagPrefix: String,
    ) {
        require(!flags.hasBits(opposingFlagPairMask)) {
            "Cannot set both ${flagPrefix}_GRANTED and ${flagPrefix}_DENIED together"
        }

        require(flagMask.hasBits(opposingFlagPairMask) || !flags.hasAnyBit(opposingFlagPairMask)) {
            "When setting ${flagPrefix}_GRANTED/DENIED, the opposing flag must also be in the mask"
        }
    }

    fun GetStateScope.getAgents(userId: Int): List<String> = buildList {
        state.externalState.packageStates.forEach { packageName, packageState ->
            if (packageState.isInstalledForUser(userId) && isValidAgent(packageState, userId)) {
                this += packageName
            }
        }
    }

    fun GetStateScope.getTargets(userId: Int): List<String> = buildList {
        state.externalState.packageStates.forEach { packageName, packageState ->
            if (isValidTarget(packageState, userId)) {
                this += packageName
            }
        }
    }

    private fun GetStateScope.anyInstalledPackageInUid(
        appId: Int,
        userId: Int,
        predicate: (PackageState) -> Boolean,
    ): Boolean {
        val packageNames = state.externalState.appIdPackageNames[appId] ?: return false
        return packageNames.anyIndexed { _, packageName ->
            val packageStates =
                state.externalState.packageStates[packageName] ?: return@anyIndexed false
            if (!packageStates.isInstalledForUser(userId)) {
                return@anyIndexed false
            }
            return@anyIndexed predicate(packageStates)
        }
    }

    private fun GetStateScope.isValidAgent(packageState: PackageState, userId: Int): Boolean {
        if (!packageState.isInstalledForUser(userId)) {
            return false
        }
        if (
            packageState.androidPackage?.requestedPermissions?.contains(EXECUTE_APP_FUNCTIONS) !=
                true
        ) {
            return false
        }

        if (state.externalState.agentAllowlist == null) {
            return true
        }

        return state.externalState.agentAllowlist?.any { isAllowlistedAgent(it, packageState) } ==
            true
    }

    fun isAllowlistedAgent(allowedAgent: SignedPackage, possibleAgent: PackageState): Boolean {
        if (allowedAgent.packageName != possibleAgent.packageName) {
            return false
        }

        return allowedAgent.certificateDigestOrNull == null ||
            possibleAgent.androidPackage
                ?.signingDetails
                ?.hasSha256Certificate(allowedAgent.certificateDigest) == true
    }

    private fun isValidTarget(packageState: PackageState, userId: Int): Boolean {
        if (!packageState.isInstalledForUser(userId)) {
            return false
        }

        return packageState.androidPackage?.services?.anyIndexed { _, service ->
            service.intents.anyIndexed { _, intent ->
                intent.intentFilter.hasAction(AppFunctionService.SERVICE_INTERFACE) &&
                    intent.intentFilter.countDataSchemes() == 0 &&
                    intent.intentFilter.countDataTypes() == 0
            }
        } == true
    }

    private fun PackageState.isInstalledForUser(userId: Int) =
        getUserStateOrDefault(userId).isInstalled

    override fun MutateStateScope.onAgentAllowlistChanged(agentAllowlist: Set<SignedPackage>?) {
        if (agentAllowlist == null) {
            // if the allowlist is null, then it isn't enforced, don't clean up state
            return
        }

        val allowlistedAppIds = MutableIntSet()
        agentAllowlist.forEachIndexed { _, signedPackage ->
            allowlistedAppIds.add(
                newState.externalState.packageStates[signedPackage.packageName]?.appId
                    ?: return@forEachIndexed
            )
        }
        newState.userStates.forEachIndexed { userIndex, user, _ ->
            val appIdAppFunctionAccessFlags =
                newState.mutateUserStateAt(userIndex).mutateAppIdAppFunctionAccessFlags()
            appIdAppFunctionAccessFlags.forEachReversedIndexed { appIdIndex, appId, _ ->
                if (appId !in allowlistedAppIds) {
                    appIdAppFunctionAccessFlags.removeAt(appIdIndex)
                }
            }
        }
    }

    override fun MutateStateScope.onUserRemoved(userId: Int) {
        newState.userStates.forEachReversedIndexed { userIndex, user, _ ->
            if (user == userId) {
                // This will be removed anyway
                return@forEachReversedIndexed
            }
            val userState = newState.mutateUserStateAt(userIndex, WriteMode.NONE)
            val appIdAppFunctionAccessFlags = userState.mutateAppIdAppFunctionAccessFlags()
            newState.externalState.packageStates.forEach { _, packageState ->
                val appFunctionAccessFlags =
                    appIdAppFunctionAccessFlags.mutate(packageState.appId) ?: return@forEach
                appFunctionAccessFlags.forEachReversedIndexed { targetUidIndex, targetUid, _ ->
                    if (UserHandle.getUserId(targetUid) == userId) {
                        appFunctionAccessFlags.removeAt(targetUidIndex)
                        userState.requestWriteMode(WriteMode.ASYNCHRONOUS)
                    }
                }
            }
        }
    }

    override fun MutateStateScope.onAppIdRemoved(appId: Int) {
        newState.userStates.forEachReversedIndexed { userIndex, user, _ ->
            val appIdAppFunctionAccessFlags =
                newState.mutateUserStateAt(userIndex).mutateAppIdAppFunctionAccessFlags()
            appIdAppFunctionAccessFlags.remove(appId)
            newState.externalState.packageStates.forEach { _, packageState ->
                val appFunctionAccessFlags =
                    appIdAppFunctionAccessFlags.mutate(packageState.appId) ?: return@forEach
                appFunctionAccessFlags.forEachReversedIndexed { targetUidIndex, targetUid, _ ->
                    if (UserHandle.getAppId(targetUid) == appId) {
                        appFunctionAccessFlags.removeAt(targetUidIndex)
                    }
                }
            }
        }
    }

    override fun MutateStateScope.onPackageUninstalled(
        packageName: String,
        appId: Int,
        userId: Int,
    ) {
        if (userId !in newState.userStates) {
            return
        }
        val isValidAgentUid = anyInstalledPackageInUid(appId, userId) { isValidAgent(it, userId) }
        if (!isValidAgentUid) {
            if (appId in newState.userStates[userId]!!.appIdAppFunctionAccessFlags) {
                newState.mutateUserState(userId)!!.mutateAppIdAppFunctionAccessFlags() -= appId
            }
        }
        val isValidTargetUid = anyInstalledPackageInUid(appId, userId) { isValidTarget(it, userId) }
        if (!isValidTargetUid) {
            val uid = UserHandle.getUid(userId, appId)
            newState.userStates.forEachIndexed { userIndex, _, userState ->
                userState.appIdAppFunctionAccessFlags.forEachIndexed {
                    appIdIndex,
                    appId,
                    accessFlags ->
                    if (uid in accessFlags) {
                        newState
                            .mutateUserStateAt(userIndex)
                            .mutateAppIdAppFunctionAccessFlags()
                            .mutateAt(appIdIndex) -= uid
                    }
                }
            }
        }
    }

    override fun MutateStateScope.upgradePackageState(
        packageState: PackageState,
        userId: Int,
        version: Int,
    ) {
        with(upgrade) { upgradePackageState(packageState, userId, version) }
    }

    override fun BinaryXmlPullParser.parseUserState(state: MutableAccessState, userId: Int) {
        with(persistence) { this@parseUserState.parseUserState(state, userId) }
    }

    override fun BinaryXmlSerializer.serializeUserState(state: AccessState, userId: Int) {
        with(persistence) { this@serializeUserState.serializeUserState(state, userId) }
    }

    companion object {
        private val LOG_TAG = AppIdAppFunctionAccessPolicy::class.java.simpleName

        // Grant logic ordering goes as follows: USER flags override OTHER flags.
        // If no other DENIED flags are applied, PREGRANTED flag means granted.
        fun isAccessGranted(flags: Int): Boolean {
            if (flags.hasAnyBit(ACCESS_FLAG_MASK_USER)) {
                return flags.hasBits(ACCESS_FLAG_USER_GRANTED)
            }
            if (flags.hasAnyBit(ACCESS_FLAG_MASK_OTHER)) {
                return flags.hasBits(ACCESS_FLAG_OTHER_GRANTED)
            }
            return flags.hasBits(ACCESS_FLAG_PREGRANTED)
        }
    }
}
