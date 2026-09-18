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
package com.android.systemui.screencapture.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureKeyboardShortcutInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest: ScreenCaptureKeyboardShortcutInteractor by lazy {
        kosmos.screenCaptureKeyboardShortcutInteractor
    }

    @Before
    fun setUp() {
        // Default to large screen.
        kosmos.fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
    }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_flagDisabled_takesFullscreenScreenshot() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_whenUiVisibleAlready_doesNothing() =
        kosmos.runTest {
            screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())

            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)

            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_headlessSystemUserIsCurrent_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_keyguardShowing_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeKeyguardRepository.setKeyguardShowing(true)
            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_userNotSetUp_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            kosmos.fakeUserSetupRepository.setUserSetUp(false)
            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_keyguardNotShowing_showsUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeKeyguardRepository.setKeyguardShowing(false)
            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_setsLargeScreenCaptureParameters() =
        kosmos.runTest {
            underTest.attemptPartialRegionScreenshot()
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))

            val largeScreenParams =
                ((uiState as ScreenCaptureUiState.Visible).parameters
                        as ScreenCaptureUiParameters.Record)
                    .largeScreenParameters
            assertThat(largeScreenParams).isNotNull()
            assertThat(largeScreenParams?.defaultCaptureType)
                .isEqualTo(LargeScreenCaptureType.SCREENSHOT)
            assertThat(largeScreenParams?.defaultCaptureRegion)
                .isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptPartialRegionScreenshot_logsEvent() =
        kosmos.runTest {
            underTest.attemptPartialRegionScreenshot()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event =
                ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT
            assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun attemptAppWindowScreenshot_umbrellaFlagDisabled_takesFullscreenScreenshot() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun attemptAppWindowScreenshot_appWindowFlagDisabled_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_whenUiVisibleAlready_doesNothing() =
        kosmos.runTest {
            screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())

            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)

            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_headlessSystemUserIsCurrent_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_keyguardShowing_doesNotShowUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeKeyguardRepository.setKeyguardShowing(true)
            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            // Nothing is logged.
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_keyguardNotShowing_showsUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            fakeKeyguardRepository.setKeyguardShowing(false)
            underTest.attemptAppWindowScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_setsLargeScreenCaptureParameters() =
        kosmos.runTest {
            underTest.attemptAppWindowScreenshot()
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))

            val largeScreenParams =
                ((uiState as ScreenCaptureUiState.Visible).parameters
                        as ScreenCaptureUiParameters.Record)
                    .largeScreenParameters
            assertThat(largeScreenParams).isNotNull()
            assertThat(largeScreenParams?.defaultCaptureType)
                .isEqualTo(LargeScreenCaptureType.SCREENSHOT)
            assertThat(largeScreenParams?.defaultCaptureRegion)
                .isEqualTo(ScreenCaptureRegion.APP_WINDOW)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW,
    )
    fun attemptAppWindowScreenshot_logsEvent() =
        kosmos.runTest {
            underTest.attemptAppWindowScreenshot()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event =
                ScreenCaptureEvent
                    .SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_KEYBOARD_SHORTCUT
            assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        }
}
