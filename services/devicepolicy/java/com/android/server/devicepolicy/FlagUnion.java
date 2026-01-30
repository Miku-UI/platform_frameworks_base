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
import android.app.admin.IntegerPolicyValue;
import android.app.admin.PolicyValue;

import java.util.LinkedHashMap;
import java.util.Objects;

final class FlagUnion extends ResolutionMechanism<Integer> {

    @Override
    ResolvedPolicy<Integer> resolve(
            @NonNull LinkedHashMap<EnforcingAdmin, PolicyValue<Integer>> adminPolicies) {
        Objects.requireNonNull(adminPolicies);
        if (adminPolicies.isEmpty()) {
            return null;
        }

        Integer unionOfPolicies = 0;
        for (PolicyValue<Integer> policyValue : adminPolicies.values()) {
            unionOfPolicies |= policyValue.getValue();
        }
        return new ResolvedPolicy<>(new IntegerPolicyValue(unionOfPolicies),
                // Since it's union, all admins contribute to the final value.
                adminPolicies.keySet());
    }

    /**
     * Checks whether the given policy {@code value} is considered applied
     * based on the {@code resolvedPolicy} and the {@code FlagUnion} resolution
     * mechanism.
     *
     * <p> This mechanism treats the Integer values as bitmasks. The check passes
     *     if all flags set in the {@code value} parameter are also set in the
     *     {@code resolvedPolicy}.
     *
     * @param value the policy value representing the flag(s) to check for.
     * @param resolvedPolicy The current resolved policy value, representing
     *                       the bitmask of all enabled flags.
     * @return true if all flags in {@code value} are set within
     *         {@code resolvedPolicy}, false otherwise.
     */
    @Override
    public boolean isPolicyApplied(@NonNull PolicyValue<Integer> value,
            @NonNull PolicyValue<Integer> resolvedPolicy) {
        Objects.requireNonNull(value, "Input PolicyValue 'value' cannot be null.");
        Objects.requireNonNull(resolvedPolicy, "Input PolicyValue 'resolvedPolicy'"
                + " cannot be null.");

        return ((resolvedPolicy.getValue() & value.getValue()) == value.getValue());
    }

    @Override
    android.app.admin.FlagUnion getParcelableResolutionMechanism() {
        return android.app.admin.FlagUnion.FLAG_UNION;
    }

    @Override
    public String toString() {
        return "IntegerUnion {}";
    }
}
