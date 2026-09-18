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

package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService.Injector;
import com.android.server.appop.AppOpsService;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Tests to verify PROCESS_START_TIME atom logging.
 */
public class ProcessStartTimeTest {
    private static final String TAG = "ProcessStartTimeTest";

    private static final String PACKAGE = "com.android.test.app";
    private static final int APP_UID = Process.myUid();
    private static final int APP_PID = Process.myPid();
    private static final int CALLER_UID = 10001;
    private static final String CALLER_PROCESS_NAME = "com.android.caller";
    private static final long START_SEQ = 100L;
    private static final String PROVIDER_AUTHORITY = "com.test.authority";

    @Rule
    public final ApplicationExitInfoTest.ServiceThreadRule
            mServiceThreadRule = new ApplicationExitInfoTest.ServiceThreadRule();

    private Context mContext;
    private HandlerThread mHandlerThread;
    private MockitoSession mMockingSession;

    @Mock private AppOpsService mAppOpsService;
    @Mock private PackageManagerInternal mPackageManagerInt;
    @Mock private ActivityTaskManagerInternal mActivityTaskManagerInt;
    @Mock private BatteryStatsService mBatteryStatsService;

    private ActivityManagerService mAms;

    @Before
    public void setUp() throws Exception {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(FrameworkStatsLog.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();

        removeLocalServices(PackageManagerInternal.class, ActivityTaskManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInt);
        LocalServices.addService(ActivityTaskManagerInternal.class, mActivityTaskManagerInt);

        doReturn(new ComponentName("", "")).when(mPackageManagerInt).getSystemUiServiceComponent();
        doReturn(true).when(mActivityTaskManagerInt).attachApplication(any());

        mAms = new ActivityManagerService(
                new TestInjector(mContext), mServiceThreadRule.getThread());
        mAms.mConstants.loadDeviceConfigConstants();
        mAms.mConstants.mEnableWaitForFinishAttachApplication = true;
        mAms.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mAms.mActivityTaskManager.initialize(null, null, mAms.mProcessStateController,
                mHandlerThread.getLooper());
        mAms.mAtmInternal = mActivityTaskManagerInt;
        mAms.mPackageManagerInt = mPackageManagerInt;
        mAms.mProcessesReady = true;
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
        removeLocalServices(PackageManagerInternal.class, ActivityTaskManagerInternal.class);
    }

    private void removeLocalServices(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            LocalServices.removeServiceForTest(clazz);
        }
    }

    @Test
    public void testProcessStartTimeLogged() throws Exception {
        final ProcessRecord app = makeProcessRecord(PACKAGE, APP_UID, APP_PID);
        final HostingRecord hostingRecord = new HostingRecord(HostingRecord.HOSTING_TYPE_BROADCAST,
                new ComponentName(PACKAGE, "Receiver"), "action",
                HostingRecord.TRIGGER_TYPE_PUSH_MESSAGE, false, CALLER_UID, CALLER_PROCESS_NAME);
        app.setHostingRecord(hostingRecord);
        app.setStartSeq(START_SEQ);

        synchronized (mAms.mPidsSelfLocked) {
            mAms.mPidsSelfLocked.doAddInternal(APP_PID, app);
        }

        mAms.finishAttachApplication(START_SEQ, 0);

        verify(() -> FrameworkStatsLog.write(
                eq(FrameworkStatsLog.PROCESS_START_TIME),
                eq(APP_UID),
                eq(APP_PID),
                eq(PACKAGE),
                eq(FrameworkStatsLog.PROCESS_START_TIME__TYPE__COLD),
                anyLong(), // startElapsedTime
                anyInt(),  // bindApplicationDelay
                anyInt(),  // processStartDelay
                eq(hostingRecord.getType()),
                eq(hostingRecord.getName()),
                any(),     // shortAction
                eq(HostingRecord.getHostingTypeIdStatsd(hostingRecord.getType())),
                eq(HostingRecord.getTriggerTypeForStatsd(hostingRecord.getTriggerType())),
                eq(CALLER_UID),
                eq(CALLER_PROCESS_NAME),
                eq(hostingRecord.getHostingAuthority()),
                eq(hostingRecord.isProviderStable()),
                eq(HostingRecord.ZYGOTE_TYPE_REGULAR)
        ));
    }

    @Test
    public void testProcessStartTimeLogged_forContentProvider() throws Exception {
        final ProcessRecord app = makeProcessRecord(PACKAGE, APP_UID, APP_PID);
        final boolean stable = true;
        final HostingRecord hostingRecord = HostingRecord.forContentProvider(
                new ComponentName(PACKAGE, "Provider"), false, CALLER_UID,
                CALLER_PROCESS_NAME, PROVIDER_AUTHORITY, stable);
        app.setHostingRecord(hostingRecord);
        app.setStartSeq(START_SEQ);

        synchronized (mAms.mPidsSelfLocked) {
            mAms.mPidsSelfLocked.doAddInternal(APP_PID, app);
        }

        mAms.finishAttachApplication(START_SEQ, 0);

        verify(() -> FrameworkStatsLog.write(
                eq(FrameworkStatsLog.PROCESS_START_TIME),
                eq(APP_UID),
                eq(APP_PID),
                eq(PACKAGE),
                eq(FrameworkStatsLog.PROCESS_START_TIME__TYPE__COLD),
                anyLong(), // startElapsedTime
                anyInt(),  // bindApplicationDelay
                anyInt(),  // processStartDelay
                eq(hostingRecord.getType()),
                eq(hostingRecord.getName()),
                any(),     // shortAction
                eq(HostingRecord.getHostingTypeIdStatsd(hostingRecord.getType())),
                eq(HostingRecord.getTriggerTypeForStatsd(hostingRecord.getTriggerType())),
                eq(CALLER_UID),
                eq(CALLER_PROCESS_NAME),
                eq(PROVIDER_AUTHORITY),
                eq(stable),
                eq(HostingRecord.ZYGOTE_TYPE_REGULAR)
        ));
    }

    private ProcessRecord makeProcessRecord(String packageName, int uid, int pid) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        ai.processName = packageName;
        ai.uid = uid;
        ProcessRecord app = spy(new ProcessRecord(mAms, ai, ai.processName, uid));
        app.setPid(pid);
        app.setStartUid(uid);
        final ApplicationThreadDeferred thread = mock(ApplicationThreadDeferred.class);
        doReturn(mock(IBinder.class)).when(thread).asBinder();
        app.makeActive(thread, mAms.mProcessStats);
        return app;
    }

    private class TestInjector extends Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mHandlerThread.getThreadHandler();
        }

        @Override
        public ProcessList getProcessList(ActivityManagerService service) {
            return new ProcessList();
        }

        @Override
        public BatteryStatsService getBatteryStatsService() {
            return mBatteryStatsService;
        }
    }

    static {
        System.loadLibrary("mockingservicestestjni");
    }
}
