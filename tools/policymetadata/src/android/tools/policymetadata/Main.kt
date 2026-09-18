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

 package android.tools.policymetadata

import android.processor.devicepolicy.protos.PolicyMetadataList
import com.google.protobuf.TextFormat
import java.io.File

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected 2 arguments, but got ${args.size}" }

        // Read the textproto file.
        val content = File(args[0]).readText(Charsets.UTF_8)
        val policyMetadata = TextFormat.parse(content, PolicyMetadataList::class.java)

        // Generate the code.
        val policiesClass = Generator.generate(policyMetadata)

        // Write the Java file.
        val outputFile = File(args[1])
        outputFile.bufferedWriter().use { writer ->
            Generator.addLicense(writer)
            policiesClass.writeTo(writer)
        }
    }
}
