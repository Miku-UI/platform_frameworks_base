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

package com.android.wm.shell.repository

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.FakeLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Tests for [GenericRepositoryUtils].
 *
 * Build/Install/Run: atest WMShellUnitTests:GenericRepositoryUtilsTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class GenericRepositoryUtilsTest : ShellTestCase() {

    private lateinit var repository: MemoryRepositoryImpl<Int, String>
    private lateinit var fakeLogger: FakeLogger

    @Before
    fun setUp() {
        fakeLogger = FakeLogger()
        repository = MemoryRepositoryImpl(fakeLogger.logger)
        addFakeValues()
    }

    @Test
    fun `should iterate over all items when no predicate is provided`() {
        val collectedItems = mutableListOf<String>()

        repository.iterate { item -> collectedItems.add(item) }

        assertEquals(5, collectedItems.size)
        assertTrue(collectedItems.containsAll(listOf("One", "Two", "Three", "Four", "Five")))
    }

    @Test
    fun `should iterate over items matching a given predicate on item`() {
        val collectedItems = mutableListOf<String>()
        // Predicate: item.value2 starts with "B" or "D"
        val predicate: (Int, String) -> Boolean = { _, item -> item.length == 3 }

        repository.iterate(predicate) { item -> collectedItems.add(item) }

        assertEquals(2, collectedItems.size)
        assertTrue(collectedItems.containsAll(listOf("One", "Two")))
    }

    @Test
    fun `should iterate over items matching a given predicate on key value`() {
        val collectedItems = mutableListOf<String>()
        // Predicate: key.id1 is even
        val predicate: (Int, String) -> Boolean = { key, _ -> key % 2 == 0 }

        repository.iterate(predicate) { item -> collectedItems.add(item) }

        assertEquals(2, collectedItems.size)
        assertTrue(collectedItems.containsAll(listOf("Two", "Four")))
    }

    @Test
    fun `should iterate over items matching a given predicate by both key and item`() {
        val collectedItems = mutableListOf<String>()
        val predicate: (Int, String) -> Boolean = { key, item -> key >= 3 && item.contains("e") }

        repository.iterate(predicate) { item -> collectedItems.add(item) }

        assertEquals(2, collectedItems.size)
        assertTrue(collectedItems.containsAll(listOf("Three", "Five")))
    }

    @Test
    fun `should not iterate if predicate matches no FakeItems`() {
        val collectedItems = mutableListOf<String>()
        val predicate: (Int, String) -> Boolean = { _, item -> item.startsWith("Z") }

        repository.iterate(predicate) { item -> collectedItems.add(item) }

        assertTrue(collectedItems.isEmpty())
    }

    @Test
    fun `should handle an empty repository gracefully with FakeItems`() {
        val emptyRepository = MemoryRepositoryImpl<Int, String>()
        val collectedItems = mutableListOf<String>()

        emptyRepository.iterate { item -> collectedItems.add(item) }

        assertTrue(collectedItems.isEmpty())
    }

    @Test
    fun `should execute the fn for each matching FakeItem exactly once`() {
        val callCount = mutableMapOf<String, Int>()

        repository.iterate { item -> callCount[item] = (callCount[item] ?: 0) + 1 }

        assertEquals(5, callCount.size)
        callCount.values.forEach { count ->
            assertEquals(1, count, "Each item should be processed exactly once")
        }
    }

    private fun addFakeValues() {
        repository.insert(1, "One")
        repository.insert(2, "Two")
        repository.insert(3, "Three")
        repository.insert(4, "Four")
        repository.insert(5, "Five")
    }
}
