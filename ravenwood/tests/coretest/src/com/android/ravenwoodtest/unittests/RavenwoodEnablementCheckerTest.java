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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.platform.test.ravenwood.RavenwoodEnablementChecker;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.PolicyChecker;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.RunMode;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.Callable;

public class RavenwoodEnablementCheckerTest {
    /**
     * Resets the singleton instance to its default state after each test.
     * This is important because some tests modify the singleton via `overrideInstance`.
     */
    @After
    public void tearDown() {
        RavenwoodEnablementChecker.setDefaultInstance();
    }

    private static PolicyChecker parse(String text) throws Exception {
        return parse(text, /* ignoreLargeTests= */ false);
    }

    private static PolicyChecker parse(String text, boolean ignoreLargeTests) throws Exception {
        return RavenwoodEnablementChecker.getTextPolicyCheckerForTest(
                "[filename]", text, ignoreLargeTests);
    }

    private static <T> T expectException(String message, Callable<T> c) throws Exception {
        T ret = null;
        try {
            ret = c.call();
            fail("Didn't throw exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains(message);
        }
        return ret;
    }

    @Test
    public void testNoHeader() throws Exception {
        expectException("File is empty", () -> parse(""));
    }

    @Test
    public void testBadHeader() throws Exception {
        expectException("File must start with", () -> parse("x"));
        expectException("File must start with", () -> parse("!module"));
        expectException("File must start with", () -> parse("!module x y"));
    }

    @Test
    public void testBadOption1() throws Exception {
        expectException("Unknown option", () -> parse("""
!module RavenwoodCoreTest
f1 enable axy
"""));
    }

    @Test
    public void testBadOption2() throws Exception {
        expectException("Unknown option", () -> parse("""
!module RavenwoodCoreTest
f1 enable :LAR
"""));
    }

    @Test
    public void testBadOption3() throws Exception {
        expectException("Unknown option", () -> parse("""
!module RavenwoodCoreTest
f1 enable :large :badoption
"""));
    }

    @Test
    public void testBadButNotTarget() throws Exception {
        // The file contains a bad lne, but the module name is different, so it won't be parsed.
        parse("""
!module RandomAnotherTestModule
f1 enable :xx # bad option
""");
    }

    @Test
    public void testInvalidWildcard() throws Exception {
        parse("""
!module RandomAnotherTestModule
x@ # Invalid char, but this file should be ignored.
""");
    }

    /**
     * Verifies that wouldRunDisabledTests() returns false when the checker is in Normal run mode.
     */
    @Test
    public void testWouldRunDisabledTests_normalMode() {
        // In normal mode, disabled tests should not run.
        RavenwoodEnablementChecker.overrideInstance(
                RavenwoodEnablementChecker.RunMode.Normal, /* policyText= */ null,
                /* overridingPattern= */ null,
                /* ignoreLargeTests= */ false,
                /* dumpTestsOnly= */ false
        );
        assertThat(RavenwoodEnablementChecker.getInstance().wouldRunDisabledTests()).isFalse();
    }

    /**
     * Verifies that wouldRunDisabledTests() returns true when the checker is in
     * AlsoDisabledTests run mode.
     */
    @Test
    public void testWouldRunDisabledTests_alsoDisabledMode() {
        // In "also disabled" mode, disabled tests should run.
        RavenwoodEnablementChecker.overrideInstance(
                RavenwoodEnablementChecker.RunMode.AlsoDisabledTests, /* policyText= */ null,
                /* overridingPattern= */ null,
                /* ignoreLargeTests= */ false,
                /* dumpTestsOnly= */ false
        );
        assertThat(RavenwoodEnablementChecker.getInstance().wouldRunDisabledTests()).isTrue();
    }

    /**
     * Verifies that wouldRunDisabledTests() returns true when the checker is in
     * AlsoDisabledTests run mode.
     */
    @Test
    public void testWouldRunDisabledTests_DisabledOnly() {
        // In "also disabled" mode, disabled tests should run.
        RavenwoodEnablementChecker.overrideInstance(
                RunMode.DisabledOnly, /* policyText= */ null,
                /* overridingPattern= */ null,
                /* ignoreLargeTests= */ false,
                /* dumpTestsOnly= */ false
            );
        assertThat(RavenwoodEnablementChecker.getInstance().wouldRunDisabledTests()).isTrue();
    }
}
