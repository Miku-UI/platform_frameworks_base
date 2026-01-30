/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.media;

import static android.bluetooth.BluetoothAdapter.ACTIVE_DEVICE_AUDIO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothVolumeControl;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.media.flags.Flags;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/* package */ class BluetoothProfileMonitor {

    private static final String TAG = BluetoothProfileMonitor.class.getSimpleName();

    /* package */ static final long GROUP_ID_NO_GROUP = -1L;
    /* package */ static final int INVALID_VOLUME = -1;
    /* package */ static final int MAXIMUM_DEVICE_VOLUME = 255;
    // TODO(b/397568136): remove reading primary group id from SettingsProvider once
    //  adopt_primary_group_management_api_v2  is rolled out
    private static final String KEY_PRIMARY_GROUP_ID =
            "bluetooth_le_broadcast_fallback_active_group_id";

    private static final int INVALID_BROADCAST_ID = 0;
    private static final String UNDERLINE = "_";
    private static final int DEFAULT_CODE_MAX = 9999;
    private static final int DEFAULT_CODE_MIN = 1000;
    private static final int MIN_NO_DEVICES_FOR_BROADCAST = 2;
    private static final String VALID_PASSWORD_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,"
                    + ".<>?/";
    private static final int PASSWORD_LENGTH = 16;
    private static final int BROADCAST_NAME_PREFIX_MAX_LENGTH = 27;
    private static final String DEFAULT_BROADCAST_NAME_PREFIX = "Broadcast";
    private static final String IMPROVE_COMPATIBILITY_HIGH_QUALITY = "1";

    @NonNull
    private final ProfileListener mProfileListener = new ProfileListener();
    @NonNull private final BroadcastCallback mBroadcastCallback = new BroadcastCallback();
    @NonNull private final VolumeControlCallback mVolumeCallback = new VolumeControlCallback();

    @NonNull
    private final BroadcastAssistantCallback mBroadcastAssistantCallback =
            new BroadcastAssistantCallback();

    @NonNull private final Context mContext;
    @NonNull private final Handler mHandler;
    @NonNull private final BluetoothAdapter mBluetoothAdapter;

    @Nullable
    private BluetoothA2dp mA2dpProfile;
    @Nullable
    private BluetoothHearingAid mHearingAidProfile;
    @Nullable
    private BluetoothLeAudio mLeAudioProfile;

    @GuardedBy("this")
    @Nullable
    private BluetoothLeBroadcast mBroadcastProfile;

    @Nullable private BluetoothLeBroadcastAssistant mAssistantProfile;
    @Nullable private BluetoothVolumeControl mVolumeProfile;

    private final List<BluetoothDevice> mDevicesToAdd = new ArrayList<>();
    private int mBroadcastId = INVALID_BROADCAST_ID;
    private final ConcurrentHashMap<BluetoothDevice, Integer> mVolumeMap =
            new ConcurrentHashMap<>();

    @NonNull
    private BluetoothDeviceRoutesManager.OnBroadcastSinkChangedListener mSinkChangedListener;

    BluetoothProfileMonitor(
            @NonNull Context context,
            @NonNull Looper looper,
            @NonNull BluetoothAdapter bluetoothAdapter) {
        mContext = Objects.requireNonNull(context);
        mHandler = new Handler(Objects.requireNonNull(looper));
        mBluetoothAdapter = Objects.requireNonNull(bluetoothAdapter);
        // no-op listener, will be overridden in start()
        mSinkChangedListener = () -> {};
    }

    /* package */ void start(
            @NonNull
                    BluetoothDeviceRoutesManager.OnBroadcastSinkChangedListener
                            sinkChangedListener) {
        mSinkChangedListener = sinkChangedListener;
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.A2DP);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEARING_AID);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.LE_AUDIO);
        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
            mBluetoothAdapter.getProfileProxy(
                    mContext, mProfileListener, BluetoothProfile.LE_AUDIO_BROADCAST);
            mBluetoothAdapter.getProfileProxy(
                    mContext, mProfileListener, BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            mBluetoothAdapter.getProfileProxy(
                    mContext, mProfileListener, BluetoothProfile.VOLUME_CONTROL);
        }
    }

    /* package */ boolean isProfileSupported(int profile, @NonNull BluetoothDevice device) {
        BluetoothProfile bluetoothProfile;

        synchronized (this) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    bluetoothProfile = mA2dpProfile;
                    break;
                case BluetoothProfile.LE_AUDIO:
                    bluetoothProfile = mLeAudioProfile;
                    break;
                case BluetoothProfile.HEARING_AID:
                    bluetoothProfile = mHearingAidProfile;
                    break;
                default:
                    throw new IllegalArgumentException(
                            profile + " is not supported as Bluetooth profile");
            }
        }

        if (bluetoothProfile == null) {
            return false;
        }

        return bluetoothProfile.getConnectedDevices().contains(device);
    }

    /* package */ long getGroupId(int profile, @NonNull BluetoothDevice device) {
        synchronized (this) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    return GROUP_ID_NO_GROUP;
                case BluetoothProfile.LE_AUDIO:
                    return mLeAudioProfile == null ? GROUP_ID_NO_GROUP : mLeAudioProfile.getGroupId(
                            device);
                case BluetoothProfile.HEARING_AID:
                    return mHearingAidProfile == null
                            ? GROUP_ID_NO_GROUP : mHearingAidProfile.getHiSyncId(device);
                default:
                    throw new IllegalArgumentException(profile
                            + " is not supported as Bluetooth profile");
            }
        }
    }

    /**
     * Starts broadcast and connect to given bluetooth devices.
     *
     * @param devices Bluetooth devices that are going to connect the to broadcast
     */
    public synchronized void startBroadcast(List<BluetoothDevice> devices) {
        if (devices.size() < MIN_NO_DEVICES_FOR_BROADCAST) {
            Slog.e(
                    TAG,
                    "Insufficient number of device to start broadcast: need to have at least "
                            + MIN_NO_DEVICES_FOR_BROADCAST
                            + " device(s) to start broadcast, current no. of selected device(s) = "
                            + devices.size());
            return;
        }

        if (mBroadcastProfile == null) {
            // BluetoothLeBroadcast is not initialized properly
            Slog.e(TAG, "Fail to start broadcast, LeBroadcast is null");
            return;
        }

        if (mBroadcastProfile.getAllBroadcastMetadata().size() >= getMaximumNumberOfBroadcasts()) {
            Slog.e(
                    TAG,
                    "Fail to start broadcast, current number of broadcasting group: "
                            + mBroadcastProfile.getAllBroadcastMetadata().size()
                            + ", exceeds the maximum allowed: "
                            + getMaximumNumberOfBroadcasts());
            return;
        }

        // Store the broadcast name so that program info and broadcast setting can use the same
        // value.
        String broadcastName = getBroadcastName();

        // Current broadcast framework only support one subgroup
        BluetoothLeBroadcastSubgroupSettings subgroupSettings =
                buildBroadcastSubgroupSettings(
                        /* language= */ null,
                        getProgramInfo(broadcastName),
                        isImproveQualityFlagEnabled());
        BluetoothLeBroadcastSettings settings =
                buildBroadcastSettings(
                        broadcastName,
                        getBroadcastCode(),
                        List.of(subgroupSettings));

        mDevicesToAdd.clear();
        mDevicesToAdd.addAll(devices);
        mBroadcastProfile.startBroadcast(settings);
    }

    /** Stops the broadcast */
    public synchronized void stopBroadcast() {
        if (mBroadcastProfile == null) {
            Slog.e(TAG, "Fail to stop broadcast, LeBroadcast is null");
            return;
        }
        mBroadcastProfile.stopBroadcast(mBroadcastId);
    }

    /**
     * Stops the broadcast, optionally making a new BT device active.
     *
     * <p>This method is expected to use the given device to determine which unicast fallback group
     * should be set or which classic device should be active when the broadcast stops.
     *
     * @param device BT device that should become active once the broadcast stops, or null if no BT
     *     device should become active once broadcast stops.
     */
    public synchronized void stopBroadcast(@Nullable BluetoothDevice device) {
        if (mBroadcastProfile == null) {
            Slog.e(TAG, "Fail to stop broadcast, LeBroadcast is null");
            return;
        }
        if (mLeAudioProfile == null) {
            Slog.e(TAG, "Fail to set fall back group, LeProfile is null");
        } else {
            // if no valid group id, set the fallback to -1, no LEA device should become active
            // once broadcast stops
            int groupId =
                    (device == null || !isProfileSupported(BluetoothProfile.LE_AUDIO, device))
                            ? BluetoothLeAudio.GROUP_ID_INVALID
                            : (int) getGroupId(BluetoothProfile.LE_AUDIO, device);
            if (device != null && groupId == BluetoothLeAudio.GROUP_ID_INVALID) {
                // for classic device, we need set active for it explicitly, because when broadcast
                // stops, bt stack will only deal with fallback LEA device.
                Slog.d(TAG, "stopBroadcast: set active device to " + device.getAnonymizedAddress());
                mBluetoothAdapter.setActiveDevice(device, ACTIVE_DEVICE_AUDIO);
            }
            Slog.d(TAG, "stopBroadcast: set broadcast fallabck group to " + groupId);
            mLeAudioProfile.setBroadcastToUnicastFallbackGroup(groupId);
        }
        mBroadcastProfile.stopBroadcast(mBroadcastId);
    }

    /**
     * Obtains selected bluetooth devices from broadcast assistant that are broadcasting.
     *
     * @return list of selected {@link BluetoothDevice}
     */
    public List<BluetoothDevice> getDevicesWithBroadcastSource() {
        if (mAssistantProfile == null) {
            return List.of();
        }

        return mAssistantProfile.getConnectedDevices().stream()
                .filter(
                        device ->
                                mAssistantProfile.getAllSources(device).stream()
                                        .anyMatch(
                                                source -> source.getBroadcastId() == mBroadcastId))
                .toList();
    }

    /**
     * Gets the maximum number of Broadcast Isochronous Group supported on this device from
     * broadcast profile
     *
     * @return value of the maximum number of Broadcast Isochronous Group supported on this device
     *     from broadcast profile
     */
    public int getMaximumNumberOfBroadcasts() {
        return mBroadcastProfile.getMaximumNumberOfBroadcasts();
    }

    /**
     * Perform add device as broadcast source to {@link BluetoothLeBroadcastAssistant}. Devices will
     * then receive audio broadcast
     *
     * @param deviceList - List of {@link BluetoothDevice} for broadcast
     * @param metadata - broadcast metadata that obtained from broadcast object
     */
    private void addSourceToDevices(
            List<BluetoothDevice> deviceList, BluetoothLeBroadcastMetadata metadata) {
        if (mAssistantProfile == null) {
            Slog.d(TAG, "BroadcastAssistant is null");
            return;
        }

        if (metadata == null) {
            Slog.d(TAG, "BroadcastMetadata is null");
            return;
        }

        for (BluetoothDevice device : deviceList) {
            mAssistantProfile.addSource(device, metadata, /* isGroupOp= */ false);
        }
    }

    @Nullable
    private byte[] getBroadcastCode() {
        ContentResolver contentResolver = mContext.getContentResolver();

        String prefBroadcastCode =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_CODE,
                        contentResolver.getUserId());

        byte[] broadcastCode =
                (prefBroadcastCode == null)
                        ? generateRandomPassword().getBytes(StandardCharsets.UTF_8)
                        : prefBroadcastCode.getBytes(StandardCharsets.UTF_8);

        return (broadcastCode != null && broadcastCode.length > 0) ? broadcastCode : null;
    }

    @NonNull
    private String getBroadcastName() {
        ContentResolver contentResolver = mContext.getContentResolver();
        String settingBroadcastName =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_NAME,
                        contentResolver.getUserId());

        if (settingBroadcastName == null || settingBroadcastName.isEmpty()) {
            int postfix = ThreadLocalRandom.current().nextInt(DEFAULT_CODE_MIN, DEFAULT_CODE_MAX);
            String name = BluetoothAdapter.getDefaultAdapter().getName();
            if (name == null || name.isEmpty()) {
                name = DEFAULT_BROADCAST_NAME_PREFIX;
            }
            return (name.length() < BROADCAST_NAME_PREFIX_MAX_LENGTH
                            ? name
                            : name.substring(0, BROADCAST_NAME_PREFIX_MAX_LENGTH))
                    + UNDERLINE
                    + postfix;
        }
        return settingBroadcastName;
    }

    @NonNull
    private String getProgramInfo(@NonNull String defaultProgramInfo) {
        ContentResolver contentResolver = mContext.getContentResolver();

        String programInfo =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_PROGRAM_INFO,
                        contentResolver.getUserId());

        if (programInfo == null || programInfo.isEmpty()) {
            return defaultProgramInfo;
        }

        return programInfo;
    }

    private static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int randomIndex = random.nextInt(VALID_PASSWORD_CHARACTERS.length());
            stringBuilder.append(VALID_PASSWORD_CHARACTERS.charAt(randomIndex));
        }

        return stringBuilder.toString();
    }

    private boolean isImproveQualityFlagEnabled() {
        ContentResolver contentResolver = mContext.getContentResolver();
        // BLUETOOTH_LE_BROADCAST_IMPROVE_COMPATIBILITY takes only "1" and "0" in string only. Check
        // android.provider.settings.validators.SecureSettingsValidators for mode details.
        return IMPROVE_COMPATIBILITY_HIGH_QUALITY.equals(
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_IMPROVE_COMPATIBILITY,
                        contentResolver.getUserId()));
    }

    private BluetoothLeBroadcastSettings buildBroadcastSettings(
            String broadcastName,
            byte[] broadcastCode,
            List<BluetoothLeBroadcastSubgroupSettings> subgroupSettingsList) {
        BluetoothLeBroadcastSettings.Builder builder =
                new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(
                                /* isPublicBroadcast= */ true) // To advertise the broadcast
                                                               // settings, e.g. name.
                        .setBroadcastName(broadcastName)
                        .setBroadcastCode(broadcastCode);
        for (BluetoothLeBroadcastSubgroupSettings subgroupSettings : subgroupSettingsList) {
            builder.addSubgroupSettings(subgroupSettings);
        }
        return builder.build();
    }

    private BluetoothLeBroadcastSubgroupSettings buildBroadcastSubgroupSettings(
            String language, String programInfo, boolean improveCompatibility) {
        BluetoothLeAudioContentMetadata metadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setLanguage(language)
                        .setProgramInfo(programInfo)
                        .build();
        // Current broadcast framework only support one subgroup, thus we still maintain the latest
        // metadata to keep legacy UI working.
        return new BluetoothLeBroadcastSubgroupSettings.Builder()
                .setPreferredQuality(
                        improveCompatibility
                                ? BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD
                                : BluetoothLeBroadcastSubgroupSettings.QUALITY_HIGH)
                .setContentMetadata(metadata)
                .build();
    }

    /* package */ boolean isDeviceInBroadcast(@NonNull BluetoothDevice device) {
        return mAssistantProfile != null
                && mBroadcastId != INVALID_BROADCAST_ID
                && mAssistantProfile.getAllSources(device).stream()
                        .anyMatch(source -> source.getBroadcastId() == mBroadcastId);
    }

    /* package */ void setDeviceVolume(
            @NonNull BluetoothDevice device, int volume, boolean isGroupOp) {
        if (mVolumeProfile != null) {
            mVolumeProfile.setDeviceVolume(device, volume, isGroupOp);
        }
    }

    /* package */ int getDeviceVolume(@NonNull BluetoothDevice device) {
        return isDeviceInBroadcast(device)
                ? mVolumeMap.getOrDefault(device, INVALID_VOLUME)
                : INVALID_VOLUME;
    }

    /**
     * Check if the BT device is the media only device in broadcast.
     *
     * <p>There are two types of sinks in the broadcast session, primary sink and media only sink.
     *
     * <p>Primary sink is the sink can listen to the call, usually it is the one belongs to the
     * broadcast owner.
     *
     * <p>Media only sink can only listen to audio shared by the broadcaster, including media and
     * notification.
     */
    /* package */ boolean isMediaOnlyDeviceInBroadcast(@NonNull BluetoothDevice device) {
        // Media only device, other than primary device, can only listen to the broadcast content
        // and is not the default one to listen to the call.
        long groupId = getGroupId(BluetoothProfile.LE_AUDIO, device);
        if (groupId == GROUP_ID_NO_GROUP) {
            Slog.d(TAG, "isMediaOnlyDeviceInBroadcast, invalid group id");
            return false;
        }
        long primaryGroupId = GROUP_ID_NO_GROUP;
        if (com.android.settingslib.flags.Flags.adoptPrimaryGroupManagementApiV2()) {
            if (mLeAudioProfile != null) {
                primaryGroupId = mLeAudioProfile.getBroadcastToUnicastFallbackGroup();
            }
        } else {
            // TODO(b/397568136): remove reading primary group id from SettingsProvider once
            //  adopt_primary_group_management_api_v2 is rolled out
            ContentResolver contentResolver = mContext.getContentResolver();
            primaryGroupId =
                    Settings.Secure.getIntForUser(
                            contentResolver,
                            KEY_PRIMARY_GROUP_ID,
                            (int) GROUP_ID_NO_GROUP,
                            contentResolver.getUserId());
        }
        return groupId != primaryGroupId;
    }

    private final class ProfileListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            synchronized (BluetoothProfileMonitor.this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dpProfile = (BluetoothA2dp) proxy;
                        break;
                    case BluetoothProfile.HEARING_AID:
                        mHearingAidProfile = (BluetoothHearingAid) proxy;
                        break;
                    case BluetoothProfile.LE_AUDIO:
                        mLeAudioProfile = (BluetoothLeAudio) proxy;
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mBroadcastProfile = (BluetoothLeBroadcast) proxy;
                            mBroadcastProfile.registerCallback(mHandler::post, mBroadcastCallback);
                        }
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mAssistantProfile = (BluetoothLeBroadcastAssistant) proxy;
                            mAssistantProfile.registerCallback(
                                    mHandler::post, mBroadcastAssistantCallback);
                        }
                        break;
                    case BluetoothProfile.VOLUME_CONTROL:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mVolumeProfile = (BluetoothVolumeControl) proxy;
                            mVolumeProfile.registerCallback(mHandler::post, mVolumeCallback);
                        }
                        break;
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            synchronized (BluetoothProfileMonitor.this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dpProfile = null;
                        break;
                    case BluetoothProfile.HEARING_AID:
                        mHearingAidProfile = null;
                        break;
                    case BluetoothProfile.LE_AUDIO:
                        mLeAudioProfile = null;
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mBroadcastProfile.unregisterCallback(mBroadcastCallback);
                            mBroadcastProfile = null;
                            mBroadcastId = INVALID_BROADCAST_ID;
                        }
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mAssistantProfile.unregisterCallback(mBroadcastAssistantCallback);
                            mAssistantProfile = null;
                        }
                        break;
                    case BluetoothProfile.VOLUME_CONTROL:
                        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                            mVolumeProfile.unregisterCallback(mVolumeCallback);
                            mVolumeProfile = null;
                        }
                        break;
                }
            }
        }
    }

    private final class BroadcastCallback implements BluetoothLeBroadcast.Callback {
        @Override
        public void onBroadcastStarted(int reason, int broadcastId) {
            mBroadcastId = broadcastId;
        }

        @Override
        public void onBroadcastStartFailed(int reason) {
            // To prevent broadcast accidentally start when metadata change
            mDevicesToAdd.clear();
        }

        @Override
        public void onBroadcastStopped(int reason, int broadcastId) {
            mBroadcastId = 0;
        }

        @Override
        public void onBroadcastStopFailed(int reason) {}

        @Override
        public void onPlaybackStarted(int reason, int broadcastId) {}

        @Override
        public void onPlaybackStopped(int reason, int broadcastId) {}

        @Override
        public void onBroadcastUpdated(int reason, int broadcastId) {}

        @Override
        public void onBroadcastUpdateFailed(int reason, int broadcastId) {}

        @Override
        public void onBroadcastMetadataChanged(
                int broadcastId, @NonNull BluetoothLeBroadcastMetadata metadata) {
            BluetoothProfileMonitor.this.addSourceToDevices(mDevicesToAdd, metadata);
            mDevicesToAdd.clear();
        }
    }

    private final class BroadcastAssistantCallback
            implements BluetoothLeBroadcastAssistant.Callback {
        @Override
        public void onSearchStarted(int reason) {}

        @Override
        public void onSearchStartFailed(int reason) {}

        @Override
        public void onSearchStopped(int reason) {}

        @Override
        public void onSearchStopFailed(int reason) {}

        @Override
        public void onSourceFound(@NonNull BluetoothLeBroadcastMetadata source) {}

        @Override
        public void onSourceAdded(@NonNull BluetoothDevice sink, int sourceId, int reason) {
            mSinkChangedListener.onBroadcastSinkChanged();
        }

        @Override
        public void onSourceAddFailed(
                @NonNull BluetoothDevice sink,
                @NonNull BluetoothLeBroadcastMetadata source,
                int reason) {}

        @Override
        public void onSourceModified(@NonNull BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceModifyFailed(@NonNull BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onSourceRemoved(@NonNull BluetoothDevice sink, int sourceId, int reason) {
            mSinkChangedListener.onBroadcastSinkChanged();
        }

        @Override
        public void onSourceRemoveFailed(@NonNull BluetoothDevice sink, int sourceId, int reason) {}

        @Override
        public void onReceiveStateChanged(
                @NonNull BluetoothDevice sink,
                int sourceId,
                @NonNull BluetoothLeBroadcastReceiveState state) {}
    }

    private final class VolumeControlCallback implements BluetoothVolumeControl.Callback {
        @Override
        public void onDeviceVolumeChanged(@NonNull BluetoothDevice device, int volume) {
            mVolumeMap.put(device, volume);
            if (isMediaOnlyDeviceInBroadcast(device)) {
                mSinkChangedListener.onBroadcastSinkChanged();
            }
        }
    }
}
