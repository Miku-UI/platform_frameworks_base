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

import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.GROUPS;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.Group.ID;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.Group.NAME;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.Group.TAG;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.MESSAGES;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.MessageData.GROUP_ID;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.MessageData.LEVEL;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.MessageData.MESSAGE;
import static android.internal.perfetto.protos.Protolog.ProtoLogViewerConfig.MessageData.MESSAGE_ID;
import static android.tools.traces.Utils.busyWaitForDataSourceRegistration;
import static android.tracing.perfetto.TestUtils.createTempWriter;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.tools.traces.io.ResultReader;
import android.tools.traces.monitors.PerfettoTraceMonitor;
import android.tools.traces.protolog.ProtoLogMessage;
import android.tools.traces.protolog.ProtoLogTrace;
import android.util.proto.ProtoOutputStream;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.protolog.common.IProtoLogGroup;
import com.android.internal.protolog.common.LogDataType;
import com.android.internal.protolog.common.LogLevel;

import com.google.common.truth.Truth;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ProtoLogNativeTest {

    private static final String PROTOLOG_DATA_SOURCE_NAME = "android.protolog";
    private static final String PROTOLOG_VIEWER_DATA_SOURCE_NAME = "android.protolog.viewer";
    private final File mTracingDirectory =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
    private static final IProtoLogGroup TEST_GROUP = new IProtoLogGroup() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isLogToLogcat() {
            return false;
        }

        @Override
        public String getTag() {
            return "ProtoLogNativeTestGroup";
        }

        @Override
        public void setLogToLogcat(boolean logToLogcat) {

        }

        @Override
        public String name() {
            return "ProtoLogNativeTestGroup";
        }

        @Override
        public int getId() {
            return 123;
        }
    };

    private static final IProtoLogGroup TEST_GROUP_2 = new IProtoLogGroup() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isLogToLogcat() {
            return false;
        }

        @Override
        public String getTag() {
            return "ProtoLogNativeTestGroup2";
        }

        @Override
        public void setLogToLogcat(boolean logToLogcat) {

        }

        @Override
        public String name() {
            return "ProtoLogNativeTestGroup2";
        }

        @Override
        public int getId() {
            return 456;
        }
    };

    @BeforeClass
    public static void setUpClass() {
        ProtoLogNative.init();
    }

    @Test
    public void testLogString_simpleMessage_isLogged() throws IOException {
        final String simpleMessage = "Simple native message";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, "TEST_TAG", simpleMessage, args);
        });

        assertWithMessage("Trace should contain the simple message")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(simpleMessage);
    }

    @Test
    public void testLogString_formattedMessage_isLogged() throws IOException {
        final String testMessageFmt = "Native test message: %s, count: %d";
        final String testStringArg = "Tango";
        final int testIntArg = 77;
        final String expectedMessage = String.format(testMessageFmt, testStringArg, testIntArg);

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {testStringArg, testIntArg};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_INFO, "TEST_TAG", testMessageFmt, args);
        });

        assertWithMessage("Trace should contain the formatted message")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogString_allArgumentTypes_areLogged() throws IOException {
        final String format = "Test message %s %d %f %b";
        final String expectedMessage = "Test message test 12345 3.140000 false";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {"test", 12345L, 3.14f, false};
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, "TEST_TAG", format, args);
        });

        assertWithMessage("Trace should contain the message with all argument types")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogNumbers() throws IOException {
        final String format = "Test message %d %d %d %d %d %d %d %d %d %d %d";
        final String expectedMessage = "Test message 12345 0 -12345 12345 0 -12345 "
                + "9223372036854775807 -9223372036854775808 2147483647 -2147483648 0";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = { 12345L, 0L, -12345L, 12345, 0, -12345, Long.MAX_VALUE, Long.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MIN_VALUE, null };
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, "TEST_TAG", format, args);
        });

        assertWithMessage("Trace should contain the message with all argument")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogFloats() throws IOException {
        final String format = "Test message %f %f %f %f %f %f %f %f %f %f %f %f %f";
        final String expectedMessage = "Test message 3.140000 0.000000 -3.140000 3.140000 "
                + "0.000000 -3.140000 3402823466385288598117041834845 0.000000 "
                + "-340282346638528859811704183484 1797693134862315708145274237317 0.000000 "
                + "-179769313486231570814527423731 0.000000";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = { 3.14f, 0f, -3.14f, 3.14d, 0d, -3.14d, Float.MAX_VALUE,
                    Float.MIN_VALUE, -1 * Float.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE,
                    -1 * Double.MAX_VALUE, null };
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, "TEST_TAG", format, args);
        });

        assertWithMessage("Trace should contain the message with all argument")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogMoreThan16Args() throws IOException {
        final String format =
                "Test message %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d";
        final String expectedMessage =
                "Test message 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20";

        ProtoLogTrace trace =
                logAndReadTrace(
                        () -> {
                            Object[] args = {
                                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                                20
                            };
                            ProtoLogNative.log(
                                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, "TEST_TAG", format, args);
                        });

        assertWithMessage("Trace should contain the message with all argument")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogHash_withViewerConfig_isDecoded() throws IOException {
        final long messageHash = 0x12345678ABCDL;
        final String messageFormat = "Hashed message: %s, value: %d";
        final String stringArg = "test string";
        final int intArg = 123;
        final String expectedMessage = String.format(messageFormat, stringArg, intArg);

        final byte[] viewerConfig;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ProtoOutputStream po = new ProtoOutputStream(os);

            final long groupToken = po.start(GROUPS);
            po.write(ID, TEST_GROUP.getId());
            po.write(NAME, TEST_GROUP.name());
            po.write(TAG, TEST_GROUP.getTag());
            po.end(groupToken);

            final long messageToken = po.start(MESSAGES);
            po.write(MESSAGE_ID, messageHash);
            po.write(MESSAGE, messageFormat);
            po.write(LEVEL, ProtoLogNative.PROTO_LOG_LEVEL_DEBUG);
            po.write(GROUP_ID, TEST_GROUP.getId());
            po.end(messageToken);

            po.flush();
            viewerConfig = os.toByteArray();
        }

        ProtoLogViewerConfigDataSource viewerDs = new ProtoLogViewerConfigDataSource();
        viewerDs.setViewerConfig(viewerConfig);
        android.tracing.perfetto.Producer.init(android.tracing.perfetto.InitArguments.DEFAULTS);
        viewerDs.register(new android.tracing.perfetto.DataSourceParams.Builder().build());
        busyWaitForDataSourceRegistration(PROTOLOG_VIEWER_DATA_SOURCE_NAME);

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {stringArg, intArg};
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_WARN, TEST_GROUP.name(), messageHash,
                    generateProtoLogToolMask(messageFormat, args), args);
            viewerDs.flush();
        }, traceMonitor);

        Truth.assertThat(
                trace.messages.stream()
                        .map(ProtoLogMessage::getMessage)
                        .collect(Collectors.toSet())
        ).contains(expectedMessage);

        assertWithMessage("Trace should contain the decoded hashed message, found: ["
                + trace.messages.stream()
                .map(ProtoLogMessage::getMessage)
                .collect(Collectors.joining(", "))
                + "] expected \"" + expectedMessage + "\""
        ).that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogString_withNullArgument_isHandledGracefully() throws IOException {
        final String format = "Message with null: %s";
        final String expectedMessage = "Message with null: null";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {null};
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_ERROR, "TEST_TAG", format, args);
        });

        assertWithMessage("Trace should handle null arguments gracefully")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testLogString_fewerArgumentsThanFormatters() {
        final String format = "Too few args: %s %d";

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> logAndReadTrace(() -> {
                    Object[] args = {"lonely-string"};
                    ProtoLogNative.log(
                            ProtoLogNative.PROTO_LOG_LEVEL_WARN, "TEST_TAG", format, args);
                })
        );

        Truth.assertThat(thrown).hasMessageThat().contains("Too few arguments");
    }

    @Test
    public void testLogString_tooManyArguments() {
        final String format = "Too many args: %s %d";

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> logAndReadTrace(() -> {
                    Object[] args = { "string", 123, 1.2f };
                    ProtoLogNative.log(
                            ProtoLogNative.PROTO_LOG_LEVEL_WARN, "TEST_TAG", format, args);
                }));

        Truth.assertThat(thrown).hasMessageThat().contains("Too many arguments");
    }

    @Test
    public void testLogString_argumentTypeMismatch() {
        final String format = "Type mismatch: %d";
        final String stringArg = "I am not a number";

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> logAndReadTrace(() -> {
                    Object[] args = {stringArg};
                    ProtoLogNative.log(
                            ProtoLogNative.PROTO_LOG_LEVEL_WARN, "TEST_TAG", format, args);
                })
        );

        Truth.assertThat(thrown).hasMessageThat()
                .contains("Cannot apply argument at index 0 to ProtoLog format string");
    }

    @Test
    public void testLogString_multipleArgumentTypeMismatches_reportsFirstError() {
        final String format = "Mismatch 1: %d, Mismatch 2: %f";
        final String stringArg = "I am not a number";
        final boolean boolArg = true; // Mismatch for %f

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> logAndReadTrace(() -> {
                    Object[] args = {stringArg, boolArg};
                    ProtoLogNative.log(
                            ProtoLogNative.PROTO_LOG_LEVEL_WARN, "TEST_TAG", format, args);
                })
        );

        // Verifies that only the first error is reported, and the second mismatch is ignored.
        Truth.assertThat(thrown).hasMessageThat()
                .contains("Cannot apply argument at index 0 to ProtoLog format string");
    }

    @Test
    public void testLogString_invalidFormatSpecifier_isLogged() throws IOException {
        final String format = "Invalid specifier: %z";
        final String expectedMessage = "Invalid specifier: %z";

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(ProtoLogNative.PROTO_LOG_LEVEL_WARN, "TEST_TAG", format, args);
        });

        assertWithMessage("Trace should contain the message with the invalid specifier intact")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(expectedMessage);
    }

    @Test
    public void testRespectsOverrideConfigs_enabled() throws IOException {
        final String testMessage = "Override enabled message";
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        false, // allEnabled = false
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TEST_GROUP.name(), LogLevel.DEBUG, true)),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), testMessage, args);
        }, traceMonitor);

        assertWithMessage("Trace should contain the message when group is explicitly enabled")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(testMessage);
    }

    @Test
    public void testRespectsOverrideConfigs_disabled() throws IOException {
        final String testMessage = "Override disabled message";
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true, // allEnabled = true
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TEST_GROUP.name(), LogLevel.WTF, false)),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), testMessage, args);
        }, traceMonitor);

        assertWithMessage("Trace should not contain the message when group is explicitly disabled")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .doesNotContain(testMessage);
    }

    @Test
    public void testRespectsAllEnabled_true() throws IOException {
        final String debugMessage = "All enabled debug message";
        final String verboseMessage = "All enabled verbose message";
        final String infoMessage = "All enabled info message";
        final String warnMessage = "All enabled warn message";
        final String errorMessage = "All enabled error message";
        final String wtfMessage = "All enabled wtf message";

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true, // allEnabled = true
                        List.of(),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), debugMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_VERBOSE, TEST_GROUP.getTag(), verboseMessage,
                    args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_INFO, TEST_GROUP.getTag(), infoMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_WARN, TEST_GROUP.getTag(), warnMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_ERROR, TEST_GROUP.getTag(), errorMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_WTF, TEST_GROUP.getTag(), wtfMessage, args);
        }, traceMonitor);

        List<String> messages = trace.messages.stream()
                .map(ProtoLogMessage::getMessage)
                .collect(Collectors.toList());
        assertWithMessage("Trace should contain the debug message")
                .that(messages).contains(debugMessage);
        assertWithMessage("Trace should contain the verbose message")
                .that(messages).contains(verboseMessage);
        assertWithMessage("Trace should contain the info message")
                .that(messages).contains(infoMessage);
        assertWithMessage("Trace should contain the warn message")
                .that(messages).contains(warnMessage);
        assertWithMessage("Trace should contain the error message")
                .that(messages).contains(errorMessage);
        assertWithMessage("Trace should contain the wtf message")
                .that(messages).contains(wtfMessage);
    }

    @Test
    public void testRespectsAllEnabled_false() throws IOException {
        final String debugMessage = "All disabled debug message";
        final String verboseMessage = "All disabled verbose message";
        final String infoMessage = "All disabled info message";
        final String warnMessage = "All disabled warn message";
        final String errorMessage = "All disabled error message";
        final String wtfMessage = "All disabled wtf message";

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        false, // allEnabled = false
                        List.of(),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), debugMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_VERBOSE, TEST_GROUP.getTag(), verboseMessage,
                    args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_INFO, TEST_GROUP.getTag(), infoMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_WARN, TEST_GROUP.getTag(), warnMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_ERROR, TEST_GROUP.getTag(), errorMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_WTF, TEST_GROUP.getTag(), wtfMessage, args);
        }, traceMonitor);

        List<String> messages = trace.messages.stream()
                .map(ProtoLogMessage::getMessage)
                .collect(Collectors.toList());
        assertWithMessage("Trace should not contain the debug message")
                .that(messages).doesNotContain(debugMessage);
        assertWithMessage("Trace should not contain the verbose message")
                .that(messages).doesNotContain(verboseMessage);
        assertWithMessage("Trace should not contain the info message")
                .that(messages).doesNotContain(infoMessage);
        assertWithMessage("Trace should not contain the warn message")
                .that(messages).doesNotContain(warnMessage);
        assertWithMessage("Trace should not contain the error message")
                .that(messages).doesNotContain(errorMessage);
        assertWithMessage("Trace should not contain the wtf message")
                .that(messages).doesNotContain(wtfMessage);
    }

    @Test
    public void testRespectsLogLevel_override() throws IOException {
        final String debugMessage = "Debug message";
        final String warnMessage = "Warn message";

        // Enable TEST_GROUP at WARN level. DEBUG logs should be ignored.
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        false, // allEnabled = false
                        List.of(new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                TEST_GROUP.name(), LogLevel.WARN, true)),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), debugMessage, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_WARN, TEST_GROUP.getTag(), warnMessage, args);
        }, traceMonitor);

        assertWithMessage("Trace should not contain debug message when level is WARN")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .doesNotContain(debugMessage);

        assertWithMessage("Trace should contain warn message when level is WARN")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(warnMessage);
    }

    @Test
    public void testMultipleGroups_independentControl() throws IOException {
        final String group1Message = "Group 1 message";
        final String group2Message = "Group 2 message";

        // Enable TEST_GROUP, Disable TEST_GROUP_2
        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        false, // allEnabled = false
                        List.of(
                                new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                        TEST_GROUP.name(), LogLevel.DEBUG, true),
                                new PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                                        TEST_GROUP_2.name(), LogLevel.WARN, false)),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP.getTag(), group1Message, args);
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, TEST_GROUP_2.getTag(), group2Message,
                    args);
        }, traceMonitor);

        List<String> messages = trace.messages.stream()
                .map(ProtoLogMessage::getMessage)
                .collect(Collectors.toList());
        assertWithMessage("Trace should contain group 1 message")
                .that(messages).contains(group1Message);
        assertWithMessage("Trace should not contain group 2 message")
                .that(messages).doesNotContain(group2Message);
    }

    @Test
    public void testUnknownGroup_isLoggedIfAllEnabled() throws IOException {
        final String unknownGroupMessage = "Unknown group message";
        final String unknownTag = "UNKNOWN_TAG";

        PerfettoTraceMonitor traceMonitor = PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(
                        true, // allEnabled = true
                        List.of(),
                        PROTOLOG_DATA_SOURCE_NAME)
                .build();

        ProtoLogTrace trace = logAndReadTrace(() -> {
            Object[] args = {};
            ProtoLogNative.log(
                    ProtoLogNative.PROTO_LOG_LEVEL_DEBUG, unknownTag, unknownGroupMessage, args);
        }, traceMonitor);

        assertWithMessage("Trace should contain message from unknown group when allEnabled is true")
                .that(trace.messages.stream().map(ProtoLogMessage::getMessage))
                .contains(unknownGroupMessage);
    }

    private ProtoLogTrace logAndReadTrace(Runnable loggingAction) throws IOException {
        return logAndReadTrace(loggingAction, PerfettoTraceMonitor.newBuilder()
                .enableProtoLog(PROTOLOG_DATA_SOURCE_NAME)
                .build());
    }

    private ProtoLogTrace logAndReadTrace(Runnable loggingAction,
            PerfettoTraceMonitor traceMonitor) throws IOException {
        final var writer = createTempWriter(mTracingDirectory);
        try {
            traceMonitor.start();
            loggingAction.run();
        } finally {
            traceMonitor.stop(writer);
        }

        final ResultReader reader = new ResultReader(writer.write());
        return reader.readProtoLogTrace();
    }

    private long generateProtoLogToolMask(String formatString, Object... args) {
        List<Integer> types = LogDataType.parseFormatString(formatString);
        // The C++ side of protolog expects the arguments to match the format string.
        assertWithMessage("Number of arguments should match format string")
                .that(args.length).isEqualTo(types.size());
        return LogDataType.logDataTypesToBitMask(types);
    }
}
