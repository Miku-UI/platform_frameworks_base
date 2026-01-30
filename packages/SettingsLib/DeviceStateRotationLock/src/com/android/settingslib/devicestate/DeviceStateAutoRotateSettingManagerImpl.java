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

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_FOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_HALF_FOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNFOLDED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
import static android.provider.Settings.Secure.DeviceStateRotationLockKey;
import static android.provider.Settings.Secure.DeviceStateRotationLockSetting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.view.RotationPolicy;
import com.android.window.flags.Flags;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of {@link DeviceStateAutoRotateSettingManager}. This implementation is a part of
 * refactoring, it should be used when
 * {@link Flags#FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR}
 * is enabled.
 */
public class DeviceStateAutoRotateSettingManagerImpl implements
        DeviceStateAutoRotateSettingManager {
    // TODO: b/397928958 rename the fields and apis from rotationLock to autoRotate.

    private static final String TAG = "DSAutoRotateMngr";
    private static final String SEPARATOR_REGEX = ":";
    private static final int VISITED = -1;

    private final List<DeviceStateAutoRotateSettingListener> mSettingListeners = new ArrayList<>();
    private final SparseIntArray mFallbackPostureMap = new SparseIntArray();
    private final SparseIntArray mDefaultDeviceStateAutoRotateSetting = new SparseIntArray();
    private final List<SettableDeviceState> mSettableDeviceState = new ArrayList<>();
    private final SecureSettings mSecureSettings;
    private final Handler mMainHandler;
    private final PostureDeviceStateConverter mPostureDeviceStateConverter;

    public DeviceStateAutoRotateSettingManagerImpl(
            Context context,
            Executor backgroundExecutor,
            SecureSettings secureSettings,
            Handler mainHandler,
            PostureDeviceStateConverter postureDeviceStateConverter
    ) {
        mSecureSettings = secureSettings;
        mMainHandler = mainHandler;
        mPostureDeviceStateConverter = postureDeviceStateConverter;

        loadAutoRotateDeviceStates(context);
        final ContentObserver contentObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                notifyListeners();
            }
        };
        backgroundExecutor.execute(() ->
                mSecureSettings.registerContentObserver(
                        DEVICE_STATE_ROTATION_LOCK,
                        /* notifyForDescendants= */false,
                        contentObserver,
                        UserHandle.USER_CURRENT
                )
        );
    }

    @Override
    public void registerListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        mSettingListeners.add(settingListener);
    }

    @Override
    public void unregisterListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        if (!mSettingListeners.remove(settingListener)) {
            Log.w(TAG, "Attempting to unregister a listener hadn't been registered");
        }
    }

    @Override
    @DeviceStateRotationLockSetting
    public Integer getRotationLockSetting(int deviceState) {
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        final DeviceStateAutoRotateSetting deviceStateAutoRotateSetting = getRotationLockSetting();
        if (deviceStateAutoRotateSetting == null) return null;

        return deviceStateAutoRotateSetting.get(devicePosture) ? DEVICE_STATE_ROTATION_LOCK_UNLOCKED
                : DEVICE_STATE_ROTATION_LOCK_LOCKED;
    }

    @Override
    public DeviceStateAutoRotateSetting getRotationLockSetting() {
        final String serializedSetting = mSecureSettings.getStringForUser(
                DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT);

        return deserializeSettingString(serializedSetting);
    }

    @Override
    public Boolean isRotationLocked(int deviceState) {
        final Integer autoRotateValue = getRotationLockSetting(deviceState);
        return autoRotateValue == null ? null
                : autoRotateValue == DEVICE_STATE_ROTATION_LOCK_LOCKED;
    }

    @Override
    public Boolean isRotationLockedForAllStates() {
        final DeviceStateAutoRotateSetting deviceStateAutoRotateSetting = getRotationLockSetting();
        if (deviceStateAutoRotateSetting == null) return null;
        for (int i = 0; i < mDefaultDeviceStateAutoRotateSetting.size(); i++) {
            if (deviceStateAutoRotateSetting.get(mDefaultDeviceStateAutoRotateSetting.keyAt(i))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public List<SettableDeviceState> getSettableDeviceStates() {
        return mSettableDeviceState;
    }

    @Override
    public void updateSetting(int deviceState, boolean rotationLock) {
        RotationPolicy.requestDeviceStateAutoRotateSettingChange(deviceState, !rotationLock);
    }

    @Override
    public void dump(@NonNull PrintWriter writer, String[] args) {
        IndentingPrintWriter indentingWriter = new IndentingPrintWriter(writer, "  ");
        indentingWriter.println("DeviceStateAutoRotateSettingManagerImpl");
        indentingWriter.increaseIndent();
        indentingWriter.println("fallbackPostureMap: " + mFallbackPostureMap);
        indentingWriter.println("settableDeviceState: " + mSettableDeviceState);
        indentingWriter.decreaseIndent();
    }

    @NonNull
    @Override
    public DeviceStateAutoRotateSetting getDefaultRotationLockSetting() {
        return new DeviceStateAutoRotateSettingImpl(mDefaultDeviceStateAutoRotateSetting);
    }

    private void updateSetting(@NonNull SparseIntArray proposedSetting) {
        final String serializedDeviceStateAutoRotateSetting =
                convertIntArrayToSerializedSetting(proposedSetting);
        mSecureSettings.putStringForUser(DEVICE_STATE_ROTATION_LOCK,
                serializedDeviceStateAutoRotateSetting, UserHandle.USER_CURRENT);
    }

    private void notifyListeners() {
        for (DeviceStateAutoRotateSettingListener listener : mSettingListeners) {
            listener.onSettingsChanged();
        }
    }

    /**
     * Loads the {@link R.array#config_perDeviceStateRotationLockDefaults} array and populates
     * the {@link #mFallbackPostureMap}, {@link #mSettableDeviceState}, and
     * {@link #mDefaultDeviceStateAutoRotateSetting} fields.
     */
    private void loadAutoRotateDeviceStates(Context context) {
        final String[] perDeviceStateAutoRotateDefaults =
                context.getResources().getStringArray(
                        R.array.config_perDeviceStateRotationLockDefaults);
        for (String entry : perDeviceStateAutoRotateDefaults) {
            final PostureEntry parsedEntry = parsePostureEntry(entry);

            final int posture = parsedEntry.posture;
            final int autoRotateValue = parsedEntry.autoRotateValue;
            final Integer fallbackPosture = parsedEntry.fallbackPosture;
            final Integer deviceState = mPostureDeviceStateConverter.postureToDeviceState(posture);
            if (!isValidDeviceStateAutoRotateSettingKey(posture)
                    || !isValidDeviceStateAutoRotateSettingValue(autoRotateValue)) {
                throw new IllegalStateException(
                        "Corrupted auto-rotate config. One or more values among " + "devicePosture="
                                + posture + " autoRotateValue=" + autoRotateValue + " are invalid");
            }

            if (deviceState == null) {
                throw new IllegalStateException("No matching device state for posture: " + posture);
            }
            mSettableDeviceState.add(new SettableDeviceState(deviceState,
                    autoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED));

            if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED
                    && fallbackPosture != null) {
                if (!isValidDeviceStateAutoRotateSettingKey(fallbackPosture)) {
                    throw new IllegalStateException(
                            "Corrupted auto-rotate config. Invalid" + " fallbackPosture="
                                    + fallbackPosture + "for devicePosture=" + posture);
                }
                mFallbackPostureMap.put(posture, fallbackPosture);
            } else if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                throw new IllegalStateException(
                        "Auto rotate setting is IGNORED for posture=" + posture
                                + ", but no fallback-posture defined");
            }
            mDefaultDeviceStateAutoRotateSetting.put(posture, autoRotateValue);
        }
    }

    @NonNull
    private static PostureEntry parsePostureEntry(String entry) {
        final String[] values = entry.split(SEPARATOR_REGEX);
        if (values.length < 2 || values.length > 3) { // It should contain 2 or 3 values.
            throw new IllegalStateException("Invalid number of values in entry: " + entry);
        }
        try {
            final int posture = Integer.parseInt(values[0]);
            final int autoRotateValue = Integer.parseInt(values[1]);
            final Integer fallbackPosture = (values.length == 3) ? Integer.parseInt(values[2])
                    : null;

            return new PostureEntry(posture, autoRotateValue, fallbackPosture);

        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid number format in '" + entry + "'" + e.getMessage());
        }
    }


    /**
     * Deserializes the string value from {@link Settings.Secure#DEVICE_STATE_ROTATION_LOCK}
     * into a {@link DeviceStateAutoRotateSetting}.
     * <p>
     * The expected format is a series of key-value pairs separated by {@link #SEPARATOR_REGEX},
     * e.g., "posture1:value1:posture2:value2".
     *
     * @param serializedSetting The string read from settings.
     * @return A {@link DeviceStateAutoRotateSetting}
     * representing the settings, or null if the input string is null, empty, or has an invalid
     * format.
     */
    @Nullable
    private DeviceStateAutoRotateSetting deserializeSettingString(
            String serializedSetting) {
        if (serializedSetting == null || serializedSetting.isEmpty()) return null;
        final String[] deserializedSettings = serializedSetting.split(SEPARATOR_REGEX);
        if (deserializedSettings.length % 2 != 0) {
            Log.e(TAG, "Invalid format in serializedSetting=" + serializedSetting
                    + "\nOdd number of elements in the list");
            return null;
        }

        final DeviceStateAutoRotateSetting deviceStateAutoRotateSetting =
                new DeviceStateAutoRotateSettingImpl(mDefaultDeviceStateAutoRotateSetting);
        final SparseIntArray devicePostureVisitStatus =
                mDefaultDeviceStateAutoRotateSetting.clone();

        for (int i = 0; i < deserializedSettings.length; i += 2) {
            try {
                final int key = Integer.parseInt(deserializedSettings[i]);
                final int value = Integer.parseInt(deserializedSettings[i + 1]);

                try {
                    deviceStateAutoRotateSetting.set(key, value);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, e.getMessage());
                    return null;
                }
                // Mark key visited
                devicePostureVisitStatus.put(key, VISITED);

                if (getValueFromIntArray(key, mDefaultDeviceStateAutoRotateSetting)
                        == DEVICE_STATE_ROTATION_LOCK_IGNORED
                        && value != DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                    Log.e(TAG, "Invalid serializedSetting=" + serializedSetting
                            + "\nThe value for devicePosture=" + key
                            + "should be IGNORED(0) but is " + value);
                    return null;
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid number format in serializedSetting=" + serializedSetting
                        + "\nError parsing pair: " + deserializedSettings[i] + ":"
                        + deserializedSettings[i + 1], e);
                return null;
            }
        }
        // Check if persisted setting was missing any posture. Persisted setting is corrupted if
        // it is missing any device posture.
        for (int i = 0; i < devicePostureVisitStatus.size(); i++) {
            if (devicePostureVisitStatus.valueAt(i) != VISITED) {
                Log.e(TAG, "Invalid serializedSetting=" + serializedSetting
                        + "\n One or more device postures missing including devicePosture="
                        + devicePostureVisitStatus.keyAt(i));
                return null;
            }
        }
        return deviceStateAutoRotateSetting;
    }

    /**
     * Retrieves the value associated with the given key from the SparseIntArray.
     * <p>
     * This method is intended for use cases where the specified {@code key} is strongly expected to
     * exist within the {@code intArray}. If the key is not found, this method throws an
     * {@link IllegalStateException}. This behavior assumes that a missing key in this context
     * signifies a critical inconsistency or an unexpected program state, rather than a common
     * 'not found' scenario.
     * </p>
     */
    private static int getValueFromIntArray(int key, SparseIntArray intArray) {
        final int indexOfKey = intArray.indexOfKey(key);
        if (indexOfKey < 0) {
            throw new IllegalStateException(
                    "Key " + key + " not found in SparseIntArray=" + intArray);
        }
        return intArray.valueAt(indexOfKey);
    }

    private static String convertIntArrayToSerializedSetting(
            SparseIntArray intArray) {
        return IntStream.range(0, intArray.size())
                .mapToObj(i -> intArray.keyAt(i) + SEPARATOR_REGEX + intArray.valueAt(i))
                .collect(Collectors.joining(SEPARATOR_REGEX));
    }

    private static boolean isValidDeviceStateAutoRotateSettingValue(
            @DeviceStateRotationLockSetting int autoRotate) {
        return switch (autoRotate) {
            case DEVICE_STATE_ROTATION_LOCK_LOCKED, DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                 DEVICE_STATE_ROTATION_LOCK_IGNORED -> true;
            default -> false;
        };
    }

    private static boolean isValidDeviceStateAutoRotateSettingKey(
            @DeviceStateRotationLockKey int devicePosture) {
        return switch (devicePosture) {
            case DEVICE_STATE_ROTATION_KEY_FOLDED, DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY,
                 DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_KEY_HALF_FOLDED -> true;
            default -> false;
        };
    }

    @VisibleForTesting
    private class DeviceStateAutoRotateSettingImpl implements DeviceStateAutoRotateSetting {
        @NonNull
        private final SparseIntArray mDeviceStateAutoRotateSetting;

        private DeviceStateAutoRotateSettingImpl(@NonNull SparseIntArray intArray) {
            mDeviceStateAutoRotateSetting = intArray.clone();
        }

        @Override
        public void set(@DeviceStateRotationLockKey int devicePosture,
                @DeviceStateRotationLockSetting int autoRotate) {
            if (autoRotate == DEVICE_STATE_ROTATION_LOCK_IGNORED) return;
            if (!isValidDeviceStateAutoRotateSettingKey(devicePosture)) {
                throw new IllegalArgumentException(
                        "Trying to set auto rotate value of invalid device posture: "
                                + devicePosture);
            }
            if (!isValidDeviceStateAutoRotateSettingValue(autoRotate)) {
                throw new IllegalArgumentException(
                        "Trying to set invalid auto rotate value: " + autoRotate);
            }
            final int currentAutoRotateValue = getValueFromIntArray(devicePosture,
                    mDeviceStateAutoRotateSetting);
            if (currentAutoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                mDeviceStateAutoRotateSetting.put(devicePosture, autoRotate);
                return;
            }
            final int fallbackPosture = mFallbackPostureMap.get(devicePosture);
            final int fallbackAutoRotateValue = getValueFromIntArray(fallbackPosture,
                    mDeviceStateAutoRotateSetting);
            if (fallbackAutoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                throw new IllegalStateException(
                        "Chained fallback map is not supported. Fallback posture for given "
                                + "ignored device posture is ignored.\nDevice posture: "
                                + devicePosture + ", fallback posture: " + fallbackPosture);
            }
            mDeviceStateAutoRotateSetting.put(fallbackPosture, autoRotate);
        }

        @Override
        public boolean get(@DeviceStateRotationLockKey int devicePosture) {
            if (!isValidDeviceStateAutoRotateSettingKey(devicePosture)) {
                throw new IllegalArgumentException(
                        "Trying to get auto rotate value of invalid device posture: "
                                + devicePosture);
            }
            final int autoRotateValue = getValueFromIntArray(devicePosture,
                    mDeviceStateAutoRotateSetting);
            if (autoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                return autoRotateValue == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
            }
            final int fallbackPosture = mFallbackPostureMap.get(devicePosture);
            final int fallbackAutoRotateValue = getValueFromIntArray(fallbackPosture,
                    mDeviceStateAutoRotateSetting);
            if (fallbackAutoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                throw new IllegalStateException(
                        "Chained fallback map is not supported. Fallback posture for given "
                                + "ignored device posture is ignored.\nDevice posture: "
                                + devicePosture + ", fallback posture: " + fallbackPosture);
            }
            return fallbackAutoRotateValue == DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
        }

        @Override
        public void write() {
            updateSetting(mDeviceStateAutoRotateSetting.clone());
        }

        @Override
        @NonNull
        public DeviceStateAutoRotateSetting clone() {
            return new DeviceStateAutoRotateSettingImpl(mDeviceStateAutoRotateSetting.clone());
        }

        @Override
        public boolean equals(Object deviceStateAutoRotateSetting) {
            if (!(deviceStateAutoRotateSetting instanceof DeviceStateAutoRotateSettingImpl)) {
                return false;
            }
            return equals(mDeviceStateAutoRotateSetting,
                    ((DeviceStateAutoRotateSettingImpl) deviceStateAutoRotateSetting)
                            .getDeviceStateAutoRotateSettingMap());
        }

        /** Returns a string representation of the settings for logging purposes. */
        @Override
        @NonNull
        public String toString() {
            return "DeviceStateAutoRotateSetting: " + mDeviceStateAutoRotateSetting;
        }

        /**
         * Overrides the hashCode method to be consistent with the custom equals method.
         * <p>
         * The hash code is derived from the contents of the mDeviceStateAutoRotateSetting
         * SparseIntArray, mirroring the logic of the custom equals method. It iterates through the
         * array, combining the hash codes of each key-value pair.
         */
        @Override
        public int hashCode() {
            int hashcode = 0;

            for (int i = 0; i < mDeviceStateAutoRotateSetting.size(); i++) {
                int key = mDeviceStateAutoRotateSetting.keyAt(i);
                int value = mDeviceStateAutoRotateSetting.valueAt(i);

                hashcode = 31 * hashcode + Objects.hash(key, value);
            }
            return hashcode;
        }

        private SparseIntArray getDeviceStateAutoRotateSettingMap() {
            return mDeviceStateAutoRotateSetting.clone();
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

    private record PostureEntry(int posture, int autoRotateValue, Integer fallbackPosture) {
    }
}
