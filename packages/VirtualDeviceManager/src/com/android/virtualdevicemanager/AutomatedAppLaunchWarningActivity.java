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

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

public class AutomatedAppLaunchWarningActivity extends FragmentActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        IntentSender target = intent.getParcelableExtra(Intent.EXTRA_INTENT, IntentSender.class);
        String automatingPackageName = intent.getStringExtra(EXTRA_AUTOMATING_PACKAGE_NAME);
        ResultReceiver resultReceiver = getIntent().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

        if (targetPackageName == null || target == null || automatingPackageName == null
                || resultReceiver == null) {
            finish();
            return;
        }

        var fragment = AutomatedAppLaunchWarningActivityFragment.newInstance(
                targetPackageName, target, automatingPackageName, resultReceiver);
        getSupportFragmentManager().beginTransaction().add(fragment, null).commit();
    }
}
