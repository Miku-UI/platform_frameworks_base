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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LockscreenBehindScrimViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest = kosmos.lockscreenBehindScrimViewModelFactory.create()

    @Test
    fun isVisible_whenIdleOnLockscreen_true() =
        kosmos.runTest {
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            assertThat(sceneInteractor.transitionState)
                .isEqualTo(TransitionState.Idle(Scenes.Lockscreen))

            assertThat(underTest.isVisible).isTrue()
        }

    @Test
    fun isVisible_whenTransitioningIntoLockscreen_true() =
        kosmos.runTest {
            sceneInteractor.snapToScene(Scenes.Shade, "")
            assertThat(sceneInteractor.transitionState)
                .isEqualTo(TransitionState.Idle(Scenes.Shade))
            sceneInteractor.startTransitionImmediately(
                MidwayTransition(fromScene = Scenes.Shade, toScene = Scenes.Lockscreen)
            )
            assertThat(
                    sceneInteractor.transitionState.isTransitioning(
                        from = Scenes.Shade,
                        to = Scenes.Lockscreen,
                    )
                )
                .isTrue()

            assertThat(underTest.isVisible).isTrue()
        }

    @Test
    fun isVisible_whenTransitioningAwayFromLockscreen_true() =
        kosmos.runTest {
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            assertThat(sceneInteractor.transitionState)
                .isEqualTo(TransitionState.Idle(Scenes.Lockscreen))
            sceneInteractor.startTransitionImmediately(
                MidwayTransition(fromScene = Scenes.Lockscreen, toScene = Scenes.Shade)
            )
            assertThat(
                    sceneInteractor.transitionState.isTransitioning(
                        from = Scenes.Lockscreen,
                        to = Scenes.Shade,
                    )
                )
                .isTrue()

            assertThat(underTest.isVisible).isTrue()
        }

    @Test
    fun isVisible_whenTransitioningAwayFromLockscreenToOccluded_false() =
        kosmos.runTest {
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            assertThat(sceneInteractor.transitionState)
                .isEqualTo(TransitionState.Idle(Scenes.Lockscreen))
            sceneInteractor.startTransitionImmediately(
                MidwayTransition(fromScene = Scenes.Lockscreen, toScene = Scenes.Occluded)
            )
            assertThat(
                    sceneInteractor.transitionState.isTransitioning(
                        from = Scenes.Lockscreen,
                        to = Scenes.Occluded,
                    )
                )
                .isTrue()

            assertThat(underTest.isVisible).isFalse()
        }

    private class MidwayTransition(
        fromScene: SceneKey,
        toScene: SceneKey,
        override val isInitiatedByUserInput: Boolean = false,
        override val isUserInputOngoing: Boolean = false,
    ) : TransitionState.Transition.ChangeScene(fromScene = fromScene, toScene = toScene) {
        override val currentScene: SceneKey = fromScene
        override val progress: Float = 0.5f
        override val progressVelocity: Float = 0f

        override suspend fun run() {}

        override fun freezeAndAnimateToCurrentState() {}
    }
}
