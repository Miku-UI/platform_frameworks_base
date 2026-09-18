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
package android.integration.multiuser;

import static android.integration.multiuser.ActivitiesHelper.assertCannotLaunchActivity;

import android.multiuser.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

/**
 * Simpler version of {@link HsuActivitiesAllowlistTest} - it doesn't set the allowlist, so it
 * doesn't require {@code adb root}.
 */
@RequiresFlagsEnabled(Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
public final class HsuActivitiesAllowlistNonRootTest {

    @Rule
    public final CheckFlagsRule flags = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testActivityLaunchIsNotAllowed() throws Exception {
        assertCannotLaunchActivity(Activity1.class);
        assertCannotLaunchActivity(Activity2.class);
    }
}
