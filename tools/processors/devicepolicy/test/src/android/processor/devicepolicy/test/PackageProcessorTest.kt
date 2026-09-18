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

import org.junit.Test

class PackageProcessorTest {

  private val compiler = PolicyIdentifierCompiler()

  companion object {
    const val ALLOWED_DPC_TYPES_SNIPPET = PolicyIdentifierCompiler.ALLOWED_DPC_TYPES_SNIPPET
  }

  @Test
  fun test_packageProcessorWithoutPackageType_failsToCompile() {
    compiler.compileExpectError(
      """
          /**
            * PackagePolicyDefinition can only be applied to policies of type PackageIdentifier.
            */
          @PackagePolicyDefinition(
                  base = @PolicyDefinition(
                          allowedScopes = { POLICY_SCOPE_USER },
                          affectedResource = RESOURCE_DEVICE_WIDE,
                          $ALLOWED_DPC_TYPES_SNIPPET
                  )
          )
          public static final PolicyIdentifier<String> POLICY_KEY =
              new PolicyIdentifier<>("POLICY_KEY");
      """,
      expectedError =
        "@PackagePolicyDefinition can only be applied to policies of type " +
          "android.app.admin.PackageIdentifier",
    )
  }
}
