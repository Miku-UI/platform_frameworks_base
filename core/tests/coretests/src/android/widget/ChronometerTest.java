/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget;


import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.app.Activity;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.Pair;

import androidx.test.filters.LargeTest;

import com.android.frameworks.coretests.R;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Test {@link Chronometer} counting up and down.
 */
@SuppressWarnings("deprecation")
@LargeTest
public class ChronometerTest extends ActivityInstrumentationTestCase2<ChronometerActivity> {

    private Locale mOriginalLocale;

    private Activity mActivity;
    private Chronometer mChronometer;

    public ChronometerTest() {
        super(ChronometerActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mOriginalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        mActivity = getActivity();
        mChronometer = mActivity.findViewById(R.id.chronometer);
    }

    @Override
    protected void tearDown() throws Exception {
        Locale.setDefault(mOriginalLocale);
        super.tearDown();
    }

    @UiThreadTest
    public void testSystemClockChronometer() {
        var clocks = new Object() {
            public Instant systemNow = Instant.ofEpochMilli(1748615185000L);
            public long elapsedRealtime = 1000L;
        };
        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        mActivity.setContentView(chronometer);

        // Start state: timer for 2 minutes.
        Instant base = clocks.systemNow.plus(2, MINUTES);
        chronometer.setBase(base);
        chronometer.setCountDown(true);
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("02:00");

        // Clocks advance normally for 20 seconds.
        clocks.systemNow = clocks.systemNow.plus(20, SECONDS);
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(20).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("01:40");

        // After 1 realtime seconds, clock is adjusted and jumps forward by 4 additional seconds!
        clocks.systemNow = clocks.systemNow.plus(5, SECONDS);
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(1).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("01:35");
    }

    @UiThreadTest
    public void testChronometerStartingFromPausedDuration() {
        var clocks = new Object() {
            public Instant systemNow = Instant.ofEpochMilli(1748615185000L);
            public long elapsedRealtime = 10_000L;
        };
        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        mActivity.setContentView(chronometer);

        // Starts paused at 5 seconds.
        chronometer.setCountDown(true);
        chronometer.setPausedDuration(Duration.ofSeconds(5));
        assertThat(chronometer.getText().toString()).isEqualTo("00:05");

        // "Continue countdown" for 3 seconds.
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(3).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("00:02");
    }

    public void testChronometerTicksSequentially() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(6);
        ArrayList<String> ticks = new ArrayList<>();
        runOnUiThread(() -> {
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setOnChronometerTickListener((chronometer) -> {
                ticks.add(chronometer.getText().toString());
                latch.countDown();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                }
            });
            mChronometer.start();
        });
        assertTrue(latch.await(5500, TimeUnit.MILLISECONDS));
        assertEquals("00:00", ticks.get(0));
        assertEquals("00:01", ticks.get(1));
        assertEquals("00:02", ticks.get(2));
        assertEquals("00:03", ticks.get(3));
        assertEquals("00:04", ticks.get(4));
        assertEquals("00:05", ticks.get(5));
    }

    public void testChronometerCountDown() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(12);
        ArrayList<String> ticks = new ArrayList<>();
        runOnUiThread(() -> {
            mChronometer.setBase(SystemClock.elapsedRealtime() + 3_000);
            mChronometer.setCountDown(true);
            mChronometer.setOnChronometerTickListener((chronometer) -> {
                ticks.add(chronometer.getText().toString());
                latch.countDown();
            });

            // start in the next frame so that it is more than 1 ms below 3 seconds
            mChronometer.post(() -> mChronometer.start());
        });
        assertTrue(latch.await(12500, TimeUnit.MILLISECONDS));
        assertEquals("00:02", ticks.get(0));
        assertEquals("00:01", ticks.get(1));
        assertEquals("00:00", ticks.get(2));
        assertEquals("−00:01", ticks.get(3));
        assertEquals("−00:02", ticks.get(4));
        assertEquals("−00:03", ticks.get(5));
        assertEquals("−00:04", ticks.get(6));
        assertEquals("−00:05", ticks.get(7));
        assertEquals("−00:06", ticks.get(8));
        assertEquals("−00:07", ticks.get(9));
        assertEquals("−00:08", ticks.get(10));
        assertEquals("−00:09", ticks.get(11));
    }

    @UiThreadTest
    public void testChronometerDisplaysAdaptiveTimeFormat() throws Throwable {
        final List<String> expectedTicks = Arrays.asList(
                "9h 4m",
                "9h 4m",
                "9h 4m",
                "9h 4m",
                "9h 4m"
        );
        final Instant systemNow = Instant.now();
        testChronometerTicks(systemNow, expectedTicks, chronometer -> {
            chronometer.setBase(systemNow.plus(9, ChronoUnit.HOURS)
                    .plus(5, MINUTES));
            chronometer.setCountDown(true);
            chronometer.setUseAdaptiveFormat(true);
        });
    }

    @UiThreadTest
    public void testChronometerDisplaysCustomFormatting() throws Throwable {
        final List<String> expectedTicks = Arrays.asList(
                "Time elapsed: 00:01",
                "Time elapsed: 00:02",
                "Time elapsed: 00:03",
                "Time elapsed: 00:04",
                "Time elapsed: 00:05"
        );

        final Instant systemNow = Instant.now();
        testChronometerTicks(systemNow, expectedTicks, chronometer -> {
            chronometer.setFormat("Time elapsed: %s");
            chronometer.setCountDown(false);
            chronometer.setUseAdaptiveFormat(false); // Ensure adaptive format doesn't interfere.
        });
    }

    @UiThreadTest
    public void testChronometerAdaptiveTimeFormatSupportsCustomFormatting() throws Throwable {
        final List<String> expectedTicks = Arrays.asList(
                "Remaining time: 9h 4m",
                "Remaining time: 9h 4m",
                "Remaining time: 9h 4m",
                "Remaining time: 9h 4m",
                "Remaining time: 9h 4m"
        );

        final Instant systemNow = Instant.now();
        testChronometerTicks(systemNow, expectedTicks, chronometer -> {
            chronometer.setFormat("Remaining time: 9h 4m");
            chronometer.setBase(systemNow.plus(9, ChronoUnit.HOURS)
                    .plus(5, MINUTES));
            chronometer.setCountDown(true);
            chronometer.setUseAdaptiveFormat(true);
        });
    }

    @UiThreadTest
    public void testChronometerAdaptiveTimeFormatDisplaysNegativeTime() throws Throwable {
        final List<String> expectedTicks = Arrays.asList(
                "−1s",
                "−2s",
                "−3s",
                "−4s",
                "−5s"
        );

        final Instant systemNow = Instant.now();
        testChronometerTicks(systemNow, expectedTicks, chronometer -> {
            chronometer.setCountDown(true);
            chronometer.setUseAdaptiveFormat(true);
        });
    }

    @UiThreadTest
    public void testScheduledTicks() {
        long base = SystemClock.elapsedRealtime();

        // Non-adaptive: Always on the next second, regardless of stopwatch or countdown.
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ false, base,
                /* now= */ base + 200,
                /* expectedDelay= */ 800);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ false, base,
                /* now= */ base + 5 * SECOND_IN_MILLIS,
                /* expectedDelay= */ 1000);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ false, base,
                /* now= */ base + 30 * SECOND_IN_MILLIS + 300,
                /* expectedDelay= */ 700);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ false, base,
                /* now= */ base + 45 * MINUTE_IN_MILLIS + 900,
                /* expectedDelay= */ 100);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ true, base,
                /* now= */ base - 200,
                /* expectedDelay= */ 200);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ true, base,
                /* now= */ base - 10 * SECOND_IN_MILLIS,
                /* expectedDelay= */ 1000);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ true, base,
                /* now= */ base + 200, // overrun countdown
                /* expectedDelay= */ 800);
        verifyNextTickScheduledIn(/* adaptive= */ false, /* countdown= */ true, base,
                /* now= */ base + 4 * SECOND_IN_MILLIS + 300, // overrun countdown
                /* expectedDelay= */ 700);

        // Adaptive stopwatch, 2:20 elapsed (< 3 minutes) -> on the second.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ false, base,
                /* now= */ base + 2 * MINUTE_IN_MILLIS + 20 * SECOND_IN_MILLIS + 100,
                /* expectedDelay= */ 900);

        // Adaptive stopwatch, more than than 3 minutes elapsed -> on the next minute.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ false, base,
                /* now= */ base + 5 * MINUTE_IN_MILLIS + 10 * SECOND_IN_MILLIS + 300,
                /* expectedDelay= */ 49 * SECOND_IN_MILLIS + 700);

        // Adaptive timer, more than than 3 minutes remaining -> on the next minute.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ true, base,
                /* now= */ base - 4 * MINUTE_IN_MILLIS - 10 * SECOND_IN_MILLIS - 300,
                /* expectedDelay= */ 10 * SECOND_IN_MILLIS + 300);

        // Adaptive timer, slightly more than than 3 minutes remaining -> on the next minute.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ true, base,
                /* now= */ base - 3 * MINUTE_IN_MILLIS - 2 * SECOND_IN_MILLIS - 100,
                /* expectedDelay= */ 2 * SECOND_IN_MILLIS + 100);

        // Adaptive timer, barely a few ms more than than 3 minutes remaining -> on the next minute.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ true, base,
                /* now= */ base - 3 * MINUTE_IN_MILLIS - 1,
                /* expectedDelay= */ 1);

        // Adaptive timer, less than than 3 minutes remaining -> on the next second.
        verifyNextTickScheduledIn(/* adaptive= */ true, /* countdown= */ true, base,
                /* now= */ base - 2 * MINUTE_IN_MILLIS - 8 * SECOND_IN_MILLIS - 400,
                /* expectedDelay= */ 400);
    }

    @UiThreadTest
    public void testLowFrequencyMode_ChronometerFormat() throws Throwable {
        long baseTime = SystemClock.elapsedRealtime();
        final List<Pair<Long, String>> testCases = Arrays.asList(
                Pair.create(baseTime - 2 * 3600 * 1000 - 330 * 1000, "−2:05:--"),
                Pair.create(baseTime - 2 * 3600 * 1000, "−2:00:--"),
                Pair.create(baseTime - 330 * 1000, "−05:--"),
                Pair.create(baseTime - 90 * 1000, "−01:--"),
                Pair.create(baseTime - 60 * 1000, "−01:--"),
                Pair.create(baseTime - 59 * 1000, "−00:--"),
                Pair.create(baseTime - 30 * 1000, "−00:--"),
                Pair.create(baseTime + 30 * 1000, "00:--"),
                Pair.create(baseTime + 59 * 1000, "00:--"),
                Pair.create(baseTime + 60 * 1000, "01:--"),
                Pair.create(baseTime + 90 * 1000, "01:--"),
                Pair.create(baseTime + 330 * 1000, "05:--"),
                Pair.create(baseTime + 2 * 3600 * 1000, "2:00:--"),
                Pair.create(baseTime + 2 * 3600 * 1000 + 330 * 1000, "2:05:--")
        );
        testChronometerTicks(Instant.ofEpochMilli(baseTime), chronometer -> {
            chronometer.setBase(baseTime);
            chronometer.setLowFrequency(true);
        }, testCases);
    }

    @UiThreadTest
    public void testLowFrequencyMode_AdaptiveFormat() throws Throwable {
        long baseTime = SystemClock.elapsedRealtime();
        final List<Pair<Long, String>> testCases = Arrays.asList(
                Pair.create(baseTime - 2 * 3600 * 1000 - 330 * 1000, "−2h 5m"),
                Pair.create(baseTime - 2 * 3600 * 1000, "−2h"),
                Pair.create(baseTime - 330 * 1000, "−5m"),
                Pair.create(baseTime - 90 * 1000, "−1m"),
                Pair.create(baseTime - 60 * 1000, "−1m"),
                Pair.create(baseTime - 59 * 1000, "~ −1m"),
                Pair.create(baseTime - 30 * 1000, "~ −1m"),
                Pair.create(baseTime + 30 * 1000, "~ 1m"),
                Pair.create(baseTime + 59 * 1000, "~ 1m"),
                Pair.create(baseTime + 60 * 1000, "1m"),
                Pair.create(baseTime + 90 * 1000, "1m"),
                Pair.create(baseTime + 330 * 1000, "5m"),
                Pair.create(baseTime + 2 * 3600 * 1000, "2h"),
                Pair.create(baseTime + 2 * 3600 * 1000 + 330 * 1000, "2h 5m")
        );
        testChronometerTicks(Instant.ofEpochMilli(baseTime), chronometer -> {
            chronometer.setBase(baseTime);
            chronometer.setLowFrequency(true);
            chronometer.setUseAdaptiveFormat(true);
            chronometer.setCountDown(false);
        }, testCases);
    }

    private void verifyNextTickScheduledIn(boolean adaptive, boolean countdown, long base, long now,
            long expectedDelay) {
        AtomicLong elapsedRealtime = new AtomicLong(0);

        // Need to spy() because it's not possible to replace the looper used by postDelayed() :(
        Chronometer chronometer = spy(
                new Chronometer(mActivity, () -> elapsedRealtime.get(),
                        () -> Instant.ofEpochMilli(0), null, 0, 0));
        mActivity.setContentView(chronometer);

        elapsedRealtime.set(now);
        chronometer.setCountDown(countdown);
        chronometer.setBase(base);
        chronometer.setUseAdaptiveFormat(adaptive);

        chronometer.start();

        // Chronometer adds a small delay to prevent transitions *exactly* on the time, but for
        // testing it's better to hide this.
        verify(chronometer).postDelayed(any(), eq(expectedDelay + 3));
    }

    private void testChronometerTicks(
            Instant clockSystemNow,
            Consumer<Chronometer> chronometerConfigurator,
            List<Pair<Long, String>> testCases) throws Throwable {

        var clocks = new Object() {
            Instant systemNow = clockSystemNow;
            long elapsedRealtime = 1000L;
        };

        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        chronometerConfigurator.accept(chronometer);
        mActivity.setContentView(chronometer);

        for (Pair<Long, String> testCase : testCases) {
            clocks.elapsedRealtime = testCase.first;
            chronometer.updateText();
            assertThat(chronometer.getText().toString()).isEqualTo(testCase.second);
        }
    }

    private void testChronometerTicks(
            Instant clockSystemNow,
            List<String> expectedTicks,
            Consumer<Chronometer> chronometerConfigurator) throws Throwable {

        var clocks = new Object() {
            public Instant systemNow = clockSystemNow;
            public long elapsedRealtime = 1000L;
        };

        final int tickCount = expectedTicks.size();
        final ArrayList<String> actualTicks = new ArrayList<>();
        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        chronometerConfigurator.accept(chronometer);
        mActivity.setContentView(chronometer);

        for (int i = 0; i < tickCount; i++) {
            clocks.systemNow = clocks.systemNow.plus(1, ChronoUnit.SECONDS);
            clocks.elapsedRealtime += 1000L;
            chronometer.updateText();
            actualTicks.add(chronometer.getText().toString());
        }

        assertArrayEquals(expectedTicks.toArray(), actualTicks.toArray());
    }

    private void runOnUiThread(Runnable runnable) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            runnable.run();
            latch.countDown();
        });
        latch.await();
    }
}
