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

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.NoRavenizer;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.RunMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@NoRavenizer // This class shouldn't be executed with RavenwoodAwareTestRunner.
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
    testAssumptionFailure: got: <false>, expected: is <true>
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
    testAssumptionFailure: got: <false>, expected: is <true>
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
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyClassDisabledMethodEnabled)
    testAssumptionFailure: got: <false>, expected: is <true>
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
    testAssumptionFailure: got: <false>, expected: is <true>
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
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testStarted: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testFinished: test3(com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodEnablementTest$TestWithPolicyWithForceRegex_methodLevel
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """,runMode = RunMode.AlsoDisabledTests, enablementPolicy = """
    !module RavenwoodCoreTest
    ** false # disable all classes
    """, overridingRegex="testwith.*#test3"
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
    """, overridingRegex="TestWithPolicy" // Should run all the tests
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
    """, overridingRegex="NotMatchingAnyTests" // no tests should be executed
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
}
