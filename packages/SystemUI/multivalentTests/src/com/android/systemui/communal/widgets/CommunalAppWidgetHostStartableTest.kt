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

package com.android.systemui.communal.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.UserInfo
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING
import com.android.systemui.Flags.restrictCommunalAppWidgetHostListening
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.repository.fakeCommunalWidgetRepository
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.setCommunalEnabled
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.shared.model.fakeGlanceableHubMultiUserHelper
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class CommunalAppWidgetHostStartableTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Mock private lateinit var appWidgetHost: CommunalAppWidgetHost
    @Mock private lateinit var communalWidgetHost: CommunalWidgetHost

    private lateinit var appWidgetIdToRemove: MutableSharedFlow<Int>

    private lateinit var communalInteractorSpy: CommunalInteractor
    private lateinit var underTest: CommunalAppWidgetHostStartable

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        kosmos.fakeUserRepository.setUserInfos(listOf(MAIN_USER_INFO, USER_INFO_WORK))
        kosmos.fakeFeatureFlagsClassic.set(Flags.COMMUNAL_SERVICE_ENABLED, true)
        mSetFlagsRule.enableFlags(FLAG_COMMUNAL_HUB)

        appWidgetIdToRemove = MutableSharedFlow()
        whenever(appWidgetHost.appWidgetIdToRemove).thenReturn(appWidgetIdToRemove)
        communalInteractorSpy = spy(kosmos.communalInteractor)

        underTest =
            with(kosmos) {
                CommunalAppWidgetHostStartable(
                    { appWidgetHost },
                    { communalWidgetHost },
                    { communalInteractorSpy },
                    { communalSettingsInteractor },
                    { keyguardInteractor },
                    { fakeUserTracker },
                    applicationCoroutineScope,
                    testDispatcher,
                    { mockGlanceableHubWidgetManager },
                    fakeGlanceableHubMultiUserHelper,
                )
            }
    }

    @Test
    fun editModeShowingStartsAppWidgetHost() =
        kosmos.runTest {
            setCommunalAvailable(false)
            communalInteractor.setEditModeOpen(true)
            verify(appWidgetHost, never()).startListening()

            underTest.start()
            runCurrent()

            verify(appWidgetHost).startListening()
            verify(appWidgetHost, never()).stopListening()

            communalInteractor.setEditModeOpen(false)
            runCurrent()

            verify(appWidgetHost).stopListening()
        }

    @Test
    @DisableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    fun communalAvailableStartsAppWidgetHost() =
        kosmos.runTest {
            setCommunalAvailable(true)
            communalInteractor.setEditModeOpen(false)
            verify(appWidgetHost, never()).startListening()

            underTest.start()
            runCurrent()

            verify(appWidgetHost).startListening()
            verify(appWidgetHost, never()).stopListening()

            setCommunalAvailable(false)
            runCurrent()

            verify(appWidgetHost).stopListening()
        }

    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @DisableSceneContainer
    fun communalShowingStartsAppWidgetHost() =
        kosmos.runTest {
            setCommunalAvailable(true)
            communalInteractor.setEditModeOpen(false)

            verify(appWidgetHost, never()).startListening()

            underTest.start()
            runCurrent()

            verify(appWidgetHost, never()).startListening()
            verify(appWidgetHost, never()).stopListening()

            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            runCurrent()

            verify(appWidgetHost).startListening()
            verify(appWidgetHost, never()).stopListening()

            communalSceneInteractor.changeScene(CommunalScenes.Blank, "test")
            runCurrent()

            verify(appWidgetHost).stopListening()
        }

    // Verifies that the widget host starts listening as soon as the hub transition starts.
    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @DisableSceneContainer
    fun communalVisibleStartsAppWidgetHost() =
        kosmos.runTest {
            setCommunalAvailable(true)
            communalInteractor.setEditModeOpen(false)

            verify(appWidgetHost, never()).startListening()

            underTest.start()
            runCurrent()

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene = CommunalScenes.Blank)
                )
            communalSceneInteractor.setTransitionState(transitionState)
            runCurrent()

            // Listening has not started or stopped yet.
            verify(appWidgetHost, never()).startListening()
            verify(appWidgetHost, never()).stopListening()

            // Start transitioning to communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = CommunalScenes.Blank,
                    toScene = CommunalScenes.Communal,
                    currentScene = flowOf(CommunalScenes.Blank),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Listening starts.
            verify(appWidgetHost).startListening()
            verify(appWidgetHost, never()).stopListening()

            // Start transitioning away from communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = CommunalScenes.Communal,
                    toScene = CommunalScenes.Blank,
                    currentScene = flowOf(CommunalScenes.Communal),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Listening continues
            verify(appWidgetHost, never()).stopListening()

            // Finish transitioning away from communal.
            transitionState.value =
                ObservableTransitionState.Idle(currentScene = CommunalScenes.Blank)
            runCurrent()

            // Listening stops.
            verify(appWidgetHost).stopListening()
        }

    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @EnableSceneContainer
    fun communalVisibleStartsAppWidgetHost_Flexi() =
        kosmos.runTest {
            setCommunalAvailable(true)
            communalInteractor.setEditModeOpen(false)

            verify(appWidgetHost, never()).startListening()

            underTest.start()
            runCurrent()

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
                )
            communalSceneInteractor.setTransitionState(transitionState)
            runCurrent()

            // Listening has not started or stopped yet.
            verify(appWidgetHost, never()).startListening()
            verify(appWidgetHost, never()).stopListening()

            // Start transitioning to communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Communal,
                    currentScene = flowOf(Scenes.Lockscreen),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Listening starts.
            verify(appWidgetHost).startListening()
            verify(appWidgetHost, never()).stopListening()

            // Start transitioning away from communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Communal,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Communal),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Listening continues
            verify(appWidgetHost, never()).stopListening()

            // Finish transitioning away from communal.
            transitionState.value = ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
            runCurrent()

            // Listening stops.
            verify(appWidgetHost).stopListening()
        }

    @Test
    fun communalAndEditModeNotShowingNeverStartListening() =
        kosmos.runTest {
            setCommunalAvailable(false)
            communalInteractor.setEditModeOpen(false)

            underTest.start()
            runCurrent()

            verify(appWidgetHost, never()).startListening()
            verify(appWidgetHost, never()).stopListening()
        }

    @Test
    @DisableSceneContainer
    fun observeHostWhenCommunalIsAvailable() =
        kosmos.runTest {
            setCommunalAvailable(true)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            }
            communalInteractor.setEditModeOpen(false)
            verify(communalWidgetHost, never()).startObservingHost()
            verify(communalWidgetHost, never()).stopObservingHost()

            underTest.start()
            runCurrent()

            verify(communalWidgetHost).startObservingHost()
            verify(communalWidgetHost, never()).stopObservingHost()

            setCommunalAvailable(false)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Blank, "test")
            }
            runCurrent()

            verify(communalWidgetHost).stopObservingHost()
        }

    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @EnableSceneContainer
    fun observeHostWhenCommunalIsVisible_Flexi() =
        kosmos.runTest {
            setCommunalAvailable(true)
            communalInteractor.setEditModeOpen(false)

            verify(communalWidgetHost, never()).startObservingHost()

            underTest.start()
            runCurrent()

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
                )
            communalSceneInteractor.setTransitionState(transitionState)
            runCurrent()

            // Observing has not started or stopped yet.
            verify(communalWidgetHost, never()).startObservingHost()
            verify(communalWidgetHost, never()).stopObservingHost()

            // Start transitioning to communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Communal,
                    currentScene = flowOf(Scenes.Lockscreen),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Observing starts.
            verify(communalWidgetHost).startObservingHost()
            verify(communalWidgetHost, never()).stopObservingHost()

            // Start transitioning away from communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Communal,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Communal),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Observing continues
            verify(communalWidgetHost, never()).stopObservingHost()

            // Finish transitioning away from communal.
            transitionState.value = ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
            runCurrent()

            // Observing stops.
            verify(communalWidgetHost).stopObservingHost()
        }

    @Test
    fun removeAppWidgetReportedByHost() =
        kosmos.runTest {
            // Set up communal widgets
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 2)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 3)

            underTest.start()

            // Assert communal widgets has 3
            val communalWidgets by collectLastValue(fakeCommunalWidgetRepository.communalWidgets)
            assertThat(communalWidgets).hasSize(3)

            val widget1 = communalWidgets!![0]
            val widget2 = communalWidgets!![1]
            val widget3 = communalWidgets!![2]
            assertThat(widget1.appWidgetId).isEqualTo(1)
            assertThat(widget2.appWidgetId).isEqualTo(2)
            assertThat(widget3.appWidgetId).isEqualTo(3)

            // Report app widget 1 to remove and assert widget removed
            appWidgetIdToRemove.emit(1)
            runCurrent()
            assertThat(communalWidgets).containsExactly(widget2, widget3)

            // Report app widget 3 to remove and assert widget removed
            appWidgetIdToRemove.emit(3)
            runCurrent()
            assertThat(communalWidgets).containsExactly(widget2)
        }

    @Test
    @DisableSceneContainer
    fun removeWidgetsForDeletedProfile_whenCommunalIsAvailable() =
        kosmos.runTest {
            // Communal is available and work profile is configured.
            setCommunalAvailable(true)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            }
            kosmos.fakeUserTracker.set(
                userInfos = listOf(MAIN_USER_INFO, USER_INFO_WORK),
                selectedUserIndex = 0,
            )
            // One work widget, one pending work widget, and one personal widget.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, userId = USER_INFO_WORK.id)
            fakeCommunalWidgetRepository.addPendingWidget(
                appWidgetId = 2,
                userId = USER_INFO_WORK.id,
            )
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 3, userId = MAIN_USER_INFO.id)

            underTest.start()
            runCurrent()

            val communalWidgets by collectLastValue(fakeCommunalWidgetRepository.communalWidgets)
            assertThat(communalWidgets).hasSize(3)

            val widget1 = communalWidgets!![0]
            val widget2 = communalWidgets!![1]
            val widget3 = communalWidgets!![2]
            assertThat(widget1.appWidgetId).isEqualTo(1)
            assertThat(widget2.appWidgetId).isEqualTo(2)
            assertThat(widget3.appWidgetId).isEqualTo(3)

            // Unlock the device and remove work profile.
            fakeKeyguardRepository.setKeyguardShowing(false)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Blank, "test")
            }
            kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
            runCurrent()

            // Communal becomes available.
            fakeKeyguardRepository.setKeyguardShowing(true)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            }
            runCurrent()

            // Both work widgets are removed.
            assertThat(communalWidgets).containsExactly(widget3)
        }

    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @EnableSceneContainer
    fun removeWidgetsForDeletedProfile_whenCommunalIsAvailable_Flexi() =
        kosmos.runTest {
            setCommunalAvailable(true)

            underTest.start()
            runCurrent()

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
                )
            communalSceneInteractor.setTransitionState(transitionState)
            runCurrent()

            kosmos.fakeUserTracker.set(
                userInfos = listOf(MAIN_USER_INFO, USER_INFO_WORK),
                selectedUserIndex = 0,
            )
            // One work widget, one pending work widget, and one personal widget.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, userId = USER_INFO_WORK.id)
            fakeCommunalWidgetRepository.addPendingWidget(
                appWidgetId = 2,
                userId = USER_INFO_WORK.id,
            )
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 3, userId = MAIN_USER_INFO.id)

            val communalWidgets by collectLastValue(fakeCommunalWidgetRepository.communalWidgets)
            assertThat(communalWidgets).hasSize(3)

            val widget1 = communalWidgets!![0]
            val widget2 = communalWidgets!![1]
            val widget3 = communalWidgets!![2]
            assertThat(widget1.appWidgetId).isEqualTo(1)
            assertThat(widget2.appWidgetId).isEqualTo(2)
            assertThat(widget3.appWidgetId).isEqualTo(3)

            // Unlock the device and remove work profile.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Communal,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Communal),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
            runCurrent()

            // Start transitioning to communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Communal,
                    currentScene = flowOf(Scenes.Lockscreen),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            // Both work widgets are removed.
            assertThat(communalWidgets).containsExactly(widget3)
        }

    @Test
    @DisableSceneContainer
    fun removeNotLockscreenWidgets_whenCommunalIsAvailable() =
        kosmos.runTest {
            // Communal is available
            setCommunalAvailable(true)
            if (restrictCommunalAppWidgetHostListening()) {
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            }
            kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
            fakeCommunalWidgetRepository.addWidget(
                appWidgetId = 1,
                userId = MAIN_USER_INFO.id,
                category = AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD,
            )
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 2, userId = MAIN_USER_INFO.id)
            fakeCommunalWidgetRepository.addWidget(
                appWidgetId = 3,
                userId = MAIN_USER_INFO.id,
                category = AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD,
            )

            underTest.start()
            runCurrent()

            val communalWidgets by collectLastValue(fakeCommunalWidgetRepository.communalWidgets)
            assertThat(communalWidgets).hasSize(1)
            assertThat(communalWidgets!![0].appWidgetId).isEqualTo(2)

            verify(communalInteractorSpy).deleteWidget(1)
            verify(communalInteractorSpy).deleteWidget(3)
        }

    @Test
    @EnableFlags(FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING)
    @EnableSceneContainer
    fun removeNotLockscreenWidgets_whenCommunalIsAvailable_Flexi() =
        kosmos.runTest {
            setCommunalAvailable(true)

            underTest.start()
            runCurrent()

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)
                )
            communalSceneInteractor.setTransitionState(transitionState)
            runCurrent()

            kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
            fakeCommunalWidgetRepository.addWidget(
                appWidgetId = 1,
                userId = MAIN_USER_INFO.id,
                category = AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD,
            )
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 2, userId = MAIN_USER_INFO.id)
            fakeCommunalWidgetRepository.addWidget(
                appWidgetId = 3,
                userId = MAIN_USER_INFO.id,
                category = AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD,
            )

            // Start transitioning to communal.
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Communal,
                    currentScene = flowOf(Scenes.Lockscreen),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()

            val communalWidgets by collectLastValue(fakeCommunalWidgetRepository.communalWidgets)
            assertThat(communalWidgets).hasSize(1)
            assertThat(communalWidgets!![0].appWidgetId).isEqualTo(2)

            verify(communalInteractorSpy).deleteWidget(1)
            verify(communalInteractorSpy).deleteWidget(3)
        }

    @Test
    fun onStartHeadlessSystemUser_registerWidgetManager_whenCommunalIsAvailable() =
        kosmos.runTest {
            fakeGlanceableHubMultiUserHelper.setIsInHeadlessSystemUser(true)
            underTest.start()
            runCurrent()
            verify(mockGlanceableHubWidgetManager, never()).register()
            verify(mockGlanceableHubWidgetManager, never()).unregister()

            // Binding to the service does not require keyguard showing
            setCommunalAvailable(true, setKeyguardShowing = false)
            fakeKeyguardRepository.setIsEncryptedOrLockdown(false)
            runCurrent()
            verify(mockGlanceableHubWidgetManager).register()

            setCommunalAvailable(false)
            runCurrent()
            verify(mockGlanceableHubWidgetManager).unregister()
        }

    private fun setCommunalAvailable(available: Boolean, setKeyguardShowing: Boolean = true) =
        with(kosmos) {
            setCommunalEnabled(available)
            if (setKeyguardShowing) {
                fakeKeyguardRepository.setKeyguardShowing(true)
            }
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(
                FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING
            )
        }

        private val MAIN_USER_INFO = UserInfo(0, "primary", UserInfo.FLAG_MAIN)
        private val USER_INFO_WORK = UserInfo(10, "work", UserInfo.FLAG_PROFILE)
    }
}
