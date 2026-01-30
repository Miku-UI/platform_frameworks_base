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

package com.android.systemui.communal.domain.interactor

import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_MAIN
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.repository.fakeCommunalPrefsRepository
import com.android.systemui.communal.data.repository.forceCommunalV2FlagState
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class HubOnboardingInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private lateinit var kosmos: Kosmos
    private lateinit var sceneInteractor: SceneInteractor
    private lateinit var underTest: HubOnboardingInteractor

    @Before
    fun setUp() {
        kosmos = testKosmos().useUnconfinedTestDispatcher()
        sceneInteractor = kosmos.sceneInteractor

        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_glanceableHubEnabled,
            true,
        )
    }

    private fun initializeUnderTest(isV2Enabled: Boolean) {
        kosmos.forceCommunalV2FlagState = isV2Enabled
        underTest = kosmos.hubOnboardingInteractor
    }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun setHubOnboardingDismissed() =
        kosmos.runTest {
            initializeUnderTest(isV2Enabled = true)

            setSelectedUser(MAIN_USER)
            val isHubOnboardingDismissed by
                collectLastValue(fakeCommunalPrefsRepository.isHubOnboardingDismissed(MAIN_USER))

            underTest.setHubOnboardingDismissed()

            assertThat(isHubOnboardingDismissed).isTrue()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun shouldShowHubOnboarding_falseWhenDismissed() =
        kosmos.runTest {
            initializeUnderTest(isV2Enabled = true)

            setSelectedUser(MAIN_USER)
            val shouldShowHubOnboarding by collectLastValue(underTest.shouldShowHubOnboarding)

            fakeCommunalPrefsRepository.setHubOnboardingDismissed(MAIN_USER)

            assertThat(shouldShowHubOnboarding).isFalse()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun shouldShowHubOnboarding_falseWhenNotIdleOnCommunal() =
        kosmos.runTest {
            initializeUnderTest(isV2Enabled = true)

            setSelectedUser(MAIN_USER)
            val shouldShowHubOnboarding by collectLastValue(underTest.shouldShowHubOnboarding)

            assertThat(shouldShowHubOnboarding).isFalse()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun shouldShowHubOnboarding_trueWhenIdleOnCommunal() =
        kosmos.runTest {
            initializeUnderTest(isV2Enabled = true)

            setSelectedUser(MAIN_USER)
            val shouldShowHubOnboarding by collectLastValue(underTest.shouldShowHubOnboarding)

            // Change to Communal scene.
            if (SceneContainerFlag.isEnabled) {
                setIdleScene(Scenes.Communal)
            } else {
                @Suppress("DEPRECATION")
                kosmos.communalSceneInteractor.snapToScene(
                    CommunalScenes.Communal,
                    "test: set idle on legacy communal",
                )
            }

            assertThat(shouldShowHubOnboarding).isTrue()
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun shouldShowHubOnboarding_falseWhenFlagDisabled() =
        kosmos.runTest {
            initializeUnderTest(isV2Enabled = false)

            setSelectedUser(MAIN_USER)
            val shouldShowHubOnboarding by collectLastValue(underTest.shouldShowHubOnboarding)

            // Change to Communal scene.
            setIdleScene(Scenes.Communal)

            assertThat(shouldShowHubOnboarding).isFalse()
        }

    private fun setIdleScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "test")
        val transitionState =
            MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(scene))
        sceneInteractor.setTransitionState(transitionState)
    }

    private suspend fun setSelectedUser(user: UserInfo) {
        with(kosmos.fakeUserRepository) {
            setUserInfos(listOf(user))
            setSelectedUserInfo(user)
        }
        kosmos.fakeUserTracker.set(userInfos = listOf(user), selectedUserIndex = 0)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        val MAIN_USER = UserInfo(0, "main", FLAG_MAIN)
    }
}
