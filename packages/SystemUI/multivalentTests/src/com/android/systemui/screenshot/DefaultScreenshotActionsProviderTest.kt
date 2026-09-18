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

package com.android.systemui.screenshot

import android.app.assist.AssistContent
import android.content.Intent
import android.content.packageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags as SysuiFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screenshot.ui.viewmodel.ActionButtonAppearance
import com.android.systemui.screenshot.ui.viewmodel.PreviewAction
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DefaultScreenshotActionsProviderTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val actionExecutor = mock<ActionExecutor>()
    private val actionsCallback = mock<ScreenshotActionsController.ActionsCallback>()

    private val request = ScreenshotData.forTesting(userHandle = UserHandle.OWNER)
    private val validResult =
        ScreenshotSavedResult(Uri.parse("test://uri"), Process.myUserHandle(), 0)

    private lateinit var actionsProvider: ScreenshotActionsProvider

    @Before
    fun setUp() {
        kosmos.uiEventLoggerFake.logs.clear()
        // Default to small screen.
        kosmos.fakeScreenCaptureDeviceStateRepository.setLargeScreen(false)
    }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun previewActionAccessed_beforeScreenshotCompleted_doesNothing() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val previewActionCaptor = argumentCaptor<PreviewAction>()
            verify(actionsCallback).providePreviewAction(previewActionCaptor.capture())
            previewActionCaptor.firstValue.onClick.invoke()
            verifyNoMoreInteractions(actionExecutor)
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun actionButtonsAccessed_beforeScreenshotCompleted_doesNothing() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            verify(actionsCallback, times(2))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.allValues.forEach { it.invoke() }
            verifyNoMoreInteractions(actionExecutor)
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun actionAccessed_withResult_launchesIntent() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            actionsProvider.setCompletedScreenshot(validResult)

            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            verify(actionsCallback, times(2))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.firstValue.invoke()

            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenshotEvent.SCREENSHOT_SHARE_TAPPED.id)
            val intentCaptor = argumentCaptor<Intent>()
            verify(actionExecutor)
                .startSharedTransition(
                    intentCaptor.capture(),
                    eq(Process.myUserHandle()),
                    eq(false),
                )
            assertThat(intentCaptor.firstValue.action).isEqualTo(Intent.ACTION_CHOOSER)
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun shareAction_includesAssistContentUri() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)

            val resolveInfo = mock<ResolveInfo>()
            whenever(
                packageManager.resolveActivity(
                    argThat { intent ->
                        intent.action == Intent.ACTION_VIEW &&
                            intent.type == DocumentsContract.Document.MIME_TYPE_DIR
                    },
                    anyInt(),
                )
            ) doReturn resolveInfo

            actionsProvider = createActionsProvider()
            actionsProvider.setCompletedScreenshot(validResult)

            val uri = Uri.parse("http://www.android.com")
            val assistContent = mock<AssistContent>() { on { webUri } doReturn uri }

            actionsProvider.onAssistContent(assistContent)

            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.firstValue.invoke()

            val intentCaptor = argumentCaptor<Intent>()
            verify(actionExecutor)
                .startSharedTransition(
                    intentCaptor.capture(),
                    eq(Process.myUserHandle()),
                    eq(false),
                )
            val innerIntent =
                intentCaptor.lastValue.extras?.getParcelable(
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
            assertThat(innerIntent?.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(uri.toString())
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun openInFilesAction_launchesIntent() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)

            val resolveInfo = mock<ResolveInfo>()
            whenever(
                packageManager.resolveActivity(
                    argThat { intent ->
                        intent.action == Intent.ACTION_VIEW &&
                            intent.type == DocumentsContract.Document.MIME_TYPE_DIR
                    },
                    anyInt(),
                )
            ) doReturn resolveInfo

            actionsProvider = createActionsProvider()
            actionsProvider.setCompletedScreenshot(validResult)

            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // Share, Copy, Open
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            // Third button is Open
            actionButtonCaptor.lastValue.invoke()

            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenshotEvent.SCREENSHOT_OPEN_TAPPED.id)
            val intentCaptor = argumentCaptor<Intent>()
            verify(actionExecutor)
                .startSharedTransition(
                    intentCaptor.capture(),
                    eq(Process.myUserHandle()),
                    eq(false),
                )
            assertThat(intentCaptor.firstValue.action).isEqualTo(Intent.ACTION_VIEW)
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun actionAccessed_whilePending_launchesMostRecentAction() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val previewActionCaptor = argumentCaptor<PreviewAction>()
            verify(actionsCallback).providePreviewAction(previewActionCaptor.capture())
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            verify(actionsCallback, times(2))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())

            actionButtonCaptor.firstValue.invoke()
            previewActionCaptor.firstValue.onClick.invoke()
            actionButtonCaptor.secondValue.invoke()
            actionsProvider.setCompletedScreenshot(validResult)

            assertThat(uiEventLoggerFake.eventId(2))
                .isEqualTo(ScreenshotEvent.SCREENSHOT_EDIT_TAPPED.id)
            val intentCaptor = argumentCaptor<Intent>()
            verify(actionExecutor)
                .startSharedTransition(intentCaptor.capture(), eq(Process.myUserHandle()), eq(true))
            assertThat(intentCaptor.firstValue.action).isEqualTo(Intent.ACTION_EDIT)
        }

    @Test
    @DisableFlags(SysuiFlags.FLAG_DELETE_AFTER_SCROLL_CAPTURE)
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun scrollChipClicked_callsOnClick_legacy() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val onScrollClick = mock<ScrollClickCallback>()
            actionsProvider.onScrollChipReady(onScrollClick)
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // share, edit, scroll
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.thirdValue.invoke()

            verify(onScrollClick).invoke(Uri.EMPTY)
        }

    @Test
    @EnableFlags(
        SysuiFlags.FLAG_DELETE_AFTER_SCROLL_CAPTURE,
        SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
    )
    fun scrollChipClicked_callsOnClick() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val onScrollClick = mock<ScrollClickCallback>()
            actionsProvider.onScrollChipReady(onScrollClick)
            actionsProvider.setCompletedScreenshot(validResult)
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // share, edit, scroll
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.thirdValue.invoke()

            verify(onScrollClick).invoke(validResult.uri)
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun scrollChipClicked_afterInvalidate_doesNothing() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val onScrollClick = mock<ScrollClickCallback>()
            actionsProvider.onScrollChipReady(onScrollClick)
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            actionsProvider.onScrollChipInvalidated()
            // share, edit, scroll
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.thirdValue.invoke()

            verify(onScrollClick, never()).invoke(any())
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    @DisableFlags(SysuiFlags.FLAG_DELETE_AFTER_SCROLL_CAPTURE)
    fun scrollChipClicked_afterUpdate_runsNewAction_legacy() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val onScrollClick = mock<ScrollClickCallback>()
            val onScrollClick2 = mock<ScrollClickCallback>()

            actionsProvider.onScrollChipReady(onScrollClick)
            actionsProvider.onScrollChipInvalidated()
            actionsProvider.onScrollChipReady(onScrollClick2)
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // share, edit, scroll
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.thirdValue.invoke()

            verify(onScrollClick2).invoke(Uri.EMPTY)
            verify(onScrollClick, never()).invoke(any())
        }

    @Test
    @EnableFlags(
        SysuiFlags.FLAG_DELETE_AFTER_SCROLL_CAPTURE,
        SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
    )
    fun scrollChipClicked_afterUpdate_runsNewAction() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val onScrollClick = mock<ScrollClickCallback>()
            val onScrollClick2 = mock<ScrollClickCallback>()

            actionsProvider.onScrollChipReady(onScrollClick)
            actionsProvider.onScrollChipInvalidated()
            actionsProvider.onScrollChipReady(onScrollClick2)
            actionsProvider.setCompletedScreenshot(validResult)
            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // share, edit, scroll
            verify(actionsCallback, times(3))
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            actionButtonCaptor.thirdValue.invoke()

            verify(onScrollClick2).invoke(validResult.uri)
            verify(onScrollClick, never()).invoke(any())
        }

    @Test
    @DisableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun editAction_whenFeatureFlagDisabled_includesButton() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .contains(context.getString(R.string.screenshot_edit_description))
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun editAction_whenFeatureFlagEnabled_andSmallScreen_includesButton() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .contains(context.getString(R.string.screenshot_edit_description))
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun editAction_whenFeatureFlagEnabled_andLargeScreen_doesNotIncludeButton() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .doesNotContain(context.getString(R.string.screenshot_edit_description))
        }

    @Test
    @DisableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun copyToClipboardAction_whenFeatureFlagDisabled_doesNotIncludeButton() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .doesNotContain(context.getString(R.string.screenshot_copy_description))
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun copyToClipboardAction_whenFeatureFlagEnabled_andNotLargeScreen_doesNotIncludeButton() =
        kosmos.runTest {
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .doesNotContain(context.getString(R.string.screenshot_copy_description))
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun copyToClipboardAction_whenFeatureFlagEnabled_andIsLargeScreen_includesButton() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            actionsProvider = createActionsProvider()

            val actionButtonAppearanceCaptor = argumentCaptor<ActionButtonAppearance>()
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(actionButtonAppearanceCaptor.capture(), any(), any())

            val buttonDescriptions = actionButtonAppearanceCaptor.allValues.map { it.description }
            assertThat(buttonDescriptions)
                .contains(context.getString(R.string.screenshot_copy_description))
        }

    @Test
    @EnableFlags(SysuiFlags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
    fun copyToClipboardAction_copiesToClipboard() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
            actionsProvider = createActionsProvider()
            actionsProvider.setCompletedScreenshot(validResult)

            val actionButtonCaptor = argumentCaptor<() -> Unit>()
            // share, copy
            verify(actionsCallback, atLeastOnce())
                .provideActionButton(any(), any(), actionButtonCaptor.capture())
            val copyAction = actionButtonCaptor.lastValue
            copyAction.invoke()

            val uriCaptor = argumentCaptor<Uri>()
            verify(actionExecutor).copyScreenshotToClipboard(uriCaptor.capture())
            assertThat(uriCaptor.firstValue).isEqualTo(validResult.uri)
        }

    private fun createActionsProvider(): ScreenshotActionsProvider {
        return kosmos.screenshotActionsProviderFactory.create(
            UUID.randomUUID(),
            request,
            actionExecutor,
            actionsCallback,
        )
    }
}
