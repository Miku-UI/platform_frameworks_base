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
package com.android.systemui.locationbutton

import android.app.Service
import android.app.permissionui.ILocationButtonClient
import android.app.permissionui.ILocationButtonService
import android.app.permissionui.LocationButtonRequest
import android.app.permissionui.LocationButtonSessionResponse
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelableException
import android.os.RemoteException
import android.os.UserHandle
import android.permission.flags.Flags
import android.util.Log
import android.util.Slog
import com.android.internal.util.LatencyTracker
import com.android.server.ServiceThread
import com.android.systemui.locationbutton.domain.interactor.LocationButtonInteractor
import com.android.systemui.locationbutton.ui.session.LocationButtonSession
import com.android.systemui.locationbutton.ui.session.LocationButtonSession.OnLocationButtonSessionCloseListener
import com.android.systemui.locationbutton.ui.viewmodel.LocationButtonViewModel
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * A service that renders the location button.
 *
 * <p> The location button, when tapped by the user, grants the application temporary access to the
 * device's precise location. By rendering the button in a trusted process, the system ensures the
 * integrity of the button and prevents tap-jacking or other malicious interactions.
 *
 * <p>Clients, typically from a Jetpack library, bind to this service and use {@link
 * ILocationButtonService#openSession} to create a new location button session. Each session is
 * represented by a [LocationButtonSession] instance, which manages the lifecycle and communication
 * for a single location button.
 *
 * <p>All operations, including session management and view rendering, are performed on a dedicated
 * thread [LocationButtonThread] to avoid blocking the main thread.
 *
 * @see com.android.systemui.locationbutton.ui.session.LocationButtonSession
 * @see android.app.permissionui.LocationButtonProvider
 * @see android.app.permissionui.ILocationButtonService
 */
class LocationButtonRenderService
@Inject
constructor(
    private val interactor: LocationButtonInteractor,
    private val viewModelFactory: LocationButtonViewModel.Factory,
    private val latencyTracker: LatencyTracker,
) : Service() {
    private val activeLocationButtonSessions = mutableListOf<LocationButtonSession>()

    /** Handles all operations for a location button. */
    private val executor: Executor = LocationButtonThread.getExecutor()
    private var nextSessionId = 0

    private val onLocationButtonSessionCloseListener =
        OnLocationButtonSessionCloseListener { session ->
            activeLocationButtonSessions.remove(session)
        }

    /**
     * Open location button session, and [android.view.SurfaceControlViewHost.SurfacePackage],
     * [LocationButtonSession] handles are returned to clients. [LocationButtonSession] helps client
     * communicate with service-side to change the button appearance.
     */
    private fun openSession(
        hostPackageName: String,
        hostPackageUid: Int,
        hostToken: IBinder,
        displayId: Int,
        request: LocationButtonRequest,
        client: ILocationButtonClient,
    ) {
        executor.execute {
            nextSessionId++
            val locationButtonSession =
                LocationButtonSession(
                    this,
                    hostToken,
                    displayId,
                    hostPackageName,
                    hostPackageUid,
                    request,
                    client,
                    executor,
                    interactor,
                    viewModelFactory,
                    nextSessionId,
                )

            if (!locationButtonSession.isActive) {
                latencyTracker.onActionCancel(LatencyTracker.ACTION_LOCATION_BUTTON_OPEN_SESSION)
                return@execute
            }

            try {
                val response =
                    LocationButtonSessionResponse(
                        locationButtonSession,
                        locationButtonSession.surfacePackage,
                    )
                client.onSessionOpened(response)
                locationButtonSession.setOnLocationButtonSessionCloseListener(
                    onLocationButtonSessionCloseListener
                )
                activeLocationButtonSessions.add(locationButtonSession)
                latencyTracker.onActionEnd(LatencyTracker.ACTION_LOCATION_BUTTON_OPEN_SESSION)
            } catch (ex: RemoteException) {
                Slog.e(LOG_TAG, "Client failed to respond, closing new session.", ex)
                locationButtonSession.close()
                latencyTracker.onActionCancel(LatencyTracker.ACTION_LOCATION_BUTTON_OPEN_SESSION)
            }
        }
    }

    private val service: ILocationButtonService =
        object : ILocationButtonService.Stub() {
            override fun openSession(
                packageName: String,
                hostToken: IBinder,
                displayId: Int,
                request: LocationButtonRequest,
                client: ILocationButtonClient,
            ) {
                latencyTracker.onActionStart(LatencyTracker.ACTION_LOCATION_BUTTON_OPEN_SESSION)
                val callerUid = Binder.getCallingUid()
                if (!isValidHostPackage(packageName, callerUid)) {
                    Slog.e(LOG_TAG, "Package name doesn't belong to the caller $callerUid")
                    try {
                        client.onSessionError(
                            ParcelableException(
                                SecurityException("Package name doesn't belong to the caller.")
                            )
                        )
                    } catch (_: RemoteException) {
                        // ignore remote exception
                    }
                    latencyTracker.onActionCancel(
                        LatencyTracker.ACTION_LOCATION_BUTTON_OPEN_SESSION
                    )
                    return
                }
                val identity = Binder.clearCallingIdentity()
                try {
                    this@LocationButtonRenderService.openSession(
                        packageName,
                        callerUid,
                        hostToken,
                        displayId,
                        request,
                        client,
                    )
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            }

            fun isValidHostPackage(packageName: String, callerUid: Int): Boolean {
                try {
                    val userId = UserHandle.getUserId(callerUid)
                    val packageUid = packageManager.getPackageUidAsUser(packageName, userId)
                    return callerUid == packageUid
                } catch (e: PackageManager.NameNotFoundException) {
                    Slog.e(LOG_TAG, "Package name not found." + e.message)
                }
                return false
            }
        }

    override fun onBind(intent: Intent): IBinder? =
        if (Flags.locationButtonEnabled()) service.asBinder() else null

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) {
            Slog.d(LOG_TAG, "Location button service created.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.execute {
            if (DEBUG) {
                Slog.d(
                    LOG_TAG,
                    "Location button service destroyed, " +
                        "cleaning up ${activeLocationButtonSessions.size} sessions.",
                )
            }
            if (activeLocationButtonSessions.isEmpty()) {
                return@execute
            }
            for (session in activeLocationButtonSessions) {
                // Unregister the listener to prevent getting a callback for this close.
                session.setOnLocationButtonSessionCloseListener(null)
                session.close()
            }
            activeLocationButtonSessions.clear()
            interactor.clearRepositoryState()
        }
    }

    /** Location button rendering and all other work for the button is done on this thread only. */
    private object LocationButtonThread :
        ServiceThread("location.button", android.os.Process.THREAD_PRIORITY_FOREGROUND, false) {

        init {
            start()
        }

        fun getExecutor(): Executor = threadExecutor
    }

    companion object {
        private const val LOG_TAG = "LocationButtonService"
        private val DEBUG = Build.IS_DEBUGGABLE || Log.isLoggable(LOG_TAG, Log.DEBUG)
    }
}
