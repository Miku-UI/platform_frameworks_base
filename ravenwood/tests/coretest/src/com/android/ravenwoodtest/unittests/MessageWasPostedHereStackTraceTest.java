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

import static android.platform.test.ravenwood.MessageWasPostedHereStackTrace.ParentExtractor.extractNestParent;

import static com.android.ravenwoodtest.unittests.StackTraceTest.ste;
import static com.android.ravenwoodtest.unittests.StackTraceTest.stes;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.ravenwood.MessageWasPostedHereStackTrace;
import android.platform.test.ravenwood.MessageWasPostedHereStackTrace.ParentExtractor.ClassForTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test for {@link MessageWasPostedHereStackTrace} -- we only test the "duplicate stack removal"
 * logic (for now).
 */
public class MessageWasPostedHereStackTraceTest {
    /**
     * Build a chain
     */
    private static List<MessageWasPostedHereStackTrace> buildChain(
            StackTraceElement[]... elements) {

        var ret = new ArrayList<MessageWasPostedHereStackTrace>();

        MessageWasPostedHereStackTrace current = null;
        for (var e : elements) {
            current = new ClassForTest(current, e);
            ret.add(current);
        }
        return ret;
    }

    @Test
    public void testSimple() {
        var chain = buildChain(
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Unique
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Same as #0, so it should be skipped.
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                )
        );


        assertThat(extractNestParent(chain.getLast()))
                .isEqualTo(chain.get(1));
    }

    @Test
    public void testAllUnique3() {
        var chain = buildChain(
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Unique
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2")
                )
        );

        assertThat(extractNestParent(chain.getLast()))
                .isEqualTo(chain.get(2));
    }

    @Test
    public void testAllUnique1() {
        var chain = buildChain(
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                )
        );

        assertThat(extractNestParent(chain.getLast()))
                .isEqualTo(chain.get(0));
    }

    @Test
    public void testAlternating() {
        var chain = buildChain(
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Unique
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Same as #0
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Same as #1
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Same as #0
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Same as #1
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                )
        );

        assertThat(extractNestParent(chain.getLast()))
                .isEqualTo(chain.get(1));
    }

    @Test
    public void testAlternating_withUnique() {
        var chain = buildChain(
                stes( // Unique
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Unique
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Same as #0
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Same as #1
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                ),
                stes( // Same as #0
                        ste(Intent.class, "method1"),
                        ste(Intent.class, "method2"),
                        ste(Intent.class, "method3")
                ),
                stes( // Unique
                        ste(ActivityManager.class, "method1")
                ),
                stes( // Same as #1
                        ste(PackageManager.class, "method1"),
                        ste(PackageManager.class, "method2"),
                        ste(PackageManager.class, "method3")
                )
        );

        // We "peal offf" duplicate exceptions from the outer exceptions, so we
        // can only peal off the last one, even though there are more duplicates,
        // because we stop as soon as a unique one.
        // (In practice, we do the duplicate check for each exception, so we shouldn't have
        // any duplicate stacks in the result, but this could happen in the test because
        // we don't do the check when building a chain.)

        assertThat(extractNestParent(chain.getLast()))
                .isEqualTo(chain.get(5));
    }


}
