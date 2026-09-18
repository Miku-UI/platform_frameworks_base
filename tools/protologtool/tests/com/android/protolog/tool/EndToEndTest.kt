/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.protolog.tool

import com.android.protolog.tool.ProtoLogTool.PROTOLOG_IMPL_SRC_PATH
import com.android.protolog.tool.ProtoLogTool.injector
import com.google.common.truth.Truth
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.jar.JarInputStream
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import perfetto.protos.PerfettoTrace

class EndToEndTest {
    private var javacFails = false
    private var useRealJavac = false

    @Before
    fun setUp() {
        javacFails = false
        useRealJavac = false
    }

    @Test
    fun e2e_transform() {
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                            """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    String argString = "hello";
                                    int argInt = 123;
                                    ProtoLog.d(GROUP, "Example: %s %d", argString, argInt);
                                }
                            }
                            """
                                .trimIndent()
                    ),
                logGroup = LogGroup("GROUP", true, false, "TAG_GROUP", 1),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        val outSrcJar = assertLoadSrcJar(output, "out.srcjar")
        Truth.assertThat(outSrcJar["frameworks/base/org/example/Example.java"])
            .contains(
                "String protoLogParam0 = String.valueOf(argString);" +
                    "  long protoLogParam1 = argInt;" +
                    "  com.android.internal.protolog.ProtoLogImpl_454675969.d(" +
                    "GROUP, -6872339441335321086L, 4L, protoLogParam0, protoLogParam1);" +
                    " }"
            )
    }

    @Test
    fun e2e_transform_moreThan16Params() {
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                                """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    ProtoLog.d(GROUP, "Example: %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
                                }
                            }
                            """
                                    .trimIndent()
                    ),
                logGroup = LogGroup("GROUP", true, false, "TAG_GROUP", 1),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        val outSrcJar = assertLoadSrcJar(output, "out.srcjar")
        Truth.assertThat(outSrcJar["frameworks/base/org/example/Example.java"])
            .contains(
                "long protoLogParam0 = 1;" +
                        "  long protoLogParam1 = 2;" +
                        "  long protoLogParam2 = 3;" +
                        "  long protoLogParam3 = 4;" +
                        "  long protoLogParam4 = 5;" +
                        "  long protoLogParam5 = 6;" +
                        "  long protoLogParam6 = 7;" +
                        "  long protoLogParam7 = 8;" +
                        "  long protoLogParam8 = 9;" +
                        "  long protoLogParam9 = 10;" +
                        "  long protoLogParam10 = 11;" +
                        "  long protoLogParam11 = 12;" +
                        "  long protoLogParam12 = 13;" +
                        "  long protoLogParam13 = 14;" +
                        "  long protoLogParam14 = 15;" +
                        "  long protoLogParam15 = 16;" +
                        "  long protoLogParam16 = 17;" +
                        "  long protoLogParam17 = 18;" +
                        "  long protoLogParam18 = 19;" +
                        "  long protoLogParam19 = 20;" +
                        "  com.android.internal.protolog.ProtoLogImpl_454675969.d(" +
                        "GROUP, 3161983496706734947L, 366503875925L, protoLogParam0, protoLogParam1, protoLogParam2, protoLogParam3, protoLogParam4, protoLogParam5, protoLogParam6, protoLogParam7, protoLogParam8, protoLogParam9, protoLogParam10, protoLogParam11, protoLogParam12, protoLogParam13, protoLogParam14, protoLogParam15, protoLogParam16, protoLogParam17, protoLogParam18, protoLogParam19);" +
                        " }"
            )
    }

    @Test
    fun e2e_viewerConfig() {
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                            """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    String argString = "hello";
                                    int argInt = 123;
                                    ProtoLog.d(GROUP, "Example: %s %d", argString, argInt);
                                }
                            }
                            """
                                .trimIndent()
                    ),
                logGroup = LogGroup("GROUP", true, false, "TAG_GROUP", 1),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "generate-viewer-config",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config",
                            "out.pb",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )

        Truth.assertThat(injector.processingErrors).isEmpty()
        Truth.assertThat(injector.processingWarnings).isEmpty()

        val viewerConfigRaw = output["out.pb"] ?: fail("out.pb not in outputs (${output.keys})")
        val viewerConfig = PerfettoTrace.ProtoLogViewerConfig.parseFrom(viewerConfigRaw)

        Truth.assertThat(viewerConfig.groupsList).hasSize(1)
        Truth.assertThat(viewerConfig.messagesList).hasSize(1)
        Truth.assertThat(viewerConfig.groupsList[0].id).isEqualTo(1)
        Truth.assertThat(viewerConfig.groupsList[0].name).isEqualTo("GROUP")
        Truth.assertThat(viewerConfig.groupsList[0].tag).isEqualTo("TAG_GROUP")
        Truth.assertThat(viewerConfig.messagesList[0].message).isEqualTo("Example: %s %d")
        Truth.assertThat(viewerConfig.messagesList[0].level)
            .isEqualTo(PerfettoTrace.ProtoLogLevel.PROTOLOG_LEVEL_DEBUG)
        Truth.assertThat(viewerConfig.messagesList[0].groupId).isEqualTo(1)
        Truth.assertThat(viewerConfig.messagesList[0].location)
            .isEqualTo("org/example/Example.java:9")
    }

    @Test
    fun e2e_transform_withErrors() {
        val srcs =
            mapOf(
                "frameworks/base/org/example/Example.java" to
                    """
                    package org.example;
                    import com.android.internal.protolog.ProtoLog;
                    import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                    class Example {
                        void method() {
                            String argString = "hello";
                            int argInt = 123;
                            ProtoLog.d(GROUP, "Invalid format: %s %d %9 %", argString, argInt);
                        }
                    }
                    """
                        .trimIndent()
            )
        val output =
            run(
                srcs = srcs,
                logGroup = LogGroup("GROUP", true, false, "TAG_GROUP", 1),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        val outSrcJar = assertLoadSrcJar(output, "out.srcjar")
        // No change to source code on failure to process
        Truth.assertThat(outSrcJar["frameworks/base/org/example/Example.java"])
            .contains(srcs["frameworks/base/org/example/Example.java"])

        Truth.assertThat(injector.processingErrors).hasSize(1)
        Truth.assertThat(injector.processingErrors.first().message).contains("Invalid format")
    }

    private fun assertLoadSrcJar(
        outputs: Map<String, ByteArray>,
        path: String,
    ): Map<String, String> {
        val out = outputs[path] ?: fail("$path not in outputs (${outputs.keys})")

        val sources = mutableMapOf<String, String>()
        JarInputStream(ByteArrayInputStream(out)).use { jarStream ->
            var entry = jarStream.nextJarEntry
            while (entry != null) {
                if (entry.name.endsWith(".java")) {
                    sources[entry.name] = jarStream.reader().readText()
                }
                entry = jarStream.nextJarEntry
            }
        }
        return sources
    }

    private fun assertLoadText(outputs: Map<String, ByteArray>, path: String): String {
        val out = outputs[path] ?: fail("$path not in outputs (${outputs.keys})")
        return out.toString(Charsets.UTF_8)
    }

    fun run(
        srcs: Map<String, String>,
        logGroup: LogGroup,
        commandOptions: CommandOptions,
    ): Map<String, ByteArray> {
        val outputs = mutableMapOf<String, ByteArrayOutputStream>()

        val srcs = srcs.toMutableMap()
        srcs[PROTOLOG_IMPL_SRC_PATH] =
            """
            package com.android.internal.protolog;

            import static com.android.internal.protolog.common.ProtoLogToolInjected.Value.VIEWER_CONFIG_PATH;

            import com.android.internal.protolog.common.ProtoLogToolInjected;

            public class ProtoLogImpl {
                @ProtoLogToolInjected(VIEWER_CONFIG_PATH)
                private static String sViewerConfigPath;
            }
            """
                .trimIndent()

        ProtoLogTool.injector =
            object : ProtoLogTool.Injector {
                override fun fileOutputStream(file: String): OutputStream =
                    ByteArrayOutputStream().also { outputs[file] = it }

                override fun readText(file: File): String {
                    for (src in srcs.entries) {
                        val filePath = src.key
                        if (file.path == filePath) {
                            return src.value
                        }
                    }
                    throw FileNotFoundException("$file not found in [${srcs.keys.joinToString()}].")
                }

                override fun readLogGroups(jarPath: String, className: String) =
                    mapOf(logGroup.name to logGroup)

                override fun reportProcessingError(ex: CodeProcessingException) {
                    processingErrors.add(ex)
                }

                override val processingErrors: MutableList<CodeProcessingException> =
                    mutableListOf()

                override fun reportWarning(ex: CodeProcessingException) {
                    processingWarnings.add(ex)
                }

                override val processingWarnings: MutableList<CodeProcessingException> =
                    mutableListOf()

                override fun hasJavaSyntaxErrors(code: String, cmdWrapper: String?): Boolean {
                    if (useRealJavac) {
                        return ProtoLogTool.hasJavaSyntaxErrors(code, cmdWrapper)
                    }
                    return javacFails
                }
            }

        ProtoLogTool.invoke(commandOptions)

        return outputs.mapValues { it.value.toByteArray() }
    }

    @Test
    fun e2e_transform_fileWithJavacError_shouldExitSilently() {
        javacFails = true
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                            """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    // This is a syntax error
                                    ProtoLog.d(GROUP, "Example: %s %d", argString, argInt)
                                }
                            }
                            """
                                .trimIndent()
                    ),
                logGroup =
                    LogGroup(
                        "GROUP",
                        enabled = true,
                        textEnabled = false,
                        tag = "TAG_GROUP",
                        id = 1,
                    ),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        assertLoadSrcJar(output, "out.srcjar")
        Truth.assertThat(injector.processingErrors).isEmpty()
        Truth.assertThat(injector.processingWarnings).hasSize(1)
    }

    @Test
    fun e2e_transform_fileWithProtologToolParserError_shouldExitWithError() {
        // We set javacFails = false to force it to not report a javac failure. So the code here is
        // intended to just trigger a failure in the javaparser not a javac failure, the syntax
        // error here in the test source code will achieve this.
        javacFails = false
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                            """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    // This is a syntax error
                                    ProtoLog.d(GROUP, "Example: %s %d", argString, argInt)
                                }
                            }
                            """
                                .trimIndent()
                    ),
                logGroup =
                    LogGroup(
                        "GROUP",
                        enabled = true,
                        textEnabled = false,
                        tag = "TAG_GROUP",
                        id = 1,
                    ),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        assertLoadSrcJar(output, "out.srcjar")
        Truth.assertThat(injector.processingErrors).hasSize(1)
        Truth.assertThat(injector.processingWarnings).isEmpty()
    }

    @Test
    fun e2e_viewerConfig_fileWithJavacError_shouldExitSilently() {
        javacFails = true
        run(
            srcs =
                mapOf(
                    "frameworks/base/org/example/Example.java" to
                        """
                        package org.example;
                        import com.android.internal.protolog.ProtoLog;
                        import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                        class Example {
                            void method() {
                                ProtoLog.d(GROUP, "Example: %s %d", argString, argInt)
                            }
                        }
                        """
                            .trimIndent()
                ),
            logGroup =
                LogGroup("GROUP", enabled = true, textEnabled = false, tag = "TAG_GROUP", id = 1),
            commandOptions =
                CommandOptions(
                    arrayOf(
                        "generate-viewer-config",
                        "--protolog-class",
                        "com.android.internal.protolog.ProtoLog",
                        "--loggroups-class",
                        "com.android.internal.protolog.ProtoLogGroup",
                        "--loggroups-jar",
                        "not_required.jar",
                        "--viewer-config",
                        "out.pb",
                        "frameworks/base/org/example/Example.java",
                    )
                ),
        )

        Truth.assertThat(injector.processingErrors).isEmpty()
        Truth.assertThat(injector.processingWarnings).hasSize(1)
    }

    @Test
    fun e2e_viewerConfig_fileWithProtologToolParserError_shouldExitWithError() {
        javacFails = false
        run(
            srcs =
                mapOf(
                    "frameworks/base/org/example/Example.java" to
                        """
                        package org.example;
                        import com.android.internal.protolog.ProtoLog;
                        import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                        class Example {
                            void method() {
                                ProtoLog.d(GROUP, "Example: %s %d", argString, argInt)
                            }
                        }
                        """
                            .trimIndent()
                ),
            logGroup =
                LogGroup("GROUP", enabled = true, textEnabled = false, tag = "TAG_GROUP", id = 1),
            commandOptions =
                CommandOptions(
                    arrayOf(
                        "generate-viewer-config",
                        "--protolog-class",
                        "com.android.internal.protolog.ProtoLog",
                        "--loggroups-class",
                        "com.android.internal.protolog.ProtoLogGroup",
                        "--loggroups-jar",
                        "not_required.jar",
                        "--viewer-config",
                        "out.pb",
                        "frameworks/base/org/example/Example.java",
                    )
                ),
        )

        Truth.assertThat(injector.processingErrors).hasSize(1)
        Truth.assertThat(injector.processingWarnings).isEmpty()
    }

    @Test
    fun e2e_transform_fileWithJavacError_shouldExitSilently_realJavac() {
        useRealJavac = true
        val output =
            run(
                srcs =
                    mapOf(
                        "frameworks/base/org/example/Example.java" to
                            """
                            package org.example;
                            import com.android.internal.protolog.ProtoLog;
                            import static com.android.internal.protolog.ProtoLogGroup.GROUP;

                            class Example {
                                void method() {
                                    // This is a syntax error
                                    ProtoLog.d(GROUP, "Example: %s %d", argString, argInt)
                                }
                            }
                            """
                                .trimIndent()
                    ),
                logGroup = LogGroup("GROUP", true, false, "TAG_GROUP", 1),
                commandOptions =
                    CommandOptions(
                        arrayOf(
                            "transform-protolog-calls",
                            "--protolog-class",
                            "com.android.internal.protolog.ProtoLog",
                            "--loggroups-class",
                            "com.android.internal.protolog.ProtoLogGroup",
                            "--loggroups-jar",
                            "not_required.jar",
                            "--viewer-config-file-path",
                            "not_required.pb",
                            "--output-srcjar",
                            "out.srcjar",
                            "frameworks/base/org/example/Example.java",
                        )
                    ),
            )
        assertLoadSrcJar(output, "out.srcjar")
        Truth.assertThat(injector.processingErrors).isEmpty()
        Truth.assertThat(injector.processingWarnings).hasSize(1)
    }

    fun fail(message: String): Nothing = Assert.fail(message) as Nothing
}
