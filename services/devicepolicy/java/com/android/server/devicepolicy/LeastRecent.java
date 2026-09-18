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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.app.admin.PolicyValue;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LeastRecent<V> extends ResolutionMechanism<V> {

    @Override
    ResolvedPolicy<V> resolve(
            @NonNull LinkedHashMap<EnforcingAdmin, PolicyValue<V>> adminPolicies) {
        Map.Entry<EnforcingAdmin, PolicyValue<V>> firstEntry = adminPolicies.firstEntry();
        return firstEntry == null ? null : new ResolvedPolicy<V>(firstEntry);
    }

    @Override
    public android.app.admin.LeastRecent<V> getParcelableResolutionMechanism() {
        return new android.app.admin.LeastRecent<V>();
    }

    @Override
    public String toString() {
        return "LeastRecent {}";
    }
}
