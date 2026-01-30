/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.policy;

import static android.content.pm.PackageManager.FEATURE_LEANBACK;

import android.hardware.input.KeyGestureEvent;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import junitparams.JUnitParamsRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@MediumTest
@RunWith(JUnitParamsRunner.class)
public class KeyGestureEventTvTests extends ShortcutKeyTestBase {
    @Before
    public void setUp() {
        setUpPhoneWindowManager(
                /*supportSettingsUpdate*/ true, /*supportFeature*/ FEATURE_LEANBACK);
        mPhoneWindowManager.overrideLaunchHome();
        mPhoneWindowManager.overrideUserSetupComplete();
    }

    @Test
    public void testKeyGestureTvStopDreamingAndGoHome() {
        mPhoneWindowManager.overrideCanStartDreaming(true);
        mPhoneWindowManager.overrideIsDreaming(true);
        sendKeyGestureEventComplete(KeyGestureEvent.KEY_GESTURE_TYPE_HOME);
        mPhoneWindowManager.assertDreamStopped();
        mPhoneWindowManager.assertGoToHomescreen();
    }
}
