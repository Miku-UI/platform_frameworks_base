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

package com.android.systemui.screencapture.record.smallscreen.player.ui.viewmodel

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.kairos.awaitClose
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive

class VideoPlayerControlsViewModel
@AssistedInject
constructor(
    @Assisted private val mediaPlayer: MediaPlayer,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    val videoDurationMillis: Int
        get() = mediaPlayer.callSafe { duration } ?: 0

    val videoPositionMillis: Int by
        mediaPlayer
            .currentPositionFlow(10.milliseconds)
            .hydratedStateOf("VideoPlayerControlsViewModel#videoPositionMillis", 0)

    val playing: Boolean
        get() = mediaPlayer.callSafe { isPlaying } ?: false

    var muted: Boolean by mutableStateOf(false)
        private set

    private var wasPlayingBeforeSeek: Boolean? = null

    override suspend fun onActivated() {
        coroutineScope {
            mediaPlayer
                .onComplete()
                .onEach { mediaPlayer.callSafe { mediaPlayer.seekTo(0) } }
                .launchIn(this)
        }
    }

    fun updatePlaying(isPlaying: Boolean) {
        mediaPlayer.callSafe {
            if (isPlaying) {
                start()
            } else {
                pause()
            }
        }
    }

    fun seek(positionMillis: Int) {
        mediaPlayer.callSafe {
            if (wasPlayingBeforeSeek == null) {
                wasPlayingBeforeSeek = playing
                updatePlaying(false)
            }
            seekTo(positionMillis.toLong(), MediaPlayer.SEEK_CLOSEST)
        }
    }

    fun seekFinished() {
        wasPlayingBeforeSeek?.let(::updatePlaying)
        wasPlayingBeforeSeek = null
    }

    fun updateMuted(isMuted: Boolean) {
        mediaPlayer.callSafe {
            if (isMuted) {
                setVolume(0f)
            } else {
                setVolume(1f)
            }
            muted = isMuted
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(player: MediaPlayer): VideoPlayerControlsViewModel
    }
}

private fun MediaPlayer.currentPositionFlow(pollingDelay: Duration): Flow<Int> {
    val polling = flow {
        while (currentCoroutineContext().isActive) {
            delay(pollingDelay)
            emit(Unit)
        }
    }
    val seeking = conflatedCallbackFlow {
        val listener = MediaPlayer.OnSeekCompleteListener { trySend(Unit) }
        setOnSeekCompleteListener(listener)
        awaitClose { setOnSeekCompleteListener(null) }
    }
    return merge(polling, seeking)
        .mapNotNull { callSafe { currentPosition } }
        .distinctUntilChanged()
}

private fun MediaPlayer.onComplete(): Flow<Unit> = callbackFlow {
    val listener = MediaPlayer.OnCompletionListener { trySend(Unit) }
    setOnCompletionListener(listener)
    awaitClose { setOnCompletionListener(null) }
}

/**
 * Unfortunately [MediaPlayer] API doesn't allow to check for the current player state, so there is
 * no good way to tell if it has been released already or not before calling a method.
 */
private fun <T> MediaPlayer.callSafe(action: MediaPlayer.() -> T): T? =
    try {
        action()
    } catch (_: IllegalStateException) {
        null
    }
