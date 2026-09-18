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

package com.android.systemui.screencapture.common.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.AppContentProjectionService
import android.media.projection.IAppContentProjectionCallback
import android.media.projection.MediaProjectionAppContent
import android.os.IBinder
import android.os.RemoteCallback
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Data class representing the app content available for screen capture. */
data class RawAppContent(
    /** The list of the app content that can be shared. */
    val contents: List<MediaProjectionAppContent>,
    /**
     * The projection callback that can start the session. This is a [WeakReference] because the
     * callback is a Binder object tied to a service connection. Using a weak reference prevents
     * consumers of this data class from accidentally causing a memory leak by holding a strong
     * reference to the Binder proxy after the connection has been torn down.
     */
    val projectionCallback: WeakReference<IAppContentProjectionCallback>,
)

/** Repository storing information about app content available for Screen Capture sessions. */
interface ScreenCaptureAppContentRepository {
    /**
     * The currently available app content for the given [packageName] and [user].
     *
     * Thumbnails will be fetched at the given [thumbnailWidthPx] and [thumbnailHeightPx]. Icons
     * will be fetched at the given [iconSizePx].
     */
    fun appContentsFor(
        packageName: String,
        user: UserHandle,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        iconSizePx: Int,
    ): Flow<Result<RawAppContent>>
}

/**
 * Default implementation of [ScreenCaptureAppContentRepository].
 *
 * Fetches app content using [AppContentProjectionService].
 */
@ScreenCaptureUiScope
class ScreenCaptureAppContentRepositoryImpl
@Inject
constructor(
    @ScreenCaptureUi private val scope: CoroutineScope,
    @Background private val bgContext: CoroutineContext,
    private val context: Context,
) : ScreenCaptureAppContentRepository {

    override fun appContentsFor(
        packageName: String,
        user: UserHandle,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        iconSizePx: Int,
    ): Flow<Result<RawAppContent>> =
        flow {
                val intent =
                    Intent(AppContentProjectionService.SERVICE_INTERFACE).setPackage(packageName)

                val resolveInfo =
                    context.packageManager.queryIntentServicesAsUser(intent, /* flags= */ 0, user)

                val serviceInfo = resolveInfo.firstOrNull { it.serviceInfo != null }?.serviceInfo

                if (serviceInfo == null) {
                    emit(
                        Result.failure(
                            IllegalStateException(
                                "Package: $packageName does not declare an AppContentProjectionService"
                            )
                        )
                    )
                    return@flow
                }

                if (!serviceInfo.exported) {
                    emit(
                        Result.failure(
                            IllegalStateException(
                                "Service ${serviceInfo.name} in $packageName is not exported"
                            )
                        )
                    )
                    return@flow
                }

                if (serviceInfo.permission != Manifest.permission.MANAGE_MEDIA_PROJECTION) {
                    emit(
                        Result.failure(
                            IllegalStateException(
                                "Service ${serviceInfo.name} in $packageName is not protected by MANAGE_MEDIA_PROJECTION"
                            )
                        )
                    )
                    return@flow
                }

                intent.setComponent(ComponentName(serviceInfo.packageName, serviceInfo.name))

                emitAll(
                    conflatedCallbackFlow {
                        val serviceConnection =
                            makeServiceConnection(
                                producerScope = this,
                                thumbnailWidthPx,
                                thumbnailHeightPx,
                                iconSizePx,
                            )
                        val bound =
                            context.bindServiceAsUser(
                                /* service= */ intent,
                                /* conn= */ serviceConnection,
                                /* flags= */ Context.BIND_AUTO_CREATE,
                                /* user= */ user,
                            )

                        if (!bound) {
                            val errMsg = "Failed to bind service: $packageName"
                            Log.w(TAG, errMsg)
                            trySend(Result.failure(IllegalStateException(errMsg)))
                            close()
                        }

                        awaitClose { context.unbindService(serviceConnection) }
                    }
                )
            }
            .flowOn(bgContext)

    private fun makeServiceConnection(
        producerScope: ProducerScope<Result<RawAppContent>>,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        iconSizePx: Int,
    ): ServiceConnection =
        object : ServiceConnection {
            private var callback: IAppContentProjectionCallback? = null

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val projectionCallback = IAppContentProjectionCallback.Stub.asInterface(service)
                if (projectionCallback == null) {
                    val errMsg = "Invalid service IBinder: $service"
                    Log.w(TAG, errMsg)
                    producerScope.trySend(Result.failure(IllegalArgumentException(errMsg)))
                    producerScope.close()
                    return
                }
                callback = projectionCallback

                val listener = RemoteCallback { bundle ->
                    if (bundle == null) return@RemoteCallback

                    val appContents =
                        bundle
                            .getParcelableArray(
                                AppContentProjectionService.EXTRA_APP_CONTENT,
                                MediaProjectionAppContent::class.java,
                            )
                            ?.toList()

                    if (appContents == null) return@RemoteCallback

                    producerScope.trySend(
                        Result.success(
                            RawAppContent(
                                contents = appContents,
                                projectionCallback = WeakReference(projectionCallback),
                            )
                        )
                    )
                }

                scope.launch(bgContext) {
                    try {
                        projectionCallback.onContentRequest(
                            listener,
                            thumbnailWidthPx,
                            thumbnailHeightPx,
                            iconSizePx,
                            iconSizePx,
                        )
                    } catch (e: RemoteException) {
                        Log.e(TAG, "App content request failed", e)
                        producerScope.trySend(Result.failure(e))
                    }
                }
            }

            override fun onBindingDied(name: ComponentName?) {
                val errMsg = "Binding died for $name"
                Log.w(TAG, errMsg)
                producerScope.trySend(Result.failure(IllegalStateException(errMsg)))
                producerScope.close()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                callback?.let {
                    scope.launch(bgContext) {
                        try {
                            it.onSessionStopped()
                        } catch (e: RemoteException) {
                            Log.e(TAG, "App content stop session failed", e)
                        }
                    }
                }
                callback = null
            }
        }
}

private const val TAG = "AppContentRepository"
