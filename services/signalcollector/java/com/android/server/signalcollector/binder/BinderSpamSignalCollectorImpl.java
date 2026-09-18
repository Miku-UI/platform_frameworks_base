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

package com.android.server.signalcollector.binder;

import android.annotation.NonNull;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class BinderSpamSignalCollectorImpl extends BinderSpamSignalCollector {
    @GuardedBy("mSubscriptions")
    private final ArrayMap<SubscriptionId,
            Pair<BinderSpamConfigList, OutcomeReceiver<BinderSpamData, Throwable>>> mSubscriptions =
            new ArrayMap<>();
    private volatile ArrayMap<BinderStatsKey, Set<OutcomeReceiver<BinderSpamData, Throwable>>>
            mReceivers = new ArrayMap<>();

    @Override
    public void onBinderStatsReported(BinderCallsStats[] statsArray) {
        if (mReceivers.isEmpty()) {
            return;
        }

        final int serverUid = Binder.getCallingUid();
        BinderSpamData.Builder builder = new BinderSpamData.Builder()
                .setServerUid(serverUid)
                .setTimespan(Duration.ofSeconds(5));
        for (var stats : statsArray) {
            handleData(builder,
                    stats.interfaceDescriptor,
                    stats.aidlMethod,
                    stats.clientUid,
                    (int) stats.callCount);
        }
    }

    @Override
    public void onBinderStatsReported(SingleSecondBinderStats[] statsArray) {
        if (mReceivers.isEmpty()) {
            return;
        }

        final int serverUid = Binder.getCallingUid();
        BinderSpamData.Builder builder = new BinderSpamData.Builder()
                .setServerUid(serverUid)
                .setTimespan(Duration.ofSeconds(1));
        for (var stats : statsArray) {
            handleData(builder,
                    stats.interfaceDescriptor,
                    stats.aidlMethod,
                    stats.clientUid,
                    stats.callCount);
        }
    }

    /**
     * Handle the incoming binder stats data. This method finds the correct receivers that
     * {@link #subscribe(BinderSpamConfigList, OutcomeReceiver)} to this particular data entry and
     * pass to them.
     *
     * @param builder       The builder to build {@link BinderSpamData} with server uid and timespan
     *                      set.
     * @param interfaceName The name of interface of the binder stats data.
     * @param methodName    The name of the method of the binder stats data.
     * @param clientUid     The client UID of the binder stats data.
     * @param callCount     The total call count included in this data entry.
     */
    private void handleData(
            BinderSpamData.Builder builder,
            String interfaceName,
            String methodName,
            int clientUid,
            int callCount) {
        mReceivers.getOrDefault(
                        new BinderStatsKey(interfaceName, methodName),
                        Collections.emptySet())
                .forEach((OutcomeReceiver<BinderSpamData, Throwable> receiver) -> {
                    try {
                        receiver.onResult(
                                builder.setInterfaceName(interfaceName)
                                        .setMethodName(methodName)
                                        .setCallingUid(clientUid)
                                        .setCallCount(callCount)
                                        .build());
                    } catch (Exception e) {
                        receiver.onError(e);
                    }
                });
    }

    @Override
    @NonNull
    public SubscriptionId subscribe(@NonNull BinderSpamConfigList configList,
            @NonNull OutcomeReceiver<BinderSpamData, Throwable> receiver) {
        Objects.requireNonNull(configList);
        Objects.requireNonNull(receiver);
        SubscriptionId id = SubscriptionId.generateNew();
        synchronized (mSubscriptions) {
            mSubscriptions.put(id, new Pair<>(configList, receiver));
            recalculateReceivers();
        }
        return id;
    }

    @Override
    public void unsubscribe(@NonNull SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        synchronized (mSubscriptions) {
            if (mSubscriptions.remove(subscriptionId) == null) {
                throw new IllegalArgumentException(
                        "The provided subscription ID can not be found!");
            }
            recalculateReceivers();
        }
    }

    @GuardedBy("mSubscriptions")
    private void recalculateReceivers() {
        ArrayMap<BinderStatsKey, Set<OutcomeReceiver<BinderSpamData, Throwable>>> receivers =
                new ArrayMap<>();
        for (var subscription : mSubscriptions.values()) {
            for (var binderSpamConfig : subscription.first.getConfigs()) {
                BinderStatsKey key =
                        new BinderStatsKey(
                                binderSpamConfig.getInterfaceName(),
                                binderSpamConfig.getMethodName());
                receivers.computeIfAbsent(key, k -> new ArraySet<>()).add(subscription.second);
            }
        }
        mReceivers = receivers;
    }

    @Override
    public BinderSpamData getData(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestUpdate(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    /** A key object used to identify binder stats. */
    private record BinderStatsKey(String interfaceName, String methodName) {}
}
