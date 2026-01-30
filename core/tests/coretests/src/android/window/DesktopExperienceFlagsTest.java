/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.window;

import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
import static com.android.window.flags.Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.support.test.uiautomator.UiDevice;
import android.window.DesktopExperienceFlags.DesktopExperienceFlag;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Test class for {@link android.window.DesktopExperienceFlags}
 *
 * <p>Build/Install/Run: atest FrameworksCoreTests:DesktopExperienceFlagsTest
 */
@SmallTest
@Presubmit
@RunWith(ParameterizedAndroidJunit4.class)
@DisableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
public class DesktopExperienceFlagsTest {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION);
    }

    @Rule
    public SetFlagsRule mSetFlagsRule;

    private UiDevice mUiDevice;
    private boolean mLocalFlagValue = false;
    private final DesktopExperienceFlag mOverriddenLocalFlag =
            new DesktopExperienceFlag(() -> mLocalFlagValue, true, "OverriddenFlag");
    private final DesktopExperienceFlag mNotOverriddenLocalFlag =
            new DesktopExperienceFlag(() -> mLocalFlagValue, false, "NotOverriddenFlag");

    private static final String OVERRIDE_OFF_SETTING = "0";
    private static final String OVERRIDE_ON_SETTING = "1";
    private Map<String, String> mInitialSyspropValues = new HashMap<>();

    private Context mMockContext;
    private Resources mMockResources;

    public DesktopExperienceFlagsTest(FlagsParameterization flags) {
        mSetFlagsRule = new SetFlagsRule(flags);
    }

    @Before
    public void setUp() throws Exception {
        mInitialSyspropValues.clear();

        mMockResources = mock(Resources.class);
        mMockContext = mock(Context.class);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        setDesktopModeSupported(false);

        // Set the application context to the mock one
        Field cachedToggleOverride =
                DesktopExperienceFlags.class.getDeclaredField("sApplicationContext");
        cachedToggleOverride.setAccessible(true);
        cachedToggleOverride.set(null, mMockContext);

        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, null);
    }

    @After
    public void tearDown() throws Exception {
        resetCache();
        resetSysProps();
    }

    @Test
    public void isTrue_overrideOff_featureFlagOn_returnsTrue() throws Exception {
        mLocalFlagValue = true;
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, OVERRIDE_OFF_SETTING);

        assertThat(mOverriddenLocalFlag.isTrue()).isTrue();
        assertThat(mNotOverriddenLocalFlag.isTrue()).isTrue();
    }

    @Test
    public void isTrue_overrideOn_featureFlagOn_returnsTrue() throws Exception {
        mLocalFlagValue = true;
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, OVERRIDE_ON_SETTING);

        assertThat(mOverriddenLocalFlag.isTrue()).isTrue();
        assertThat(mNotOverriddenLocalFlag.isTrue()).isTrue();
    }

    @Test
    public void isTrue_overrideOff_featureFlagOff_returnsFalse() throws Exception {
        mLocalFlagValue = false;
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, OVERRIDE_OFF_SETTING);

        assertThat(mOverriddenLocalFlag.isTrue()).isFalse();
        assertThat(mNotOverriddenLocalFlag.isTrue()).isFalse();
    }

    @Test
    public void isTrue_devOptionEnabled_overrideOn_featureFlagOff() throws Exception {
        assumeTrue(Flags.showDesktopExperienceDevOption());
        setDesktopModeSupported(true);
        mLocalFlagValue = false;
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, OVERRIDE_ON_SETTING);

        assertThat(mOverriddenLocalFlag.isTrue()).isTrue();
        assertThat(mNotOverriddenLocalFlag.isTrue()).isFalse();
    }

    @Test
    public void isTrue_devOptionDisabled_overrideOn_featureFlagOff_returnsFalse() throws Exception {
        assumeFalse(Flags.showDesktopExperienceDevOption());
        mLocalFlagValue = false;
        setSysProp(DesktopExperienceFlags.SYSTEM_PROPERTY_NAME, OVERRIDE_ON_SETTING);

        assertThat(mOverriddenLocalFlag.isTrue()).isFalse();
        assertThat(mNotOverriddenLocalFlag.isTrue()).isFalse();
    }

    private void setSysProp(String name, String value) throws Exception {
        if (!mInitialSyspropValues.containsKey(name)) {
            String initialValue = mUiDevice.executeShellCommand("getprop " + name).trim();
            mInitialSyspropValues.put(name, initialValue);
        }
        setSysPropUnchecked(name, Objects.requireNonNullElse(value, "''"));
    }

    private void setSysPropUnchecked(String name, String value) throws Exception {
        mUiDevice.executeShellCommand("setprop " + name + " " + value);
    }

    private void resetSysProps() throws Exception {
        for (Map.Entry<String, String> entry : mInitialSyspropValues.entrySet()) {
            setSysPropUnchecked(entry.getKey(), entry.getValue());
        }
        mInitialSyspropValues.clear();
    }

    private void resetCache() throws Exception {
        Field cachedToggleOverride =
                DesktopExperienceFlags.class.getDeclaredField("sCachedToggleOverride");
        cachedToggleOverride.setAccessible(true);
        cachedToggleOverride.set(null, null);
    }

    private void setDesktopModeSupported(boolean isSupported) {
        when(mMockResources.getBoolean(eq(R.bool.config_isDesktopModeSupported))).thenReturn(
                isSupported);
    }
}
