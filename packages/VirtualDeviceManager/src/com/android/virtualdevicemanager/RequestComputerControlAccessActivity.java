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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

public class RequestComputerControlAccessActivity extends FragmentActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ResultReceiver resultReceiver = getIntent().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        if (resultReceiver == null) {
            finish();
            return;
        }

        String agentPackageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (agentPackageName == null) {
            resultReceiver.send(Activity.RESULT_CANCELED, null);
            finish();
            return;
        }

        var fragment =
                RequestComputerControlAccessFragment.newInstance(agentPackageName, resultReceiver);
        getSupportFragmentManager().beginTransaction().add(fragment, null).commit();
    }
}
