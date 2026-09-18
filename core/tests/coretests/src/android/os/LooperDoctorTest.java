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

package android.os;

import static com.google.common.truth.Truth.assertThat;

import static android.os.LooperDoctor.LooperDoctorThread.THREAD_NAME;
import static android.os.LooperDoctor.LOOPER_DOCTOR_TRIGGER_LATE_HANDLER;
import static android.os.LooperDoctor.LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE;
import static android.os.LooperDoctor.LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH;

import android.content.Context;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.dev.perfetto.sdk.PerfettoTrace;

import com.google.protobuf.ExtensionRegistryLite;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import perfetto.protos.AndroidTrackEventOuterClass;
import perfetto.protos.DataSourceConfigOuterClass;
import perfetto.protos.TraceConfigOuterClass;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.TriggerConfig;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.TriggerConfig.Trigger;
import perfetto.protos.TraceOuterClass;
import perfetto.protos.TracePacketOuterClass;
import perfetto.protos.TrackEventConfigOuterClass;
import perfetto.protos.TriggerOuterClass;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

@RunWith(ParameterizedAndroidJunit4.class)
@DisabledOnRavenwood()
public class LooperDoctorTest {
    static final String TAG = "LooperDoctorTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final TestName mTestName = new TestName();

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V3);
    }

    @Rule public SetFlagsRule mSetFlagsRule;

    public LooperDoctorTest(FlagsParameterization flags) {
        mSetFlagsRule = new SetFlagsRule(flags);
    }

    @BeforeClass
    public static void setUpClass() {
        PerfettoTrace.register(true);
        PerfettoCategories.registerCategories();
    }

    private static final long MAX_MESSAGE_QUEUE_LEN = 1_000;
    private static final long MESSAGE_LATE_MS = 1_000;
    private static final long MAX_HANDLER_RUNTIME_MS = 500;

    private LooperDoctor mLooperDoctor;
    private Looper mLooper;
    private HandlerThread mThread;

    private com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session mSessionV3;

    private void setupLooperAndLooperDoctor() {
        LooperDoctor.sDropThreadRefOnExit = true;
        mThread = new HandlerThread("mq_test_thread");
        mThread.start();
        final Handler handler = mThread.getThreadHandler();

        mLooperDoctor = new LooperDoctor.Builder()
                .setLateMessageThresholdMs(MESSAGE_LATE_MS)
                .setMaxQueueLength(MAX_MESSAGE_QUEUE_LEN)
                .setMaxHandlerRuntimeMs(MAX_HANDLER_RUNTIME_MS)
                .build();
        mLooper = handler.getLooper();
        mLooper.setLooperDoctor(mLooperDoctor);
    }

    private void teardownLooperAndLooperDoctor() {
        mThread.quitSafely();
        mLooper = null;
        mLooperDoctor = null;
        mThread = null;
    }

    private void beginTrace(String triggerName) {
        byte[] mqTraceConfig = getTraceConfig("mq", triggerName).toByteArray();
        mSessionV3 =
                new com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session(
                        true, mqTraceConfig);
    }

    private ExtensionRegistryLite mRegistry;
    private byte[] endTrace() {
        mRegistry = ExtensionRegistryLite.newInstance();
        AndroidTrackEventOuterClass.registerAllExtensions(mRegistry);
        byte[] traceData;
        traceData = mSessionV3.close();

        writeTraceToFile(traceData);

        return traceData;
    }

    private void checkForTrigger(byte[] traceData, String expectedTriggerName,
            boolean shouldExist) throws Exception {
        TraceOuterClass.Trace trace = TraceOuterClass.Trace.parseFrom(traceData, mRegistry);

        // check for trigger
        boolean hasTrigger = false;
        for (TracePacketOuterClass.TracePacket packet : trace.getPacketList()) {
            if (packet.hasTrigger()) {
                assertThat(shouldExist).isTrue();

                hasTrigger = true;

                TriggerOuterClass.Trigger trigger = packet.getTrigger();

                assertThat(trigger.hasTriggerName()).isTrue();
                String triggerName = trigger.getTriggerName();
                assertThat(triggerName).isEqualTo(expectedTriggerName);
            }
        }

        if (shouldExist) {
            assertThat(hasTrigger).isTrue();
        } else {
            assertThat(hasTrigger).isFalse();
        }
    }

    private void writeTraceToFile(byte[] traceData) {
        String methodName = mTestName.getMethodName();
        if (methodName != null) {
            // Replace brackets, equals signs, and spaces with underscores
            methodName = methodName.replaceAll("[^a-zA-Z0-9_\\.-]", "_");
        } else {
            methodName = "unknown_test";
        }

        // Get a safe, writable directory for this test package
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir = new File(context.getExternalFilesDir(null), "perfetto_traces");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File traceFile = new File(dir, TAG + "_" + methodName + ".perfetto-trace");

        try (FileOutputStream fos = new FileOutputStream(traceFile)) {
            fos.write(traceData);
            Log.i(TAG, "Successfully wrote Perfetto trace to: " + traceFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write Perfetto trace", e);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testBadMessageQueueLength() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH);

        mLooperDoctor.checkMessageQueueLength(MAX_MESSAGE_QUEUE_LEN + 1);

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH, true);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testMultipleBadMessageQueueLength() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH);

        mLooperDoctor.checkMessageQueueLength(MAX_MESSAGE_QUEUE_LEN + 1);

        byte[] traceData = endTrace();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH, true);

        beginTrace(LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH);

        mLooperDoctor.checkMessageQueueLength(MAX_MESSAGE_QUEUE_LEN + 1);

        traceData = endTrace();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH, false);

        teardownLooperAndLooperDoctor();
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testGoodMessageQueueLength() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH);

        mLooperDoctor.checkMessageQueueLength(MAX_MESSAGE_QUEUE_LEN);

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH, false);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testLateImmediateMessage() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE);

        final Message m = Message.obtain();
        m.when = 0;
        final long now = SystemClock.uptimeMillis();
        m.enqueueTime = now - MESSAGE_LATE_MS - 1;

        mLooperDoctor.messageDequeuedForDelivery(m, now);

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE, true);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testLateMessage() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE);

        final Message m = Message.obtain();
        final long now = SystemClock.uptimeMillis();
        m.when = now - MESSAGE_LATE_MS - 1;
        m.enqueueTime = now - MESSAGE_LATE_MS - 1;

        mLooperDoctor.messageDequeuedForDelivery(m, now);

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE, true);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testOnTimeMessage() throws Exception {
        setupLooperAndLooperDoctor();

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE);

        final Message m = Message.obtain();
        m.when = 0;
        final long now = SystemClock.uptimeMillis();
        m.enqueueTime = now;

        mLooperDoctor.messageDequeuedForDelivery(m, now);

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE, false);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testOnTimeHandler() throws Exception {
        setupLooperAndLooperDoctor();
        final Handler handler = mThread.getThreadHandler();
        final CountDownLatch latch = new CountDownLatch(1);

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_HANDLER);

        handler.post(
                () -> {
                    latch.countDown();
                });

        latch.await();

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_HANDLER, false);
    }

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testLateHandler() throws Exception {
        setupLooperAndLooperDoctor();
        final Handler handler = mThread.getThreadHandler();
        final CountDownLatch latch = new CountDownLatch(1);

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_HANDLER);

        handler.post(
                () -> {
                    try {
                        // Sleep here to make the handler late
                        Thread.sleep(2 * MAX_HANDLER_RUNTIME_MS);
                    } catch (InterruptedException e) { }

                    latch.countDown();
                });

        latch.await();

        // This may race with the thread going to sleep after having triggered
        assertThat(isLooperDoctorThreadSleeping()).isTrue();

        byte[] traceData = endTrace();

        teardownLooperAndLooperDoctor();

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_HANDLER, true);
    }

    LooperDoctor[] mLooperDoctors;
    Looper[] mLoopers;
    HandlerThread[] mThreads;

    private void setupLooperAndLooperDoctorArray(int numLoopers) {
        LooperDoctor.sDropThreadRefOnExit = true;

        mLoopers = new Looper[numLoopers];
        mLooperDoctors = new LooperDoctor[numLoopers];
        mThreads = new HandlerThread[numLoopers];

        for (int i = 0; i < numLoopers; i++) {
            mThreads[i] = new HandlerThread("mq_test_thread");
            mThreads[i].start();

            final Handler handler = mThreads[i].getThreadHandler();

            mLooperDoctors[i] = new LooperDoctor.Builder()
                .setLateMessageThresholdMs(MESSAGE_LATE_MS)
                .setMaxQueueLength(MAX_MESSAGE_QUEUE_LEN)
                .setMaxHandlerRuntimeMs(MAX_HANDLER_RUNTIME_MS)
                .build();
            mLoopers[i] = handler.getLooper();
            mLoopers[i].setLooperDoctor(mLooperDoctors[i]);
        }
    }

    private void teardownLooperAndLooperDoctorArray() {
        for (int i = 0; i < mLooperDoctors.length; i++) {
            mThreads[i].quitSafely();
            mLoopers[i] = null;
            mLooperDoctors[i] = null;
            mThreads[i] = null;
        }

        mLooperDoctors = null;
        mLoopers = null;
        mThreads = null;
    }

    private boolean isLooperDoctorThreadSleeping() {
        Set<Thread> allThreads =
                Thread.getAllStackTraces().keySet();
        for (Thread thread : allThreads) {
            if (thread.getName().equals(THREAD_NAME)) {
                Log.d(TAG, "Found LooperDoctorThread: " + THREAD_NAME);
                Thread.State state = thread.getState();
                switch (state) {
                    case Thread.State.BLOCKED:
                        Log.d(TAG, "Thread state BLOCKED");
                        return true;
                    case Thread.State.NEW:
                        Log.d(TAG, "Thread state NEW");
                        return false;
                    case Thread.State.RUNNABLE:
                        Log.d(TAG, "Thread state RUNNABLE");
                        return false;
                    case Thread.State.TERMINATED:
                        Log.d(TAG, "Thread state TERMINATED");
                        return false;
                    case Thread.State.TIMED_WAITING:
                        Log.d(TAG, "Thread state TIMED_WAITING");
                        return true;
                    case Thread.State.WAITING:
                        Log.d(TAG, "Thread state WAITING");
                        return true;
                }
                break;
            }
        }
        return false;
    }

    private int countLooperDoctorThreads() {
        int count = 0;

        Set<Thread> allThreads =
                Thread.getAllStackTraces().keySet();
        for (Thread thread : allThreads) {
            if (thread.getName().equals(THREAD_NAME)) {
                count++;
            }
        }
        return count;
    }

    static final int MAX_LOOPER_DOCTOR_THREADS = 10;
    static final int MESSAGE_DELAY_MS          = 100;
    static final int NUM_MESSAGES_PER_THREAD   = 100;

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testStressManyLoopers() throws Exception {
        setupLooperAndLooperDoctorArray(MAX_LOOPER_DOCTOR_THREADS);

        beginTrace(LOOPER_DOCTOR_TRIGGER_LATE_HANDLER);

        final CountDownLatch latch =
                new CountDownLatch(MAX_LOOPER_DOCTOR_THREADS * NUM_MESSAGES_PER_THREAD);
        for (int i = 0; i < mLooperDoctors.length; i++) {
            Handler handler = mThreads[i].getThreadHandler();
            for (int j = 0; j < NUM_MESSAGES_PER_THREAD; j++) {
                handler.postDelayed(
                        () -> {
                            latch.countDown();
                        }, MESSAGE_DELAY_MS);
            }
        }

        latch.await();

        // Should have exactly one LooperDoctor thread
        int threadCount = countLooperDoctorThreads();
        assertThat(threadCount).isEqualTo(1);

        byte[] traceData = endTrace();
        teardownLooperAndLooperDoctorArray();

        // Should have no dangling threads
        threadCount = countLooperDoctorThreads();
        assertThat(threadCount).isEqualTo(0);

        checkForTrigger(traceData, LOOPER_DOCTOR_TRIGGER_LATE_HANDLER, false);
    }

    static final int NUM_LOOPER_STARTUPS_PER_PASS = 10;
    static final int LOOPER_STARTUP_NUM_PASSES = 1000;

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testStressLooperStartup() throws Exception {
        for (int i = 0; i < LOOPER_STARTUP_NUM_PASSES; i++) {
            setupLooperAndLooperDoctorArray(NUM_LOOPER_STARTUPS_PER_PASS);
            teardownLooperAndLooperDoctorArray();
        }

        // Should have no dangling threads
        int threadCount = countLooperDoctorThreads();
        assertThat(threadCount).isEqualTo(0);
    }

    static final int NUM_STRESS_TEST_THREADS = 10;

    @Test
    @EnableFlags(Flags.FLAG_MESSAGE_QUEUE_MONITORING_ENABLED)
    public void testStressThreadedLooperStartup() throws Exception {
        LooperDoctor.sDropThreadRefOnExit = true;
        CountDownLatch threadsStarted = new CountDownLatch(1);
        CountDownLatch threadsCompleted = new CountDownLatch(NUM_STRESS_TEST_THREADS);
        mLooperDoctor = new LooperDoctor.Builder()
                .setLateMessageThresholdMs(MESSAGE_LATE_MS)
                .setMaxQueueLength(MAX_MESSAGE_QUEUE_LEN)
                .setMaxHandlerRuntimeMs(MAX_HANDLER_RUNTIME_MS)
                .build();

        Thread[] threads = new Thread[NUM_STRESS_TEST_THREADS];
        for (int i = 0; i < NUM_STRESS_TEST_THREADS; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        threadsStarted.await();
                    } catch (InterruptedException e) { }

                    for (int i = 0; i < LOOPER_STARTUP_NUM_PASSES; i++) {
                        LooperDoctor.LooperDoctorAlarm alarm =
                                mLooperDoctor.notifyLooperStartedLooping(null);
                        mLooperDoctor.notifyLooperQuit(alarm);
                    }

                    threadsCompleted.countDown();
                }
            });
            threads[i].start();
        }

        threadsStarted.countDown();
        threadsCompleted.await();

        for (int i = 0; i < NUM_STRESS_TEST_THREADS; i++) {
            threads[i].join();
        }

        mLooperDoctor = null;
    }

    private TraceConfigOuterClass.TraceConfig getTraceConfig(String cat, String trig) {
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
        Trigger trigger = Trigger.newBuilder().setName(trig).build();
        TriggerConfig triggerConfig =
                TriggerConfig.newBuilder()
                .setTriggerMode(TriggerConfig.TriggerMode.STOP_TRACING)
                .setTriggerTimeoutMs(1000)
                .addTriggers(trigger)
                .build();
        TraceConfigOuterClass.TraceConfig traceConfig =
                TraceConfigOuterClass.TraceConfig.newBuilder()
                        .addBuffers(bufferConfig)
                        .addDataSources(ds)
                        .setTriggerConfig(triggerConfig)
                        .build();
        return traceConfig;
    }
}
