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

package androidx.window.extensions.embedding;

import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.AppGlobals;
import android.app.compat.CompatChanges;
import android.content.ComponentName;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.hardware.input.InputManagerGlobal;
import android.platform.test.annotations.Presubmit;
import android.view.InputDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

/**
 * Test class for {@link AppCompatEmbeddingRuleController}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:AppCompatEmbeddingRuleControllerTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppCompatEmbeddingRuleControllerTest {

    @Mock
    private IPackageManager mMockPackageManager;

    @Mock
    private InputManagerGlobal mMockInputManagerGlobal;

    private StaticMockitoSession mMockSession;

    @Before
    public void setUp() {
        mMockSession =
                ExtendedMockito.mockitoSession()
                        .initMocks(this)
                        .mockStatic(AppGlobals.class)
                        .mockStatic(CompatChanges.class)
                        .mockStatic(InputManagerGlobal.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        when(AppGlobals.getPackageManager()).thenReturn(mMockPackageManager);
        when(CompatChanges.isChangeEnabled(
                eq(OVERRIDE_ENABLE_VIRTUAL_GAMEPAD))).thenReturn(true);
        when(InputManagerGlobal.getInstance()).thenReturn(mMockInputManagerGlobal);
        when(mMockInputManagerGlobal.getInputDeviceIds()).thenReturn(new int[0]);
    }

    @After
    public void tearDown() {
        mMockSession.finishMocking();
    }

    @Test
    public void testCreateVirtualGamepadOverrideRule() {
        final EmbeddingRule embeddingRule =
                AppCompatEmbeddingRuleController.createVirtualGamepadOverrideRule(
                        "package", "activity", 100, "self-package", 1000, 10000);
        assertNotNull(embeddingRule);
        assertTrue(embeddingRule instanceof SplitPlaceholderRule);

        final SplitPlaceholderRule placeholderRule = (SplitPlaceholderRule) embeddingRule;
        final ComponentName componentName = placeholderRule.getPlaceholderIntent().getComponent();
        assertNotNull(componentName);
        assertEquals("package", componentName.getPackageName());
        assertEquals("activity", componentName.getClassName());
    }

    @Test
    public void testCreateVirtualGamepadOverrideRule_emptyConfig() {
        final EmbeddingRule embeddingRule =
                AppCompatEmbeddingRuleController.createVirtualGamepadOverrideRule(
                        "" /* packageName */, "" /* activityName */, 100, "self-package", 1000,
                        10000);
        assertNull(embeddingRule);
    }

    @Test
    public void testVirtualGamepadOptOutSessionStickiness() throws Exception {
        AppCompatEmbeddingRuleController.sIsVirtualGamepadRuleEnabled = true;
        AppCompatEmbeddingRuleController.sIsVirtualGamepadOptOutSeenInSession = false;

        // 1. Initial state: UNSET -> returns true
        when(mMockPackageManager.getVirtualGamepadUserOption(anyString(), anyInt()))
                .thenReturn(PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_UNSET);
        assertTrue(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));

        // 2. OPT_OUT -> returns false, sets session flag
        when(mMockPackageManager.getVirtualGamepadUserOption(anyString(), anyInt()))
                .thenReturn(PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_OPT_OUT);
        assertFalse(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));
        assertTrue(AppCompatEmbeddingRuleController.sIsVirtualGamepadOptOutSeenInSession);

        // 3. UNSET again -> returns false (sticky)
        when(mMockPackageManager.getVirtualGamepadUserOption(anyString(), anyInt()))
                .thenReturn(PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_UNSET);
        assertFalse(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));
    }

    @Test
    public void testIsVirtualGamepadEnabled_physicalGamepadConnected() throws Exception {
        AppCompatEmbeddingRuleController.sIsVirtualGamepadRuleEnabled = true;
        AppCompatEmbeddingRuleController.sIsVirtualGamepadOptOutSeenInSession = false;
        when(mMockPackageManager.getVirtualGamepadUserOption(anyString(), anyInt()))
                .thenReturn(PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_UNSET);

        // 1. No physical gamepad -> returns true
        when(mMockInputManagerGlobal.getInputDeviceIds()).thenReturn(new int[0]);
        assertTrue(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));

        // 2. Physical gamepad connected -> returns false
        final int deviceId = 1;
        when(mMockInputManagerGlobal.getInputDeviceIds()).thenReturn(new int[]{deviceId});
        final InputDevice mockDevice = mock(InputDevice.class);
        when(mockDevice.isVirtual()).thenReturn(false);
        when(mockDevice.getSources()).thenReturn(InputDevice.SOURCE_GAMEPAD);
        when(mMockInputManagerGlobal.getInputDevice(deviceId)).thenReturn(mockDevice);

        assertFalse(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));

        // 3. Virtual gamepad connected (should be ignored) -> returns true
        when(mockDevice.isVirtual()).thenReturn(true);
        assertTrue(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));

        // 4. Physical joystick connected -> returns false
        when(mockDevice.isVirtual()).thenReturn(false);
        when(mockDevice.getSources()).thenReturn(InputDevice.SOURCE_JOYSTICK);
        assertFalse(AppCompatEmbeddingRuleController.isVirtualGamepadEnabled("pkg", 0));
    }
}
