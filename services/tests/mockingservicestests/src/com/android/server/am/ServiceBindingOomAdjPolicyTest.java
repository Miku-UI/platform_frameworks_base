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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_IMPLICIT_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_HOME;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_INCLUDE_CAPABILITIES;
import static android.content.Context.BIND_WAIVE_PRIORITY;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.am.psc.Constants.CACHED_APP_MIN_ADJ;
import static com.android.server.am.psc.Constants.HOME_APP_ADJ;
import static com.android.server.am.psc.Constants.PERCEPTIBLE_APP_ADJ;
import static com.android.server.am.psc.Constants.SERVICE_ADJ;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_NONE;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_OTHER;
import static com.android.server.am.psc.OomAdjuster.IMPLICIT_CPU_TIME_REASON_NONE;
import static com.android.server.am.psc.OomAdjuster.IMPLICIT_CPU_TIME_REASON_OTHER;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.IServiceConnection;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;

import com.android.server.am.psc.MockUtils;
import com.android.server.am.psc.OomAdjuster;
import com.android.server.wm.WindowProcessController;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.util.function.Consumer;

/**
 * Test class for the service binding policy.
 *
 * Build/Install/Run:
 *  atest ServiceBindingOomAdjPolicyTest
 */
@Presubmit
public final class ServiceBindingOomAdjPolicyTest extends BaseServiceTest {
    private static final String TEST_APP1_NAME = CALLING_PACKAGE_NAME;
    private static final String TEST_SERVICE1_NAME = CALLING_PACKAGE_NAME + ".Foobar";
    private static final int TEST_APP1_UID = 10123;
    private static final int TEST_APP1_PID = 12345;

    private static final String TEST_APP2_NAME = "com.example.bar";
    private static final String TEST_SERVICE2_NAME = "com.example.bar.Buz";
    private static final int TEST_APP2_UID = 10124;
    private static final int TEST_APP2_PID = 12346;

    /** Run at the test class initialization */
    @BeforeClass
    public static void setUpOnce() {
        // Share class loader to allow access to package-private classes
        System.setProperty("dexmaker.share_classloader", "true");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mCurrentCallingUid = TEST_APP1_UID;
        mCurrentCallingPid = TEST_APP1_PID;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testServiceSelfBindingOomAdj() throws Exception {
        // Verify that there should be 0 oom adj updates.
        performTestServiceSelfBindingOomAdj(never(), never());
    }

    @SuppressWarnings("GuardedBy")
    private void performTestServiceSelfBindingOomAdj(VerificationMode bindMode,
            VerificationMode unbindMode) throws Exception {
        final ProcessRecord app = addProcessRecord(
                TEST_APP1_PID,           // pid
                TEST_APP1_UID,           // uid
                PROCESS_STATE_SERVICE,   // procstate
                SERVICE_ADJ,             // adj
                PROCESS_CAPABILITY_NONE, // capabilities
                TEST_APP1_NAME          // packageName
        );
        final Intent serviceIntent = createServiceIntent(TEST_APP1_NAME, TEST_SERVICE1_NAME,
                TEST_APP1_UID);
        final IServiceConnection serviceConnection = mock(IServiceConnection.class);

        // Make a self binding.
        assertNotEquals(0, mAms.bindService(
                app.getThread(),         // caller
                null,                    // token
                serviceIntent,           // service
                null,                    // resolveType
                serviceConnection,       // connection
                BIND_AUTO_CREATE,        // flags
                TEST_APP1_NAME,          // callingPackage
                USER_SYSTEM              // userId
        ));

        verify(mAms.mProcessStateController, bindMode).runPendingUpdate(anyInt());
        clearInvocations(mAms.mProcessStateController);

        // Unbind the service.
        mAms.unbindService(serviceConnection);

        verify(mAms.mProcessStateController, unbindMode).runPendingUpdate(anyInt());
        clearInvocations(mAms.mProcessStateController);

        removeProcessRecord(app);
    }

    @Test
    public void testServiceDistinctBindingOomAdjMoreImportant() throws Exception {
        // Verify that there should be at least 1 oom adj update
        // because the client is more important.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ,
                PROCESS_CAPABILITY_NONE, TEST_APP1_NAME,
                this::setHasForegroundServices,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_HOME,
                HOME_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHomeProcess,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjLessImportant() throws Exception {
        // Verify that there should be 0 oom adj update
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_HOME, HOME_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP1_NAME,
                this::setHomeProcess,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHasForegroundServices,
                BIND_AUTO_CREATE,
                never(), never());
    }

    @Test
    public void testServiceDistinctBindingOomAdj_propagateCpuTimeCapability() throws Exception {
        // Note that PROCESS_CAPABILITY_CPU_TIME is special and should be propagated even when
        // BIND_INCLUDE_CAPABILITIES is not present.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_HOME, HOME_APP_ADJ, PROCESS_CAPABILITY_CPU_TIME, TEST_APP1_NAME,
                this::setHomeProcess,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHasForegroundServices,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());

        // BIND_WAIVE_PRIORITY should not affect propagation of capability CPU_TIME
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_FOREGROUND_SERVICE, PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_CPU_TIME,
                TEST_APP1_NAME,
                this::setHasForegroundServices,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_HOME, HOME_APP_ADJ,
                PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHomeProcess,
                BIND_AUTO_CREATE | BIND_WAIVE_PRIORITY,
                atLeastOnce(), atLeastOnce());

        // If both process have the capability, the bind should not need an update but the unbind
        // is not safe to skip.
        // Note that this check can fail on future changes that are not related to
        // PROCESS_CAPABILITY_CPU_TIME and trigger updates but this is important to ensure
        // efficiency of OomAdjuster.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_HOME, HOME_APP_ADJ, PROCESS_CAPABILITY_CPU_TIME, TEST_APP1_NAME,
                this::setHomeProcess,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_HOME, HOME_APP_ADJ,
                PROCESS_CAPABILITY_CPU_TIME, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHomeProcess,
                BIND_AUTO_CREATE,
                never(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjNoIncludeCapabilities() throws Exception {
        // Note that some capabilities like PROCESS_CAPABILITY_CPU_TIME are special and propagated
        // regardless of BIND_INCLUDE_CAPABILITIES. We don't test for them here.

        // Verify that there should be 0 oom adj update
        // because we didn't specify the "BIND_INCLUDE_CAPABILITIES"
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_HOME, HOME_APP_ADJ,
                PROCESS_CAPABILITY_FOREGROUND_MICROPHONE, TEST_APP1_NAME,
                this::setHomeProcess,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHasForegroundServices,
                BIND_AUTO_CREATE,
                never(), never());
    }

    @Test
    public void testServiceDistinctBindingOomAdjWithIncludeCapabilities() throws Exception {
        // Verify that there should be at least 1 oom adj update
        // because we use the "BIND_INCLUDE_CAPABILITIES"
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_HOME, HOME_APP_ADJ,
                PROCESS_CAPABILITY_FOREGROUND_MICROPHONE, TEST_APP1_NAME,
                this::setHomeProcess,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHasForegroundServices,
                BIND_AUTO_CREATE | BIND_INCLUDE_CAPABILITIES,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjFreezeCaller() throws Exception {
        // Verify that there should be 0 oom adj update
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_NONE,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_FOREGROUND_SERVICE,
                PERCEPTIBLE_APP_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                this::setHasForegroundServices,
                BIND_AUTO_CREATE,
                never(), never());
    }

    @Test
    public void testServiceDistinctBindingOomAdjCpuTime() throws Exception {
        // Verify the CPU_TIME capability triggers an update.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjCpuTime_hostHasCpuTime() throws Exception {
        // Verify the CPU_TIME capability does not trigger an update if the host has already it.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_CPU_TIME, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                never(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjCpuTime_hostHasImplicitCpuTime() throws Exception {
        // Verify the CPU_TIME capability still triggers an update even if the host has the
        // IMPLICIT_CPU_TIME.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_IMPLICIT_CPU_TIME, TEST_APP2_NAME,
                TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjImplicitCpuTime() throws Exception {
        // Verify the IMPLICIT_CPU_TIME capability triggers an update.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ,
                PROCESS_CAPABILITY_IMPLICIT_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_NONE, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjImplicitCpuTime_hostHasCpuTime() throws Exception {
        // Verify the IMPLICIT_CPU_TIME capability still triggers an update even if the host has the
        // CPU_TIME.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ,
                PROCESS_CAPABILITY_IMPLICIT_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_CPU_TIME, TEST_APP2_NAME, TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                atLeastOnce(), atLeastOnce());
    }

    @Test
    public void testServiceDistinctBindingOomAdjImplicitCpuTime_hostHasImplicitCpuTime()
            throws Exception {
        // Verify the IMPLICIT_CPU_TIME capability does not trigger an update if the host has
        // already it.
        performTestServiceDistinctBindingOomAdj(TEST_APP1_PID, TEST_APP1_UID,
                PROCESS_STATE_CACHED_EMPTY, CACHED_APP_MIN_ADJ,
                PROCESS_CAPABILITY_IMPLICIT_CPU_TIME,
                TEST_APP1_NAME, null,
                TEST_APP2_PID, TEST_APP2_UID, PROCESS_STATE_CACHED_EMPTY,
                CACHED_APP_MIN_ADJ, PROCESS_CAPABILITY_IMPLICIT_CPU_TIME, TEST_APP2_NAME,
                TEST_SERVICE2_NAME,
                null,
                BIND_AUTO_CREATE,
                never(), atLeastOnce());
    }

    @SuppressWarnings("GuardedBy")
    private void performTestServiceDistinctBindingOomAdj(int clientPid, int clientUid,
            int clientProcState, int clientAdj, int clientCap, String clientPackageName,
            Consumer<ProcessRecord> clientAppFixer,
            int servicePid, int serviceUid, int serviceProcState, int serviceAdj,
            int serviceCap, String servicePackageName, String serviceName,
            Consumer<ProcessRecord> serviceAppFixer, int bindingFlags,
            VerificationMode bindMode, VerificationMode unbindMode)
            throws Exception {
        final ProcessRecord clientApp = addProcessRecord(
                clientPid,
                clientUid,
                clientProcState,
                clientAdj,
                clientCap,
                clientPackageName
        );
        final ProcessRecord serviceApp = addProcessRecord(
                servicePid,
                serviceUid,
                serviceProcState,
                serviceAdj,
                serviceCap,
                servicePackageName
        );
        final Intent serviceIntent = createServiceIntent(servicePackageName, serviceName,
                serviceUid);
        final IServiceConnection serviceConnection = mock(IServiceConnection.class);
        if (clientAppFixer != null) clientAppFixer.accept(clientApp);
        if (serviceAppFixer != null) serviceAppFixer.accept(serviceApp);

        // Make a self binding.
        assertNotEquals(0, mAms.bindService(
                clientApp.getThread(), // caller
                null,                  // token
                serviceIntent,         // service
                null,                  // resolveType
                serviceConnection,     // connection
                bindingFlags,          // flags
                clientPackageName,     // callingPackage
                USER_SYSTEM            // userId
        ));

        verify(mAms.mProcessStateController, bindMode).runPendingUpdateImpl(anyInt());
        clearInvocations(mAms.mProcessStateController);

        if (clientApp.isFreezable()) {
            verify(mAms.getCachedAppOptimizer(), times(1))
                    .freezeAppAsyncAtEarliestLSP(eq(clientApp));
            clearInvocations(mAms.getCachedAppOptimizer());
        }

        // Unbind the service.
        mAms.unbindService(serviceConnection);

        verify(mAms.mProcessStateController, unbindMode).runPendingUpdateImpl(anyInt());
        clearInvocations(mAms.mProcessStateController);

        removeProcessRecord(clientApp);
        removeProcessRecord(serviceApp);
    }

    private void setHasForegroundServices(ProcessRecord app) {
        mAms.mProcessStateController.setHasForegroundServices(app.mServices, true,
                FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED, false);
    }

    private void setHomeProcess(ProcessRecord app) {
        final WindowProcessController wpc = app.getWindowProcessController();
        doReturn(true).when(wpc).isHomeProcess();
    }

    @SuppressWarnings("GuardedBy")
    private ProcessRecord addProcessRecord(int pid, int uid, int procState, int adj, int cap,
                String packageName) {
        final ProcessRecord app = makeProcessRecord(pid, uid, procState, adj, cap, packageName,
                packageName, mAms);

        doReturn(app.getSetCapability()).when(mAms.mOomAdjuster).getDefaultCapability(
                eq(app), anyInt());

        return app;
    }

    @SuppressWarnings("GuardedBy")
    private void removeProcessRecord(ProcessRecord app) {
        mAms.mProcessStateController.setKilled(app, true);
        mProcessList.removeProcessNameLocked(app.processName, app.uid);
        mProcessList.removeLruProcessLocked(app);
    }

    private boolean containsCpuTime(int cap) {
        return (cap & PROCESS_CAPABILITY_CPU_TIME) != 0;
    }

    private boolean containsImplicitCpuTime(int cap) {
        return (cap & PROCESS_CAPABILITY_IMPLICIT_CPU_TIME) != 0;
    }

    @OomAdjuster.CpuTimeReasons
    private int defaultCpuTimeReasons(int cap) {
        return containsCpuTime(cap) ? CPU_TIME_REASON_OTHER : CPU_TIME_REASON_NONE;
    }

    @OomAdjuster.ImplicitCpuTimeReasons
    private int defaultImplicitCpuTimeReasons(int cap) {
        return containsImplicitCpuTime(cap) ? IMPLICIT_CPU_TIME_REASON_OTHER
                : IMPLICIT_CPU_TIME_REASON_NONE;
    }

    @SuppressWarnings("GuardedBy")
    private ProcessRecord makeProcessRecord(int pid, int uid, int procState, int adj, int cap,
            String processName, String packageName, ActivityManagerService ams) {
        final ProcessRecord app = new TestProcessBuilder()
                .setActivityManagerService(ams)
                .setPackageName(packageName)
                .setProcessName(processName)
                .setPid(pid)
                .setUid(uid)
                .build();

        MockUtils.setCurRawProcState(app, procState);
        MockUtils.setCurProcState(app, procState);
        MockUtils.setSetProcState(app, procState);
        MockUtils.setCurRawAdj(app, adj);
        MockUtils.setCurAdj(app, adj);
        MockUtils.setSetAdj(app, adj);
        MockUtils.setCurCapability(app, cap);
        MockUtils.addCurCpuTimeReasons(app, defaultCpuTimeReasons(cap));
        MockUtils.addCurImplicitCpuTimeReasons(app, defaultImplicitCpuTimeReasons(cap));
        MockUtils.setSetCapability(app, cap);
        MockUtils.setSetCpuTimeReasons(app, defaultCpuTimeReasons(cap));
        MockUtils.setSetImplicitCpuTimeReasons(app, defaultImplicitCpuTimeReasons(cap));
        return app;
    }

    // TODO: [b/302724778] Remove manual JNI load
    static {
        System.loadLibrary("mockingservicestestjni");
    }
}
