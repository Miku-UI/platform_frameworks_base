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
import android.util.Log;

public class TestNonPccJobService extends JobService {
    private static final String TAG = TestNonPccJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Job started, id: " + params.getJobId());
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
