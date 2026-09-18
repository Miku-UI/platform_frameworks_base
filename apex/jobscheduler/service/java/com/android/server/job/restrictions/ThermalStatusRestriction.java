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
 * limitations under the License
 */

package com.android.server.job.restrictions;

import android.app.compat.CompatChanges;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.os.PowerManager;
import android.os.PowerManager.OnThermalStatusChangedListener;
import android.util.IndentingPrintWriter;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.job.JobSchedulerService;
import com.android.server.job.controllers.JobStatus;

public class ThermalStatusRestriction extends JobRestriction {
    private static final String TAG = "ThermalStatusRestriction";

    /** The threshold at which we start restricting low and min priority jobs. */
    private static final int LOW_PRIORITY_THRESHOLD = PowerManager.THERMAL_STATUS_LIGHT;
    /** The threshold at which we start restricting higher priority jobs. */
    private static final int HIGHER_PRIORITY_THRESHOLD = PowerManager.THERMAL_STATUS_MODERATE;
    /** The lowest threshold at which we start restricting jobs. */
    private static final int LOWER_THRESHOLD = LOW_PRIORITY_THRESHOLD;
    /** The threshold at which we start restricting ALL jobs. */
    private static final int UPPER_THRESHOLD = PowerManager.THERMAL_STATUS_SEVERE;

    private volatile int mThermalStatus = PowerManager.THERMAL_STATUS_NONE;

    public ThermalStatusRestriction(JobSchedulerService service) {
        // Uses the new specific stop reason code as STOP_REASON_DEVICE_STATE_THERMAL and
        // pending reason code as PENDING_JOB_REASON_DEVICE_STATE_THERMAL, when flag is enabled,
        // otherwise fall back to the generic stop reason code STOP_REASON_DEVICE_STATE and pending
        // reason code as PENDING_JOB_REASON_DEVICE_STATE.
        super(service, maybeGetEnhancedStopReason(), maybeGetEnhancedPendingReason(),
                JobParameters.INTERNAL_STOP_REASON_DEVICE_THERMAL);
    }

    @Override
    public void onSystemServicesReady() {
        final PowerManager powerManager =
                mService.getTestableContext().getSystemService(PowerManager.class);
        // Use MainExecutor
        powerManager.addThermalStatusListener(new OnThermalStatusChangedListener() {
            @Override
            public void onThermalStatusChanged(int status) {
                // This is called on the main thread. Do not do any slow operations in it.
                // mService.onControllerStateChanged() will just post a message, which is okay.

                // There are three buckets:
                //   1. Below the lower threshold (we don't care about changes within this bucket)
                //   2. Between the lower and upper thresholds.
                //     -> We care about transitions across buckets
                //     -> We care about transitions within the middle bucket
                //   3. Upper the upper threshold (we don't care about changes within this bucket)
                final boolean significantChange =
                        // Handle transitions within and into the bucket we care about (thus
                        // causing us to change our restrictions).
                        (status >= LOWER_THRESHOLD && status <= UPPER_THRESHOLD)
                                // Take care of transitions from the 2nd or 3rd bucket to the 1st
                                // bucket (thus exiting any restrictions we started enforcing).
                                || (mThermalStatus >= LOWER_THRESHOLD && status < LOWER_THRESHOLD)
                                // Take care of transitions from the 1st or 2nd bucket to the 3rd
                                // bucket (thus resulting in us beginning to enforce the tightest
                                // restrictions).
                                || (mThermalStatus < UPPER_THRESHOLD && status > UPPER_THRESHOLD);
                final boolean increased = mThermalStatus < status;
                mThermalStatus = status;
                if (significantChange) {
                    mService.onRestrictionStateChanged(ThermalStatusRestriction.this, increased);
                }
            }
        });
    }

    @Override
    public boolean isJobRestricted(JobStatus job, int bias) {
        if (bias >= JobInfo.BIAS_TOP_APP) {
            // Jobs with BIAS_TOP_APP should not be restricted
            return false;
        }
        if (mThermalStatus >= UPPER_THRESHOLD) {
            return true;
        }
        final int priority = job.getEffectivePriority();
        if (mThermalStatus >= HIGHER_PRIORITY_THRESHOLD) {
            // For moderate throttling:
            // Let all user-initiated jobs run.
            // Only let expedited jobs run if:
            // 1. They haven't previously run
            // 2. They're already running and aren't yet in overtime
            // Only let high priority jobs run if:
            //   They are already running and aren't yet in overtime
            // Don't let any other job run.
            if (job.shouldTreatAsUserInitiatedJob()) {
                return false;
            }
            if (job.shouldTreatAsExpeditedJob()) {
                return job.getNumPreviousAttempts() > 0
                        || (mService.isCurrentlyRunningLocked(job)
                                && mService.isJobInOvertimeLocked(job));
            }
            if (priority == JobInfo.PRIORITY_HIGH) {
                return !mService.isCurrentlyRunningLocked(job)
                        || mService.isJobInOvertimeLocked(job);
            }
            return true;
        }
        if (mThermalStatus >= LOW_PRIORITY_THRESHOLD) {
            if (bias >= JobInfo.BIAS_FOREGROUND_SERVICE) {
                // No restrictions on foreground jobs
                // on LOW_PRIORITY_THRESHOLD and below
                return false;
            }
            // For light throttling, throttle all min priority jobs and all low priority jobs that
            // aren't already running or have been running for long enough.
            return priority == JobInfo.PRIORITY_MIN
                    || (priority == JobInfo.PRIORITY_LOW
                        && (!mService.isCurrentlyRunningLocked(job)
                            || mService.isJobInOvertimeLocked(job)));
        }
        return false;
    }

    @VisibleForTesting
    int getThermalStatus() {
        return mThermalStatus;
    }

    @Override
    public void dumpConstants(IndentingPrintWriter pw) {
        pw.print("Thermal status: ");
        pw.println(mThermalStatus);
    }

    /**
     * Returns the appropriate pending reason to use when a job is restricted due to
     * device thermal status.
     *
     * <p>If the {@link android.app.job.Flags#enhancedPendingAndStopReasonsApi()} flag is enabled
     * and the app has the {@link JobStatus#INTRODUCE_NEW_PENDING_AND_STOP_DEVICE_STATE_REASONS}
     * compatibility change enabled, this will return the specific
     * {@link JobScheduler#PENDING_JOB_REASON_DEVICE_STATE_THERMAL}. Otherwise, it returns the
     * generic {@link JobScheduler#PENDING_JOB_REASON_DEVICE_STATE}.
     *
     * @return The pending reason to be reported to the system and developers.
     */
    private static int maybeGetEnhancedPendingReason() {
        if (android.app.job.Flags.enhancedPendingAndStopReasonsApi()
                && CompatChanges.isChangeEnabled(
                        JobStatus.INTRODUCE_NEW_PENDING_AND_STOP_DEVICE_STATE_REASONS)) {
            return JobScheduler.PENDING_JOB_REASON_DEVICE_STATE_THERMAL;
        }
        return JobScheduler.PENDING_JOB_REASON_DEVICE_STATE;
    }

    /**
     * Returns the appropriate stop reason to use when a job is stopped due to
     * device thermal status.
     *
     * <p>These improved response codes help developers better understand system behavior
     * and application code, leading to more accurate and user-friendly experiences.
     *
     * <p>If the {@link android.app.job.Flags#enhancedPendingAndStopReasonsApi()} flag is enabled
     * and the app has the {@link JobStatus#INTRODUCE_NEW_PENDING_AND_STOP_DEVICE_STATE_REASONS}
     * compatibility change enabled, this will return the specific
     * {@link JobParameters#STOP_REASON_DEVICE_STATE_THERMAL}. Otherwise, it returns the
     * generic {@link JobParameters#STOP_REASON_DEVICE_STATE}.
     *
     * @return The stop reason to be reported to the system and developers.
     */
    private static int maybeGetEnhancedStopReason() {
        if (android.app.job.Flags.enhancedPendingAndStopReasonsApi()
                && CompatChanges.isChangeEnabled(
                        JobStatus.INTRODUCE_NEW_PENDING_AND_STOP_DEVICE_STATE_REASONS)) {
            return JobParameters.STOP_REASON_DEVICE_STATE_THERMAL;
        }
        return JobParameters.STOP_REASON_DEVICE_STATE;
    }
}
