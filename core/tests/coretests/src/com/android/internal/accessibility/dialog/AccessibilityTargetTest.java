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

package com.android.internal.accessibility.dialog;

import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.GESTURE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.HARDWARE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.accessibility.common.ShortcutConstants;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.stream.IntStream;

@RunWith(AndroidJUnit4.class)
public class AccessibilityTargetTest {
    private static final int[] EXPECTED_TYPES_GESTURE = { HARDWARE, SOFTWARE, GESTURE };

    @Test
    public void isRecognizedShortcutType_expectedType_gestureIncluded_isTrue() {
        for (int type : EXPECTED_TYPES_GESTURE) {
            if (!AccessibilityTarget.isRecognizedShortcutType(type)) {
                throw new AssertionError(
                        "Shortcut type " + type + " should be recognized");
            }
        }
    }

    @Test
    public void isRecognizedShortcutType_notExpectedType_gestureIncluded_isFalse() {
        for (int type: ShortcutConstants.USER_SHORTCUT_TYPES) {
            if (IntStream.of(EXPECTED_TYPES_GESTURE).noneMatch(x -> x == type)) {
                if (AccessibilityTarget.isRecognizedShortcutType(type)) {
                    throw new AssertionError(
                            "Shortcut type " + type + " should not be recognized");
                }
            }
        }
    }
}
