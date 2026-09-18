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

package com.android.systemui.scene

import android.app.ActivityManager
import android.content.Context
import com.android.compose.animation.scene.SceneKey
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.CoreStartable
import com.android.systemui.Flags.startOnGoneSceneIfNoLockscreen
import com.android.systemui.notifications.ui.composable.NotificationsShadeSessionModule
import com.android.systemui.res.R
import com.android.systemui.scene.domain.SceneDomainModule
import com.android.systemui.scene.domain.interactor.DualShadeEducationInteractorModule
import com.android.systemui.scene.domain.interactor.WindowRootViewVisibilityInteractor
import com.android.systemui.scene.domain.resolver.HomeSceneFamilyResolverModule
import com.android.systemui.scene.domain.startable.KeyguardStateCallbackStartable
import com.android.systemui.scene.domain.startable.SceneContainerStartable
import com.android.systemui.scene.domain.startable.StatusBarStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.SceneContainerTransitions
import com.android.systemui.shade.shared.flag.DualShadeFlag
import com.android.systemui.statusbar.quickactions.popups.StatusBarPopupChips
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Scene framework Dagger module suitable for AOSP. */
@Module(
    includes =
        [
            BouncerOverlayModule::class,
            CommunalSceneModule::class,
            DreamSceneModule::class,
            DualShadeEducationInteractorModule::class,
            EmptySceneModule::class,
            GoneSceneModule::class,
            LockscreenSceneModule::class,
            OccludedSceneModule::class,
            QuickSettingsSceneModule::class,
            ShadeSceneModule::class,
            QuickActionsOverlayModule::class,
            QuickSettingsShadeOverlayModule::class,
            NotificationsShadeOverlayModule::class,
            NotificationsShadeSessionModule::class,
            SceneDomainModule::class,

            // List SceneResolver modules for supported SceneFamilies
            HomeSceneFamilyResolverModule::class,
        ]
)
interface SceneContainerFrameworkModule {

    @Binds
    @IntoMap
    @ClassKey(SceneContainerStartable::class)
    fun containerStartable(impl: SceneContainerStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(StatusBarStartable::class)
    fun statusBarStartable(impl: StatusBarStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(KeyguardStateCallbackStartable::class)
    fun keyguardStateCallbackStartable(impl: KeyguardStateCallbackStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(WindowRootViewVisibilityInteractor::class)
    fun bindWindowRootViewVisibilityInteractor(
        impl: WindowRootViewVisibilityInteractor
    ): CoreStartable

    companion object {

        private fun determineInitialScene(lockPatternUtils: LockPatternUtils): SceneKey {
            if (!startOnGoneSceneIfNoLockscreen()) {
                return Scenes.Lockscreen
            }

            val userId = ActivityManager.getCurrentUser()
            if (lockPatternUtils.isLockScreenDisabled(userId)) {
                return Scenes.Gone
            }

            return Scenes.Lockscreen
        }

        @Provides
        fun containerConfig(
            context: Context,
            lockPatternUtils: LockPatternUtils,
        ): SceneContainerConfig {
            // Include the shade and quick settings scenes if:
            // 1. Dual Shade is disabled, or
            // 2. The config explicitly requires them.
            // It helps to improve SysUI performance when Dual Shade is active, as Dual Shade uses
            // two overlays instead of these two scenes and those two scenes are set up with
            // alwaysCompose=true which makes their content compose even when they're not visible
            // (which is a separate performance improvement).
            // TODO(b/485637607): We currently lack a way to determine Dual Shade status purely via
            // config. As a workaround, we set `config_includeQSAndShadeScenes` to false to manually
            // disable these scenes on specific large-screen form factors (like desktop).
            // Revisit once a robust configuration check is available.
            val includeShadeAndQSScenes =
                !DualShadeFlag.isEnabled ||
                    context.resources.getBoolean(R.bool.config_includeQSAndShadeScenes)

            return SceneContainerConfig(
                // Note that this list is in z-order. The first one is the bottom-most and the last
                // one is top-most.
                sceneKeys =
                    listOfNotNull(
                        Scenes.Gone,
                        Scenes.Communal,
                        Scenes.Dream,
                        Scenes.Occluded,
                        Scenes.Lockscreen,
                        Scenes.QuickSettings.takeIf { includeShadeAndQSScenes },
                        Scenes.Shade.takeIf { includeShadeAndQSScenes },
                    ),
                initialSceneKey = determineInitialScene(lockPatternUtils),
                overlayKeys =
                    listOfNotNull(
                        Overlays.NotificationsShade,
                        Overlays.QuickSettingsShade,
                        Overlays.QuickActions.takeIf { StatusBarPopupChips.isEnabled },
                        Overlays.Bouncer,
                    ),
                navigationDistances =
                    buildMap {
                        putAll(
                            arrayOf(
                                Scenes.Gone to 0,
                                Scenes.Lockscreen to 0,
                                Scenes.Communal to 1,
                                Scenes.Dream to 2,
                                Scenes.Occluded to 3,
                            )
                        )

                        if (includeShadeAndQSScenes) {
                            putAll(arrayOf(Scenes.Shade to 4, Scenes.QuickSettings to 5))
                        }
                    },
                transitionsBuilder = SceneContainerTransitions(),
            )
        }
    }
}
