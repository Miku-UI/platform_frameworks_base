/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wm;

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.wm.DeviceStateAutoRotateSettingIssueLogger.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_THRESHOLD_MILLIS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.wm.utils.CurrentTimeMillisSupplierFake;
import com.android.server.wm.utils.DeviceStateTestUtils;
import com.android.settingslib.devicestate.SecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

/**
 * Test class for {@link DeviceStateAutoRotateSettingIssueLogger}.
 *
 * <p>Build/Install/Run: atest WmTests:DeviceStateAutoRotateSettingIssueLoggerTests
 */
@SmallTest
@Presubmit
public class DeviceStateAutoRotateSettingIssueLoggerTests {
    private static final int DELAY = 500;

    private final Answer<Boolean> mRunRunnableAnswer = invocation -> {
        Runnable runnable = (Runnable) invocation.getArguments()[0];
        runnable.run();
        return null;
    };

    @Mock
    private SecureSettings mMockSecureSettings;
    @Mock
    private Handler mMockHandler;
    @Mock
    private DeviceStateController mMockDeviceStateController;

    @Captor
    private ArgumentCaptor<ContentObserver> mContentObserverCaptor;
    @Captor
    private ArgumentCaptor<DeviceStateController.DeviceStateListener> mDeviceStateListenerCaptor;

    private StaticMockitoSession mStaticMockitoSession;
    @NonNull
    private CurrentTimeMillisSupplierFake mTestTimeSupplier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mStaticMockitoSession = mockitoSession().mockStatic(
                FrameworkStatsLog.class).startMocking();
        when(mMockHandler.post(any(Runnable.class))).thenAnswer(mRunRunnableAnswer);
        mTestTimeSupplier = new CurrentTimeMillisSupplierFake();


        new DeviceStateAutoRotateSettingIssueLogger(mTestTimeSupplier, mMockSecureSettings,
                mMockDeviceStateController, mMockHandler);

        verify(mMockSecureSettings, atLeastOnce()).registerContentObserver(
                eq(DEVICE_STATE_ROTATION_LOCK), /* notifyForDescendants= */ eq(false),
                mContentObserverCaptor.capture(), eq(UserHandle.USER_CURRENT));
        verify(mMockDeviceStateController).registerDeviceStateCallback(
                mDeviceStateListenerCaptor.capture(), any());
    }

    @After
    public void teardown() {
        mStaticMockitoSession.finishMocking();
    }

    @Test
    public void onStateChange_deviceStateChangedFirst_isDeviceStateFirstTrue() {
        notifyDeviceStateChanged();
        mTestTimeSupplier.delay(DELAY);
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        eq(true)));
    }

    @Test
    public void onStateChange_autoRotateSettingChangedFirst_isDeviceStateFirstFalse() {
        mContentObserverCaptor.getValue().onChange(false);
        mTestTimeSupplier.delay(DELAY);
        notifyDeviceStateChanged();

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        eq(false)));
    }

    @Test
    public void onStateChange_deviceStateDidNotChange_doNotReport() {
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        anyBoolean()), never());
    }

    @Test
    public void onStateChange_autoRotateSettingDidNotChange_doNotReport() {
        notifyDeviceStateChanged();

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        anyBoolean()), never());
    }

    @Test
    public void onStateChange_issueOccurred_correctDurationReported() {
        notifyDeviceStateChanged();
        mTestTimeSupplier.delay(DELAY);
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        eq(DELAY),
                        anyBoolean()));
    }

    @Test
    public void onStateChange_durationLongerThanThreshold_doNotReport() {
        notifyDeviceStateChanged();
        mTestTimeSupplier.delay(
                DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_THRESHOLD_MILLIS + DELAY);
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        anyBoolean()), never());
    }

    @Test
    public void onStateChange_issueOccurredSettingChangedTwice_reportOnlyOnce() {
        mContentObserverCaptor.getValue().onChange(false);
        notifyDeviceStateChanged();
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        anyBoolean()), times(1));
    }

    @Test
    public void onStateChange_issueOccurredDeviceStateChangedTwice_reportOnlyOnce() {
        notifyDeviceStateChanged();
        mContentObserverCaptor.getValue().onChange(false);
        notifyDeviceStateChanged();

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        anyInt(),
                        anyBoolean()), times(1));
    }

    @Test
    public void onStateChange_issueOccurredAfterDelay_reportOnce() {
        mContentObserverCaptor.getValue().onChange(false);
        mTestTimeSupplier.delay(
                DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_THRESHOLD_MILLIS + DELAY);
        notifyDeviceStateChanged();
        mTestTimeSupplier.delay(DELAY);
        mContentObserverCaptor.getValue().onChange(false);

        verify(() ->
                FrameworkStatsLog.write(
                        eq(FrameworkStatsLog.DEVICE_STATE_AUTO_ROTATE_SETTING_ISSUE_REPORTED),
                        eq(DELAY),
                        anyBoolean()), times(1));
    }

    private void notifyDeviceStateChanged() {
        mDeviceStateListenerCaptor.getValue().onDeviceStateChanged(
                DeviceStateController.DeviceStateEnum.UNKNOWN, DeviceStateTestUtils.UNKNOWN);
    }
}
