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

package com.android.virtualdevicemanager;

import static android.companion.virtual.computercontrol.ComputerControlSession.EXTRA_AUTOMATING_PACKAGE_NAME;

import android.companion.virtual.IVirtualDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

public class AutomatedAppLaunchWarningActivity extends FragmentActivity {

    private static final String TAG = "AutomatedAppLaunchWarning";

    private IVirtualDeviceManager mVirtualDeviceManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVirtualDeviceManager = IVirtualDeviceManager.Stub.asInterface(
                ServiceManager.getService(Context.VIRTUAL_DEVICE_SERVICE));
        if (mVirtualDeviceManager == null) {
            Log.e(TAG, "Could not get VirtualDeviceManager service");
            finish();
            return;
        }

        Intent intent = getIntent();
        String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        IntentSender target = intent.getParcelableExtra(Intent.EXTRA_INTENT, IntentSender.class);
        String automatingPackageName = intent.getStringExtra(EXTRA_AUTOMATING_PACKAGE_NAME);
        ResultReceiver resultReceiver = getIntent().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

        if (targetPackageName == null || target == null || automatingPackageName == null
                || resultReceiver == null) {
            Log.e(TAG, "Got invalid intent");
            finish();
            return;
        }

        if (!validateAutomatedAppLaunchWarningIntent(intent)) {
            Log.i(TAG, "Skip warning, automation is no longer active");
            AutomatedAppLaunchWarningActivityFragment.startIntentSender(this, target);
            finish();
            return;
        }

        var fragment = AutomatedAppLaunchWarningActivityFragment.newInstance(
                targetPackageName, target, automatingPackageName, resultReceiver);
        getSupportFragmentManager().beginTransaction().add(fragment, null).commit();
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (!validateAutomatedAppLaunchWarningIntent(intent)) {
            Log.i(TAG, "Skip warning, automation is no longer active");
            IntentSender target =
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, IntentSender.class);
            AutomatedAppLaunchWarningActivityFragment.startIntentSender(this, target);
            finish();
        }
    }

    private boolean validateAutomatedAppLaunchWarningIntent(Intent intent) {
        try {
            return mVirtualDeviceManager.validateAutomatedAppLaunchWarningIntent(intent);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to validate autoamted app launch warning intent", e);
            return false;
        }
    }

}
