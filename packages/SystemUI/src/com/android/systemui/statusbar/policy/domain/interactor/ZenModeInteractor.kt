/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.policy.domain.interactor

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.provider.Settings.Secure.ZEN_DURATION_FOREVER
import android.provider.Settings.Secure.ZEN_DURATION_PROMPT
import android.service.notification.ZenPolicy.STATE_DISALLOW
import android.service.notification.ZenPolicy.VISUAL_EFFECT_NOTIFICATION_LIST
import android.util.Log
import androidx.concurrent.futures.await
import com.android.settingslib.notification.data.repository.ZenModeRepository
import com.android.settingslib.notification.modes.ZenIconLoader
import com.android.settingslib.notification.modes.ZenMode
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository
import com.android.systemui.statusbar.policy.data.repository.DeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.UserSetupRepository
import com.android.systemui.statusbar.policy.domain.model.ActiveZenModes
import com.android.systemui.statusbar.policy.domain.model.ZenModeInfo
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * An interactor that performs business logic related to the status and configuration of Zen Mode
 * (or Do Not Disturb/DND Mode).
 */
@SysUISingleton
class ZenModeInteractor
@Inject
constructor(
    private val context: Context,
    private val zenModeRepository: ZenModeRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Background private val backgroundScope: CoroutineScope,
    private val iconLoader: ZenIconLoader,
    deviceProvisioningRepository: DeviceProvisioningRepository,
    userSetupRepository: UserSetupRepository,
) {
    /**
     * List of predicates to determine if the [ZenMode] blocks an audio stream. Typical use case
     * would be: `zenModeByStreamPredicates[stream](zenMode)`
     */
    private val zenModeByStreamPredicates =
        mapOf<Int, (ZenMode) -> Boolean>(
            AudioManager.STREAM_MUSIC to { it.policy.priorityCategoryMedia == STATE_DISALLOW },
            AudioManager.STREAM_ALARM to { it.policy.priorityCategoryAlarms == STATE_DISALLOW },
            AudioManager.STREAM_SYSTEM to { it.policy.priorityCategorySystem == STATE_DISALLOW },
        )

    val isZenAvailable: Flow<Boolean> =
        combine(
            deviceProvisioningRepository.isDeviceProvisioned,
            userSetupRepository.isUserSetUp,
        ) { isDeviceProvisioned, isUserSetUp ->
            isDeviceProvisioned && isUserSetUp
        }

    val isZenModeEnabled: Flow<Boolean> =
        zenModeRepository.globalZenMode
            .map {
                when (it ?: Settings.Global.ZEN_MODE_OFF) {
                    Settings.Global.ZEN_MODE_ALARMS -> true
                    Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS -> true
                    Settings.Global.ZEN_MODE_NO_INTERRUPTIONS -> true
                    Settings.Global.ZEN_MODE_OFF -> false
                    else -> false
                }
            }
            .distinctUntilChanged()

    val areNotificationsHiddenInShade: Flow<Boolean> =
        combine(isZenModeEnabled, zenModeRepository.consolidatedNotificationPolicy) {
                dndEnabled,
                policy ->
                if (!dndEnabled) {
                    false
                } else {
                    val showInNotificationList = policy?.showInNotificationList() ?: true
                    !showInNotificationList
                }
            }
            .distinctUntilChanged()

    val modes: StateFlow<List<ZenMode>> = zenModeRepository.modes

    /**
     * Returns the special "manual DND" mode.
     *
     * This should only be used when there is a strong reason to handle DND specifically (such as
     * legacy UI pieces that haven't been updated to use modes more generally, or if the user
     * explicitly wants a shortcut to DND). Please prefer using [modes] or [activeModes] in all
     * other scenarios.
     */
    val dndMode: StateFlow<ZenMode?> =
        zenModeRepository.modes
            .map { modes -> modes.singleOrNull { it.isManualDnd } }
            .stateIn(scope = backgroundScope, started = SharingStarted.Eagerly, initialValue = null)

    /**
     * Returns the current state of the special "manual DND" mode.
     *
     * This should only be used when there is a strong reason to handle DND specifically (such as
     * legacy UI pieces that haven't been updated to use modes more generally, or if the user
     * explicitly wants a shortcut to DND). Please prefer using [modes] or [activeModes] in all
     * other scenarios.
     */
    fun getDndMode(): ZenMode {
        return zenModeRepository.getModes().single { it.isManualDnd }
    }

    /** Flow returning the currently active mode(s), if any. */
    val activeModes: Flow<ActiveZenModes> =
        modes
            .map { modes -> buildActiveZenModes(modes) }
            .flowOn(bgDispatcher)
            .distinctUntilChanged()

    fun canBeBlockedByZenMode(stream: AudioStream): Boolean =
        zenModeByStreamPredicates.containsKey(stream.value)

    fun activeModesBlockingStream(stream: AudioStream): Flow<ActiveZenModes> {
        val isBlockingStream = zenModeByStreamPredicates[stream.value]
        require(isBlockingStream != null) {
            "$stream is unsupported. Use canBeBlockedByZenMode to check if the stream can be affected by the Zen Mode."
        }
        return modes
            .map { modes -> modes.filter { isBlockingStream(it) } }
            .map { modes -> buildActiveZenModes(modes) }
            .flowOn(bgDispatcher)
            .distinctUntilChanged()
    }

    suspend fun getActiveModes(): ActiveZenModes {
        if (android.app.Flags.modesUiTileReactivatesLast()) {
            Log.wtfStack(
                TAG,
                "getActiveModes shouldn't be called with modes_ui_tile_reactivates_last",
            )
        }

        return buildActiveZenModes(zenModeRepository.getModes())
    }

    private suspend fun buildActiveZenModes(modes: List<ZenMode>): ActiveZenModes {
        val activeModesList =
            modes.filter { mode -> mode.isActive }.sortedWith(ZenMode.PRIORITIZING_COMPARATOR)
        val mainActiveMode =
            activeModesList.firstOrNull()?.let { ZenModeInfo(it.id, it.name, getModeIcon(it)) }

        return ActiveZenModes(activeModesList.map { m -> m.name }, mainActiveMode)
    }

    val mainActiveMode: Flow<ZenModeInfo?> = activeModes.map { a -> a.main }.distinctUntilChanged()

    val modesHidingNotifications: Flow<ActiveZenModes> by lazy {
        modes
            .map { modes ->
                modes.filter { mode ->
                    mode.isActive &&
                        !mode.policy.isVisualEffectAllowed(
                            /* effect = */ VISUAL_EFFECT_NOTIFICATION_LIST,
                            /* defaultVal = */ true,
                        )
                }
            }
            .map { modes -> buildActiveZenModes(modes) }
            .flowOn(bgDispatcher)
            .distinctUntilChanged()
    }

    suspend fun getModeIcon(mode: ZenMode): Icon.Loaded {
        val zenIcon = iconLoader.getIcon(context, mode).await()
        val drawable = zenIcon.drawable
        // Return a copy of the icon, to prevent callers from making changes that will be reflected
        // everywhere.
        val drawableCopy = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        return Icon.Loaded(
            drawable = drawableCopy,
            contentDescription = ContentDescription.Loaded(mode.name),
            packageName = zenIcon.key.resPackage,
            resId = zenIcon.key.resId,
        )
    }

    fun activateMode(zenMode: ZenMode) {
        if (zenMode.isManualDnd) {
            val duration =
                when (zenDuration) {
                    ZEN_DURATION_PROMPT -> {
                        Log.e(
                            TAG,
                            "Interactor cannot handle showing the zen duration prompt. " +
                                    "Please use EnableZenModeDialog when this setting is active.",
                        )
                        null
                    }

                    ZEN_DURATION_FOREVER -> null
                    else -> Duration.ofMinutes(zenDuration.toLong())
                }

            zenModeRepository.activateMode(zenMode, duration)
        } else {
            zenModeRepository.activateMode(zenMode)
        }
    }

    fun deactivateMode(zenMode: ZenMode) {
        zenModeRepository.deactivateMode(zenMode)
    }

    fun deactivateAllModes() {
        if (android.app.Flags.modesUiTileReactivatesLast()) {
            // Deactivate in reverse order of priority. This will prevent flickering in the
            // "active mode" icon (which is the highest-priority one).
            val modesToDeactivate =
                zenModeRepository
                    .getModes()
                    .filter { it.isActive }
                    .sortedWith(ZenMode.PRIORITIZING_COMPARATOR.reversed())
            for (mode in modesToDeactivate) {
                deactivateMode(mode)
            }
        } else {
            for (mode in zenModeRepository.getModes()) {
                if (mode.isActive) {
                    deactivateMode(mode)
                }
            }
        }
    }

    private val zenDuration
        get() = notificationSettingsRepository.zenDuration.value

    fun shouldAskForZenDuration(mode: ZenMode): Boolean =
        mode.isManualDnd && (zenDuration == ZEN_DURATION_PROMPT)

    companion object {
        private const val TAG = "ZenModeInteractor"
    }
}
