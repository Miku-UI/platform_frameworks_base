/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.settingslib.media;

import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceInfo.AudioDeviceType;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.text.TextUtils;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Provides functionalities to get/observe input routes, control input routing and volume gain. */
public final class InputRouteManager {

    private static final String TAG = "InputRouteManager";

    @VisibleForTesting
    static final AudioAttributes INPUT_ATTRIBUTES =
            new AudioAttributes.Builder().setCapturePreset(MediaRecorder.AudioSource.MIC).build();

    @VisibleForTesting
    static final int[] PRESETS = {
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.CAMCORDER,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.VOICE_PERFORMANCE
    };

    private final Context mContext;

    private final AudioManager mAudioManager;

    private final InfoMediaManager mInfoMediaManager;

    @VisibleForTesting final List<MediaDevice> mInputMediaDevices = new CopyOnWriteArrayList<>();

    @NonNull
    private AudioDeviceAttributes mSelectedDeviceAttributes;

    private final Collection<InputDeviceCallback> mCallbacks = new CopyOnWriteArrayList<>();
    private final Object mCallbackLock = new Object();

    @VisibleForTesting
    final AudioDeviceCallback mAudioDeviceCallback =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(@NonNull AudioDeviceInfo[] addedDevices) {
                    Slog.v(TAG, "onAudioDevicesAdded");
                    applyDefaultSelectedTypeToAllPresets();

                    // Activate the last hot plugged valid input device, to match the output device
                    // behavior.
                    AudioDeviceAttributes deviceAttributesToActivate = mSelectedDeviceAttributes;
                    for (AudioDeviceInfo info : addedDevices) {
                        Slog.v(TAG,
                                "onAudioDevicesAdded: enumerating"
                                + ": type=" + info.getType()
                                + ", name=" + info.getProductName()
                                + ", isSource=" + info.isSource()
                                + ", isSink=" + info.isSink());

                        if (!info.isSource()) {
                            continue;
                        }

                        @AudioDeviceType int type = info.getType();
                        String addr = info.getAddress();
                        // Since onAudioDevicesAdded is called not only when new device is hot
                        // plugged, but also when the switcher dialog is opened, make sure to check
                        // against existing device list and only activate if the device does not
                        // exist previously.
                        if (InputMediaDevice.isSupportedInputDevice(type)
                                && findDeviceByTypeAndAddress(type, addr) == null) {
                            Slog.v(TAG,
                                    "onAudioDevicesAdded: updated type=" + type + ", addr=" + addr);
                            deviceAttributesToActivate = createInputDeviceAttributes(type, addr);
                        }
                    }

                    // Only activate if we find a different valid input device. e.g. if none of the
                    // addedDevices is supported input device, we don't need to activate anything.
                    if (!mSelectedDeviceAttributes.equals(deviceAttributesToActivate)) {
                        mSelectedDeviceAttributes = deviceAttributesToActivate;
                        setPreferredDeviceForAllPresets(deviceAttributesToActivate);
                    }
                }

                @Override
                public void onAudioDevicesRemoved(@NonNull AudioDeviceInfo[] removedDevices) {
                    for (AudioDeviceInfo info : removedDevices) {
                        Slog.v(TAG,
                                "onAudioDevicesRemoved: enumerating"
                                + ": type=" + info.getType()
                                + ", name=" + info.getProductName()
                                + ", isSource=" + info.isSource()
                                + ", isSink=" + info.isSink());

                        if (!info.isSource()) {
                            continue;
                        }

                        @AudioDeviceType int type = info.getType();
                        String addr = info.getAddress();
                        // Only when the selected input got removed, apply default as fallback.
                        if (InputMediaDevice.isSupportedInputDevice(type)
                                && (mSelectedDeviceAttributes.getType() == type)
                                && (mSelectedDeviceAttributes.getAddress().equals(addr))) {
                            Slog.v(TAG,
                                    "selected input is removed: updated type="
                                    + type + ", addr=" + addr);
                            applyDefaultSelectedTypeToAllPresets();
                            break;
                        }
                    }
                }
            };

    public InputRouteManager(
            @NonNull Context context,
            @NonNull AudioManager audioManager,
            @NonNull InfoMediaManager infoMediaManager) {
        mContext = context;
        mAudioManager = audioManager;
        mInfoMediaManager = infoMediaManager;
        Handler handler = new Handler(context.getMainLooper());

        mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, handler);

        mAudioManager.addOnPreferredDevicesForCapturePresetChangedListener(
                new HandlerExecutor(handler),
                this::onPreferredDevicesForCapturePresetChangedListener);

        applyDefaultSelectedTypeToAllPresets();
    }

    @VisibleForTesting
    void onPreferredDevicesForCapturePresetChangedListener(
            @MediaRecorder.SystemSource int capturePreset,
            @NonNull List<AudioDeviceAttributes> devices) {
        if (capturePreset == MediaRecorder.AudioSource.MIC) {
            dispatchInputDeviceListUpdate();
        }
    }

    public void registerCallback(@NonNull InputDeviceCallback callback) {
        synchronized (mCallbackLock) {
            if (!mCallbacks.contains(callback)) {
                mCallbacks.add(callback);
                dispatchInputDeviceListUpdate();
            }
        }
    }

    public void unregisterCallback(@NonNull InputDeviceCallback callback) {
        synchronized (mCallbackLock) {
            mCallbacks.remove(callback);
        }
    }

    @Nullable
    private MediaDevice findDeviceByTypeAndAddress(@AudioDeviceType int type, String addr) {
        for (MediaDevice device : mInputMediaDevices) {
            if (((InputMediaDevice) device).getAudioDeviceInfoType() == type
                    && (TextUtils.isEmpty(addr)
                            || ((InputMediaDevice) device).getAddress().equals(addr))) {
                return device;
            }
        }
        return null;
    }

    private void applyDefaultSelectedTypeToAllPresets() {
        AudioDeviceAttributes deviceAttributes = retrieveDefaultSelectedInputDeviceAttrs();

        mSelectedDeviceAttributes = deviceAttributes;
        setPreferredDeviceForAllPresets(deviceAttributes);
    }

    private AudioDeviceAttributes createInputDeviceAttributes(@AudioDeviceType int type,
            String address) {
        return new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_INPUT, type, address);
    }

    private AudioDeviceAttributes retrieveDefaultSelectedInputDeviceAttrs() {
        List<AudioDeviceAttributes> attributesOfSelectedInputDevices =
                mAudioManager.getDevicesForAttributes(INPUT_ATTRIBUTES);
        @AudioDeviceType int selectedType;
        String selectedAddr;
        if (attributesOfSelectedInputDevices.isEmpty()) {
            Slog.e(TAG, "Unexpected empty list of input devices. Using built-in mic.");
            selectedType = AudioDeviceInfo.TYPE_BUILTIN_MIC;
            selectedAddr = "";
        } else {
            if (attributesOfSelectedInputDevices.size() > 1) {
                Slog.w(
                        TAG,
                        "AudioManager.getDevicesForAttributes returned more than one element."
                                + " Using the first one.");
            }
            selectedType = attributesOfSelectedInputDevices.get(0).getType();
            selectedAddr = attributesOfSelectedInputDevices.get(0).getAddress();
        }
        return createInputDeviceAttributes(selectedType, selectedAddr);
    }

    private void dispatchInputDeviceListUpdate() {
        // Get all input devices.
        AudioDeviceInfo[] audioDeviceInfos =
                mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        mInputMediaDevices.clear();
        for (AudioDeviceInfo info : audioDeviceInfos) {
            boolean isSelected = isSelectedDevice(info.getType(), info.getAddress());
            MediaDevice mediaDevice =
                    InputMediaDevice.create(
                            mContext,
                            String.valueOf(info.getId()),
                            info.getAddress(),
                            info.getType(),
                            getMaxInputGain(),
                            getCurrentInputGain(),
                            isInputGainFixed(),
                            isSelected,
                            getProductNameFromAudioDeviceInfo(info));
            if (mediaDevice != null) {
                if (isSelected) {
                    mInfoMediaManager.setDeviceState(mediaDevice, STATE_SELECTED);
                }
                mInputMediaDevices.add(mediaDevice);
            }
        }

        final List<MediaDevice> inputMediaDevices = new ArrayList<>(mInputMediaDevices);
        synchronized (mCallbackLock) {
            for (InputDeviceCallback callback : mCallbacks) {
                callback.onInputDeviceListUpdated(inputMediaDevices);
            }
        }
    }

    private boolean isSelectedDevice(@AudioDeviceType int type, @NonNull String address) {
        // If the selected device's address is empty, there will be only one device of that type,
        // therefore matching by type is sufficient.
        return type == mSelectedDeviceAttributes.getType()
                && (TextUtils.isEmpty(mSelectedDeviceAttributes.getAddress())
                || address.equals(mSelectedDeviceAttributes.getAddress()));
    }

    /**
     * Gets the product name for the given {@link AudioDeviceInfo}.
     *
     * @return The product name for the given {@link AudioDeviceInfo}, or null if a suitable name
     *     cannot be found.
     */
    @Nullable
    private String getProductNameFromAudioDeviceInfo(AudioDeviceInfo deviceInfo) {
        CharSequence productName = deviceInfo.getProductName();
        if (productName == null) {
            return null;
        }
        String productNameString = productName.toString();
        if (productNameString.isBlank()) {
            return null;
        }
        return productNameString;
    }

    public void selectDevice(@NonNull MediaDevice device) {
        if (!(device instanceof InputMediaDevice inputMediaDevice)) {
            Slog.w(TAG, "This device is not an InputMediaDevice: " + device.getName());
            return;
        }

        if (isSelectedDevice(inputMediaDevice.getAudioDeviceInfoType(),
                inputMediaDevice.getAddress())) {
            Slog.w(TAG, "This device is already selected: " + device.getName());
            return;
        }

        // Handle edge case where the targeting device is not available, e.g. disconnected.
        if (!mInputMediaDevices.contains(device)) {
            Slog.w(TAG, "This device is not available: " + device.getName());
            return;
        }

        // Update mSelectedInputDeviceType/Addr directly based on user action.

        mSelectedDeviceAttributes =
                createInputDeviceAttributes(inputMediaDevice.getAudioDeviceInfoType(),
                        inputMediaDevice.getAddress());
        Slog.v(TAG, "User selected device: type=" + mSelectedDeviceAttributes.getType()
                + ", addr=" + mSelectedDeviceAttributes.getAddress());
        try {
            setPreferredDeviceForAllPresets(mSelectedDeviceAttributes);
        } catch (IllegalArgumentException e) {
            Slog.e(
                    TAG,
                    "Illegal argument exception while setPreferredDeviceForAllPreset: "
                            + device.getName(),
                    e);
        }
    }

    private void setPreferredDeviceForAllPresets(@NonNull AudioDeviceAttributes deviceAttributes) {
        // The input routing via system setting takes effect on all capture presets.
        for (@MediaRecorder.Source int preset : PRESETS) {
            mAudioManager.setPreferredDeviceForCapturePreset(preset, deviceAttributes);
        }
    }

    public int getMaxInputGain() {
        // TODO (b/357123335): use real input gain implementation.
        // Using 100 for now since it matches the maximum input gain index in classic ChromeOS.
        return 100;
    }

    public int getCurrentInputGain() {
        // TODO (b/357123335): use real input gain implementation.
        // Show a fixed full gain in UI before it really works per UX requirement.
        return 100;
    }

    public boolean isInputGainFixed() {
        // TODO (b/357123335): use real input gain implementation.
        return true;
    }

    /** Callback for listening to input device changes. */
    public interface InputDeviceCallback {
        void onInputDeviceListUpdated(@NonNull List<MediaDevice> devices);
    }
}
