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

import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Log
import android.view.Display
import android.view.WindowManager.ScreenshotSource
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.ScreenshotRequest
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_CAPTURE_FAILED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_DISMISSED_OTHER
import com.android.systemui.screenshot.TakeScreenshotService.RequestCallback
import com.android.systemui.screenshot.proxy.ScreenshotProxy
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

interface TakeScreenshotExecutor {
    suspend fun executeScreenshots(
        screenshotRequest: ScreenshotRequest,
        onSaved: (Uri?) -> Unit,
        requestCallback: RequestCallback,
    )

    fun onCloseSystemDialogsReceived()

    fun removeWindows()

    fun onDestroy()

    fun executeScreenshotsAsync(
        screenshotRequest: ScreenshotRequest,
        onSaved: Consumer<Uri?>,
        requestCallback: RequestCallback,
    )
}

interface ScreenshotHandler {
    fun handleScreenshot(
        screenshot: ScreenshotData,
        finisher: Consumer<Uri?>,
        requestCallback: RequestCallback,
    )
}

/**
 * Receives the signal to take a screenshot from [TakeScreenshotService], and calls back with the
 * result.
 *
 * Captures a screenshot for each [Display] available.
 */
@SysUISingleton
class TakeScreenshotExecutorImpl
@Inject
constructor(
    private val interactiveScreenshotHandlerFactory: InteractiveScreenshotHandler.Factory,
    private val displayManager: DisplayManager,
    @Application private val mainScope: CoroutineScope,
    private val screenshotRequestProcessor: ScreenshotRequestProcessor,
    private val uiEventLogger: UiEventLogger,
    private val screenshotNotificationControllerFactory: ScreenshotNotificationsController.Factory,
    private val screenshotProxy: ScreenshotProxy,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : TakeScreenshotExecutor {
    private var screenshotController: InteractiveScreenshotHandler? = null
    private val notificationControllers = mutableMapOf<Int, ScreenshotNotificationsController>()

    /**
     * Executes the [ScreenshotRequest].
     *
     * [onSaved] is invoked only on the default display result. [RequestCallback.onFinish] is
     * invoked only when both screenshot UIs are removed.
     */
    override suspend fun executeScreenshots(
        screenshotRequest: ScreenshotRequest,
        onSaved: (Uri?) -> Unit,
        requestCallback: RequestCallback,
    ) {
        val display = getDisplayToScreenshot(screenshotRequest)
        val screenshotHandler = getScreenshotController(display)
        dispatchToController(
            screenshotHandler,
            ScreenshotData.fromRequest(screenshotRequest, display.displayId),
            onSaved,
            requestCallback,
        )
    }

    /** All logging should be triggered only by this method. */
    private suspend fun dispatchToController(
        screenshotHandler: ScreenshotHandler,
        rawScreenshotData: ScreenshotData,
        onSaved: (Uri?) -> Unit,
        callback: RequestCallback,
    ) {
        // Let's wait before logging "screenshot requested", as we should log the processed
        // ScreenshotData.
        val screenshotData =
            runCatching { screenshotRequestProcessor.process(rawScreenshotData) }
                .onFailure {
                    Log.e(TAG, "Failed to process screenshot request!", it)
                    logScreenshotRequested(rawScreenshotData)
                    onFailedScreenshotRequest(rawScreenshotData, callback)
                }
                .getOrNull() ?: return

        logScreenshotRequested(screenshotData)
        Log.d(TAG, "Screenshot request: $screenshotData")
        try {
            screenshotHandler.handleScreenshot(screenshotData, onSaved, callback)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error while ScreenshotController was handling ScreenshotData!", e)
            onFailedScreenshotRequest(screenshotData, callback)
            return // After a failure log, nothing else should run.
        }
    }

    /**
     * This should be logged also in case of failed requests, before the [SCREENSHOT_CAPTURE_FAILED]
     * event.
     */
    private fun logScreenshotRequested(screenshotData: ScreenshotData) {
        uiEventLogger.log(
            ScreenshotEvent.getScreenshotSource(screenshotData.source),
            0,
            screenshotData.packageNameString,
        )
    }

    private fun onFailedScreenshotRequest(
        screenshotData: ScreenshotData,
        callback: RequestCallback,
    ) {
        uiEventLogger.log(SCREENSHOT_CAPTURE_FAILED, 0, screenshotData.packageNameString)
        getNotificationController(screenshotData.displayId)
            .notifyScreenshotError(R.string.screenshot_failed_to_capture_text)
        callback.reportError()
    }

    // Return the single display to be screenshot based upon the request.
    private suspend fun getDisplayToScreenshot(screenshotRequest: ScreenshotRequest): Display {
        return when (screenshotRequest.source) {
            // For screenshots from Overview or the Screen Capture UI, use the display where the UI
            // was shown, if available.
            ScreenshotSource.SCREENSHOT_OVERVIEW,
            ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI ->
                displayManager.getDisplay(screenshotRequest.displayId)
                    ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: error("Can't find default display")

            // Key chord and vendor gesture occur on the device itself, so screenshot the device's
            // display
            ScreenshotSource.SCREENSHOT_KEY_CHORD,
            ScreenshotSource.SCREENSHOT_VENDOR_GESTURE ->
                displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: error("Can't find default display")

            // All other invocations use the focused display
            else -> {
                val focusedDisplay = getFocusedDisplay()
                Log.i(TAG, "Focused display ID is $focusedDisplay")
                displayManager.getDisplay(focusedDisplay)
                    ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: error("Can't find default display")
            }
        }
    }

    /** Propagates the close system dialog signal to the ScreenshotController. */
    override fun onCloseSystemDialogsReceived() {
        if (screenshotController?.isPendingSharedTransition() == false) {
            screenshotController?.requestDismissal(SCREENSHOT_DISMISSED_OTHER)
        }
    }

    /** Removes all screenshot related windows. */
    override fun removeWindows() {
        screenshotController?.removeWindow()
    }

    /**
     * Destroys the executor. Afterwards, this class is not expected to work as intended anymore.
     */
    override fun onDestroy() {
        screenshotController?.onDestroy()
        screenshotController = null
    }

    private suspend fun getFocusedDisplay() =
        withContext(backgroundDispatcher) { screenshotProxy.getFocusedDisplay() }

    private fun getNotificationController(id: Int): ScreenshotNotificationsController {
        return notificationControllers.computeIfAbsent(id) {
            screenshotNotificationControllerFactory.create(id)
        }
    }

    /** For java compatibility only. see [executeScreenshots] */
    override fun executeScreenshotsAsync(
        screenshotRequest: ScreenshotRequest,
        onSaved: Consumer<Uri?>,
        requestCallback: RequestCallback,
    ) {
        mainScope.launch {
            executeScreenshots(screenshotRequest, { uri -> onSaved.accept(uri) }, requestCallback)
        }
    }

    private fun getScreenshotController(display: Display): InteractiveScreenshotHandler {

        if (screenshotController?.getDisplay() != display) {
            // New request is from a different display, throw out the old UI so we can instantiate a
            // new one.
            screenshotController?.onDestroy()
            screenshotController = null
        }
        val controller = screenshotController ?: interactiveScreenshotHandlerFactory.create(display)
        screenshotController = controller
        return controller
    }

    private companion object {
        val TAG = LogConfig.logTag(TakeScreenshotService::class.java)
    }
}
