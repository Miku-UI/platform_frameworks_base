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

package com.android.systemui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DejankUtilsTest : SysuiTestCase() {

    @Test
    fun testClassLoading_noLooper_doesNotCrash() {
        val crashed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val t = Thread {
            try {
                val strict = DejankUtils.STRICT_MODE_ENABLED
            } catch (e: Exception) {
                crashed.set(true)
            } finally {
                latch.countDown()
            }
        }
        t.start()
        latch.await(5, TimeUnit.SECONDS)

        assertFalse("DejankUtils crashed on thread without Looper", crashed.get())
    }

    @Test
    fun testPostAfterTraversal_runsRunnable() {
        val latch = CountDownLatch(1)
        val r = Runnable { latch.countDown() }

        mContext.mainExecutor.execute { DejankUtils.postAfterTraversal(r) }

        // nothing crashed!
    }

    @Test
    fun testRemoveCallbacks_removesRunnable() {
        val latch = CountDownLatch(1)
        val r = Runnable { latch.countDown() }

        mContext.mainExecutor.execute {
            DejankUtils.postAfterTraversal(r)
            DejankUtils.removeCallbacks(r)
        }

        val afterLatch = CountDownLatch(1)
        mContext.mainExecutor.execute { DejankUtils.postAfterTraversal { afterLatch.countDown() } }

        // Wait for the second runnable to run, which implies the first one would have run if it
        // wasn't removed
        afterLatch.await(5, TimeUnit.SECONDS)

        // We expect the count to remain 1, meaning the runnable was NOT run.
        // If it ran, count would be 0.
        assertFalse("DejankUtils.removeCallbacks failed to remove runnable", latch.count == 0L)
    }
}
