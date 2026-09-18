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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.composable.elements.UnfoldTranslations
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map

class LockscreenUpperRegionViewModel
@AssistedInject
constructor(
    private val clockInteractor: KeyguardClockInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val unfoldTransitionInteractor: UnfoldTransitionInteractor,
    private val headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    private val keyguardMediaViewModelFactory: KeyguardMediaViewModel.Factory,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val desktopInteractor: DesktopInteractor,
    private val transitionInteractor: KeyguardTransitionInteractor,
) : HydratedActivatable() {
    data class Decision<T>(val choice: T, val reason: String)

    private val keyguardMediaViewModel: KeyguardMediaViewModel by lazy {
        keyguardMediaViewModelFactory.create()
    }

    val isDozing: Boolean by keyguardInteractor.isDozing.hydratedStateOf()

    val isMediaVisible: Boolean
        get() = keyguardMediaViewModel.isMediaVisible

    val isNotificationStackActive: Boolean by
        activeNotificationsInteractor.areAnyNotificationsPresent.hydratedStateOf(
            initialValue = activeNotificationsInteractor.areAnyNotificationsPresentValue
        )

    val isHeadsUpNotificationActive: Boolean by
        headsUpNotificationInteractor.isHeadsUpOrAnimatingAway.hydratedStateOf(initialValue = false)

    val isPromotedNotificationActive: Boolean by
        clockInteractor.isAodPromotedNotificationPresent.hydratedStateOf(initialValue = false)

    val unfoldTranslations: UnfoldTranslations =
        object : UnfoldTranslations {
            override val start: Float by
                unfoldTransitionInteractor
                    .unfoldTranslationX(isOnStartSide = true)
                    .hydratedStateOf(traceName = "unfoldTranslations.start", initialValue = 0f)

            override val end: Float by
                unfoldTransitionInteractor
                    .unfoldTranslationX(isOnStartSide = false)
                    .hydratedStateOf(traceName = "unfoldTranslations.end", initialValue = 0f)
        }

    val shadeMode: ShadeMode by shadeModeInteractor.shadeMode.hydratedStateOf()

    val useDesktopStatusBar: Boolean by
        desktopInteractor.useDesktopStatusBar.hydratedStateOf(
            initialValue = desktopInteractor.useDesktopStatusBar.value
        )

    private val forcedClockSize: ClockSize? by
        clockInteractor.forcedClockSize.hydratedStateOf(initialValue = null)

    private val clockSizeSetting: ClockSizeSetting by
        clockInteractor.selectedClockSize.hydratedStateOf()

    val shouldSkipTransition: Boolean by
        transitionInteractor.transitionState
            .map { state -> state.from == KeyguardState.OFF || state.from == KeyguardState.DOZING }
            .hydratedStateOf(initialValue = false)

    fun evaluateClockSize(evaluateDynamicSize: () -> Decision<ClockSize>): Decision<ClockSize> {
        return forcedClockSize?.let { Decision(it, "forcedClockSize") }
            ?: when (clockSizeSetting) {
                ClockSizeSetting.SMALL -> Decision(ClockSize.SMALL, "clockSizeSetting == SMALL")
                ClockSizeSetting.DYNAMIC -> evaluateDynamicSize()
            }
    }

    override suspend fun onActivated() {
        coroutineScope { launch { keyguardMediaViewModel.activate() } }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenUpperRegionViewModel
    }
}
