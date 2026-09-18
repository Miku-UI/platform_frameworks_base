/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.processor.devicepolicy.DevicePolicyAnnotationProcessor
import android.processor.devicepolicy.protos.PolicyMetadataList
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.protobuf.Message
import com.google.protobuf.TextFormat
import com.google.testing.compile.Compilation
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import javax.tools.JavaFileObject
import javax.tools.StandardLocation.SOURCE_OUTPUT
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import org.junit.Assert.assertNotNull

// Helper class to test compilation of a PolicyIdentifier file.
class PolicyIdentifierCompiler() {

    private val compiler: Compiler =
        Compiler.javac().withProcessors(DevicePolicyAnnotationProcessor())
    private val compilerWithoutProcessor: Compiler = Compiler.javac()

    // Compiles the given policies snippet, and returns the generated protobuf file
    fun compileExpectSuccess(policies: String): PolicyMetadataList =
        compileExpectSuccess(buildPolicyIdentifier(policies))

    // Compiles the given java files, and returns the generated protobuf file
    fun compileExpectSuccess(vararg files: JavaFileObject): PolicyMetadataList {
        val compilation: Compilation = compiler.compile(files.asList())
        assertThat(compilerWithoutProcessor.compile(files.asList())).succeeded()
        assertThat(compilation).succeeded()

        val result = compilation.generatedProto()
        checkNotNull(result)
        return result
    }

    // Compiles the given policies snippet, and checks that the given error is reported.
    fun compileExpectError(policies: String, expectedError: String) =
        compileExpectErrors(policies, expectedErrors = listOf(expectedError))

    // Compiles the given java files, and checks that the given error is reported.
    fun compileExpectError(vararg files: JavaFileObject, expectedError: String) =
        compileExpectErrors(*files, expectedErrors = listOf(expectedError))

    // Compiles the given policy snippet, and checks that the given errors are reported.
    fun compileExpectErrors(policies: String, expectedErrors: List<String>) =
        compileExpectErrors(buildPolicyIdentifier(policies), expectedErrors = expectedErrors)

    // Compiles the given java files, and checks that the given errors are reported.
    fun compileExpectErrors(
        vararg files: JavaFileObject,
        expectedErrors: List<String>,
    ): Compilation {
        val compilation: Compilation = compiler.compile(files.asList())
        assertThat(compilerWithoutProcessor.compile(files.asList())).succeeded()
        assertThat(compilation).failed()
        expectedErrors.forEach { assertThat(compilation).hadErrorContaining(it) }
        return compilation
    }

    private fun buildPolicyIdentifier(policies: String): JavaFileObject =
        JavaFileObjects.forSourceLines(
            "android.app.admin.PolicyIdentifier",
            """
                package android.app.admin;

                import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                import android.annotation.IntDef;
                import android.processor.devicepolicy.AllowedDpcTypes;
                import android.processor.devicepolicy.BooleanPolicyDefinition;
                import android.processor.devicepolicy.EnumPolicyDefinition;
                import android.processor.devicepolicy.EnumResolutionMechanism;
                import android.processor.devicepolicy.IntegerPolicyDefinition;
                import android.processor.devicepolicy.IntegerResolutionMechanism;
                import android.processor.devicepolicy.ListOfPackagePolicyDefinition;
                import android.processor.devicepolicy.ListOfStringPolicyDefinition;
                import android.processor.devicepolicy.ListResolutionMechanism;
                import android.processor.devicepolicy.LongPolicyDefinition;
                import android.processor.devicepolicy.LongResolutionMechanism;
                import android.processor.devicepolicy.PackagePolicyDefinition;
                import android.processor.devicepolicy.PolicyDefinition;
                import android.processor.devicepolicy.StringPolicyDefinition;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.util.List;

                public final class PolicyIdentifier<T> {
                    // Allow using the constants without having to build DevicePolicyManager
                    // in our tests.
                    public static final int POLICY_SCOPE_USER = 1;
                    public static final int POLICY_SCOPE_DEVICE = 2;
                    public static final int POLICY_SCOPE_PARENT_USER = 3;
                    public static final int RESOURCE_DEVICE_WIDE = 1;
                    public static final int RESOURCE_PER_USER = 2;

                    // We don't actually do anything with this.
                    public PolicyIdentifier(String id) {}

                    ${policies.trimIndent()}
                }
            """
                .trimIndent(),
        )

    companion object {

        // Can be used by tests that do not care about the allowedDpcTypes field.
        const val ALLOWED_DPC_TYPES_SNIPPET =
            """
            allowedDpcTypes = @AllowedDpcTypes(
                    deviceOwner = DISALLOWED,
                    financedDeviceOwner = DISALLOWED,
                    managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                    managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                    profileOwnerOnUser0 = DISALLOWED,
                    unaffiliatedFullUserProfileOwner = DISALLOWED,
                    affiliatedFullUserProfileOwner = DISALLOWED)
        """

        /** Build path for the output. */
        private const val POLICIES_TEXTPROTO_LOCATION =
            "android/processor/devicepolicy/policies.textproto"

        private fun <T : Message> CharSequence.parseProto(kClass: KClass<T>) =
            TextFormat.parse(this, kClass.java)

        private fun Compilation.generatedProto(): PolicyMetadataList? {
            return this.generatedFile(SOURCE_OUTPUT, POLICIES_TEXTPROTO_LOCATION)
                .getOrNull()
                ?.getCharContent(true)
                ?.parseProto(PolicyMetadataList::class)
        }
    }
}
