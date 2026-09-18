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

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import android.util.Log;

public class TestNonPccService extends Service {

    private static final String TAG = TestNonPccService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Non-PCC service created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand called.");
        if (intent != null) {
            Bundle data = intent.getExtras();
            if (data != null) {
                String text = data.getString(Constants.KEY_MY_KEY);
                if (text != null) {
                    String message =
                            String.format("Data received in Non-Pcc service: \"%s\"", text);
                    Log.i(TAG, message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG)
                    .show();
                } else {
                    Log.w(TAG, "No 'my_key' found in intent extras.");
                    Toast.makeText(this,
                    "No 'my_key' found in intent extras.", Toast.LENGTH_SHORT)
                    .show();
                }
            } else {
                Log.w(TAG, "No extras found in intent.");
                Toast.makeText(this, "No extras found in intent.", Toast.LENGTH_SHORT)
                .show();
            }
        } else {
            Log.w(TAG, "Intent is null in onStartCommand.");
            Toast.makeText(this, "Service started with null intent.", Toast.LENGTH_SHORT)
            .show();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
