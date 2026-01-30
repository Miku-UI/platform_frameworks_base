/*
 *  Copyright (C) 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.android.systemui.keyguard.data.quickaffordance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.ZenModeConfig
import android.util.Log
import com.android.settingslib.notification.modes.EnableDndDialogFactory
import com.android.settingslib.notification.modes.EnableDndDialogMetricsLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DoNotDisturbQuickAffordanceConfig(
    private val context: Context,
    private val interactor: ZenModeInteractor,
    private val userTracker: UserTracker,
    @Background private val backgroundScope: CoroutineScope,
    private val testConditionId: Uri?,
    testDialogFactory: EnableDndDialogFactory?,
) : KeyguardQuickAffordanceConfig {

    @Inject
    constructor(
        @ShadeDisplayAware context: Context,
        interactor: ZenModeInteractor,
        userTracker: UserTracker,
        @Background backgroundScope: CoroutineScope,
    ) : this(context, interactor, userTracker, backgroundScope, null, null)

    private var settingsValue: Int = 0

    private val isAvailable: StateFlow<Boolean> by lazy {
        interactor.isZenAvailable.stateIn(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    }

    private val conditionUri: Uri
        get() =
            testConditionId
                ?: ZenModeConfig.toTimeCondition(
                        context,
                        settingsValue,
                        userTracker.userId,
                        true, /* shortVersion */
                    )
                    .id

    private val dialogFactory: EnableDndDialogFactory by lazy {
        testDialogFactory
            ?: EnableDndDialogFactory(
                context,
                R.style.Theme_SystemUI_Dialog,
                true, /* cancelIsNeutral */
                EnableDndDialogMetricsLogger(context),
            )
    }

    override val key: String = BuiltInKeyguardQuickAffordanceKeys.DO_NOT_DISTURB

    override fun pickerName(): String = context.getString(R.string.quick_settings_dnd_label)

    override val pickerIconResourceId: Int = R.drawable.ic_do_not_disturb

    override val lockScreenState: Flow<KeyguardQuickAffordanceConfig.LockScreenState> =
        combine(isAvailable, interactor.dndMode) { isAvailable, dndMode ->
            if (!isAvailable) {
                KeyguardQuickAffordanceConfig.LockScreenState.Hidden
            } else if (dndMode?.isActive == true) {
                KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                    Icon.Resource(
                        R.drawable.qs_dnd_icon_on,
                        ContentDescription.Resource(R.string.dnd_is_on),
                    ),
                    ActivationState.Active,
                )
            } else {
                KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                    Icon.Resource(
                        R.drawable.qs_dnd_icon_off,
                        ContentDescription.Resource(R.string.dnd_is_off),
                    ),
                    ActivationState.Inactive,
                )
            }
        }

    override suspend fun getPickerScreenState(): KeyguardQuickAffordanceConfig.PickerScreenState {
        return if (isAvailable.value) {
            KeyguardQuickAffordanceConfig.PickerScreenState.Default(
                configureIntent = Intent(Settings.ACTION_ZEN_MODE_SETTINGS)
            )
        } else {
            KeyguardQuickAffordanceConfig.PickerScreenState.UnavailableOnDevice
        }
    }

    override fun onTriggered(
        expandable: Expandable?
    ): KeyguardQuickAffordanceConfig.OnTriggeredResult {
        return if (!isAvailable.value) {
            KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false)
        } else {
            val dnd = interactor.dndMode.value
            if (dnd == null) {
                Log.wtf(TAG, "Triggered DND but it's null!?")
                return KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false)
            }
            if (dnd.isActive) {
                interactor.deactivateMode(dnd)
                return KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false)
            } else {
                if (interactor.shouldAskForZenDuration(dnd)) {
                    // NOTE: The dialog handles turning on the mode itself.
                    return KeyguardQuickAffordanceConfig.OnTriggeredResult.ShowDialog(
                        dialogFactory.createDialog(),
                        expandable,
                    )
                } else {
                    interactor.activateMode(dnd)
                    return KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false)
                }
            }
        }
    }

    companion object {
        const val TAG = "DoNotDisturbQuickAffordanceConfig"
    }
}
