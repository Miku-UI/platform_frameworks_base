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

package com.android.systemui.lowlight

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.lowlightclock.LowLightLogger
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class LightSensorDebounceAlgorithmTest : SysuiTestCase() {
    companion object {
        private const val LIGHT_MODE_THRESHOLD = 5.0f
        private const val DARK_MODE_THRESHOLD = 2.0f
        private const val LIGHT_MODE_SPAN = 100
        private const val DARK_MODE_SPAN = 50
        private const val LIGHT_MODE_FREQUENCY = 10
        private const val DARK_MODE_FREQUENCY = 5
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.logger by Kosmos.Fixture { LowLightLogger(logcatLogBuffer()) }

    private val Kosmos.underTest: LightSensorEventsDebounceAlgorithm by
        Kosmos.Fixture {
            LightSensorEventsDebounceAlgorithm(
                fakeExecutor,
                dumpManager,
                logger,
                LIGHT_MODE_THRESHOLD,
                DARK_MODE_THRESHOLD,
                LIGHT_MODE_SPAN,
                DARK_MODE_SPAN,
                LIGHT_MODE_FREQUENCY,
                DARK_MODE_FREQUENCY,
            )
        }

    @Test
    fun shouldOnlyTriggerCallbackWhenValueChanges() =
        kosmos.runTest {
            val callback = startAlgorithm()

            // Light mode, should trigger callback.
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            reset(callback)

            // Light mode again, should NOT trigger callback.
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
            verify(callback, never()).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            reset(callback)

            // Dark mode, should trigger callback.
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            reset(callback)

            // Dark mode again, should not trigger callback.
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
            verify(callback, never()).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }

    @Test
    fun shouldReportUndecidedWhenNeitherLightNorDarkClaimIsTrue() =
        kosmos.runTest {
            underTest.isDarkMode = false
            underTest.isLightMode = false

            assertThat(underTest.mode)
                .isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED)
        }

    @Test
    fun shouldReportDarkModeAsLongAsDarkModeClaimIsTrue() =
        kosmos.runTest {
            underTest.isDarkMode = true
            underTest.isLightMode = false

            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)

            underTest.isLightMode = true
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }

    @Test
    fun shouldReportLightModeWhenLightModeClaimIsTrueAndDarkModeClaimIsFalse() =
        kosmos.runTest {
            underTest.isLightMode = true
            underTest.isDarkMode = false

            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
        }

    @Test
    fun shouldSetIsLightModeToTrueWhenBundleAverageIsGreaterThanThreshold() =
        kosmos.runTest {
            // Note: [mockLightModeThreshold] is 5.0.
            underTest.bundleAverageLightMode = 5.1
            assertThat(underTest.isLightMode).isTrue()

            underTest.bundleAverageLightMode = 10.0
            assertThat(underTest.isLightMode).isTrue()

            underTest.bundleAverageLightMode = 20.0
            assertThat(underTest.isLightMode).isTrue()

            underTest.bundleAverageLightMode = 5.0
            assertThat(underTest.isLightMode).isFalse()

            underTest.bundleAverageLightMode = 3.0
            assertThat(underTest.isLightMode).isFalse()

            underTest.bundleAverageLightMode = 0.0
            assertThat(underTest.isLightMode).isFalse()
        }

    @Test
    fun shouldSetIsDarkModeToTrueWhenBundleAverageIsLessThanThreshold() =
        kosmos.runTest {
            // Note: [mockDarkModeThreshold] is 2.0.
            underTest.bundleAverageDarkMode = 1.9
            assertThat(underTest.isDarkMode).isTrue()

            underTest.bundleAverageDarkMode = 1.0
            assertThat(underTest.isDarkMode).isTrue()

            underTest.bundleAverageDarkMode = 0.0
            assertThat(underTest.isDarkMode).isTrue()

            underTest.bundleAverageDarkMode = 2.0
            assertThat(underTest.isDarkMode).isFalse()

            underTest.bundleAverageDarkMode = 3.0
            assertThat(underTest.isDarkMode).isFalse()

            underTest.bundleAverageDarkMode = 10.0
            assertThat(underTest.isDarkMode).isFalse()
        }

    @Test
    fun shouldCorrectlyCalculateAverageFromABundle() =
        kosmos.runTest {
            // For light mode.
            underTest.bundleLightMode = arrayListOf(1.0f, 3.0f, 5.0f, 7.0f)
            assertThat(underTest.bundleAverageLightMode).isEqualTo(4.0)

            underTest.bundleLightMode = arrayListOf(2.0f, 4.0f, 6.0f, 8.0f)
            assertThat(underTest.bundleAverageLightMode).isEqualTo(5.0)

            // For dark mode.
            underTest.bundleDarkMode = arrayListOf(1.0f, 3.0f, 5.0f, 7.0f, 9.0f)
            assertThat(underTest.bundleAverageDarkMode).isEqualTo(5.0)

            underTest.bundleDarkMode = arrayListOf(2.0f, 4.0f, 6.0f, 8.0f, 10.0f)
            assertThat(underTest.bundleAverageDarkMode).isEqualTo(6.0)
        }

    @Test
    fun shouldAddSensorEventUpdatesToBundles() =
        kosmos.runTest {
            startAlgorithm()

            // Add 1 more bundle to queue for each mode.
            underTest.bundlesQueueLightMode.add(ArrayList())
            underTest.bundlesQueueDarkMode.add(ArrayList())

            underTest.onUpdateLightSensorEvent(1.0f)
            underTest.onUpdateLightSensorEvent(1.0f)
            underTest.onUpdateLightSensorEvent(2.0f)
            underTest.onUpdateLightSensorEvent(3.0f)
            underTest.onUpdateLightSensorEvent(4.0f)

            val expectedValues = listOf(1.0f, 1.0f, 2.0f, 3.0f, 4.0f)

            assertBundleContainsAll(underTest.bundlesQueueLightMode[0], expectedValues)
            assertBundleContainsAll(underTest.bundlesQueueLightMode[1], expectedValues)
            assertBundleContainsAll(underTest.bundlesQueueDarkMode[0], expectedValues)
            assertBundleContainsAll(underTest.bundlesQueueDarkMode[1], expectedValues)
        }

    @Test
    fun shouldCorrectlyEnqueueLightModeBundles() =
        kosmos.runTest {
            startAlgorithm()

            // Assert that starting the algorithm enqueues the first light mode bundle.
            assertThat(underTest.bundlesQueueLightMode.size).isEqualTo(1)

            underTest.enqueueLightModeBundle.run()
            assertThat(underTest.bundlesQueueLightMode.size).isEqualTo(2)

            underTest.enqueueLightModeBundle.run()
            assertThat(underTest.bundlesQueueLightMode.size).isEqualTo(3)

            // Verifies dark mode bundles queue is not impacted.
            assertThat(underTest.bundlesQueueDarkMode.size).isEqualTo(1)
        }

    @Test
    fun shouldCorrectlyEnqueueDarkModeBundles() =
        kosmos.runTest {
            startAlgorithm()

            // Assert that starting the algorithm enqueues the first dark mode bundle.
            assertThat(underTest.bundlesQueueDarkMode.size).isEqualTo(1)

            underTest.enqueueDarkModeBundle.run()
            assertThat(underTest.bundlesQueueDarkMode.size).isEqualTo(2)

            underTest.enqueueDarkModeBundle.run()
            assertThat(underTest.bundlesQueueDarkMode.size).isEqualTo(3)

            // Verifies light mode bundles queue is not impacted.
            assertThat(underTest.bundlesQueueLightMode.size).isEqualTo(1)
        }

    @Test
    fun shouldCorrectlyDequeueLightModeBundles() =
        kosmos.runTest {
            startAlgorithm()

            // Assert that starting the algorithm enqueues an empty light mode bundle.
            underTest.dequeueLightModeBundle.run()
            assertThat(underTest.bundleLightMode.size).isEqualTo(0)

            // Sets up the light mode bundles queue.
            val bundle1 = arrayListOf(1.0f, 3.0f, 6.0f, 9.0f)
            val bundle2 = arrayListOf(5.0f, 10f)
            val bundle3 = arrayListOf(2.0f, 4.0f)
            underTest.bundlesQueueLightMode.add(bundle1)
            underTest.bundlesQueueLightMode.add(bundle2)
            underTest.bundlesQueueLightMode.add(bundle3)

            // The committed bundle should be the first one in queue.
            underTest.dequeueLightModeBundle.run()
            assertBundleContainsAll(underTest.bundleLightMode, bundle1)

            underTest.dequeueLightModeBundle.run()
            assertBundleContainsAll(underTest.bundleLightMode, bundle2)

            underTest.dequeueLightModeBundle.run()
            assertBundleContainsAll(underTest.bundleLightMode, bundle3)

            // Verifies that the dark mode bundle is not impacted.
            assertBundleContainsAll(underTest.bundleDarkMode, listOf())
        }

    @Test
    fun shouldCorrectlyDequeueDarkModeBundles() =
        kosmos.runTest {
            startAlgorithm()

            // Assert that starting the algorithm enqueues an empty dark mode bundle.
            underTest.dequeueDarkModeBundle.run()
            assertThat(underTest.bundleDarkMode.size).isEqualTo(0)

            // Sets up the dark mode bundles queue.
            val bundle1 = arrayListOf(2.0f, 4.0f)
            val bundle2 = arrayListOf(5.0f, 10f)
            val bundle3 = arrayListOf(1.0f, 3.0f, 6.0f, 9.0f)
            underTest.bundlesQueueDarkMode.add(bundle1)
            underTest.bundlesQueueDarkMode.add(bundle2)
            underTest.bundlesQueueDarkMode.add(bundle3)

            // The committed bundle should be the first one in queue.
            underTest.dequeueDarkModeBundle.run()
            assertBundleContainsAll(underTest.bundleDarkMode, bundle1)

            underTest.dequeueDarkModeBundle.run()
            assertBundleContainsAll(underTest.bundleDarkMode, bundle2)

            underTest.dequeueDarkModeBundle.run()
            assertBundleContainsAll(underTest.bundleDarkMode, bundle3)

            // Verifies that the light mode bundle is not impacted.
            assertBundleContainsAll(underTest.bundleLightMode, listOf())
        }

    @Test
    fun shouldSetLightSensorLevelFromSensorEventUpdates() =
        kosmos.runTest {
            startAlgorithm()

            underTest.onUpdateLightSensorEvent(1.0f)
            assertThat(underTest.lightSensorLevel).isEqualTo(1.0f)

            underTest.onUpdateLightSensorEvent(10.0f)
            assertThat(underTest.lightSensorLevel).isEqualTo(10.0f)

            underTest.onUpdateLightSensorEvent(0.0f)
            assertThat(underTest.lightSensorLevel).isEqualTo(0.0f)
        }

    @Test
    fun shouldRippleFromSensorEventUpdatesDownToAmbientLightMode() =
        kosmos.runTest {
            val callback = startAlgorithm()

            // Sensor event updates.
            underTest.onUpdateLightSensorEvent(10.0f)
            underTest.onUpdateLightSensorEvent(15.0f)
            underTest.onUpdateLightSensorEvent(12.0f)
            underTest.onUpdateLightSensorEvent(10.0f)

            // Advances time so both light and dark claims have been calculated.
            fakeSystemClock.advanceTime((LIGHT_MODE_SPAN + 1).toLong())

            // Verifies the callback is triggered the ambient mode has changed LIGHT.
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
        }

    @Test
    fun shouldRippleFromSensorEventUpdatesDownToAmbientDarkMode() =
        kosmos.runTest {
            val callback = startAlgorithm()

            // Sensor event updates.
            underTest.onUpdateLightSensorEvent(1.0f)
            underTest.onUpdateLightSensorEvent(0.5f)
            underTest.onUpdateLightSensorEvent(1.2f)
            underTest.onUpdateLightSensorEvent(0.8f)

            // Advances time so both light and dark claims have been calculated.
            fakeSystemClock.advanceTime((LIGHT_MODE_SPAN + 1).toLong())

            // Verifies the callback is triggered the ambient mode has changed DARK.
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }

    @Test
    fun shouldImmediatelyComputeResultWhenStarted() =
        kosmos.runTest {
            val callback = startAlgorithm()

            // Mock first sensor event, which is between thresholds 2 and 5.
            underTest.onUpdateLightSensorEvent(3.0f)

            // Verify callback not triggered, because mode is undetermined.
            verify(callback, never()).onChange(any())

            // Mock second sensor event, which is below dark threshold 2.
            underTest.onUpdateLightSensorEvent(1.0f)

            // Verify mode immediately changed to dark.
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }

    @Test
    fun shouldImmediatelyComputeResultAfterRestart() =
        kosmos.runTest {
            val callback = startAlgorithm()

            // Mock sensor event, which is above light threshold 5.
            underTest.onUpdateLightSensorEvent(10.0f)

            // Verify mode immediately changed to light.
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)

            // Restart underTest.
            underTest.stop()
            clearInvocations(callback)
            underTest.start(callback)

            // Mock sensor event, which is below dark threshold 2.
            underTest.onUpdateLightSensorEvent(1.0f)

            // Verify mode immediately changed to dark.
            verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }

    @Test
    fun shouldResetAlgorithmDataAfterStop() =
        kosmos.runTest {
            startAlgorithm()

            // Mock some algorithm data.
            underTest.lightSensorLevel = 2.0f
            underTest.bundleLightMode.add(2.0f)
            underTest.bundleDarkMode.add(2.0f)
            underTest.bundleAverageLightMode = 2.0
            underTest.bundleAverageDarkMode = 2.0
            underTest.isLightMode = true
            underTest.isDarkMode = true
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK

            // Stop underTest.
            underTest.stop()

            // Verifies algorithm data cleared immediately.
            assertThatAlgorithmDataIsClear()

            // Advance enough time that if the algorithm wasn't stopped correctly more data would
            // have built up.
            fakeSystemClock.advanceTime((LIGHT_MODE_SPAN + 1).toLong())

            // Verifies algorithm data stays clear.
            assertThatAlgorithmDataIsClear()
        }

    // Asserts that [bundle] contains the same elements as [expected], not necessarily in the same
    // order.
    private fun assertBundleContainsAll(bundle: ArrayList<Float>, expected: Collection<Float>) {
        assertThat(bundle.size).isEqualTo(expected.size)
        assertThat(bundle.containsAll(expected)).isTrue()
    }

    // Asserts algorithm data is at initial state.
    private fun Kosmos.assertThatAlgorithmDataIsClear() {
        assertThat(underTest.lightSensorLevel).isZero()
        assertThat(underTest.bundleLightMode).isEmpty()
        assertThat(underTest.bundleDarkMode).isEmpty()
        assertThat(underTest.bundleAverageLightMode).isZero()
        assertThat(underTest.bundleAverageDarkMode).isZero()
        assertThat(underTest.isLightMode).isFalse()
        assertThat(underTest.isDarkMode).isFalse()
        assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED)
    }

    private fun Kosmos.startAlgorithm(): AmbientLightModeMonitor.Callback {
        val callback = mock<AmbientLightModeMonitor.Callback>()
        underTest.start(callback)
        fakeExecutor.runAllReady()

        return callback
    }

    @Test
    fun shouldSafelyIgnoreEmptyQueueOnDequeue() =
        kosmos.runTest {
            startAlgorithm()

            // Explicitly clear the queues to simulate concurrent stop.
            underTest.bundlesQueueLightMode.clear()
            underTest.bundlesQueueDarkMode.clear()

            // Run the dequeue runnables. They should not crash.
            underTest.dequeueLightModeBundle.run()
            underTest.dequeueDarkModeBundle.run()

            // The bundles shouldn't be affected since the queue was empty.
            assertThat(underTest.bundleLightMode).isEmpty()
            assertThat(underTest.bundleDarkMode).isEmpty()
        }

    @Test
    fun updateMode_withHysteresis_switchesCorrectly() =
        kosmos.runTest {
            // Start in undecided
            underTest.mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED
            underTest.isLightMode = false
            underTest.isDarkMode = false

            // Undecided -> Dark
            underTest.isDarkMode = true
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)

            // Dark -> Light (should not switch if isDarkMode is still true)
            underTest.isLightMode = true
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)

            // Dark -> Light (should switch)
            underTest.isDarkMode = false
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)

            // Light -> Dark (should not switch if isLightMode is still true)
            underTest.isDarkMode = true
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)

            // Light -> Dark (should switch)
            underTest.isLightMode = false
            assertThat(underTest.mode).isEqualTo(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        }
}
