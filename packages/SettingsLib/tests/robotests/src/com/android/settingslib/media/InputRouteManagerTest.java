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

import static com.android.settingslib.media.InputRouteManager.INPUT_ATTRIBUTES;
import static com.android.settingslib.media.InputRouteManager.PRESETS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;

import androidx.annotation.Nullable;

import com.android.settingslib.testutils.shadow.ShadowRouter2Manager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowRouter2Manager.class})
public class InputRouteManagerTest {
    private static final int BUILTIN_MIC_ID = 1;
    private static final int INPUT_WIRED_HEADSET_ID = 2;
    private static final int INPUT_USB_DEVICE_ID = 3;
    private static final int INPUT_USB_HEADSET_ID = 4;
    private static final int INPUT_USB_ACCESSORY_ID = 5;
    private static final int HDMI_ID = 6;
    private static final int INPUT_USB_DEVICE_2_ID = 7;
    private static final int MAX_VOLUME = 1;
    private static final int CURRENT_VOLUME = 0;
    private static final boolean VOLUME_FIXED_TRUE = true;
    private static final String PRODUCT_NAME_BUILTIN_MIC = "Built-in Mic";
    private static final String PRODUCT_NAME_WIRED_HEADSET = "My Wired Headset";
    private static final String PRODUCT_NAME_USB_HEADSET = "My USB Headset";
    private static final String PRODUCT_NAME_USB_DEVICE = "My USB Device";
    private static final String PRODUCT_NAME_USB_DEVICE_1 = "USB Device 1";
    private static final String PRODUCT_NAME_USB_DEVICE_2 = "USB Device 2";
    private static final String PRODUCT_NAME_USB_ACCESSORY = "My USB Accessory";
    private static final String PRODUCT_NAME_HDMI_DEVICE = "HDMI device";

    @Mock InfoMediaManager mInfoMediaManager;
    @Mock AudioManager mAudioManager;
    private final Context mContext = spy(RuntimeEnvironment.application);
    private InputRouteManager mInputRouteManager;

    private AudioDeviceInfo mockBuiltinMicInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_MIC);
        when(info.getId()).thenReturn(BUILTIN_MIC_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_BUILTIN_MIC);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockWiredHeadsetInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET);
        when(info.getId()).thenReturn(INPUT_WIRED_HEADSET_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_WIRED_HEADSET);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockUsbDeviceInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_DEVICE);
        when(info.getId()).thenReturn(INPUT_USB_DEVICE_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_USB_DEVICE);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockUsbDeviceInfoWithAddress(
            int id, String address, String productName) {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_DEVICE);
        when(info.getId()).thenReturn(id);
        when(info.getAddress()).thenReturn(address);
        when(info.getProductName()).thenReturn(productName);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockUsbHeadsetInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_HEADSET);
        when(info.getId()).thenReturn(INPUT_USB_HEADSET_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_USB_HEADSET);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockUsbAccessoryInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_ACCESSORY);
        when(info.getId()).thenReturn(INPUT_USB_ACCESSORY_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_USB_ACCESSORY);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockHdmiInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_HDMI);
        when(info.getId()).thenReturn(HDMI_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_HDMI_DEVICE);
        when(info.isSource()).thenReturn(true);
        when(info.isSink()).thenReturn(false);
        return info;
    }

    private AudioDeviceInfo mockUsbHeadsetOutputInfo() {
        final AudioDeviceInfo info = mock(AudioDeviceInfo.class);
        when(info.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_HEADSET);
        when(info.getId()).thenReturn(INPUT_USB_HEADSET_ID);
        when(info.getAddress()).thenReturn("");
        when(info.getProductName()).thenReturn(PRODUCT_NAME_USB_HEADSET);
        when(info.isSource()).thenReturn(false);
        when(info.isSink()).thenReturn(true);
        return info;
    }

    private AudioDeviceAttributes getBuiltinMicDeviceAttributes() {
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_INPUT,
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                /* address= */ "");
    }

    private AudioDeviceAttributes getWiredHeadsetDeviceAttributes() {
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_INPUT,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                /* address= */ "");
    }

    private AudioDeviceAttributes getUsbHeadsetDeviceAttributes() {
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_INPUT,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                /* address= */ "");
    }

    private AudioDeviceAttributes getUsbDeviceAttributesWithAddress(String address) {
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_INPUT, AudioDeviceInfo.TYPE_USB_DEVICE, address);
    }

    private AudioDeviceAttributes getHdmiDeviceAttributes() {
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_INPUT, AudioDeviceInfo.TYPE_HDMI, /* address= */ "");
    }

    private void onPreferredDevicesForCapturePresetChanged() {
        final List<AudioDeviceAttributes> audioDeviceAttributesList =
                new ArrayList<AudioDeviceAttributes>();
        mInputRouteManager.onPreferredDevicesForCapturePresetChangedListener(
                MediaRecorder.AudioSource.MIC, audioDeviceAttributesList);
    }

    private void addListOfAudioDevices() {
        AudioDeviceInfo[] devices = {
            mockBuiltinMicInfo(),
            mockWiredHeadsetInfo(),
            mockUsbDeviceInfo(),
            mockUsbHeadsetInfo(),
            mockUsbAccessoryInfo(),
            mockHdmiInfo()
        };
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        assertThat(mInputRouteManager.mInputMediaDevices).isEmpty();

        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);
        onPreferredDevicesForCapturePresetChanged();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mInputRouteManager = new InputRouteManager(mContext, mAudioManager, mInfoMediaManager);
    }

    @Test
    public void onAudioDevicesAdded_shouldUpdateInputMediaDevice() {
        addListOfAudioDevices();

        // The unsupported (hdmi) info should be filtered out.
        // devices.length - 1 = 5
        assertThat(mInputRouteManager.mInputMediaDevices).hasSize(5);
        assertThat(mInputRouteManager.mInputMediaDevices.get(0).getId())
                .isEqualTo(String.valueOf(BUILTIN_MIC_ID));
        assertThat(mInputRouteManager.mInputMediaDevices.get(1).getId())
                .isEqualTo(String.valueOf(INPUT_WIRED_HEADSET_ID));
        assertThat(mInputRouteManager.mInputMediaDevices.get(2).getId())
                .isEqualTo(String.valueOf(INPUT_USB_DEVICE_ID));
        assertThat(mInputRouteManager.mInputMediaDevices.get(3).getId())
                .isEqualTo(String.valueOf(INPUT_USB_HEADSET_ID));
        assertThat(mInputRouteManager.mInputMediaDevices.get(4).getId())
                .isEqualTo(String.valueOf(INPUT_USB_ACCESSORY_ID));
    }

    @Test
    public void onAudioDevicesRemoved_shouldUpdateInputMediaDevice() {
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
                .thenReturn(new AudioDeviceInfo[] {});

        final MediaDevice device = mock(MediaDevice.class);
        mInputRouteManager.mInputMediaDevices.add(device);

        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesRemoved(
                new AudioDeviceInfo[] {mockWiredHeadsetInfo()});
        onPreferredDevicesForCapturePresetChanged();

        assertThat(mInputRouteManager.mInputMediaDevices).isEmpty();
    }

    @Test
    public void getSelectedInputDevice_returnOneFromAudioManager() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo(), mockBuiltinMicInfo()};
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        // Mock audioManager.getDevicesForAttributes returns exactly one audioDeviceAttributes.
        when(mAudioManager.getDevicesForAttributes(INPUT_ATTRIBUTES))
                .thenReturn(Collections.singletonList(getWiredHeadsetDeviceAttributes()));

        mInputRouteManager = new InputRouteManager(mContext, mAudioManager, mInfoMediaManager);
        onPreferredDevicesForCapturePresetChanged();

        // The selected input device has the same type as the one returned from AudioManager.
        assertThat(getSelectedInputDevice().getAudioDeviceInfoType())
                .isEqualTo(AudioDeviceInfo.TYPE_WIRED_HEADSET);
    }

    @Test
    public void getSelectedInputDevice_returnMoreThanOneFromAudioManager() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo(), mockBuiltinMicInfo()};
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        // Mock audioManager.getDevicesForAttributes returns more than one audioDeviceAttributes.
        List<AudioDeviceAttributes> attributesOfSelectedInputDevices = new ArrayList<>();
        attributesOfSelectedInputDevices.add(getWiredHeadsetDeviceAttributes());
        attributesOfSelectedInputDevices.add(getBuiltinMicDeviceAttributes());
        when(mAudioManager.getDevicesForAttributes(INPUT_ATTRIBUTES))
                .thenReturn(attributesOfSelectedInputDevices);

        mInputRouteManager = new InputRouteManager(mContext, mAudioManager, mInfoMediaManager);
        onPreferredDevicesForCapturePresetChanged();

        // The selected input device has the same type as the first one returned from AudioManager.
        assertThat(getSelectedInputDevice().getAudioDeviceInfoType())
                .isEqualTo(AudioDeviceInfo.TYPE_WIRED_HEADSET);
    }

    @Test
    public void getSelectedInputDevice_returnEmptyFromAudioManager() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo(), mockBuiltinMicInfo()};
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        // Mock audioManager.getDevicesForAttributes returns empty list of audioDeviceAttributes.
        List<AudioDeviceAttributes> emptyAttributesOfSelectedInputDevices = new ArrayList<>();
        when(mAudioManager.getDevicesForAttributes(INPUT_ATTRIBUTES))
                .thenReturn(emptyAttributesOfSelectedInputDevices);

        onPreferredDevicesForCapturePresetChanged();

        // The selected input device has default type AudioDeviceInfo.TYPE_BUILTIN_MIC.
        assertThat(getSelectedInputDevice().getAudioDeviceInfoType())
                .isEqualTo(AudioDeviceInfo.TYPE_BUILTIN_MIC);
    }

    @Test
    public void selectDevice() {
        final MediaDevice builtinMicDevice =
                InputMediaDevice.create(
                        mContext,
                        String.valueOf(BUILTIN_MIC_ID),
                        "",
                        AudioDeviceInfo.TYPE_BUILTIN_MIC,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ false,
                        PRODUCT_NAME_BUILTIN_MIC);
        mInputRouteManager.mInputMediaDevices.add(builtinMicDevice);
        mInputRouteManager.selectDevice(builtinMicDevice);

        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, atLeastOnce())
                    .setPreferredDeviceForCapturePreset(preset, getBuiltinMicDeviceAttributes());
        }
    }

    @Test
    public void selectDevice_withAddress_updatesSelectionCorrectly() {
        // 1. Setup: Create devices with same type but different addresses.
        final AudioDeviceInfo builtinMicInfo = mockBuiltinMicInfo();
        final AudioDeviceInfo usbDevice1Info =
                mockUsbDeviceInfoWithAddress(
                        INPUT_USB_DEVICE_ID, "address1", PRODUCT_NAME_USB_DEVICE_1);
        final AudioDeviceInfo usbDevice2Info =
                mockUsbDeviceInfoWithAddress(
                        INPUT_USB_DEVICE_2_ID, "address2", PRODUCT_NAME_USB_DEVICE_2);

        AudioDeviceInfo[] devices = {builtinMicInfo, usbDevice1Info, usbDevice2Info};
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        // Initial state: Built-in mic is selected by default.
        when(mAudioManager.getDevicesForAttributes(INPUT_ATTRIBUTES))
                .thenReturn(Collections.singletonList(getBuiltinMicDeviceAttributes()));
        mInputRouteManager = new InputRouteManager(mContext, mAudioManager, mInfoMediaManager);
        onPreferredDevicesForCapturePresetChanged();

        assertThat(getSelectedInputDevice().getAudioDeviceInfoType())
                .isEqualTo(AudioDeviceInfo.TYPE_BUILTIN_MIC);

        // 2. Action: Select the first USB device.
        MediaDevice usbDevice1 = getInputDeviceById(INPUT_USB_DEVICE_ID);
        mInputRouteManager.selectDevice(usbDevice1);
        onPreferredDevicesForCapturePresetChanged(); // This triggers dispatchInputDeviceListUpdate.

        // 3. Assertions.
        // Verify the selected device in the list is correct.
        InputMediaDevice selectedDevice = getSelectedInputDevice();
        assertThat(selectedDevice.getId()).isEqualTo(String.valueOf(INPUT_USB_DEVICE_ID));

        // Verify other devices are not selected.
        assertThat(getInputDeviceById(INPUT_USB_DEVICE_2_ID).isSelected())
                .isFalse();
        assertThat(getInputDeviceById(BUILTIN_MIC_ID).isSelected())
                .isFalse();
    }

    @Test
    public void onInitiation_shouldApplyDefaultSelectedDeviceToAllPresets() {
        verify(mAudioManager, atLeastOnce()).getDevicesForAttributes(INPUT_ATTRIBUTES);
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, atLeastOnce())
                    .setPreferredDeviceForCapturePreset(preset, getBuiltinMicDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesAdded_shouldActivateAddedDevice() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo()};
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);

        // The only added wired headset will be activated.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, atLeast(1))
                    .setPreferredDeviceForCapturePreset(preset, getWiredHeadsetDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesAdded_shouldActivateLastAddedDevice() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo(), mockUsbHeadsetInfo()};
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);

        // When adding multiple valid input devices, the last added device (usb headset in this
        // case) will be activated.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, never())
                    .setPreferredDeviceForCapturePreset(preset, getWiredHeadsetDeviceAttributes());
            verify(mAudioManager, atLeast(1))
                    .setPreferredDeviceForCapturePreset(preset, getUsbHeadsetDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesAdded_doNotActivateInvalidAddedDevice() {
        AudioDeviceInfo[] devices = {mockHdmiInfo()};
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);

        // Do not activate since HDMI is not a valid input device.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, never())
                    .setPreferredDeviceForCapturePreset(preset, getHdmiDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesAdded_doNotActivateOnOutputDeviceAdded() {
        AudioDeviceInfo[] devices = {mockUsbHeadsetOutputInfo()};
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);

        // Do not activate input since the added device is output.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, never())
                    .setPreferredDeviceForCapturePreset(preset, getUsbHeadsetDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesAdded_doNotActivatePreexistingDevice() {
        final AudioDeviceInfo info = mockWiredHeadsetInfo();
        InputMediaDevice device = createInputMediaDeviceFromDeviceInfo(info);
        mInputRouteManager.mInputMediaDevices.add(device);

        // Trigger onAudioDevicesAdded with a device that already exists in the device list.
        AudioDeviceInfo[] devices = {info};
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);

        // The device should not be activated.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, never())
                    .setPreferredDeviceForCapturePreset(preset, getWiredHeadsetDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesRemoved_shouldApplyDefaultSelectedDeviceToAllPresets() {
        AudioDeviceInfo[] devices = {mockWiredHeadsetInfo()};
        MediaDevice inputWiredHeadset = createInputMediaDeviceFromDeviceInfo(devices[0]);

        addListOfAudioDevices();
        mInputRouteManager.selectDevice(inputWiredHeadset);
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesRemoved(devices);

        // Called three times, one after initiation, one at devices added
        // and the other after onAudioDevicesRemoved call.
        verify(mAudioManager, times(3)).getDevicesForAttributes(INPUT_ATTRIBUTES);
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, times(3))
                    .setPreferredDeviceForCapturePreset(preset, getBuiltinMicDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesRemoved_doNotApplyDefaultSelectedTypeUnselectedDevRemoved() {
        final MediaDevice usbAccessory =
                InputMediaDevice.create(
                        mContext,
                        String.valueOf(INPUT_USB_ACCESSORY_ID),
                        "",
                        AudioDeviceInfo.TYPE_USB_ACCESSORY,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ false,
                        PRODUCT_NAME_USB_ACCESSORY);
        AudioDeviceInfo[] devices = {mockUsbHeadsetInfo()};

        addListOfAudioDevices();
        mInputRouteManager.selectDevice(usbAccessory);
        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesRemoved(devices);

        // Called two times, one after init, one at devices added
        verify(mAudioManager, atLeast(2)).getDevicesForAttributes(INPUT_ATTRIBUTES);
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, times(2))
                    .setPreferredDeviceForCapturePreset(preset, getBuiltinMicDeviceAttributes());
        }
    }

    @Test
    public void onAudioDevicesRemoved_doNotApplyDefaultSelectedTypeAtOutputRemoval() {
        AudioDeviceInfo[] devices = {mockUsbHeadsetOutputInfo()};

        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesRemoved(devices);

        // Called just once after init.
        for (@MediaRecorder.Source int preset : PRESETS) {
            verify(mAudioManager, times(1))
                    .setPreferredDeviceForCapturePreset(preset, getBuiltinMicDeviceAttributes());
        }
    }

    @Test
    public void getMaxInputGain_returnMaxInputGain() {
        assertThat(mInputRouteManager.getMaxInputGain()).isEqualTo(100);
    }

    @Test
    public void getCurrentInputGain_returnCurrentInputGain() {
        assertThat(mInputRouteManager.getCurrentInputGain()).isEqualTo(100);
    }

    @Test
    public void isInputGainFixed() {
        assertThat(mInputRouteManager.isInputGainFixed()).isTrue();
    }

    @Test
    public void onAudioDevicesAdded_shouldSetProductNameCorrectly() {
        final AudioDeviceInfo info1 = mockWiredHeadsetInfo();
        String firstProductName = "My first headset";
        when(info1.getProductName()).thenReturn(firstProductName);
        InputMediaDevice inputMediaDevice1 = createInputMediaDeviceFromDeviceInfo(info1);

        final AudioDeviceInfo info2 = mockWiredHeadsetInfo();
        String secondProductName = "My second headset";
        when(info2.getProductName()).thenReturn(secondProductName);
        InputMediaDevice inputMediaDevice2 = createInputMediaDeviceFromDeviceInfo(info2);

        final AudioDeviceInfo infoWithNullProductName = mockWiredHeadsetInfo();
        when(infoWithNullProductName.getProductName()).thenReturn(null);
        InputMediaDevice inputMediaDevice3 =
                createInputMediaDeviceFromDeviceInfo(infoWithNullProductName);

        final AudioDeviceInfo infoWithBlankProductName = mockWiredHeadsetInfo();
        when(infoWithBlankProductName.getProductName()).thenReturn("");
        InputMediaDevice inputMediaDevice4 =
                createInputMediaDeviceFromDeviceInfo(infoWithBlankProductName);

        AudioDeviceInfo[] devices = {
            info1, info2, infoWithNullProductName, infoWithBlankProductName
        };
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(devices);

        assertThat(mInputRouteManager.mInputMediaDevices).isEmpty();

        mInputRouteManager.mAudioDeviceCallback.onAudioDevicesAdded(devices);
        onPreferredDevicesForCapturePresetChanged();

        assertThat(mInputRouteManager.mInputMediaDevices)
                .containsExactly(
                        inputMediaDevice1, inputMediaDevice2, inputMediaDevice3, inputMediaDevice4)
                .inOrder();
    }

    private InputMediaDevice createInputMediaDeviceFromDeviceInfo(AudioDeviceInfo info) {
        return InputMediaDevice.create(
                mContext,
                String.valueOf(info.getId()),
                info.getAddress(),
                info.getType(),
                MAX_VOLUME,
                CURRENT_VOLUME,
                VOLUME_FIXED_TRUE,
                /* isSelected= */ false,
                info.getProductName() == null ? null : info.getProductName().toString());
    }

    @Nullable
    private InputMediaDevice getSelectedInputDevice() {
        return (InputMediaDevice) mInputRouteManager.mInputMediaDevices.stream().filter(
                MediaDevice::isSelected).findFirst().orElse(null);
    }

    @Nullable
    private InputMediaDevice getInputDeviceById(int deviceId) {
        return (InputMediaDevice) mInputRouteManager.mInputMediaDevices.stream()
                        .filter(d -> d.getId().equals(String.valueOf(deviceId)))
                        .findFirst()
                        .orElse(null);
    }
}
