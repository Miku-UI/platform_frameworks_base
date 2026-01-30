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

import android.Manifest
import android.app.appfunctions.AppFunctionAccessServiceInterface
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE
import android.content.pm.SignedPackage
import android.os.Binder
import android.os.UserHandle
import android.permission.flags.Flags.appFunctionAccessServiceEnabled
import android.util.Slog
import com.android.server.LocalManagerRegistry
import com.android.server.LocalServices
import com.android.server.permission.access.AccessCheckingService
import com.android.server.permission.access.AppFunctionAccessUri
import com.android.server.permission.access.UidUri
import com.android.server.permission.access.collection.*
import com.android.server.permission.access.util.PermissionEnforcer
import com.android.server.pm.PackageManagerLocal
import com.android.server.pm.UserManagerInternal
import com.android.server.pm.pkg.PackageState

class AppFunctionAccessService(private val service: AccessCheckingService) :
    AppFunctionAccessServiceInterface {
    private val policy =
        service.getSchemePolicy(UidUri.SCHEME, AppFunctionAccessUri.SCHEME)
            as AppIdAppFunctionAccessPolicy
    private val pregrants = AppFunctionAccessPregrant(this)

    private val context = service.context
    private lateinit var packageManagerLocal: PackageManagerLocal
    private lateinit var userManagerInternal: UserManagerInternal
    private lateinit var permissionEnforcer: PermissionEnforcer

    fun initialize() {
        packageManagerLocal =
            LocalManagerRegistry.getManagerOrThrow(PackageManagerLocal::class.java)
        userManagerInternal = LocalServices.getService(UserManagerInternal::class.java)
        permissionEnforcer = PermissionEnforcer(context)
    }

    override fun getAccessRequestState(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
    ): Int {
        if (!appFunctionAccessServiceEnabled()) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE
        }

        val methodName = "getAppFunctionAccessRequestState"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE
        }

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName)
                ?: return ACCESS_REQUEST_STATE_UNREQUESTABLE

        if (agentPackageState.appId != UserHandle.getAppId(Binder.getCallingUid())) {
            permissionEnforcer.enforceCallingOrSelfAnyPermission(
                methodName,
                Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
            )
        }
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName)
                ?: return ACCESS_REQUEST_STATE_UNREQUESTABLE

        return service.getState {
            with(policy) {
                getAccessRequestState(
                    agentPackageState.appId,
                    agentUserId,
                    targetPackageState.appId,
                    targetUserId,
                )
            }
        }
    }

    override fun getAccessFlags(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
    ): Int {
        if (!appFunctionAccessServiceEnabled()) {
            return 0
        }

        val methodName = "getAppFunctionAccessFlags"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return 0
        }

        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName) ?: return 0
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName) ?: return 0

        return service.getState {
            with(policy) {
                getAccessFlags(
                    agentPackageState.appId,
                    agentUserId,
                    targetPackageState.appId,
                    targetUserId,
                )
            }
        }
    }

    override fun setAgentAllowlist(agentAllowlist: Set<SignedPackage>?) {
        service.onAgentAllowlistChanged(agentAllowlist)
    }

    override fun updateAccessFlags(
        agentPackageName: String,
        agentUserId: Int,
        targetPackageName: String,
        targetUserId: Int,
        flagMask: Int,
        flags: Int,
    ): Boolean {
        if (!appFunctionAccessServiceEnabled()) {
            return false
        }

        val methodName = "updateAppFunctionAccessFlags"
        enforceCallingOrSelfCrossUserPermission(methodName, agentUserId, targetUserId)
        if (!allUsersExist(methodName, agentUserId, targetUserId)) {
            return false
        }

        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        val agentPackageState =
            getFilteredPackageState(agentPackageName, agentUserId, methodName) ?: return false
        val targetPackageState =
            getFilteredPackageState(targetPackageName, targetUserId, methodName) ?: return false
        var changed = true
        service.mutateState {
            with(policy) {
                changed =
                    updateAccessFlags(
                        agentPackageState.appId,
                        agentUserId,
                        targetPackageState.appId,
                        targetUserId,
                        flagMask,
                        flags,
                    )
            }
        }
        return changed
    }

    override fun revokeSelfAccess(targetPackageName: String) {
        val methodName = "revokeSelfAppFunctionAccess"
        val userId = UserHandle.getUserId(Binder.getCallingUid())
        val targetPackageState =
            getFilteredPackageState(targetPackageName, userId, methodName) ?: return
        val agentAppId = UserHandle.getAppId(Binder.getCallingUid())

        service.getState {
            with(policy) {
                if (getAccessFlags(agentAppId, userId, targetPackageState.appId, userId) == 0) {
                    // We have no state, so don't set any
                    return
                }
            }
        }

        service.mutateState {
            with(policy) {
                // Set the OTHER_DENIED flag, so that, if this package had the PREGRANTED flag set,
                // This flag will override, and the PREGRANT flag won't be re-added on next
                // fingerprint change.
                updateAccessFlags(
                    agentAppId,
                    userId,
                    targetPackageState.appId,
                    userId,
                    ACCESS_FLAG_MASK_USER or ACCESS_FLAG_MASK_OTHER,
                    ACCESS_FLAG_OTHER_DENIED,
                )
            }
        }
    }

    override fun getValidAgents(userId: Int): List<String> {
        val methodName = "getValidAgents"
        if (!userExists(methodName, userId)) {
            return emptyList()
        }
        enforceCallingOrSelfCrossUserPermission(methodName, userId)
        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )

        return service.getState { with(policy) { filterPackageNames(getAgents(userId), userId) } }
    }

    override fun getValidTargets(targetUserId: Int): List<String> {
        val methodName = "getValidTargets"
        if (!userExists(methodName, targetUserId)) {
            return emptyList()
        }
        enforceCallingOrSelfCrossUserPermission(methodName, targetUserId)
        permissionEnforcer.enforceCallingOrSelfAnyPermission(
            methodName,
            Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
        )
        return service.getState {
            with(policy) { filterPackageNames(getTargets(targetUserId), targetUserId) }
        }
    }

    override fun onUserStarting(userId: Int) {
        if (!appFunctionAccessServiceEnabled()) {
            return
        }
        service.mutateState { with(pregrants) { applyIfNeeded(userId) } }
    }

    private fun enforceCallingOrSelfCrossUserPermission(
        message: String?,
        agentUserId: Int,
        targetUserId: Int,
    ) {
        enforceCallingOrSelfCrossUserPermission(message, agentUserId)
        enforceCallingOrSelfCrossUserPermission(message, targetUserId)
    }

    private fun enforceCallingOrSelfCrossUserPermission(message: String?, userId: Int) {
        permissionEnforcer.enforceCallingOrSelfCrossUserPermission(
            userId,
            enforceFullPermission = true,
            enforceShellRestriction = true,
            message,
        )
    }

    private fun allUsersExist(methodName: String, userId1: Int, userId2: Int): Boolean =
        userExists(methodName, userId1) && userExists(methodName, userId2)

    private fun userExists(methodName: String, userId: Int): Boolean {
        val exists = userManagerInternal.exists(userId)
        if (!exists) {
            Slog.w(LOG_TAG, "$methodName: Unknown user $userId")
        }
        return exists
    }

    private fun getFilteredPackageState(
        packageName: String,
        userId: Int,
        methodName: String,
    ): PackageState? =
        packageManagerLocal.withFilteredSnapshot().use { snapshot ->
            filterPackageState(snapshot, packageName, userId, methodName)
        }

    private fun filterPackageNames(packageNames: List<String>, userId: Int): List<String> =
        packageManagerLocal.withFilteredSnapshot().use { snapshot ->
            buildList {
                packageNames.forEachIndexed { _, packageName ->
                    this +=
                        filterPackageState(snapshot, packageName, userId)?.packageName
                            ?: return@forEachIndexed
                }
            }
        }

    private fun filterPackageState(
        snapshot: PackageManagerLocal.FilteredSnapshot,
        packageName: String,
        userId: Int,
        methodName: String? = null,
    ): PackageState? {
        val state = snapshot.getPackageState(packageName)
        if (state == null || !state.getUserStateOrDefault(userId).isInstalled) {
            if (methodName != null) {
                Slog.w(LOG_TAG, "$methodName: Unknown package $packageName")
            }
            return null
        }
        return state
    }

    companion object {
        private val LOG_TAG = AppFunctionAccessService::class.java.simpleName
    }
}
