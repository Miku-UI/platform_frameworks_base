/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.memorylimitertests.apps.memorylimitertestapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Trace;
import android.util.Log;

public class ClientTestReceiver extends BroadcastReceiver {
    private static final String TAG = TestActivity.TAG;

    private final TestActivity mTest;

    ClientTestReceiver(TestActivity activity) {
        mTest = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.contains(".MEMORY")) {
            Trace.beginSection("ClientTestReceiver.onReceive");
            int size = intent.getIntExtra("size", 0);
            int delay = intent.getIntExtra("delay", 0);
            Log.i(TAG, "handling memory size " + size + "MB, delay " + delay + "s");
            if (size > 0) {
                mTest.setMemory(size, delay);
            }
            Trace.endSection();
        } else if (action.contains(".EXIT")) {
            // Hard exit.  The test program is testing how the memory limiter responds to process
            // exits.
            Log.i(TAG, "handling exit request");
            System.exit(0);
        } else {
            Log.w(TAG, "unexpected intent: " + intent.toString());
        }
    }
}
