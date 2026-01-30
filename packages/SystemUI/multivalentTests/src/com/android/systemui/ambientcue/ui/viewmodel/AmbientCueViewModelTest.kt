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

package com.android.systemui.ambientcue.ui.viewmodel

import android.content.Context
import android.content.applicationContext
import android.graphics.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.ambientCueRepository
import com.android.systemui.ambientcue.data.repository.fake
import com.android.systemui.ambientcue.domain.interactor.ambientCueInteractor
import com.android.systemui.ambientcue.shared.logger.ambientCueLogger
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.plugins.cuebar.IconModel
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val viewModel = kosmos.ambientCueViewModelFactory.create()

    @Test
    fun isVisible_timesOut() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            // Times out when there's no interaction
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_MS.milliseconds)
            runCurrent()
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun isVisible_setTimeoutAs60sec_stillVisibleAfter30sec() =
        kosmos.runTest {
            ambientCueRepository.fake.setAmbientCueTimeoutMs(60_000)
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            runCurrent()
            advanceTimeBy(30.seconds)
            runCurrent()
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isTrue()
        }

    @Test
    fun isVisible_setTimeoutAs60sec_invisibleAfter60sec() =
        kosmos.runTest {
            ambientCueRepository.fake.setAmbientCueTimeoutMs(60_000)
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            advanceTimeBy(30.seconds)
            runCurrent()
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()
            assertThat(viewModel.isVisible).isTrue()
            advanceTimeBy(30.seconds)
            runCurrent()
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun isVisible_imeNotVisible_true() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            ambientCueRepository.fake.setActions(testActions(applicationContext))
            ambientCueInteractor.setDeactivated(false)

            ambientCueInteractor.setImeVisible(false)
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isTrue()
        }

    @Test
    fun isVisible_imeVisible_false() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            ambientCueInteractor.setImeVisible(true)
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun isVisible_isOccludedBySystemUi_true() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            fakeKeyguardRepository.setKeyguardShowing(false)
            runCurrent()

            assertThat(viewModel.isVisible).isTrue()
        }

    @Test
    fun isVisible_isOccludedBySystemUi_false() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            fakeKeyguardRepository.setKeyguardShowing(true)
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun onClick_collapses() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            ambientCueRepository.fake.setActions(testActions(applicationContext))
            ambientCueInteractor.setDeactivated(false)
            viewModel.expand()
            runCurrent()

            assertThat(viewModel.isExpanded).isTrue()
            val action: ActionViewModel = viewModel.actions.first()

            // UI Collapses upon clicking on an action
            action.onClick()
            assertThat(viewModel.isExpanded).isFalse()
        }

    @Test
    fun delayAndDeactivateCueBar_refreshTimeout() =
        kosmos.runTest {
            ambientCueRepository.fake.setAmbientCueTimeoutMs(
                AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_MS
            )
            viewModel.activateIn(kosmos.testScope)
            ambientCueInteractor.setDeactivated(false)
            testScope.backgroundScope.launch { viewModel.delayAndDeactivateCueBar() }
            advanceTimeBy(10.seconds)
            runCurrent()
            assertThat(ambientCueRepository.isDeactivated.value).isFalse()

            testScope.backgroundScope.launch { viewModel.delayAndDeactivateCueBar() }
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_MS.milliseconds - 10.seconds)
            runCurrent()
            // 5 seconds after calling delayAndDeactivateCueBar() again (totally 15 seconds after
            // test begins), isDeactivated should still be false.
            assertThat(ambientCueRepository.isDeactivated.value).isFalse()
            advanceTimeBy(10.seconds)
            runCurrent()

            // 15 seconds after calling delayAndDeactivateCueBar() again, isDeactivated should be
            // true.
            assertThat(ambientCueRepository.isDeactivated.value).isTrue()
        }

    fun pillStyle_gestureNav_isInNavbarMode() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            ambientCueRepository.fake.setIsGestureNav(true)
            ambientCueRepository.fake.setTaskBarVisible(false)

            runCurrent()
            assertThat(viewModel.pillStyle).isEqualTo(PillStyleViewModel.NavBarPillStyle)
        }

    @Test
    fun pillStyle_gestureNavAndTaskBar_shortPillEndAligned() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            ambientCueRepository.fake.setIsGestureNav(true)
            ambientCueRepository.fake.setTaskBarVisible(true)

            runCurrent()
            assertThat(viewModel.pillStyle)
                .isInstanceOf(PillStyleViewModel.ShortPillStyle::class.java)
        }

    @Test
    fun pillStyle_3ButtonNav_shortPill() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            val recentsButtonPosition = Rect(10, 20, 30, 40)
            ambientCueRepository.fake.setIsGestureNav(false)
            ambientCueRepository.fake.setTaskBarVisible(true)
            ambientCueRepository.fake.setRecentsButtonPosition(recentsButtonPosition)

            runCurrent()
            assertThat((viewModel.pillStyle as PillStyleViewModel.ShortPillStyle).position)
                .isEqualTo(recentsButtonPosition.toComposeRect())
        }

    @Test
    fun pillStyle_3ButtonNavAndTaskBar_shortPill() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            ambientCueRepository.fake.setIsGestureNav(false)
            ambientCueRepository.fake.setTaskBarVisible(false)

            runCurrent()
            assertThat(viewModel.pillStyle)
                .isInstanceOf(PillStyleViewModel.ShortPillStyle::class.java)
        }

    @Test
    fun showFirstTimeEducation_expanded_false() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.showFirstTimeEducation).isTrue()
            viewModel.expand()
            runCurrent()
            assertThat(viewModel.showFirstTimeEducation).isFalse()
        }

    @Test
    fun showFirstTimeEducation_hidden_false() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            initializeIsVisible()
            assertThat(viewModel.showFirstTimeEducation).isTrue()
            viewModel.hide()
            runCurrent()
            assertThat(viewModel.showFirstTimeEducation).isFalse()
        }

    @Test
    fun showLongPressEducation_in7days() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            // Shouldn't show immediately
            initializeIsVisible()
            assertThat(viewModel.showLongPressEducation).isFalse()
            viewModel.hide()

            // But would be triggered in 7 days
            fakeSystemClock.advanceTime(SEVEN_DAYS_MS * 1000000L)
            initializeIsVisible()
            assertThat(viewModel.showLongPressEducation).isTrue()

            // And never again
            viewModel.expand()
            viewModel.collapse()
            runCurrent()
            assertThat(viewModel.showLongPressEducation).isFalse()
        }

    @Test
    fun hide_setsClickedClosedButtonStatus() =
        kosmos.runTest {
            viewModel.activateIn(kosmos.testScope)
            viewModel.hide()
            runCurrent()

            verify(kosmos.ambientCueLogger).setClickedCloseButtonStatus()
        }

    private fun testActions(applicationContext: Context) =
        listOf(
            ActionModel(
                icon =
                    IconModel(
                        small =
                            applicationContext.resources.getDrawable(
                                R.drawable.ic_content_paste_spark,
                                applicationContext.theme,
                            ),
                        large =
                            applicationContext.resources.getDrawable(
                                R.drawable.ic_content_paste_spark,
                                applicationContext.theme,
                            ),
                        iconId = "test.icon",
                    ),
                label = "Sunday Morning",
                attribution = null,
                onPerformAction = {},
                onPerformLongClick = {},
            )
        )

    private fun Kosmos.initializeIsVisible() {
        ambientCueRepository.fake.setActions(testActions(applicationContext))
        ambientCueInteractor.setDeactivated(false)
        ambientCueInteractor.setImeVisible(false)
        ambientCueRepository.fake.updateRootViewAttached()
        runCurrent()
    }

    private companion object {
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
