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

package com.android.systemui.shade.ui.viewmodel

import android.app.ActivityManager
import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntRect
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.privacy.AbstractOngoingPrivacyChip
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.scene.domain.interactor.DualShadeEducationInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.domain.model.DualShadeEducationModel
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.TransitionKeys.SlightlyFasterShadeTransition
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.PrivacyChipInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.phone.domain.interactor.ShadeDarkIconInteractor
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.CarrierTextInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairos
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.EmptySystemStatusIconBlockListInteractor
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModel
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Models UI state for the shade header. */
class ShadeHeaderViewModel
@AssistedInject
constructor(
    private val activityStarter: ActivityStarter,
    private val sceneInteractor: SceneInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val carrierTextInteractor: CarrierTextInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    shadeDarkIconInteractor: ShadeDarkIconInteractor,
    mobileIconsInteractor: MobileIconsInteractor,
    val mobileIconsViewModel: dagger.Lazy<MobileIconsViewModel>,
    val systemStatusIconsBlockListInteractor: EmptySystemStatusIconBlockListInteractor,
    private val privacyChipInteractor: PrivacyChipInteractor,
    private val clockInteractor: ClockInteractor,
    private val batteryMeterViewControllerFactory: BatteryMeterViewController.Factory,
    val statusBarIconController: StatusBarIconController,
    val batteryViewModelFactory: BatteryViewModel.AlwaysShowPercent.Factory,
    val systemStatusIconsViewModelFactory: SystemStatusIconsViewModel.Factory,
    val kairosNetwork: KairosNetwork,
    val mobileIconsViewModelKairos: dagger.Lazy<MobileIconsViewModelKairos>,
    private val dualShadeEducationInteractor: DualShadeEducationInteractor,
    desktopInteractor: DesktopInteractor,
    @ShadeDisplayAware systemBarUtilsState: SystemBarUtilsState,
    @Assisted private val ignoreTestHarness: Boolean,
) : HydratedActivatable() {

    val isShadeAreaDark: IsAreaDark by
        shadeDarkIconInteractor.isShadeAreaDark.hydratedStateOf(initialValue = IsAreaDark { true })

    val createBatteryMeterViewController:
        (ViewGroup, StatusBarLocation) -> BatteryMeterViewController =
        batteryMeterViewControllerFactory::create

    /** True if there is exactly one mobile connection. */
    val isSingleCarrier: Boolean by
        mobileIconsInteractor.isSingleCarrier.hydratedStateOf(
            initialValue = mobileIconsInteractor.isSingleCarrier.value
        )

    /** The list of subscription Ids for current mobile connections. */
    val mobileSubIds: List<Int> by
        mobileIconsInteractor.filteredSubscriptions
            .map { list -> list.map { it.subscriptionId } }
            .hydratedStateOf(initialValue = emptyList())

    val carrierText: CharSequence? by carrierTextInteractor.carrierText.hydratedStateOf()

    /** The list of PrivacyItems to be displayed by the privacy chip. */
    val privacyItems: List<PrivacyItem> by privacyChipInteractor.privacyItems.hydratedStateOf()

    /** Whether or not mic & camera indicators are enabled in the device privacy config. */
    val isMicCameraIndicationEnabled: Boolean by
        privacyChipInteractor.isMicCameraIndicationEnabled.hydratedStateOf()

    /** Whether or not location indicators are enabled in the device privacy config. */
    val isLocationIndicationEnabled: Boolean by
        privacyChipInteractor.isLocationIndicationEnabled.hydratedStateOf()

    /** Whether or not the privacy chip should be visible. */
    val isPrivacyChipVisible: Boolean by derivedStateOf { privacyItems.isNotEmpty() }

    /** Whether or not the privacy chip is enabled in the device privacy config. */
    val isPrivacyChipEnabled: Boolean by derivedStateOf {
        isMicCameraIndicationEnabled || isLocationIndicationEnabled
    }

    /**
     * Avoid showing the dual shade educational tooltips in test harness mode and not explicitly
     * allowed, as the tooltip may interfere with test automation.
     */
    private val disableEducationTooltips =
        !ignoreTestHarness && ActivityManager.isRunningInUserTestHarness()

    val animateNotificationsChipBounce: Boolean
        get() =
            !disableEducationTooltips &&
                dualShadeEducationInteractor.education ==
                    DualShadeEducationModel.ForNotificationsShade

    val animateSystemIconChipBounce: Boolean
        get() =
            !disableEducationTooltips &&
                dualShadeEducationInteractor.education ==
                    DualShadeEducationModel.ForQuickSettingsShade

    val longerDateText: String by
        combine(clockInteractor.longerDateFormat, clockInteractor.currentTime) { format, time ->
                format.format(time)
            }
            .hydratedStateOf(initialValue = "")

    val shorterDateText: String by
        combine(clockInteractor.shorterDateFormat, clockInteractor.currentTime) { format, time ->
                format.format(time)
            }
            .hydratedStateOf(initialValue = "")

    val inactiveChipHighlight: ChipHighlightModel
        get() =
            if (useDesktopStatusBar) {
                ChipHighlightModel.Transparent
            } else {
                ChipHighlightModel.Weak
            }

    val statusBarHeightPx: Int by
        systemBarUtilsState.statusBarHeight.hydratedStateOf(
            traceName = "ShadeHeader#statusBarHeight",
            initialValue = 0,
        )

    private val useDesktopStatusBar: Boolean by
        desktopInteractor.useDesktopStatusBar.hydratedStateOf(
            initialValue = desktopInteractor.useDesktopStatusBar.value
        )

    /** Notifies that the privacy chip was clicked. */
    fun onPrivacyChipClicked(privacyChip: AbstractOngoingPrivacyChip) {
        privacyChipInteractor.onPrivacyChipClicked(privacyChip)
    }

    /** Notifies that the clock was clicked. */
    fun onClockClicked() {
        if (shadeModeInteractor.isDualShade && useDesktopStatusBar) {
            toggleNotificationShade(
                loggingReason = "ShadeHeaderViewModel.onClockChipClicked",
                launchClockActivityOnCollapse = false,
            )
        } else {
            clockInteractor.launchClockActivity()
        }
    }

    /** Notifies that the notification icons container was clicked. */
    fun onNotificationIconChipClicked() {
        if (!shadeModeInteractor.isDualShade) {
            return
        }
        toggleNotificationShade(
            loggingReason = "ShadeHeaderViewModel.onNotificationIconChipClicked",
            launchClockActivityOnCollapse = !useDesktopStatusBar,
        )
    }

    private fun toggleNotificationShade(
        loggingReason: String,
        launchClockActivityOnCollapse: Boolean,
    ) {
        val currentOverlays = sceneInteractor.currentOverlays.value
        if (Overlays.NotificationsShade in currentOverlays) {
            shadeInteractor.collapseNotificationsShade(
                loggingReason = loggingReason,
                transitionKey = SlightlyFasterShadeTransition,
            )
            if (launchClockActivityOnCollapse) {
                clockInteractor.launchClockActivity()
            }
        } else {
            shadeInteractor.expandNotificationsShade(loggingReason)
        }
    }

    /** Notifies that the system icons container was clicked. */
    fun onSystemIconChipClicked() {
        val loggingReason = "ShadeHeaderViewModel.onSystemIconChipClicked"
        if (shadeModeInteractor.isDualShade) {
            val currentOverlays = sceneInteractor.currentOverlays.value
            if (Overlays.QuickSettingsShade in currentOverlays) {
                shadeInteractor.collapseQuickSettingsShade(
                    loggingReason = loggingReason,
                    transitionKey = SlightlyFasterShadeTransition,
                )
            } else {
                shadeInteractor.expandQuickSettingsShade(loggingReason)
            }
        } else {
            shadeInteractor.collapseEitherShade(
                loggingReason = loggingReason,
                transitionKey = SlightlyFasterShadeTransition,
            )
        }
    }

    /** Notifies that the shadeCarrierGroup was clicked. */
    fun onShadeCarrierGroupClicked() {
        activityStarter.postStartActivityDismissingKeyguard(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            0,
        )
    }

    fun onDualShadeEducationElementBoundsChange(
        element: DualShadeEducationElement,
        bounds: IntRect,
    ) {
        dualShadeEducationInteractor.onDualShadeEducationElementBoundsChange(element, bounds)
    }

    @AssistedFactory
    interface Factory {
        fun create(ignoreTestHarness: Boolean = false): ShadeHeaderViewModel
    }
}
