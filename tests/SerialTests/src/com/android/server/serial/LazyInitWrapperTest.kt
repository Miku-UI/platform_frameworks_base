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
 *
 */

package com.android.server.serial

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyInitWrapperTest {
    @Test
    fun testNormalGet() {
        var calledInitializer = false
        val value = LazyInitWrapper.create {
            assertFalse(calledInitializer)
            calledInitializer = true

            TEST_OBJECT
        }
        assertSame(value.get(), TEST_OBJECT)
        assertTrue(calledInitializer)

        // Assert twice to ensure following get() returns the expected value
        assertSame(value.get(), TEST_OBJECT)
    }

    @Test
    fun testNullGet() {
        var calledInitializer = false
        val value = LazyInitWrapper.create {
            val firstCall = !calledInitializer
            calledInitializer = true

            if (firstCall) null else TEST_OBJECT
        }
        assertNull(value.get())
        assertSame(value.get(), TEST_OBJECT)

        // Assert twice to ensure following get() returns the expected value
        assertSame(value.get(), TEST_OBJECT)
    }

    companion object {
        val TEST_OBJECT = Object()
    }
}