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

package com.android.server.display

import java.util.function.Consumer

/**
 * Test utilities for [PersistentDataStoreTest] designed for Java compatibility.
 * Provides a fluent builder to construct `display-manager-state` XML.
 */
internal class PersistentDataStoreTestUtils {
    companion object {
        /** Returns a new builder for creating a `display-manager-state` XML string. */
        @JvmStatic
        fun stateBuilder(): DisplayManagerStateBuilder {
            return DisplayManagerStateBuilder()
        }

        /** Creates a mock DisplayDevice with a stable unique ID for testing. */
        @JvmStatic
        @JvmOverloads
        fun createTestDisplayDevice(
            displayAdapter: DisplayAdapter,
            uniqueId: String?,
            hasStableUniqueId: Boolean = true,
        ): DisplayDevice {
            return object : DisplayDevice(displayAdapter, null, uniqueId, null) {
                override fun hasStableUniqueId() = hasStableUniqueId
                override fun getDisplayDeviceInfoLocked() = null
            }
        }
    }
}

/** Fluent builder for the top-level <display-manager-state>. */
internal class DisplayManagerStateBuilder {
    private val displayStates = StringBuilder()

    /**
     * Adds a `<display>` entry to the state.
     * @param uniqueId The unique ID for the display.
     * @param action A lambda to configure the display's properties.
     * @return The same builder instance for chaining.
     */
    fun display(uniqueId: String, action: Consumer<DisplayStateBuilder>): DisplayManagerStateBuilder {
        val builder = DisplayStateBuilder(uniqueId)
        action.accept(builder)
        displayStates.append(builder.build())
        return this
    }

    fun build(): String {
        return """
            |<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            |<display-manager-state>
            |    <display-states>
            |${displayStates.toString().prependIndent("        ")}
            |    </display-states>
            |</display-manager-state>
        """.trimMargin()
    }
}

/** Fluent builder for a single `<display>` entry's properties. */
internal class DisplayStateBuilder(private val uniqueId: String) {
    private val properties = StringBuilder()

    fun withConnectionPreference(preference: Int): DisplayStateBuilder {
        properties.append("<connection-preference>$preference</connection-preference>\n")
        return this
    }

    fun withBrightness(userSerial: Int, value: Float): DisplayStateBuilder {
        properties.append(
            "<brightness-value user-serial=\"$userSerial\">$value</brightness-value>\n"
        )
        return this
    }

    fun build(): String {
        return """
            |<display unique-id="$uniqueId">
            |${properties.toString().prependIndent("    ")}
            |</display>
        """.trimMargin()
    }
}