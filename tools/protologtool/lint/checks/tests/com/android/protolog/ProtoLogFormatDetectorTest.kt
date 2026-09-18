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

package com.android.protolog

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProtoLogFormatDetectorTest : LintDetectorTest() {
    override fun getDetector() = ProtoLogFormatDetector()

    override fun getIssues() =
        listOf(
            ProtoLogFormatDetector.ISSUE_NON_CONSTANT_FORMAT,
            ProtoLogFormatDetector.ISSUE_ARG_COUNT,
            ProtoLogFormatDetector.ISSUE_ARG_TYPE,
            ProtoLogFormatDetector.ISSUE_INVALID_FORMAT_SPECIFIER,
            ProtoLogFormatDetector.ISSUE_CONSTANT_ARGUMENT,
            ProtoLogFormatDetector.ISSUE_NO_TEXT_CONTEXT,
            ProtoLogFormatDetector.ISSUE_TOO_MANY_ARGS,
        )

    override fun lint(): TestLintTask {
        return super.lint().allowMissingSdk(true)
    }

    private val protologApi: Array<TestFile> =
        arrayOf(
            java(
                """
                package com.android.internal.protolog;

                public class ProtoLog {
                    public static void v(ProtoLogGroup group, String message, Object... args) {}
                    public static void d(ProtoLogGroup group, String message, Object... args) {}
                    public static void i(ProtoLogGroup group, String message, Object... args) {}
                    public static void w(ProtoLogGroup group, String message, Object... args) {}
                    public static void e(ProtoLogGroup group, String message, Object... args) {}
                    public static void wtf(ProtoLogGroup group, String message, Object... args) {}
                }
                """.addLineContinuation()
            )
                .indented(),
            java(
                """
                package com.android.internal.protolog;

                public enum ProtoLogGroup {
                    TEST_GROUP,
                    ANOTHER_GROUP
                }
                """.addLineContinuation()
            )
                .indented(),
        )

    @Test
    fun testValidCases() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void good(String s, int i, boolean b, double f, long l, Object obj) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "This is fine");
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "User %s, id %d", s, i);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "User %s, id %d. with punctuation", s, i);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "Espaced%%in the middle of a string");
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "Number%din the middle of a string", i);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "String%sin the middle of a string", s);
                            ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Flag is %b", b);
                            ProtoLog.e(ProtoLogGroup.TEST_GROUP, "Value: %f", f);
                            ProtoLog.v(ProtoLogGroup.TEST_GROUP, "Escaped %% sign");
                            ProtoLog.wtf(ProtoLogGroup.TEST_GROUP, "All types: %b, %d, %f, %s", \
                                b, i, f, s);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "All types: %b, %d, %f, %s", \
                                b, l, f, obj);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Hex %x and Octal %o", i, i);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Sci %e and Float %g", f, f);
                        }
                    }
                    """.addLineContinuation()
                )
                    .indented(),
                *protologApi,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testNonConstantFormatString() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void bad(String userId) {
                            ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Failed for user " + userId);
                        }
                    }
                    """.addLineContinuation()
                )
                    .indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Error: ProtoLog format string should be a \
                        compile-time constant. [ProtoLogNonConstantFormat]
                            ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Failed for user " + userId);
                                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                    """.addLineContinuation()
            )
    }

    @Test
    fun testArgumentCountMismatch() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void bad(int i, int j) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d"); // Too few args
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", i, j); // Too \
                                many args
                        }
                    }
                    """.addLineContinuation()
                )
                    .indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Error: Incorrect argument count: format string \
                    expects 1 arguments but 0 were provided. [ProtoLogArgCount]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d"); // Too few args
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:9: Error: Incorrect argument count: format string \
                    expects 1 arguments but 2 were provided. [ProtoLogArgCount]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", i, j); // Too many \
                        args
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testInvalidSpecifiers() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void bad(String arg) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %z", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %X", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %abc", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %ul", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %lld", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %B", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %D", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %.2f", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %03d", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %1${'$'}s", \
                                arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,d", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,f", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,s", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,b", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %-d", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %+d", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %(d", arg);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: % d", arg);
                        }
                    }
                    """.addLineContinuation()
                )
                    .indented(),
                *protologApi,
            ).run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Error: Unsupported format specifier '%z'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %z", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:9: Error: Unsupported format specifier '%X'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %X", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:10: Error: Unsupported format specifier '%a'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %abc", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:11: Error: Unsupported format specifier '%u'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %ul", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:12: Error: Unsupported format specifier '%l'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %lld", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:13: Error: Unsupported format specifier '%B'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %B", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:14: Error: Unsupported format specifier '%D'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %D", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:15: Error: Unsupported format specifier '%.'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %.2f", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:16: Error: Unsupported format specifier '%0'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %03d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:17: Error: Unsupported format specifier '%1'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %1${'$'}s", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:18: Error: Unsupported format specifier '%,'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:19: Error: Unsupported format specifier '%,'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,f", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:20: Error: Unsupported format specifier '%,'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,s", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:21: Error: Unsupported format specifier '%,'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %,b", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:22: Error: Unsupported format specifier '%-'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %-d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:23: Error: Unsupported format specifier '%+'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %+d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:24: Error: Unsupported format specifier '%('. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %(d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:25: Error: Unsupported format specifier '% '. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: % d", arg);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~
                18 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testArgumentTypeMismatch() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void bad(String statusString, int i, double d, boolean b) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", statusString);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Some float: %f", i);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", i);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", statusString);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", i);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", d);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", b);
                        }
                    }
                    """.addLineContinuation()
                )
                    .indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Error: Incorrect argument type for format specifier \
                    '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", statusString);
                                                                                ~~~~~~~~~~~~
                src/test/pkg/TestClass.java:9: Error: Incorrect argument type for format specifier \
                    '%f': expected float or double but got int [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Some float: %f", i);
                                                                               ~
                src/test/pkg/TestClass.java:10: Error: Incorrect argument type for format \
                    specifier '%b': expected boolean but got int [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", i);
                                                                            ~
                src/test/pkg/TestClass.java:11: Error: Incorrect argument type for format \
                    specifier '%b': expected boolean but got String [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", statusString);
                                                                            ~~~~~~~~~~~~
                src/test/pkg/TestClass.java:12: Error: Incorrect argument type for format \
                    specifier '%s': expected String but got int [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", i);
                                                                           ~
                src/test/pkg/TestClass.java:13: Error: Incorrect argument type for format \
                    specifier '%s': expected String but got double [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", d);
                                                                           ~
                src/test/pkg/TestClass.java:14: Error: Incorrect argument type for format \
                    specifier '%s': expected String but got boolean [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "String: %s", b);
                                                                           ~
                7 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testValidCasesKotlin() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun good(s: String, i: Int, b: Boolean, f: Double, l: Long, obj: Any?) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "This is fine")
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "User %s, id %d", s, i)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "User %s, id %d. with punctuation", s, i)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "Espaced%%in the middle of a string")
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "Number%din the middle of a string", i)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, \
                                "String%sin the middle of a string", s)
                            ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Flag is %b", b)
                            ProtoLog.e(ProtoLogGroup.TEST_GROUP, "Value: %f", f)
                            ProtoLog.v(ProtoLogGroup.TEST_GROUP, "Escaped %% sign")
                            ProtoLog.wtf(ProtoLogGroup.TEST_GROUP, "All types: %b, %d, %f, %s", \
                                b, i, f, s)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "All types: %b, %d, %f, %s", \
                                b, l, f, obj)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Hex %x and Octal %o", i, i)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Sci %e and Float %g", f, f)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testInvalidSpecifiersKotlin() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun bad(randomArg: String) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %z", randomArg)
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %03d", \
                                randomArg)
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %.2f", \
                                randomArg)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:8: Error: Unsupported format specifier '%z'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %z", randomArg)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.kt:9: Error: Unsupported format specifier '%0'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %03d", randomArg)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.kt:10: Error: Unsupported format specifier '%.'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Invalid specifier: %.2f", randomArg)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testArgumentTypeMismatchKotlin() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun bad(statusString: String, i: Int) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", statusString)
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Some float: %f", i)
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", i)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:8: Error: Incorrect argument type for format specifier \
                    '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Status code: %d", statusString)
                                                                                ~~~~~~~~~~~~
                src/test/pkg/TestClass.kt:9: Error: Incorrect argument type for format specifier \
                    '%f': expected float or double but got int [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Some float: %f", i)
                                                                               ~
                src/test/pkg/TestClass.kt:10: Error: Incorrect argument type for format specifier \
                    '%b': expected boolean but got int [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Enabled: %b", i)
                                                                            ~
                3 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testDanglingPercent() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void bad() {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling %");
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling % in middle");
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling % ");
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Error: Unsupported format specifier '%'. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling %");
                                                             ~~~~~~~~~~~~
                src/test/pkg/TestClass.java:9: Error: Unsupported format specifier '% '. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling % in middle");
                                                             ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:10: Error: Unsupported format specifier '% '. \
                    Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Dangling % ");
                                                             ~~~~~~~~~~~~~
                3 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testEmbeddedSpecifiers() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void check(int i) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "number%din", i);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "number%03din", i);
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:9: Error: Unsupported format specifier '%0'. \
                Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "number%03din", i);
                                                             ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testNullArguments() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void check() {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %s", null);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %d", null);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %b", null);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %f", null);
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:9: Error: Incorrect argument type for format specifier \
                    '%d': expected integer, long, short, or byte but got null [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %d", null);
                                                                        ~~~~
                src/test/pkg/TestClass.java:10: Error: Incorrect argument type for format \
                    specifier '%b': expected boolean but got null [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %b", null);
                                                                        ~~~~
                src/test/pkg/TestClass.java:11: Error: Incorrect argument type for format \
                    specifier '%f': expected float or double but got null [ProtoLogArgType]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Null %f", null);
                                                                        ~~~~
                3 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testConstantIndirection() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        companion object {
                            const val FORMAT = "My format %d"
                        }
                        fun check(i: Int) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, FORMAT, i)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testEdgeCases() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void check(String s, int i, double d, boolean b) {
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "No specifiers here.");
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Only escaped %% here.");
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Mixed %% and %s", s);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Multiple %s %d %f %b", s, i, d, \
                                b);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Format with trailing percent %", \
                                s);
                            ProtoLog.i(ProtoLogGroup.TEST_GROUP, \
                                "Format with trailing percent %%", s);
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:13: Error: Incorrect argument count: format string \
                expects 0 arguments but 1 were provided. [ProtoLogArgCount]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Format with trailing percent %%", s);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.java:12: Error: Unsupported format specifier '%'. \
                Supported: [%b, %d, %o, %x, %f, %e, %g, %s]. Use %% to escape. [ProtoLogInvalidFormatSpecifier]
                        ProtoLog.i(ProtoLogGroup.TEST_GROUP, "Format with trailing percent %", s);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testKotlinStringInterpolation() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun bad(userId: String) {
                            ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Failed for user ${'$'}userId")
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:8: Error: ProtoLog format string should be a \
                compile-time constant. [ProtoLogNonConstantFormat]
                        ProtoLog.w(ProtoLogGroup.TEST_GROUP, "Failed for user ${'$'}userId")
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testConstantArguments() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void check() {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", 123);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %s", "constant");
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %b", true);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %f", 3.14);
                        }

                        void check2(int x) {
                             ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", x); // Not constant
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Error: ProtoLog format string argument should not \
                    be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", 123);
                                                                        ~~~
                src/test/pkg/TestClass.java:9: Error: ProtoLog format string argument should not \
                    be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %s", "constant");
                                                                        ~~~~~~~~~~
                src/test/pkg/TestClass.java:10: Error: ProtoLog format string argument should not \
                    be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %b", true);
                                                                        ~~~~
                src/test/pkg/TestClass.java:11: Error: ProtoLog format string argument should not \
                    be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %f", 3.14);
                                                                        ~~~~
                4 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testConstantArgumentsKotlin() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    const val CONST_VAL = 123

                    class TestClass {
                        fun check(param: Int) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", 123)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", CONST_VAL)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", param)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:10: Error: ProtoLog format string argument should not \
                    be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d", 123)
                                                                        ~~~
                1 error
                """.addLineContinuation()
            )
    }

    @Test
    fun testNoContextLogging() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class TestClass {
                        void check(int x, String s) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d", x);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%s", s);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, " %s ", s);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d %s", x, s);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Value: %d", x);
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%%");
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:8: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d", x);
                                                             ~~~~
                src/test/pkg/TestClass.java:9: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%s", s);
                                                             ~~~~
                src/test/pkg/TestClass.java:10: Warning: ProtoLog format string should contain \
                    some context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, " %s ", s);
                                                             ~~~~~~
                src/test/pkg/TestClass.java:11: Warning: ProtoLog format string should contain \
                    some context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d %s", x, s);
                                                             ~~~~~~~
                0 errors, 4 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testNoContextLoggingKotlin() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun check(x: Int, s: String) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d", x)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%s", s)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, " %s ", s)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d %s", x, s)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Value: %d", x)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:8: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d", x)
                                                             ~~~~
                src/test/pkg/TestClass.kt:9: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%s", s)
                                                             ~~~~
                src/test/pkg/TestClass.kt:10: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, " %s ", s)
                                                             ~~~~~~
                src/test/pkg/TestClass.kt:11: Warning: ProtoLog format string should contain some \
                    context, not just specifiers. [ProtoLogNoContext]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "%d %s", x, s)
                                                             ~~~~~~~
                0 errors, 4 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testTooManyArguments() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;
                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;
                    class TestClass {
                        void bad(int i) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d %d %d %d %d %d %d %d %d \
                            %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d \
                            %d",
                                i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, \
                                i, i, i, i, i, i, i, i, i, i, i);
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:6: Error: ProtoLog method has too many arguments 33 \
                (limit is 32) [ProtoLogTooManyArgs]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Test %d %d %d %d %d %d %d %d %d %d \
                        %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d",
                        ^
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectLogCallTypes() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        companion object {
                            private const val TAG = "TestClass"
                        }

                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private fun logV(msg: String, vararg arguments: Any?) {
                            ProtoLog.v(ProtoLogGroup.TEST_GROUP, "%s: ${'$'}msg", TAG, *arguments)
                        }

                        fun check(s: String, d: Int) {
                            logV("Test %d", s) // Mismatch
                            logV("Test %s", d) // Mismatch
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:17: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        logV("Test %d", s) // Mismatch
                                        ~
                src/test/pkg/TestClass.kt:18: Error: Incorrect argument type for format specifier \
                '%s': expected String but got int [ProtoLogArgType]
                        logV("Test %s", d) // Mismatch
                                        ~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectLogCallArity() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        companion object {
                            private const val TAG = "TestClass"
                        }

                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private fun logV(msg: String, vararg arguments: Any?) {
                            ProtoLog.v(ProtoLogGroup.TEST_GROUP, "%s: ${'$'}msg", TAG, *arguments)
                        }

                        fun check() {
                            logV("Test %d")   // Missing argument
                            logV("Test", 123) // Extra argument
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:17: Error: Incorrect argument count: format string \
                expects 2 arguments but 1 were provided. [ProtoLogArgCount]
                        logV("Test %d")   // Missing argument
                        ~~~~~~~~~~~~~~~
                src/test/pkg/TestClass.kt:18: Error: Incorrect argument count: format string \
                expects 1 arguments but 2 were provided. [ProtoLogArgCount]
                        logV("Test", 123) // Extra argument
                        ~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectLogCallPassthroughFormat() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private fun myLog(fmt: String, vararg args: Any?) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, fmt, *args)
                        }

                        fun check(s: String, d: Int) {
                            myLog("My Format %d", s) // Mismatch
                            myLog("My Format %d", d) // Valid
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        myLog("My Format %d", s) // Mismatch
                                              ~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectLogCallConcatenation() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private fun logWithPrefix(prefix: String, value: Int) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, prefix + " code: %d", value)
                        }

                        fun check(d: Int, s: String) {
                            logWithPrefix("Error", d) // Valid
                            logWithPrefix("Error", s) // Mismatch
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:14: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        logWithPrefix("Error", s) // Mismatch
                                               ~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectLogCallJava() {
        lint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.android.internal.protolog.ProtoLog;
                    import com.android.internal.protolog.ProtoLogGroup;

                    class JavaTestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private void logWrapper(String msg, Object... args) {
                            ProtoLog.v(ProtoLogGroup.TEST_GROUP, "Java: " + msg, args);
                        }

                        void check(int d, String s) {
                            logWrapper("Test %d", d); // Valid
                            logWrapper("Test %d", s); // Mismatch
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/JavaTestClass.java:14: Error: Incorrect argument type for format \
                specifier '%d': expected integer, long, short, or byte but got String \
                [ProtoLogArgType]
                        logWrapper("Test %d", s); // Mismatch
                                              ~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testSimpleWrapperExpressionBody() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        fun log(msg: String, vararg args: Any?) =
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Log: ${'$'}msg %d", *args)

                        fun check(d: Int, s: String) {
                            log("A message", d)
                            log("A message", s)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        log("A message", s)
                                         ~
                1 error
                """.addLineContinuation()
            )
    }

    @Test
    fun testComplexWrapperWithVarargs() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        fun complexLog(check: Boolean, msg: String, vararg args: Any?) {
                            if (check) {
                                ProtoLog.v(ProtoLogGroup.TEST_GROUP, msg, *args)
                            } else {
                                // Do nothing
                            }
                        }

                        fun check(d: Int, s: String) {
                            complexLog(true, "Test %d", d) // Valid
                            complexLog(true, "Test %d", s) // Mismatch
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:18: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        complexLog(true, "Test %d", s) // Mismatch
                                                    ~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testTypeMismatchReproduction() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun check(value: Boolean?) {
                            val boolVal = value ?: false
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Boolean: %b", boolVal)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP,
                                "Boolean from Any: %b", (value ?: true))
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testKotlinDefaultArgumentsFormatMismatch() {
        lint()
             .files(
                 kotlin(
                     """
                     package test.pkg

                     import com.android.internal.protolog.ProtoLog
                     import com.android.internal.protolog.ProtoLogGroup

                     class TestClass {
                         @SuppressWarnings("ProtoLogNonConstantFormat")
                         fun log(msg: String, value: Any = 0) {
                             ProtoLog.d(ProtoLogGroup.TEST_GROUP, msg, value)
                         }

                         fun check(s: String) {
                             log("Value: %d", s)
                             log("Value: %d")
                             log("Value: %b")
                         }
                     }
                     """.addLineContinuation()
                 ).indented(),
                 *protologApi,
             )
             .run()
             .expect(
                 """
                 src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                 '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                         log("Value: %d", s)
                                          ~
                 1 errors, 0 warnings
                 """.addLineContinuation()
             )
    }

    @Test
    fun testKotlinNamedArguments() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        fun log(msg: String, s: String, i: Int) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, msg, s, i)
                        }

                        fun check(str: String, num: Int) {
                            log(msg = "S: %s, I: %d", i = num, s = str)
                            log(msg = "S: %s, I: %d", i = str, s = num)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:14: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                        log(msg = "S: %s, I: %d", i = str, s = num)
                                                      ~~~
                src/test/pkg/TestClass.kt:14: Error: Incorrect argument type for format specifier \
                '%s': expected String but got int [ProtoLogArgType]
                        log(msg = "S: %s, I: %d", i = str, s = num)
                                                               ~~~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testKotlinNamedArgumentsFormatMismatch() {
        lint()
             .files(
                 kotlin(
                     """
                     package test.pkg

                     import com.android.internal.protolog.ProtoLog
                     import com.android.internal.protolog.ProtoLogGroup

                     class TestClass {
                         @SuppressWarnings("ProtoLogNonConstantFormat")
                         fun log(msg: String, param1: Any, param2: Any) {
                             ProtoLog.d(ProtoLogGroup.TEST_GROUP, msg, param1, param2)
                         }

                         fun check(str: String, num: Int) {
                             log(msg = "First: %d, Second: %s", param1 = str, param2 = num)
                         }
                     }
                     """.addLineContinuation()
                 ).indented(),
                 *protologApi,
             )
             .run()
             .expect(
                 """
                 src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                 '%d': expected integer, long, short, or byte but got String [ProtoLogArgType]
                         log(msg = "First: %d, Second: %s", param1 = str, param2 = num)
                                                                     ~~~
                 src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                 '%s': expected String but got int [ProtoLogArgType]
                         log(msg = "First: %d, Second: %s", param1 = str, param2 = num)
                                                                                   ~~~
                 2 errors, 0 warnings
                 """.addLineContinuation()
             )
    }

    @Test
    fun testNullabilityInWrapper() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        fun log(msg: String, value: Int?) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, msg, value)
                        }

                        fun check() {
                            log("Value: %d", null)
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:13: Error: Incorrect argument type for format specifier \
                '%d': expected integer, long, short, or byte but got null [ProtoLogArgType]
                        log("Value: %d", null)
                                         ~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testWrapperWithHardcodedFormatStringExcluded() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun wrapper(x: Int) {
                            // This wrapper has an error (2 specifiers, 1 arg).
                            // This error should be reported here.
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Format %d %d", x)
                        }

                        fun caller() {
                            // This call should NOT be validated because the format string
                            // is not forwarded. If validation were enabled, it would report
                            // an error here too (indirectly).
                            wrapper(1)
                        }
                    }
                    """
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:10: Error: Incorrect argument count: format string \
                expects 2 arguments but 1 were provided. [ProtoLogArgCount]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Format %d %d", x)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testWrapperWithCalculatedFormatStringExcluded() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        fun wrapper(x: Int) {
                            // This wrapper has an error (2 specifiers, 1 arg).
                            // This error should be reported here.
                            val format = "Format %d %d"
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Prefix: " + format, x)
                        }

                        fun caller() {
                            // This call should NOT be validated because the format string
                            // is not forwarded. If validation were enabled, it would report
                            // an error here too (indirectly).
                            wrapper(1)
                        }
                    }
                    """
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:11: Error: Incorrect argument count: format string \
                expects 2 arguments but 1 were provided. [ProtoLogArgCount]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Prefix: " + format, x)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testWrapperWithInterpolatedFormatString() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        fun wrapper(prefix: String, x: Int) {
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Prefix: ${'$'}prefix %d", x)
                        }

                        fun callerGood(i: Int) {
                            wrapper("MyString", i)
                        }

                        fun callerBad(i: Int) {
                            wrapper("MyString %d", i)
                        }
                    }
                    """
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:17: Error: Incorrect argument count: format string \
                expects 2 arguments but 1 were provided. [ProtoLogArgCount]
                        wrapper("MyString %d", i)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    @Test
    fun testIndirectNonConstantFormatString() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        companion object {
                            private const val TAG = "TestClass"
                        }

                        @SuppressWarnings("ProtoLogNonConstantFormat")
                        private fun logE(msg: String, vararg arguments: Any?) {
                            ProtoLog.e(ProtoLogGroup.TEST_GROUP, "%s: ${'$'}msg", TAG, *arguments)
                        }

                        fun check(i: Int) {
                            logE("Non constant string ${'$'}i")
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:17: Error: ProtoLog format string should be a \
                compile-time constant. [ProtoLogNonConstantFormat]
                        logE("Non constant string ${'$'}i")
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """.addLineContinuation()
            )
    }

    @Test
    fun testConstantArgumentsWithNamedConstants() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import com.android.internal.protolog.ProtoLog
                    import com.android.internal.protolog.ProtoLogGroup

                    class TestClass {
                        companion object {
                            const val CONST_INT = 123
                            const val CONST_STR = "state"
                        }

                        enum class MyEnum {
                            VALUE_ONE, VALUE_TWO
                        }

                        fun check() {
                            // These should ideally be ALLOWED, but currently might fail if we are too strict.
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Int: %d", CONST_INT)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Q: %s", MyEnum.VALUE_ONE)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Str: %s", CONST_STR)

                            // These should always FAIL (Literals)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Int: %d", 42)
                            ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Str: %s", "literal")
                        }
                    }
                    """.addLineContinuation()
                ).indented(),
                *protologApi,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:23: Error: ProtoLog format string argument should not be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Int: %d", 42)
                                                                        ~~
                src/test/pkg/TestClass.kt:24: Error: ProtoLog format string argument should not be a constant. [ProtoLogConstantArgument]
                        ProtoLog.d(ProtoLogGroup.TEST_GROUP, "Str: %s", "literal")
                                                                        ~~~~~~~~~
                2 errors, 0 warnings
                """.addLineContinuation()
            )
    }

    // Substitutes "backslash + new line" with an empty string to imitate line continuation
    private fun String.addLineContinuation(): String = this.trimIndent().replace("""\\ *\n\h*""".toRegex(), "")
}
