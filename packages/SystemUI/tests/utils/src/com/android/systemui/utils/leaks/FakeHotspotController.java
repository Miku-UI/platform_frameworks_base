/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.utils.leaks;

import android.testing.LeakCheck;

import androidx.annotation.NonNull;

import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.HotspotController.Callback;

import java.util.ArrayList;
import java.util.List;

public class FakeHotspotController extends BaseLeakChecker<Callback> implements HotspotController {
    private boolean mHotspotEnabled = false;
    private final List<Callback> mCallbacks = new ArrayList<>();

    public FakeHotspotController(LeakCheck test) {
        super(test, "hotspot");
    }

    @Override
    public boolean isHotspotEnabled() {
        return mHotspotEnabled;
    }

    @Override
    public boolean isHotspotTransient() {
        return false;
    }

    @Override
    public void setHotspotEnabled(boolean enabled) {
        if (mHotspotEnabled != enabled) {
            mHotspotEnabled = enabled;
            fireHotspotEnabledChanged(mHotspotEnabled);
        }
    }

    @Override
    public boolean isHotspotSupported() {
        return false;
    }

    @Override
    public int getNumConnectedDevices() {
        return 0;
    }

    @Override
    public void addCallback(@NonNull HotspotController.Callback listener) {
        if (mCallbacks.contains(listener)) {
            return;
        }
        mCallbacks.add(listener);

        listener.onHotspotChanged(mHotspotEnabled, /* numDevices= */0);
    }

    @Override
    public void removeCallback(@NonNull HotspotController.Callback listener) {
        mCallbacks.remove(listener);
    }

    private void fireHotspotEnabledChanged(boolean enabled) {
        // Iterate over a copy in case of concurrent modification or reentrancy.
        for (HotspotController.Callback callback : new ArrayList<>(mCallbacks)) {
            callback.onHotspotChanged(enabled, /* numDevices= */ 0);
        }
    }

}