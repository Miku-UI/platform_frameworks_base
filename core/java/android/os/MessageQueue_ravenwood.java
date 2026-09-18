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

import android.platform.test.ravenwood.RavenwoodEnvironment;
import android.platform.test.ravenwood.RavenwoodErrorHandler;
import android.platform.test.ravenwood.RavenwoodImplUtils;
import android.util.IndentingPrintWriter;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Redirection target class from {@link MessageQueue}.
 *
 * TODO: Keep track of all sync barriers
 */
public class MessageQueue_ravenwood {
    private MessageQueue_ravenwood() {
    }

    private static boolean targetsAtLeast(int sdkVersion) {
        return RavenwoodEnvironment.getInstance().getTargetSdkLevel() >= sdkVersion;
    }

    /**
     * Used by the "combined" version.
     */
    static boolean computeUseConcurrent() {
        // On Ravenwood, @ChangeIds are not yet ready when this method is called,
        // so manually check the test's target SDK version.
        var def = targetsAtLeast(android.os.Build.VERSION_CODES.BAKLAVA);

        // Use "ravenwood.prop" to explicitly enable/disable for a specific test.
        return SystemProperties.getBoolean(
                "ravenwood.android.os.MessageQueue.useConcurrent", def);
    }

    /**
     * Used by the "combineddeli" version.
     */
    static boolean computeUseDeliQueue(boolean enable) {
        // On Ravenwood, @ChangeIds are not yet ready when this method is called,
        // so manually check the test's target SDK version.
        var def = targetsAtLeast(android.os.Build.VERSION_CODES.BAKLAVA);

        // Use "ravenwood.prop" to explicitly enable/disable for a specific test.
        return SystemProperties.getBoolean(
                "ravenwood.android.os.MessageQueue.useDeliQueue", def);
    }

    static void onResetForTestCalled() {
        RavenwoodErrorHandler.onWarningDetected("MessageQueue.resetForTest() called!");
    }

    record SyncBarrierInfo(
            int token,
            Throwable stack) {
    }


    record QueueInfo(
            MessageQueue queue,
            /** Sync barrier token -> stack trace, sortd by token. */
            TreeMap<Integer, SyncBarrierInfo> syncBarriers
    ) {
    }

    @GuardedBy("sSyncBarriers")
    private static final Map<MessageQueue, QueueInfo> sSyncBarriers = new WeakHashMap<>();

    /**
     * Called when a new sync barrier is called. We keep track of it.
     */
    static int onSyncBarrierPosted(MessageQueue queue, int token) {
        var stack = RavenwoodImplUtils.getStackTrace(
                "Sync barrier [" + token + "] obtained here",
                MessageQueue.class,
                /*removeMatchingFrame=*/ true);
        var binfo = new SyncBarrierInfo(token, stack);
        synchronized (sSyncBarriers) {
            var qinfo = sSyncBarriers.get(queue);
            if (qinfo == null) {
                qinfo = new QueueInfo(queue, new TreeMap<>());
                sSyncBarriers.put(queue, qinfo);
            }
            qinfo.syncBarriers.put(token, binfo);
        }
        return token;
    }

    /**
     * Called when a sync barrier is removed. We keep track of it.
     */
    static void onSyncBarrierRemoved(MessageQueue queue, int token) {
        synchronized (sSyncBarriers) {
            var qinfo = sSyncBarriers.get(queue);
            if (qinfo != null) {
                qinfo.syncBarriers.remove(token);
            }
        }
    }

    /**
     * Print all the pending sync barriers on the given stream.
     */
    public static void dumpAllSyncBarriers(PrintStream ps) {
        var out = new IndentingPrintWriter(new PrintWriter(ps, true), "  ");
        synchronized (sSyncBarriers) {
            var globalHeaderShown = false;
            for (var qinfo : sSyncBarriers.values()) {
                var qinfos = qinfo.syncBarriers.values();
                if (qinfos.isEmpty()) {
                    continue;
                }
                if (!globalHeaderShown) {
                    out.println("Pending Sync Barriers:");
                    out.increaseIndent();
                    globalHeaderShown = true;
                }
                out.increaseIndent();
                out.println("Thread=" + qinfo.queue.getLooperThread());

                var i = 0;
                for (var binfo : qinfos) {
                    out.println("Barrier #" + (i++)
                            + ": syncBarrierToken=[" + binfo.token + "] posted here:");
                    out.increaseIndent();
                    binfo.stack.printStackTrace(out);
                    out.decreaseIndent();
                }
                out.decreaseIndent();
            }
        }
        out.flush();
    }
}

