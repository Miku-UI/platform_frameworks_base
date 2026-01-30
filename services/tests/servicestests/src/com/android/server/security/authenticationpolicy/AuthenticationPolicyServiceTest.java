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

import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_ERROR_LOCKOUT;
import static android.security.Flags.FLAG_SECURE_LOCKDOWN;
import static android.security.Flags.FLAG_SECURE_LOCK_DEVICE;
import static android.security.Flags.secureLockdown;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.SUCCESS;

import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.biometrics.AuthenticationStateListener;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricRequestConstants;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.biometrics.events.AuthenticationErrorInfo;
import android.hardware.biometrics.events.AuthenticationFailedInfo;
import android.hardware.biometrics.events.AuthenticationSucceededInfo;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockPatternUtils;
import com.android.server.LocalServices;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.locksettings.LockSettingsStateListener;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * atest FrameworksServicesTests:AuthenticationPolicyServiceTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AuthenticationPolicyServiceTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    private static final int PRIMARY_USER_ID = 0;
    private static final int MANAGED_PROFILE_USER_ID = 12;
    private static final int MAX_ALLOWED_FAILED_AUTH_ATTEMPTS = 5;
    private static final int DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS = 0;

    private Context mContext;
    private Resources mResources;
    private AuthenticationPolicyService mAuthenticationPolicyService;

    @Mock
    LockPatternUtils mLockPatternUtils;
    @Mock
    private LockSettingsInternal mLockSettings;
    @Mock
    private BiometricManager mBiometricManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private WindowManagerInternal mWindowManager;
    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private SecureLockDeviceServiceInternal mSecureLockDeviceService;
    @Mock
    private WatchRangingServiceInternal mWatchRangingService;

    @Captor
    ArgumentCaptor<LockSettingsStateListener> mLockSettingsStateListenerCaptor;
    @Captor
    ArgumentCaptor<AuthenticationStateListener> mAuthenticationStateListenerCaptor;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mResources = spy(mContext.getResources());

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_enableFailedAuthLock)).thenReturn(true);
        when(mResources.getInteger(
                com.android.internal.R.integer.config_maxAllowedFailedAuthAttempts))
                .thenReturn(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_enableFailedAuthLockToggle)).thenReturn(true);

        assumeTrue("Adaptive auth is disabled on device",
                !mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        when(mContext.getSystemService(BiometricManager.class)).thenReturn(mBiometricManager);
        when(mContext.getSystemService(KeyguardManager.class)).thenReturn(mKeyguardManager);

        LocalServices.removeServiceForTest(LockSettingsInternal.class);
        LocalServices.addService(LockSettingsInternal.class, mLockSettings);
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mWindowManager);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.removeServiceForTest(WatchRangingServiceInternal.class);
        LocalServices.addService(WatchRangingServiceInternal.class, mWatchRangingService);

        if (secureLockdown()) {
            LocalServices.removeServiceForTest(SecureLockDeviceServiceInternal.class);
            LocalServices.addService(SecureLockDeviceServiceInternal.class,
                    mSecureLockDeviceService);
        }

        mAuthenticationPolicyService = new AuthenticationPolicyService(mContext, mLockPatternUtils);
        mAuthenticationPolicyService.init();

        verify(mLockSettings).registerLockSettingsStateListener(
                mLockSettingsStateListenerCaptor.capture());
        verify(mBiometricManager).registerAuthenticationStateListener(
                mAuthenticationStateListenerCaptor.capture());

        // Set PRIMARY_USER_ID as the parent of MANAGED_PROFILE_USER_ID
        when(mUserManager.getProfileParentId(eq(MANAGED_PROFILE_USER_ID)))
                .thenReturn(PRIMARY_USER_ID);

        if (android.security.Flags.secureLockdown()) {
            when(mSecureLockDeviceService.enableSecureLockDevice(eq(UserHandle.of(PRIMARY_USER_ID)),
                    any())).thenReturn(SUCCESS);
            when(mSecureLockDeviceService.disableSecureLockDevice(
                    eq(UserHandle.of(PRIMARY_USER_ID)), any(), anyBoolean())).thenReturn(SUCCESS);
        }

        toggleAdaptiveAuthSettingsOverride(PRIMARY_USER_ID, false /* disable */);
    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(LockSettingsInternal.class);
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        if (secureLockdown()) {
            LocalServices.removeServiceForTest(SecureLockDeviceServiceInternal.class);
        }
        toggleAdaptiveAuthSettingsOverride(PRIMARY_USER_ID, false /* disable */);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_FAILED_AUTH_LOCK_TOGGLE})
    public void testConfig_failedAuthLock_whenDisabled() throws RemoteException {
        // The feature is disabled in config. In this case, the toggle config value (true or false)
        // doesn't matter
        clearSettingsAndInitService(false /* featureEnabled */, true /* toggleEnabled */);

        // Verify that the setting was not written
        assertThrows(Settings.SettingNotFoundException.class, () -> {
            Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK, PRIMARY_USER_ID);
        });

        // Five failed biometric auth attempts
        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        // Verify that there are no reported failed auth attempts and that the device is never
        // locked, because the failed auth lock feature is completely disabled in config
        verifyNotLockDevice(DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_FAILED_AUTH_LOCK_TOGGLE})
    @DisableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testConfig_failedAuthLockToggle_whenDisabled() throws RemoteException {
        // The feature is enabled, but the toggle is disabled in config
        clearSettingsAndInitService(true /* featureEnabled */, false /* toggleEnabled */);

        // Verify that the setting was not written because the toggle is disabled
        assertThrows(Settings.SettingNotFoundException.class, () -> {
            Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK, PRIMARY_USER_ID);
        });

        // Five failed biometric auth attempts
        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        // Verify that the device is locked, because the toggle is disabled in config, and thus the
        // feature can't be disabled by users in settings on non-debuggable builds
        verifyAdaptiveAuthLocksDevice(PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_FAILED_AUTH_LOCK_TOGGLE})
    @DisableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testConfig_failedAuthLockToggle_whenEnabled() throws RemoteException {
        // The feature and the toggle are enabled in config
        clearSettingsAndInitService(true /* featureEnabled */, true /* toggleEnabled */);

        // Verify that the setting was written with the default value (0 means the feature is
        // enabled)
        assertEquals(0, Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK, -1, PRIMARY_USER_ID));

        // Five failed biometric auth attempts
        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        // Verify that the device is locked
        verifyAdaptiveAuthLocksDevice(PRIMARY_USER_ID);
    }

    private void clearSettingsAndInitService(boolean featureEnabled, boolean toggleEnabled) {
        // Ensure that the setting does not exist beforehand to test the initialization logic
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK, null, PRIMARY_USER_ID);

        // Change the feature and the toggle configs
        when(mResources.getBoolean(com.android.internal.R.bool.config_enableFailedAuthLock))
                .thenReturn(featureEnabled);
        when(mResources.getBoolean(com.android.internal.R.bool.config_enableFailedAuthLockToggle))
                .thenReturn(toggleEnabled);

        // Re-initialize the service to trigger setting initialization
        mAuthenticationPolicyService = new AuthenticationPolicyService(mContext, mLockPatternUtils);
        mAuthenticationPolicyService.init();

        // Re-capture the listener from the new service instance
        verify(mBiometricManager, atLeastOnce()).registerAuthenticationStateListener(
                mAuthenticationStateListenerCaptor.capture());
    }

    @Test
    public void testReportAuthAttempt_primaryAuthSucceeded()
            throws RemoteException {
        mLockSettingsStateListenerCaptor.getValue().onAuthenticationSucceeded(PRIMARY_USER_ID);
        waitForAuthCompletion();

        verifyNotLockDevice(DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_primaryAuthFailed_once()
            throws RemoteException {
        mLockSettingsStateListenerCaptor.getValue().onAuthenticationFailed(PRIMARY_USER_ID);
        waitForAuthCompletion();

        verifyNotLockDevice(1 /* expectedCntFailedAttempts */, PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_primaryAuthFailed_multiple_deviceCurrentlyLocked()
            throws RemoteException {
        // Device is currently locked and Keyguard is showing
        when(mKeyguardManager.isDeviceLocked(PRIMARY_USER_ID)).thenReturn(true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);

        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mLockSettingsStateListenerCaptor.getValue().onAuthenticationFailed(PRIMARY_USER_ID);
        }
        waitForAuthCompletion();

        verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_primaryAuthFailed_multiple_deviceCurrentlyNotLocked()
            throws RemoteException {
        // Device is currently not locked and Keyguard is not showing
        when(mKeyguardManager.isDeviceLocked(PRIMARY_USER_ID)).thenReturn(false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);

        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mLockSettingsStateListenerCaptor.getValue().onAuthenticationFailed(PRIMARY_USER_ID);
        }
        waitForAuthCompletion();

        verifyAdaptiveAuthLocksDevice(PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_biometricAuthSucceeded()
            throws RemoteException {
        mAuthenticationStateListenerCaptor.getValue().onAuthenticationSucceeded(
                authSuccessInfo(PRIMARY_USER_ID));
        waitForAuthCompletion();

        verifyNotLockDevice(DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags({FLAG_SECURE_LOCK_DEVICE, FLAG_SECURE_LOCKDOWN})
    public void testSecureLockDeviceServiceNotified_onSuccessfulBiometricAuth()
            throws RemoteException {
        when(mSecureLockDeviceService.isSecureLockDeviceEnabled()).thenReturn(true);
        mAuthenticationStateListenerCaptor.getValue().onAuthenticationSucceeded(
                authSuccessInfo(PRIMARY_USER_ID));
        waitForAuthCompletion();
        verify(mSecureLockDeviceService).onStrongBiometricAuthenticationSuccess(
                eq(UserHandle.of(PRIMARY_USER_ID)));
    }

    @Test
    @EnableFlags({FLAG_SECURE_LOCK_DEVICE, FLAG_SECURE_LOCKDOWN})
    public void testSecureLockDeviceResetsPrimaryAuthFlag_onBiometricLockout()
            throws RemoteException {
        when(mSecureLockDeviceService.isSecureLockDeviceEnabled()).thenReturn(true);
        mAuthenticationStateListenerCaptor.getValue().onAuthenticationError(
                authErrorInfo(BIOMETRIC_ERROR_LOCKOUT));
        waitForAuthCompletion();
        verify(mLockPatternUtils).requireStrongAuth(
                eq(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE), eq(UserHandle.USER_ALL));
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testAdaptiveAuthDisabled_whenSecureLockDeviceEnabled()
            throws RemoteException {
        // Secure lock device is enabled
        when(mSecureLockDeviceService.isSecureLockDeviceEnabled()).thenReturn(true);

        // Device is currently locked and Keyguard is showing
        when(mKeyguardManager.isDeviceLocked(PRIMARY_USER_ID)).thenReturn(true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);

        // Fail biometric auth 5 times
        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_biometricAuthFailed_once()
            throws RemoteException {
        mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                authFailedInfo(PRIMARY_USER_ID));
        waitForAuthCompletion();

        verifyNotLockDevice(1 /* expectedCntFailedAttempts */, PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyLocked()
            throws RemoteException {
        // Device is currently locked and Keyguard is showing
        when(mKeyguardManager.isDeviceLocked(PRIMARY_USER_ID)).thenReturn(true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);

        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyNotLocked_deviceLockEnabled()
            throws RemoteException {
        testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyNotLocked(
                true /* enabled */);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyNotLocked_deviceLockDisabled()
            throws RemoteException {
        toggleAdaptiveAuthSettingsOverride(PRIMARY_USER_ID, true /* disabled */);
        testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyNotLocked(
                false /* enabled */);
    }

    private void testReportAuthAttempt_biometricAuthFailed_multiple_deviceCurrentlyNotLocked(
            boolean enabled) throws RemoteException {
        // Device is currently not locked and Keyguard is not showing
        when(mKeyguardManager.isDeviceLocked(PRIMARY_USER_ID)).thenReturn(false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);

        for (int i = 0; i < MAX_ALLOWED_FAILED_AUTH_ATTEMPTS; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        if (enabled) {
            verifyAdaptiveAuthLocksDevice(PRIMARY_USER_ID);
        } else {
            verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS, PRIMARY_USER_ID);
        }
    }

    @Test
    public void testReportAuthAttempt_biometricAuthFailedThenPrimaryAuthSucceeded()
            throws RemoteException {
        // Three failed biometric auth attempts
        for (int i = 0; i < 3; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        // One successful primary auth attempt
        mLockSettingsStateListenerCaptor.getValue().onAuthenticationSucceeded(PRIMARY_USER_ID);
        waitForAuthCompletion();

        verifyNotLockDevice(DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    public void testReportAuthAttempt_primaryAuthFailedThenBiometricAuthSucceeded()
            throws RemoteException {
        // Three failed primary auth attempts
        for (int i = 0; i < 3; i++) {
            mLockSettingsStateListenerCaptor.getValue().onAuthenticationFailed(PRIMARY_USER_ID);
        }
        // One successful biometric auth attempt
        mAuthenticationStateListenerCaptor.getValue().onAuthenticationSucceeded(
                authSuccessInfo(PRIMARY_USER_ID));
        waitForAuthCompletion();

        verifyNotLockDevice(DEFAULT_COUNT_FAILED_AUTH_ATTEMPTS /* expectedCntFailedAttempts */,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser_deviceLockEnabled()
            throws RemoteException {
        testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser(
                true /* enabled */);
    }

    @Test
    @EnableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    public void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser_deviceLockDisabled()
            throws RemoteException {
        toggleAdaptiveAuthSettingsOverride(PRIMARY_USER_ID, true /* disabled */);
        testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser(
                false /* enabled */);
    }

    @Test
    @DisableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    @EnableFlags({android.security.Flags.FLAG_FAILED_AUTH_LOCK_TOGGLE})
    public void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUserAndProfile_deviceLockDisabled()
            throws RemoteException {
        // The failed auth lock toggle for the primary user is set to disable
        toggleAdaptiveAuthSettingsOverride(PRIMARY_USER_ID, true /* disabled */);
        // The failed auth lock toggle for the managed profile is set to enable
        toggleAdaptiveAuthSettingsOverride(MANAGED_PROFILE_USER_ID, false /* disabled */);

        // Device lock should be disabled for both the primary user and its profile
        testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser(
                false /* enabled */);
        testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_profileOfPrimaryUser(
                false /* enabled */);
    }

    private void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_primaryUser(
            boolean enabled) throws RemoteException {
        // Three failed primary auth attempts
        for (int i = 0; i < 3; i++) {
            mLockSettingsStateListenerCaptor.getValue().onAuthenticationFailed(PRIMARY_USER_ID);
        }
        // Two failed biometric auth attempts
        for (int i = 0; i < 2; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(PRIMARY_USER_ID));
        }
        waitForAuthCompletion();

        if (enabled) {
            verifyAdaptiveAuthLocksDevice(PRIMARY_USER_ID);
        } else {
            verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS, PRIMARY_USER_ID);
        }
    }

    @Test
    @DisableFlags({android.security.Flags.FLAG_DISABLE_ADAPTIVE_AUTH_COUNTER_LOCK})
    @EnableFlags({android.security.Flags.FLAG_FAILED_AUTH_LOCK_TOGGLE})
    public void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_profile_deviceLockEnabled()
            throws RemoteException {
        // The failed auth lock toggle for the primary user is set to enable in test setup, so the
        // toggle for the managed profile is implicitly set to enable

        // Device lock should be enabled for the managed profile
        testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_profileOfPrimaryUser(
                true /* enabled */);
    }

    private void testReportAuthAttempt_primaryAuthAndBiometricAuthFailed_profileOfPrimaryUser(
            boolean enabled) throws RemoteException {
        // Three failed primary auth attempts
        for (int i = 0; i < 3; i++) {
            mLockSettingsStateListenerCaptor.getValue()
                    .onAuthenticationFailed(MANAGED_PROFILE_USER_ID);
        }
        // Two failed biometric auth attempts
        for (int i = 0; i < 2; i++) {
            mAuthenticationStateListenerCaptor.getValue().onAuthenticationFailed(
                    authFailedInfo(MANAGED_PROFILE_USER_ID));
        }
        waitForAuthCompletion();

        if (enabled) {
            verifyAdaptiveAuthLocksDevice(MANAGED_PROFILE_USER_ID);
        } else {
            verifyNotLockDevice(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS, MANAGED_PROFILE_USER_ID);
        }
    }

    private void verifyNotLockDevice(int expectedCntFailedAttempts, int userId) {
        assertEquals(expectedCntFailedAttempts,
                mAuthenticationPolicyService.mFailedAttemptsForUser.get(userId));
        verify(mWindowManager, never()).lockNow();
    }

    private void verifyAdaptiveAuthLocksDevice(int userId) {
        assertEquals(MAX_ALLOWED_FAILED_AUTH_ATTEMPTS,
                mAuthenticationPolicyService.mFailedAttemptsForUser.get(userId));
        verify(mLockPatternUtils).requireStrongAuth(
                eq(SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST), eq(userId));
        // If userId is MANAGED_PROFILE_USER_ID, the StrongAuthFlag of its parent (PRIMARY_USER_ID)
        // should also be verified
        if (userId == MANAGED_PROFILE_USER_ID) {
            verify(mLockPatternUtils).requireStrongAuth(
                    eq(SOME_AUTH_REQUIRED_AFTER_ADAPTIVE_AUTH_REQUEST), eq(PRIMARY_USER_ID));
        }
        verify(mWindowManager).lockNow();
    }

    /**
     * Wait for all auth events to complete before verification
     */
    private static void waitForAuthCompletion() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private AuthenticationSucceededInfo authSuccessInfo(int userId) {
        return new AuthenticationSucceededInfo.Builder(BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_UNKNOWN, true, userId).build();
    }

    private AuthenticationErrorInfo authErrorInfo(int errCode) {
        return new AuthenticationErrorInfo.Builder(BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_UNKNOWN, "errString", errCode).build();
    }

    private AuthenticationFailedInfo authFailedInfo(int userId) {
        return new AuthenticationFailedInfo.Builder(BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_UNKNOWN, userId).build();
    }

    private void toggleAdaptiveAuthSettingsOverride(int userId, boolean disable) {
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.DISABLE_ADAPTIVE_AUTH_LIMIT_LOCK, disable ? 1 : 0, userId);
    }
}
