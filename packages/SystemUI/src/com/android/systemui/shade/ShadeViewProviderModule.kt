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

package com.android.systemui.shade

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import androidx.constraintlayout.motion.widget.MotionLayout
import com.android.compose.animation.scene.SceneKey
import com.android.keyguard.logging.ScrimLogger
import com.android.systemui.Flags.groupedPrivacyChip
import com.android.systemui.biometrics.AuthRippleView
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.view.KeyguardRootView
import com.android.systemui.keyguard.ui.viewmodel.AuthRippleScrimViewModel
import com.android.systemui.privacy.AbstractOngoingPrivacyChip
import com.android.systemui.privacy.ui.view.ComposeOngoingPrivacyChip
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.SceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.scene.ui.view.SceneJankMonitor
import com.android.systemui.scene.ui.view.SceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.view.SceneWindowRootView
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.scene.ui.view.WindowRootViewKeyEventHandler
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import com.android.systemui.statusbar.LightRevealScrim
import com.android.systemui.statusbar.NotificationInsetsController
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.TapAgainView
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.window.ui.BlurChoreographerModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Provider

/** Module for providing views related to the shade. */
@Module(includes = [BlurChoreographerModule::class])
abstract class ShadeViewProviderModule {

    @Binds
    @SysUISingleton
    // TODO(b/277762009): Only allow this view's binder to inject the view.
    abstract fun bindsNotificationScrollView(
        notificationStackScrollLayout: NotificationStackScrollLayout
    ): NotificationScrollView

    companion object {
        const val SHADE_HEADER = "large_screen_shade_header"
        const val LOW_LIGHT_CONTAINER = "low_light_animation_container"

        @SuppressLint("InflateParams") // Root views don't have parents.
        @Provides
        @SysUISingleton
        fun providesWindowRootView(
            @ShadeDisplayAware layoutInflater: LayoutInflater,
            viewModelFactory: SceneContainerViewModel.Factory,
            containerConfigProvider: Provider<SceneContainerConfig>,
            scenesProvider: Provider<Set<@JvmSuppressWildcards Scene>>,
            overlaysProvider: Provider<Set<@JvmSuppressWildcards Overlay>>,
            layoutInsetController: NotificationInsetsController,
            sceneDataSourceDelegator: Provider<SceneDataSourceDelegator>,
            sceneJankMonitorFactory: SceneJankMonitor.Factory,
            sceneTransitionLatencyMonitor: SceneTransitionLatencyMonitor,
            windowRootViewKeyEventHandler: WindowRootViewKeyEventHandler,
            tintedIconManagerFactory: TintedIconManager.Factory,
            authRippleViewModelFactory: AuthRippleScrimViewModel.Factory,
        ): WindowRootView {
            return if (SceneContainerFlag.isEnabled) {
                checkNoSceneDuplicates(scenesProvider.get())
                val sceneWindowRootView =
                    layoutInflater.inflate(R.layout.scene_window_root, null) as SceneWindowRootView
                sceneWindowRootView.init(
                    viewModelFactory = viewModelFactory,
                    containerConfig = containerConfigProvider.get(),
                    sharedNotificationContainer =
                        sceneWindowRootView.requireViewById(R.id.shared_notification_container),
                    scenes = scenesProvider.get(),
                    overlays = overlaysProvider.get(),
                    layoutInsetController = layoutInsetController,
                    sceneDataSourceDelegator = sceneDataSourceDelegator.get(),
                    sceneJankMonitorFactory = sceneJankMonitorFactory,
                    sceneTransitionLatencyMonitor = sceneTransitionLatencyMonitor,
                    windowRootViewKeyEventHandler = windowRootViewKeyEventHandler,
                    tintedIconManagerFactory = tintedIconManagerFactory,
                    authRippleViewModelFactory = authRippleViewModelFactory,
                )
                sceneWindowRootView
            } else {
                layoutInflater.inflate(R.layout.super_notification_shade, null)
            }
                as WindowRootView?
                ?: throw IllegalStateException("Window root view could not be properly inflated")
        }

        // TODO(b/277762009): Do something similar to
        //  {@link StatusBarWindowModule.InternalWindowView} so that only
        //  {@link NotificationShadeWindowViewController} can inject this view.
        @Provides
        @SysUISingleton
        fun providesNotificationShadeWindowView(root: WindowRootView): NotificationShadeWindowView {
            if (SceneContainerFlag.isEnabled) {
                return root.requireViewById(R.id.legacy_window_root)
            }
            return root as NotificationShadeWindowView?
                ?: throw IllegalStateException("root view not a NotificationShadeWindowView")
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        fun providesNotificationStackScrollLayout(
            notificationShadeWindowView: NotificationShadeWindowView
        ): NotificationStackScrollLayout {
            return notificationShadeWindowView.requireViewById(R.id.notification_stack_scroller)
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        fun providesNotificationPanelView(
            notificationShadeWindowView: NotificationShadeWindowView
        ): NotificationPanelView {
            return notificationShadeWindowView.requireViewById(R.id.notification_panel)
        }

        @Provides
        @SysUISingleton
        fun providesLightRevealScrim(
            notificationShadeWindowView: NotificationShadeWindowView,
            scrimLogger: ScrimLogger,
        ): LightRevealScrim {
            val scrim =
                notificationShadeWindowView.requireViewById<LightRevealScrim>(
                    R.id.light_reveal_scrim
                )
            scrim.scrimLogger = scrimLogger
            return scrim
        }

        @Provides
        @SysUISingleton
        fun providesKeyguardRootView(
            notificationShadeWindowView: NotificationShadeWindowView
        ): KeyguardRootView {
            return notificationShadeWindowView.requireViewById(R.id.keyguard_root_view)
        }

        @Provides
        @SysUISingleton
        fun providesSharedNotificationContainer(
            notificationShadeWindowView: NotificationShadeWindowView
        ): SharedNotificationContainer {
            return notificationShadeWindowView.requireViewById(R.id.shared_notification_container)
        }

        @Provides
        @Named(LOW_LIGHT_CONTAINER)
        @SysUISingleton
        fun providesLowLightAnimationContainer(
            notificationShadeWindowView: NotificationShadeWindowView
        ): FrameLayout {
            return notificationShadeWindowView.requireViewById(R.id.low_light_animation_container)
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        fun providesAuthRippleView(
            notificationShadeWindowView: NotificationShadeWindowView
        ): AuthRippleView? {
            return notificationShadeWindowView.requireViewById(R.id.auth_ripple)
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        fun providesTapAgainView(notificationPanelView: NotificationPanelView): TapAgainView {
            return notificationPanelView.requireViewById(R.id.shade_falsing_tap_again)
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        fun providesNotificationsQuickSettingsContainer(
            notificationShadeWindowView: NotificationShadeWindowView
        ): NotificationsQuickSettingsContainer {
            return notificationShadeWindowView.requireViewById(R.id.notification_container_parent)
        }

        // TODO(b/277762009): Only allow this view's controller to inject the view. See above.
        @Provides
        @SysUISingleton
        @Named(SHADE_HEADER)
        fun providesShadeHeaderView(
            notificationShadeWindowView: NotificationShadeWindowView
        ): MotionLayout {
            val stub = notificationShadeWindowView.requireViewById<ViewStub>(R.id.qs_header_stub)
            val layoutId = R.layout.combined_qs_header
            stub.layoutResource = layoutId
            return stub.inflate() as MotionLayout
        }

        @Provides
        @SysUISingleton
        fun providesCombinedShadeHeadersConstraintManager(): CombinedShadeHeadersConstraintManager {
            return CombinedShadeHeadersConstraintManagerImpl
        }

        @Provides
        @SysUISingleton
        @Named(SHADE_HEADER)
        fun providesOngoingPrivacyChip(
            @Named(SHADE_HEADER) header: MotionLayout
        ): AbstractOngoingPrivacyChip {
            val legacyChips: AbstractOngoingPrivacyChip = header.requireViewById(R.id.privacy_chip)
            if (groupedPrivacyChip()) {
                val newChips = ComposeOngoingPrivacyChip(legacyChips.context)
                (legacyChips.parent as ViewGroup).apply {
                    removeAllViews()
                    addView(newChips, legacyChips.layoutParams)
                }
                return newChips
            } else {
                return legacyChips
            }
        }

        @Provides
        @SysUISingleton
        @Named(SHADE_HEADER)
        fun providesStatusIconContainer(
            @Named(SHADE_HEADER) header: MotionLayout
        ): StatusIconContainer {
            return header.requireViewById(R.id.statusIcons)
        }

        private fun checkNoSceneDuplicates(scenes: Set<Scene>) {
            val keys = mutableSetOf<SceneKey>()
            val duplicates = mutableSetOf<SceneKey>()
            scenes
                .map { it.key }
                .forEach { sceneKey ->
                    if (keys.contains(sceneKey)) {
                        duplicates.add(sceneKey)
                    } else {
                        keys.add(sceneKey)
                    }
                }

            check(duplicates.isEmpty()) { "Duplicate scenes detected: $duplicates" }
        }
    }
}
