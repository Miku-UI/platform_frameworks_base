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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN;
import static android.content.pm.ActivityInfo.CONFIG_NAVIGATION;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.content.pm.ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.android.window.flags.Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link AppCompatRecreateOnConfigChangePolicy}.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatRecreateOnConfigChangePolicyTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatRecreateOnConfigChangePolicyTest extends WindowTestsBase {
    @Mock private Context mPackageContext; // App context
    @Mock private Resources mResources;

    private AppCompatRecreateOnConfigChangePolicy mPolicy;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        ActivityRecord activityRecord = new ActivityBuilder(mAtm).build();
        mPolicy = spy(new AppCompatRecreateOnConfigChangePolicy(activityRecord));

        doReturn(mPackageContext).when(mAtm.mContext)
                .createPackageContextAsUser(anyString(), anyInt(), any(UserHandle.class));
        doReturn(mResources).when(mPackageContext).getResources();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testGetRecreateConfigMask_undefined_equalToZero() {
        Configuration config = new Configuration();
        config.keyboard = Configuration.KEYBOARD_UNDEFINED;
        config.keyboardHidden = Configuration.KEYBOARDHIDDEN_UNDEFINED;
        config.navigation = Configuration.NAVIGATION_UNDEFINED;
        config.touchscreen = Configuration.TOUCHSCREEN_UNDEFINED;
        config.colorMode = Configuration.COLOR_MODE_UNDEFINED;
        doReturn(new Configuration[]{config}).when(mResources).getResourceConfigurations();

        int result = mPolicy.getRecreateConfigMask();
        assertEquals(0, result & CONFIG_KEYBOARD);
        assertEquals(0, result & CONFIG_KEYBOARD_HIDDEN);
        assertEquals(0, result & CONFIG_NAVIGATION);
        assertEquals(0, result & CONFIG_TOUCHSCREEN);
        assertEquals(0, result & CONFIG_COLOR_MODE);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testGetRecreateConfigMask_defined_notEqualToZero() {
        Configuration config = new Configuration();
        config.keyboard = Configuration.KEYBOARD_QWERTY;
        config.keyboardHidden = Configuration.KEYBOARDHIDDEN_NO;
        config.navigation = Configuration.NAVIGATION_DPAD;
        config.touchscreen = Configuration.TOUCHSCREEN_FINGER;
        config.colorMode = Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_YES;
        doReturn(new Configuration[]{config}).when(mResources).getResourceConfigurations();

        int result = mPolicy.getRecreateConfigMask();
        assertNotEquals(0, result & CONFIG_KEYBOARD);
        assertNotEquals(0, result & CONFIG_KEYBOARD_HIDDEN);
        assertNotEquals(0, result & CONFIG_NAVIGATION);
        assertNotEquals(0, result & CONFIG_TOUCHSCREEN);
        assertNotEquals(0, result & CONFIG_COLOR_MODE);
    }
}
