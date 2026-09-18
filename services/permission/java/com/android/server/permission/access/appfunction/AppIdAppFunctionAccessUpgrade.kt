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

package com.android.server.permission.access.appfunction

import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_UPGRADE_GRANTED
import android.permission.flags.Flags
import com.android.server.permission.access.MutateStateScope
import com.android.server.pm.pkg.PackageState
import java.lang.IllegalStateException

class AppIdAppFunctionAccessUpgrade(private val policy: AppIdAppFunctionAccessPolicy) {
    fun MutateStateScope.upgradePackageState(
        packageState: PackageState,
        userId: Int,
        version: Int,
    ) {
        if (Flags.appFunctionAccessServiceEnabled()) {
            throw IllegalStateException(
                "The appFunctionAccessServiceEnabled flag is enabled, but the upgrade " +
                    "version check is outdated. Please allocate a new version and remove this " +
                    "check before enabling."
            )
        }
        // Version 18 is the first package version with app functions enabled. When updating to it,
        // pregrant all existing agents and targets
        if (Flags.appFunctionAccessServiceEnabled() && version < 18) {
            with(policy) {
                if (!isValidAgent(packageState, userId)) {
                    return
                }
                if (!packageState.isPrivileged) {
                    // If this package wasn't privileged before version 18, it didn't actually have
                    // AppFunction access
                    return
                }
                for (target in getTargets(userId)) {
                    if (oldState.userStates[userId]?.packageVersions[target] == null) {
                        // This is a new target package, do not grant access to it
                        continue
                    }
                    updateAccessFlags(
                        packageState.appId,
                        userId,
                        newState.externalState.packageStates[target]!!.appId,
                        userId,
                        ACCESS_FLAG_UPGRADE_GRANTED,
                        ACCESS_FLAG_UPGRADE_GRANTED,
                    )
                }
            }
        }
    }
}
