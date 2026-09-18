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

package com.android.settingslib.bluetooth.hearingdevices.ui;

import static android.bluetooth.AudioInputControl.MUTE_MUTED;
import static android.bluetooth.AudioInputControl.MUTE_NOT_MUTED;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;

import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT;
import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT;
import static com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.SIDE_UNIFIED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;

import androidx.test.core.app.ApplicationProvider;

import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.bluetooth.VolumeControlProfile;
import com.android.settingslib.bluetooth.hearingdevices.AmbientVolumeController;
import com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceLocalDataManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/** Tests for {@link AmbientVolumeUiController}. */
@RunWith(RobolectricTestRunner.class)
public class AmbientVolumeUiControllerTest {

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    private static final String TEST_ADDRESS = "00:00:00:00:11";
    private static final String TEST_MEMBER_ADDRESS = "00:00:00:00:22";
    private static final int TEST_AMBIENT_MAX = 60;
    private static final int TEST_AMBIENT_MIN = -30;
    private static final int TEST_AMBIENT = 10;
    private static final int TEST_MEMBER_AMBIENT = 20;

    @Mock
    LocalBluetoothManager mBluetoothManager;
    @Mock
    LocalBluetoothProfileManager mProfileManager;
    @Mock
    BluetoothEventManager mEventManager;
    @Mock
    VolumeControlProfile mVolumeControlProfile;
    @Mock
    AmbientVolumeUi mAmbientLayout;
    @Mock
    private AmbientVolumeController mVolumeController;
    @Mock
    private HearingDeviceLocalDataManager mLocalDataManager;
    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private CachedBluetoothDevice mCachedMemberDevice;
    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private BluetoothDevice mMemberDevice;
    @Mock
    private Handler mTestHandler;

    @Spy
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private AmbientVolumeUiController mController;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        when(mBluetoothManager.getProfileManager()).thenReturn(mProfileManager);
        when(mBluetoothManager.getEventManager()).thenReturn(mEventManager);

        mController = spy(new AmbientVolumeUiController(mContext, mBluetoothManager,
                mAmbientLayout, mVolumeController, mLocalDataManager));

        when(mProfileManager.getVolumeControlProfile()).thenReturn(mVolumeControlProfile);
        when(mVolumeControlProfile.getConnectionStatus(mDevice)).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        when(mVolumeControlProfile.getConnectionStatus(mMemberDevice)).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        when(mVolumeController.isAmbientControlAvailable(mDevice)).thenReturn(true);
        when(mVolumeController.getAmbientMax(mDevice)).thenReturn(TEST_AMBIENT_MAX);
        when(mVolumeController.getAmbientMin(mDevice)).thenReturn(TEST_AMBIENT_MIN);
        when(mVolumeController.isAmbientControlAvailable(mMemberDevice)).thenReturn(true);
        when(mVolumeController.getAmbientMax(mMemberDevice)).thenReturn(TEST_AMBIENT_MAX);
        when(mVolumeController.getAmbientMin(mMemberDevice)).thenReturn(TEST_AMBIENT_MIN);
        when(mLocalDataManager.get(any(BluetoothDevice.class))).thenReturn(
                new HearingDeviceLocalDataManager.Data.Builder().build());

        when(mContext.getMainThreadHandler()).thenReturn(mTestHandler);
        Answer<Object> answer = invocationOnMock -> {
            invocationOnMock.getArgument(0, Runnable.class).run();
            return null;
        };
        when(mTestHandler.post(any(Runnable.class))).thenAnswer(answer);
        when(mTestHandler.postDelayed(any(Runnable.class), anyLong())).thenAnswer(answer);

        prepareDevice(/* hasMember= */ true);
        prepareRemoteData(mDevice, TEST_AMBIENT, MUTE_NOT_MUTED);
        prepareRemoteData(mMemberDevice, TEST_MEMBER_AMBIENT, MUTE_NOT_MUTED);
        mController.loadDevice(mCachedDevice);
        Mockito.reset(mController);
        Mockito.reset(mAmbientLayout);
    }

    @Test
    public void loadDevice_deviceWithoutMember_controlNotExpandable() {
        prepareDevice(/* hasMember= */ false);

        mController.loadDevice(mCachedDevice);

        verify(mAmbientLayout).setControlExpandable(false);
    }

    @Test
    public void loadDevice_deviceWithMember_controlExpandable() {
        prepareDevice(/* hasMember= */ true);

        mController.loadDevice(mCachedDevice);

        verify(mAmbientLayout).setControlExpandable(true);
    }

    @Test
    public void loadDevice_deviceNotSupportVcp_ambientLayoutGone() {
        when(mCachedDevice.getProfiles()).thenReturn(List.of());

        mController.loadDevice(mCachedDevice);

        verify(mAmbientLayout).setVisible(false);
    }

    @Test
    public void refresh_ambientControlNotAvailable_ambientLayoutGone() {
        when(mVolumeController.isAmbientControlAvailable(mDevice)).thenReturn(false);
        when(mVolumeController.isAmbientControlAvailable(mMemberDevice)).thenReturn(false);

        mController.loadDevice(mCachedDevice);
        mController.refresh();

        verify(mAmbientLayout).setVisible(false);
    }

    @Test
    public void refresh_supportVcpAndAmbientControlAvailable_ambientLayoutVisible() {
        when(mCachedDevice.getProfiles()).thenReturn(List.of(mVolumeControlProfile));
        when(mVolumeController.isAmbientControlAvailable(mDevice)).thenReturn(true);

        mController.loadDevice(mCachedDevice);
        mController.refresh();

        verify(mAmbientLayout).setVisible(true);
    }

    @Test
    public void start_callbackRegistered() {
        mController.start();

        verify(mEventManager).registerCallback(mController);
        verify(mLocalDataManager).start();
        verify(mVolumeController).registerCallback(any(Executor.class), eq(mDevice));
        verify(mVolumeController).registerCallback(any(Executor.class), eq(mMemberDevice));
        verify(mCachedDevice).registerCallback(any(Executor.class),
                any(CachedBluetoothDevice.Callback.class));
        verify(mCachedMemberDevice).registerCallback(any(Executor.class),
                any(CachedBluetoothDevice.Callback.class));
        verify(mController).refresh();
    }

    @Test
    public void stop_callbackUnregistered() {
        mController.start();
        mController.stop();

        verify(mEventManager).unregisterCallback(mController);
        verify(mLocalDataManager).stop();
        verify(mVolumeController).unregisterCallback(mDevice);
        verify(mVolumeController).unregisterCallback(mMemberDevice);
        verify(mCachedDevice).unregisterCallback(any(CachedBluetoothDevice.Callback.class));
        verify(mCachedMemberDevice).unregisterCallback(any(CachedBluetoothDevice.Callback.class));
    }

    @Test
    public void onDeviceLocalDataChange_verifySetControlExpandedAndDataUpdated() {
        HearingDeviceLocalDataManager.Data data = prepareLocalData(30, 40, true);
        when(mLocalDataManager.get(mDevice)).thenReturn(data);

        mController.onDeviceLocalDataChange(TEST_ADDRESS, data);

        verify(mAmbientLayout).setControlExpanded(true);
        verify(mLocalDataManager).updateAmbient(mDevice, 30);
        verify(mLocalDataManager).updateGroupAmbient(mDevice, 40);
        verify(mLocalDataManager).updateAmbientControlExpanded(mDevice, true);
    }

    @Test
    public void onAmbientChanged_controlExpanded_updateUiIfDifferent() {
        HearingDeviceLocalDataManager.Data data = prepareLocalData(10, 10, true);
        when(mLocalDataManager.get(mDevice)).thenReturn(data);
        when(mAmbientLayout.isControlExpanded()).thenReturn(true);

        mController.onAmbientChanged(mDevice, 10);
        verify(mAmbientLayout, never()).setSliderValue(SIDE_LEFT, 10);

        mController.onAmbientChanged(mDevice, 20);
        verify(mAmbientLayout).setSliderValue(SIDE_LEFT, 20);
    }

    @Test
    public void onAmbientChanged_controlNotExpanded_refreshUiIfDifferent() {
        HearingDeviceLocalDataManager.Data data = prepareLocalData(10, 10, false);
        when(mLocalDataManager.get(mDevice)).thenReturn(data);
        when(mAmbientLayout.isControlExpanded()).thenReturn(false);

        mController.onAmbientChanged(mDevice, 10);
        verify(mController, never()).refresh();

        mController.onAmbientChanged(mDevice, 20);
        verify(mController).refresh();
    }

    @Test
    public void onMuteChanged_controlExpanded_updateUiIfDifferent() {
        when(mAmbientLayout.getSliderMuteState(SIDE_LEFT)).thenReturn(MUTE_NOT_MUTED);
        when(mAmbientLayout.isControlExpanded()).thenReturn(true);

        mController.onMuteChanged(mDevice, MUTE_NOT_MUTED);
        verify(mAmbientLayout, never()).setSliderMuteState(SIDE_LEFT, MUTE_NOT_MUTED);

        mController.onMuteChanged(mDevice, MUTE_MUTED);
        verify(mAmbientLayout).setSliderMuteState(SIDE_LEFT, MUTE_MUTED);
    }

    @Test
    public void onMuteChanged_controlNotExpanded_expandUiIfDifferent() {
        when(mAmbientLayout.getSliderMuteState(SIDE_LEFT)).thenReturn(MUTE_NOT_MUTED);
        when(mAmbientLayout.getSliderMuteState(SIDE_UNIFIED)).thenReturn(MUTE_NOT_MUTED);
        when(mAmbientLayout.isControlExpanded()).thenReturn(false);

        mController.onMuteChanged(mDevice, MUTE_NOT_MUTED);
        verify(mAmbientLayout, never()).setControlExpanded(true);

        mController.onMuteChanged(mDevice, MUTE_MUTED);
        verify(mAmbientLayout).setControlExpanded(true);
    }

    @Test
    public void refresh_leftAndRightDifferentGainSetting_expandControl() {
        prepareRemoteData(mDevice, 10, MUTE_NOT_MUTED);
        prepareRemoteData(mMemberDevice, 20, MUTE_NOT_MUTED);
        when(mAmbientLayout.isControlExpanded()).thenReturn(false);

        mController.refresh();

        verify(mAmbientLayout).setControlExpanded(true);
    }

    @Test
    public void refresh_leftAndRightDifferentMuteState_expandControl() {
        prepareRemoteData(mDevice, 10, MUTE_MUTED);
        prepareRemoteData(mMemberDevice, 10, MUTE_NOT_MUTED);
        when(mAmbientLayout.isControlExpanded()).thenReturn(false);

        mController.refresh();

        verify(mAmbientLayout).setControlExpanded(true);
    }

    private void prepareDevice(boolean hasMember) {
        when(mCachedDevice.getDeviceSide()).thenReturn(SIDE_LEFT);
        when(mCachedDevice.getDevice()).thenReturn(mDevice);
        when(mCachedDevice.getBondState()).thenReturn(BOND_BONDED);
        when(mCachedDevice.getProfiles()).thenReturn(List.of(mVolumeControlProfile));
        when(mDevice.getAddress()).thenReturn(TEST_ADDRESS);
        when(mDevice.getAnonymizedAddress()).thenReturn(TEST_ADDRESS);
        when(mDevice.isConnected()).thenReturn(true);
        if (hasMember) {
            when(mCachedDevice.getMemberDevice()).thenReturn(Set.of(mCachedMemberDevice));
            when(mCachedMemberDevice.getDeviceSide()).thenReturn(SIDE_RIGHT);
            when(mCachedMemberDevice.getDevice()).thenReturn(mMemberDevice);
            when(mCachedMemberDevice.getBondState()).thenReturn(BOND_BONDED);
            when(mCachedMemberDevice.getProfiles()).thenReturn(List.of(mVolumeControlProfile));
            when(mMemberDevice.getAddress()).thenReturn(TEST_MEMBER_ADDRESS);
            when(mMemberDevice.getAnonymizedAddress()).thenReturn(TEST_MEMBER_ADDRESS);
            when(mMemberDevice.isConnected()).thenReturn(true);
        } else {
            when(mCachedDevice.getMemberDevice()).thenReturn(Set.of());
        }
    }

    private void prepareRemoteData(BluetoothDevice device, int gainSetting, int mute) {
        when(mVolumeController.refreshAmbientState(device)).thenReturn(
                new AmbientVolumeController.RemoteAmbientState(gainSetting, mute));
    }

    private HearingDeviceLocalDataManager.Data prepareLocalData(int ambient, int groupAmbient,
            boolean expanded) {
        return new HearingDeviceLocalDataManager.Data.Builder()
                .ambient(ambient).groupAmbient(groupAmbient).ambientControlExpanded(
                        expanded).build();
    }
}
