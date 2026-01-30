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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.Alignment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_STATUS_BAR_FOR_DESKTOP
import com.android.systemui.SysuiTestCase
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimRounding
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationStackAppearanceInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { notificationStackAppearanceInteractor }

    @Test
    fun stackNotificationScrimBounds() =
        kosmos.runTest {
            val stackBounds by collectLastValue(underTest.notificationShadeScrimBounds)

            val bounds1 = ShadeScrimBounds(top = 100f, bottom = 200f)
            underTest.setNotificationShadeScrimBounds(bounds1)
            assertThat(stackBounds).isEqualTo(bounds1)

            val bounds2 = ShadeScrimBounds(top = 200f, bottom = 300f)
            underTest.setNotificationShadeScrimBounds(bounds2)
            assertThat(stackBounds).isEqualTo(bounds2)
        }

    @Test
    fun setQsPanelShape() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            underTest.qsPanelShapeInWindow.observe { shape -> actual = shape }

            val expected1 =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(top = 0f, bottom = 100f),
                    topRadius = 0,
                    bottomRadius = 10,
                )
            underTest.setQsPanelShapeInWindow(expected1.copy())
            assertThat(actual).isEqualTo(expected1)

            val expected2 = expected1.copy(topRadius = 10)
            underTest.setQsPanelShapeInWindow(expected2.copy())
            assertThat(actual).isEqualTo(expected2)
        }

    @Test
    fun stackRounding() =
        kosmos.runTest {
            val stackRounding by collectLastValue(underTest.shadeScrimRounding)

            enableSingleShade()
            assertThat(stackRounding)
                .isEqualTo(ShadeScrimRounding(isTopRounded = true, isBottomRounded = false))

            enableSplitShade()
            assertThat(stackRounding)
                .isEqualTo(ShadeScrimRounding(isTopRounded = true, isBottomRounded = true))
        }

    @Test
    fun stackNotificationScrimBounds_withImproperBounds_throwsException() =
        kosmos.runTest {
            assertThrows(IllegalStateException::class.java) {
                underTest.setNotificationShadeScrimBounds(
                    ShadeScrimBounds(top = 100f, bottom = 99f)
                )
            }
        }

    @Test
    fun setQsPanelShape_withImproperBounds_throwsException() =
        kosmos.runTest {
            val invalidBounds = ShadeScrimBounds(top = 0f, bottom = -10f)
            assertThrows(IllegalStateException::class.java) {
                underTest.setQsPanelShapeInWindow(
                    ShadeScrimShape(bounds = invalidBounds, topRadius = 10, bottomRadius = 10)
                )
            }
        }

    @Test
    fun shouldCloseGuts_userInputOngoing_currentGestureInGuts() =
        kosmos.runTest {
            val shouldCloseGuts by collectLastValue(underTest.shouldCloseGuts)

            sceneInteractor.onSceneContainerUserInputStarted()
            underTest.setCurrentGestureInGuts(true)

            assertThat(shouldCloseGuts).isFalse()
        }

    @Test
    fun shouldCloseGuts_userInputOngoing_currentGestureNotInGuts() =
        kosmos.runTest {
            val shouldCloseGuts by collectLastValue(underTest.shouldCloseGuts)

            sceneInteractor.onSceneContainerUserInputStarted()
            underTest.setCurrentGestureInGuts(false)

            assertThat(shouldCloseGuts).isTrue()
        }

    @Test
    fun shouldCloseGuts_userInputNotOngoing_currentGestureInGuts() =
        kosmos.runTest {
            val shouldCloseGuts by collectLastValue(underTest.shouldCloseGuts)

            sceneInteractor.onUserInputFinished()
            underTest.setCurrentGestureInGuts(true)

            assertThat(shouldCloseGuts).isFalse()
        }

    @Test
    fun shouldCloseGuts_userInputNotOngoing_currentGestureNotInGuts() =
        kosmos.runTest {
            val shouldCloseGuts by collectLastValue(underTest.shouldCloseGuts)

            sceneInteractor.onUserInputFinished()
            underTest.setCurrentGestureInGuts(false)

            assertThat(shouldCloseGuts).isFalse()
        }

    @Test
    fun notificationStackHorizontalAlignment_singleShade_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSingleShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    fun notificationStackHorizontalAlignment_splitShade_endAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableSplitShade()

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableSceneContainer
    fun notificationStackHorizontalAlignment_dualShadeNarrow_centeredHorizontally() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = false)

            assertThat(alignment).isEqualTo(Alignment.CenterHorizontally)
        }

    @Test
    @EnableSceneContainer
    fun notificationStackHorizontalAlignment_dualShadeWide_startAligned() =
        kosmos.runTest {
            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_STATUS_BAR_FOR_DESKTOP)
    fun notificationStackHorizontalAlignment_desktopWithTopEndConfig_endAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)

            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableUsingDesktopStatusBar()
            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.End)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_STATUS_BAR_FOR_DESKTOP)
    fun notificationStackHorizontalAlignment_desktopWithoutTopEndConfig_startAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)

            val alignment by collectLastValue(underTest.notificationStackHorizontalAlignment)

            enableUsingDesktopStatusBar()
            enableDualShade(wideLayout = true)

            assertThat(alignment).isEqualTo(Alignment.Start)
        }
}
