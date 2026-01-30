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

package com.google.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class PreconditionsDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = PreconditionsDetector()

    override fun getIssues(): List<Issue> = PreconditionsDetector.ISSUES

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    private val preconditionsStub =
        java(
                """
            package com.android.internal.util;

            public class Preconditions {
                public static <T> T checkNotNull(T reference) {
                    if (reference == null) throw new NullPointerException();
                    return reference;
                }
                public static <T> T checkNotNull(T reference, Object errorMessage) {
                    if (reference == null) throw new NullPointerException(String.valueOf(errorMessage));
                    return reference;
                }
                public static <T> T checkNotNull(T reference, String message, Object... args) {
                    if (reference == null) throw new NullPointerException(String.format(message, args));
                    return reference;
                }
                public static <T> T checkNotNullWithName(T reference, String referenceName) {
                    if (reference == null) throw new NullPointerException(referenceName + " must not be null");
                    return reference;
                }
                public static void checkArgument(boolean expression) {
                    if (!expression) throw new IllegalArgumentException();
                }
                public static void checkArgument(boolean expression, Object errorMessage) {
                    if (!expression) throw new IllegalArgumentException(String.valueOf(errorMessage));
                }
                public static void checkArgument(boolean expression, String message, Object... args) {
                    if (!expression) throw new IllegalArgumentException(String.format(message, args));
                }
                public static void checkState(boolean expression) {
                    if (!expression) throw new IllegalStateException();
                }
                public static void checkState(boolean expression, Object errorMessage) {
                    if (!expression) throw new IllegalStateException(String.valueOf(errorMessage));
                }
                public static void checkState(boolean expression, String message, Object... args) {
                    if (!expression) throw new IllegalStateException(String.format(message, args));
                }
            }
        """
            )
            .indented()

    fun testManualNullCheck_noMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testManualNullCheck_noMessage_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new NullPointerException();
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testManualNullCheck_noMessage_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testManualNullCheck_withMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testManualNullCheck_withMessage_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new NullPointerException("null arguments are not allowed");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testManualNullCheck_withMessage_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo, "null arguments are not allowed");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testManualNullCheckWithName_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testManualNullCheck_withMessage_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new NullPointerException("foo is null");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testManualNullCheck_withMessage_isFlagged(Object foo) {
                                Preconditions.checkNotNullWithName(foo, "foo");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    // Test that we are robust to dangling if statements.
    fun testManualNullCheck_noBlock_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testManualNullCheck_noBlock_isFlagged(Object foo) {
                            if (foo == null) throw new NullPointerException();
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testManualNullCheck_noBlock_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    // Ensure that null checks are detected both for null being the l-value and r-value.
    fun testManualNullCheck_reversedOrder_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testManualNullCheck_reversedOrder_isFlagged(Object foo) {
                            if (null == foo) {
                                throw new NullPointerException();
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testManualNullCheck_reversedOrder_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCorrectUsage_isNotFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    import com.android.internal.util.Preconditions;
                    class TestClass {
                        void testCorrectUsage_isNotFlagged(Object foo) {
                            Preconditions.checkNotNull(foo);
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectClean()
    }

    fun testDifferentCheck_isNotFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testDifferentCheck_isNotFlagged(Object foo) {
                            if (foo == null) {
                                System.out.println("foo is null");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectClean()
    }

    fun testNullCheck_withIllegalArgumentException_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testNullCheck_withIllegalArgumentException_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new IllegalArgumentException("nulls are not allowed");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testNullCheck_withIllegalArgumentException_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo, "nulls are not allowed");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testNullCheck_withIllegalStateException_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testNullCheck_withIllegalStateException_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new IllegalStateException("nulls are not allowed");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testNullCheck_withIllegalStateException_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo, "nulls are not allowed");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckArgument_noMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckArgument_noMessage_isFlagged(boolean condition) {
                            if (!condition) {
                                throw new IllegalArgumentException();
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckArgument_noMessage_isFlagged(boolean condition) {
                                Preconditions.checkArgument(condition);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckArgument_withMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckArgument_withMessage_isFlagged(boolean condition) {
                            if (!condition) {
                                throw new IllegalArgumentException("condition is false");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckArgument_withMessage_isFlagged(boolean condition) {
                                Preconditions.checkArgument(condition, "condition is false");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckState_noMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckState_noMessage_isFlagged(boolean condition) {
                            if (!condition) {
                                throw new IllegalStateException();
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckState_noMessage_isFlagged(boolean condition) {
                                Preconditions.checkState(condition);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckState_withMessage_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckState_withMessage_isFlagged(boolean condition) {
                            if (!condition) {
                                throw new IllegalStateException("condition is false");
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckState_withMessage_isFlagged(boolean condition) {
                                Preconditions.checkState(condition, "condition is false");
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckNotNull_withStringFormat_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckNotNull_withStringFormat_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new NullPointerException(String.format("foo is null: %s", foo));
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckNotNull_withStringFormat_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo, "foo is null: %s", foo);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckNotNull_withConcatenation_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckNotNull_withConcatenation_isFlagged(Object foo) {
                            if (foo == null) {
                                throw new NullPointerException("foo is " + "null: " + foo);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckNotNull_withConcatenation_isFlagged(Object foo) {
                                Preconditions.checkNotNull(foo, "foo is null: %s", foo);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckNotNull_withMultipleConcatenations_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckNotNull_withMultipleConcatenations_isFlagged(Object foo, Object bar) {
                            if (foo == null) {
                                throw new NullPointerException("foo is " + "null: " + foo + " and bar is " + bar);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckNotNull_withMultipleConcatenations_isFlagged(Object foo, Object bar) {
                                Preconditions.checkNotNull(foo, "foo is null: %s and bar is %s", foo, bar);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckArgument_withStringFormat_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckArgument_withStringFormat_isFlagged(boolean condition, Object arg) {
                            if (!condition) {
                                throw new IllegalArgumentException(String.format("bad arg: %s", arg));
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckArgument_withStringFormat_isFlagged(boolean condition, Object arg) {
                                Preconditions.checkArgument(condition, "bad arg: %s", arg);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckArgument_withConcatenation_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckArgument_withConcatenation_isFlagged(boolean condition, Object arg) {
                            if (!condition) {
                                throw new IllegalArgumentException("bad arg: " + arg);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckArgument_withConcatenation_isFlagged(boolean condition, Object arg) {
                                Preconditions.checkArgument(condition, "bad arg: %s", arg);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckArgument_withMultipleConcatenations_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckArgument_withMultipleConcatenations_isFlagged(boolean condition, Object arg1, Object arg2) {
                            if (!condition) {
                                throw new IllegalArgumentException("bad args: " + arg1 + " and " + arg2);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckArgument_withMultipleConcatenations_isFlagged(boolean condition, Object arg1, Object arg2) {
                                Preconditions.checkArgument(condition, "bad args: %s and %s", arg1, arg2);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckState_withStringFormat_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckState_withStringFormat_isFlagged(boolean condition, Object state) {
                            if (!condition) {
                                throw new IllegalStateException(String.format("bad state: %s", state));
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckState_withStringFormat_isFlagged(boolean condition, Object state) {
                                Preconditions.checkState(condition, "bad state: %s", state);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    // String concatenation becomes string formatting with %s placeholder.
    fun testCheckState_withConcatenation_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckState_withConcatenation_isFlagged(boolean condition, Object state) {
                            if (!condition) {
                                throw new IllegalStateException("bad state: " + state);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckState_withConcatenation_isFlagged(boolean condition, Object state) {
                                Preconditions.checkState(condition, "bad state: %s", state);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }

    fun testCheckState_withMultipleConcatenations_isFlagged() {
        lint()
            .files(
                preconditionsStub,
                java(
                        """
                    package test.pkg;
                    class TestClass {
                        void testCheckState_withMultipleConcatenations_isFlagged(boolean condition, Object state1, Object state2) {
                            if (!condition) {
                                throw new IllegalStateException("bad state: " + state1 + " and " + state2);
                            }
                        }
                    }
                """
                    )
                    .indented(),
            )
            .run()
            .expectWarningCount(1)
            .checkFix(
                null,
                after =
                    java(
                            """
                        package test.pkg;
                        import com.android.internal.util.Preconditions;
                        class TestClass {
                            void testCheckState_withMultipleConcatenations_isFlagged(boolean condition, Object state1, Object state2) {
                                Preconditions.checkState(condition, "bad state: %s and %s", state1, state2);
                            }
                        }
                        """
                        )
                        .indented(),
            )
    }
}
