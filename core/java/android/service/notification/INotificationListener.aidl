/**
 * Copyright (c) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.pm.ParceledListSlice;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.NotificationStats;
import android.service.notification.IDispatchCompletionListener;
import android.service.notification.IStatusBarNotificationHolder;
import android.service.notification.StatusBarNotification;
import android.service.notification.NotificationRankingUpdate;

/** @hide */
oneway interface INotificationListener
{
    // listeners and assistant
    void onListenerConnected(in NotificationRankingUpdate update,
            in IDispatchCompletionListener completionCallback, in long dispatchToken);
    void onNotificationPosted(in IStatusBarNotificationHolder notificationHolder,
            in NotificationRankingUpdate update, in long dispatchToken);
    void onNotificationPostedFull(in StatusBarNotification sbn,
            in NotificationRankingUpdate update, in long dispatchToken);
    void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons, in long dispatchToken);
    // stats only for assistant
    void onNotificationRemoved(in IStatusBarNotificationHolder notificationHolder,
            in NotificationRankingUpdate update, in NotificationStats stats, int reason,
            in long dispatchToken);
    void onNotificationRemovedFull(in StatusBarNotification sbn,
                in NotificationRankingUpdate update, in NotificationStats stats, int reason,
                in long dispatchToken);
    void onNotificationRankingUpdate(in NotificationRankingUpdate update, in long dispatchToken);
    void onListenerHintsChanged(int hints, in long dispatchToken);
    void onInterruptionFilterChanged(int interruptionFilter, in long dispatchToken);

    // companion device managers and assistants only
    void onNotificationChannelModification(String pkgName, in UserHandle user, in NotificationChannel channel, int modificationType, in long dispatchToken);
    void onNotificationChannelGroupModification(String pkgName, in UserHandle user, in NotificationChannelGroup group, int modificationType, in long dispatchToken);

    // assistants only
    void onNotificationEnqueuedWithChannel(in IStatusBarNotificationHolder notificationHolder, in NotificationChannel channel, in NotificationRankingUpdate update);
    void onNotificationEnqueuedWithChannelFull(in StatusBarNotification sbn, in NotificationChannel channel, in NotificationRankingUpdate update);
    void onNotificationSnoozedUntilContext(in IStatusBarNotificationHolder notificationHolder, String snoozeCriterionId);
    void onNotificationSnoozedUntilContextFull(in StatusBarNotification sbn, String snoozeCriterionId);
    void onNotificationsSeen(in List<String> keys);
    void onPanelRevealed(int items);
    void onPanelHidden();
    void onNotificationVisibilityChanged(String key, boolean isVisible);
    void onNotificationExpansionChanged(String key, boolean userAction, boolean expanded);
    void onNotificationDirectReply(String key);
    void onSuggestedReplySent(String key, in CharSequence reply, int source);
    void onActionClicked(String key, in Notification.Action action, int source);
    void onNotificationClicked(String key);
    void onAllowedAdjustmentsChanged();
    void onNotificationFeedbackReceived(String key, in NotificationRankingUpdate update, in Bundle feedback);
}
