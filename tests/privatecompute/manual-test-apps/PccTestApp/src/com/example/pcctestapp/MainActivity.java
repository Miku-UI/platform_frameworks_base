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

import android.app.Activity;
import android.app.privatecompute.PccClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private boolean mIsPccServiceBound = false;
    private PccClient mClient = null;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DisplayUtils.logAndShowToast(MainActivity.this, Log.INFO, TAG, "Service connected!");
            mClient = PccClient.createInstance(MainActivity.this, service);
            mIsPccServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            DisplayUtils.logAndShowToast(
                    MainActivity.this, Log.INFO, TAG, "Service disconnected unexpectedly.");
            mClient = null;
            mIsPccServiceBound = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(Gravity.CENTER);

        // Button to bind to pcc service.
        Button bindButton = new Button(this);
        bindButton.setText("Bind to PCC service");
        bindButton.setOnClickListener(v -> {
            if (!mIsPccServiceBound) {
                Log.i(TAG, "Attempting to bind service...");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.example.pcctestapp",
                        "com.example.pcctestapp.TestPccService"));
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            } else {
                DisplayUtils.logAndShowToast(this, Log.WARN, TAG, "Service is already bound.");
            }
        });

        // Button to send data to the pcc service.
        Button sendDataButton = new Button(this);
        sendDataButton.setText("Send data to Pcc service");
        sendDataButton.setOnClickListener(v -> {
            if (mIsPccServiceBound) {
                Bundle data = new Bundle();
                data.putString(Constants.KEY_MY_KEY, "Hello World");
                try {
                    mClient.sendData(data);
                    DisplayUtils.logAndShowToast(this, Log.INFO, TAG, "Data sent to Pcc service.");
                } catch (Exception e) {
                    DisplayUtils.logAndShowToast(
                            this, Log.WARN, TAG, "Failed to send data to Pcc service. " + e);
                }
            } else {
                DisplayUtils.logAndShowToast(this, Log.ERROR, TAG,
                        "Pcc service is not bound yet. Bind to Pcc service first.");
            }
        });


        // Button to schedule a constrained Pcc job from the pcc service.
        Button scheduleConstrainedPccJobFromPccServiceButton = new Button(this);
        scheduleConstrainedPccJobFromPccServiceButton.setText(
            "Schedule a constrained Pcc-Job from Pcc service");
        scheduleConstrainedPccJobFromPccServiceButton.setOnClickListener(v -> {
            if (mIsPccServiceBound) {
                Bundle data = new Bundle();
                data.putBoolean(Constants.KEY_SCHEDULE_CONSTRAINED_JOB, true);
                try {
                    mClient.sendData(data);
                    DisplayUtils.logAndShowToast(this, Log.INFO, TAG,
                            "Request to schedule a constrained job sent to Pcc service.");
                } catch (Exception e) {
                    DisplayUtils.logAndShowToast(this, Log.WARN, TAG,
                            "Failed to send request to schedule a constrained job. " + e);
                }
            } else {
                DisplayUtils.logAndShowToast(this, Log.ERROR, TAG,
                        "Pcc service is not bound yet. Bind to Pcc service first.");
            }
        });

        // Button to schedule a constrained job from the main activity using TestJobService.
        Button scheduleConstrainedPccJobFromNonPccMainActivityButton = new Button(this);
        scheduleConstrainedPccJobFromNonPccMainActivityButton.setText(
            "Schedule a constrained Pcc-Job from Non Pcc Main Activity (will fail)");
        scheduleConstrainedPccJobFromNonPccMainActivityButton.setOnClickListener(
                v -> JobServiceScheduler.scheduleConstrainedTestPccJob(this));

        // Button to send data from the pcc service to non pcc service.
        Button sendDataFromPccServiceToNonPccServiceButton = new Button(this);
        sendDataFromPccServiceToNonPccServiceButton.setText(
            "Send data from Pcc service to Non Pcc service (not allowed)");
        sendDataFromPccServiceToNonPccServiceButton.setOnClickListener(v -> {
            if (mIsPccServiceBound) {
                Bundle data = new Bundle();
                data.putBoolean(Constants.KEY_SEND_DATA_TO_NON_PCC, true);
                try {
                    mClient.sendData(data);
                    DisplayUtils.logAndShowToast(this, Log.INFO, TAG,
                            "Request to send data to Non Pcc service sent to Pcc service.");
                } catch (Exception e) {
                    DisplayUtils.logAndShowToast(this, Log.WARN, TAG,
                            "Failed to send request to send data to Non Pcc service. " + e);
                }
            } else {
                DisplayUtils.logAndShowToast(this, Log.ERROR, TAG,
                        "Pcc service is not bound yet. Bind to Pcc service first.");
            }
        });

        // Button to schedule an unconstrained job from the pcc service to start a non-pcc service.
        Button scheduleUnconstrainedPccJobToStartNonPccServiceButton = new Button(this);
        scheduleUnconstrainedPccJobToStartNonPccServiceButton.setText(
                "Schedule Unconstrained PCC-Job to start Non-PCC-Service (not allowed)");
        scheduleUnconstrainedPccJobToStartNonPccServiceButton.setOnClickListener(
                v -> {
                    if (mIsPccServiceBound) {
                        Bundle data = new Bundle();
                        data.putBoolean(
                                Constants.KEY_SCHEDULE_UNCONSTRAINED_JOB_AND_START_NON_PCC_SERVICE,
                                true);
                        try {
                            mClient.sendData(data);
                            DisplayUtils.logAndShowToast(
                                    this,
                                    Log.INFO,
                                    TAG,
                                    "Request to schedule a job and start non-pcc service sent to"
                                            + " Pcc service.");
                        } catch (Exception e) {
                            DisplayUtils.logAndShowToast(
                                    this,
                                    Log.WARN,
                                    TAG,
                                    "Failed to send request to schedule a job. " + e);
                        }
                    } else {
                        DisplayUtils.logAndShowToast(
                                this,
                                Log.ERROR,
                                TAG,
                                "Pcc service is not bound yet. Bind to Pcc service first.");
                    }
                });


        // Button to unbind from the pcc service.
        Button unbindButton = new Button(this);
        unbindButton.setText("Unbind from PCC service");
        unbindButton.setOnClickListener(v -> {
            if (mIsPccServiceBound) {
                Log.i(TAG, "Unbinding service...");
                unbindService(mConnection);
                DisplayUtils.logAndShowToast(this, Log.INFO, TAG, "Service disconnected.");
                mIsPccServiceBound = false;
            } else {
                DisplayUtils.logAndShowToast(
                        this, Log.ERROR, TAG, "Service is not bound, cannot unbind.");
            }
        });

        layout.addView(bindButton);
        layout.addView(sendDataButton);

        layout.addView(scheduleConstrainedPccJobFromPccServiceButton);

        layout.addView(scheduleConstrainedPccJobFromNonPccMainActivityButton);

        layout.addView(sendDataFromPccServiceToNonPccServiceButton);
        layout.addView(scheduleUnconstrainedPccJobToStartNonPccServiceButton);

        layout.addView(unbindButton);

        setContentView(layout);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mIsPccServiceBound) {
            Log.i(TAG, "Unbinding service on activity destroy to prevent service leaks.");
            unbindService(mConnection);
            mIsPccServiceBound = false;
        }
    }
}
