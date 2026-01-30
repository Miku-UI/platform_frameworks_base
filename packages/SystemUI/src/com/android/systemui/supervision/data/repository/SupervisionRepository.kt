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

package com.android.systemui.supervision.data.repository

import android.annotation.SuppressLint
import android.annotation.WorkerThread
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.app.supervision.SupervisionManager.SupervisionListener
import android.content.ComponentName
import android.content.Context
import android.content.pm.UserInfo
import android.os.UserHandle
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.supervision.data.model.SupervisionModel
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Source of truth for any info related to the app(s) enforcing supervision (e.g. parental controls)
 * on the device.
 */
interface SupervisionRepository {
    /** The current [SupervisionModel] for the current user */
    val supervision: Flow<SupervisionModel>
}

@SuppressLint("MissingPermission")
@SysUISingleton
class SupervisionRepositoryImpl
@Inject
constructor(
    supervisionManagerProvider: Provider<SupervisionManager>,
    userRepository: UserRepository,
    private val roleManager: RoleManager,
    private val devicePolicyManager: DevicePolicyManager,
    @Application private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : SupervisionRepository {
    private var currentModelForUser: SupervisionModel =
        SupervisionModel(
            isSupervisionEnabled = false,
            label = null,
            icon = null,
            disclaimerText = null,
            footerText = null,
        )

    @WorkerThread
    private fun createSupervisionMode(
        isSupervisionEnabledForUser: Boolean,
        userHandle: UserHandle,
    ): SupervisionModel {
        if (!isSupervisionEnabledForUser) {
            return SupervisionModel(
                isSupervisionEnabled = false,
                label = null,
                icon = null,
                disclaimerText = null,
                footerText = null,
            )
        }

        val systemSupervisionRoleHolders =
            roleManager.getRoleHoldersAsUser(RoleManager.ROLE_SYSTEM_SUPERVISION, userHandle)
        val supervisionRoleHolders =
            roleManager.getRoleHoldersAsUser(RoleManager.ROLE_SUPERVISION, userHandle)
        val po = devicePolicyManager.getProfileOwnerAsUser(userHandle)
        val defaultSupervisionComponent =
            ComponentName.unflattenFromString(
                context.getString(
                    com.android.internal.R.string.config_defaultSupervisionProfileOwnerComponent
                )
            )
        val isSupervisionProfileOwner = po != null && po == defaultSupervisionComponent
        val isOnDeviceSupervision =
            systemSupervisionRoleHolders.isNotEmpty() &&
                systemSupervisionRoleHolders.none { supervisionRoleHolders.contains(it) } &&
                !isSupervisionProfileOwner

        return if (isOnDeviceSupervision) {
            SupervisionModel(
                isSupervisionEnabled = true,
                label = context.getString(R.string.status_bar_pin_supervision),
                icon = context.getDrawable(R.drawable.ic_pin_supervision),
                disclaimerText = context.getString(R.string.monitoring_description_pin_protection),
                footerText = context.getString(R.string.quick_settings_disclosure_pin_protection),
            )
        } else {
            SupervisionModel(
                isSupervisionEnabled = true,
                label = context.getString(R.string.status_bar_supervision),
                icon = context.getDrawable(R.drawable.ic_supervision),
                disclaimerText =
                    context.getString(R.string.monitoring_description_parental_controls),
                footerText = context.getString(R.string.quick_settings_disclosure_parental_controls),
            )
        }
    }

    override val supervision: Flow<SupervisionModel> =
        userRepository.selectedUserInfo
            .flatMapLatestConflated<UserInfo, SupervisionModel> { userInfo ->
                conflatedCallbackFlow {
                    val supervisionManager = supervisionManagerProvider.get()

                    fun refreshState(isSupervisionEnabled: Boolean? = null) {
                        val isSupervisionEnabledForUser =
                            isSupervisionEnabled
                                ?: supervisionManager.isSupervisionEnabledForUser(userInfo.id)
                        currentModelForUser =
                            createSupervisionMode(isSupervisionEnabledForUser, userInfo.userHandle)

                        trySendWithFailureLogging(
                            currentModelForUser,
                            LOG_TAG,
                            "$currentModelForUser",
                        )
                    }

                    class SupervisionStateListener : SupervisionListener() {
                        override fun onSupervisionDisabled(userId: Int) {
                            if (userInfo.id == userId) {
                                refreshState(isSupervisionEnabled = false)
                            }
                        }

                        override fun onSupervisionEnabled(userId: Int) {
                            if (userInfo.id == userId) {
                                refreshState(isSupervisionEnabled = true)
                            }
                        }
                    }

                    // emit initial value
                    refreshState()

                    val listener = SupervisionStateListener()
                    supervisionManager.registerSupervisionListener(listener)

                    awaitClose { supervisionManager.unregisterSupervisionListener(listener) }
                }
            }
            .flowOn(backgroundDispatcher)

    private companion object {
        const val LOG_TAG = "SupervisionRepository"
    }
}
