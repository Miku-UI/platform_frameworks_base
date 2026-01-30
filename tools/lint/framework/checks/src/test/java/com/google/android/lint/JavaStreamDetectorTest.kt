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

@Suppress("UnstableApiUsage")
class JavaStreamDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = JavaStreamDetector()

    override fun getIssues(): List<Issue> = listOf(JavaStreamDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    fun testNoStream() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.Collection;

                        public class TestClass {

                            public void testMethod(Collection<String> collection) {
                                System.out.println(collection);
                            }
                        }
                    """
                )
                    .indented(),
            )
            .run()
            .expectClean()
    }

    fun testCollectionStream() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.Collection;

                        public class TestClass {

                            public void testMethod(Collection<String> collection) {
                                collection.stream().forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            collection.stream().forEach(System.out::println);
                            ~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testListStream() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.List;

                        public class TestClass {

                            public void testMethod(List<String> list) {
                                list.stream().forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            list.stream().forEach(System.out::println);
                            ~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testArraysStreamObject() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.Arrays;

                        public class TestClass {

                            public void testMethod(Object[] array) {
                                Arrays.stream(array).forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            Arrays.stream(array).forEach(System.out::println);
                            ~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testArraysStreamInt() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.Arrays;

                        public class TestClass {

                            public void testMethod(int[] array) {
                                Arrays.stream(array).forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            Arrays.stream(array).forEach(System.out::println);
                            ~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testStreamOf() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.stream.Stream;

                        public class TestClass {

                            public void testMethod() {
                                Stream.of("a", "b").forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            Stream.of("a", "b").forEach(System.out::println);
                            ~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testIntStreamOf() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.util.stream.IntStream;

                        public class TestClass {

                            public void testMethod() {
                                IntStream.of(1, 2).forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            IntStream.of(1, 2).forEach(System.out::println);
                            ~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testBufferedReaderLines() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import java.io.BufferedReader;

                        public class TestClass {

                            public void testMethod(BufferedReader reader) {
                                reader.lines().forEach(System.out::println);
                            }
                        }
                    """
                )
                    .indented()
            )
            .run()
            .expect(
                """
                    src/test/pkg/TestClass.java:8: Warning: Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like CollectionUtils. [JavaStream]
                            reader.lines().forEach(System.out::println);
                            ~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }
}
