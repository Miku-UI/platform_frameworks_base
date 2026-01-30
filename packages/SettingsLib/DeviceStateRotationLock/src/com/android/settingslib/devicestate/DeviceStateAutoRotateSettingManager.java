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

package com.android.settingslib.devicestate;

import android.annotation.Discouraged;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.provider.Settings.Secure.DeviceStateRotationLockKey;
import android.provider.Settings.Secure.DeviceStateRotationLockSetting;
import android.util.Dumpable;

import java.util.List;

/**
 * Interface for managing {@link DEVICE_STATE_ROTATION_LOCK} setting.
 * <p>
 * It provides methods to register/unregister listeners for setting changes, update the setting for
 * specific device states, retrieve the setting value, and check if rotation is locked for specific
 * or all device states.
 */
public interface DeviceStateAutoRotateSettingManager extends Dumpable {
    // TODO: b/397928958 - Rename all terms from rotationLock to autoRotate in all apis.

    /** Listener for changes in device-state based auto rotate setting. */
    interface DeviceStateAutoRotateSettingListener {
        /** Called whenever the setting has changed. */
        void onSettingsChanged();
    }

    /** Register listener for changes to {@link DEVICE_STATE_ROTATION_LOCK} setting. */
    void registerListener(@NonNull DeviceStateAutoRotateSettingListener settingListener);

    /** Unregister listener for changes to {@link DEVICE_STATE_ROTATION_LOCK} setting. */
    void unregisterListener(@NonNull DeviceStateAutoRotateSettingListener settingListener);

    /**
     * Write {@code deviceState}'s setting value as {@code rotationLock}, for
     * {@link DEVICE_STATE_ROTATION_LOCK} setting.
     */
    void updateSetting(int deviceState, boolean rotationLock);

    /**
     * Get {@link DEVICE_STATE_ROTATION_LOCK} setting value for {@code deviceState}.
     * Note that the returned setting values in map are "resolved". This means that for device
     * states where the auto-rotate setting is not user-settable, the value returned will be the
     * same as the value configured for its designated fallback posture.
     * Returns null if string value of {@link DEVICE_STATE_ROTATION_LOCK} is corrupted.
     * <p>
     * If the value is null, system_server will shortly reset the value of
     * {@link DEVICE_STATE_ROTATION_LOCK}. Clients can either subscribe to setting changes or query
     * this API again after a brief delay.
     */
    @Nullable
    Integer getRotationLockSetting(int deviceState);

    /**
     * Get {@link DEVICE_STATE_ROTATION_LOCK} setting value in form of
     * {@link DeviceStateAutoRotateSetting}. Returns null if string value of
     * {@link DEVICE_STATE_ROTATION_LOCK} is corrupted.
     * <p>
     * If the value is null, system_server will shortly reset the value of
     * {@link DEVICE_STATE_ROTATION_LOCK}. Clients can either subscribe to setting changes or query
     * this API again after a brief delay.
     */
    @Nullable
    DeviceStateAutoRotateSetting getRotationLockSetting();

    /**
     * Returns true if auto-rotate setting is OFF for {@code deviceState}. Returns null if string
     * value of {@link DEVICE_STATE_ROTATION_LOCK} is corrupted.
     * <p>
     * Note that the returned setting values in map are "resolved". This means that for device
     * states where the auto-rotate setting is not user-settable, the value returned will be the
     * same as the value configured for its designated fallback posture.
     * <p>
     * If the value is null, system_server will shortly reset the value of
     * {@link DEVICE_STATE_ROTATION_LOCK}. Clients can either subscribe to setting changes or query
     * this API again after a brief delay.
     */
    @Nullable
    Boolean isRotationLocked(int deviceState);

    /**
     * Returns true if the auto-rotate setting value for all settable device states is OFF.
     * Returns null if string value of {@link DEVICE_STATE_ROTATION_LOCK} is corrupted.
     * <p>
     * If the value is null, system_server will shortly reset the value of
     * {@link DEVICE_STATE_ROTATION_LOCK}. Clients can either subscribe to setting changes or query
     * this API again after a brief delay.
     */
    @Nullable
    Boolean isRotationLockedForAllStates();

    /** Returns a list of device states and their respective auto rotate setting availability. */
    @NonNull
    List<SettableDeviceState> getSettableDeviceStates();

    /**
     * Returns default value of {@link DEVICE_STATE_ROTATION_LOCK} setting from config, in form of
     * {@link DeviceStateAutoRotateSetting}.
     */
    @NonNull
    DeviceStateAutoRotateSetting getDefaultRotationLockSetting();

    /**
     * Encapsulates device-state auto-rotate setting as a single entity.
     * This interface provides methods to get and set the auto-rotate preference for specific
     * device postures.
     * <p>
     * This object is guaranteed to never hold corrupted or invalid device-state based auto-rotate
     * setting.
     */
    interface DeviceStateAutoRotateSetting extends Cloneable {
        /**
         * Sets the auto-rotate setting for a specific device posture.
         *
         * @param devicePosture The device posture key.
         * @param autoRotate    The desired auto-rotate setting value.
         * @throws IllegalArgumentException if the device posture or auto-rotate value is invalid.
         * @throws IllegalStateException    if the fallback posture for an ignored posture is also
         *                                  ignored (chained fallback link is not supported).
         */
        void set(@DeviceStateRotationLockKey int devicePosture,
                @DeviceStateRotationLockSetting int autoRotate);

        /**
         * Gets the resolved auto-rotate setting for a specific device posture.
         * <p>
         * If the setting for the given {@code devicePosture} is
         * {@link Settings.Secure#DEVICE_STATE_ROTATION_LOCK_IGNORED},
         * this method returns the setting value of its designated fallback posture.
         * </p>
         *
         * @param devicePosture The device posture key.
         * @return true if auto-rotate is unlocked for the resolved setting, false if locked.
         * @throws IllegalArgumentException if the device posture is invalid.
         * @throws IllegalStateException    if the fallback posture for an ignored posture is also
         *                                  ignored (chained fallback link is not supported).
         */
        boolean get(@DeviceStateRotationLockKey int devicePosture);

        /**
         * Write device state auto rotate setting stored in this object into persisted setting.
         * <p>
         * This method is exclusively for internal use by the
         * {@link com.android.server.wm.DeviceStateAutoRotateSettingController}.
         * </p>
         * <p>
         * Direct invocation by other clients can bypass crucial validation or business
         * logic, potentially leading to an inconsistent or corrupt settings state.
         * </p>
         * <p>
         * The designated API for updating settings is {@link #updateSetting(int, boolean)}. Please
         * use that method.
         */
        @Discouraged(message = "This method is exclusively for internal use. The designated API for"
                + " updating settings is #updateSetting(int, boolean) in com.android.settingslib."
                + "devicestate.DeviceStateAutoRotateSettingManager. Please use that method.")
        void write();

        /** Returns a deep copy of this {@link DeviceStateAutoRotateSetting} object. */
        @NonNull
        DeviceStateAutoRotateSetting clone();
    }
}
