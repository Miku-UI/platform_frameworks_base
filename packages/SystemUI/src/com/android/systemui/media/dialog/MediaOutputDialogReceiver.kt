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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.UserHandle
import android.util.Log
import com.android.media.flags.Flags.fixOutputSwitcherMultiuserSupport
import com.android.settingslib.media.MediaOutputConstants
import com.android.systemui.settings.UserTracker
import javax.inject.Inject

private const val TAG = "MediaOutputDlgReceiver"
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

/** BroadcastReceiver for handling media output intent */
class MediaOutputDialogReceiver
@Inject
constructor(
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val userTracker: UserTracker,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG -> {
                val packageName = intent.getPackageNameExtra()
                if (fixOutputSwitcherMultiuserSupport()) {
                    val userHandle = intent.getUserHandleExtra()
                    val mediaSessionToken = intent.getMediaSessionTokenExtra()
                    launchAppRoutingDialog(userHandle, packageName, mediaSessionToken)
                } else {
                    launchMediaOutputDialogIfPossible(packageName)
                }
            }
            MediaOutputConstants.ACTION_LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG -> {
                if (fixOutputSwitcherMultiuserSupport()) {
                    val userHandle = intent.getUserHandleExtra()
                    launchSystemRoutingDialog(userHandle)
                } else {
                    mediaOutputDialogManager.createAndShowForSystemRouting()
                }
            }
        }
    }

    private fun launchAppRoutingDialog(
        userHandle: UserHandle?,
        packageName: String?,
        mediaSessionToken: MediaSession.Token?,
    ) {
        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "Can't launch dialog. Package name is empty.")
            return
        }
        // TODO: b/437382985 - require userHandle to be non-null as soon as it's confirmed that
        // there are no calls to ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG outside of the AOSP code.
        val resolvedUserHandle =
            userHandle
                ?: userTracker.userHandle.also {
                    Log.w(TAG, "userHandle is null, using fallback from userTracker: $it")
                }
        mediaOutputDialogManager.createAndShow(
            packageName,
            aboveStatusBar = false,
            useSystemColors = true,
            userHandle = resolvedUserHandle,
            token = mediaSessionToken,
        )
    }

    private fun launchSystemRoutingDialog(userHandle: UserHandle?) {
        if (userHandle == null) {
            Log.e(TAG, "Can't launch dialog. UserHandle is empty.")
            return
        }
        mediaOutputDialogManager.createAndShowForSystemRouting(userHandle = userHandle)
    }

    private fun launchMediaOutputDialogIfPossible(packageName: String?) {
        if (!packageName.isNullOrEmpty()) {
            mediaOutputDialogManager.createAndShow(
                packageName,
                aboveStatusBar = false,
                useSystemColors = true,
            )
        } else if (DEBUG) {
            Log.e(TAG, "Unable to launch media output dialog. Package name is empty.")
        }
    }

    fun Intent.getPackageNameExtra(): String? =
        getStringExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME)

    fun Intent.getUserHandleExtra(): UserHandle? =
        getParcelableExtra(MediaOutputConstants.EXTRA_USER_HANDLE, UserHandle::class.java)

    fun Intent.getMediaSessionTokenExtra(): MediaSession.Token? =
        getParcelableExtra(
            MediaOutputConstants.KEY_MEDIA_SESSION_TOKEN,
            MediaSession.Token::class.java,
        )
}
