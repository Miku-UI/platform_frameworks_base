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

package com.android.server.stats.binder;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;

import android.os.Process;
import android.os.binder.BinderCallsStats;
import android.os.binder.BinderSpamStats;
import android.os.binder.SingleSecondBinderStats;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto;
import com.android.server.LocalServices;
import com.android.server.signalcollector.SignalCollectorManagerInternal;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;

/**
 * Tests for {@link com.android.server.stats.binder.BinderStatsConsumerService}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class BinderStatsConsumerServiceTest {
    private BinderStatsConsumerService mService;
    private static final int SPAM_STATS_ATOM_ID = 1064;
    private static final int CALL_STATS_ATOM_ID = 1090;
    private static final long TEST_START_TIME = 100_000L;
    private static final long TEST_FLUSH_TIME = TEST_START_TIME + 60_001L;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;
    @Mock SignalCollectorManagerInternal mSignalCollectorManagerInternal;

    @Before
    public void setUp() {
        mService = new BinderStatsConsumerService();
        mStatsEventCaptor = ArgumentCaptor.forClass(StatsEvent.class);
        LocalServices.addService(SignalCollectorManagerInternal.class,
                mSignalCollectorManagerInternal);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(SignalCollectorManagerInternal.class);
    }

    private void flushStats() throws Exception {
        mService.maybeFlushOldValues(TEST_FLUSH_TIME);
    }

    private StatsEvent buildBinderCallsStats(BinderCallsStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(CALL_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount);
        builder.writeLong(stat.durationSumMicros);
        builder.writeLong(stat.secondsWithAtLeast10Calls);
        builder.writeLong(stat.secondsWithAtLeast50Calls);
        builder.writeLong(stat.callDurationSumSquaredMicros);
        builder.writeLong(stat.cpuTimeCount);
        builder.writeLong(stat.cpuTimeSumMicros);
        builder.writeLong(stat.cpuTimeSumSquaredMicros);
        builder.writeLong(/* secondsWithAtLeast125Calls= */ 0);
        builder.writeLong(/* secondsWithAtLeast250Calls= */ 0);
        builder.writeIntArray(new int[0]);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildBinderSpamStats(BinderSpamStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(SPAM_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.secondsWithAtLeast125Calls);
        builder.writeLong(stat.secondsWithAtLeast250Calls);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildSingleSecondBinderCallsStats(
            SingleSecondBinderStats stat, int aggregationCount, int[] expectedHistogram) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(CALL_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount * ((long) aggregationCount));
        builder.writeLong(stat.durationMicrosSum * ((long) aggregationCount));
        builder.writeLong(stat.durationCount >= 10 ? aggregationCount : 0);
        builder.writeLong(stat.durationCount >= 50 ? aggregationCount : 0);
        builder.writeLong(stat.durationMicrosSquaredSum * ((long) aggregationCount));
        builder.writeLong(stat.cpuTimeCount * ((long) aggregationCount));
        builder.writeLong(stat.cpuTimeMicrosSum * ((long) aggregationCount));
        builder.writeLong(stat.cpuTimeMicrosSquaredSum * ((long) aggregationCount));
        builder.writeLong(stat.callCount >= 125 ? aggregationCount : 0);
        builder.writeLong(stat.callCount >= 250 ? aggregationCount : 0);
        builder.writeIntArray(expectedHistogram);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildSingleSecondBinderSpamStats(
            SingleSecondBinderStats stat, int aggregationCount) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(SPAM_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount >= 125 ? aggregationCount : 0);
        builder.writeLong(stat.callCount >= 250 ? aggregationCount : 0);
        return builder.usePooledBuffer().build();
    }

    @Test
    public void testReportSingleSecondGranularityStats_noHistogram() throws Exception {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 10;
        stats[0].durationMicrosSum = 100;
        stats[0].durationMicrosSquaredSum = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeMicrosSum = 50;
        stats[0].cpuTimeMicrosSquaredSum = 250;
        stats[0].durationBinIndices = new byte[0]; // Empty indices when histogram is off

        mService.reportSecondGranularityStats(stats, TEST_START_TIME);

        flushStats();

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        StatsEvent expectedEvent = buildSingleSecondBinderCallsStats(stats[0], 1, new int[0]);
        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportSingleSecondGranularityStats_aggregation() throws Exception {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 10;
        stats[0].durationMicrosSum = 100;
        stats[0].durationMicrosSquaredSum = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeMicrosSum = 50;
        stats[0].cpuTimeMicrosSquaredSum = 250;
        stats[0].durationBinIndices = new byte[] {1, 5, 10};
        stats[0].durationCount = stats[0].durationBinIndices.length;

        mService.reportSecondGranularityStats(stats, TEST_START_TIME);
        mService.reportSecondGranularityStats(stats, TEST_START_TIME);
        mService.reportSecondGranularityStats(stats, TEST_START_TIME);

        flushStats();

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        int[] expectedHistogram = new int[100];
        expectedHistogram[1] = 3;
        expectedHistogram[5] = 3;
        expectedHistogram[10] = 3;

        StatsEvent expectedEvent =
                buildSingleSecondBinderCallsStats(stats[0], 3, expectedHistogram);
        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportSingleSecondGranularityStats_multipleDifferentStats() throws Exception {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[2];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 10;
        stats[0].durationMicrosSum = 100;
        stats[0].durationMicrosSquaredSum = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeMicrosSum = 50;
        stats[0].cpuTimeMicrosSquaredSum = 250;
        stats[0].durationBinIndices = new byte[] {1, 2, 3};
        stats[0].durationCount = stats[0].durationBinIndices.length;

        stats[1] = new SingleSecondBinderStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].callCount = 20;
        stats[1].durationCount = 20;
        stats[1].durationMicrosSum = 200;
        stats[1].durationMicrosSquaredSum = 145;
        stats[1].cpuTimeCount = 8;
        stats[1].cpuTimeMicrosSum = 74;
        stats[1].cpuTimeMicrosSquaredSum = 932;
        stats[1].durationBinIndices = new byte[] {4, 5, 6, 7};
        stats[1].durationCount = stats[1].durationBinIndices.length;

        mService.reportSecondGranularityStats(stats, TEST_START_TIME);

        flushStats();

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        int[] expectedHistogram1 = new int[100];
        expectedHistogram1[1] = 1;
        expectedHistogram1[2] = 1;
        expectedHistogram1[3] = 1;

        int[] expectedHistogram2 = new int[100];
        expectedHistogram2[4] = 1;
        expectedHistogram2[5] = 1;
        expectedHistogram2[6] = 1;
        expectedHistogram2[7] = 1;

        StatsEvent expectedEvent1 =
                buildSingleSecondBinderCallsStats(stats[0], 1, expectedHistogram1);
        StatsEvent expectedEvent2 =
                buildSingleSecondBinderCallsStats(stats[1], 1, expectedHistogram2);
        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);
    }

    @Test
    public void testReportSingleSecondGranularityStats_spam() throws Exception {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 150; // Should trigger >= 125 spam counter
        stats[0].durationCount = 10;
        stats[0].durationMicrosSum = 100;
        stats[0].durationMicrosSquaredSum = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeMicrosSum = 50;
        stats[0].cpuTimeMicrosSquaredSum = 250;
        stats[0].durationBinIndices = new byte[] {10, 20, 30};
        stats[0].durationCount = stats[0].durationBinIndices.length;

        mService.reportSecondGranularityStats(stats, TEST_START_TIME);

        flushStats();

        // Expecting 2 atoms, one for call stats and one for spam
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));

        int[] expectedHistogram = new int[100];
        expectedHistogram[10] = 1;
        expectedHistogram[20] = 1;
        expectedHistogram[30] = 1;

        StatsEvent expectedCallStatsEvent =
                buildSingleSecondBinderCallsStats(stats[0], 1, expectedHistogram);
        AtomsProto.Atom expectedCallStatsAtom =
                StatsEventTestUtils.convertToAtom(expectedCallStatsEvent);

        StatsEvent expectedSpamEvent = buildSingleSecondBinderSpamStats(stats[0], 1);
        AtomsProto.Atom expectedSpamAtom = StatsEventTestUtils.convertToAtom(expectedSpamEvent);

        assertTrue(
                (actualAtom1.equals(expectedCallStatsAtom) && actualAtom2.equals(expectedSpamAtom))
                || (actualAtom1.equals(expectedSpamAtom)
                        && actualAtom2.equals(expectedCallStatsAtom)));
    }

    @Test
    public void testReportSingleSecondGranularityStats_timeBasedFlush() throws Exception {
        long startTime = android.os.SystemClock.elapsedRealtime();
        SingleSecondBinderStats[] stats1 = new SingleSecondBinderStats[1];
        stats1[0] = new SingleSecondBinderStats();
        stats1[0].clientUid = 1000;
        stats1[0].interfaceDescriptor = "com.example.IFoo";
        stats1[0].aidlMethod = "bar";
        stats1[0].callCount = 10;
        stats1[0].durationCount = 10;
        stats1[0].durationMicrosSum = 100;
        stats1[0].durationMicrosSquaredSum = 112;
        stats1[0].cpuTimeCount = 5;
        stats1[0].cpuTimeMicrosSum = 50;
        stats1[0].cpuTimeMicrosSquaredSum = 250;
        stats1[0].durationBinIndices = new byte[] {40, 50};
        stats1[0].durationCount = stats1[0].durationBinIndices.length;

        // First report: creates the bucket at startTime
        mService.reportSecondGranularityStats(stats1, startTime);

        // Verify no write yet (still in aggregation window)
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(0));

        // Second report (different client) at startTime + 61s: should trigger flush of the
        // first bucket
        SingleSecondBinderStats[] stats2 = new SingleSecondBinderStats[1];
        stats2[0] = new SingleSecondBinderStats();
        stats2[0].clientUid = 1001;
        stats2[0].interfaceDescriptor = "com.example.IBar";
        stats2[0].aidlMethod = "foo";
        stats2[0].callCount = 5;
        stats2[0].durationBinIndices = new byte[0];

        mService.reportSecondGranularityStats(stats2, startTime + 61_000);

        // Verify that the first bucket was reported
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(1));

        int[] expectedHistogram = new int[100];
        expectedHistogram[40] = 1;
        expectedHistogram[50] = 1;

        StatsEvent expectedEvent =
                buildSingleSecondBinderCallsStats(stats1[0], 1, expectedHistogram);
        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportCallStats() throws InvalidProtocolBufferException {
        BinderCallsStats[] stats = new BinderCallsStats[1];
        stats[0] = new BinderCallsStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationSumMicros = 100;
        stats[0].secondsWithAtLeast10Calls = 1;
        stats[0].secondsWithAtLeast50Calls = 0;
        stats[0].callDurationSumSquaredMicros = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeSumMicros = 50;
        stats[0].cpuTimeSumSquaredMicros = 250;
        StatsEvent expectedEvent = buildBinderCallsStats(stats[0]);

        mService.reportCallStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);

        verify(mSignalCollectorManagerInternal).reportBinderStats(stats);
    }

    @Test
    public void testReportSpamStats() throws Exception, InvalidProtocolBufferException {
        BinderSpamStats[] stats = new BinderSpamStats[1];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 5;
        stats[0].secondsWithAtLeast250Calls = 2;
        StatsEvent expectedEvent = buildBinderSpamStats(stats[0]);

        mService.reportSpamStats(stats);

        flushStats();

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportMultipleCallStatsAtoms() throws InvalidProtocolBufferException {
        BinderCallsStats[] stats = new BinderCallsStats[2];
        stats[0] = new BinderCallsStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationSumMicros = 100;
        stats[0].secondsWithAtLeast10Calls = 1;
        stats[0].secondsWithAtLeast50Calls = 0;
        stats[0].callDurationSumSquaredMicros = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeSumMicros = 50;
        stats[0].cpuTimeSumSquaredMicros = 250;

        stats[1] = new BinderCallsStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].callCount = 20;
        stats[1].durationSumMicros = 200;
        stats[1].secondsWithAtLeast10Calls = 2;
        stats[1].secondsWithAtLeast50Calls = 1;
        stats[1].callDurationSumSquaredMicros = 145;
        stats[1].cpuTimeCount = 8;
        stats[1].cpuTimeSumMicros = 74;
        stats[1].cpuTimeSumSquaredMicros = 932;

        StatsEvent expectedEvent1 = buildBinderCallsStats(stats[0]);
        StatsEvent expectedEvent2 = buildBinderCallsStats(stats[1]);

        mService.reportCallStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);

        verify(mSignalCollectorManagerInternal).reportBinderStats(stats);
    }

    @Test
    public void testReportMultipleSpamStatsAtoms() throws InvalidProtocolBufferException {
        BinderSpamStats[] stats = new BinderSpamStats[2];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 5;

        stats[1] = new BinderSpamStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].secondsWithAtLeast125Calls = 10;
        stats[1].secondsWithAtLeast250Calls = 1;

        StatsEvent expectedEvent1 = buildBinderSpamStats(stats[0]);
        StatsEvent expectedEvent2 = buildBinderSpamStats(stats[1]);

        mService.reportSpamStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);
    }

    private static final int HISTOGRAM_SIZE = 100;

    @Test
    public void testAddSamplesToHistogram_emptyHistogram() {
        byte[] input = new byte[0];
        int[] expected = new int[0];
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        h.addSamplesToHistogram(input);
        assertArrayEquals(expected, h.getHistogram());
    }

    @Test
    public void testAddSamplesToHistogram_manyBins() {
        byte[] input = new byte[HISTOGRAM_SIZE];
        int[] expected = new int[HISTOGRAM_SIZE];
        for (int i = 0; i < HISTOGRAM_SIZE; i++) {
            input[i] = (byte) i;
            expected[i] = 1;
        }
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        h.addSamplesToHistogram(input);
        assertArrayEquals(expected, h.getHistogram());
    }

    @Test
    public void testAddSamplesToHistogram_repeatedIndices() {
        byte[] input = new byte[] {5, 5, 5, 10, 10, 20};
        int[] expected = new int[HISTOGRAM_SIZE];
        expected[5] = 3;
        expected[10] = 2;
        expected[20] = 1;
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        h.addSamplesToHistogram(input);
        assertArrayEquals(expected, h.getHistogram());
    }

    @Test
    public void testAddSamplesToHistogram_nullInput() {
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        assertThrows(NullPointerException.class, () -> h.addSamplesToHistogram(null));
    }

    @Test
    public void testOptimizedHistogram_conversionToFull() {
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        // Add 16 samples
        for (int i = 0; i < 16; i++) {
            h.addSamplesToHistogram(new byte[] {(byte) i});
        }
        // Should still be compact internally
        int[] expectedFull = new int[HISTOGRAM_SIZE];
        for (int i = 0; i < 16; i++) expectedFull[i] = 1;
        assertArrayEquals(expectedFull, h.getHistogram());

        // Add 17th sample, triggers conversion
        h.addSamplesToHistogram(new byte[] {(byte) 50});
        expectedFull[50] = 1;
        assertArrayEquals(expectedFull, h.getHistogram());

        // Add more samples after conversion to verify data preservation (no wiping)
        h.addSamplesToHistogram(new byte[] {(byte) 60, (byte) 60});
        expectedFull[60] = 2;
        assertArrayEquals(expectedFull, h.getHistogram());
    }

    @Test
    public void testAddSamplesToHistogram_outOfBoundsIndices() {
        // Test with index 100 (size is 100, so indices 0-99 are valid)
        byte[] input = new byte[] {0, 99, (byte) 100, (byte) 255};

        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        h.addSamplesToHistogram(input);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> h.getHistogram());
    }

    @Test
    public void testAddSamplesToHistogram_largeSingleAppend() {
        // Adding 20 samples in one go should trigger immediate conversion to full.
        byte[] input = new byte[20];
        int[] expected = new int[HISTOGRAM_SIZE];
        for (int i = 0; i < 20; i++) {
            input[i] = (byte) i;
            expected[i] = 1;
        }
        BinderStatsConsumerService.OptimizedHistogram h =
                new BinderStatsConsumerService.OptimizedHistogram();
        h.addSamplesToHistogram(input);
        assertArrayEquals(expected, h.getHistogram());
    }
}
