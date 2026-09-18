/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.am;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UptimeMillisLong;
import android.content.Intent;
import android.os.Bundle;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.proto.ProtoOutputStream;

import com.android.internal.annotations.VisibleForTesting;

import dalvik.annotation.optimization.NeverCompile;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collection of recent historical broadcasts that are available to be dumped
 * for debugging purposes. Automatically trims itself over time.
 */
public class BroadcastHistory {
    /**
     * The number of top broadcast actions to dump in the summary for a given UID.
     */
    @VisibleForTesting
    static final int TOP_N_INTENTS_TO_DUMP = 5;

    private final int MAX_BROADCAST_HISTORY;
    private final int MAX_BROADCAST_SUMMARY_HISTORY;

    public BroadcastHistory(@NonNull BroadcastConstants constants) {
        MAX_BROADCAST_HISTORY = constants.MAX_HISTORY_COMPLETE_SIZE;
        MAX_BROADCAST_SUMMARY_HISTORY = constants.MAX_HISTORY_SUMMARY_SIZE;

        mBroadcastHistory = new BroadcastRecord[MAX_BROADCAST_HISTORY];
        mBroadcastSummaryHistory = new Intent[MAX_BROADCAST_SUMMARY_HISTORY];
        mSummaryHistoryEnqueueTime = new long[MAX_BROADCAST_SUMMARY_HISTORY];
        mSummaryHistoryDispatchTime = new long[MAX_BROADCAST_SUMMARY_HISTORY];
        mSummaryHistoryFinishTime = new long[MAX_BROADCAST_SUMMARY_HISTORY];
    }

    /**
     * List of broadcasts in frozen processes that are yet to be enqueued.
     */
    private final ArrayList<BroadcastRecord> mFrozenBroadcasts = new ArrayList<>();

    /**
     * List of broadcasts which are being delivered or yet to be delivered.
     */
    private final ArrayList<BroadcastRecord> mPendingBroadcasts = new ArrayList<>();

    /**
     * Historical data of past broadcasts, for debugging.  This is a ring buffer
     * whose last element is at mHistoryNext.
     */
    final BroadcastRecord[] mBroadcastHistory;
    int mHistoryNext = 0;

    /**
     * Summary of historical data of past broadcasts, for debugging.  This is a
     * ring buffer whose last element is at mSummaryHistoryNext.
     */
    final Intent[] mBroadcastSummaryHistory;
    int mSummaryHistoryNext = 0;

    /**
     * Various milestone timestamps of entries in the mBroadcastSummaryHistory ring
     * buffer, also tracked via the mSummaryHistoryNext index.  These are all in wall
     * clock time, not elapsed.
     */
    final long[] mSummaryHistoryEnqueueTime;
    final long[] mSummaryHistoryDispatchTime;
    final long[] mSummaryHistoryFinishTime;

    /**
     * Map of uids to number of pending broadcasts it sent.
     */
    private final SparseArray<ArrayMap<String, Integer>> mPendingBroadcastCountsPerUid =
            new SparseArray<>();

    /**
     * Map of uids to process names to number of pending broadcasts it sent.
     */
    private final SparseArray<ArrayMap<String, Integer>> mPendingBroadcastCountsPerProcess =
            new SparseArray<>();

    void onBroadcastFrozenLocked(@NonNull BroadcastRecord r) {
        mFrozenBroadcasts.add(r);
    }

    void onBroadcastEnqueuedLocked(@NonNull BroadcastRecord r) {
        mFrozenBroadcasts.remove(r);
        if (mPendingBroadcasts.add(r)) {
            updatePendingBroadcastCounterAndLogToTrace(r, /* delta= */ 1);
        }
    }

    void onBroadcastFinishedLocked(@NonNull BroadcastRecord r) {
        if (mPendingBroadcasts.remove(r)) {
            updatePendingBroadcastCounterAndLogToTrace(r, /* delta= */ -1);
        }
        addBroadcastToHistoryLocked(r);
    }

    private void updatePendingBroadcastCounterAndLogToTrace(@NonNull BroadcastRecord r,
            int delta) {
        if (r.callerApp != null) {
            final String processName = r.callerApp.processName;
            ArrayMap<String, Integer> processCounts =
                    mPendingBroadcastCountsPerProcess.get(r.callingUid);
            if (processCounts == null) {
                processCounts = new ArrayMap<>();
                mPendingBroadcastCountsPerProcess.put(r.callingUid, processCounts);
            }
            final int newCount = processCounts.getOrDefault(processName, 0) + delta;
            if (newCount <= 0) {
                processCounts.remove(processName);
                if (processCounts.isEmpty()) {
                    mPendingBroadcastCountsPerProcess.remove(r.callingUid);
                }
            } else {
                processCounts.put(processName, newCount);
            }
        }

        ArrayMap<String, Integer> pendingBroadcastCounts =
                mPendingBroadcastCountsPerUid.get(r.callingUid);
        if (pendingBroadcastCounts == null) {
            pendingBroadcastCounts = new ArrayMap<>();
            mPendingBroadcastCountsPerUid.put(r.callingUid, pendingBroadcastCounts);
        }
        final String callerPackage = r.callerPackage == null ? "null" : r.callerPackage;
        final Integer currentCount = pendingBroadcastCounts.get(callerPackage);
        final int newCount = (currentCount == null ? 0 : currentCount) + delta;
        if (newCount == 0) {
            pendingBroadcastCounts.remove(callerPackage);
            if (pendingBroadcastCounts.isEmpty()) {
                mPendingBroadcastCountsPerUid.remove(r.callingUid);
            }
        } else {
            pendingBroadcastCounts.put(callerPackage, newCount);
        }

        Trace.instantForTrack(Trace.TRACE_TAG_ACTIVITY_MANAGER, "Broadcasts pending per uid",
                callerPackage + "/" + r.callingUid + ":" + newCount);
        Trace.traceCounter(Trace.TRACE_TAG_ACTIVITY_MANAGER, "Broadcasts pending",
                mPendingBroadcasts.size());
    }

    public void addBroadcastToHistoryLocked(@NonNull BroadcastRecord original) {
        // Note sometimes (only for sticky broadcasts?) we reuse BroadcastRecords,
        // So don't change the incoming record directly.
        final BroadcastRecord historyRecord = original.maybeStripForHistory();

        mBroadcastHistory[mHistoryNext] = historyRecord;
        mHistoryNext = ringAdvance(mHistoryNext, 1, MAX_BROADCAST_HISTORY);

        mBroadcastSummaryHistory[mSummaryHistoryNext] = historyRecord.intent;
        mSummaryHistoryEnqueueTime[mSummaryHistoryNext] = historyRecord.enqueueClockTime;
        mSummaryHistoryDispatchTime[mSummaryHistoryNext] = historyRecord.dispatchClockTime;
        mSummaryHistoryFinishTime[mSummaryHistoryNext] = System.currentTimeMillis();
        mSummaryHistoryNext = ringAdvance(mSummaryHistoryNext, 1, MAX_BROADCAST_SUMMARY_HISTORY);
    }

    public int getPendingBroadcastsCount() {
        return mPendingBroadcasts.size();
    }

    int getPendingBroadcastCountForSenderProcess(int uid, @NonNull String processName) {
        final ArrayMap<String, Integer> processCounts =
                mPendingBroadcastCountsPerProcess.get(uid);
        if (processCounts == null) {
            return 0;
        }
        return processCounts.getOrDefault(processName, 0);
    }

    int getPendingBroadcastCountForSenderUid(int uid) {
        final ArrayMap<String, Integer> countsPerPkg = mPendingBroadcastCountsPerUid.get(uid);
        if (countsPerPkg == null) {
            return 0;
        }
        final int pkgCount = countsPerPkg.size();
        int pendingCount = 0;
        for (int i = 0; i < pkgCount; i++) {
            pendingCount += countsPerPkg.valueAt(i);
        }
        return pendingCount;
    }

    int getPendingBroadcastCountForSenderProcessSince(int uid, @NonNull String processName,
            @UptimeMillisLong long sinceTime) {
        int count = 0;
        final int size = mPendingBroadcasts.size();
        for (int i = 0; i < size; i++) {
            final BroadcastRecord br = mPendingBroadcasts.get(i);
            if (br.callingUid == uid && br.callerApp != null
                    && Objects.equals(br.callerApp.processName, processName)) {
                if (br.enqueueTime >= sinceTime) {
                    count++;
                }
            }
        }
        return count;
    }

    void appendPendingBroadcastsSummaryForUid(@NonNull StringBuilder summary, int uid) {
        final HashMap<String, Integer> actionCounts = new HashMap<>();
        for (int i = 0; i < mPendingBroadcasts.size(); i++) {
            final BroadcastRecord br = mPendingBroadcasts.get(i);
            if (br.callingUid == uid) {
                final String action = br.intent.getAction();
                actionCounts.put(action, actionCounts.getOrDefault(action, 0) + 1);
            }
        }

        if (actionCounts.isEmpty()) {
            return;
        }

        // Sort the entries by count
        final ArrayList<Map.Entry<String, Integer>> sortedActions =
                new ArrayList<>(actionCounts.entrySet());
        sortedActions.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        // Build the summary string
        summary.append(" Top 5 broadcast actions:\n");
        final int limit = Math.min(sortedActions.size(), TOP_N_INTENTS_TO_DUMP);
        for (int i = 0; i < limit; i++) {
            final Map.Entry<String, Integer> entry = sortedActions.get(i);
            summary.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue());
            if (i < limit - 1) {
                summary.append("\n");
            }
        }
    }

    private int ringAdvance(int x, final int increment, final int ringSize) {
        x += increment;
        if (x < 0) return (ringSize - 1);
        else if (x >= ringSize) return 0;
        else return x;
    }

    @NeverCompile
    public void dumpDebug(@NonNull ProtoOutputStream proto) {
        for (int i = 0; i < mPendingBroadcasts.size(); ++i) {
            final BroadcastRecord r = mPendingBroadcasts.get(i);
            r.dumpDebug(proto, BroadcastQueueProto.PENDING_BROADCASTS);
        }
        for (int i = 0; i < mFrozenBroadcasts.size(); ++i) {
            final BroadcastRecord r = mFrozenBroadcasts.get(i);
            r.dumpDebug(proto, BroadcastQueueProto.FROZEN_BROADCASTS);
        }

        int lastIndex = mHistoryNext;
        int ringIndex = lastIndex;
        do {
            // increasing index = more recent entry, and we want to print the most
            // recent first and work backwards, so we roll through the ring backwards.
            ringIndex = ringAdvance(ringIndex, -1, MAX_BROADCAST_HISTORY);
            BroadcastRecord r = mBroadcastHistory[ringIndex];
            if (r != null) {
                r.dumpDebug(proto, BroadcastQueueProto.HISTORICAL_BROADCASTS);
            }
        } while (ringIndex != lastIndex);

        lastIndex = ringIndex = mSummaryHistoryNext;
        do {
            ringIndex = ringAdvance(ringIndex, -1, MAX_BROADCAST_SUMMARY_HISTORY);
            Intent intent = mBroadcastSummaryHistory[ringIndex];
            if (intent == null) {
                continue;
            }
            long summaryToken = proto.start(BroadcastQueueProto.HISTORICAL_BROADCASTS_SUMMARY);
            intent.dumpDebug(proto, BroadcastQueueProto.BroadcastSummary.INTENT,
                    false, true, true, false);
            proto.write(BroadcastQueueProto.BroadcastSummary.ENQUEUE_CLOCK_TIME_MS,
                    mSummaryHistoryEnqueueTime[ringIndex]);
            proto.write(BroadcastQueueProto.BroadcastSummary.DISPATCH_CLOCK_TIME_MS,
                    mSummaryHistoryDispatchTime[ringIndex]);
            proto.write(BroadcastQueueProto.BroadcastSummary.FINISH_CLOCK_TIME_MS,
                    mSummaryHistoryFinishTime[ringIndex]);
            proto.end(summaryToken);
        } while (ringIndex != lastIndex);
    }

    @NeverCompile
    public boolean dumpLocked(@NonNull PrintWriter pw, @Nullable String dumpPackage,
            @Nullable String dumpIntentAction,
            @NonNull SimpleDateFormat sdf, boolean dumpAll) {
        boolean needSep = true;
        dumpBroadcastList(pw, sdf, mFrozenBroadcasts, dumpIntentAction, dumpAll, "Frozen");
        dumpBroadcastList(pw, sdf, mPendingBroadcasts, dumpIntentAction, dumpAll, "Pending");

        int i;
        boolean printed = false;

        i = -1;
        int lastIndex = mHistoryNext;
        int ringIndex = lastIndex;
        do {
            // increasing index = more recent entry, and we want to print the most
            // recent first and work backwards, so we roll through the ring backwards.
            ringIndex = ringAdvance(ringIndex, -1, MAX_BROADCAST_HISTORY);
            BroadcastRecord r = mBroadcastHistory[ringIndex];
            if (r == null) {
                continue;
            }

            i++; // genuine record of some sort even if we're filtering it out
            if (dumpPackage != null && !dumpPackage.equals(r.callerPackage)) {
                continue;
            }
            if (dumpIntentAction != null && !Objects.equals(dumpIntentAction,
                    r.intent.getAction())) {
                continue;
            }
            if (!printed) {
                if (needSep) {
                    pw.println();
                }
                needSep = true;
                pw.println("  Historical broadcasts:");
                printed = true;
            }
            if (dumpIntentAction != null) {
                pw.print("  Historical Broadcast #");
                pw.print(i); pw.println(":");
                r.dump(pw, "    ", sdf);
                if (!dumpAll) {
                    break;
                }
            } else if (dumpAll) {
                pw.print("  Historical Broadcast #");
                pw.print(i); pw.println(":");
                r.dump(pw, "    ", sdf);
            } else {
                pw.print("  #"); pw.print(i); pw.print(": "); pw.println(r);
                pw.print("    ");
                pw.println(r.intent.toShortString(false, true, true, false));
                if (r.targetComp != null && r.targetComp != r.intent.getComponent()) {
                    pw.print("    targetComp: "); pw.println(r.targetComp.toShortString());
                }
                Bundle bundle = r.intent.getExtras();
                if (bundle != null) {
                    pw.print("    extras: "); pw.println(bundle.toString());
                }
            }
        } while (ringIndex != lastIndex);

        if (dumpPackage == null && dumpIntentAction == null) {
            lastIndex = ringIndex = mSummaryHistoryNext;
            if (dumpAll) {
                printed = false;
                i = -1;
            } else {
                // roll over the 'i' full dumps that have already been issued
                for (int j = i;
                        j > 0 && ringIndex != lastIndex;) {
                    ringIndex = ringAdvance(ringIndex, -1, MAX_BROADCAST_SUMMARY_HISTORY);
                    BroadcastRecord r = mBroadcastHistory[ringIndex];
                    if (r == null) {
                        continue;
                    }
                    j--;
                }
            }
            // done skipping; dump the remainder of the ring. 'i' is still the ordinal within
            // the overall broadcast history.
            do {
                ringIndex = ringAdvance(ringIndex, -1, MAX_BROADCAST_SUMMARY_HISTORY);
                Intent intent = mBroadcastSummaryHistory[ringIndex];
                if (intent == null) {
                    continue;
                }
                if (!printed) {
                    if (needSep) {
                        pw.println();
                    }
                    needSep = true;
                    pw.println("  Historical broadcasts summary:");
                    printed = true;
                }
                if (!dumpAll && i >= 50) {
                    pw.println("  ...");
                    break;
                }
                i++;
                pw.print("  #"); pw.print(i); pw.print(": ");
                pw.println(intent.toShortString(false, true, true, false));
                pw.print("    ");
                TimeUtils.formatDuration(mSummaryHistoryDispatchTime[ringIndex]
                        - mSummaryHistoryEnqueueTime[ringIndex], pw);
                pw.print(" dispatch ");
                TimeUtils.formatDuration(mSummaryHistoryFinishTime[ringIndex]
                        - mSummaryHistoryDispatchTime[ringIndex], pw);
                pw.println(" finish");
                pw.print("    enq=");
                pw.print(sdf.format(new Date(mSummaryHistoryEnqueueTime[ringIndex])));
                pw.print(" disp=");
                pw.print(sdf.format(new Date(mSummaryHistoryDispatchTime[ringIndex])));
                pw.print(" fin=");
                pw.println(sdf.format(new Date(mSummaryHistoryFinishTime[ringIndex])));
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    pw.print("    extras: "); pw.println(bundle.toString());
                }
            } while (ringIndex != lastIndex);
        }
        return needSep;
    }

    private void dumpBroadcastList(@NonNull PrintWriter pw, @NonNull SimpleDateFormat sdf,
            @NonNull ArrayList<BroadcastRecord> broadcasts, @Nullable String dumpIntentAction,
            boolean dumpAll, @NonNull String flavor) {
        pw.print("  "); pw.print(flavor); pw.println(" broadcasts:");
        if (broadcasts.isEmpty()) {
            pw.println("    <empty>");
        } else {
            boolean printedAnything = false;
            for (int idx = broadcasts.size() - 1; idx >= 0; --idx) {
                final BroadcastRecord r = broadcasts.get(idx);
                if (dumpIntentAction != null && !Objects.equals(dumpIntentAction,
                        r.intent.getAction())) {
                    continue;
                }
                pw.print(flavor); pw.print("  broadcast #"); pw.print(idx); pw.println(":");
                r.dump(pw, "    ", sdf);
                printedAnything = true;
                if (dumpIntentAction != null && !dumpAll) {
                    break;
                }
            }
            if (!printedAnything) {
                pw.println("    <no-matches>");
            }
        }
    }
}
