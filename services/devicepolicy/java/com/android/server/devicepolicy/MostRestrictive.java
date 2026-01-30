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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.app.admin.PolicyValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class MostRestrictive<V> extends ResolutionMechanism<V> {

    private List<PolicyValue<V>> mMostToLeastRestrictive;

    MostRestrictive(@NonNull List<PolicyValue<V>> mostToLeastRestrictive) {
        mMostToLeastRestrictive = mostToLeastRestrictive;
    }

    @Override
    ResolvedPolicy<V> resolve(
            @NonNull LinkedHashMap<EnforcingAdmin, PolicyValue<V>> adminPolicies) {
        if (adminPolicies.isEmpty()) {
            return null;
        }
        // Check for the policy values in order of most to least restrictive to find the most
        // restrictive value set by admins. The most restrictive value will be applied as the
        // resolved policy value and the admin who has set it will be returned as the
        // contributing admin. If there are multiple admins who has set the most restrictive
        // value, they all will be added to the contributing admins set.
        for (PolicyValue<V> value : mMostToLeastRestrictive) {
            Set<EnforcingAdmin> admins = adminPolicies.entrySet().stream().filter(
                    e -> e.getValue().equals(value)).map(Map.Entry::getKey).collect(
                    Collectors.toSet());
            if (!admins.isEmpty()) {
                return new ResolvedPolicy<>(value, admins);
            }
        }
        // Return first set policy if none can be found in known values
        return new ResolvedPolicy<>(adminPolicies.firstEntry());
    }

    @Override
    PolicyValue<V> resolve(List<PolicyValue<V>> adminPolicies) {
        if (adminPolicies.isEmpty()) {
            return null;
        }
        for (PolicyValue<V> value : mMostToLeastRestrictive) {
            if (adminPolicies.contains(value)) {
                return value;
            }
        }
        // Return first set policy if none can be found in known values
        return adminPolicies.get(0);
    }

    @Override
    android.app.admin.MostRestrictive<V> getParcelableResolutionMechanism() {
        return new android.app.admin.MostRestrictive<V>(mMostToLeastRestrictive);
    }

    @Override
    public String toString() {
        return "MostRestrictive { mMostToLeastRestrictive= " + mMostToLeastRestrictive + " }";
    }
}
