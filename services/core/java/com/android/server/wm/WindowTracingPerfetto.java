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

package com.android.server.wm;

import android.annotation.Nullable;
import android.internal.perfetto.protos.TracePacketOuterClass.TracePacket;
import android.internal.perfetto.protos.WinscopeExtensionsImplOuterClass.WinscopeExtensionsImpl;
import android.os.Handler;
import android.os.Looper;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.Trace;
import android.tracing.TracingUtils;
import android.util.Log;
import android.util.proto.ProtoOutputStream;
import android.view.Choreographer;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class WindowTracingPerfetto extends WindowTracing {
    private static final String TAG = "WindowTracing";
    private static final String PRODUCTION_DATA_SOURCE_NAME = "android.windowmanager";

    private final AtomicInteger mCountSessionsOnFrame = new AtomicInteger();
    private final AtomicInteger mCountSessionsOnTransaction = new AtomicInteger();
    private final WindowTracingDataSource mDataSource;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    WindowTracingPerfetto(WindowManagerService service, Choreographer choreographer) {
        this(service, choreographer, service.mGlobalLock, PRODUCTION_DATA_SOURCE_NAME);
    }

    @VisibleForTesting
    WindowTracingPerfetto(WindowManagerService service, Choreographer choreographer,
            WindowManagerGlobalLock globalLock, String dataSourceName) {
        super(service, choreographer, globalLock);
        mDataSource = new WindowTracingDataSource(this, dataSourceName);
    }

    @Override
    void setLogLevel(@WindowTracingLogLevel int logLevel, PrintWriter pw) {
        logAndPrintln(pw, "Log level must be configured through perfetto");
    }

    @Override
    void setLogFrequency(boolean onFrame, PrintWriter pw) {
        logAndPrintln(pw, "Log frequency must be configured through perfetto");
    }

    @Override
    void setBufferCapacity(int capacity, PrintWriter pw) {
        logAndPrintln(pw, "Buffer capacity must be configured through perfetto");
    }

    @Override
    boolean isEnabled() {
        return (mCountSessionsOnFrame.get() + mCountSessionsOnTransaction.get()) > 0;
    }

    @Override
    int onShellCommand(ShellCommand shell) {
        PrintWriter pw = shell.getOutPrintWriter();
        pw.println("Shell commands are ignored."
                + " Any type of action should be performed through perfetto.");
        return -1;
    }

    @Override
    String getStatus() {
        return "Status: "
                + ((isEnabled()) ? "Enabled" : "Disabled")
                + "\n"
                + "Sessions logging 'on frame': " + mCountSessionsOnFrame.get()
                + "\n"
                + "Sessions logging 'on transaction': " + mCountSessionsOnTransaction.get()
                + "\n";
    }

    @Override
    protected void startTraceInternal(@Nullable PrintWriter pw) {
        logAndPrintln(pw, "Tracing must be started through perfetto");
    }

    @Override
    protected void stopTraceInternal(@Nullable PrintWriter pw) {
        logAndPrintln(pw, "Tracing must be stopped through perfetto");
    }

    @Override
    protected void saveForBugreportInternal(@Nullable PrintWriter pw) {
        logAndPrintln(pw, "Tracing snapshot for bugreport must be handled through perfetto");
    }

    @Override
    protected void log(String where) {
        try {
            Trace.beginSection(TracingUtils.uiTracingSliceName("Window::log"));
            boolean isStartLogEvent = WHERE_START_TRACING.equals(where);
            boolean isOnFrameLogEvent = WHERE_ON_FRAME.equals(where);

            ArrayList<Runnable> pendingStopDones = new ArrayList<>();

            mDataSource.trace((context) -> {
                WindowTracingDataSource.TlsState tlsState = context.getCustomTlsState();

                if (isStartLogEvent) {
                    if (tlsState.mConfig.mLogFrequency == WindowTracingLogFrequency.SINGLE_DUMP) {
                        // A single dump gets triggered when the data source is stopping
                        // (onStop() callback)
                        boolean isStopping = tlsState.mStatus.compareAndSet(
                                WindowTracingDataSource.Status.STOPPING,
                                WindowTracingDataSource.Status.STOPPED);
                        if (!isStopping) {
                            return;
                        }
                        pendingStopDones.add(context::stopDone);
                    } else {
                        boolean isStarting = tlsState.mStatus.compareAndSet(
                                WindowTracingDataSource.Status.STARTING,
                                WindowTracingDataSource.Status.STARTED);
                        if (!isStarting) {
                            return;
                        }
                    }
                } else if (isOnFrameLogEvent) {
                    if (tlsState.mConfig.mLogFrequency != WindowTracingLogFrequency.FRAME) {
                        return;
                    }
                    boolean isStarted =
                            tlsState.mStatus.get() == WindowTracingDataSource.Status.STARTED;
                    if (!isStarted) {
                        return;
                    }
                }

                ProtoOutputStream os = context.newTracePacket();
                long timestamp = SystemClock.elapsedRealtimeNanos();
                os.write(TracePacket.TIMESTAMP, timestamp);
                final long tokenWinscopeExtensions =
                        os.start(TracePacket.WINSCOPE_EXTENSIONS);
                final long tokenExtensionsField =
                        os.start(WinscopeExtensionsImpl.WINDOWMANAGER);
                dumpToProto(os, tlsState.mConfig.mLogLevel, where, timestamp);
                os.end(tokenExtensionsField);
                os.end(tokenWinscopeExtensions);
            });

            // Execute the stopDone() calls only after DataSource#trace() has returned. Within
            // DataSource#trace() the data is not written to Perfetto yet, hence stopDone()
            // can't be called there.
            for (int i = 0; i < pendingStopDones.size(); ++i) {
                pendingStopDones.get(i).run();
                Log.i(TAG, "Stopped session (frequency=SINGLE_DUMP) (postponed stop)");
            }
        } catch (Exception e) {
            Log.wtf(TAG, "Exception while tracing state", e);
        } finally {
            Trace.endSection();
        }
    }

    @Override
    protected boolean shouldLogOnFrame() {
        return mCountSessionsOnFrame.get() > 0;
    }

    @Override
    protected boolean shouldLogOnTransaction() {
        return mCountSessionsOnTransaction.get() > 0;
    }

    void onStart(WindowTracingDataSource.Config config) {
        if (config.mLogFrequency == WindowTracingLogFrequency.FRAME) {
            Log.i(TAG, "Started session (frequency=FRAME, log level=" + config.mLogFrequency + ")");
            mCountSessionsOnFrame.incrementAndGet();
        } else if (config.mLogFrequency == WindowTracingLogFrequency.TRANSACTION) {
            Log.i(TAG, "Started session (frequency=TRANSACTION, log level="
                    + config.mLogFrequency + ")");
            mCountSessionsOnTransaction.incrementAndGet();
        } else if (config.mLogFrequency == WindowTracingLogFrequency.SINGLE_DUMP) {
            Log.i(TAG, "Started session (frequency=SINGLE_DUMP, log level="
                    + config.mLogFrequency + ")");
            return; // onStop() will trigger the start log event of SINGLE_DUMP tracing sessions
        }

        mMainHandler.post(() -> log(WHERE_START_TRACING));
    }

    void onStop(WindowTracingDataSource.Instance instance) {
        if (instance.mConfig.mLogFrequency == WindowTracingLogFrequency.FRAME) {
            instance.stopDone();
            instance.mStatus.set(WindowTracingDataSource.Status.STOPPED);
            mCountSessionsOnFrame.decrementAndGet();
            Log.i(TAG, "Stopped session (frequency=FRAME)");
        } else if (instance.mConfig.mLogFrequency == WindowTracingLogFrequency.TRANSACTION) {
            instance.stopDone();
            instance.mStatus.set(WindowTracingDataSource.Status.STOPPED);
            mCountSessionsOnTransaction.decrementAndGet();
            Log.i(TAG, "Stopped session (frequency=TRANSACTION)");
        } else if (instance.mConfig.mLogFrequency == WindowTracingLogFrequency.SINGLE_DUMP) {
            Log.i(TAG, "Triggering log event on stop (frequency=SINGLE_DUMP)");
            instance.mStatus.set(WindowTracingDataSource.Status.STOPPING);
            mMainHandler.post(() -> log(WHERE_START_TRACING));
        }
    }
}
