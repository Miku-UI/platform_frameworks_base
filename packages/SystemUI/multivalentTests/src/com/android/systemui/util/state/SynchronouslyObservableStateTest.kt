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
open class SynchronouslyObservableStateTest : SysuiTestCase() {

    private val underTest = SynchronouslyObservableState<String?>(null)

    @Test
    fun valueField() {
        assertThat(underTest.value).isNull()
        underTest.value = "val1"
        assertThat(underTest.value).isEqualTo("val1")
        underTest.value = "val2"
        assertThat(underTest.value).isEqualTo("val2")
        underTest.value = null
        assertThat(underTest.value).isNull()
    }

    @Test
    fun observe_isCalledWithInitialValue() {
        val observedResults = mutableListOf<String?>()
        underTest.observe { value -> observedResults.add(value) }
        assertThat(observedResults).containsExactly(null)
    }

    @Test
    fun observe_isNotCalledWithInitialValue() {
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        assertThat(observedResults).isEmpty()
    }

    @Test
    fun observe_doesNotReceiveDuplicateValues() {
        val observedResults = mutableListOf<String?>()
        underTest.observe { value -> observedResults.add(value) }
        underTest.value = "val1"
        underTest.value = "val2"
        underTest.value = "val2"
        underTest.value = "val3"
        underTest.value = "val3"
        underTest.value = null
        underTest.value = null
        assertThat(observedResults).containsExactly(null, "val1", "val2", "val3", null)
    }

    @Test
    fun observe_canSeeCurrentValueInState() {
        underTest.observe { value -> assertThat(underTest.value).isEqualTo(value) }
        underTest.value = "val1"
        underTest.value = "val2"
        underTest.value = "val2"
        underTest.value = "val3"
        underTest.value = "val3"
        underTest.value = null
        underTest.value = null
    }

    @Test
    fun observe_stopsReceivingValuesWhenDisposed() {
        val observedResults = mutableListOf<String?>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        underTest.value = "val1"
        disposableHandle.dispose()
        underTest.value = "val2"
        assertThat(observedResults).containsExactly(null, "val1")
    }

    @Test
    fun observe_canBeDisposedMultipleTimes() {
        val observedResults = mutableListOf<String?>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        underTest.value = "val1"
        disposableHandle.dispose()
        disposableHandle.dispose()
        underTest.value = "val2"
        assertThat(observedResults).containsExactly(null, "val1")
    }

    @Test
    fun observe_disposeIsPerObserver() {
        val observedResults1 = mutableListOf<String?>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        val observedResults2 = mutableListOf<String?>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        underTest.value = "val1"
        disposableHandle1.dispose()
        underTest.value = "val2"
        disposableHandle2.dispose()
        underTest.value = "val3"
        assertThat(observedResults1).containsExactly(null, "val1")
        assertThat(observedResults2).containsExactly(null, "val1", "val2")
    }

    @Test
    fun observe_picksUpAtCurrentState() {
        val observedResults1 = mutableListOf<String?>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        underTest.value = "val1"
        disposableHandle1.dispose()
        underTest.value = "val2"
        val observedResults2 = mutableListOf<String?>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        underTest.value = "val3"
        disposableHandle2.dispose()
        assertThat(observedResults1).containsExactly(null, "val1")
        assertThat(observedResults2).containsExactly("val2", "val3")
    }
}
