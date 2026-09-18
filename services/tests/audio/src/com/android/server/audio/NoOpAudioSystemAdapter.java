/*
 * Copyright 2020 The Android Open Source Project
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

import static android.media.audiopolicy.AudioProductStrategy.DEFAULT_ZONE_ID;

import android.annotation.NonNull;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioMixerAttributes;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioProductStrategy;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides an adapter for AudioSystem that does nothing.
 * Overridden methods can be configured.
 */
public class NoOpAudioSystemAdapter extends AudioSystemAdapter {
    private static final String TAG = "ASA";
    private boolean mIsMicMuted = false;
    private boolean mMuteMicrophoneFails = false;
    private boolean mIsStreamActive = false;
    private List<AudioProductStrategy> mAudioProductStrategies = Collections.emptyList();
    private boolean mFailOnUserSetProductStrategiesMapping = false;
    private SparseIntArray mMinVolumeGroupValues = new SparseIntArray();
    private SparseIntArray mMaxVolumeGroupValues = new SparseIntArray();

    public void configureIsMicrophoneMuted(boolean muted) {
        mIsMicMuted = muted;
    }

    public void configureIsStreamActive(boolean active) {
        mIsStreamActive = active;
    }

    public void configureMuteMicrophoneToFail(boolean fail) {
        mMuteMicrophoneFails = fail;
    }

    /**
     * Configure the audio product strategies.
     *
     * @param strategies that will be returned by getAudioProductStrategies()
     */
    public void configureAudioProductStrategies(List<AudioProductStrategy> strategies) {
        mAudioProductStrategies = strategies;
    }

    /**
     * Configure the min and max volume group values.
     *
     * @param minValues the min volume group values
     * @param maxValues the max volume group values
     */
    public void configureMinMaxVolumeGroupValues(SparseIntArray minValues,
            SparseIntArray maxValues) {
        mMinVolumeGroupValues = minValues;
        mMaxVolumeGroupValues = maxValues;
    }

    //-----------------------------------------------------------------
    // Overrides of AudioSystemAdapter
    @Override
    public int setDeviceConnectionState(AudioDeviceAttributes attributes, int state,
            int codecFormat, boolean deviceSwitch) {
        Log.i(TAG, String.format("setDeviceConnectionState(0x%s, %d, 0x%s %b",
                attributes.toString(), state, Integer.toHexString(codecFormat), deviceSwitch));
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int getDeviceConnectionState(int device, String deviceAddress) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int handleDeviceConfigChange(int device, String deviceAddress,
            String deviceName, int codecFormat) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setDevicesRoleForStrategy(int strategy, int role,
            @NonNull List<AudioDeviceAttributes> devices) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int removeDevicesRoleForStrategy(int strategy, int role,
            @NonNull List<AudioDeviceAttributes> devices) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int clearDevicesRoleForStrategy(int strategy, int role) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setDevicesRoleForCapturePreset(int capturePreset, int role,
                                              @NonNull List<AudioDeviceAttributes> devices) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int removeDevicesRoleForCapturePreset(
            int capturePreset, int role, @NonNull List<AudioDeviceAttributes> devicesToRemove) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int clearDevicesRoleForCapturePreset(int capturePreset, int role) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setParameters(String keyValuePairs) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public boolean isMicrophoneMuted() {
        return mIsMicMuted;
    }

    @Override
    public int muteMicrophone(boolean on) {
        if (mMuteMicrophoneFails) {
            return AudioSystem.AUDIO_STATUS_ERROR;
        }
        mIsMicMuted = on;
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setCurrentImeUid(int uid) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public boolean isStreamActive(int stream, int inPastMs) {
        return mIsStreamActive;
    }

    @Override
    public int setStreamVolumeIndexAS(int stream, int index, boolean muted, int device) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setVolumeIndexForAttributes(AudioAttributes attributes, int index, boolean muted,
            int device) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int getVolumeIndexForGroup(int groupId, int device) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int getMinVolumeIndexForGroup(int groupId) {
        return mMinVolumeGroupValues.get(groupId, AudioSystem.AUDIO_STATUS_OK);
    }

    @Override
    public int setMinVolumeIndexForGroup(int groupId, int index) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int getMaxVolumeIndexForGroup(int groupId) {
        return mMaxVolumeGroupValues.get(groupId, AudioSystem.AUDIO_STATUS_OK);
    }

    @Override
    public int setMaxVolumeIndexForGroup(int groupId, int index) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int setVolumeIndexForGroup(int groupId, int uid, int index, boolean muted, int device) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    @NonNull
    public ArrayList<AudioDeviceAttributes> getDevicesForAttributes(
            @NonNull AudioAttributes attributes, boolean forVolume) {
        return new ArrayList<>();
    }

    @Override
    public int setMasterMute(boolean muted) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public List<AudioProductStrategy> getAudioProductStrategies(boolean filterInternal) {
        return mAudioProductStrategies;
    }

    @Override
    public int setProductStrategiesZoneIdForUserId(int userId, int zoneId) {
        return mFailOnUserSetProductStrategiesMapping
                ? AudioSystem.AUDIO_STATUS_ERROR : AudioSystem.AUDIO_STATUS_OK;
    }

    @Override
    public int resetProductStrategiesZoneIdForUserId(int userId) {
        return AudioSystem.AUDIO_STATUS_OK;
    }

    /**
     * Configures this adapter to fail when calling
     * {@link #setProductStrategiesZoneIdForUserId(int, int)}.
     * @param fail true to fail, false otherwise
     */
    public void configureFailOnSetProductStrategiesZoneIdForUserId(boolean fail) {
        mFailOnUserSetProductStrategiesMapping = fail;
    }

    @Override
    public int getZoneIdForAudioVolumeGroupId(int groupId) {
        return DEFAULT_ZONE_ID;
    }

    @Override
    public int getPreferredMixerAttributes(AudioAttributes attributes, int portId, int uid,
            List<AudioMixerAttributes> mixerAttrList) {
        return AudioSystem.AUDIO_STATUS_OK;
    }
}
