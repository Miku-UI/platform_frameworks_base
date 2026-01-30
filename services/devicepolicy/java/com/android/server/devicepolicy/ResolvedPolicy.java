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

package com.android.server.devicepolicy;

import android.app.admin.PolicyValue;

import java.util.Map;
import java.util.Set;

class ResolvedPolicy<V> {
    private final PolicyValue<V> mResolvedPolicyValue;
    private final Set<EnforcingAdmin> mContributingAdmins;

    ResolvedPolicy(PolicyValue<V> resolvedPolicyValue, Set<EnforcingAdmin> admins) {
        this.mResolvedPolicyValue = resolvedPolicyValue;
        this.mContributingAdmins = admins;
    }

    ResolvedPolicy(Map.Entry<EnforcingAdmin, PolicyValue<V>> entry) {
        this(entry.getValue(), Set.of(entry.getKey()));
    }

    PolicyValue<V> getResolvedPolicyValue() {
        return mResolvedPolicyValue;
    }

    /*
     * Returns the admins who contributed to the resolved policy value.
     */
    Set<EnforcingAdmin> getContributingAdmins() {
        return mContributingAdmins;
    }
}
