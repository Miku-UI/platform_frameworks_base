package com.android.systemui.screenshot

import android.content.ComponentName
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.net.Uri
import android.view.Display
import android.view.Display.TYPE_EXTERNAL
import android.view.Display.TYPE_INTERNAL
import android.view.WindowManager
import android.view.WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.util.ScreenshotRequest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.FakeDisplayRepository
import com.android.systemui.display.data.repository.display
import com.android.systemui.screenshot.proxy.ScreenshotProxy
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
@SmallTest
class TakeScreenshotExecutorTest : SysuiTestCase() {

    private val controller = mock<ScreenshotController>()
    private val notificationsController0 = mock<ScreenshotNotificationsController>()
    private val notificationsController1 = mock<ScreenshotNotificationsController>()
    private val controllerFactory = mock<InteractiveScreenshotHandler.Factory>()
    private val callback = mock<TakeScreenshotService.RequestCallback>()
    private val notificationControllerFactory = mock<ScreenshotNotificationsController.Factory>()
    private val displayManager = mock<DisplayManager>()

    private val fakeDisplayRepository = FakeDisplayRepository()
    private val requestProcessor = FakeRequestProcessor()
    private val topComponent = ComponentName(mContext, TakeScreenshotExecutorTest::class.java)
    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val eventLogger = UiEventLoggerFake()

    private val screenshotProxy =
        mock<ScreenshotProxy> {
            onBlocking { getFocusedDisplay() } doReturn Display.DEFAULT_DISPLAY
        }

    private val screenshotExecutor =
        TakeScreenshotExecutorImpl(
            controllerFactory,
            displayManager,
            testScope,
            requestProcessor,
            eventLogger,
            notificationControllerFactory,
            screenshotProxy,
            dispatcher,
        )

    @Before
    fun setUp() {
        whenever(controllerFactory.create(any())).thenReturn(controller)
        whenever(notificationControllerFactory.create(eq(0))).thenReturn(notificationsController0)
        whenever(notificationControllerFactory.create(eq(1))).thenReturn(notificationsController1)
    }

    @Test
    fun executeScreenshots_fromOverview_honorsDisplay() =
        testScope.runTest {
            val displayId = 1
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = displayId),
            )

            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    displayId = displayId,
                    source = WindowManager.ScreenshotSource.SCREENSHOT_OVERVIEW,
                ),
                onSaved,
                callback,
            )

            val dataCaptor = argumentCaptor<ScreenshotData>()

            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())

            assertThat(dataCaptor.lastValue.displayId).isEqualTo(displayId)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_fromOverviewInvalidDisplay_usesDefault() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = Display.DEFAULT_DISPLAY),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    displayId = 5,
                    source = WindowManager.ScreenshotSource.SCREENSHOT_OVERVIEW,
                ),
                onSaved,
                callback,
            )

            val dataCaptor = argumentCaptor<ScreenshotData>()

            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())

            assertThat(dataCaptor.lastValue.displayId).isEqualTo(Display.DEFAULT_DISPLAY)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_fromScreenCaptureUI_honorsDisplayArgument() =
        testScope.runTest {
            val displayId = 1
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = displayId),
            )
            val request =
                createScreenshotRequest(
                    displayId = displayId,
                    source = WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI,
                )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(request, onSaved, callback)

            val dataCaptor = argumentCaptor<ScreenshotData>()
            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())
            assertThat(dataCaptor.lastValue.displayId).isEqualTo(displayId)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_fromScreenCaptureUI_withInvalidDisplay_usesDefaultDisplay() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = Display.DEFAULT_DISPLAY),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val request =
                createScreenshotRequest(
                    displayId = 5,
                    source = WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI,
                )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(request, onSaved, callback)

            val dataCaptor = argumentCaptor<ScreenshotData>()
            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())
            assertThat(dataCaptor.lastValue.displayId).isEqualTo(Display.DEFAULT_DISPLAY)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_keyOther_usesFocusedDisplay() =
        testScope.runTest {
            val displayId = 1
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = displayId),
            )
            val onSaved = { _: Uri? -> }
            screenshotProxy.stub { onBlocking { getFocusedDisplay() } doReturn displayId }

            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    source = WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER
                ),
                onSaved,
                callback,
            )

            val dataCaptor = argumentCaptor<ScreenshotData>()

            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())

            assertThat(dataCaptor.lastValue.displayId).isEqualTo(displayId)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_keyOtherInvalidDisplay_usesDefault() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = Display.DEFAULT_DISPLAY),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            screenshotProxy.stub {
                onBlocking { getFocusedDisplay() } doReturn 5 // invalid display
            }
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    source = WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER
                ),
                onSaved,
                callback,
            )

            val dataCaptor = argumentCaptor<ScreenshotData>()

            verify(controller).handleScreenshot(dataCaptor.capture(), any(), any())

            assertThat(dataCaptor.lastValue.displayId).isEqualTo(Display.DEFAULT_DISPLAY)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun onDestroy_propagatedToControllers() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            screenshotExecutor.onDestroy()
            verify(controller).onDestroy()
        }

    @Test
    fun removeWindows_propagatedToController() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            screenshotExecutor.removeWindows()
            verify(controller).removeWindow()

            screenshotExecutor.onDestroy()
        }

    @Test
    fun onCloseSystemDialogsReceived_propagatedToController() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            screenshotExecutor.onCloseSystemDialogsReceived()
            verify(controller).requestDismissal(any())

            screenshotExecutor.onDestroy()
        }

    @Test
    fun onCloseSystemDialogsReceived_controllerHasPendingTransitions() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            whenever(controller.isPendingSharedTransition()).thenReturn(true)
            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            screenshotExecutor.onCloseSystemDialogsReceived()
            verify(controller, never()).requestDismissal(any())

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_controllerCalledWithRequestProcessorReturnValue() =
        testScope.runTest {
            setDisplays(display(type = TYPE_INTERNAL, id = 0))
            val screenshotRequest = createScreenshotRequest()
            val toBeReturnedByProcessor = ScreenshotData.forTesting()
            requestProcessor.toReturn = toBeReturnedByProcessor

            val onSaved = { _: Uri? -> }
            screenshotExecutor.executeScreenshots(screenshotRequest, onSaved, callback)

            assertThat(requestProcessor.processed)
                .isEqualTo(ScreenshotData.fromRequest(screenshotRequest))

            val capturer = argumentCaptor<ScreenshotData>()
            verify(controller).handleScreenshot(capturer.capture(), any(), any())
            assertThat(capturer.lastValue).isEqualTo(toBeReturnedByProcessor)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_errorFromProcessorOnDefaultDisplay_showsErrorNotification() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            requestProcessor.shouldThrowException = true

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            verify(notificationsController0).notifyScreenshotError(any<Int>())
            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_errorFromProcessorOnSecondaryDisplay_showsErrorNotification() =
        testScope.runTest {
            setDisplays(display(type = TYPE_INTERNAL, id = 0))
            val onSaved = { _: Uri? -> }
            requestProcessor.shouldThrowException = true

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            verify(notificationsController0).notifyScreenshotError(any<Int>())
            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_errorFromScreenshotController_reportsRequested() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            whenever(controller.handleScreenshot(any(), any(), any()))
                .thenThrow(IllegalStateException::class.java)

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            val screenshotRequested =
                eventLogger.logs.filter {
                    it.eventId == ScreenshotEvent.SCREENSHOT_REQUESTED_KEY_OTHER.id
                }
            assertThat(screenshotRequested).hasSize(1)
            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_errorFromScreenshotController_reportsError() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            whenever(controller.handleScreenshot(any(), any(), any()))
                .thenThrow(IllegalStateException::class.java)

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            val screenshotRequested =
                eventLogger.logs.filter {
                    it.eventId == ScreenshotEvent.SCREENSHOT_CAPTURE_FAILED.id
                }
            assertThat(screenshotRequested).hasSize(1)
            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_errorFromScreenshotController_showsErrorNotification() =
        testScope.runTest {
            setDisplays(
                display(type = TYPE_INTERNAL, id = 0),
                display(type = TYPE_EXTERNAL, id = 1),
            )
            val onSaved = { _: Uri? -> }
            whenever(controller.handleScreenshot(any(), any(), any()))
                .thenThrow(IllegalStateException::class.java)

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)

            verify(notificationsController0).notifyScreenshotError(any<Int>())
            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_finisherCalledWithNullUri_succeeds() =
        testScope.runTest {
            setDisplays(display(type = TYPE_INTERNAL, id = 0))
            var onSavedCallCount = 0
            val onSaved: (Uri?) -> Unit = {
                assertThat(it).isNull()
                onSavedCallCount += 1
            }
            whenever(controller.handleScreenshot(any(), any(), any())).thenAnswer {
                (it.getArgument(1) as Consumer<Uri?>).accept(null)
            }

            screenshotExecutor.executeScreenshots(createScreenshotRequest(), onSaved, callback)
            assertThat(onSavedCallCount).isEqualTo(1)

            screenshotExecutor.onDestroy()
        }

    @Test
    fun executeScreenshots_consecutiveRequestsOnDifferentDisplays() =
        testScope.runTest {
            val secondaryDisplay = display(type = TYPE_EXTERNAL, id = 1)
            var focusedDisplay = Display.DEFAULT_DISPLAY
            setDisplays(
                display(type = TYPE_INTERNAL, id = Display.DEFAULT_DISPLAY),
                secondaryDisplay,
            )
            screenshotProxy.stub {
                onBlocking { getFocusedDisplay() }.thenAnswer { focusedDisplay }
            }

            val secondaryController = mock<ScreenshotController>()
            whenever(controllerFactory.create(eq(secondaryDisplay))).thenReturn(secondaryController)

            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    source = WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER
                ),
                { _: Uri? -> },
                callback,
            )

            verify(controller).handleScreenshot(any(), any(), any())
            verify(secondaryController, never()).handleScreenshot(any(), any(), any())

            // Now input focus moves to secondary display.
            focusedDisplay = secondaryDisplay.displayId
            screenshotExecutor.executeScreenshots(
                createScreenshotRequest(
                    source = WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER
                ),
                { _: Uri? -> },
                callback,
            )

            // Destroy the old controller, send screenshot to the secondary display one.
            verify(controller).onDestroy()
            verify(secondaryController).handleScreenshot(any(), any(), any())

            screenshotExecutor.onDestroy()
        }

    private suspend fun TestScope.setDisplays(vararg displays: Display) {
        fakeDisplayRepository.emit(displays.toSet())
        displays.forEach { whenever(displayManager.getDisplay(it.displayId)).thenReturn(it) }
        runCurrent()
    }

    private fun createScreenshotRequest(
        type: Int = WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
        source: Int = WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) =
        ScreenshotRequest.Builder(type, source)
            .setTopComponent(topComponent)
            .setDisplayId(displayId)
            .also {
                if (type == TAKE_SCREENSHOT_PROVIDED_IMAGE) {
                    it.setBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
                }
            }
            .build()

    private class FakeRequestProcessor : ScreenshotRequestProcessor {
        var processed: ScreenshotData? = null
        var toReturn: ScreenshotData? = null
        var shouldThrowException = false

        override suspend fun process(screenshot: ScreenshotData): ScreenshotData {
            if (shouldThrowException) throw RequestProcessorException("")
            processed = screenshot
            return toReturn ?: screenshot
        }
    }
}
