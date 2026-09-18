/*
 * Copyright 2025 The Android Open Source Project
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

import static com.android.server.audio.AudioService.anonymizeBluetoothAddress;
import static com.android.server.utils.EventLogger.Event.ALOGI;
import static com.android.server.utils.EventLogger.Event.ALOGW;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.media.AudioDeviceAttributes;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecordingConfiguration;
import android.media.AudioSystem;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.function.TriFunction;
import com.android.server.utils.EventLogger;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Maintains a stack of preferred devices for a set of clients. The latest added session is the
 * winner, unless a mode owner is present, in which case it always wins.
 */
public class RouteSelectionStack implements IBinder.DeathRecipient {
    private final Consumer<RouteClient> mDeathCb;
    private final EventLogger mLogger;

    // Invariant: client.getToken is unique per client
    @GuardedBy("this") private final List<RouteClient> mClients = new ArrayList<>();

    // Token of the current mode owner (matching the route client)
    @GuardedBy("this") private IBinder mModeOwnerToken;

    // threading note: deathCallable should post
    RouteSelectionStack(Consumer<RouteClient> deathCallable, EventLogger logger) {
        mDeathCb = deathCallable;
        mLogger = logger;
    }

    public synchronized void addClient(RouteClient client) {
        if (removeClient(client.getToken()) != null) {
            mLogger.enqueueAndSlog("client replacement: " + client, ALOGI);
        }
        mClients.add(client);
        try {
            client.getToken().linkToDeath(this, 0);
        } catch (RemoteException e) {
            mDeathCb.accept(client);
        }
        mLogger.enqueueAndSlog("addClient: " + client + " -> " + getStackStateString(), ALOGI);
    }

    public synchronized boolean updateClient(
            IBinder token, Optional<AudioDeviceAttributes> device) {
        for (var client : mClients) {
            if (client.getToken().equals(token)) {
                client.setDevice(device);
                mLogger.enqueueAndSlog(
                        "updateClient: " + token + " -> " + getStackStateString(), ALOGI);
                return true;
            }
        }
        return false;
    }

    public synchronized RouteClient removeClient(IBinder token) {
        RouteClient client = null;
        // really Java..
        for (int i = mClients.size() - 1; i >= 0; i--) {
            if (mClients.get(i).getToken().equals(token)) {
                token.unlinkToDeath(this, 0);
                client = mClients.remove(i);
                break;
            }
        }
        mLogger.enqueueAndSlog("removeClient: " + client + " -> " + getStackStateString(), ALOGI);
        return client;
    }

    public synchronized void removeClientPreference(Predicate<AudioDeviceAttributes> pred) {
        for (var client : mClients) {
            client.setDevice(client.getDevice().filter(pred.negate()));
        }
        mLogger.enqueueAndSlog("removeClientPreference: " + getStackStateString(), ALOGI);
    }

    /**
     * Returns the device corresponding to the communication client with the highest priority:
     * In all cases, if the client is disabled, it is still the owning client, but this method will
     * return empty.
     * - 1) the client which is currently also controlling the audio mode
     * - 2) the top active client in the stack if there is no audio mode owner
     * - 3) empty otherwise
     * @return the audio device which should drive the APM strategy
     */
    public synchronized Optional<AudioDeviceAttributes> selectedDevice() {
        return topClient().flatMap(x -> x.getDevice());
    }

    public synchronized Optional<RouteClient> topClient() {
        Optional<RouteClient> lastActive = Optional.empty();
        for (var client : mClients) {
            if (client.getToken().equals(mModeOwnerToken)) {
                return Optional.of(client).filter(x -> !x.isDisabled());
            }
            if (client.isActive()) {
                lastActive = Optional.of(client);
            }
        }
        return mModeOwnerToken != null ? Optional.empty() : lastActive.filter(x -> !x.isDisabled());
    }

    /**
     * Set the token corresponding to the current mode owner (i.e. the same as the token in the
     * route client). Null to clear.
     */
    public synchronized void setModeOwnerToken(@Nullable IBinder who) {
        mModeOwnerToken = who;
        mLogger.enqueueAndSlog("setModeOwnerToken: " + who + " -> " + getStackStateString(), ALOGI);
    }

    /**
     * Disable, or un-disable the preference of all clients whose device preference based on a
     * predicate.
     *
     * @param pred Input: client device pref -> Non-null ADA iff client selecting this device
     *         should be enabled, with the ADA updated to this value
     * TODO: temporary until this logic is folded in.
     */
    public synchronized void applyDeviceRestrictions(
            Function<AudioDeviceAttributes, AudioDeviceAttributes> pred) {
        for (var client : mClients) {
            if (!client.getDevice().isPresent() || client.isForModeSession()) {
                continue;
            }
            var newDevice = pred.apply(client.getDevice().get());
            if (newDevice != null && !newDevice.equals(client.getDevice().get())) {
                // Routing fixup
                client.setDevice(Optional.of(newDevice));
            }
            client.setDisabled(newDevice == null);
        }
        mLogger.enqueueAndSlog("applyDeviceRestrictions " + getStackStateString(), ALOGI);
    }

    public synchronized Optional<RouteClient> getClientForToken(IBinder token) {
        for (var client : mClients) {
            if (client.getToken().equals(token)) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }

    // TODO: All identification should be binder-mediated, either via static tokens generated in
    // AudioManager for existing/legacy clients (which moves matching from uid to process, but this
    // is better, and almost certainly non-breaking), or via API exposed sessions which hold
    // explicit tokens.
    public synchronized RouteClient getClientForUid(int uid) {
        for (var client : mClients) {
            if (client.getUid() == uid) {
                return client;
            }
        }
        return null;
    }

    public synchronized boolean updateActiveForUid(int uid, boolean active) {
        boolean updated = false;
        for (var client : mClients) {
            if (client.getUid() != uid) {
                continue;
            }
            boolean wasActive = client.isActive();
            client.setStreamActive(active);
            updated = client.isActive() != wasActive;
        }
        if (updated) {
            mLogger.enqueueAndSlog(
                    "updateActiveForUid " + uid + active + getStackStateString(), ALOGI);
        }
        return updated;
    }

    @Override
    public void binderDied() {
        throw new IllegalStateException("Unexpected binderDied cb called");
    }

    // TODO: layer inversion
    @Override
    public void binderDied(IBinder who) {
        mDeathCb.accept(getClientForToken(who).orElse(null));
    }

    public synchronized void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "RouteSelectionStack:");
        pw.println(prefix + "  Mode owner token: " + mModeOwnerToken);
        pw.println(prefix + "  Clients (top of stack first):");
        for (int i = mClients.size() - 1; i >= 0; i--) {
            RouteClient client = mClients.get(i);
            String ownerSuffix = client.getToken() == mModeOwnerToken ? " (mode owner)" : "";
            pw.println(prefix + "    " + client + ownerSuffix);
        }
    }

    private synchronized String getStackStateString() {
        synchronized (this) {
            return mClients.stream()
                    .map(c
                            -> c.toShortString() + ":"
                                    + (c.getToken() == mModeOwnerToken ? "o" : ""))
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    static final class RouteClient {
        private final IBinder mToken;
        private final AttributionSource mAttributionSource;
        private final boolean mIsPrivileged;
        private Optional<AudioDeviceAttributes> mDevice;
        private boolean mStreamActive;

        // Route selection should not be considered due to device invalidation
        private boolean mDisabled;
        private boolean mForModeSession;

        RouteClient(IBinder cb, @NonNull AttributionSource attributionSource,
                AudioDeviceAttributes device, boolean isPrivileged) {
            this(cb, attributionSource, device, isPrivileged, false);
        }

        RouteClient(IBinder cb, @NonNull AttributionSource attributionSource,
                AudioDeviceAttributes device, boolean isPrivileged, boolean forModeSession) {
            mToken = cb;
            mAttributionSource = attributionSource;
            mDevice = Optional.ofNullable(device);
            mIsPrivileged = isPrivileged;
            mStreamActive = true;
            mDisabled = false;
            mForModeSession = forModeSession;
        }

        IBinder getToken() {
            return mToken;
        }

        @NonNull
        AttributionSource getAttributionSource() {
            return mAttributionSource;
        }

        int getUid() {
            return mAttributionSource.getUid();
        }

        boolean isPrivileged() {
            return mIsPrivileged;
        }

        boolean isForModeSession() {
            return mForModeSession;
        }

        Optional<AudioDeviceAttributes> getDevice() {
            return mDevice;
        }

        public boolean isActive() {
            return mIsPrivileged || mStreamActive;
        }

        public boolean isDisabled() {
            return mDisabled;
        }

        private void setDevice(Optional<AudioDeviceAttributes> device) {
            mDevice = device;
        }

        private void setStreamActive(boolean active) {
            mStreamActive = active;
        }

        private void setDisabled(boolean disabled) {
            mDisabled = disabled;
        }

        @Override
        public String toString() {
            return "[RouteClient: " + mAttributionSource.getUid() + "("
                    + mAttributionSource.getPackageName() + ")"
                    + " mDevice: " + mDevice + " mIsPrivileged: " + mIsPrivileged
                    + " mStreamActive: " + mStreamActive + " mDisabled: " + mDisabled + "]";
        }

        public String toShortString() {
            var abbrevDev = mDevice.map(x -> AudioSystem.getDeviceName(x.getType()))
                                    .map(x -> clampedSubstr(x, 0, 3))
                                    .orElse(null);
            return String.valueOf(getUid()) + "("
                    + abbrevPackage(getAttributionSource().getPackageName()) + ")"
                    + "|" + abbrevDev + "_" + anonymizeBluetoothAddress(mDevice.orElse(null))
                    + "|" + (isActive() ? "a" : "i") + "|" + (isDisabled() ? "d" : "e");
        }
    }

    private static String abbrevPackage(String packageName) {
        if (packageName == null) {
            return "";
        }
        var cursor = packageName.lastIndexOf('.') + 1;
        return clampedSubstr(packageName, cursor, cursor + 5);
    }

    private static String clampedSubstr(String str, int beginInd, int endInd) {
        return str.substring(Math.max(0, beginInd), Math.min(endInd, str.length()));
    }
}
