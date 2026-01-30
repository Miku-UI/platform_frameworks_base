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

import static android.bluetooth.AudioInputControl.MUTE_DISABLED;
import static android.bluetooth.AudioInputControl.MUTE_MUTED;
import static android.bluetooth.AudioInputControl.MUTE_NOT_MUTED;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;

import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_INVALID;
import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT;
import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT;
import static com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceLocalDataManager.Data.INVALID_VOLUME;
import static com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.SIDE_UNIFIED;
import static com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.VALID_SIDES;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.ArraySet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.R;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.VolumeControlProfile;
import com.android.settingslib.bluetooth.hearingdevices.AmbientVolumeController;
import com.android.settingslib.bluetooth.hearingdevices.metrics.HearingDeviceLocalDataManager;
import com.android.settingslib.utils.ThreadUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Map;
import java.util.Set;

/** This class controls ambient volume UI with local and remote ambient data. */
public class AmbientVolumeUiController implements
        HearingDeviceLocalDataManager.OnDeviceLocalDataChangeListener,
        AmbientVolumeController.AmbientVolumeControlCallback,
        AmbientVolumeUi.AmbientVolumeUiListener, BluetoothCallback, CachedBluetoothDevice.Callback {

    private static final boolean DEBUG = true;
    private static final String TAG = "AmbientVolumeUiController";

    private final Context mContext;
    private final BluetoothEventManager mEventManager;
    private final AmbientVolumeUi mAmbientLayout;
    private final AmbientVolumeController mVolumeController;
    private final HearingDeviceLocalDataManager mLocalDataManager;

    private final Set<CachedBluetoothDevice> mCachedDevices = new ArraySet<>();
    private final BiMap<Integer, BluetoothDevice> mSideToDeviceMap = HashBiMap.create();
    private final Set<Integer> mRangeInitializedSliderSides = new ArraySet<>();
    private CachedBluetoothDevice mCachedDevice;
    private boolean mShowUiWhenLocalDataExist = true;
    private boolean mStarted = false;

    public AmbientVolumeUiController(@NonNull Context context,
            @NonNull LocalBluetoothManager bluetoothManager,
            @NonNull AmbientVolumeUi ambientLayout) {
        mContext = context;
        mEventManager = bluetoothManager.getEventManager();
        mAmbientLayout = ambientLayout;
        mAmbientLayout.setListener(this);
        mVolumeController = new AmbientVolumeController(bluetoothManager.getProfileManager(), this);
        mLocalDataManager = new HearingDeviceLocalDataManager(context);
        mLocalDataManager.setOnDeviceLocalDataChangeListener(this,
                ThreadUtils.getBackgroundExecutor());
    }

    @VisibleForTesting
    public AmbientVolumeUiController(@NonNull Context context,
            @NonNull LocalBluetoothManager bluetoothManager,
            @NonNull AmbientVolumeUi ambientLayout,
            @NonNull AmbientVolumeController volumeController,
            @NonNull HearingDeviceLocalDataManager localDataManager) {
        mContext = context;
        mEventManager = bluetoothManager.getEventManager();
        mAmbientLayout = ambientLayout;
        mVolumeController = volumeController;
        mLocalDataManager = localDataManager;
    }


    @Override
    public void onDeviceLocalDataChange(@NonNull String address,
            @Nullable HearingDeviceLocalDataManager.Data data) {
        if (data == null) {
            // The local data is removed because the device is unpaired, do nothing
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onDeviceLocalDataChange, address:" + address + ", data:" + data);
        }
        for (BluetoothDevice device : mSideToDeviceMap.values()) {
            if (device.getAnonymizedAddress().equals(address)) {
                postOnMainThread(() -> loadLocalDataToUi(device));
                return;
            }
        }
    }

    @Override
    public void onVolumeControlServiceConnected() {
        mCachedDevices.forEach(device -> mVolumeController.registerCallback(
                ThreadUtils.getBackgroundExecutor(), device.getDevice()));
    }

    @Override
    public void onAmbientChanged(@NonNull BluetoothDevice device, int gainSettings) {
        if (DEBUG) {
            Log.d(TAG, "onAmbientChanged, value:" + gainSettings + ", device:" + device);
        }
        HearingDeviceLocalDataManager.Data data = mLocalDataManager.get(device);
        final boolean expanded = mAmbientLayout.isControlExpanded();
        final boolean isInitiatedFromUi = (expanded && data.ambient() == gainSettings)
                || (!expanded && data.groupAmbient() == gainSettings);
        if (isInitiatedFromUi) {
            // The change is initiated from UI, no need to update UI
            return;
        }

        // We have to check if we need to expand the controls by getting all remote
        // device's ambient value, delay for a while to wait all remote devices update
        // to the latest value to avoid unnecessary expand action.
        postDelayedOnMainThread(this::refresh, 1200L);
    }

    @Override
    public void onMuteChanged(@NonNull BluetoothDevice device, int mute) {
        if (DEBUG) {
            Log.d(TAG, "onMuteChanged, mute:" + mute + ", device:" + device);
        }
        final boolean muted = mAmbientLayout.isMuted();
        boolean isInitiatedFromUi = (muted && mute == MUTE_MUTED)
                || (!muted && mute == MUTE_NOT_MUTED);
        if (isInitiatedFromUi) {
            // The change is initiated from UI, no need to update UI
            return;
        }

        // We have to check if we need to mute the devices by getting all remote
        // device's mute state, delay for a while to wait all remote devices update
        // to the latest value.
        postDelayedOnMainThread(this::refresh, 1200L);
    }

    @Override
    public void onCommandFailed(@NonNull BluetoothDevice device) {
        Log.w(TAG, "onCommandFailed, device:" + device);
        postOnMainThread(() -> {
            showErrorToast(R.string.bluetooth_hearing_device_ambient_error);
            refresh();
        });
    }

    @Override
    public void onExpandIconClick() {
        mSideToDeviceMap.forEach((s, d) -> {
            if (!isDeviceMuted(d)) {
                // Apply previous collapsed/expanded volume to remote device
                HearingDeviceLocalDataManager.Data data = mLocalDataManager.get(d);
                int volume = mAmbientLayout.isControlExpanded()
                        ? data.ambient() : data.groupAmbient();
                mVolumeController.setAmbient(d, volume);
            }
            // Update new value to local data
            mLocalDataManager.updateAmbientControlExpanded(d, mAmbientLayout.isControlExpanded());
        });
        mLocalDataManager.flush();
    }

    @Override
    public void onAmbientVolumeIconClick() {
        if (!mAmbientLayout.isMuted()) {
            loadLocalDataToUi();
        }
        for (BluetoothDevice device : mSideToDeviceMap.values()) {
            mVolumeController.setMuted(device, mAmbientLayout.isMuted());
        }
    }

    @Override
    public void onSliderValueChange(int side, int value) {
        if (DEBUG) {
            Log.d(TAG, "onSliderValueChange: side=" + side + ", value=" + value);
        }
        setVolumeIfValid(side, value);

        Runnable setAmbientRunnable = () -> {
            if (side == SIDE_UNIFIED) {
                mSideToDeviceMap.forEach((s, d) -> mVolumeController.setAmbient(d, value));
            } else {
                final BluetoothDevice device = mSideToDeviceMap.get(side);
                mVolumeController.setAmbient(device, value);
            }
        };

        boolean performUnmuteAction = false;
        if (side == SIDE_UNIFIED) {
            if (mAmbientLayout.isMuted()) {
                // User drag on the unified slider when muted. Unmute all devices first.
                mAmbientLayout.setSliderMuteState(side, MUTE_NOT_MUTED);
                for (BluetoothDevice device : mSideToDeviceMap.values()) {
                    mVolumeController.setMuted(device, false);
                }
                performUnmuteAction = true;
            }
        } else {
            final BluetoothDevice device = mSideToDeviceMap.get(side);
            if (isDeviceMuted(device)) {
                // User drag on the slider when muted. Unmute the device first.
                mAmbientLayout.setSliderMuteState(side, MUTE_NOT_MUTED);
                mVolumeController.setMuted(device, false);
                performUnmuteAction = true;

            }
        }
        if (performUnmuteAction) {
            // Delay set ambient on remote device since the immediately sequential command
            // might get failed sometimes
            postDelayedOnMainThread(setAmbientRunnable, 1000L);
        } else {
            setAmbientRunnable.run();
        }
    }

    @Override
    public void onProfileConnectionStateChanged(@NonNull CachedBluetoothDevice cachedDevice,
            int state, int bluetoothProfile) {
        if (bluetoothProfile == BluetoothProfile.VOLUME_CONTROL
                && state == BluetoothProfile.STATE_CONNECTED
                && mCachedDevices.contains(cachedDevice)) {
            // After VCP connected, AICS may not ready yet and still return invalid value, delay
            // a while to wait AICS ready as a workaround
            postDelayedOnMainThread(this::refresh, 1000L);
        }
    }

    @Override
    public void onDeviceAttributesChanged() {
        mCachedDevices.forEach(device -> {
            device.unregisterCallback(this);
            mVolumeController.unregisterCallback(device.getDevice());
        });
        postOnMainThread(()-> {
            loadDevice(mCachedDevice);
            ThreadUtils.postOnBackgroundThread(()-> {
                mCachedDevices.forEach(device -> {
                    device.registerCallback(ThreadUtils.getBackgroundExecutor(), this);
                    mVolumeController.registerCallback(ThreadUtils.getBackgroundExecutor(),
                            device.getDevice());
                });
            });
        });
    }

    /**
     * Registers callbacks and listeners, this should be called when needs to start listening to
     * events.
     */
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        mEventManager.registerCallback(this);
        mLocalDataManager.start();
        mCachedDevices.forEach(device -> {
            device.registerCallback(ThreadUtils.getBackgroundExecutor(), this);
            mVolumeController.registerCallback(ThreadUtils.getBackgroundExecutor(),
                    device.getDevice());
        });
        postOnMainThread(this::refresh);
    }

    /**
     * Unregisters callbacks and listeners, this should be called when no longer needs to listen to
     * events.
     */
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mEventManager.unregisterCallback(this);
        mLocalDataManager.stop();
        mCachedDevices.forEach(device -> {
            device.unregisterCallback(this);
            mVolumeController.unregisterCallback(device.getDevice());
        });
    }

    /**
     * Loads all devices in the same set with {@code cachedDevice} and create corresponding sliders.
     *
     * <p>If the devices has valid ambient control points, the ambient volume UI will be visible.
     * @param cachedDevice the remote device
     */
    public void loadDevice(CachedBluetoothDevice cachedDevice) {
        if (DEBUG) {
            Log.d(TAG, "loadDevice, device=" + cachedDevice);
        }
        mCachedDevice = cachedDevice;
        mSideToDeviceMap.clear();
        mCachedDevices.clear();
        boolean deviceSupportVcp =
                cachedDevice != null && cachedDevice.getProfiles().stream().anyMatch(
                        p -> p instanceof VolumeControlProfile);
        if (!deviceSupportVcp) {
            mAmbientLayout.setVisible(false);
            return;
        }

        // load devices in the same set
        if (VALID_SIDES.contains(cachedDevice.getDeviceSide())
                && cachedDevice.getBondState() == BOND_BONDED) {
            mSideToDeviceMap.put(cachedDevice.getDeviceSide(), cachedDevice.getDevice());
            mCachedDevices.add(cachedDevice);
        }
        for (CachedBluetoothDevice memberDevice : cachedDevice.getMemberDevice()) {
            if (VALID_SIDES.contains(memberDevice.getDeviceSide())
                    && memberDevice.getBondState() == BOND_BONDED) {
                mSideToDeviceMap.put(memberDevice.getDeviceSide(), memberDevice.getDevice());
                mCachedDevices.add(memberDevice);
            }
        }

        mAmbientLayout.setControlExpandable(mSideToDeviceMap.size() >  1);
        mAmbientLayout.setupSliders(mSideToDeviceMap.keySet());
        if (mStarted) {
            // We have to check if we need to expand the controls by getting all remote
            // device's ambient value, delay for a while to wait all remote devices connected and
            // updated to the latest value to avoid unnecessary expand action.
            postDelayedOnMainThread(this::refresh, 1200L);
        }
    }

    /** Refreshes the ambient volume UI. */
    public void refresh() {
        if (isAmbientControlAvailable()) {
            mAmbientLayout.setVisible(true);
            loadRemoteDataToUi();
        } else {
            mAmbientLayout.setVisible(false);
        }
    }

    /** Sets if the ambient volume UI should be visible when local ambient data exist. */
    public void setShowUiWhenLocalDataExist(boolean shouldShow) {
        mShowUiWhenLocalDataExist = shouldShow;
    }

    /** Sets the ambient to the corresponding control slider. */
    private void setVolumeIfValid(int side, int volume) {
        if (volume == INVALID_VOLUME) {
            return;
        }
        if (!mRangeInitializedSliderSides.contains(side)) {
            Log.w(TAG, "the slider is not initialized yet, skip set volume on side=" + side);
            return;
        }
        mAmbientLayout.setSliderValue(side, volume);
        // Update new value to local data
        if (side == SIDE_UNIFIED) {
            mSideToDeviceMap.forEach((s, d) -> mLocalDataManager.updateGroupAmbient(d, volume));
        } else {
            mLocalDataManager.updateAmbient(mSideToDeviceMap.get(side), volume);
        }
        mLocalDataManager.flush();
    }

    private void loadLocalDataToUi() {
        mSideToDeviceMap.forEach((s, d) -> loadLocalDataToUi(d));
    }

    private void loadLocalDataToUi(BluetoothDevice device) {
        final HearingDeviceLocalDataManager.Data data = mLocalDataManager.get(device);
        if (DEBUG) {
            Log.d(TAG, "loadLocalDataToUi, data=" + data + ", device=" + device);
        }
        if (isDeviceAmbientControlAvailable(device) && !isDeviceMuted(device)) {
            final int side = mSideToDeviceMap.inverse().getOrDefault(device, SIDE_INVALID);
            setVolumeIfValid(side, data.ambient());
            setVolumeIfValid(SIDE_UNIFIED, data.groupAmbient());
        }
        setAmbientControlExpanded(data.ambientControlExpanded());
    }

    private void loadRemoteDataToUi() {
        BluetoothDevice leftDevice = mSideToDeviceMap.get(SIDE_LEFT);
        AmbientVolumeController.RemoteAmbientState leftState =
                mVolumeController.refreshAmbientState(leftDevice);
        BluetoothDevice rightDevice = mSideToDeviceMap.get(SIDE_RIGHT);
        AmbientVolumeController.RemoteAmbientState rightState =
                mVolumeController.refreshAmbientState(rightDevice);
        if (DEBUG) {
            Log.d(TAG, "loadRemoteDataToUi, left=" + leftState + ", right=" + rightState);
        }
        // Update ambient range. This should be done first since the muted state and enabled state
        // will set the value to minimum value
        mSideToDeviceMap.forEach((side, device) -> {
            if (!mRangeInitializedSliderSides.contains(side)) {
                int ambientMax = mVolumeController.getAmbientMax(device);
                int ambientMin = mVolumeController.getAmbientMin(device);
                if (ambientMin != ambientMax) {
                    mAmbientLayout.setSliderRange(side, ambientMin, ambientMax);
                    mAmbientLayout.setSliderRange(SIDE_UNIFIED, ambientMin, ambientMax);
                    mRangeInitializedSliderSides.add(side);
                    mRangeInitializedSliderSides.add(SIDE_UNIFIED);
                }
            }
        });

        // Check the remote mute state to decide if we need to expand the control. This should be
        // done before updating ambient value since it'll affect the controls expanded state
        final int leftMuteState = leftState != null ? leftState.mute() : MUTE_DISABLED;
        final int rightMuteState = rightState != null ? rightState.mute() : MUTE_DISABLED;
        if (leftMuteState != MUTE_DISABLED && rightMuteState != MUTE_DISABLED
                && leftMuteState != rightMuteState) {
            // Expand the controls if two devices are mutable but with different mute states
            setAmbientControlExpanded(true);
        }

        // Update ambient volume
        final int leftAmbient = leftState != null ? leftState.gainSetting() : INVALID_VOLUME;
        final int rightAmbient = rightState != null ? rightState.gainSetting() : INVALID_VOLUME;
        if (mAmbientLayout.isControlExpanded()) {
            setVolumeIfValid(SIDE_LEFT, leftAmbient);
            setVolumeIfValid(SIDE_RIGHT, rightAmbient);
        } else {
            if (leftAmbient != INVALID_VOLUME && rightAmbient != INVALID_VOLUME
                    && leftAmbient != rightAmbient) {
                // Expand the controls if two devices have different ambient values
                setVolumeIfValid(SIDE_LEFT, leftAmbient);
                setVolumeIfValid(SIDE_RIGHT, rightAmbient);
                setAmbientControlExpanded(true);
            } else {
                int unifiedAmbient = leftAmbient != INVALID_VOLUME ? leftAmbient : rightAmbient;
                setVolumeIfValid(SIDE_UNIFIED, unifiedAmbient);
            }
        }
        // Initialize local data between side and group value
        initLocalAmbientDataIfNeeded();

        // Update slider mute state. This should be done after loading remote ambient into local
        // database since we'll show minimum value of the slider instead of the remote value if the
        // device is muted
        mAmbientLayout.setSliderMuteState(SIDE_LEFT, leftMuteState);
        mAmbientLayout.setSliderMuteState(SIDE_RIGHT, rightMuteState);

        // Update slider enabled state. This should be done after loading remote ambient into local
        // database since we'll show minimum value of the slider instead of the remote value if the
        // slider is not enabled.
        boolean isAnySliderEnabled = false;
        for (Map.Entry<Integer, BluetoothDevice> entry : mSideToDeviceMap.entrySet()) {
            final int side = entry.getKey();
            final BluetoothDevice device = entry.getValue();
            final boolean enabled = isDeviceAmbientControlAvailable(device);
            isAnySliderEnabled |= enabled;
            mAmbientLayout.setSliderEnabled(side, enabled);
        }
        mAmbientLayout.setSliderEnabled(SIDE_UNIFIED, isAnySliderEnabled);
    }

    private void setAmbientControlExpanded(boolean expanded) {
        mAmbientLayout.setControlExpanded(expanded);
        mSideToDeviceMap.forEach((s, d) -> {
            // Update new value to local data
            mLocalDataManager.updateAmbientControlExpanded(d, expanded);
        });
        mLocalDataManager.flush();
    }

    /** Checks if any device in the same set has valid ambient control points */
    public boolean isAmbientControlAvailable() {
        for (BluetoothDevice device : mSideToDeviceMap.values()) {
            if (mShowUiWhenLocalDataExist) {
                // Found local ambient data
                if (mLocalDataManager.get(device).hasAmbientData()) {
                    return true;
                }
            }
            // Found remote ambient control points
            if (isDeviceAmbientControlAvailable(device)) {
                return true;
            }
        }
        return false;
    }

    private void initLocalAmbientDataIfNeeded() {
        int smallerVolumeAmongGroup = Integer.MAX_VALUE;
        for (BluetoothDevice device : mSideToDeviceMap.values()) {
            HearingDeviceLocalDataManager.Data data = mLocalDataManager.get(device);
            if (data.ambient() != INVALID_VOLUME) {
                smallerVolumeAmongGroup = Math.min(data.ambient(), smallerVolumeAmongGroup);
            } else if (data.groupAmbient() != INVALID_VOLUME) {
                // Initialize side ambient from group ambient value
                mLocalDataManager.updateAmbient(device, data.groupAmbient());
            }
        }
        if (smallerVolumeAmongGroup != Integer.MAX_VALUE) {
            for (BluetoothDevice device : mSideToDeviceMap.values()) {
                HearingDeviceLocalDataManager.Data data = mLocalDataManager.get(device);
                if (data.groupAmbient() == INVALID_VOLUME) {
                    // Initialize group ambient from smaller side ambient value
                    mLocalDataManager.updateGroupAmbient(device, smallerVolumeAmongGroup);
                }
            }
        }
        mLocalDataManager.flush();
    }

    private boolean isDeviceMuted(BluetoothDevice device) {
        final int side = mSideToDeviceMap.inverse().getOrDefault(device, SIDE_INVALID);
        return mAmbientLayout.getSliderMuteState(side) == MUTE_MUTED;
    }

    private boolean isDeviceAmbientControlAvailable(BluetoothDevice device) {
        return device.isConnected() && mVolumeController.isAmbientControlAvailable(device);
    }

    private void postOnMainThread(Runnable runnable) {
        mContext.getMainThreadHandler().post(runnable);
    }

    private void postDelayedOnMainThread(Runnable runnable, long delay) {
        mContext.getMainThreadHandler().postDelayed(runnable, delay);
    }

    private void showErrorToast(int stringResId) {
        Toast.makeText(mContext, stringResId, Toast.LENGTH_SHORT).show();
    }
}
