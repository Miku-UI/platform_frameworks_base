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

package com.android.systemui.statusbar.notification;

import static android.app.NotificationChannel.SYSTEM_RESERVED_IDS;
import static android.service.notification.Flags.notificationClassification;

import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Slog;

import com.android.systemui.statusbar.notification.collection.NotificationEntry;

/**
 * Helps SystemUI create notification channels.
 */
public class NotificationChannelHelper {
    private static final String TAG = "NotificationChannelHelper";

    /** Creates a conversation channel based on the shortcut info or notification title. */
    public static NotificationChannel createConversationChannelIfNeeded(
            Context context,
            INotificationManager notificationManager,
            NotificationListenerService.Ranking ranking,
            StatusBarNotification sbn,
            NotificationChannel channel) {
        if (notificationClassification() && SYSTEM_RESERVED_IDS.contains(channel.getId())) {
            return channel;
        }
        if (!TextUtils.isEmpty(channel.getConversationId())) {
            return channel;
        }
        final String conversationId =sbn.getShortcutId();
        final String pkg = sbn.getPackageName();
        final int appUid = sbn.getUid();
        if (TextUtils.isEmpty(conversationId) || TextUtils.isEmpty(pkg)
            || ranking.getConversationShortcutInfo() == null) {
            return channel;
        }

        // If this channel is not already a customized conversation channel, create
        // a custom channel
        try {
            channel.setName(getName(ranking, sbn));
            notificationManager.createConversationNotificationChannelForPackage(
                    pkg, appUid, channel,
                    conversationId);
            channel = notificationManager.getConversationNotificationChannel(
                    context.getOpPackageName(), UserHandle.getUserId(appUid), pkg,
                    channel.getId(), false, conversationId);
        } catch (RemoteException e) {
            Slog.e(TAG, "Could not create conversation channel", e);
        }
        return channel;
    }

    public static CharSequence getName(NotificationListenerService.Ranking ranking,
            StatusBarNotification sbn) {
        if (ranking.getConversationShortcutInfo().getLabel() != null) {
            return ranking.getConversationShortcutInfo().getLabel().toString();
        }
        Bundle extras = sbn.getNotification().extras;
        CharSequence nameString = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (TextUtils.isEmpty(nameString)) {
            nameString = extras.getCharSequence(Notification.EXTRA_TITLE);
        }
        if (TextUtils.isEmpty(nameString)) {
            nameString = "fallback";
        }
        return nameString;
    }
}
