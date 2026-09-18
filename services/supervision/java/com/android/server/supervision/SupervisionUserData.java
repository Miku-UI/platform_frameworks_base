/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.supervision;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.supervision.Policy;
import android.app.supervision.PolicyKey;
import android.os.PersistableBundle;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/** User specific data, used internally by the {@link SupervisionService}. */
public class SupervisionUserData {
    public final @UserIdInt int userId;
    public boolean supervisionEnabled;
    @Nullable public String supervisionAppPackage;
    public boolean supervisionLockScreenEnabled;
    @Nullable public PersistableBundle supervisionLockScreenOptions;
    ArraySet<String> supervisionRoleHolders = new ArraySet<>();
    public boolean escrowTokenRequired;
    final PolicyMap policies = new PolicyMap();

    public SupervisionUserData(@UserIdInt int userId) {
        this.userId = userId;
    }

    void dump(@NonNull IndentingPrintWriter pw) {
        pw.println();
        pw.println("User " + userId + ":");
        pw.increaseIndent();
        pw.println("supervisionEnabled: " + supervisionEnabled);
        pw.println("supervisionAppPackage: " + supervisionAppPackage);
        pw.println("supervisionRoleHolders: " + supervisionRoleHolders);
        pw.println("escrowTokenRequired: " + escrowTokenRequired);
        pw.println("supervisionLockScreenEnabled: " + supervisionLockScreenEnabled);
        pw.println("supervisionLockScreenOptions: " + supervisionLockScreenOptions);
        pw.println("policies list size(): " + policies.size());
        pw.increaseIndent();
        for (PolicyData policyData : policies.values()) {
            pw.println("policy: " + policyData.policy);
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }

    /** Wrapper class for a policy and its associated internal data. */
    static class PolicyData {
        public Policy policy;

        // Whether the policy has a pending notification that needs to be posted.
        public boolean hasPendingNotification;

        PolicyData(Policy policy) {
            this.policy = policy;
            this.hasPendingNotification = false;
        }
    }

    /**
     * A map of policies, keyed by their {@link PolicyKey}.
     *
     * <p>This class extends {@link HashMap} to enforce that the key of the map is always the same
     * as the {@link PolicyKey} of the {@link Policy} object stored in the {@link PolicyData}.
     */
    static class PolicyMap extends HashMap<PolicyKey, PolicyData> {
        @Override
        @Deprecated
        public PolicyData put(PolicyKey k, PolicyData p) {
            return super.put(p.policy.getPolicyKey(), p);
        }

        public void add(Policy policy) {
            PolicyData data = get(policy.getPolicyKey());
            if (data != null) {
                data.policy = policy;
            } else {
                super.put(policy.getPolicyKey(), new PolicyData(policy));
            }
        }

        public long getCurrentVersion(PolicyKey k) {
            PolicyData data = get(k);
            return (data != null) ? data.policy.getVersion() : 0;
        }

        public List<Policy> getPolicies() {
            return values().stream().map(d -> d.policy).collect(Collectors.toList());
        }
    }
}
