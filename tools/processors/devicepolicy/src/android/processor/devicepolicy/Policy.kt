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

package android.processor.devicepolicy

import com.android.json.stream.JsonWriter

data class Policy(
    val name: String, val type: String, val documentation: String, val metadata: PolicyMetadata
) {
    fun dump(writer: JsonWriter) {
        writer.apply {
            beginObject()

            name("name")
            value(name)

            name("type")
            value(type)

            name("documentation")
            value(documentation)

            metadata.dump(writer)

            endObject()
        }
    }
}

abstract class PolicyMetadata() {
    abstract fun dump(writer: JsonWriter)
}

fun dumpJSON(writer: JsonWriter, items: List<Policy>) {
    writer.beginArray()

    items.forEachIndexed { index, policy ->
        policy.dump(writer)
    }

    writer.endArray()
}