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

package android.os;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;

import com.google.protobuf.ExtensionRegistryLite;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import perfetto.protos.AndroidTrackEventOuterClass;
import perfetto.protos.AndroidTrackEventOuterClass.AndroidMessageQueue;
import perfetto.protos.AndroidTrackEventOuterClass.AndroidTrackEvent;
import perfetto.protos.DataSourceConfigOuterClass;
import perfetto.protos.InternedDataOuterClass;
import perfetto.protos.TraceConfigOuterClass;
import perfetto.protos.TraceOuterClass;
import perfetto.protos.TracePacketOuterClass;
import perfetto.protos.TrackDescriptorOuterClass;
import perfetto.protos.TrackEventConfigOuterClass;
import perfetto.protos.TrackEventOuterClass;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the Perfetto SDK tracing for the {@link android.os.MessageQueue} and {@link
 * android.os.Looper} classes. <br>
 * You need to manually enable or disable the {@code android.os.perfetto_sdk_tracing_v3} flag before
 * running this test. This is needed because the {@link PerfettoTrace} caches the value of the flag
 * that was at the boot time, and the {@code SetFlagsRule} can't change it. <br>
 * Run the following adb commands before running the test: <br>
 * {@code adb shell aflags enable android.os.perfetto_sdk_tracing_v3 } <br>
 * or {@code adb shell aflags disable android.os.perfetto_sdk_tracing_v3 } <br>
 * and then <br>
 * {@code adb reboot} <br>
 *
 * <p>This test is a parameterized test, and only the version for the flag value that matches the
 * boot flag value will be run. The test output allows you to verify that you are running the
 * correct version.
 */
@RunWith(ParameterizedAndroidJunit4.class)
@DisabledOnRavenwood(blockedBy = PerfettoTrace.class)
@RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
public class PerfettoTraceMessageQueueTest {
    private static final boolean PERFETTO_V3_FLAG_WHEN_STARTED =
            android.os.Flags.perfettoSdkTracingV3();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V3);
    }

    @Rule public SetFlagsRule mSetFlagsRule;

    public PerfettoTraceMessageQueueTest(FlagsParameterization flags) {
        mSetFlagsRule = new SetFlagsRule(flags);
    }

    private static final int MESSAGE = 1234567;
    private static final int MESSAGE_DELAYED = 7654321;

    private final Set<String> mCategoryNames = new ArraySet<>();
    private final Set<String> mEventNames = new ArraySet<>();

    @BeforeClass
    public static void setUpClass() {
        PerfettoTrace.register(true);
        PerfettoTrace.registerCategories();
    }

    private boolean mPerfettoV3FlagWhenRunning = false;

    @Before
    public void setUp() {
        // The value of the flag is changed by the 'mSetFlagsRule'.
        mPerfettoV3FlagWhenRunning = android.os.Flags.perfettoSdkTracingV3();
        Assume.assumeTrue(
                "Parametrized test flag value is not equal to the boot time flag value.",
                mPerfettoV3FlagWhenRunning == PERFETTO_V3_FLAG_WHEN_STARTED);
        mCategoryNames.clear();
        mEventNames.clear();
    }

    @Test
    public void testMessageQueue() throws Exception {
        // Assert we initialize the correct API version.
        if (mPerfettoV3FlagWhenRunning) {
            assertThat(PerfettoTrace.MQ_CATEGORY.isRegistered()).isFalse();
            assertThat(PerfettoTrace.MQ_CATEGORY_V3.isRegistered()).isTrue();
        } else {
            assertThat(PerfettoTrace.MQ_CATEGORY.isRegistered()).isTrue();
            assertThat(PerfettoTrace.MQ_CATEGORY_V3.isRegistered()).isFalse();
        }

        final String mqReceiverThreadName = "mq_test_thread";
        final String mqSenderThreadName = Thread.currentThread().getName();
        final HandlerThread thread = new HandlerThread(mqReceiverThreadName);
        thread.start();
        final Handler handler = thread.getThreadHandler();
        final CountDownLatch latch = new CountDownLatch(1);

        byte[] mqTraceConfig = getTraceConfig("mq").toByteArray();
        PerfettoTrace.Session session = null;
        com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session sessionV3 = null;
        if (mPerfettoV3FlagWhenRunning) {
            sessionV3 =
                    new com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session(
                            true, mqTraceConfig);
        } else {
            session = new PerfettoTrace.Session(true, mqTraceConfig);
        }

        final int eventsCount = 4;

        handler.sendEmptyMessage(MESSAGE);
        handler.sendEmptyMessageDelayed(MESSAGE_DELAYED, 10);
        handler.sendEmptyMessage(MESSAGE);
        handler.postDelayed(
                () -> {
                    latch.countDown();
                },
                20);
        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isTrue();

        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        AndroidTrackEventOuterClass.registerAllExtensions(registry);

        byte[] traceData;
        if (mPerfettoV3FlagWhenRunning) {
            traceData = sessionV3.close();
        } else {
            traceData = session.close();
        }
        TraceOuterClass.Trace trace = TraceOuterClass.Trace.parseFrom(traceData, registry);

        int counterCount = 0;
        int sliceEndEventCount = 0;

        Long counterTrackUuid = null;
        ArrayList<AndroidMessageQueue> messageQueueSendEvents = new ArrayList<>();
        ArrayList<Long> messageQueueSendFlowIds = new ArrayList<>();
        ArrayList<AndroidMessageQueue> messageQueueReceiveEvents = new ArrayList<>();
        ArrayList<Long> messageQueueReceiveTerminateFlowIds = new ArrayList<>();

        for (TracePacketOuterClass.TracePacket packet : trace.getPacketList()) {
            if (packet.hasTrackDescriptor()) {
                TrackDescriptorOuterClass.TrackDescriptor trackDescriptor =
                        packet.getTrackDescriptor();
                if (trackDescriptor.getName().equals(mqReceiverThreadName)
                        && trackDescriptor.hasCounter()) {
                    counterTrackUuid = trackDescriptor.getUuid();
                }
            } else if (packet.hasTrackEvent()) {
                TrackEventOuterClass.TrackEvent event = packet.getTrackEvent();
                if (event.getType() == TrackEventOuterClass.TrackEvent.Type.TYPE_INSTANT) {
                    if (event.hasExtension(AndroidTrackEvent.messageQueue)) {
                        AndroidMessageQueue mqEvent =
                                event.getExtension(AndroidTrackEvent.messageQueue);
                        messageQueueSendEvents.add(mqEvent);
                        assertThat(event.getFlowIdsCount()).isEqualTo(1);
                        messageQueueSendFlowIds.add(event.getFlowIds(0));
                    }
                } else if (event.getType()
                        == TrackEventOuterClass.TrackEvent.Type.TYPE_SLICE_BEGIN) {
                    if (event.hasExtension(AndroidTrackEvent.messageQueue)) {
                        AndroidMessageQueue mqEvent =
                                event.getExtension(AndroidTrackEvent.messageQueue);
                        messageQueueReceiveEvents.add(mqEvent);
                        assertThat(event.getTerminatingFlowIdsCount()).isEqualTo(1);
                        messageQueueReceiveTerminateFlowIds.add(event.getTerminatingFlowIds(0));
                    }
                } else if (event.getType() == TrackEventOuterClass.TrackEvent.Type.TYPE_SLICE_END) {
                    sliceEndEventCount++;
                } else if (event.getType() == TrackEventOuterClass.TrackEvent.Type.TYPE_COUNTER) {
                    if (counterTrackUuid != null && event.getTrackUuid() == counterTrackUuid) {
                        counterCount++;
                    }
                }
            }
            collectInternedData(packet);
        }

        assertThat(mCategoryNames).containsExactly("mq");
        assertThat(mEventNames).containsExactly("message_queue_send", "message_queue_receive");
        assertThat(counterTrackUuid).isNotNull();
        assertThat(counterCount).isAtLeast(eventsCount);
        assertThat(sliceEndEventCount).isEqualTo(eventsCount);

        assertThat(messageQueueSendEvents).hasSize(eventsCount);
        assertThat(messageQueueSendFlowIds).hasSize(eventsCount);

        assertThat(messageQueueReceiveEvents).hasSize(eventsCount);
        assertThat(messageQueueReceiveTerminateFlowIds).hasSize(eventsCount);

        assertThat(messageQueueSendEvents.get(0).getMessageCode()).isEqualTo(MESSAGE);
        assertThat(messageQueueSendEvents.get(0).getMessageDelayMs()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(0).getReceivingThreadName())
                .isEqualTo(mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(1).getMessageCode()).isEqualTo(MESSAGE_DELAYED);
        assertThat(messageQueueSendEvents.get(1).getMessageDelayMs()).isEqualTo(10);
        assertThat(messageQueueSendEvents.get(1).getReceivingThreadName())
                .isEqualTo(mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(2).getMessageCode()).isEqualTo(MESSAGE);
        assertThat(messageQueueSendEvents.get(2).getMessageDelayMs()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(2).getReceivingThreadName())
                .isEqualTo(mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(3).getMessageCode()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(3).getMessageDelayMs()).isEqualTo(20);
        assertThat(messageQueueSendEvents.get(3).getReceivingThreadName())
                .isEqualTo(mqReceiverThreadName);

        assertThat(messageQueueReceiveEvents.get(0).getSendingThreadName())
                .isEqualTo(mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(1).getSendingThreadName())
                .isEqualTo(mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(2).getSendingThreadName())
                .isEqualTo(mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(3).getSendingThreadName())
                .isEqualTo(mqSenderThreadName);

        // The second message was send with a delay, and was received after the third one,
        // so we assert that the terminating flow Id of the third message is equal to the flow Id
        // of the second and the terminating flow id of the second is equal to the flow Id of the
        // third.
        assertThat(messageQueueSendFlowIds.get(0))
                .isEqualTo(messageQueueReceiveTerminateFlowIds.get(0));
        assertThat(messageQueueSendFlowIds.get(1))
                .isEqualTo(messageQueueReceiveTerminateFlowIds.get(2));
        assertThat(messageQueueSendFlowIds.get(2))
                .isEqualTo(messageQueueReceiveTerminateFlowIds.get(1));
        assertThat(messageQueueSendFlowIds.get(3))
                .isEqualTo(messageQueueReceiveTerminateFlowIds.get(3));
    }

    private TraceConfigOuterClass.TraceConfig getTraceConfig(String cat) {
        TraceConfigOuterClass.TraceConfig.BufferConfig bufferConfig =
                TraceConfigOuterClass.TraceConfig.BufferConfig.newBuilder().setSizeKb(1024).build();
        TrackEventConfigOuterClass.TrackEventConfig trackEventConfig =
                TrackEventConfigOuterClass.TrackEventConfig.newBuilder()
                        .addEnabledCategories(cat)
                        .build();
        DataSourceConfigOuterClass.DataSourceConfig dsConfig =
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder()
                        .setName("track_event")
                        .setTargetBuffer(0)
                        .setTrackEventConfig(trackEventConfig)
                        .build();
        TraceConfigOuterClass.TraceConfig.DataSource ds =
                TraceConfigOuterClass.TraceConfig.DataSource.newBuilder()
                        .setConfig(dsConfig)
                        .build();
        TraceConfigOuterClass.TraceConfig traceConfig =
                TraceConfigOuterClass.TraceConfig.newBuilder()
                        .addBuffers(bufferConfig)
                        .addDataSources(ds)
                        .build();
        return traceConfig;
    }

    private void collectInternedData(TracePacketOuterClass.TracePacket packet) {
        if (!packet.hasInternedData()) {
            return;
        }

        InternedDataOuterClass.InternedData data = packet.getInternedData();

        for (TrackEventOuterClass.EventCategory cat : data.getEventCategoriesList()) {
            mCategoryNames.add(cat.getName());
        }
        for (TrackEventOuterClass.EventName ev : data.getEventNamesList()) {
            mEventNames.add(ev.getName());
        }
    }
}
