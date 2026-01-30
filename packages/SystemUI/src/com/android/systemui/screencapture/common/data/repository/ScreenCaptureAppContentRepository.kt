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
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Repository storing information about app content available for Screen Capture sessions. */
interface ScreenCaptureAppContentRepository {
    /**
     * The currently available app content for the given [packageName] and [user].
     *
     * Thumbnails will be fetched at the given [thumbnailWidthPx] and [thumbnailHeightPx].
     */
    fun appContentsFor(
        packageName: String,
        user: UserHandle,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
    ): Flow<Result<List<MediaProjectionAppContent>>>
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
    ): Flow<Result<List<MediaProjectionAppContent>>> =
        conflatedCallbackFlow {
                val serviceConnection =
                    makeServiceConnection(thumbnailWidthPx, thumbnailHeightPx) { trySend(it) }

                val intent =
                    Intent(AppContentProjectionService.SERVICE_INTERFACE).setPackage(packageName)

                val bound =
                    context.bindServiceAsUser(
                        /* service= */ intent,
                        /* conn= */ serviceConnection,
                        /* flags= */ Context.BIND_AUTO_CREATE,
                        /* user= */ user,
                    )

                if (!bound) {
                    val errMsg = "Failed to bind service"
                    Log.w(TAG, errMsg)
                    context.unbindService(serviceConnection)
                    send(Result.failure(IllegalStateException(errMsg)))
                }

                awaitClose { if (bound) context.unbindService(serviceConnection) }
            }
            .flowOn(bgContext)

    private fun makeServiceConnection(
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        onAppContents: (Result<List<MediaProjectionAppContent>>) -> Unit,
    ): ServiceConnection =
        object : ServiceConnection {
            private val listener = RemoteCallback { bundle ->
                if (bundle == null) return@RemoteCallback

                val appContents =
                    bundle
                        .getParcelableArray(
                            AppContentProjectionService.EXTRA_APP_CONTENT,
                            MediaProjectionAppContent::class.java,
                        )
                        ?.toList()

                if (appContents == null) return@RemoteCallback

                onAppContents(Result.success(appContents))
            }

            private var callback: IAppContentProjectionCallback? = null

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                callback =
                    IAppContentProjectionCallback.Stub.asInterface(service).also {
                        if (it == null) {
                            val errMsg = "Invalid service IBinder: $service"
                            Log.w(TAG, errMsg)
                            context.unbindService(this)
                            onAppContents(Result.failure(IllegalArgumentException(errMsg)))
                            return
                        }

                        scope.launch(bgContext) {
                            try {
                                it.onContentRequest(listener, thumbnailWidthPx, thumbnailHeightPx)
                            } catch (e: RemoteException) {
                                Log.e(TAG, "App content request failed", e)
                                onAppContents(Result.failure(e))
                            }
                        }
                    }
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
