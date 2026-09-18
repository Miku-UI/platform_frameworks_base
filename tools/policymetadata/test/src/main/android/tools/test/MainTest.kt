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

package android.tools.test

import android.tools.policymetadata.Main
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainTest {
    companion object {
        const val RESOURCE_ROOT = "test/resources/android/tools/policymetadata/test"

        const val INPUT_METADATA_TEXTPROTO = "$RESOURCE_ROOT/TestPolicyMetadata.textproto"
        const val EXPECTED_OUTPUT_JAVA = "$RESOURCE_ROOT/ExpectedPolicies.java"
    }

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun testExpectedPolicies() {
        val inputPath = tempFolder.root.toPath() / "input.textproto"
        val outputPath = tempFolder.root.toPath() / "output.java"
        inputPath.writeText(loadTextResource(INPUT_METADATA_TEXTPROTO))

        Main.main(arrayOf(inputPath.toString(), outputPath.toString()))

        assertThat(outputPath.readText()).isEqualTo(loadTextResource(EXPECTED_OUTPUT_JAVA))
    }

    private fun loadTextResource(path: String): String {
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
