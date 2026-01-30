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

import static android.internal.perfetto.protos.AppWakelockConfig.AppWakelocksConfig.DROP_OWNER_PID;
import static android.internal.perfetto.protos.AppWakelockConfig.AppWakelocksConfig.FILTER_DURATION_BELOW_MS;
import static android.internal.perfetto.protos.AppWakelockConfig.AppWakelocksConfig.WRITE_DELAY_MS;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockBundle.ENCODED_TS;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockBundle.INTERN_ID;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.FLAGS;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.IID;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.OWNER_PID;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.OWNER_UID;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.TAG;
import static android.internal.perfetto.protos.AppWakelockData.AppWakelockInfo.WORK_UID;
import static android.internal.perfetto.protos.DataSourceConfigOuterClass.DataSourceConfig.APP_WAKELOCKS_CONFIG;
import static android.internal.perfetto.protos.InternedDataOuterClass.InternedData.APP_WAKELOCK_INFO;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.APP_WAKELOCK_BUNDLE;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.INTERNED_DATA;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.SEQUENCE_FLAGS;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.SEQ_INCREMENTAL_STATE_CLEARED;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.SEQ_NEEDS_INCREMENTAL_STATE;
import static android.internal.perfetto.protos.TracePacketOuterClass.TracePacket.TIMESTAMP;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.WorkSource;
import android.tracing.perfetto.CreateIncrementalStateArgs;
import android.tracing.perfetto.DataSource;
import android.tracing.perfetto.DataSourceInstance;
import android.tracing.perfetto.DataSourceParams;
import android.tracing.perfetto.FlushCallbackArguments;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.util.proto.WireTypeMismatchException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Records wakelock events using the Perfetto SDK. */
final class WakelockTracer
        extends DataSource<WakelockTracer.Instance, Void, WakelockTracer.IncrementalState> {
    private final Handler mHandler;
    private static final long NANOS_PER_MS = 1_000_000L;

    WakelockTracer(Looper looper) {
        this(looper, DataSourceParams.DEFAULTS);
    }

    WakelockTracer(Looper looper, DataSourceParams params) {
        super("android.app_wakelocks");
        mHandler = new Handler(looper);
        if (params != null) {
            register(params);
        }
    }

    /**
     * Creates a new instance of the Wakelock data source.
     *
     * @param stream A ProtoInputStream to read the tracing instance's config.
     * @param instanceIdx The index of the instance being created.
     * @return A new data source instance setup with the provided config.
     */
    @Override
    public Instance createInstance(ProtoInputStream stream, int instanceIdx) {
        return new Instance(this, instanceIdx, stream);
    }

    /**
     * Creates a new incremental state object for the Perfetto data source.
     *
     * @return A new (clean) incremental state instance.
     */
    @Override
    public IncrementalState createIncrementalState(CreateIncrementalStateArgs<Instance> args) {
        return new IncrementalState();
    }

    /**
     * Records a wakelock acquire or release event.
     *
     * Calls to onWakelockEvent are thread-safe. The mutations it applies to the instances
     * are protected by the Perfetto mutex. All other accesses to instance state must be
     * guarded by the Perfetto mutex.
     *
     * Calls to onWakelockEvent should be strictly sequenced. A release event must be sent
     * after the acquire event. This is done in Notifier.java by recording events while
     * holding the PowerManagerService lock.
     *
     * @param acquired Whether or not this is a acquire or release event.
     * @param tag The wakelock tag provided by the application.
     * @param uid The uid of the process requesting the wakelock.
     * @param pid The pid of the process requesting the wakelock.
     * @param flags The wakelock flag bitmask (such as PowerManager.PARTIAL_WAKE_LOCK).
     * @param ws The Worksource attached to the wakelock (or null if there is none).
     */
    public void onWakelockEvent(
            boolean acquired,
            String tag,
            int uid,
            int pid,
            int flags,
            WorkSource ws) {
        trace((ctx) -> {
            try (Instance instance = ctx.getDataSourceInstanceLocked()) {
                if (instance == null) return;
                instance.onWakelockEvent(acquired, tag, uid, pid, flags, ws,
                        SystemClock.elapsedRealtimeNanos());
            }
        });
    }

    /** Writes all pending data for the given instance to the Perfetto backend. */
    private void writeInstance(Instance target) {
        trace((ctx) -> {
            try (Instance instance = ctx.getDataSourceInstanceLocked()) {
                if (instance != target) return;
                instance.write(ctx.newTracePacket(), ctx.getIncrementalState(),
                        SystemClock.elapsedRealtimeNanos());
            }
        });
    }

    /** Wakelock identifies time and event-type independent attributes of a wakelock. */
    private record Wakelock(String tag, int ownerUid, int ownerPid, int flags, int workUid) {}

    /** WakelockEvent records an acquire or release of a wakelock. */
    private record WakelockEvent(Wakelock lock, boolean acquired, long timestampNs) {}

    /** InstanceConfig records the per-instance proto configuration. */
    private record InstanceConfig(long thresholdMs, long delayMs, boolean dropPid) {}

    /** IncrementalState tracks the interned wakelock info already written. */
    public static class IncrementalState {
        // Map from wakelock attributes to interned id.
        public final Map<Wakelock, Integer> iids = new HashMap<Wakelock, Integer>();
    }

    /** Instance corresponds to a single trace data source in a session. */
    public static class Instance extends DataSourceInstance {
        private final InstanceConfig mConfig;
        private final WakelockTracer mParent;

        private ArrayList<WakelockEvent> mEvents = new ArrayList<WakelockEvent>();
        private boolean mHasPendingWrite = false;

        Instance(WakelockTracer dataSource, int index, ProtoInputStream stream) {
            super(dataSource, index);
            mConfig = parseConfig(stream);
            mParent = dataSource;
        }

        @Override
        public void onFlush(FlushCallbackArguments args) {
            mParent.writeInstance(this);
        }

        /** Adds the event to the in-memory buffer. */
        public void onWakelockEvent(
                boolean acquired,
                String tag,
                int uid,
                int pid,
                int flags,
                WorkSource ws,
                long nanoTime) {
            pid = mConfig.dropPid ? -1 : pid;
            int workUid = ws == null ? -1 : ws.getAttributionUid();
            Wakelock lock = new Wakelock(tag, uid, pid, flags, workUid);
            WakelockEvent wle = new WakelockEvent(lock, acquired, nanoTime);

            if (!acquired) {
                // For release events, find the last acquire. If it exists and
                // the duration is less than the cutoff, remove it and don't add
                // the new event. If it is not found, assume that it was already
                // written. mEvents is expected to be sorted, so we only need to
                // look back until the cutoff time.
                long cutoff = nanoTime - (mConfig.thresholdMs * NANOS_PER_MS);
                for (int i = mEvents.size() - 1; i >= 0; i--) {
                    WakelockEvent curr = mEvents.get(i);
                    if (curr.timestampNs < cutoff) break;
                    if (pairsWith(curr, wle)) {
                        mEvents.remove(i);
                        return;
                    }
                }
            }

            mEvents.add(wle);
            scheduleWrite();
        }

        /** Writes pending events to the proto output stream. */
        public void write(ProtoOutputStream stream, IncrementalState state, long nanoTime) {
            ArrayList<WakelockEvent> pending = new ArrayList<WakelockEvent>();

            // Remove events that are ready and add them to pending. Start events are
            // ready only if they're older than the duration threshold. End events
            // are always ready (they aren't added if matching a short wakelock).
            long cutoff = nanoTime - (mConfig.thresholdMs * NANOS_PER_MS);
            mEvents.removeIf((event) -> {
                boolean ready = !event.acquired || event.timestampNs <= cutoff;
                if (ready) pending.add(event);
                return ready;
            });

            mHasPendingWrite = false;
            if (!mEvents.isEmpty()) {
                scheduleWrite();
            }

            writeToPacket(stream, state, pending);
        }

        private void scheduleWrite() {
            if (!mHasPendingWrite) {
                mHasPendingWrite = true;
                mParent.mHandler.postDelayed(
                        () -> {
                            mParent.writeInstance(this);
                        },
                        mConfig.delayMs);
            }
        }

        private static boolean pairsWith(WakelockEvent a, WakelockEvent b) {
            return a.acquired != b.acquired && a.lock.equals(b.lock);
        }

        private static long encodeTs(WakelockEvent e, long referenceTs) {
            return (e.timestampNs - referenceTs) << 1 | (e.acquired ? 1 : 0);
        }

        private static InstanceConfig parseConfig(ProtoInputStream stream) {
            boolean dropPid = false;
            int thresholdMs = 0;
            int delayMs = 5000;

            try {
                while (stream.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                    if (stream.getFieldNumber() == (int) APP_WAKELOCKS_CONFIG) {
                        final long token = stream.start(APP_WAKELOCKS_CONFIG);
                        while (stream.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                            switch (stream.getFieldNumber()) {
                                case (int) FILTER_DURATION_BELOW_MS:
                                    thresholdMs = stream.readInt(FILTER_DURATION_BELOW_MS);
                                    break;
                                case (int) WRITE_DELAY_MS:
                                    delayMs = stream.readInt(WRITE_DELAY_MS);
                                    break;
                                case (int) DROP_OWNER_PID:
                                    dropPid = stream.readBoolean(DROP_OWNER_PID);
                                    break;
                            }
                        }
                        stream.end(token);
                        break;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read Wakelock DataSource config", e);
            } catch (WireTypeMismatchException e) {
                throw new RuntimeException("Failed to parse Wakelock DataSource config", e);
            }

            return new InstanceConfig(thresholdMs, delayMs, dropPid);
        }

        private static void writeToPacket(
                ProtoOutputStream stream, IncrementalState state, ArrayList<WakelockEvent> events) {
            if (events.isEmpty()) return;

            // Events should be sorted, first event has lowest timestamp.
            long packetTs = events.get(0).timestampNs;

            stream.write(TIMESTAMP, packetTs);
            stream.write(
                    SEQUENCE_FLAGS,
                    state.iids.isEmpty()
                            ? SEQ_INCREMENTAL_STATE_CLEARED
                            : SEQ_NEEDS_INCREMENTAL_STATE);

            int[] iids = new int[events.size()];
            long[] encodedTs = new long[events.size()];
            long internToken = stream.start(INTERNED_DATA);
            for (int i = 0; i < events.size(); i++) {
                WakelockEvent event = events.get(i);
                Wakelock lock = event.lock;

                Integer iid = state.iids.get(lock);
                if (iid == null) {
                    iid = state.iids.size() + 1;
                    state.iids.put(lock, iid);
                    long itemToken = stream.start(APP_WAKELOCK_INFO);
                    stream.write(IID, iid.intValue());
                    stream.write(TAG, lock.tag);
                    stream.write(FLAGS, lock.flags);
                    if (lock.ownerPid >= 0) stream.write(OWNER_PID, lock.ownerPid);
                    if (lock.ownerUid >= 0) stream.write(OWNER_UID, lock.ownerUid);
                    if (lock.workUid >= 0) stream.write(WORK_UID, lock.workUid);
                    stream.end(itemToken);
                }

                iids[i] = iid.intValue();
                encodedTs[i] = encodeTs(event, packetTs);
            }
            stream.end(internToken);

            long bundleToken = stream.start(APP_WAKELOCK_BUNDLE);
            stream.writePackedUInt32(INTERN_ID, iids);
            stream.writePackedUInt64(ENCODED_TS, encodedTs);
            stream.end(bundleToken);
        }
    }
}
