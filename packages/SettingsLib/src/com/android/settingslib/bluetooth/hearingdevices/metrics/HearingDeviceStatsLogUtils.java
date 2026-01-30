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

package com.android.settingslib.bluetooth.hearingdevices.metrics;

import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.HapClientProfile;
import com.android.settingslib.bluetooth.HeadsetProfile;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LeAudioProfile;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

/** Utils class to report hearing aid metrics to statsd */
public final class HearingDeviceStatsLogUtils {

    private static final String TAG = "HearingAidStatsLogUtils";
    private static final boolean DEBUG = true;
    private static final String ACCESSIBILITY_PREFERENCE = "accessibility_prefs";

    private static final String BT_LE_HEARING_PAIRED_HISTORY =
            "bt_le_hearing_aids_paired_history";
    private static final String BT_HEARING_PAIRED_HISTORY =
            "bt_hearing_aids_paired_history";
    // The values "hearing_devices" actually represent Bluetooth hearable devices, but were
    // mistyped before. Keep the string values to ensure record persistence.
    private static final String BT_LE_HEARABLE_PAIRED_HISTORY =
            "bt_le_hearing_devices_paired_history";
    private static final String BT_HEARABLE_PAIRED_HISTORY =
            "bt_hearing_devices_paired_history";

    private static final String BT_LE_HEARING_CONNECTED_HISTORY =
            "bt_le_hearing_aids_connected_history";
    private static final String BT_HEARING_CONNECTED_HISTORY =
            "bt_hearing_aids_connected_history";
    private static final String BT_LE_HEARABLE_CONNECTED_HISTORY =
            "bt_le_hearing_devices_connected_history";
    private static final String BT_HEARABLE_CONNECTED_HISTORY =
            "bt_hearing_devices_connected_history";

    private static final String HISTORY_RECORD_DELIMITER = ",";

    static final String CATEGORY_HEARING_DEVICES = "A11yHearingAidsUser";
    static final String CATEGORY_NEW_HEARING_DEVICES = "A11yNewHearingAidsUser";
    static final String CATEGORY_LE_HEARING_DEVICES = "A11yLeHearingAidsUser";
    static final String CATEGORY_NEW_LE_HEARING_DEVICES = "A11yNewLeHearingAidsUser";
    // The values here actually represent Bluetooth hearable devices, but were mistyped
    // as hearing devices in the string value previously. Keep the string values to ensure record
    // persistence.
    static final String CATEGORY_HEARABLE_DEVICES = "A11yHearingDevicesUser";
    static final String CATEGORY_NEW_HEARABLE_DEVICES = "A11yNewHearingDevicesUser";
    static final String CATEGORY_LE_HEARABLE_DEVICES = "A11yLeHearingDevicesUser";
    static final String CATEGORY_NEW_LE_HEARABLE_DEVICES = "A11yNewLeHearingDevicesUser";

    static final int PAIRED_HISTORY_EXPIRED_DAY = 30;
    static final int CONNECTED_HISTORY_EXPIRED_DAY = 7;
    private static final int VALID_CONNECTED_EVENT_COUNT = 7;

    /**
     * Type of different Bluetooth device events history related to hearing.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            HistoryType.TYPE_UNKNOWN,
            HistoryType.TYPE_HEARING_PAIRED,
            HistoryType.TYPE_HEARING_CONNECTED,
            HistoryType.TYPE_HEARABLE_PAIRED,
            HistoryType.TYPE_HEARABLE_CONNECTED,
            HistoryType.TYPE_LE_HEARING_CONNECTED,
            HistoryType.TYPE_LE_HEARABLE_CONNECTED,
            HistoryType.TYPE_LE_HEARING_PAIRED,
            HistoryType.TYPE_LE_HEARABLE_PAIRED,
    })
    public @interface HistoryType {
        int TYPE_UNKNOWN = -1;
        int TYPE_HEARING_PAIRED = 0;
        int TYPE_HEARING_CONNECTED = 1;
        int TYPE_HEARABLE_PAIRED = 2;
        int TYPE_HEARABLE_CONNECTED = 3;
        int TYPE_LE_HEARING_CONNECTED = 4;
        int TYPE_LE_HEARABLE_CONNECTED = 5;
        int TYPE_LE_HEARING_PAIRED = 6;
        int TYPE_LE_HEARABLE_PAIRED = 7;
    }

    private static final HashMap<String, Integer> sDeviceAddressToBondEntryMap = new HashMap<>();
    private static final Set<String> sJustBondedDeviceAddressSet = new HashSet<>();

    /**
     * Sets the mapping from hearing aid device to the bond entry where this device starts it's
     * bonding(connecting) process.
     *
     * @param bondEntry The entry page id where the bonding process starts
     * @param device The bonding(connecting) hearing aid device
     */
    public static void setBondEntryForDevice(int bondEntry, CachedBluetoothDevice device) {
        sDeviceAddressToBondEntryMap.put(device.getAddress(), bondEntry);
    }

    /**
     * Logs hearing aid device information to statsd, including device mode, device side, and entry
     * page id where the binding(connecting) process starts.
     *
     * Only logs the info once after hearing aid is bonded(connected). Clears the map entry of this
     * device when logging is completed.
     *
     * @param device The bonded(connected) hearing aid device
     */
    public static void logHearingAidInfo(CachedBluetoothDevice device) {
        final String deviceAddress = device.getAddress();
        if (sDeviceAddressToBondEntryMap.containsKey(deviceAddress)) {
            final int bondEntry = sDeviceAddressToBondEntryMap.getOrDefault(deviceAddress, -1);
            final int deviceMode = device.getDeviceMode();
            final int deviceSide = device.getDeviceSide();
            FrameworkStatsLog.write(FrameworkStatsLog.HEARING_AID_INFO_REPORTED, deviceMode,
                    deviceSide, bondEntry);

            sDeviceAddressToBondEntryMap.remove(deviceAddress);
        } else {
            Log.w(TAG, "The device address was not found. Hearing aid device info is not logged.");
        }
    }

    @VisibleForTesting
    static HashMap<String, Integer> getDeviceAddressToBondEntryMap() {
        return sDeviceAddressToBondEntryMap;
    }

    /**
     * Updates corresponding history if we found the device is a hearing related device after
     * profile state changed.
     *
     * @param context the request context
     * @param cachedDevice the remote device
     * @param profile the profile that has a state changed
     * @param profileState the new profile state
     */
    public static void updateHistoryIfNeeded(Context context, CachedBluetoothDevice cachedDevice,
            LocalBluetoothProfile profile, int profileState) {

        if (isJustBonded(cachedDevice.getAddress())) {
            // Saves bonded timestamp as the source for judging whether to display the survey
            if (isLeHearingDevice(cachedDevice)) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_LE_HEARING_PAIRED);
            } else if (isHearingDevice(cachedDevice)) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_HEARING_PAIRED);
            } else if (isLeHearableDevice(cachedDevice)) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_LE_HEARABLE_PAIRED);
            } else if (isHearableDevice(cachedDevice)) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_HEARABLE_PAIRED);
            }
            removeFromJustBonded(cachedDevice.getAddress());
        }

        if (profileState == BluetoothProfile.STATE_CONNECTED) {
            // Saves connected timestamp as the source for judging whether to display the survey
            if (profile instanceof HapClientProfile) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_LE_HEARING_CONNECTED);
            } else if (profile instanceof HearingAidProfile) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_HEARING_CONNECTED);
            } else if (profile instanceof LeAudioProfile && isLeHearableDevice(cachedDevice)) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_LE_HEARABLE_CONNECTED);
            } else if (profile instanceof A2dpProfile || profile instanceof HeadsetProfile) {
                addCurrentTimeToHistory(context, HistoryType.TYPE_HEARABLE_CONNECTED);
            }
        }
    }

    /**
     * Returns the user category based on different histories.
     *
     * @param context the request context
     * @return the category which user belongs to
     */
    public static synchronized String getUserCategory(Context context) {
        boolean isNewPairedHearingUser = hasSufficientData(context,
                HistoryType.TYPE_LE_HEARING_PAIRED) || hasSufficientData(context,
                HistoryType.TYPE_HEARING_PAIRED);
        boolean isNewPairedHearableUser = hasSufficientData(context,
                HistoryType.TYPE_LE_HEARABLE_PAIRED) || hasSufficientData(context,
                HistoryType.TYPE_HEARABLE_PAIRED);

        if (hasSufficientData(context, HistoryType.TYPE_LE_HEARING_CONNECTED)) {
            return isNewPairedHearingUser ? CATEGORY_NEW_LE_HEARING_DEVICES
                    : CATEGORY_LE_HEARING_DEVICES;
        }
        if (hasSufficientData(context, HistoryType.TYPE_HEARING_CONNECTED)) {
            return isNewPairedHearingUser ? CATEGORY_NEW_HEARING_DEVICES
                    : CATEGORY_HEARING_DEVICES;
        }
        if (hasSufficientData(context, HistoryType.TYPE_LE_HEARABLE_CONNECTED)) {
            return isNewPairedHearableUser ? CATEGORY_NEW_LE_HEARABLE_DEVICES
                    : CATEGORY_LE_HEARABLE_DEVICES;
        }
        if (hasSufficientData(context, HistoryType.TYPE_HEARABLE_CONNECTED)) {
            return isNewPairedHearableUser ? CATEGORY_NEW_HEARABLE_DEVICES
                    : CATEGORY_HEARABLE_DEVICES;
        }
        return "";
    }

    /**
     * Maintains a temporarily list of just bonded device address. After the device profiles are
     * connected, {@link HearingDeviceStatsLogUtils#removeFromJustBonded} will be called to remove
     * the address.
     * @param address the device address
     */
    public static void addToJustBonded(String address) {
        sJustBondedDeviceAddressSet.add(address);
    }

    /**
     * Removes the device address from the just bonded list.
     * @param address the device address
     */
    private static void removeFromJustBonded(String address) {
        sJustBondedDeviceAddressSet.remove(address);
    }

    /**
     * Checks whether the device address is in the just bonded list.
     * @param address the device address
     * @return true if the device address is in the just bonded list
     */
    private static boolean isJustBonded(String address) {
        return sJustBondedDeviceAddressSet.contains(address);
    }

    /**
     * Adds current timestamp into BT hearing related devices history.
     * @param context the request context
     * @param type the type of history to store the data. See {@link HistoryType}.
     */
    public static void addCurrentTimeToHistory(Context context, @HistoryType int type) {
        addToHistory(context, type, System.currentTimeMillis());
    }

    static synchronized void addToHistory(Context context, @HistoryType int type,
            long timestamp) {

        LinkedList<Long> history = getHistory(context, type);
        if (history == null) {
            if (DEBUG) {
                Log.w(TAG, "Couldn't find shared preference name matched type=" + type);
            }
            return;
        }
        if (history.peekLast() != null && isSameDay(timestamp, history.peekLast())) {
            if (DEBUG) {
                Log.w(TAG, "Remove the earlier same day record of history type=" + type);
            }
            history.remove(history.peekLast());
        }
        history.add(timestamp);
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(HISTORY_TYPE_TO_SP_NAME_MAPPING.get(type),
                convertToHistoryString(history)).apply();
    }

    @Nullable
    static synchronized LinkedList<Long> getHistory(Context context, @HistoryType int type) {
        String spName = HISTORY_TYPE_TO_SP_NAME_MAPPING.get(type);
        if (BT_LE_HEARING_PAIRED_HISTORY.equals(spName)
                || BT_HEARING_PAIRED_HISTORY.equals(spName)
                || BT_LE_HEARABLE_PAIRED_HISTORY.equals(spName)
                || BT_HEARABLE_PAIRED_HISTORY.equals(spName)) {
            LinkedList<Long> history = convertToHistoryList(
                    getSharedPreferences(context).getString(spName, ""));
            removeRecordsBeforeDay(history, PAIRED_HISTORY_EXPIRED_DAY);
            return history;
        } else if (BT_HEARING_CONNECTED_HISTORY.equals(spName)
                || BT_HEARABLE_CONNECTED_HISTORY.equals(spName)
                || BT_LE_HEARING_CONNECTED_HISTORY.equals(spName)
                || BT_LE_HEARABLE_CONNECTED_HISTORY.equals(spName)) {
            LinkedList<Long> history = convertToHistoryList(
                    getSharedPreferences(context).getString(spName, ""));
            removeRecordsBeforeDay(history, CONNECTED_HISTORY_EXPIRED_DAY);
            return history;
        }
        return null;
    }

    /**
     * Gets the history type of the most recent event across all history categories.
     *
     * @param context The context, used to access SharedPreferences.
     * @return The {@link HistoryType} of the most recent event, or
     *         {@link HistoryType#TYPE_UNKNOWN} if no history records are found.
     */
    public static synchronized @HistoryType int getLatestHistoryType(Context context) {
        long latestTimestamp = 0L;
        @HistoryType int latestHistoryType = HistoryType.TYPE_UNKNOWN;

        for (int curType : HISTORY_TYPE_TO_SP_NAME_MAPPING.keySet()) {
            LinkedList<Long> history = getHistory(context, curType);

            if (history != null && !history.isEmpty()) {
                long curTimestamp = history.peekLast();
                if (curTimestamp > latestTimestamp) {
                    latestTimestamp = curTimestamp;
                    latestHistoryType = curType;
                }
            }
        }
        return latestHistoryType;
    }

    private static void removeRecordsBeforeDay(LinkedList<Long> history, int day) {
        if (history == null || history.isEmpty()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        while (history.peekFirst() != null
                && dayDifference(currentTime, history.peekFirst()) >= day) {
            history.poll();
        }
    }

    private static String convertToHistoryString(LinkedList<Long> history) {
        return history.stream().map(Object::toString).collect(
                Collectors.joining(HISTORY_RECORD_DELIMITER));
    }
    private static LinkedList<Long> convertToHistoryList(String string) {
        if (string == null || string.isEmpty()) {
            return new LinkedList<>();
        }
        LinkedList<Long> ll = new LinkedList<>();
        String[] elements = string.split(HISTORY_RECORD_DELIMITER);
        for (String e: elements) {
            if (e.isEmpty()) continue;
            ll.offer(Long.parseLong(e));
        }
        return ll;
    }

    /**
     * Check if two timestamps are in the same date according to current timezone. This function
     * doesn't consider the original timezone when the timestamp is saved.
     *
     * @param t1 the first epoch timestamp
     * @param t2 the second epoch timestamp
     * @return {@code true} if two timestamps are on the same day
     */
    private static boolean isSameDay(long t1, long t2) {
        return dayDifference(t1, t2) == 0;
    }
    private static long dayDifference(long t1, long t2) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date1 = Instant.ofEpochMilli(t1).atZone(zoneId).toLocalDate();
        LocalDate date2 = Instant.ofEpochMilli(t2).atZone(zoneId).toLocalDate();
        return Math.abs(ChronoUnit.DAYS.between(date1, date2));
    }

    private static boolean isHearingDevice(CachedBluetoothDevice device) {
        return device.getProfiles().stream().anyMatch(p -> p instanceof HearingAidProfile);
    }

    private static boolean isLeHearingDevice(CachedBluetoothDevice device) {
        return device.getProfiles().stream().anyMatch(p -> p instanceof HapClientProfile);

    }

    private static boolean isHearableDevice(CachedBluetoothDevice device) {
        return device.getProfiles().stream().anyMatch(
                p -> (p instanceof A2dpProfile || p instanceof HeadsetProfile));
    }

    private static boolean isLeHearableDevice(CachedBluetoothDevice device) {
        var profiles = device.getProfiles();
        return profiles.stream().anyMatch(p -> p instanceof LeAudioProfile)
                && profiles.stream().noneMatch(
                        p -> (p instanceof HapClientProfile
                                || p instanceof HearingAidProfile));
    }

    private static boolean hasSufficientData(Context context, @HistoryType int historyType) {
        LinkedList<Long> history = getHistory(context, historyType);
        if (history == null) {
            return false;
        }

        if (historyType == HistoryType.TYPE_LE_HEARING_PAIRED
                || historyType == HistoryType.TYPE_HEARING_PAIRED
                || historyType == HistoryType.TYPE_LE_HEARABLE_PAIRED
                || historyType == HistoryType.TYPE_HEARABLE_PAIRED
        ) {
            return !history.isEmpty();
        } else {
            return history.size() >= VALID_CONNECTED_EVENT_COUNT;
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(ACCESSIBILITY_PREFERENCE, Context.MODE_PRIVATE);
    }

    private static final HashMap<Integer, String> HISTORY_TYPE_TO_SP_NAME_MAPPING;
    static {
        HISTORY_TYPE_TO_SP_NAME_MAPPING = new HashMap<>();
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_LE_HEARING_PAIRED, BT_LE_HEARING_PAIRED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_HEARING_PAIRED, BT_HEARING_PAIRED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_LE_HEARABLE_PAIRED, BT_LE_HEARABLE_PAIRED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_HEARABLE_PAIRED, BT_HEARABLE_PAIRED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_LE_HEARING_CONNECTED, BT_LE_HEARING_CONNECTED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_HEARING_CONNECTED, BT_HEARING_CONNECTED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_LE_HEARABLE_CONNECTED, BT_LE_HEARABLE_CONNECTED_HISTORY);
        HISTORY_TYPE_TO_SP_NAME_MAPPING.put(
                HistoryType.TYPE_HEARABLE_CONNECTED, BT_HEARABLE_CONNECTED_HISTORY);
    }

    private HearingDeviceStatsLogUtils() {}
}
