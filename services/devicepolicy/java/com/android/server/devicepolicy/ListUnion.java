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
import android.app.admin.ListOfStringPolicyValue;
import android.app.admin.PolicyValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared class to support list union for different types.
 *
 * <p>Subclasses to store different value types are present as static variables.
 */
public abstract class ListUnion<T> extends ResolutionMechanism<List<T>> {

    public static final ListUnion<String> STRING =
            new ListUnion<String>() {

                @Override
                public PolicyValue<List<String>> createPolicyValue(@NonNull List<String> values) {
                    return new ListOfStringPolicyValue(values);
                }

                @Override
                public String toString() {
                    return "ListUnion<String> {}";
                }
            };

    // TODO(b/482394284): Change String to Package once the Package type is introduced.
    public static final ListUnion<String> PACKAGE =
            new ListUnion<String>() {

                @Override
                public PolicyValue<List<String>> createPolicyValue(@NonNull List<String> values) {
                    return new ListOfStringPolicyValue(values);
                }

                @Override
                public String toString() {
                    return "ListUnion<Package> {}";
                }
            };

    @Override
    ResolvedPolicy<List<T>> resolve(
            @NonNull LinkedHashMap<EnforcingAdmin, PolicyValue<List<T>>> adminPolicies) {
        Objects.requireNonNull(adminPolicies);
        if (adminPolicies.isEmpty()) {
            return null;
        }
        Set<T> unionOfPolicies =
                adminPolicies.values().stream()
                        .flatMap(policyValue -> policyValue.getValue().stream())
                        .collect(Collectors.toSet());
        return new ResolvedPolicy<>(
                createPolicyValue(new ArrayList<>(unionOfPolicies)),
                // Since it's union, all admins contribute to the final value.
                adminPolicies.keySet());
    }

    public abstract PolicyValue<List<T>> createPolicyValue(List<T> values);

    /**
     * Checks whether the given policy {@code value} is considered applied based on the {@code
     * resolvedPolicy} and the {@code ListUnion} resolution mechanism.
     *
     * <p>The check passes if all strings found in the {@code value} parameter are also found in the
     * {@code resolvedPolicy} list.
     *
     * @param value the policy value representing the list of strings to check for.
     * @param resolvedPolicy The current resolved policy value, represented by a list of strings.
     * @return true if all strings in {@code value} are found within {@code resolvedPolicy}, false
     *     otherwise.
     */
    @Override
    public boolean isPolicyApplied(
            @NonNull PolicyValue<List<T>> value, @NonNull PolicyValue<List<T>> resolvedPolicy) {
        Objects.requireNonNull(value, "Input PolicyValue 'value' cannot be null.");
        Objects.requireNonNull(
                resolvedPolicy, "Input PolicyValue 'resolvedPolicy' " + "cannot be null");

        return resolvedPolicy.getValue().containsAll(value.getValue());
    }

    @Override
    android.app.admin.ListUnion<T> getParcelableResolutionMechanism() {
        return android.app.admin.ListUnion.INSTANCE;
    }

    @Override
    public String toString() {
        return "ListUnion {}";
    }
}
