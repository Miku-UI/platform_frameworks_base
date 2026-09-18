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

import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.tools.policymetadata.Generator
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.JavaFile
import java.io.CharArrayWriter

// Overload of [Generator.generate] that takes a builder of a single policy metadata.
fun Generator.generate(policy: PolicyMetadata.Builder): JavaFile =
    this.generate(PolicyMetadataList.newBuilder().addPolicyMetadata(policy).build())

// Helper assert that checks that the java file is the generated policy metadata containing the
// given policies code snippet.
fun JavaFile.assertContainsPolicy(
    code: String,
    includes: List<String> = listOf(),
    staticImports: List<String> = listOf(),
) {
    assertThat(javaFileToString(this))
        .isEqualTo(fillInFile(code = code, staticImports = staticImports, includes = includes))
}

fun simpleNameToFieldName(name: String): FullyQualifiedFieldName {
    val fieldName = name.substringAfterLast(".")
    val rest = name.substringBeforeLast(".")
    val className = rest.substringAfterLast(".")
    val packageName = rest.substringBeforeLast(".")

    return FullyQualifiedFieldName.newBuilder()
        .setFieldName(fieldName)
        .setClassName(className)
        .setPackageName(packageName)
        .build()
}

private fun fillInFile(
    code: String,
    includes: List<String> = listOf(),
    staticImports: List<String> = listOf(),
): String {
    var allIncludes = includes + listOf("java.util.ArrayList", "java.util.List", "java.util.Set")

    return trimLines(
        """
          package android.app.admin.metadata;

          ${
              staticImports.sorted().joinToString(
                  separator = ";\nimport static ",
                  prefix = "import static ",
                  postfix = ";",
              )
          }
          ${
              allIncludes.sorted().joinToString(
                  separator = ";\nimport ",
                  prefix = "import ",
                  postfix = ";",
              )
          }

          /**
          * Generated class that contains metadata on all known policies.
          *
          * @hide
          */
          public class Policies {
              /**
              * Generated method that returns a list of all policy metadata
              */
              public static List<PolicyMetadata<?>> loadPolicyMetadata() {
                  List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
                  $code
                  return policies;
              }
          }
        """
    )
}

private fun javaFileToString(file: JavaFile): String {
    val writer = CharArrayWriter()

    file.writeTo(writer)

    return trimLines(writer.toString())
}

/** Remove whitespace and empty lines to make string comparisons on code simpler for tests. */
private fun trimLines(string: String) =
    string.lines().map { it.trim() }.filter { !it.isEmpty() }.joinToString("\n")
