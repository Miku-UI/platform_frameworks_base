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

package com.android.server.permission.test

import android.Manifest.permission.EXECUTE_APP_FUNCTIONS
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_ALL
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.app.appfunctions.AppFunctionService
import android.content.IntentFilter
import android.content.pm.SignedPackage
import android.content.pm.SigningDetails
import android.os.UserHandle
import android.util.ArrayMap
import com.android.internal.pm.pkg.component.ParsedIntentInfo
import com.android.internal.pm.pkg.component.ParsedService
import com.android.server.permission.access.GetStateScope
import com.android.server.permission.access.MutableAccessState
import com.android.server.permission.access.MutableUserState
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.appfunction.AppIdAppFunctionAccessPolicy
import com.android.server.permission.access.immutable.*
import com.android.server.pm.pkg.AndroidPackage
import com.android.server.pm.pkg.PackageState
import com.android.server.pm.pkg.PackageUserState
import com.android.server.testutils.mock
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq

class AppIdAppFunctionAccessPolicyTest {

    private lateinit var oldState: MutableAccessState
    private lateinit var newState: MutableAccessState
    private val appIdAppFunctionPolicy = AppIdAppFunctionAccessPolicy()

    private fun mockPackageState(
        appId: Int,
        packageName: String,
        installedUsers: List<Int> = listOf(USER_ID_0),
        isAgent: Boolean = false,
        isTarget: Boolean = false,
        cert: ByteArray? = null,
    ): PackageState = mock {
        whenever(this.appId).thenReturn(appId)
        whenever(this.packageName).thenReturn(packageName)
        whenever(this.androidPackage).thenReturn(null)
        val installedState: PackageUserState =
            mock { whenever(this.isInstalled).thenReturn(true) }
        val uninstalledState: PackageUserState =
            mock { whenever(this.isInstalled).thenReturn(false) }

        whenever(this.getUserStateOrDefault(Mockito.intThat { installedUsers.contains(it) }))
            .thenReturn(installedState)
        whenever(this.getUserStateOrDefault(Mockito.intThat { !installedUsers.contains(it) }))
            .thenReturn(uninstalledState)

        val androidPackage = if (isAgent || isTarget || cert != null) {
            Mockito.mock(AndroidPackage::class.java)
        } else {
            null
        }

        if (isAgent) {
            whenever(androidPackage!!.requestedPermissions)
                .thenReturn(setOf(EXECUTE_APP_FUNCTIONS))
        }
        if (isTarget) {
            val intentFilter = IntentFilter().apply {
                addAction(AppFunctionService.SERVICE_INTERFACE)
            }
            val intent: ParsedIntentInfo = mock {
                whenever(this.intentFilter).thenReturn(intentFilter)
            }
            val service: ParsedService = mock { whenever(this.intents).thenReturn(listOf(intent)) }
            whenever(androidPackage!!.services).thenReturn(listOf(service))
        }
        if (cert != null) {
            val signingDetails: SigningDetails = mock {
                whenever(this.hasSha256Certificate(eq(cert))).thenReturn(true)
            }
            whenever(androidPackage!!.signingDetails).thenReturn(signingDetails)
        }
        whenever(this.androidPackage).thenReturn(androidPackage)
    }

    @Before
    fun setUp() {
        oldState = MutableAccessState()
        createUserState(USER_ID_0)
        createUserState(USER_ID_1)
        oldState.mutateExternalState().setPackageStates(ArrayMap())
        oldState.mutateExternalState().setAgentAllowlist(setOf(
            SignedPackage(AGENT_PKG_NAME, null),
            SignedPackage(OTHER_AGENT_PKG_NAME, null)
        ))
    }

    private fun createUserState(userId: Int) {
        oldState.mutateExternalState().mutateUserIds().add(userId)
        oldState.mutateUserStatesNoWrite().put(userId, MutableUserState())
    }

    private fun getAppFunctionFlags(
        agentAppId: Int,
        userId: Int,
        targetUid: Int,
        state: MutableAccessState = newState,
    ): Int =
        state.userStates[userId]?.appIdAppFunctionAccessFlags?.get(agentAppId)
            .getWithDefault(targetUid, 0)

    private fun setAccessFlags(
        agentAppId: Int,
        userId: Int,
        targetUid: Int,
        flags: Int,
        state: MutableAccessState = oldState,
    ) =
        state
            .mutateUserState(userId)!!
            .mutateAppIdAppFunctionAccessFlags()
            .mutateOrPut(agentAppId) { MutableIntIntMap() }
            .put(targetUid, flags)

    private fun addPackageState(
        packageState: PackageState,
        state: MutableAccessState = oldState,
    ) {
        state.mutateExternalState().apply {
            setPackageStates(
                packageStates.toMutableMap().apply { put(packageState.packageName, packageState) }
            )
            mutateAppIdPackageNames()
                .mutateOrPut(packageState.appId) { MutableIndexedListSet() }
                .add(packageState.packageName)
        }
    }

    private inline fun mutateState(action: MutateStateScope.() -> Unit) {
        newState = oldState.toMutable()
        MutateStateScope(oldState, newState).action()
    }

    @Test
    fun testOnPackageInstalled_defaultToNoFlags() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        assertWithMessage("Flags should default to 0")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, oldState))
            .isEqualTo(0)
    }

    @Test
    fun testGetAccessFlags() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        val flags: Int
        GetStateScope(oldState).apply {
            with(appIdAppFunctionPolicy) {
                flags = getAccessFlags(
                    AGENT_APP_ID,
                    USER_ID_0,
                    TARGET_APP_ID,
                    USER_ID_0,
                )
            }
        }
        assertWithMessage("Flags should be $ACCESS_FLAG_USER_GRANTED when set").that(flags)
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testUpdateAccessFlags_invalidFlag_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        NONEXISTENT_FLAGS, NONEXISTENT_FLAGS
                    )
                    assertWithMessage("Specifying an invalid flag should result in an exception")
                        .fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAccessFlags_provideFlagNotInMask_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0, 0,
                        ACCESS_FLAG_PREGRANTED
                    )
                    assertWithMessage("If a flag is specified without being in the flag mask, an " +
                            "exception should be thrown").fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAccessFlags_setOpposingFlags_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_MASK_USER, ACCESS_FLAG_MASK_USER
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when USER flags are set together"
                    ).fail()
                } catch (_: IllegalArgumentException) { }

                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_MASK_OTHER, ACCESS_FLAG_MASK_OTHER
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when OTHER flags are set together"
                    ).fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAccessFlags_setWithoutClearingOpposing_throwsException() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)

        mutateState {
            with(appIdAppFunctionPolicy) {
                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_USER_DENIED, ACCESS_FLAG_USER_DENIED
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when the USER_DENIED flag is set " +
                                " without clearing the GRANTED flag"
                    ).fail()
                } catch (_: IllegalArgumentException) { }

                try {
                    updateAccessFlags(
                        AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                        ACCESS_FLAG_OTHER_DENIED, ACCESS_FLAG_OTHER_DENIED
                    )
                    assertWithMessage(
                        "Expected an exception to be thrown when the OTHER_DENIED flag is set " +
                                "without clearing the GRANTED flag"
                    ).fail()
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testUpdateAccessFlags_clearSingleOpposing_doesntThrow() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)

        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_USER_GRANTED, 0)
            }
        }
    }

    @Test
    fun testUpdateAccessFlags_agentNotInstalled() {
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Flags should not be set for non-installed agent")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_targetNotInstalled() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        addPackageState(agent)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Flags should not be set for non-installed target")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_invalidUser() {
        oldState.mutateUserStatesNoWrite().remove(USER_ID_1)
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Invalid users should not have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_invalidTarget() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Invalid targets should not have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_agentHasNoPermission() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Apps without EXECUTE_APP_FUNCTION should not have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_agentNotInAllowlist() {
        oldState.mutateExternalState().setAgentAllowlist(emptySet())
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    ACCESS_FLAG_PREGRANTED, ACCESS_FLAG_PREGRANTED
                )
            }
        }

        assertWithMessage("Non allowlisted agents should not have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testUpdateAccessFlags_validFlagsSet() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)

        val flags = ACCESS_FLAG_USER_GRANTED or ACCESS_FLAG_PREGRANTED
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    flags or ACCESS_FLAG_USER_DENIED, flags
                )
            }
        }
        assertWithMessage("Valid flags for valid app IDs should have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(flags)
    }

    @Test
    fun testUpdateAccessFlags_validFlagsSet_agentAllowlistNotEnforced() {
        oldState.mutateExternalState().setAgentAllowlist(null)
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(target)

        val flags = ACCESS_FLAG_USER_GRANTED or ACCESS_FLAG_PREGRANTED
        mutateState {
            with(appIdAppFunctionPolicy) {
                updateAccessFlags(
                    AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, USER_ID_0,
                    flags or ACCESS_FLAG_USER_DENIED, flags
                )
            }
        }
        assertWithMessage("Valid flags for valid app IDs should have flags set")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(flags)
    }

    @Test
    fun testGetTargets_needService() {
        val notTarget = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        addPackageState(notTarget)
        addPackageState(target)
        val targets = with(appIdAppFunctionPolicy) {
            GetStateScope(oldState).getTargets(0)
        }
        assertWithMessage("Expected target with AppFunctionService to be included in target list")
            .that(targets)
            .contains(TARGET_PKG_NAME)
        assertWithMessage(
            "Expected target without AppFunctionService not to be included in target list"
        ).that(targets)
            .doesNotContain(AGENT_PKG_NAME)
    }

    @Test
    fun testGetAgents_needPermission() {
        oldState.mutateExternalState().setAgentAllowlist(setOf(
            SignedPackage(AGENT_PKG_NAME, null),
            SignedPackage(TARGET_PKG_NAME, null)
        ))
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val notAgent = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(notAgent)
        val targets = with(appIdAppFunctionPolicy) {
            GetStateScope(oldState).getAgents(0)
        }
        assertWithMessage("Expected agent to be included in agents list")
            .that(targets)
            .contains(AGENT_PKG_NAME)
        assertWithMessage(
            "Expected app without EXECUTE_APP_FUNCTIONS not to be included in agents list"
        ).that(targets)
            .doesNotContain(TARGET_PKG_NAME)
    }

    @Test
    fun testGetAgents_needAllowlistIfAllowlistEnabled() {
        oldState.mutateExternalState().setAgentAllowlist(setOf())
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        addPackageState(agent)
        val targets = with(appIdAppFunctionPolicy) {
            GetStateScope(oldState).getAgents(0)
        }
        assertWithMessage("Non allowlisted apps should not be included in agents list")
            .that(targets)
            .doesNotContain(AGENT_PKG_NAME)
    }

    @Test
    fun testGetAgents_allowlistEnforcesCertificate() {
        val cert = byteArrayOf(1, 2, 3, 4)
        oldState.mutateExternalState().setAgentAllowlist(setOf(
            SignedPackage(AGENT_PKG_NAME, cert),
            SignedPackage(OTHER_AGENT_PKG_NAME, byteArrayOf(1))
        ))
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true, cert = cert)
        val agentWithWrongCert = mockPackageState(
            OTHER_AGENT_APP_ID,
            OTHER_AGENT_PKG_NAME,
            isAgent = true,
            cert = byteArrayOf(1, 2)
        )
        addPackageState(agent)
        addPackageState(agentWithWrongCert)
        val targets = with(appIdAppFunctionPolicy) {
            GetStateScope(oldState).getAgents(0)
        }
        assertWithMessage("Apps with matching certs should be included in the agents list")
            .that(targets)
            .contains(AGENT_PKG_NAME)
        assertWithMessage("Apps with non matching certs shouldn't be included in the agents list")
            .that(targets)
            .doesNotContain(OTHER_AGENT_PKG_NAME)
    }

    @Test
    fun testGetAgents_dontNeedAllowlistIfNotEnabled() {
        oldState.mutateExternalState().setAgentAllowlist(null)
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        addPackageState(agent)
        val targets = with(appIdAppFunctionPolicy) {
            GetStateScope(oldState).getAgents(0)
        }
        assertWithMessage(
            "Apps with EXECUTE_APP_FUNCTIONS should be included if allowlist isn't enabled"
        ).that(targets)
            .contains(AGENT_PKG_NAME)
    }

    @Test
    fun testOnAppIdRemoved_agentFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val otherAgent = mockPackageState(OTHER_AGENT_APP_ID, OTHER_AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(otherAgent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_1, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAppIdRemoved(AGENT_APP_ID)
            }
        }
        assertWithMessage("all flags for an agent should be cleared when its app id is removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("all flags for an agent should be cleared when its app id is removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("only the agent being removed should have its flags cleared")
            .that(getAppFunctionFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnAppIdRemoved_targetFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_1, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAppIdRemoved(TARGET_APP_ID)
            }
        }
        assertWithMessage("target flags should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("only flags related to the removed app id should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnPackageUninstalled_agentFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME, isAgent = true)
        val otherAgent = mockPackageState(OTHER_AGENT_APP_ID, OTHER_AGENT_PKG_NAME, isAgent = true)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME, isTarget = true)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME, isTarget = true)
        addPackageState(agent)
        addPackageState(otherAgent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        // remove the agent package from user 0
        addPackageState(mockPackageState(
            AGENT_APP_ID,
            AGENT_PKG_NAME,
            isAgent = true,
            installedUsers = listOf(USER_ID_1)
        ))
        assertWithMessage("expected to find in old").that(oldState.userStates[USER_ID_1]!!.appIdAppFunctionAccessFlags.contains(AGENT_APP_ID)).isTrue()
        mutateState {
            assertWithMessage("expected to find in new").that(newState.userStates[USER_ID_1]!!.appIdAppFunctionAccessFlags.contains(AGENT_APP_ID)).isTrue()
            with(appIdAppFunctionPolicy) {
                onPackageUninstalled(AGENT_PKG_NAME, AGENT_APP_ID, USER_ID_0)
            }
        }
        assertWithMessage("flags for an agent in a given user should be cleared when its package is removed from that user")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("flags for an agent in a given user should be cleared when its package is removed from that user")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("flags for an agent in other users should not be cleared")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
        assertWithMessage("only the agent being removed should have its flags cleared")
            .that(getAppFunctionFlags(OTHER_AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnPackageUninstalled_targetFlagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        val otherTarget = mockPackageState(TARGET_APP_ID, OTHER_TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        addPackageState(otherTarget)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        val targetUid = UserHandle.getUid(USER_ID_1, TARGET_APP_ID)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, targetUid, ACCESS_FLAG_USER_GRANTED)
        // remove the target package from user 0
        addPackageState(mockPackageState(
            TARGET_APP_ID,
            TARGET_PKG_NAME,
            isAgent = true,
            installedUsers = listOf(USER_ID_1)
        ))
        mutateState {
            with(appIdAppFunctionPolicy) {
                onPackageUninstalled(TARGET_PKG_NAME, TARGET_APP_ID, USER_ID_0)
            }
        }
        assertWithMessage("target flags should be removed across all users")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("target flags should be removed across all users")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(0)
        assertWithMessage("target flags for the same package, but another user, should not be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, targetUid, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
        assertWithMessage("only flags related to the removed package should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, OTHER_TARGET_APP_ID, newState))
            .isEqualTo(ACCESS_FLAG_USER_GRANTED)
    }

    @Test
    fun testOnUserRemoved_targetFlagsClearedAcrossAllUsers() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        // Target app is user 0, agent is user 1
        setAccessFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID,
            ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onUserRemoved(USER_ID_0)
            }
        }
        assertWithMessage("all target flags corresponding to the removed user should be removed")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_1, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testOnAgentRemovedFromAllowlist_flagsCleared() {
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAgentAllowlistChanged(setOf())
            }
        }
        assertWithMessage("all flags for an agent should be cleared when it is removed from the allowlist")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    @Test
    fun testOnAllowlistEnabled_invalidAgentFlagsCleared() {
        oldState.mutateExternalState().setAgentAllowlist(null)
        val agent = mockPackageState(AGENT_APP_ID, AGENT_PKG_NAME)
        val target = mockPackageState(TARGET_APP_ID, TARGET_PKG_NAME)
        addPackageState(agent)
        addPackageState(target)
        setAccessFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, ACCESS_FLAG_USER_GRANTED)
        mutateState {
            with(appIdAppFunctionPolicy) {
                onAgentAllowlistChanged(setOf())
            }
        }
        assertWithMessage("all flags for a non allowlisted agent should be cleared when the allowlist is enabled")
            .that(getAppFunctionFlags(AGENT_APP_ID, USER_ID_0, TARGET_APP_ID, newState))
            .isEqualTo(0)
    }

    companion object {
        const val AGENT_PKG_NAME = "agentPkg"
        const val OTHER_AGENT_PKG_NAME = "otherAgentPkg"
        const val TARGET_PKG_NAME = "targetPkg"
        const val OTHER_TARGET_PKG_NAME = "otherTargetPkg"
        const val AGENT_APP_ID = 0
        const val TARGET_APP_ID = 1
        const val OTHER_AGENT_APP_ID = 2
        const val OTHER_TARGET_APP_ID = 3
        const val USER_ID_0 = 0
        const val USER_ID_1 = 1

        const val NONEXISTENT_FLAGS = ACCESS_FLAG_MASK_ALL.inv()
    }
}