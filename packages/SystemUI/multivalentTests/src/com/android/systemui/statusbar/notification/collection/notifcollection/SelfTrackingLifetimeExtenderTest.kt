/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.collection.notifcollection

import android.os.Handler
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender.OnEndLifetimeExtensionCallback
import com.android.systemui.statusbar.notification.collection.notifcollection.SelfTrackingLifetimeExtender.FinishReason
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer
import java.util.function.Predicate

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class SelfTrackingLifetimeExtenderTest : SysuiTestCase() {
    private lateinit var extender: TestableSelfTrackingLifetimeExtender

    private lateinit var entry1: NotificationEntry
    private lateinit var entry2: NotificationEntry

    private val callback: OnEndLifetimeExtensionCallback = mock()
    private val mainHandler: Handler = mock()

    private val shouldExtend = mutableMapOf<NotificationEntry, Boolean>()
    private val onStarted = mutableListOf<NotificationEntry>()
    private val onContinued = mutableListOf<NotificationEntry>()
    private val onFinished = mutableListOf<Pair<NotificationEntry, FinishReason>>()

    @Before
    fun setUp() {
        extender = TestableSelfTrackingLifetimeExtender()
        extender.setCallback(callback)
        entry1 = NotificationEntryBuilder().setId(1).build()
        entry2 = NotificationEntryBuilder().setId(2).build()
    }

    @Test
    fun testName() {
        assertThat(extender.name).isEqualTo("Testable")
    }

    @Test
    fun testNoExtend() {
        // WHEN not extending
        shouldExtend[entry1] = false
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isFalse()

        // VERIFY not extending
        assertThat(extender.isExtending(entry1.key)).isFalse()
        assertThat(onStarted).isEmpty()
        assertThat(onFinished).isEmpty()
    }

    @Test
    fun testExtendThenCancelForRepost() {
        // WHEN extending
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()

        // VERIFY started but not finisned; is extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).isEmpty()
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // WHEN canceling
        extender.cancelLifetimeExtension(entry1)

        // VERIFY now finished; not extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.Canceled)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    @Test
    fun testExtendThenNotRenewed() {
        // WHEN extending
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()

        // VERIFY started but not finisned; is extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).isEmpty()
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // WHEN not renewing
        shouldExtend[entry1] = false
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isFalse()

        // VERIFY both started and finished; not extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.NotRenewed)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    @Test
    fun testExtendThenCancel_thenEndDoesNothing() {
        testExtendThenCancelForRepost()
        assertThat(extender.isExtending(entry1.key)).isFalse()

        extender.endLifetimeExtension(entry1.key)
        extender.endLifetimeExtensionAfterDelay(entry1.key, 1000)
        verify(callback, never()).onEndLifetimeExtension(any(), any())
        verify(mainHandler, never()).postDelayed(any(), any())
    }

    @Test
    fun testExtendThenEnd() {
        // WHEN extending
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()

        // VERIFY started but not finisned; is extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onContinued).isEmpty()
        assertThat(onFinished).isEmpty()
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // WHEN ending
        extender.endLifetimeExtension(entry1.key)

        // VERIFY callback notified; finished; not extending
        verify(callback).onEndLifetimeExtension(extender, entry1)
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onContinued).isEmpty()
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    @Test
    fun testExtendThenContinueTwiceThenEnd() {
        // WHEN extending
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()

        // VERIFY started but not finisned; is extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onContinued).isEmpty()
        assertThat(onFinished).isEmpty()
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // WHEN continuing
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()

        // VERIFY continued; not finished; is extending
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onContinued).containsExactly(entry1, entry1)
        assertThat(onFinished).isEmpty()
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // WHEN ending
        extender.endLifetimeExtension(entry1.key)

        // VERIFY callback notified; finished; not extending
        verify(callback).onEndLifetimeExtension(extender, entry1)
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onContinued).containsExactly(entry1, entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    @Test
    fun testExtendThenEndAfterDelay() {
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)
        assertThat(extender.isExtending(entry1.key)).isTrue()

        // Call the method and capture the posted runnable
        extender.endLifetimeExtensionAfterDelay(entry1.key, 1234)
        val runnable = withArgCaptor<Runnable> {
            verify(mainHandler).postDelayed(capture(), eq(1234.toLong()))
        }
        assertThat(extender.isExtending(entry1.key)).isTrue()
        verify(callback, never()).onEndLifetimeExtension(any(), any())

        // now run the posted runnable and ensure it works as expected
        runnable.run()
        verify(callback).onEndLifetimeExtension(extender, entry1)
        assertThat(extender.isExtending(entry1.key)).isFalse()
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
    }

    @Test
    fun testExtendThenEndAll() {
        shouldExtend[entry1] = true
        shouldExtend[entry2] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)
        assertThat(extender.isExtending(entry1.key)).isTrue()
        assertThat(extender.isExtending(entry2.key)).isFalse()
        assertThat(extender.maybeExtendLifetime(entry2, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1, entry2)
        assertThat(extender.isExtending(entry1.key)).isTrue()
        assertThat(extender.isExtending(entry2.key)).isTrue()
        extender.endAllLifetimeExtensions()
        verify(callback).onEndLifetimeExtension(extender, entry1)
        verify(callback).onEndLifetimeExtension(extender, entry2)
        assertThat(onFinished).containsExactly(
            entry1 to FinishReason.EndRequested,
            entry2 to FinishReason.EndRequested
        )
    }

    @Test
    fun testExtendWithinEndCanReExtend() {
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)

        whenever(callback.onEndLifetimeExtension(extender, entry1)).thenAnswer {
            assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        }
        extender.endLifetimeExtension(entry1.key)
        assertThat(onStarted).containsExactly(entry1, entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isTrue()
    }

    @Test
    fun testExtendWithinEndCanNotReExtend() {
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)

        shouldExtend[entry1] = false
        whenever(callback.onEndLifetimeExtension(extender, entry1)).thenAnswer {
            assertThat(extender.maybeExtendLifetime(entry1, 0)).isFalse()
        }
        extender.endLifetimeExtension(entry1.key)
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    @Test
    fun testExtendWithinEndAllCanReExtend() {
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)

        whenever(callback.onEndLifetimeExtension(extender, entry1)).thenAnswer {
            assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        }
        extender.endAllLifetimeExtensions()
        assertThat(onStarted).containsExactly(entry1, entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isTrue()
    }

    @Test
    fun testExtendWithinEndAllCanNotReExtend() {
        shouldExtend[entry1] = true
        assertThat(extender.maybeExtendLifetime(entry1, 0)).isTrue()
        assertThat(onStarted).containsExactly(entry1)

        shouldExtend[entry1] = false
        whenever(callback.onEndLifetimeExtension(extender, entry1)).thenAnswer {
            assertThat(extender.maybeExtendLifetime(entry1, 0)).isFalse()
        }
        extender.endAllLifetimeExtensions()
        assertThat(onStarted).containsExactly(entry1)
        assertThat(onFinished).containsExactly(entry1 to FinishReason.EndRequested)
        assertThat(extender.isExtending(entry1.key)).isFalse()
    }

    inner class TestableSelfTrackingLifetimeExtender(debug: Boolean = false) :
            SelfTrackingLifetimeExtender("Test", "Testable", debug, mainHandler) {

        override fun queryShouldExtendLifetime(entry: NotificationEntry) =
                requireNotNull(shouldExtend[entry])

        override fun onStartedLifetimeExtension(entry: NotificationEntry) {
            onStarted.add(entry)
        }

        override fun onContinuedLifetimeExtension(entry: NotificationEntry) {
            onContinued.add(entry)
        }

        override fun onFinishedLifetimeExtension(entry: NotificationEntry, reason: FinishReason) {
            onFinished.add(entry to reason)
        }
    }
}
