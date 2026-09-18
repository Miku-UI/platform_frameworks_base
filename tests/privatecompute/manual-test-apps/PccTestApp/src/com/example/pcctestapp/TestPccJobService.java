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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TestPccJobService extends JobService {

    private static final String TAG = TestPccJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {

        Log.i(TAG, "Job started, id: " + params.getJobId());

        Log.i(TAG, "JobParameters: " + params);

        if (params.getExtras() != null && !params.getExtras().isEmpty()) {
            Log.i(TAG, "JobParameters Extras:");
            for (String key : params.getExtras().keySet()) {
                Log.i(TAG, "  " + key + ": " + params.getExtras().get(key));
            }
        }

        if (params.getExtras().getBoolean(Constants.KEY_START_NON_PCC_SERVICE, false)) {
            Log.i(TAG, "Starting non-PCC service from PCC job service.");
            Intent intent = new Intent(this, TestNonPccService.class);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.KEY_MY_KEY, "Hello from PccJobService");
            intent.putExtras(bundle);
            startService(intent);
        }
        // Job finished successfully.
        jobFinished(params, false);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "Job stopped, id: " + params.getJobId());
        return true;
    }
}
