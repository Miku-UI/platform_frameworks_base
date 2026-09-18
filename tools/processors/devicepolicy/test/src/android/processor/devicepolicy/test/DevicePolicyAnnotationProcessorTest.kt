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

import android.processor.devicepolicy.protos.PolicyMetadataList
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.protobuf.Message
import com.google.protobuf.TextFormat
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.JavaFileObjects
import java.io.IOException
import kotlin.reflect.KClass
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class DevicePolicyAnnotationProcessorTest {

    private val compiler = PolicyIdentifierCompiler()

    private companion object {
        const val RESOURCE_ROOT = "test/resources/android/processor/devicepolicy/test"

        const val POLICY_IDENTIFIER_JAVA = "$RESOURCE_ROOT/TestPolicyIdentifier.java"
        const val POLICY_IDENTIFIER_LOCATION = "android/app/admin/PolicyIdentifier"
        const val POLICY_IDENTIFIER_TEXTPROTO = "$RESOURCE_ROOT/ExpectedPolicyIdentifier.textproto"

        const val ALLOWED_DPC_TYPES_SNIPPET = PolicyIdentifierCompiler.ALLOWED_DPC_TYPES_SNIPPET

        // A set of source files required to compile `POLICY_IDENTIFIER_JAVA`
        val REQUIRED_SOURCE_FILES = setOf("android/annotation/IntDef.java")

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
    fun test_policyIdentifierFake_generates() {
        val expectedOutput = loadTextResource(POLICY_IDENTIFIER_TEXTPROTO)
        val requiredSources =
            REQUIRED_SOURCE_FILES.map { JavaFileObjects.forResource(it) }.toTypedArray()

        val generatedProto =
            compiler.compileExpectSuccess(
                JavaFileObjects.forSourceString(
                    POLICY_IDENTIFIER_LOCATION,
                    loadTextResource(POLICY_IDENTIFIER_JAVA),
                ),
                *requiredSources,
            )

        assertThat(generatedProto).isEqualTo(expectedOutput.parseProto(PolicyMetadataList::class))
    }

    @Test
    fun test_other_class_failsToCompile() {
        val otherClass =
            JavaFileObjects.forSourceLines(
                "android.app.admin.OtherClass",
                """
                    package android.app.admin;

                    import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                    import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                    import android.processor.devicepolicy.AllowedDpcTypes;
                    import android.processor.devicepolicy.BooleanPolicyDefinition;
                    import android.processor.devicepolicy.PolicyDefinition;

                    public final class OtherClass {
                        public OtherClass(String id) {}

                        /**
                        * Policies can only be defined in PolicyIdentifier.
                        */
                        @BooleanPolicyDefinition(
                                base = @PolicyDefinition(
                                        allowedScopes = { 1 },
                                        affectedResource = 1,
                                        $ALLOWED_DPC_TYPES_SNIPPET
                                )
                        )
                        public static final PolicyIdentifier<Boolean> LOST_POLICY =
                            new PolicyIdentifier<>("LOST_POLICY");
                    }
                """
                    .trimIndent(),
            )
        val policyIdentifier =
            JavaFileObjects.forSourceString(
                POLICY_IDENTIFIER_LOCATION,
                loadTextResource(POLICY_IDENTIFIER_JAVA),
            )

        compiler.compileExpectError(
            otherClass,
            policyIdentifier,
            expectedError =
                "@PolicyDefinition can only be applied to fields in android.app.admin.PolicyIdentifier",
        )
    }

    @Test
    fun test_invalidType_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Type of metadata and identifier must match.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_DEVICE },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Integer> INVALID_TYPE =
                    new PolicyIdentifier<>("INVALID_TYPE");
            """,
            expectedError =
                "booleanValue in @PolicyDefinition can only be applied to policies of type java.lang.Boolean",
        )
    }

    @Test
    fun test_directPolicyDefinition_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Don't use @PolicyDefinition
                */
                @PolicyDefinition(
                        allowedScopes = { POLICY_SCOPE_DEVICE },
                        affectedResource = RESOURCE_DEVICE_WIDE,
                        $ALLOWED_DPC_TYPES_SNIPPET
                )
                public static final PolicyIdentifier<Boolean> INVALID_ANNOTATION =
                    new PolicyIdentifier<>("INVALID_ANNOTATION");
            """,
            expectedError =
                "@PolicyDefinition can not be applied to any element, use a type-specific annotation such as @EnumPolicyDefinition instead",
        )
    }

    @Test
    fun test_missingDocumentation_failsToCompile() {
        compiler.compileExpectError(
            """
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_DEVICE },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> MISSING_DOCS =
                    new PolicyIdentifier<>("MISSING_DOCS");
            """,
            expectedError = "Missing JavaDoc",
        )
    }

    @Test
    fun test_emptyScope_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Empty allowedScopes should fail.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = {},
                                affectedResource = RESOURCE_PER_USER,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY =
                    new PolicyIdentifier<>("EMPTY_SCOPE");
            """,
            expectedError = "allowedScopes must not be empty",
        )
    }

    @Test
    fun test_invalidScopeValue_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Invalid scope should fail.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { 100 },
                                affectedResource = RESOURCE_PER_USER,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY =
                    new PolicyIdentifier<>("INVALID_SCOPE");
            """,
            expectedError = "allowedScopes contains an unknown value",
        )
    }

    @Test
    fun test_undefinedScope_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Unspecified (0) scope should fail.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { 0 },
                                affectedResource = RESOURCE_PER_USER,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> UNDEFINED_SCOPE =
                    new PolicyIdentifier<>("UNDEFINED_SCOPE");
            """,
            expectedError = "allowedScopes contains an unknown value",
        )
    }

    @Test
    fun test_invalidAffectedResource_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Invalid resource should fail.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = 100,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> INVALID_AFFECTED_RESOURCE_POLICY =
                    new PolicyIdentifier<>("INVALID_AFFECTED_RESOURCE");
            """,
            expectedError = "affectedResource is set to an unknown value",
        )
    }

    @Test
    fun test_undefinedAffectedResource_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Unspecified (0) resource should fail.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = 0,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> UNSPECIFIED_AFFECTED_RESOURCE_POLICY =
                    new PolicyIdentifier<>("UNSPECIFIED_AFFECTED_RESOURCE");
            """,
            expectedError = "affectedResource is set to an unknown value",
        )
    }

    @Test
    fun test_invalidCrossUserPermission_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * requiredCrossUserPermission only allows one of the 3 permissions.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                requiredCrossUserPermission = "my.custom.PERMISSION",
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> INVALID_PERMISSION =
                    new PolicyIdentifier<>("INVALID_PERMISSION");
            """,
            expectedError = "requiredCrossUserPermission was set to",
        )
    }

    @Test
    fun test_missingModifiers_failsToCompile() {
        compiler.compileExpectErrors(
            """
                /**
                * field must be public, static and final.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                private PolicyIdentifier<Boolean> MISSING_MODIFIERS =
                    new PolicyIdentifier<>("MISSING_MODIFIERS");
            """,
            expectedErrors =
                listOf("Field must be static", "Field must be public", "Field must be final"),
        )
    }

    @Test
    fun test_invalidInitializer_failsToCompile() {
        compiler.compileExpectError(
            """
                private static final String INVALID_INITIALIZER_KEY = "INVALID_INITIALIZER";
                /**
                * Initializer must use a literal String.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> INVALID_INITIALIZER =
                    new PolicyIdentifier<>(INVALID_INITIALIZER_KEY);
            """,
            expectedError = "the argument to the constructor is not a literal",
        )
    }

    @Test
    fun test_wrongKey_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * Initializer and keys must match.
                */
                @BooleanPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        )
                )
                public static final PolicyIdentifier<Boolean> INVALID_KEY_POLICY =
                    new PolicyIdentifier<>("WRONG_KEY_POLICY");
            """,
            expectedError = "the argument to the constructor should be \"INVALID_KEY_POLICY\"",
        )
    }

    private fun <T : Message> CharSequence.parseProto(kClass: KClass<T>) =
        TextFormat.parse(this, kClass.java)
}
