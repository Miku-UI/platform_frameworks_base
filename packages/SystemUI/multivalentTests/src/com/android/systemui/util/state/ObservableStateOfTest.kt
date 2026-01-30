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

package com.android.systemui.util.state

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
open class ObservableStateOfTest : SysuiTestCase() {

    @Test
    fun valueField_withObject() {
        val underTest = observableStateOf("value")
        assertThat(underTest.value).isEqualTo("value")
    }

    @Test
    fun valueField_withNull() {
        val underTest = observableStateOf<Int?>(null)
        assertThat(underTest.value).isNull()
    }

    @Test
    fun observe_isCalledWithValue() {
        val underTest = observableStateOf("value")
        val observedResults = mutableListOf<String?>()
        underTest.observe { value -> observedResults.add(value) }
        assertThat(observedResults).containsExactly("value")
    }

    @Test
    fun observe_isNotCalledWithValue() {
        val underTest = observableStateOf("value")
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        assertThat(observedResults).isEmpty()
    }

    @Test
    fun observe_canBeDisposedMultipleTimes() {
        val underTest = observableStateOf("value")
        val observedResults = mutableListOf<String?>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        disposableHandle.dispose()
        disposableHandle.dispose()
        assertThat(observedResults).containsExactly("value")
    }
}
