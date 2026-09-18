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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.ComponentName
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.annotation.DrawableRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.activityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.TransitionAwareChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.TransitionState
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ChipTransitionHelperTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun createChipFlow_typicallyFollowsInputFlow() =
        kosmos.runTest {
            val underTest = ChipTransitionHelper(applicationCoroutineScope)
            val inputChipFlow =
                MutableStateFlow<OngoingActivityChipModel>(OngoingActivityChipModel.Inactive())
            val latest by collectLastValue(underTest.createChipFlow(inputChipFlow))

            val newChip =
                OngoingActivityChipModel.Active(
                    key = KEY,
                    icon = createIcon(R.drawable.ic_cake),
                    content =
                        OngoingActivityChipModel.Content.Timer(
                            Chronometer.Running(EventTime.ElapsedRealtime(100L)),
                            timeSource = kosmos.fakeSystemClock,
                        ),
                    colors = ColorsModel.AccentThemed,
                    clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                )

            inputChipFlow.value = newChip

            assertThat(latest).isEqualTo(newChip)

            val newerChip =
                OngoingActivityChipModel.Active(
                    key = KEY,
                    icon = createIcon(R.drawable.ic_hotspot),
                    content = OngoingActivityChipModel.Content.IconOnly,
                    colors = ColorsModel.AccentThemed,
                    clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                )

            inputChipFlow.value = newerChip

            assertThat(latest).isEqualTo(newerChip)
        }

    @Test
    fun activityStopped_chipHiddenWithoutAnimationFor500ms() =
        kosmos.runTest {
            val underTest = ChipTransitionHelper(applicationCoroutineScope)
            val inputChipFlow =
                MutableStateFlow<OngoingActivityChipModel>(OngoingActivityChipModel.Inactive())
            val latest by collectLastValue(underTest.createChipFlow(inputChipFlow))

            val activeChip =
                OngoingActivityChipModel.Active(
                    key = KEY,
                    icon = createIcon(R.drawable.ic_cake),
                    content =
                        OngoingActivityChipModel.Content.Timer(
                            Chronometer.Running(EventTime.ElapsedRealtime(100L)),
                            timeSource = kosmos.fakeSystemClock,
                        ),
                    colors = ColorsModel.AccentThemed,
                    clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                )

            inputChipFlow.value = activeChip

            assertThat(latest).isEqualTo(activeChip)

            // WHEN #onActivityStopped is invoked
            underTest.onActivityStoppedFromDialog()
            runCurrent()

            // THEN the chip is hidden and has no animation
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))

            // WHEN only 250ms have elapsed
            advanceTimeBy(250)

            // THEN the chip is still hidden
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))

            // WHEN over 500ms have elapsed
            advanceTimeBy(251)

            // THEN the chip returns to the original input flow value
            assertThat(latest).isEqualTo(activeChip)
        }

    @Test
    fun activityStopped_stoppedAgainBefore500ms_chipReshownAfterSecond500ms() =
        kosmos.runTest {
            val underTest = ChipTransitionHelper(applicationCoroutineScope)
            val inputChipFlow =
                MutableStateFlow<OngoingActivityChipModel>(OngoingActivityChipModel.Inactive())
            val latest by collectLastValue(underTest.createChipFlow(inputChipFlow))

            val activeChip =
                OngoingActivityChipModel.Active(
                    key = KEY,
                    icon = createIcon(R.drawable.ic_cake),
                    content =
                        OngoingActivityChipModel.Content.Timer(
                            Chronometer.Running(EventTime.ElapsedRealtime(100L)),
                            timeSource = kosmos.fakeSystemClock,
                        ),
                    colors = ColorsModel.AccentThemed,
                    clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                )

            inputChipFlow.value = activeChip

            assertThat(latest).isEqualTo(activeChip)

            // WHEN #onActivityStopped is invoked
            underTest.onActivityStoppedFromDialog()
            runCurrent()

            // THEN the chip is hidden and has no animation
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))

            // WHEN 250ms have elapsed, get another stop event
            advanceTimeBy(250)
            underTest.onActivityStoppedFromDialog()
            runCurrent()

            // THEN the chip is still hidden for another 500ms afterwards
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))
            advanceTimeBy(499)
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))
            advanceTimeBy(2)
            assertThat(latest).isEqualTo(activeChip)
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_flagDisabled_isNull() {
        kosmos.runTest {
            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip =
                        TransitionAwareChipModel.Active(
                            cookie = COOKIE,
                            component = COMPONENT,
                            isAppVisible = false,
                        ),
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNull()
        }
    }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_nullChip_isNull() =
        kosmos.runTest {
            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = null,
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNull()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_inactiveChip_canOnlyUnregister() =
        kosmos.runTest {
            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = TransitionAwareChipModel.Inactive(COOKIE),
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.controllerFactory).isNull()

            transitionManager.registerTransition()
            transitionManager.unregisterTransition()
            verify(activityStarter, never()).registerTransition(any(), any(), any())
            verify(activityStarter).unregisterTransition(eq(COOKIE))
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_activeChipWithNullComponent_isInert() =
        kosmos.runTest {
            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip =
                        TransitionAwareChipModel.Active(
                            cookie = COOKIE,
                            component = null,
                            isAppVisible = false,
                        ),
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.controllerFactory).isNull()

            transitionManager.registerTransition()
            transitionManager.unregisterTransition()
            verify(activityStarter, never()).registerTransition(any(), any(), any())
            verify(activityStarter, never()).unregisterTransition(any())
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_activeChipWithComponent_usesGivenFactory() =
        kosmos.runTest {
            val factory = mock<ComposableControllerFactory>()
            whenever(factory.component).thenReturn(COMPONENT)

            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip =
                        TransitionAwareChipModel.Active(
                            cookie = COOKIE,
                            component = COMPONENT,
                            isAppVisible = false,
                        ),
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = factory,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.controllerFactory).isEqualTo(factory)

            transitionManager.registerTransition()
            transitionManager.unregisterTransition()
            verify(activityStarter).registerTransition(COOKIE, factory, testScope)
            verify(activityStarter, never()).unregisterTransition(any())
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_activeChipWithComponent_createsNewFactory() =
        kosmos.runTest {
            val transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip =
                        TransitionAwareChipModel.Active(
                            cookie = COOKIE,
                            component = COMPONENT,
                            isAppVisible = false,
                            launchCujType = LAUNCH_CUJ,
                            returnCujType = RETURN_CUJ,
                        ),
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.controllerFactory).isNotNull()
            assertThat(transitionManager.controllerFactory!!.cookie).isEqualTo(COOKIE)
            assertThat(transitionManager.controllerFactory!!.component).isEqualTo(COMPONENT)
            assertThat(transitionManager.controllerFactory!!.launchCujType).isEqualTo(LAUNCH_CUJ)
            assertThat(transitionManager.controllerFactory!!.returnCujType).isEqualTo(RETURN_CUJ)

            transitionManager.registerTransition()
            transitionManager.unregisterTransition()
            verify(activityStarter)
                .registerTransition(COOKIE, transitionManager.controllerFactory, testScope)
            verify(activityStarter, never()).unregisterTransition(any())
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun buildTransitionManager_hidesChipForTransition_withReturnRequested() =
        kosmos.runTest {
            val chip =
                TransitionAwareChipModel.Active(
                    cookie = COOKIE,
                    component = COMPONENT,
                    isAppVisible = false,
                )

            // NoTransition
            var transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = chip,
                    transitionState = TransitionState.NoTransition,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.hideChipForTransition).isFalse()

            // LaunchRequested
            transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = chip,
                    transitionState = TransitionState.LaunchRequested,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.hideChipForTransition).isFalse()

            // Launching
            transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = chip,
                    transitionState = TransitionState.Launching,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.hideChipForTransition).isFalse()

            // ReturnRequested
            transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = chip,
                    transitionState = TransitionState.ReturnRequested,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.hideChipForTransition).isTrue()

            // Returning
            transitionManager =
                ChipTransitionHelper.buildTransitionManager(
                    chip = chip,
                    transitionState = TransitionState.Returning,
                    updateTransitionState = {},
                    scope = testScope,
                    factory = null,
                    activityStarter = activityStarter,
                )
            assertThat(transitionManager).isNotNull()
            assertThat(transitionManager!!.hideChipForTransition).isFalse()
        }

    @Test
    fun shouldChipBeHidden_appNotVisible() =
        kosmos.runTest {
            // Was null.
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = null,
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = false,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isFalse()

            // Was Inactive.
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = TransitionAwareChipModel.Inactive(COOKIE),
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = false,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isFalse()

            // Transition ongoing (should never happen in user code).
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = null,
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = false,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.Launching,
                        newTransitionState = TransitionState.Launching,
                    )
                )
                .isFalse()
        }

    @Test
    fun shouldChipBeHidden_wasNotActive() =
        kosmos.runTest {
            // Was null.
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = null,
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = true,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // Was inactive.
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = TransitionAwareChipModel.Inactive(COOKIE),
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = true,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // Transition ongoing (should never happen in user code).
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = TransitionAwareChipModel.Inactive(COOKIE),
                        newState =
                            TransitionAwareChipModel.Active(
                                isAppVisible = true,
                                cookie = COOKIE,
                                component = COMPONENT,
                            ),
                        oldTransitionState = TransitionState.Launching,
                        newTransitionState = TransitionState.Launching,
                    )
                )
                .isTrue()
        }

    @Test
    fun shouldChipBeHidden_notIdle() =
        kosmos.runTest {
            val chip =
                TransitionAwareChipModel.Active(
                    isAppVisible = true,
                    cookie = COOKIE,
                    component = COMPONENT,
                )

            // LaunchRequested
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.LaunchRequested,
                    )
                )
                .isFalse()

            // Launching
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.LaunchRequested,
                        newTransitionState = TransitionState.Launching,
                    )
                )
                .isFalse()

            // ReturnRequested
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.ReturnRequested,
                    )
                )
                .isFalse()

            // Returning
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.ReturnRequested,
                        newTransitionState = TransitionState.Returning,
                    )
                )
                .isFalse()
        }

    @Test
    fun shouldChipBeHidden_appWasNotVisible() =
        kosmos.runTest {
            val oldChip =
                TransitionAwareChipModel.Active(
                    isAppVisible = false,
                    cookie = COOKIE,
                    component = COMPONENT,
                )
            val newChip =
                TransitionAwareChipModel.Active(
                    isAppVisible = true,
                    cookie = COOKIE,
                    component = COMPONENT,
                )

            // No transition.
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = oldChip,
                        newState = newChip,
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // Transition ending (should never happen in user code).
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = oldChip,
                        newState = newChip,
                        oldTransitionState = TransitionState.Returning,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()
        }

    @Test
    fun shouldChipBeHidden_wasNotReturning() =
        kosmos.runTest {
            val chip =
                TransitionAwareChipModel.Active(
                    isAppVisible = true,
                    cookie = COOKIE,
                    component = COMPONENT,
                )

            // NoTransition
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.NoTransition,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // LaunchRequested
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.LaunchRequested,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // Launching
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.Launching,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()

            // ReturnRequested
            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.ReturnRequested,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isTrue()
        }

    @Test
    fun shouldChipBeHidden_wasReturning() =
        kosmos.runTest {
            val chip =
                TransitionAwareChipModel.Active(
                    isAppVisible = true,
                    cookie = COOKIE,
                    component = COMPONENT,
                )

            assertThat(
                    ChipTransitionHelper.shouldChipBeHidden(
                        oldState = chip,
                        newState = chip,
                        oldTransitionState = TransitionState.Returning,
                        newTransitionState = TransitionState.NoTransition,
                    )
                )
                .isFalse()
        }

    companion object {
        private val COMPONENT = ComponentName("package", "class")
        private val COOKIE = ActivityTransitionAnimator.TransitionCookie("testCookie")
        private const val KEY = "testKey"
        private const val LAUNCH_CUJ = 100
        private const val RETURN_CUJ = 200

        private fun createIcon(@DrawableRes drawable: Int) =
            OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                Icon.Resource(drawable, contentDescription = null)
            )
    }
}
