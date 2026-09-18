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
import static org.junit.Assert.assertNotSame;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.Editable;
import android.text.Selection;
import android.text.ShowSecretsSetting;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.BaseKeyListener.PhysicalInputSpan;
import android.text.method.TextKeyListener.Capitalize;
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

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = KeyEvent.class, reason = "android.view.KeyEvent.<init> is missing")
public class MultiTapKeyListenerTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final PlatformCompatChangeRule mPlatformCompatChangeRule =
            new PlatformCompatChangeRule();

    private MultiTapKeyListener mListener;
    private Editable mEditable;

    @Before
    public void setUp() {
        mListener = MultiTapKeyListener.getInstance(false, Capitalize.NONE);
        mEditable = new SpannableStringBuilder("");
        Selection.setSelection(mEditable, 0);
    }

    @Test
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testPhysicalKey_addsSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent event =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, event);

                            // Pressing 2 once on a T-9 style keyboard produces a lowercase a.
                            assertEquals("a", mEditable.toString());
                            assertEquals(1, watcher.mAddedSpans.size());
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 1, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testVirtualKey_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent event =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            -1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, event);

                            assertEquals("a", mEditable.toString());
                            assertEquals(0, watcher.mAddedSpans.size());
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 1, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testMultiplePhysicalKeys_addsDistinctSpans() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent eventA =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, eventA);

                            KeyEvent eventB =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_3,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_3, eventB);

                            assertEquals("ad", mEditable.toString());
                            assertEquals(2, watcher.mAddedSpans.size());
                            assertNotSame(watcher.mAddedSpans.get(0), watcher.mAddedSpans.get(1));

                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 2, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @DisableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @DisableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testPhysicalKey_flagDisabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent event =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, event);

                            assertEquals("a", mEditable.toString());
                            assertEquals(0, watcher.mAddedSpans.size());
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 1, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @DisableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @EnableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testPhysicalKey_flagEnabled_changeDisabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent event =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, event);

                            assertEquals("a", mEditable.toString());
                            assertEquals(0, watcher.mAddedSpans.size());
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 1, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    @Test
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    @DisableFlags(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testPhysicalKey_flagDisabled_changeEnabled_doesNotAddSpan() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            RecordingSpanWatcher watcher = new RecordingSpanWatcher();
                            mEditable.setSpan(watcher, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            KeyEvent event =
                                    new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_2,
                                            0,
                                            0,
                                            1,
                                            0);
                            mListener.onKeyDown(null, mEditable, KeyEvent.KEYCODE_2, event);

                            assertEquals("a", mEditable.toString());
                            assertEquals(0, watcher.mAddedSpans.size());
                            PhysicalInputSpan[] spans =
                                    mEditable.getSpans(0, 1, PhysicalInputSpan.class);
                            assertEquals(0, spans.length);
                        });
    }

    private static class RecordingSpanWatcher implements SpanWatcher {
        final List<Object> mAddedSpans = new ArrayList<>();

        @Override
        public void onSpanAdded(Spannable text, Object what, int start, int end) {
            if (what instanceof PhysicalInputSpan) {
                mAddedSpans.add(what);
            }
        }

        @Override
        public void onSpanRemoved(Spannable text, Object what, int start, int end) {}

        @Override
        public void onSpanChanged(
                Spannable text, Object what, int ostart, int oend, int nstart, int nend) {}
    }
}
