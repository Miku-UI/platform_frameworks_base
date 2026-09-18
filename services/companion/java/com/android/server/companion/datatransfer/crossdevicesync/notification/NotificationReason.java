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

package com.android.server.companion.datatransfer.crossdevicesync.notification;

import android.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Reasons for showing a notification. */
@Retention(RetentionPolicy.SOURCE)
@StringDef({NotificationReason.AIRPLANE_MODE_SYNCED, NotificationReason.DO_NOT_DISTURB_SYNCED})
public @interface NotificationReason {
    String AIRPLANE_MODE_SYNCED = "apm_sync_notification";
    String DO_NOT_DISTURB_SYNCED = "dnd_sync_notification";
}
