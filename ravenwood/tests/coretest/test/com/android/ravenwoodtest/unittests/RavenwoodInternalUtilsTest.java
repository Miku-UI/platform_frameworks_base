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

import static com.android.ravenwood.common.RavenwoodInternalUtils.parseClassNameWildcard;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import org.junit.Test;

public class RavenwoodInternalUtilsTest {

    private void checkParseClassNameWildcard(String pattern, String expected) {
        assertThat(parseClassNameWildcard(pattern).pattern()).isEqualTo(expected);
    }

    private void assertWildcardMatches(String pattern, String... expected) {
        var p = parseClassNameWildcard(pattern);
        for (var e : expected) {
            assertWithMessage("'" + pattern + "' should match '" + e + "'")
                    .that(p.matcher(e).matches()).isTrue();
        }
    }

    private void assertWildcardNotMatches(String pattern, String... expected) {
        var p = parseClassNameWildcard(pattern);
        for (var e : expected) {
            assertWithMessage("'" + pattern + "' should not match '" + e + "'")
                    .that(p.matcher(e).matches()).isFalse();
        }
    }

    @Test
    public void testParseClassNameWildcard() {
        checkParseClassNameWildcard("", "");
        checkParseClassNameWildcard("ab.cd.ef", "ab\\.cd\\.ef");
        checkParseClassNameWildcard("ab.c*d.ef", "ab\\.c[^.]*d\\.ef");
        checkParseClassNameWildcard("ab.c*d.e*f", "ab\\.c[^.]*d\\.e[^.]*f");
        checkParseClassNameWildcard("ab.c*d.**.e*f.**", "ab\\.c[^.]*d\\..*\\.e[^.]*f\\..*");

        assertWildcardMatches("a", "a");
        assertWildcardNotMatches("a", "", "aa", "package.a");

        assertWildcardMatches("a.b.c.*Test",
                "a.b.c.Test",
                "a.b.c.AbcTest",
                "a.b.c.DefTest"
        );

        assertWildcardNotMatches("a.b.c.*Test",
                "a.b.c.AbcTestX",
                "a.b.c.sub.DefTest"
        );

        assertWildcardMatches("**AbcTest",
                "AbcTest",
                "a.b.c.AbcTest"
        );

        assertWildcardNotMatches("**.AbcTest",
                "AbcTest",
                "a.b.c.AbcTestX"
        );

        // Use $ signs.
        assertWildcardMatches("a$b", "a$b");
        assertWildcardNotMatches("a$", "a");
    }

    private static void assertInvalidWildcard(String pattern) {
        try {
            parseClassNameWildcard(pattern);
            fail("Didn't throw IAE for " + pattern);
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("Invalid character found in wildcard");
        }
    }

    @Test
    public void testInvalidWildcard() {
        assertInvalidWildcard("abc@");
        assertInvalidWildcard("abc#abc");
    }
}
