/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.audio;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_MUTE;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_UNMUTE;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.ShellCommand;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AudioManagerShellCommand extends ShellCommand {
    private static final String TAG = "AudioManagerShellCommand";

    private final AudioService mService;
    private final AudioManager mAudioManager;

    AudioManagerShellCommand(AudioService service) {
        mService = service;
        mAudioManager = service.mContext.getSystemService(AudioManager.class);
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        final PrintWriter pw = getOutPrintWriter();
        switch (cmd) {
            case "set-surround-format-enabled":
                return setSurroundFormatEnabled();
            case "get-is-surround-format-enabled":
                return getIsSurroundFormatEnabled();
            case "set-encoded-surround-mode":
                return setEncodedSurroundMode();
            case "get-encoded-surround-mode":
                return getEncodedSurroundMode();
            case "set-sound-dose-value":
                return setSoundDoseValue();
            case "get-sound-dose-value":
                return getSoundDoseValue();
            case "reset-sound-dose-timeout":
                return resetSoundDoseTimeout();
            case "set-ringer-mode":
                return setRingerMode();
            case "set-volume":
                return setVolume();
            case "get-min-volume":
                return getMinVolume();
            case "get-max-volume":
                return getMaxVolume();
            case "get-stream-volume":
                return getStreamVolume();
            case "set-device-volume":
                return setDeviceVolume();
            case "is-stream-mute":
                return getIsStreamMute();
            case "is-volume-fixed":
                return getIsVolumeFixed();
            case "adj-mute":
                return adjMute();
            case "adj-unmute":
                return adjUnmute();
            case "adj-volume":
                return adjVolume();
            case "set-group-volume":
                return setGroupVolume();
            case "adj-group-volume":
                return adjGroupVolume();
            case "set-hardening":
                return setEnableHardening();
            case "get-preferred-output-device":
                return getPreferredOutputDevice();
            case "set-preferred-output-device":
                return setPreferredOutputDevice();
            case "get-supported-output-devices":
                return getSupportedOutputDevices();
            case "get-current-output-device":
                return getCurrentOutputDevice();
            case "get-connected-output-devices":
                return getConnectedOutputDevices();
        }
        return 0;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Audio manager commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println();
        pw.println("  set-surround-format-enabled SURROUND_FORMAT IS_ENABLED");
        pw.println("    Enables/disabled the SURROUND_FORMAT based on IS_ENABLED");
        pw.println("  get-is-surround-format-enabled SURROUND_FORMAT");
        pw.println("    Returns if the SURROUND_FORMAT is enabled");
        pw.println("  set-encoded-surround-mode SURROUND_SOUND_MODE");
        pw.println("    Sets the encoded surround sound mode to SURROUND_SOUND_MODE");
        pw.println("  get-encoded-surround-mode");
        pw.println("    Returns the encoded surround sound mode");
        pw.println("  set-sound-dose-value");
        pw.println("    Sets the current sound dose value");
        pw.println("  get-sound-dose-value");
        pw.println("    Returns the current sound dose value");
        pw.println("  reset-sound-dose-timeout");
        pw.println("    Resets the sound dose timeout used for momentary exposure");
        pw.println("  set-ringer-mode NORMAL|SILENT|VIBRATE");
        pw.println("    Sets the Ringer mode to one of NORMAL|SILENT|VIBRATE");
        pw.println("  set-volume STREAM_TYPE VOLUME_INDEX");
        pw.println("    Sets the volume for STREAM_TYPE to VOLUME_INDEX");
        pw.println("  get-min-volume STREAM_TYPE");
        pw.println("    Gets the min volume for STREAM_TYPE");
        pw.println("  get-max-volume STREAM_TYPE");
        pw.println("    Gets the max volume for STREAM_TYPE");
        pw.println("  get-stream-volume STREAM_TYPE");
        pw.println("    Gets the volume for STREAM_TYPE");
        pw.println("  set-device-volume STREAM_TYPE VOLUME_INDEX NATIVE_DEVICE_TYPE");
        pw.println("    Sets for NATIVE_DEVICE_TYPE the STREAM_TYPE volume to VOLUME_INDEX");
        pw.println("  is-stream-mute STREAM_TYPE");
        pw.println("    Returns whether the STREAM_TYPE is muted.");
        pw.println("  is-volume-fixed");
        pw.println("    Returns whether the volume is fixed for the device");
        pw.println("  adj-mute STREAM_TYPE");
        pw.println("    mutes the STREAM_TYPE");
        pw.println("  adj-unmute STREAM_TYPE");
        pw.println("    unmutes the STREAM_TYPE");
        pw.println("  adj-volume STREAM_TYPE <RAISE|LOWER|MUTE|UNMUTE>");
        pw.println("    Adjusts the STREAM_TYPE volume given the specified direction");
        pw.println("  set-group-volume GROUP_ID VOLUME_INDEX");
        pw.println("    Sets the volume for GROUP_ID to VOLUME_INDEX");
        pw.println("  adj-group-volume GROUP_ID <RAISE|LOWER|MUTE|UNMUTE>");
        pw.println("    Adjusts the group volume for GROUP_ID given the specified direction");
        pw.println("  set-enable-hardening <1|0>");
        pw.println("    Enables full audio hardening enforcement, disabling any exemptions");
        pw.println("  set-preferred-output-device AUDIO_DEVICE_TYPE [ADDRESS]");
        pw.println("    Sets the output audio device to AUDIO_DEVICE_TYPE "
                + "(e.g. AUTO, HDMI, BLUETOOTH_A2DP, BUILTIN_SPEAKER) for media strategy."
                + " AUTO means that the preferred device will be removed and the system will"
                + " select the default device."
                + " Optionally, the ADDRESS can be provided to select a specific device."
                + " 'get-supported-output-devices' and 'get-connected-output-devices'"
            + " commands to see available output devices");
        pw.println("  get-supported-output-devices");
        pw.println("    Returns a list of supported audio output devices using"
                + " AudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS)");
        pw.println("  get-current-output-device");
        pw.println("    Returns the current output audio device for the media strategy"
                + " if preferred device is not set then returns first device in the"
                + " connected list of devices with media strategy using"
                + " AudioManager.getDevicesForAttributes(<AudioAttributes.USAGE_MEDIA>)");
        pw.println(" get-connected-output-devices");
        pw.println("    Returns a list of all connected output devices using"
                + " AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)");
        pw.println("  get-preferred-output-device");
        pw.println("    Returns the preferred output audio device for the media strategy");
    }

    private int setSurroundFormatEnabled() {
        String surroundFormatText = getNextArg();
        String isSurroundFormatEnabledText = getNextArg();

        if (surroundFormatText == null) {
            getErrPrintWriter().println("Error: no surroundFormat specified");
            return 1;
        }

        if (isSurroundFormatEnabledText == null) {
            getErrPrintWriter().println("Error: no enabled value for surroundFormat specified");
            return 1;
        }

        int surroundFormat = -1;
        boolean isSurroundFormatEnabled = false;
        try {
            surroundFormat = Integer.parseInt(surroundFormatText);
            isSurroundFormatEnabled = Boolean.parseBoolean(isSurroundFormatEnabledText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: wrong format specified for surroundFormat");
            return 1;
        }
        if (surroundFormat < 0) {
            getErrPrintWriter().println("Error: invalid value of surroundFormat");
            return 1;
        }

        mAudioManager.setSurroundFormatEnabled(surroundFormat, isSurroundFormatEnabled);
        return 0;
    }

    private int setRingerMode() {
        String ringerModeText = getNextArg();
        if (ringerModeText == null) {
            getErrPrintWriter().println("Error: no ringer mode specified");
            return 1;
        }

        final int ringerMode = getRingerMode(ringerModeText);
        if (!AudioManager.isValidRingerMode(ringerMode)) {
            getErrPrintWriter()
                    .println(
                            "Error: invalid value of ringerMode, should be one of "
                                    + "NORMAL|SILENT|VIBRATE");
            return 1;
        }

        mAudioManager.setRingerModeInternal(ringerMode);
        return 0;
    }

    private int getRingerMode(String ringerModeText) {
        return switch (ringerModeText) {
            case "NORMAL" -> AudioManager.RINGER_MODE_NORMAL;
            case "VIBRATE" -> AudioManager.RINGER_MODE_VIBRATE;
            case "SILENT" -> AudioManager.RINGER_MODE_SILENT;
            default -> -1;
        };
    }

    private int getIsSurroundFormatEnabled() {
        String surroundFormatText = getNextArg();

        if (surroundFormatText == null) {
            getErrPrintWriter().println("Error: no surroundFormat specified");
            return 1;
        }

        int surroundFormat = -1;
        try {
            surroundFormat = Integer.parseInt(surroundFormatText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: wrong format specified for surroundFormat");
            return 1;
        }

        if (surroundFormat < 0) {
            getErrPrintWriter().println("Error: invalid value of surroundFormat");
            return 1;
        }
        getOutPrintWriter()
                .println(
                        "Value of enabled for "
                                + surroundFormat
                                + " is: "
                                + mAudioManager.isSurroundFormatEnabled(surroundFormat));
        return 0;
    }

    private int setEncodedSurroundMode() {
        String encodedSurroundModeText = getNextArg();

        if (encodedSurroundModeText == null) {
            getErrPrintWriter().println("Error: no encodedSurroundMode specified");
            return 1;
        }

        int encodedSurroundMode = -1;
        try {
            encodedSurroundMode = Integer.parseInt(encodedSurroundModeText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: wrong format specified for encoded surround mode");
            return 1;
        }

        if (encodedSurroundMode < 0) {
            getErrPrintWriter().println("Error: invalid value of encodedSurroundMode");
            return 1;
        }

        mAudioManager.setEncodedSurroundMode(encodedSurroundMode);
        return 0;
    }

    private int getEncodedSurroundMode() {
        getOutPrintWriter()
                .println("Encoded surround mode: " + mAudioManager.getEncodedSurroundMode());
        return 0;
    }

    private int setSoundDoseValue() {
        String soundDoseValueText = getNextArg();

        if (soundDoseValueText == null) {
            getErrPrintWriter().println("Error: no sound dose value specified");
            return 1;
        }

        float soundDoseValue = 0.f;
        try {
            soundDoseValue = Float.parseFloat(soundDoseValueText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: wrong format specified for sound dose");
            return 1;
        }

        if (soundDoseValue < 0) {
            getErrPrintWriter().println("Error: invalid value of sound dose");
            return 1;
        }
        mAudioManager.setCsd(soundDoseValue);
        return 0;
    }

    private int getSoundDoseValue() {
        getOutPrintWriter().println("Sound dose value: " + mAudioManager.getCsd());
        return 0;
    }

    private int resetSoundDoseTimeout() {
        mAudioManager.setCsd(-1.f);
        getOutPrintWriter().println("Reset sound dose momentary exposure timeout");
        return 0;
    }

    private int setVolume() {
        final int stream = readIntArg();
        final int index = readIntArg();
        getOutPrintWriter()
                .println("calling AudioManager.setStreamVolume(" + stream + ", " + index + ", 0)");
        mAudioManager.setStreamVolume(stream, index, 0);
        return 0;
    }

    private int getMinVolume() {
        final int stream = readIntArg();
        final int result = mAudioManager.getStreamMinVolume(stream);
        getOutPrintWriter().println("AudioManager.getStreamMinVolume(" + stream + ") -> " + result);
        return 0;
    }

    private int getMaxVolume() {
        final int stream = readIntArg();
        final int result = mAudioManager.getStreamMaxVolume(stream);
        getOutPrintWriter().println("AudioManager.getStreamMaxVolume(" + stream + ") -> " + result);
        return 0;
    }

    private int getStreamVolume() {
        final int stream = readIntArg();
        final int result = mAudioManager.getStreamVolume(stream);
        getOutPrintWriter().println("AudioManager.getStreamVolume(" + stream + ") -> " + result);
        return 0;
    }

    private int setDeviceVolume() {
        final Context context = mService.mContext;
        final AudioDeviceVolumeManager advm =
                (AudioDeviceVolumeManager)
                        context.getSystemService(Context.AUDIO_DEVICE_VOLUME_SERVICE);
        final int stream = readIntArg();
        final int index = readIntArg();
        final int device = readIntArg();

        final VolumeInfo volume = new VolumeInfo.Builder(stream).setVolumeIndex(index).build();
        final AudioDeviceAttributes ada =
                new AudioDeviceAttributes(/*native type*/ device, /*address*/ "foo");
        getOutPrintWriter()
                .println(
                        "calling AudioDeviceVolumeManager.setDeviceVolume("
                                + volume
                                + ", "
                                + ada
                                + ")");
        advm.setDeviceVolume(volume, ada);
        return 0;
    }

    private int getIsStreamMute() {
        final int stream = readIntArg();
        final boolean isMuted = mAudioManager.isStreamMute(stream);
        getOutPrintWriter().println(isMuted ? "true" : "false");
        return 0;
    }

    private int getIsVolumeFixed() {
        final boolean isFixed = mAudioManager.isVolumeFixed();
        getOutPrintWriter().println(isFixed ? "true" : "false");
        return 0;
    }

    private int adjMute() {
        final int stream = readIntArg();
        getOutPrintWriter()
                .println(
                        "calling AudioManager.adjustStreamVolume("
                                + stream
                                + ", AudioManager.ADJUST_MUTE, 0)");
        mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
        return 0;
    }

    private int adjUnmute() {
        final int stream = readIntArg();
        getOutPrintWriter()
                .println(
                        "calling AudioManager.adjustStreamVolume("
                                + stream
                                + ", AudioManager.ADJUST_UNMUTE, 0)");
        mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
        return 0;
    }

    private int adjVolume() {
        final int stream = readIntArg();
        final int direction = readDirectionArg();
        getOutPrintWriter()
                .println(
                        "calling AudioManager.adjustStreamVolume("
                                + stream
                                + ", "
                                + direction
                                + ", 0)");
        mAudioManager.adjustStreamVolume(stream, direction, 0);
        return 0;
    }

    private int setGroupVolume() {
        final int groupId = readIntArg();
        final int index = readIntArg();
        getOutPrintWriter()
                .println(
                        "calling AudioManager.setVolumeGroupVolumeIndex("
                                + groupId
                                + ", "
                                + index
                                + ", 0)");
        mAudioManager.setVolumeGroupVolumeIndex(groupId, index, 0);
        return 0;
    }

    private int adjGroupVolume() {
        final int groupId = readIntArg();
        final int direction = readDirectionArg();
        getOutPrintWriter()
                .println(
                        "calling AudioManager.adjustVolumeGroupVolume("
                                + groupId
                                + ", "
                                + direction
                                + ", 0)");
        mAudioManager.adjustVolumeGroupVolume(groupId, direction, 0);
        return 0;
    }

    private int setEnableHardening() {
        final boolean shouldEnable = !(readIntArg() == 0);
        getOutPrintWriter()
                .println("calling AudioManager.setEnableHardening(" + shouldEnable + ")");
        try {
            mAudioManager.setEnableHardening(shouldEnable);
        } catch (Exception e) {
            getOutPrintWriter().println("Exception: " + e);
        }
        return 0;
    }

    private int getConnectedOutputDevices() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<String> connectedDevices = new ArrayList<>();
        for (AudioDeviceInfo device : devices) {
            connectedDevices.add(convertAudioDeviceTypeToString(device.getType()));
        }
        getOutPrintWriter().println(connectedDevices);
        return 0;
    }

    @RequiresPermission(Manifest.permission.MODIFY_AUDIO_ROUTING)
    private int getCurrentOutputDevice() {
        List<AudioDeviceAttributes> devices = mAudioManager
                .getDevicesForAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());
        if (devices.size() == 0) {
            getErrPrintWriter().println("Error: no devices audio devices connected");
            return 1;
        }

        getOutPrintWriter()
                        .println(convertAudioDeviceTypeToString(devices.get(0).getType()));
        return 0;
    }

    private int getSupportedOutputDevices() {
        Set<Integer> devices = mAudioManager
                .getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        List<String> supportedDevices = new ArrayList<>();
        for (int device : devices) {
            String deviceType = convertAudioDeviceTypeToString(device);
            if (!deviceType.equals("UNKNOWN")) {
                supportedDevices.add(convertAudioDeviceTypeToString(device));
            }
        }
        getOutPrintWriter().println(supportedDevices);
        return 0;
    }

    @RequiresPermission(Manifest.permission.MODIFY_AUDIO_ROUTING)
    private int getPreferredOutputDevice() {
        AudioDeviceAttributes preferredDevice = mAudioManager
                .getPreferredDeviceForStrategy(getMediaStrategy());
        if (preferredDevice == null) {
            getErrPrintWriter().println("no preferred device set");
            return 1;
        }
        getOutPrintWriter().println(convertAudioDeviceTypeToString(preferredDevice.getType()));
        return 0;
    }

    private int setPreferredOutputDevice() {
        final String argText = getNextArg();
        if (argText == null || argText.isEmpty()) {
            getErrPrintWriter()
                .println("Error: no output device type provided"
                        + "\n'get-connected-output-devices' can be used to get supported devices");
            return 1;
        }
        // if the argument is AUTO, remove the previous preferred audio device
        if (argText.equalsIgnoreCase("AUTO")) {
            boolean result = mAudioManager.removePreferredDeviceForStrategy(getMediaStrategy());
            if (!result) {
                getErrPrintWriter().println("failed to set to default");
                return 1;
            }
            getOutPrintWriter().println("successfully set to default");
            return 0;
        }

        int audioDeviceType = convertAudioDeviceTypeFromString(argText);
        if (audioDeviceType == AudioDeviceInfo.TYPE_UNKNOWN) {
            getErrPrintWriter().println("Error: invalid output device type provided: " + argText
                    + "'get-supported-audio-devices' can be used to get supported devices");
            return 1;
        }

        // Get all connected output devices
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices.length == 0) {
            getErrPrintWriter().println("Error: no devices connected");
            return 1;
        }

        String address = getNextArg();
        AudioDeviceInfo selectedDevice = null;
        // Find the device with the given type
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == audioDeviceType
                && (address == null || device.getAddress().equals(address))) {
                selectedDevice = device;
                break;
            }
        }

        if (selectedDevice == null) {
            getErrPrintWriter().println("Error: no device connected for type: " + argText
                + (address != null ? " with address: " + address : ""));
            return 1;
        }

        AudioDeviceAttributes audioDeviceAttributes = new AudioDeviceAttributes(
                selectedDevice.getInternalType(), selectedDevice.getAddress());
        boolean result = mAudioManager
                .setPreferredDeviceForStrategy(getMediaStrategy(), audioDeviceAttributes);
        if (!result) {
            getErrPrintWriter().println("failed to set preferred device");
            return 1;
        }
        getOutPrintWriter().println("successfully set preferred device");
        return 0;
    }

    private AudioProductStrategy getMediaStrategy() {
        AudioAttributes audioAttributes =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
        return AudioProductStrategy
            .getAudioProductStrategyForAudioAttributes(audioAttributes, true);
    }

    private int readIntArg() throws IllegalArgumentException {
        final String argText = getNextArg();

        if (argText == null) {
            getErrPrintWriter().println("Error: no argument provided");
            throw new IllegalArgumentException("No argument provided");
        }

        int argIntVal;
        try {
            argIntVal = Integer.parseInt(argText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: wrong format for argument " + argText);
            throw new IllegalArgumentException("Wrong format for argument " + argText);
        }

        return argIntVal;
    }

    private int readDirectionArg() throws IllegalArgumentException {
        final String argText = getNextArg();

        if (argText == null) {
            getErrPrintWriter().println("Error: no argument provided");
            throw new IllegalArgumentException("No argument provided");
        }

        return switch (argText) {
            case "RAISE" -> ADJUST_RAISE;
            case "LOWER" -> ADJUST_LOWER;
            case "MUTE" -> ADJUST_MUTE;
            case "UNMUTE" -> ADJUST_UNMUTE;
            default -> throw new IllegalArgumentException("Wrong direction argument: " + argText);
        };
    }

    private static final Map<String, Integer> sDeviceTypeStringToInteger = new HashMap<>();

    private static final Map<Integer, String> sDeviceTypeIntegerToString = new HashMap<>();

    static {
        sDeviceTypeStringToInteger.put("BUILTIN_EARPIECE", AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        sDeviceTypeStringToInteger.put("BUILTIN_MIC", AudioDeviceInfo.TYPE_BUILTIN_MIC);
        sDeviceTypeStringToInteger.put("BUILTIN_SPEAKER", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        sDeviceTypeStringToInteger.put("BLUETOOTH_SCO", AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        sDeviceTypeStringToInteger.put("BLUETOOTH_A2DP", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        sDeviceTypeStringToInteger.put("WIRED_HEADPHONES", AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
        sDeviceTypeStringToInteger.put("WIRED_HEADSET", AudioDeviceInfo.TYPE_WIRED_HEADSET);
        sDeviceTypeStringToInteger.put("HDMI", AudioDeviceInfo.TYPE_HDMI);
        sDeviceTypeStringToInteger.put("TELEPHONY", AudioDeviceInfo.TYPE_TELEPHONY);
        sDeviceTypeStringToInteger.put("DOCK", AudioDeviceInfo.TYPE_DOCK);
        sDeviceTypeStringToInteger.put("USB_ACCESSORY", AudioDeviceInfo.TYPE_USB_ACCESSORY);
        sDeviceTypeStringToInteger.put("USB_DEVICE", AudioDeviceInfo.TYPE_USB_DEVICE);
        sDeviceTypeStringToInteger.put("USB_HEADSET", AudioDeviceInfo.TYPE_USB_HEADSET);
        sDeviceTypeStringToInteger.put("FM", AudioDeviceInfo.TYPE_FM);
        sDeviceTypeStringToInteger.put("FM_TUNER", AudioDeviceInfo.TYPE_FM_TUNER);
        sDeviceTypeStringToInteger.put("TV_TUNER", AudioDeviceInfo.TYPE_TV_TUNER);
        sDeviceTypeStringToInteger.put("LINE_ANALOG", AudioDeviceInfo.TYPE_LINE_ANALOG);
        sDeviceTypeStringToInteger.put("LINE_DIGITAL", AudioDeviceInfo.TYPE_LINE_DIGITAL);
        sDeviceTypeStringToInteger.put("IP", AudioDeviceInfo.TYPE_IP);
        sDeviceTypeStringToInteger.put("BUS", AudioDeviceInfo.TYPE_BUS);
        sDeviceTypeStringToInteger.put("REMOTE_SUBMIX", AudioDeviceInfo.TYPE_REMOTE_SUBMIX);
        sDeviceTypeStringToInteger.put("BLE_HEADSET", AudioDeviceInfo.TYPE_BLE_HEADSET);
        sDeviceTypeStringToInteger.put("HDMI_ARC", AudioDeviceInfo.TYPE_HDMI_ARC);
        sDeviceTypeStringToInteger.put("HDMI_EARC", AudioDeviceInfo.TYPE_HDMI_EARC);
        sDeviceTypeStringToInteger.put("ECHO_REFERENCE", AudioDeviceInfo.TYPE_ECHO_REFERENCE);
        sDeviceTypeStringToInteger.put("DOCK_ANALOG", AudioDeviceInfo.TYPE_DOCK_ANALOG);
        sDeviceTypeStringToInteger.put("AUX_LINE", AudioDeviceInfo.TYPE_AUX_LINE);
        sDeviceTypeStringToInteger.put("HEARING_AID", AudioDeviceInfo.TYPE_HEARING_AID);
        sDeviceTypeStringToInteger.put(
                "BUILTIN_SPEAKER_SAFE", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE);
        sDeviceTypeStringToInteger.put("BLE_SPEAKER", AudioDeviceInfo.TYPE_BLE_SPEAKER);
        sDeviceTypeStringToInteger.put("BLE_BROADCAST", AudioDeviceInfo.TYPE_BLE_BROADCAST);
        sDeviceTypeStringToInteger.put(
                "MULTICHANNEL_GROUP", AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP);

        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "BUILTIN_EARPIECE");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BUILTIN_MIC, "BUILTIN_MIC");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "BUILTIN_SPEAKER");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "BLUETOOTH_SCO");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "BLUETOOTH_A2DP");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "WIRED_HEADPHONES");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_WIRED_HEADSET, "WIRED_HEADSET");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_HDMI, "HDMI");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_TELEPHONY, "TELEPHONY");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_DOCK, "DOCK");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_USB_ACCESSORY, "USB_ACCESSORY");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_USB_DEVICE, "USB_DEVICE");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_USB_HEADSET, "USB_HEADSET");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_FM, "FM");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_FM_TUNER, "FM_TUNER");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_TV_TUNER, "TV_TUNER");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_LINE_ANALOG, "LINE_ANALOG");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_LINE_DIGITAL, "LINE_DIGITAL");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_IP, "IP");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BUS, "BUS");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "REMOTE_SUBMIX");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BLE_HEADSET, "BLE_HEADSET");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_HDMI_ARC, "HDMI_ARC");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_HDMI_EARC, "HDMI_EARC");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_ECHO_REFERENCE, "ECHO_REFERENCE");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_DOCK_ANALOG, "DOCK_ANALOG");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_AUX_LINE, "AUX_LINE");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_HEARING_AID, "HEARING_AID");
        sDeviceTypeIntegerToString.put(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, "BUILTIN_SPEAKER_SAFE");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BLE_SPEAKER, "BLE_SPEAKER");
        sDeviceTypeIntegerToString.put(AudioDeviceInfo.TYPE_BLE_BROADCAST, "BLE_BROADCAST");
        sDeviceTypeIntegerToString.put(
                AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP, "MULTICHANNEL_GROUP");
    }

    /**
     * Converts an integer to a string representation of the audio device type.
     *
     * @param deviceType The integer representing the audio device type.
     * @return The string representation of the audio device type.
     */
    private String convertAudioDeviceTypeToString(@AudioDeviceInfo.AudioDeviceType int deviceType) {
        return sDeviceTypeIntegerToString.getOrDefault(deviceType, "UNKNOWN");
    }

    /**
     * Converts a string representation of the audio device type to an integer.
     *
     * @param deviceTypeString The string representing the audio device type.
     * @return The integer representing the audio device type.
     */
    private @AudioDeviceInfo.AudioDeviceType int convertAudioDeviceTypeFromString(
            String deviceTypeString) {
        return sDeviceTypeStringToInteger.getOrDefault(
                deviceTypeString, AudioDeviceInfo.TYPE_UNKNOWN);
    }
}
