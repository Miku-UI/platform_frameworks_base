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

package android.view;

import static com.google.common.truth.Truth.assertThat;

import android.os.PowerManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.accessibility.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link WindowManagerPolicyConstants}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class WindowManagerPolicyConstantsTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(Flags.FLAG_FIX_A11Y_LOCK_SCREEN_JANK)
    public void testTranslateSleepReasonToOffReason() {
        assertThat(WindowManagerPolicyConstants.translateSleepReasonToOffReason(
                        PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN))
                .isEqualTo(WindowManagerPolicyConstants.OFF_BECAUSE_OF_ADMIN);

        assertThat(WindowManagerPolicyConstants.translateSleepReasonToOffReason(
                        PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON))
                .isEqualTo(WindowManagerPolicyConstants.OFF_BECAUSE_OF_USER);

        // Accessibility sleep reason falls back to user reason.
        assertThat(WindowManagerPolicyConstants.translateSleepReasonToOffReason(
                        PowerManager.GO_TO_SLEEP_REASON_ACCESSIBILITY))
                .isEqualTo(WindowManagerPolicyConstants.OFF_BECAUSE_OF_USER);
    }
}
