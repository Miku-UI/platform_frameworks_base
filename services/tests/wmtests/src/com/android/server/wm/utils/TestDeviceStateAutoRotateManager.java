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

package com.android.server.wm.utils;

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager;
import com.android.settingslib.devicestate.PostureDeviceStateConverter;
import com.android.settingslib.devicestate.SettableDeviceState;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A test implementation of {@link DeviceStateAutoRotateSettingManager} for use in tests.
 *
 * <p>This class provides a way to simulate the behavior of
 * {@link DeviceStateAutoRotateSettingManager} and allows for setting and verifying the device
 * state auto-rotate settings in a test environment.
 */
public class TestDeviceStateAutoRotateManager implements DeviceStateAutoRotateSettingManager {
    @Nullable
    private TestDeviceStateAutoRotateSetting mDeviceStateAutoRotateSetting =
            new TestDeviceStateAutoRotateSetting();
    @Nullable
    private TestDeviceStateAutoRotateSetting mDefaultDeviceStateAutoRotateSetting =
            new TestDeviceStateAutoRotateSetting();
    @NonNull
    private final PostureDeviceStateConverter mPostureDeviceStateConverter;

    private final List<DeviceStateAutoRotateSettingListener> mListeners = new ArrayList<>();

    public TestDeviceStateAutoRotateManager(
            @NonNull PostureDeviceStateConverter postureDeviceStateConverter) {
        mPostureDeviceStateConverter = postureDeviceStateConverter;
    }

    @Override
    public void registerListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        mListeners.add(settingListener);
    }

    @Override
    public void unregisterListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        mListeners.remove(settingListener);
    }

    @Override
    public void updateSetting(int deviceState, boolean rotationLock) {
        if (mDeviceStateAutoRotateSetting == null) return;
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        mDeviceStateAutoRotateSetting.set(devicePosture,
                rotationLock ? DEVICE_STATE_ROTATION_LOCK_LOCKED
                        : DEVICE_STATE_ROTATION_LOCK_UNLOCKED);
    }

    @Nullable
    @Override
    public Integer getRotationLockSetting(int deviceState) {
        if (mDeviceStateAutoRotateSetting == null) return null;
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        return mDeviceStateAutoRotateSetting.get(devicePosture)
                ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                : DEVICE_STATE_ROTATION_LOCK_LOCKED;
    }

    @Nullable
    @Override
    public DeviceStateAutoRotateSetting getRotationLockSetting() {
        if (mDeviceStateAutoRotateSetting == null) return null;
        return mDeviceStateAutoRotateSetting.clone();
    }

    @Nullable
    @Override
    public Boolean isRotationLocked(int deviceState) {
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        return !mDeviceStateAutoRotateSetting.get(devicePosture);
    }

    @Nullable
    @Override
    public Boolean isRotationLockedForAllStates() {
        return null;
    }

    @NonNull
    @Override
    public List<SettableDeviceState> getSettableDeviceStates() {
        return List.of();
    }

    @NonNull
    @Override
    public DeviceStateAutoRotateSetting getDefaultRotationLockSetting() {
        return mDefaultDeviceStateAutoRotateSetting.clone();
    }

    @Override
    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
    }

    /** Notifies all registered listeners that the settings have changed. */
    public void notifyListeners() {
        for (DeviceStateAutoRotateSettingListener listener : mListeners) {
            listener.onSettingsChanged();
        }
    }

    /** Sets the current device state auto-rotate settings. */
    public void setDeviceStateAutoRotateSetting(
            @Nullable TestDeviceStateAutoRotateSetting deviceStateAutoRotateSetting) {
        mDeviceStateAutoRotateSetting = deviceStateAutoRotateSetting;
    }

    /** Sets the default device state auto-rotate settings. */
    public void setDefaultDeviceStateAutoRotateSetting(
            @Nullable TestDeviceStateAutoRotateSetting defaultDeviceStateAutoRotateSetting
    ) {
        mDefaultDeviceStateAutoRotateSetting = defaultDeviceStateAutoRotateSetting;
    }

    /** A test implementation of {@link DeviceStateAutoRotateSetting}. */
    public class TestDeviceStateAutoRotateSetting implements
            DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSetting {
        private final SparseIntArray mDeviceStateAutoRotateSettingInner;

        private TestDeviceStateAutoRotateSetting() {
            mDeviceStateAutoRotateSettingInner = new SparseIntArray();
        }

        public TestDeviceStateAutoRotateSetting(SparseIntArray intArray) {
            mDeviceStateAutoRotateSettingInner = intArray;
        }

        @Override
        public void set(int devicePosture, int autoRotate) {
            mDeviceStateAutoRotateSettingInner.put(devicePosture, autoRotate);
        }

        @Override
        public boolean get(int devicePosture) {
            int index = mDeviceStateAutoRotateSettingInner.indexOfKey(devicePosture);
            if (index < -1) {
                throw new IllegalArgumentException("Invalid device posture=" + devicePosture);
            }
            return mDeviceStateAutoRotateSettingInner.valueAt(index)
                    == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
        }

        @Override
        public void write() {
            mDeviceStateAutoRotateSetting = new TestDeviceStateAutoRotateSetting(
                    mDeviceStateAutoRotateSettingInner);
            notifyListeners();
        }

        private SparseIntArray getDeviceStateAutoRotateSettingMap() {
            return mDeviceStateAutoRotateSettingInner.clone();
        }

        @NonNull
        @Override
        public DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSetting clone() {
            return new TestDeviceStateAutoRotateSetting(mDeviceStateAutoRotateSettingInner.clone());
        }

        @Override
        public boolean equals(Object deviceStateAutoRotateSetting) {
            if (!(deviceStateAutoRotateSetting instanceof TestDeviceStateAutoRotateSetting)) {
                return false;
            }
            return equals(mDeviceStateAutoRotateSettingInner,
                    ((TestDeviceStateAutoRotateSetting) deviceStateAutoRotateSetting)
                            .getDeviceStateAutoRotateSettingMap());
        }

        @Override
        public String toString() {
            return mDeviceStateAutoRotateSettingInner.toString();
        }

        @Override
        public int hashCode() {
            int hashcode = 0;

            for (int i = 0; i < mDeviceStateAutoRotateSettingInner.size(); i++) {
                int key = mDeviceStateAutoRotateSettingInner.keyAt(i);
                int value = mDeviceStateAutoRotateSettingInner.valueAt(i);

                hashcode = 31 * hashcode + Objects.hash(key, value);
            }
            return hashcode;
        }

        private static boolean equals(SparseIntArray a, SparseIntArray b) {
            if (a == b) return true;
            if (a == null || b == null || a.size() != b.size()) return false;

            for (int i = 0; i < a.size(); i++) {
                if (b.keyAt(i) != a.keyAt(i) || b.valueAt(i) != a.valueAt(i)) {
                    return false;
                }
            }
            return true;
        }
    }
}
