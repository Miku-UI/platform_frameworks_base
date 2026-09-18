/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.communal.domain.preconditions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.service.dreams.DreamService
import com.android.systemui.common.domain.interactor.PackageChangeInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.power.data.repository.PowerRepository
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.statusbar.pipeline.shared.data.repository.ConnectivityRepository
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Encapsulates the set of common, device-wide preconditions that must be met for any contextual
 * setup flow to be shown.
 */
interface CommonSetupPreconditions {
    /** Emits `true` if all common preconditions for showing a setup flow are met. */
    val allMet: Flow<Boolean>
}

@SysUISingleton
class CommonSetupPreconditionsImpl
@Inject
constructor(
    @Application private val context: Context,
    private val deviceProvisionedController: DeviceProvisionedController,
    private val packageChangeInteractor: PackageChangeInteractor,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val userContextProvider: UserContextProvider,
    keyguardInteractor: KeyguardInteractor,
    connectivityRepository: ConnectivityRepository,
    powerRepository: PowerRepository,
    @Background private val bgScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : CommonSetupPreconditions {

    /**
     * Emits true when the device is fully provisioned and the current user is set up.
     *
     * This is a critical security and usability gate. We must not show any setup flows or
     * notifications before the device is provisioned and the user has completed their setup.
     */
    private val isDeviceReady = conflatedCallbackFlow {
        val callback =
            object : DeviceProvisionedController.DeviceProvisionedListener {
                override fun onDeviceProvisionedChanged() {
                    trySend(isDeviceReady())
                }

                override fun onUserSetupChanged() {
                    trySend(isDeviceReady())
                }

                override fun onUserSwitched() {
                    trySend(isDeviceReady())
                }
            }
        trySend(isDeviceReady())
        deviceProvisionedController.addCallback(callback)
        awaitClose { deviceProvisionedController.removeCallback(callback) }
    }

    private fun isDeviceReady() =
        deviceProvisionedController.isDeviceProvisioned &&
            deviceProvisionedController.isCurrentUserSetup

    /** Emits true if at least [MIN_DREAM_COUNT] dreams are available. */
    private val areSufficientDreamsInstalled: Flow<Boolean> =
        merge(
                packageChangeInteractor.packageChanged(UserHandle.CURRENT).map { Unit },
                selectedUserInteractor.selectedUser.map { Unit },
            )
            .onStart { emit(Unit) }
            .map { areDreamsAvailable() }
            .flowOn(bgDispatcher)

    private fun areDreamsAvailable(): Boolean {
        val userContext = userContextProvider.createCurrentUserContext(context)
        val dreamServices =
            userContext.packageManager.queryIntentServices(
                Intent(DreamService.SERVICE_INTERFACE),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        return dreamServices.size >= MIN_DREAM_COUNT
    }

    private fun conditionsMet(
        deviceReady: Boolean,
        validated: Boolean,
        dreaming: Boolean,
        interactive: Boolean,
        sufficientDreamsInstalled: Boolean,
    ): Boolean = deviceReady && validated && !dreaming && interactive && sufficientDreamsInstalled

    /**
     * Emits true if all common preconditions are met:
     * 1. Device is ready (fully provisioned and user setup complete).
     * 2. Network connection is validated (internet access available).
     * 3. Device is NOT currently dreaming (to avoid interrupting an active dream).
     * 4. Device is interactive (screen is on).
     * 5. At least [MIN_DREAM_COUNT] dreams are installed.
     *
     * This flow is compatible with the lockscreen and "User Locked" states. It specifically gates
     * the dream setup flow and remains false if the device is already dreaming to prevent redundant
     * triggers.
     */
    override val allMet: Flow<Boolean> =
        combine(
                isDeviceReady,
                connectivityRepository.defaultConnections.debounce {
                    if (it.isValidated) 500L else 0L
                },
                keyguardInteractor.isDreaming,
                powerRepository.isInteractive,
                areSufficientDreamsInstalled,
            ) {
                deviceReady,
                defaultConnections,
                isDreaming,
                isInteractive,
                sufficientDreamsInstalled ->
                conditionsMet(
                    deviceReady,
                    defaultConnections.isValidated,
                    isDreaming,
                    isInteractive,
                    sufficientDreamsInstalled,
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = bgScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    conditionsMet(
                        isDeviceReady(),
                        connectivityRepository.defaultConnections.value.isValidated,
                        keyguardInteractor.isDreaming.value,
                        powerRepository.isInteractive.value,
                        areDreamsAvailable(),
                    ),
            )

    companion object {
        private const val MIN_DREAM_COUNT = 4
    }
}
