/*
 * Copyright 2019 The Android Open Source Project
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

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_UNTETHERED_HEADSET;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_WATCH;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_CARKIT;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_HEADPHONES;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_HEARING_AID;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_RECEIVER;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_SPEAKER;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_UNKNOWN;
import static android.media.AudioManager.AUDIO_DEVICE_CATEGORY_WATCH;
import static android.media.audio.Flags.blePeripheralDevices;
import static android.media.audio.Flags.bleHearingAidDevice;

import static com.android.media.audio.Flags.bleHearingAidDeviceImpl;
import static com.android.media.audio.Flags.optimizeBtDeviceSwitch;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothCodecType;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothLeAudioPeripheral;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;
import android.content.Intent;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.AudioManager.AudioDeviceCategory;
import android.media.AudioSystem;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.IntArray;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.server.utils.EventLogger;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
/**
 * @hide
 * Class to encapsulate all communication with Bluetooth services
 */
public class BtHelper {

    private static final String TAG = "AS.BtHelper";

    private final @NonNull AudioDeviceBroker mDeviceBroker;

    private ScoHelper mScoHelper;

    private final boolean mSupportsBleHearingAids;

    // BluetoothHeadset API to control SCO connection
    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothHeadset mBluetoothHeadset;

    // Bluetooth headset device
    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private @Nullable BluetoothDevice mBluetoothHeadsetDevice;

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private final Map<BluetoothDevice, AudioDeviceAttributes> mResolvedScoAudioDevices =
            new HashMap<>();

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothHearingAid mHearingAid = null;

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothLeAudio mLeAudio = null;

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothLeAudioCodecConfig mLeAudioCodecConfig;

    // Reference to BluetoothA2dp to query for AbsoluteVolume.
    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothA2dp mA2dp = null;

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothCodecConfig mA2dpCodecConfig;

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothLeAudioPeripheral mLeAudioPeripheral = null;

    // bitmask of BLE peripheral profile stream types currently active as reported by the
    // BLE peripheral profile proxy
    private int mLeAudioPeripheralStreamTypes = 0;

    static final int LE_PERIPHERAL_INPUT_STREAM_TYPES =
            BluetoothLeAudioPeripheral.STREAM_TYPE_CALL
                    | BluetoothLeAudioPeripheral.STREAM_TYPE_MEDIA
                    | BluetoothLeAudioPeripheral.STREAM_TYPE_GAME
                    |  BluetoothLeAudioPeripheral.STREAM_TYPE_VOICE_ASSISTANT;

    static final int LE_PERIPHERAL_OUTPUT_STREAM_TYPES =
            BluetoothLeAudioPeripheral.STREAM_TYPE_CALL
                    |  BluetoothLeAudioPeripheral.STREAM_TYPE_RECORDING;


    @GuardedBy("BtHelper.this")
    private @AudioSystem.AudioFormatNativeEnumForBtCodec
            int mLeAudioBroadcastCodec = AudioSystem.AUDIO_FORMAT_DEFAULT;

    // If absolute volume is supported in AVRCP device
    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private boolean mAvrcpAbsVolSupported = false;

    @GuardedBy("BtHelper.this")
    private @Nullable BluetoothHapClient mHapClient = null;

    interface ScoHelper {
        boolean isBluetoothScoOn();
        boolean isBluetoothScoRequestedInternally();
        boolean startBluetoothSco(AttributionSource client);
        boolean stopBluetoothSco();
        void onBroadcastScoConnectionState(int state);
        void resetBluetoothSco();

        void dump(PrintWriter pw, String prefix);

        // Internal methods
        void onProfileConnected();
        void onScoAudioStateChanged(int state);
    }

    /*
     * This class is a bit of an inheritance crime: unlike the legacy implementation,
     * start/stop doesn't actually start or stop SCO. Rather, it tracks whether the default
     * communication route is set to a SCO device (for intent broadcasting for legacy
     * clients), and it handles priming SCO for non telecom/BT clients.
     * Most of the other no longer relevant methods just throw.
     */
   public static class AmScoHelper implements ScoHelper {
        interface BluetoothHeadsetProxy {
            boolean startScoUsingVirtualVoiceCall();
            boolean stopScoUsingVirtualVoiceCall();
        }

        private final BluetoothHeadsetProxy mHfp;

        // This state is needed to handle non telecom/bt communication clients.
        // For these clients, we must explicitly prime the SCO stream via the virtual
        // call API.
        // This becomes tricky to manage with transitions. In particular, whenever an
        // actual call is received, the BT stack clears the virtual call state. So,
        // for a virtual -> managed -> virtual transition, we need to restart the virtual
        // call in the latter case.
        // This requires the BT stack holding the virtual call in a pending state.
        enum ScoState {
            OFF,
            ON_VIRTUAL,
            ON,
        }

        private ScoState mState = ScoState.OFF;
        private final Consumer<Intent> mBroadcaster;

        AmScoHelper(BluetoothHeadsetProxy hfp, Consumer<Intent> broadcaster) {
            mHfp = Objects.requireNonNull(hfp);
            mBroadcaster = broadcaster;
        }

        @Override
        public boolean isBluetoothScoOn() {
            throw new UnsupportedOperationException("Does not make sense on this code path");
        }

        @Override
        public boolean isBluetoothScoRequestedInternally() {
            throw new UnsupportedOperationException("Does not make sense on this code path");
        }

        @Override
        public synchronized boolean startBluetoothSco(AttributionSource client) {
            var next = shouldStartVirtualCall(client) ? ScoState.ON_VIRTUAL : ScoState.ON;
            if (next == mState) {
                return true;
            }
            AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent("startBluetoothSco: "
                        + mState + "-> " + next));
            if (next == ScoState.ON_VIRTUAL) {
                // this returns a boolean, but we explicitly ignore it under
                // AMSCO. We'll receive failure via callback
                mHfp.startScoUsingVirtualVoiceCall();
            } else if (mState == ScoState.ON_VIRTUAL) {
                // ON_VIRTUAL -> ON
                mHfp.stopScoUsingVirtualVoiceCall();
            }

            if (mState == ScoState.OFF) {
                mBroadcaster.accept(makeIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING));
                mBroadcaster.accept(makeIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED));
            }
            mState = next;
            return true;
        }

        @Override
        public synchronized boolean stopBluetoothSco() {
            if (mState == ScoState.ON_VIRTUAL) {
                mHfp.stopScoUsingVirtualVoiceCall();
            }
            if (mState != ScoState.OFF) {
                mBroadcaster.accept(makeIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED));
            }
            mState = ScoState.OFF;
            return true;
        }

        @Override
        public void onBroadcastScoConnectionState(int state) {
            throw new UnsupportedOperationException("Does not make sense on this code path");
        }

        @Override
        public void onScoAudioStateChanged(int state) {
            // No-op:
            // We don't react to BT stack SCO state changes, since those changes are downstream of
            // us in AMSCO
        }

        @Override
        public void onProfileConnected() {
            // No-op:
            // 1. We don't need to sync internal/external SCO state w/ a profile as it comes
            // up.
            // 2. We expect the profile to be connected in the case we have an active device
            // set: When a profile is (dis)connected, the BT stack will notify us via
            // onSetBtScoActiveDevice that the active device is updated
        }
        @Override
        public synchronized void resetBluetoothSco() {
            if (mState == ScoState.ON_VIRTUAL) {
                mHfp.stopScoUsingVirtualVoiceCall();
            }
            if (mState != ScoState.OFF) {
                mBroadcaster.accept(makeIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED));
            }
            mState = ScoState.OFF;
        }

        @Override
        public synchronized void dump(PrintWriter pw, String prefix) {
            pw.println(prefix + "mScoAudioState: " + mState);
        }

        private static Intent makeIntent(int to) {
            int from = switch (to) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTING ->
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                case AudioManager.SCO_AUDIO_STATE_CONNECTED ->
                    AudioManager.SCO_AUDIO_STATE_CONNECTING;
                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED ->
                    AudioManager.SCO_AUDIO_STATE_CONNECTED;
                default -> throw new IllegalArgumentException();
            };
            var i = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            i.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, to);
            i.putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, from);
            return i;
        }
    }

    public class LegacyScoHelper implements ScoHelper {
        // Current connection state indicated by bluetooth headset
        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        private int mScoConnectionState = android.media.AudioManager.SCO_AUDIO_STATE_ERROR;

        // Indicate if SCO audio connection is currently active and if the initiator is
        // audio service (internal) or bluetooth headset (external)
        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        private int mScoAudioState;

        // SCO audio state is not active
        private static final int SCO_STATE_INACTIVE = 0;
        // SCO audio activation request waiting for headset service to connect
        private static final int SCO_STATE_ACTIVATE_REQ = 1;
        // SCO audio state is active due to an action in BT handsfree (either voice recognition or
        // in call audio)
        private static final int SCO_STATE_ACTIVE_EXTERNAL = 2;
        // SCO audio state is active or starting due to a request from AudioManager API
        private static final int SCO_STATE_ACTIVE_INTERNAL = 3;
        // SCO audio deactivation request waiting for headset service to connect
        private static final int SCO_STATE_DEACTIVATE_REQ = 4;
        // SCO audio deactivation in progress, waiting for Bluetooth audio intent
        private static final int SCO_STATE_DEACTIVATING = 5;

        /**
         * Returns a string representation of the scoAudioState.
         */
        public static String scoAudioStateToString(int scoAudioState) {
            switch (scoAudioState) {
                case SCO_STATE_INACTIVE:
                    return "SCO_STATE_INACTIVE";
                case SCO_STATE_ACTIVATE_REQ:
                    return "SCO_STATE_ACTIVATE_REQ";
                case SCO_STATE_ACTIVE_EXTERNAL:
                    return "SCO_STATE_ACTIVE_EXTERNAL";
                case SCO_STATE_ACTIVE_INTERNAL:
                    return "SCO_STATE_ACTIVE_INTERNAL";
                case SCO_STATE_DEACTIVATING:
                    return "SCO_STATE_DEACTIVATING";
                default:
                    return "SCO_STATE_(" + scoAudioState + ")";
            }
        }

        @Override
        public void dump(PrintWriter pw, String prefix) {
            pw.println(prefix + "mScoAudioState: " + scoAudioStateToString(mScoAudioState));
            pw.println(prefix + "mSupportsBleHearingAids: " + mSupportsBleHearingAids);
        }

        /**
         *
         * @return false if SCO isn't connected
         */
        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public synchronized boolean isBluetoothScoOn() {
            if (mBluetoothHeadset == null || mBluetoothHeadsetDevice == null) {
                return false;
            }
            try {
                return mBluetoothHeadset.getAudioState(mBluetoothHeadsetDevice)
                        == BluetoothHeadset.STATE_AUDIO_CONNECTED;
            } catch (Exception e) {
                Log.e(TAG, "Exception while getting audio state of " + mBluetoothHeadsetDevice, e);
            }
            return false;
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public boolean isBluetoothScoRequestedInternally() {
            return mScoAudioState == SCO_STATE_ACTIVE_INTERNAL
                  || mScoAudioState == SCO_STATE_ACTIVATE_REQ;
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public synchronized boolean startBluetoothSco(AttributionSource client) {
            return requestScoState(BluetoothHeadset.STATE_AUDIO_CONNECTED);
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public synchronized boolean stopBluetoothSco() {
            return requestScoState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        public void onBroadcastScoConnectionState(int state) {
            if (state == mScoConnectionState) {
                return;
            }
            Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state);
            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                    mScoConnectionState);
            sendStickyBroadcastToAll(newIntent);
            mScoConnectionState = state;
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public void resetBluetoothSco() {
            mScoAudioState = SCO_STATE_INACTIVE;
            broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            mDeviceBroker.clearA2dpSuspended(false /* internalOnly */);
            mDeviceBroker.clearLeAudioSuspended(false /* internalOnly */);
            mDeviceBroker.setBluetoothScoOn(false, "resetBluetoothSco");
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        private synchronized boolean requestScoState(int state) {
            checkScoAudioState();
            if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                // Make sure that the state transitions to CONNECTING even if we cannot initiate
                // the connection except if already connected internally
                if (mScoAudioState != SCO_STATE_ACTIVE_INTERNAL) {
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTING);
                }
                switch (mScoAudioState) {
                    case SCO_STATE_INACTIVE:
                        if (mBluetoothHeadset == null) {
                            if (getBluetoothHeadset()) {
                                mScoAudioState = SCO_STATE_ACTIVATE_REQ;
                            } else {
                                Log.w(TAG, "requestScoState: getBluetoothHeadset failed during"
                                        + " connection");
                                broadcastScoConnectionState(
                                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                                return false;
                            }
                            break;
                        }
                        if (mBluetoothHeadsetDevice == null) {
                            Log.w(TAG, "requestScoState: no active device while connecting");
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            return false;
                        }
                        if (mBluetoothHeadset.startScoUsingVirtualVoiceCall()) {
                            mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                        } else {
                            Log.w(TAG, "requestScoState: connect to "
                                    + getAnonymizedAddress(mBluetoothHeadsetDevice)
                                    + " failed");
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            return false;
                        }
                        break;
                    case SCO_STATE_DEACTIVATING:
                        mScoAudioState = SCO_STATE_ACTIVATE_REQ;
                        break;
                    case SCO_STATE_DEACTIVATE_REQ:
                        mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTED);
                        break;
                    case SCO_STATE_ACTIVE_INTERNAL:
                        // Already in ACTIVE mode, simply return
                        break;
                    case SCO_STATE_ACTIVE_EXTERNAL:
                        /* Confirm SCO Audio connection to requesting app as it is already connected
                         * externally (i.e. through SCO APIs by Telecom service). Once SCO Audio is
                         * disconnected by the external owner, we will reconnect it automatically on
                         * behalf of the requesting app and the state will move to
                         * SCO_STATE_ACTIVE_INTERNAL.
                         */
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTED);
                        break;
                    default:
                        Log.w(TAG, "requestScoState: failed to connect in state " + mScoAudioState);
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        return false;
                }
            } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                switch (mScoAudioState) {
                    case SCO_STATE_ACTIVE_INTERNAL:
                        if (mBluetoothHeadset == null) {
                            if (getBluetoothHeadset()) {
                                mScoAudioState = SCO_STATE_DEACTIVATE_REQ;
                            } else {
                                Log.w(TAG, "requestScoState: getBluetoothHeadset failed during"
                                        + " disconnection");
                                mScoAudioState = SCO_STATE_INACTIVE;
                                broadcastScoConnectionState(
                                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                                return false;
                            }
                            break;
                        }
                        if (mBluetoothHeadsetDevice == null) {
                            mScoAudioState = SCO_STATE_INACTIVE;
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            break;
                        }
                        if (mBluetoothHeadset.stopScoUsingVirtualVoiceCall()) {
                            mScoAudioState = SCO_STATE_DEACTIVATING;
                        } else {
                            mScoAudioState = SCO_STATE_INACTIVE;
                            broadcastScoConnectionState(
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        }
                        break;
                    case SCO_STATE_ACTIVATE_REQ:
                        mScoAudioState = SCO_STATE_INACTIVE;
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        break;
                    default:
                        Log.w(TAG, "requestScoState: failed to disconnect in state "
                                + mScoAudioState);
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        return false;
                }
            }
            return true;
        }

        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        private synchronized void checkScoAudioState() {
            try {
                if (mBluetoothHeadset != null
                        && mBluetoothHeadsetDevice != null
                        && mScoAudioState == SCO_STATE_INACTIVE
                        && mBluetoothHeadset.getAudioState(mBluetoothHeadsetDevice)
                            != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while getting audio state of " + mBluetoothHeadsetDevice, e);
            }
        }

        public boolean getBluetoothHeadset() {
            boolean result = false;
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                result = adapter.getProfileProxy(mDeviceBroker.getContext(),
                        mBluetoothProfileServiceListener, BluetoothProfile.HEADSET);
            }
            // If we could not get a bluetooth headset proxy, send a failure message
            // without delay to reset the SCO audio state and clear SCO clients.
            // If we could get a proxy, send a delayed failure message that will reset our state
            // in case we don't receive onServiceConnected().
            mDeviceBroker.handleFailureToConnectToBtHeadsetService(
                    result ? AudioDeviceBroker.BT_HEADSET_CNCT_TIMEOUT_MS : 0);
            return result;
        }

        @Override
        public void onProfileConnected() {
            // Refresh SCO audio state
            checkScoAudioState();
            if (mScoAudioState != SCO_STATE_ACTIVATE_REQ
                    && mScoAudioState != SCO_STATE_DEACTIVATE_REQ) {
                return;
            }
            boolean status = false;
            if (mBluetoothHeadsetDevice != null) {
                switch (mScoAudioState) {
                    case SCO_STATE_ACTIVATE_REQ:
                        status = mBluetoothHeadset.startScoUsingVirtualVoiceCall();
                        if (status) {
                            mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                        }
                        break;
                    case SCO_STATE_DEACTIVATE_REQ:
                        status = mBluetoothHeadset.stopScoUsingVirtualVoiceCall();
                        if (status) {
                            mScoAudioState = SCO_STATE_DEACTIVATING;
                        }
                        break;
                }
            }
            if (!status) {
                mScoAudioState = SCO_STATE_INACTIVE;
                broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            }
        }
        /**
         * Exclusively called from AudioDeviceBroker (with mDeviceStateLock held)
         * when handling MSG_L_RECEIVED_BT_EVENT in {@link #onReceiveBtEvent(Intent)}
         * as part of the serialization of the communication route selection
         */
        @GuardedBy("mDeviceBroker.mDeviceStateLock")
        @Override
        public synchronized void onScoAudioStateChanged(int state) {
            boolean broadcast = false;
            int scoAudioState = AudioManager.SCO_AUDIO_STATE_ERROR;
            Log.i(TAG, "onScoAudioStateChanged  state: " + state
                    + ", mScoAudioState: " + mScoAudioState);
            switch (state) {
                case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                    scoAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTED;
                    if (mScoAudioState != SCO_STATE_ACTIVE_INTERNAL
                            && mScoAudioState != SCO_STATE_DEACTIVATE_REQ) {
                        mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
                    } else if (mDeviceBroker.isBluetoothScoRequested()) {
                        // broadcast intent if the connection was initated by AudioService
                        broadcast = true;
                    }
                    mDeviceBroker.setBluetoothScoOn(
                            true, "BtHelper.onScoAudioStateChanged, state: " + state);
                    break;
                case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                    mDeviceBroker.setBluetoothScoOn(
                            false, "BtHelper.onScoAudioStateChanged, state: " + state);
                    scoAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                    // There are two cases where we want to immediately reconnect audio:
                    // 1) If a new start request was received while disconnecting: this was
                    // notified by requestScoState() setting state to SCO_STATE_ACTIVATE_REQ.
                    // 2) If audio was connected then disconnected via Bluetooth APIs and
                    // we still have pending activation requests by apps: this is indicated by
                    // state SCO_STATE_ACTIVE_EXTERNAL and BT SCO is requested.
                    if (mScoAudioState == SCO_STATE_ACTIVATE_REQ) {
                        if (mBluetoothHeadset != null && mBluetoothHeadsetDevice != null
                                && mBluetoothHeadset.startScoUsingVirtualVoiceCall()) {
                            mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                            scoAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTING;
                            broadcast = true;
                            break;
                        }
                    }
                    if (mScoAudioState != SCO_STATE_ACTIVE_EXTERNAL) {
                        broadcast = true;
                    }
                    mScoAudioState = SCO_STATE_INACTIVE;
                    break;
                case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                    if (mScoAudioState != SCO_STATE_ACTIVE_INTERNAL
                            && mScoAudioState != SCO_STATE_DEACTIVATE_REQ) {
                        mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
                    }
                    break;
                default:
                    break;
            }
            if (broadcast) {
                Log.i(TAG, "onScoAudioStateChanged  broadcasting state: " + scoAudioState);
                broadcastScoConnectionState(scoAudioState);
            }
        }
    }

    private static final int BT_HEARING_AID_GAIN_MIN = -128;
    private static final int BT_LE_AUDIO_MAX_VOL = 255;

    // BtDevice constants currently rolling out under flag protection. Use own
    // constants instead to avoid mainline dependency from flag library import
    // TODO(b/335936458): remove once the BtDevice flag is rolled out
    private static final String DEVICE_TYPE_SPEAKER = "Speaker";
    private static final String DEVICE_TYPE_HEADSET = "Headset";
    private static final String DEVICE_TYPE_CARKIT = "Carkit";
    private static final String DEVICE_TYPE_HEARING_AID = "HearingAid";

    // A2DP device events
    /*package*/ static final int EVENT_DEVICE_CONFIG_CHANGE = 0;

    /*package*/ static String deviceEventToString(int event) {
        switch (event) {
            case EVENT_DEVICE_CONFIG_CHANGE: return "DEVICE_CONFIG_CHANGE";
            default:
                return new String("invalid event:" + event);
        }
    }

    /*package*/ @NonNull static String getName(@NonNull BluetoothDevice device) {
        final String deviceName = device.getName();
        if (deviceName == null) {
            return "";
        }
        return deviceName;
    }

    BtHelper(@NonNull AudioDeviceBroker broker) {
        mDeviceBroker = broker;
        if (!mDeviceBroker.isScoManagedByAudio()) {
            mScoHelper = new LegacyScoHelper();
        }
        // BLE hearing aid devices will be identified as such only if the flag is set AND the
        // vendor audio HAL implementation supports BLE hearing devices: this allows supporting
        // BLE hearing aids as regular BLE headsets on older implementations.
        if (bleHearingAidDevice()) {
            // TODO(b/370812132) - Framework support for the new BLE hearing aid type is temporarily
            // disabled pending soak/verification and client adoption.
            if (bleHearingAidDeviceImpl()) {
                IntArray deviceTypes = new IntArray();
                if (AudioSystem.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS,
                        deviceTypes) == AudioSystem.SUCCESS) {
                    mSupportsBleHearingAids =
                            deviceTypes.contains(AudioSystem.DEVICE_OUT_BLE_HEARING_AID);
                    return;
                }
            }
        }
        mSupportsBleHearingAids = false;
    }

    //----------------------------------------------------------------------
    // Interface for AudioDeviceBroker

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void onSystemReady() {
        if (mScoHelper != null) {
            mScoHelper.resetBluetoothSco();
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.HEADSET);
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.A2DP);
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.A2DP_SINK);
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.HEARING_AID);
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.LE_AUDIO);
            adapter.getProfileProxy(mDeviceBroker.getContext(),
                    mBluetoothProfileServiceListener, BluetoothProfile.LE_AUDIO_BROADCAST);
            if (blePeripheralDevices()) {
                adapter.getProfileProxy(mDeviceBroker.getContext(),
                        mBluetoothProfileServiceListener, BluetoothProfile.LE_AUDIO_PERIPHERAL);
            }
            if (mSupportsBleHearingAids) {
                adapter.getProfileProxy(mDeviceBroker.getContext(),
                        mBluetoothProfileServiceListener, BluetoothProfile.HAP_CLIENT);
            }
        }
    }

    /*package*/ boolean isBluetoothScoOn() {
        if (mScoHelper != null) {
            return mScoHelper.isBluetoothScoOn();
        } else {
            return false;
        }
    }
    /*package*/ boolean isBluetoothScoRequestedInternally() {
        if (mScoHelper != null) {
            return mScoHelper.isBluetoothScoRequestedInternally();
        } else {
            return false;
        }
    }

    /*package*/ boolean startBluetoothSco(@NonNull String eventSource, AttributionSource client) {
        AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                    "startBluetoothSco: " + eventSource));
        if (mScoHelper != null) {
            return mScoHelper.startBluetoothSco(client);
        } else {
            return false;
        }
    }

    /*package*/ boolean stopBluetoothSco(@NonNull String eventSource) {
        AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                    "stopBluetoothSco: " + eventSource));
        if (mScoHelper != null) {
            return mScoHelper.stopBluetoothSco();
        } else {
            return false;
        }
    }

    /*package*/ void onBroadcastScoConnectionState(int state) {
        if (mScoHelper != null) {
            mScoHelper.onBroadcastScoConnectionState(state);
        }
    }

    /*package*/ void resetBluetoothSco() {
        if (mScoHelper != null) {
            mScoHelper.resetBluetoothSco();
        }
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ void setAvrcpAbsoluteVolumeSupported(boolean supported) {
        mAvrcpAbsVolSupported = supported;
        Log.i(TAG, "setAvrcpAbsoluteVolumeSupported supported=" + supported);
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void setAvrcpAbsoluteVolumeIndex(int index) {
        if (mA2dp == null) {
            if (AudioService.DEBUG_VOL) {
                AudioService.sVolumeLogger.enqueue(new EventLogger.StringEvent(
                        "setAvrcpAbsoluteVolumeIndex: bailing due to null mA2dp").printLog(TAG));
            }
            return;
        }
        if (!mAvrcpAbsVolSupported) {
            AudioService.sVolumeLogger.enqueue(new EventLogger.StringEvent(
                    "setAvrcpAbsoluteVolumeIndex: abs vol not supported ").printLog(TAG));
            return;
        }
        if (AudioService.DEBUG_VOL) {
            Log.i(TAG, "setAvrcpAbsoluteVolumeIndex index=" + index);
        }
        AudioService.sVolumeLogger.enqueue(new AudioServiceEvents.VolumeEvent(
                AudioServiceEvents.VolumeEvent.VOL_SET_AVRCP_VOL, index));
        try {
            mA2dp.setAvrcpAbsoluteVolume(index);
        } catch (Exception e) {
            Log.e(TAG, "Exception while changing abs volume", e);
        }
    }

    private synchronized Pair<Integer, Boolean> getCodec(
            @NonNull BluetoothDevice device, @AudioService.BtProfile int profile) {

        switch (profile) {
            case BluetoothProfile.A2DP: {
                boolean changed = mA2dpCodecConfig != null;
                if (mA2dp == null) {
                    mA2dpCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                BluetoothCodecStatus btCodecStatus = null;
                try {
                    btCodecStatus = mA2dp.getCodecStatus(device);
                } catch (Exception e) {
                    Log.e(TAG, "Exception while getting status of " + device, e);
                }
                if (btCodecStatus == null) {
                    Log.e(TAG, "getCodec, null A2DP codec status for device: " + device);
                    mA2dpCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                final BluetoothCodecConfig btCodecConfig = btCodecStatus.getCodecConfig();
                if (btCodecConfig == null) {
                    mA2dpCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                final BluetoothCodecType btCodecType = btCodecConfig.getExtendedCodecType();
                if (btCodecType == null) {
                    mA2dpCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                changed = !btCodecConfig.equals(mA2dpCodecConfig);
                mA2dpCodecConfig = btCodecConfig;
                return new Pair<>(
                        AudioSystem.bluetoothA2dpCodecToAudioFormat(btCodecType.getCodecId()),
                        changed);
            }
            case BluetoothProfile.LE_AUDIO: {
                boolean changed = mLeAudioCodecConfig != null;
                if (mLeAudio == null) {
                    mLeAudioCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                BluetoothLeAudioCodecStatus btLeCodecStatus = null;
                int groupId = mLeAudio.getGroupId(device);
                try {
                    btLeCodecStatus = mLeAudio.getCodecStatus(groupId);
                } catch (Exception e) {
                    Log.e(TAG, "Exception while getting status of " + device, e);
                }
                if (btLeCodecStatus == null) {
                    Log.e(TAG, "getCodec, null LE codec status for device: " + device);
                    mLeAudioCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                BluetoothLeAudioCodecConfig btLeCodecConfig =
                        btLeCodecStatus.getOutputCodecConfig();
                if (btLeCodecConfig == null) {
                    mLeAudioCodecConfig = null;
                    return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, changed);
                }
                changed = !btLeCodecConfig.equals(mLeAudioCodecConfig);
                mLeAudioCodecConfig = btLeCodecConfig;
                return new Pair<>(AudioSystem.bluetoothLeCodecToAudioFormat(
                        btLeCodecConfig.getCodecType()), changed);
            }
            case BluetoothProfile.LE_AUDIO_BROADCAST: {
                // We assume LC3 for LE Audio broadcast codec as there is no API to get the codec
                // config on LE Broadcast profile proxy.
                boolean changed = mLeAudioBroadcastCodec != AudioSystem.AUDIO_FORMAT_LC3;
                mLeAudioBroadcastCodec = AudioSystem.AUDIO_FORMAT_LC3;
                return new Pair<>(mLeAudioBroadcastCodec, changed);
            }
            default:
                if (blePeripheralDevices() && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    if (mLeAudioPeripheral == null) {
                        return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, false);
                    }

                    // Use LC3 for now as the codec config cannot be retrieved from the profile
                    return new Pair<>(AudioSystem.bluetoothLeCodecToAudioFormat(
                            BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3), false);

                }
                return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, false);
        }
    }

    /*package*/ synchronized Pair<Integer, Boolean>
                    getCodecWithFallback(@NonNull BluetoothDevice device,
                                         @AudioService.BtProfile int profile,
                                         boolean isLeOutput, @NonNull String source) {
        // For profiles other than A2DP and LE Audio output, the audio codec format must be
        // AUDIO_FORMAT_DEFAULT as native audio policy manager expects a specific audio format
        // only if audio HW module selection based on format is supported for the device type.
        if (!(profile == BluetoothProfile.A2DP
                || (isLeOutput && ((profile == BluetoothProfile.LE_AUDIO)
                        || (profile == BluetoothProfile.LE_AUDIO_BROADCAST))))) {
            return new Pair<>(AudioSystem.AUDIO_FORMAT_DEFAULT, false);
        }
        Pair<Integer, Boolean> codecAndChanged =
                getCodec(device, profile);
        if (codecAndChanged.first == AudioSystem.AUDIO_FORMAT_DEFAULT) {
            AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                    "getCodec DEFAULT from " + source + " fallback to "
                            + (profile == BluetoothProfile.A2DP ? "SBC" : "LC3")));
            return new Pair<>(profile == BluetoothProfile.A2DP
                    ? AudioSystem.AUDIO_FORMAT_SBC : AudioSystem.AUDIO_FORMAT_LC3, true);
        }

        return codecAndChanged;
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void onReceiveBtEvent(Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED)) {
            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                    android.bluetooth.BluetoothDevice.class);
            if (btDevice != null && !isProfileProxyConnected(BluetoothProfile.HEADSET)) {
                AudioService.sDeviceLogger.enqueue((new EventLogger.StringEvent(
                        "onReceiveBtEvent ACTION_ACTIVE_DEVICE_CHANGED "
                                + "received with null profile proxy for device: "
                                + btDevice)).printLog(TAG));
                return;

            }
            boolean deviceSwitch = optimizeBtDeviceSwitch()
                    && btDevice != null && mBluetoothHeadsetDevice != null;
            mDeviceBroker.onSetBtScoActiveDevice(btDevice, deviceSwitch);
        } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
            int btState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if (mScoHelper != null) {
                mScoHelper.onScoAudioStateChanged(btState);
            }
        }
    }


    /*package*/ synchronized void setLeAudioVolume(int index, int maxIndex, int streamType) {
        if (mLeAudio == null) {
            if (AudioService.DEBUG_VOL) {
                Log.i(TAG, "setLeAudioVolume: null mLeAudio");
            }
            return;
        }
        /* leaudio expect volume value in range 0 to 255 */
        int volume = (int) Math.round((double) index * BT_LE_AUDIO_MAX_VOL / maxIndex);

        if (AudioService.DEBUG_VOL) {
            Log.i(TAG, "setLeAudioVolume: calling mLeAudio.setVolume idx="
                    + index + " volume=" + volume);
        }
        AudioService.sVolumeLogger.enqueue(new AudioServiceEvents.VolumeEvent(
                AudioServiceEvents.VolumeEvent.VOL_SET_LE_AUDIO_VOL, streamType, index,
                maxIndex, /*caller=*/null));
        try {
            mLeAudio.setVolume(volume);
        } catch (Exception e) {
            Log.e(TAG, "Exception while setting LE volume", e);
        }
    }

    /*package*/ synchronized void setHearingAidVolume(int index, int streamType,
            boolean isHeadAidConnected) {
        if (mHearingAid == null) {
            if (AudioService.DEBUG_VOL) {
                Log.i(TAG, "setHearingAidVolume: null mHearingAid");
            }
            return;
        }
        //hearing aid expect volume value in range -128dB to 0dB
        int gainDB = (int) AudioSystem.getStreamVolumeDB(streamType, index / 10,
                AudioSystem.DEVICE_OUT_HEARING_AID);
        if (gainDB < BT_HEARING_AID_GAIN_MIN) {
            gainDB = BT_HEARING_AID_GAIN_MIN;
        }
        if (AudioService.DEBUG_VOL) {
            Log.i(TAG, "setHearingAidVolume: calling mHearingAid.setVolume idx="
                    + index + " gain=" + gainDB);
        }
        // do not log when hearing aid is not connected to avoid confusion when reading dumpsys
        if (isHeadAidConnected) {
            AudioService.sVolumeLogger.enqueue(new AudioServiceEvents.VolumeEvent(
                    AudioServiceEvents.VolumeEvent.VOL_SET_HEARING_AID_VOL, index, gainDB));
        }
        try {
            mHearingAid.setVolume(gainDB);
        } catch (Exception e) {
            Log.i(TAG, "Exception while setting hearing aid volume", e);
        }
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void onBtProfileDisconnected(int profile) {
        AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                "BT profile " + BluetoothProfile.getProfileName(profile)
                + " disconnected").printLog(TAG));
        switch (profile) {
            case BluetoothProfile.HEADSET:
                mBluetoothHeadset = null;
                if (mDeviceBroker.isScoManagedByAudio()) {
                    mScoHelper = null;
                }
                break;
            case BluetoothProfile.A2DP:
                mA2dp = null;
                mA2dpCodecConfig = null;
                break;
            case BluetoothProfile.HEARING_AID:
                mHearingAid = null;
                break;
            case BluetoothProfile.LE_AUDIO:
                if (mLeAudio != null && mLeAudioCallback != null) {
                    try {
                        mLeAudio.unregisterCallback(mLeAudioCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception while unregistering callback for LE audio", e);
                    }
                }
                mLeAudio = null;
                mLeAudioCallback = null;
                mLeAudioCodecConfig = null;
                break;
            case BluetoothProfile.LE_AUDIO_BROADCAST:
                mLeAudioBroadcastCodec = AudioSystem.AUDIO_FORMAT_DEFAULT;
                break;
            case BluetoothProfile.A2DP_SINK:
                // nothing to do in BtHelper
                break;
            case BluetoothProfile.HAP_CLIENT:
                if (mSupportsBleHearingAids) {
                    mHapClient = null;
                }
            default:
                if (blePeripheralDevices() && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    if (mLeAudioPeripheral != null && mLeAudioPeripheralCallback != null) {
                        try {
                            mLeAudioPeripheral.unregisterCallback(mLeAudioPeripheralCallback);
                        } catch (Exception e) {
                            Log.e(TAG, "Unregistering LE audio peripheral callback failed: ", e);
                        }
                    }
                    mLeAudioPeripheral = null;
                    mLeAudioPeripheralCallback = null;
                    mLeAudioPeripheralStreamTypes = 0;
                    break;
                }
                // Not a valid profile to disconnect
                Log.e(TAG, "onBtProfileDisconnected: Not a valid profile to disconnect "
                        + BluetoothProfile.getProfileName(profile));
                break;
        }
    }

    // BluetoothLeAudio callback used to update the list of addresses in the same group as a
    // connected LE Audio device
    class MyLeAudioCallback implements BluetoothLeAudio.Callback {
        @Override
        public void onCodecConfigChanged(int groupId,
                                  @NonNull BluetoothLeAudioCodecStatus status) {
            // Do nothing
        }

        @Override
        public void onGroupNodeAdded(@NonNull BluetoothDevice device, int groupId) {
            mDeviceBroker.postUpdateLeAudioGroupAddresses(groupId);
        }

        @Override
        public void onGroupNodeRemoved(@NonNull BluetoothDevice device, int groupId) {
            mDeviceBroker.postUpdateLeAudioGroupAddresses(groupId);
        }
        @Override
        public void onGroupStatusChanged(int groupId, int groupStatus) {
            mDeviceBroker.postUpdateLeAudioGroupAddresses(groupId);
        }
    }

    @GuardedBy("BtHelper.this")
    MyLeAudioCallback mLeAudioCallback = null;

    // BluetoothLeAudioPeripheral callback used to update the list of active use cases on
    // an active LE Audio peripheral link.
    class MyLeAudioPeripheralCallback implements BluetoothLeAudioPeripheral.Callback {
        @Override
        public void onStreamTypesChanged(
                @NonNull BluetoothDevice device, int streamTypes) {
            mLeAudioPeripheralStreamTypes = streamTypes;
            //TODO b/423053144: see if anything needs to be done when the active stream
            // types change
        }
    }

    MyLeAudioPeripheralCallback mLeAudioPeripheralCallback = null;

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package*/ synchronized void onBtProfileConnected(int profile, BluetoothProfile proxy) {
        AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                "BT profile " + BluetoothProfile.getProfileName(profile) + " connected to proxy "
                + proxy).printLog(TAG));
        if (proxy == null) {
            Log.e(TAG, "onBtProfileConnected: null proxy for profile: " + profile);
            return;
        }
        switch (profile) {
            case BluetoothProfile.HEADSET:
                onHeadsetProfileConnected((BluetoothHeadset) proxy);
                return;
            case BluetoothProfile.A2DP:
                if (((BluetoothA2dp) proxy).equals(mA2dp)) {
                    return;
                }
                mA2dp = (BluetoothA2dp) proxy;
                break;
            case BluetoothProfile.HEARING_AID:
                if (((BluetoothHearingAid) proxy).equals(mHearingAid)) {
                    return;
                }
                mHearingAid = (BluetoothHearingAid) proxy;
                break;
            case BluetoothProfile.LE_AUDIO:
                if (((BluetoothLeAudio) proxy).equals(mLeAudio)) {
                    return;
                }
                if (mLeAudio != null && mLeAudioCallback != null) {
                    try {
                        mLeAudio.unregisterCallback(mLeAudioCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception while unregistering callback for LE audio", e);
                    }
                }
                mLeAudio = (BluetoothLeAudio) proxy;
                mLeAudioCallback = new MyLeAudioCallback();
                try{
                    mLeAudio.registerCallback(
                            mDeviceBroker.getContext().getMainExecutor(), mLeAudioCallback);
                } catch (Exception e) {
                    mLeAudioCallback = null;
                    Log.e(TAG, "Exception while registering callback for LE audio", e);
                }
                break;
            case BluetoothProfile.A2DP_SINK:
            case BluetoothProfile.LE_AUDIO_BROADCAST:
                // nothing to do in BtHelper
                return;
            case BluetoothProfile.HAP_CLIENT:
                if (mSupportsBleHearingAids) {
                    if (((BluetoothHapClient) proxy).equals(mHapClient)) {
                        return;
                    }
                    mHapClient = (BluetoothHapClient) proxy;
                }
            default:
                if (blePeripheralDevices() && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    if (((BluetoothLeAudioPeripheral) proxy).equals(mLeAudioPeripheral)) {
                        return;
                    }
                    mLeAudioPeripheral = (BluetoothLeAudioPeripheral) proxy;
                    mLeAudioPeripheralCallback = new MyLeAudioPeripheralCallback();
                    try {
                        mLeAudioPeripheral.registerCallback(
                                mDeviceBroker.getContext().getMainExecutor(),
                                mLeAudioPeripheralCallback);
                    } catch (Exception e) {
                        mLeAudioPeripheralCallback = null;
                        Log.e(TAG, "Exception while registering callback for LE audio", e);
                    }
                    break;
                }
                // Not a valid profile to connect
                Log.e(TAG, "onBtProfileConnected: Not a valid profile to connect "
                        + BluetoothProfile.getProfileName(profile));
                return;
        }

        // this part is only for A2DP, LE Audio unicast and Hearing aid
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.e(TAG, "onBtProfileConnected: Null BluetoothAdapter when connecting profile: "
                    + BluetoothProfile.getProfileName(profile));
            return;
        }
        List<BluetoothDevice> activeDevices = adapter.getActiveDevices(profile);
        if (activeDevices.isEmpty() || activeDevices.get(0) == null) {
            return;
        }
        BluetoothDevice device = activeDevices.get(0);
        switch (profile) {
            case BluetoothProfile.A2DP: {
                BluetoothProfileConnectionInfo bpci =
                        BluetoothProfileConnectionInfo.createA2dpInfo(false, -1);
                postBluetoothActiveDevice(device, bpci);
            } break;
            case BluetoothProfile.HEARING_AID: {
                BluetoothProfileConnectionInfo bpci =
                        BluetoothProfileConnectionInfo.createHearingAidInfo(false);
                postBluetoothActiveDevice(device, bpci);
            } break;
            case BluetoothProfile.LE_AUDIO: {
                int groupId = mLeAudio.getGroupId(device);
                BluetoothLeAudioCodecStatus btLeCodecStatus = null;
                try {
                    btLeCodecStatus = mLeAudio.getCodecStatus(groupId);
                } catch (Exception e) {
                    Log.e(TAG, "Exception while getting status of " + device, e);
                }
                if (btLeCodecStatus == null) {
                    Log.i(TAG, "onBtProfileConnected null LE codec status for groupId: "
                            + groupId + ", device: " + device);
                    break;
                }
                List<BluetoothLeAudioCodecConfig> outputCodecConfigs =
                        btLeCodecStatus.getOutputCodecSelectableCapabilities();
                if (!outputCodecConfigs.isEmpty()) {
                    BluetoothProfileConnectionInfo bpci =
                            BluetoothProfileConnectionInfo.createLeAudioInfo(
                                    false /*suppressNoisyIntent*/, true /*isLeOutput*/);
                    postBluetoothActiveDevice(device, bpci);
                }
                List<BluetoothLeAudioCodecConfig> inputCodecConfigs =
                        btLeCodecStatus.getInputCodecSelectableCapabilities();
                if (!inputCodecConfigs.isEmpty()) {
                    BluetoothProfileConnectionInfo bpci =
                            BluetoothProfileConnectionInfo.createLeAudioInfo(
                                    false /*suppressNoisyIntent*/, false /*isLeOutput*/);
                    postBluetoothActiveDevice(device, bpci);
                }
            } break;
            default:
                if (blePeripheralDevices()
                        && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    mLeAudioPeripheralStreamTypes =
                            mLeAudioPeripheral.getEnabledStreamTypes(device);

                    if ((mLeAudioPeripheralStreamTypes & LE_PERIPHERAL_OUTPUT_STREAM_TYPES) != 0) {
                        BluetoothProfileConnectionInfo bpci =
                                BluetoothProfileConnectionInfo.createLeAudioPeripheralInfo(
                                        true /*isLeOutput*/);
                        postBluetoothActiveDevice(device, bpci);
                    }
                    if ((mLeAudioPeripheralStreamTypes & LE_PERIPHERAL_INPUT_STREAM_TYPES) != 0) {
                        BluetoothProfileConnectionInfo bpci =
                                BluetoothProfileConnectionInfo.createLeAudioPeripheralInfo(
                                        false /*isLeOutput*/);
                        postBluetoothActiveDevice(device, bpci);
                    }
                    break;
                }
                // Not a valid profile to connect
                Log.wtf(TAG, "Invalid profile! onBtProfileConnected");
                break;
        }
    }

    private void postBluetoothActiveDevice(
            BluetoothDevice device, BluetoothProfileConnectionInfo bpci) {
        AudioDeviceBroker.BtDeviceChangedData data = new AudioDeviceBroker.BtDeviceChangedData(
                device, null, bpci, "mBluetoothProfileServiceListener");
        AudioDeviceBroker.BtDeviceInfo info = mDeviceBroker.createBtDeviceInfo(
                data, device, BluetoothProfile.STATE_CONNECTED);
        mDeviceBroker.postBluetoothActiveDevice(info, 0 /* delay */);
    }

    /*package*/ synchronized boolean isProfileProxyConnected(int profile) {
        switch (profile) {
            case BluetoothProfile.HEADSET:
                return mBluetoothHeadset != null;
            case BluetoothProfile.A2DP:
                return mA2dp != null;
            case BluetoothProfile.HEARING_AID:
                return mHearingAid != null;
            case BluetoothProfile.LE_AUDIO:
                return mLeAudio != null;
            case BluetoothProfile.A2DP_SINK:
            case BluetoothProfile.LE_AUDIO_BROADCAST:
            default:
                if (blePeripheralDevices()
                        && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    return mLeAudioPeripheral != null;
                }

                // return true for profiles that are not managed by the BtHelper because
                // the fact that the profile proxy is not connected does not affect
                // the device connection handling.
                return true;
        }
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private synchronized void onHeadsetProfileConnected(@NonNull BluetoothHeadset headset) {
        if (mDeviceBroker.isScoManagedByAudio()) {
            mScoHelper = new AmScoHelper(new AmScoHelper.BluetoothHeadsetProxy() {
                @Override
                public boolean startScoUsingVirtualVoiceCall() {
                    return headset.startScoUsingVirtualVoiceCall();
                }

                @Override
                public boolean stopScoUsingVirtualVoiceCall() {
                    return headset.stopScoUsingVirtualVoiceCall();
                }
            }, this::sendStickyBroadcastToAll);
        }
        // Discard timeout message
        mDeviceBroker.handleCancelFailureToConnectToBtHeadsetService();
        mBluetoothHeadset = headset;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            List<BluetoothDevice> activeDevices =
                    adapter.getActiveDevices(BluetoothProfile.HEADSET);
            for (BluetoothDevice device : activeDevices) {
                if (device == null) {
                    continue;
                }
                onSetBtScoActiveDevice(device, false /*deviceSwitch*/);
            }
        } else {
            Log.e(TAG, "onHeadsetProfileConnected: Null BluetoothAdapter");
        }
        mScoHelper.onProfileConnected();
    }

    //----------------------------------------------------------------------
    private void broadcastScoConnectionState(int state) {
        mDeviceBroker.postBroadcastScoConnectionState(state);
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    @Nullable AudioDeviceAttributes getHeadsetAudioDevice() {
        if (mBluetoothHeadsetDevice == null) {
            return null;
        }
        return getHeadsetAudioDevice(mBluetoothHeadsetDevice);
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private @NonNull AudioDeviceAttributes getHeadsetAudioDevice(BluetoothDevice btDevice) {
        AudioDeviceAttributes deviceAttr = mResolvedScoAudioDevices.get(btDevice);
        if (deviceAttr != null) {
            // Returns the cached device attributes so that it is consistent as the previous one.
            return deviceAttr;
        }
        return btHeadsetDeviceToAudioDevice(btDevice);
    }

    private static AudioDeviceAttributes btHeadsetDeviceToAudioDevice(BluetoothDevice btDevice) {
        if (btDevice == null) {
            return new AudioDeviceAttributes(AudioSystem.DEVICE_OUT_BLUETOOTH_SCO, "");
        }
        String address = btDevice.getAddress();
        String name = getName(btDevice);
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        BluetoothClass btClass = btDevice.getBluetoothClass();
        int nativeType = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO;
        if (btClass != null) {
            switch (btClass.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                    nativeType = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET;
                    break;
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                    nativeType = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_CARKIT;
                    break;
            }
        }
        if (AudioService.DEBUG_DEVICES) {
            Log.i(TAG, "btHeadsetDeviceToAudioDevice btDevice: " + btDevice
                    + " btClass: " + (btClass == null ? "Unknown" : btClass)
                    + " nativeType: " + nativeType + " address: " + address);
        }
        return new AudioDeviceAttributes(nativeType, address, name);
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    private boolean handleBtScoActiveDeviceChange(BluetoothDevice btDevice, boolean isActive,
            boolean deviceSwitch) {
        if (btDevice == null) {
            return true;
        }
        boolean result = false;
        AudioDeviceAttributes audioDevice = null; // Only used if isActive is true
        String address = btDevice.getAddress();
        String name = getName(btDevice);
        // Handle output device
        if (isActive) {
            audioDevice = btHeadsetDeviceToAudioDevice(btDevice);
            result = mDeviceBroker.handleDeviceConnection(
                    audioDevice, true /*connect*/, btDevice, false /*deviceSwitch*/);
        } else {
            AudioDeviceAttributes ada = mResolvedScoAudioDevices.get(btDevice);
            if (ada != null) {
                result = mDeviceBroker.handleDeviceConnection(
                    ada, false /*connect*/, btDevice, deviceSwitch);
            } else {
                // Disconnect all possible audio device types if the disconnected device type is
                // unknown
                int[] outDeviceTypes = {
                    AudioSystem.DEVICE_OUT_BLUETOOTH_SCO,
                    AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET,
                    AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_CARKIT
                };
                for (int outDeviceType : outDeviceTypes) {
                    result |= mDeviceBroker.handleDeviceConnection(new AudioDeviceAttributes(
                            outDeviceType, address, name), false /*connect*/, btDevice,
                            deviceSwitch);
                }
            }
        }
        // Handle input device
        int inDevice = AudioSystem.DEVICE_IN_BLUETOOTH_SCO_HEADSET;
        // handleDeviceConnection() && result to make sure the method get executed
        result = mDeviceBroker.handleDeviceConnection(new AudioDeviceAttributes(
                        inDevice, address, name),
                isActive, btDevice, deviceSwitch) && result;
        if (result) {
            if (isActive) {
                mResolvedScoAudioDevices.put(btDevice, audioDevice);
            } else {
                mResolvedScoAudioDevices.remove(btDevice);
            }
        }
        return result;
    }

    // Return `(null)` if given BluetoothDevice is null. Otherwise, return the anonymized address.
    private String getAnonymizedAddress(BluetoothDevice btDevice) {
        return btDevice == null ? "(null)" : btDevice.getAnonymizedAddress();
    }

    @GuardedBy("mDeviceBroker.mDeviceStateLock")
    /*package */ void onSetBtScoActiveDevice(BluetoothDevice btDevice, boolean deviceSwitch) {
        Log.i(TAG, "onSetBtScoActiveDevice: " + getAnonymizedAddress(mBluetoothHeadsetDevice)
                + " -> " + getAnonymizedAddress(btDevice) + ", deviceSwitch: " + deviceSwitch);
        final BluetoothDevice previousActiveDevice = mBluetoothHeadsetDevice;
        if (Objects.equals(btDevice, previousActiveDevice)) {
            return;
        }
        if (!handleBtScoActiveDeviceChange(previousActiveDevice, false, deviceSwitch)) {
            Log.w(TAG, "onSetBtScoActiveDevice() failed to remove previous device "
                    + getAnonymizedAddress(previousActiveDevice));
        }
        // mBluetoothHeadsetDevice must correspond to previous device until now and new device from
        // now on for SCO activation/deactivation requests made by
        // AudioDeviceBroker.onUpdateCommunicationRouteClient() to succeed.
        mBluetoothHeadsetDevice = btDevice;
        if (!handleBtScoActiveDeviceChange(btDevice, true, false /*deviceSwitch*/)) {
            Log.e(TAG, "onSetBtScoActiveDevice() failed to add new device "
                    + getAnonymizedAddress(btDevice));
            // set mBluetoothHeadsetDevice to null when failing to add new device
            mBluetoothHeadsetDevice = null;
        }
        if (mBluetoothHeadsetDevice == null && mScoHelper != null) {
            mScoHelper.resetBluetoothSco();
        }
    }

    // NOTE this listener is NOT called from AudioDeviceBroker event thread, only call async
    //      methods inside listener.
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    switch(profile) {
                        case BluetoothProfile.A2DP:
                        case BluetoothProfile.HEADSET:
                        case BluetoothProfile.HEARING_AID:
                        case BluetoothProfile.LE_AUDIO:
                        case BluetoothProfile.A2DP_SINK:
                        case BluetoothProfile.LE_AUDIO_BROADCAST:
                        case BluetoothProfile.LE_AUDIO_PERIPHERAL:
                        case BluetoothProfile.HAP_CLIENT:
                            AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                                    "BT profile service: connecting "
                                    + BluetoothProfile.getProfileName(profile)
                                    + " profile").printLog(TAG));
                            mDeviceBroker.postBtProfileConnected(profile, proxy);
                            break;

                        default:
                            break;
                    }
                }
                public void onServiceDisconnected(int profile) {

                    switch (profile) {
                        case BluetoothProfile.A2DP:
                        case BluetoothProfile.HEADSET:
                        case BluetoothProfile.HEARING_AID:
                        case BluetoothProfile.LE_AUDIO:
                        case BluetoothProfile.A2DP_SINK:
                        case BluetoothProfile.LE_AUDIO_BROADCAST:
                        case BluetoothProfile.LE_AUDIO_PERIPHERAL:
                        case BluetoothProfile.HAP_CLIENT:
                            AudioService.sDeviceLogger.enqueue(new EventLogger.StringEvent(
                                    "BT profile service: disconnecting "
                                        + BluetoothProfile.getProfileName(profile)
                                        + " profile").printLog(TAG));
                            mDeviceBroker.postBtProfileDisconnected(profile);
                            break;

                        default:
                            break;
                    }
                }
            };

    // Utilities
    // suppress warning due to generic Intent passed as param
    @SuppressWarnings("AndroidFrameworkRequiresPermission")
    private void sendStickyBroadcastToAll(Intent intent) {
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final long ident = Binder.clearCallingIdentity();
        try {
            mDeviceBroker.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
    /*package*/ synchronized int getLeAudioDeviceGroupId(BluetoothDevice device, int profile) {
        if (mLeAudio == null || device == null) {
            return BluetoothLeAudio.GROUP_ID_INVALID;
        }
        if (profile == BluetoothProfile.LE_AUDIO) {
            return mLeAudio.getGroupId(device);
        } else {
            return mLeAudio.getBroadcastToUnicastFallbackGroup();
        }
    }

    /**
     * Returns all addresses and identity addresses for LE Audio devices a group.
     * @param groupId The ID of the group from which to get addresses.
     * @return A List of Pair(String main_address, String identity_address). Note that the
     * addresses returned by BluetoothDevice can be null.
     */
    /*package*/ synchronized List<Pair<String, String>> getLeAudioGroupAddresses(
                int groupId, int profile) {
        List<Pair<String, String>> addresses = new ArrayList<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || mLeAudio == null) {
            return addresses;
        }
        if (profile == BluetoothProfile.LE_AUDIO) {
            List<BluetoothDevice> activeDevices = adapter.getActiveDevices(
                    BluetoothProfile.LE_AUDIO);
            for (BluetoothDevice device : activeDevices) {
                if (device != null && mLeAudio.getGroupId(device) == groupId) {
                    addresses.add(new Pair(device.getAddress(), device.getIdentityAddress()));
                }
            }
        } else {
            BluetoothDevice device = mLeAudio.getConnectedGroupLeadDevice(groupId);
            if (device != null) {
                addresses.add(new Pair(device.getAddress(), device.getIdentityAddress()));
            }
        }
        return addresses;
    }

    /**
     * Indicates if a Bluetooth SCO activation request owner requires us to prime the HFP device for
     * SCO or not.
     * If true, we need to call {@code startScoUsingVirtualVoiceCall}
     * @param attributionSource the AttributionSource of the SCO request owner app
     * @return true iff the client requires us to prime SCO. false for telecom, bt stacks.
     */
    private static boolean shouldStartVirtualCall(AttributionSource attributionSource) {
        if (attributionSource == null) {
            return true;
        }
        int uid = attributionSource.getUid();
        return !(UserHandle.isSameApp(uid, Process.BLUETOOTH_UID)
                || UserHandle.isSameApp(uid, Process.PHONE_UID)
                || (UserHandle.isSameApp(uid, Process.SYSTEM_UID)
                    && "com.android.server.telecom".equals(attributionSource.getPackageName())));
    }

    /*package */ static int getProfileFromType(int deviceType) {
        if (AudioSystem.isBluetoothA2dpOutDevice(deviceType)) {
            return BluetoothProfile.A2DP;
        } else if (AudioSystem.isBluetoothScoDevice(deviceType)) {
            return BluetoothProfile.HEADSET;
        } else if (AudioSystem.isBluetoothLeDevice(deviceType)) {
            return BluetoothProfile.LE_AUDIO;
        } else if (blePeripheralDevices()
                && AudioSystem.isBluetoothLeCentralDevice(deviceType)) {
            return BluetoothProfile.LE_AUDIO_PERIPHERAL;
        }
        return 0; // 0 is not a valid profile
    }

    /*package */ int getTypeFromProfile(
            int profile, boolean isLeOutput, BluetoothDevice device) {
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                return AudioSystem.DEVICE_IN_BLUETOOTH_A2DP;
            case BluetoothProfile.A2DP:
                return AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP;
            case BluetoothProfile.HEARING_AID:
                return AudioSystem.DEVICE_OUT_HEARING_AID;
            case BluetoothProfile.LE_AUDIO:
                boolean isHap = mSupportsBleHearingAids && mHapClient != null
                        && mHapClient.getConnectedDevices().contains(device);
                if (isLeOutput) {
                    return isHap ? AudioSystem.DEVICE_OUT_BLE_HEARING_AID
                            : AudioSystem.DEVICE_OUT_BLE_HEADSET;
                } else {
                    return isHap ? AudioSystem.DEVICE_IN_BLE_HEARING_AID
                            : AudioSystem.DEVICE_IN_BLE_HEADSET;
                }
            case BluetoothProfile.LE_AUDIO_BROADCAST:
                return AudioSystem.DEVICE_OUT_BLE_BROADCAST;
            case BluetoothProfile.HEADSET:
                return btHeadsetDeviceToAudioDevice(device).getInternalType();
            default:
                if (blePeripheralDevices()
                        && profile == BluetoothProfile.LE_AUDIO_PERIPHERAL) {
                    if (isLeOutput) {
                        return AudioSystem.DEVICE_OUT_BLE_CENTRAL;
                    } else {
                        //TODO b/423053144: how do we identify the DEVICE_IN_BLE_CENTRAL_BROADCAST?
                        // different BT profile of Profile API?
                        return AudioSystem.DEVICE_IN_BLE_CENTRAL;
                    }
                }
                throw new IllegalArgumentException("Invalid profile " + profile);
        }
    }

    /*package */ static Bundle getPreferredAudioProfiles(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter.getPreferredAudioProfiles(adapter.getRemoteDevice(address));
    }

    @Nullable
    /*package */ static BluetoothDevice getBluetoothDevice(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            return null;
        }

        return adapter.getRemoteDevice(address);
    }

    @AudioDeviceCategory
    /*package*/ static int getBtDeviceCategory(String address) {
        BluetoothDevice device = BtHelper.getBluetoothDevice(address);
        if (device == null) {
            return AUDIO_DEVICE_CATEGORY_UNKNOWN;
        }

        byte[] deviceType = device.getMetadata(BluetoothDevice.METADATA_DEVICE_TYPE);
        if (deviceType == null) {
            return AUDIO_DEVICE_CATEGORY_UNKNOWN;
        }
        String deviceCategory = new String(deviceType);
        switch (deviceCategory) {
            case DEVICE_TYPE_HEARING_AID:
                return AUDIO_DEVICE_CATEGORY_HEARING_AID;
            case DEVICE_TYPE_CARKIT:
                return AUDIO_DEVICE_CATEGORY_CARKIT;
            case DEVICE_TYPE_HEADSET:
            case DEVICE_TYPE_UNTETHERED_HEADSET:
                return AUDIO_DEVICE_CATEGORY_HEADPHONES;
            case DEVICE_TYPE_SPEAKER:
                return AUDIO_DEVICE_CATEGORY_SPEAKER;
            case DEVICE_TYPE_WATCH:
                return AUDIO_DEVICE_CATEGORY_WATCH;
            case DEVICE_TYPE_DEFAULT:
            default:
                // fall through
        }

        BluetoothClass deviceClass = device.getBluetoothClass();
        if (deviceClass == null) {
            return AUDIO_DEVICE_CATEGORY_UNKNOWN;
        }

        switch (deviceClass.getDeviceClass()) {
            case BluetoothClass.Device.WEARABLE_WRIST_WATCH:
                return AUDIO_DEVICE_CATEGORY_WATCH;
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                return AUDIO_DEVICE_CATEGORY_SPEAKER;
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                return AUDIO_DEVICE_CATEGORY_HEADPHONES;
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                return AUDIO_DEVICE_CATEGORY_RECEIVER;
            default:
                return AUDIO_DEVICE_CATEGORY_UNKNOWN;
        }
    }

    /**
     * Notifies Bluetooth framework that new preferred audio profiles for Bluetooth devices
     * have been applied.
     */
    public static void onNotifyPreferredAudioProfileApplied(BluetoothDevice btDevice) {
        BluetoothAdapter.getDefaultAdapter().notifyActiveDeviceChangeApplied(btDevice);
    }

    /**
     * Returns the string equivalent for the btDeviceClass class.
     */
    public static String btDeviceClassToString(int btDeviceClass) {
        switch (btDeviceClass) {
            case BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED:
                return "AUDIO_VIDEO_UNCATEGORIZED";
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                return "AUDIO_VIDEO_WEARABLE_HEADSET";
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                return "AUDIO_VIDEO_HANDSFREE";
            case 0x040C:
                return "AUDIO_VIDEO_RESERVED_0x040C"; // uncommon
            case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                return "AUDIO_VIDEO_MICROPHONE";
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                return "AUDIO_VIDEO_LOUDSPEAKER";
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                return "AUDIO_VIDEO_HEADPHONES";
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                return "AUDIO_VIDEO_PORTABLE_AUDIO";
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                return "AUDIO_VIDEO_CAR_AUDIO";
            case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                return "AUDIO_VIDEO_SET_TOP_BOX";
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                return "AUDIO_VIDEO_HIFI_AUDIO";
            case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                return "AUDIO_VIDEO_VCR";
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA:
                return "AUDIO_VIDEO_VIDEO_CAMERA";
            case BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER:
                return "AUDIO_VIDEO_CAMCORDER";
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR:
                return "AUDIO_VIDEO_VIDEO_MONITOR";
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                return "AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER";
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING:
                return "AUDIO_VIDEO_VIDEO_CONFERENCING";
            case 0x0444:
                return "AUDIO_VIDEO_RESERVED_0x0444"; // uncommon
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY:
                return "AUDIO_VIDEO_VIDEO_GAMING_TOY";
            default: // other device classes printed as a hex string.
                return TextUtils.formatSimple("0x%04x", btDeviceClass);
        }
    }

    //------------------------------------------------------------
    /*package*/ void dump(PrintWriter pw, String prefix) {
        pw.println("\n" + prefix + "mBluetoothHeadset: " + mBluetoothHeadset);
        pw.println(prefix + "mBluetoothHeadsetDevice: " + mBluetoothHeadsetDevice);
        if (mBluetoothHeadsetDevice != null) {
            final BluetoothClass bluetoothClass = mBluetoothHeadsetDevice.getBluetoothClass();
            if (bluetoothClass != null) {
                pw.println(prefix + "mBluetoothHeadsetDevice.DeviceClass: "
                        + btDeviceClassToString(bluetoothClass.getDeviceClass()));
            }
        }
        if (mScoHelper != null) {
            mScoHelper.dump(pw, prefix);
        }
        pw.println("\n" + prefix + "mHearingAid: " + mHearingAid);
        pw.println("\n" + prefix + "mLeAudio: " + mLeAudio);
        pw.println(prefix + "mA2dp: " + mA2dp);
        pw.println(prefix + "mLeAudioPeripheral: " + mLeAudioPeripheral);
        if (mSupportsBleHearingAids) {
            pw.println(prefix + "mHapClient: " + mHapClient);
        }
        pw.println(prefix + "mAvrcpAbsVolSupported: " + mAvrcpAbsVolSupported);
    }

}
