/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.vibrator;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibrator;

import org.junit.Test;

public class VibratorControllerTest extends HalVibratorTestCase {

    @Override
    HalVibrator newVibrator(int vibratorId) {
        return mHelper.newVibratorController(vibratorId);
    }

    @Test
    public void onSystemReady_loadSucceededOnInit_doesNotReload() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        HalVibrator vibrator = newVibrator(VIBRATOR_ID);

        vibrator.init(mCallbacksMock);
        assertThat(vibrator.getInfo().getCapabilities()).isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);

        mHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL); // will be ignored
        vibrator.onSystemReady();
        assertThat(vibrator.getInfo().getCapabilities()).isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
    }

    @Test
    public void onSystemReady_loadFailedOnInit_reloadsVibratorInfo() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.setLoadInfoToFail();
        HalVibrator vibrator = newVibrator(VIBRATOR_ID);

        vibrator.init(mCallbacksMock);
        assertThat(vibrator.getInfo().getCapabilities()).isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);

        mHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL); // will be loaded
        vibrator.onSystemReady();
        assertThat(vibrator.getInfo().getCapabilities()).isEqualTo(IVibrator.CAP_AMPLITUDE_CONTROL);
    }
}
