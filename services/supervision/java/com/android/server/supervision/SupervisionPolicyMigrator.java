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

package com.android.server.supervision;

import android.annotation.NonNull;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.util.Slog;

import com.android.server.pm.UserManagerInternal;

import java.util.List;

class SupervisionPolicyMigrator {
    private final Context mContext;
    private final UserManagerInternal mUserManagerInternal;
    private final DevicePolicyManagerInternal mDpmi;

    SupervisionPolicyMigrator(
            Context context,
            @NonNull UserManagerInternal userManagerInternal,
            @NonNull DevicePolicyManagerInternal dpmi) {
        mContext = context;
        mUserManagerInternal = userManagerInternal;
        mDpmi = dpmi;
    }

    boolean upgrade(int fromVersion, int toVersion) {
        if (fromVersion >= toVersion) {
            return true;
        }
        Slog.i(
                SupervisionLog.TAG,
                "Upgrading supervision settings from " + fromVersion + " to " + toVersion);

        if (fromVersion < 1) {
            if (!upgradeToVersion1()) {
                return false;
            }
        }
        return true;
    }

    private boolean upgradeToVersion1() {
        // Clear "application hidden" DPM restrictions set by each of the current Supervision
        // Role holders.
        List<UserInfo> users = mUserManagerInternal.getUsers(false /*excludeDying*/);
        for (UserInfo user : users) {
            // Clear legacy DPM policies for both supervision roles across all relevant identities.
            mDpmi.clearHiddenApplicationsForRole(RoleManager.ROLE_SUPERVISION, user.id);
            mDpmi.clearHiddenApplicationsForRole(RoleManager.ROLE_SYSTEM_SUPERVISION, user.id);
        }
        return true;
    }
}
