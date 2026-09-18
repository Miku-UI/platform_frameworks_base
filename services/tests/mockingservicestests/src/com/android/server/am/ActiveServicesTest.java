/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.internal.app.procstats.ProcessStats.ADJ_MEM_FACTOR_CRITICAL;
import static com.android.internal.app.procstats.ProcessStats.ADJ_MEM_FACTOR_LOW;
import static com.android.internal.app.procstats.ProcessStats.ADJ_MEM_FACTOR_MODERATE;
import static com.android.internal.app.procstats.ProcessStats.ADJ_MEM_FACTOR_NORMAL;
import static com.android.internal.util.FrameworkStatsLog.FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_DECLARED_AND_GRANTED;
import static com.android.internal.util.FrameworkStatsLog.FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_DECLARED_BUT_DENIED;
import static com.android.internal.util.FrameworkStatsLog.FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_NOT_DECLARED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.ActivityManagerInternal.ForegroundServiceStateListener;
import android.app.AnrTypes;
import android.app.IApplicationThread;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.compat.CompatChanges;
import android.app.privatecompute.flags.Flags;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.service.voice.HotwordDetectionService;
import android.service.voice.VisualQueryDetectionService;
import android.service.wearable.WearableSensingService;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.am.psc.ProcessStateController;
import com.android.server.am.psc.ServiceRecordInternal;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ActiveServicesTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PACKAGE_NAME_1 = "com.foo";
    private static final String PACKAGE_NAME_2 = "com.bar";
    private static final String PACKAGE_NAME_PCC = "com.android.pcc";
    private static final String SERVICE_NAME = "barService";
    private static final String PROCESS_NAME_1 = PACKAGE_NAME_1;
    private static final String TRUSTED_ISOLATED_PROCESS_NAME_1 =
            PACKAGE_NAME_1 + ":trusted_isolated";
    private static final String ISOLATED_PROCESS_NAME_2 = PACKAGE_NAME_2 + ":isolated";
    private static final String ISOLATED_PROCESS_INSTANCE_NAME = "pool";
    private static final String SHARED_ISOLATED_PROCESS_NAME =
            PACKAGE_NAME_1 + ":ishared:" + ISOLATED_PROCESS_INSTANCE_NAME;

    private static final long DEFAULT_SERVICE_MIN_RESTART_TIME_BETWEEN = 10 * 1000;
    private static final long[] DEFAULT_EXTRA_SERVICE_RESTART_DELAY_ON_MEM_PRESSURE = {
        0,
        DEFAULT_SERVICE_MIN_RESTART_TIME_BETWEEN,
        DEFAULT_SERVICE_MIN_RESTART_TIME_BETWEEN * 2,
        DEFAULT_SERVICE_MIN_RESTART_TIME_BETWEEN * 3
    };

    private static final int TEST_UID = 10123;
    private static final int TEST_UID_PCC = 30001;
    private static final int TEST_USERID = 0;
    private static final int TEST_PID = 1234;

    private static final int ANR_ID = 1234;
    private static final long ELAPSED_TIME_MS = 2000L;

    private MockitoSession mMockingSession;
    private ActivityManagerService mService;
    private ActiveServices mActiveServices;
    private PackageManagerInternal mPackageManagerInternal;
    private Context mContext;

    @Before
    public void setUp() {
        mMockingSession =
                mockitoSession()
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .mockStatic(CompatChanges.class)
                        .startMocking();
        // Most tests require a non-null ActivityManagerService.  Create one now.  If the test
        // requires a specialized mock, the test can replace this value.
        mService = mock(ActivityManagerService.class);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void rescheduleServiceRestartsOnChanges() {
        prepareTestRescheduleServiceRestarts();
        final long now = SystemClock.uptimeMillis();
        final long btwn = mService.mConstants.SERVICE_MIN_RESTART_TIME_BETWEEN;
        final long rd0 = 0;
        final long rd1 = 1000;
        final long rd2 = rd1 + btwn;
        final long rd3 = rd2 + btwn;
        final long rd4 = rd3 + btwn * 10;
        final long rd5 = rd4 + btwn;
        int memFactor = ADJ_MEM_FACTOR_MODERATE;
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);
        fillInRestartingServices(now, new long[] {rd0, rd1, rd2, rd3, rd4, rd5});

        // Test enable/disable.
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(false, true, now);
        long extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        verifyDelays(
                now,
                new long[] {rd0, extra, btwn + extra * 2, btwn * 2 + extra * 3, rd4, rd5 + extra});
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(true, false, now);
        verifyDelays(now, new long[] {rd0, rd1, rd2, rd3, rd4, rd5});

        final long elapsed = 10;
        final long now2 = now + elapsed;
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(false, true, now2);
        verifyDelays(
                now2,
                new long[] {
                    rd0 - elapsed,
                    extra - elapsed,
                    btwn + extra * 2 - elapsed,
                    btwn * 2 + extra * 3 - elapsed,
                    rd4 - elapsed,
                    rd5 + extra - elapsed
                });

        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(true, false, now2);
        verifyDelays(
                now2,
                new long[] {
                    rd0 - elapsed,
                    rd1 - elapsed,
                    rd2 - elapsed,
                    rd3 - elapsed,
                    rd4 - elapsed,
                    rd5 - elapsed
                });

        // Test memory level changes.
        memFactor = ADJ_MEM_FACTOR_LOW;
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);
        extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        final long elapsed3 = elapsed * 2;
        final long now3 = now + elapsed3;
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(
                ADJ_MEM_FACTOR_MODERATE, memFactor, "test", now3);
        verifyDelays(
                now3,
                new long[] {
                    rd0 - elapsed3,
                    extra - elapsed3,
                    btwn + extra * 2 - elapsed3,
                    btwn * 2 + extra * 3 - elapsed3,
                    rd4 - elapsed3,
                    rd5 + extra - elapsed3
                });

        memFactor = ADJ_MEM_FACTOR_CRITICAL;
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);
        extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        mService.mAppProfiler = mock(AppProfiler.class);
        final long elapsed4 = elapsed * 3;
        final long now4 = now + elapsed4;
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(
                ADJ_MEM_FACTOR_LOW, memFactor, "test", now4);
        verifyDelays(
                now4,
                new long[] {
                    rd0 - elapsed4,
                    extra - elapsed4,
                    btwn + extra * 2 - elapsed4,
                    btwn * 2 + extra * 3 - elapsed4,
                    btwn * 3 + extra * 4 - elapsed4,
                    btwn * 4 + extra * 5 - elapsed4
                });

        memFactor = ADJ_MEM_FACTOR_MODERATE;
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);
        extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        final long elapsed5 = elapsed * 4;
        final long now5 = now + elapsed5;
        mActiveServices.rescheduleServiceRestartOnMemoryPressureIfNeededLocked(
                ADJ_MEM_FACTOR_CRITICAL, memFactor, "test", now5);
        verifyDelays(
                now5,
                new long[] {
                    rd0 - elapsed5,
                    extra - elapsed5,
                    btwn + extra * 2 - elapsed5,
                    btwn * 2 + extra * 3 - elapsed5,
                    rd4 - elapsed5,
                    rd5 + extra - elapsed5
                });
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void rescheduleServiceRestartsOnOtherChanges() {
        prepareTestRescheduleServiceRestarts();
        final long now = SystemClock.uptimeMillis();
        final long btwn = mService.mConstants.SERVICE_MIN_RESTART_TIME_BETWEEN;
        final long rd0 = 1000;
        final long rd1 = 2000;
        final long rd2 = btwn * 10;
        final long rd3 = 5000;
        final long rd4 = btwn * 11 + 5000;
        final long rd5 = 3000;
        int memFactor = ADJ_MEM_FACTOR_CRITICAL;
        long extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);

        fillInRestartingServices(now, new long[] {rd0, rd1, rd2, rd3, rd4, rd5});
        setNextRestarts(
                now,
                new long[] {
                    extra,
                    btwn + extra * 2,
                    btwn * 2 + extra * 3,
                    btwn * 3 + extra * 4,
                    btwn * 4 + extra * 5,
                    btwn * 5 + extra * 6
                });
        mActiveServices.mRestartingServices.remove(1);
        mActiveServices.rescheduleServiceRestartIfPossibleLocked(extra, btwn, "test", now);
        verifyDelays(
                now,
                new long[] {
                    extra,
                    rd2,
                    rd2 + btwn + extra,
                    rd2 + (btwn + extra) * 2,
                    rd2 + (btwn + extra) * 3
                });
        mActiveServices.mRestartingServices.remove(0);
        mActiveServices.rescheduleServiceRestartIfPossibleLocked(extra, btwn, "test", now);
        verifyDelays(now, new long[] {extra, rd2, rd2 + btwn + extra, rd2 + (btwn + extra) * 2});
        mActiveServices.mRestartingServices.remove(1);
        mActiveServices.rescheduleServiceRestartIfPossibleLocked(extra, btwn, "test", now);
        verifyDelays(now, new long[] {extra, btwn + extra * 2, rd4});

        fillInRestartingServices(now, new long[] {rd0, rd1, rd2, rd3, rd4, rd5});
        setNextRestarts(
                now,
                new long[] {
                    extra,
                    btwn + extra * 2,
                    btwn * 2 + extra * 3,
                    btwn * 3 + extra * 4,
                    btwn * 4 + extra * 5,
                    btwn * 5 + extra * 6
                });
        mActiveServices.mRestartingServices.remove(1);
        mActiveServices.rescheduleServiceRestartIfPossibleLocked(extra, btwn, "test", now);
        memFactor = ADJ_MEM_FACTOR_LOW;
        extra = mService.mConstants.mExtraServiceRestartDelayOnMemPressure[memFactor];
        when(mService.mAppProfiler.getLastMemoryLevelLocked()).thenReturn(memFactor);
        mActiveServices.rescheduleServiceRestartIfPossibleLocked(extra, btwn, "test", now);
        verifyDelays(
                now,
                new long[] {
                    extra, btwn + extra * 2, rd2, rd2 + btwn + extra, rd2 + (btwn + extra) * 2
                });
    }

    @Test
    public void getProcessNameForService_regularService() {
        // Regular service
        final ServiceInfo regularService = createServiceInfo(PROCESS_NAME_1, 0);
        String processName =
                ActiveServices.getProcessNameForService(
                        regularService, null, null, null, false, false, false);
        assertThat(processName).isEqualTo(PROCESS_NAME_1);
    }

    @Test
    public void getProcessNameForService_isolatedService() {
        // Isolated service
        final ServiceInfo isolatedService =
                createServiceInfo(PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        final ComponentName component = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        String processName =
                ActiveServices.getProcessNameForService(
                        isolatedService, component, null, null, false, false, false);
        assertThat(processName).isEqualTo(PROCESS_NAME_1 + ":" + SERVICE_NAME);
    }

    @Test
    public void getProcessNameForService_isolatedServiceInPackagePrivateProcess() {
        // Isolated Service in package private process.
        final ServiceInfo isolatedService =
                createServiceInfo(
                        TRUSTED_ISOLATED_PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        final ComponentName componentName = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        String processName =
                ActiveServices.getProcessNameForService(
                        isolatedService, componentName, null, null, false, false, false);
        assertThat(processName).isEqualTo(TRUSTED_ISOLATED_PROCESS_NAME_1 + ":" + SERVICE_NAME);
    }

    @Test
    public void
            getProcessNameForService_isolatedServiceInPackagePrivateSharedProcess_mainProcess() {
        // Isolated service in package-private shared process (main process)
        final ServiceInfo isolatedPackageSharedService =
                createServiceInfo(PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        final ComponentName componentName1 = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        String packageSharedIsolatedProcessName =
                ActiveServices.getProcessNameForService(
                        isolatedPackageSharedService,
                        componentName1,
                        null,
                        null,
                        false,
                        false,
                        true);
        assertThat(packageSharedIsolatedProcessName).isEqualTo(PROCESS_NAME_1 + ":" + SERVICE_NAME);
    }

    @Test
    public void getProcessNameForService_isolatedServiceInPackagePrivateSharedProcess() {
        // Isolated service in package-private shared process
        final ServiceInfo isolatedPackageSharedService =
                createServiceInfo(
                        TRUSTED_ISOLATED_PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        isolatedPackageSharedService.applicationInfo.processName = PACKAGE_NAME_1;
        final ComponentName componentName1 = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        String packageSharedIsolatedProcessName =
                ActiveServices.getProcessNameForService(
                        isolatedPackageSharedService,
                        componentName1,
                        null,
                        null,
                        false,
                        false,
                        true);
        assertThat(packageSharedIsolatedProcessName).isEqualTo(TRUSTED_ISOLATED_PROCESS_NAME_1);
    }

    @Test
    public void
            getProcessNameForService_isolatedServiceInPackagePrivateSharedProcess_bindAnother() {
        // Isolated service in package-private shared process
        final ServiceInfo isolatedPackageSharedService1 =
                createServiceInfo(
                        TRUSTED_ISOLATED_PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        isolatedPackageSharedService1.applicationInfo.processName = PACKAGE_NAME_1;
        final ComponentName componentName1 = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);

        // Bind another one in the same isolated process
        final ServiceInfo isolatedPackageSharedService2 =
                new ServiceInfo(isolatedPackageSharedService1);
        String packageSharedIsolatedProcessName =
                ActiveServices.getProcessNameForService(
                        isolatedPackageSharedService2,
                        componentName1,
                        null,
                        null,
                        false,
                        false,
                        true);
        assertThat(packageSharedIsolatedProcessName).isEqualTo(TRUSTED_ISOLATED_PROCESS_NAME_1);
    }

    @Test
    public void
            getProcessNameForService_isolatedServiceInPackagePrivateSharedProcess_bindAnotherApp() {
        // Isolated service in package-private shared process
        final ServiceInfo isolatedPackageSharedService1 =
                createServiceInfo(
                        TRUSTED_ISOLATED_PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        isolatedPackageSharedService1.applicationInfo.processName = PACKAGE_NAME_1;
        final ComponentName componentName1 = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);

        // Simulate another app trying to do the bind.
        final ServiceInfo isolatedPackageSharedService2 =
                new ServiceInfo(isolatedPackageSharedService1);
        String packageSharedIsolatedProcessName =
                ActiveServices.getProcessNameForService(
                        isolatedPackageSharedService2,
                        componentName1,
                        PACKAGE_NAME_2,
                        null,
                        false,
                        false,
                        true);
        assertThat(packageSharedIsolatedProcessName).isEqualTo(TRUSTED_ISOLATED_PROCESS_NAME_1);
    }

    @Test
    public void
            getProcessNameForServiceIsolatedServiceInPackagePrivateSharedProcessAnotherAppOwner() {
        // Isolated service in package-private shared process
        final ServiceInfo isolatedPackageSharedService1 =
                createServiceInfo(
                        TRUSTED_ISOLATED_PROCESS_NAME_1, ServiceInfo.FLAG_ISOLATED_PROCESS);
        isolatedPackageSharedService1.applicationInfo.processName = PACKAGE_NAME_1;

        // Simulate another app owning the service
        final ServiceInfo isolatedOtherPackageSharedService =
                new ServiceInfo(isolatedPackageSharedService1);
        final ComponentName componentName2 = new ComponentName(PACKAGE_NAME_2, SERVICE_NAME);
        isolatedOtherPackageSharedService.processName = ISOLATED_PROCESS_NAME_2;
        isolatedPackageSharedService1.applicationInfo.processName = PACKAGE_NAME_2;
        String packageSharedIsolatedProcessName =
                ActiveServices.getProcessNameForService(
                        isolatedOtherPackageSharedService,
                        componentName2,
                        PACKAGE_NAME_1,
                        null,
                        false,
                        false,
                        true);
        assertThat(packageSharedIsolatedProcessName).isEqualTo(ISOLATED_PROCESS_NAME_2);
    }

    @Test
    public void getProcessNameForService_isolatedServiceInSharedIsolatedProcess() {
        // Isolated service in shared isolated process
        final ServiceInfo isolatedServiceShared1 = new ServiceInfo();
        isolatedServiceShared1.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;
        final String sharedIsolatedProcessName1 =
                ActiveServices.getProcessNameForService(
                        isolatedServiceShared1,
                        null,
                        PACKAGE_NAME_1,
                        ISOLATED_PROCESS_INSTANCE_NAME,
                        false,
                        true,
                        false);
        assertThat(sharedIsolatedProcessName1).isEqualTo(SHARED_ISOLATED_PROCESS_NAME);
    }

    @Test
    public void getProcessNameForService_isolatedServiceInSharedIsolatedProcess_bindAnother() {
        // Isolated service in shared isolated process
        final ServiceInfo isolatedServiceShared1 = new ServiceInfo();
        isolatedServiceShared1.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;
        final String instanceName = ISOLATED_PROCESS_INSTANCE_NAME;
        final String callingPackage = PACKAGE_NAME_1;
        final String sharedIsolatedProcessName1 =
                ActiveServices.getProcessNameForService(
                        isolatedServiceShared1,
                        null,
                        callingPackage,
                        instanceName,
                        false,
                        true,
                        false);

        // Bind another one in the same isolated process
        final ServiceInfo isolatedServiceShared2 = new ServiceInfo(isolatedServiceShared1);
        final String sharedIsolatedProcessName2 =
                ActiveServices.getProcessNameForService(
                        isolatedServiceShared2,
                        null,
                        callingPackage,
                        instanceName,
                        false,
                        true,
                        false);
        assertThat(sharedIsolatedProcessName2).isEqualTo(sharedIsolatedProcessName1);
    }

    @Test
    public void getProcessNameForService_isolatedServiceInSharedIsolatedProcess_bindAnotherApp() {
        // Isolated service in shared isolated process
        final ServiceInfo isolatedServiceShared1 = new ServiceInfo();
        isolatedServiceShared1.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;
        final String instanceName = ISOLATED_PROCESS_INSTANCE_NAME;
        final String sharedIsolatedProcessName1 =
                ActiveServices.getProcessNameForService(
                        isolatedServiceShared1,
                        null,
                        PACKAGE_NAME_1,
                        instanceName,
                        false,
                        true,
                        false);

        // Simulate another app trying to do the bind
        final ServiceInfo isolatedServiceShared2 = new ServiceInfo(isolatedServiceShared1);
        final String sharedIsolatedProcessName2 =
                ActiveServices.getProcessNameForService(
                        isolatedServiceShared2,
                        null,
                        PACKAGE_NAME_2,
                        instanceName,
                        false,
                        true,
                        false);
        assertThat(sharedIsolatedProcessName2).isNotEqualTo(sharedIsolatedProcessName1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void bringUpServiceLocked_pcc() throws Exception {
        prepareTestRescheduleServiceRestarts();
        mService.mPackageManagerInt = mock(PackageManagerInternal.class);
        mService.mUsageStatsService = mock(UsageStatsManagerInternal.class);

        setFieldValue(
                ActivityManagerService.class,
                mService,
                "mUserController",
                mock(UserController.class));
        setFieldValue(
                ActivityManagerService.class, mService, "mProcessList", mock(ProcessList.class));
        setFieldValue(ActiveServices.class, mActiveServices, "mPendingServices", new ArrayList<>());

        final ServiceRecord r = createPccServiceRecord();
        final ProcessRecord proc = createPccProcessRecord(mService);

        // Simulate no existing process for this ServiceRecord
        when(mService.getProcessRecordLocked(anyString(), anyInt())).thenReturn(null);

        when(mService.mUserController.hasStartedUserState(anyInt())).thenReturn(true);
        when(mService.mProcessList.getAppStartInfoTracker())
                .thenReturn(mock(AppStartInfoTracker.class));
        when(mService.startProcessLocked(
                        anyString(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean()))
                .thenReturn(proc);

        doCallRealMethod()
                .when(mActiveServices)
                .bringUpServiceLocked(
                        any(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt());

        mActiveServices.bringUpServiceLocked(
                r, r.serviceInfo.flags, false, false, false, false, false, 0);

        verify(mService).getProcessRecordLocked(r.processName, r.appInfo.pccUid);

        final ArgumentCaptor<HostingRecord> hostingRecord =
                ArgumentCaptor.forClass(HostingRecord.class);
        verify(mService)
                .startProcessLocked(
                        eq(r.processName),
                        eq(r.appInfo),
                        eq(true),
                        anyInt(),
                        hostingRecord.capture(),
                        eq(Process.ZYGOTE_POLICY_FLAG_EMPTY),
                        eq(false),
                        eq(false));
        assertThat(hostingRecord.getValue().isPcc()).isTrue();
    }

    @Test
    public void testBringUpServiceLocked_startedService() throws Exception {
        prepareTestRescheduleServiceRestarts();
        setupBringUpServiceLocked();

        final ServiceRecord r = createServiceRecord();
        when(r.isStartRequested()).thenReturn(true);

        mActiveServices.bringUpServiceLocked(
                r, r.serviceInfo.flags, false, false, false, false, false, 0);

        final ArgumentCaptor<HostingRecord> hostingRecord =
                ArgumentCaptor.forClass(HostingRecord.class);
        verify(mService)
                .startProcessLocked(
                        anyString(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        hostingRecord.capture(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean());
        assertThat(hostingRecord.getValue().getType())
                .isEqualTo(HostingRecord.HOSTING_TYPE_STARTED_SERVICE);
    }

    @Test
    public void testBringUpServiceLocked_boundService() throws Exception {
        prepareTestRescheduleServiceRestarts();
        setupBringUpServiceLocked();

        final ServiceRecord r = createServiceRecord();
        when(r.isStartRequested()).thenReturn(false);

        mActiveServices.bringUpServiceLocked(
                r, r.serviceInfo.flags, false, false, false, false, false, 0);

        final ArgumentCaptor<HostingRecord> hostingRecord =
                ArgumentCaptor.forClass(HostingRecord.class);
        verify(mService)
                .startProcessLocked(
                        anyString(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        hostingRecord.capture(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean());
        assertThat(hostingRecord.getValue().getType())
                .isEqualTo(HostingRecord.HOSTING_TYPE_BOUND_SERVICE);
    }

    private void setupBringUpServiceLocked() {
        mService.mPackageManagerInt = mock(PackageManagerInternal.class);
        mService.mUsageStatsService = mock(UsageStatsManagerInternal.class);
        setFieldValue(
                ActivityManagerService.class,
                mService,
                "mUserController",
                mock(UserController.class));
        setFieldValue(
                ActivityManagerService.class, mService, "mProcessList", mock(ProcessList.class));
        setFieldValue(ActiveServices.class, mActiveServices, "mPendingServices", new ArrayList<>());

        when(mService.mUserController.hasStartedUserState(anyInt())).thenReturn(true);
        when(mService.mProcessList.getAppStartInfoTracker())
                .thenReturn(mock(AppStartInfoTracker.class));
        when(mService.startProcessLocked(
                        anyString(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean()))
                .thenReturn(mock(ProcessRecord.class));
        when(mService.getProcessRecordLocked(anyString(), anyInt())).thenReturn(null);

        try {
            doCallRealMethod()
                    .when(mActiveServices)
                    .bringUpServiceLocked(
                            any(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean(),
                            anyInt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void attachApplicationLocked_pcc() throws Exception {
        prepareTestRescheduleServiceRestarts();
        mService.mProcessStateController = mock(ProcessStateController.class);
        mService.mAppProfiler = mock(AppProfiler.class);
        mService.mActivityTaskManager = mock(ActivityTaskManagerService.class);
        mService.mPackageManagerInt = mock(PackageManagerInternal.class);
        mService.mProcessStateController = mock(ProcessStateController.class);

        setFieldValue(ActiveServices.class, mActiveServices, "mAm", mService);

        final ServiceRecord r = createPccServiceRecord();
        final ProcessRecord proc = createPccProcessRecord(mService);

        // Make sure we call the real attachApplicationLocked, and mock out the stuff it calls
        doCallRealMethod().when(mActiveServices).attachApplicationLocked(any(), anyString());
        when(mService.mProcessStateController.startServiceBatchSession(anyInt())).thenReturn(null);
        when(mService.mProcessStateController.getOomConstants()).thenReturn(null);
        doNothing()
                .when(mActiveServices)
                .realStartServiceLocked(
                        any(ServiceRecord.class),
                        any(ProcessRecord.class),
                        any(IApplicationThread.class),
                        anyInt(),
                        any(UidRecord.class),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt());
        when(r.isNeededLocked(anyBoolean(), anyBoolean())).thenReturn(true);

        setFieldValue(ActiveServices.class, mActiveServices, "mPendingServices", new ArrayList<>());
        mActiveServices.mPendingServices.add(r);

        mActiveServices.attachApplicationLocked(proc, r.processName);

        assertThat(mActiveServices.mPendingServices).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPostNotification_usesPccUid() {
        setMockHandlerOnAms();
        mContext = mock(Context.class);
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);
        setFieldValue(ActivityManagerService.class, mService, "mContext", mContext);

        List<ForegroundServiceStateListener> fsslList = new ArrayList<>();
        ForegroundServiceStateListener listener = mock(ForegroundServiceStateListener.class);
        fsslList.add(listener);
        setFieldValue(
                ActivityManagerService.class,
                mService,
                "mForegroundServiceStateListeners",
                fsslList);

        NotificationManagerInternal nmi = mock(NotificationManagerInternal.class);
        NotificationChannel nc = mock(NotificationChannel.class);
        when(nmi.getNotificationChannel(
                        eq(PACKAGE_NAME_PCC),
                        eq(TEST_UID_PCC),
                        eq("testPostNotification_usesPccUid")))
                .thenReturn(nc);
        when(nmi.areNotificationsEnabledForPackage(eq(PACKAGE_NAME_PCC), eq(TEST_UID_PCC)))
                .thenReturn(true);
        LocalServices.removeServiceForTest(NotificationManagerInternal.class);
        LocalServices.addService(NotificationManagerInternal.class, nmi);

        try {
            final ServiceRecord r = createPccServiceRecord();
            when(r.isForeground()).thenReturn(true);
            Notification notification = mock(Notification.class);
            when(notification.getSmallIcon()).thenReturn(mock(Icon.class));
            when(notification.getChannelId()).thenReturn("testPostNotification_usesPccUid");
            r.foregroundNoti = notification;
            r.foregroundId = 42;

            final ProcessRecord pr = mock(ProcessRecord.class);
            when(pr.getPid()).thenReturn(1234);
            when(r.getHostProcess()).thenReturn(pr);

            // Allow calling real postNotification
            doCallRealMethod().when(r).postNotification(anyBoolean());

            r.postNotification(true);

            // Verify enqueueNotification is called with the PCC UID (30001)
            verify(nmi)
                    .enqueueNotification(
                            eq(PACKAGE_NAME_PCC),
                            eq(PACKAGE_NAME_PCC),
                            eq(TEST_UID_PCC),
                            eq(1234),
                            any(),
                            eq(42),
                            eq(r.foregroundNoti),
                            eq(TEST_USERID),
                            anyBoolean());

            // Verify listener is called with PCC UID
            verify(listener)
                    .onForegroundServiceNotificationUpdated(
                            eq(PACKAGE_NAME_PCC), eq(TEST_UID_PCC), eq(42), eq(false));

        } finally {
            LocalServices.removeServiceForTest(NotificationManagerInternal.class);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCancelNotification_usesPccUid() {
        setMockHandlerOnAms();
        List<ForegroundServiceStateListener> fsslList = new ArrayList<>();
        ForegroundServiceStateListener listener = mock(ForegroundServiceStateListener.class);
        fsslList.add(listener);
        setFieldValue(
                ActivityManagerService.class,
                mService,
                "mForegroundServiceStateListeners",
                fsslList);

        NotificationManagerInternal nmi = mock(NotificationManagerInternal.class);
        LocalServices.removeServiceForTest(NotificationManagerInternal.class);
        LocalServices.addService(NotificationManagerInternal.class, nmi);

        try {
            final ServiceRecord r = createPccServiceRecord();
            r.foregroundId = 42;

            final ProcessRecord pr = mock(ProcessRecord.class);
            when(pr.getPid()).thenReturn(1234);
            when(r.getHostProcess()).thenReturn(pr);

            doCallRealMethod().when(r).cancelNotification();

            r.cancelNotification();

            // Verify cancelNotification is called with the PCC UID (30001)
            verify(nmi)
                    .cancelNotification(
                            eq(PACKAGE_NAME_PCC),
                            eq(PACKAGE_NAME_PCC),
                            eq(TEST_UID_PCC),
                            eq(1234),
                            any(),
                            eq(42),
                            eq(TEST_USERID));

            // Verify listener is called with PCC UID
            verify(listener)
                    .onForegroundServiceNotificationUpdated(
                            eq(PACKAGE_NAME_PCC), eq(TEST_UID_PCC), eq(42), eq(true));

        } finally {
            LocalServices.removeServiceForTest(NotificationManagerInternal.class);
        }
    }

    private void setMockHandlerOnAms() {
        ActivityManagerService.MainHandler mockHandler =
                mock(ActivityManagerService.MainHandler.class);
        when(mockHandler.post(any(Runnable.class)))
                .thenAnswer(
                        invocation -> {
                            ((Runnable) invocation.getArgument(0)).run();
                            return true;
                        });

        setFieldValue(ActivityManagerService.class, mService, "mHandler", mockHandler);
    }

    private void prepareTestRescheduleServiceRestarts() {
        mService.mConstants = mock(ActivityManagerConstants.class);
        mService.mConstants.mEnableExtraServiceRestartDelayOnMemPressure = true;
        mService.mConstants.mExtraServiceRestartDelayOnMemPressure =
                DEFAULT_EXTRA_SERVICE_RESTART_DELAY_ON_MEM_PRESSURE;
        mService.mConstants.SERVICE_MIN_RESTART_TIME_BETWEEN =
                DEFAULT_SERVICE_MIN_RESTART_TIME_BETWEEN;
        AppProfiler profiler = mock(AppProfiler.class);
        setFieldValue(ActivityManagerService.class, mService, "mAppProfiler", profiler);
        when(profiler.getLastMemoryLevelLocked()).thenReturn(ADJ_MEM_FACTOR_NORMAL);
        mActiveServices = mock(ActiveServices.class);
        setFieldValue(ActiveServices.class, mActiveServices, "mAm", mService);
        setFieldValue(
                ActiveServices.class, mActiveServices, "mRestartingServices", new ArrayList<>());
        setFieldValue(
                ActiveServices.class,
                mActiveServices,
                "mRestartBackoffDisabledPackages",
                new ArraySet<>());
        doNothing()
                .when(mActiveServices)
                .performScheduleRestartLocked(
                        any(ServiceRecord.class), any(String.class), any(String.class), anyLong());
        doCallRealMethod()
                .when(mActiveServices)
                .rescheduleServiceRestartOnMemoryPressureIfNeededLocked(
                        anyBoolean(), anyBoolean(), anyLong());
        doCallRealMethod()
                .when(mActiveServices)
                .rescheduleServiceRestartOnMemoryPressureIfNeededLocked(
                        anyInt(), anyInt(), any(String.class), anyLong());
        doCallRealMethod()
                .when(mActiveServices)
                .rescheduleServiceRestartIfPossibleLocked(
                        anyLong(), anyLong(), any(String.class), anyLong());
        doCallRealMethod()
                .when(mActiveServices)
                .performRescheduleServiceRestartOnMemoryPressureLocked(
                        anyLong(), anyLong(), any(String.class), anyLong());
        doCallRealMethod().when(mActiveServices).getExtraRestartTimeInBetweenLocked();
        doCallRealMethod()
                .when(mActiveServices)
                .isServiceRestartBackoffEnabledLocked(any(String.class));
    }

    /**
     * Sets the value of a field in an object using reflection. This is useful for setting private
     * or final fields in tests.
     *
     * @param clazz The class of the object.
     * @param obj The object whose field to set.
     * @param fieldName The name of the field to set.
     * @param val The new value for the field.
     * @param <T> The type of the field value.
     * @throws RuntimeException if the field does not exist or cannot be accessed.
     */
    private static <T> void setFieldValue(Class<?> clazz, Object obj, String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            // Remove the 'final' modifier to allow re-assignment.
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void fillInRestartingServices(long now, long[] delays) {
        mActiveServices.mRestartingServices.clear();
        for (int i = 0; i < delays.length; i++) {
            mActiveServices.mRestartingServices.add(
                    createRestartingService("testpackage" + i, now, delays[i]));
        }
    }

    private void setNextRestarts(long now, long[] nextRestartDelays) {
        for (int i = 0; i < nextRestartDelays.length; i++) {
            final ServiceRecord r = mActiveServices.mRestartingServices.get(i);
            r.restartDelay = nextRestartDelays[i];
            r.nextRestartTime = now + r.restartDelay;
        }
    }

    private ServiceRecord createRestartingService(String packageName, long now, long delay) {
        final ServiceRecord r = mock(ServiceRecord.class);
        r.appInfo = new ApplicationInfo();
        r.appInfo.flags = delay == 0 ? ApplicationInfo.FLAG_PERSISTENT : 0;
        final ServiceInfo si = new ServiceInfo();
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", packageName);
        si.applicationInfo = r.appInfo;
        r.nextRestartTime = r.mEarliestRestartTime = now + delay;
        r.mRestartSchedulingTime = now;
        r.restartDelay = delay;
        return r;
    }

    private void verifyDelays(long now, long[] delays) {
        for (int i = 0; i < delays.length; i++) {
            final ServiceRecord r = mActiveServices.mRestartingServices.get(i);
            assertWithMessage("Expected restart delay=" + delays[i]).that(r.restartDelay).isEqualTo(
                    Math.max(0, delays[i]));
            assertWithMessage("Expected next restart time=" + now + delays[i]).that(
                    r.nextRestartTime).isEqualTo(now + delays[i]);
        }
    }

    private ServiceInfo createServiceInfo(String processName, int flags) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.processName = processName;
        serviceInfo.flags = flags;
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.processName = processName;
        return serviceInfo;
    }

    private ProcessRecord createPccProcessRecord(ActivityManagerService ams) {
        final ProcessRecord proc = mock(ProcessRecord.class);

        proc.info = new ApplicationInfo();
        proc.info.uid = 20001;
        proc.info.pccUid = TEST_UID_PCC;
        proc.info.packageName = PACKAGE_NAME_PCC;
        setFieldValue(ProcessRecord.class, proc, "mService", ams);
        setFieldValue(ProcessRecordInternal.class, proc, "uid", TEST_UID_PCC);

        return proc;
    }

    private ServiceRecord createPccServiceRecord() {
        final ServiceRecord r = mock(ServiceRecord.class);
        r.appInfo = new ApplicationInfo();
        r.appInfo.uid = 20001;
        r.appInfo.pccUid = TEST_UID_PCC;
        r.appInfo.packageName = PACKAGE_NAME_PCC;
        final ServiceInfo si = new ServiceInfo();
        si.applicationInfo = r.appInfo;
        si.flags = ServiceInfo.FLAG_RUN_IN_PCC_SANDBOX;
        setFieldValue(ServiceRecord.class, r, "ams", mService);
        setFieldValue(ServiceRecord.class, r, "userId", TEST_USERID);
        setFieldValue(ServiceRecord.class, r, "packageName", PACKAGE_NAME_PCC);
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "processName", "test");
        setFieldValue(ServiceRecord.class, r, "intent", new Intent.FilterComparison(new Intent()));
        setFieldValue(
                ServiceRecordInternal.class, r, "instanceName", new ComponentName("pkg", "class"));
        return r;
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_hds_returnsIsolatedComputeApp() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent(HotwordDetectionService.SERVICE_INTERFACE);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEqualTo(":isolatedComputeApp");
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_vqds_returnsIsolatedComputeApp() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent(VisualQueryDetectionService.SERVICE_INTERFACE);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEqualTo(":isolatedComputeApp");
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_sandboxService_returnsIsolatedComputeApp() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent(OnDeviceSandboxedInferenceService.SERVICE_INTERFACE);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEqualTo(":isolatedComputeApp");
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_wearableServiceNoRestriction_returnsICA() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent(WearableSensingService.SERVICE_INTERFACE);
        when(mService.hasRestrictedAssociations(r.packageName)).thenReturn(false);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEqualTo(":isolatedComputeApp");
    }

    @Test
    public void
            testGenerateAdditionalSeInfoFromService_wearableServiceWithRestriction_returnsEmpty() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent(WearableSensingService.SERVICE_INTERFACE);
        when(mService.hasRestrictedAssociations(r.packageName)).thenReturn(true);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEmpty();
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_PccService_ReturnsEmpty() {
        ActiveServices activeServices = new ActiveServices(mService);
        ServiceRecord r = createPccServiceRecord();
        Intent intent = new Intent(HotwordDetectionService.SERVICE_INTERFACE);
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEmpty();
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_NormalService_ReturnsEmpty() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent("com.foo.bar.MY_SERVICE");
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEmpty();
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_NullIntentAction_ReturnsEmpty() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        Intent intent = new Intent();
        String seInfo = activeServices.generateAdditionalSeInfoFromService(intent, r);
        assertThat(seInfo).isEmpty();
    }

    @Test
    public void testGenerateAdditionalSeInfoFromService_NullIntent_ReturnsEmpty() {
        ActiveServices activeServices = new ActiveServices(mService);
        final ServiceRecord r = mock(ServiceRecord.class);
        final ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", "com.foo");
        String seInfo = activeServices.generateAdditionalSeInfoFromService(null, r);
        assertThat(seInfo).isEmpty();
    }

    @Test
    public void testGetNotificationPermissionState_notificationPermissionNotDeclared() {
        mActiveServices = new ActiveServices(mService);
        mPackageManagerInternal = mock(PackageManagerInternal.class);

        ServiceRecord r = createServiceRecord();
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);

        PackageInfo packageInfo = setupPackageInfo(false);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);

        int result = mActiveServices.getNotificationPermissionState(r);
        assertThat(result)
                .isEqualTo(
                        FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_NOT_DECLARED);
        assertThat(mActiveServices.mNotificationPermCache.get(TEST_UID)).isFalse();
    }

    @Test
    public void testGetNotificationPermissionState_notificationPermissionDeclaredAndGranted() {
        mActiveServices = new ActiveServices(mService);
        mPackageManagerInternal = mock(PackageManagerInternal.class);
        mContext = mock(Context.class);

        ServiceRecord r = createServiceRecord();
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);

        PackageInfo packageInfo = setupPackageInfo(true);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);

        setFieldValue(ActivityManagerService.class, mService, "mContext", mContext);
        when(mContext.checkPermission(eq(Manifest.permission.POST_NOTIFICATIONS), eq(TEST_PID),
                eq(TEST_UID)))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        int result = mActiveServices.getNotificationPermissionState(r);

        assertThat(result)
                .isEqualTo(
                        FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_DECLARED_AND_GRANTED);
        assertThat(mActiveServices.mNotificationPermCache.get(TEST_UID)).isTrue();
    }

    @Test
    public void testGetNotificationPermissionState_notificationPermissionDeclaredAndDenied() {
        mActiveServices = new ActiveServices(mService);
        mPackageManagerInternal = mock(PackageManagerInternal.class);
        mContext = mock(Context.class);

        ServiceRecord r = createServiceRecord();
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);

        PackageInfo packageInfo = setupPackageInfo(true);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);

        setFieldValue(ActivityManagerService.class, mService, "mContext", mContext);
        when(mContext.checkPermission(eq(Manifest.permission.POST_NOTIFICATIONS), eq(TEST_PID),
                eq(TEST_UID)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        int result = mActiveServices.getNotificationPermissionState(r);

        assertThat(result)
                .isEqualTo(
                        FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_DECLARED_BUT_DENIED);
        assertThat(mActiveServices.mNotificationPermCache.get(TEST_UID)).isTrue();
    }

    @Test
    public void testGetNotificationPermissionState_notificationPermissionCacheHit() {
        mActiveServices = new ActiveServices(mService);
        mPackageManagerInternal = mock(PackageManagerInternal.class);

        ServiceRecord r = createServiceRecord();
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);

        PackageInfo packageInfo = setupPackageInfo(false);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);


        // Populates cache on first call
        mActiveServices.getNotificationPermissionState(r);
        verify(mPackageManagerInternal, times(1)).getPackageInfo(eq(PACKAGE_NAME_1), anyLong(),
                eq(TEST_UID), eq(TEST_USERID));

        // Gets the permission state from cache on second call
        int result = mActiveServices.getNotificationPermissionState(r);
        assertThat(result)
                .isEqualTo(
                        FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_NOT_DECLARED);
        verify(mPackageManagerInternal, times(1)).getPackageInfo(eq(PACKAGE_NAME_1), anyLong(),
                eq(TEST_UID), eq(TEST_USERID));
    }


    @Test
    public void testGetNotificationPermissionState_cacheInvalidationOnPackageUpdate() {
        mActiveServices = new ActiveServices(mService);
        mPackageManagerInternal = mock(PackageManagerInternal.class);
        mContext = mock(Context.class);

        ServiceRecord r = createServiceRecord();
        when(mService.getPackageManagerInternal()).thenReturn(mPackageManagerInternal);

        PackageInfo packageInfo = setupPackageInfo(false);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);

        // Populates cache with false
        mActiveServices.getNotificationPermissionState(r);
        assertThat(mActiveServices.mNotificationPermCache.get(TEST_UID)).isFalse();

        // Triggering package update
        mActiveServices.mPackageMonitor.onPackageUpdateFinished(PACKAGE_NAME_1, TEST_UID);

        packageInfo = setupPackageInfo(true);
        when(mPackageManagerInternal.getPackageInfo(eq(PACKAGE_NAME_1), anyLong(), eq(TEST_UID),
                eq(TEST_USERID)))
                .thenReturn(packageInfo);


        setFieldValue(ActivityManagerService.class, mService, "mContext", mContext);
        when(mContext.checkPermission(eq(Manifest.permission.POST_NOTIFICATIONS), eq(TEST_PID),
                eq(TEST_UID)))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        int result = mActiveServices.getNotificationPermissionState(r);
        assertThat(result)
                .isEqualTo(
                        FOREGROUND_SERVICE_STATE_CHANGED__FGS_NOTIFICATION_PERMISSION_STATE__FGS_NOTIFICATION_PERMISSION_DECLARED_AND_GRANTED);
    }

    private ServiceRecord createServiceRecord() {
        ServiceRecord r = mock(ServiceRecord.class);
        setFieldValue(ServiceRecord.class, r, "packageName", PACKAGE_NAME_1);
        r.appInfo = new ApplicationInfo();
        r.appInfo.uid = TEST_UID;
        r.appInfo.packageName = PACKAGE_NAME_1;
        r.appInfo.seInfo = "";
        setFieldValue(ServiceRecord.class, r, "userId", TEST_USERID);
        ProcessRecord processRecord = mock(ProcessRecord.class);
        when(processRecord.getPid()).thenReturn(TEST_PID);
        when(r.getHostProcess()).thenReturn(processRecord);
        ComponentName componentName = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        when(r.getComponentName()).thenReturn(componentName);
        setFieldValue(ServiceRecordInternal.class, r, "instanceName", componentName);
        setFieldValue(ServiceRecord.class, r, "processName", PROCESS_NAME_1);
        setFieldValue(ServiceRecord.class, r, "intent", new Intent.FilterComparison(new Intent()));
        setFieldValue(ServiceRecord.class, r, "mRecentCallingPackage", PACKAGE_NAME_1);

        ServiceInfo si = new ServiceInfo();
        si.applicationInfo = r.appInfo;
        si.processName = PROCESS_NAME_1;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);

        return r;
    }

    private PackageInfo setupPackageInfo(boolean declarePermission) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE_NAME_1;
        if (declarePermission) {
            packageInfo.requestedPermissions = new String[]{Manifest.permission.POST_NOTIFICATIONS};
        } else {
            packageInfo.requestedPermissions = new String[]{};
        }
        return packageInfo;
    }

    @Test
    public void testServiceForegroundAnrWarning() {
        ActiveServices activeServices = new ActiveServices(mService);
        mService.mConstants = mock(ActivityManagerConstants.class);
        mService.mConstants.mServiceStartForegroundTimeoutMs = 10000;

        ComponentName componentName = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        ServiceRecord serviceRecord = createServiceRecord();

        activeServices.serviceForegroundAnrWarning(serviceRecord, ANR_ID, ELAPSED_TIME_MS);

        verify(mService)
                .notifyAnrWarning(
                        eq(TEST_UID),
                        eq(ANR_ID),
                        eq(AnrTypes.ANR_TYPE_START_FOREGROUND_SERVICE),
                        eq(ELAPSED_TIME_MS),
                        eq((long) mService.mConstants.mServiceStartForegroundTimeoutMs),
                        eq("Foreground Service: " + componentName));
    }

    @Test
    public void testOnShortFgsAnrTimeoutWarning() {
        ActiveServices activeServices = new ActiveServices(mService);

        ComponentName componentName = new ComponentName(PACKAGE_NAME_1, SERVICE_NAME);
        ServiceRecord serviceRecord = createServiceRecord();
        ServiceRecord.ShortFgsInfo shortFgsInfo = mock(ServiceRecord.ShortFgsInfo.class);
        when(serviceRecord.getShortFgsInfo()).thenReturn(shortFgsInfo);

        long anrTime = 20000L;
        long startTime = 5000L;
        long expectedTimeoutMs = anrTime - startTime;
        when(shortFgsInfo.getAnrTime()).thenReturn(anrTime);
        when(shortFgsInfo.getStartTime()).thenReturn(startTime);

        activeServices.onShortFgsAnrTimeoutWarning(serviceRecord, ANR_ID, ELAPSED_TIME_MS);

        verify(mService)
                .notifyAnrWarning(
                        eq(TEST_UID),
                        eq(ANR_ID),
                        eq(AnrTypes.ANR_TYPE_FOREGROUND_SHORT_SERVICE_TIMEOUT),
                        eq(ELAPSED_TIME_MS),
                        eq(expectedTimeoutMs),
                        eq("Short Foreground Service: " + componentName));
    }

    @Test
    public void testOnShortFgsAnrTimeoutWarning_nullShortFgsInfo() {
        ActiveServices activeServices = new ActiveServices(mService);

        ServiceRecord serviceRecord = createServiceRecord();
        when(serviceRecord.getShortFgsInfo()).thenReturn(null);

        activeServices.onShortFgsAnrTimeoutWarning(serviceRecord, ANR_ID, ELAPSED_TIME_MS);

        verify(mService, never())
                .notifyAnrWarning(anyInt(), anyInt(), anyInt(), anyLong(), anyLong(), anyString());
    }

    @Test
    public void testServiceTimeoutAnrWarning() {
        ActiveServices activeServices = new ActiveServices(mService);
        mService.mConstants = mock(ActivityManagerConstants.class);
        mService.mConstants.SERVICE_TIMEOUT = 20000L;

        ProcessRecord proc = mock(ProcessRecord.class);
        setFieldValue(ProcessRecordInternal.class, proc, "uid", TEST_UID);
        proc.info = new ApplicationInfo();
        proc.info.uid = TEST_UID;

        when(proc.getThread()).thenReturn(mock(android.app.IApplicationThread.class));
        when(proc.isKilled()).thenReturn(false);

        ProcessList processList = mock(ProcessList.class);
        setFieldValue(ActivityManagerService.class, mService, "mProcessList", processList);
        when(processList.isInLruListLOSP(proc)).thenReturn(true);

        ProcessServiceRecord psr = mock(ProcessServiceRecord.class);
        setFieldValue(ProcessRecord.class, proc, "mServices", psr);

        ServiceRecord serviceRecord = createServiceRecord();

        // Set executingStart to 30s ago (timeout is 20s)
        long now = SystemClock.uptimeMillis();
        long elapsedTimeMs = 30000L;
        serviceRecord.executingStart = now - elapsedTimeMs;

        // Setup PSR to return 1 executing service that is timeout
        when(psr.numberOfExecutingServices()).thenReturn(1);
        when(psr.getExecutingServiceAt(0)).thenReturn(serviceRecord);
        when(psr.isExecServicesFg()).thenReturn(true);

        activeServices.serviceTimeoutAnrWarning(proc, ANR_ID, elapsedTimeMs);

        verify(mService)
                .notifyAnrWarning(
                        eq(TEST_UID),
                        eq(ANR_ID),
                        eq(AnrTypes.ANR_TYPE_EXECUTE_SERVICE),
                        eq(elapsedTimeMs),
                        eq(mService.mConstants.SERVICE_TIMEOUT),
                        contains("Executing Service:"));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testServiceRecordGetUid_PccService() {
        // This test verifies that ServiceRecord.getUid() returns the PCC UID
        // when the service is configured to run in the PCC sandbox.
        // This indirectly tests the change in ServiceRecord.java where
        // serviceInfo.getUid() is used instead of serviceInfo.applicationInfo.uid.

        // Create a PCC ServiceRecord
        ServiceRecord r = createPccServiceRecord();

        // Verify that getUid() returns the PCC UID (30001)
        // createPccServiceRecord sets pccUid to 30001 and flag FLAG_RUN_IN_PCC_SANDBOX
        assertThat(r.serviceInfo.getUid()).isEqualTo(TEST_UID_PCC);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testServiceRecordGetUid_NormalService() {
        // This test verifies that ServiceRecord.getUid() returns the App UID
        // when the service is NOT configured to run in the PCC sandbox.

        // Create a normal ServiceRecord
        ServiceRecord r = createServiceRecord();

        // Verify that getUid() returns the App UID (TEST_UID = 10123)
        assertThat(r.serviceInfo.getUid()).isEqualTo(TEST_UID);
    }

    /**
     * Verify that {@link ActiveServices#rebindServiceConnectionsLocked} correctly handles
     * successful flag updates, ensuring LRU and OOM adj are updated for the host process.
     */
    @Test
    public void testRebindServiceConnectionsLocked_Success() {
        prepareTestRebindServiceConnections();
        final IBinder binder = mock(IBinder.class);
        final ProcessRecord host = mock(ProcessRecord.class);
        final ConnectionRecord r = createConnectionRecord(host, Context.BIND_IMPORTANT);
        final ArrayList<ConnectionRecord> clist = new ArrayList<>();
        clist.add(r);

        final long flags = Context.BIND_IMPORTANT | Context.BIND_NOT_FOREGROUND;
        when(mService.mProcessStateController.updateConnectionFlags(r, flags)).thenReturn(true);

        boolean needOomAdj = mActiveServices.rebindServiceConnectionsLocked(binder, clist, flags);

        assertThat(needOomAdj).isTrue();
        verify(mService).updateLruProcessLocked(eq(host), eq(true), any());
        verify(mService).enqueueOomAdjTargetLocked(eq(host));
    }

    /**
     * Verify that {@link ActiveServices#rebindServiceConnectionsLocked} correctly handles
     * cases where no OOM adjustment is needed because the flags update didn't change the
     * process state.
     */
    @Test
    public void testRebindServiceConnectionsLocked_NoOomAdjNeeded() {
        prepareTestRebindServiceConnections();
        final IBinder binder = mock(IBinder.class);
        final ProcessRecord host = mock(ProcessRecord.class);
        final ConnectionRecord r = createConnectionRecord(host, Context.BIND_IMPORTANT);
        final ArrayList<ConnectionRecord> clist = new ArrayList<>();
        clist.add(r);

        final long flags = Context.BIND_IMPORTANT | Context.BIND_NOT_FOREGROUND;
        when(mService.mProcessStateController.updateConnectionFlags(r, flags)).thenReturn(false);

        boolean needOomAdj = mActiveServices.rebindServiceConnectionsLocked(binder, clist, flags);

        assertThat(needOomAdj).isFalse();
        verify(mService, never()).updateLruProcessLocked(any(), anyBoolean(), any());
        verify(mService, never()).enqueueOomAdjTargetLocked(any());
    }

    /**
     * Verify that {@link ActiveServices#rebindServiceConnectionsLocked} throws
     * {@link IllegalArgumentException} when attempting to update non-updateable flags.
     */
    @Test
    public void testRebindServiceConnectionsLocked_InvalidFlags() {
        prepareTestRebindServiceConnections();
        final IBinder binder = mock(IBinder.class);
        final ProcessRecord host = mock(ProcessRecord.class);
        final ConnectionRecord r = createConnectionRecord(host, Context.BIND_IMPORTANT);
        final ArrayList<ConnectionRecord> clist = new ArrayList<>();
        clist.add(r);

        // BIND_DEBUG_UNBIND is not in BIND_UPDATEABLE_FLAGS
        final long flags = Context.BIND_IMPORTANT | Context.BIND_DEBUG_UNBIND;

        try {
            mActiveServices.rebindServiceConnectionsLocked(binder, clist, flags);
            assertWithMessage("Should throw IllegalArgumentException").fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    /**
     * Verify that {@link ActiveServices#rebindServiceConnectionsLocked} correctly handles
     * multiple connections, ensuring LRU and OOM adj are updated only for the processes
     * that had their connection flags successfully updated.
     */
    @Test
    public void testRebindServiceConnectionsLocked_MultipleConnections() {
        prepareTestRebindServiceConnections();
        final IBinder binder = mock(IBinder.class);
        final ProcessRecord host1 = mock(ProcessRecord.class);
        final ProcessRecord host2 = mock(ProcessRecord.class);
        final ConnectionRecord r1 = createConnectionRecord(host1, Context.BIND_IMPORTANT);
        final ConnectionRecord r2 = createConnectionRecord(host2, Context.BIND_IMPORTANT);
        final ArrayList<ConnectionRecord> clist = new ArrayList<>();
        clist.add(r1);
        clist.add(r2);

        final long flags = Context.BIND_IMPORTANT | Context.BIND_NOT_FOREGROUND;
        when(mService.mProcessStateController.updateConnectionFlags(r1, flags)).thenReturn(true);
        when(mService.mProcessStateController.updateConnectionFlags(r2, flags)).thenReturn(false);

        boolean needOomAdj = mActiveServices.rebindServiceConnectionsLocked(binder, clist, flags);

        assertThat(needOomAdj).isTrue();
        verify(mService).updateLruProcessLocked(eq(host1), eq(true), any());
        verify(mService).enqueueOomAdjTargetLocked(eq(host1));
        verify(mService, never()).updateLruProcessLocked(eq(host2), anyBoolean(), any());
        verify(mService, never()).enqueueOomAdjTargetLocked(eq(host2));
    }

    /**
     * Verify that {@link ActiveServices#rebindServiceConnectionsLocked} correctly handles
     * normalization of BIND_EXTERNAL_SERVICE flags, treating BIND_EXTERNAL_SERVICE and
     * BIND_EXTERNAL_SERVICE_LONG as equivalent.
     */
    @Test
    public void testRebindServiceConnectionsLocked_normalizeExternalServiceFlags() {
        prepareTestRebindServiceConnections();
        final IBinder binder = mock(IBinder.class);
        final ProcessRecord host = mock(ProcessRecord.class);

        // Case 1: Existing has BIND_EXTERNAL_SERVICE
        final long legacyFlags =
                Context.BIND_IMPORTANT | Integer.toUnsignedLong(Context.BIND_EXTERNAL_SERVICE);
        final ConnectionRecord r1 = createConnectionRecord(host, legacyFlags);
        final ArrayList<ConnectionRecord> clist1 = new ArrayList<>();
        clist1.add(r1);

        when(mService.mProcessStateController.updateConnectionFlags(eq(r1), anyLong()))
                .thenReturn(false);

        // rebind with BIND_EXTERNAL_SERVICE_LONG is normalized to BIND_EXTERNAL_SERVICE.
        final long flags1 = Context.BIND_IMPORTANT | Context.BIND_EXTERNAL_SERVICE_LONG;
        mActiveServices.rebindServiceConnectionsLocked(binder, clist1, flags1);

        verify(mService.mProcessStateController)
                .updateConnectionFlags(r1, legacyFlags);

        final long flags2 = Context.BIND_WAIVE_PRIORITY | Context.BIND_EXTERNAL_SERVICE_LONG;
        mActiveServices.rebindServiceConnectionsLocked(binder, clist1, flags2);

        verify(mService.mProcessStateController)
                .updateConnectionFlags(
                        r1,
                        Context.BIND_WAIVE_PRIORITY
                                | Integer.toUnsignedLong(Context.BIND_EXTERNAL_SERVICE));

        // Case 2: Existing has BIND_EXTERNAL_SERVICE_LONG
        final long newFlags = Context.BIND_IMPORTANT | Context.BIND_EXTERNAL_SERVICE_LONG;
        final ConnectionRecord r2 = createConnectionRecord(host, newFlags);
        final ArrayList<ConnectionRecord> clist2 = new ArrayList<>();
        clist2.add(r2);

        when(mService.mProcessStateController.updateConnectionFlags(eq(r2), anyLong()))
                .thenReturn(false);

        // rebind with the same flag should not trigger update.
        mActiveServices.rebindServiceConnectionsLocked(binder, clist2, newFlags);

        verify(mService.mProcessStateController)
                .updateConnectionFlags(r2, newFlags);

        // Existing has BIND_EXTERNAL_SERVICE_LONG, rebind with BIND_EXTERNAL_SERVICE
        final long flags3 = Context.BIND_IMPORTANT
                | Integer.toUnsignedLong(Context.BIND_EXTERNAL_SERVICE);
        mActiveServices.rebindServiceConnectionsLocked(binder, clist2, flags3);

        verify(mService.mProcessStateController, times(2))
                .updateConnectionFlags(r2, newFlags);

        // Case 3: Existing has both, rebind with BIND_EXTERNAL_SERVICE_LONG
        final long bothFlags = Context.BIND_IMPORTANT
                | Context.BIND_EXTERNAL_SERVICE_LONG
                | Integer.toUnsignedLong(Context.BIND_EXTERNAL_SERVICE);
        final ConnectionRecord r3 = createConnectionRecord(host, bothFlags);
        final ArrayList<ConnectionRecord> clist3 = new ArrayList<>();
        clist3.add(r3);

        when(mService.mProcessStateController.updateConnectionFlags(eq(r3), anyLong()))
                .thenReturn(false);

        final long flags4 = Context.BIND_IMPORTANT | Context.BIND_EXTERNAL_SERVICE_LONG;
        mActiveServices.rebindServiceConnectionsLocked(binder, clist3, flags4);

        verify(mService.mProcessStateController)
                .updateConnectionFlags(r3, bothFlags);
    }

    /**
     * Helper to prepare {@code mActiveServices} as a real object and mock
     * {@code mService.mProcessStateController}.
     */
    private void prepareTestRebindServiceConnections() {
        mActiveServices = new ActiveServices(mService);
        mService.mProcessStateController = mock(ProcessStateController.class);
    }

    /**
     * Helper to create a {@link ConnectionRecord} with a mocked host process.
     */
    private ConnectionRecord createConnectionRecord(ProcessRecord host, long flags) {
        final ServiceRecord sr = mock(ServiceRecord.class);
        when(sr.getHostProcess()).thenReturn(host);

        final AppBindRecord abr = new AppBindRecord(sr, mock(IntentBindRecord.class),
                mock(ProcessRecord.class), mock(ProcessRecord.class));

        return new ConnectionRecord(abr, null, null, flags, 0, null, 0, null, null, null);
    }
}
