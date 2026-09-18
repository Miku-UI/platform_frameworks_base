/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.notification;

import android.annotation.NonNull;
import android.app.AutomaticZenRule;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.app.backup.BackupRestoreEventLogger;
import android.os.UserHandle;
import android.service.notification.Adjustment;
import android.service.notification.DeviceEffectsApplier;

import com.android.internal.annotations.Keep;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Keep
public interface NotificationManagerInternal {
    NotificationChannel getNotificationChannel(String pkg, int uid, String channelId);
    NotificationChannelGroup getNotificationChannelGroup(String pkg, int uid, String channelId);
    void enqueueNotification(String pkg, String basePkg, int callingUid, int callingPid,
            String tag, int id, Notification notification, int userId);
    void enqueueNotification(String pkg, String basePkg, int callingUid, int callingPid,
            String tag, int id, Notification notification, int userId,
            boolean byForegroundService);
    void cancelNotification(String pkg, String basePkg, int callingUid, int callingPid,
            String tag, int id, int userId);

    /** is the given notification currently showing? */
    boolean isNotificationShown(String pkg, String tag, int notificationId, int userId);

    void removeForegroundServiceFlagFromNotification(String pkg, int notificationId, int userId);

    void removeUserInitiatedJobFlagFromNotification(String pkg, int notificationId, int userId);

    void removeComputerControlFlagFromNotification(String pkg, int notificationId, int userId);

    void onConversationRemoved(String pkg, int uid, Set<String> shortcuts);

    /** Get the number of app created notification channels for a given package */
    int getNumNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);

    /** Does the specified package/uid have permission to post notifications? */
    boolean areNotificationsEnabledForPackage(String pkg, int uid);

    /** Send a notification to the user prompting them to review their notification permissions. */
    void sendReviewPermissionsNotification();

    void cleanupHistoryFiles();

    void removeBitmaps();

    /**
     * Sets the {@link DeviceEffectsApplier} that will be used to apply the different
     * {@link android.service.notification.ZenDeviceEffects} that are relevant for the platform
     * when {@link android.service.notification.ZenModeConfig.ZenRule} instances are activated and
     * deactivated.
     *
     * <p>This method is optional and needs only be called if the platform supports non-standard
     * effects (i.e. any that are not <em>public APIs</em> in
     * {@link android.service.notification.ZenDeviceEffects}, or if they must be applied in a
     * non-standard fashion. If not used, a {@link DefaultDeviceEffectsApplier} will be invoked,
     * which should be sufficient for most devices.
     *
     * <p>If this method is called, it <em>must</em> be during system startup and <em>before</em>
     * the {@link com.android.server.SystemService#PHASE_THIRD_PARTY_APPS_CAN_START} boot phase.
     * Otherwise an {@link IllegalStateException} will be thrown.
     */
    void setDeviceEffectsApplier(DeviceEffectsApplier applier);

    // Backup/restore interface
    byte[] getBackupPayload(int user, BackupRestoreEventLogger logger);

    void applyRestore(byte[] payload, int user, BackupRestoreEventLogger logger);

    /**
     * Notifies NotificationManager that the system decorations should be removed from the display.
     *
     * @param displayId display ID
     */
    void onDisplayRemoveSystemDecorations(int displayId);

    /**
     * Requests that the active {@link android.service.notification.NotificationAssistantService}
     * apply the given list of {@link Adjustment} to notifications. This does not guarantee that
     * the adjustments will be applied, but rather requests that the service applies them.
     *
     * @param adjustments the requested adjustments
     */
    void requestSystemAdjustments(@NonNull List<Adjustment> adjustments);

    /** Check if manual zen rule is active for the provided user. */
    boolean isManualZenRuleActive(UserHandle userHandle);

    /**
     * Activate/deactivate the manual zen rule for the provided user. Unlike
     * {@link NotificationManager#setInterruptionFilter(int)} or
     * {@link NotificationManager#setNotificationPolicy(Policy)}, this method doesn't create
     * implicit automatic zen rule for the caller.
     */
    void setManualZenRuleActive(UserHandle userHandle, boolean active);

    /** Get automatic zen rules for the provided user. */
    Map<String, AutomaticZenRule> getAutomaticZenRules(UserHandle userHandle);

    /** Check if an automatic zen rule is active for the provided user. */
    boolean isAutomaticZenRuleActive(UserHandle userHandle, String id);

    /** Activate/deactivate automatic zen rule for the provided user. */
    void setAutomaticZenRuleActive(UserHandle userHandle, String id, boolean active);

    /** Add a callback for monitoring zen mode change. */
    void addZenModeCallback(ZenModeHelper.Callback callback);

    /**
     * Check if a user has a zen mode config. Some user types don't have zen config, e.g. work
     * profile, secondary user, etc.
     */
    boolean hasZenModeConfig(UserHandle userHandle);
}
