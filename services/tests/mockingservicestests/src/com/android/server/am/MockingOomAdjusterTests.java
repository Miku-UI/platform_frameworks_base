/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_IMPLICIT_CPU_TIME;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_RECENT;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_HEAVY_WEIGHT;
import static android.app.ActivityManager.PROCESS_STATE_HOME;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;
import static android.app.ActivityManager.PROCESS_STATE_RECEIVER;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_TOP_SLEEPING;
import static android.app.ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BACKUP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_NONE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.am.ActivityManagerService.FOLLOW_UP_OOMADJUSTER_UPDATE_MSG;
import static com.android.server.am.MockingOomAdjusterTests.ProcessStateAssert.assertThatProcess;
import static com.android.server.am.OomAdjuster.CPU_TIME_REASON_OTHER;
import static com.android.server.am.OomAdjuster.CPU_TIME_REASON_TRANSMITTED;
import static com.android.server.am.OomAdjuster.CPU_TIME_REASON_TRANSMITTED_LEGACY;
import static com.android.server.am.OomAdjuster.IMPLICIT_CPU_TIME_REASON_OTHER;
import static com.android.server.am.OomAdjuster.IMPLICIT_CPU_TIME_REASON_TRANSMITTED;
import static com.android.server.am.OomAdjuster.IMPLICIT_CPU_TIME_REASON_TRANSMITTED_LEGACY;
import static com.android.server.am.ProcessList.BACKUP_APP_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_IMPORTANCE_LEVELS;
import static com.android.server.am.ProcessList.CACHED_APP_MAX_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_MIN_ADJ;
import static com.android.server.am.ProcessList.FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.HEAVY_WEIGHT_APP_ADJ;
import static com.android.server.am.ProcessList.HOME_APP_ADJ;
import static com.android.server.am.ProcessList.INVALID_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_LOW_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.PERSISTENT_PROC_ADJ;
import static com.android.server.am.ProcessList.PERSISTENT_SERVICE_ADJ;
import static com.android.server.am.ProcessList.PREVIOUS_APP_ADJ;
import static com.android.server.am.ProcessList.PREVIOUS_APP_MAX_ADJ;
import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
import static com.android.server.am.ProcessList.SCHED_GROUP_FOREGROUND_WINDOW;
import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;
import static com.android.server.am.ProcessList.SERVICE_ADJ;
import static com.android.server.am.ProcessList.SERVICE_B_ADJ;
import static com.android.server.am.ProcessList.SYSTEM_ADJ;
import static com.android.server.am.ProcessList.UNKNOWN_ADJ;
import static com.android.server.am.ProcessList.VISIBLE_APP_ADJ;
import static com.android.server.am.ProcessList.VISIBLE_APP_MAX_ADJ;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_IS_PAUSING_OR_PAUSED;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_IS_STOPPING;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_IS_STOPPING_FINISHING;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_IS_VISIBLE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.ApplicationExitInfo;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.android.server.LocalServices;
import com.android.server.am.ProcessStateController.ProcessLruUpdater;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.tests.assertutils.FlagAssert;
import com.android.server.wm.ActivityServiceConnectionsHolder;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.WindowProcessController;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Test class for {@link OomAdjuster}.
 *
 * Build/Install/Run:
 * atest MockingOomAdjusterTests
 */
@Presubmit
public class MockingOomAdjusterTests {
    private static final int MOCKAPP_PID = 12345;
    private static final int MOCKAPP_UID = 12345;
    private static final String MOCKAPP_PROCESSNAME = "test #1";
    private static final String MOCKAPP_PACKAGENAME = "com.android.test.test1";
    private static final int MOCKAPP2_PID = MOCKAPP_PID + 1;
    private static final int MOCKAPP2_UID = MOCKAPP_UID + 1;
    private static final String MOCKAPP2_PROCESSNAME = "test #2";
    private static final String MOCKAPP2_PACKAGENAME = "com.android.test.test2";
    private static final int MOCKAPP3_PID = MOCKAPP_PID + 2;
    private static final int MOCKAPP3_UID = MOCKAPP_UID + 2;
    private static final String MOCKAPP3_PROCESSNAME = "test #3";
    private static final String MOCKAPP3_PACKAGENAME = "com.android.test.test3";
    private static final int MOCKAPP4_PID = MOCKAPP_PID + 3;
    private static final int MOCKAPP4_UID = MOCKAPP_UID + 3;
    private static final String MOCKAPP4_PROCESSNAME = "test #4";
    private static final String MOCKAPP4_PACKAGENAME = "com.android.test.test4";
    private static final int MOCKAPP5_PID = MOCKAPP_PID + 4;
    private static final int MOCKAPP5_UID = MOCKAPP_UID + 4;
    private static final String MOCKAPP5_PROCESSNAME = "test #5";
    private static final String MOCKAPP5_PACKAGENAME = "com.android.test.test5";
    private static final int MOCKAPP2_UID_OTHER = MOCKAPP2_UID + UserHandle.PER_USER_RANGE;
    private static final int MOCKAPP_ISOLATED_UID = Process.FIRST_ISOLATED_UID + 321;
    private static final String MOCKAPP_ISOLATED_PROCESSNAME = "isolated test #1";
    private static final int MOCKAPP_SDK_SANDBOX_UID = Process.FIRST_SDK_SANDBOX_UID + 654;
    private static final String MOCKAPP_SDK_SANDBOX_PROCESSNAME = "sandbox test #1";

    private static final long HANDLER_THREAD_TIMEOUT_MILLIS = 1_000L;

    private static int sFirstCachedAdj = ProcessList.CACHED_APP_MIN_ADJ
            + ProcessList.CACHED_APP_IMPORTANCE_LEVELS;

    private Context mContext;
    private ProcessStateController mProcessStateController;
    private ProcessStateController.ActivityStateAsyncUpdater mActivityStateAsyncUpdater;
    private ActiveUids mActiveUids;
    private PackageManagerInternal mPackageManagerInternal;
    private ActivityManagerService mService;
    private TestCachedAppOptimizer mTestCachedAppOptimizer;
    private OomAdjusterInjector mInjector = new OomAdjusterInjector();
    private ActivityManagerService.OomAdjusterCallback mCallback;

    private HandlerThread mActivityStateHandlerThread;
    private Handler mActivityStateHandler;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @SuppressWarnings("GuardedBy")
    @Before
    public void setUp() {
        mContext = getInstrumentation().getTargetContext();
        System.setProperty("dexmaker.share_classloader", "true");

        mPackageManagerInternal = mock(PackageManagerInternal.class);
        doReturn(new ComponentName("", "")).when(mPackageManagerInternal)
                .getSystemUiServiceComponent();
        // Remove stale instance of PackageManagerInternal if there is any
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);

        mService = mock(ActivityManagerService.class);
        mService.mPackageManagerInt = mPackageManagerInternal;

        mService.mConstants = new ActivityManagerConstants(mContext, mService,
                mContext.getMainThreadHandler());
        setFieldValue(ActivityManagerService.class, mService, "mContext",
                mContext);
        ProcessList pr = spy(new ProcessList());
        pr.mService = mService;
        AppProfiler profiler = mock(AppProfiler.class);
        setFieldValue(ActivityManagerService.class, mService, "mOomAdjObserverLock",
                new Object());
        setFieldValue(ActivityManagerService.class, mService, "mProcessList",
                pr);
        setFieldValue(ActivityManagerService.class, mService, "mHandler",
                mock(ActivityManagerService.MainHandler.class));
        setFieldValue(ActivityManagerService.class, mService, "mProcessStats",
                new ProcessStatsService(mService, new File(mContext.getFilesDir(), "procstats")));
        setFieldValue(ActivityManagerService.class, mService, "mBackupTargets",
                mock(SparseArray.class));
        setFieldValue(ActivityManagerService.class, mService, "mUserController",
                mock(UserController.class));
        setFieldValue(ActivityManagerService.class, mService, "mAppProfiler", profiler);
        setFieldValue(ActivityManagerService.class, mService, "mProcLock",
                new ActivityManagerProcLock());
        setFieldValue(ActivityManagerService.class, mService, "mServices",
                spy(new ActiveServices(mService)));
        setFieldValue(ActivityManagerService.class, mService, "mInternal",
                mock(ActivityManagerService.LocalService.class));
        setFieldValue(ActivityManagerService.class, mService, "mBatteryStatsService",
                mock(BatteryStatsService.class));
        setFieldValue(ActivityManagerService.class, mService, "mInjector",
                new ActivityManagerService.Injector(mContext));

        PhantomProcessList ppl = mock(PhantomProcessList.class);
        doNothing().when(ppl).setProcessGroupForPhantomProcessOfApp(any(ProcessRecord.class),
                anyInt());
        setFieldValue(ActivityManagerService.class, mService, "mPhantomProcessList", ppl);

        doReturn(mock(AppOpsManager.class)).when(mService).getAppOpsManager();
        doCallRealMethod().when(mService).enqueueOomAdjTargetLocked(any(ProcessRecord.class));
        doCallRealMethod().when(mService).updateOomAdjPendingTargetsLocked(OOM_ADJ_REASON_ACTIVITY);
        setFieldValue(AppProfiler.class, profiler, "mProfilerLock", new Object());

        doNothing().when(pr).enqueueProcessChangeItemLocked(anyInt(), anyInt(), anyInt(),
                anyInt());
        doNothing().when(pr).enqueueProcessChangeItemLocked(anyInt(), anyInt(), anyInt(),
                anyBoolean());
        mActiveUids = new ActiveUids(null);
        mActivityStateHandlerThread = new HandlerThread("ActivityStateThread");
        mActivityStateHandlerThread.start();
        mActivityStateHandler = new Handler(mActivityStateHandlerThread.getLooper());
        ProcessLruUpdater lruUpdater = new ProcessLruUpdater() {
            @Override
            public void updateLruProcessLocked(ProcessRecord app, boolean activityChange,
                    ProcessRecord client) {
                ArrayList<ProcessRecord> lru = mService.mProcessList.getLruProcessesLOSP();
                lru.remove(app);
                lru.add(app);
            }

            @Override
            public void removeLruProcessLocked(ProcessRecord app) {
                ArrayList<ProcessRecord> lru = mService.mProcessList.getLruProcessesLOSP();
                lru.remove(app);
            }
        };

        doCallRealMethod().when(mService).setCachedAppOptimizer(any(CachedAppOptimizer.class));
        doCallRealMethod().when(mService).getCachedAppOptimizer();
        mTestCachedAppOptimizer = new TestCachedAppOptimizer(mService);
        mService.setCachedAppOptimizer(mTestCachedAppOptimizer);

        mCallback = spy(mService.new OomAdjusterCallback());
        mProcessStateController = new ProcessStateController.Builder(mService,
                mService.mProcessList, mActiveUids, mCallback)
                .setProcessLruUpdater(lruUpdater)
                .setOomAdjusterInjector(mInjector)
                .build();
        mActivityStateAsyncUpdater = mProcessStateController.createActivityStateAsyncUpdater(
                mActivityStateHandlerThread.getLooper());
        mService.mProcessStateController = mProcessStateController;
        mService.mOomAdjuster = mService.mProcessStateController.getOomAdjuster();
        mService.mOomAdjuster.mAdjSeq = 10000;
        mService.mWakefulness = new AtomicInteger(PowerManagerInternal.WAKEFULNESS_AWAKE);

        mService.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mService.mActivityTaskManager.initialize(null, null, mProcessStateController,
                mActivityStateHandlerThread.getLooper());
        mService.mAtmInternal = spy(mService.mActivityTaskManager.getAtmInternal());
    }

    @SuppressWarnings("GuardedBy")
    @After
    public void tearDown() {
        mTestCachedAppOptimizer.throwFailure();
        mProcessStateController.getOomAdjuster().resetInternal();
        mActiveUids.clear();
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        mInjector.reset();
        mActivityStateHandlerThread.quitSafely();
        mTestCachedAppOptimizer.mCachedAppOptimizerThread.quitSafely();
    }

    private static <T> void setFieldValue(Class clazz, Object obj, String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
    }

    static class ProcessStateAssert {
        private final ProcessRecord mApp;

        private ProcessStateAssert(ProcessRecord app) {
            mApp = app;
        }

        static ProcessStateAssert assertThatProcess(ProcessRecord app) {
            return new ProcessStateAssert(app);
        }

        CpuTimeReasonsAssert hasCpuTimeCapability() {
            FlagAssert.assertThat(mApp.getSetCapability()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
            FlagAssert.assertThat(mApp.getCurCpuTimeReasons()).hasAnySet();
            return new CpuTimeReasonsAssert(mApp, mApp.getCurCpuTimeReasons());
        }

        ProcessStateAssert notHasCpuTimeCapability() {
            FlagAssert.assertThat(mApp.getSetCapability()).hasNotSet(PROCESS_CAPABILITY_CPU_TIME);
            FlagAssert.assertThat(mApp.getCurCpuTimeReasons()).isEmpty();
            return this;
        }

        CpuTimeReasonsAssert hasImplicitCpuTimeCapability() {
            FlagAssert.assertThat(mApp.getSetCapability()).hasSet(
                    PROCESS_CAPABILITY_IMPLICIT_CPU_TIME);
            FlagAssert.assertThat(mApp.getCurImplicitCpuTimeReasons()).hasAnySet();
            return new CpuTimeReasonsAssert(mApp, mApp.getCurImplicitCpuTimeReasons());
        }

        ProcessStateAssert notHasImplicitCpuTimeCapability() {
            FlagAssert.assertThat(mApp.getSetCapability()).hasNotSet(
                    PROCESS_CAPABILITY_IMPLICIT_CPU_TIME);
            FlagAssert.assertThat(mApp.getCurImplicitCpuTimeReasons()).isEmpty();
            return this;
        }

        ProcessStateAssert hasCapability(int capability) {
            FlagAssert.assertThat(mApp.getSetCapability()).hasSet(capability);
            return this;
        }

        ProcessStateAssert notHasCapability(int capability) {
            FlagAssert.assertThat(mApp.getSetCapability()).hasNotSet(capability);
            return this;
        }
    }

    static final class CpuTimeReasonsAssert extends ProcessStateAssert {
        private final FlagAssert mReasonsAssert;

        CpuTimeReasonsAssert(ProcessRecord app, int reasons) {
            super(app);
            mReasonsAssert = FlagAssert.assertThat(reasons);
        }

        CpuTimeReasonsAssert withReasons(int reasons) {
            mReasonsAssert.hasSet(reasons);
            return this;
        }

        CpuTimeReasonsAssert withoutReasons(int reasons) {
            mReasonsAssert.hasNotSet(reasons);
            return this;
        }

        CpuTimeReasonsAssert withExactReasons(int reasons) {
            mReasonsAssert.isEqualTo(reasons);
            return this;
        }
    }

    @SuppressWarnings("GuardedBy")
    private void setProcessesToLru(ProcessRecord... apps) {
        for (ProcessRecord app : apps) {
            updateProcessLru(app);
        }
    }

    private void updateProcessLru(ProcessRecord app) {
        mProcessStateController.updateLruProcess(app, false, null);
    }

    /**
     * Run updateOomAdjLocked().
     * - If there is no process specified, run updateOomAdjLocked(int) on existing lru
     * - If there's only one process, then it calls updateOomAdjLocked(ProcessRecord, int).
     * - Otherwise, sets the processes to the LRU and run updateOomAdjLocked(int).
     */
    @SuppressWarnings("GuardedBy")
    private void updateOomAdj(ProcessRecord... apps) {
        if (apps.length == 0) {
            updateProcessRecordNodes(mService.mProcessList.getLruProcessesLOSP());
            mProcessStateController.runFullUpdate(OOM_ADJ_REASON_NONE);
        } else {
            updateProcessRecordNodes(Arrays.asList(apps));
            if (apps.length == 1) {
                final ProcessRecord app = apps[0];
                updateProcessLru(app);
                mProcessStateController.runUpdate(app, OOM_ADJ_REASON_NONE);
            } else {
                setProcessesToLru(apps);
                mProcessStateController.runFullUpdate(OOM_ADJ_REASON_NONE);
                mService.mProcessList.getLruProcessesLOSP().clear();
            }
        }
    }

    /**
     * Run updateOomAdjPendingTargetsLocked().
     * - enqueues all provided processes to the pending list and lru before running
     */
    @SuppressWarnings("GuardedBy")
    private void updateOomAdjPending(ProcessRecord... apps) {
        setProcessesToLru(apps);
        for (ProcessRecord app : apps) {
            mProcessStateController.enqueueUpdateTarget(app);
        }
        mProcessStateController.runPendingUpdate(OOM_ADJ_REASON_NONE);
        mService.mProcessList.getLruProcessesLOSP().clear();
    }

    /**
     * Fix up the pointers in the {@link ProcessRecordNode#mApp}:
     * because we used the mokito spy objects all over the tests here, but the internal
     * pointers in the {@link ProcessRecordNode#mApp} actually point to the real object.
     * This needs to be fixed up here.
     */
    private void updateProcessRecordNodes(List<ProcessRecord> apps) {
        for (ProcessRecord app : apps) {
            ProcessRecord.updateProcessRecordNodes(app);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Persistent_TopUi_Sleeping() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(app, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasTopUi(app, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_ASLEEP);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_RESTRICTED);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Persistent_TopUi_Awake() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(app, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasTopUi(app, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_PERSISTENT_UI, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Persistent_TopApp() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(app, PERSISTENT_PROC_ADJ);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        setTopProcess(null);

        assertProcStates(app, PROCESS_STATE_PERSISTENT_UI, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TopApp_Awake() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        setTopProcess(null);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_RunningAnimations() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP_SLEEPING);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        mProcessStateController.setRunningRemoteAnimation(app, true);

        if (Flags.autoTriggerOomadjUpdates()) {
            // Do not manually run the update.
        } else {
            updateOomAdj(app);
        }

        assertProcStates(app, PROCESS_STATE_TOP_SLEEPING, VISIBLE_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_RunningInstrumentation() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setActiveInstrumentation(app, mock(ActiveInstrumentation.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        mProcessStateController.setActiveInstrumentation(app, null);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, FOREGROUND_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ReceivingBroadcast() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);

        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setIsReceivingBroadcast(app, true, SCHED_GROUP_BACKGROUND);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_RECEIVER, FOREGROUND_APP_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TopSleepingReceivingBroadcast() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP_SLEEPING);
        setTopProcess(app);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP_SLEEPING, FOREGROUND_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertTrue(app.getHasForegroundActivities());

        setIsReceivingBroadcast(app, true, SCHED_GROUP_BACKGROUND);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_RECEIVER, FOREGROUND_APP_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertTrue(app.getHasForegroundActivities());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ExecutingService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.startExecutingService(app.mServices, makeServiceRecord());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_SERVICE, FOREGROUND_APP_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TopApp_Sleeping() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP_SLEEPING);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_ASLEEP);
        updateOomAdj(app);
        setTopProcess(null);
        setTopProcessState(PROCESS_STATE_TOP);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        assertProcStates(app, PROCESS_STATE_TOP_SLEEPING, FOREGROUND_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_CachedEmpty() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        app.setCurRawAdj(CACHED_APP_MIN_ADJ);
        app.setCurAdj(CACHED_APP_MIN_ADJ);
        setTopProcess(null);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_VisibleActivities() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertFalse(app.isCached());
        assertFalse(app.isEmpty());
        assertEquals("vis-activity", app.getAdjType());
        assertFalse(app.isCached());
        assertFalse(app.isEmpty());
        assertEquals("vis-activity", app.getAdjType());
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE
                | WindowProcessController.ACTIVITY_STATE_FLAG_RESUMED_SPLIT_SCREEN);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertEquals("resumed-split-screen-activity", app.getAdjType());
        assertEquals("resumed-split-screen-activity", app.getAdjType());
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE
                | WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertEquals("perceptible-freeform-activity", app.getAdjType());
        assertEquals("perceptible-freeform-activity", app.getAdjType());
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE
                | WindowProcessController.ACTIVITY_STATE_FLAG_VISIBLE_MULTI_WINDOW_MODE);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ,
                SCHED_GROUP_FOREGROUND_WINDOW);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertEquals("vis-multi-window-activity", app.getAdjType());
        assertEquals("vis-multi-window-activity", app.getAdjType());
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE
                | WindowProcessController.ACTIVITY_STATE_FLAG_OCCLUDED_FREEFORM);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertEquals("occluded-freeform-activity", app.getAdjType());
        assertEquals("occluded-freeform-activity", app.getAdjType());
        assertThatProcess(app).hasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_PausingStoppingActivities() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_PAUSING_OR_PAUSED);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, PERCEPTIBLE_APP_ADJ, SCHED_GROUP_DEFAULT,
                "pause-activity");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_STOPPING);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_LAST_ACTIVITY, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "stop-activity");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCpuTimeCapability();

        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_STOPPING_FINISHING);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_CACHED_ACTIVITY, CACHED_APP_MIN_ADJ,
                SCHED_GROUP_BACKGROUND, "cch-act");
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_RecentTasks() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasRecentTasks(wpc, true);
        app.setLastTopTime(SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        doCallRealMethod().when(wpc).hasRecentTasks();

        assertEquals(PROCESS_STATE_CACHED_RECENT, app.getSetProcState());
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_FgServiceLocation() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasForegroundServices(app.mServices, true,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, /* hasNoneType=*/false);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_FgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasForegroundServices(app.mServices, true, 0, /* hasNoneType=*/
                true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_FgService_ShortFgs() {
        mService.mConstants.TOP_TO_FGS_GRACE_DURATION = 100_000;
        mService.mConstants.mShortFgsProcStateExtraWaitDuration = 200_000;

        ServiceRecord s = ServiceRecord.newEmptyInstanceForTest(mService);
        s.appInfo = new ApplicationInfo();
        mProcessStateController.setStartRequested(s, true);
        s.setIsForeground(true);
        s.setForegroundServiceType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        mProcessStateController.setShortFgsInfo(s, SystemClock.uptimeMillis());

        // SHORT_SERVICE FGS will get IMP_FG and a slightly different recent-adjustment.
        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            mProcessStateController.startService(app.mServices, s);
            mProcessStateController.setHasForegroundServices(app.mServices, true,
                    FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, /* hasNoneType=*/false);
            app.setLastTopTime(SystemClock.uptimeMillis());
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

            updateOomAdj(app);

            assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE,
                    PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 1, SCHED_GROUP_DEFAULT);
            assertThatProcess(app).hasImplicitCpuTimeCapability();
            assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
        }

        // SHORT_SERVICE, but no longer recent.
        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            mProcessStateController.setHasForegroundServices(app.mServices, true,
                    FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, /* hasNoneType=*/false);
            mProcessStateController.startService(app.mServices, s);
            app.setLastTopTime(SystemClock.uptimeMillis()
                    - mService.mConstants.TOP_TO_FGS_GRACE_DURATION);
            mService.mWakefulness.set(PowerManagerInternal.WAKEFULNESS_AWAKE);

            updateOomAdj(app);

            assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE,
                    PERCEPTIBLE_MEDIUM_APP_ADJ + 1, SCHED_GROUP_DEFAULT);
            assertThatProcess(app).hasImplicitCpuTimeCapability();
            assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
        }

        // SHORT_SERVICE, timed out already.
        s = ServiceRecord.newEmptyInstanceForTest(mService);
        s.appInfo = new ApplicationInfo();

        mProcessStateController.setStartRequested(s, true);
        s.setIsForeground(true);
        s.setForegroundServiceType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        mProcessStateController.setShortFgsInfo(s, SystemClock.uptimeMillis()
                - mService.mConstants.mShortFgsTimeoutDuration
                - mService.mConstants.mShortFgsProcStateExtraWaitDuration);
        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            mProcessStateController.setHasForegroundServices(app.mServices, true,
                    FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, /* hasNoneType=*/false);
            mProcessStateController.startService(app.mServices, s);
            app.setLastTopTime(SystemClock.uptimeMillis()
                    - mService.mConstants.TOP_TO_FGS_GRACE_DURATION);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

            updateOomAdj(app);

            // Procstate should be lower than FGS. (It should be SERVICE)
            assertEquals(app.getSetProcState(), PROCESS_STATE_SERVICE);
            assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_bindingWithAllowFreeze() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        // App with a visible activity binds to app2 without any special flag.
        bindService(app2, app, null, null, 0, mock(IBinder.class));

        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // App with a visible activity binds to app3 with ALLOW_FREEZE.
        bindService(app3, app, null, null, Context.BIND_ALLOW_FREEZE, mock(IBinder.class));

        setProcessesToLru(app, app2, app3);

        updateOomAdj(app);

        assertThatProcess(app).hasCpuTimeCapability();
        assertThatProcess(app2).hasCpuTimeCapability()
                .withExactReasons(CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app3).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_bindingWithSimulateAllowFreeze() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        // App with a visible activity binds to app2 with SIMULATE_ALLOW_FREEZE
        bindService(app2, app, null, null, Context.BIND_SIMULATE_ALLOW_FREEZE,
                mock(IBinder.class));

        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // App with a visible activity binds to app2 with both SIMULATE_ALLOW_FREEZE and
        // ALLOW_FREEZE.
        bindService(app3, app, null, null,
                Context.BIND_ALLOW_FREEZE | Context.BIND_SIMULATE_ALLOW_FREEZE,
                mock(IBinder.class));

        setProcessesToLru(app, app2, app3);

        updateOomAdj(app);

        assertThatProcess(app).hasCpuTimeCapability();
        assertThatProcess(app2).hasCpuTimeCapability().withExactReasons(
                CPU_TIME_REASON_TRANSMITTED_LEGACY);
        // ALLOW_FREEZE takes precedence over SIMULATE_ALLOW_FREEZE.
        assertThatProcess(app3).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_bindingWithSimulateAllowFreeze_cycle_branch() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        final WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        // App with a visible activity binds to app2 with SIMULATE_ALLOW_FREEZE
        bindService(app2, app, null, null, Context.BIND_SIMULATE_ALLOW_FREEZE,
                mock(IBinder.class));

        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // App2 binds to App3
        bindService(app3, app2, null, null, 0, mock(IBinder.class));
        // App3 binds to App
        bindService(app, app3, null, null, 0, mock(IBinder.class));

        ProcessRecord app4 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        final WindowProcessController wpc4 = app4.getWindowProcessController();
        setHasActivity(wpc4, true);
        setActivityStateFlags(wpc4, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        // App4 with a visible activity binds to App3
        bindService(app3, app4, null, null, 0, mock(IBinder.class));

        updateOomAdj(app, app2, app3, app4);

        // app has a visible activity, but it is in a cycle and can get other transmitted reasons
        // depending on the traversal path of the algorithm. We don't care about those reasons.
        assertThatProcess(app).hasCpuTimeCapability().withReasons(CPU_TIME_REASON_OTHER);
        // The only incoming binding has simulate_allow_freeze so the only reason present should be
        // transmitted_legacy.
        assertThatProcess(app2).hasCpuTimeCapability().withExactReasons(
                CPU_TIME_REASON_TRANSMITTED_LEGACY);
        // Direct (non-simulated) binding from app4 should definitely grant CPU_TIME with the
        // reason transmitted, but never reason_other because there is no non-transmitted reason for
        // app3 to have cpu_time. It may or may not get reason transmitted_legacy from app2.
        assertThatProcess(app3)
                .hasCpuTimeCapability()
                .withReasons(CPU_TIME_REASON_TRANSMITTED)
                .withoutReasons(CPU_TIME_REASON_OTHER);
        // App4 has an activity and is out of the cycle.
        assertThatProcess(app4).hasCpuTimeCapability().withExactReasons(CPU_TIME_REASON_OTHER);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_bindingWithSimulateAllowFreeze_branch() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        final WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        // App with a visible activity binds to app2 with SIMULATE_ALLOW_FREEZE
        bindService(app2, app, null, null, Context.BIND_SIMULATE_ALLOW_FREEZE,
                mock(IBinder.class));

        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final WindowProcessController wpc3 = app3.getWindowProcessController();
        setHasActivity(wpc3, true);
        setActivityStateFlags(wpc3, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        // App3 also with a visible activity binds to App2
        bindService(app2, app3, null, null, 0, mock(IBinder.class));

        updateOomAdj(app, app2, app3);

        assertThatProcess(app).hasCpuTimeCapability().withExactReasons(CPU_TIME_REASON_OTHER);
        // Two incoming bindings, where one has simulate_allow_freeze and the other doesn't. Both
        // transmitted and transmitted_legacy reasons must be present. No other reason is expected.
        assertThatProcess(app2).hasCpuTimeCapability().withExactReasons(
                CPU_TIME_REASON_TRANSMITTED_LEGACY | CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app3).hasCpuTimeCapability().withExactReasons(CPU_TIME_REASON_OTHER);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_allowFreezeBinding_ongoingBinderCalls() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // App with a visible activity binds to app2 with ALLOW_FREEZE.
        final IBinder mockBinder = mock(IBinder.class);
        final ServiceRecord sr = bindService(app2, app, null, null, Context.BIND_ALLOW_FREEZE,
                mockBinder);

        setProcessesToLru(app, app2);

        updateOomAdj(app);
        assertThatProcess(app).hasCpuTimeCapability();
        assertThatProcess(app2).notHasCpuTimeCapability();

        final ConnectionRecord cr = sr.getConnections().get(mockBinder).get(0);
        mProcessStateController.updateBinderServiceCalls(cr, true);

        updateOomAdj(app);
        assertThatProcess(app2).hasCpuTimeCapability()
                .withExactReasons(CPU_TIME_REASON_TRANSMITTED);

        mProcessStateController.updateBinderServiceCalls(cr, false);
        updateOomAdj(app);
        assertThatProcess(app2).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_simulateAllowFreezeBinding_ongoingBinderCalls() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHasActivity(wpc, true);
        setActivityStateFlags(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // App with a visible activity binds to app2 with SIMULATE_ALLOW_FREEZE.
        final IBinder mockBinder = mock(IBinder.class);
        final ServiceRecord sr = bindService(app2, app, null, null,
                Context.BIND_SIMULATE_ALLOW_FREEZE, mockBinder);

        setProcessesToLru(app, app2);

        updateOomAdj(app);
        assertThatProcess(app).hasCpuTimeCapability();
        assertThatProcess(app2).hasCpuTimeCapability().withExactReasons(
                CPU_TIME_REASON_TRANSMITTED_LEGACY);

        final ConnectionRecord cr = sr.getConnections().get(mockBinder).get(0);
        mProcessStateController.updateBinderServiceCalls(cr, true);

        updateOomAdj(app);
        assertThatProcess(app2).hasCpuTimeCapability()
                .withExactReasons(CPU_TIME_REASON_TRANSMITTED);

        mProcessStateController.updateBinderServiceCalls(cr, false);
        updateOomAdj(app);
        assertThatProcess(app2).hasCpuTimeCapability()
                .withExactReasons(CPU_TIME_REASON_TRANSMITTED_LEGACY);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_bindingFromFgs() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasForegroundServices(app.mServices, true,
                FOREGROUND_SERVICE_TYPE_SPECIAL_USE, false);

        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        // App with a foreground service binds to app2
        bindService(app2, app, null, null, 0, mock(IBinder.class));

        setProcessesToLru(app, app2);
        updateOomAdj(app);

        assertThatProcess(app).hasCpuTimeCapability();
        assertThatProcess(app2).hasCpuTimeCapability()
                .withExactReasons(CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_soloFgs() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasForegroundServices(app.mServices, true,
                FOREGROUND_SERVICE_TYPE_SPECIAL_USE, false);

        setProcessesToLru(app);
        updateOomAdj(app);

        assertThatProcess(app).hasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_receivers() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);

        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();

        mProcessStateController.noteBroadcastDeliveryStarted(app, SCHED_GROUP_BACKGROUND);
        updateOomAdj(app);
        assertThatProcess(app).hasCpuTimeCapability();

        mProcessStateController.noteBroadcastDeliveryEnded(app);
        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_executingServices() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);

        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();

        mProcessStateController.startExecutingService(app.mServices, makeServiceRecord());
        updateOomAdj(app);
        assertThatProcess(app).hasCpuTimeCapability();

        mProcessStateController.stopAllExecutingServices(app.mServices);
        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdjFreezeState_activeInstrumentation() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();

        mProcessStateController.setActiveInstrumentation(app, mock(ActiveInstrumentation.class));
        updateOomAdj(app);
        assertThatProcess(app).hasCpuTimeCapability();

        mProcessStateController.setActiveInstrumentation(app, null);
        updateOomAdj(app);
        assertThatProcess(app).notHasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_OverlayUi() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasOverlayUi(app, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_PerceptibleRecent_FgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasForegroundServices(app.mServices, true, 0, /* hasNoneType=*/
                true);
        app.setLastTopTime(SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT, "fg-service-act");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);

        final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mService.mHandler).sendEmptyMessageAtTime(
                eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG), followUpTimeCaptor.capture());
        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        mProcessStateController.runFollowUpUpdate();

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT, "fg-service");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        // Follow up should not have been called again.
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_PerceptibleRecent_AlmostPerceptibleService() {
        // Grace period allows the adjustment.
        {
            ProcessRecord system = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, true);
            long nowUptime = SystemClock.uptimeMillis();
            app.setLastTopTime(nowUptime);
            // Simulate the system starting and binding to a service in the app.
            ServiceRecord s = bindService(app, system,
                    null, null, Context.BIND_ALMOST_PERCEPTIBLE, mock(IBinder.class));
            mProcessStateController.setLastTopAlmostPerceptibleBindRequest(s, nowUptime);
            s.getConnections().clear();
            mProcessStateController.updateHasTopStartedAlmostPerceptibleServices(app.mServices);
            mService.mWakefulness.set(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(app);

            assertEquals(PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2, app.getSetAdj());

            final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
            verify(mService.mHandler).sendEmptyMessageAtTime(
                    eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG), followUpTimeCaptor.capture());
            mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
            mProcessStateController.runFollowUpUpdate();

            final int expectedAdj = sFirstCachedAdj;
            assertEquals(expectedAdj, app.getSetAdj());
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
            // Follow up should not have been called again.
            verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                    followUpTimeCaptor.capture());

        }

        // Out of grace period but valid binding allows the adjustment.
        {
            ProcessRecord system = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, true);
            long nowUptime = SystemClock.uptimeMillis();
            app.setLastTopTime(nowUptime);
            // Simulate the system starting and binding to a service in the app.
            ServiceRecord s = bindService(app, system,
                    null, null, Context.BIND_ALMOST_PERCEPTIBLE + 2, mock(IBinder.class));
            mProcessStateController.setLastTopAlmostPerceptibleBindRequest(s,
                    nowUptime - 2 * mService.mConstants.mServiceBindAlmostPerceptibleTimeoutMs);
            mProcessStateController.updateHasTopStartedAlmostPerceptibleServices(app.mServices);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(app);

            assertEquals(PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }

        // Out of grace period and no valid binding so no adjustment.
        {
            ProcessRecord system = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, true);
            long nowUptime = SystemClock.uptimeMillis();
            app.setLastTopTime(nowUptime);
            // Simulate the system starting and binding to a service in the app.
            ServiceRecord s = bindService(app, system,
                    null, null, Context.BIND_ALMOST_PERCEPTIBLE, mock(IBinder.class));
            mProcessStateController.setLastTopAlmostPerceptibleBindRequest(s,
                    nowUptime - 2 * mService.mConstants.mServiceBindAlmostPerceptibleTimeoutMs);
            s.getConnections().clear();
            mProcessStateController.updateHasTopStartedAlmostPerceptibleServices(app.mServices);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(app);

            assertNotEquals(PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ImpFg_AlmostPerceptibleService() {
        ProcessRecord system = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(system, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasTopUi(system, true);
        // Simulate the system starting and binding to a service in the app.
        ServiceRecord s = bindService(app, system,
                null, null, Context.BIND_ALMOST_PERCEPTIBLE, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(system, app);

        assertProcStates(app, PROCESS_STATE_IMPORTANT_FOREGROUND,
                PERCEPTIBLE_APP_ADJ + 1, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Toast() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setForcingToImportant(app, new Object());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_HeavyWeight() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHeavyWeightProcess(wpc);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);
        setHeavyWeightProcess(null);

        assertProcStates(app, PROCESS_STATE_HEAVY_WEIGHT, HEAVY_WEIGHT_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_HomeApp() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHomeProcess(wpc);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_HOME, HOME_APP_ADJ, SCHED_GROUP_BACKGROUND);
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app).hasImplicitCpuTimeCapability();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_PreviousApp() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setPreviousProcess(wpc);
        setHasActivity(wpc, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_LAST_ACTIVITY, PREVIOUS_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "previous");
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app).hasImplicitCpuTimeCapability();
        }

        final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());
        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        mProcessStateController.runFollowUpUpdate();

        int expectedAdj = CACHED_APP_MIN_ADJ;
        assertProcStates(app, PROCESS_STATE_LAST_ACTIVITY, expectedAdj,
                SCHED_GROUP_BACKGROUND, "previous-expired");
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        // Follow up should not have been called again.
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoPending_PreviousApp() {
        testUpdateOomAdj_PreviousApp(apps -> {
            for (ProcessRecord app : apps) {
                mProcessStateController.enqueueUpdateTarget(app);
            }
            mProcessStateController.runPendingUpdate(OOM_ADJ_REASON_NONE);
        });
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_PreviousApp() {
        testUpdateOomAdj_PreviousApp(apps -> {
            mProcessStateController.runFullUpdate(OOM_ADJ_REASON_NONE);
        });
    }

    private void testUpdateOomAdj_PreviousApp(Consumer<ProcessRecord[]> updater) {
        final int numberOfApps = 105;
        final ProcessRecord[] apps = new ProcessRecord[numberOfApps];
        // Create an activity that has recently been backgrounded.
        final ProcessRecord previous = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        setPreviousProcess(previous.getWindowProcessController());
        setHasActivity(previous.getWindowProcessController(), true);

        // Bind to many services from that previous activity and populated an LRU list.
        for (int i = 0; i < numberOfApps - 1; i++) {
            apps[i] = makeDefaultProcessRecord(MOCKAPP_PID + i + 1, MOCKAPP_UID + i + 1,
                    MOCKAPP_PROCESSNAME + i, MOCKAPP_PACKAGENAME + i, false);
            bindService(apps[i], previous,
                    null, null, Context.BIND_IMPORTANT, mock(IBinder.class));
        }
        // Set the most recently used spot as the activity.
        apps[numberOfApps - 1] = previous;
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setProcessesToLru(apps);
        updater.accept(apps);
        assertProcStates(previous, PROCESS_STATE_LAST_ACTIVITY, PREVIOUS_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "previous");
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(previous).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(previous).hasImplicitCpuTimeCapability();
        }
        for (int i = 0; i < numberOfApps - 1; i++) {
            final int mruIndex = numberOfApps - i - 1;
            final int expectedAdj = Math.min(PREVIOUS_APP_ADJ + mruIndex, PREVIOUS_APP_MAX_ADJ);
            assertProcStates(apps[i], PROCESS_STATE_LAST_ACTIVITY, expectedAdj,
                    SCHED_GROUP_BACKGROUND, "service");
            if (Flags.prototypeAggressiveFreezing()) {
                assertThatProcess(apps[i]).notHasImplicitCpuTimeCapability();
            } else {
                assertThatProcess(apps[i]).hasImplicitCpuTimeCapability().withExactReasons(
                        IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
            }
        }

        for (int i = 0; i < numberOfApps; i++) {
            final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
            verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                    followUpTimeCaptor.capture());
            mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        }

        mProcessStateController.runFollowUpUpdate();

        for (int i = 0; i < numberOfApps - 1; i++) {
            final int mruIndex = numberOfApps - i - 1;
            int expectedAdj = CACHED_APP_MIN_ADJ + (mruIndex * 2 * CACHED_APP_IMPORTANCE_LEVELS);
            if (expectedAdj > CACHED_APP_MAX_ADJ) {
                expectedAdj = CACHED_APP_MAX_ADJ;
            }
            assertProcStates(apps[i], PROCESS_STATE_LAST_ACTIVITY, expectedAdj,
                    SCHED_GROUP_BACKGROUND, "service");
            assertThatProcess(apps[i]).notHasImplicitCpuTimeCapability();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags({Flags.FLAG_REMOVE_LRU_SPAM_PREVENTION, Flags.FLAG_OOMADJUSTER_VIS_LADDERING})
    public void testUpdateOomAdj_DoPending_VisibleApp() {
        testUpdateOomAdj_VisibleApp(apps -> {
            for (ProcessRecord app : apps) {
                mProcessStateController.enqueueUpdateTarget(app);
            }
            mProcessStateController.runPendingUpdate(OOM_ADJ_REASON_NONE);
        });
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags({Flags.FLAG_REMOVE_LRU_SPAM_PREVENTION, Flags.FLAG_OOMADJUSTER_VIS_LADDERING})
    public void testUpdateOomAdj_DoAll_VisibleApp() {
        testUpdateOomAdj_VisibleApp(apps -> {
            mProcessStateController.runFullUpdate(OOM_ADJ_REASON_NONE);
        });
    }

    private void testUpdateOomAdj_VisibleApp(Consumer<ProcessRecord[]> updater) {
        final int numberOfApps = 105;
        final ProcessRecord[] apps = new ProcessRecord[numberOfApps];
        // Create an activity that has recently been backgrounded.
        final ProcessRecord visible = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, true);
        setTopProcess(visible);
        setHasActivity(visible.getWindowProcessController(), true);

        // Bind to many services from that previous activity and populated an LRU list.
        for (int i = 0; i < numberOfApps - 1; i++) {
            apps[i] = makeDefaultProcessRecord(MOCKAPP_PID + i + 1, MOCKAPP_UID + i + 1,
                    MOCKAPP_PROCESSNAME + i, MOCKAPP_PACKAGENAME + i, false);
            bindService(apps[i], visible,
                    null, null, Context.BIND_AUTO_CREATE, mock(IBinder.class));
        }
        // Set the most recently used spot as the activity.
        apps[numberOfApps - 1] = visible;
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setProcessesToLru(apps);
        updater.accept(apps);
        assertProcStates(visible, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ,
                SCHED_GROUP_TOP_APP, "top-activity");
        assertThatProcess(visible).hasImplicitCpuTimeCapability();
        for (int i = 0; i < numberOfApps - 1; i++) {
            final int mruIndex = numberOfApps - i - 2;
            final int expectedAdj = Math.min(VISIBLE_APP_ADJ + mruIndex, VISIBLE_APP_MAX_ADJ);
            assertProcStates(apps[i], PROCESS_STATE_BOUND_TOP, expectedAdj,
                    SCHED_GROUP_DEFAULT, "service");
            assertThatProcess(apps[i]).hasImplicitCpuTimeCapability().withExactReasons(
                    IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Backup() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setBackupTarget(app);
        if (Flags.pushGlobalStateToOomadjuster() && Flags.autoTriggerOomadjUpdates()) {
            // Do not manually run the update.
        } else {
            updateOomAdj(app);
        }
        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, BACKUP_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        stopBackupTarget(app.userId);
        if (Flags.pushGlobalStateToOomadjuster() && Flags.autoTriggerOomadjUpdates()) {
            // Do not manually run the update.
        } else {
            updateOomAdj(app);
        }
        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj,
                SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ClientActivities() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setHasClientActivities(app.mServices, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertEquals(PROCESS_STATE_CACHED_ACTIVITY_CLIENT, app.getSetProcState());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TreatLikeActivity() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setTreatLikeActivity(app.mServices, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertEquals(PROCESS_STATE_CACHED_ACTIVITY, app.getSetProcState());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ServiceB() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        app.setServiceB(true);
        ServiceRecord s = makeServiceRecord();
        doReturn(new ArrayMap<IBinder, ArrayList<ConnectionRecord>>()).when(s).getConnections();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        mProcessStateController.startService(app.mServices, s);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_B_ADJ, SCHED_GROUP_BACKGROUND);
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app).hasImplicitCpuTimeCapability();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_MaxAdj() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(app, PERCEPTIBLE_LOW_APP_ADJ);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, PERCEPTIBLE_LOW_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_NonCachedToCached() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        app.setCurRawAdj(SERVICE_ADJ);
        app.setCurAdj(SERVICE_ADJ);
        setTopProcess(null);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertTrue(CACHED_APP_MIN_ADJ <= app.getSetAdj());
        assertTrue(CACHED_APP_MAX_ADJ >= app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_CallbackOnOomAdjustChanged() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        final int oldAdj = CACHED_APP_MIN_ADJ;
        app.setCurRawAdj(oldAdj);
        app.setCurAdj(oldAdj);
        app.setSetAdj(oldAdj);

        setTopProcess(app);
        updateOomAdj(app);
        final int newAdj = app.getSetAdj();

        assertTrue(oldAdj != newAdj);
        verify(mCallback).onOomAdjustChanged(oldAdj, newAdj, app);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Started() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ServiceRecord s = makeServiceRecord();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        mProcessStateController.startService(app.mServices, s);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Started_WaivePriority() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ServiceRecord s = bindService(app, client, null, null, Context.BIND_WAIVE_PRIORITY,
                mock(IBinder.class));
        mProcessStateController.setStartRequested(s, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client);
        updateOomAdj(client, app);
        setTopProcess(null);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_SERVICE, expectedAdj, SCHED_GROUP_BACKGROUND);
        // This WPRIO service oom score is in the FREEZER_CUTOFF_ADJ range, but the client is not
        // frozen, so neither should the service.
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Started_WaivePriority_TreatLikeActivity() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setTreatLikeActivity(client.mServices, true);
        bindService(app, client, null, null, Context.BIND_WAIVE_PRIORITY
                | Context.BIND_TREAT_LIKE_ACTIVITY, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_CACHED_ACTIVITY, app.getSetProcState());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Started_WaivePriority_AdjustWithActivity() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        IBinder binder = mock(IBinder.class);
        ServiceRecord s = bindService(app, client, null, null, Context.BIND_WAIVE_PRIORITY
                | Context.BIND_ADJUST_WITH_ACTIVITY | Context.BIND_IMPORTANT, binder);
        ConnectionRecord cr = s.getConnections().get(binder).get(0);
        setFieldValue(ConnectionRecord.class, cr, "activity",
                mock(ActivityServiceConnectionsHolder.class));
        setTopProcess(client);
        doReturn(true).when(cr.activity).isActivityVisible();
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());
        assertEquals(SCHED_GROUP_TOP_APP_BOUND, app.getSetSchedGroup());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Self() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        bindService(app, app, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_CachedActivity() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setTreatLikeActivity(client.mServices, true);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_CACHED_EMPTY, app.getSetProcState());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_AllowOomManagement() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        setHomeProcess(null);
        setPreviousProcess(wpc);
        setHasActivity(wpc, true);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_ALLOW_OOM_MANAGEMENT,
                mock(IBinder.class));
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);
        setTopProcess(null);

        assertEquals(PREVIOUS_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundByPersistentService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_FOREGROUND_SERVICE, mock(IBinder.class));
        mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasTopUi(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Bound_ImportantFg() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_IMPORTANT, mock(IBinder.class));
        mProcessStateController.startExecutingService(client.mServices, makeServiceRecord());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundByTop() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);
        setTopProcess(null);

        assertProcStates(app, PROCESS_STATE_BOUND_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundFgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_FOREGROUND_SERVICE, mock(IBinder.class));
        mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_BOUND_FOREGROUND_SERVICE, app.getSetProcState());
        assertEquals(PROCESS_STATE_PERSISTENT, client.getSetProcState());
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_BoundFgService_Sleeping() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_FOREGROUND_SERVICE, mock(IBinder.class));
        client.setMaxAdj(PERSISTENT_PROC_ADJ);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_ASLEEP);
        updateOomAdj(client, app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_RESTRICTED);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client, PROCESS_STATE_PERSISTENT, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundNotForeground() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_NOT_FOREGROUND, mock(IBinder.class));
        mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_TRANSIENT_BACKGROUND, app.getSetProcState());
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_ImportantFgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client.mServices, true,
                0, /* hasNoneType=*/true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, client.getSetProcState());
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, app.getSetProcState());
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_ImportantFgService_ShortFgs() {
        // Client has a SHORT_SERVICE FGS, which isn't allowed BFSL.
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));

        // In order to trick OomAdjuster to think it has a short-service, we need this logic.
        ServiceRecord s = ServiceRecord.newEmptyInstanceForTest(mService);
        s.appInfo = new ApplicationInfo();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setIsForegroundService(s, true);
        mProcessStateController.setForegroundServiceType(s, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        mProcessStateController.setShortFgsInfo(s, SystemClock.uptimeMillis());
        mProcessStateController.startService(client.mServices, s);
        client.setLastTopTime(SystemClock.uptimeMillis());

        mProcessStateController.setHasForegroundServices(client.mServices, true,
                FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, /* hasNoneType=*/false);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        // Client only has a SHORT_FGS, so it doesn't have BFSL, and that's propagated.
        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, client.getSetProcState());
        assertThatProcess(client).notHasCapability(PROCESS_CAPABILITY_BFSL);
        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, app.getSetProcState());
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundForegroundService_with_ShortFgs() {

        // app2, which is bound by app1 (which makes it BFGS)
        // but it also has a short-fgs.
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);

        // In order to trick OomAdjuster to think it has a short-service, we need this logic.
        ServiceRecord s = ServiceRecord.newEmptyInstanceForTest(mService);
        s.appInfo = new ApplicationInfo();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setIsForegroundService(s, true);
        mProcessStateController.setForegroundServiceType(s, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        mProcessStateController.setShortFgsInfo(s, SystemClock.uptimeMillis());
        mProcessStateController.startService(app2.mServices, s);
        app2.setLastTopTime(SystemClock.uptimeMillis());

        mProcessStateController.setHasForegroundServices(app2.mServices, true,
                FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, /* hasNoneType=*/false);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app2);

        // Client only has a SHORT_FGS, so it doesn't have BFSL, and that's propagated.
        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, app2.getSetProcState());
        assertThatProcess(app2).notHasCapability(PROCESS_CAPABILITY_BFSL);

        // Now, create a BFGS process (app1), and make it bind to app 2

        // Persistent process
        ProcessRecord pers = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(pers, PERSISTENT_PROC_ADJ);

        // app1, which is bound by pers (which makes it BFGS)
        ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        bindService(app1, pers, null, null, Context.BIND_FOREGROUND_SERVICE, mock(IBinder.class));
        bindService(app2, app1, null, null, 0, mock(IBinder.class));

        updateOomAdj(pers, app1, app2);

        assertEquals(PROCESS_STATE_BOUND_FOREGROUND_SERVICE, app1.getSetProcState());
        assertThatProcess(app1).hasCapability(PROCESS_CAPABILITY_BFSL);

        // Now, app2 gets BFSL from app1.
        assertEquals(PROCESS_STATE_FOREGROUND_SERVICE, app2.getSetProcState());
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundByBackup_AboveClient() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_ABOVE_CLIENT, mock(IBinder.class));
        setBackupTarget(client);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        mProcessStateController.stopBackupTarget(UserHandle.getUserId(MOCKAPP2_UID));

        assertEquals(BACKUP_APP_ADJ, app.getSetAdj());
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);

        mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
        updateOomAdj(client, app);

        assertEquals(PERSISTENT_SERVICE_ADJ, app.getSetAdj());
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_NotPerceptible() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_NOT_PERCEPTIBLE, mock(IBinder.class));
        mProcessStateController.setRunningRemoteAnimation(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PERCEPTIBLE_LOW_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_NotPerceptible_AboveClient() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ProcessRecord service = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_NOT_PERCEPTIBLE, mock(IBinder.class));
        bindService(service, app, null, null, Context.BIND_ABOVE_CLIENT, mock(IBinder.class));
        mProcessStateController.setRunningRemoteAnimation(client, true);
        mProcessStateController.updateHasAboveClientLocked(app.mServices);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app, service);

        assertEquals(SERVICE_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_NotVisible() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_NOT_VISIBLE, mock(IBinder.class));
        mProcessStateController.setRunningRemoteAnimation(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PERCEPTIBLE_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Perceptible() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasOverlayUi(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PERCEPTIBLE_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_AlmostPerceptible() {
        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
            ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
            bindService(app, client, null, null,
                    Context.BIND_ALMOST_PERCEPTIBLE | Context.BIND_NOT_FOREGROUND,
                    mock(IBinder.class));
            mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(client, app);

            assertEquals(PERCEPTIBLE_MEDIUM_APP_ADJ + 2, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }

        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
            ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
            WindowProcessController wpc = client.getWindowProcessController();
            setHeavyWeightProcess(wpc);
            bindService(app, client, null, null,
                    Context.BIND_ALMOST_PERCEPTIBLE | Context.BIND_NOT_FOREGROUND,
                    mock(IBinder.class));
            mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(client, app);
            setHeavyWeightProcess(null);

            assertEquals(PERCEPTIBLE_MEDIUM_APP_ADJ + 2, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }

        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
            ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
            bindService(app, client, null, null,
                    Context.BIND_ALMOST_PERCEPTIBLE,
                    mock(IBinder.class));
            mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(client, app);

            assertEquals(PERCEPTIBLE_APP_ADJ + 1, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }

        {
            ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                    MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
            ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                    MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
            WindowProcessController wpc = client.getWindowProcessController();
            setHeavyWeightProcess(wpc);
            bindService(app, client, null, null,
                    Context.BIND_ALMOST_PERCEPTIBLE,
                    mock(IBinder.class));
            mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
            setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
            updateOomAdj(client, app);
            setHeavyWeightProcess(null);

            assertEquals(PERCEPTIBLE_APP_ADJ + 1, app.getSetAdj());

            mProcessStateController.getOomAdjuster().resetInternal();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Other() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        mProcessStateController.setRunningRemoteAnimation(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(VISIBLE_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Bind_ImportantBg() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_IMPORTANT_BACKGROUND,
                mock(IBinder.class));
        mProcessStateController.setHasOverlayUi(client, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertEquals(PROCESS_STATE_IMPORTANT_BACKGROUND, app.getSetProcState());
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_Self() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(app, cpr);
        updateOomAdj(app);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_Cached_Activity() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        mProcessStateController.setTreatLikeActivity(client.mServices, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_TopApp() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);
        setTopProcess(null);

        assertProcStates(app, PROCESS_STATE_BOUND_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_FgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(client.mServices, true, 0, true);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_FgService_ShortFgs() {
        // Client has a SHORT_SERVICE FGS, which isn't allowed BFSL.
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        // In order to trick OomAdjuster to think it has a short-service, we need this logic.
        ServiceRecord s = ServiceRecord.newEmptyInstanceForTest(mService);
        s.appInfo = new ApplicationInfo();
        mProcessStateController.setStartRequested(s, true);
        s.setIsForeground(true);
        s.setForegroundServiceType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        mProcessStateController.setShortFgsInfo(s, SystemClock.uptimeMillis());
        mProcessStateController.startService(client.mServices, s);
        client.setLastTopTime(SystemClock.uptimeMillis());

        mProcessStateController.setHasForegroundServices(client.mServices, true,
                FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        // Client only has a SHORT_FGS, so it doesn't have BFSL, and that's propagated.
        assertProcStates(client, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 1,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(client).notHasCapability(PROCESS_CAPABILITY_BFSL);
        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE,
                PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 1,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_ExternalProcessHandles() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, true);
        bindProvider(client, cpr);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertProcStates(app, PROCESS_STATE_IMPORTANT_FOREGROUND, FOREGROUND_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_Retention() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final String providerName = "aProvider";
        // Go through the motions of binding a provider
        final ContentProviderRecord cpr = createContentProviderRecord(app, providerName, false);
        final ContentProviderConnection conn = bindProvider(client, cpr);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, app);

        assertProcStates(app, PROCESS_STATE_BOUND_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);

        unbindProvider(client, cpr, conn);
        mProcessStateController.removePublishedProvider(app, providerName);
        mProcessStateController.setLastProviderTime(app, SystemClock.uptimeMillis());
        updateOomAdj(client, app);

        assertProcStates(app, PROCESS_STATE_LAST_ACTIVITY, PREVIOUS_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "recent-provider");
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app).hasImplicitCpuTimeCapability();
        }

        final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());

        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        setProcessesToLru(client, app);
        mProcessStateController.runFollowUpUpdate();

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND,
                "cch-empty");
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        // Follow up should not have been called again.
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByTop() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client2);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, app);
        setTopProcess(null);

        assertProcStates(app, PROCESS_STATE_BOUND_TOP, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_BoundByFgService_Branch() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        bindService(client2, app, null, null, 0, mock(IBinder.class));

        // Note: We add processes to LRU but still call updateOomAdjLocked() with a specific
        // processes.
        setProcessesToLru(app, client, client2);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client2).hasCapability(PROCESS_CAPABILITY_BFSL);

        mProcessStateController.setHasForegroundServices(client2.mServices, false, 0, false);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client2);

        assertEquals(PROCESS_STATE_CACHED_EMPTY, client2.getSetProcState());
        assertEquals(PROCESS_STATE_CACHED_EMPTY, client.getSetProcState());
        assertEquals(PROCESS_STATE_CACHED_EMPTY, app.getSetProcState());
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        assertThatProcess(client).notHasImplicitCpuTimeCapability();
        assertThatProcess(client2).notHasImplicitCpuTimeCapability();
        assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client).notHasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client2).notHasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle_2() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        bindService(client, app, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client2, client, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(client2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client2).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle_3() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        bindService(client2, client, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(client2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client2).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle_4() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        bindService(client2, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        bindService(client3, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client4 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        bindService(client3, client4, null, null, 0, mock(IBinder.class));
        bindService(client4, client3, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3, client4);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(client2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client3).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(client4, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client4).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client3).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(client4).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle_Branch() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        bindService(client2, app, null, null, 0, mock(IBinder.class));
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setForcingToImportant(client3, new Object());
        bindService(app, client3, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_Perceptible_Cycle_Branch() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        bindService(client2, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = client2.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setForcingToImportant(client3, new Object());
        bindService(app, client3, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3);

        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_Perceptible_Cycle_2() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        bindService(client2, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = client2.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        ProcessRecord client4 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setForcingToImportant(client4, new Object());
        bindService(app, client4, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3, client4);

        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Cycle_Branch_2() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, 0, mock(IBinder.class));
        bindService(client2, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = client2.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setForcingToImportant(client3, new Object());
        bindService(app, client3, null, null, 0, mock(IBinder.class));
        ProcessRecord client4 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(client4.mServices, true, 0, true);
        bindService(app, client4, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3, client4);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Chain_BoundByFgService_Branch_3() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        WindowProcessController wpc = client.getWindowProcessController();
        setHomeProcess(wpc);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setForcingToImportant(client3, new Object());
        bindService(app, client3, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, client3, app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Provider() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(client, null, false);
        bindProvider(client2, cpr);
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Service_Provider_Cycle() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(client, null, false);
        bindProvider(client2, cpr);
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        bindService(client2, app, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_Chain_BoundByFgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ContentProviderRecord cpr2 = createContentProviderRecord(client, null, false);
        bindProvider(client2, cpr2);
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client, client2, app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_Provider_Chain_BoundByFgService_Cycle() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(client, cpr);
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ContentProviderRecord cpr2 = createContentProviderRecord(client, null, false);
        bindProvider(client2, cpr2);
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);
        final ContentProviderRecord cpr3 = createContentProviderRecord(client2, null, false);
        bindProvider(app, cpr3);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_ScheduleLikeTop() {
        final ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ProcessRecord client1 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        bindService(app1, client1, null, null, Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                mock(IBinder.class));
        bindService(app2, client2, null, null, Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                mock(IBinder.class));
        mProcessStateController.setMaxAdj(client1, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasForegroundServices(client2.mServices, true, 0, true);

        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(client1, client2, app1, app2);

        assertProcStates(app1, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app1).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);

        bindService(app1, client1, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        bindService(app2, client2, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        updateOomAdj(client1, client2, app1, app2);

        assertProcStates(app1, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_TOP_APP);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);

        setWakefulness(PowerManagerInternal.WAKEFULNESS_ASLEEP);
        updateOomAdj(client1, client2, app1, app2);
        assertProcStates(app1, PROCESS_STATE_IMPORTANT_FOREGROUND, VISIBLE_APP_ADJ,
                SCHED_GROUP_TOP_APP);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);

        bindService(client2, app1, null, null, 0, mock(IBinder.class));
        bindService(app1, client2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(client2.mServices, false, 0, false);
        updateOomAdj(app1, client1, client2);
        assertProcStates(app1, PROCESS_STATE_IMPORTANT_FOREGROUND, VISIBLE_APP_ADJ
                        + (Flags.oomadjusterVisLaddering() && Flags.removeLruSpamPrevention()
                        ? 1 : 0), SCHED_GROUP_TOP_APP);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TreatLikeVisFGS() {
        final ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ProcessRecord client1 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client1, PERSISTENT_PROC_ADJ);
        mProcessStateController.setMaxAdj(client2, PERSISTENT_PROC_ADJ);

        final ServiceRecord s1 = bindService(app1, client1, null, null,
                Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE, mock(IBinder.class));
        final ServiceRecord s2 = bindService(app2, client2, null, null,
                Context.BIND_IMPORTANT, mock(IBinder.class));

        updateOomAdj(client1, client2, app1, app2);

        assertProcStates(app1, PROCESS_STATE_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_PERSISTENT, PERSISTENT_SERVICE_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);

        bindService(app2, client1, null, s2, Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE,
                mock(IBinder.class));
        updateOomAdj(app2);
        assertProcStates(app2, PROCESS_STATE_PERSISTENT, PERSISTENT_SERVICE_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);

        s1.getConnections().clear();
        s2.getConnections().clear();
        client1.mServices.removeAllConnections();
        client2.mServices.removeAllConnections();
        mProcessStateController.setMaxAdj(client1, UNKNOWN_ADJ);
        mProcessStateController.setMaxAdj(client2, UNKNOWN_ADJ);
        mProcessStateController.setHasForegroundServices(client1.mServices, true, 0, true);
        mProcessStateController.setHasOverlayUi(client2, true);

        bindService(app1, client1, null, s1, Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE,
                mock(IBinder.class));
        bindService(app2, client2, null, s2, Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE,
                mock(IBinder.class));

        updateOomAdj(client1, client2, app1, app2);

        // VISIBLE_APP_ADJ is the max oom-adj for BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE.
        assertProcStates(app1, PROCESS_STATE_FOREGROUND_SERVICE, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);

        mProcessStateController.setHasOverlayUi(client2, false);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(client2);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        updateOomAdj(client2, app2);
        assertProcStates(app2, PROCESS_STATE_BOUND_TOP, VISIBLE_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_BindNotPerceptibleFGS() {
        final ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord client1 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client1, PERSISTENT_PROC_ADJ);

        mProcessStateController.setHasForegroundServices(app1.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        bindService(app1, client1, null, null, Context.BIND_NOT_PERCEPTIBLE, mock(IBinder.class));

        updateOomAdj(client1, app1);

        assertProcStates(app1, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER | IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertThatProcess(app1).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_BindAlmostPerceptibleFGS() {
        final ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord client1 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client1, PERSISTENT_PROC_ADJ);

        mProcessStateController.setHasForegroundServices(app1.mServices, true, 0, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        bindService(app1, client1, null, null, Context.BIND_ALMOST_PERCEPTIBLE,
                mock(IBinder.class));

        updateOomAdj(client1, app1);

        assertProcStates(app1, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app1).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app1).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_PendingFinishAttach() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        mProcessStateController.setPendingFinishAttach(app, true);
        app.setHasForegroundActivities(false);

        mProcessStateController.setAttachingProcessStatesLSP(app);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_CACHED_EMPTY, FOREGROUND_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_TopApp_PendingFinishAttach() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        mProcessStateController.setPendingFinishAttach(app, true);
        app.setHasForegroundActivities(true);
        setTopProcess(app);

        mProcessStateController.setAttachingProcessStatesLSP(app);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ,
                SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_UidIdle_StopService() {
        final ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ProcessRecord client1 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP3_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        final UidRecord app1UidRecord = new UidRecord(MOCKAPP_UID, mService);
        final UidRecord app2UidRecord = new UidRecord(MOCKAPP2_UID, mService);
        final UidRecord app3UidRecord = new UidRecord(MOCKAPP5_UID, mService);
        final UidRecord clientUidRecord = new UidRecord(MOCKAPP3_UID, mService);
        app1.setUidRecord(app1UidRecord);
        app2.setUidRecord(app2UidRecord);
        app3.setUidRecord(app3UidRecord);
        client1.setUidRecord(clientUidRecord);
        client2.setUidRecord(clientUidRecord);

        mProcessStateController.setHasForegroundServices(client1.mServices, true, 0, true);
        mProcessStateController.setForcingToImportant(client2, new Object());
        setProcessesToLru(app1, app2, app3, client1, client2);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);

        final ComponentName cn1 = ComponentName.unflattenFromString(
                MOCKAPP_PACKAGENAME + "/.TestService");
        final ServiceRecord s1 = bindService(app1, client1, null, null, 0, mock(IBinder.class));
        setFieldValue(ServiceRecord.class, s1, "name", cn1);
        mProcessStateController.setStartRequested(s1, true);

        final ComponentName cn2 = ComponentName.unflattenFromString(
                MOCKAPP2_PACKAGENAME + "/.TestService");
        final ServiceRecord s2 = bindService(app2, client2, null, null, 0, mock(IBinder.class));
        setFieldValue(ServiceRecord.class, s2, "name", cn2);
        mProcessStateController.setStartRequested(s2, true);

        final ComponentName cn3 = ComponentName.unflattenFromString(
                MOCKAPP5_PACKAGENAME + "/.TestService");
        final ServiceRecord s3 = bindService(app3, client1, null, null, 0, mock(IBinder.class));
        setFieldValue(ServiceRecord.class, s3, "name", cn3);
        mProcessStateController.setStartRequested(s3, true);

        final ComponentName cn4 = ComponentName.unflattenFromString(
                MOCKAPP3_PACKAGENAME + "/.TestService");
        final ServiceRecord c2s = makeServiceRecord(client2);
        setFieldValue(ServiceRecord.class, c2s, "name", cn4);
        mProcessStateController.setStartRequested(c2s, true);

        try {
            mActiveUids.put(MOCKAPP_UID, app1UidRecord);
            mActiveUids.put(MOCKAPP2_UID, app2UidRecord);
            mActiveUids.put(MOCKAPP5_UID, app3UidRecord);
            mActiveUids.put(MOCKAPP3_UID, clientUidRecord);

            setServiceMap(s1, MOCKAPP_UID, cn1);
            setServiceMap(s2, MOCKAPP2_UID, cn2);
            setServiceMap(s3, MOCKAPP5_UID, cn3);
            setServiceMap(c2s, MOCKAPP3_UID, cn4);
            app2UidRecord.setIdle(false);
            updateOomAdj();

            assertProcStates(app1, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                    SCHED_GROUP_DEFAULT);
            assertThatProcess(app1)
                    .hasImplicitCpuTimeCapability()
                    .withReasons(IMPLICIT_CPU_TIME_REASON_TRANSMITTED)
                    .withoutReasons(IMPLICIT_CPU_TIME_REASON_TRANSMITTED_LEGACY);
            assertProcStates(app3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                    SCHED_GROUP_DEFAULT);
            assertThatProcess(app3)
                    .hasImplicitCpuTimeCapability()
                    .withReasons(IMPLICIT_CPU_TIME_REASON_TRANSMITTED)
                    .withoutReasons(IMPLICIT_CPU_TIME_REASON_TRANSMITTED_LEGACY);
            assertProcStates(client1, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                    SCHED_GROUP_DEFAULT);
            assertThatProcess(client1).hasImplicitCpuTimeCapability().withExactReasons(
                    IMPLICIT_CPU_TIME_REASON_OTHER);
            assertEquals(PROCESS_STATE_TRANSIENT_BACKGROUND, app2.getSetProcState());
            assertEquals(PROCESS_STATE_TRANSIENT_BACKGROUND, client2.getSetProcState());

            mProcessStateController.setHasForegroundServices(client1.mServices, false, 0, false);
            mProcessStateController.setForcingToImportant(client2, null);
            app1UidRecord.reset();
            app2UidRecord.reset();
            app3UidRecord.reset();
            clientUidRecord.reset();
            app1UidRecord.setIdle(true);
            app2UidRecord.setIdle(true);
            app3UidRecord.setIdle(true);
            clientUidRecord.setIdle(true);
            doReturn(ActivityManager.APP_START_MODE_DELAYED).when(mService)
                    .getAppStartModeLOSP(anyInt(), any(String.class), anyInt(),
                            anyInt(), anyBoolean(), anyBoolean(), anyBoolean());
            doNothing().when(mService.mServices)
                    .scheduleServiceTimeoutLocked(any(ProcessRecord.class));
            updateOomAdj(client1, client2, app1, app2, app3);

            assertEquals(PROCESS_STATE_CACHED_EMPTY, client1.getSetProcState());
            assertEquals(PROCESS_STATE_SERVICE, app1.getSetProcState());
            assertEquals(PROCESS_STATE_SERVICE, client2.getSetProcState());
        } finally {
            doCallRealMethod().when(mService)
                    .getAppStartModeLOSP(anyInt(), any(String.class), anyInt(),
                            anyInt(), anyBoolean(), anyBoolean(), anyBoolean());
            mService.mServices.mServiceMap.clear();
            mActiveUids.clear();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Unbound() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        mProcessStateController.setForcingToImportant(app, new Object());
        mProcessStateController.setHasForegroundServices(app2.mServices, true, 0, /* hasNoneType=*/
                true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, app2);

        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability();
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundFgService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        mProcessStateController.setForcingToImportant(app, new Object());
        mProcessStateController.setHasForegroundServices(app2.mServices, true, 0, /* hasNoneType=*/
                true);
        bindService(app, app2, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, app2);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundFgService_Cycle() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, app2, null, null, 0, mock(IBinder.class));
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app2, app3, null, null, 0, mock(IBinder.class));
        mProcessStateController.setHasForegroundServices(app3.mServices, true, 0, true);
        bindService(app3, app, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, app2, app3);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app3).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED | IMPLICIT_CPU_TIME_REASON_OTHER);
        assertEquals("service", app.getAdjType());
        assertEquals("service", app2.getAdjType());
        assertEquals("fg-service", app3.getAdjType());
        assertEquals(false, app.isCached());
        assertEquals(false, app2.isCached());
        assertEquals(false, app3.isCached());
        assertEquals(false, app.isEmpty());
        assertEquals(false, app2.isEmpty());
        assertEquals(false, app3.isEmpty());
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app3).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundFgService_Cycle_Branch_2() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ServiceRecord s = bindService(app, app2, null, null, 0, mock(IBinder.class));
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app2, app3, null, null, 0, mock(IBinder.class));
        bindService(app3, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = app3.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord app4 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setHasOverlayUi(app4, true);
        bindService(app, app4, null, s, 0, mock(IBinder.class));
        ProcessRecord app5 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(app5.mServices, true, 0, true);
        bindService(app, app5, null, s, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, app2, app3, app4, app5);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        final int app3selfReasons =
                Flags.prototypeAggressiveFreezing() ? 0 : IMPLICIT_CPU_TIME_REASON_OTHER;
        assertThatProcess(app3).hasImplicitCpuTimeCapability().withExactReasons(
                app3selfReasons | IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app4, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app4).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(app5, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app5).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app3).hasCapability(PROCESS_CAPABILITY_BFSL);
        // 4 is IMP_FG
        assertThatProcess(app5).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundFgService_Cycle_Branch_3() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ServiceRecord s = bindService(app, app2, null, null, 0, mock(IBinder.class));
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app2, app3, null, null, 0, mock(IBinder.class));
        bindService(app3, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = app3.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord app4 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setHasOverlayUi(app4, true);
        bindService(app, app4, null, s, 0, mock(IBinder.class));
        ProcessRecord app5 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(app5.mServices, true, 0, true);
        bindService(app, app5, null, s, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app5, app4, app3, app2, app);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        final int app3selfReasons =
                Flags.prototypeAggressiveFreezing() ? 0 : IMPLICIT_CPU_TIME_REASON_OTHER;
        assertThatProcess(app3).hasImplicitCpuTimeCapability().withExactReasons(
                app3selfReasons | CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app4, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app4).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(app5, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app5).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);

        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app3).hasCapability(PROCESS_CAPABILITY_BFSL);
        // 4 is IMP_FG
        assertThatProcess(app5).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundFgService_Cycle_Branch_4() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ServiceRecord s = bindService(app, app2, null, null, 0, mock(IBinder.class));
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(app2, app3, null, null, 0, mock(IBinder.class));
        bindService(app3, app, null, null, 0, mock(IBinder.class));
        WindowProcessController wpc = app3.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord app4 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setHasOverlayUi(app4, true);
        bindService(app, app4, null, s, 0, mock(IBinder.class));
        ProcessRecord app5 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(app5.mServices, true, 0, true);
        bindService(app, app5, null, s, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app3, app4, app2, app, app5);

        assertProcStates(app, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app3, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        final int app3selfReasons =
                Flags.prototypeAggressiveFreezing() ? 0 : IMPLICIT_CPU_TIME_REASON_OTHER;
        assertThatProcess(app3).hasImplicitCpuTimeCapability().withExactReasons(
                app3selfReasons | IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app4, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app4).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(app5, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app5).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app3).hasCapability(PROCESS_CAPABILITY_BFSL);
        // 4 is IMP_FG
        assertThatProcess(app5).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BoundByPersService_Cycle_Branch_Capability() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        bindService(app, client, null, null, Context.BIND_INCLUDE_CAPABILITIES,
                mock(IBinder.class));
        ProcessRecord client2 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        bindService(client, client2, null, null, Context.BIND_INCLUDE_CAPABILITIES,
                mock(IBinder.class));
        bindService(client2, app, null, null, Context.BIND_INCLUDE_CAPABILITIES,
                mock(IBinder.class));
        ProcessRecord client3 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client3, PERSISTENT_PROC_ADJ);
        bindService(app, client3, null, null, Context.BIND_INCLUDE_CAPABILITIES,
                mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client, client2, client3);

        final int expected = PROCESS_CAPABILITY_ALL & ~PROCESS_CAPABILITY_BFSL;
        assertEquals(expected, client.getSetCapability());
        assertEquals(expected, client2.getSetCapability());
        assertEquals(expected, app.getSetCapability());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Provider_Cycle_Branch_2() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ContentProviderRecord cpr = createContentProviderRecord(app, null, false);
        bindProvider(app2, cpr);
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        final ContentProviderRecord cpr2 = createContentProviderRecord(app2, null, false);
        bindProvider(app3, cpr2);
        final ContentProviderRecord cpr3 = createContentProviderRecord(app3, null, false);
        bindProvider(app, cpr3);
        WindowProcessController wpc = app3.getWindowProcessController();
        setHomeProcess(wpc);
        ProcessRecord app4 = makeDefaultProcessRecord(MOCKAPP4_PID, MOCKAPP4_UID,
                MOCKAPP4_PROCESSNAME, MOCKAPP4_PACKAGENAME, false);
        mProcessStateController.setHasOverlayUi(app4, true);
        bindProvider(app4, cpr);
        ProcessRecord app5 = makeDefaultProcessRecord(MOCKAPP5_PID, MOCKAPP5_UID,
                MOCKAPP5_PROCESSNAME, MOCKAPP5_PACKAGENAME, false);
        mProcessStateController.setHasForegroundServices(app5.mServices, true, 0, true);
        bindProvider(app5, cpr);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, app2, app3, app4, app5);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app2, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app2).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app3, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        final int app3selfReasons =
                Flags.prototypeAggressiveFreezing() ? 0 : IMPLICIT_CPU_TIME_REASON_OTHER;
        assertThatProcess(app3).hasImplicitCpuTimeCapability().withExactReasons(
                app3selfReasons | IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        assertProcStates(app4, PROCESS_STATE_IMPORTANT_FOREGROUND, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app4).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertProcStates(app5, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(app5).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_OTHER);
        assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app2).hasCapability(PROCESS_CAPABILITY_BFSL);
        assertThatProcess(app3).hasCapability(PROCESS_CAPABILITY_BFSL);
        // 4 is IMP_FG
        assertThatProcess(app5).hasCapability(PROCESS_CAPABILITY_BFSL);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_ServiceB() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        long now = SystemClock.uptimeMillis();
        ServiceRecord s = bindService(app, app2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);
        s = bindService(app2, app, null, null, 0, mock(IBinder.class));
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);
        ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        s = makeServiceRecord();
        mProcessStateController.setHostProcess(s, app3);
        setFieldValue(ServiceRecord.class, s, "connections",
                new ArrayMap<IBinder, ArrayList<ConnectionRecord>>());
        mProcessStateController.startService(app3.mServices, s);
        doCallRealMethod().when(s).getConnections();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        mService.mOomAdjuster.mNumServiceProcs = 3;
        updateOomAdj(app3, app2, app);

        assertEquals(SERVICE_B_ADJ, app3.getSetAdj());
        assertEquals(SERVICE_ADJ, app2.getSetAdj());
        assertEquals(SERVICE_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Service_KeepWarmingList() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID_OTHER,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final int userOwner = 0;
        final int userOther = 1;

        final int cachedAdj1 = CACHED_APP_MIN_ADJ + CACHED_APP_IMPORTANCE_LEVELS;
        final int cachedAdj2 = cachedAdj1 + CACHED_APP_IMPORTANCE_LEVELS * 2;
        doReturn(userOwner).when(mService.mUserController).getCurrentUserId();

        final ArrayList<ProcessRecord> lru = mService.mProcessList.getLruProcessesLOSP();
        lru.clear();
        lru.add(app2);
        lru.add(app);

        final ComponentName cn = ComponentName.unflattenFromString(
                MOCKAPP_PACKAGENAME + "/.TestService");
        final ComponentName cn2 = ComponentName.unflattenFromString(
                MOCKAPP2_PACKAGENAME + "/.TestService");
        final long now = SystemClock.uptimeMillis();

        mService.mConstants.KEEP_WARMING_SERVICES.clear();
        final ServiceInfo si = mock(ServiceInfo.class);
        si.applicationInfo = mock(ApplicationInfo.class);
        ServiceRecord s = spy(new ServiceRecord(mService, cn, cn, null, 0, null,
                si, false, null));
        doReturn(new ArrayMap<IBinder, ArrayList<ConnectionRecord>>()).when(s).getConnections();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);

        mProcessStateController.startService(app.mServices, s);
        mProcessStateController.setHasShownUi(app, true);

        final ServiceInfo si2 = mock(ServiceInfo.class);
        si2.applicationInfo = mock(ApplicationInfo.class);
        si2.applicationInfo.uid = MOCKAPP2_UID_OTHER;
        ServiceRecord s2 = spy(new ServiceRecord(mService, cn2, cn2, null, 0, null,
                si2, false, null));
        doReturn(new ArrayMap<IBinder, ArrayList<ConnectionRecord>>()).when(s2).getConnections();
        mProcessStateController.setStartRequested(s2, true);
        mProcessStateController.setServiceLastActivityTime(s2,
                now - mService.mConstants.MAX_SERVICE_INACTIVITY - 1);

        mProcessStateController.startService(app2.mServices, s2);
        mProcessStateController.setHasShownUi(app2, false);

        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj();

        assertProcStates(app, PROCESS_STATE_SERVICE, cachedAdj1,
                SCHED_GROUP_BACKGROUND, "cch-started-ui-services", true);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        assertProcStates(app2, PROCESS_STATE_SERVICE, cachedAdj2,
                SCHED_GROUP_BACKGROUND, "cch-started-services", true);
        assertThatProcess(app2).notHasImplicitCpuTimeCapability();

        app.setSetProcState(PROCESS_STATE_NONEXISTENT);
        app.setAdjType(null);
        app.setSetAdj(UNKNOWN_ADJ);
        mProcessStateController.setHasShownUi(app, false);
        updateOomAdj();

        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "started-services", false);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        app.setSetProcState(PROCESS_STATE_NONEXISTENT);
        app.setAdjType(null);
        app.setSetAdj(UNKNOWN_ADJ);
        mProcessStateController.setServiceLastActivityTime(s,
                now - mService.mConstants.MAX_SERVICE_INACTIVITY - 1);
        updateOomAdj();

        assertProcStates(app, PROCESS_STATE_SERVICE, cachedAdj1,
                SCHED_GROUP_BACKGROUND, "cch-started-services", true);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();

        mProcessStateController.stopService(app.mServices, s);
        app.setSetProcState(PROCESS_STATE_NONEXISTENT);
        app.setAdjType(null);
        app.setSetAdj(UNKNOWN_ADJ);
        mProcessStateController.setHasShownUi(app, true);
        mService.mConstants.KEEP_WARMING_SERVICES.add(cn);
        mService.mConstants.KEEP_WARMING_SERVICES.add(cn2);
        s = spy(new ServiceRecord(mService, cn, cn, null, 0, null,
                si, false, null));
        doReturn(new ArrayMap<IBinder, ArrayList<ConnectionRecord>>()).when(s).getConnections();
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);

        mProcessStateController.startService(app.mServices, s);
        updateOomAdj();

        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "started-services", false);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertProcStates(app2, PROCESS_STATE_SERVICE, cachedAdj1,
                SCHED_GROUP_BACKGROUND, "cch-started-services", true);
        assertThatProcess(app2).notHasImplicitCpuTimeCapability();

        app.setSetProcState(PROCESS_STATE_NONEXISTENT);
        app.setAdjType(null);
        app.setSetAdj(UNKNOWN_ADJ);
        mProcessStateController.setHasShownUi(app, false);
        mProcessStateController.setServiceLastActivityTime(s,
                now - mService.mConstants.MAX_SERVICE_INACTIVITY - 1);
        updateOomAdj();

        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "started-services", false);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertProcStates(app2, PROCESS_STATE_SERVICE, cachedAdj1,
                SCHED_GROUP_BACKGROUND, "cch-started-services", true);
        assertThatProcess(app2).notHasImplicitCpuTimeCapability();

        doReturn(userOther).when(mService.mUserController).getCurrentUserId();
        mService.mOomAdjuster.prewarmServicesIfNecessary();

        updateOomAdj();
        assertProcStates(app, PROCESS_STATE_SERVICE, cachedAdj1,
                SCHED_GROUP_BACKGROUND, "cch-started-services", true);
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        assertProcStates(app2, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "started-services", false);
        assertThatProcess(app2).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_AboveClient() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        ProcessRecord service = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());

        // Simulate binding to a service in the same process using BIND_ABOVE_CLIENT and
        // verify that its OOM adjustment level is unaffected.
        bindService(service, app, null, null, Context.BIND_ABOVE_CLIENT, mock(IBinder.class));
        mProcessStateController.updateHasAboveClientLocked(app.mServices);
        assertTrue(app.mServices.isHasAboveClient());

        updateOomAdj(app);
        assertEquals(VISIBLE_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_AboveClient_SameProcess() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());

        // Simulate binding to a service in the same process using BIND_ABOVE_CLIENT and
        // verify that its OOM adjustment level is unaffected.
        bindService(app, app, null, null, Context.BIND_ABOVE_CLIENT, mock(IBinder.class));
        mProcessStateController.updateHasAboveClientLocked(app.mServices);
        assertFalse(app.mServices.isHasAboveClient());

        updateOomAdj(app);
        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Side_Cycle() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        long now = SystemClock.uptimeMillis();
        ServiceRecord s = bindService(app, app2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, now);
        s = bindService(app2, app3, null, null, 0, mock(IBinder.class));
        mProcessStateController.setServiceLastActivityTime(s, now);
        s = bindService(app3, app2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setServiceLastActivityTime(s, now);

        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        mService.mOomAdjuster.mNumServiceProcs = 3;
        updateOomAdj(app, app2, app3);

        assertEquals(SERVICE_ADJ, app.getSetAdj());
        assertTrue(sFirstCachedAdj <= app2.getSetAdj());
        assertTrue(sFirstCachedAdj <= app3.getSetAdj());
        assertTrue(CACHED_APP_MAX_ADJ >= app2.getSetAdj());
        assertTrue(CACHED_APP_MAX_ADJ >= app3.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoOne_AboveClient_NotStarted() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app);

        assertEquals(FOREGROUND_APP_ADJ, app.getSetAdj());

        // Start binding to a service that isn't running yet.
        ServiceRecord sr = makeServiceRecord(app);
        mProcessStateController.setHostProcess(sr, null);
        bindService(null, app, null, sr, Context.BIND_ABOVE_CLIENT, mock(IBinder.class));

        // Since sr.app is null, this service cannot be in the same process as the
        // client so we expect the BIND_ABOVE_CLIENT adjustment to take effect.
        mProcessStateController.updateHasAboveClientLocked(app.mServices);
        updateOomAdj(app);
        assertTrue(app.mServices.isHasAboveClient());
        assertNotEquals(FOREGROUND_APP_ADJ, app.getSetAdj());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Isolated_stopService() {
        ProcessRecord app = makeSpiedProcessRecord(MOCKAPP_PID, MOCKAPP_ISOLATED_UID,
                MOCKAPP_ISOLATED_PROCESSNAME, MOCKAPP_PACKAGENAME, false);

        setProcessesToLru(app);
        ServiceRecord s = makeServiceRecord(app);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj();
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        mProcessStateController.stopService(app.mServices, s);
        updateOomAdj();
        // isolated process should be killed immediately after service stop.
        verify(app).killLocked("isolated not needed", ApplicationExitInfo.REASON_OTHER,
                ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED, true);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoPending_Isolated_stopService() {
        ProcessRecord app = makeSpiedProcessRecord(MOCKAPP_PID, MOCKAPP_ISOLATED_UID,
                MOCKAPP_ISOLATED_PROCESSNAME, MOCKAPP_PACKAGENAME, false);

        ServiceRecord s = makeServiceRecord(app);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdjPending(app);
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        mProcessStateController.stopService(app.mServices, s);
        updateOomAdjPending(app);
        // isolated process should be killed immediately after service stop.
        verify(app).killLocked("isolated not needed", ApplicationExitInfo.REASON_OTHER,
                ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED, true);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Isolated_stopServiceWithEntryPoint() {
        ProcessRecord app = makeSpiedProcessRecord(MOCKAPP_PID, MOCKAPP_ISOLATED_UID,
                MOCKAPP_ISOLATED_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        app.setIsolatedEntryPoint("test");

        setProcessesToLru(app);
        ServiceRecord s = makeServiceRecord(app);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj();
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        mProcessStateController.stopService(app.mServices, s);
        updateOomAdj();
        // isolated process with entry point should not be killed
        verify(app, never()).killLocked("isolated not needed", ApplicationExitInfo.REASON_OTHER,
                ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED, true);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoPending_Isolated_stopServiceWithEntryPoint() {
        ProcessRecord app = makeSpiedProcessRecord(MOCKAPP_PID, MOCKAPP_ISOLATED_UID,
                MOCKAPP_ISOLATED_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        app.setIsolatedEntryPoint("test");

        ServiceRecord s = makeServiceRecord(app);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdjPending(app);
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        mProcessStateController.stopService(app.mServices, s);
        updateOomAdjPending(app);
        // isolated process with entry point should not be killed
        verify(app, never()).killLocked("isolated not needed", ApplicationExitInfo.REASON_OTHER,
                ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED, true);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_SdkSandbox_attributedClient() {
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ProcessRecord attributedClient = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, true);
        ProcessRecord sandboxService = spy(new ProcessRecordBuilder(MOCKAPP_PID,
                MOCKAPP_SDK_SANDBOX_UID, MOCKAPP_SDK_SANDBOX_PROCESSNAME, MOCKAPP_PACKAGENAME)
                .setSdkSandboxClientAppPackage(MOCKAPP3_PACKAGENAME)
                .build());

        setProcessesToLru(sandboxService, client, attributedClient);

        mProcessStateController.setMaxAdj(client, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasForegroundServices(attributedClient.mServices, true, 0, true);
        bindService(sandboxService, client, attributedClient, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj();
        assertProcStates(client, PROCESS_STATE_PERSISTENT, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(client).hasImplicitCpuTimeCapability();
        assertProcStates(attributedClient, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(attributedClient).hasImplicitCpuTimeCapability();
        assertProcStates(sandboxService, PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT);
        assertThatProcess(sandboxService).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testSetUidTempAllowlistState() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        setProcessesToLru(app, app2);

        // App1 binds to app2 and gets temp allowlisted.
        bindService(app2, app, null, null, 0, mock(IBinder.class));
        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP_UID, true);

        assertEquals(true, app.getUidRecord().isSetAllowListed());
        assertFreezeState(app, false);
        assertFreezeState(app2, false);
        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
            assertThatProcess(app).hasCpuTimeCapability();
            assertThatProcess(app2).hasCpuTimeCapability().withExactReasons(
                    CPU_TIME_REASON_TRANSMITTED);
        }

        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP_UID, false);
        assertEquals(false, app.getUidRecord().isSetAllowListed());
        assertFreezeState(app, true);
        assertFreezeState(app2, true);
        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
            assertThatProcess(app).notHasCpuTimeCapability();
            assertThatProcess(app2).notHasCpuTimeCapability();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testSetUidTempAllowlistState_multipleAllowlistClients() {
        final ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID,
                MOCKAPP_PROCESSNAME, MOCKAPP_PACKAGENAME, false);
        final ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        final ProcessRecord app3 = makeDefaultProcessRecord(MOCKAPP3_PID, MOCKAPP3_UID,
                MOCKAPP3_PROCESSNAME, MOCKAPP3_PACKAGENAME, false);
        setProcessesToLru(app, app2, app3);

        // App1 and app2 both bind to app3 and get temp allowlisted.
        bindService(app3, app, null, null, 0, mock(IBinder.class));
        bindService(app3, app2, null, null, 0, mock(IBinder.class));
        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP_UID, true);
        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP2_UID, true);

        assertEquals(true, app.getUidRecord().isSetAllowListed());
        assertEquals(true, app2.getUidRecord().isSetAllowListed());
        assertFreezeState(app, false);
        assertFreezeState(app2, false);
        assertFreezeState(app3, false);
        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
            assertThatProcess(app).hasCpuTimeCapability();
            assertThatProcess(app2).hasCpuTimeCapability();
            assertThatProcess(app3).hasCpuTimeCapability().withExactReasons(
                    CPU_TIME_REASON_TRANSMITTED);
        }

        // Remove app1 from allowlist.
        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP_UID, false);
        assertEquals(false, app.getUidRecord().isSetAllowListed());
        assertEquals(true, app2.getUidRecord().isSetAllowListed());
        assertFreezeState(app, true);
        assertFreezeState(app2, false);
        assertFreezeState(app3, false);
        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
            assertThatProcess(app).notHasCpuTimeCapability();
            assertThatProcess(app2).hasCpuTimeCapability();
            assertThatProcess(app3).hasCpuTimeCapability().withExactReasons(
                    CPU_TIME_REASON_TRANSMITTED);
        }

        // Now remove app2 from allowlist.
        mProcessStateController.setUidTempAllowlistStateLSP(MOCKAPP2_UID, false);
        assertEquals(false, app.getUidRecord().isSetAllowListed());
        assertEquals(false, app2.getUidRecord().isSetAllowListed());
        assertFreezeState(app, true);
        assertFreezeState(app2, true);
        assertFreezeState(app3, true);
        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
            assertThatProcess(app).notHasCpuTimeCapability();
            assertThatProcess(app2).notHasCpuTimeCapability();
            assertThatProcess(app3).notHasCpuTimeCapability();
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_ClientlessService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);

        setProcessesToLru(app);
        ServiceRecord s = makeServiceRecord(app);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj();
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "started-services");
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mService.mHandler).sendEmptyMessageAtTime(
                eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG), followUpTimeCaptor.capture());
        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        mProcessStateController.runFollowUpUpdate();

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_SERVICE, expectedAdj, SCHED_GROUP_BACKGROUND,
                "cch-started-services");
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
        // Follow up should not have been called again.
        verify(mService.mHandler).sendEmptyMessageAtTime(eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG),
                followUpTimeCaptor.capture());
    }

    /**
     * For Perceptible Tasks adjustment, this solely unit-tests OomAdjuster -> onOtherActivity()
     */
    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = {"b/429993976"})
    public void testPerceptibleAdjustment() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);

        long now = mInjector.getUptimeMillis();

        // GIVEN: perceptible adjustment is NOT enabled (perceptible stop time is not set)
        // EXPECT: zero adjustment
        // TLDR: App is not set as a perceptible task and hence no oom_adj boosting.
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.initialize(app, CACHED_APP_MIN_ADJ,
                false, false, PROCESS_STATE_CACHED_ACTIVITY,
                SCHED_GROUP_DEFAULT, PROCESS_STATE_IMPORTANT_FOREGROUND, true);
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.onOtherActivity(-1);
        assertEquals(CACHED_APP_MIN_ADJ, mService.mOomAdjuster.mTmpOomAdjWindowCalculator.getAdj());

        // GIVEN: perceptible adjustment is enabled (perceptible stop time is set) and
        //        elapsed time < PERCEPTIBLE_TASK_TIMEOUT
        // EXPECT: adjustment to PERCEPTIBLE_MEDIUM_APP_ADJ
        // TLDR: App is a perceptible task (e.g. opened from launcher) and has oom_adj boosting.
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.initialize(app, CACHED_APP_MIN_ADJ,
                false, false, PROCESS_STATE_CACHED_ACTIVITY,
                SCHED_GROUP_DEFAULT, PROCESS_STATE_IMPORTANT_FOREGROUND, true);
        mInjector.reset();
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.onOtherActivity(now);
        assertEquals(PERCEPTIBLE_MEDIUM_APP_ADJ,
                mService.mOomAdjuster.mTmpOomAdjWindowCalculator.getAdj());

        // GIVEN: perceptible adjustment is enabled (perceptible stop time is set) and
        //        elapsed time >  PERCEPTIBLE_TASK_TIMEOUT
        // EXPECT: adjustment to PREVIOUS_APP_ADJ
        // TLDR: App is a perceptible task (e.g. opened from launcher) and has oom_adj boosting, but
        //       time has elapsed and has dropped to a lower boosting of PREVIOUS_APP_ADJ
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.initialize(app, CACHED_APP_MIN_ADJ,
                false, false, PROCESS_STATE_CACHED_ACTIVITY,
                SCHED_GROUP_DEFAULT, PROCESS_STATE_IMPORTANT_FOREGROUND, true);
        mInjector.jumpUptimeAheadTo(OomAdjuster.PERCEPTIBLE_TASK_TIMEOUT_MILLIS + 1000);
        mService.mOomAdjuster.mTmpOomAdjWindowCalculator.onOtherActivity(0);
        assertEquals(PREVIOUS_APP_ADJ, mService.mOomAdjuster.mTmpOomAdjWindowCalculator.getAdj());
    }

    /**
     * For Perceptible Tasks adjustment, this tests overall adjustment flow.
     */
    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PERCEPTIBLE_TASKS)
    @DesktopTest(cujs = {"b/429993976"})
    public void testUpdateOomAdjPerceptible() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();

        // Set uptime to be at least the timeout time + buffer, so that we don't end up with
        // negative stopTime in our test input
        mInjector.jumpUptimeAheadTo(OomAdjuster.PERCEPTIBLE_TASK_TIMEOUT_MILLIS + 60L * 1000L);
        long now = mInjector.getUptimeMillis();
        setHasActivity(wpc, true);

        // GIVEN: perceptible adjustment is is enabled
        // EXPECT: perceptible-act adjustment
        setActivityState(wpc, ACTIVITY_STATE_FLAG_IS_STOPPING_FINISHING, now);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_IMPORTANT_BACKGROUND, PERCEPTIBLE_MEDIUM_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "perceptible-act");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).notHasCpuTimeCapability();

        // GIVEN: perceptible adjustment is is enabled and timeout has been reached
        // EXPECT: stale-perceptible-act adjustment
        setActivityState(wpc, ACTIVITY_STATE_FLAG_IS_STOPPING_FINISHING,
                now - OomAdjuster.PERCEPTIBLE_TASK_TIMEOUT_MILLIS);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_LAST_ACTIVITY, PREVIOUS_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "stale-perceptible-act");
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app).hasImplicitCpuTimeCapability();
        }
        assertThatProcess(app).notHasCpuTimeCapability();

        // GIVEN: perceptible adjustment is is disabled
        // EXPECT: no perceptible adjustment
        setActivityState(wpc, ACTIVITY_STATE_FLAG_IS_STOPPING_FINISHING, Long.MIN_VALUE);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_CACHED_ACTIVITY, CACHED_APP_MIN_ADJ,
                SCHED_GROUP_BACKGROUND, "cch-act");
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();

        // GIVEN: perceptible app is in foreground
        // EXPECT: no perceptible adjustment
        setActivityState(wpc, ACTIVITY_STATE_FLAG_IS_VISIBLE, now);
        updateOomAdj(app);
        assertProcStates(app, PROCESS_STATE_TOP, VISIBLE_APP_ADJ,
                SCHED_GROUP_DEFAULT, "vis-activity");
        assertThatProcess(app).hasImplicitCpuTimeCapability();
        assertThatProcess(app).hasCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_Multiple_Provider_Retention() {
        ProcessRecord app1 = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);
        ProcessRecord app2 = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        app1.mProviders.setLastProviderTime(SystemClock.uptimeMillis());
        app2.mProviders.setLastProviderTime(SystemClock.uptimeMillis() + 2000);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setProcessesToLru(app1, app2);
        mProcessStateController.runFullUpdate(OOM_ADJ_REASON_NONE);

        assertProcStates(app1, PROCESS_STATE_LAST_ACTIVITY,
                PREVIOUS_APP_ADJ + (Flags.oomadjusterPrevLaddering() ? 1 : 0),
                SCHED_GROUP_BACKGROUND, "recent-provider");
        assertProcStates(app2, PROCESS_STATE_LAST_ACTIVITY, PREVIOUS_APP_ADJ,
                SCHED_GROUP_BACKGROUND, "recent-provider");
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(app1).notHasImplicitCpuTimeCapability();
            assertThatProcess(app2).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(app1).hasImplicitCpuTimeCapability();
            assertThatProcess(app2).hasImplicitCpuTimeCapability();
        }

        final ArgumentCaptor<Long> followUpTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mService.mHandler, atLeastOnce()).sendEmptyMessageAtTime(
                eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG), followUpTimeCaptor.capture());
        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        mProcessStateController.runFollowUpUpdate();

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app1, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND,
                "cch-empty");
        assertThatProcess(app1).notHasImplicitCpuTimeCapability();

        verify(mService.mHandler, atLeastOnce()).sendEmptyMessageAtTime(
                eq(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG), followUpTimeCaptor.capture());
        mInjector.jumpUptimeAheadTo(followUpTimeCaptor.getValue());
        mProcessStateController.runFollowUpUpdate();
        assertProcStates(app2, PROCESS_STATE_CACHED_EMPTY, expectedAdj, SCHED_GROUP_BACKGROUND,
                "cch-empty");
        assertThatProcess(app2).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_FIX_APPLY_OOMADJ_ORDER)
    public void testUpdateOomAdj_ApplyOomAdjInCorrectOrder() {
        final int numberOfApps = 5;
        final ProcessRecord[] apps = new ProcessRecord[numberOfApps];
        for (int i = 0; i < numberOfApps; i++) {
            apps[i] = makeDefaultProcessRecord(MOCKAPP_PID + i, MOCKAPP_UID + i,
                    MOCKAPP_PROCESSNAME + i, MOCKAPP_PACKAGENAME + i, true);
        }
        updateOomAdj(apps);
        for (int i = 1; i < numberOfApps; i++) {
            final int pre = mInjector.mSetOomAdjAppliedAt.get(apps[i - 1].mPid);
            final int cur = mInjector.mSetOomAdjAppliedAt.get(apps[i].mPid);
            assertTrue("setOomAdj is called in wrong order", pre < cur);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_DoAll_BindUiServiceFromClientService() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        ServiceRecord s = makeServiceRecord(client);
        mProcessStateController.setStartRequested(s, true);
        mProcessStateController.setServiceLastActivityTime(s, SystemClock.uptimeMillis());
        bindService(app, client, null, null, 0, mock(IBinder.class));

        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client);
        assertProcStates(app, PROCESS_STATE_SERVICE, SERVICE_ADJ, SCHED_GROUP_BACKGROUND,
                "service");
        assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_CPU_TIME_CAPABILITY_BASED_FREEZE_POLICY)
    public void testUpdateOomAdj_DoAll_BindUiServiceFromClientHome() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);

        WindowProcessController wpc = client.getWindowProcessController();
        setHomeProcess(wpc);
        bindService(app, client, null, null, 0, mock(IBinder.class));
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        updateOomAdj(app, client);

        final int expectedAdj = sFirstCachedAdj;
        assertProcStates(app, PROCESS_STATE_HOME, expectedAdj, SCHED_GROUP_BACKGROUND,
                "cch-bound-ui-services");
        // CPU_TIME is not granted to the client and so cannot be propagated to the service.
        assertThatProcess(client).notHasCpuTimeCapability();
        assertThatProcess(app).notHasCpuTimeCapability();
        // Granting of IMPLICIT_CPU_TIME will depend on the freezer oomAdj cutoff and will be
        // propagated to the service from the client when available.
        if (Flags.prototypeAggressiveFreezing()) {
            assertThatProcess(client).notHasImplicitCpuTimeCapability();
            assertThatProcess(app).notHasImplicitCpuTimeCapability();
        } else {
            assertThatProcess(client).hasImplicitCpuTimeCapability();
            assertThatProcess(app).hasImplicitCpuTimeCapability().withExactReasons(
                    IMPLICIT_CPU_TIME_REASON_TRANSMITTED);
        }
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER)
    public void testUpdateOomAdj_DoOne_TopApp_Unlocking() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setDeviceUnlocking(true);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        setDeviceUnlocking(false);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER)
    public void testUpdateOomAdj_DoOne_TopApp_ExpandedNotificationShade() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setExpandedNotificationShade(true);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        setExpandedNotificationShade(false);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }
    //setVisibleDozeUiProcess

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER)
    public void testUpdateOomAdj_DoOne_PersistentTopApp_VisibleDozeUi() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        setTopProcessState(PROCESS_STATE_TOP);
        setTopProcess(app);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_AWAKE);
        setExpandedNotificationShade(true);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_DEFAULT);
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        setExpandedNotificationShade(false);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_TOP, FOREGROUND_APP_ADJ, SCHED_GROUP_TOP_APP);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }


    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PUSH_ACTIVITY_STATE_TO_OOMADJUSTER)
    public void testUpdateOomAdj_DoOne_Persistent_TopUi_VisibleDozeUi() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        WindowProcessController wpc = app.getWindowProcessController();
        mProcessStateController.setMaxAdj(app, PERSISTENT_PROC_ADJ);
        mProcessStateController.setHasTopUi(app, true);
        setWakefulness(PowerManagerInternal.WAKEFULNESS_DOZING);
        setVisibleDozeUiProcess(wpc);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_PERSISTENT, PERSISTENT_PROC_ADJ, SCHED_GROUP_DEFAULT);
        // Oom adj score below the freeze cutoff should always have the IMPLICIT_CPU_TIME.
        assertThatProcess(app).hasImplicitCpuTimeCapability();

        setVisibleDozeUiProcess(null);
        updateOomAdj(app);

        assertProcStates(app, PROCESS_STATE_BOUND_FOREGROUND_SERVICE, PERSISTENT_PROC_ADJ,
                SCHED_GROUP_RESTRICTED);
        assertThatProcess(app).hasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_bindScheduleLikeTopApp_systemClient_hostGetsTopSchedGroup() {
        // When system client binds a service with BIND_SCHEDULE_LIKE_TOP_APP, the service should
        // will be prioritized as top app.
        ProcessRecord host = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        host.setCurrentSchedulingGroup(SCHED_GROUP_DEFAULT);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client, SYSTEM_ADJ);

        bindService(host, client, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        updateOomAdj(client);

        assertTrue(host.getScheduleLikeTopApp());
        assertEquals(SCHED_GROUP_TOP_APP, host.getCurrentSchedulingGroup());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_bindScheduleLikeTopApp_nonSystemClient_hostNotGetTopSchedGroup() {
        ProcessRecord host = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client, PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ);

        bindService(host, client, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        updateOomAdj(client);

        assertFalse(host.getScheduleLikeTopApp());
        assertNotEquals(SCHED_GROUP_TOP_APP, host.getCurrentSchedulingGroup());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @DisableFlags(Flags.FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP)
    public void testUpdateOomAdj_bindScheduleLikeTopApp_systemClient_hostPrivileged_skipConnectionCompute_hostNotGetTopSchedGroup() {
        // Similar to testUpdateOomAdj_bindScheduleLikeTopApp_systemClient_hostGetsTopSchedGroup,
        // but now the host process is already marked as privileged(see
        // OomAdjusterImpl#isHighPriorityProcess for detail). In this case, connection evaluation
        // will be skipped, as a result, the scheduling group stays default.
        ProcessRecord host = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(host, PERSISTENT_SERVICE_ADJ);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client, SYSTEM_ADJ);

        bindService(host, client, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        updateOomAdj(client);

        // The update for host by its client connection evaluation is skipped.
        assertFalse(host.getScheduleLikeTopApp());
        assertNotEquals(SCHED_GROUP_TOP_APP, host.getSetSchedGroup());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_NOT_SKIP_CONNECTION_RECOMPUTE_FOR_BIND_SCHEDULE_LIKE_TOP_APP)
    public void testUpdateOomAdj_bindScheduleLikeTopApp_systemClient_hostPrivileged_notSkipConnectionCompute_hostGetsTopSchedGroup() {
        // Similar to its counter-part "withoutFlag" but when the feature flag
        // "not_skip_connection_recompute_for_bind_schedule_like_top_app" is enabled, the evaluation
        // of connection with BIND_SCHEDULE_LIKE_TOP_APP will not be skipped if the corresponding
        // flag has not yet been set.
        ProcessRecord host = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);
        mProcessStateController.setMaxAdj(host, PERSISTENT_SERVICE_ADJ);
        ProcessRecord client = makeDefaultProcessRecord(MOCKAPP2_PID, MOCKAPP2_UID,
                MOCKAPP2_PROCESSNAME, MOCKAPP2_PACKAGENAME, false);
        mProcessStateController.setMaxAdj(client, SYSTEM_ADJ);

        bindService(host, client, null, null, Context.BIND_SCHEDULE_LIKE_TOP_APP,
                mock(IBinder.class));
        updateOomAdj(client);

        assertTrue(host.getScheduleLikeTopApp());
        assertEquals(SCHED_GROUP_TOP_APP, host.getCurrentSchedulingGroup());
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_repeatedFreeze() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);

        // trigger an update that will freeze app.
        updateOomAdj(app);

        assertFreezeState(app, true);
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();

        // trigger again
        updateOomAdj(app);

        assertFreezeState(app, true);
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PSC_BATCH_UPDATE)
    public void testBatchSession() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);

        try (var unused = mProcessStateController.startBatchSession(OOM_ADJ_REASON_BACKUP)) {
            setBackupTarget(app);
            updateOomAdj(app);
            // While in the BatchSession the update should not have run.
            assertProcStates(app, PROCESS_STATE_NONEXISTENT, INVALID_ADJ, SCHED_GROUP_BACKGROUND);
        }
        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, BACKUP_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    @EnableFlags(Flags.FLAG_PSC_BATCH_UPDATE)
    public void testBatchSession_nested() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, true);

        try (var unused = mProcessStateController.startBatchSession(OOM_ADJ_REASON_BACKUP)) {
            try (var unused2 = mProcessStateController.startBatchSession(OOM_ADJ_REASON_NONE)) {
                setBackupTarget(app);
                updateOomAdj(app);
            }
            // While in the BatchSession the update should not have run.
            assertProcStates(app, PROCESS_STATE_NONEXISTENT, INVALID_ADJ,
                    SCHED_GROUP_BACKGROUND);
        }
        assertProcStates(app, PROCESS_STATE_TRANSIENT_BACKGROUND, BACKUP_APP_ADJ,
                SCHED_GROUP_BACKGROUND);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testUpdateOomAdj_repeatedFreeze_notPendingFreeze() {
        ProcessRecord app = makeDefaultProcessRecord(MOCKAPP_PID, MOCKAPP_UID, MOCKAPP_PROCESSNAME,
                MOCKAPP_PACKAGENAME, false);

        // trigger an update that will freeze app.
        updateOomAdj(app);

        assertFreezeState(app, true);
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();

        // Move app from pending freeze to frozen
        app.mOptRecord.setPendingFreeze(false);
        app.mOptRecord.setFrozen(true);

        // trigger again
        updateOomAdj(app);

        assertFreezeState(app, true);
        assertThatProcess(app).notHasCpuTimeCapability();
        assertThatProcess(app).notHasImplicitCpuTimeCapability();
    }

    private ProcessRecord makeDefaultProcessRecord(int pid, int uid, String processName,
            String packageName, boolean hasShownUi) {
        final ProcessRecord proc = new ProcessRecordBuilder(pid, uid, processName,
                packageName).setHasShownUi(hasShownUi).build();
        updateProcessLru(proc);
        return proc;
    }

    private ProcessRecord makeSpiedProcessRecord(int pid, int uid, String processName,
            String packageName, boolean hasShownUi) {
        final ProcessRecord proc = spy(new ProcessRecordBuilder(pid, uid, processName,
                packageName).setHasShownUi(hasShownUi).build());
        final WindowProcessController wpc = proc.getWindowProcessController();
        // Need to overwrite the WindowProcessController.mOwner with the new spy object
        setFieldValue(WindowProcessController.class, wpc, "mOwner", proc);
        updateProcessLru(proc);
        return proc;
    }

    private ServiceRecord makeServiceRecord() {
        final ServiceRecord record = mock(ServiceRecord.class);
        // Don't mock getter/setter methods at ServiceRecordInternal.
        doCallRealMethod().when(record).isStartRequested();
        doCallRealMethod().when(record).setStartRequested(any(boolean.class));
        doCallRealMethod().when(record).isForeground();
        doCallRealMethod().when(record).setIsForeground(any(boolean.class));
        doCallRealMethod().when(record).isKeepWarming();
        doCallRealMethod().when(record).updateKeepWarmLocked();
        doCallRealMethod().when(record).getLastActivity();
        doCallRealMethod().when(record).setLastActivity(any(long.class));
        doCallRealMethod().when(record).getForegroundServiceType();
        doCallRealMethod().when(record).setForegroundServiceType(any(int.class));
        doCallRealMethod().when(record).getHostProcess();
        doCallRealMethod().when(record).getIsolationHostProcess();
        doCallRealMethod().when(record).getLastTopAlmostPerceptibleBindRequestUptimeMs();
        doCallRealMethod().when(record).setLastTopAlmostPerceptibleBindRequestUptimeMs(
                any(long.class));

        setFieldValue(ServiceRecord.class, record, "connections",
                new ArrayMap<IBinder, ArrayList<ConnectionRecord>>());
        doCallRealMethod().when(record).getConnectionsSize();
        doCallRealMethod().when(record).getConnectionAt(any(int.class));
        doCallRealMethod().when(record).getConnections();
        return record;
    }

    private ServiceRecord makeServiceRecord(ProcessRecord app) {
        final ServiceRecord record = makeServiceRecord();
        mProcessStateController.setHostProcess(record, app);
        setFieldValue(ServiceRecord.class, record, "packageName", app.info.packageName);
        mProcessStateController.startService(app.mServices, record);
        record.appInfo = app.info;
        setFieldValue(ServiceRecord.class, record, "bindings", new ArrayMap<>());
        setFieldValue(ServiceRecord.class, record, "pendingStarts", new ArrayList<>());
        setFieldValue(ServiceRecord.class, record, "isSdkSandbox", app.isSdkSandbox);
        return record;
    }

    private void setServiceMap(ServiceRecord s, int uid, ComponentName cn) {
        ActiveServices.ServiceMap serviceMap = mService.mServices.mServiceMap.get(
                UserHandle.getUserId(uid));
        if (serviceMap == null) {
            serviceMap = mock(ActiveServices.ServiceMap.class);
            setFieldValue(ActiveServices.ServiceMap.class, serviceMap, "mServicesByInstanceName",
                    new ArrayMap<>());
            setFieldValue(ActiveServices.ServiceMap.class, serviceMap, "mActiveForegroundApps",
                    new ArrayMap<>());
            setFieldValue(ActiveServices.ServiceMap.class, serviceMap, "mServicesByIntent",
                    new ArrayMap<>());
            setFieldValue(ActiveServices.ServiceMap.class, serviceMap, "mDelayedStartList",
                    new ArrayList<>());
            mService.mServices.mServiceMap.put(UserHandle.getUserId(uid), serviceMap);
        }
        serviceMap.mServicesByInstanceName.put(cn, s);
    }

    private ServiceRecord bindService(ProcessRecord service, ProcessRecord client,
            ProcessRecord attributedClient, ServiceRecord record, long bindFlags, IBinder binder) {
        if (record == null) {
            record = makeServiceRecord(service);
        }
        AppBindRecord binding = new AppBindRecord(record, null, client, attributedClient);
        ConnectionRecord cr = spy(new ConnectionRecord(binding,
                mock(ActivityServiceConnectionsHolder.class),
                mock(IServiceConnection.class), bindFlags,
                0, null, client.uid, client.processName, client.info.packageName, null));
        doCallRealMethod().when(record).addConnection(any(IBinder.class),
                any(ConnectionRecord.class));
        record.addConnection(binder, cr);
        mProcessStateController.addConnection(client.mServices, cr);
        binding.connections.add(cr);
        doNothing().when(cr).trackProcState(anyInt(), anyInt());
        return record;
    }

    private void setTopProcessState(int procState) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setTopProcessStateAsync(procState);
            flushActivityStateHandler();
        } else {
            doReturn(procState).when(mService.mAtmInternal).getTopProcessState();
        }
    }

    private void setDeviceUnlocking(boolean unlocking) {
        mActivityStateAsyncUpdater.setDeviceUnlocking(unlocking);
    }

    private void setExpandedNotificationShade(boolean expandedShade) {
        mActivityStateAsyncUpdater.setExpandedNotificationShadeAsync(expandedShade);
        flushActivityStateHandler();
    }

    private void setTopProcess(ProcessRecord proc) {
        if (Flags.pushActivityStateToOomadjuster()) {
            final WindowProcessController wpc =
                    proc == null ? null : proc.getWindowProcessController();
            mActivityStateAsyncUpdater.setTopProcessAsync(wpc, false,
                    false);
            flushActivityStateHandler();
        } else {
            if (proc == null) return;
            doReturn(proc).when(mService).getTopApp();
        }
    }

    private void setPreviousProcess(WindowProcessController wpc) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setPreviousProcessAsync(wpc);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(true).when(wpc).isPreviousProcess();
        }
    }

    private void setHomeProcess(WindowProcessController wpc) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setHomeProcessAsync(wpc);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(true).when(wpc).isHomeProcess();
        }
    }

    private void setHeavyWeightProcess(WindowProcessController wpc) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setHeavyWeightProcessAsync(wpc);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(true).when(wpc).isHeavyWeightProcess();
        }
    }

    private void setVisibleDozeUiProcess(WindowProcessController wpc) {
        mActivityStateAsyncUpdater.setVisibleDozeUiProcessAsync(wpc);
        flushActivityStateHandler();
    }

    private void setWakefulness(int state) {
        if (Flags.pushGlobalStateToOomadjuster()) {
            mProcessStateController.setWakefulness(state);
        } else {
            mService.mWakefulness.set(state);
        }
    }

    @SuppressWarnings("GuardedBy")
    private void setBackupTarget(ProcessRecord app) {
        if (Flags.pushGlobalStateToOomadjuster()) {
            mProcessStateController.setBackupTarget(app, app.userId);
        } else {
            BackupRecord backupTarget = new BackupRecord(null, 0, 0, 0, true);
            backupTarget.app = app;
            doReturn(backupTarget).when(mService.mBackupTargets).get(anyInt());
        }
    }

    @SuppressWarnings("GuardedBy")
    private void stopBackupTarget(int userId) {
        if (Flags.pushGlobalStateToOomadjuster()) {
            mProcessStateController.stopBackupTarget(userId);
        } else {
            doReturn(null).when(mService.mBackupTargets).get(anyInt());
        }
    }

    private void setHasActivity(WindowProcessController wpc, boolean hasActivity) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setHasActivityAsync(wpc, hasActivity);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(hasActivity).when(wpc).hasActivities();
        }
    }

    private void setActivityStateFlags(WindowProcessController wpc, int flags) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setActivityStateAsync(wpc, flags, Long.MIN_VALUE);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(flags).when(wpc).getActivityStateFlags();
        }
    }

    private void setActivityState(WindowProcessController wpc, int flags,
            long perceptibleStopTimeMs) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setActivityStateAsync(wpc, flags, perceptibleStopTimeMs);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(flags).when(wpc).getActivityStateFlags();
            doReturn(perceptibleStopTimeMs).when(wpc).getPerceptibleTaskStoppedTimeMillis();
        }
    }

    private void setHasRecentTasks(WindowProcessController wpc, boolean hasRecentTasks) {
        if (Flags.pushActivityStateToOomadjuster()) {
            mActivityStateAsyncUpdater.setHasRecentTasksAsync(wpc, hasRecentTasks);
            flushActivityStateHandler();
        } else {
            if (wpc == null) return;
            doReturn(hasRecentTasks).when(wpc).hasRecentTasks();
        }
    }

    @SuppressWarnings("GuardedBy")
    private void setIsReceivingBroadcast(ProcessRecord app, boolean isReceivingBroadcast,
            int schedGroup) {
        if (Flags.pushBroadcastStateToOomadjuster()) {
            if (isReceivingBroadcast) {
                mProcessStateController.noteBroadcastDeliveryStarted(app, schedGroup);
            } else {
                mProcessStateController.noteBroadcastDeliveryEnded(app);
            }
        } else {
            doReturn(isReceivingBroadcast).when(mService).isReceivingBroadcastLocked(
                    any(ProcessRecord.class), any(int[].class));
        }
    }


    private ContentProviderRecord createContentProviderRecord(ProcessRecord publisher, String name,
            boolean hasExternalProviders) {
        ContentProviderRecord record = mock(ContentProviderRecord.class);
        mProcessStateController.addPublishedProvider(publisher, name, record);
        record.proc = publisher;
        setFieldValue(ContentProviderRecord.class, record, "connections",
                new ArrayList<ContentProviderConnection>());
        doCallRealMethod().when(record).getHostProcess();
        doCallRealMethod().when(record).numberOfConnections();
        doCallRealMethod().when(record).getConnectionsAt(any(int.class));
        doReturn(hasExternalProviders).when(record).hasExternalProcessHandles();
        return record;
    }

    private ContentProviderConnection bindProvider(ProcessRecord client,
            ContentProviderRecord record) {
        ContentProviderConnection conn = spy(new ContentProviderConnection(record, client,
                client.info.packageName, UserHandle.getUserId(client.uid)));
        record.connections.add(conn);
        mProcessStateController.addProviderConnection(client, conn);
        return conn;
    }

    private void unbindProvider(ProcessRecord client, ContentProviderRecord record,
            ContentProviderConnection conn) {
        record.connections.remove(conn);
        mProcessStateController.removeProviderConnection(client, conn);
    }

    @SuppressWarnings("GuardedBy")
    private void assertProcStates(ProcessRecord app, int expectedProcState, int expectedAdj,
            int expectedSchedGroup) {
        final ProcessRecordInternal state = app;
        final int pid = app.getPid();
        assertEquals(expectedProcState, state.getSetProcState());
        assertEquals(expectedAdj, state.getSetAdj());
        assertEquals(expectedAdj, mInjector.mLastSetOomAdj.get(pid, INVALID_ADJ));
        assertEquals(expectedSchedGroup, state.getSetSchedGroup());

        // Below BFGS should never have BFSL.
        if (expectedProcState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            assertThatProcess(app).notHasCapability(PROCESS_CAPABILITY_BFSL);
        }
        // Above FGS should always have BFSL.
        if (expectedProcState < PROCESS_STATE_FOREGROUND_SERVICE) {
            assertThatProcess(app).hasCapability(PROCESS_CAPABILITY_BFSL);
        }
    }

    @SuppressWarnings("GuardedBy")
    private void assertProcStates(ProcessRecord app, int expectedProcState, int expectedAdj,
            int expectedSchedGroup, String expectedAdjType) {
        assertProcStates(app, expectedProcState, expectedAdj, expectedSchedGroup);
        final ProcessRecordInternal state = app;
        assertEquals(expectedAdjType, state.getAdjType());
    }

    @SuppressWarnings("GuardedBy")
    private void assertProcStates(ProcessRecord app, int expectedProcState, int expectedAdj,
            int expectedSchedGroup, String expectedAdjType, boolean expectedCached) {
        assertProcStates(app, expectedProcState, expectedAdj, expectedSchedGroup, expectedAdjType);
        final ProcessRecordInternal state = app;
        assertEquals(expectedCached, state.isCached());
    }

    @SuppressWarnings("GuardedBy")
    private void assertFreezeState(ProcessRecord app, boolean expectedFreezeState) {
        boolean actualFreezeState = mTestCachedAppOptimizer.mLastSetFreezeState.get(app.getPid(),
                false);
        assertEquals("Unexcepted freeze state for " + app.processName, expectedFreezeState,
                actualFreezeState);
    }

    private void flushActivityStateHandler() {
        CountDownLatch latch = new CountDownLatch(1);
        mActivityStateHandler.post(() -> {
            latch.countDown();
        });
        try {
            assertTrue("Timed-out waiting for flushActivityStateHandler to complete",
                    latch.await(HANDLER_THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            fail("Unexpected exception in flushActivityStateHandler: " + e.toString());
        }
    }

    private class ProcessRecordBuilder {
        @SuppressWarnings("UnusedVariable")
        int mPid;
        int mUid;
        String mProcessName;
        String mPackageName;
        long mVersionCode = 12345;
        int mTargetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
        long mLastActivityTime;
        long mLastPssTime;
        long mNextPssTime;
        long mLastPss = 12345;
        int mMaxAdj = UNKNOWN_ADJ;
        int mSetRawAdj = INVALID_ADJ;
        int mCurAdj = INVALID_ADJ;
        int mSetAdj = INVALID_ADJ;
        int mCurSchedGroup = SCHED_GROUP_BACKGROUND;
        int mSetSchedGroup = SCHED_GROUP_BACKGROUND;
        int mCurProcState = PROCESS_STATE_NONEXISTENT;
        int mRepProcState = PROCESS_STATE_NONEXISTENT;
        int mCurRawProcState = PROCESS_STATE_NONEXISTENT;
        int mSetProcState = PROCESS_STATE_NONEXISTENT;
        int mConnectionGroup = 0;
        int mConnectionImportance = 0;
        boolean mServiceb = false;
        boolean mHasClientActivities = false;
        boolean mHasForegroundServices = false;
        int mFgServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE;
        boolean mHasForegroundActivities = false;
        boolean mRepForegroundActivities = false;
        boolean mSystemNoUi = false;
        boolean mHasShownUi = false;
        boolean mHasTopUi = false;
        boolean mHasOverlayUi = false;
        boolean mRunningRemoteAnimation = false;
        boolean mHasAboveClient = false;
        boolean mTreatLikeActivity = false;
        boolean mKilledByAm = false;
        Object mForcingToImportant;
        long mLastProviderTime = Long.MIN_VALUE;
        long mLastTopTime = Long.MIN_VALUE;
        int mNumOfExecutingServices = 0;
        String mIsolatedEntryPoint = null;
        boolean mExecServicesFg = false;
        String mSdkSandboxClientAppPackage = null;

        ProcessRecordBuilder(int pid, int uid, String processName, String packageName) {
            mPid = pid;
            mUid = uid;
            mProcessName = processName;
            mPackageName = packageName;

            long now = SystemClock.uptimeMillis();
            mLastActivityTime = now;
            mLastPssTime = now;
            mNextPssTime = now;
        }

        ProcessRecordBuilder setHasShownUi(boolean hasShownUi) {
            mHasShownUi = hasShownUi;
            return this;
        }

        ProcessRecordBuilder setSdkSandboxClientAppPackage(String sdkSandboxClientAppPackage) {
            mSdkSandboxClientAppPackage = sdkSandboxClientAppPackage;
            return this;
        }

        @SuppressWarnings("GuardedBy")
        public ProcessRecord build() {
            ApplicationInfo ai = spy(new ApplicationInfo());
            ai.uid = mUid;
            ai.packageName = mPackageName;
            ai.longVersionCode = mVersionCode;
            ai.targetSdkVersion = mTargetSdkVersion;
            doCallRealMethod().when(mService).getPackageManagerInternal();
            doReturn(null).when(mPackageManagerInternal).getApplicationInfo(
                    eq(mSdkSandboxClientAppPackage), anyLong(), anyInt(), anyInt());
            ProcessRecord app = new ProcessRecord(mService, ai, mProcessName, mUid,
                    mSdkSandboxClientAppPackage, -1, null);
            app.setPid(mPid);
            final ProcessRecordInternal state = app;
            final ProcessServiceRecord services = app.mServices;
            final ProcessProfileRecord profile = app.mProfile;
            final ProcessProviderRecord providers = app.mProviders;
            app.makeActive(mock(ApplicationThreadDeferred.class), mService.mProcessStats);
            app.setLastActivityTime(mLastActivityTime);
            app.setKilledByAm(mKilledByAm);
            app.setIsolatedEntryPoint(mIsolatedEntryPoint);
            final WindowProcessController wpc = spy(app.getWindowProcessController());
            setFieldValue(ProcessRecord.class, app, "mWindowProcessController", wpc);
            doReturn(Long.MIN_VALUE)
                .when(wpc)
                .getPerceptibleTaskStoppedTimeMillis();
            profile.setLastPssTime(mLastPssTime);
            profile.setNextPssTime(mNextPssTime);
            profile.setLastPss(mLastPss);
            state.setMaxAdj(mMaxAdj);
            state.setSetRawAdj(mSetRawAdj);
            state.setCurAdj(mCurAdj);
            state.setSetAdj(mSetAdj);
            state.setCurrentSchedulingGroup(mCurSchedGroup);
            state.setSetSchedGroup(mSetSchedGroup);
            state.setCurProcState(mCurProcState);
            state.setReportedProcState(mRepProcState);
            state.setCurRawProcState(mCurRawProcState);
            state.setSetProcState(mSetProcState);
            state.setServiceB(mServiceb);
            state.setRepForegroundActivities(mRepForegroundActivities);
            state.setHasForegroundActivities(mHasForegroundActivities);
            state.setSystemNoUi(mSystemNoUi);
            state.setHasShownUi(mHasShownUi);
            state.setHasTopUi(mHasTopUi);
            state.setIsRunningRemoteAnimation(mRunningRemoteAnimation);
            state.setHasOverlayUi(mHasOverlayUi);
            state.setLastTopTime(mLastTopTime);
            state.setForcingToImportant(mForcingToImportant);
            services.setConnectionGroup(mConnectionGroup);
            services.setConnectionImportance(mConnectionImportance);
            services.setHasClientActivities(mHasClientActivities);
            services.setHasForegroundServices(mHasForegroundServices, mFgServiceTypes,
                    /* hasNoneType=*/false);
            services.setHasAboveClient(mHasAboveClient);
            services.setTreatLikeActivity(mTreatLikeActivity);
            services.setExecServicesFg(mExecServicesFg);
            for (int i = 0; i < mNumOfExecutingServices; i++) {
                services.startExecutingService(makeServiceRecord());
            }
            providers.setLastProviderTime(mLastProviderTime);

            UidRecord uidRec = mActiveUids.get(mUid);
            if (uidRec == null) {
                uidRec = new UidRecord(mUid, mService);
                mActiveUids.put(mUid, uidRec);
            }
            uidRec.addProcess(app);
            app.setUidRecord(uidRec);
            return app;
        }
    }

    private static final class TestProcessDependencies
            implements CachedAppOptimizer.ProcessDependencies {
        @Override
        public long[] getRss(int pid) {
            return new long[]{/*totalRSS*/ 0, /*fileRSS*/ 0, /*anonRSS*/ 0, /*swap*/ 0};
        }

        @Override
        public void performCompaction(CachedAppOptimizer.CompactProfile action, int pid)
                throws IOException {}

        @Override
        public void performMemcgCompaction(
                CachedAppOptimizer.CompactProfile action, int uid, int pid
        )
                throws IOException {}

        @Override
        public void performNativeCompaction(CachedAppOptimizer.CompactProfile action, int pid)
                throws IOException {}
    }

    private static class TestCachedAppOptimizer extends CachedAppOptimizer {
        private AssertionError mAssertionError = null;
        private SparseBooleanArray mLastSetFreezeState = new SparseBooleanArray();

        TestCachedAppOptimizer(ActivityManagerService ams) {
            super(ams, null, new TestProcessDependencies());
        }

        @Override
        public boolean useFreezer() {
            return true;
        }

        @Override
        public void freezeAppAsyncLSP(ProcessRecord app) {
            try {
                // This try-catch and throw later is a workaround for b/437137965.
                // TODO: b/437137965 - When mid-update exceptions are no longer caught, assert here.
                assertFalse("Should not try to freeze an already frozen process.",
                        app.mOptRecord.isFrozen());
                assertFalse("Should not try to freeze a process pending freeze",
                        app.mOptRecord.isPendingFreeze());
            } catch (AssertionError ae) {
                if (mAssertionError == null) {
                    // Just capture the first assert;
                    mAssertionError = ae;
                }
            }
            mLastSetFreezeState.put(app.getPid(), true);
            app.mOptRecord.setPendingFreeze(true);
        }

        @Override
        public void unfreezeAppLSP(ProcessRecord app, @UnfreezeReason int reason) {
            mLastSetFreezeState.put(app.getPid(), false);
            app.mOptRecord.setPendingFreeze(false);
            app.mOptRecord.setFrozen(false);
        }

        public void throwFailure() {
            if (mAssertionError == null) return;
            throw mAssertionError;
        }
    }

    static class OomAdjusterInjector extends OomAdjuster.Injector {
        // Jump ahead in time by this offset amount.
        long mTimeOffsetMillis = 0;
        private SparseIntArray mLastSetOomAdj = new SparseIntArray();

        // A sequence number that increases every time setOomAdj is called
        int mLastAppliedAt = 0;
        // Holds the last sequence number setOomAdj is called for a pid
        private SparseIntArray mSetOomAdjAppliedAt = new SparseIntArray();

        void reset() {
            mTimeOffsetMillis = 0;
            mLastSetOomAdj.clear();
            mLastAppliedAt = 0;
            mSetOomAdjAppliedAt.clear();
        }

        void jumpUptimeAheadTo(long uptimeMillis) {
            final long jumpMs = uptimeMillis - getUptimeMillis();
            if (jumpMs <= 0) return;
            mTimeOffsetMillis += jumpMs;
        }

        @Override
        long getUptimeMillis() {
            return SystemClock.uptimeMillis() + mTimeOffsetMillis;
        }

        @Override
        long getElapsedRealtimeMillis() {
            return SystemClock.elapsedRealtime() + mTimeOffsetMillis;
        }

        @Override
        void batchSetOomAdj(ArrayList<ProcessRecordInternal> procsToOomAdj) {
            for (ProcessRecordInternal proc : procsToOomAdj) {
                final int pid = proc.getPid();
                if (pid <= 0) continue;
                mLastSetOomAdj.put(pid, proc.getCurAdj());
                mSetOomAdjAppliedAt.put(pid, mLastAppliedAt++);
            }
        }

        @Override
        void setOomAdj(int pid, int uid, int adj) {
            if (pid <= 0) return;
            mLastSetOomAdj.put(pid, adj);
            mSetOomAdjAppliedAt.put(pid, mLastAppliedAt++);
        }

        @Override
        void setThreadPriority(int tid, int priority) {
            // do nothing
        }
    }
}
