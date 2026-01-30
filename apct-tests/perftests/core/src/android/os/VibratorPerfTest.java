/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.os;

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.content.Context;
import android.perftests.utils.ManualBenchmarkState;
import android.perftests.utils.PerfManualStatusReporter;
import android.perftests.utils.TraceMarkParser;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@LargeTest
public class VibratorPerfTest {
    private static final String TAG = "VibratorPerfTest";
    private static final long NANOS_PER_MS = 1000L * 1000;
    private static final long NANOS_PER_S = 1000 * NANOS_PER_MS;

    /**
     * Time to wait for the vibrator service to settle after vibration is started/ended/cancelled.
     *
     * <p>This should be used between iterations to make sure the vibrate method being tested is
     * hitting the vibrator while it's idle.
     */
    private static final long SERVICE_DELAY_MS = 100;

    private static final int ATRACE_BUFFER_SIZE = 1024;
    private static final String ATRACE_TAG = "vibrator";
    private static final String ATRACE_START =
            String.format("atrace --async_start -b %d -c %s", ATRACE_BUFFER_SIZE, ATRACE_TAG);
    private static final String ATRACE_STOP = "atrace --async_stop";
    private static final String ATRACE_DUMP = "atrace --async_dump";

    // Traces that includes the vibration duration and should be used to generate latency metrics.
    private static final Set<String> LATENCY_TRACES = Set.of("vibration", "HalVibrator.vibration");
    private static final String[] VIBRATION_TRACES = new String[]{
            // VibratorManagerService async trace for entire vibration
            "vibration",
            // HalVibrator async trace between on/off commands
            "HalVibrator.vibration",
            // HalVibrator methods
            "HalVibrator.onMillis",
            "HalVibrator.onPrebaked",
            "HalVibrator.onPrimitives",
            "HalVibrator.onPwleV2",
            "HalVibrator.setAmplitude",
            "HalVibrator.off",
            // VibratorManagerService methods
            "vibrate",
            "cancelVibrate",
            "startVibrationLocked",
            "runVibrationOnVibrationThread",
    };

    private static final String LATENCY_METRIC_KEY_SUFFIX = "Latency";
    private static final String VIBRATOR_STATE_START_LATENCY_METRIC_KEY =
            "OnVibratorStateChangedListener.start" + LATENCY_METRIC_KEY_SUFFIX;
    private static final String VIBRATOR_STATE_STOP_LATENCY_METRIC_KEY =
            "OnVibratorStateChangedListener.stop" + LATENCY_METRIC_KEY_SUFFIX;

    @Rule
    public final PerfManualStatusReporter mStatusReporter = new PerfManualStatusReporter();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            getInstrumentation().getUiAutomation(), Manifest.permission.ACCESS_VIBRATOR_STATE);

    private TraceMarkParser mTraceMethods;

    private Vibrator mVibrator;
    private VibratorStateListener mStateListener;

    @Before
    public void setUp() {
        Context context = getInstrumentation().getTargetContext();
        mVibrator = context.getSystemService(Vibrator.class);
        mStateListener = new VibratorStateListener();
        mVibrator.cancel();
        mVibrator.addVibratorStateListener(mStateListener);
    }

    @After
    public void cleanUp() {
        mVibrator.removeVibratorStateListener(mStateListener);
    }

    @Test
    public void testEffectClickSuperseded() {
        VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);

        long elapsedTimeNs = 0;
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        while (state.keepRunning(elapsedTimeNs)) {
            // Measure vibrate call right after initial call that will be superseded.
            mVibrator.vibrate(effect);
            elapsedTimeNs = measureVibrate(effect);
        }
    }

    @Test
    public void testEffectClick() {
        // Unknown predefined click estimated duration, cannot add vibration latency metrics.
        benchmarkVibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
    }

    @Test
    public void testEffectClickTraces() throws InterruptedException {
        // Enable traces in separate test case, as they might affect performance.
        assumeTrue("Device without predefined click support",
                mVibrator.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK)
                        == Vibrator.VIBRATION_EFFECT_SUPPORT_YES);

        // Unknown predefined click estimated duration, cannot add vibration latency metrics.
        VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
        benchmarkVibrateWithTraces(effect, /* durationMs= */ -1);
    }

    @Test
    public void testPrimitiveClickTraces() throws InterruptedException {
        // Enable traces in separate test case, as they might affect performance.
        assumeTrue("Device without primitive click support",
                mVibrator.areAllPrimitivesSupported(PRIMITIVE_CLICK));

        long durationMs = mVibrator.getPrimitiveDurations(PRIMITIVE_CLICK)[0];
        VibrationEffect effect =
                VibrationEffect.startComposition().addPrimitive(PRIMITIVE_CLICK).compose();
        benchmarkVibrateWithTraces(effect, durationMs);
    }

    @Test
    public void testOneShot() throws InterruptedException {
        long durationMs = 100;
        VibrationEffect effect =
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE);
        benchmarkVibrate(effect, durationMs);
    }

    @Test
    public void testOneShotTraces() throws InterruptedException {
        // Enable traces in separate test case, as they might affect performance.
        long durationMs = 100;
        VibrationEffect effect =
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE);
        benchmarkVibrateWithTraces(effect, durationMs);
    }

    @Test
    public void testWaveform() {
        // Vibrator turns on/off multiple times, cannot add vibration latency metrics.
        long[] timings = new long[]{SECONDS.toMillis(1), SECONDS.toMillis(2), SECONDS.toMillis(1)};
        benchmarkVibrate(VibrationEffect.createWaveform(timings, -1));
    }

    @Test
    public void testComposePrimitives() throws InterruptedException {
        int[] primitiveDurations = mVibrator.getPrimitiveDurations(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        long durationMs = primitiveDurations[0] + 100 + primitiveDurations[1];
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
                .compose();
        benchmarkVibrate(effect, durationMs,
                vib -> vib.areAllPrimitivesSupported(PRIMITIVE_CLICK, PRIMITIVE_TICK));
    }

    @Test
    public void testComposePrimitivesTraces() throws InterruptedException {
        // Enable traces in separate test case, as they might affect performance.
        assumeTrue("Device without primitives support",
                mVibrator.areAllPrimitivesSupported(PRIMITIVE_CLICK, PRIMITIVE_TICK));

        int[] primitiveDurations = mVibrator.getPrimitiveDurations(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        long durationMs = primitiveDurations[0] + 100 + primitiveDurations[1];
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
                .compose();
        benchmarkVibrateWithTraces(effect, durationMs);
    }

    @Test
    public void testEnvelopeEffect() throws InterruptedException {
        long durationMs = 100;
        VibrationEffect effect = new VibrationEffect.BasicEnvelopeBuilder()
                .addControlPoint(1, 1, 50)
                .addControlPoint(0, 0, 50)
                .build();
        benchmarkVibrate(effect, durationMs, Vibrator::areEnvelopeEffectsSupported);
    }

    @Test
    public void testEnvelopeEffectTraces() throws InterruptedException {
        // Enable traces in separate test case, as they might affect performance.
        assumeTrue("Device without envelope effect support",
                mVibrator.areEnvelopeEffectsSupported());

        long durationMs = 100;
        VibrationEffect effect = new VibrationEffect.BasicEnvelopeBuilder()
                .addControlPoint(1, 1, 50)
                .addControlPoint(0, 0, 50)
                .build();
        benchmarkVibrateWithTraces(effect, durationMs);
    }

    @Test
    public void testAreEffectsSupported() {
        int[] effects = new int[]{VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_TICK};

        long elapsedTimeNs = 0;
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        while (state.keepRunning(elapsedTimeNs)) {
            long startTimeNs = System.nanoTime();
            mVibrator.areEffectsSupported(effects);
            elapsedTimeNs = System.nanoTime() - startTimeNs;
        }
    }

    @Test
    public void testArePrimitivesSupported() {
        int[] primitives = new int[]{PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK};

        long elapsedTimeNs = 0;
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        while (state.keepRunning(elapsedTimeNs)) {
            long startTimeNs = System.nanoTime();
            mVibrator.arePrimitivesSupported(primitives);
            elapsedTimeNs = System.nanoTime() - startTimeNs;
        }
    }

    @Test
    public void testCancelIdle() {
        long elapsedTimeNs = 0;
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        while (state.keepRunning(elapsedTimeNs)) {
            long startTimeNs = System.nanoTime();
            mVibrator.cancel();
            elapsedTimeNs = System.nanoTime() - startTimeNs;
        }
    }

    @Test
    public void testCancelVibrating() {
        VibrationEffect effect = VibrationEffect.createOneShot(SECONDS.toMillis(2),
                VibrationEffect.DEFAULT_AMPLITUDE);

        long elapsedTimeNs = 0;
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();

        while (state.keepRunning(elapsedTimeNs)) {
            // Wait until the vibration is taken by the service and starts before cancelling.
            mVibrator.vibrate(effect);
            SystemClock.sleep(SERVICE_DELAY_MS);

            long startTimeNs = System.nanoTime();
            mVibrator.cancel();
            elapsedTimeNs = System.nanoTime() - startTimeNs;
        }
    }

    private void assertVibratorIdle() throws InterruptedException {
        assertWithMessage("Vibrator should be idle before test")
                .that(mStateListener.awaitIdle(5, SECONDS)).isTrue();
        mStateListener.resetCounters();
    }

    private void benchmarkVibrate(VibrationEffect effect, long durationMs)
            throws InterruptedException {
        benchmarkVibrate(effect, durationMs, unused -> true);
    }

    private void benchmarkVibrate(VibrationEffect effect, long durationMs,
            Predicate<Vibrator> isEffectSupported) throws InterruptedException {
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        if (!mVibrator.hasVibrator() || !isEffectSupported.test(mVibrator)) {
            // Device does not support effect, cannot listen to state changes.
            benchmarkVibrate(effect);
            return;
        }
        assertVibratorIdle();
        long elapsedTimeNs = 0;
        while (state.keepRunning(elapsedTimeNs)) {
            elapsedTimeNs = measureVibrateWithStateChangeLatency(state, effect, durationMs);
        }
    }

    private void benchmarkVibrate(VibrationEffect effect) {
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        long elapsedTimeNs = 0;
        while (state.keepRunning(elapsedTimeNs)) {
            elapsedTimeNs = measureVibrate(effect);
        }
    }

    private void benchmarkVibrateWithTraces(VibrationEffect effect, long durationMs)
            throws InterruptedException {
        assumeTrue("Device without vibrator", mVibrator.hasVibrator());
        assertVibratorIdle();
        ManualBenchmarkState state = mStatusReporter.getBenchmarkState();
        try {
            mTraceMethods = new TraceMarkParser(VIBRATION_TRACES);
            startAsyncAtrace();
            long elapsedTimeNs = 0;
            while (state.keepRunning(elapsedTimeNs)) {
                elapsedTimeNs = measureVibrateWithTraces(effect);
            }
        } finally {
            stopAsyncAtraceAndDumpTraces();
            addTracesToState(state, durationMs);
        }
    }

    /**
     * Measure {@link Vibrator#vibrate} method call latency then cancel vibration.
     *
     * <p>This will cancel the vibrator and apply a rate-limiting sleep to wait for the service to
     * become idle before next iteration.
     */
    private long measureVibrate(VibrationEffect effect) {
        long startTimeNs = System.nanoTime();
        mVibrator.vibrate(effect);
        long latencyNs = System.nanoTime() - startTimeNs;

        // Rate-limiting, stop vibration and wait for service to become idle.
        mVibrator.cancel();
        SystemClock.sleep(SERVICE_DELAY_MS);

        return latencyNs;
    }

    /**
     * Measure {@link Vibrator#vibrate} method call latency then wait for vibration to finish.
     *
     * <p>This will wait for the vibration and apply a rate-limiting sleep to wait for the service
     * to become idle before next iteration.
     */
    private long measureVibrateWithTraces(VibrationEffect effect) throws InterruptedException {
        long startTimeNs = System.nanoTime();
        mVibrator.vibrate(effect);
        long latencyNs = System.nanoTime() - startTimeNs;

        mStateListener.awaitVibrating(5, SECONDS);
        mStateListener.awaitIdle(5, SECONDS);
        mStateListener.resetCounters();

        // Rate-limiting, wait for service to become idle after vibration ended.
        SystemClock.sleep(SERVICE_DELAY_MS);

        return latencyNs;
    }

    /**
     * Measure {@link Vibrator#vibrate} method call latency with added metrics for state change.
     *
     * <p>This will add vibration latency as extra results and apply a rate-limiting sleep to wait
     * for the service to become idle before next iteration.
     */
    private long measureVibrateWithStateChangeLatency(ManualBenchmarkState state,
            VibrationEffect effect, long durationMs) throws InterruptedException {
        long vibrateTimeNs = System.nanoTime();
        mVibrator.vibrate(effect);
        long vibrateLatencyNs = System.nanoTime() - vibrateTimeNs;

        long startTimeNs = mStateListener.awaitVibrating(5, SECONDS) ? System.nanoTime() : -1;
        long stopTimeNs = mStateListener.awaitIdle(5, SECONDS) ? System.nanoTime() : -1;
        mStateListener.resetCounters();

        // Rate-limiting, wait for service to clean-up previous vibration and become idle.
        SystemClock.sleep(SERVICE_DELAY_MS);

        if (startTimeNs < 0) {
            Log.w(TAG, "Vibrator state ON never received for " + effect);
            return vibrateLatencyNs;
        }
        if (stopTimeNs < 0) {
            Log.w(TAG, "Vibrator state OFF never received for " + effect);
            return vibrateLatencyNs;
        }

        long startLatencyNs = startTimeNs - vibrateTimeNs;
        long stopLatencyNs = stopTimeNs - startTimeNs - durationMs * NANOS_PER_MS;
        if (stopLatencyNs < 0) {
            Log.w(TAG, "Vibration stopped " + -stopLatencyNs + "ns early for " + effect);
            stopLatencyNs = 0;
        }
        state.addExtraResult(VIBRATOR_STATE_START_LATENCY_METRIC_KEY, startLatencyNs);
        state.addExtraResult(VIBRATOR_STATE_STOP_LATENCY_METRIC_KEY, stopLatencyNs);
        return vibrateLatencyNs;
    }

    private static void startAsyncAtrace() {
        getInstrumentation().getUiAutomation().executeShellCommand(ATRACE_START);
        // Wait for command to take effect.
        SystemClock.sleep(SECONDS.toMillis(1));
    }

    private void stopAsyncAtraceAndDumpTraces() {
        getInstrumentation().getUiAutomation().executeShellCommand(ATRACE_STOP);
        if (mTraceMethods == null) {
            Log.w(TAG, "No trace methods being tracked");
            return;
        }
        InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(
                getInstrumentation().getUiAutomation().executeShellCommand(ATRACE_DUMP));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                mTraceMethods.visit(line);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to read the result of stopped atrace", e);
        }
    }

    private void addTracesToState(ManualBenchmarkState state, long durationMs) {
        if (mTraceMethods == null) {
            Log.w(TAG, "No trace methods");
            return;
        }
        mTraceMethods.forAllSlices((key, slices) -> {
            if (slices.size() < 2) {
                Log.w(TAG, "No enough trace samples for: " + key);
                return;
            }
            for (TraceMarkParser.TraceMarkSlice slice : slices) {
                long valueNs = (long) (slice.getDurationInSeconds() * NANOS_PER_S);
                state.addExtraResult(key, valueNs);
                if (durationMs > 0 && LATENCY_TRACES.contains(key)) {
                    addVibrationLatencyMetricForTrace(state, key, valueNs, durationMs);
                }
            }
        });
        Log.i(TAG, String.valueOf(mTraceMethods));
    }

    private void addVibrationLatencyMetricForTrace(ManualBenchmarkState state, String key,
            long valueNs, long durationMs) {
        long latencyNs = valueNs - durationMs * NANOS_PER_MS;
        if (latencyNs < 0) {
            Log.w(TAG, "Vibration stopped " + -latencyNs + "ns early for trace " + key);
            latencyNs = 0;
        }
        state.addExtraResult(key + LATENCY_METRIC_KEY_SUFFIX, latencyNs);
    }

    /** {@link Vibrator.OnVibratorStateChangedListener} implementation for testing. */
    private static final class VibratorStateListener
            implements Vibrator.OnVibratorStateChangedListener {
        private CountDownLatch mStartCount = new CountDownLatch(1);
        private CountDownLatch mStopCount = new CountDownLatch(1);

        @Override
        public synchronized void onVibratorStateChanged(boolean isVibrating) {
            if (isVibrating) {
                mStartCount.countDown();
            } else {
                mStopCount.countDown();
            }
        }

        public boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
            return mStopCount.await(timeout, unit);
        }

        public boolean awaitVibrating(long timeout, TimeUnit unit) throws InterruptedException {
            return mStartCount.await(timeout, unit);
        }

        public synchronized void resetCounters() {
            mStartCount = new CountDownLatch(1);
            mStopCount = new CountDownLatch(1);
        }
    }
}
