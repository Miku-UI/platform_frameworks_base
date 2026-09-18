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

package com.android.server.job;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.app.job.JobInfo;
import android.content.ComponentName;

import com.android.server.job.controllers.JobStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public final class JobStoreTest {
    private static final int TEST_UID = 1000;
    private static final int NON_MATCHING_SOURCE_UID = 2000;
    private static final String TEST_PACKAGE_NAME = "foo";
    private static final String TEST_JOB_NAME_1 = "TestJobName1";
    private static final String TEST_JOB_NAME_2 = "TestJobName2";
    private static final String TEST_JOB_NAME_3 = "TestJobName3";
    private static final String TEST_JOB_NAME_4 = "TestJobName4";
    private static final String TEST_JOB_NAME_5 = "TestJobName5";
    private static final String TEST_JOB_NAME_6 = "TestJobName6";

    private JobStore.JobSet mJobSet;
    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession =
                mockitoSession().initMocks(this).strictness(Strictness.LENIENT).startMocking();
        mJobSet = new JobStore.JobSet();
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void testGetTopJobsDebugStringForUid_empty() {
        assertThat(mJobSet.getTopJobsDebugStringForUid(TEST_UID)).isEmpty();
    }

    @Test
    public void testGetTopJobsDebugStringForUid_noMatchingSourceUid() {
        JobStatus job = createJobStatus(1, TEST_UID, TEST_UID, TEST_JOB_NAME_1);
        final JobStatus spiedJob = spy(job);
        doReturn(NON_MATCHING_SOURCE_UID).when(spiedJob).getSourceUid();
        mJobSet.add(spiedJob);
        assertThat(mJobSet.getTopJobsDebugStringForUid(TEST_UID)).isEmpty();
    }

    @Test
    public void testGetTopJobsDebugStringForUid_variousJobs() {
        final int uid = TEST_UID;
        int jobId = 0;

        jobId = addJobs(jobId, uid, 6, TEST_JOB_NAME_1);
        jobId = addJobs(jobId, uid, 5, TEST_JOB_NAME_2);
        jobId = addJobs(jobId, uid, 4, TEST_JOB_NAME_3);
        jobId = addJobs(jobId, uid, 3, TEST_JOB_NAME_4);
        jobId = addJobs(jobId, uid, 2, TEST_JOB_NAME_5);
        // 1 job for TestJobName6
        mJobSet.add(createJobStatus(jobId++, uid, uid, TEST_JOB_NAME_6));

        // Add some noise that should be filtered out
        mJobSet.add(createJobStatus(jobId++, uid, uid + 1, "noise_job"));
        mJobSet.add(createJobStatus(jobId++, uid + 1, uid + 1, "noise_job2"));

        final String expected = String.join(",",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_1 + ":6",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_2 + ":5",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_3 + ":4",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_4 + ":3",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_5 + ":2");
        assertThat(mJobSet.getTopJobsDebugStringForUid(uid)).isEqualTo(expected);
    }

    @Test
    public void testGetTopJobsDebugStringForUid_lessThanFive() {
        final int uid = TEST_UID;
        int jobId = 0;

        jobId = addJobs(jobId, uid, 3, TEST_JOB_NAME_1);
        addJobs(jobId, uid, 2, TEST_JOB_NAME_2);

        final String expected = String.join(",",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_1 + ":3",
                TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_2 + ":2");
        assertThat(mJobSet.getTopJobsDebugStringForUid(uid)).isEqualTo(expected);
    }

    @Test
    public void testGetTopJobsDebugStringForUid_oneJobName() {
        final int uid = TEST_UID;
        mJobSet.add(createJobStatus(1, uid, uid, TEST_JOB_NAME_1));
        mJobSet.add(createJobStatus(2, uid, uid, TEST_JOB_NAME_1));
        assertThat(mJobSet.getTopJobsDebugStringForUid(uid))
                .isEqualTo(TEST_PACKAGE_NAME + "/" + TEST_JOB_NAME_1 + ":2");
    }

    /**
     * Creates a {@link JobStatus} object for testing.
     *
     * @param jobId       The ID for the job.
     * @param uid         The UID of the job.
     * @param sourceUid   The source UID of the job.
     * @param batteryName The battery name for the job.
     * @return A {@link JobStatus} object.
     */
    private JobStatus createJobStatus(int jobId, int uid, int sourceUid, String batteryName) {
        final JobInfo jobInfo = new JobInfo.Builder(jobId,
                new ComponentName(TEST_PACKAGE_NAME, batteryName)).build();
        return JobStatus.createFromJobInfo(jobInfo, uid, TEST_PACKAGE_NAME, sourceUid,
                null, batteryName);
    }

    /**
     * Adds a number of jobs to the job set.
     *
     * @param startingJobId The starting ID for the jobs.
     * @param uid           The UID for the jobs.
     * @param count         The number of jobs to add.
     * @param batteryName   The battery name for the jobs.
     * @return The next available job ID.
     */
    private int addJobs(int startingJobId, int uid, int count, String batteryName) {
        for (int i = 0; i < count; i++) {
            mJobSet.add(createJobStatus(startingJobId++, uid, uid, batteryName));
        }
        return startingJobId;
    }
}
