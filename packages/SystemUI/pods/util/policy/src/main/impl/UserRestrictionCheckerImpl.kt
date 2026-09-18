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

package com.android.systemui.util.policy.impl

import android.content.Context
import com.android.settingslib.RestrictedLockUtils
import com.android.settingslib.RestrictedLockUtilsInternal
import com.android.systemui.util.policy.UserRestrictionChecker
import javax.inject.Inject

/** Proxy to call [RestrictedLockUtilsInternal] */
public class UserRestrictionCheckerImpl @Inject constructor() : UserRestrictionChecker {
    override fun checkIfRestrictionEnforced(
        context: Context,
        userRestriction: String,
        userId: Int,
    ): RestrictedLockUtils.EnforcedAdmin? {
        return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
            context,
            userRestriction,
            userId,
        )
    }

    override fun hasBaseUserRestriction(
        context: Context,
        userRestriction: String,
        userId: Int,
    ): Boolean {
        return RestrictedLockUtilsInternal.hasBaseUserRestriction(context, userRestriction, userId)
    }
}
