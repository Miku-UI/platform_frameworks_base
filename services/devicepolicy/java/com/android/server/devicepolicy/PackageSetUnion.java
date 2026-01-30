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
import android.app.admin.PackageSetPolicyValue;
import android.app.admin.PolicyValue;
import android.app.admin.StringSetUnion;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

final class PackageSetUnion extends ResolutionMechanism<Set<String>> {

    @Override
    ResolvedPolicy<Set<String>> resolve(
            @NonNull LinkedHashMap<EnforcingAdmin, PolicyValue<Set<String>>> adminPolicies) {
        Objects.requireNonNull(adminPolicies);
        if (adminPolicies.isEmpty()) {
            return null;
        }
        Set<String> unionOfPolicies = new HashSet<>();
        for (PolicyValue<Set<String>> policyValue : adminPolicies.values()) {
            unionOfPolicies.addAll(policyValue.getValue());
        }
        return new ResolvedPolicy<>(new PackageSetPolicyValue(unionOfPolicies),
                // Since it's union, all admins contribute to the final value.
                adminPolicies.keySet());
    }
    /**
     * Checks whether the given policy {@code value} is considered applied
     * based on the {@code resolvedPolicy} and the {@code PackageSetUnion} resolution
     * mechanism.
     *
     * <p> The check passes if all packages found in the {@code value} parameter are also found
     * in the {@code resolvedPolicy} set.
     *
     * @param value the policy value representing the set of packages to check for.
     * @param resolvedPolicy The current resolved policy value, represented by a set of packages.
     *
     * @return true if all packages in {@code value} are found within
     *         {@code resolvedPolicy}, false otherwise.
     */
    @Override
    public boolean isPolicyApplied(@NonNull PolicyValue<Set<String>> value,
            @NonNull PolicyValue<Set<String>> resolvedPolicy) {
        Objects.requireNonNull(value, "Input PolicyValue 'value' cannot be null.");
        Objects.requireNonNull(value, "Input PolicyValue 'resolvedPolicy' "
                + "cannot be null");

        return resolvedPolicy.getValue().containsAll(value.getValue());
    }

    @Override
    StringSetUnion getParcelableResolutionMechanism() {
        return new StringSetUnion();
    }


    @Override
    public String toString() {
        return "PackageSetUnion {}";
    }
}
