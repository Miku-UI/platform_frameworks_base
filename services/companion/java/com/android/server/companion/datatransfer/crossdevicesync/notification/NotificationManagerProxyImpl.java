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

import android.annotation.NonNull;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.UserHandle;

/** Implementation of {@link NotificationManagerProxy}. */
public class NotificationManagerProxyImpl implements NotificationManagerProxy {

    private final NotificationManager mNotificationManager;

    public NotificationManagerProxyImpl(Context context) {
        mNotificationManager = context.getSystemService(NotificationManager.class);
    }

    @Override
    public void createNotificationChannel(@NonNull NotificationChannel channel) {
        mNotificationManager.createNotificationChannel(channel);
    }

    @Override
    public void notify(int id, @NonNull Notification notification, @NonNull UserHandle user) {
        mNotificationManager.notifyAsUser(/* tag= */ null, id, notification, user);
    }

    @Override
    public void cancel(int id, @NonNull UserHandle user) {
        mNotificationManager.cancelAsUser(/* tag= */ null, id, user);
    }
}
