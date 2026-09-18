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

package com.android.server.job;

import static android.app.job.Flags.FLAG_HANDLE_ABANDONED_JOBS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.BACK_OFF_POLICY_TYPE;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.DEADLINE_MS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.DELAY_MS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.EFFECTIVE_PRIORITY;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.INTERNAL_STOP_REASON;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.JOB_ID;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.JOB_START_LATENCY_MS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.JOB_STATE_FLAGS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.NUM_PREVIOUS_ATTEMPTS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.NUM_RESCHEDULES_DUE_TO_ABANDONMENT;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.NUM_UNCOMPLETED_WORK_ITEMS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.PERIODIC_JOB_FLEX_INTERVAL_MS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.PERIODIC_JOB_INTERVAL_MS;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.PROC_STATE;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.PROXY_UID;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.PUBLIC_STOP_REASON;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.REQUESTED_PRIORITY;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.SOURCE_UID;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.STANDBY_BUCKET;
import static android.internal.perfetto.protos.AndroidTrackEventOuterClass.AndroidJobSchedulerJob.STATE;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.job.Flags.FLAG_INCLUDE_JOB_NAME_IN_ANR_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AppGlobals;
import android.app.job.IJobService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobProtoEnums;
import android.app.usage.UsageStatsManagerInternal;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.TestLooperManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.TimeoutRecord;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.LocalServices;
import com.android.server.job.JobServiceContext.JobCallback;
import com.android.server.job.controllers.JobStatus;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

public class JobServiceContextTest {
    private static final String SOURCE_PACKAGE = "com.android.frameworks.mockingservicestests";
    private static final String TAG = JobServiceContextTest.class.getSimpleName();
    @ClassRule
    public static final SetFlagsRule.ClassRule mSetFlagsClassRule = new SetFlagsRule.ClassRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = mSetFlagsClassRule.createSetFlagsRule();
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Mock
    private JobSchedulerService mMockJobSchedulerService;
    @Mock
    private ActivityManagerInternal mMockActivityManagerInternal;
    @Mock
    private JobConcurrencyManager mMockConcurrencyManager;
    @Mock
    private JobNotificationCoordinator mMockNotificationCoordinator;
    @Mock
    private IBatteryStats.Stub mMockBatteryStats;
    @Mock
    private JobPackageTracker mMockJobPackageTracker;
    @Mock
    private Context mMockContext;
    @Mock
    private UsageStatsManagerInternal mMockUsageStatsManagerInternal;
    @Mock
    private JobStatus mMockJobStatus;
    @Mock
    private JobParameters mMockJobParameters;
    @Mock
    private JobCallback mMockJobCallback;
    private MockitoSession mMockingSession;
    private JobServiceContext mJobServiceContext;
    private TestLooperManager mTestLooperManager;
    private Object mLock;
    private int mSourceUid;
    @Mock
    private JobPerfettoTracer mMockPerfettoTracer;
    private ComponentName mComponentName;

    private static final int TEST_JOB_ID = 456;
    private static final int ABANDONED_JOB_ID = 123;
    @Before
    public void setUp() throws Exception {
        mMockingSession =
                mockitoSession()
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .mockStatic(LocalServices.class)
                        .startMocking();
        mComponentName = new ComponentName("foo", "bar");
        JobSchedulerService.sElapsedRealtimeClock =
                Clock.fixed(SystemClock.elapsedRealtimeClock().instant(), ZoneOffset.UTC);
        PowerManager mockPowerManager = mock(PowerManager.class);
        when(mockPowerManager.newWakeLock(anyInt(), anyString()))
                .thenReturn(mock(PowerManager.WakeLock.class));
        doReturn(mockPowerManager).when(mMockContext).getSystemService(PowerManager.class);
        doReturn(mMockContext).when(mMockJobSchedulerService).getContext();
        mLock = new Object();
        doReturn(mLock).when(mMockJobSchedulerService).getLock();
        doReturn(mMockActivityManagerInternal)
                .when(() -> LocalServices.getService(ActivityManagerInternal.class));
        doReturn(mMockUsageStatsManagerInternal)
                .when(() -> LocalServices.getService(UsageStatsManagerInternal.class));
        doReturn(ActivityManager.PROCESS_STATE_TOP)
                .when(mMockJobSchedulerService)
                .getUidProcState(anyInt());
        mTestLooperManager = new TestLooperManager(Looper.getMainLooper());
        mJobServiceContext =
                new JobServiceContext(
                        mMockJobSchedulerService,
                        mMockConcurrencyManager,
                        mMockNotificationCoordinator,
                        mMockBatteryStats,
                        mMockJobPackageTracker,
                        Looper.getMainLooper(),
                        mMockPerfettoTracer);
        spyOn(mJobServiceContext);
        mJobServiceContext.setJobParamsLockedForTest(mMockJobParameters);
        mSourceUid = AppGlobals.getPackageManager().getPackageUid(SOURCE_PACKAGE, 0, 0);

        doNothing()
                .when(mMockUsageStatsManagerInternal)
                .setLastJobRunTime(anyString(), anyInt(), anyLong());
        doReturn(true).when(mMockContext).bindServiceAsUser(any(), any(), any(), any());
        doReturn(0L).when(mMockJobSchedulerService).getMinJobExecutionGuaranteeMs(any());
        doReturn(0L).when(mMockJobSchedulerService).getMaxJobExecutionTimeMs(any());

        when(mMockPerfettoTracer.startEvent(anyString())).thenReturn(mMockPerfettoTracer);
        when(mMockPerfettoTracer.addField(anyLong(), anyInt())).thenReturn(mMockPerfettoTracer);
        when(mMockPerfettoTracer.addField(anyLong(), anyLong())).thenReturn(mMockPerfettoTracer);
        doNothing().when(mMockPerfettoTracer).emit();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestLooperManager != null) {
            mTestLooperManager.release();
        }
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    private Clock getAdvancedClock(Clock clock, long incrementMs) {
        return Clock.offset(clock, Duration.ofMillis(incrementMs));
    }

    private void advanceElapsedClock(long incrementMs) {
        JobSchedulerService.sElapsedRealtimeClock =
                getAdvancedClock(JobSchedulerService.sElapsedRealtimeClock, incrementMs);
    }

    /**
     * Test that with the compat change disabled and the flag enabled, abandoned
     * jobs that are timed out are stopped with the correct stop reason and the
     * job is marked as abandoned.
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    @DisableCompatChanges({JobParameters.OVERRIDE_HANDLE_ABANDONED_JOBS})
    public void testJobServiceContext_TimeoutAbandonedJob_EnableFlagDisableCompat() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        doNothing().when(mJobServiceContext).sendStopMessageLocked(captor.capture());

        advanceElapsedClock(30 * MINUTE_IN_MILLIS); // 30 minutes
        mJobServiceContext.setPendingStopReasonLockedForTest(JobParameters.STOP_REASON_UNDEFINED);

        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        doReturn(mSourceUid).when(mMockJobStatus).getSourceUid();
        doReturn(true).when(mMockJobStatus).isAbandoned();
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;

        mJobServiceContext.handleOpTimeoutLocked();

        String stopMessage = captor.getValue();
        assertEquals("timeout while executing and maybe abandoned", stopMessage);
        verify(mMockJobParameters)
                .setStopReason(
                        JobParameters.STOP_REASON_TIMEOUT_ABANDONED,
                        JobParameters.INTERNAL_STOP_REASON_TIMEOUT_ABANDONED,
                        "client timed out and maybe abandoned");
    }

    /**
     * Test that with the compat change enabled and the flag enabled, abandoned
     * jobs that are timed out are stopped with the correct stop reason and the
     * job is not marked as abandoned.
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    @EnableCompatChanges({JobParameters.OVERRIDE_HANDLE_ABANDONED_JOBS})
    public void testJobServiceContext_TimeoutAbandonedJob_EnableFlagEnableCompat() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        doNothing().when(mJobServiceContext).sendStopMessageLocked(captor.capture());

        advanceElapsedClock(30 * MINUTE_IN_MILLIS); // 30 minutes
        mJobServiceContext.setPendingStopReasonLockedForTest(JobParameters.STOP_REASON_UNDEFINED);

        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        doReturn(mSourceUid).when(mMockJobStatus).getSourceUid();
        doReturn(true).when(mMockJobStatus).isAbandoned();
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;

        mJobServiceContext.handleOpTimeoutLocked();

        String stopMessage = captor.getValue();
        assertEquals("timeout while executing", stopMessage);
        verify(mMockJobParameters)
                .setStopReason(
                        JobParameters.STOP_REASON_TIMEOUT,
                        JobParameters.INTERNAL_STOP_REASON_TIMEOUT,
                        "client timed out");
    }

    /**
     * Test that with the compat change disabled and the flag disabled, abandoned
     * jobs that are timed out are stopped with the correct stop reason and the
     * job is not marked as abandoned.
     */
    @Test
    @DisableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    @DisableCompatChanges({JobParameters.OVERRIDE_HANDLE_ABANDONED_JOBS})
    public void testJobServiceContext_TimeoutAbandonedJob_DisableFlagDisableCompat() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        doNothing().when(mJobServiceContext).sendStopMessageLocked(captor.capture());

        advanceElapsedClock(30 * MINUTE_IN_MILLIS); // 30 minutes
        mJobServiceContext.setPendingStopReasonLockedForTest(JobParameters.STOP_REASON_UNDEFINED);

        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        doReturn(true).when(mMockJobStatus).isAbandoned();
        doReturn(mSourceUid).when(mMockJobStatus).getSourceUid();
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;

        synchronized (mLock) {
            mJobServiceContext.handleOpTimeoutLocked();
        }

        String stopMessage = captor.getValue();
        assertEquals("timeout while executing", stopMessage);
        verify(mMockJobParameters)
                .setStopReason(
                        JobParameters.STOP_REASON_TIMEOUT,
                        JobParameters.INTERNAL_STOP_REASON_TIMEOUT,
                        "client timed out");
    }

    /**
     * Test that non-abandoned jobs that are timed out are stopped with the correct stop reason
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    @DisableCompatChanges({JobParameters.OVERRIDE_HANDLE_ABANDONED_JOBS})
    public void testJobServiceContext_TimeoutNoAbandonedJob() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        synchronized (mLock) {
            doNothing().when(mJobServiceContext).sendStopMessageLocked(captor.capture());
        }
        advanceElapsedClock(30 * MINUTE_IN_MILLIS); // 30 minutes
        mJobServiceContext.setPendingStopReasonLockedForTest(JobParameters.STOP_REASON_UNDEFINED);

        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        doReturn(false).when(mMockJobStatus).isAbandoned();
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;

        mJobServiceContext.handleOpTimeoutLocked();

        String stopMessage = captor.getValue();
        assertEquals("timeout while executing", stopMessage);
        verify(mMockJobParameters)
                .setStopReason(
                        JobParameters.STOP_REASON_TIMEOUT,
                        JobParameters.INTERNAL_STOP_REASON_TIMEOUT,
                        "client timed out");
    }

    /**
     * Test that the JobStatus is marked as abandoned when the JobServiceContext
     * receives a MSG_HANDLE_ABANDONED_JOB message
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    public void testJobServiceContext_HandleAbandonedJob() {
        final int jobId = ABANDONED_JOB_ID;
        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        mJobServiceContext.setRunningCallbackLockedForTest(mMockJobCallback);
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        doReturn(jobId).when(mMockJobStatus).getJobId();

        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);

        verify(mMockJobStatus).setAbandoned(true);
    }

    /**
     * Test that the JobStatus is not marked as abandoned when the
     * JobServiceContext receives a MSG_HANDLE_ABANDONED_JOB message and the
     * JobServiceContext is not running a job
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    public void testJobServiceContext_HandleAbandonedJob_notRunningJob() {
        final int jobId = ABANDONED_JOB_ID;
        mJobServiceContext.setRunningJobLockedForTest(null);
        mJobServiceContext.setRunningCallbackLockedForTest(mMockJobCallback);

        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);
        verify(mMockJobStatus, never()).setAbandoned(true);

        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        doReturn(jobId).when(mMockJobStatus).getJobId();

        mJobServiceContext.mVerb = JobServiceContext.VERB_BINDING;
        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);
        verify(mMockJobStatus, never()).setAbandoned(true);

        mJobServiceContext.mVerb = JobServiceContext.VERB_STARTING;
        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);
        verify(mMockJobStatus, never()).setAbandoned(true);

        mJobServiceContext.mVerb = JobServiceContext.VERB_STOPPING;
        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);
        verify(mMockJobStatus, never()).setAbandoned(true);

        mJobServiceContext.mVerb = JobServiceContext.VERB_FINISHED;
        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);
        verify(mMockJobStatus, never()).setAbandoned(true);
    }

    /**
     * Test that the JobStatus is not marked as abandoned when the
     * JobServiceContext receives a MSG_HANDLE_ABANDONED_JOB message and the
     * JobServiceContext is running a job with a different jobId
     */
    @Test
    @EnableFlags(FLAG_HANDLE_ABANDONED_JOBS)
    public void testJobServiceContext_HandleAbandonedJob_differentJobId() {
        final int jobId = ABANDONED_JOB_ID;
        final int differentJobId = TEST_JOB_ID;
        mJobServiceContext.setRunningJobLockedForTest(mMockJobStatus);
        mJobServiceContext.setRunningCallbackLockedForTest(mMockJobCallback);
        doReturn(differentJobId).when(mMockJobStatus).getJobId();

        mJobServiceContext.doHandleAbandonedJob(mMockJobCallback, jobId);

        verify(mMockJobStatus, never()).setAbandoned(true);
    }

    @Test
    public void testPerfettoTracing_Execute() throws Exception {
        when(mMockPerfettoTracer.isTraceEnabled()).thenReturn(true);
        final int jobId = TEST_JOB_ID;
        final JobInfo job = createJobInfo(jobId, mComponentName).build();
        final JobStatus jobStatus =
                JobStatus.createFromJobInfo(job, mSourceUid, SOURCE_PACKAGE, 0, TAG, null);
        final long loggingId = jobStatus.getLoggingJobId();
        long jobStartLatencyMs = 1000L;

        doReturn(jobStatus.enqueueTime + jobStartLatencyMs)
                .when(mJobServiceContext)
                .getExecutionStartTimeElapsed();
        mJobServiceContext.executeRunnableJob(jobStatus, JobConcurrencyManager.WORK_TYPE_NONE);

        // Verify execute trace.
        verify(mMockPerfettoTracer).startEvent(eq(jobStatus.getBatteryName()));
        verify(mMockPerfettoTracer).addField(eq((long) JOB_ID), eq(loggingId));
        verify(mMockPerfettoTracer)
                .addField(eq((long) SOURCE_UID), eq((long) mSourceUid));
        verify(mMockPerfettoTracer)
                .addField(eq((long) PROXY_UID), eq((long) mSourceUid));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) STATE),
                        eq((long) FrameworkStatsLog.SCHEDULED_JOB_STATE_CHANGED__STATE__STARTED));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) STANDBY_BUCKET),
                        eq((long) jobStatus.getStandbyBucket()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) REQUESTED_PRIORITY),
                        eq((long) job.getPriority()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) EFFECTIVE_PRIORITY),
                        eq((long) jobStatus.getEffectivePriority()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_PREVIOUS_ATTEMPTS),
                        eq((long) jobStatus.getNumPreviousAttempts()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) DEADLINE_MS),
                        eq(job.getMaxExecutionDelayMillis()));
        verify(mMockPerfettoTracer)
                .addField(eq((long) DELAY_MS), eq(job.getMinLatencyMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) JOB_START_LATENCY_MS),
                        eq(jobStartLatencyMs));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_UNCOMPLETED_WORK_ITEMS),
                        eq((long) jobStatus.getWorkCount()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PROC_STATE),
                        eq((long) ActivityManager.processStateAmToProto(
                                        ActivityManager.PROCESS_STATE_TOP)));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PERIODIC_JOB_INTERVAL_MS),
                        eq(job.getIntervalMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PERIODIC_JOB_FLEX_INTERVAL_MS),
                        eq(job.getFlexMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_RESCHEDULES_DUE_TO_ABANDONMENT),
                        eq((long) jobStatus.getNumAbandonedFailures()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) BACK_OFF_POLICY_TYPE),
                        eq((long) job.getBackoffPolicy() + 1L));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) INTERNAL_STOP_REASON),
                        eq((long) JobProtoEnums.INTERNAL_STOP_REASON_UNKNOWN));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PUBLIC_STOP_REASON),
                        eq((long) JobProtoEnums.STOP_REASON_UNDEFINED));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) JOB_STATE_FLAGS),
                        eq(JobStatus.packStatesToBits(jobStatus)));
        verify(mMockPerfettoTracer).emit();
    }

    @Test
    public void testPerfettoTracing_Cleanup() throws Exception {
        when(mMockPerfettoTracer.isTraceEnabled()).thenReturn(true);
        final int jobId = TEST_JOB_ID;
        final JobInfo job = createJobInfo(jobId, mComponentName).build();
        final JobStatus jobStatus =
                JobStatus.createFromJobInfo(job, mSourceUid, SOURCE_PACKAGE, 0, TAG, null);
        final long loggingId = jobStatus.getLoggingJobId();
        long jobStartLatencyMs = 1000L;

        doReturn(jobStatus.enqueueTime + jobStartLatencyMs)
                .when(mJobServiceContext)
                .getExecutionStartTimeElapsed();
        mJobServiceContext.executeRunnableJob(jobStatus, JobConcurrencyManager.WORK_TYPE_NONE);
        // Clear invocations from executeRunnableJob so we only verify the new calls in cleanup.
        Mockito.clearInvocations(mMockPerfettoTracer);
        final IJobService.Stub mockIJobService = mock(IJobService.Stub.class);
        final IBinder mockBinder = mock(IBinder.class);
        doReturn(mockIJobService).when(mockBinder).queryLocalInterface(anyString());
        mJobServiceContext.onServiceConnected(new ComponentName("foo", "bar"), mockBinder);
        ArgumentCaptor<JobParameters> jobParametersCaptor =
                ArgumentCaptor.forClass(JobParameters.class);
        verify(mockIJobService).startJob(jobParametersCaptor.capture());
        final JobCallback cb = (JobCallback) jobParametersCaptor.getValue().getCallback();
        mJobServiceContext.mVerb = JobServiceContext.VERB_EXECUTING;
        mJobServiceContext.doJobFinished(cb, jobId, false);

        verify(mMockPerfettoTracer).startEvent(eq(jobStatus.getBatteryName()));
        verify(mMockPerfettoTracer).addField(eq((long) JOB_ID), eq(loggingId));
        verify(mMockPerfettoTracer)
                .addField(eq((long) SOURCE_UID), eq((long) mSourceUid));
        verify(mMockPerfettoTracer)
                .addField(eq((long) PROXY_UID), eq((long) mSourceUid));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) STATE),
                        eq((long) FrameworkStatsLog.SCHEDULED_JOB_STATE_CHANGED__STATE__FINISHED));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) STANDBY_BUCKET),
                        eq((long) jobStatus.getStandbyBucket()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) REQUESTED_PRIORITY),
                        eq((long) job.getPriority()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) EFFECTIVE_PRIORITY),
                        eq((long) jobStatus.getEffectivePriority()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_PREVIOUS_ATTEMPTS),
                        eq((long) jobStatus.getNumPreviousAttempts()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) DEADLINE_MS),
                        eq(job.getMaxExecutionDelayMillis()));
        verify(mMockPerfettoTracer)
                .addField(eq((long) DELAY_MS), eq(job.getMinLatencyMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) JOB_START_LATENCY_MS),
                        eq(jobStartLatencyMs));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_UNCOMPLETED_WORK_ITEMS),
                        eq((long) jobStatus.getWorkCount()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PROC_STATE),
                        eq((long) ActivityManager.processStateAmToProto(
                                        ActivityManager.PROCESS_STATE_TOP)));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PERIODIC_JOB_INTERVAL_MS),
                        eq((long) job.getIntervalMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PERIODIC_JOB_FLEX_INTERVAL_MS),
                        eq(job.getFlexMillis()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) NUM_RESCHEDULES_DUE_TO_ABANDONMENT),
                        eq((long) jobStatus.getNumAbandonedFailures()));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) BACK_OFF_POLICY_TYPE),
                        eq((long) job.getBackoffPolicy() + 1L));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) INTERNAL_STOP_REASON),
                        eq((long) JobProtoEnums.INTERNAL_STOP_REASON_SUCCESSFUL_FINISH));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) PUBLIC_STOP_REASON),
                        eq((long) JobProtoEnums.STOP_REASON_UNDEFINED));
        verify(mMockPerfettoTracer)
                .addField(
                        eq((long) JOB_STATE_FLAGS),
                        eq(JobStatus.packStatesToBits(jobStatus)));
        verify(mMockPerfettoTracer).emit();
    }

    @Test
    @EnableFlags(FLAG_INCLUDE_JOB_NAME_IN_ANR_MESSAGE)
    @EnableCompatChanges({JobServiceContext.ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES})
    public void testOnSlowAppResponseLocked_anrMessage_flagEnabled() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_STOPPING;
        final JobInfo job = createJobInfo(TEST_JOB_ID, mComponentName).build();
        final JobStatus jobStatus =
                JobStatus.createFromJobInfo(job, mSourceUid, SOURCE_PACKAGE, 0, TAG, null);
        jobStatus.serviceProcessName = "some.process";
        mJobServiceContext.setRunningJobLockedForTest(jobStatus);

        mJobServiceContext.handleOpTimeoutLocked();

        final ArgumentCaptor<TimeoutRecord> timeoutRecordCaptor =
                ArgumentCaptor.forClass(TimeoutRecord.class);
        verify(mMockActivityManagerInternal).appNotResponding(
                eq(jobStatus.serviceProcessName), eq(jobStatus.getUid()),
                timeoutRecordCaptor.capture());
        assertThat(timeoutRecordCaptor.getValue().mReason).contains(
                jobStatus.getBatteryName());
    }

    @Test
    @DisableFlags(FLAG_INCLUDE_JOB_NAME_IN_ANR_MESSAGE)
    @EnableCompatChanges({JobServiceContext.ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES})
    public void testOnSlowAppResponseLocked_anrMessage_flagDisabled() {
        mJobServiceContext.mVerb = JobServiceContext.VERB_STOPPING;
        final JobInfo job = createJobInfo(TEST_JOB_ID, mComponentName).build();
        final JobStatus jobStatus =
                JobStatus.createFromJobInfo(job, mSourceUid, SOURCE_PACKAGE, 0, TAG, null);
        jobStatus.serviceProcessName = "some.process";
        mJobServiceContext.setRunningJobLockedForTest(jobStatus);

        mJobServiceContext.handleOpTimeoutLocked();

        final ArgumentCaptor<TimeoutRecord> timeoutRecordCaptor =
                ArgumentCaptor.forClass(TimeoutRecord.class);
        verify(mMockActivityManagerInternal).appNotResponding(
                eq(jobStatus.serviceProcessName), eq(jobStatus.getUid()),
                timeoutRecordCaptor.capture());
        assertThat(timeoutRecordCaptor.getValue().mReason).doesNotContain(
                jobStatus.getBatteryName());
    }

    private static JobInfo.Builder createJobInfo(int jobId, ComponentName componentName) {
        return new JobInfo.Builder(jobId, componentName);
    }
}