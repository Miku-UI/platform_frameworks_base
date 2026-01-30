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

package com.android.server.power;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat;

import static perfetto.protos.AppWakelockData.AppWakelockInfo;
import static perfetto.protos.InternedDataOuterClass.InternedData;
import static perfetto.protos.TracePacketOuterClass.TracePacket;

import android.os.test.TestLooper;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;

import perfetto.protos.AppWakelockConfig;
import perfetto.protos.DataSourceConfigOuterClass;

/** Tests for {@link com.android.server.power.WakelockTracer} */
public class WakelockTracerTest {
    private TestLooper mTestLooper = new TestLooper();
    private WakelockTracer mTracer = new WakelockTracer(
            mTestLooper.getLooper(), /* params= */ null);

    private ProtoInputStream emptyConfig() {
        return new ProtoInputStream(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().build().toByteArray());
    }

    private ProtoInputStream makeConfig(int thresholdMs, int delayMs, boolean dropPid) {
        return new ProtoInputStream(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder()
                        .setAppWakelocksConfig(
                                AppWakelockConfig.AppWakelocksConfig.newBuilder()
                                        .setFilterDurationBelowMs(thresholdMs)
                                        .setWriteDelayMs(delayMs)
                                        .setDropOwnerPid(dropPid)
                                        .build())
                        .build()
                        .toByteArray());
    }

    private long timeAtMillis(long millis) {
        return millis * 1_000_000L;
    }

    private long encodeTimestamp(long millis, boolean acquired) {
        return timeAtMillis(millis) << 1 | (acquired ? 1 : 0);
    }

    private TracePacket write(
            WakelockTracer.Instance instance, WakelockTracer.IncrementalState state, long nanoTime)
            throws InvalidProtocolBufferException {
        ProtoOutputStream stream = new ProtoOutputStream();
        instance.write(stream, state, nanoTime);
        return TracePacket.parseFrom(stream.getBytes());
    }

    @Test
    public void testWakelockUnderThreshold() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(10));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // Wakelock is under threshold, so it is not written.
        assertThat(packet.hasAppWakelockBundle()).isFalse();
    }

    @Test
    public void testWakelockAboveThreshold() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(10));
        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(100));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // Wakelock is above threshold, so it is written.
        assertThat(packet.getAppWakelockBundle().getInternIdList()).containsExactly(1, 1);
        assertThat(packet.getAppWakelockBundle().getEncodedTsList())
                .containsExactly(encodeTimestamp(0, true), encodeTimestamp(90, false));
    }

    @Test
    public void testInternedDataFormat() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(0));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        assertThat(packet.getInternedData())
                .isEqualTo(
                        InternedData.newBuilder()
                                .addAppWakelockInfo(
                                        AppWakelockInfo.newBuilder()
                                                .setIid(1)
                                                .setTag("foo")
                                                .setOwnerUid(1)
                                                .setOwnerPid(2)
                                                .setFlags(3)
                                                .build())
                                .build());
    }

    @Test
    public void testInternedDataUniqueness() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        // Each wakelock below changes one field from the first.
        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(false, "bar", 1, 2, 3, null, timeAtMillis(1));
        instance.onWakelockEvent(false, "foo", 9, 2, 3, null, timeAtMillis(2));
        instance.onWakelockEvent(false, "foo", 1, 9, 3, null, timeAtMillis(3));
        instance.onWakelockEvent(false, "foo", 1, 2, 9, null, timeAtMillis(4));

        // The wakelock below is a repeat, to show interned id re-use.
        instance.onWakelockEvent(false, "foo", 9, 2, 3, null, timeAtMillis(5));

        // The wakelock below is a repeat with different acquire/time values.
        instance.onWakelockEvent(true, "foo", 9, 2, 3, null, timeAtMillis(6));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // Five unique locks written first, then two repeats of the third item.
        assertThat(packet.getAppWakelockBundle().getInternIdList())
                .containsExactly(1, 2, 3, 4, 5, 3, 3);
        assertThat(packet.getAppWakelockBundle().getEncodedTsList())
                .containsExactly(
                        encodeTimestamp(0, false),
                        encodeTimestamp(1, false),
                        encodeTimestamp(2, false),
                        encodeTimestamp(3, false),
                        encodeTimestamp(4, false),
                        encodeTimestamp(5, false),
                        encodeTimestamp(6, true));
    }

    @Test
    public void testDropOwnerPid() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, true), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(true, "foo", 1, 4, 3, null, timeAtMillis(10));
        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(20));
        instance.onWakelockEvent(false, "foo", 1, 4, 3, null, timeAtMillis(90));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // With dropOwnerPid, both pairing and interning excludes the pid. Above, the
        // second and third events will pair and be below threshold with different
        // pids, and the output will use a single intern id for first/fourth event.
        assertThat(packet.getAppWakelockBundle().getInternIdList()).containsExactly(1, 1);
        assertThat(packet.getAppWakelockBundle().getEncodedTsList())
                .containsExactly(encodeTimestamp(0, true), encodeTimestamp(90, false));
    }

    @Test
    public void testStartTooRecent() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));

        TracePacket packet = write(instance, state, timeAtMillis(10));

        // The acquire event is too recent to know if the dur > threshold, don't write.
        assertThat(packet).isEqualTo(TracePacket.newBuilder().build());
        assertThat(packet.hasAppWakelockBundle()).isFalse();
    }

    @Test
    public void testStartOldEnough() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // The acquire event is 100ms old, it must be > 50ms threshold.
        assertThat(packet.hasAppWakelockBundle()).isTrue();
    }

    @Test
    public void testUnmatchedRelease() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(false, "bar", 1, 2, 3, null, timeAtMillis(90));

        TracePacket packet = write(instance, state, timeAtMillis(100));

        // End events should be written if there's no matching start (the start may have been
        // before the trace, or written due to already aging out. The end event does not care
        // about how recent it is like the acquire event.
        assertThat(packet.getAppWakelockBundle().getInternIdList()).containsExactly(1, 2);
    }

    @Test
    public void testSchedulesSingleWrite() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));

        // The write shouldn't dispatch until time has elapsed.
        assertThat(mTestLooper.dispatchAll()).isEqualTo(0);

        // There should only be one write scheduled at a time.
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
    }

    @Test
    public void testEmptyConfig() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(emptyConfig(), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));
        instance.onWakelockEvent(false, "foo", 1, 2, 3, null, timeAtMillis(1));

        // Default is threshold of zero, so a 1ms wakelock should be written.
        TracePacket packet = write(instance, state, timeAtMillis(100));
        assertThat(packet.getAppWakelockBundle().getInternIdList()).containsExactly(1, 1);

        // Default delay is 5000ms, so expect handler to trigger after that time.
        mTestLooper.moveTimeForward(4000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(0);

        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
    }

    @Test
    public void testResetPendingWhenEmpty() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(0));

        // Simulate a delay and write.
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
        write(instance, state, timeAtMillis(1000));

        // After further delay, no additional dispatches.
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(0);

        // After a new event and delay, expect a new dispatch.
        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(1000));
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
    }

    @Test
    public void testScheduleWriteWhenNonEmpty() throws InvalidProtocolBufferException {
        WakelockTracer.Instance instance = mTracer.createInstance(makeConfig(50, 1000, false), 0);
        WakelockTracer.IncrementalState state = new WakelockTracer.IncrementalState();

        instance.onWakelockEvent(true, "foo", 1, 2, 3, null, timeAtMillis(1000));

        // Simulate a delay and write.
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
        write(instance, state, timeAtMillis(1000));

        // Since the event was too new, it wasn't written above. The call to write should
        // internally schedule another write (as will be dispatched here).
        mTestLooper.moveTimeForward(1000);
        assertThat(mTestLooper.dispatchAll()).isEqualTo(1);
    }
}
