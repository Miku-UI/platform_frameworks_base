/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.job.controllers;

import static android.app.usage.UsageStatsManager.REASON_MAIN_DEFAULT;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_SYSTEM;
import static android.app.usage.UsageStatsManager.REASON_MAIN_PREDICTED;
import static android.app.usage.UsageStatsManager.REASON_MAIN_TIMEOUT;
import static android.app.usage.UsageStatsManager.REASON_MAIN_USAGE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.job.JobSchedulerService.ACTIVE_INDEX;
import static com.android.server.job.JobSchedulerService.EXEMPTED_INDEX;
import static com.android.server.job.JobSchedulerService.FREQUENT_INDEX;
import static com.android.server.job.JobSchedulerService.NEVER_INDEX;
import static com.android.server.job.JobSchedulerService.RARE_INDEX;
import static com.android.server.job.JobSchedulerService.RESTRICTED_INDEX;
import static com.android.server.job.JobSchedulerService.WORKING_INDEX;
import static com.android.server.job.JobSchedulerService.sElapsedRealtimeClock;
import static com.android.server.job.JobSchedulerService.standbyBucketToBucketIndex;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_BACKGROUND_NOT_RESTRICTED;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_BATTERY_NOT_LOW;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_CHARGING;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_CONNECTIVITY;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_CONTENT_TRIGGER;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_DEADLINE;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_DEVICE_NOT_DOZING;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_FLEXIBLE;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_IDLE;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_STORAGE_NOT_LOW;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_TIMING_DELAY;
import static com.android.server.job.controllers.JobStatus.CONSTRAINT_WITHIN_QUOTA;
import static com.android.server.job.controllers.JobStatus.NO_EARLIEST_RUNTIME;
import static com.android.server.job.controllers.JobStatus.NO_LATEST_RUNTIME;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.pm.PackageManagerInternal;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.MediaStore;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.IntPair;
import com.android.server.LocalServices;
import com.android.server.job.Flags;
import com.android.server.job.JobSchedulerInternal;
import com.android.server.job.JobSchedulerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class JobStatusTest {
    private static final double DELTA = 0.00001;
    private static final String TEST_PACKAGE = "job.test.package";
    private static final ComponentName TEST_JOB_COMPONENT = new ComponentName(TEST_PACKAGE, "test");
    private static final String SOURCE_PACKAGE = "com.android.frameworks.mockingservicestests";
    private static final int SOURCE_USER_ID = 0;

    private static final Uri IMAGES_MEDIA_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private static final Uri VIDEO_MEDIA_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

    private static final String TEST_TRACE_TAG = "test_trace_tag";
    private static final String TEST_DEBUG_TAG1 = "test_debug_tag1";
    private static final String TEST_DEBUG_TAG2 = "test_debug_tag2";

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private JobSchedulerInternal mJobSchedulerInternal;
    @Mock
    private UsageStatsManagerInternal mUsageStatsManagerInternal;
    private MockitoSession mMockingSession;

    @Before
    public void setUp() throws Exception {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .mockStatic(LocalServices.class)
                .startMocking();
        doReturn(mJobSchedulerInternal)
                .when(() -> LocalServices.getService(JobSchedulerInternal.class));
        doReturn(mock(PackageManagerInternal.class))
                .when(() -> LocalServices.getService(PackageManagerInternal.class));
        doReturn(mUsageStatsManagerInternal)
                .when(() -> LocalServices.getService(UsageStatsManagerInternal.class));
        JobSchedulerService.sUsageStatsManagerInternal = mUsageStatsManagerInternal;

        // Freeze the clocks at this moment in time
        JobSchedulerService.sSystemClock =
                Clock.fixed(Clock.systemUTC().instant(), ZoneOffset.UTC);
        JobSchedulerService.sUptimeMillisClock =
                Clock.fixed(SystemClock.uptimeClock().instant(), ZoneOffset.UTC);
        sElapsedRealtimeClock =
                Clock.fixed(SystemClock.elapsedRealtimeClock().instant(), ZoneOffset.UTC);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    private static void assertEffectiveBucketForMediaExemption(JobStatus jobStatus,
            boolean exemptionGranted) {
        assertEffectiveBucketForMediaExemption(jobStatus, exemptionGranted,
                /* allowRestrictedWithResonTimeout= */ true);
    }

    private static void assertEffectiveBucketForMediaExemption(JobStatus jobStatus,
            boolean exemptionGranted, boolean allowRestrictedWithResonTimeout) {
        final ArrayMap<Pair<Integer, Integer>, Integer> effectiveBucket = new ArrayMap<>();
        effectiveBucket.put(new Pair<>(ACTIVE_INDEX, REASON_MAIN_DEFAULT), ACTIVE_INDEX);
        effectiveBucket.put(new Pair<>(WORKING_INDEX, REASON_MAIN_USAGE), WORKING_INDEX);
        effectiveBucket.put(new Pair<>(FREQUENT_INDEX, REASON_MAIN_USAGE),
                exemptionGranted ? WORKING_INDEX : FREQUENT_INDEX);
        effectiveBucket.put(new Pair<>(RARE_INDEX, REASON_MAIN_TIMEOUT),
                exemptionGranted ? WORKING_INDEX : RARE_INDEX);
        effectiveBucket.put(new Pair<>(NEVER_INDEX, REASON_MAIN_DEFAULT), NEVER_INDEX);
        effectiveBucket.put(new Pair<>(RESTRICTED_INDEX, REASON_MAIN_TIMEOUT),
                (exemptionGranted && allowRestrictedWithResonTimeout)
                        ? WORKING_INDEX : RESTRICTED_INDEX);
        effectiveBucket.put(new Pair<>(RESTRICTED_INDEX, REASON_MAIN_FORCED_BY_SYSTEM),
                RESTRICTED_INDEX);
        for (int i = 0; i < effectiveBucket.size(); i++) {
            jobStatus.setStandbyBucket(effectiveBucket.keyAt(i).first,
                    effectiveBucket.keyAt(i).second);
            assertEquals(effectiveBucket.valueAt(i).intValue(),
                    jobStatus.getEffectiveStandbyBucket());
        }
    }

    @Test
    public void testApplyBasicPiiFilters_email() {
        assertEquals("[EMAIL]", JobStatus.applyBasicPiiFilters("test@email.com"));
        assertEquals("[EMAIL]", JobStatus.applyBasicPiiFilters("test+plus@email.com"));
        assertEquals("[EMAIL]", JobStatus.applyBasicPiiFilters("t.e_st+plus-minus@email.com"));

        assertEquals("prefix:[EMAIL]", JobStatus.applyBasicPiiFilters("prefix:test@email.com"));

        assertEquals("not-an-email", JobStatus.applyBasicPiiFilters("not-an-email"));
    }

    @Test
    public void testApplyBasicPiiFilters_mixture() {
        assertEquals("[PHONE]:[EMAIL]",
                JobStatus.applyBasicPiiFilters("123-456-7890:test+plus@email.com"));
        assertEquals("prefix:[PHONE]:[EMAIL]",
                JobStatus.applyBasicPiiFilters("prefix:123-456-7890:test+plus@email.com"));
    }

    @Test
    public void testApplyBasicPiiFilters_phone() {
        assertEquals("[PHONE]", JobStatus.applyBasicPiiFilters("123-456-7890"));
        assertEquals("[PHONE]", JobStatus.applyBasicPiiFilters("+1-234-567-8900"));

        assertEquals("prefix:[PHONE]", JobStatus.applyBasicPiiFilters("prefix:123-456-7890"));

        assertEquals("not-a-phone-number", JobStatus.applyBasicPiiFilters("not-a-phone-number"));
    }

    @Test
    public void testCanRunInBatterySaver_regular() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus job = createJobStatus(jobInfo);
        assertFalse(job.canRunInBatterySaver());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInBatterySaver());
    }

    @Test
    public void testCanRunInBatterySaver_expedited() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setExpedited(true)
                        .build();
        JobStatus job = createJobStatus(jobInfo);
        markExpeditedQuotaApproved(job, true);
        assertTrue(job.canRunInBatterySaver());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInBatterySaver());

        // Reset the job
        job = createJobStatus(jobInfo);
        markExpeditedQuotaApproved(job, true);
        spyOn(job);
        when(job.shouldTreatAsExpeditedJob()).thenReturn(false);
        job.startedAsExpeditedJob = true;
        assertTrue(job.canRunInBatterySaver());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInBatterySaver());
    }

    @Test
    public void testCanRunInBatterySaver_userInitiated() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        JobStatus job = createJobStatus(jobInfo);
        assertTrue(job.canRunInBatterySaver());
        // User-initiated privilege should trump bs & doze requirement.
        job.disallowRunInBatterySaverAndDoze();
        assertTrue(job.canRunInBatterySaver());
    }

    @Test
    public void testCanRunInDoze_regular() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus job = createJobStatus(jobInfo);
        assertFalse(job.canRunInDoze());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInDoze());
    }

    @Test
    public void testCanRunInDoze_expedited() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setExpedited(true)
                        .build();
        JobStatus job = createJobStatus(jobInfo);
        markExpeditedQuotaApproved(job, true);
        assertTrue(job.canRunInDoze());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInDoze());

        // Reset the job
        job = createJobStatus(jobInfo);
        markExpeditedQuotaApproved(job, true);
        spyOn(job);
        when(job.shouldTreatAsExpeditedJob()).thenReturn(false);
        job.startedAsExpeditedJob = true;
        assertTrue(job.canRunInDoze());
        job.disallowRunInBatterySaverAndDoze();
        assertFalse(job.canRunInDoze());
    }

    @Test
    public void testCanRunInDoze_userInitiated() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        JobStatus job = createJobStatus(jobInfo);
        assertTrue(job.canRunInDoze());
        // User-initiated privilege should trump bs & doze requirement.
        job.disallowRunInBatterySaverAndDoze();
        assertTrue(job.canRunInDoze());
    }

    @Test
    public void testFlexibleConstraintCounts() {
        JobStatus js = createJobStatus(new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(false)
                .build());

        js.setNumAppliedFlexibleConstraints(3);
        js.setNumDroppedFlexibleConstraints(2);
        assertEquals(3, js.getNumAppliedFlexibleConstraints());
        assertEquals(2, js.getNumDroppedFlexibleConstraints());
        assertEquals(1, js.getNumRequiredFlexibleConstraints());
    }

    @Test
    public void testGetFilteredDebugTags() {
        final JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .addDebugTag("test@email.com")
                .addDebugTag("123-456-7890")
                .addDebugTag("random")
                .build();
        JobStatus job = createJobStatus(jobInfo);
        String[] expected = new String[]{"[EMAIL]", "[PHONE]", "random"};
        String[] result = job.getFilteredDebugTags();
        Arrays.sort(expected);
        Arrays.sort(result);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testGetFilteredTraceTag() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setTraceTag("test@email.com")
                .build();
        JobStatus job = createJobStatus(jobInfo);
        assertEquals("[EMAIL]", job.getFilteredTraceTag());

        jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setTraceTag("123-456-7890")
                .build();
        job = createJobStatus(jobInfo);
        assertEquals("[PHONE]", job.getFilteredTraceTag());

        jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setTraceTag("random")
                .build();
        job = createJobStatus(jobInfo);
        assertEquals("random", job.getFilteredTraceTag());
    }

    @Test
    public void testIsUserVisibleJob() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(false)
                .build();
        JobStatus job = createJobStatus(jobInfo);

        assertFalse(job.isUserVisibleJob());

        // User-initiated jobs are always user-visible unless they've been demoted.
        jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        job = createJobStatus(jobInfo);

        assertTrue(job.isUserVisibleJob());

        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertFalse(job.isUserVisibleJob());

        job.startedAsUserInitiatedJob = true;
        assertTrue(job.isUserVisibleJob());

        job.startedAsUserInitiatedJob = false;
        assertFalse(job.isUserVisibleJob());
    }

    @Test
    public void testMediaBackupExemption_lateConstraint() {
        final JobInfo.Builder triggerContentJobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .setOverrideDeadline(HOUR_IN_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(triggerContentJobBuilder.build()), false);

        mSetFlagsRule.enableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY);
        // High priority job from the cloud media package should be exempted.
        triggerContentJobBuilder.setPriority(JobInfo.PRIORITY_HIGH);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(triggerContentJobBuilder.build()), true);
    }

    @Test
    public void testMediaBackupExemption_noConnectivityConstraint() {
        final JobInfo.Builder triggerContentJobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0));

        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(triggerContentJobBuilder.build()), false);

        mSetFlagsRule.enableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY);
        triggerContentJobBuilder.setPriority(JobInfo.PRIORITY_HIGH);
        // High priority job from the cloud media package should be exempted.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(triggerContentJobBuilder.setPriority(JobInfo.PRIORITY_HIGH)
                        .build()), true);
    }

    @Test
    public void testMediaBackupExemption_noContentTriggerConstraint() {
        final JobInfo.Builder networkJobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);

        assertEffectiveBucketForMediaExemption(createJobStatus(networkJobBuilder.build()), false);
        mSetFlagsRule.enableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY);
        networkJobBuilder.setPriority(JobInfo.PRIORITY_HIGH);
        // High priority job from the cloud media package should be exempted.
        assertEffectiveBucketForMediaExemption(createJobStatus(networkJobBuilder.build()), true);
    }

    @Test
    @DisableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    public void testMediaBackupExemption_wrongSourcePackage_policyUpdateDisabled() {
        final JobInfo networkContentJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0)))
                .thenReturn("not.test.package");
        assertEffectiveBucketForMediaExemption(createJobStatus(networkContentJob), false);
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    public void testMediaBackupExemption_wrongSourcePackage() {
        final JobInfo networkContentJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .build();
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0)))
                .thenReturn("not.test.package");
        assertEffectiveBucketForMediaExemption(createJobStatus(networkContentJob), false);
    }

    @Test
    public void testMediaBackupExemption_nonEligibleUri() {
        final Uri nonEligibleUri = MediaStore.AUTHORITY_URI.buildUpon()
                .appendPath("any_path").build();
        final JobInfo.Builder networkContentJobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(nonEligibleUri, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);

        assertEffectiveBucketForMediaExemption(
                createJobStatus(networkContentJobBuilder.build()), false);
        mSetFlagsRule.enableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY);
        networkContentJobBuilder.setPriority(JobInfo.PRIORITY_HIGH);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(networkContentJobBuilder.build()), true);
    }

    @Test
    @DisableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    public void testMediaBackupExemption_lowPriorityJobs() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0)))
                .thenReturn(TEST_PACKAGE);
        final JobInfo.Builder jobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_LOW).build()), false);
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_MIN).build()), false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET)
    @EnableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    public void testMediaBackupExemption_priority_restrictedBucketDisabled_policyUpdateEnabled() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        final JobInfo.Builder jobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT);

        // DEFAULT job priority.
        assertEffectiveBucketForMediaExemption(createJobStatus(jobBuilder.build()), false, false);
        // LOW priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_LOW).build()), false,
                false);
        // MIN priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_MIN).build()), false,
                false);
        // HIGH priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_HIGH).build()), true,
                false);
    }

    @Test
    @EnableFlags({Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET,
            Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY})
    public void testMediaBackupExemption_priority() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0)))
                .thenReturn(TEST_PACKAGE);
        final JobInfo.Builder jobBuilder = new JobInfo.Builder(42, TEST_JOB_COMPONENT);

        // DEFAULT job priority.
        assertEffectiveBucketForMediaExemption(createJobStatus(jobBuilder.build()), false);
        // LOW priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_LOW).build()), false);
        // MIN priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_MIN).build()), false);
        // HIGH priority.
        assertEffectiveBucketForMediaExemption(
                createJobStatus(jobBuilder.setPriority(JobInfo.PRIORITY_HIGH).build()), true);
    }

    @DisableFlags({Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET,
            Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY})
    @Test
    public void testMediaBackupExemptionGranted_restrictedBucketDisabled_policyUpdateDisabled() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        final JobInfo imageUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(imageUriJob), true, false);

        final JobInfo videoUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(VIDEO_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(videoUriJob), true, false);

        final JobInfo bothUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(VIDEO_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(bothUriJob), true, false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET)
    @EnableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    public void testMediaBackupExemptionGranted_restrictedBucketDisabled_policyUpdateEnabled() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        final JobInfo job = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(job), true, false);
    }

    @EnableFlags(Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET)
    @DisableFlags(Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY)
    @Test
    public void testMediaBackupExemptionGranted_restrictedBucketEnabled_policyUpdateDisabled() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        final JobInfo imageUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(imageUriJob), true);

        final JobInfo videoUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(VIDEO_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(videoUriJob), true);

        final JobInfo bothUriJob = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(IMAGES_MEDIA_URI, 0))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(VIDEO_MEDIA_URI, 0))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(bothUriJob), true);
    }

    @Test
    @EnableFlags({Flags.FLAG_ALLOW_CMP_EXEMPTION_FOR_RESTRICTED_BUCKET,
            Flags.FLAG_UPDATE_MEDIA_BACKUP_EXEMPTION_POLICY})
    public void testMediaBackupExemptionGranted_policyUpdateEnabled() {
        when(mJobSchedulerInternal.getCloudMediaProviderPackage(eq(0))).thenReturn(TEST_PACKAGE);
        final JobInfo job = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .build();
        assertEffectiveBucketForMediaExemption(createJobStatus(job), true);
    }

    @Test
    public void testFraction() throws Exception {
        final long now = sElapsedRealtimeClock.millis();

        assertEquals(1, createJobStatus(0, Long.MAX_VALUE).getFractionRunTime(), DELTA);

        assertEquals(1, createJobStatus(0, now - 1000).getFractionRunTime(), DELTA);
        assertEquals(0, createJobStatus(0, now + 1000).getFractionRunTime(), DELTA);

        assertEquals(1, createJobStatus(now - 1000, Long.MAX_VALUE).getFractionRunTime(), DELTA);
        assertEquals(0, createJobStatus(now + 1000, Long.MAX_VALUE).getFractionRunTime(), DELTA);

        assertEquals(0, createJobStatus(now, now + 2000).getFractionRunTime(), DELTA);
        assertEquals(0.25, createJobStatus(now - 500, now + 1500).getFractionRunTime(), DELTA);
        assertEquals(0.5, createJobStatus(now - 1000, now + 1000).getFractionRunTime(), DELTA);
        assertEquals(0.75, createJobStatus(now - 1500, now + 500).getFractionRunTime(), DELTA);
        assertEquals(1, createJobStatus(now - 2000, now).getFractionRunTime(), DELTA);
    }

    @Test
    public void testGetEffectivePriority_Expedited() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setExpedited(true)
                        .build();
        JobStatus job = createJobStatus(jobInfo);

        // Less than 2 failures, priority shouldn't be affected.
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());
        int numFailures = 1;
        int numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());

        // 2+ failures, priority should be lowered as much as possible.
        numFailures = 2;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
        numFailures = 5;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
        numFailures = 8;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());

        // System stops shouldn't factor in the downgrade.
        numSystemStops = 10;
        numFailures = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());

        // Less than 2 failures, but job is downgraded.
        numFailures = 1;
        numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
    }

    @Test
    public void testGetEffectivePriority_Regular_High() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .build();
        JobStatus job = createJobStatus(jobInfo);

        // Less than 2 failures, priority shouldn't be affected.
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
        int numFailures = 1;
        int numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());

        // Failures in [2,4), priority should be lowered slightly.
        numFailures = 2;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_DEFAULT, job.getEffectivePriority());
        numFailures = 3;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_DEFAULT, job.getEffectivePriority());

        // Failures in [4,6), priority should be lowered more.
        numFailures = 4;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
        numFailures = 5;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());

        // 6+ failures, priority should be lowered as much as possible.
        numFailures = 6;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MIN, job.getEffectivePriority());
        numFailures = 12;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MIN, job.getEffectivePriority());

        // System stops shouldn't factor in the downgrade.
        numSystemStops = 10;
        numFailures = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
    }

    /**
     * Test that LOW priority jobs don't have their priority lowered as quickly as higher priority
     * jobs.
     */
    @Test
    public void testGetEffectivePriority_Regular_Low() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .build();
        JobStatus job = createJobStatus(jobInfo);

        // Less than 6 failures, priority shouldn't be affected.
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
        int numFailures = 1;
        int numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
        numFailures = 4;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
        numFailures = 5;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());

        // 6+ failures, priority should be lowered as much as possible.
        numFailures = 6;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MIN, job.getEffectivePriority());
        numFailures = 12;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MIN, job.getEffectivePriority());

        // System stops shouldn't factor in the downgrade.
        numSystemStops = 10;
        numFailures = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
    }

    @Test
    public void testGetEffectivePriority_UserInitiated() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        JobStatus job = createJobStatus(jobInfo);

        // Less than 2 failures, priority shouldn't be affected.
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());
        int numFailures = 1;
        int numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());

        // 2+ failures, priority shouldn't be affected while job is still a UI job
        numFailures = 2;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());
        numFailures = 5;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());
        numFailures = 8;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());

        // System stops shouldn't factor in the downgrade.
        numSystemStops = 10;
        numFailures = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MAX, job.getEffectivePriority());

        // Job can no long run as user-initiated. Downgrades should be effective.
        // Priority can't be max.
        job = createJobStatus(jobInfo);
        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertFalse(job.shouldTreatAsUserInitiatedJob());

        // Less than 2 failures.
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
        numFailures = 1;
        numSystemStops = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());

        // 2+ failures, priority should start getting lower
        numFailures = 2;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_DEFAULT, job.getEffectivePriority());
        numFailures = 5;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_LOW, job.getEffectivePriority());
        numFailures = 8;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_MIN, job.getEffectivePriority());

        // System stops shouldn't factor in the downgrade.
        numSystemStops = 10;
        numFailures = 0;
        job = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME, numFailures,
                0, numSystemStops, 0, 0, 0);
        assertEquals(JobInfo.PRIORITY_HIGH, job.getEffectivePriority());
    }

    @Test
    public void testGetEffectiveStandbyBucket_buggyApp() {
        when(mJobSchedulerInternal.isAppConsideredBuggy(
                anyInt(), anyString(), anyInt(), anyString()))
                .thenReturn(true);

        final JobInfo jobInfo = new JobInfo.Builder(1234, TEST_JOB_COMPONENT).build();
        JobStatus job = createJobStatus(jobInfo);

        // Exempt apps be exempting.
        job.setStandbyBucket(EXEMPTED_INDEX, REASON_MAIN_DEFAULT);
        assertEquals(EXEMPTED_INDEX, job.getEffectiveStandbyBucket());

        // Actual bucket is higher than the buggy cap, so the cap comes into effect.
        job.setStandbyBucket(ACTIVE_INDEX, REASON_MAIN_USAGE);
        assertEquals(WORKING_INDEX, job.getEffectiveStandbyBucket());

        // Buckets at the cap or below shouldn't be affected.
        job.setStandbyBucket(WORKING_INDEX, REASON_MAIN_USAGE);
        assertEquals(WORKING_INDEX, job.getEffectiveStandbyBucket());

        job.setStandbyBucket(FREQUENT_INDEX, REASON_MAIN_PREDICTED);
        assertEquals(FREQUENT_INDEX, job.getEffectiveStandbyBucket());

        job.setStandbyBucket(RARE_INDEX, REASON_MAIN_TIMEOUT);
        assertEquals(RARE_INDEX, job.getEffectiveStandbyBucket());

        job.setStandbyBucket(RESTRICTED_INDEX, REASON_MAIN_TIMEOUT);
        assertEquals(RESTRICTED_INDEX, job.getEffectiveStandbyBucket());

        // Any other reason should not trigger the bucket elevation
        job.setStandbyBucket(RESTRICTED_INDEX, REASON_MAIN_FORCED_BY_SYSTEM);
        assertEquals(RESTRICTED_INDEX, job.getEffectiveStandbyBucket());

        job.setStandbyBucket(NEVER_INDEX, REASON_MAIN_DEFAULT);
        assertEquals(NEVER_INDEX, job.getEffectiveStandbyBucket());
    }

    @Test
    public void testModifyingInternalFlags() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setExpedited(true)
                        .build();
        JobStatus job = createJobStatus(jobInfo);

        assertEquals(0, job.getInternalFlags());

        // Add single flag
        job.addInternalFlags(JobStatus.INTERNAL_FLAG_HAS_FOREGROUND_EXEMPTION);
        assertEquals(JobStatus.INTERNAL_FLAG_HAS_FOREGROUND_EXEMPTION, job.getInternalFlags());

        // Add multiple flags
        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER
                | JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ);
        assertEquals(JobStatus.INTERNAL_FLAG_HAS_FOREGROUND_EXEMPTION
                        | JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER
                        | JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ,
                job.getInternalFlags());

        // Add flag that's already set
        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ);
        assertEquals(JobStatus.INTERNAL_FLAG_HAS_FOREGROUND_EXEMPTION
                        | JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER
                        | JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ,
                job.getInternalFlags());

        // Remove multiple
        job.removeInternalFlags(JobStatus.INTERNAL_FLAG_HAS_FOREGROUND_EXEMPTION
                | JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertEquals(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ, job.getInternalFlags());

        // Remove one that isn't set
        job.removeInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertEquals(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ, job.getInternalFlags());

        // Remove final flag.
        job.removeInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ);
        assertEquals(0, job.getInternalFlags());
    }

    @Test
    public void testShouldTreatAsUserInitiated() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(false)
                .build();
        JobStatus job = createJobStatus(jobInfo);

        assertFalse(job.shouldTreatAsUserInitiatedJob());

        jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        job = createJobStatus(jobInfo);

        assertTrue(job.shouldTreatAsUserInitiatedJob());
    }

    @Test
    public void testShouldTreatAsUserInitiated_userDemoted() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        JobStatus job = createJobStatus(jobInfo);

        assertTrue(job.shouldTreatAsUserInitiatedJob());

        JobStatus rescheduledJob = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME,
                0, 0, 0, 0, 0, 0);
        assertTrue(rescheduledJob.shouldTreatAsUserInitiatedJob());

        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertFalse(job.shouldTreatAsUserInitiatedJob());

        rescheduledJob = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME,
                0, 0, 0, 0, 0, 0);
        assertFalse(rescheduledJob.shouldTreatAsUserInitiatedJob());

        rescheduledJob.removeInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_USER);
        assertTrue(rescheduledJob.shouldTreatAsUserInitiatedJob());
    }

    @Test
    public void testShouldTreatAsUserInitiated_systemDemoted() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        JobStatus job = createJobStatus(jobInfo);

        assertTrue(job.shouldTreatAsUserInitiatedJob());

        JobStatus rescheduledJob = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME,
                0, 0, 0, 0, 0, 0);
        assertTrue(rescheduledJob.shouldTreatAsUserInitiatedJob());

        job.addInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ);
        assertFalse(job.shouldTreatAsUserInitiatedJob());

        rescheduledJob = new JobStatus(job, NO_EARLIEST_RUNTIME, NO_LATEST_RUNTIME,
                0, 0, 0, 0, 0, 0);
        assertFalse(rescheduledJob.shouldTreatAsUserInitiatedJob());

        rescheduledJob.removeInternalFlags(JobStatus.INTERNAL_FLAG_DEMOTED_BY_SYSTEM_UIJ);
        assertTrue(rescheduledJob.shouldTreatAsUserInitiatedJob());
    }

    /**
     * Test {@link JobStatus#wouldBeReadyWithConstraint} on explicit constraints that weren't
     * requested.
     */
    @Test
    public void testWouldBeReadyWithConstraint_NonRequestedConstraints() {
        // Job with no explicit constraints.
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());

        markImplicitConstraintsSatisfied(job, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        markImplicitConstraintsSatisfied(job, true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedCharging() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresCharging(true)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));

        markImplicitConstraintsSatisfied(job, false);
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedDeviceIdle() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresDeviceIdle(true)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setIdleConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        job.setIdleConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));

        markImplicitConstraintsSatisfied(job, false);
        job.setIdleConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        job.setIdleConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedBatteryNotLow() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresBatteryNotLow(true)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setBatteryNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        job.setBatteryNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));

        markImplicitConstraintsSatisfied(job, false);
        job.setBatteryNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        job.setBatteryNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedStorageNotLow() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresStorageNotLow(true)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setStorageNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        job.setStorageNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));

        markImplicitConstraintsSatisfied(job, false);
        job.setStorageNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        job.setStorageNotLowConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedTimingDelay() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setMinimumLatency(60_000)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setTimingDelayConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        job.setTimingDelayConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));

        markImplicitConstraintsSatisfied(job, false);
        job.setTimingDelayConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        job.setTimingDelayConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedOverrideDeadline() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setOverrideDeadline(HOUR_IN_MILLIS)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        markImplicitConstraintsSatisfied(job, false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedConnectivity() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));

        markImplicitConstraintsSatisfied(job, false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedContentTrigger() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        markImplicitConstraintsSatisfied(job, false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedMixture_NoDeadline() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, false);

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        // Still false because implicit constraints aren't satisfied.
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        markImplicitConstraintsSatisfied(job, true);

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        // Turn on constraints one at a time.
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        // With two of the 3 constraints satisfied (and implicit constraints also satisfied), only
        // the unsatisfied constraint should return true.
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setConnectivityConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
    }

    @Test
    public void testWouldBeReadyWithConstraint_RequestedMixture_WithDeadline() {
        final JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setRequiresCharging(true)
                        .setOverrideDeadline(HOUR_IN_MILLIS)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .build();
        final JobStatus job = createJobStatus(jobInfo);

        markImplicitConstraintsSatisfied(job, false);

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        // Still false because implicit constraints aren't satisfied.
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        markImplicitConstraintsSatisfied(job, true);

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        // Turn on constraints one at a time.
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        // Deadline should force isReady to be true, but isn't needed for the job to be
        // considered ready.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        // Since the deadline constraint is satisfied, none of the other explicit constraints are
        // needed.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        // With two of the 3 constraints satisfied (and implicit constraints also satisfied), only
        // the unsatisfied constraint should return true.
        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));

        job.setChargingConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setContentTriggerConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        job.setDeadlineConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        // Once implicit constraint are satisfied, deadline constraint should always return true.
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
    }

    @Test
    public void testWouldBeReadyWithConstraint_ImplicitDeviceNotDozing() {
        // Job with no explicit constraints.
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());

        markImplicitConstraintsSatisfied(job, false);
        job.setDeviceNotDozingConstraintSatisfied(sElapsedRealtimeClock.millis(), false, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEVICE_NOT_DOZING));
        job.setDeviceNotDozingConstraintSatisfied(sElapsedRealtimeClock.millis(), true, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEVICE_NOT_DOZING));

        markImplicitConstraintsSatisfied(job, true);
        job.setDeviceNotDozingConstraintSatisfied(sElapsedRealtimeClock.millis(), false, false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEVICE_NOT_DOZING));
        job.setDeviceNotDozingConstraintSatisfied(sElapsedRealtimeClock.millis(), true, false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEVICE_NOT_DOZING));
    }

    @Test
    public void testWouldBeReadyWithConstraint_ImplicitQuota() {
        // Job with no explicit constraints.
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());

        markImplicitConstraintsSatisfied(job, false);
        job.setQuotaConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_WITHIN_QUOTA));
        job.setQuotaConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_WITHIN_QUOTA));

        markImplicitConstraintsSatisfied(job, true);
        job.setQuotaConstraintSatisfied(sElapsedRealtimeClock.millis(), false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_WITHIN_QUOTA));
        job.setQuotaConstraintSatisfied(sElapsedRealtimeClock.millis(), true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_WITHIN_QUOTA));
    }

    @Test
    public void testWouldBeReadyWithConstraint_ImplicitBackgroundNotRestricted() {
        // Job with no explicit constraints.
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());

        markImplicitConstraintsSatisfied(job, false);
        job.setBackgroundNotRestrictedConstraintSatisfied(
                sElapsedRealtimeClock.millis(), false, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BACKGROUND_NOT_RESTRICTED));
        job.setBackgroundNotRestrictedConstraintSatisfied(
                sElapsedRealtimeClock.millis(), true, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BACKGROUND_NOT_RESTRICTED));

        markImplicitConstraintsSatisfied(job, true);
        job.setBackgroundNotRestrictedConstraintSatisfied(
                sElapsedRealtimeClock.millis(), false, false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BACKGROUND_NOT_RESTRICTED));
        job.setBackgroundNotRestrictedConstraintSatisfied(
                sElapsedRealtimeClock.millis(), true, false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BACKGROUND_NOT_RESTRICTED));
    }

    @Test
    public void testWouldBeReadyWithConstraint_FlexibilityDoesNotAffectReadiness() {
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());

        markImplicitConstraintsSatisfied(job, false);
        job.setFlexibilityConstraintSatisfied(0, false);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_FLEXIBLE));

        markImplicitConstraintsSatisfied(job, true);
        job.setFlexibilityConstraintSatisfied(0, false);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_FLEXIBLE));

        markImplicitConstraintsSatisfied(job, false);
        job.setFlexibilityConstraintSatisfied(0, true);
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertFalse(job.wouldBeReadyWithConstraint(CONSTRAINT_FLEXIBLE));

        markImplicitConstraintsSatisfied(job, true);
        job.setFlexibilityConstraintSatisfied(0, true);
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CHARGING));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_IDLE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_BATTERY_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_STORAGE_NOT_LOW));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_TIMING_DELAY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_DEADLINE));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONNECTIVITY));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_CONTENT_TRIGGER));
        assertTrue(job.wouldBeReadyWithConstraint(CONSTRAINT_FLEXIBLE));
    }

    @Test
    public void testReadinessStatusWithConstraint_FlexibilityConstraint() {
        final JobStatus job = createJobStatus(
                new JobInfo.Builder(101, new ComponentName("foo", "bar")).build());
        job.setConstraintSatisfied(CONSTRAINT_FLEXIBLE, sElapsedRealtimeClock.millis(), false);
        markImplicitConstraintsSatisfied(job, true);
        assertTrue(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, true));
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, false));

        markImplicitConstraintsSatisfied(job, false);
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, true));
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, false));

        job.setConstraintSatisfied(CONSTRAINT_FLEXIBLE, sElapsedRealtimeClock.millis(), true);
        markImplicitConstraintsSatisfied(job, true);
        assertTrue(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, true));
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, false));

        markImplicitConstraintsSatisfied(job, false);
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, true));
        assertFalse(job.readinessStatusWithConstraint(CONSTRAINT_FLEXIBLE, false));
    }

    @Test
    public void testJobName_NoTagNoNamespace() {
        final JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus jobStatus = createJobStatus(jobInfo, null, -1, null, null);
        assertEquals("foo/bar", jobStatus.getBatteryName());
    }

    @Test
    public void testJobName_NoTagWithNamespace() {
        final JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus jobStatus = createJobStatus(jobInfo, null, -1, "TestNamespace", null);
        assertEquals("@TestNamespace@foo/bar", jobStatus.getBatteryName());
    }

    @Test
    public void testJobName_WithTagNoNamespace() {
        final JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus jobStatus = createJobStatus(jobInfo, SOURCE_PACKAGE, 0, null, "TestTag");
        assertEquals("TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    public void testJobName_WithTagAndNamespace() {
        final JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar")).build();
        JobStatus jobStatus = createJobStatus(jobInfo, SOURCE_PACKAGE, 0,
                "TestNamespace", "TestTag");
        assertEquals("@TestNamespace@TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_NotTagNoNamespace_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("TestTraceTag")
                        .build();
        JobStatus jobStatus = createJobStatus(jobInfo, null, -1, null, null);
        assertEquals("#TestTraceTag#foo/bar", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_NoTagWithNamespace_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("TestTraceTag")
                        .build();
        JobStatus jobStatus = createJobStatus(jobInfo, null, -1, "TestNamespace", null);
        assertEquals("#TestTraceTag#@TestNamespace@foo/bar", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_WithTagNoNamespace_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("TestTraceTag")
                        .build();
        JobStatus jobStatus = createJobStatus(jobInfo, SOURCE_PACKAGE, 0, null, "TestTag");
        assertEquals("#TestTraceTag#TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_FilteredTraceTagEmail_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("test@email.com")
                        .build();
        JobStatus jobStatus = createJobStatus(jobInfo, SOURCE_PACKAGE, 0, null, "TestTag");
        assertEquals("#[EMAIL]#TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_FilteredTraceTagPhone_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("123-456-7890")
                        .build();
        JobStatus jobStatus = createJobStatus(jobInfo, SOURCE_PACKAGE, 0, null, "TestTag");
        assertEquals("#[PHONE]#TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    @EnableFlags({
        com.android.server.job.Flags.FLAG_INCLUDE_TRACE_TAG_IN_JOB_NAME,
        android.app.job.Flags.FLAG_JOB_DEBUG_INFO_APIS
    })
    public void testJobName_WithTagAndNamespace_IncludeTraceTagInJobNameEnabled() {
        JobInfo jobInfo =
                new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                        .setTraceTag("TestTraceTag")
                        .build();
        JobStatus jobStatus =
                createJobStatus(jobInfo, SOURCE_PACKAGE, 0, "TestNamespace", "TestTag");
        assertEquals("#TestTraceTag#@TestNamespace@TestTag:foo", jobStatus.getBatteryName());
    }

    @Test
    public void testCreateJobStatus_validateStandbyBucketAndReason() {
        doReturn(IntPair.of(STANDBY_BUCKET_FREQUENT, REASON_MAIN_PREDICTED))
                .when(mUsageStatsManagerInternal)
                .getAppStandbyBucketAndReason(eq(TEST_PACKAGE), anyInt(), anyLong());

        final JobInfo jobInfo = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .build();
        final JobStatus jobStatus = createJobStatus(jobInfo);

        assertEquals(standbyBucketToBucketIndex(STANDBY_BUCKET_FREQUENT),
                jobStatus.getStandbyBucket());
        assertEquals(REASON_MAIN_PREDICTED, jobStatus.getStandbyBucketReason());
    }

    @Test
    public void testCreateJobStatus_validateTags() {
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final JobInfo jobInfo = new JobInfo.Builder(42, TEST_JOB_COMPONENT)
                .setRequiredNetwork(networkRequest)
                .setTraceTag(TEST_TRACE_TAG)
                .addDebugTags(Set.of(TEST_DEBUG_TAG1, TEST_DEBUG_TAG2))
                .build();
        final JobStatus jobStatus = createJobStatus(jobInfo);
        assertEquals(TEST_TRACE_TAG, jobStatus.getJob().getTraceTag());
        assertEquals(Set.of(TEST_DEBUG_TAG1, TEST_DEBUG_TAG2), jobStatus.getJob().getDebugTags());
    }

    private void markExpeditedQuotaApproved(JobStatus job, boolean isApproved) {
        if (job.isRequestedExpeditedJob()) {
            job.setExpeditedJobQuotaApproved(sElapsedRealtimeClock.millis(), isApproved);
        }
    }

    private void markImplicitConstraintsSatisfied(JobStatus job, boolean isSatisfied) {
        job.setQuotaConstraintSatisfied(sElapsedRealtimeClock.millis(), isSatisfied);
        job.setDeviceNotDozingConstraintSatisfied(
                sElapsedRealtimeClock.millis(), isSatisfied, false);
        job.setBackgroundNotRestrictedConstraintSatisfied(
                sElapsedRealtimeClock.millis(), isSatisfied, false);
    }

    private static JobStatus createJobStatus(long earliestRunTimeElapsedMillis,
            long latestRunTimeElapsedMillis) {
        return createJobStatus(
                /* standbyBucketIndex= */ 0,
                /* standbyBucketReason= */ 0,
                earliestRunTimeElapsedMillis,
                latestRunTimeElapsedMillis);
    }

    private static JobStatus createJobStatus(int standbyBucketIndex, int standbyBucketReason,
            long earliestRunTimeElapsedMillis, long latestRunTimeElapsedMillis) {
        final JobInfo job = new JobInfo.Builder(101, new ComponentName("foo", "bar"))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();
        return new JobStatus(
                /* job= */ job,
                /* callingUid= */ 0,
                /* sourcePkgName= */ null,
                /* sourceUserId= */ -1,
                standbyBucketIndex,
                standbyBucketReason,
                /* namespace= */ null,
                /* sourceTag= */ null,
                /* earliestRunTimeElapsedMillis= */ earliestRunTimeElapsedMillis,
                /* latestRunTimeElapsedMillis= */ latestRunTimeElapsedMillis,
                /* lastSuccessfulRunTime= */ 0,
                /* lastFailedRunTime= */ 0,
                /* cumulativeExecutionTimeMs= */ 0,
                /* persistedExecutionTimesUTC= */ null,
                /* innerFlags= */ 0,
                /* dynamicConstraints= */ 0);
    }

    private static JobStatus createJobStatus(JobInfo job) {
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, 0, null, -1, "JobStatusTest", null);
        jobStatus.serviceProcessName = "testProcess";
        return jobStatus;
    }

    private static JobStatus createJobStatus(
            JobInfo job, String packageName, int callingUid, String namespace, String tag) {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                        job, callingUid, packageName, SOURCE_USER_ID, namespace, tag);
        jobStatus.serviceProcessName = "testProcess";
        return jobStatus;
    }
}
