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

package com.android.settingslib.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.utils.runSafely
import com.android.settingslib.utils.runSafelyAsync
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunSafelyTest {

    private val TAG = "TestTag"
    private val NAME = "TestAction"

    @Test
    fun `runSafely with return value returns block result when successful`() {
        val result = runSafely(TAG, NAME, fallback = false) {
            true
        }

        Assert.assertEquals(true, result)
    }

    @Test
    fun `runSafely with return value returns fallback and logs error on exception`() {
        val result = runSafely(TAG, NAME, fallback = "default") {
            throw RuntimeException("Boom!")
        }

        Assert.assertEquals("default", result)
    }

    @Test
    fun `runSafely Unit version executes block when successful`() {
        var called = false

        runSafely(TAG, NAME) {
            called = true
        }

        Assert.assertEquals(true, called)
    }

    @Test
    fun `runSafelyAsync returns value after suspension`() = runTest {
        val result = runSafelyAsync(TAG, NAME, fallback = "error") {
            delay(10)
            "Success"
        }

        Assert.assertEquals("Success", result)
    }

    @Test
    fun `runSafelyAsync returns fallback and logs on exception`() = runTest {
        val exception = RuntimeException("Network Failed")

        val result = runSafelyAsync(TAG, NAME, fallback = "fallback") {
            delay(5)
            throw exception
        }

        Assert.assertEquals("fallback", result)
    }

    @Test
    fun `runSafelyAsync Unit version executes successfully`() = runTest {
        var completed = false

        runSafelyAsync(TAG, NAME) {
            delay(10)
            completed = true
        }

        Assert.assertEquals(true, completed)
    }
}