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

package com.android.systemui.screenshot

import android.media.MediaActionSound
import android.media.MediaPlayer
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND
import com.android.systemui.SysuiTestCase
import java.lang.IllegalStateException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotSoundControllerTest : SysuiTestCase() {

    private val soundProvider = mock<ScreenshotSoundProvider>()
    private val mediaPlayer = mock<MediaPlayer>()
    private val mediaActionSound = mock<MediaActionSound>()
    private val soundPolicy = mock<ScreenshotSoundPolicy>()
    private val bgDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(bgDispatcher)

    @Before
    fun setup() = runTest {
        whenever(soundProvider.getScreenshotSound()).thenReturn(mediaPlayer)
        whenever(soundProvider.getForcedShutterSound()).thenReturn(mediaActionSound)
        soundPolicy.stub { onBlocking { shouldForceShutterSound() }.doReturn(false) } // default
    }

    @Test
    @DisableFlags(FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND)
    fun init_soundLoading() {
        createController()
        scope.advanceUntilIdle()

        verify(soundProvider).getScreenshotSound()
        verify(soundProvider).getForcedShutterSound()
    }

    @Test
    fun init_soundLoadingException_playAndReleaseDoNotThrow() =
        scope.runTest {
            whenever(soundProvider.getScreenshotSound()).thenThrow(IllegalStateException())

            val controller = createController()

            controller.playScreenshotSound()
            advanceUntilIdle()

            verify(mediaPlayer, never()).start()
            verify(mediaPlayer, never()).release()
        }

    @Test
    @EnableFlags(FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND)
    fun playCameraSound_withRemovedSound_noSoundPlays() =
        scope.runTest {
            val controller = createController()

            controller.playScreenshotSound()
            advanceUntilIdle()

            verify(mediaPlayer, never()).start()
            verify(mediaActionSound, never()).play(any())
        }

    @Test
    @DisableFlags(FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND)
    fun playCameraSound_soundLoadingSuccessful_mediaPlayerPlays() =
        scope.runTest {
            val controller = createController()

            controller.playScreenshotSound()
            advanceUntilIdle()

            verify(mediaPlayer).start()
            verify(mediaActionSound, never()).play(any())
        }

    @Test
    @DisableFlags(FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND)
    fun playCameraSound_illegalStateException_doesNotThrow() =
        scope.runTest {
            whenever(mediaPlayer.start()).thenThrow(IllegalStateException())

            val controller = createController()
            controller.playScreenshotSound()
            advanceUntilIdle()

            verify(mediaPlayer).start()
            verify(mediaPlayer).release()
            verify(mediaActionSound).release()
        }

    @Test
    @DisableFlags(FLAG_SCREENSHOT_REMOVE_NONFORCED_SOUND)
    fun playCameraSound_soundLoadingSuccessful_mediaPlayerReleases() =
        scope.runTest {
            val controller = createController()

            controller.releaseScreenshotSound()
            advanceUntilIdle()

            verify(mediaPlayer).release()
            verify(mediaActionSound).release()
        }

    @Test
    fun screenshotSoundForced_usesShutterSound() = runTest {
        soundPolicy.stub { onBlocking { shouldForceShutterSound() }.doReturn(true) }
        val controller = createController()

        controller.playScreenshotSound()
        advanceUntilIdle()

        verify(mediaPlayer, never()).start()
        verify(mediaActionSound).play(MediaActionSound.SHUTTER_CLICK)
    }

    private fun createController() =
        ScreenshotSoundControllerImpl(soundProvider, soundPolicy, scope, bgDispatcher)
}
