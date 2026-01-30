/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_SECURE_LOCK_DEVICE;
import static android.Manifest.permission.TEST_BIOMETRIC;
import static android.Manifest.permission.USE_BIOMETRIC_INTERNAL;
import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_ERROR_LOCKOUT;
import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_ERROR_LOCKOUT_PERMANENT;
import static android.security.Flags.disableAdaptiveAuthCounterLock;
import static android.security.Flags.failedAuthLockToggle;
import static android.security.Flags.secureLockDevice;
import static android.security.Flags.secureLockdown;

import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.AuthenticationStateListener;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.events.AuthenticationAcquiredInfo;
import android.hardware.biometrics.events.AuthenticationErrorInfo;
import android.hardware.biometrics.events.AuthenticationFailedInfo;
import android.hardware.biometrics.events.AuthenticationHelpInfo;
import android.hardware.biometrics.events.AuthenticationStartedInfo;
import android.hardware.biometrics.events.AuthenticationStoppedInfo;
import android.hardware.biometrics.events.AuthenticationSucceededInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.proximity.IProximityResultCallback;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.IAuthenticationPolicyService;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.locksettings.LockSettingsStateListener;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.io.FileDescriptor;
import java.util.Objects;

/**
 * @hide
 */
public class AuthenticationPolicyService extends SystemService {
    private static final String TAG = "AuthenticationPolicyService";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private static final int MSG_REPORT_PRIMARY_AUTH_ATTEMPT = 1;
    private static final int MSG_REPORT_BIOMETRIC_AUTH_SUCCESS = 2;
    private static final int MSG_REPORT_BIOMETRIC_AUTH_FAILURE = 3;
    private static final int MSG_REPORT_BIOMETRIC_AUTH_ERROR = 4;
    private static final int AUTH_SUCCESS = 1;
    private static final int AUTH_FAILURE = 0;
    private static final int TYPE_PRIMARY_AUTH = 0;
    private static final int TYPE_BIOMETRIC_AUTH = 1;

    private final LockPatternUtils mLockPatternUtils;
    private final LockSettingsInternal mLockSettings;
    private final BiometricManager mBiometricManager;
    private final KeyguardManager mKeyguardManager;
    private final WindowManagerInternal mWindowManager;
    private final UserManagerInternal mUserManager;
    private final boolean mEnableFailedAuthLock;
    private final int mMaxAllowedFailedAuthAttempts;
    private final boolean mEnableFailedAuthLockToggle;
    private SecureLockDeviceServiceInternal mSecureLockDeviceService;
    private WatchRangingServiceInternal mWatchRangingService;
    @VisibleForTesting
    final SparseIntArray mFailedAttemptsForUser = new SparseIntArray();
    private final SparseLongArray mLastLockedTimestamp = new SparseLongArray();

    public AuthenticationPolicyService(Context context) {
        this(context, new LockPatternUtils(context));
    }

    @VisibleForTesting
    public AuthenticationPolicyService(Context context, LockPatternUtils lockPatternUtils) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mLockSettings = Objects.requireNonNull(
                LocalServices.getService(LockSettingsInternal.class));
        mBiometricManager = Objects.requireNonNull(
                context.getSystemService(BiometricManager.class));
        mKeyguardManager = Objects.requireNonNull(context.getSystemService(KeyguardManager.class));
        mWindowManager = Objects.requireNonNull(
                LocalServices.getService(WindowManagerInternal.class));
        mUserManager = Objects.requireNonNull(LocalServices.getService(UserManagerInternal.class));
        if (secureLockdown()) {
            mSecureLockDeviceService = Objects.requireNonNull(
                    LocalServices.getService(SecureLockDeviceServiceInternal.class));
        }
        if (Flags.identityCheckWatch()) {
            mWatchRangingService = Objects.requireNonNull(LocalServices.getService(
                    WatchRangingServiceInternal.class));
        }
        mEnableFailedAuthLock = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableFailedAuthLock);
        mMaxAllowedFailedAuthAttempts = context.getResources().getInteger(
                com.android.internal.R.integer.config_maxAllowedFailedAuthAttempts);
        mEnableFailedAuthLockToggle = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableFailedAuthLockToggle);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.AUTHENTICATION_POLICY_SERVICE, mService);
        LocalServices.addService(AuthenticationPolicyService.class, this);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            init();
        }
    }

    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        if (failedAuthLockToggle() && mEnableFailedAuthLock && mEnableFailedAuthLockToggle) {
            mayInitiateFailedAuthLockSettings(to.getUserIdentifier());
        }
    }

    @VisibleForTesting
    void init() {
        mLockSettings.registerLockSettingsStateListener(mLockSettingsStateListener);
        mBiometricManager.registerAuthenticationStateListener(mAuthenticationStateListener);

        if (failedAuthLockToggle() && mEnableFailedAuthLock && mEnableFailedAuthLockToggle) {
            final int mainUserId = mUserManager.getMainUserId();
            if (mainUserId != UserHandle.USER_NULL) {
                mayInitiateFailedAuthLockSettings(mainUserId);
            } else {
                Slog.w(TAG, "No main user exists so use user 0 instead");
                mayInitiateFailedAuthLockSettings(UserHandle.USER_SYSTEM);
            }
        }
    }

    private void mayInitiateFailedAuthLockSettings(int userId) {
        // If userId is a profile, check its parent's settings
        final int parentUserId = mUserManager.getProfileParentId(userId);
        try {
            // Attempt to get the settings without specifying the default value
            Settings.Secure.getIntForUser(
                    getContext().getContentResolver(),
                    Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK,
                    parentUserId);
        } catch (SettingNotFoundException e) {
            // If the settings does not exist yet, set it to the default value for the main user, so
            // that other components can start populating the settings value accordingly (e.g. for
            // showing the failed auth lock toggle)
            Slog.i(TAG, "Initiate the failed auth lock settings for userId=" + parentUserId);
            Settings.Secure.putIntForUser(
                    getContext().getContentResolver(),
                    Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK,
                    mEnableFailedAuthLock ? 0 : 1,
                    parentUserId);
        }
    }

    private final LockSettingsStateListener mLockSettingsStateListener =
            new LockSettingsStateListener() {
                @Override
                public void onAuthenticationSucceeded(int userId) {
                    if (DEBUG) {
                        Slog.d(TAG, "LockSettingsStateListener#onAuthenticationSucceeded");
                    }
                    mHandler.obtainMessage(MSG_REPORT_PRIMARY_AUTH_ATTEMPT, AUTH_SUCCESS, userId)
                            .sendToTarget();
                }

                @Override
                public void onAuthenticationFailed(int userId) {
                    Slog.i(TAG, "LockSettingsStateListener#onAuthenticationFailed");
                    mHandler.obtainMessage(MSG_REPORT_PRIMARY_AUTH_ATTEMPT, AUTH_FAILURE, userId)
                            .sendToTarget();
                }
            };

    private final AuthenticationStateListener mAuthenticationStateListener =
            new AuthenticationStateListener.Stub() {
                @Override
                public void onAuthenticationAcquired(AuthenticationAcquiredInfo authInfo) {}

                @Override
                public void onAuthenticationError(AuthenticationErrorInfo authInfo) {
                    Slog.i(TAG, "AuthenticationStateListener#onAuthenticationError");
                    mHandler.obtainMessage(
                            MSG_REPORT_BIOMETRIC_AUTH_ERROR, authInfo).sendToTarget();
                }

                @Override
                public void onAuthenticationFailed(AuthenticationFailedInfo authInfo) {
                    Slog.i(TAG, "AuthenticationStateListener#onAuthenticationFailed");
                    mHandler.obtainMessage(
                            MSG_REPORT_BIOMETRIC_AUTH_FAILURE, authInfo).sendToTarget();
                }

                @Override
                public void onAuthenticationHelp(AuthenticationHelpInfo authInfo) {}

                @Override
                public void onAuthenticationStarted(AuthenticationStartedInfo authInfo) {}

                @Override
                public void onAuthenticationStopped(AuthenticationStoppedInfo authInfo) {}

                @Override
                public void onAuthenticationSucceeded(AuthenticationSucceededInfo authInfo) {
                    if (DEBUG) {
                        Slog.d(TAG, "AuthenticationStateListener#onAuthenticationSucceeded");
                    }
                    mHandler.obtainMessage(
                            MSG_REPORT_BIOMETRIC_AUTH_SUCCESS, authInfo).sendToTarget();
                }
            };

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REPORT_PRIMARY_AUTH_ATTEMPT:
                    handleReportPrimaryAuthAttempt(msg.arg1 != AUTH_FAILURE, msg.arg2);
                    break;
                case MSG_REPORT_BIOMETRIC_AUTH_SUCCESS:
                    AuthenticationSucceededInfo successInfo = (AuthenticationSucceededInfo) msg.obj;
                    handleReportBiometricAuthSuccess(successInfo);
                    break;
                case MSG_REPORT_BIOMETRIC_AUTH_FAILURE:
                    AuthenticationFailedInfo failInfo = (AuthenticationFailedInfo) msg.obj;
                    handleReportBiometricAuthFailure(failInfo.getUserId());
                    break;
                case MSG_REPORT_BIOMETRIC_AUTH_ERROR:
                    AuthenticationErrorInfo errorInfo = (AuthenticationErrorInfo) msg.obj;
                    handleReportBiometricAuthError(errorInfo);
                    break;
            }
        }
    };

    private void handleReportPrimaryAuthAttempt(boolean success, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "handleReportPrimaryAuthAttempt: success=" + success
                    + ", userId=" + userId);
        }
        reportAuthAttempt(TYPE_PRIMARY_AUTH, success, userId);
    }

    private void handleReportBiometricAuthSuccess(AuthenticationSucceededInfo successInfo) {
        boolean isStrongBiometric = successInfo.isIsStrongBiometric();
        int userId = successInfo.getUserId();

        if (DEBUG) {
            Slog.d(TAG, "handleReportBiometricAuthSuccess: isStrongBiometric="
                    + isStrongBiometric + ", userId=" + userId);
        }
        if (secureLockDevice() && secureLockdown() && isStrongBiometric
                && mSecureLockDeviceService.isSecureLockDeviceEnabled()) {
            // After successful strong biometric auth during secure lock device, notify
            // SecureLockDeviceService
            mSecureLockDeviceService.onStrongBiometricAuthenticationSuccess(UserHandle.of(userId));
        }
        reportAuthAttempt(TYPE_BIOMETRIC_AUTH, /* success */ true, userId);
    }

    private void handleReportBiometricAuthFailure(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "handleReportBiometricAuthFailure: userId=" + userId);
        }
        reportAuthAttempt(TYPE_BIOMETRIC_AUTH, /* success */ false, userId);
    }

    private void handleReportBiometricAuthError(AuthenticationErrorInfo errorInfo) {
        if (DEBUG) {
            Slog.d(TAG, "handleReportBiometricAuthError: "
                    + "biometricSourceType=" + errorInfo.getBiometricSourceType()  + ", "
                    + "requestReason=" + errorInfo.getRequestReason()  + ", "
                    + "errCode=" + errorInfo.getErrCode()  + ", "
                    + "errString=" + errorInfo.getErrString()
            );
        }

        // BIOMETRIC_ERROR_LOCKOUT == FACE_ERROR_LOCKOUT == FINGERPRINT_ERROR_LOCKOUT
        boolean isLockout = errorInfo.getErrCode() == BIOMETRIC_ERROR_LOCKOUT
                || errorInfo.getErrCode() == BIOMETRIC_ERROR_LOCKOUT_PERMANENT;

        boolean secureLockDeviceEnabled = secureLockDevice() && secureLockdown()
                && mSecureLockDeviceService.isSecureLockDeviceEnabled();
        if (secureLockDeviceEnabled && isLockout) {
            // On biometric lockout when secure lock device is enabled, reset authentication
            // progress and return to step 1 of the two-factor authentication - credential
            // auth on the bouncer
            mLockPatternUtils.requireStrongAuth(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                    UserHandle.USER_ALL);
        }
    }

    private void reportAuthAttempt(int authType, boolean success, int userId) {
        // Do not report auth attempts and do not proceed to lock the device if the failed auth lock
        // feature (aka adaptive auth) is completely disabled by the device manufacturer
        if (failedAuthLockToggle() && !mEnableFailedAuthLock) {
            Slog.v(TAG, "Failed auth lock is disabled by the device manufacturer");
            return;
        }

        // Disable adaptive auth for automotive devices by default
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        if (success) {
            // Deleting the entry effectively resets the counter of failed attempts for the user
            mFailedAttemptsForUser.delete(userId);

            // Collect metrics if the device was locked by adaptive auth before
            if (mLastLockedTimestamp.indexOfKey(userId) >= 0) {
                final long lastLockedTime = mLastLockedTimestamp.get(userId);
                collectTimeElapsedSinceLastLocked(
                        lastLockedTime, SystemClock.elapsedRealtime(), authType);

                // Remove the entry for the last locked time because a successful auth just happened
                // and metrics have been collected
                mLastLockedTimestamp.delete(userId);
            }
            return;
        }

        final int numFailedAttempts = mFailedAttemptsForUser.get(userId, 0) + 1;
        Slog.i(TAG, "reportAuthAttempt: numFailedAttempts=" + numFailedAttempts
                + ", userId=" + userId);
        mFailedAttemptsForUser.put(userId, numFailedAttempts);

        // Don't lock again if the device is already locked and if Keyguard is already showing and
        // isn't trivially dismissible
        if (mKeyguardManager.isDeviceLocked(userId) && mKeyguardManager.isKeyguardLocked()) {
            Slog.d(TAG, "Not locking the device because the device is already locked.");
            return;
        }

        if (numFailedAttempts < mMaxAllowedFailedAuthAttempts) {
            Slog.d(TAG, "Not locking the device because the number of failed attempts is below"
                    + " the threshold.");
            return;
        }

        // If a user toggle is enabled by the device manufacturer on 25Q4+ builds, or if it's
        // debuggable 25Q3+ builds, then failed auth lock can be enabled or disabled by
        // users in settings
        if ((failedAuthLockToggle() && mEnableFailedAuthLockToggle)
                || (disableAdaptiveAuthCounterLock() && Build.IS_DEBUGGABLE)) {
            // If userId is a profile, use its parent's settings to determine whether failed auth
            // lock is enabled or disabled for the profile, irrespective of the profile's own
            // settings. If userId is a main user (i.e. parentUserId equals to userId), use its own
            // settings
            final int parentUserId = mUserManager.getProfileParentId(userId);
            final boolean disabled = Settings.Secure.getIntForUser(
                    getContext().getContentResolver(),
                    Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK,
                    mEnableFailedAuthLock ? 0 : 1,
                    parentUserId) != 0;
            if (disabled) {
                Slog.i(TAG, "userId=" + userId + ", parentUserId=" + parentUserId
                        + ", failed auth lock is disabled by user in settings");
                return;
            }
        }

        //TODO: additionally consider the trust signal before locking device
        lockDevice(userId);
    }

    private static void collectTimeElapsedSinceLastLocked(long lastLockedTime, long authTime,
            int authType) {
        final int unlockType = switch (authType) {
            case TYPE_PRIMARY_AUTH -> FrameworkStatsLog
                    .ADAPTIVE_AUTH_UNLOCK_AFTER_LOCK_REPORTED__UNLOCK_TYPE__PRIMARY_AUTH;
            case TYPE_BIOMETRIC_AUTH -> FrameworkStatsLog
                    .ADAPTIVE_AUTH_UNLOCK_AFTER_LOCK_REPORTED__UNLOCK_TYPE__BIOMETRIC_AUTH;
            default -> FrameworkStatsLog
                    .ADAPTIVE_AUTH_UNLOCK_AFTER_LOCK_REPORTED__UNLOCK_TYPE__UNKNOWN;
        };

        if (DEBUG) {
            Slog.d(TAG, "collectTimeElapsedSinceLastLockedForUser: "
                    + "lastLockedTime=" + lastLockedTime
                    + ", authTime=" + authTime
                    + ", unlockType=" + unlockType);
        }

        // This usually shouldn't happen, and just check out of an abundance of caution
        if (lastLockedTime > authTime) {
            return;
        }

        // Log to statsd
        FrameworkStatsLog.write(FrameworkStatsLog.ADAPTIVE_AUTH_UNLOCK_AFTER_LOCK_REPORTED,
                lastLockedTime, authTime, unlockType);
    }

    /**
     * Locks the device and requires primary auth or biometric auth for unlocking
     */
    private void lockDevice(int userId) {
        // Require either primary auth or biometric auth to unlock the device again. Keyguard and
        // bouncer will also check the StrongAuthFlag for the user to display correct strings for
        // explaining why the device is locked
        mLockPatternUtils.requireStrongAuth(SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST, userId);

        // If userId is a profile that has a different parent userId (regardless of its profile
        // type, or whether it's a profile with unified challenges or not), its parent userId that
        // owns the Keyguard will also be locked
        final int parentUserId = mUserManager.getProfileParentId(userId);
        Slog.i(TAG, "lockDevice: userId=" + userId + ", parentUserId=" + parentUserId);
        if (parentUserId != userId) {
            mLockPatternUtils.requireStrongAuth(SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST,
                    parentUserId);
        }

        // Lock the device
        mWindowManager.lockNow();

        // Record the time that the device is locked by adaptive auth to collect metrics when the
        // next successful primary or biometric auth happens
        mLastLockedTimestamp.put(userId, SystemClock.elapsedRealtime());
    }

    /**
     * Require that the caller userId matches the context userId, or that the caller has the
     * permission to interact across users.
     *
     * @param targetUser userId of the target user of the operation
     * @param message a message to include in the exception if it is thrown.
     */
    private void enforceCrossUserPermission(UserHandle targetUser, String message) {
        if (targetUser.getIdentifier() == UserHandle.getCallingUserId()) {
            return;
        }
        getContext().enforceCallingOrSelfPermission(
                INTERACT_ACROSS_USERS_FULL,
                message);
    }

    private final IBinder mService = new IAuthenticationPolicyService.Stub() {
        /**
         * @see AuthenticationPolicyManager#getSecureLockDeviceAvailability()
         * @param user user associated with the calling context to check for secure lock device
         *             availability
         * @return {@link GetSecureLockDeviceAvailabilityRequestStatus} int indicating whether
         * secure lock device is available for the calling user
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        @GetSecureLockDeviceAvailabilityRequestStatus
        public int getSecureLockDeviceAvailability(UserHandle user) {
            getSecureLockDeviceAvailability_enforcePermission();
            enforceCrossUserPermission(user, TAG + "#getSecureLockDeviceAvailability");

            // Required for internal service to acquire necessary system permissions
            final long identity = Binder.clearCallingIdentity();
            try {
                return mSecureLockDeviceService.getSecureLockDeviceAvailability(user);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#enableSecureLockDevice(EnableSecureLockDeviceParams)
         * @param params EnableSecureLockDeviceParams for caller to supply params related
         *               to the secure lock device request
         * @param user user associated with the calling context to check for secure lock device
         *             availability
         * @return {@link EnableSecureLockDeviceRequestStatus} int indicating the result of the
         * Secure Lock Device request
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        @EnableSecureLockDeviceRequestStatus
        public int enableSecureLockDevice(UserHandle user, EnableSecureLockDeviceParams params) {
            enableSecureLockDevice_enforcePermission();
            enforceCrossUserPermission(user, TAG + "#enableSecureLockDevice");
            // Required for internal service to acquire necessary system permissions
            final long identity = Binder.clearCallingIdentity();
            try {
                return mSecureLockDeviceService.enableSecureLockDevice(user, params);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#disableSecureLockDevice(DisableSecureLockDeviceParams)
         * @param params @DisableSecureLockDeviceParams for caller to supply params related
         *               to the secure lock device request
         * @param user user associated with the calling context to verify it matches the user that
         *             enabled secure lock device
         * @return {@link DisableSecureLockDeviceRequestStatus} int indicating the result of the
         * Secure Lock Device request
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        @DisableSecureLockDeviceRequestStatus
        public int disableSecureLockDevice(UserHandle user, DisableSecureLockDeviceParams params) {
            disableSecureLockDevice_enforcePermission();
            enforceCrossUserPermission(user, TAG + "#disableSecureLockDevice");

            // Required for internal service to acquire necessary system permissions
            final long identity = Binder.clearCallingIdentity();
            try {
                boolean authenticationComplete =
                        mSecureLockDeviceService.hasUserCompletedTwoFactorAuthentication(user);
                Slog.d(TAG, "Disabling secure lock device: "
                        + "user " + user + ", authenticationComplete " + authenticationComplete);
                return mSecureLockDeviceService.disableSecureLockDevice(user, params,
                        /* authenticationComplete = */ authenticationComplete);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#isSecureLockDeviceEnabled()
         * @return true if secure lock device is enabled, false otherwise
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        public boolean isSecureLockDeviceEnabled() {
            isSecureLockDeviceEnabled_enforcePermission();
            // Required for internal service to acquire necessary system permissions
            final long identity = Binder.clearCallingIdentity();
            try {
                return mSecureLockDeviceService.isSecureLockDeviceEnabled();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#registerSecureLockDeviceStatusListener
         * @param user user associated with the calling context to notify of updates to secure
         *             lock device availability
         * @param listener to register for updates to secure lock device availability
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        public void registerSecureLockDeviceStatusListener(
                UserHandle user,
                @NonNull ISecureLockDeviceStatusListener listener
        ) {
            registerSecureLockDeviceStatusListener_enforcePermission();
            enforceCrossUserPermission(user, TAG
                    + "#registerSecureLockDeviceStatusListener");
            // Required for internal service to acquire necessary system permissions
            final long identity = Binder.clearCallingIdentity();
            try {
                mSecureLockDeviceService.registerSecureLockDeviceStatusListener(user, listener);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#unregisterSecureLockDeviceStatusListener
         * @param listener to unregister for updates to secure lock device availability
         */
        @Override
        @EnforcePermission(MANAGE_SECURE_LOCK_DEVICE)
        public void unregisterSecureLockDeviceStatusListener(
                @NonNull ISecureLockDeviceStatusListener listener
        ) {
            unregisterSecureLockDeviceStatusListener_enforcePermission();
            final long identity = Binder.clearCallingIdentity();
            try {
                mSecureLockDeviceService.unregisterSecureLockDeviceStatusListener(listener);
                if (DEBUG) {
                    Slog.d(TAG, "Unregistered listener: " + listener.asBinder());
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * @see AuthenticationPolicyManager#setSecureLockDeviceTestStatus(boolean)
         * @param isTestMode boolean indicating whether to enable test mode for secure lock device
         */
        @Override
        @EnforcePermission(TEST_BIOMETRIC)
        public void setSecureLockDeviceTestStatus(boolean isTestMode) {
            setSecureLockDeviceTestStatus_enforcePermission();
            mSecureLockDeviceService.setSecureLockDeviceTestStatus(isTestMode);
        }

        @Override
        @EnforcePermission(USE_BIOMETRIC_INTERNAL)
        public void startWatchRangingForIdentityCheck(long authenticationRequestId,
                @NonNull IProximityResultCallback proximityResultCallback) {
            startWatchRangingForIdentityCheck_enforcePermission();

            mWatchRangingService.startWatchRangingForIdentityCheck(authenticationRequestId,
                    proximityResultCallback);
        }

        @Override
        @EnforcePermission(USE_BIOMETRIC_INTERNAL)
        public void cancelWatchRangingForRequestId(long authenticationRequestId) {
            cancelWatchRangingForRequestId_enforcePermission();

            mWatchRangingService.cancelWatchRangingForRequestId(authenticationRequestId);
        }

        @Override
        @EnforcePermission(USE_BIOMETRIC_INTERNAL)
        public void isWatchRangingAvailable(
                @NonNull IProximityResultCallback proximityResultCallback) {
            isWatchRangingAvailable_enforcePermission();

            mWatchRangingService.isWatchRangingAvailable(proximityResultCallback);
        }

        @Override
        public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                @NonNull String[] args, ShellCallback callback,
                @NonNull ResultReceiver resultReceiver) {
            if (Build.IS_DEBUGGABLE) {
                if (Binder.getCallingUid() != Process.SHELL_UID) {
                    Slog.e(TAG, "Shell command called from non-shell UID: "
                            + Binder.getCallingUid());
                    resultReceiver.send(-1, null);
                    return;
                }
                (new AuthenticationPolicyServiceShellCommand(this, getContext()))
                        .exec(this, in, out, err, args, callback, resultReceiver);
            }
        }
    };
}