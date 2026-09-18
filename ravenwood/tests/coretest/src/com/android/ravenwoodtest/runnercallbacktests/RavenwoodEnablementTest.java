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
package com.android.ravenwoodtest.runnercallbacktests;

import static org.junit.Assert.fail;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.RunMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.Parameter;

public class RavenwoodEnablementTest extends RavenwoodRunnerTestBase {
    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimple
    testSuiteFinished: classes
    testRunFinished: 2,0,1,0
    """)
    // CHECKSTYLE:ON
    public static class TestSimple {
        public TestSimple() {
        }

        @Test
        public void test1() {
        }

        @Test
        @DisabledOnRavenwood
        public void test2() {
            fail("This shouldn't be executed");
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledToo
    testSuiteFinished: classes
    testRunFinished: 2,0,0,0
    """, runMode = RunMode.AlsoDisabledTests)
    // CHECKSTYLE:ON
    public static class TestSimpleRunDisabledToo {
        public TestSimpleRunDisabledToo() {
        }

        @Test
        public void test1() {
        }

        @Test
        @DisabledOnRavenwood
        public void test2() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testAssumptionFailure: This test is disabled on Ravenwood: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_NoClassAnnotation
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """, runMode = RunMode.DisabledOnly)
    // CHECKSTYLE:ON
    public static class TestSimpleRunDisabledOnly_NoClassAnnotation {
        public TestSimpleRunDisabledOnly_NoClassAnnotation() {
        }

        @Test
        public void test1() {
            fail("This shouldn't be executed");
        }

        @Test
        @EnabledOnRavenwood
        public void test2() {
            fail("This shouldn't be executed");
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassDisabledAnnotation
    testSuiteFinished: classes
    testRunFinished: 3,0,1,0
    """, runMode = RunMode.DisabledOnly)
    // CHECKSTYLE:ON
    @DisabledOnRavenwood
    public static class TestSimpleRunDisabledOnly_WithClassDisabledAnnotation {
        public TestSimpleRunDisabledOnly_WithClassDisabledAnnotation() {
        }

        @Test
        public void test1() {
            // Because of the class level annotation, this method is implicitly disabled,
            // so DisabledOnly will execute it.
        }

        @Test
        @EnabledOnRavenwood
        public void test2() {
            fail("This shouldn't be executed");
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testAssumptionFailure: This test is disabled on Ravenwood: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestSimpleRunDisabledOnly_WithClassEnabledAnnotation
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """, runMode = RunMode.DisabledOnly)
    // CHECKSTYLE:ON
    @EnabledOnRavenwood // DisabledOnly + class-level @Enable still runs disabled tests in it.
    public static class TestSimpleRunDisabledOnly_WithClassEnabledAnnotation {
        public TestSimpleRunDisabledOnly_WithClassEnabledAnnotation() {
        }

        @Test
        public void test1() {
            fail("This shouldn't be executed");
        }

        @Test
        @EnabledOnRavenwood
        public void test2() {
            fail("This shouldn't be executed");
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @DisabledOnRavenwood
    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabled)
    testIgnored: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabled)
    testSuiteFinished: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabled)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """)
    // CHECKSTYLE:ON
    public static class TestClassDisabled {
        public TestClassDisabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        @DisabledOnRavenwood
        public void test2() {
        }
    }

    @DisabledOnRavenwood
    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestClassDisabledRunDisabledToo
    testSuiteFinished: classes
    testRunFinished: 2,0,0,0
    """, runMode = RunMode.AlsoDisabledTests)
    // CHECKSTYLE:ON
    public static class TestClassDisabledRunDisabledToo {
        public TestClassDisabledRunDisabledToo() {
        }

        @Test
        public void test1() {
        }

        @Test
        @DisabledOnRavenwood
        public void test2() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled)
    testIgnored: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled)
    testSuiteFinished: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """, runMode = RunMode.Normal, enablementPolicy = """
    !module RavenwoodCoreTest
    **TestWithPolicyClassDisabled false
    ** true
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabled {
        public TestWithPolicyClassDisabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testAssumptionFailure: This test is disabled on Ravenwood: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled
    testSuiteFinished: classes
    testRunFinished: 3,0,1,0
    """, runMode = RunMode.Normal, enablementPolicy = """
    !module RavenwoodCoreTest
    **$TestWithPolicyClassEnabled true
    ** false
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassEnabled {
        public TestWithPolicyClassEnabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testAssumptionFailure: This test is disabled on Ravenwood: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """,runMode = RunMode.Normal, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disabled at class level

    # Only test1() is enabled
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled#test1
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabledMethodEnabled {
        public TestWithPolicyClassDisabledMethodEnabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWrongModuleName
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWrongModuleName)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWrongModuleName)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWrongModuleName
    testSuiteFinished: classes
    testRunFinished: 1,0,0,0
    """, runMode = RunMode.Normal, enablementPolicy = """
    !module wrong-module-name
    ** false # Disable all, but because the module name is wrong, it should be ignored. 
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyWrongModuleName {
        public TestWithPolicyWrongModuleName() {
        }

        @Test
        public void test1() {
        }
    }


    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyDefaultEnabled
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyDefaultEnabled)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyDefaultEnabled)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyDefaultEnabled
    testSuiteFinished: classes
    testRunFinished: 1,0,0,0
    """, runMode = RunMode.Normal, enablementPolicy = """
    !module RavenwoodCoreTest
    ** # missing true/false -> default is "true".
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyDefaultEnabled {
        public TestWithPolicyDefaultEnabled() {
        }

        @Test
        public void test1() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabled_RDT
    testSuiteFinished: classes
    testRunFinished: 3,0,0,0
    """, runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    **$TestWithPolicyClassDisabled** false
    ** true
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabled_RDT {
        public TestWithPolicyClassDisabled_RDT() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassEnabled_RDT
    testSuiteFinished: classes
    testRunFinished: 3,0,0,0
    """, runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    **$TestWithPolicyClassEnabled** true
    ** false
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassEnabled_RDT {
        public TestWithPolicyClassEnabled_RDT() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT
    testSuiteFinished: classes
    testRunFinished: 3,0,0,0
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disabled at class level

    # Only test1() is enabled
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled#test1
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabledMethodEnabled_RDT {
        public TestWithPolicyClassDisabledMethodEnabled_RDT() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverClass)
    testIgnored: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverClass)
    testSuiteFinished: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverClass)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    **$TestWithPolicyClassDisabledMethodEnabled** never

    # Only test1() is enabled
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled#test1
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabledMethodEnabled_RDT_NeverClass {
        public TestWithPolicyClassDisabledMethodEnabled_RDT_NeverClass() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod
    testSuiteFinished: classes
    testRunFinished: 3,0,1,0
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disable all classes

    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod#test2 never
    """)
    // CHECKSTYLE:ON
    public static class TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod {
        public TestWithPolicyClassDisabledMethodEnabled_RDT_NeverMethod() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testAssumptionFailure: This test is disabled on Ravenwood: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disable all classes
    """, overridingRegex="!testwith.*#test3"
    )
    // CHECKSTYLE:ON
    public static class TestWithPolicyWithForceRegex_methodLevel {
        public TestWithPolicyWithForceRegex_methodLevel() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelEnabled
    testSuiteFinished: classes
    testRunFinished: 3,0,0,0
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disable all classes
    """, overridingRegex="!TestWithPolicy" // Should run all the tests
    )
    // CHECKSTYLE:ON
    public static class TestWithPolicyWithForceRegex_classLevelEnabled {
        public TestWithPolicyWithForceRegex_classLevelEnabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelDisabled)
    testIgnored: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelDisabled)
    testSuiteFinished: <init>(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_classLevelDisabled)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    """, overridingRegex="!NotMatchingAnyTests" // no tests should be executed
    )
    // CHECKSTYLE:ON
    public static class TestWithPolicyWithForceRegex_classLevelDisabled {
        public TestWithPolicyWithForceRegex_classLevelDisabled() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testAssumptionFailure: This test is disabled on Ravenwood: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex
    testSuiteFinished: classes
    testRunFinished: 3,0,1,0
    """, runMode = RunMode.Normal,

    // Matches the class name but not forcing, so we just follow the annotations.
    overridingRegex="TestWithPolicy"
    )
    // CHECKSTYLE:ON
    public static class TestWithPolicyWithNonForceRegex {
        public TestWithPolicyWithNonForceRegex() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testAssumptionFailure: This test is disabled on Ravenwood: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testAssumptionFailure: This test is disabled on Ravenwood: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithNonForceRegex2
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """, runMode = RunMode.Normal,

    // Only selects test2 and test3. Test3 is dsabled by the annotation, so we only run test2.
    overridingRegex="TestWithPolicy.*#test[23]"
    )
    // CHECKSTYLE:ON
    public static class TestWithPolicyWithNonForceRegex2 {
        public TestWithPolicyWithNonForceRegex2() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

        @Test
        @DisabledOnRavenwood
        public void test3() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore
    testSuiteFinished: classes
    testRunFinished: 2,0,0,0
    """, runMode = RunMode.Normal,

    // There's a ":large" test, but we run it by default.
    enablementPolicy = """
    !module RavenwoodCoreTest
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore#test1 enable
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_notIgnore#test2 enable :large
    ** disable
    """
    )
    // CHECKSTYLE:ON
    public static class TestWithLargeTests_notIgnore {
        public TestWithLargeTests_notIgnore() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }
    }

    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore
    testSuiteFinished: classes
    testRunFinished: 2,0,1,0
    """, runMode = RunMode.Normal,

    // Skip the ":large" test.
    enablementPolicy = """
    !module RavenwoodCoreTest
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore#test1 enable
    com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithLargeTests_Ignore#test2 enable :large
    ** disable
    """, ignoreLargeTests = true
    )
    // CHECKSTYLE:ON
    public static class TestWithLargeTests_Ignore {
        public TestWithLargeTests_Ignore() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }
    }

    /**
     * The class should be enabled but it should skip all test methods.
     */
    @RunWith(BlockJUnit4ClassRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testAssumptionFailure: This test is disabled on Ravenwood: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testAssumptionFailure: This test is disabled on Ravenwood: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$DumpTestsOnly
    testSuiteFinished: classes
    testRunFinished: 2,0,2,0
    """, runMode = RunMode.Normal,

    // Skip the ":large" test.
    enablementPolicy = """
    !module RavenwoodCoreTest
    ** enable
    """, dumpTestsOnly = true
    )
    // CHECKSTYLE:ON
    public static class DumpTestsOnly {
        public DumpTestsOnly() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }
    }

    /**
     * Simple test using the Parameterized runner.
     */
    @RunWith(Parameterized.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest
    testSuiteStarted: [0]
    testStarted: test1[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testFinished: test1[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testStarted: test2[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testFinished: test2[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testSuiteFinished: [0]
    testSuiteStarted: [1]
    testStarted: test1[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testFinished: test1[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testStarted: test2[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testFinished: test2[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest)
    testSuiteFinished: [1]
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedTest
    testSuiteFinished: classes
    testRunFinished: 4,0,0,0
    """, runMode = RunMode.Normal
    )
    // CHECKSTYLE:ON
    public static class ParametrizedTest {
        @Parameters
        public static Object[] data() {
            return new Object[] { "test1", "test2"};
        }

        @Parameter(0)
        public String parameter;

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }
    }

    /**
     */
    @RunWith(Parameterized.class)
    // CHECKSTYLE:OFF Generated code
    @Expected(value = """
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest
    testSuiteStarted: [0]
    testStarted: test1[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testFinished: test1[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testStarted: test2[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testAssumptionFailure: This test is disabled on Ravenwood: test2[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testFinished: test2[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testSuiteFinished: [0]
    testSuiteStarted: [1]
    testStarted: test1[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testFinished: test1[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testStarted: test2[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testAssumptionFailure: This test is disabled on Ravenwood: test2[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testFinished: test2[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest)
    testSuiteFinished: [1]
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$ParametrizedWithRegexFilterTest
    testSuiteFinished: classes
    testRunFinished: 4,0,2,0
    """, runMode = RunMode.Normal,
    overridingRegex = "test1"
    )
    // CHECKSTYLE:ON
    public static class ParametrizedWithRegexFilterTest {
        @Parameters
        public static Object[] data() {
            return new Object[] { "test1", "test2"};
        }

        @Parameter(0)
        public String parameter;

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }
    }
}
