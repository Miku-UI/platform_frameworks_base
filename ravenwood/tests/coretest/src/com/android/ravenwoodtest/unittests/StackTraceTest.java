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
package com.android.ravenwoodtest.unittests;

import static com.android.ravenwood.common.StackTrace.applyStackTraceFilter;
import static com.android.ravenwood.common.StackTrace.buildStackFrameFilter;
import static com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTestBase.stripMultiLines;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.ravenwood.common.StackTrace;

import org.junit.Test;

/**
 * Test for {@link StackTrace} -- we only test the stack trace filtering logic (for now).
 */
public class StackTraceTest {
    /** Create a {@link StackTraceElement}. */
    public static StackTraceElement ste(Class<?> clazz, String methodName) {
        return new StackTraceElement(clazz.getName(), methodName, "xxx.java", 123);
    }

    /** Create a {@link StackTraceElement} array. */
    public static StackTraceElement[] stes(StackTraceElement... elements) {
        return elements;
    }

    /** Convert an array of {@link StackTraceElement}s into a string for assertion. */
    private static String str(StackTraceElement... elements) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : elements) {
            sb.append(element.getClassName());
            sb.append("#");
            sb.append(element.getMethodName());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Test for the stacktrace filtering mechanism.
     */
    @Test
    public void testStackTraceFilter() {
        var target = stes(
                ste(Intent.class, "method1"),
                ste(Intent.class, "method2"),
                ste(PackageManager.class, "method1"),
                ste(PackageManager.class, "method2"),
                ste(ActivityManager.class, "method1"),
                ste(ActivityManager.class, "method2")
        );
        {
            // Remove up to Intent, exclusive, so no frames should be removed.
            var r = applyStackTraceFilter(
                    buildStackFrameFilter(StackTrace.classPredicate(Intent.class), false),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.content.Intent#method1
                    android.content.Intent#method2
                    android.content.pm.PackageManager#method1
                    android.content.pm.PackageManager#method2
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }
        {
            // Remove up to Intent, inclusive.
            var r = applyStackTraceFilter(
                    buildStackFrameFilter(StackTrace.classPredicate(Intent.class), true),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.content.pm.PackageManager#method1
                    android.content.pm.PackageManager#method2
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }
        {
            // Remove up to PackageManager, exclusive, so no frames should be removed.
            var r = applyStackTraceFilter(
                    buildStackFrameFilter(StackTrace.classPredicate(PackageManager.class), false),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.content.pm.PackageManager#method1
                    android.content.pm.PackageManager#method2
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }
        {
            // Remove up to PackageManager, inclusive.
            var r = applyStackTraceFilter(
                    buildStackFrameFilter(StackTrace.classPredicate(PackageManager.class), true),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }

        // Filter doesn't match (exclusive), -> should return the original one.
        {
            var r = applyStackTraceFilter(
                    buildStackFrameFilter((ste) -> false, false),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.content.Intent#method1
                    android.content.Intent#method2
                    android.content.pm.PackageManager#method1
                    android.content.pm.PackageManager#method2
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }

        // Filter doesn't match (inclusive), -> should return the original one.
        {
            var r = applyStackTraceFilter(
                    buildStackFrameFilter((ste) -> false, true),
                    target);
            assertThat(str(r)).isEqualTo(stripMultiLines("""
                    android.content.Intent#method1
                    android.content.Intent#method2
                    android.content.pm.PackageManager#method1
                    android.content.pm.PackageManager#method2
                    android.app.ActivityManager#method1
                    android.app.ActivityManager#method2
                    """));
        }
    }
}
