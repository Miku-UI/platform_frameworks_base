/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubbles.utils

import android.app.INotificationManager
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ServiceManager
import com.android.server.wm.flicker.helpers.MultiWindowUtils

/** Helper class for enabling chat bubble notifications. */
class BubbleNotificationsEnabler(
    private val instrumentation: Instrumentation,
    private val packageName: String,
) {
    private val notificationManager =
        INotificationManager.Stub.asInterface(
            ServiceManager.getService(Context.NOTIFICATION_SERVICE)
        )

    private val uid =
        instrumentation.context.packageManager
            .getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            .uid

    fun enable() {
        MultiWindowUtils.executeShellCommand(
            instrumentation,
            "settings put secure force_hide_bubbles_user_education 1",
        )
        notificationManager.setBubblesAllowed(
            packageName,
            uid,
            NotificationManager.BUBBLE_PREFERENCE_ALL,
        )
    }

    fun disable() {
        MultiWindowUtils.executeShellCommand(
            instrumentation,
            "settings put secure force_hide_bubbles_user_education 0",
        )
        notificationManager.setBubblesAllowed(
            packageName,
            uid,
            NotificationManager.BUBBLE_PREFERENCE_NONE,
        )
    }
}
