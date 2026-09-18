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

package com.android.systemui.communal

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.Flags.COMMUNAL_SERVICE_ENABLED
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class CommunalSceneStartableTest(flags: FlagsParameterization) : SysuiTestCase() {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CommunalSceneStartable(
                communalSettingsInteractor = communalSettingsInteractor,
                communalSceneInteractor = communalSceneInteractor,
                notificationShadeWindowController = notificationShadeWindowController,
                bgScope = applicationCoroutineScope,
                mainDispatcher = testDispatcher,
            )
        }

    @Before
    fun setUp() {
        with(kosmos) {
            fakeFeatureFlagsClassic.set(COMMUNAL_SERVICE_ENABLED, true)

            underTest.start()

            // Make communal available so that communalInteractor.desiredScene accurately reflects
            // scene changes instead of just returning Blank.
            runBlocking { setCommunalAvailable(true) }
        }
    }

    @Test
    fun hubShowing_whenSceneChanges() =
        kosmos.runTest {
            verify(notificationShadeWindowController).setGlanceableHubShowing(false)
            clearInvocations(notificationShadeWindowController)

            setScene(CommunalScenes.Communal, Scenes.Communal)
            verify(notificationShadeWindowController).setGlanceableHubShowing(true)
            clearInvocations(notificationShadeWindowController)

            // Switch away from communal
            setScene(CommunalScenes.Blank, Scenes.Lockscreen)
            verify(notificationShadeWindowController).setGlanceableHubShowing(false)
        }

    private suspend fun Kosmos.setScene(communalScene: SceneKey, scene: SceneKey) {
        if (SceneContainerFlag.isEnabled) {
            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(scene))
            )
        } else {
            communalSceneInteractor.snapToScene(
                newScene = communalScene,
                loggingReason = "test",
                delayMillis = 0,
            )
        }
    }
}
