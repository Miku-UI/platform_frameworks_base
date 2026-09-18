/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog

import android.content.Intent
import android.media.session.MediaSession
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.flags.Flags
import com.android.settingslib.media.MediaOutputConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.settings.FakeUserTracker
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaOutputDialogReceiverTest : SysuiTestCase() {

    private val mockMediaOutputDialogManager: MediaOutputDialogManager = mock()
    private val fakeUserTracker = FakeUserTracker(TEST_USER_ID)
    private lateinit var mediaOutputDialogReceiver: MediaOutputDialogReceiver

    @Before
    fun setup() {
        mediaOutputDialogReceiver =
            MediaOutputDialogReceiver(mockMediaOutputDialogManager, fakeUserTracker)
    }

    @Test
    @DisableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchMediaOutputDialog_extraPackageName_noMultiuserFlag_dialogFactoryCalled() {
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, context.packageName)
            }

        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, times(1))
            .createAndShow(
                packageName = context.packageName,
                aboveStatusBar = false,
                useSystemColors = true,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchMediaOutputDialog_allExtrasPresent_launchesAppRoutingDialog() {
        val uid = 42
        val mediaSessionToken = MediaSession.Token(uid, null)
        val userHandle = UserHandle.getUserHandleForUid(uid)
        val packageName = "com.example.app"
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, packageName)
                putExtra(MediaOutputConstants.EXTRA_USER_HANDLE, userHandle)
                putExtra(MediaOutputConstants.KEY_MEDIA_SESSION_TOKEN, mediaSessionToken)
            }

        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager)
            .createAndShow(
                packageName = packageName,
                aboveStatusBar = false,
                useSystemColors = true,
                userHandle = userHandle,
                token = mediaSessionToken,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchMediaOutputDialog_noUserHandle_launchesAppRoutingDialogWithUserFromUserTracker() {
        val uid = 42
        val mediaSessionToken = MediaSession.Token(uid, null)
        val packageName = "com.example.app"
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, packageName)
                putExtra(MediaOutputConstants.KEY_MEDIA_SESSION_TOKEN, mediaSessionToken)
            }

        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager)
            .createAndShow(
                packageName = packageName,
                aboveStatusBar = false,
                useSystemColors = true,
                userHandle = fakeUserTracker.userHandle,
                token = mediaSessionToken,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchMediaOutputDialog_noPackage_fails() {
        val uid = 42
        val mediaSessionToken = MediaSession.Token(uid, null)
        val userHandle = UserHandle.getUserHandleForUid(uid)
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                putExtra(MediaOutputConstants.EXTRA_USER_HANDLE, userHandle)
                putExtra(MediaOutputConstants.KEY_MEDIA_SESSION_TOKEN, mediaSessionToken)
            }

        mediaOutputDialogReceiver.onReceive(context, intent)

        verifyNoInteractions(mockMediaOutputDialogManager)
    }

    @Test
    @DisableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchSystemMediaOutputDialog_noMultiuserFlag_launchesSystemRoutingDialog() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG)

        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager).createAndShowForSystemRouting()
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchSystemMediaOutputDialog_hasUser_launchesSystemRoutingDialog() {
        val userHandle = UserHandle.getUserHandleForUid(42)
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG).apply {
                putExtra(MediaOutputConstants.EXTRA_USER_HANDLE, userHandle)
            }

        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager).createAndShowForSystemRouting(userHandle = userHandle)
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun launchSystemMediaOutputDialog_noUser_fails() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG)

        mediaOutputDialogReceiver.onReceive(context, intent)

        verifyNoInteractions(mockMediaOutputDialogManager)
    }

    @Test
    fun launchMediaOutputDialog_wrongExtraKey_dialogFactoryNotCalled() {
        val intent =
            Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
                putExtra("Wrong Package Name Key", context.packageName)
            }
        mediaOutputDialogReceiver.onReceive(context, intent)

        verifyNoInteractions(mockMediaOutputDialogManager)
    }

    @Test
    fun launchMediaOutputDialog_noExtra_dialogFactoryNotCalled() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG)
        mediaOutputDialogReceiver.onReceive(context, intent)

        verifyNoInteractions(mockMediaOutputDialogManager)
    }

    @Test
    fun unknownAction_extraPackageName_factoriesNotCalled() {
        val intent =
            Intent("Unknown Action").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, context.packageName)
            }
        mediaOutputDialogReceiver.onReceive(context, intent)

        verifyNoInteractions(mockMediaOutputDialogManager)
    }

    companion object {
        const val TEST_USER_ID = 25
    }
}
