/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.companion.utils;

import static android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;
import static android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_FITNESS_TRACKER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES;
import static android.companion.AssociationRequest.DEVICE_PROFILE_MEDICAL;
import static android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_VIRTUAL_DEVICE;
import static android.companion.AssociationRequest.DEVICE_PROFILE_WATCH;
import static android.companion.AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING;
import static android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED;
import static android.companion.DevicePresenceEvent.EVENT_BLE_DISAPPEARED;
import static android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED;
import static android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED;
import static android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED;

import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__ACTION__CREATED;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__ACTION__REMOVED;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_APP_STREAMING;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_AUTO_PROJECTION;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_COMPUTER;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_FITNESS_TRACKER;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_GLASSES;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_NEARBY_DEVICE_STREAMING;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_NULL;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_VIRTUAL_DEVICE;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_WATCH;
import static com.android.internal.util.FrameworkStatsLog.CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_WEARABLE_SENSING;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BLE_APPEARED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BLE_DISAPPEARED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BT_CONNECTED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BT_DISCONNECTED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_SELF_MANAGED_APPEARED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_SELF_MANAGED_DISAPPEARED;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_APP_STREAMING;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_AUTO_PROJECTION;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_COMPUTER;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_FITNESS_TRACKER;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_GLASSES;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_NULL;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_UUID;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_VIRTUAL_DEVICE;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_WATCH;
import static com.android.internal.util.FrameworkStatsLog.DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_WEARABLE_SENSING;
import static com.android.internal.util.FrameworkStatsLog.write;
import static com.android.server.companion.utils.PackageUtils.PACKAGE_NOT_FOUND;
import static com.android.server.companion.utils.PackageUtils.getUidFromPackageName;

import static java.util.Map.entry;

import android.companion.AssociationInfo;
import android.content.Context;

import java.util.Map;
import java.util.Objects;

public final class MetricUtils {
    /**
     * A String to indicate device presence base on UUID.
     */
    public static final String UUID = "UUID";

    /**
     * A String to indicate the device profile is null.
     */
    private static final String DEVICE_PROFILE_NULL = "null";

    private static final Map<String, Integer> ASSOCIATION_ACTION_DEVICE_PROFILE = Map.of(
            DEVICE_PROFILE_NULL,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_NULL,
            DEVICE_PROFILE_WATCH,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_WATCH,
            DEVICE_PROFILE_APP_STREAMING,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_APP_STREAMING,
            DEVICE_PROFILE_AUTOMOTIVE_PROJECTION,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_AUTO_PROJECTION,
            DEVICE_PROFILE_COMPUTER,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_COMPUTER,
            DEVICE_PROFILE_GLASSES,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_GLASSES,
            DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
            DEVICE_PROFILE_VIRTUAL_DEVICE,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_VIRTUAL_DEVICE,
            DEVICE_PROFILE_WEARABLE_SENSING,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_WEARABLE_SENSING,
            DEVICE_PROFILE_FITNESS_TRACKER,
            CDM_ASSOCIATION_ACTION__DEVICE_PROFILE__DEVICE_PROFILE_FITNESS_TRACKER
    );

    private static final Map<String, Integer> DEVICE_PRESENCE_CHANGED_DEVICE_PROFILE = Map.ofEntries(
            entry(
                    DEVICE_PROFILE_NULL,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_NULL),
            entry(
                    UUID,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_UUID),
            entry(
                    DEVICE_PROFILE_WATCH,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_WATCH),
            entry(
                    DEVICE_PROFILE_APP_STREAMING,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_APP_STREAMING),
            entry(
                    DEVICE_PROFILE_AUTOMOTIVE_PROJECTION,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_AUTO_PROJECTION),
            entry(
                    DEVICE_PROFILE_COMPUTER,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_COMPUTER),
            entry(
                    DEVICE_PROFILE_GLASSES,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_GLASSES),
            entry(
                    DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_APP_STREAMING),
            entry(
                    DEVICE_PROFILE_VIRTUAL_DEVICE,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_VIRTUAL_DEVICE),
            entry(
                    DEVICE_PROFILE_WEARABLE_SENSING,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_WEARABLE_SENSING),
            entry(
                    DEVICE_PROFILE_FITNESS_TRACKER,
                    DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_FITNESS_TRACKER));

    private static final Map<Integer, Integer> DEVICE_PRESENCE_CHANGED_EVENT = Map.of(
            EVENT_BLE_APPEARED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BLE_APPEARED,
            EVENT_BLE_DISAPPEARED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BLE_DISAPPEARED,
            EVENT_BT_CONNECTED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BT_CONNECTED,
            EVENT_BT_DISCONNECTED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_BT_DISCONNECTED,
            EVENT_SELF_MANAGED_APPEARED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_SELF_MANAGED_APPEARED,
            EVENT_SELF_MANAGED_DISAPPEARED,
            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_SELF_MANAGED_DISAPPEARED
    );

    /**
     * Log association creation
     */
    public static void logCreateAssociation(AssociationInfo ai, Context context) {
        // Do not log medical activity
        if (Objects.equals(ai.getDeviceProfile(), DEVICE_PROFILE_MEDICAL)) {
            return;
        }

        int uid = getUidFromPackageName(ai.getUserId(), context, ai.getPackageName());

        write(CDM_ASSOCIATION_ACTION,
                CDM_ASSOCIATION_ACTION__ACTION__CREATED,
                ASSOCIATION_ACTION_DEVICE_PROFILE.get(ai.getDeviceProfile() == null
                        ? DEVICE_PROFILE_NULL : ai.getDeviceProfile()),
                uid);
    }

    /**
     * Log association removal
     */
    public static void logRemoveAssociation(AssociationInfo ai, Context context) {
        // Do not log medical activity
        if (Objects.equals(ai.getDeviceProfile(), DEVICE_PROFILE_MEDICAL)) {
            return;
        }

        int uid = getUidFromPackageName(ai.getUserId(), context, ai.getPackageName());

        write(CDM_ASSOCIATION_ACTION,
                CDM_ASSOCIATION_ACTION__ACTION__REMOVED,
                ASSOCIATION_ACTION_DEVICE_PROFILE.get(ai.getDeviceProfile() == null
                        ? DEVICE_PROFILE_NULL : ai.getDeviceProfile()),
                uid);
    }

    /**
     * Log device presence event changed.
     */
    public static void logDevicePresenceEvent(int userId, Context context,
            String deviceProfileOrUuid, String packageName, int event) {
        // Do not log medical activity
        if (Objects.equals(deviceProfileOrUuid, DEVICE_PROFILE_MEDICAL)) {
            return;
        }

        int uid = getUidFromPackageName(userId, context, packageName);
        if (uid != PACKAGE_NOT_FOUND) {
            write(
                    DEVICE_PRESENCE_CHANGED,
                    uid,
                    DEVICE_PRESENCE_CHANGED_DEVICE_PROFILE.getOrDefault(
                            deviceProfileOrUuid == null ? DEVICE_PROFILE_NULL : deviceProfileOrUuid,
                            DEVICE_PRESENCE_CHANGED__DEVICE_PRESENCE_STATUS__NOTIFY_UNKNOWN),
                    DEVICE_PRESENCE_CHANGED_EVENT.getOrDefault(event,
                            DEVICE_PRESENCE_CHANGED__DEVICE_PROFILE__DEVICE_PROFILE_UNKNOWN)
            );
        }
    }
}
