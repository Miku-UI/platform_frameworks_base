/*
 * Copyright 2018 The Android Open Source Project
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

import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;
import static android.media.RoutingChangeInfo.ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.RoutingChangeInfo;
import android.media.RoutingChangeInfo.EntryPoint;
import android.media.RoutingSessionInfo;
import android.media.SuggestedDeviceInfo;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LeAudioProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LocalMediaManager provide interface to get MediaDevice list and transfer media to MediaDevice.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class LocalMediaManager implements BluetoothCallback {
    private static final String TAG = "LocalMediaManager";
    private static final int MAX_DISCONNECTED_DEVICE_NUM = 5;
    private static final int MIN_DURATION_BETWEEN_SUGGESTION_REQUESTS_MILLIS = 5000;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MediaDeviceState.STATE_CONNECTED,
            MediaDeviceState.STATE_CONNECTING,
            MediaDeviceState.STATE_DISCONNECTED,
            MediaDeviceState.STATE_CONNECTING_FAILED,
            MediaDeviceState.STATE_SELECTED,
            MediaDeviceState.STATE_GROUPING})
    public @interface MediaDeviceState {
        int STATE_CONNECTED = 0;
        int STATE_CONNECTING = 1;
        int STATE_DISCONNECTED = 2;
        int STATE_CONNECTING_FAILED = 3;
        int STATE_SELECTED = 4;
        int STATE_GROUPING = 5;
    }

    private final Collection<DeviceCallback> mCallbacks = new CopyOnWriteArrayList<>();
    private final Object mMediaDevicesLock = new Object();
    @VisibleForTesting
    final MediaDeviceCallback mMediaDeviceCallback = new MediaDeviceCallback();

    private Context mContext;
    private LocalBluetoothManager mLocalBluetoothManager;
    private InfoMediaManager mInfoMediaManager;
    private String mPackageName;
    private MediaDevice mOnTransferBluetoothDevice;
    @VisibleForTesting
    AudioManager mAudioManager;

    @VisibleForTesting
    List<MediaDevice> mMediaDevices = new CopyOnWriteArrayList<>();
    @VisibleForTesting
    List<MediaDevice> mDisconnectedMediaDevices = new CopyOnWriteArrayList<>();
    @VisibleForTesting
    MediaDevice mCurrentConnectedDevice;
    @VisibleForTesting
    DeviceAttributeChangeCallback mDeviceAttributeChangeCallback =
            new DeviceAttributeChangeCallback();

    @GuardedBy("mMediaDevicesLock")
    @Nullable
    ConnectingSuggestedDeviceState mConnectingSuggestedDeviceState;

    @VisibleForTesting Handler mConnectSuggestedDeviceHandler;

    @VisibleForTesting
    BluetoothAdapter mBluetoothAdapter;

    private long mLastSuggestionRequestTime = 0L;

    /**
     * Register to start receiving callbacks for MediaDevice events.
     */
    public void registerCallback(DeviceCallback callback) {
        boolean wasEmpty = mCallbacks.isEmpty();
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
            if (wasEmpty) {
                mInfoMediaManager.registerCallback(mMediaDeviceCallback);
            }
        }
    }

    /**
     * Unregister to stop receiving callbacks for MediaDevice events
     */
    public void unregisterCallback(DeviceCallback callback) {
        if (mCallbacks.remove(callback) && mCallbacks.isEmpty()) {
            mInfoMediaManager.unregisterCallback(mMediaDeviceCallback);
            unRegisterDeviceAttributeChangeCallback();
        }
    }

    /**
     * Creates a LocalMediaManager with references to given managers.
     *
     * It will obtain a {@link LocalBluetoothManager} by calling
     * {@link LocalBluetoothManager#getInstance} and create an {@link InfoMediaManager} passing
     * that bluetooth manager.
     *
     * It will use {@link BluetoothAdapter#getDefaultAdapter()] for setting the bluetooth adapter.
     */
    public LocalMediaManager(Context context, String packageName) {
        mContext = context;
        mPackageName = packageName;
        mLocalBluetoothManager =
                LocalBluetoothManager.getInstance(context, /* onInitCallback= */ null);
        mAudioManager = context.getSystemService(AudioManager.class);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mConnectSuggestedDeviceHandler = new Handler(context.getMainLooper());
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }

        mInfoMediaManager =
                // TODO: b/321969740 - Take the userHandle as a parameter and pass it through. The
                // package name is not sufficient to unambiguously identify an app.
                InfoMediaManager.createInstance(
                        context,
                        packageName,
                        /* userHandle */ null,
                        mLocalBluetoothManager,
                        /* token */ null);
    }

    /**
     * Creates a LocalMediaManager with references to given managers.
     *
     * It will use {@link BluetoothAdapter#getDefaultAdapter()] for setting the bluetooth adapter.
     */
    public LocalMediaManager(Context context, LocalBluetoothManager localBluetoothManager,
            InfoMediaManager infoMediaManager, String packageName) {
        mContext = context;
        mLocalBluetoothManager = localBluetoothManager;
        mInfoMediaManager = infoMediaManager;
        mPackageName = packageName;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = context.getSystemService(AudioManager.class);
        mConnectSuggestedDeviceHandler = new Handler(context.getMainLooper());
    }

    /**
     * Connect the MediaDevice to transfer media
     *
     * @param connectDevice the MediaDevice
     * @return {@code true} if successfully call, otherwise return {@code false}
     * @deprecated Use {@link #connectDevice(MediaDevice, RoutingChangeInfo)} instead.
     */
    @Deprecated
    public boolean connectDevice(MediaDevice connectDevice) {
        return connectDevice(
                connectDevice,
                new RoutingChangeInfo(
                        ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED, /* isSuggested= */ false));
    }

    /**
     * Connect the MediaDevice to transfer media
     *
     * @param connectDevice the MediaDevice
     * @param routingChangeInfo the invocation details of the connect device request.
     * @return {@code true} if successfully call, otherwise return {@code false}
     */
    public boolean connectDevice(
            @NonNull MediaDevice connectDevice, @NonNull RoutingChangeInfo routingChangeInfo) {
        MediaDevice device = getMediaDeviceById(connectDevice.getId());
        if (device == null) {
            Log.w(TAG, "connectDevice() connectDevice not in the list!");
            return false;
        }
        if (device instanceof BluetoothMediaDevice) {
            final CachedBluetoothDevice cachedDevice =
                    ((BluetoothMediaDevice) device).getCachedDevice();
            if (!cachedDevice.isConnected() && !cachedDevice.isBusy()) {
                mOnTransferBluetoothDevice = connectDevice;
                mInfoMediaManager.setDeviceState(device, MediaDeviceState.STATE_CONNECTING);
                cachedDevice.connect();
                return true;
            }
        }

        if (device.equals(mCurrentConnectedDevice)) {
            Log.d(TAG, "connectDevice() this device is already connected! : " + device.getName());
            return false;
        }

        mInfoMediaManager.setDeviceState(device, MediaDeviceState.STATE_CONNECTING);
        mInfoMediaManager.connectToDevice(device, routingChangeInfo);
        return true;
    }

    /**
     * Connects to a suggested device. If the device is not already scanned, a scan will be started
     * to attempt to discover the device.
     *
     * @param suggestion the suggested device to connect to.
     * @param routingChangeInfo the invocation details of the connect device request.
     */
    public void connectSuggestedDevice(
            @NonNull SuggestedDeviceState suggestion,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        synchronized (mMediaDevicesLock) {
            if (mConnectingSuggestedDeviceState != null) {
                return;
            }
            for (MediaDevice device : mMediaDevices) {
                if (suggestion.getSuggestedDeviceInfo().getRouteId().equals(device.getId())) {
                    Log.i(TAG, "Suggestion: device is available, connecting. deviceId = "
                            + device.getId());
                    connectDevice(device, routingChangeInfo);
                    return;
                }
            }
            mConnectingSuggestedDeviceState =
                    new ConnectingSuggestedDeviceState(
                            suggestion, routingChangeInfo.getEntryPoint());
            mConnectingSuggestedDeviceState.tryConnect();
        }
    }

    /** Requests a suggestion from other routers. */
    public void requestDeviceSuggestion() {
        // Debounce multiple requests in a short duration
        long currentRequestTime = System.currentTimeMillis();
        if (currentRequestTime - mLastSuggestionRequestTime
                > MIN_DURATION_BETWEEN_SUGGESTION_REQUESTS_MILLIS) {
            Log.i(TAG, "requesting device suggestion");
            mLastSuggestionRequestTime = currentRequestTime;
            mInfoMediaManager.requestDeviceSuggestion();
        } else {
            Log.d(
                    TAG,
                    "requesting device suggestion throttled, elapsed time: "
                            + (currentRequestTime - mLastSuggestionRequestTime));
        }
    }

    @NonNull
    public List<SuggestedDeviceInfo> getSuggestions() {
        return mInfoMediaManager.getSuggestions();
    }

    void dispatchSelectedDeviceStateChanged(MediaDevice device, @MediaDeviceState int state) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onSelectedDeviceStateChanged(device, state);
        }
    }

    /**
     * Returns if the media session is available for volume control.
     * @return True if this media session is available for colume control, false otherwise.
     */
    public boolean isMediaSessionAvailableForVolumeControl() {
        return mInfoMediaManager.isRoutingSessionAvailableForVolumeControl();
    }

    /**
     * Returns if media app establishes a preferred route listing order.
     *
     * @return True if route list ordering exist and not using system ordering, false otherwise.
     */
    public boolean isPreferenceRouteListingExist() {
        return mInfoMediaManager.preferRouteListingOrdering();
    }

    /**
     * Returns required component name for system to take the user back to the app by launching an
     * intent with the returned {@link ComponentName}, using action {@link #ACTION_TRANSFER_MEDIA},
     * with the extra {@link #EXTRA_ROUTE_ID}.
     */
    @Nullable
    public ComponentName getLinkedItemComponentName() {
        return mInfoMediaManager.getLinkedItemComponentName();
    }

    /**
     * Start scan connected MediaDevice
     */
    public void startScan() {
        mInfoMediaManager.startScan();
    }

    void dispatchDeviceListUpdate() {
        final List<MediaDevice> mediaDevices = new ArrayList<>(mMediaDevices);
        for (DeviceCallback callback : getCallbacks()) {
            callback.onDeviceListUpdate(mediaDevices);
        }
    }

    void dispatchDeviceAttributesChanged() {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onDeviceAttributesChanged();
        }
    }

    void dispatchOnRequestFailed(int reason) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onRequestFailed(reason);
        }
    }

    void dispatchDeviceSuggestionsUpdated(List<SuggestedDeviceInfo> deviceSuggestions) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onDeviceSuggestionsUpdated(deviceSuggestions);
        }
    }

    void dispatchConnectSuggestedDeviceFinished(SuggestedDeviceState state, boolean success) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onConnectSuggestedDeviceFinished(state, success);
        }
    }

    /**
     * Dispatch a change in the about-to-connect device. See
     * {@link DeviceCallback#onAboutToConnectDeviceAdded} for more information.
     */
    public void dispatchAboutToConnectDeviceAdded(
            @NonNull String deviceAddress,
            @NonNull String deviceName,
            @Nullable Drawable deviceIcon) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onAboutToConnectDeviceAdded(deviceAddress, deviceName, deviceIcon);
        }
    }

    /**
     * Dispatch a change in the about-to-connect device. See
     * {@link DeviceCallback#onAboutToConnectDeviceRemoved} for more information.
     */
    public void dispatchAboutToConnectDeviceRemoved() {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onAboutToConnectDeviceRemoved();
        }
    }

    /**
     * Stop scan MediaDevice
     */
    public void stopScan() {
        mInfoMediaManager.stopScan();
    }

    /**
     * Find the MediaDevice through id.
     *
     * @param id the unique id of MediaDevice
     * @return MediaDevice
     */
    public MediaDevice getMediaDeviceById(String id) {
        synchronized (mMediaDevicesLock) {
            for (MediaDevice mediaDevice : mMediaDevices) {
                if (TextUtils.equals(mediaDevice.getId(), id)) {
                    return mediaDevice;
                }
            }
        }
        Log.i(TAG, "getMediaDeviceById() failed to find device with id: " + id);
        return null;
    }

    /**
     * Returns a list of MediaDevice objects.
     *
     * @return a list of media devices
     */
    public List<MediaDevice> getMediaDevices() {
        synchronized (mMediaDevicesLock) {
            return new ArrayList<>(mMediaDevices);
        }
    }

    /**
     * Find the current connected MediaDevice.
     *
     * @return MediaDevice
     */
    @Nullable
    public MediaDevice getCurrentConnectedDevice() {
        return mCurrentConnectedDevice;
    }

    /**
     * Add a MediaDevice to let it play current media.
     *
     * @param device MediaDevice
     * @param routingChangeInfo the invocation details of the media routing change.
     * @return If add device successful return {@code true}, otherwise return {@code false}
     */
    public boolean addDeviceToPlayMedia(MediaDevice device, RoutingChangeInfo routingChangeInfo) {
        mInfoMediaManager.setDeviceState(device, MediaDeviceState.STATE_GROUPING);
        return mInfoMediaManager.addDeviceToPlayMedia(device, routingChangeInfo);
    }

    /**
     * Remove a {@code device} from current media.
     *
     * @param device MediaDevice
     * @param routingChangeInfo the invocation details of the media routing change.
     * @return If device stop successful return {@code true}, otherwise return {@code false}
     */
    public boolean removeDeviceFromPlayMedia(
            MediaDevice device, RoutingChangeInfo routingChangeInfo) {
        mInfoMediaManager.setDeviceState(device, MediaDeviceState.STATE_GROUPING);
        return mInfoMediaManager.removeDeviceFromPlayMedia(device, routingChangeInfo);
    }

    /**
     * Get the current session's release type.
     *
     * @return release type of the current session
     */
    public @RoutingSessionInfo.ReleaseType int getSessionReleaseType() {
        return mInfoMediaManager.getSessionReleaseType();
    }

    /**
     * Release session to stop playing media on MediaDevice.
     */
    public boolean releaseSession() {
        return mInfoMediaManager.releaseSession();
    }

    /**
     * Requests a volume change for a specific media device.
     *
     * This operation is different from {@link #adjustSessionVolume(String, int)}, which changes the
     * volume of the overall session.
     */
    public void adjustDeviceVolume(MediaDevice device, int volume) {
        mInfoMediaManager.adjustDeviceVolume(device, volume);
    }

    /**
     * Adjust the volume of session.
     *
     * @param sessionId the value of media session id
     * @param volume the value of volume
     */
    public void adjustSessionVolume(String sessionId, int volume) {
        RoutingSessionInfo session = mInfoMediaManager.getRoutingSessionById(sessionId);
        if (session != null) {
            mInfoMediaManager.adjustSessionVolume(session, volume);
        } else {
            Log.w(TAG, "adjustSessionVolume: Unable to find session: " + sessionId);
        }
    }

    /**
     * Adjust the volume of session.
     *
     * @param volume the value of volume
     */
    public void adjustSessionVolume(int volume) {
        mInfoMediaManager.adjustSessionVolume(volume);
    }

    /**
     * Gets the maximum volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return  maximum volume of the session, and return -1 if not found.
     */
    public int getSessionVolumeMax() {
        return mInfoMediaManager.getSessionVolumeMax();
    }

    /**
     * Gets the current volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return current volume of the session, and return -1 if not found.
     */
    public int getSessionVolume() {
        return mInfoMediaManager.getSessionVolume();
    }

    /**
     * Gets the user-visible name of the {@link android.media.RoutingSessionInfo}.
     *
     * @return current name of the session, and return {@code null} if not found.
     */
    @Nullable
    public CharSequence getSessionName() {
        return mInfoMediaManager.getSessionName();
    }

    /**
     * Gets the list of remote {@link RoutingSessionInfo routing sessions} known to the system.
     *
     * <p>This list does not include any system routing sessions.
     */
    public List<RoutingSessionInfo> getRemoteRoutingSessions() {
        return mInfoMediaManager.getRemoteSessions();
    }

    /**
     * Gets the current package name.
     *
     * @return current package name
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Returns {@code true} if needed to enable volume seekbar, otherwise returns {@code false}.
     */
    public boolean shouldEnableVolumeSeekBar(RoutingSessionInfo sessionInfo) {
        return mInfoMediaManager.shouldEnableVolumeSeekBar(sessionInfo);
    }

    @VisibleForTesting
    MediaDevice updateCurrentConnectedDevice() {
        MediaDevice connectedDevice = null;
        synchronized (mMediaDevicesLock) {
            for (MediaDevice device : mMediaDevices) {
                if (device instanceof BluetoothMediaDevice) {
                    if (isActiveDevice(((BluetoothMediaDevice) device).getCachedDevice())
                            && device.isConnected()) {
                        return device;
                    }
                } else if (device instanceof PhoneMediaDevice) {
                    connectedDevice = device;
                }
            }
        }

        return connectedDevice;
    }

    private boolean isActiveDevice(CachedBluetoothDevice device) {
        boolean isActiveDeviceA2dp = false;
        boolean isActiveDeviceHearingAid = false;
        boolean isActiveLeAudio = false;
        final A2dpProfile a2dpProfile = mLocalBluetoothManager.getProfileManager().getA2dpProfile();
        if (a2dpProfile != null) {
            isActiveDeviceA2dp = device.getDevice().equals(a2dpProfile.getActiveDevice());
        }
        if (!isActiveDeviceA2dp) {
            final HearingAidProfile hearingAidProfile = mLocalBluetoothManager.getProfileManager()
                    .getHearingAidProfile();
            if (hearingAidProfile != null) {
                isActiveDeviceHearingAid =
                        hearingAidProfile.getActiveDevices().contains(device.getDevice());
            }
        }

        if (!isActiveDeviceA2dp && !isActiveDeviceHearingAid) {
            final LeAudioProfile leAudioProfile = mLocalBluetoothManager.getProfileManager()
                    .getLeAudioProfile();
            if (leAudioProfile != null) {
                isActiveLeAudio = leAudioProfile.getActiveDevices().contains(device.getDevice());
            }
        }

        return isActiveDeviceA2dp || isActiveDeviceHearingAid || isActiveLeAudio;
    }

    private Collection<DeviceCallback> getCallbacks() {
        return new CopyOnWriteArrayList<>(mCallbacks);
    }

    class MediaDeviceCallback implements InfoMediaManager.MediaDeviceCallback {
        @Override
        public void onDeviceListAdded(@NonNull List<MediaDevice> devices) {
            synchronized (mMediaDevicesLock) {
                mMediaDevices.clear();
                mMediaDevices.addAll(devices);
                // Add muting expected bluetooth devices only when phone output device is available.
                for (MediaDevice device : devices) {
                    final int type = device.getDeviceType();
                    if (type == MediaDevice.MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE
                            || type == MediaDevice.MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE
                            || type == MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE) {
                        if (isTv()) {
                            mMediaDevices.addAll(buildDisconnectedBluetoothDevice());
                        } else {
                            MediaDevice mutingExpectedDevice = getMutingExpectedDevice();
                            if (mutingExpectedDevice != null) {
                                Log.d(TAG, "Muting expected device added, id: "
                                        + mutingExpectedDevice.getId());
                                mMediaDevices.add(mutingExpectedDevice);
                            }
                        }
                        break;
                    }
                }
            }

            final MediaDevice infoMediaDevice = mInfoMediaManager.getCurrentConnectedDevice();
            mCurrentConnectedDevice = infoMediaDevice != null
                    ? infoMediaDevice : updateCurrentConnectedDevice();
            dispatchDeviceListUpdate();
            if (mOnTransferBluetoothDevice != null && mOnTransferBluetoothDevice.isConnected()) {
                connectDevice(mOnTransferBluetoothDevice);
                mInfoMediaManager.setDeviceState(
                        mOnTransferBluetoothDevice, MediaDeviceState.STATE_CONNECTED);
                dispatchSelectedDeviceStateChanged(mOnTransferBluetoothDevice,
                        MediaDeviceState.STATE_CONNECTED);
                mOnTransferBluetoothDevice = null;
            }
        }

        private boolean isTv() {
            PackageManager pm = mContext.getPackageManager();
            return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                    || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }

        private MediaDevice getMutingExpectedDevice() {
            if (mBluetoothAdapter == null
                    || mAudioManager.getMutingExpectedDevice() == null) {
                return null;
            }
            final List<BluetoothDevice> bluetoothDevices =
                    mBluetoothAdapter.getMostRecentlyConnectedDevices();
            final CachedBluetoothDeviceManager cachedDeviceManager =
                    mLocalBluetoothManager.getCachedDeviceManager();
            for (BluetoothDevice device : bluetoothDevices) {
                final CachedBluetoothDevice cachedDevice =
                        cachedDeviceManager.findDevice(device);
                if (isBondedMediaDevice(cachedDevice) && isMutingExpectedDevice(cachedDevice)) {
                    return new BluetoothMediaDevice(mContext, cachedDevice, /* info= */
                            null, /* dynamicRouteAttributes= */  null, /* item= */
                            null, /* isMutingExpectedDevice= */ true);
                }
            }
            return null;
        }

        private boolean isMutingExpectedDevice(CachedBluetoothDevice cachedDevice) {
            AudioDeviceAttributes mutingExpectedDevice = mAudioManager.getMutingExpectedDevice();
            if (mutingExpectedDevice == null || cachedDevice == null) {
                return false;
            }
            return cachedDevice.getAddress().equals(mutingExpectedDevice.getAddress());
        }

        private List<MediaDevice> buildDisconnectedBluetoothDevice() {
            if (mBluetoothAdapter == null) {
                Log.w(TAG, "buildDisconnectedBluetoothDevice() BluetoothAdapter is null");
                return new ArrayList<>();
            }

            final List<BluetoothDevice> bluetoothDevices =
                    mBluetoothAdapter.getMostRecentlyConnectedDevices();
            final CachedBluetoothDeviceManager cachedDeviceManager =
                    mLocalBluetoothManager.getCachedDeviceManager();

            final List<CachedBluetoothDevice> cachedBluetoothDeviceList = new ArrayList<>();
            int deviceCount = 0;
            for (BluetoothDevice device : bluetoothDevices) {
                final CachedBluetoothDevice cachedDevice =
                        cachedDeviceManager.findDevice(device);
                if (cachedDevice != null) {
                    if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED
                            && !cachedDevice.isConnected()
                            && isMediaDevice(cachedDevice)) {
                        deviceCount++;
                        cachedBluetoothDeviceList.add(cachedDevice);
                        if (deviceCount >= MAX_DISCONNECTED_DEVICE_NUM) {
                            break;
                        }
                    }
                }
            }

            unRegisterDeviceAttributeChangeCallback();
            mDisconnectedMediaDevices.clear();
            for (CachedBluetoothDevice cachedDevice : cachedBluetoothDeviceList) {
                final MediaDevice mediaDevice =
                        new BluetoothMediaDevice(mContext, cachedDevice, /* info= */
                                null, /* dynamicRouteAttributes= */  null, /* item= */ null);
                if (!mMediaDevices.contains(mediaDevice)) {
                    cachedDevice.registerCallback(mDeviceAttributeChangeCallback);
                    mDisconnectedMediaDevices.add(mediaDevice);
                }
            }
            return new ArrayList<>(mDisconnectedMediaDevices);
        }

        private boolean isBondedMediaDevice(CachedBluetoothDevice cachedDevice) {
            return cachedDevice != null
                    && cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED
                    && !cachedDevice.isConnected()
                    && isMediaDevice(cachedDevice);
        }

        private boolean isMediaDevice(CachedBluetoothDevice device) {
            for (LocalBluetoothProfile profile : device.getUiAccessibleProfiles()) {
                if (profile instanceof A2dpProfile || profile instanceof HearingAidProfile ||
                        profile instanceof LeAudioProfile) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onDeviceListRemoved(@NonNull List<MediaDevice> devices) {
            synchronized (mMediaDevicesLock) {
                mMediaDevices.removeAll(devices);
            }
            dispatchDeviceListUpdate();
        }

        @Override
        public void onConnectedDeviceChanged(String id) {
            MediaDevice connectDevice = getMediaDeviceById(id);
            connectDevice = connectDevice != null
                    ? connectDevice : updateCurrentConnectedDevice();

            mCurrentConnectedDevice = connectDevice;
            if (connectDevice != null) {
                mInfoMediaManager.setDeviceState(connectDevice, MediaDeviceState.STATE_CONNECTED);

                dispatchSelectedDeviceStateChanged(mCurrentConnectedDevice,
                        MediaDeviceState.STATE_CONNECTED);
            }
        }

        @Override
        public void onRequestFailed(int reason) {
            synchronized (mMediaDevicesLock) {
                for (MediaDevice device : mMediaDevices) {
                    if (device.getState() == MediaDeviceState.STATE_CONNECTING) {
                        mInfoMediaManager.setDeviceState(
                                device, MediaDeviceState.STATE_CONNECTING_FAILED);
                    }
                }
            }
            dispatchOnRequestFailed(reason);
        }

        @Override
        public void onDeviceSuggestionsUpdated(
                @NonNull List<SuggestedDeviceInfo> deviceSuggestions) {
            dispatchDeviceSuggestionsUpdated(deviceSuggestions);
        }
    }

    private void unRegisterDeviceAttributeChangeCallback() {
        for (MediaDevice device : mDisconnectedMediaDevices) {
            ((BluetoothMediaDevice) device).getCachedDevice()
                    .unregisterCallback(mDeviceAttributeChangeCallback);
        }
    }

    /**
     * Callback for notifying device information updating
     */
    public interface DeviceCallback {
        /**
         * Callback for notifying device list updated.
         *
         * @param devices MediaDevice list
         */
        default void onDeviceListUpdate(List<MediaDevice> devices) {};

        /**
         * Callback for notifying the connected device is changed.
         *
         * @param device the changed connected MediaDevice
         * @param state the current MediaDevice state, the possible values are:
         * {@link MediaDeviceState#STATE_CONNECTED},
         * {@link MediaDeviceState#STATE_CONNECTING},
         * {@link MediaDeviceState#STATE_DISCONNECTED}
         */
        default void onSelectedDeviceStateChanged(MediaDevice device,
                @MediaDeviceState int state) {};

        /**
         * Callback for notifying the device attributes is changed.
         */
        default void onDeviceAttributesChanged() {};

        /**
         * Callback for notifying that transferring is failed.
         *
         * @param reason the reason that the request has failed. Can be one of followings:
         * {@link android.media.MediaRoute2ProviderService#REASON_UNKNOWN_ERROR},
         * {@link android.media.MediaRoute2ProviderService#REASON_REJECTED},
         * {@link android.media.MediaRoute2ProviderService#REASON_NETWORK_ERROR},
         * {@link android.media.MediaRoute2ProviderService#REASON_ROUTE_NOT_AVAILABLE},
         * {@link android.media.MediaRoute2ProviderService#REASON_INVALID_COMMAND},
         */
        default void onRequestFailed(int reason){};

        /**
         * Callback for notifying that we have a new about-to-connect device.
         *
         * An about-to-connect device is a device that is not yet connected but is expected to
         * connect imminently and should be displayed as the current device in the media player.
         * See [AudioManager.muteAwaitConnection] for more details.
         *
         * The information in the most recent callback should override information from any previous
         * callbacks.
         *
         * @param deviceAddress the address of the device. {@see AudioDeviceAttributes.address}.
         *                      If present, we'll use this address to fetch the full information
         *                      about the device (if we can find that information).
         * @param deviceName the name of the device (displayed to the user). Used as a backup in
         *                   case using deviceAddress doesn't work.
         * @param deviceIcon the icon that should be used with the device. Used as a backup in case
         *                   using deviceAddress doesn't work.
         */
        default void onAboutToConnectDeviceAdded(
                @NonNull String deviceAddress,
                @NonNull String deviceName,
                @Nullable Drawable deviceIcon
        ) {}

        /**
         * Callback for notifying that we no longer have an about-to-connect device.
         */
        default void onAboutToConnectDeviceRemoved() {}

        /** Callback for notifying that the suggested device list has been updated. */
        default void onDeviceSuggestionsUpdated(
                @NonNull List<SuggestedDeviceInfo> deviceSuggestions) {
        }

        /** Callback for notifying that connection to suggested device is finished. */
        default void onConnectSuggestedDeviceFinished(
                @NonNull SuggestedDeviceState suggestedDeviceState, boolean success) {
        }
    }

    /**
     * This callback is for update {@link BluetoothMediaDevice} summary when
     * {@link CachedBluetoothDevice} connection state is changed.
     */
    @VisibleForTesting
    class DeviceAttributeChangeCallback implements CachedBluetoothDevice.Callback {

        @Override
        public void onDeviceAttributesChanged() {
            if (mOnTransferBluetoothDevice != null
                    && !((BluetoothMediaDevice) mOnTransferBluetoothDevice).getCachedDevice()
                    .isBusy()
                    && !mOnTransferBluetoothDevice.isConnected()) {
                // Failed to connect
                mInfoMediaManager.setDeviceState(
                        mOnTransferBluetoothDevice, MediaDeviceState.STATE_CONNECTING_FAILED);
                mOnTransferBluetoothDevice = null;
                dispatchOnRequestFailed(REASON_UNKNOWN_ERROR);
            }
            dispatchDeviceAttributesChanged();
        }
    }

    private class ConnectingSuggestedDeviceState {
        private static final int SCAN_AND_CONNECTION_DURATION_MS = 30000;

        @NonNull final SuggestedDeviceState mSuggestedDeviceState;
        @NonNull final DeviceCallback mDeviceCallback;
        @NonNull final Runnable mConnectionAttemptFinishedRunnable;

        boolean mIsConnectionAttemptActive = false;
        boolean mDidAttemptCompleteSuccessfully = false;

        ConnectingSuggestedDeviceState(
                @NonNull SuggestedDeviceState suggestedDeviceState, @EntryPoint int entryPoint) {
            mSuggestedDeviceState = suggestedDeviceState;
            mDeviceCallback =
                    new DeviceCallback() {
                        @Override
                        public void onDeviceListUpdate(List<MediaDevice> mediaDevices) {
                            synchronized (mMediaDevicesLock) {
                                for (MediaDevice mediaDevice : mediaDevices) {
                                    if (isSuggestedDevice(mediaDevice)) {
                                        Log.i(
                                                TAG,
                                                "Suggestion: scan found matched device, "
                                                        + "connecting. deviceId = "
                                                        + mediaDevice.getId());
                                        connectDevice(
                                                mediaDevice,
                                                new RoutingChangeInfo(
                                                        entryPoint, /* isSuggested= */ true));
                                        mIsConnectionAttemptActive = true;
                                        break;
                                    }
                                }
                            }
                        }

                        @Override
                        public void onSelectedDeviceStateChanged(
                                @NonNull MediaDevice device, @MediaDeviceState int state) {
                            if (isSuggestedDevice(device)
                                    && state == MediaDeviceState.STATE_CONNECTED) {
                                if (!mConnectSuggestedDeviceHandler.hasCallbacks(
                                        mConnectionAttemptFinishedRunnable)) {
                                    return;
                                }
                                mDidAttemptCompleteSuccessfully = true;
                                // Remove the postDelayed runnable previously set and post a new one
                                // to be executed right away.
                                mConnectSuggestedDeviceHandler.removeCallbacks(
                                        mConnectionAttemptFinishedRunnable);
                                mConnectSuggestedDeviceHandler.post(
                                        mConnectionAttemptFinishedRunnable);
                            }
                        }

                        private boolean isSuggestedDevice(MediaDevice device) {
                            return mConnectingSuggestedDeviceState != null
                                    && mConnectingSuggestedDeviceState
                                            .mSuggestedDeviceState
                                            .getSuggestedDeviceInfo()
                                            .getRouteId()
                                            .equals(device.getId());
                        }
                    };
            mConnectionAttemptFinishedRunnable =
                    () -> {
                        synchronized (mMediaDevicesLock) {
                            mConnectingSuggestedDeviceState = null;
                            mIsConnectionAttemptActive = false;
                        }
                        unregisterCallback(mDeviceCallback);
                        stopScan();
                        Log.i(TAG, "Suggestion: scan stopped. success = "
                                + mDidAttemptCompleteSuccessfully);
                        dispatchConnectSuggestedDeviceFinished(mSuggestedDeviceState,
                                mDidAttemptCompleteSuccessfully);
                    };
        }

        void tryConnect() {
            // Attempt connection only if there isn't one already in progress.
            if (mIsConnectionAttemptActive) {
                return;
            }
            Log.i(TAG, "Suggestion: scanning for devices.");
            // Reset mDidAttemptCompleteSuccessfully at the start of each connection attempt.
            mDidAttemptCompleteSuccessfully = false;
            registerCallback(mDeviceCallback);
            startScan();
            mConnectSuggestedDeviceHandler.postDelayed(
                    mConnectionAttemptFinishedRunnable, SCAN_AND_CONNECTION_DURATION_MS);
        }
    }
}
