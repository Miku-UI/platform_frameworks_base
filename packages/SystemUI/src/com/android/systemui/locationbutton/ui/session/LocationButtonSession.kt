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
package com.android.systemui.locationbutton.ui.session

import android.Manifest
import android.app.PendingIntent
import android.app.permissionui.ILocationButtonClient
import android.app.permissionui.ILocationButtonSession
import android.app.permissionui.LocationButtonClient
import android.app.permissionui.LocationButtonRequest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelableException
import android.os.Process
import android.os.RemoteCallback
import android.os.RemoteException
import android.os.Trace
import android.os.UserHandle
import android.util.Log
import android.util.Slog
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.window.TrustedPresentationThresholds
import androidx.compose.ui.platform.ComposeView
import com.android.internal.graphics.ColorUtils
import com.android.systemui.locationbutton.domain.interactor.LocationButtonInteractor
import com.android.systemui.locationbutton.ui.compose.LocationButton
import com.android.systemui.locationbutton.ui.view.LocationButtonRootView
import com.android.systemui.locationbutton.ui.viewmodel.LocationButtonViewModel
import com.android.systemui.shared.system.SysUiStatsLog
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Manages a single location button session, handling client communication and lifecycle.
 *
 * This class is the server-side implementation of a location button session. It is created by
 * `com.android.systemui.locationbutton.LocationButtonRenderService` for each new session request.
 * Its primary responsibilities include:
 * * **IPC Handling:** Implements the [ILocationButtonSession.Stub] interface to handle remote calls
 *   from the client.
 * * **Remote Rendering:** Uses a [SurfaceControlViewHost] to render the location button into the
 *   client application's view hierarchy.
 * * **Security:** Registers a [android.window.TrustedPresentationListener] to monitor the button's
 *   visibility and detect potential occlusion.
 * * **Client Lifecycle:** Implements [IBinder.DeathRecipient] to listen for the client process's
 *   death, ensuring proper cleanup.
 * * **View Management:** Delegates view creation to [setupComposeView], which hosts the
 *   [LocationButton] composable. State is managed via the [LocationButtonInteractor] and
 *   [LocationButtonViewModel].
 *
 * @param context The SystemUI context.
 * @param hostToken The client's host token for [SurfaceControlViewHost].
 * @param displayId The ID of the display to render on.
 * @param packageName The package name of the client (currently unused, but available).
 * @param request The initial [LocationButtonRequest] containing size and style info.
 * @param locationButtonClient The AIDL client interface for callbacks (e.g., error).
 * @param executor The main executor to run operations on.
 * @param interactor The business logic layer for managing button state.
 * @param viewModelFactory The factory to create the [LocationButtonViewModel].
 * @param sessionId A unique ID for this session.
 * @see com.android.systemui.locationbutton.LocationButtonRenderService
 */
class LocationButtonSession(
    val context: Context,
    hostToken: IBinder,
    val displayId: Int,
    val packageName: String,
    val packageUid: Int,
    request: LocationButtonRequest,
    val locationButtonClient: ILocationButtonClient,
    val executor: Executor,
    private val interactor: LocationButtonInteractor,
    private val viewModelFactory: LocationButtonViewModel.Factory,
    private val sessionId: Int,
) : ILocationButtonSession.Stub(), IBinder.DeathRecipient {
    val displayManager = context.getSystemService(DisplayManager::class.java)!!
    private val windowManager = context.getSystemService(WindowManager::class.java)!!

    private val surfaceControlViewHost: SurfaceControlViewHost

    // Service set up this listener to track active sessions.
    private var onLocationButtonSessionCloseListener: OnLocationButtonSessionCloseListener? = null
    var isActive = false
    private var isButtonUiInTrustedState = false

    private val trustedPresentationListener =
        Consumer<Boolean> { inTrustedPresentationState ->
            if (DEBUG) {
                Slog.d(LOG_TAG, "TPL callback for session $sessionId: $inTrustedPresentationState")
            }
            isButtonUiInTrustedState = inTrustedPresentationState
        }

    init {
        if (DEBUG) {
            Slog.d(LOG_TAG, "Creating new location button session $sessionId for request: $request")
        }
        val display = displayManager.getDisplay(displayId)
        requireNotNull(display) { "Invalid display Id, display must not be null." }
        val displayContext = context.createDisplayContext(display)
        interactor.setButtonState(
            sessionId,
            request,
            displayContext.resources.displayMetrics.density,
        )
        surfaceControlViewHost = SurfaceControlViewHost(context, display, hostToken)
        setupComposeView()
        registerTrustedPresentationListener()
        linkToDeath()
        logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__OPEN_SESSION)
    }

    private fun logAtom(action: Int, result: Int = 0) {
        val model =
            interactor.getButtonState(sessionId)
                ?: run {
                    Slog.w(LOG_TAG, "model not found for session $sessionId")
                    return
                }
        SysUiStatsLog.write(
            SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED,
            packageUid,
            sessionId,
            action,
            result,
            model.textType,
            model.validationFlags,
        )
    }

    private fun setupComposeView() {
        Trace.beginSection("LocationButtonSession#setupComposeView")
        try {
            val buttonModel = interactor.getButtonState(sessionId) ?: return
            val rootView = LocationButtonRootView(context)
            val composeView = ComposeView(context)
            rootView.addView(composeView)
            composeView.setContent {
                LocationButton(
                    viewModelFactory = viewModelFactory,
                    sessionId = sessionId,
                    onClick = { handleLocationButtonClick() },
                )
            }
            surfaceControlViewHost.setView(rootView, buttonModel.width, buttonModel.height)
        } finally {
            Trace.endSection()
        }
    }

    private fun linkToDeath() {
        try {
            locationButtonClient.asBinder().linkToDeath(this, 0)
            isActive = true
        } catch (ex: RemoteException) {
            Slog.e(LOG_TAG, "Client died, closing new session.", ex)
            close()
        }
    }

    val surfacePackage = surfaceControlViewHost.surfacePackage!!

    private fun handleLocationButtonClick() {
        Trace.beginSection("LocationButtonSession#handleLocationButtonClick")
        try {
            if (!isButtonUiInTrustedState) {
                Slog.w(LOG_TAG, "Location button clicked, but ui state not trusted.")
                logAtom(
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__BUTTON_CLICKED,
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__RESULT__REJECTED_UNTRUSTED,
                )
                return
            }
            if (DEBUG) {
                Slog.d(LOG_TAG, "Location button clicked...")
            }
            if (
                context.checkPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Process.INVALID_PID,
                    packageUid,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                logAtom(
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__BUTTON_CLICKED,
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__RESULT__ALREADY_GRANTED,
                )
                try {
                    locationButtonClient.onPermissionsResult(true)
                } catch (e: RemoteException) {
                    Slog.e(
                        LOG_TAG,
                        "Client died or failed to send permission result, close session.",
                        e,
                    )
                    close()
                }
                return
            }
            logAtom(
                SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__BUTTON_CLICKED,
                SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__RESULT__SUCCESS_TRUSTED,
            )

            val remoteCallback = RemoteCallback { bundle ->
                val isPermissionGranted =
                    bundle?.getBoolean(LocationButtonClient.EXTRA_PERMISSION_RESULT) ?: false
                logAtom(
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__BUTTON_CLICKED,
                    if (isPermissionGranted) {
                        SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__RESULT__PERMISSION_GRANTED
                    } else {
                        SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__RESULT__PERMISSION_DENIED
                    },
                )
                try {
                    locationButtonClient.onPermissionsResult(isPermissionGranted)
                } catch (e: RemoteException) {
                    Slog.e(
                        LOG_TAG,
                        "Client died or failed to send permission result, close session.",
                        e,
                    )
                    close()
                }
            }

            val userHandle = UserHandle.getUserHandleForUid(packageUid)
            val userContext = context.createContextAsUser(userHandle, /* flags= */ 0)

            val intent =
                Intent(LocationButtonClient.ACTION_REQUEST_LOCATION_BUTTON_PERMISSIONS).apply {
                    setPackage(context.packageManager.permissionControllerPackageName)
                    putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(Intent.EXTRA_REMOTE_CALLBACK, remoteCallback)
                }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            val pendingIntent =
                PendingIntent.getActivity(userContext, /* requestCode */ 0, intent, flags)
            try {
                locationButtonClient.onRequestPermissions(pendingIntent)
            } catch (e: RemoteException) {
                Slog.e(
                    LOG_TAG,
                    "Client died or failed to respond on button click, close session.",
                    e,
                )
                close()
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun registerTrustedPresentationListener() {
        val trustedPresentationThresholds =
            TrustedPresentationThresholds(
                MIN_ALPHA_FOR_TRUSTED_PRESENTATION,
                MIN_FRACTION_RENDERED_FOR_TRUSTED_PRESENTATION,
                TRUSTED_PRESENTATION_STABILITY_MS,
            )
        windowManager.registerTrustedPresentationListener(
            surfaceControlViewHost.windowToken.asBinder(),
            trustedPresentationThresholds,
            executor,
            trustedPresentationListener,
        )
    }

    override fun setCornerRadius(cornerRadius: Float) {
        executor.execute {
            if (DEBUG) {
                Slog.d(LOG_TAG, "setCornerRadius() for session $sessionId: $cornerRadius")
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setCornerRadius(sessionId, cornerRadius)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_CORNER_RADIUS)
        }
    }

    override fun setPressedCornerRadius(cornerRadius: Float) {
        executor.execute {
            if (DEBUG) {
                Slog.d(LOG_TAG, "setPressedCornerRadius() for session $sessionId: $cornerRadius")
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setPressedCornerRadius(sessionId, cornerRadius)
            logAtom(
                SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_PRESSED_CORNER_RADIUS
            )
        }
    }

    override fun setBackgroundColor(color: Int) {
        if (DEBUG) {
            Slog.d(
                LOG_TAG,
                "setBackgroundColor() for session $sessionId: #${Integer.toHexString(color)}",
            )
        }
        executor.execute {
            if (!ensureActiveSession()) {
                return@execute
            }
            // For security, ensure the background is always opaque.
            val opaqueBackgroundColor = ColorUtils.setAlphaComponent(color, 255)
            interactor.setBackgroundColor(sessionId, opaqueBackgroundColor)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_BACKGROUND_COLOR)
        }
    }

    override fun setTextColor(textColor: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(
                    LOG_TAG,
                    "setTextColor() for session $sessionId: #${Integer.toHexString(textColor)}",
                )
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setTextColor(sessionId, textColor)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_TEXT_COLOR)
        }
    }

    override fun setIconTint(color: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(
                    LOG_TAG,
                    "setIconTint() for session $sessionId: #${Integer.toHexString(color)}",
                )
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setIconTint(sessionId, color)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_ICON_TINT)
        }
    }

    override fun setTextType(textType: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(LOG_TAG, "setTextType() for session $sessionId: $textType")
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setTextType(sessionId, textType)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_TEXT_TYPE)
        }
    }

    override fun resize(width: Int, height: Int) {
        executor.execute {
            Trace.beginSection("LocationButtonSession#resize")
            try {
                if (DEBUG) {
                    Slog.d(LOG_TAG, "resize() called for session $sessionId: $width x $height")
                }
                if (!ensureActiveSession()) {
                    return@execute
                }
                interactor.setSize(sessionId, width, height)
                val model = interactor.getButtonState(sessionId) ?: return@execute
                if (DEBUG) {
                    Slog.d(
                        LOG_TAG,
                        "relayout() session $sessionId to validated size: ${model.width} x ${model.height}",
                    )
                }
                surfaceControlViewHost.relayout(model.width, model.height)
                logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__RESIZE)
            } finally {
                Trace.endSection()
            }
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(LOG_TAG, "setPadding() for session $sessionId: $left, $top, $right, $bottom")
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setPadding(sessionId, left, top, right, bottom)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_PADDING)
        }
    }

    override fun setStrokeColor(color: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(
                    LOG_TAG,
                    "setStrokeColor() for session $sessionId: #${Integer.toHexString(color)}",
                )
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setStrokeColor(sessionId, color)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_STROKE_COLOR)
        }
    }

    override fun setStrokeWidth(width: Int) {
        executor.execute {
            if (DEBUG) {
                Slog.d(LOG_TAG, "setStrokeWidth() for session $sessionId: $width")
            }
            if (!ensureActiveSession()) {
                return@execute
            }
            interactor.setStrokeWidth(sessionId, width)
            logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__SET_STROKE_WIDTH)
        }
    }

    override fun changeConfiguration(newConfig: Configuration) {
        executor.execute {
            Trace.beginSection("LocationButtonSession#changeConfiguration")
            try {
                if (DEBUG) {
                    Slog.d(LOG_TAG, "changeConfiguration() for session $sessionId: $newConfig")
                }
                if (!ensureActiveSession()) {
                    return@execute
                }
                val display = displayManager.getDisplay(displayId)
                val displayContext = context.createDisplayContext(display)
                interactor.setConfiguration(
                    sessionId,
                    newConfig,
                    displayContext.resources.displayMetrics.density,
                )
                logAtom(
                    SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__CHANGE_CONFIGURATION
                )
            } finally {
                Trace.endSection()
            }
        }
    }

    override fun close() {
        executor.execute {
            Trace.beginSection("LocationButtonSession#close")
            try {
                if (!isActive) {
                    return@execute
                }
                logAtom(SysUiStatsLog.LOCATION_BUTTON_SESSION_REPORTED__ACTION__CLOSE_SESSION)
                isActive = false
                windowManager.unregisterTrustedPresentationListener(trustedPresentationListener)
                surfaceControlViewHost.release()
                try {
                    locationButtonClient.asBinder().unlinkToDeath(this, 0)
                } catch (_: NoSuchElementException) {
                    // ignore
                }
                interactor.removeButtonState(sessionId)
                onLocationButtonSessionCloseListener?.onSessionClose(this)
            } finally {
                Trace.endSection()
            }
        }
    }

    private fun ensureActiveSession(): Boolean {
        if (!isActive) {
            Slog.w(LOG_TAG, "Session is already closed, can't use a closed session.")
            try {
                locationButtonClient.onSessionError(
                    ParcelableException(
                        IllegalStateException(
                            "Attempted to use a session that has already been closed."
                        )
                    )
                )
            } catch (e: RemoteException) {
                Slog.w(
                    LOG_TAG,
                    "ensureActiveSession: Failed to notify client of closed session.",
                    e,
                )
            }
            return false
        }
        return true
    }

    override fun binderDied() {
        close()
    }

    fun setOnLocationButtonSessionCloseListener(listener: OnLocationButtonSessionCloseListener?) {
        onLocationButtonSessionCloseListener = listener
    }

    fun interface OnLocationButtonSessionCloseListener {
        fun onSessionClose(locationButtonSession: LocationButtonSession)
    }

    companion object {
        private const val LOG_TAG = "LocationButtonSession"
        private val DEBUG = Build.IS_DEBUGGABLE || Log.isLoggable(LOG_TAG, Log.DEBUG)

        /** The minimum alpha the button must have to be considered trusted. 95% opaque. */
        private const val MIN_ALPHA_FOR_TRUSTED_PRESENTATION = 0.95f

        /** The minimum fraction of the button that must be visible. 95% visible. */
        private const val MIN_FRACTION_RENDERED_FOR_TRUSTED_PRESENTATION = 0.95f

        /** The minimum time in ms the button must be stable to be considered trusted. */
        private const val TRUSTED_PRESENTATION_STABILITY_MS = 200
    }
}
