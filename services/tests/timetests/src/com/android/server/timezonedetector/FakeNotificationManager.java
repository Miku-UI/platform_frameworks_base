/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.timezonedetector;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.UserHandle;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

/**
 * A fake {@link NotificationManager} for use in tests. This class captures notifications that would
 * be sent and stores them for later inspection.
 */
public class FakeNotificationManager extends NotificationManager {

    private final List<Notification> mNotifications = new ArrayList<>();

    /** Creates an instance of {@link FakeNotificationManager}. */
    FakeNotificationManager(Context context, InstantSource clock) {
        super(context, clock);
    }

    /** Captures the notification instead of sending it. */
    @Override
    public void notifyAsUser(String tag, int id, Notification notification, UserHandle user) {
        mNotifications.add(notification);
    }

    /** Returns the list of captured notifications. */
    public List<Notification> getNotifications() {
        return mNotifications;
    }
}
