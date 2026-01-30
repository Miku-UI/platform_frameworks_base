/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static android.tools.traces.Utils.busyWaitTracingSessionDoesntExist;
import static android.tools.traces.Utils.busyWaitTracingSessionExists;
import static android.tracing.perfetto.TestUtils.createTempWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static perfetto.protos.TracePacketOuterClass.TracePacket.SequenceFlags.SEQ_INCREMENTAL_STATE_CLEARED;
import static perfetto.protos.TracePacketOuterClass.TracePacket.SequenceFlags.SEQ_NEEDS_INCREMENTAL_STATE;

import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.tools.Tag;
import android.tools.io.TraceType;
import android.tools.traces.io.ResultReader;
import android.tools.traces.io.ResultWriter;
import android.tools.traces.monitors.PerfettoTraceMonitor;
import android.tools.traces.protolog.ProtoLogTrace;
import android.tracing.perfetto.DataSource;
import android.tracing.perfetto.DataSourceParams;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.protolog.ProtoLogConfigurationServiceImpl.ViewerConfigFileTracer;
import com.android.internal.protolog.common.IProtoLogGroup;
import com.android.internal.protolog.common.LogDataType;
import com.android.internal.protolog.common.LogLevel;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import perfetto.protos.Protolog;
import perfetto.protos.ProtologCommon;
import perfetto.protos.TraceOuterClass.Trace;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test class for {@link ProtoLogImpl}.
 */
@SuppressWarnings("ConstantConditions")
@Presubmit
@RunWith(JUnit4.class)
public class ProcessedPerfettoProtoLogImplTest {
    private static final String TEST_PROTOLOG_DATASOURCE_NAME = "test.android.protolog";
    private static final String MOCK_VIEWER_CONFIG_FILE = "my/mock/viewer/config/file.pb";
    private final File mTracingDirectory = InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getFilesDir();

    private static ProtoLogConfigurationService sProtoLogConfigurationService;
    private static ProtoLogDataSource sTestDataSource;
    private static PerfettoProtoLogImpl sProtoLog;
    private static Protolog.ProtoLogViewerConfig.Builder sViewerConfigBuilder;
    private static ProtoLogCacheUpdater sCacheUpdater;

    private static ProtoLogViewerConfigReader sReader;

    private static int sOriginalMaxInternedStringsSize;


    public ProcessedPerfettoProtoLogImplTest() throws IOException { }

    @BeforeClass
    public static void setUp() throws Exception {
        sViewerConfigBuilder = Protolog.ProtoLogViewerConfig.newBuilder()
                .addGroups(
                        Protolog.ProtoLogViewerConfig.Group.newBuilder()
                                .setId(1)
                                .setName(TestProtoLogGroup.TEST_GROUP.toString())
                                .setTag(TestProtoLogGroup.TEST_GROUP.getTag())
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(1)
                                .setMessage("My Test Debug Log Message %b")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_DEBUG)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:123")
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(2)
                                .setMessage("My Test Verbose Log Message %b")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_VERBOSE)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:342")
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(3)
                                .setMessage("My Test Warn Log Message %b")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_WARN)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:563")
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(4)
                                .setMessage("My Test Error Log Message %b")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_ERROR)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:156")
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(5)
                                .setMessage("My Test WTF Log Message %b")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_WTF)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:192")
                ).addMessages(
                        Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                                .setMessageId(6)
                                .setMessage("My Test String Arg Message %s")
                                .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_DEBUG)
                                .setGroupId(1)
                                .setLocation("com/test/MyTestClass.java:193")
                );

        ViewerConfigInputStreamProvider viewerConfigInputStreamProvider = Mockito.mock(
                ViewerConfigInputStreamProvider.class);
        Mockito.when(viewerConfigInputStreamProvider.getInputStream())
                .thenAnswer(it -> new AutoClosableProtoInputStream(
                        sViewerConfigBuilder.build().toByteArray()));

        sCacheUpdater = (instance) -> {};
        sReader = Mockito.spy(new ProtoLogViewerConfigReader(viewerConfigInputStreamProvider));
        sTestDataSource = new ProtoLogDataSource(TEST_PROTOLOG_DATASOURCE_NAME);
        DataSourceParams params =
                new DataSourceParams.Builder()
                        .setBufferExhaustedPolicy(
                                DataSourceParams
                                        .PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_DROP)
                        .build();
        sTestDataSource.register(params);
        busyWaitForDataSourceRegistration(TEST_PROTOLOG_DATASOURCE_NAME);

        final ViewerConfigFileTracer tracer = (dataSource, viewerConfigFilePath) -> {
            Utils.dumpViewerConfig(dataSource, () -> {
                if (!viewerConfigFilePath.equals(MOCK_VIEWER_CONFIG_FILE)) {
                    throw new RuntimeException(
                            "Unexpected viewer config file path provided");
                }
                return new AutoClosableProtoInputStream(sViewerConfigBuilder.build().toByteArray());
            });
        };
        sProtoLogConfigurationService =
                new ProtoLogConfigurationServiceImpl(sTestDataSource, tracer);

        sProtoLog = new ProcessedPerfettoProtoLogImpl(sTestDataSource,
                MOCK_VIEWER_CONFIG_FILE, viewerConfigInputStreamProvider, sReader,
                (instance) -> sCacheUpdater.update(instance), TestProtoLogGroup.values(),
                sProtoLogConfigurationService);
        sProtoLog.enable();

        sOriginalMaxInternedStringsSize =
                PerfettoProtoLogImpl.MAX_INTERNED_STRINGS_SIZE_BYTES_BEFORE_RESET;
    }

    @Before
    public void before() {
        Mockito.reset(sReader);

        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(false);
    }

    @After
    public void tearDown() {
        ProtoLogImpl.setSingleInstance(null);
        PerfettoProtoLogImpl.MAX_INTERNED_STRINGS_SIZE_BYTES_BEFORE_RESET =
                sOriginalMaxInternedStringsSize;
    }

    @Test
    public void isEnabled_returnsFalseByDefault() {
        assertFalse(sProtoLog.isProtoEnabled());
    }

    @Test
    public void isEnabled_returnsTrueAfterStart() {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(TEST_PROTOLOG_DATASOURCE_NAME)
                .build();
        try {
            traceMonitor.start();
            assertTrue(sProtoLog.isProtoEnabled());
        } finally {
            traceMonitor.stop(createTempWriter(mTracingDirectory));
        }
    }

    @Test
    public void isEnabled_returnsFalseAfterStop() {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(TEST_PROTOLOG_DATASOURCE_NAME)
                .build();
        try {
            traceMonitor.start();
            assertTrue(sProtoLog.isProtoEnabled());
        } finally {
            traceMonitor.stop(createTempWriter(mTracingDirectory));
        }

        assertFalse(sProtoLog.isProtoEnabled());
    }

    @Test
    public void defaultMode() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(false, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            // Shouldn't be logging anything except WTF unless explicitly requested in the group
            // override.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP, 2,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP, 3,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.ERROR, TestProtoLogGroup.TEST_GROUP, 4,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WTF, TestProtoLogGroup.TEST_GROUP, 5,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getLevel()).isEqualTo(LogLevel.WTF);
    }

    @Test
    public void respectsOverrideConfigs_defaultMode() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true,
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.DEBUG, true)),
                        TEST_PROTOLOG_DATASOURCE_NAME
                ).build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP, 2,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP, 3,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.ERROR, TestProtoLogGroup.TEST_GROUP, 4,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WTF, TestProtoLogGroup.TEST_GROUP, 5,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(4);
        Truth.assertThat(protolog.messages.get(0).getLevel()).isEqualTo(LogLevel.DEBUG);
        Truth.assertThat(protolog.messages.get(1).getLevel()).isEqualTo(LogLevel.WARN);
        Truth.assertThat(protolog.messages.get(2).getLevel()).isEqualTo(LogLevel.ERROR);
        Truth.assertThat(protolog.messages.get(3).getLevel()).isEqualTo(LogLevel.WTF);
    }

    @Test
    public void respectsOverrideConfigs_allEnabledMode() throws IOException {
        PerfettoTraceMonitor traceMonitor =
                PerfettoTraceMonitor.newBuilder().enableProtoLog(
                        true,
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.WARN, false)),
                        TEST_PROTOLOG_DATASOURCE_NAME
                    ).build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP, 2,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP, 3,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.ERROR, TestProtoLogGroup.TEST_GROUP, 4,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WTF, TestProtoLogGroup.TEST_GROUP, 5,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(3);
        Truth.assertThat(protolog.messages.get(0).getLevel()).isEqualTo(LogLevel.WARN);
        Truth.assertThat(protolog.messages.get(1).getLevel()).isEqualTo(LogLevel.ERROR);
        Truth.assertThat(protolog.messages.get(2).getLevel()).isEqualTo(LogLevel.WTF);
    }

    @Test
    public void respectsAllEnabledMode() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP, 2,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP, 3,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.ERROR, TestProtoLogGroup.TEST_GROUP, 4,
                    LogDataType.BOOLEAN, new Object[]{true});
            sProtoLog.log(LogLevel.WTF, TestProtoLogGroup.TEST_GROUP, 5,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(5);
        Truth.assertThat(protolog.messages.get(0).getLevel()).isEqualTo(LogLevel.DEBUG);
        Truth.assertThat(protolog.messages.get(1).getLevel()).isEqualTo(LogLevel.VERBOSE);
        Truth.assertThat(protolog.messages.get(2).getLevel()).isEqualTo(LogLevel.WARN);
        Truth.assertThat(protolog.messages.get(3).getLevel()).isEqualTo(LogLevel.ERROR);
        Truth.assertThat(protolog.messages.get(4).getLevel()).isEqualTo(LogLevel.WTF);
    }

    @Test
    public void log_logcatEnabled() {
        when(sReader.getViewerString(anyLong())).thenReturn("test %b %d %% 0x%x %s %f");
        PerfettoProtoLogImpl implSpy = Mockito.spy(sProtoLog);
        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(true);

        implSpy.log(
                LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, 1234, 4321,
                new Object[]{true, 10000, 30000, "test", 0.000003});

        verify(implSpy).passToLogcat(eq(TestProtoLogGroup.TEST_GROUP.getTag()), eq(
                LogLevel.INFO),
                eq("test true 10000 % 0x7530 test 3.0E-6"));
        verify(sReader).getViewerString(eq(1234L));
    }

    @Test
    public void log_logcatEnabledInvalidMessage() {
        when(sReader.getViewerString(anyLong())).thenReturn("test %b %d %% %x %s %f");
        PerfettoProtoLogImpl implSpy = Mockito.spy(sProtoLog);
        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(true);

        implSpy.log(
                LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, 1234, 4321,
                new Object[]{true, 10000, 0.0001, 0.00002, "test"});

        verify(implSpy).passToLogcat(eq(TestProtoLogGroup.TEST_GROUP.getTag()), eq(
                LogLevel.INFO),
                eq("FORMAT_ERROR \"test %b %d %% %x %s %f\", "
                        + "args=(true, 10000, 1.0E-4, 2.0E-5, test)"));
        verify(sReader).getViewerString(eq(1234L));
    }

    @Test
    public void log_logcatEnabledNoMessageThrows() {
        when(sReader.getViewerString(anyLong())).thenReturn(null);
        PerfettoProtoLogImpl implSpy = Mockito.spy(sProtoLog);
        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(true);

        var assertion = assertThrows(RuntimeException.class, () ->
                implSpy.log(LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, 1234, 4321,
                    new Object[]{5}));
        Truth.assertThat(assertion).hasMessageThat()
                .contains("Failed to decode message for logcat");
    }

    @Test
    public void log_logcatDisabled() {
        when(sReader.getViewerString(anyLong())).thenReturn("test %d");
        PerfettoProtoLogImpl implSpy = Mockito.spy(sProtoLog);
        TestProtoLogGroup.TEST_GROUP.setLogToLogcat(false);

        implSpy.log(
                LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, 1234, 4321,
                new Object[]{5});

        verify(implSpy, never()).passToLogcat(any(), any(), any());
        verify(sReader, never()).getViewerString(anyLong());
    }

    @Test
    public void log_protoEnabled() throws Exception {
        final long messageHash = addMessageToConfig(
                ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_INFO,
                "My test message :: %s, %d, %o, %x, %f, %e, %g, %b");

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        long before;
        long after;
        try {
            assertFalse(sProtoLog.isProtoEnabled());
            traceMonitor.start();
            assertTrue(sProtoLog.isProtoEnabled());

            before = SystemClock.elapsedRealtimeNanos();
            sProtoLog.log(
                    LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, messageHash,
                    0b1110101001010100,
                    new Object[]{"test", 1, 2, 3, 0.4, 0.5, 0.6, true});
            after = SystemClock.elapsedRealtimeNanos();
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getTimestamp().getElapsedNanos())
                .isAtLeast(before);
        Truth.assertThat(protolog.messages.getFirst().getTimestamp().getElapsedNanos())
                .isAtMost(after);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo(
                        "My test message :: test, 1, 2, 3, 0.400000, 5.000000e-01, 0.6, true");
    }

    @Test
    public void log_noProcessing() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        long before;
        long after;
        try {
            traceMonitor.start();
            assertTrue(sProtoLog.isProtoEnabled());

            before = SystemClock.elapsedRealtimeNanos();
            sProtoLog.log(
                    LogLevel.INFO, TestProtoLogGroup.TEST_GROUP,
                    "My test message :: %s, %d, %x, %f, %b",
                    "test", 1, 3, 0.4, true);
            after = SystemClock.elapsedRealtimeNanos();
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getTimestamp().getElapsedNanos())
                .isAtLeast(before);
        Truth.assertThat(protolog.messages.getFirst().getTimestamp().getElapsedNanos())
                .isAtMost(after);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("My test message :: test, 1, 3, 0.400000, true");
    }

    @Test
    public  void supportsLocationInformation() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.get(0).getLocation())
                .isEqualTo("com/test/MyTestClass.java:123");
    }

    private long addMessageToConfig(ProtologCommon.ProtoLogLevel logLevel, String message) {
        final long messageId = new Random().nextLong();
        sViewerConfigBuilder.addMessages(Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                .setMessageId(messageId)
                .setMessage(message)
                .setLevel(logLevel)
                .setGroupId(1)
        );

        return messageId;
    }

    @Test
    public void log_invalidParamsMask() {
        final long messageHash = addMessageToConfig(
                ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_INFO,
                "My test message :: %s, %d, %f, %b");
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(
                    LogLevel.INFO, TestProtoLogGroup.TEST_GROUP, messageHash,
                    0b01100100,
                    new Object[]{"test", 1, 0.1, true});
        } finally {
            traceMonitor.stop(writer);
        }
    }

    @Test
    public void log_protoDisabled() throws Exception {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(false, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    0b11, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).isEmpty();
    }

    @Test
    public void stackTraceTrimmed() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true,
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.DEBUG,
                                true)),
                        TEST_PROTOLOG_DATASOURCE_NAME
                ).build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            ProtoLogImpl.setSingleInstance(sProtoLog);
            ProtoLogImpl.d(TestProtoLogGroup.TEST_GROUP, 1,
                    0b11, true);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        String stacktrace = protolog.messages.getFirst().getStacktrace();
        Truth.assertThat(stacktrace)
                .doesNotContain(PerfettoProtoLogImpl.class.getSimpleName() + ".java");
        Truth.assertThat(stacktrace).doesNotContain(DataSource.class.getSimpleName() + ".java");
        Truth.assertThat(stacktrace)
                .doesNotContain(ProtoLogImpl.class.getSimpleName() + ".java");
        Truth.assertThat(stacktrace)
                .contains(ProcessedPerfettoProtoLogImplTest.class.getSimpleName());
        Truth.assertThat(stacktrace).contains("stackTraceTrimmed");
    }

    @Test
    public void cacheIsUpdatedWhenTracesStartAndStop() {
        final AtomicInteger cacheUpdateCallCount = new AtomicInteger(0);
        sCacheUpdater = (instance) -> cacheUpdateCallCount.incrementAndGet();

        PerfettoTraceMonitor traceMonitor1 = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true,
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.WARN,
                                false)), TEST_PROTOLOG_DATASOURCE_NAME
                ).build();

        PerfettoTraceMonitor traceMonitor2 =
                PerfettoTraceMonitor.newBuilder().enableProtoLog(true,
                                List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                        TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.DEBUG,
                                        false)), TEST_PROTOLOG_DATASOURCE_NAME)
                        .build();

        Truth.assertThat(cacheUpdateCallCount.get()).isEqualTo(0);

        try {
            traceMonitor1.start();

            Truth.assertThat(cacheUpdateCallCount.get()).isEqualTo(1);

            try {
                traceMonitor2.start();

                Truth.assertThat(cacheUpdateCallCount.get()).isEqualTo(2);
            } finally {
                traceMonitor2.stop(createTempWriter(mTracingDirectory));
            }

            Truth.assertThat(cacheUpdateCallCount.get()).isEqualTo(3);

        } finally {
            traceMonitor1.stop(createTempWriter(mTracingDirectory));
        }

        Truth.assertThat(cacheUpdateCallCount.get()).isEqualTo(4);
    }

    @Test
    public void isEnabledUpdatesBasedOnRunningTraces() {
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.DEBUG))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.VERBOSE))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.INFO))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WARN))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.ERROR))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WTF)).isFalse();

        PerfettoTraceMonitor traceMonitor1 =
                PerfettoTraceMonitor.newBuilder().enableProtoLog(true,
                                List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                        TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.WARN,
                                        false)), TEST_PROTOLOG_DATASOURCE_NAME)
                        .build();

        PerfettoTraceMonitor traceMonitor2 =
                PerfettoTraceMonitor.newBuilder().enableProtoLog(true,
                                List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                        TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.DEBUG,
                                        false)), TEST_PROTOLOG_DATASOURCE_NAME)
                        .build();

        try {
            traceMonitor1.start();

            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.DEBUG))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.VERBOSE))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.INFO))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WARN))
                    .isTrue();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.ERROR))
                    .isTrue();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WTF))
                    .isTrue();

            try {
                traceMonitor2.start();

                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.DEBUG))
                        .isTrue();
                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP,
                        LogLevel.VERBOSE)).isFalse();
                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.INFO))
                        .isTrue();
                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WARN))
                        .isTrue();
                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.ERROR))
                        .isTrue();
                Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WTF))
                        .isTrue();
            } finally {
                traceMonitor2.stop(createTempWriter(mTracingDirectory));
            }

            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.DEBUG))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.VERBOSE))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.INFO))
                    .isFalse();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WARN))
                    .isTrue();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.ERROR))
                    .isTrue();
            Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WTF))
                    .isTrue();
        } finally {
            traceMonitor1.stop(createTempWriter(mTracingDirectory));
        }

        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.DEBUG))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.VERBOSE))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.INFO))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WARN))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.ERROR))
                .isFalse();
        Truth.assertThat(sProtoLog.isEnabled(TestProtoLogGroup.TEST_GROUP, LogLevel.WTF))
                .isFalse();
    }

    @Test
    public void supportsNullString() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "My test null string: %s", (Object) null);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("My test null string: null");
    }

    @Test
    public void supportNullParams() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "My null args: %d, %f, %b", null, null, null);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("My null args: 0, 0.000000, false");
    }

    @Test
    public void handlesConcurrentTracingSessions() throws IOException {
        PerfettoTraceMonitor traceMonitor1 = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        PerfettoTraceMonitor traceMonitor2 = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        final var writer2 = createTempWriter(mTracingDirectory);
        try {
            traceMonitor1.start();
            traceMonitor2.start();

            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor1.stop(writer);
            traceMonitor2.stop(writer2);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protologFromMonitor1 = reader.readProtoLogTrace();

        final ResultReader reader2 = new ResultReader(writer2.write());
        final ProtoLogTrace protologFromMonitor2 = reader2.readProtoLogTrace();

        Truth.assertThat(protologFromMonitor1.messages).hasSize(1);
        Truth.assertThat(protologFromMonitor1.messages.getFirst().getMessage())
                .isEqualTo("My Test Debug Log Message true");

        Truth.assertThat(protologFromMonitor2.messages).hasSize(1);
        Truth.assertThat(protologFromMonitor2.messages.getFirst().getMessage())
                .isEqualTo("My Test Debug Log Message true");
    }

    @Test
    public void usesDefaultLogFromLevel() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(LogLevel.WARN, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "This message should not be logged");
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP,
                    "This message should be logged %d", 123);
            sProtoLog.log(LogLevel.ERROR, TestProtoLogGroup.TEST_GROUP,
                    "This message should also be logged %d", 567);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(2);

        Truth.assertThat(protolog.messages.get(0).getLevel())
                .isEqualTo(LogLevel.WARN);
        Truth.assertThat(protolog.messages.get(0).getMessage())
                .isEqualTo("This message should be logged 123");

        Truth.assertThat(protolog.messages.get(1).getLevel())
                .isEqualTo(LogLevel.ERROR);
        Truth.assertThat(protolog.messages.get(1).getMessage())
                .isEqualTo("This message should also be logged 567");
    }

    @Test
    public void verboseLowerThanDebugLogLevelDefaultLevel() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(LogLevel.DEBUG, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP,
                    "This message should not be logged");
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "This message should be logged %d", 123);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);

        Truth.assertThat(protolog.messages.getFirst().getLevel())
                .isEqualTo(LogLevel.DEBUG);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("This message should be logged 123");
    }

    @Test
    public void verboseLowerThanDebugLogLevel() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(LogLevel.VERBOSE, List.of(
                        new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.name(), LogLevel.DEBUG, false)
                ), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            sProtoLog.log(LogLevel.VERBOSE, TestProtoLogGroup.TEST_GROUP,
                    "This message should not be logged");
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    "This message should be logged %d", 123);
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);

        Truth.assertThat(protolog.messages.getFirst().getLevel())
                .isEqualTo(LogLevel.DEBUG);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("This message should be logged 123");
    }

    @Test
    public void enablesLogGroupAfterLoadingConfig() {
        sProtoLog.stopLoggingToLogcat(
                new String[] { TestProtoLogGroup.TEST_GROUP.name() }, (msg) -> {});
        Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isFalse();

        doAnswer((Answer<Void>) invocation -> {
            // logToLogcat is still false before we laod the viewer config
            Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isFalse();
            return null;
        }).when(sReader).unloadViewerConfig(any(), any());

        sProtoLog.startLoggingToLogcat(
                new String[] { TestProtoLogGroup.TEST_GROUP.name() }, (msg) -> {});
        Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isTrue();
    }

    @Test
    public void disablesLogGroupBeforeUnloadingConfig() {
        sProtoLog.startLoggingToLogcat(
                new String[] { TestProtoLogGroup.TEST_GROUP.name() }, (msg) -> {});
        Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isTrue();

        doAnswer((Answer<Void>) invocation -> {
            // Already set logToLogcat to false by the time we unload the config
            Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isFalse();
            return null;
        }).when(sReader).unloadViewerConfig(any(), any());
        sProtoLog.stopLoggingToLogcat(
                new String[] { TestProtoLogGroup.TEST_GROUP.name() }, (msg) -> {});
        Truth.assertThat(TestProtoLogGroup.TEST_GROUP.isLogToLogcat()).isFalse();
    }

    @Test
    public void messagesLoggedWhenProtoDisabledAreNotTraced() throws IOException {
        assertFalse("ProtoLog should be disabled before starting any trace",
                sProtoLog.isProtoEnabled());

        // Log a message when ProtoLog is disabled.
        sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                LogDataType.BOOLEAN, new Object[]{false}); // "false" to distinguish

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            assertTrue("ProtoLog should be enabled after starting the trace.",
                    sProtoLog.isProtoEnabled());

            // Log a message when ProtoLog is enabled.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true}); // "true" to distinguish
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(1);
        Truth.assertThat(protolog.messages.getFirst().getMessage())
                .isEqualTo("My Test Debug Log Message true"); // Only the "true" message
    }


    @Test
    public void messagesInQueueBeforeNewSessionActivationAreNotTracedInNewSession()
            throws Exception {
        final int numOldMessages = 2;
        final int numNewMessages = 2;

        final CountDownLatch executorBlockedLatch = new CountDownLatch(1);
        final CountDownLatch releaseExecutorLatch = new CountDownLatch(1);

        // Submit task to block the executor.
        sProtoLog.mSingleThreadedExecutor.execute(() -> {
            executorBlockedLatch.countDown();
            try {
                if (!releaseExecutorLatch.await(60, TimeUnit.SECONDS)) {
                    Truth.assertWithMessage("Timeout waiting for releaseExecutorLatch").fail();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task_Block interrupted: " + e.getMessage());
            }
        });
        assertTrue("Executor did not block in time",
                executorBlockedLatch.await(5, TimeUnit.SECONDS));

        PerfettoTraceMonitor traceMonitor0 = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .setUniqueSessionName("test_session0")
                .build();
        traceMonitor0.start();
        busyWaitTracingSessionExists("test_session0");

        // Log "old" messages. These are queued before we start the second tracing session.
        for (int i = 0; i < numOldMessages; i++) {
            sProtoLog.log(LogLevel.WARN, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{false}); // Task_LogOld_i
        }

        // At this point, queue on backgroundService is roughly:
        // [Task_Block(paused), Task_Activate0, Task_LogOld1, ..., Task_Deactivate0]

        // Start the actual trace session to inspect (traceMonitor1). Queues Task_Activate1.
        PerfettoTraceMonitor traceMonitor1 = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .setUniqueSessionName("test_session1")
                .build();
        traceMonitor1.start();
        busyWaitTracingSessionExists("test_session1");

        // Log "new" messages. These are for traceMonitor1.
        for (int i = 0; i < numNewMessages; i++) {
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
        }

        // Unblock the executor.
        releaseExecutorLatch.countDown();

        var writer0 = new ResultWriter()
                .withName("scenario0")
                .withOutputDir(mTracingDirectory).setRunComplete();
        traceMonitor0.stop(writer0);

        var writer1 = new ResultWriter()
                .withName("scenario1")
                .withOutputDir(mTracingDirectory).setRunComplete();
        traceMonitor1.stop(writer1);
        busyWaitTracingSessionDoesntExist("test_session1");

        final ResultReader reader0 = new ResultReader(writer0.write());
        final ProtoLogTrace protolog0 = reader0.readProtoLogTrace();

        Truth.assertThat(protolog0.messages).hasSize(numOldMessages + numNewMessages);

        final ResultReader reader1 = new ResultReader(writer1.write());
        final ProtoLogTrace protolog1 = reader1.readProtoLogTrace();

        Truth.assertThat(protolog1.messages).hasSize(numNewMessages);
        for (int i = 0; i < numNewMessages; i++) {
            Truth.assertThat(protolog1.messages.get(i).getLevel()).isEqualTo(LogLevel.DEBUG);
            Truth.assertThat(protolog1.messages.get(i).getMessage())
                    .isEqualTo("My Test Debug Log Message true");
        }
        // Ensure no messages (from the "old" batch) are present.
        for (var msg : protolog1.messages) {
            Truth.assertThat(msg.getLevel()).isNotEqualTo(LogLevel.VERBOSE);
        }
    }

    @Test
    public void processesAllPendingMessagesBeforeTraceStop()
            throws IOException, InterruptedException {
        // large number of messages to log to stress the queue
        final int numMessages = 1000;
        final CountDownLatch processingHasStartedLatch = new CountDownLatch(1);
        final CountDownLatch allowProcessingToContinueLatch = new CountDownLatch(1);
        final AtomicBoolean blockingTaskStartedExecution = new AtomicBoolean(false);

        // Configure trace monitor to enable all log levels for the test data source.
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            assertTrue("ProtoLog should be enabled after starting the trace.",
                    sProtoLog.isProtoEnabled());

            // Submit a task that will block the executor queue.
            sProtoLog.mSingleThreadedExecutor.execute(() -> {
                try {
                    blockingTaskStartedExecution.set(true);
                    processingHasStartedLatch.countDown(); // Signal that this task has started
                    // Wait until the main test thread signals to continue
                    if (!allowProcessingToContinueLatch.await(60, TimeUnit.SECONDS)) {
                        // Fail fast if timeout occurs, to avoid test hanging indefinitely
                        Truth.assertWithMessage(
                                "Timeout waiting for allowProcessingToContinueLatch")
                                .fail();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Blocking task was interrupted: " + e.getMessage());
                }
            });

            // Wait for the blocking task to actually start executing on the background thread.
            // This ensures it's at the head of the executor's queue before we add more tasks.
            assertTrue("Blocking task did not start execution in time.",
                    processingHasStartedLatch.await(5, TimeUnit.SECONDS));

            // Now, submit all the log messages. They will be queued behind the blocking task.
            for (int i = 0; i < numMessages; i++) {
                sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                        LogDataType.BOOLEAN, new Object[]{true});
            }

            // Assert that the blocking task is still active (i.e., waiting on the latch),
            // which implies the subsequently submitted log messages are still queued.
            assertTrue("Blocking task should have started execution.",
                    blockingTaskStartedExecution.get());
            Truth.assertWithMessage(
                    "allowProcessingToContinueLatch should not have been counted down yet.")
                    .that(allowProcessingToContinueLatch.getCount())
                    .isEqualTo(1L);

            // Allow the blocking task to complete. This will allow the executor
            // to start processing the queued log messages.
            allowProcessingToContinueLatch.countDown();

            // Stop tracing immediately. The implementation should wait for the
            // mSingleThreadedExecutor to process all queued messages (including the
            // now-unblocked first task and all subsequent log messages).
        } finally {
            // Ensure the latch is always counted down if an exception occurred before stop,
            // or if the test is ending, to prevent the background thread from hanging.
            if (allowProcessingToContinueLatch.getCount() > 0) {
                allowProcessingToContinueLatch.countDown();
            }
            traceMonitor.stop(writer);
        }

        // Verify that all messages were written to the trace.
        final ResultReader reader = new ResultReader(writer.write());
        final ProtoLogTrace protolog = reader.readProtoLogTrace();

        Truth.assertThat(protolog.messages).hasSize(numMessages);
        for (int i = 0; i < numMessages; i++) {
            Truth.assertThat(protolog.messages.get(i).getLevel()).isEqualTo(LogLevel.DEBUG);
            Truth.assertThat(protolog.messages.get(i).getMessage())
                    .isEqualTo("My Test Debug Log Message true");
        }
    }

    @Test
    public void snapshotsMutableArgumentsOnCallingThread() throws Exception {
        // This test ensures that the mutable argument is snapshotted on the calling thread,
        // and not on the background thread, otherwise it might be modified concurrently and this
        // might lead to a crash.

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final String initialValue = "InitialValue";
        final String modifiedValue = "ModifiedValueAfterLogCall";
        final StringBuilder mutableArg = new StringBuilder(initialValue);
        final String logMessageFormat = "Test with mutable arg: %s";

        final ExecutorService backgroundHandler = sProtoLog.mSingleThreadedExecutor;

        // Task to pause the background thread.
        final CountDownLatch backgroundThreadPausedLatch = new CountDownLatch(1);
        backgroundHandler.execute(() -> {
            try {
                backgroundThreadPausedLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Truth.assertWithMessage(
                        "Background thread interrupted while waiting on pause latch.")
                        .fail();
            }
        });

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            assertTrue("ProtoLog tracing should be enabled", sProtoLog.isProtoEnabled());

            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP,
                    logMessageFormat, mutableArg);

            mutableArg.replace(0, mutableArg.length(), modifiedValue);

            // Unpause the background thread.
            backgroundThreadPausedLatch.countDown();
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader resultReader = new ResultReader(writer.write());
        final ProtoLogTrace protologTrace = resultReader.readProtoLogTrace();

        Truth.assertThat(protologTrace.messages).hasSize(1);
        final String expectedLoggedMessage = String.format(logMessageFormat, initialValue);
        Truth.assertThat(protologTrace.messages.getFirst().getMessage())
                .isEqualTo(expectedLoggedMessage);
    }

    @Test
    public void incrementalStateFlagSetForStackTrace() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true,
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TestProtoLogGroup.TEST_GROUP.toString(), LogLevel.DEBUG,
                                true)), // enable stacktrace
                        TEST_PROTOLOG_DATASOURCE_NAME
                ).build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            // Log a message with a stacktrace but no string arguments. The stacktrace is the only
            // interned data.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 1,
                    LogDataType.BOOLEAN, new Object[]{true});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final var traceBytes = reader.readBytes(TraceType.PERFETTO, Tag.ALL);
        final var trace = Trace.parseFrom(traceBytes);
        final var protoLogMessagePackets = trace.getPacketList().stream()
                .filter(it -> it.hasProtologMessage()
                        && it.getProtologMessage().getMessageId() == 1)
                .toList();

        Truth.assertThat(protoLogMessagePackets).hasSize(1);
        final var sequenceFlag = protoLogMessagePackets.getFirst().getSequenceFlags();
        final var incrementalStateFlag = SEQ_NEEDS_INCREMENTAL_STATE.getNumber();
        Truth.assertWithMessage("SEQ_NEEDS_INCREMENTAL_STATE flag should be set")
                .that(sequenceFlag & incrementalStateFlag).isEqualTo(incrementalStateFlag);
    }

    @Test
    public void incrementalStateFlagSetForStringArg() throws IOException {
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            // Log a message with a string argument. The argument is the only
            // interned data.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 6,
                    LogDataType.STRING, new Object[]{"test_string"});
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final var traceBytes = reader.readBytes(TraceType.PERFETTO, Tag.ALL);
        final var trace = Trace.parseFrom(traceBytes);
        final var protoLogMessagePackets = trace.getPacketList().stream()
                .filter(it -> it.hasProtologMessage()
                        && it.getProtologMessage().getMessageId() == 6)
                .toList();

        Truth.assertThat(protoLogMessagePackets).hasSize(1);
        final var sequenceFlag = protoLogMessagePackets.getFirst().getSequenceFlags();
        final var incrementalStateFlag = SEQ_NEEDS_INCREMENTAL_STATE.getNumber();
        Truth.assertWithMessage("SEQ_NEEDS_INCREMENTAL_STATE flag should be set")
                .that(sequenceFlag & incrementalStateFlag).isEqualTo(incrementalStateFlag);
    }

    @Test
    public void incrementalStateResetWhenStringsTooLarge() throws IOException {
        assumeTrue(android.tracing.Flags.protologAutoClearIncrementalState());
        PerfettoProtoLogImpl.MAX_INTERNED_STRINGS_SIZE_BYTES_BEFORE_RESET = 10;

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(true, List.of(), TEST_PROTOLOG_DATASOURCE_NAME)
                .build();

        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();

            // Log a 5-char string, should not reset yet.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 6,
                    LogDataType.STRING, new Object[]{"12345"});

            // Log another 4-char string, should not reset yet (5 + 4 = 9).
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, 6,
                    LogDataType.STRING, new Object[]{"abcd"});

            // Log a message string, should trigger reset because 9 + message_len > 10.
            sProtoLog.log(LogLevel.DEBUG, TestProtoLogGroup.TEST_GROUP, "message");
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        final var traceBytes = reader.readBytes(TraceType.PERFETTO, Tag.ALL);
        final var trace = Trace.parseFrom(traceBytes);

        final var clearPackets = trace.getPacketList().stream()
                .filter(it -> (it.getSequenceFlags()
                        & SEQ_INCREMENTAL_STATE_CLEARED.getNumber()) != 0)
                .toList();
        Truth.assertThat(clearPackets).hasSize(1);
    }

    private enum TestProtoLogGroup implements IProtoLogGroup {
        TEST_GROUP(true, false, "TEST_TAG");

        private final boolean mEnabled;
        private volatile boolean mLogToLogcat;
        private final String mTag;

        /**
         * @param enabled     set to false to exclude all log statements for this group from
         *                    compilation,
         *                    they will not be available in runtime.
         * @param logToLogcat enable text logging for the group
         * @param tag         name of the source of the logged message
         */
        TestProtoLogGroup(boolean enabled, boolean logToLogcat, String tag) {
            this.mEnabled = enabled;
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
