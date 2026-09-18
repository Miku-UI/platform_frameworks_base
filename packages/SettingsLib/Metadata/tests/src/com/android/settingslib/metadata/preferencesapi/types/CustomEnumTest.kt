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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.test.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomEnumTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A mock Integer-based Enum to test generic type <Int>, with string res purposes. */
    private enum class TestIntEnum(override val asApiValue: Int, override val purpose: Int) :
        EnumApiWithRes<Int> {
        FIRST(1, R.string.enum_api_purpose_message1),
        SECOND(2, R.string.enum_api_purpose_message2),
        NEGATIVE(-100, R.string.enum_api_purpose_message3),
    }

    /** A mock String-based Enum to test generic type <String>, with string purposes */
    private enum class TestStringEnum(
        override val asApiValue: String,
        override val purpose: String,
    ) : EnumApiWithString<String> {
        ACTIVE("active_state", ENUM_PURPOSE_MESSAGE),
        INACTIVE("inactive_state", ENUM_PURPOSE_MESSAGE2),
    }

    /** An empty Enum */
    private enum class EmptyEnum(override val asApiValue: Int, override val purpose: Int = 0) :
        EnumApiWithRes<Int>

    /** An incorrect Enum */
    private enum class IncorrectEnum(override val asApiValue: Int) : EnumApi<Int>

    @Test
    fun instantiateCustomEnum_wrongEnumApiInterface_returnsInitError() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomEnum(IncorrectEnum::class, "Incorrect enum")
        }
    }

    @Test
    fun getOptions_integerEnum_returnCorrectPairValuePurpose() {
        val customEnumOptions = runBlocking {
            CustomEnum(TestIntEnum::class, "Test Int enum").getOptions(context)
        }

        assertThat(customEnumOptions[0])
            .isEqualTo(TestIntEnum.FIRST to context.getString(R.string.enum_api_purpose_message1))
        assertThat(customEnumOptions[1])
            .isEqualTo(TestIntEnum.SECOND to context.getString(R.string.enum_api_purpose_message2))
        assertThat(customEnumOptions[2])
            .isEqualTo(
                TestIntEnum.NEGATIVE to context.getString(R.string.enum_api_purpose_message3)
            )
    }

    @Test
    fun getOptions_stringEnum_returnCorrectPairValuePurpose() {
        val customEnumOptions = runBlocking {
            CustomEnum(TestStringEnum::class, "Test String enum").getOptions(context)
        }

        assertThat(customEnumOptions[0]).isEqualTo(TestStringEnum.ACTIVE to ENUM_PURPOSE_MESSAGE)
        assertThat(customEnumOptions[1]).isEqualTo(TestStringEnum.INACTIVE to ENUM_PURPOSE_MESSAGE2)
    }

    @Test
    fun fromApiValue_validInteger_returnCorrectName() {
        val customEnum = CustomEnum(TestIntEnum::class, "Test Int enum")

        assertThat(customEnum.fromApiValue(1)).isEqualTo(TestIntEnum.FIRST)
        assertThat(customEnum.fromApiValue(2)).isEqualTo(TestIntEnum.SECOND)
        assertThat(customEnum.fromApiValue(-100)).isEqualTo(TestIntEnum.NEGATIVE)
    }

    @Test
    fun fromApiValue_invalidInteger_returnNull() {
        val customEnum = CustomEnum(TestIntEnum::class, "Test Int enum")

        assertThat(customEnum.fromApiValue(999)).isNull()
    }

    @Test
    fun fromApiValue_validString_returnCorrectName() {
        val customEnum = CustomEnum(TestStringEnum::class, "Test String enum")

        assertThat(customEnum.fromApiValue("active_state")).isEqualTo(TestStringEnum.ACTIVE)
        assertThat(customEnum.fromApiValue("inactive_state")).isEqualTo(TestStringEnum.INACTIVE)
    }

    @Test
    fun fromApiValue_invalidString_returnNull() {
        val customEnum = CustomEnum(TestStringEnum::class, "Test String enum")

        assertThat(customEnum.fromApiValue("weird_state")).isNull()
    }

    @Test
    fun fromApiValue_emptyEnum_returnNull() {
        val customEnum = CustomEnum(EmptyEnum::class, "Empty enum")

        assertThat(customEnum.fromApiValue(1)).isNull()
    }

    @Test
    fun getEntries_validEnum_returnAllConstants() {
        val customEnum = CustomEnum(TestIntEnum::class, "Test Int enum")

        assertThat(customEnum.getEntries()).apply {
            hasSize(3)
            containsExactlyElementsIn(
                listOf(TestIntEnum.FIRST, TestIntEnum.SECOND, TestIntEnum.NEGATIVE)
            )
        }
    }

    @Test
    fun getEntries_emptyEnum_returnEmptyList() {
        val customEnum = CustomEnum(EmptyEnum::class, "Empty enum")

        assertThat(customEnum.getEntries()).isEmpty()
    }

    @Test
    fun getDescription_stringDescriptionEnum_returnCorrectName() {
        val customEnum = CustomEnum(TestIntEnum::class, "Test Int enum")
        assertThat(customEnum.getDescription(context)).isEqualTo("Test Int enum")
    }

    @Test
    fun getDescription_resourceDescriptionEnum_returnCorrectName() {
        val customEnum = CustomEnum(TestIntEnum::class, R.string.test_int_enum_description)
        assertThat(customEnum.getDescription(context)).isEqualTo("Test Int enum")
    }

    companion object {
        const val ENUM_PURPOSE_MESSAGE = "Enum purpose message"
        const val ENUM_PURPOSE_MESSAGE2 = "Enum purpose message 2"
    }
}
