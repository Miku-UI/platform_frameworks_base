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

import android.os.OutcomeReceiver;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.os.profiling.anomaly.collector.SignalCollector;
import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.util.Objects;

/**
 * A signal collector class dedicated to collect binder spam signals.
 */
public final class BinderSpamSignalCollector
        implements SignalCollector<BinderSpamConfig, BinderSpamData> {
    @GuardedBy("mConfigs")
    private final ArrayMap<SubscriptionId, BinderSpamConfig> mConfigs = new ArrayMap<>();
    @GuardedBy("mConfigs")
    private final ArrayMap<SubscriptionId, OutcomeReceiver<BinderSpamData, Throwable>>
            mReceivers = new ArrayMap<>();

    /**
     * Report a binder spam data based on the configurations.
     */
    public void onBinderSpamDataReported(BinderSpamData data) {
        synchronized (mConfigs) {
            for (SubscriptionId id : mConfigs.keySet()) {
                if (shouldReportToAnomalyDetector(data, mConfigs.get(id))) {
                    mReceivers.get(id).onResult(data);
                }
            }
        }
    }

    private static boolean shouldReportToAnomalyDetector(
            BinderSpamData data,
            BinderSpamConfig config) {
        return TextUtils.equals(data.getInterfaceName(), config.getInterfaceName())
                && TextUtils.equals(data.getMethodName(), config.getMethodName());
    }

    @Override
    public SubscriptionId subscribe(BinderSpamConfig config,
            OutcomeReceiver<BinderSpamData, Throwable> listener) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(listener);
        SubscriptionId id = SubscriptionId.generateNew();
        synchronized (mConfigs) {
            mConfigs.put(id, config);
            mReceivers.put(id, listener);
        }
        return id;
    }

    @Override
    public void unsubscribe(SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        synchronized (mConfigs) {
            mConfigs.remove(subscriptionId);
            mReceivers.remove(subscriptionId);
        }
    }

    @Override
    public BinderSpamData getData(SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestUpdate(SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }
}
