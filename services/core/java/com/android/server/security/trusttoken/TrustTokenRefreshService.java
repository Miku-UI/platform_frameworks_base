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
import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.util.Slog;

import com.android.server.LocalServices;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO(b/418280383): figure out a way to test this. It's not reliable to use a real JobScheduler.
// It's prohibited to mock SDK. Robolectric JobScheduler doesn't support JobScheduler.forNamespace,
// nor does it actually executes the job.
public class TrustTokenRefreshService extends JobService {
    private static final String TAG = "TrustTokenRefreshService";

    private static final String NAMESPACE = "trust_token";
    private static final ComponentName REFRESH_SERVICE_COMPONENT =
            new ComponentName("android", TrustTokenRefreshService.class.getName());
    private static final int REGULAR_REFRESH_JOB_ID = 1;
    private static final int URGENT_REFRESH_JOB_ID = 2;

    @NonNull private ExecutorService mExecutorService;
    @NonNull private TrustTokenManagerInternal mInternal;
    @Nullable private TrustTokenProvider mProvider;

    @Override
    public void onCreate() {
        var internal = LocalServices.getService(TrustTokenManagerInternal.class);
        mExecutorService = Executors.newSingleThreadExecutor();
        Context context = getBaseContext();
        ComponentName providerComponent = TrustTokenProvider.getServiceProvider(context);
        TrustTokenProvider provider = null;
        if (providerComponent != null) {
            provider = new TrustTokenProvider(context, mExecutorService, providerComponent);
        } else {
            Slog.e(TAG, "no TrustTokenProvider found");
        }
        onCreate(internal, provider);
    }

    // For unit test.
    void onCreate(TrustTokenManagerInternal internal, TrustTokenProvider provider) {
        super.onCreate();
        mInternal = internal;
        mProvider = provider;
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
        var startLogger = new MetricsLogger.RefreshTokenStarted();
        var finishLogger = new MetricsLogger.RefreshTokenFinished();
        if (params.getJobId() == URGENT_REFRESH_JOB_ID) {
            startLogger.setRefreshType(startLogger.REFRESH_TYPE_URGENT);
            finishLogger.setRefreshType(finishLogger.REFRESH_TYPE_URGENT);
        } else if (params.getJobId() == REGULAR_REFRESH_JOB_ID) {
            startLogger.setRefreshType(startLogger.REFRESH_TYPE_REGULAR);
            finishLogger.setRefreshType(finishLogger.REFRESH_TYPE_REGULAR);
        }
        startLogger.log();
        if (mProvider == null) {
            Slog.e(TAG, "no TrustTokenProvider found");
            finishLogger.setOutcome(finishLogger.OUTCOME_PROVIDER_UNAVAILABLE).log();
            return false;
        }
        mExecutorService.execute(
                () -> {
                    int num = 0;
                    if (params.getJobId() == URGENT_REFRESH_JOB_ID) {
                        num = 100;
                    } else if (params.getJobId() == REGULAR_REFRESH_JOB_ID) {
                        num = 5000;
                    }
                    mInternal.refillTokens(
                            mProvider,
                            num,
                            new OutcomeReceiver<Void, Throwable>() {
                                @Override
                                public void onResult(Void unused) {
                                    finishLogger.setOutcome(finishLogger.OUTCOME_SUCCESS).log();
                                    jobFinished(params, /* wantsReschedule= */ false);
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    Slog.e(TAG, "Failed to refresh tokens: " + throwable);
                                    if (throwable instanceof TrustTokenProvider.ProviderError) {
                                        finishLogger
                                                .setOutcome(finishLogger.OUTCOME_SERVER_ERROR)
                                                .setServerErrorCode(
                                                        ((TrustTokenProvider.ProviderError)
                                                                        throwable)
                                                                .getCode())
                                                .log();
                                    } else {
                                        finishLogger
                                                .setOutcome(finishLogger.OUTCOME_SERVICE_ERROR)
                                                .log();
                                    }
                                    // Only retry the periodical refresh job. The one off refreshes
                                    // will be triggered again by the client if it's still
                                    // necessary.
                                    jobFinished(
                                            params,
                                            /* wantsReschedule= */ params.getJobId()
                                                    == REGULAR_REFRESH_JOB_ID);
                                }
                            });
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Slog.i(TAG, "stop job " + params.toString());
        // TODO(b/418280383): cancel the on-going request
        return false;
    }

    static class Scheduler {
        JobScheduler mJobScheduler;

        Scheduler(Context context) {
            mJobScheduler = context.getSystemService(JobScheduler.class).forNamespace(NAMESPACE);
        }

        void scheduleRegularRefresh() {
            if (mJobScheduler.getPendingJob(REGULAR_REFRESH_JOB_ID) != null) {
                Slog.i(TAG, "A regular refresh job already exists. Do nothing.");
                return;
            }
            long frequencyMillis = Duration.ofDays(3).toMillis();
            JobInfo.Builder jobInfo =
                    new JobInfo.Builder(REGULAR_REFRESH_JOB_ID, REFRESH_SERVICE_COMPONENT)
                            .setPeriodic(frequencyMillis)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setRequiresCharging(true)
                            .setBackoffCriteria(
                                    Duration.ofMinutes(15).toMillis(),
                                    JobInfo.BACKOFF_POLICY_EXPONENTIAL);
            if (mJobScheduler.schedule(jobInfo.build()) == JobScheduler.RESULT_SUCCESS) {
                Slog.i(TAG, "Regular trust token refresh scheduled.");
            } else {
                Slog.e(TAG, "Regular trust token refresh failed to schedule.");
            }
        }

        void scheduleUrgentRefresh() {
            // TODO(b/418280383): verify this can be scheduled quick enough.
            if (mJobScheduler.getPendingJob(URGENT_REFRESH_JOB_ID) != null) {
                // Do nothing if an one off refresh job already exists.
                Slog.i(TAG, "An urgent trust token refresh is already enqueued. Do nothing.");
                return;
            }
            JobInfo.Builder jobInfo =
                    new JobInfo.Builder(URGENT_REFRESH_JOB_ID, REFRESH_SERVICE_COMPONENT)
                            .setExpedited(true)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            // Refresh even when the phone is on battery
                            .setRequiresCharging(false);
            if (mJobScheduler.schedule(jobInfo.build()) == JobScheduler.RESULT_SUCCESS) {
                Slog.i(TAG, "Urgent trust token refresh scheduled.");
            } else {
                Slog.e(TAG, "Urgent trust token refresh failed to schedule.");
            }
        }
    }
}
