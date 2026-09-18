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
class SynchronouslyObservableStateMapTest : SysuiTestCase() {

    private val alphabeticalSelector: (Map<String, String>) -> String? = { map ->
        // alphabetically first by key
        map.entries.minByOrNull { it.key }?.value
    }

    private val underTest = SynchronouslyObservableStateMap(alphabeticalSelector)

    @Test
    fun valueField_withSingleKey() {
        assertThat(underTest.value).isNull()
        underTest["a"] = "A"
        assertThat(underTest.value).isEqualTo("A")
        underTest["a"] = "AA"
        assertThat(underTest.value).isEqualTo("AA")
        underTest.remove("a")
        assertThat(underTest.value).isNull()
    }

    @Test
    fun valueField_withMultipleKeys() {
        assertThat(underTest.value).isNull()
        underTest["a"] = "A"
        assertThat(underTest.value).isEqualTo("A")
        underTest["b"] = "B"
        assertThat(underTest.value).isEqualTo("A")
        underTest.remove("a")
        assertThat(underTest.value).isEqualTo("B")
        underTest.remove("b")
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
    fun observe_overwritingKey_updatesDerivedValue() {
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        underTest["a"] = "A"
        underTest["a"] = "new A"
        assertThat(observedResults).containsExactly("A", "new A")
    }

    @Test
    fun observe_addingIrrelevantKey_doesNotChangeDerivedValue() {
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        underTest["a"] = "A"
        underTest["b"] = "B"
        assertThat(observedResults).containsExactly("A")
    }

    @Test
    fun observe_removingIrrelevantKey_doesNotChangeDerivedValue() {
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        underTest["a"] = "A"
        underTest["b"] = "B"
        underTest.remove("b")
        assertThat(observedResults).containsExactly("A")
    }

    @Test
    fun observe_remove_nonExistentKey_doesNotExplode() {
        val observedResults = mutableListOf<String?>()
        underTest.observe(sendCurrentValue = false) { value -> observedResults.add(value) }
        underTest["a"] = "A"
        underTest.remove("z") // Remove a key that was never added.
        assertThat(observedResults).containsExactly("A")
    }

    @Test
    fun observe_doesNotReceiveDuplicateValues() {
        val observedResults = mutableListOf<String?>()
        underTest.observe { value -> observedResults.add(value) }
        underTest["a"] = "A"
        underTest["a"] = "A"
        underTest["b"] = "BB"
        underTest["b"] = "B"
        underTest.remove("a")
        underTest.remove("b")
        assertThat(observedResults).containsExactly(null, "A", "B", null)
    }

    @Test
    fun observe_canSeeCurrentValueInState() {
        underTest.observe { value -> assertThat(underTest.value).isEqualTo(value) }
        underTest["a"] = "A"
        underTest["a"] = "A"
        underTest["b"] = "BB"
        underTest["b"] = "B"
        underTest.remove("a")
        underTest.remove("b")
    }

    @Test
    fun observe_stopsReceivingValuesWhenDisposed() {
        val observedResults = mutableListOf<String?>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        underTest["a"] = "A"
        disposableHandle.dispose()
        underTest["a"] = "AA"
        assertThat(observedResults).containsExactly(null, "A")
    }

    @Test
    fun observe_canBeDisposedMultipleTimes() {
        val observedResults = mutableListOf<String?>()
        val disposableHandle = underTest.observe { value -> observedResults.add(value) }
        underTest["a"] = "A"
        disposableHandle.dispose()
        disposableHandle.dispose()
        underTest["a"] = "AA"
        assertThat(observedResults).containsExactly(null, "A")
    }

    @Test
    fun observe_disposeIsPerObserver() {
        val observedResults1 = mutableListOf<String?>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        val observedResults2 = mutableListOf<String?>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        underTest["a"] = "A"
        disposableHandle1.dispose()
        underTest["a"] = "AA"
        disposableHandle2.dispose()
        underTest["a"] = "AAA"
        assertThat(observedResults1).containsExactly(null, "A")
        assertThat(observedResults2).containsExactly(null, "A", "AA")
    }

    @Test
    fun observe_picksUpAtCurrentState() {
        val observedResults1 = mutableListOf<String?>()
        val disposableHandle1 = underTest.observe { value -> observedResults1.add(value) }
        underTest["a"] = "A"
        disposableHandle1.dispose()
        underTest["a"] = "AA"
        val observedResults2 = mutableListOf<String?>()
        val disposableHandle2 = underTest.observe { value -> observedResults2.add(value) }
        underTest["a"] = "AAA"
        disposableHandle2.dispose()
        assertThat(observedResults1).containsExactly(null, "A")
        assertThat(observedResults2).containsExactly("AA", "AAA")
    }

    @Test
    fun selector_canDependOnMapValues() {
        // Create a selector where the result depends on the map values, not keys.
        // Logic: Return the longest string value.
        val valueLengthSelector: (Map<String, String>) -> String? = { map ->
            map.values.maxByOrNull { it.length }
        }
        val valueDependentMap = SynchronouslyObservableStateMap(valueLengthSelector)

        valueDependentMap["k1"] = "short"
        assertThat(valueDependentMap.value).isEqualTo("short")

        valueDependentMap["k2"] = "longer"
        assertThat(valueDependentMap.value).isEqualTo("longer")

        // Overwrite k1 to be the longest now.
        valueDependentMap["k1"] = "longest_value"
        assertThat(valueDependentMap.value).isEqualTo("longest_value")
    }

    @Test
    fun selector_canTransformValues() {
        // Create a selector where the stored value is transformed to a different type.
        val mapSizeSelector: (Map<String, String>) -> Int = { map -> map.size }
        val sizeTrackingMap = SynchronouslyObservableStateMap(mapSizeSelector)

        // Initial state.
        assertThat(sizeTrackingMap.value).isEqualTo(0)

        // Add items.
        sizeTrackingMap["a"] = "A"
        assertThat(sizeTrackingMap.value).isEqualTo(1)

        sizeTrackingMap["b"] = "B"
        assertThat(sizeTrackingMap.value).isEqualTo(2)

        // Overwrite existing item (size should not change).
        sizeTrackingMap["a"] = "New A"
        assertThat(sizeTrackingMap.value).isEqualTo(2)

        // Remove item.
        sizeTrackingMap.remove("a")
        assertThat(sizeTrackingMap.value).isEqualTo(1)
    }
}
