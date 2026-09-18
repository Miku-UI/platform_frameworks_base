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

package com.android.server.security.trusttoken;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Slog;

import com.android.server.LocalServices;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO(b/418280383): figure out a way to test this. It's not reliable to use a real JobScheduler.
// It's prohibited to mock SDK. Robolectric JobScheduler doesn't support JobScheduler.forNamespace,
// nor does it actually executes the job.
public class TrustTokenCleanUpService extends JobService {
    private static final String TAG = "TrustTokenCleanUpService";

    private static final String NAMESPACE = "trust_token_clean_up";
    private static final ComponentName CLEAN_UP_SERVICE_COMPONENT =
            new ComponentName("android", TrustTokenCleanUpService.class.getName());
    private static final int CLEAN_UP_JOB_ID = 1;

    @NonNull private ExecutorService mExecutorService;
    @NonNull private TrustTokenManagerInternal mInternal;

    @Override
    public void onCreate() {
        mExecutorService = Executors.newSingleThreadExecutor();
        mInternal = LocalServices.getService(TrustTokenManagerInternal.class);
    }

    @Override
    public void onDestroy() {
        mExecutorService.close();
    }

    @Override
    public void onNetworkChanged(JobParameters params) {
        // It's required to override this method.
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Slog.i(TAG, "start job " + params.toString());
        mExecutorService.execute(
                () -> {
                    try {
                        mInternal.cleanUpDatabase();
                    } catch (RuntimeException e) {
                        Slog.e(TAG, "Failed to clean up: " + e);
                    }
                    // It shouldn't fail if there's no bug, so there's not much use retrying.
                    jobFinished(params, /* wantsReschedule= */ false);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Slog.i(TAG, "stop job " + params.toString());
        return true;
    }

    static class Scheduler {
        JobScheduler mJobScheduler;

        Scheduler(Context context) {
            mJobScheduler = context.getSystemService(JobScheduler.class).forNamespace(NAMESPACE);
        }

        void scheduleRegularCleanUp() {
            if (mJobScheduler.getPendingJob(CLEAN_UP_JOB_ID) != null) {
                Slog.i(TAG, "A regular clean up job already exists. Do nothing.");
                return;
            }
            long frequencyMillis = Duration.ofDays(15).toMillis();
            long flexMillis = Duration.ofDays(1).toMillis();
            JobInfo.Builder jobInfo =
                    new JobInfo.Builder(CLEAN_UP_JOB_ID, CLEAN_UP_SERVICE_COMPONENT)
                            .setPeriodic(frequencyMillis, flexMillis)
                            .setRequiresCharging(true)
                            .setRequiresDeviceIdle(true);
            if (mJobScheduler.schedule(jobInfo.build()) == JobScheduler.RESULT_SUCCESS) {
                Slog.i(TAG, "Regular trust token refresh scheduled.");
            } else {
                Slog.e(TAG, "Regular trust token refresh failed to schedule.");
            }
        }
    }
}
