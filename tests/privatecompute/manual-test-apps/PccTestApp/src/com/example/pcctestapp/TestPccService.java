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

package com.example.pcctestapp;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.privatecompute.PccService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;


public class TestPccService extends PccService {
    private static final String TAG = TestPccService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "PCC service created.");
    }

    @Override
    public void onReceiveData(@NonNull Bundle data, @NonNull String packageName) {
        String text = data.getString(Constants.KEY_MY_KEY);
        if (text != null) {
            String message =
                    String.format(
                            "Data received in Pcc service: \"%s\", from: \"%s\"",
                            text, packageName);
            Log.i(TAG, message);
        }
        if (data.getBoolean(
            Constants.KEY_SCHEDULE_UNCONSTRAINED_JOB_AND_START_NON_PCC_SERVICE, false)) {
            JobServiceScheduler.scheduleUnconstrainedTestPccJob(this, true);
            String message =
            "Scheduled unconstrained PCC job and starting non-PCC service " +
            "from the Pcc Job Service.";
            DisplayUtils.logAndShowToast(this, Log.INFO, TAG, message);
        }
        if (data.getBoolean(Constants.KEY_SCHEDULE_CONSTRAINED_JOB, false)) {
            JobServiceScheduler.scheduleConstrainedTestPccJob(this);
        }
        if (data.getBoolean(Constants.KEY_SEND_DATA_TO_NON_PCC, false)) {
            Intent intent = new Intent(this, TestNonPccService.class);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.KEY_MY_KEY, "Hello from PccService");
            intent.putExtras(bundle);
            startService(intent);
            String message = "Sending data to non-PCC service: TestNonPccService";
            DisplayUtils.logAndShowToast(this, Log.INFO, TAG, message);
        }
    }

}
