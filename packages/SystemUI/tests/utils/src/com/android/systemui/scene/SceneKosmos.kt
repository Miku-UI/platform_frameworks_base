package com.android.systemui.scene

import android.content.res.mainResources
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.accessibility.domain.interactor.accessibilityInteractor
import com.android.systemui.classifier.domain.interactor.falsingInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.desktop.domain.interactor.desktopInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.haptics.msdl.msdlPlayer
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.keyguard.ui.viewmodel.BurnInMovementState
import com.android.systemui.keyguard.ui.viewmodel.aodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.lightRevealScrimViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.qs.panels.ui.viewmodel.animateQsTilesViewModelFactory
import com.android.systemui.scene.domain.interactor.onBootTransitionInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.logger.sceneLogger
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.FakeOverlay
import com.android.systemui.scene.ui.composable.ConstantSceneContainerTransitionsBuilder
import com.android.systemui.scene.ui.composable.SceneContainerTransitions
import com.android.systemui.scene.ui.viewmodel.SceneContainerHapticsViewModel
import com.android.systemui.scene.ui.viewmodel.SceneContainerToastDisplayer
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import com.android.systemui.scene.ui.viewmodel.SceneTransitionBlurViewModel
import com.android.systemui.scene.ui.viewmodel.dualShadeEducationalTooltipsViewModelFactory
import com.android.systemui.scene.ui.viewmodel.toBouncerTransitionViewModelFactory
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.statusbar.domain.interactor.remoteInputInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationContainerInteractor
import com.android.systemui.statusbar.ui.systemBarUtilsState
import com.android.systemui.wallpapers.domain.interactor.wallpaperInteractorFaked
import com.android.systemui.wallpapers.ui.viewmodel.wallpaperViewModel
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import com.android.systemui.window.logging.blurLogger
import com.android.systemui.window.ui.FakeBlurChoreographer
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.mock

var Kosmos.sceneKeys by Fixture {
    listOf(
        Scenes.Gone,
        Scenes.Communal,
        Scenes.Dream,
        Scenes.Occluded,
        Scenes.Lockscreen,
        Scenes.QuickSettings,
        Scenes.Shade,
    )
}

var Kosmos.sceneNavigationDistances by Fixture {
    mapOf(
        Scenes.Gone to 0,
        Scenes.Lockscreen to 0,
        Scenes.Communal to 1,
        Scenes.Dream to 2,
        Scenes.Occluded to 3,
        Scenes.Shade to 4,
        Scenes.QuickSettings to 5,
    )
}

val Kosmos.initialSceneKey by Fixture { Scenes.Lockscreen }

var Kosmos.overlayKeys by Fixture {
    listOf(
        Overlays.NotificationsShade,
        Overlays.QuickSettingsShade,
        Overlays.Bouncer,
        Overlays.QuickActions,
    )
}

val Kosmos.fakeOverlaysByKeys by Fixture { overlayKeys.associateWith { FakeOverlay(it) } }

val Kosmos.fakeOverlays by Fixture { fakeOverlaysByKeys.values.toSet() }

val Kosmos.overlays by Fixture { fakeOverlays }

val Kosmos.sceneTransitionsBuilder by Fixture { ConstantSceneContainerTransitionsBuilder() }
val Kosmos.sceneContainerTransitions by Fixture { SceneContainerTransitions() }

var Kosmos.sceneContainerConfig by Fixture {
    SceneContainerConfig(
        sceneKeys = sceneKeys,
        initialSceneKey = initialSceneKey,
        overlayKeys = overlayKeys,
        navigationDistances = sceneNavigationDistances,
        transitionsBuilder = sceneTransitionsBuilder,
    )
}

val Kosmos.transitionState by Fixture {
    MutableStateFlow<ObservableTransitionState>(
        ObservableTransitionState.Idle(sceneContainerConfig.initialSceneKey)
    )
}

val Kosmos.sceneContainerViewModel by Fixture {
    sceneContainerViewModelFactory.create {}.apply { setTransitionState(transitionState) }
}

val Kosmos.fakeBlurChoreographer by Fixture { FakeBlurChoreographer() }

val Kosmos.sceneTransitionBlurViewModel by Fixture {
    SceneTransitionBlurViewModel(
        wallpaperInteractor = wallpaperInteractorFaked,
        communalSettingsInteractor = communalSettingsInteractor,
        windowRootViewBlurInteractor = windowRootViewBlurInteractor,
        keyguardTransitionInteractor = keyguardTransitionInteractor,
        blurConfig = blurConfig,
        blurChoreographer = fakeBlurChoreographer,
        shadeInteractor = shadeInteractor,
        deviceEntryInteractor = deviceEntryInteractor,
        logger = blurLogger,
    )
}

val Kosmos.sceneTransitionBlurViewModelFactory by Fixture {
    object : SceneTransitionBlurViewModel.Factory {
        override fun create(): SceneTransitionBlurViewModel {
            return sceneTransitionBlurViewModel
        }
    }
}

val Kosmos.sceneContainerViewModelFactory by Fixture {
    object : SceneContainerViewModel.Factory {
        override fun create(
            motionEventHandlerReceiver: (SceneContainerViewModel.MotionEventHandler?) -> Unit
        ): SceneContainerViewModel =
            SceneContainerViewModel(
                resources = mainResources,
                systemBarUtilsState = systemBarUtilsState,
                sceneInteractor = sceneInteractor,
                desktopInteractor = desktopInteractor,
                deviceUnlockedInteractor = deviceUnlockedInteractor,
                falsingInteractor = falsingInteractor,
                powerInteractor = powerInteractor,
                shadeModeInteractor = shadeModeInteractor,
                remoteInputInteractor = remoteInputInteractor,
                logger = sceneLogger,
                hapticsViewModelFactory = sceneContainerHapticsViewModelFactory,
                motionEventHandlerReceiver = motionEventHandlerReceiver,
                lightRevealScrim = lightRevealScrimViewModel,
                wallpaperViewModel = wallpaperViewModel,
                keyguardInteractor = keyguardInteractor,
                keyguardTransitionInteractor = keyguardTransitionInteractor,
                onBootTransitionInteractor = onBootTransitionInteractor,
                notificationContainerInteractor = notificationContainerInteractor,
                dualShadeEducationalTooltipsViewModelFactory =
                    dualShadeEducationalTooltipsViewModelFactory,
                animateQsTilesViewModelFactory = animateQsTilesViewModelFactory,
                sceneTransitionBlurViewModelFactory = sceneTransitionBlurViewModelFactory,
                toastDisplayer = { sceneContainerToastDisplayer },
                toBouncerTransitionViewModelFactory = toBouncerTransitionViewModelFactory,
                burnInMovementFactory =
                    object : BurnInMovementState.Factory {
                        override fun create() = BurnInMovementState(aodBurnInViewModel)
                    },
                accessibilityInteractor = accessibilityInteractor,
            )
    }
}

val Kosmos.sceneContainerHapticsViewModelFactory by Fixture {
    object : SceneContainerHapticsViewModel.Factory {
        override fun create(): SceneContainerHapticsViewModel {
            return SceneContainerHapticsViewModel(
                sceneInteractor = sceneInteractor,
                shadeInteractor = shadeInteractor,
                msdlPlayer = msdlPlayer,
            )
        }
    }
}

private val Kosmos.sceneContainerToastDisplayer by Fixture { mock<SceneContainerToastDisplayer>() }
