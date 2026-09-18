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

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class RequestComputerControlAccessActivity extends FragmentActivity {

    private static final String TAG = "ComputerControlAccessActivity";
    private ResultReceiver mResultReceiver;
    private int mAgentUid;
    private String mAgentPackageName;
    private final Queue<String> mRemainingTargets = new ArrayDeque<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mResultReceiver = getIntent().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        if (mResultReceiver == null) {
            finish();
            return;
        }

        mAgentUid = getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        mAgentPackageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (mAgentPackageName == null) {
            mResultReceiver.send(Activity.RESULT_CANCELED, null);
            finish();
            return;
        }

        if (android.companion.virtualdevice.flags.Flags.computerControlPerAppConsent()) {
            String[] targetPackageNames = getIntent().getStringArrayExtra(Intent.EXTRA_PACKAGES);
            if (targetPackageNames == null || targetPackageNames.length == 0) {
                mResultReceiver.send(Activity.RESULT_CANCELED, null);
                finish();
                return;
            }

            if (savedInstanceState == null) {
                mRemainingTargets.addAll(Arrays.asList(targetPackageNames));
                showNextTargetAppConsentDialog();
            }
        } else {
            if (savedInstanceState == null) {
                showGlobalConsentDialog();
            }
        }
    }

    private void showNextTargetAppConsentDialog() {
        String nextTarget = mRemainingTargets.poll();
        if (nextTarget == null) {
            mResultReceiver.send(Activity.RESULT_OK, null);
            finish();
            return;
        }

        ResultReceiver fragmentReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == Activity.RESULT_OK) {
                    showNextTargetAppConsentDialog();
                } else {
                    mResultReceiver.send(Activity.RESULT_CANCELED, null);
                    finish();
                }
            }
        };

        var fragment = RequestComputerControlAccessFragment.newInstance(mAgentUid,
                mAgentPackageName, nextTarget, fragmentReceiver);
        fragment.show(getSupportFragmentManager(), TAG);
    }

    private void showGlobalConsentDialog() {
        ResultReceiver fragmentReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                mResultReceiver.send(resultCode, null);
                finish();
            }
        };

        var fragment = RequestComputerControlAccessFragment.newInstance(
                mAgentUid, mAgentPackageName, null, fragmentReceiver);
        fragment.show(getSupportFragmentManager(), TAG);
    }
}
