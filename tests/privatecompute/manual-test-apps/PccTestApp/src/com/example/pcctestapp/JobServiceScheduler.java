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

package com.example.pcctestapp;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

/**
 * Utility class to schedule test jobs.
 */
public class JobServiceScheduler {
    private static final String TAG = JobServiceScheduler.class.getSimpleName();
    private static final int TEST_JOB_ID = 1;
    private static final int TEST_CONSTRAINED_PCC_JOB_ID = 3;

    public static void scheduleUnconstrainedTestPccJob(
            Context context, boolean startNonPccService) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            Log.e(TAG, "Unable to get JobScheduler.");
            return;
        }

        ComponentName componentName = new ComponentName(context, TestPccJobService.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(Constants.KEY_START_NON_PCC_SERVICE, startNonPccService);
        JobInfo jobInfo =
                new JobInfo.Builder(TEST_JOB_ID, componentName)
                        .setExtras(extras)
                        .setOverrideDeadline(0)
                        .build();

        int resultCode = jobScheduler.schedule(jobInfo);
        handleJobScheduleResult(context, resultCode, "Unconstrained PCC");
    }

    public static void scheduleConstrainedTestPccJob(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            Log.e(TAG, "Unable to get JobScheduler.");
            return;
        }

        final ComponentName componentName = new ComponentName(context, TestPccJobService.class);
        final JobInfo.Builder builder =
                new JobInfo.Builder(TEST_CONSTRAINED_PCC_JOB_ID, componentName)
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setRequiresDeviceIdle(true)
                        .setPersisted(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(true);
        }

        final long periodMillis = 15 * 60 * 1000L; // 15 minutes
        final long flexMillis = 5 * 60 * 1000L; // 5 minutes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPeriodic(periodMillis, flexMillis);
        } else {
            builder.setPeriodic(periodMillis);
        }

        final int resultCode = jobScheduler.schedule(builder.build());
        handleJobScheduleResult(context, resultCode, "Constrained PCC");
    }

    private static void handleJobScheduleResult(Context context, int resultCode, String jobName) {
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            DisplayUtils.logAndShowToast(
                    context, Log.INFO, TAG, jobName + " job scheduled successfully.");
        } else {
            DisplayUtils.logAndShowToast(
                context, Log.ERROR, TAG, jobName + " job scheduling failed.");
        }
    }
}
