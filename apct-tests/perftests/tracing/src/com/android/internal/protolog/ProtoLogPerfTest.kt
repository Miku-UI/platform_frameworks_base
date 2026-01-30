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
package com.android.internal.protolog

import android.os.ServiceManager
import android.perftests.utils.PerfStatusReporter
import android.platform.test.annotations.Postsubmit
import android.tools.traces.busyWaitForDataSourceRegistration
import android.tools.traces.busyWaitTracingSessionDoesntExist
import android.tools.traces.busyWaitTracingSessionExists
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.PerfettoTraceMonitor.Companion.newBuilder
import android.tracing.perfetto.DataSourceParams
import android.tracing.perfetto.InitArguments
import android.tracing.perfetto.Producer
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.protolog.common.IProtoLog
import com.android.internal.protolog.common.IProtoLogGroup
import com.android.internal.protolog.common.LogLevel
import java.io.File
import kotlin.concurrent.Volatile
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import perfetto.protos.Protolog
import perfetto.protos.ProtologCommon

@RunWith(Parameterized::class)
@Postsubmit
class ProtoLogPerfTest(logType: LogType) {

    @get:Rule val perfStatusReporter = PerfStatusReporter()

    private val mLogToProto: Boolean
    private val mLogToLogcat: Boolean
    private val mCollectStackTrace: Boolean

    private var mProcessedProtoLogger: IProtoLog? = null
    private var mPerfettoTracingMonitor: PerfettoTraceMonitor? = null

    init {
        mLogToProto =
            when (logType) {
                LogType.ALL,
                LogType.PROTO_AND_STACKTRACE_ONLY,
                LogType.ALL_NO_STACKTRACE,
                LogType.PROTO_ONLY -> true
                LogType.LOGCAT_ONLY,
                LogType.NONE -> false
            }

        mLogToLogcat =
            when (logType) {
                LogType.ALL,
                LogType.ALL_NO_STACKTRACE,
                LogType.LOGCAT_ONLY -> true
                LogType.PROTO_AND_STACKTRACE_ONLY,
                LogType.PROTO_ONLY,
                LogType.NONE -> false
            }

        mCollectStackTrace =
            when (logType) {
                LogType.ALL,
                LogType.PROTO_AND_STACKTRACE_ONLY -> true
                LogType.ALL_NO_STACKTRACE,
                LogType.LOGCAT_ONLY,
                LogType.PROTO_ONLY,
                LogType.NONE -> false
            }
    }

    @Before
    @Throws(ServiceManager.ServiceNotFoundException::class)
    fun setUp() {
        TEST_GROUP.isLogToLogcat = mLogToLogcat

        mProcessedProtoLogger =
            ProcessedPerfettoProtoLogImpl(
                sTestDataSource!!,
                MOCK_TEST_FILE_PATH,
                { AutoClosableProtoInputStream(VIEWER_CONFIG.toByteArray()) },
                { instance: IProtoLog? -> },
                arrayOf(TEST_GROUP),
            )

        val groupOverrides =
            if (mLogToProto) {
                listOf(
                    PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                        groupName = TEST_GROUP.name()!!,
                        logFrom = LogLevel.VERBOSE,
                        collectStackTrace = mCollectStackTrace,
                    )
                )
            } else {
                listOf(
                    PerfettoTraceMonitor.Builder.ProtoLogGroupOverride(
                        groupName = TEST_GROUP.name()!!,
                        logFrom = LogLevel.WTF,
                        collectStackTrace = mCollectStackTrace,
                    )
                )
            }

        mPerfettoTracingMonitor =
            newBuilder()
                .setUniqueSessionName(TRACE_SESSION_NAME)
                .enableProtoLog(
                    logAll = mLogToProto,
                    groupOverrides = groupOverrides,
                    dataSourceName = TEST_PROTOLOG_DATASOURCE_NAME,
                )
                .build()
        mPerfettoTracingMonitor?.start()
        busyWaitTracingSessionExists(TRACE_SESSION_NAME)
    }

    @After
    fun tearDown() {
        val dir: File = tempDataSourceDir()
        val writer: ResultWriter = createDummyWriter(dir)
        mPerfettoTracingMonitor?.stop(writer)

        busyWaitTracingSessionDoesntExist(TRACE_SESSION_NAME)
    }

    @Test
    fun log_Processed_NoArgs() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mProcessedProtoLogger!!.log(LogLevel.INFO, TEST_GROUP, 123, 0, null as Array<Any?>?)
        }
    }

    @Test
    fun log_Processed_WithArgs() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            mProcessedProtoLogger!!.log(
                LogLevel.INFO,
                TEST_GROUP,
                123,
                59988,
                arrayOf<Any>("test", 1, 2, 3, 0.4, 0.5, 0.6, true),
            )
        }
    }

    @Test
    fun log_Unprocessed_NoArgs() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            ProtoLog.d(TEST_GROUP, "Test message")
        }
    }

    @Test
    fun log_Unprocessed_WithArgs() {
        val state = perfStatusReporter.benchmarkState
        while (state.keepRunning()) {
            ProtoLog.d(TEST_GROUP, "Test message %s, %d, %b", "arg1", 2, true)
        }
    }

    private fun tempDataSourceDir(): File {
        val tempDir = InstrumentationRegistry.getInstrumentation().context.cacheDir
        return File(tempDir, "DataSourcePerfTest_Dummy_" + System.nanoTime())
    }

    // Helper to create a dummy ResultWriter
    private fun createDummyWriter(scenarioDir: File): ResultWriter {
        return ResultWriter()
            .withName("DataSourcePerfTest")
            .withOutputDir(scenarioDir)
            .setRunComplete()
    }

    enum class LogType {
        PROTO_ONLY,
        LOGCAT_ONLY,
        PROTO_AND_STACKTRACE_ONLY,
        ALL_NO_STACKTRACE,
        ALL,
        NONE,
    }

    companion object {
        /** Generates the parameters used for this test class */
        @Parameters(name = "LOG_TO_{0}")
        @JvmStatic
        fun params(): MutableCollection<Array<Any?>?> {
            val params = ArrayList<Array<Any?>?>()

            for (logTo in LogType.entries) {
                params.add(arrayOf(logTo))
            }

            return params
        }

        const val TRACE_SESSION_NAME = "protolog-perf-test"

        val TEST_GROUP =
            object : IProtoLogGroup {
                private val mEnabled: Boolean = true
                @field:Volatile private var mLogToLogcat: Boolean = true
                private val mTag: String = "WindowManagerProtoLogTest"

                override fun isEnabled(): Boolean {
                    return mEnabled
                }

                override fun isLogToLogcat(): Boolean {
                    return mLogToLogcat
                }

                override fun getTag(): String? {
                    return mTag
                }

                override fun setLogToLogcat(logToLogcat: Boolean) {
                    this.mLogToLogcat = logToLogcat
                }

                override fun name(): String? {
                    return "TEST_GROUP"
                }

                override fun getId(): Int {
                    return 1
                }
            }

        private var sTestDataSource: ProtoLogDataSource? = null
        private const val TEST_PROTOLOG_DATASOURCE_NAME = "test.android.protolog"
        private const val MOCK_TEST_FILE_PATH = "mock/file/path"
        private val VIEWER_CONFIG: Protolog.ProtoLogViewerConfig =
            Protolog.ProtoLogViewerConfig.newBuilder()
                .addGroups(
                    Protolog.ProtoLogViewerConfig.Group.newBuilder()
                        .setId(TEST_GROUP.id)
                        .setName(TEST_GROUP.name())
                        .setTag(TEST_GROUP.tag)
                )
                .addMessages(
                    Protolog.ProtoLogViewerConfig.MessageData.newBuilder()
                        .setMessageId(123)
                        .setMessage("My Test Debug Log Message %b")
                        .setLevel(ProtologCommon.ProtoLogLevel.PROTOLOG_LEVEL_DEBUG)
                        .setGroupId(1)
                        .setLocation("com/test/MyTestClass.java:123")
                )
                .build()

        @BeforeClass
        @JvmStatic
        fun init() {
            Producer.init(InitArguments.DEFAULTS)

            sTestDataSource = ProtoLogDataSource(TEST_PROTOLOG_DATASOURCE_NAME)
            val params =
                DataSourceParams.Builder()
                    .setBufferExhaustedPolicy(
                        DataSourceParams.PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_DROP
                    )
                    .build()
            sTestDataSource!!.register(params)

            ProtoLog.init(TEST_GROUP)

            busyWaitForDataSourceRegistration(TEST_PROTOLOG_DATASOURCE_NAME)
        }
    }
}
