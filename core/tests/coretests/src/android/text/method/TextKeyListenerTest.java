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

package android.text.method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.text.ShowSecretsSetting;
import android.util.PollingCheck;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.text.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@SmallTest
@DisabledOnRavenwood(
        blockedBy = ContentResolver.class,
        reason = "API (un)registerContentObserver is missing")
public class TextKeyListenerTest {

    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    public final TestRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mSetFlagsRule).around(mCompatChangeRule);

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() {
        TextKeyListener.getInstance().release();
        Settings.System.putString(mResolver, Settings.System.TEXT_SHOW_PASSWORD, null);
        Settings.Secure.putString(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, null);
        Settings.Secure.putString(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, null);
    }

    @Test
    @DisableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    public void testGuardsDisabled_OnlySetsShowPassword() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mSetFlagsRule.disableFlags(
                                    Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL);

                            Settings.System.putInt(
                                    mResolver, Settings.System.TEXT_SHOW_PASSWORD, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 1);

                            TextKeyListener listener = TextKeyListener.getInstance();
                            int prefs = listener.getPrefs(mContext);

                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);
                        });
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    public void testGuardsEnabled_SetsAll() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mSetFlagsRule.enableFlags(
                                    Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL);

                            Settings.System.putInt(
                                    mResolver, Settings.System.TEXT_SHOW_PASSWORD, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 1);

                            TextKeyListener listener = TextKeyListener.getInstance();
                            int prefs = listener.getPrefs(mContext);

                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);
                        });
    }

    @Test
    @DisableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    public void testFlagEnabled_CompatDisabled_OnlySetsShowPassword() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mSetFlagsRule.enableFlags(
                                    Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL);

                            Settings.System.putInt(
                                    mResolver, Settings.System.TEXT_SHOW_PASSWORD, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 1);

                            TextKeyListener listener = TextKeyListener.getInstance();
                            int prefs = listener.getPrefs(mContext);

                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);
                        });
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    public void testFlagDisabled_CompatEnabled_OnlySetsShowPassword() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mSetFlagsRule.disableFlags(
                                    Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL);

                            Settings.System.putInt(
                                    mResolver, Settings.System.TEXT_SHOW_PASSWORD, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 1);
                            Settings.Secure.putInt(
                                    mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 1);

                            TextKeyListener listener = TextKeyListener.getInstance();
                            int prefs = listener.getPrefs(mContext);

                            assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
                            assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);
                        });
    }

    private static final long POLLING_TIMEOUT_MS = 10000;

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    public void testDynamicUpdate() {
        mSetFlagsRule.enableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL);

        // Initialize listener on Main Thread to ensure SettingsObserver attaches to Main Looper
        // which is used in teardown.
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            TextKeyListener.getInstance().getPrefs(mContext);
                        });

        Settings.System.putInt(mResolver, Settings.System.TEXT_SHOW_PASSWORD, 0);
        Settings.Secure.putInt(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 0);
        Settings.Secure.putInt(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 0);

        TextKeyListener listener = TextKeyListener.getInstance();
        PollingCheck.waitFor(
                POLLING_TIMEOUT_MS,
                () -> {
                    int currentPrefs = listener.getPrefs(mContext);
                    return (currentPrefs & TextKeyListener.SHOW_PASSWORD) == 0
                            && (currentPrefs & TextKeyListener.SHOW_PASSWORD_TOUCH) == 0
                            && (currentPrefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL) == 0;
                });

        Settings.System.putInt(mResolver, Settings.System.TEXT_SHOW_PASSWORD, 1);
        PollingCheck.waitFor(
                POLLING_TIMEOUT_MS,
                () -> {
                    int currentPrefs = listener.getPrefs(mContext);
                    return (currentPrefs & TextKeyListener.SHOW_PASSWORD) != 0;
                });
        int prefs = listener.getPrefs(mContext);
        assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
        assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);

        Settings.Secure.putInt(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, 1);
        PollingCheck.waitFor(
                POLLING_TIMEOUT_MS,
                () -> {
                    int currentPrefs = listener.getPrefs(mContext);
                    return (currentPrefs & TextKeyListener.SHOW_PASSWORD_TOUCH) != 0;
                });
        prefs = listener.getPrefs(mContext);
        assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
        assertEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL);

        Settings.Secure.putInt(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, 1);
        PollingCheck.waitFor(
                POLLING_TIMEOUT_MS,
                () -> {
                    int currentPrefs = listener.getPrefs(mContext);
                    return (currentPrefs & TextKeyListener.SHOW_PASSWORD_PHYSICAL) != 0;
                });
        prefs = listener.getPrefs(mContext);
        assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD);
        assertNotEquals(0, prefs & TextKeyListener.SHOW_PASSWORD_TOUCH);
    }
}
