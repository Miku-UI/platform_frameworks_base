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

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.ShowSecretsSetting;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.BaseKeyListener.PhysicalInputSpan;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.text.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = KeyEvent.class, reason = "android.view.KeyEvent.<init> is missing")
public class BaseKeyListenerTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final PlatformCompatChangeRule mPlatformCompatChangeRule =
            new PlatformCompatChangeRule();

    private BaseKeyListener mListener;
    private Editable mEditable;

    @Before
    public void setUp() {
        mListener = new TestBaseKeyListener();
        mEditable = new SpannableStringBuilder("");
        Selection.setSelection(mEditable, 0);
    }

    @Test
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnKeyOther_physicalKey_addsSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "text", 1, 0);

                            mListener.onKeyOther(null, mEditable, event);

                            assertEquals("text", mEditable.toString());
                            assertEquals(1, watcher.mAddedCount);
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 4, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnKeyOther_virtualKey_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "text", -1, 0);

                            mListener.onKeyOther(null, mEditable, event);

                            assertEquals("text", mEditable.toString());
                            assertEquals(0, watcher.mAddedCount);
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 4, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @DisableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @DisableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnKeyOther_physicalKey_flagDisabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "text", 1, 0);

                            mListener.onKeyOther(null, mEditable, event);

                            assertEquals("text", mEditable.toString());
                            assertEquals(0, watcher.mAddedCount);
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 4, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @DisableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnKeyOther_physicalKey_flagEnabled_changeDisabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "text", 1, 0);

                            mListener.onKeyOther(null, mEditable, event);

                            assertEquals("text", mEditable.toString());
                            assertEquals(0, watcher.mAddedCount);
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 4, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @DisableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testOnKeyOther_physicalKey_flagDisabled_changeEnabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            long now = System.currentTimeMillis();
                            KeyEvent event = new KeyEvent(now, "text", 1, 0);

                            mListener.onKeyOther(null, mEditable, event);

                            assertEquals("text", mEditable.toString());
                            assertEquals(0, watcher.mAddedCount);
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 4, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    private static class TestBaseKeyListener extends BaseKeyListener {
        @Override
        public int getInputType() {
            return InputType.TYPE_CLASS_TEXT;
        }
    }

    private static class RecordingSpanWatcher implements SpanWatcher {
        int mAddedCount = 0;

        @Override
        public void onSpanAdded(Spannable text, Object what, int start, int end) {
            if (what instanceof PhysicalInputSpan) {
                mAddedCount++;
            }
        }

        @Override
        public void onSpanRemoved(Spannable text, Object what, int start, int end) {}

        @Override
        public void onSpanChanged(
                Spannable text, Object what, int ostart, int oend, int nstart, int nend) {}
    }
}
