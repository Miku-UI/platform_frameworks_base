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

package com.android.systemui.statusbar.notification.row.data.model

import android.os.UserHandle

/**
 * Info necessary for app related bundle UI
 *
 * @property packageName The package name of the application.
 * @property user The UserHandle associated with the notification from this application.
 * @property timeAddedToBundle Uptime millis when last notif was put in current bundle
 */
data class AppData(val packageName: String, val user: UserHandle, val timeAddedToBundle: Long)
