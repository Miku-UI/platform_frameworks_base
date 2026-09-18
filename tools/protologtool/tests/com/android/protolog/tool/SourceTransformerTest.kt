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

import com.android.internal.protolog.common.LogLevel
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.MethodCallExpr
import com.google.common.truth.Truth
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito

class SourceTransformerTest {
    companion object {

        private val TEST_CODE = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1);
                }
            }
            """.trimIndent()

        private val TEST_CODE_MULTILINE = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, "test %d %f " +
                    "abc %s\n test", 100,
                     0.1, "test");
                }
            }
            """.trimIndent()

        private val TEST_CODE_BLOCK_STRING = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, ${"\"\"\""}
            test %d %f
            abc %s
            test${"\"\"\""},
                     100, 0.1, "test");
                }
            }
            """.trimIndent()

        private val TEST_CODE_MULTICALLS = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1); /* ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1); */ ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1);
                    ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1);
                }
            }
            """.trimIndent()

        private val TEST_CODE_NO_PARAMS = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, "test");
                }
            }
            """.trimIndent()

        private val TEST_CODE_MORE_THAN_16_PARAMS = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.w(TEST_GROUP, "test %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_TEXT_ENABLED = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  org.example.ProtoLogImpl.w(TEST_GROUP, -1473209266730422156L, 9L, protoLogParam0, protoLogParam1); }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_MULTILINE_TEXT_ENABLED = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  String protoLogParam2 = String.valueOf("test");  org.example.ProtoLogImpl.w(TEST_GROUP, -4447034859795564700L, 9L, protoLogParam0, protoLogParam1, protoLogParam2);

            }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_BLOCK_TEXT = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  String protoLogParam2 = String.valueOf("test");  org.example.ProtoLogImpl.w(TEST_GROUP, 4940069191137245365L, 9L, protoLogParam0, protoLogParam1, protoLogParam2);



            }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_MULTICALL_TEXT = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  org.example.ProtoLogImpl.w(TEST_GROUP, -1473209266730422156L, 9L, protoLogParam0, protoLogParam1); } /* ProtoLog.w(TEST_GROUP, "test %d %f", 100, 0.1); */ if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  org.example.ProtoLogImpl.w(TEST_GROUP, -1473209266730422156L, 9L, protoLogParam0, protoLogParam1); }
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  org.example.ProtoLogImpl.w(TEST_GROUP, -1473209266730422156L, 9L, protoLogParam0, protoLogParam1); }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_NO_PARAMS = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  org.example.ProtoLogImpl.w(TEST_GROUP, 3218600869538902408L, 0L, (Object[]) null); }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_MORE_THAN_16_PARAMS = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 1;  long protoLogParam1 = 2;  long protoLogParam2 = 3;  long protoLogParam3 = 4;  long protoLogParam4 = 5;  long protoLogParam5 = 6;  long protoLogParam6 = 7;  long protoLogParam7 = 8;  long protoLogParam8 = 9;  long protoLogParam9 = 10;  long protoLogParam10 = 11;  long protoLogParam11 = 12;  long protoLogParam12 = 13;  long protoLogParam13 = 14;  long protoLogParam14 = 15;  long protoLogParam15 = 16;  long protoLogParam16 = 17;  long protoLogParam17 = 18;  long protoLogParam18 = 19;  long protoLogParam19 = 20;  org.example.ProtoLogImpl.w(TEST_GROUP, 3061299425417639850L, 366503875925L, protoLogParam0, protoLogParam1, protoLogParam2, protoLogParam3, protoLogParam4, protoLogParam5, protoLogParam6, protoLogParam7, protoLogParam8, protoLogParam9, protoLogParam10, protoLogParam11, protoLogParam12, protoLogParam13, protoLogParam14, protoLogParam15, protoLogParam16, protoLogParam17, protoLogParam18, protoLogParam19); }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_TEXT_DISABLED = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  org.example.ProtoLogImpl.w(TEST_GROUP, -1473209266730422156L, 9L, protoLogParam0, protoLogParam1); }
                }
            }
            """.trimIndent()

        private val TRANSFORMED_CODE_MULTILINE_TEXT_DISABLED = """
            package org.example;

            class Test {
                void test() {
                    if (org.example.ProtoLogImpl.Cache.TEST_GROUP_enabled[3]) {  long protoLogParam0 = 100;  double protoLogParam1 = 0.1;  String protoLogParam2 = String.valueOf("test");  org.example.ProtoLogImpl.w(TEST_GROUP, -4447034859795564700L, 9L, protoLogParam0, protoLogParam1, protoLogParam2);

            }
                }
            }
            """.trimIndent()

        private const val PATH = "com.example.Test.java"

        @BeforeClass
        @JvmStatic
        fun setParserConfig() {
            StaticJavaParser.setConfiguration(ProtoLogTool.PARSER_CONFIG)
        }
    }

    private val processor: ProtoLogCallProcessor = Mockito.mock(ProtoLogCallProcessor::class.java)
    private val implName = "org.example.ProtoLogImpl"
    private val sourceJarWriter = SourceTransformer(implName, processor)

    private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

    @Test
    fun processClass_textEnabled() {
        var code = StaticJavaParser.parse(TEST_CODE)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0], "test %d %f",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 123)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(5, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("-1473209266730422156L", methodCall.arguments[1].toString())
        assertEquals(0b1001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals(TRANSFORMED_CODE_TEXT_ENABLED, out)
    }

    @Test
    fun processClass_textEnabledMulticalls() {
        var code = StaticJavaParser.parse(TEST_CODE_MULTICALLS)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            val calls = code.findAll(MethodCallExpr::class.java)
            visitor.processCall(calls[0], "test %d %f",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 456)
            visitor.processCall(calls[1], "test %d %f",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 789)
            visitor.processCall(calls[2], "test %d %f",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 123)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_MULTICALLS, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(3)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(5, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("-1473209266730422156L", methodCall.arguments[1].toString())
        assertEquals(0b1001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals(TRANSFORMED_CODE_MULTICALL_TEXT, out)
    }

    @Test
    fun processClass_textEnabledMultiline() {
        var code = StaticJavaParser.parse(TEST_CODE_MULTILINE)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0],
                    "test %d %f abc %s\n test", LogLevel.WARN, LogGroup("TEST_GROUP",
                    true, true, "WM_TEST", 1), 123)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_MULTILINE, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(6, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("-4447034859795564700L", methodCall.arguments[1].toString())
        assertEquals(0b001001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals("protoLogParam2", methodCall.arguments[5].toString())
        assertEquals(TRANSFORMED_CODE_MULTILINE_TEXT_ENABLED, out)
    }

    @Test
    fun processClass_textEnabledMultiline_BlockString() {
        var code = StaticJavaParser.parse(TEST_CODE_BLOCK_STRING)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val cu = invocation.arguments[0] as CompilationUnit
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            val methodCallExpr = cu.findAll(MethodCallExpr::class.java)[0]
            val messageExpr = methodCallExpr.arguments[1]

            val dummyContext = ParsingContext("BLOCK_STRING_TEST", methodCallExpr)
            val extractedMessage = CodeUtils.concatMultilineString(messageExpr, dummyContext)

            assertEquals("test %d %f\nabc %s\ntest", extractedMessage)

            visitor.processCall(
                methodCallExpr,
                extractedMessage,
                LogLevel.WARN,
                LogGroup("TEST_GROUP", true, true, "WM_TEST", 1),
                123
            )

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_BLOCK_STRING, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(6, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("4940069191137245365L", methodCall.arguments[1].toString())
        assertEquals(0b001001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals("protoLogParam2", methodCall.arguments[5].toString())
        assertEquals(TRANSFORMED_CODE_BLOCK_TEXT, out)
    }

    @Test
    fun processClass_noParams() {
        var code = StaticJavaParser.parse(TEST_CODE_NO_PARAMS)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0], "test",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 456)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_NO_PARAMS, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(4, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("3218600869538902408L", methodCall.arguments[1].toString())
        assertEquals(0.toString() + "L", methodCall.arguments[2].toString())
        assertEquals(TRANSFORMED_CODE_NO_PARAMS, out)
    }

    @Test
    fun processClass_textEnabledMoreThan16Params() {
        var code = StaticJavaParser.parse(TEST_CODE_MORE_THAN_16_PARAMS)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0],
                "test %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d",
                LogLevel.WARN, LogGroup("TEST_GROUP", true, true, "WM_TEST", 1), 456)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_MORE_THAN_16_PARAMS, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(23, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("3061299425417639850L", methodCall.arguments[1].toString())
        // Denotes 20 integers each encoded with '01'
        assertEquals(0b0101010101010101010101010101010101010101.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam19", methodCall.arguments[22].toString())
        assertEquals(TRANSFORMED_CODE_MORE_THAN_16_PARAMS, out)
    }

    @Test
    fun processClass_textDisabled() {
        var code = StaticJavaParser.parse(TEST_CODE)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0], "test %d %f",
                    LogLevel.WARN, LogGroup("TEST_GROUP", true, false, "WM_TEST", 1), 789)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(5, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("-1473209266730422156L", methodCall.arguments[1].toString())
        assertEquals(0b1001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals(TRANSFORMED_CODE_TEXT_DISABLED, out)
    }

    @Test
    fun processClass_textDisabledMultiline() {
        var code = StaticJavaParser.parse(TEST_CODE_MULTILINE)

        Mockito.`when`(processor.process(
            any(CompilationUnit::class.java),
            any(ProtoLogCallVisitor::class.java),
            any(MethodCallVisitor::class.java),
            any(String::class.java))
        ).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(code.findAll(MethodCallExpr::class.java)[0],
                    "test %d %f abc %s\n test", LogLevel.WARN, LogGroup("TEST_GROUP",
                    true, false, "WM_TEST", 1), 123)

            listOf<CodeProcessingException>()
        }

        val (out, _) = sourceJarWriter.processClass(TEST_CODE_MULTILINE, PATH, PATH, code)
        code = StaticJavaParser.parse(out)

        val protoLogCalls = code.findAll(MethodCallExpr::class.java).filter {
            it.scope.orElse(null)?.toString() == implName
        }
        Truth.assertThat(protoLogCalls).hasSize(1)
        val methodCall = protoLogCalls[0] as MethodCallExpr
        assertEquals("w", methodCall.name.asString())
        assertEquals(6, methodCall.arguments.size)
        assertEquals("TEST_GROUP", methodCall.arguments[0].toString())
        assertEquals("-4447034859795564700L", methodCall.arguments[1].toString())
        assertEquals(0b001001.toString() + "L", methodCall.arguments[2].toString())
        assertEquals("protoLogParam0", methodCall.arguments[3].toString())
        assertEquals("protoLogParam1", methodCall.arguments[4].toString())
        assertEquals("protoLogParam2", methodCall.arguments[5].toString())
        assertEquals(TRANSFORMED_CODE_MULTILINE_TEXT_DISABLED, out)
    }
}
