/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import static com.android.server.security.authenticationpolicy.agent.AgentSessionMap.Key;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceId;
import android.companion.virtual.VirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDeviceListener;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.Flags;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.security.authenticationpolicy.StrongAuthListener;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This sub-service contains an internal authentication API for agents running on
 * a connected device. Agents may perform automated actions when the user's primary device
 * is locked. This service is responsible for deciding if an agent can continue to run in cases
 * like that by incorporating various "signals," such as the last time a user authenticated on
 * their device, connected device status, etc.
 *
 * This service is not meant to be exposed directly to an agent, but is an internal service
 * that higher-level Android services will use as a part of their overall orchestration.
 *
 * @hide
 */
@SuppressLint("MissingPermission")
public class AgentAuthService implements AgentAuthServiceInternal {
    private static final String TAG = "AgentAuthService";

    private final Context mContext;
    private final Handler mHandler;
    private final StrongAuthListener mStrongAuthListener;

    private LockSettingsInternal mLockSettings;
    private BiometricManager mBiometricManager;
    private KeyguardManager mKeyguardManager;
    private CompanionDeviceManager mCompanionDeviceManager;
    private VirtualDeviceManager mVirtualDeviceManager;

    private final Clock mClock;
    private final long mLastAuthTimeIntervalMillis;
    private CDMAgentMonitor mAgentMonitor;
    private int mCurrentUserId = UserHandle.USER_NULL;

    // session list, writes must be done on the Handler
    private AgentSessionMap mAgentSessionList;

    AgentAuthService(@NonNull Context context, @NonNull Handler handler,
            @NonNull Clock clock, @IntRange(from = 0) long lastAuthTimeIntervalMillis) {
        mContext = context;
        mHandler = handler;
        mClock = clock;
        mLastAuthTimeIntervalMillis = lastAuthTimeIntervalMillis;
        mAgentSessionList = new AgentSessionMap(mContext, mCurrentUserId);
        mStrongAuthListener = new StrongAuthListener(mHandler, this::onStrongAuthForUser);
    }

    @Override
    public boolean isAgentAuthorized(@UserIdInt int userId, int deviceId,
            @Nullable DeviceId companionDeviceId) {
        if (companionDeviceId == null) {
            // check for valid VDM managed local agents first
            if (mVirtualDeviceManager != null
                    && mVirtualDeviceManager.isValidVirtualDeviceId(deviceId)) {
                // TODO: delete this block when flag is removed
                if (isAutomotiveProjection(mVirtualDeviceManager.getVirtualDevice(deviceId))
                        && !Flags.agentAuthAllowAutoProjected()) {
                    Slog.w(TAG, "Skip AutomotiveProjection");
                    return !mKeyguardManager.isDeviceLocked(userId, Context.DEVICE_ID_DEFAULT);
                }
                return isAgentAuthorizedByDeviceId(userId, deviceId);
            }

            // otherwise, the device must be unlocked
            return !mKeyguardManager.isDeviceLocked(userId, Context.DEVICE_ID_DEFAULT);
        }

        // check for a valid association with the given remote agent
        final var association = mCompanionDeviceManager != null ?
                mCompanionDeviceManager.getAssociationByDeviceId(userId, companionDeviceId) : null;
        if (association == null) {
            Slog.w(TAG, "No association found for companionDeviceId: " + companionDeviceId);
            return false;
        }

        return isAgentAuthorizedByAssociationId(userId, association.getId());
    }

    @Override
    public boolean isAgentAuthorizedByDeviceId(@UserIdInt int userId, int deviceId) {
        final var info = mAgentSessionList.get(Key.ofLocal(deviceId));
        return info != null && (info.getUserId() == userId) && info.isAllowed()
                && !mKeyguardManager.isDeviceLocked(userId, deviceId);
    }

    @Override
    public boolean isAgentAuthorizedByAssociationId(@UserIdInt int userId, int associationId) {
        final var info = mAgentSessionList.get(Key.ofRemote(associationId));
        return info != null && (info.getUserId() == userId) && info.isAllowed();
    }

    @Override
    public boolean setOverrideForDeviceId(@UserIdInt int userId,
            int deviceId, boolean authorized) {
        if (Build.IS_DEBUGGABLE) {
            final AgentSession result = authorized
                    ? mAgentSessionList.authorizeIfPresent(userId, Key.ofLocal(deviceId))
                    : mAgentSessionList.revokeIfPresent(userId, Key.ofLocal(deviceId));
            return result != null && result.isAllowed();
        }

        return false;
    }

    @Override
    public boolean setOverrideForAssociationId(@UserIdInt int userId,
            int associationId, boolean authorized) {
        if (Build.IS_DEBUGGABLE) {
            final AgentSession result = authorized
                    ? mAgentSessionList.authorizeIfPresent(userId, Key.ofRemote(associationId))
                    : mAgentSessionList.revokeIfPresent(userId, Key.ofRemote(associationId));
            return result != null && result.isAllowed();
        }

        return false;
    }

    /** Start the service start monitoring for connected agents and init for current user. */
    void start() {
        mHandler.post(() -> {
            start(Objects.requireNonNull(LocalServices.getService(LockSettingsInternal.class)),
                    Objects.requireNonNull(mContext.getSystemService(BiometricManager.class)),
                    Objects.requireNonNull(mContext.getSystemService(KeyguardManager.class)),
                    Objects.requireNonNull(mContext.getSystemService(CompanionDeviceManager.class)),
                    mContext.getSystemService(VirtualDeviceManager.class),
                    ActivityManager.getCurrentUser());
        });
    }

    @VisibleForTesting
    void start(@NonNull LockSettingsInternal lockSettings,
            @NonNull BiometricManager biometricManager,
            @NonNull KeyguardManager keyguardManager,
            @NonNull CompanionDeviceManager companionDeviceManager,
            @Nullable VirtualDeviceManager virtualDeviceManager,
            @UserIdInt int initialUserId) {
        mLockSettings = lockSettings;
        mBiometricManager = biometricManager;
        mKeyguardManager = keyguardManager;
        mCompanionDeviceManager = companionDeviceManager;
        mVirtualDeviceManager = virtualDeviceManager;

        mLockSettings.registerLockSettingsStateListener(
                mStrongAuthListener.asLockSettingsStateListener());
        mBiometricManager.registerAuthenticationStateListener(
                mStrongAuthListener.asAuthenticationStateListener());
        if (mVirtualDeviceManager != null) {
            mVirtualDeviceManager.registerVirtualDeviceListener(mHandler::post,
                    new VirtualDeviceListener() {
                        @Override
                        public void onVirtualDeviceCreated(int deviceId) {
                            handleVirtualDeviceCreated(deviceId);
                        }

                        @Override
                        public void onVirtualDeviceClosed(int deviceId) {
                            handleVirtualDeviceClosed(deviceId);
                        }
                    });
        }

        initInBackgroundForUser(initialUserId);
    }

    /** Refresh data for the given user (call when the foreground user changes). */
    void initInBackgroundForUser(@UserIdInt int userId) {
        mHandler.post(() -> {
            Slog.i(TAG, "Refreshing data for user: " + userId);

            onUserSwitched(userId);
        });
    }

    private void onUserSwitched(@UserIdInt int userId) {
        if (mCurrentUserId == userId) {
            Slog.i(TAG, "user did not change - ignore");
            return;
        }

        // shutdown any existing subscriptions
        if (mAgentMonitor != null) {
            Slog.d(TAG, "Stop listening for associated agents with user: " + mCurrentUserId);

            mAgentMonitor.stop();
            mAgentMonitor = null;
        }

        // reset list to an initial state
        mCurrentUserId = userId;
        mAgentSessionList.clear();
        mAgentSessionList = new AgentSessionMap(mContext, mCurrentUserId);
        mHandler.removeCallbacksAndMessages(null);
        Slog.d(TAG, "Reset sessions / dropped all pending updates");

        // subscribe to events for this new user
        if (userId >= 0) {
            Slog.d(TAG, "Start listening for associated agents with user: " + mCurrentUserId);

            mAgentMonitor = new CDMAgentMonitor(mHandler, userId,
                    mCompanionDeviceManager, new CDMAgentMonitor.Listener() {
                @Override
                public void onAgentConnectionStarted(int associationId) {
                    final boolean authorized = isAuthorizedAtConnection();
                    if (authorized) {
                        Slog.d(TAG, "Start remote session (authorized): " + associationId);
                        mAgentSessionList.put(Key.ofRemote(associationId),
                                AgentSession.authorized(userId));
                    } else {
                        Slog.d(TAG, "Start remote session (not authorized): " + associationId);
                        mAgentSessionList.put(Key.ofRemote(associationId),
                                AgentSession.notAuthorized(userId));
                    }
                }

                @Override
                public void onAgentConnectionStopped(int associationId) {
                    Slog.d(TAG, "End remote session (if any) for associationId: " + associationId);
                    mAgentSessionList.remove(Key.ofRemote(associationId));
                }
            });
            mAgentMonitor.start();
        }
    }

    private void handleVirtualDeviceCreated(int deviceId) {
        Slog.d(TAG, "Virtual device created: " + deviceId);

        final VirtualDevice virtualDevice = mVirtualDeviceManager != null
                ? mVirtualDeviceManager.getVirtualDevice(deviceId) : null;
        if (virtualDevice == null) {
            Slog.w(TAG, "Could not find virtual device for id: " + deviceId);
            return;
        }

        final boolean requireHostUnlock = requiresRecentUnlockOnHost(virtualDevice);
        final boolean authorized = !requireHostUnlock || isAuthorizedAtConnection();
        if (authorized) {
            Slog.d(TAG, "Start local session (authorized): " + deviceId);
            mAgentSessionList.put(Key.ofLocal(deviceId),
                    AgentSession.authorized(mCurrentUserId));
        } else {
            Slog.d(TAG, "Start local session (not authorized): " + deviceId);
            mAgentSessionList.put(Key.ofLocal(deviceId),
                    AgentSession.notAuthorized(mCurrentUserId));
        }
    }

    private void handleVirtualDeviceClosed(int deviceId) {
        Slog.d(TAG, "End local session (if any) for deviceId: " + deviceId);
        mAgentSessionList.remove(Key.ofLocal(deviceId));
    }

    private boolean requiresRecentUnlockOnHost(@Nullable VirtualDevice virtualDevice) {
        if (virtualDevice != null) {
            // only auto projected supports this, others use only the virtual device lock state
            if (Flags.agentAuthAllowAutoProjected() && isAutomotiveProjection(virtualDevice)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAutomotiveProjection(@Nullable VirtualDevice virtualDevice) {
        if(virtualDevice != null) {
            final int profile = virtualDevice.getDeviceProfile();
            return profile == VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;
        }
        return false;
    }

    private void onStrongAuthForUser(@UserIdInt int userId) {
        if (mCurrentUserId != userId) {
            return;
        }

        mAgentSessionList.authorizeAll(userId);
    }

    private boolean isAuthorizedAtConnection() {
        if (!mKeyguardManager.isDeviceLocked(mCurrentUserId)) {
            return true;
        }

        // no custom interval for the normal case
        return hasRecentStrongAuth(0);
    }

    private boolean hasRecentStrongAuth(@IntRange(from = 0) int intervalMillis) {
        try {
            final long now = mClock.millis();
            final long interval = intervalMillis > 0 ? Math.min(intervalMillis,
                    mLastAuthTimeIntervalMillis) : mLastAuthTimeIntervalMillis;
            final long lastAuthTime = mBiometricManager.getLastAuthenticationTime(mCurrentUserId,
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            | BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return lastAuthTime > 0 && (now - lastAuthTime) < interval;
        } catch (Exception e) {
            Slog.e(TAG, "Unable to determine last auth time", e);
        }

        return false;
    }

    /** System service lifecycle for {@link AgentAuthService}. */
    public static final class Lifecycle extends SystemService {
        private final AgentAuthService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);

            mService = new AgentAuthService(context,
                    new Handler(BackgroundThread.getHandler().getLooper()),
                    SystemClock.elapsedRealtimeClock(),
                    TimeUnit.MINUTES.toMillis(10));
        }

        @Override
        public void onStart() {
            LocalServices.addService(AgentAuthServiceInternal.class, mService);
            mService.start();
        }

        @Override
        public void onUserSwitching(TargetUser from, TargetUser to) {
            final int fromId = (from == null) ? -1 : from.getUserIdentifier();
            final int toId = to.getUserIdentifier();

            Slog.i(TAG, "user switch: " + fromId + " -> " + toId);
            mService.initInBackgroundForUser(toId);
        }
    }
}
