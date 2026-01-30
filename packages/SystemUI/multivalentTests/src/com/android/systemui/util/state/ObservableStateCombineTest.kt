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
open class ObservableStateCombineTest : SysuiTestCase() {

    private val base = SynchronouslyObservableState(1)
    private val mult = SynchronouslyObservableState(5)
    private val underTest = combine(base, mult) { base, mult -> (base % 100) * mult }

    @Test
    fun valueField() {
        assertThat(underTest.value).isEqualTo(5)
        base.value = 2
        assertThat(underTest.value).isEqualTo(10)
        mult.value = 10
        assertThat(underTest.value).isEqualTo(20)
    }

    @Test
    fun observe_isCalledWithInitialValue() {
        val observedResults = mutableListOf<Int>()
        underTest.observe { value -> observedResults.add(value) }
        assertThat(observedResults).containsExactly(5)
    }

    @Test
    fun observe_isNotCalledWithInitialValue() {
        val observedResults = mutableListOf<Int>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        assertThat(observedResults).isEmpty()
    }

    @Test
    fun observe_doesNotReceiveDuplicateValues() {
        val observedResults = mutableListOf<Int>()
        underTest.observe { value -> observedResults.add(value) }
        base.value = 101
        base.value = 2
        base.value = 102
        base.value = 3
        base.value = 103
        mult.value = 1
        base.value = 5
        base.value = 105
        assertThat(observedResults).containsExactly(5, 10, 15, 3, 5)
    }

    @Test
    fun observe_canSeeCurrentValueInState() {
        underTest.observe { value -> assertThat(underTest.value).isEqualTo(value) }
        base.value = 101
        base.value = 2
        base.value = 102
        base.value = 3
        base.value = 103
        mult.value = 1
        base.value = 5
        base.value = 105
    }

    @Test
    fun observe_stopsReceivingValuesWhenDisposed() {
        val observedResults = mutableListOf<Int>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        base.value = 2
        mult.value = 10
        disposableHandle.dispose()
        base.value = 5
        mult.value = 50
        assertThat(observedResults).containsExactly(5, 10, 20)
    }

    @Test
    fun observe_canBeDisposedMultipleTimes() {
        val observedResults = mutableListOf<Int>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        base.value = 2
        mult.value = 10
        disposableHandle.dispose()
        disposableHandle.dispose()
        base.value = 5
        mult.value = 50
        assertThat(observedResults).containsExactly(5, 10, 20)
    }

    @Test
    fun observe_disposeIsPerObserver() {
        val observedResults1 = mutableListOf<Int>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        val observedResults2 = mutableListOf<Int>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        base.value = 2
        mult.value = 2
        disposableHandle1.dispose()
        base.value = 3
        mult.value = 4
        disposableHandle2.dispose()
        base.value = 5
        mult.value = 10
        assertThat(observedResults1).containsExactly(5, 10, 4)
        assertThat(observedResults2).containsExactly(5, 10, 4, 6, 12)
    }

    @Test
    fun observe_picksUpAtCurrentState() {
        val observedResults1 = mutableListOf<Int>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        base.value = 2
        mult.value = 2
        disposableHandle1.dispose()
        base.value = 3
        mult.value = 4
        val observedResults2 = mutableListOf<Int>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        base.value = 5
        mult.value = 10
        disposableHandle2.dispose()
        assertThat(observedResults1).containsExactly(5, 10, 4)
        assertThat(observedResults2).containsExactly(12, 20, 50)
    }
}
