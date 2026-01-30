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

package com.android.server.appwidget;

import static android.app.job.JobScheduler.RESULT_SUCCESS;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManagerInternal;
import android.content.ComponentName;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;

/**
 * This is a periodic job that triggers reporting of widget events from AppWidgetService into
 * UsageStatsService.
 */
public class ReportWidgetEventsJob extends JobService {
    private static final String TAG = "ReportWidgetEventsJob";
    private static final int JOB_ID = 1;
    private static final String NAMESPACE =
            "com.android.server.appwidget.AppWidgetServiceImpl.ReportWidgetEventsJob";

    static void schedule(JobScheduler jobScheduler, long periodMillis) {
        try {
            jobScheduler = jobScheduler.forNamespace(NAMESPACE);

            // If periodMillis is 0 or less, do not schedule a job. The event will be reported to
            // UsageStatsManager as soon as it is received from the widget view.
            if (periodMillis <= 0) {
                jobScheduler.cancel(JOB_ID);
                return;
            }

            ComponentName component = new ComponentName("android",
                    ReportWidgetEventsJob.class.getName());
            JobInfo newJob = new JobInfo.Builder(JOB_ID, component)
                    .setRequiresDeviceIdle(false)
                    .setPeriodic(periodMillis)
                    .build();
            if (jobScheduler.schedule(newJob) != RESULT_SUCCESS) {
                Slog.w(TAG, "Failed to schedule reportWidgetEvents job: failed result");
            }
        } catch (Throwable e) {
            Slog.e(TAG, "Failed to schedule reportWidgetEvents job", e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        BackgroundThread.getExecutor().execute(() -> {
            LocalServices.getService(AppWidgetManagerInternal.class)
                    .saveWidgetEvents();
            jobFinished(params, /* wantsReschedule= */ false);
        });
        // Return true to indicate that job is still running in another thread.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Return false so that this job is not retried (it will still be periodically
        // scheduled).
        return false;
    }
}
