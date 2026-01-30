/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.locksettings;

import static android.security.Flags.FLAG_SECURE_LOCK_DEVICE;

import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_BOOT;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_LOCKOUT;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE;
import static com.android.server.locksettings.LockSettingsStrongAuth.DEFAULT_NON_STRONG_BIOMETRIC_IDLE_TIMEOUT_MS;
import static com.android.server.locksettings.LockSettingsStrongAuth.DEFAULT_NON_STRONG_BIOMETRIC_TIMEOUT_MS;
import static com.android.server.locksettings.LockSettingsStrongAuth.NON_STRONG_BIOMETRIC_IDLE_TIMEOUT_ALARM_TAG;
import static com.android.server.locksettings.LockSettingsStrongAuth.NON_STRONG_BIOMETRIC_TIMEOUT_ALARM_TAG;
import static com.android.server.locksettings.LockSettingsStrongAuth.STRONG_AUTH_TIMEOUT_ALARM_TAG;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.locksettings.LockSettingsStrongAuth.NonStrongBiometricIdleTimeoutAlarmListener;
import com.android.server.locksettings.LockSettingsStrongAuth.NonStrongBiometricTimeoutAlarmListener;
import com.android.server.locksettings.LockSettingsStrongAuth.StrongAuthTimeoutAlarmListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class LockSettingsStrongAuthTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public MockitoRule mockito = MockitoJUnit.rule();
    private static final String TAG = LockSettingsStrongAuthTest.class.getSimpleName();

    private static final int PRIMARY_USER_ID = 0;

    private LockSettingsStrongAuth mStrongAuth;
    private final int mDefaultStrongAuthFlags = STRONG_AUTH_NOT_REQUIRED;
    private final boolean mDefaultIsNonStrongBiometricAllowed = true;

    @Mock
    private Context mContext;
    @Mock
    private LockSettingsStrongAuth.Injector mInjector;
    @Mock
    private AlarmManager mAlarmManager;
    @Mock
    private DevicePolicyManager mDPM;

    @Before
    public void setUp() {
        when(mInjector.getAlarmManager(mContext)).thenReturn(mAlarmManager);
        when(mInjector.getDefaultStrongAuthFlags(mContext)).thenReturn(mDefaultStrongAuthFlags);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(mDPM);

        mStrongAuth = new LockSettingsStrongAuth(mContext, mInjector);
    }

    @Test
    public void testScheduleNonStrongBiometricIdleTimeout() {
        final long nextAlarmTime = 1000;
        when(mInjector.getNextAlarmTimeMs(DEFAULT_NON_STRONG_BIOMETRIC_IDLE_TIMEOUT_MS))
                .thenReturn(nextAlarmTime);
        mStrongAuth.scheduleNonStrongBiometricIdleTimeout(PRIMARY_USER_ID);

        waitForIdle();
        NonStrongBiometricIdleTimeoutAlarmListener alarm = mStrongAuth
                .mNonStrongBiometricIdleTimeoutAlarmListener.get(PRIMARY_USER_ID);
        // verify that a new alarm for idle timeout is added for the user
        assertNotNull(alarm);
        // verify that the alarm is scheduled
        verifyAlarm(nextAlarmTime, NON_STRONG_BIOMETRIC_IDLE_TIMEOUT_ALARM_TAG, alarm);
    }

    @Test
    public void testSetIsNonStrongBiometricAllowed_disallowed() {
        mStrongAuth.setIsNonStrongBiometricAllowed(false /* allowed */, PRIMARY_USER_ID);

        waitForIdle();
        // verify that unlocking with non-strong biometrics is not allowed
        assertFalse(mStrongAuth.mIsNonStrongBiometricAllowedForUser
                .get(PRIMARY_USER_ID, mDefaultIsNonStrongBiometricAllowed));
    }

    @Test
    public void testReportSuccessfulBiometricUnlock_nonStrongBiometric_fallbackTimeout() {
        final long nextAlarmTime = 1000;
        when(mInjector.getNextAlarmTimeMs(DEFAULT_NON_STRONG_BIOMETRIC_TIMEOUT_MS))
                .thenReturn(nextAlarmTime);
        mStrongAuth.reportSuccessfulBiometricUnlock(false /* isStrongBiometric */, PRIMARY_USER_ID);

        waitForIdle();
        NonStrongBiometricTimeoutAlarmListener alarm =
                mStrongAuth.mNonStrongBiometricTimeoutAlarmListener.get(PRIMARY_USER_ID);
        // verify that a new alarm for fallback timeout is added for the user
        assertNotNull(alarm);
        // verify that the alarm is scheduled
        verifyAlarm(nextAlarmTime, NON_STRONG_BIOMETRIC_TIMEOUT_ALARM_TAG, alarm);
    }

    @Test
    public void testRequireStrongAuth_nonStrongBiometric_fallbackTimeout() {
        mStrongAuth.requireStrongAuth(
                STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT /* strongAuthReason */,
                PRIMARY_USER_ID);

        waitForIdle();
        // verify that the StrongAuthFlags for the user contains the expected flag
        final int expectedFlag = STRONG_AUTH_REQUIRED_AFTER_NON_STRONG_BIOMETRICS_TIMEOUT;
        verifyStrongAuthFlags(expectedFlag, PRIMARY_USER_ID);
    }

    private void enableSecureLockDevice() {
        mStrongAuth.requireStrongAuth(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
        mStrongAuth.requireStrongAuth(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthChanges_whenSecureLockDeviceEnabled() {
        setupAlarms(PRIMARY_USER_ID);
        enableSecureLockDevice();
        waitForIdle();

        // verify that the StrongAuthFlags for the user contains the expected flags
        verifyStrongAuthFlags(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE, PRIMARY_USER_ID);
        verifyStrongAuthFlags(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);

        // Non-strong biometrics disabled by STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
        verifyIfNonStrongBiometricsAllowed(PRIMARY_USER_ID, false);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthChanges_onPrimaryAuthInSecureLockDeviceMode() {
        setupAlarms(PRIMARY_USER_ID);
        enableSecureLockDevice();
        waitForIdle();

        // On successful primary auth
        mStrongAuth.reportSuccessfulPrimaryAuthInSecureLockDeviceMode(PRIMARY_USER_ID);
        waitForIdle();

        // Verify that unlocking with primary auth (PIN/pattern/password) does not cancel alarms
        // for fallback and idle timeout and does not re-allow unlocking with non-strong biometric
        verifyAlarmsNotCancelled(PRIMARY_USER_ID);
        verifyIfNonStrongBiometricsAllowed(PRIMARY_USER_ID, false);

        // Expect PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag is unset
        verifyStrongAuthFlagNotSet(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE, PRIMARY_USER_ID);

        // Expect STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag still set
        verifyStrongAuthFlags(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthChanges_onSuccessfulBiometricAuthInSecureLockDeviceMode() {
        setupAlarms(PRIMARY_USER_ID);
        enableSecureLockDevice();
        mStrongAuth.reportSuccessfulPrimaryAuthInSecureLockDeviceMode(PRIMARY_USER_ID);
        waitForIdle();

        mStrongAuth.reportSuccessfulBiometricUnlock(true /* isStrongBiometric */, PRIMARY_USER_ID);
        mStrongAuth.disableSecureLockDevice(PRIMARY_USER_ID, true);
        waitForIdle();

        // verify that unlocking with strong biometric cancels alarms for fallback and idle timeout
        // and re-allow unlocking with non-strong biometric
        verifyAlarmsCancelledAndNonStrongBiometricAllowed(PRIMARY_USER_ID);

        // Expect STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag is unset
        verifyStrongAuthFlagNotSet(
                STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE, PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthChanges_onSecureLockDeviceModeManuallyDisabled() {
        setupAlarms(PRIMARY_USER_ID);
        enableSecureLockDevice();
        waitForIdle();

        mStrongAuth.disableSecureLockDevice(PRIMARY_USER_ID, /* authenticationComplete= */ false);
        waitForIdle();

        // Expect default flags are set
        final int userFlags = mStrongAuth.mStrongAuthForUser.get(PRIMARY_USER_ID,
                mDefaultStrongAuthFlags);
        assertEquals(mDefaultStrongAuthFlags, userFlags);

        // Verify that manually disabling secure lock device does not cancel alarms for fallback and
        // idle timeout
        verifyAlarmsNotCancelled(PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthAfterBootFlagUnsetOnPrimaryAuth_whenSecureLockDeviceEnabled() {
        // Set secure lock device flags
        mStrongAuth.requireStrongAuth(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
        // Verify setting secure lock device strong biometric auth flag
        mStrongAuth.requireStrongAuth(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);

        // Mock device rebooted, STRONG_AUTH_REQUIRED_AFTER_BOOT flag set
        mStrongAuth.requireStrongAuth(STRONG_AUTH_REQUIRED_AFTER_BOOT, PRIMARY_USER_ID);
        waitForIdle();

        mStrongAuth.reportSuccessfulPrimaryAuthInSecureLockDeviceMode(PRIMARY_USER_ID);
        waitForIdle();

        // Expect PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag is unset
        verifyStrongAuthFlagNotSet(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE, PRIMARY_USER_ID);

        // Expect STRONG_AUTH_REQUIRED_AFTER_BOOT flag is unset
        verifyStrongAuthFlagNotSet(STRONG_AUTH_REQUIRED_AFTER_BOOT, PRIMARY_USER_ID);

        // Expect STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag still set
        verifyStrongAuthFlags(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
    }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    public void testStrongAuthAfterLockoutFlagUnsetOnPrimaryAuth_whenSecureLockDeviceEnabled() {
        // Set secure lock device flags
        mStrongAuth.requireStrongAuth(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
        // Verify setting secure lock device strong biometric auth flag
        mStrongAuth.requireStrongAuth(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);

        // Mock lockout event, STRONG_AUTH_REQUIRED_AFTER_LOCKOUT flag set
        mStrongAuth.requireStrongAuth(STRONG_AUTH_REQUIRED_AFTER_LOCKOUT, PRIMARY_USER_ID);
        waitForIdle();

        // On successful primary auth
        mStrongAuth.reportSuccessfulPrimaryAuthInSecureLockDeviceMode(PRIMARY_USER_ID);
        waitForIdle();

        // Expect PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag is unset
        verifyStrongAuthFlagNotSet(PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE, PRIMARY_USER_ID);

        // Expect STRONG_AUTH_REQUIRED_AFTER_LOCKOUT flag is unset
        verifyStrongAuthFlagNotSet(STRONG_AUTH_REQUIRED_AFTER_LOCKOUT, PRIMARY_USER_ID);

        // Expect STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE flag still set
        verifyStrongAuthFlags(STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                PRIMARY_USER_ID);
    }

    @Test
    public void testReportSuccessfulBiometricUnlock_nonStrongBiometric_cancelIdleTimeout() {
        // lock device and schedule an alarm for non-strong biometric idle timeout
        mStrongAuth.scheduleNonStrongBiometricIdleTimeout(PRIMARY_USER_ID);
        // unlock with non-strong biometric
        mStrongAuth.reportSuccessfulBiometricUnlock(false /* isStrongBiometric */, PRIMARY_USER_ID);

        waitForIdle();

        // verify that the current alarm for idle timeout is cancelled after a successful unlock
        verify(mAlarmManager).cancel(any(NonStrongBiometricIdleTimeoutAlarmListener.class));
    }

    @Test
    public void testReportSuccessfulBiometricUnlock_strongBio_cancelAlarmsAndAllowNonStrongBio() {
        setupAlarms(PRIMARY_USER_ID);
        mStrongAuth.reportSuccessfulBiometricUnlock(true /* isStrongBiometric */, PRIMARY_USER_ID);

        waitForIdle();
        // verify that unlocking with strong biometric cancels alarms for fallback and idle timeout
        // and re-allow unlocking with non-strong biometric
        verifyAlarmsCancelledAndNonStrongBiometricAllowed(PRIMARY_USER_ID);
    }

    @Test
    public void testReportSuccessfulStrongAuthUnlock_schedulePrimaryAuthTimeout() {
        final long currentTime = 1000;
        final long timeout = 1000;
        final long nextAlarmTime = currentTime + timeout;
        when(mInjector.getElapsedRealtimeMs()).thenReturn(currentTime);
        when(mDPM.getRequiredStrongAuthTimeout(null, PRIMARY_USER_ID)).thenReturn(timeout);
        mStrongAuth.reportSuccessfulStrongAuthUnlock(PRIMARY_USER_ID);

        waitForIdle();
        StrongAuthTimeoutAlarmListener alarm =
                mStrongAuth.mStrongAuthTimeoutAlarmListenerForUser.get(PRIMARY_USER_ID);
        // verify that a new alarm for primary auth timeout is added for the user
        assertNotNull(alarm);
        // verify that the alarm is scheduled
        verifyAlarm(nextAlarmTime, STRONG_AUTH_TIMEOUT_ALARM_TAG, alarm);
    }

    @Test
    public void testReportSuccessfulStrongAuthUnlock_testRefreshStrongAuthTimeout() {
        final long currentTime = 1000;
        final long oldTimeout = 5000;
        final long nextAlarmTime = currentTime + oldTimeout;
        when(mInjector.getElapsedRealtimeMs()).thenReturn(currentTime);
        when(mDPM.getRequiredStrongAuthTimeout(null, PRIMARY_USER_ID)).thenReturn(oldTimeout);
        mStrongAuth.reportSuccessfulStrongAuthUnlock(PRIMARY_USER_ID);
        waitForIdle();

        StrongAuthTimeoutAlarmListener alarm =
                mStrongAuth.mStrongAuthTimeoutAlarmListenerForUser.get(PRIMARY_USER_ID);
        assertEquals(currentTime, alarm.getLatestStrongAuthTime());
        verifyAlarm(nextAlarmTime, STRONG_AUTH_TIMEOUT_ALARM_TAG, alarm);

        final long newTimeout = 3000;
        when(mDPM.getRequiredStrongAuthTimeout(null, PRIMARY_USER_ID)).thenReturn(newTimeout);
        mStrongAuth.refreshStrongAuthTimeout(PRIMARY_USER_ID);
        waitForIdle();
        verify(mAlarmManager).cancel(alarm);
        verifyAlarm(currentTime + newTimeout, STRONG_AUTH_TIMEOUT_ALARM_TAG, alarm);
    }

    @Test
    public void testReportSuccessfulStrongAuthUnlock_cancelAlarmsAndAllowNonStrongBio() {
        setupAlarms(PRIMARY_USER_ID);
        mStrongAuth.reportSuccessfulStrongAuthUnlock(PRIMARY_USER_ID);

        waitForIdle();
        // verify that unlocking with primary auth (PIN/pattern/password) cancels alarms
        // for fallback and idle timeout and re-allow unlocking with non-strong biometric
        verifyAlarmsCancelledAndNonStrongBiometricAllowed(PRIMARY_USER_ID);
    }

    @Test
    public void testFallbackTimeout_convenienceBiometric_weakBiometric() {
        // assume that unlock with convenience biometric
        mStrongAuth.reportSuccessfulBiometricUnlock(false /* isStrongBiometric */, PRIMARY_USER_ID);
        // assume that unlock again with weak biometric
        mStrongAuth.reportSuccessfulBiometricUnlock(false /* isStrongBiometric */, PRIMARY_USER_ID);

        waitForIdle();
        // verify that the fallback alarm scheduled when unlocking with convenience biometric is
        // not affected when unlocking again with weak biometric
        verify(mAlarmManager, never()).cancel(any(NonStrongBiometricTimeoutAlarmListener.class));
        assertNotNull(mStrongAuth.mNonStrongBiometricTimeoutAlarmListener.get(PRIMARY_USER_ID));
    }

    private void verifyAlarm(long when, String tag, AlarmManager.OnAlarmListener alarm) {
        verify(mAlarmManager).setExact(
                eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                eq(when),
                eq(tag),
                eq(alarm),
                eq(mStrongAuth.mHandler));
    }

    private void verifyStrongAuthFlags(int reason, int userId) {
        final int flags = mStrongAuth.mStrongAuthForUser.get(userId, mDefaultStrongAuthFlags);
        Log.d(TAG, "verifyStrongAuthFlags:"
                + " reason=" + Integer.toHexString(reason)
                + " userId=" + userId
                + " flags=" + Integer.toHexString(flags));
        assertTrue(containsFlag(flags, reason));
    }

    private void verifyStrongAuthFlagNotSet(int reason, int userId) {
        final int flags = mStrongAuth.mStrongAuthForUser.get(userId, mDefaultStrongAuthFlags);
        Log.d(TAG, "verifyStrongAuthFlagNotSet:"
                + " reason=" + Integer.toHexString(reason)
                + " userId=" + userId
                + " flags=" + Integer.toHexString(flags));
        assertFalse(containsFlag(flags, reason));
    }

    private void setupAlarms(int userId) {
        // Used to verify that unlocking with strong biometric or primary auth will cancel
        // non strong biometric timeout alarms
        setupNonStrongBiometricFallbackTimeoutAlarm(userId);
        setupNonStrongBiometricIdleTimeoutAlarm(userId);
    }

    private void setupNonStrongBiometricFallbackTimeoutAlarm(int userId) {
        // schedule an alarm for non-strong biometric fallback timeout, so later we can verify that
        // unlocking with strong biometric or primary auth will cancel that alarm
        mStrongAuth.reportSuccessfulBiometricUnlock(false /* isStrongBiometric */, userId);
    }

    private void setupNonStrongBiometricIdleTimeoutAlarm(int userId) {
        // schedule an alarm for non-strong biometric idle timeout, so later we can verify that
        // unlocking with strong biometric or primary auth will cancel that alarm
        mStrongAuth.scheduleNonStrongBiometricIdleTimeout(userId);
    }

    private void verifyAlarmsCancelledAndNonStrongBiometricAllowed(int userId) {
        // verify that the current alarm for non-strong biometric fallback timeout is cancelled and
        // removed
        verify(mAlarmManager, atLeastOnce()).cancel(
                any(NonStrongBiometricTimeoutAlarmListener.class));
        assertNull(mStrongAuth.mNonStrongBiometricTimeoutAlarmListener.get(userId));

        // verify that the current alarm for non-strong biometric idle timeout is cancelled
        verify(mAlarmManager, atLeastOnce()).cancel(
                any(NonStrongBiometricIdleTimeoutAlarmListener.class));

        // verify that unlocking with non-strong biometrics is allowed
        verifyIfNonStrongBiometricsAllowed(userId, true);
    }

    private void verifyAlarmsNotCancelled(int userId) {
        // verify that the current alarm for non-strong biometric fallback timeout is not
        // cancelled or removed
        verify(mAlarmManager, never()).cancel(any(NonStrongBiometricTimeoutAlarmListener.class));
        assertNotNull(mStrongAuth.mNonStrongBiometricTimeoutAlarmListener.get(userId));

        // verify that the current alarm for non-strong biometric idle timeout is not cancelled
        verify(mAlarmManager, never())
                .cancel(any(NonStrongBiometricIdleTimeoutAlarmListener.class));
    }

    private void verifyIfNonStrongBiometricsAllowed(int userId, boolean isAllowed) {
        if (isAllowed) {
            assertTrue(mStrongAuth.mIsNonStrongBiometricAllowedForUser
                    .get(userId, mDefaultIsNonStrongBiometricAllowed));
        } else {
            assertFalse(mStrongAuth.mIsNonStrongBiometricAllowedForUser
                    .get(userId, mDefaultIsNonStrongBiometricAllowed));
        }
    }

    private static boolean containsFlag(int haystack, int needle) {
        return (haystack & needle) != 0;
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
