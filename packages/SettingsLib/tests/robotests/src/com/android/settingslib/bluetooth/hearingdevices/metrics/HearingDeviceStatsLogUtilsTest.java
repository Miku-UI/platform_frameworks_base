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

import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.CONNECTED_HISTORY_EXPIRED_DAY;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_HEARABLE_CONNECTED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_HEARABLE_PAIRED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_HEARING_CONNECTED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_HEARING_PAIRED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_LE_HEARABLE_CONNECTED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_LE_HEARABLE_PAIRED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_LE_HEARING_CONNECTED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_LE_HEARING_PAIRED;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.HistoryType.TYPE_UNKNOWN;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceStatsLogUtils.PAIRED_HISTORY_EXPIRED_DAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.util.FrameworkStatsLog;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.HapClientProfile;
import com.android.settingslib.bluetooth.HeadsetProfile;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LeAudioProfile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class HearingDeviceStatsLogUtilsTest {

    private static final String TEST_DEVICE_ADDRESS = "00:A1:A1:A1:A1:A1";
    private static final int TEST_HISTORY_TYPE = TYPE_HEARING_CONNECTED;

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;

    @Before
    public void setUp() {
        when(mCachedBluetoothDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS);
    }

    @Test
    public void setBondEntryForDevice_addsEntryToDeviceAddressToBondEntryMap() {
        HearingDeviceStatsLogUtils.setBondEntryForDevice(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH,
                mCachedBluetoothDevice);

        final HashMap<String, Integer> map =
                HearingDeviceStatsLogUtils.getDeviceAddressToBondEntryMap();
        assertThat(map.containsKey(TEST_DEVICE_ADDRESS)).isTrue();
        assertThat(map.get(TEST_DEVICE_ADDRESS)).isEqualTo(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH);
    }

    @Test
    public void logHearingAidInfo_removesEntryFromDeviceAddressToBondEntryMap() {
        HearingDeviceStatsLogUtils.setBondEntryForDevice(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH,
                mCachedBluetoothDevice);
        HearingDeviceStatsLogUtils.logHearingAidInfo(mCachedBluetoothDevice);

        final HashMap<String, Integer> map =
                HearingDeviceStatsLogUtils.getDeviceAddressToBondEntryMap();
        assertThat(map.containsKey(TEST_DEVICE_ADDRESS)).isFalse();
    }

    @Test
    public void addCurrentTimeToHistory_addNewData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long lastData = todayStartOfDay - TimeUnit.DAYS.toMillis(6);
        HearingDeviceStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, lastData);

        HearingDeviceStatsLogUtils.addCurrentTimeToHistory(mContext, TEST_HISTORY_TYPE);

        LinkedList<Long> history = HearingDeviceStatsLogUtils.getHistory(mContext,
                TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(2);
    }
    @Test
    public void addCurrentTimeToHistory_replaceSameDateData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        HearingDeviceStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, todayStartOfDay);

        final long newerTodayStartOfDay = todayStartOfDay + 1;
        HearingDeviceStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE,
                newerTodayStartOfDay);

        LinkedList<Long> history = HearingDeviceStatsLogUtils.getHistory(mContext,
                TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.getFirst()).isEqualTo(newerTodayStartOfDay);
    }

    @Test
    public void addCurrentTimeToHistory_cleanUpExpiredData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long expiredData = todayStartOfDay - TimeUnit.DAYS.toMillis(6) - 1;
        HearingDeviceStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, expiredData);

        HearingDeviceStatsLogUtils.addCurrentTimeToHistory(mContext, TEST_HISTORY_TYPE);

        LinkedList<Long> history = HearingDeviceStatsLogUtils.getHistory(mContext,
                TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.getFirst()).isNotEqualTo(expiredData);
    }

    @Test
    public void getUserCategory_hearingDevices() {
        prepareConnectedHistory(TYPE_HEARING_CONNECTED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_HEARING_DEVICES);

        preparePairedHistory(TYPE_HEARING_PAIRED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_NEW_HEARING_DEVICES);
    }

    @Test
    public void getUserCategory_hearableDevices() {
        prepareConnectedHistory(TYPE_HEARABLE_CONNECTED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_HEARABLE_DEVICES);

        preparePairedHistory(TYPE_HEARABLE_PAIRED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_NEW_HEARABLE_DEVICES);
    }

    @Test
    public void getUserCategory_leHearingDevices() {
        prepareConnectedHistory(TYPE_HEARING_CONNECTED);
        prepareConnectedHistory(TYPE_LE_HEARING_CONNECTED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_LE_HEARING_DEVICES);

        preparePairedHistory(TYPE_LE_HEARING_PAIRED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_NEW_LE_HEARING_DEVICES);
    }

    @Test
    public void getUserCategory_leHearableDevices() {
        prepareConnectedHistory(TYPE_HEARABLE_CONNECTED);
        prepareConnectedHistory(TYPE_LE_HEARABLE_CONNECTED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_LE_HEARABLE_DEVICES);

        preparePairedHistory(TYPE_LE_HEARABLE_PAIRED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_NEW_LE_HEARABLE_DEVICES);
    }

    @Test
    public void getUserCategory_bothHearingAndHearableDevices_returnHearingDevicesUser() {
        prepareConnectedHistory(TYPE_HEARING_CONNECTED);
        prepareConnectedHistory(TYPE_HEARABLE_CONNECTED);

        assertThat(HearingDeviceStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingDeviceStatsLogUtils.CATEGORY_HEARING_DEVICES);
    }

    @Test
    public void updateHistoryIfNeeded_ashaHearingDevice_ashaConnected_historyCorrect() {
        prepareAshaHearingDevice();

        HearingAidProfile ashaProfile = mock(HearingAidProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                ashaProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_hapClientConnected_historyCorrect() {
        prepareLeAudioHearingDevice();

        HapClientProfile hapClientProfile = mock(HapClientProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                hapClientProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_onlyLeAudioConnected_notBelongAnyYet() {
        prepareLeAudioHearingDevice();

        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_CONNECTED, 0);
        assertHistorySize(TYPE_LE_HEARING_CONNECTED, 0);

    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_hapAndLeAudioConnected_historyCorrect() {
        prepareLeAudioHearingDevice();

        HapClientProfile hapClientProfile = mock(HapClientProfile.class);
        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                hapClientProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_CONNECTED, 0);
        assertHistorySize(TYPE_LE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_hearableDevice_a2dpConnected_historyCorrect() {
        prepareHearableDevice();

        A2dpProfile a2dpProfile = mock(A2dpProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                a2dpProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARABLE_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leHearableDevice_leAudioConnected_historyCorrect() {
        prepareLeHearableDevice();

        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARABLE_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_ashaHearingDevice_justBonded_historyCorrect() {
        prepareAshaHearingDevice();
        HearingDeviceStatsLogUtils.addToJustBonded(TEST_DEVICE_ADDRESS);

        HearingAidProfile ashaProfile = mock(HearingAidProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                ashaProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_PAIRED, 1);
        assertHistorySize(TYPE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_justBonded_historyCorrect() {
        prepareLeAudioHearingDevice();
        HearingDeviceStatsLogUtils.addToJustBonded(TEST_DEVICE_ADDRESS);

        HapClientProfile hapClientProfile = mock(HapClientProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                hapClientProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARING_PAIRED, 1);
        assertHistorySize(TYPE_LE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_hearableDevice_justBonded_historyCorrect() {
        prepareHearableDevice();
        HearingDeviceStatsLogUtils.addToJustBonded(TEST_DEVICE_ADDRESS);

        A2dpProfile a2dpProfile = mock(A2dpProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                a2dpProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARABLE_PAIRED, 1);
        assertHistorySize(TYPE_HEARABLE_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leHearableDevice_justBonded_historyCorrect() {
        prepareLeHearableDevice();
        HearingDeviceStatsLogUtils.addToJustBonded(TEST_DEVICE_ADDRESS);

        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingDeviceStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARABLE_PAIRED, 1);
        assertHistorySize(TYPE_LE_HEARABLE_CONNECTED, 1);
    }

    @Test
    public void getLatestHistoryType_multipleHistory_returnLatestType() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        HearingDeviceStatsLogUtils.addToHistory(mContext, TYPE_HEARING_CONNECTED,
                todayStartOfDay);
        HearingDeviceStatsLogUtils.addToHistory(mContext, TYPE_LE_HEARABLE_PAIRED,
                todayStartOfDay + 1);
        HearingDeviceStatsLogUtils.addToHistory(mContext, TYPE_LE_HEARING_CONNECTED,
                todayStartOfDay + 2);

        assertThat(HearingDeviceStatsLogUtils.getLatestHistoryType(mContext)).isEqualTo(
                TYPE_LE_HEARING_CONNECTED);
    }

    @Test
    public void getLatestHistoryType_noHistory_typeUnknown() {
        assertThat(HearingDeviceStatsLogUtils.getLatestHistoryType(mContext)).isEqualTo(
                TYPE_UNKNOWN);
    }

    private long convertToStartOfDayTime(long timestamp) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate();
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    private void prepareConnectedHistory(int historyType) {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        for (int i = CONNECTED_HISTORY_EXPIRED_DAY - 1; i >= 0; i--) {
            final long data = todayStartOfDay - TimeUnit.DAYS.toMillis(i);
            HearingDeviceStatsLogUtils.addToHistory(mContext, historyType, data);
        }
    }

    private void preparePairedHistory(int historyType) {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long data = todayStartOfDay - TimeUnit.DAYS.toMillis(PAIRED_HISTORY_EXPIRED_DAY - 1);
        HearingDeviceStatsLogUtils.addToHistory(mContext, historyType, data);
    }

    private void prepareAshaHearingDevice() {
        doReturn(List.of(mock(HearingAidProfile.class))).when(mCachedBluetoothDevice).getProfiles();
    }

    private void prepareLeAudioHearingDevice() {
        doReturn(List.of(mock(HapClientProfile.class), mock(LeAudioProfile.class))).when(
                mCachedBluetoothDevice).getProfiles();
    }

    private void prepareHearableDevice() {
        doReturn(List.of(mock(A2dpProfile.class), mock(HeadsetProfile.class))).when(
                mCachedBluetoothDevice).getProfiles();
    }

    private void prepareLeHearableDevice() {
        doReturn(List.of(mock(LeAudioProfile.class))).when(mCachedBluetoothDevice).getProfiles();
    }

    private void assertHistorySize(int type, int size) {
        LinkedList<Long> history = HearingDeviceStatsLogUtils.getHistory(mContext, type);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(size);
    }
}
