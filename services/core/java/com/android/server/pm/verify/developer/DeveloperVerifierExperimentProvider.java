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
package com.android.server.pm.verify.developer;

import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;

import android.annotation.Nullable;
import android.content.pm.PackageInstaller;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.os.Handler;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.PackageInstallerSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeveloperVerifierExperimentProvider {
    @GuardedBy("mExperiments")
    private final ArrayMap<String, ExperimentConfiguration> mExperiments = new ArrayMap<>();
    private static final long EXPERIMENT_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();
    private final Handler mHandler;

    public DeveloperVerifierExperimentProvider(Handler handler) {
        mHandler = handler;
    }

    /**
     * Add an experiment to the provider.
     */
    public void addExperiment(String packageName, int verificationPolicy, List<Integer> status) {
        // Remove invalid status codes
        final List<Integer> validStatuses = new ArrayList<>();
        final int size = status.size();
        for (int i = 0; i < size; i++) {
            final int code = status.get(i);
            if (code > DeveloperVerificationStatusInternal.STATUS_UNKNOWN
                    && code <= DeveloperVerificationStatusInternal.STATUS_INFEASIBLE) {
                validStatuses.add(code);
            }
        }
        if (validStatuses.isEmpty()) {
            return;
        }
        synchronized (mExperiments) {
            mExperiments.put(packageName,
                    new ExperimentConfiguration(verificationPolicy, validStatuses));
        }
        final String token = getExperimentToken(packageName);
        // Remove any previously set cleaning task for this package
        mHandler.removeCallbacksAndEqualMessages(token);
        // Automatically remove the experiment after timeout
        mHandler.postDelayed(() -> {
            synchronized (mExperiments) {
                mExperiments.remove(packageName);
            }
        }, token, EXPERIMENT_TIMEOUT_MILLIS);
    }

    /**
     * Remove the experiment for a package. If the package name is null, clear all experiments.
     */
    public void clearExperiment(@Nullable String packageName) {
        if (packageName == null) {
            Set<String> packages = mExperiments.keySet();
            synchronized (mExperiments) {
                mExperiments.clear();
            }
            // Remove all previously set cleaning tasks
            for (int i = 0; i < packages.size(); i++) {
                final String pkg = packages.iterator().next();
                mHandler.removeCallbacksAndEqualMessages(getExperimentToken(pkg));
            }
        } else {
            synchronized (mExperiments) {
                mExperiments.remove(packageName);
            }
            // Remove any previously set cleaning task for this package
            mHandler.removeCallbacksAndEqualMessages(getExperimentToken(packageName));
        }
    }

    private String getExperimentToken(String packageName) {
        return "addExperiment:" + packageName;
    }
    /**
     * Check if the provider has an experiment for the given package.
     */
    public boolean hasExperiments(String packageName) {
        synchronized (mExperiments) {
            return mExperiments.containsKey(packageName);
        }
    }

    /**
     * Run the next experiment for the given package.
     */
    public boolean runNextExperiment(String packageName,
            PackageInstallerSession.DeveloperVerifierCallback callback) {
        final int policy;
        final int nextStatus;
        synchronized (mExperiments) {
            final ExperimentConfiguration experiment = mExperiments.get(packageName);
            if (experiment == null) {
                return false;
            }
            final List<Integer> status = experiment.mStatus;
            if (status.isEmpty()) {
                return false;
            }
            policy = experiment.mVerificationPolicy;
            nextStatus = status.removeFirst();
        }
        // First apply the policy
        callback.onVerificationPolicyOverridden(policy);
        boolean experimentRun = false;
        // Then apply the result
        switch (nextStatus) {
            case DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS:
                callback.onVerificationCompleteReceived(
                        new DeveloperVerificationStatus.Builder().setVerified(true).build(), null);
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_REJECT:
                callback.onVerificationCompleteReceived(
                        new DeveloperVerificationStatus.Builder().setVerified(false).build(), null);
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_UNKNOWN:
                callback.onVerificationIncompleteReceived(
                        DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_NETWORK_UNAVAILABLE:
                callback.onVerificationIncompleteReceived(
                        DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE);
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_TIMEOUT:
                callback.onTimeout();
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_DISCONNECTED:
                callback.onConnectionFailed();
                experimentRun = true;
                break;
            case DeveloperVerificationStatusInternal.STATUS_INFEASIBLE:
                callback.onConnectionInfeasible();
                experimentRun = true;
                break;
            default:
                break;
        }
        // Clean up
        synchronized (mExperiments) {
            final ExperimentConfiguration experiment = mExperiments.get(packageName);
            if (experiment == null) {
                return experimentRun;
            }
            final List<Integer> status = experiment.mStatus;
            if (status.isEmpty()) {
                mExperiments.remove(packageName);
            }
        }
        return experimentRun;
    }

    private static class ExperimentConfiguration {
        @PackageInstaller.DeveloperVerificationPolicy
        final int mVerificationPolicy;
        /**
         * A list of expected result status to be returned in sequence to upcoming verification
         * requests. {@link DeveloperVerificationStatusInternal.Status} defines the status codes.
         */
        final List<Integer> mStatus;

        ExperimentConfiguration(int verificationPolicy, List<Integer> status) {
            mVerificationPolicy = verificationPolicy;
            mStatus = status;
        }
    }
}
