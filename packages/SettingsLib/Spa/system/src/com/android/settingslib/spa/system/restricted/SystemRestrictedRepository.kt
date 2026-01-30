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

package com.android.settingslib.spa.system.restricted

import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.MANAGE_USERS
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.os.bundleOf
import com.android.settingslib.spa.flow.broadcastReceiverFlow
import com.android.settingslib.spa.restricted.NoRestricted
import com.android.settingslib.spa.restricted.RestrictedMode
import com.android.settingslib.spa.restricted.RestrictedRepository
import com.android.settingslib.spa.restricted.Restrictions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * To use this, please register in [com.android.settingslib.spa.framework.common.SpaEnvironment].
 *
 * Would either need [MANAGE_USERS] or [INTERACT_ACROSS_USERS] permission to call, otherwise always
 * no restricted.
 */
class SystemRestrictedRepository(private val context: Context) : RestrictedRepository {

    private val userManager = context.getSystemService(UserManager::class.java)

    /**
     * Gets the restricted mode.
     *
     * Would either need [MANAGE_USERS] or [INTERACT_ACROSS_USERS] permission to call, otherwise
     * always no restricted.
     */
    @RequiresPermission(anyOf = [MANAGE_USERS, INTERACT_ACROSS_USERS])
    override fun restrictedModeFlow(restrictions: Restrictions): Flow<RestrictedMode> {
        check(restrictions is SystemRestrictions)
        return context
            .broadcastReceiverFlow(IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED))
            .map {}
            .onStart { emit(Unit) }
            .map { getRestrictedMode(restrictions) }
    }

    @RequiresPermission(anyOf = [MANAGE_USERS, INTERACT_ACROSS_USERS])
    private fun getRestrictedMode(restrictions: SystemRestrictions): RestrictedMode {
        val userRestrictions = getUserRestrictions()
        for (key in restrictions.keys) {
            if (userRestrictions.getBoolean(key)) return SystemBlockedByAdmin(context, key)
        }
        return NoRestricted
    }

    @RequiresPermission(anyOf = [MANAGE_USERS, INTERACT_ACROSS_USERS])
    private fun getUserRestrictions(): Bundle =
        try {
            userManager?.userRestrictions ?: bundleOf()
        } catch (e: Exception) {
            Log.w(TAG, "userManager.getUserRestrictions() failed", e)
            bundleOf()
        }

    private companion object {
        private const val TAG = "SystemRestrictedRepo"
    }
}
