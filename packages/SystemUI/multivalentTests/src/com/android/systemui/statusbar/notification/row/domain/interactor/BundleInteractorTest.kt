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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.statusbar.notification.row.domain.interactor

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.SysuiTestCase
import com.android.systemui.controls.dagger.ControlsComponentTest.Companion.eq
import com.android.systemui.controls.ui.ControlActionCoordinatorImplTest.Companion.any
import com.android.systemui.kosmos.testScope
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.BundleRepository
import com.android.systemui.statusbar.notification.row.data.repository.testBundleRepository
import com.android.systemui.statusbar.notification.row.domain.bundleInteractor
import com.android.systemui.statusbar.notification.row.icon.appIconProvider
import com.android.systemui.statusbar.notification.row.icon.mockAppIconProvider
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.util.time.systemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.motion.compose.runMonotonicClockTest

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleInteractorTest : SysuiTestCase() {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val fakeSystemClock = FakeSystemClock()

    private val testBundleRepository: BundleRepository = kosmos.testBundleRepository
    private lateinit var underTest: BundleInteractor

    private val drawable1: Drawable = ColorDrawable(Color.RED)
    private val drawable2: Drawable = ColorDrawable(Color.GREEN)
    private val drawable3: Drawable = ColorDrawable(Color.BLUE)

    @Before
    fun setUp() {
        kosmos.appIconProvider = kosmos.mockAppIconProvider
        kosmos.systemClock = fakeSystemClock
        underTest = kosmos.bundleInteractor
    }

    @Test
    fun previewIcons_callsFetchEnoughTimesForThreeNonNullResults() =
        testScope.runTest {
            // Arrange
            val appDataList =
                List(5) { index ->
                    mock<AppData> {
                        on { it.packageName }.thenReturn("com.example.app${index + 1}")
                    }
                }

            val unusedDrawable: Drawable = ColorDrawable(android.graphics.Color.YELLOW)

            whenever(
                    kosmos.mockAppIconProvider.getOrFetchAppIcon(
                        any<String>(),
                        any<UserHandle>(),
                        any<String>(),
                    )
                )
                .thenReturn(drawable1)
                .thenReturn(null)
                .thenReturn(drawable2)
                .thenReturn(drawable3)
                .thenReturn(unusedDrawable)

            // Act
            testBundleRepository.appDataList.value = appDataList
            runCurrent()
            val result = underTest.previewIcons.first()

            // Assert
            verify(kosmos.mockAppIconProvider, times(4))
                .getOrFetchAppIcon(any<String>(), any<UserHandle>(), any<String>())

            assertThat(result).hasSize(3)
            assertThat(result).containsExactly(drawable1, drawable2, drawable3).inOrder()
        }

    @Test
    fun previewIcons_collapseTimeZero_fetchAll() =
        testScope.runTest {
            // Arrange
            val app1Data =
                mock<AppData> {
                    on { packageName }.thenReturn("app1")
                    on { timeAddedToBundle }.thenReturn(10L)
                }
            val app2Data =
                mock<AppData> {
                    on { packageName }.thenReturn("app2")
                    on { timeAddedToBundle }.thenReturn(20L)
                }
            val appDataList = listOf(app1Data, app2Data)

            whenever(
                    kosmos.mockAppIconProvider.getOrFetchAppIcon(
                        eq("app1"),
                        any<UserHandle>(),
                        any<String>(),
                    )
                )
                .thenReturn(drawable1)
            whenever(
                    kosmos.mockAppIconProvider.getOrFetchAppIcon(
                        eq("app2"),
                        any<UserHandle>(),
                        any<String>(),
                    )
                )
                .thenReturn(drawable2)

            testBundleRepository.lastCollapseTime = 0L
            testBundleRepository.appDataList.value = appDataList
            runCurrent()

            // Act
            val result = underTest.previewIcons.first()

            // Assert
            assertThat(result).containsExactly(drawable1, drawable2).inOrder()
            verify(kosmos.mockAppIconProvider)
                .getOrFetchAppIcon(eq("app1"), any<UserHandle>(), any<String>())
            verify(kosmos.mockAppIconProvider)
                .getOrFetchAppIcon(eq("app2"), any<UserHandle>(), any<String>())
        }

    @Test
    fun previewIcons_collapseTimeNonZero_stillFetchesAllIcons() =
        testScope.runTest {
            // Arrange
            val collapseTime = 100L
            val appDataOld =
                mock<AppData> {
                    on { packageName }.thenReturn("old_app")
                    on { timeAddedToBundle }.thenReturn(50L) // Older than collapseTime
                }
            val appDataNew =
                mock<AppData> {
                    on { packageName }.thenReturn("new_app")
                    on { timeAddedToBundle }.thenReturn(150L) // Newer than collapseTime
                }
            val appDataList = listOf(appDataOld, appDataNew)

            whenever(kosmos.mockAppIconProvider.getOrFetchAppIcon(eq("old_app"), any(), any()))
                .thenReturn(drawable1)
            whenever(kosmos.mockAppIconProvider.getOrFetchAppIcon(eq("new_app"), any(), any()))
                .thenReturn(drawable2)

            testBundleRepository.lastCollapseTime = collapseTime
            testBundleRepository.appDataList.value = appDataList
            runCurrent()

            // Act
            val result = underTest.previewIcons.first()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result).containsExactly(drawable1, drawable2).inOrder()

            verify(kosmos.mockAppIconProvider).getOrFetchAppIcon(eq("old_app"), any(), any())
            verify(kosmos.mockAppIconProvider).getOrFetchAppIcon(eq("new_app"), any(), any())
        }

    @Test
    fun previewIcons_allAppDataOlderThanCollapseTime_emitsFullList() =
        testScope.runTest {
            // Arrange
            val collapseTime = 200L
            val appDataList =
                List(3) { i ->
                    mock<AppData> {
                        on { packageName }.thenReturn("app$i")
                        on { timeAddedToBundle }.thenReturn(i * 1L)
                    }
                }

            whenever(kosmos.mockAppIconProvider.getOrFetchAppIcon(anyString(), any(), any()))
                .thenReturn(drawable1, drawable2, drawable3)

            testBundleRepository.lastCollapseTime = collapseTime
            testBundleRepository.appDataList.value = appDataList
            runCurrent()

            // Act
            val result = underTest.previewIcons.first()

            // Assert
            assertThat(result).hasSize(3)
            verify(kosmos.mockAppIconProvider, times(3))
                .getOrFetchAppIcon(anyString(), any<UserHandle>(), any<String>())
        }

    @Test
    fun previewIcons_emptyAppDataList_emitsEmptyIconList() =
        testScope.runTest {
            // Arrange
            testBundleRepository.appDataList.value = emptyList()
            testBundleRepository.lastCollapseTime = 0L
            runCurrent()

            // Act
            val result = underTest.previewIcons.first()

            // Assert
            assertThat(result).isEmpty()
        }

    @Test
    fun setExpansionState_sets_state_to_expanded() = runMonotonicClockTest {
        // Arrange
        underTest.composeScope = this
        underTest.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Collapsed)

        // Act
        underTest.setExpansionState(true)

        // Assert
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Expanded)
    }

    @Test
    fun setExpansionState_sets_state_to_collapsed() = runMonotonicClockTest {
        // Arrange
        underTest.composeScope = this
        underTest.state =
            MutableSceneTransitionLayoutState(BundleHeader.Scenes.Expanded, MotionScheme.standard())
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Expanded)

        // Act
        underTest.setExpansionState(false)

        // Assert
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Collapsed)
    }

    @Test
    fun setExpansionState_whenCollapsing_updatesLastCollapseTime() = runMonotonicClockTest {
        // Arrange
        val testTime = 20000L
        fakeSystemClock.setUptimeMillis(testTime)
        underTest.state =
            MutableSceneTransitionLayoutState(
                initialScene = BundleHeader.Scenes.Expanded,
                motionScheme = MotionScheme.standard(),
            )
        underTest.composeScope = this

        // Act
        underTest.setExpansionState(isExpanded = false)
        testScope.runCurrent()

        // Assert
        assertThat(testBundleRepository.lastCollapseTime).isEqualTo(testTime)
    }

    @Test
    fun setExpansionState_whenExpanding_doesNotUpdateLastCollapseTime() = runMonotonicClockTest {
        // Arrange
        val initialTime = 11000L
        testBundleRepository.lastCollapseTime = initialTime
        fakeSystemClock.setUptimeMillis(20000L)
        underTest.state =
            MutableSceneTransitionLayoutState(
                initialScene = BundleHeader.Scenes.Collapsed,
                motionScheme = MotionScheme.standard(),
            )
        underTest.composeScope = this

        // Act
        underTest.setExpansionState(isExpanded = true)
        testScope.runCurrent()

        // Assert
        assertThat(testBundleRepository.lastCollapseTime).isEqualTo(initialTime)
    }
}
