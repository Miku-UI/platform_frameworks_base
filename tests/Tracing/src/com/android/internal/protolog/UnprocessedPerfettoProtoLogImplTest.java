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

package com.android.internal.protolog;

import static android.tools.traces.Utils.busyWaitForDataSourceRegistration;
import static android.tracing.perfetto.TestUtils.createTempWriter;

import static perfetto.protos.TracePacketOuterClass.TracePacket.SequenceFlags.SEQ_NEEDS_INCREMENTAL_STATE;

import android.tools.Tag;
import android.tools.io.TraceType;
import android.tools.traces.io.ResultReader;
import android.tools.traces.monitors.PerfettoTraceMonitor;
import android.tracing.perfetto.DataSourceParams;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.protolog.common.IProtoLogGroup;
import com.android.internal.protolog.common.LogLevel;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import perfetto.protos.TraceOuterClass.Trace;
import perfetto.protos.TracePacketOuterClass.TracePacket;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UnprocessedPerfettoProtoLogImplTest {
    private static final String TEST_PROTOLOG_DATASOURCE_NAME = "test.android.protolog.unprocessed";

    private final File mTracingDirectory = InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getFilesDir();

    private static ProtoLogDataSource sTestDataSource;
    private static UnprocessedPerfettoProtoLogImpl sProtoLog;

    public UnprocessedPerfettoProtoLogImplTest() throws IOException { }

    @BeforeClass
    public static void setUp() throws Exception {
        sTestDataSource = new ProtoLogDataSource(TEST_PROTOLOG_DATASOURCE_NAME);
        DataSourceParams params =
                new DataSourceParams.Builder()
                        .setBufferExhaustedPolicy(
                                DataSourceParams
                                        .PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_DROP)
                        .build();
        sTestDataSource.register(params);
        busyWaitForDataSourceRegistration(TEST_PROTOLOG_DATASOURCE_NAME);

        sProtoLog = new UnprocessedPerfettoProtoLogImpl(sTestDataSource,
                TestProtoLogGroup.values());
        sProtoLog.enable();
    }

    @Before
    public void before() {
        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(false);
    }

    @After
    public void tearDown() {
        ProtoLogImpl.setSingleInstance(null);
    }

    @Test
    public void incrementalStateFlagSetForUnprocessedMessage() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            // Log a message with a string format. The message format string is the only
            // interned data.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "My Unprocessed Message");
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final var traceBytes = reader.readBytes(TraceType.PERFETTO, Tag.ALL);
        final var trace = Trace.parseFrom(traceBytes);

        final var targetViewerConfigs = trace.getPacketList().stream()
                .filter(TracePacket::hasProtologViewerConfig)
                .map(TracePacket::getProtologViewerConfig)
                .filter(config ->
                        config.getMessagesList().stream()
                                .anyMatch(messageConfig ->
                                        messageConfig.getGroupId()
                                                == TestProtoLogGroup.TEST_GROUP.getId()
                                ) && config.getMessagesCount() == 1
                )
                .toList();

        Truth.assertThat(targetViewerConfigs).hasSize(1);
        final var targetViewerConfig = targetViewerConfigs.getFirst();
        Truth.assertThat(targetViewerConfig.getMessagesList()).hasSize(1);
        final var targetMessageId = targetViewerConfig.getMessages(0).getMessageId();

        final var targetProtoLogMessagePackets = trace.getPacketList().stream()
                .filter(TracePacket::hasProtologMessage)
                .filter(packet -> packet.getProtologMessage().getMessageId()
                        == targetMessageId)
                .toList();

        Truth.assertThat(targetProtoLogMessagePackets).hasSize(1);
        final var targetProtoLogMessagePacket = targetProtoLogMessagePackets.getFirst();
        Truth.assertWithMessage("SEQ_NEEDS_INCREMENTAL_STATE flag should be set")
                .that(targetProtoLogMessagePacket.getSequenceFlags()
                        & SEQ_NEEDS_INCREMENTAL_STATE.getNumber())
                .isEqualTo(SEQ_NEEDS_INCREMENTAL_STATE.getNumber());
    }

    private enum TestProtoLogGroup implements IProtoLogGroup {
        TEST_GROUP(true, true, false, "TEST_TAG");

        private final boolean mEnabled;
        private volatile boolean mLogToProto;
        private volatile boolean mLogToLogcat;
        private final String mTag;

        TestProtoLogGroup(boolean enabled, boolean logToProto, boolean logToLogcat, String tag) {
            this.mEnabled = enabled;
            this.mLogToProto = logToProto;
            this.mLogToLogcat = logToLogcat;
            this.mTag = tag;
        }

        @Override
        public boolean isEnabled() {
            return mEnabled;
        }

        @Override
        public boolean isLogToLogcat() {
            return mLogToLogcat;
        }

        @Override
        public String getTag() {
            return mTag;
        }

        @Override
        public void setLogToLogcat(boolean logToLogcat) {
            this.mLogToLogcat = logToLogcat;
        }

        @Override
        public int getId() {
            return ordinal();
        }
    }
}
