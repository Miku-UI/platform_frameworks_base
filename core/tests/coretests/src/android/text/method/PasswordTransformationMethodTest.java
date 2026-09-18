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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.text.ShowSecretsSetting;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.widget.EditText;

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
public class PasswordTransformationMethodTest {

    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    public final TestRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mSetFlagsRule).around(mCompatChangeRule);

    private Context mContext;
    private ContentResolver mResolver;
    private PasswordTransformationMethod mMethod;
    private EditText mEditText;

    @Before
    public void setUp() {
        TextKeyListener.getInstance().release();

        mContext = ApplicationProvider.getApplicationContext();
        mResolver = mContext.getContentResolver();
        mMethod = PasswordTransformationMethod.getInstance();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mEditText = new EditText(mContext);
                        });
    }

    @After
    public void tearDown() {
        TextKeyListener.getInstance().release();
        Settings.System.putString(mResolver, Settings.System.TEXT_SHOW_PASSWORD, null);
        Settings.Secure.putString(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, null);
        Settings.Secure.putString(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, null);
    }

    private void setPasswordsVisibility(boolean legacy, boolean touch, boolean physical) {
        Settings.System.putInt(mResolver, Settings.System.TEXT_SHOW_PASSWORD, legacy ? 1 : 0);
        Settings.Secure.putInt(mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH, touch ? 1 : 0);
        Settings.Secure.putInt(
                mResolver, Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL, physical ? 1 : 0);
    }

    @Test
    @DisableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @DisableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnTextChanged_GuardsDisabled_UsesLegacySetting() {
        setPasswordsVisibility(true, false, false);

        final SpannableStringBuilder s = new SpannableStringBuilder("a");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                            mMethod.onTextChanged(s, 0, 0, 1);
                        });

        assertTrue(hasVisibleSpan(s));
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnTextChanged_GuardsEnabled_NoSpan_UsesTouchSetting() {
        setPasswordsVisibility(false, true, false);

        final SpannableStringBuilder s = new SpannableStringBuilder("a");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                            mMethod.onTextChanged(s, 0, 0, 1);
                        });

        assertTrue(hasVisibleSpan(s));
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnTextChanged_GuardsEnabled_WithPhysicalSpan_UsesPhysicalSetting() {
        setPasswordsVisibility(false, false, true);

        final SpannableStringBuilder s = new SpannableStringBuilder("a");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                            s.setSpan(
                                    new BaseKeyListener.PhysicalInputSpan(),
                                    0,
                                    1,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            mMethod.onTextChanged(s, 0, 0, 1);
                        });

        assertTrue(hasVisibleSpan(s));
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnTextChanged_GuardsEnabled_MixedInput_UsesCorrectSettings() {
        // Touch is ON, Physical is OFF
        setPasswordsVisibility(false, true, false);

        final String text = "Password123!";
        // Arbitrary mixed pattern of input types (True = Physical, False = Touch)
        final boolean[] isPhysicalInput = {
            true, false, false, true, false, true, true, true, false, false, true, false
        };
        final SpannableStringBuilder s = new SpannableStringBuilder();

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                        });

        for (int i = 0; i < text.length(); i++) {
            final int index = i;
            final char c = text.charAt(i);
            final boolean isPhysical = isPhysicalInput[i];

            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(
                            () -> {
                                s.append(c);
                                if (isPhysical) {
                                    s.setSpan(
                                            new BaseKeyListener.PhysicalInputSpan(),
                                            index,
                                            index + 1,
                                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                mMethod.onTextChanged(s, index, 0, 1);
                            });

            final boolean isVisible = !isPhysical;
            assertEquals(
                    "Visibility mismatch for char '" + c + "' at " + index,
                    isVisible,
                    hasVisibleSpan(s, index, index + 1));

            // Verify previous characters are not visible.
            // Note: If the current character is NOT visible, we don't clear previous spans.
            if (isVisible && index > 0) {
                assertFalse(
                        "Previous chars should not be visible at index " + index,
                        hasVisibleSpan(s, 0, index));
            }
        }
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnTextChanged_MultipleCharactersInserted_NoVisibleSpanAdded() {
        setPasswordsVisibility(false, true, false);

        final SpannableStringBuilder s = new SpannableStringBuilder();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                            s.append("abc");
                            mMethod.onTextChanged(s, 0, 0, 3);
                        });

        assertFalse(hasVisibleSpan(s));
    }

    @Test
    @EnableCompatChanges({ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL})
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testPhysicalInputSpan_OnReplace_DoesNotExpand() {
        setPasswordsVisibility(false, true, false);

        final SpannableStringBuilder s = new SpannableStringBuilder("test");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mMethod.getTransformation(s, mEditText);
                            s.setSpan(mMethod, 0, s.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "A", 1, 0);
                            BaseKeyListener.replaceText(s, 0, 4, "A", 0, 1, event);
                        });

        assertFalse(hasVisibleSpan(s));
    }

    private boolean hasVisibleSpan(Spannable s) {
        return hasVisibleSpan(s, 0, s.length());
    }

    private boolean hasVisibleSpan(Spannable s, int start, int end) {
        Object[] spans = s.getSpans(start, end, Object.class);
        for (Object span : spans) {
            if (span instanceof PasswordTransformationMethod.Visible) {
                return true;
            }
        }
        return false;
    }
}
