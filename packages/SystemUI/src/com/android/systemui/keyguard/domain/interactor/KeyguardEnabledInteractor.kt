/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.domain.interactor

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.CoreStartable
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.sample
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Binds
import dagger.Lazy
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Logic around the keyguard being enabled, disabled, or suppressed via adb. If the keyguard is
 * disabled or suppressed, the lockscreen cannot be shown and the device will go from AOD/DOZING
 * directly to GONE.
 *
 * Keyguard can be disabled by selecting Security: "None" in settings, or by apps that hold
 * permission to do so (such as Phone). Some CTS tests also disable keyguard in onCreate or onStart
 * rather than simply dismissing the keyguard or setting up the device to have Security: None, for
 * reasons unknown.
 *
 * Keyguard can be suppressed by calling "adb shell locksettings set-disabled true", which is
 * frequently done in tests. If keyguard is suppressed, it won't show even if the keyguard is
 * enabled. If keyguard is not suppressed, then we defer to whether keyguard is enabled or disabled.
 */
@SysUISingleton
class KeyguardEnabledInteractor
@Inject
constructor(
    @Application val scope: CoroutineScope,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    val repository: KeyguardRepository,
    val biometricSettingsRepository: BiometricSettingsRepository,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val lockPatternUtils: LockPatternUtils,
    private val keyguardDismissTransitionInteractor: Lazy<KeyguardDismissTransitionInteractor>,
    private val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    deviceEntryInteractor: Lazy<DeviceEntryInteractor>,
    authenticationInteractor: AuthenticationInteractor,
    keyguardServiceShowLockscreenInteractor: KeyguardServiceShowLockscreenInteractor,
    private val sceneInteractor: Lazy<SceneInteractor>,
) : CoreStartable {

    /**
     * Whether the keyguard is enabled for the current user. Since there is no way to listen for
     * events when this changes, check any time the device is going to lockscreen or switching
     * users.
     */
    private val isKeyguardEnabledForUser: Flow<Boolean> =
        selectedUserInteractor.selectedUser.flatMapLatestConflated { selectedUser ->
            sceneInteractor
                .get()
                .transitionStateFlow
                .map { it.isTransitioning(to = Scenes.Lockscreen) }
                .distinctUntilChanged()
                .map { !lockPatternUtils.isLockScreenDisabled(selectedUser) }
        }

    /**
     * Whether the keyguard is enabled, per [KeyguardService]. If the keyguard is not enabled, the
     * lockscreen cannot be shown and the device will go from AOD/DOZING directly to GONE.
     *
     * Keyguard can be disabled by selecting Security: "None" in settings, or by apps that hold
     * permission to do so (such as Phone).
     *
     * If the keyguard is disabled while we're locked, we will transition to GONE unless we're in
     * lockdown mode. If the keyguard is re-enabled, we'll transition back to LOCKSCREEN if we were
     * locked when it was disabled.
     *
     * Even if the keyguard is enabled, it's possible for it to be suppressed temporarily via adb.
     */
    val isKeyguardEnabled: StateFlow<Boolean> =
        combine(repository.isKeyguardEnabled, isKeyguardEnabledForUser) {
                enabledPerKeyguardService,
                enabledForUser ->
                enabledPerKeyguardService && enabledForUser
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    repository.isKeyguardEnabled.value &&
                        !lockPatternUtils.isLockScreenDisabled(
                            selectedUserInteractor.getSelectedUserId()
                        ),
            )

    /** Whether we need to show the keyguard when the keyguard is re-enabled. */
    val showKeyguardWhenReenabled: Flow<Boolean> =
        merge(
            // If the keyguard is disabled while we were locked/showing keyguard, we want to re-show
            // it when the keyguard is re-enabled.
            isKeyguardEnabled
                .filter { enabled -> !enabled }
                .sample(biometricSettingsRepository.isCurrentUserInLockdown, ::Pair)
                .map { (_, inLockdown) ->
                    if (SceneContainerFlag.isEnabled) {
                        !deviceEntryInteractor.get().isDeviceEntered.value && !inLockdown
                    } else {
                        val transitionInfo =
                            internalTransitionInteractor.currentTransitionInfoInternal()
                        // ...we hide the keyguard, if it's showing and we're not in lockdown. In
                        // that case, we want to remember that and re-show it when keyguard is
                        // enabled again.
                        transitionInfo.to != KeyguardState.GONE && !inLockdown
                    }
                },
            // If we change to an insecure auth method, any pending re-show should be cleared.
            authenticationInteractor.authenticationMethod
                .map { it.isSecure }
                .distinctUntilChanged()
                .filter { secure -> !secure },
            // If we timeout or lockNow while the keyguard is disabled, we won't show immediately,
            // but need to re-show when we're re-enabled.
            keyguardServiceShowLockscreenInteractor.showNowEvents
                .filter {
                    it == ShowWhileAwakeReason.KEYGUARD_TIMEOUT_WHILE_SCREEN_ON &&
                        !isKeyguardEnabled.value
                }
                .map { true },
        )

    override fun start() {
        /*
         * Whenever keyguard is disabled, transition to GONE unless we're in lockdown or already
         * GONE.
         */
        if (!SceneContainerFlag.isEnabled) {
            scope.launch {
                repository.isKeyguardEnabled
                    .filter { enabled -> !enabled }
                    .sample(biometricSettingsRepository.isCurrentUserInLockdown, ::Pair)
                    .collect { (_, inLockdown) ->
                        val currentTransitionInfo =
                            internalTransitionInteractor.currentTransitionInfoInternal()
                        if (currentTransitionInfo.to != KeyguardState.GONE && !inLockdown) {
                            keyguardDismissTransitionInteractor
                                .get()
                                .startDismissKeyguardTransition("keyguard disabled")
                        }
                    }
            }
        }
    }

    fun notifyKeyguardEnabled(enabled: Boolean) {
        repository.setKeyguardEnabled(enabled)
    }

    /**
     * Whether the keyguard is enabled, and has not been suppressed via adb.
     *
     * There is unfortunately no callback for [isKeyguardSuppressed], which means this can't be a
     * flow, since it's ambiguous when we would query the latest suppression value.
     */
    suspend fun isKeyguardEnabledAndNotSuppressed(): Boolean {
        return repository.isKeyguardEnabled.value && !isKeyguardSuppressed()
    }

    /**
     * Returns whether the lockscreen has been disabled ("suppressed") via "adb shell locksettings
     * set-disabled". If suppressed, we'll ignore all signals that would typically result in showing
     * the keyguard, regardless of the value of [isKeyguardEnabled].
     *
     * It's extremely confusing to have [isKeyguardEnabled] not be the inverse of "is lockscreen
     * disabled", so this method intentionally re-terms it as "suppressed".
     *
     * Note that if the lockscreen is currently showing when it's suppressed, it will remain visible
     * until it's unlocked, at which point it will never re-appear until suppression is removed.
     */
    suspend fun isKeyguardSuppressed(
        userId: Int = selectedUserInteractor.getSelectedUserId()
    ): Boolean {
        // isLockScreenDisabled returns true whenever keyguard is not enabled, even if the adb
        // command was not used to disable/suppress the lockscreen. To make these booleans as clear
        // as possible, only return true if keyguard is suppressed when it otherwise would have
        // been enabled.
        return withContext(backgroundDispatcher) {
            repository.isKeyguardEnabled.value && lockPatternUtils.isLockScreenDisabled(userId)
        }
    }

    suspend fun hydrateTableLogBuffer(tableLogBuffer: TableLogBuffer) {
        isKeyguardEnabled
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                columnName = "isKeyguardEnabled",
                initialValue = isKeyguardEnabled.value,
            )
            .collect()
    }

    @dagger.Module
    interface Module {
        @Binds
        @IntoMap
        @ClassKey(KeyguardEnabledInteractor::class)
        fun coreStartable(impl: KeyguardEnabledInteractor): CoreStartable
    }
}
