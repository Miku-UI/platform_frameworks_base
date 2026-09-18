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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityThread.SERVICE_DONE_EXECUTING_ANON;
import static android.app.ActivityThread.SERVICE_DONE_EXECUTING_START;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.Constants.PERCEPTIBLE_APP_ADJ;
import static com.android.server.am.psc.Constants.SERVICE_ADJ;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.IServiceConnection;
import android.content.Intent;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;

import com.android.server.am.psc.MockUtils;
import com.android.server.am.psc.ProcessImportanceAssert;
import com.android.server.am.psc.ProcessImportanceExpectations;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test class for validating process importance around the service lifecycle events.
 *
 * Build/Install/Run:
 * atest ServiceLifecycleImportanceTest
 */
@Presubmit
public class ServiceLifecycleImportanceTest extends BaseServiceTest {
    private static final String TEST_APP1_NAME = CALLING_PACKAGE_NAME;
    private static final String TEST_SERVICE1_NAME = CALLING_PACKAGE_NAME + ".Foobar";
    private static final int TEST_APP1_UID = 10123;
    private static final int TEST_APP1_PID = 12345;

    private static final String TEST_APP2_NAME = "com.example.bar";
    private static final String TEST_SERVICE2_NAME = "com.example.bar.Buz";
    private static final int TEST_APP2_UID = 10124;
    private static final int TEST_APP2_PID = 12346;

    @BeforeClass
    public static void setUpOnce() {
        // Share class loader to allow access to package-private classes
        System.setProperty("dexmaker.share_classloader", "true");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    // The expected importance values for a service in the executing state.
    private static final ProcessImportanceExpectations EXECUTING_SERVICE_EXPECTATIONS =
            new ProcessImportanceExpectations.Builder()
                    .setProcState(PROCESS_STATE_SERVICE)
                    .setOomAdjScore(FOREGROUND_APP_ADJ)
                    .setFreezability(false)
                    .build();

    // The expected importance values for a foreground service.
    private static final ProcessImportanceExpectations FOREGROUND_SERVICE_EXPECTATIONS =
            new ProcessImportanceExpectations.Builder()
                    .setProcState(PROCESS_STATE_FOREGROUND_SERVICE)
                    .setOomAdjScore(PERCEPTIBLE_APP_ADJ)
                    .setFreezability(false)
                    .build();

    // The expected importance values for a background service.
    private static final ProcessImportanceExpectations BACKGROUND_SERVICE_EXPECTATIONS =
            new ProcessImportanceExpectations.Builder()
                    .setProcState(PROCESS_STATE_SERVICE)
                    .setOomAdjScore(SERVICE_ADJ)
                    .setFreezability(false)
                    .build();

    @Test
    public void startService() throws Exception {
        mAms.mInternal.setDeviceIdleAllowlist(new int[]{TEST_APP1_UID}, new int[]{TEST_APP1_UID});

        final ApplicationThreadDeferred serviceThread = mock(ApplicationThreadDeferred.class);
        final ProcessRecord proc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP1_NAME)
                .setPid(TEST_APP1_PID)
                .setUid(TEST_APP1_UID)
                .setAppThread(serviceThread)
                .build();

        ServiceLifecycleArgs createServiceArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            createServiceArgs.token = invocation.getArgument(0);
            // scheduleCreateService will be called while in the executing state.
            ProcessImportanceAssert.assertThat(proc)
                    .matchesIntersection(BACKGROUND_SERVICE_EXPECTATIONS,
                            EXECUTING_SERVICE_EXPECTATIONS);
            return null;
        }).when(serviceThread).scheduleCreateService(any(), any(), any(), anyInt());

        ServiceLifecycleArgs serviceArgsArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            serviceArgsArgs.token = invocation.getArgument(0);
            // scheduleServiceArgs will be called while in the executing state.
            ProcessImportanceAssert.assertThat(proc)
                    .matchesIntersection(BACKGROUND_SERVICE_EXPECTATIONS,
                            EXECUTING_SERVICE_EXPECTATIONS);
            return null;
        }).when(serviceThread).scheduleServiceArgs(any(), any());

        final Intent serviceIntent = createServiceIntent(TEST_APP1_NAME, TEST_SERVICE1_NAME,
                TEST_APP1_UID);
        mAms.startService(
                proc.getThread(),       // caller
                serviceIntent,          // service
                null,                   // resolveType
                false,                  // requireForeground
                proc.getPackageName(),  // callingPackage
                null,                   // callingFeatureId
                USER_SYSTEM);           // userId

        verify(serviceThread).scheduleCreateService(any(), any(), any(), anyInt());
        verify(serviceThread).scheduleServiceArgs(any(), any());
        verify(serviceThread, never()).scheduleBindService(any(), any(), any(), anyBoolean(),
                anyInt(),
                anyLong());

        // The service process need to be both note it is done executing for the service create
        // as well as the pushed service args, to get out of the executing state.
        mAms.serviceDoneExecuting(createServiceArgs.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
        mAms.serviceDoneExecuting(serviceArgsArgs.token, SERVICE_DONE_EXECUTING_START, 0, 0);

        ProcessImportanceAssert.assertThat(proc).matches(BACKGROUND_SERVICE_EXPECTATIONS);
    }

    @Test
    public void startService_alreadyCreated() throws Exception {
        mAms.mInternal.setDeviceIdleAllowlist(new int[]{TEST_APP1_UID}, new int[]{TEST_APP1_UID});

        final ApplicationThreadDeferred serviceThread = mock(ApplicationThreadDeferred.class);
        final ProcessRecord proc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP1_NAME)
                .setPid(TEST_APP1_PID)
                .setUid(TEST_APP1_UID)
                .setAppThread(serviceThread)
                .build();

        // Start the service before starting again to it to get it in the already created state.
        final Intent serviceIntent = createServiceIntent(TEST_APP1_NAME, TEST_SERVICE1_NAME,
                TEST_APP1_UID);
        createAndStartService(proc, serviceIntent, serviceThread);

        ServiceLifecycleArgs serviceArgsArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            serviceArgsArgs.token = invocation.getArgument(0);
            // scheduleServiceArgs will be called while in the executing state.
            ProcessImportanceAssert.assertThat(proc)
                    .matchesIntersection(BACKGROUND_SERVICE_EXPECTATIONS,
                            EXECUTING_SERVICE_EXPECTATIONS);
            return null;
        }).when(serviceThread).scheduleServiceArgs(any(), any());

        final Intent secondServiceIntent = createServiceIntent(TEST_APP1_NAME, TEST_SERVICE1_NAME,
                TEST_APP1_UID);
        mAms.startService(
                proc.getThread(),       // caller
                secondServiceIntent,    // service
                null,                   // resolveType
                false,                  // requireForeground
                proc.getPackageName(),  // callingPackage
                null,                   // callingFeatureId
                USER_SYSTEM);           // userId

        verify(serviceThread, never()).scheduleCreateService(any(), any(), any(), anyInt());
        verify(serviceThread).scheduleServiceArgs(any(), any());
        verify(serviceThread, never()).scheduleBindService(any(), any(), any(), anyBoolean(),
                anyInt(),
                anyLong());

        mAms.serviceDoneExecuting(serviceArgsArgs.token, SERVICE_DONE_EXECUTING_START, 0, 0);

        ProcessImportanceAssert.assertThat(proc).matches(BACKGROUND_SERVICE_EXPECTATIONS);
    }

    @Test
    public void bindService() throws Exception {
        final ProcessRecord clientProc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP1_NAME)
                .setPid(TEST_APP1_PID)
                .setUid(TEST_APP1_UID)
                .build();

        makeForegroundService(clientProc);

        // Create a process to bind to and snoop on the App thread calls.
        final ApplicationThreadDeferred serviceThread = mock(ApplicationThreadDeferred.class);
        final ProcessRecord serviceProc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP2_NAME)
                .setPid(TEST_APP2_PID)
                .setUid(TEST_APP2_UID)
                .setAppThread(serviceThread)
                .build();

        ServiceLifecycleArgs createServiceArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            createServiceArgs.token = invocation.getArgument(0);
            // scheduleCreateService will be called while in the executing state.
            ProcessImportanceAssert.assertThat(serviceProc)
                    .matchesIntersection(FOREGROUND_SERVICE_EXPECTATIONS,
                            EXECUTING_SERVICE_EXPECTATIONS);
            return null;
        }).when(serviceThread).scheduleCreateService(any(), any(), any(), anyInt());

        ServiceLifecycleArgs bindServiceArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            bindServiceArgs.token = invocation.getArgument(0);
            bindServiceArgs.bindToken = invocation.getArgument(1);
            // scheduleBindService will be called while in the executing state.
            ProcessImportanceAssert.assertThat(serviceProc)
                    .matchesIntersection(FOREGROUND_SERVICE_EXPECTATIONS,
                            EXECUTING_SERVICE_EXPECTATIONS);
            return null;
        }).when(serviceThread).scheduleBindService(any(), any(), any(), anyBoolean(), anyInt(),
                anyLong());

        // Bind to the service process from the client process.
        final Intent serviceIntent = createServiceIntent(TEST_APP2_NAME, TEST_SERVICE2_NAME,
                TEST_APP2_UID);
        final IServiceConnection serviceConnection = mock(IServiceConnection.class);
        mAms.bindService(
                clientProc.getThread(),             // caller
                null,                               // token
                serviceIntent,                      // service
                null,                               // resolveType
                serviceConnection,                  // connection
                BIND_AUTO_CREATE | BIND_IMPORTANT,  // flags
                clientProc.getPackageName(),        // callingPackage
                USER_SYSTEM                         // userId
        );

        verify(serviceThread).scheduleCreateService(any(), any(), any(), anyInt());
        verify(serviceThread).scheduleBindService(any(), any(), any(), anyBoolean(), anyInt(),
                anyLong());
        verify(serviceThread, never()).scheduleServiceArgs(any(), any());

        // The service process need to be both note it is done executing for the service create
        // as well as publish the service from the service bind, to get out of the executing state.
        mAms.serviceDoneExecuting(createServiceArgs.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
        mAms.publishService(bindServiceArgs.token, bindServiceArgs.bindToken, mock(IBinder.class));

        // The service should now only be as important as it's binding allows it.
        ProcessImportanceAssert.assertThat(serviceProc).matches(FOREGROUND_SERVICE_EXPECTATIONS);
    }

    @Test
    public void bindService_alreadyCreated() throws Exception {
        mAms.mInternal.setDeviceIdleAllowlist(new int[]{TEST_APP2_UID}, new int[]{TEST_APP2_UID});

        // Create a process to bind to and snoop on the App thread calls.
        final ApplicationThreadDeferred serviceThread = mock(ApplicationThreadDeferred.class);
        final ProcessRecord serviceProc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP2_NAME)
                .setPid(TEST_APP2_PID)
                .setUid(TEST_APP2_UID)
                .setAppThread(serviceThread)
                .build();

        // Start the service before binding to it to get it in the already created state.
        final Intent serviceIntent = createServiceIntent(TEST_APP2_NAME, TEST_SERVICE2_NAME,
                TEST_APP2_UID);
        createAndStartService(serviceProc, serviceIntent, serviceThread);

        // Start the actual test.
        // Create a client foreground service process to bind to the existing service.
        final ProcessRecord clientProc = new TestProcessBuilder()
                .setActivityManagerService(mAms)
                .setPackageName(TEST_APP1_NAME)
                .setPid(TEST_APP1_PID)
                .setUid(TEST_APP1_UID)
                .build();

        makeForegroundService(clientProc);

        ServiceLifecycleArgs bindServiceArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            bindServiceArgs.token = invocation.getArgument(0);
            bindServiceArgs.bindToken = invocation.getArgument(1);
            if (Flags.pscBatchServiceUpdates()) {
                ProcessImportanceAssert.assertThat(serviceProc)
                        .matchesIntersection(FOREGROUND_SERVICE_EXPECTATIONS,
                                EXECUTING_SERVICE_EXPECTATIONS);
            } else {
                // Legacy behavior incorrectly skips triggering an update after putting a bound
                // service in the executing state.
                ProcessImportanceAssert.assertThat(serviceProc)
                        .matches(FOREGROUND_SERVICE_EXPECTATIONS);
            }
            return null;
        }).when(serviceThread).scheduleBindService(any(), any(), any(), anyBoolean(), anyInt(),
                anyLong());

        // Bind to the service process from the client process.
        final Intent secondServiceIntent = createServiceIntent(TEST_APP2_NAME, TEST_SERVICE2_NAME,
                TEST_APP2_UID);
        final IServiceConnection serviceConnection = mock(IServiceConnection.class);
        mAms.bindService(
                clientProc.getThread(),             // caller
                null,                               // token
                secondServiceIntent,                // service
                null,                               // resolveType
                serviceConnection,                  // connection
                BIND_AUTO_CREATE | BIND_IMPORTANT,  // flags
                clientProc.getPackageName(),        // callingPackage
                USER_SYSTEM                         // userId
        );

        // Only the bind service event should have been triggered.
        verify(serviceThread, never()).scheduleCreateService(any(), any(), any(), anyInt());
        verify(serviceThread).scheduleBindService(any(), any(), any(), anyBoolean(), anyInt(),
                anyLong());
        verify(serviceThread, never()).scheduleServiceArgs(any(), any());

        // Finish the bindService callback.
        mAms.publishService(bindServiceArgs.token, bindServiceArgs.bindToken, mock(IBinder.class));

        ProcessImportanceAssert.assertThat(serviceProc).matches(FOREGROUND_SERVICE_EXPECTATIONS);
    }

    // Create and start a service and then finish executing the lifecycle events.
    private void createAndStartService(ProcessRecord serviceProc,
            Intent serviceIntent,
            ApplicationThreadDeferred serviceThread) throws Exception {
        ServiceLifecycleArgs createServiceArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            createServiceArgs.token = invocation.getArgument(0);
            return null;
        }).when(serviceThread).scheduleCreateService(any(), any(), any(), anyInt());

        ServiceLifecycleArgs serviceArgsArgs = new ServiceLifecycleArgs();
        doAnswer((invocation) -> {
            serviceArgsArgs.token = invocation.getArgument(0);
            return null;
        }).when(serviceThread).scheduleServiceArgs(any(), any());

        mAms.startService(
                serviceProc.getThread(),        // caller
                serviceIntent,                  // service
                null,                           // resolveType
                false,                          // requireForeground
                serviceProc.getPackageName(),   // callingPackage
                null,                           // callingFeatureId
                USER_SYSTEM);                   // userId
        mAms.serviceDoneExecuting(createServiceArgs.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
        mAms.serviceDoneExecuting(serviceArgsArgs.token, SERVICE_DONE_EXECUTING_START, 0, 0);
        clearInvocations(serviceThread);
    }

    /** Set proc's importance state as if it was an FGS that has already been evaluated by PSC. */
    private void makeForegroundService(ProcessRecord proc) {
        MockUtils.setCurRawProcState(proc, PROCESS_STATE_FOREGROUND_SERVICE);
        MockUtils.setCurProcState(proc, PROCESS_STATE_FOREGROUND_SERVICE);
        MockUtils.setSetProcState(proc, PROCESS_STATE_FOREGROUND_SERVICE);

        MockUtils.setCurRawAdj(proc, PERCEPTIBLE_APP_ADJ);
        MockUtils.setCurAdj(proc, PERCEPTIBLE_APP_ADJ);
        MockUtils.setSetAdj(proc, PERCEPTIBLE_APP_ADJ);

        MockUtils.setCurCapability(proc, PROCESS_CAPABILITY_ALL);
        MockUtils.setSetCapability(proc, PROCESS_CAPABILITY_ALL);
    }

    // Simple data object for caching the args the app thread receives from service lifecycle events
    static class ServiceLifecycleArgs {
        public IBinder token;
        public IBinder bindToken; // Should only be used for scheduleBindService
    }

    // TODO: b/302724778 - Remove manual JNI load
    static {
        System.loadLibrary("mockingservicestestjni");
    }
}
