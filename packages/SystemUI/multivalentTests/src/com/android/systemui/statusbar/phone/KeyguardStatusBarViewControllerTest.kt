/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.statusbar.phone

import android.app.StatusBarManager
import android.graphics.Insets
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.CarrierTextController
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.keyguard.logging.KeyguardLogger
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.communal.data.repository.fakeCommunalSceneRepository
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.dreams.ui.viewmodel.dreamViewModel
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionState.CANCELED
import com.android.systemui.keyguard.shared.model.TransitionState.FINISHED
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.keyguard.shared.model.TransitionState.STARTED
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.viewmodel.glanceableHubToLockscreenTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.goneToGlanceableHubTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.lockscreenToGlanceableHubTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.occludedToLockscreenTransitionViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeViewStateProvider
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.data.repository.StatusBarContentInsetsProviderStore
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler
import com.android.systemui.statusbar.layout.mockStatusBarContentInsetsProvider
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.batteryViewModelShowWhenChargingOrSettingFactory
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.batteryWithPercentViewModelFactory
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.UserInfoController
import com.android.systemui.statusbar.ui.viewmodel.keyguardStatusBarViewModel
import com.android.systemui.statusbar.ui.viewmodel.statusBarUserChipViewModel
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
@DisableFlags(NewStatusBarIcons.FLAG_NAME)
class KeyguardStatusBarViewControllerTest : SysuiTestCase() {
    private lateinit var kosmos: Kosmos
    private lateinit var testScope: TestScope

    @Mock private lateinit var carrierTextController: CarrierTextController

    @Mock private lateinit var configurationController: ConfigurationController

    @Mock private lateinit var animationScheduler: SystemStatusAnimationScheduler

    @Mock private lateinit var batteryController: BatteryController

    @Mock private lateinit var userInfoController: UserInfoController

    @Mock private lateinit var statusBarIconController: StatusBarIconController

    @Mock private lateinit var iconManagerFactory: TintedIconManager.Factory

    @Mock private lateinit var iconManager: TintedIconManager

    @Mock private lateinit var batteryMeterViewController: BatteryMeterViewController

    @Mock private lateinit var keyguardStateController: KeyguardStateController

    @Mock private lateinit var keyguardBypassController: KeyguardBypassController

    @Mock private lateinit var keyguardUpdateMonitor: KeyguardUpdateMonitor

    @Mock private lateinit var biometricUnlockController: BiometricUnlockController

    @Mock
    private lateinit var statusBarContentInsetsProviderStore: StatusBarContentInsetsProviderStore

    @Mock private lateinit var userManager: UserManager

    @Captor
    private lateinit var configurationListenerCaptor:
        ArgumentCaptor<ConfigurationController.ConfigurationListener>

    @Captor
    private lateinit var keyguardCallbackCaptor: ArgumentCaptor<KeyguardUpdateMonitorCallback>

    @Mock private lateinit var secureSettings: SecureSettings

    @Mock private lateinit var commandQueue: CommandQueue

    @Mock private lateinit var logger: KeyguardLogger

    @Mock private lateinit var statusOverlayHoverListenerFactory: StatusOverlayHoverListenerFactory

    private lateinit var shadeViewStateProvider: TestShadeViewStateProvider

    private lateinit var keyguardStatusBarView: KeyguardStatusBarView
    private lateinit var controller: KeyguardStatusBarViewController
    private val fakeExecutor = FakeExecutor(FakeSystemClock())
    private val backgroundExecutor = FakeExecutor(FakeSystemClock())

    private lateinit var looper: TestableLooper

    @Before
    @Throws(Exception::class)
    fun setup() {
        looper = TestableLooper.get(this)
        kosmos = testKosmos()
        testScope = kosmos.testScope
        shadeViewStateProvider = TestShadeViewStateProvider()

        whenever(
                kosmos.mockStatusBarContentInsetsProvider
                    .getStatusBarContentInsetsForCurrentRotation()
            )
            .thenReturn(Insets.of(0, 0, 0, 0))

        MockitoAnnotations.initMocks(this)

        whenever(iconManagerFactory.create(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(iconManager)
        whenever(statusBarContentInsetsProviderStore.forDisplay(context.displayId))
            .thenReturn(kosmos.mockStatusBarContentInsetsProvider)
        allowTestableLooperAsMainThread()
        looper.runWithLooper {
            keyguardStatusBarView =
                Mockito.spy(
                    LayoutInflater.from(mContext).inflate(R.layout.keyguard_status_bar, null)
                        as KeyguardStatusBarView
                )
            whenever(keyguardStatusBarView.display).thenReturn(mContext.display)
            whenever(keyguardStatusBarView.isAttachedToWindow).thenReturn(true)
        }

        controller = createController()
    }

    private fun createController(): KeyguardStatusBarViewController {
        return KeyguardStatusBarViewController(
            kosmos.testDispatcher,
            context,
            keyguardStatusBarView,
            carrierTextController,
            configurationController,
            animationScheduler,
            batteryController,
            userInfoController,
            statusBarIconController,
            iconManagerFactory,
            batteryMeterViewController,
            kosmos.batteryWithPercentViewModelFactory,
            kosmos.batteryViewModelShowWhenChargingOrSettingFactory,
            shadeViewStateProvider,
            keyguardStateController,
            keyguardBypassController,
            keyguardUpdateMonitor,
            kosmos.keyguardStatusBarViewModel,
            biometricUnlockController,
            kosmos.statusBarStateController,
            statusBarContentInsetsProviderStore,
            userManager,
            kosmos.statusBarUserChipViewModel,
            secureSettings,
            commandQueue,
            fakeExecutor,
            backgroundExecutor,
            logger,
            statusOverlayHoverListenerFactory,
            kosmos.communalSceneInteractor,
            kosmos.glanceableHubToLockscreenTransitionViewModel,
            kosmos.lockscreenToGlanceableHubTransitionViewModel,
            kosmos.goneToGlanceableHubTransitionViewModel,
            kosmos.occludedToLockscreenTransitionViewModel,
            kosmos.dreamViewModel,
            kosmos.keyguardInteractor,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onViewAttached_updateUserSwitcherFlagEnabled_callbacksRegistered() {
        controller.onViewAttached()

        runAllScheduled()
        Mockito.verify(configurationController).addCallback(ArgumentMatchers.any())
        Mockito.verify(animationScheduler).addCallback(ArgumentMatchers.any())
        Mockito.verify(userInfoController).addCallback(ArgumentMatchers.any())
        Mockito.verify(commandQueue).addCallback(ArgumentMatchers.any())
        Mockito.verify(statusBarIconController).addIconGroup(ArgumentMatchers.any())
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    @DisableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onViewAttached_updateUserSwitcherFlagDisabled_callbacksRegistered() {
        controller.onViewAttached()

        Mockito.verify(configurationController).addCallback(ArgumentMatchers.any())
        Mockito.verify(animationScheduler).addCallback(ArgumentMatchers.any())
        Mockito.verify(userInfoController).addCallback(ArgumentMatchers.any())
        Mockito.verify(commandQueue).addCallback(ArgumentMatchers.any())
        Mockito.verify(statusBarIconController).addIconGroup(ArgumentMatchers.any())
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onConfigurationChanged_updateUserSwitcherFlagEnabled_updatesUserSwitcherVisibility() {
        controller.onViewAttached()
        runAllScheduled()
        Mockito.verify(configurationController).addCallback(configurationListenerCaptor.capture())
        Mockito.clearInvocations(userManager)
        Mockito.clearInvocations(keyguardStatusBarView)

        configurationListenerCaptor.value.onConfigChanged(null)

        runAllScheduled()
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
        Mockito.verify(keyguardStatusBarView).setUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    @DisableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onConfigurationChanged_updateUserSwitcherFlagDisabled_updatesUserSwitcherVisibility() {
        controller.onViewAttached()
        Mockito.verify(configurationController).addCallback(configurationListenerCaptor.capture())
        Mockito.clearInvocations(userManager)
        Mockito.clearInvocations(keyguardStatusBarView)

        configurationListenerCaptor.value.onConfigChanged(null)
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
        Mockito.verify(keyguardStatusBarView).setUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onKeyguardVisibilityChanged_userSwitcherFlagEnabled_updatesUserSwitcherVisibility() {
        controller.onViewAttached()
        runAllScheduled()
        Mockito.verify(keyguardUpdateMonitor).registerCallback(keyguardCallbackCaptor.capture())
        Mockito.clearInvocations(userManager)
        Mockito.clearInvocations(keyguardStatusBarView)

        keyguardCallbackCaptor.value.onKeyguardVisibilityChanged(true)

        runAllScheduled()
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
        Mockito.verify(keyguardStatusBarView).setUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    @DisableFlags(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND)
    fun onKeyguardVisibilityChanged_userSwitcherFlagDisabled_updatesUserSwitcherVisibility() {
        controller.onViewAttached()
        Mockito.verify(keyguardUpdateMonitor).registerCallback(keyguardCallbackCaptor.capture())
        Mockito.clearInvocations(userManager)
        Mockito.clearInvocations(keyguardStatusBarView)

        keyguardCallbackCaptor.value.onKeyguardVisibilityChanged(true)
        Mockito.verify(userManager).isUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
        Mockito.verify(keyguardStatusBarView).setUserSwitcherEnabled(ArgumentMatchers.anyBoolean())
    }

    @Test
    fun onViewDetached_callbacksUnregistered() {
        // Set everything up first.
        controller.onViewAttached()

        controller.onViewDetached()

        Mockito.verify(configurationController).removeCallback(ArgumentMatchers.any())
        Mockito.verify(animationScheduler).removeCallback(ArgumentMatchers.any())
        Mockito.verify(userInfoController).removeCallback(ArgumentMatchers.any())
        Mockito.verify(commandQueue).removeCallback(ArgumentMatchers.any())
        Mockito.verify(statusBarIconController).removeIconGroup(ArgumentMatchers.any())
    }

    @Test
    @DisableSceneContainer
    fun onViewReAttached_flagOff_iconManagerNotReRegistered() {
        controller.onViewAttached()
        controller.onViewDetached()
        Mockito.reset(statusBarIconController)

        controller.onViewAttached()

        Mockito.verify(statusBarIconController, Mockito.never())
            .addIconGroup(ArgumentMatchers.any())
    }

    @Test
    @EnableSceneContainer
    fun onViewReAttached_flagOn_iconManagerReRegistered() {
        controller.onViewAttached()
        controller.onViewDetached()
        Mockito.reset(statusBarIconController)

        controller.onViewAttached()

        Mockito.verify(statusBarIconController).addIconGroup(ArgumentMatchers.any())
    }

    @Test
    @DisableSceneContainer
    fun setBatteryListening_true_callbackAdded() {
        controller.setBatteryListening(true)

        Mockito.verify(batteryController).addCallback(ArgumentMatchers.any())
    }

    @Test
    @DisableSceneContainer
    fun setBatteryListening_false_callbackRemoved() {
        // First set to true so that we know setting to false is a change in state.
        controller.setBatteryListening(true)

        controller.setBatteryListening(false)

        Mockito.verify(batteryController).removeCallback(ArgumentMatchers.any())
    }

    @Test
    @DisableSceneContainer
    fun setBatteryListening_trueThenTrue_callbackAddedOnce() {
        controller.setBatteryListening(true)
        controller.setBatteryListening(true)

        Mockito.verify(batteryController).addCallback(ArgumentMatchers.any())
    }

    @Test
    @EnableSceneContainer
    fun setBatteryListening_true_flagOn_callbackNotAdded() {
        controller.setBatteryListening(true)

        Mockito.verify(batteryController, Mockito.never()).addCallback(ArgumentMatchers.any())
    }

    @Test
    fun updateTopClipping_viewClippingUpdated() {
        val viewTop = 20
        keyguardStatusBarView.top = viewTop
        val notificationPanelTop = 30

        controller.updateTopClipping(notificationPanelTop)

        assertThat(keyguardStatusBarView.clipBounds.top).isEqualTo(notificationPanelTop - viewTop)
    }

    @Test
    fun setNotTopClipping_viewClippingUpdatedToZero() {
        // Start out with some amount of top clipping.
        controller.updateTopClipping(50)
        assertThat(keyguardStatusBarView.clipBounds.top).isGreaterThan(0)

        controller.setNoTopClipping()

        assertThat(keyguardStatusBarView.clipBounds.top).isEqualTo(0)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_alphaAndVisibilityGiven_viewUpdated() {
        // Verify the initial values so we know the method triggers changes.
        assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

        val newAlpha = 0.5f
        val newVisibility = View.INVISIBLE
        controller.updateViewState(newAlpha, newVisibility)

        assertThat(keyguardStatusBarView.alpha).isEqualTo(newAlpha)
        assertThat(keyguardStatusBarView.visibility).isEqualTo(newVisibility)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_paramVisibleButIsDisabled_viewIsInvisible() {
        controller.onViewAttached()
        setDisableSystemIcons(true)

        controller.updateViewState(1f, View.VISIBLE)

        // Since we're disabled, we stay invisible
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_notKeyguardState_nothingUpdated() {
        controller.onViewAttached()
        updateStateToNotKeyguard()

        val oldAlpha = keyguardStatusBarView.alpha

        controller.updateViewState()

        assertThat(keyguardStatusBarView.alpha).isEqualTo(oldAlpha)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_bypassEnabledAndShouldListenForFace_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

        whenever(keyguardUpdateMonitor.shouldListenForFace()).thenReturn(true)
        whenever(keyguardBypassController.bypassEnabled).thenReturn(true)
        onFinishedGoingToSleep()

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_bypassNotEnabled_viewShown() {
        controller.onViewAttached()
        updateStateToKeyguard()

        whenever(keyguardUpdateMonitor.shouldListenForFace()).thenReturn(true)
        whenever(keyguardBypassController.bypassEnabled).thenReturn(false)
        onFinishedGoingToSleep()

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_shouldNotListenForFace_viewShown() {
        controller.onViewAttached()
        updateStateToKeyguard()

        whenever(keyguardUpdateMonitor.shouldListenForFace()).thenReturn(false)
        whenever(keyguardBypassController.bypassEnabled).thenReturn(true)
        onFinishedGoingToSleep()

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_panelExpandedHeightZero_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()

        shadeViewStateProvider.panelViewExpandedHeight = 0f

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_lockscreenShadeDrag40Percent_alphaIsAt20Percent() {
        controller.onViewAttached()
        updateStateToKeyguard()

        shadeViewStateProvider.lockscreenShadeDragProgress = .4f

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
        assertThat(keyguardStatusBarView.alpha).isWithin(.01f).of(.2f)
    }

    @Test
    @DisableSceneContainer
    @EnableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun updateViewState_lockscreenShadeDragOverHub40Percent_alphaIsAt20Percent() =
        testScope.runTest {
            controller.onViewAttached()
            updateStateToKeyguard()

            // Fully transition to communal, and verify status bar is fully visible
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)

            // Start dragging down shade, and verify status bar alpha updates
            shadeViewStateProvider.lockscreenShadeDragProgress = .4f
            controller.updateViewState()
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isWithin(.01f).of(.2f)
        }

    @Test
    @DisableSceneContainer
    fun updateViewState_dragProgressOne_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()

        shadeViewStateProvider.lockscreenShadeDragProgress = 1f

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_disableSystemInfoFalse_viewShown() {
        controller.onViewAttached()
        updateStateToKeyguard()
        setDisableSystemInfo(false)

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_disableSystemInfoTrue_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()
        setDisableSystemInfo(true)

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_disableSystemIconsFalse_viewShown() {
        controller.onViewAttached()
        updateStateToKeyguard()
        setDisableSystemIcons(false)

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_disableSystemIconsTrue_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()
        setDisableSystemIcons(true)

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_dozingTrue_flagOff_viewHidden() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()

        controller.setDozing(true)

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateViewState_dozingFalse_flagOff_viewShown() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()

        controller.setDozing(false)

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @EnableSceneContainer
    fun updateViewState_flagOn_doesNothing() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()

        keyguardStatusBarView.visibility = View.GONE
        keyguardStatusBarView.alpha = 0.456f

        controller.updateViewState()

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.GONE)
        assertThat(keyguardStatusBarView.alpha).isEqualTo(0.456f)
    }

    @Test
    @EnableSceneContainer
    fun updateViewStateWithAlphaAndVis_flagOn_doesNothing() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()

        keyguardStatusBarView.visibility = View.GONE
        keyguardStatusBarView.alpha = 0.456f

        controller.updateViewState(0.789f, View.VISIBLE)

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.GONE)
        assertThat(keyguardStatusBarView.alpha).isEqualTo(0.456f)
    }

    @Test
    @EnableSceneContainer
    fun setAlpha_flagOn_doesNothing() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()

        keyguardStatusBarView.alpha = 0.456f

        controller.setAlpha(0.123f)

        assertThat(keyguardStatusBarView.alpha).isEqualTo(0.456f)
    }

    @Test
    @EnableSceneContainer
    fun setDozing_flagOn_doesNothing() {
        controller.init()
        controller.onViewAttached()
        updateStateToKeyguard()
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

        controller.setDozing(true)

        // setDozing(true) should typically cause the view to hide. But since the flag is on, we
        // should ignore these set dozing calls and stay the same visibility.
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun setAlpha_explicitAlpha_setsExplicitAlpha() {
        controller.onViewAttached()
        updateStateToKeyguard()

        controller.setAlpha(0.5f)

        assertThat(keyguardStatusBarView.alpha).isEqualTo(0.5f)
    }

    @Test
    @DisableSceneContainer
    fun setAlpha_explicitAlpha_thenMinusOneAlpha_setsAlphaBasedOnDefaultCriteria() {
        controller.onViewAttached()
        updateStateToKeyguard()

        controller.setAlpha(0.5f)
        controller.setAlpha(-1f)

        assertThat(keyguardStatusBarView.alpha).isGreaterThan(0)
        assertThat(keyguardStatusBarView.alpha).isNotEqualTo(0.5f)
    }

    // TODO(b/195442899): Add more tests for #updateViewState once CLs are finalized.
    @Test
    @DisableSceneContainer
    fun updateForHeadsUp_headsUpShouldBeVisible_viewHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()
        keyguardStatusBarView.visibility = View.VISIBLE

        shadeViewStateProvider.setShouldHeadsUpBeVisible(true)
        controller.updateForHeadsUp(/* animate= */ false)

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableSceneContainer
    fun updateForHeadsUp_headsUpShouldNotBeVisible_viewShown() {
        controller.onViewAttached()
        updateStateToKeyguard()

        // Start with the opposite state.
        shadeViewStateProvider.setShouldHeadsUpBeVisible(true)
        controller.updateForHeadsUp(/* animate= */ false)

        shadeViewStateProvider.setShouldHeadsUpBeVisible(false)
        controller.updateForHeadsUp(/* animate= */ false)

        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun testNewUserSwitcherDisablesAvatar_newUiOn() =
        testScope.runTest {
            // GIVEN the status bar user switcher chip is enabled
            kosmos.fakeUserRepository.isStatusBarUserChipEnabled = true

            // WHEN the controller is created
            controller = createController()

            // THEN keyguard status bar view avatar is disabled
            assertThat(keyguardStatusBarView.isKeyguardUserAvatarEnabled).isFalse()
        }

    @Test
    fun testNewUserSwitcherDisablesAvatar_newUiOff() {
        // GIVEN the status bar user switcher chip is disabled
        kosmos.fakeUserRepository.isStatusBarUserChipEnabled = false

        // WHEN the controller is created
        controller = createController()

        // THEN keyguard status bar view avatar is enabled
        assertThat(keyguardStatusBarView.isKeyguardUserAvatarEnabled).isTrue()
    }

    @Test
    fun testBlockedIcons_obeysSettingForVibrateIcon_settingOff() {
        val str = mContext.getString(com.android.internal.R.string.status_bar_volume)

        // GIVEN the setting is off
        whenever(secureSettings.getInt(Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON, 0))
            .thenReturn(0)

        // WHEN CollapsedStatusBarFragment builds the blocklist
        controller.updateBlockedIcons()

        // THEN status_bar_volume SHOULD be present in the list
        val contains = controller.blockedIcons.contains(str)
        Assert.assertTrue(contains)
    }

    @Test
    fun testBlockedIcons_obeysSettingForVibrateIcon_settingOn() {
        val str = mContext.getString(com.android.internal.R.string.status_bar_volume)

        // GIVEN the setting is ON
        whenever(
                secureSettings.getIntForUser(
                    Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON,
                    0,
                    UserHandle.USER_CURRENT,
                )
            )
            .thenReturn(1)

        // WHEN CollapsedStatusBarFragment builds the blocklist
        controller.updateBlockedIcons()

        // THEN status_bar_volume SHOULD NOT be present in the list
        val contains = controller.blockedIcons.contains(str)
        Assert.assertFalse(contains)
    }

    private fun updateStateToNotKeyguard() {
        updateStatusBarState(StatusBarState.SHADE)
    }

    private fun updateStateToKeyguard() {
        updateStatusBarState(StatusBarState.KEYGUARD)
    }

    private fun updateStatusBarState(state: Int) {
        kosmos.statusBarStateController.setState(state)
    }

    @Test
    @DisableSceneContainer
    fun animateKeyguardStatusBarIn_isDisabled_viewStillHidden() {
        controller.onViewAttached()
        updateStateToKeyguard()
        setDisableSystemInfo(true)
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)

        controller.animateKeyguardStatusBarIn()

        // Since we're disabled, we don't actually animate in and stay invisible
        assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
    }

    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun animateToGlanceableHub_v2Disabled_affectsAlpha() =
        testScope.runTest {
            controller.init()
            val transitionAlphaAmount = .5f

            updateStateToKeyguard()
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()
            controller.updateCommunalAlphaTransition(transitionAlphaAmount)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(transitionAlphaAmount)
        }

    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun animateToGlanceableHub_v2Disabled_alphaResetOnCommunalNotShowing() =
        testScope.runTest {
            controller.init()

            updateStateToKeyguard()

            // Verify status bar is fully visible on lockscreen
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)

            // Start transitioning to communal, and verify status bar is half visible
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()
            controller.updateCommunalAlphaTransition(.5f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(.5f)

            // Transition back to lockscreen, and verify status bar is set back to fully visible
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Blank)
            runCurrent()
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isNotEqualTo(.5f)
        }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun statusBar_isHidden_goneToGlanceableHubV2Disabled() =
        testScope.runTest {
            controller.init()

            // Keyguard is showing and start transitioning to communal
            updateStateToKeyguard()
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()

            val transitionSteps =
                listOf(
                    goneToGlanceableHubTransitionStep(0.0f, STARTED),
                    goneToGlanceableHubTransitionStep(.1f),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            // Verify status bar is not visible
            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    goneToGlanceableHubTransitionStep(1f),
                    goneToGlanceableHubTransitionStep(1f, FINISHED),
                ),
                testScope,
            )

            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun statusBar_fullyVisible_goneToGlanceableHubV2Enabled() =
        testScope.runTest {
            controller.init()

            // Keyguard is showing and start transitioning to communal
            updateStateToKeyguard()
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()

            // Verify status bar is fully visible
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    goneToGlanceableHubTransitionStep(0.0f, STARTED),
                    goneToGlanceableHubTransitionStep(.1f),
                ),
                testScope,
            )

            // The transition will not affect alpha and visibility
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    goneToGlanceableHubTransitionStep(1f),
                    goneToGlanceableHubTransitionStep(1f, FINISHED),
                ),
                testScope,
            )

            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun dragDownShadeOverGlanceableHub_v2Disabled_alphaRemainsZero() =
        testScope.runTest {
            controller.init()

            updateStateToKeyguard()

            // Verify status bar is fully visible on lockscreen
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)

            // Fully transition to communal, and verify status bar is invisible
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()
            controller.updateCommunalAlphaTransition(0f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)

            // Start dragging down shade, and verify status bar remains invisible
            shadeViewStateProvider.lockscreenShadeDragProgress = .1f
            controller.updateViewState()
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)
        }

    @DisableSceneContainer
    @EnableFlags(Flags.FLAG_GLANCEABLE_HUB_V2)
    fun animateToGlanceableHub_v2Enabled_alphaDoesNotChange() =
        testScope.runTest {
            controller.init()

            updateStateToKeyguard()

            // Verify status bar is fully visible on lockscreen
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)

            // Transition to communal halfway, and verify status bar remains fully visible
            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)
            runCurrent()
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
            assertThat(keyguardStatusBarView.alpha).isEqualTo(1f)
        }

    @Test
    @DisableSceneContainer
    fun lockscreenToDreaming_affectsAlpha() =
        testScope.runTest {
            controller.init()
            updateStateToKeyguard()

            val transitionSteps =
                listOf(
                    lockscreenToDreamTransitionStep(0.0f, STARTED),
                    lockscreenToDreamTransitionStep(.1f),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            assertThat(keyguardStatusBarView.alpha).isIn(Range.open(0f, 1f))
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    lockscreenToDreamTransitionStep(1f),
                    lockscreenToDreamTransitionStep(1f, FINISHED),
                ),
                testScope,
            )

            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun dreamingToLockscreen_affectsAlpha() =
        testScope.runTest {
            controller.init()
            updateStateToKeyguard()

            val transitionSteps =
                listOf(
                    dreamToLockscreenTransitionStep(0.0f, STARTED),
                    dreamToLockscreenTransitionStep(.3f),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            assertThat(keyguardStatusBarView.alpha).isIn(Range.open(0f, 1f))
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun dreamingToLockscreen_resetAlphaOnFinished() =
        testScope.runTest {
            controller.init()
            updateStateToKeyguard()

            val transitionSteps =
                listOf(
                    dreamToLockscreenTransitionStep(0.0f, STARTED),
                    dreamToLockscreenTransitionStep(.3f),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            val explicitAlpha = keyguardStatusBarView.alpha
            assertThat(explicitAlpha).isIn(Range.open(0f, 1f))

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(dreamToLockscreenTransitionStep(1f, FINISHED)),
                testScope,
            )

            assertThat(keyguardStatusBarView.alpha).isNotEqualTo(explicitAlpha)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun goneToDreaming_affectsAlpha() =
        testScope.runTest {
            controller.init()
            updateStateToKeyguard()

            val transitionSteps =
                listOf(goneToDreamTransitionStep(0.0f, STARTED), goneToDreamTransitionStep(.1f))
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            assertThat(keyguardStatusBarView.alpha).isEqualTo(0f)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun resetAlpha_onTransitionToDreamingInterrupted() =
        testScope.runTest {
            controller.init()
            updateStateToKeyguard()

            // Transition to dreaming
            var transitionSteps =
                listOf(
                    lockscreenToDreamTransitionStep(0.0f, STARTED),
                    lockscreenToDreamTransitionStep(.1f),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            val explicitAlphaByDream = keyguardStatusBarView.alpha
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.VISIBLE)

            // Transition is interrupted and goes to AOD
            controller.setDozing(true)
            transitionSteps =
                listOf(
                    lockscreenToDreamTransitionStep(.1f, CANCELED),
                    dreamToAodTransitionStep(0.1f, STARTED),
                    dreamToAodTransitionStep(.5f),
                    dreamToAodTransitionStep(1f, FINISHED),
                )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(transitionSteps, testScope)

            assertThat(keyguardStatusBarView.alpha).isNotEqualTo(explicitAlphaByDream)
            assertThat(keyguardStatusBarView.visibility).isEqualTo(View.INVISIBLE)
        }

    /**
     * Calls [com.android.keyguard.KeyguardUpdateMonitorCallback.onFinishedGoingToSleep] to ensure
     * values are updated properly.
     */
    private fun onFinishedGoingToSleep() {
        val keyguardUpdateCallbackCaptor =
            ArgumentCaptor.forClass(KeyguardUpdateMonitorCallback::class.java)
        Mockito.verify(keyguardUpdateMonitor)
            .registerCallback(keyguardUpdateCallbackCaptor.capture())
        val callback = keyguardUpdateCallbackCaptor.value

        callback.onFinishedGoingToSleep(0)
    }

    private fun setDisableSystemInfo(disabled: Boolean) {
        val callback = commandQueueCallback
        val disabled1 = if (disabled) StatusBarManager.DISABLE_SYSTEM_INFO else 0
        callback.disable(mContext.displayId, disabled1, 0, false)
    }

    private fun setDisableSystemIcons(disabled: Boolean) {
        val callback = commandQueueCallback
        val disabled2 = if (disabled) StatusBarManager.DISABLE2_SYSTEM_ICONS else 0
        callback.disable(mContext.displayId, 0, disabled2, false)
    }

    private val commandQueueCallback: CommandQueue.Callbacks
        get() {
            val captor = ArgumentCaptor.forClass(CommandQueue.Callbacks::class.java)
            Mockito.verify(commandQueue).addCallback(captor.capture())
            return captor.value
        }

    private fun runAllScheduled() {
        backgroundExecutor.runAllReady()
        fakeExecutor.runAllReady()
    }

    private class TestShadeViewStateProvider : ShadeViewStateProvider {
        override var panelViewExpandedHeight: Float = 100f
        private var mShouldHeadsUpBeVisible = false
        override var lockscreenShadeDragProgress: Float = 0f

        override fun shouldHeadsUpBeVisible(): Boolean {
            return mShouldHeadsUpBeVisible
        }

        fun setShouldHeadsUpBeVisible(shouldHeadsUpBeVisible: Boolean) {
            this.mShouldHeadsUpBeVisible = shouldHeadsUpBeVisible
        }
    }

    private fun lockscreenToDreamTransitionStep(
        value: Float,
        transitionState: TransitionState = RUNNING,
    ) =
        TransitionStep(
            from = KeyguardState.LOCKSCREEN,
            to = KeyguardState.DREAMING,
            value = value,
            transitionState = transitionState,
            ownerName = "KeyguardStatusBarViewControllerTest",
        )

    private fun dreamToLockscreenTransitionStep(
        value: Float,
        transitionState: TransitionState = RUNNING,
    ) =
        TransitionStep(
            from = KeyguardState.DREAMING,
            to = KeyguardState.LOCKSCREEN,
            value = value,
            transitionState = transitionState,
            ownerName = "KeyguardStatusBarViewControllerTest",
        )

    private fun goneToDreamTransitionStep(
        value: Float,
        transitionState: TransitionState = RUNNING,
    ) =
        TransitionStep(
            from = KeyguardState.GONE,
            to = KeyguardState.DREAMING,
            value = value,
            transitionState = transitionState,
            ownerName = "KeyguardStatusBarViewControllerTest",
        )

    private fun dreamToAodTransitionStep(value: Float, transitionState: TransitionState = RUNNING) =
        TransitionStep(
            from = KeyguardState.DREAMING,
            to = KeyguardState.AOD,
            value = value,
            transitionState = transitionState,
            ownerName = "KeyguardStatusBarViewControllerTest",
        )

    private fun goneToGlanceableHubTransitionStep(
        value: Float,
        transitionState: TransitionState = RUNNING,
    ) =
        TransitionStep(
            from = KeyguardState.GONE,
            to = KeyguardState.GLANCEABLE_HUB,
            value = value,
            transitionState = transitionState,
            ownerName = "KeyguardStatusBarViewControllerTest",
        )
}
