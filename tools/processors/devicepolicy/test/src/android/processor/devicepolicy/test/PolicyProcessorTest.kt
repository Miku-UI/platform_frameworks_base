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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.PolicyProcessor
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.testing.compile.Compilation
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import java.io.IOException
import javax.tools.StandardLocation.SOURCE_OUTPUT
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail

import org.junit.Test

class PolicyProcessorTest {
    private val mCompiler = Compiler.javac().withProcessors(PolicyProcessor())

    private companion object {
        const val RESOURCE_ROOT = "test/resources/android/processor/devicepolicy/test"

        const val POLICY_IDENTIFIER = "$RESOURCE_ROOT/PolicyIdentifier"
        const val POLICY_IDENTIFIER_JAVA = "$POLICY_IDENTIFIER.java"
        const val POLICY_IDENTIFIER_JSON = "$POLICY_IDENTIFIER.json"

        const val OTHER_CLASS_JAVA = "$RESOURCE_ROOT/OtherClass.java"
        const val POLICY_IDENTIFIER_INVALID_TYPE_JAVA = "$RESOURCE_ROOT/invalidtype/PolicyIdentifier.java"
        const val POLICY_IDENTIFIER_DIRECT_DEFINITION_JAVA = "$RESOURCE_ROOT/directPolicyDefinition/PolicyIdentifier.java"
        const val POLICY_IDENTIFIER_MISSING_DOCUMENTATION_JAVA = "$RESOURCE_ROOT/missingDocumentation/PolicyIdentifier.java"


        /**
         * Comes from the actual IntDef.java in the source, located in a different folder.
         */
        const val INT_DEF_JAVA = "android/annotation/IntDef.java"

        /**
         * Build path for the output.
         */
        const val POLICIES_JSON_LOCATION = "android/processor/devicepolicy/policies.json"

        fun loadTextResource(path: String): String {
            try {
                val url = Resources.getResource(path)
                assertNotNull(String.format("Resource file not found: %s", path), url)
                return Resources.toString(url, Charsets.UTF_8)
            } catch (e: IOException) {
                fail(e.message)
                return ""
            }
        }
    }

    @Test
    fun test_PolicyIdendifierFake_generates() {
        val expectedOutput = loadTextResource(POLICY_IDENTIFIER_JSON)

        val compilation: Compilation =
            mCompiler.compile(
                JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA),
                JavaFileObjects.forResource(INT_DEF_JAVA)
            )

        assertThat(compilation).succeeded()
        assertThat(compilation).generatedFile(SOURCE_OUTPUT,
            POLICIES_JSON_LOCATION).contentsAsUtf8String().isEqualTo(expectedOutput)
    }

    @Test
    fun test_other_class_failsToCompile() {
        val compilation: Compilation =
            mCompiler.compile(
                JavaFileObjects.forResource(OTHER_CLASS_JAVA),
                JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA)
            )
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition can only be applied to fields in android.app.admin.PolicyIdentifier")
    }

    @Test
    fun test_invalidType_failsToCompile() {
        val compilation: Compilation = mCompiler.compile(
            JavaFileObjects.forResource(POLICY_IDENTIFIER_INVALID_TYPE_JAVA)
        )
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("booleanValue in @PolicyDefinition can only be applied to policies of type java.lang.Boolean")
    }

    @Test
    fun test_directPolicyDefinition_failsToCompile() {
        val compilation: Compilation = mCompiler.compile(
            JavaFileObjects.forResource(POLICY_IDENTIFIER_DIRECT_DEFINITION_JAVA)
        )
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition should not be applied to any element")
    }

    @Test
    fun test_missingDocumentation_failsToCompile() {
        val compilation: Compilation = mCompiler.compile(
            JavaFileObjects.forResource(POLICY_IDENTIFIER_MISSING_DOCUMENTATION_JAVA)
        )
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Missing JavaDoc")
    }

    /**
     * Errors should only come from our processor.
     */
    @Test
    fun test_invalidTestData_compilesWithoutProcessor() {
        val plainCompiler = Compiler.javac()

        fun checkCompileSucceeds(vararg files: String) {
            val resources = files.map { JavaFileObjects.forResource(it) }

            val compilation = plainCompiler.compile(*resources.toTypedArray())
            assertThat(compilation).succeeded()
        }

        checkCompileSucceeds(OTHER_CLASS_JAVA, POLICY_IDENTIFIER_JAVA, INT_DEF_JAVA)
        checkCompileSucceeds(POLICY_IDENTIFIER_INVALID_TYPE_JAVA)
        checkCompileSucceeds(POLICY_IDENTIFIER_DIRECT_DEFINITION_JAVA)
        checkCompileSucceeds(POLICY_IDENTIFIER_MISSING_DOCUMENTATION_JAVA)
    }
}