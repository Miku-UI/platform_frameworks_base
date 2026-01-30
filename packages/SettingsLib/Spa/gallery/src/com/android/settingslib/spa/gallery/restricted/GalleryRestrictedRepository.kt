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

package com.android.settingslib.spa.gallery.restricted

import android.content.Context
import android.content.Intent
import com.android.settingslib.spa.restricted.Blocked.SwitchPreferenceOverrides
import com.android.settingslib.spa.restricted.BlockedWithDetails
import com.android.settingslib.spa.restricted.NoRestricted
import com.android.settingslib.spa.restricted.RestrictedMode
import com.android.settingslib.spa.restricted.RestrictedRepository
import com.android.settingslib.spa.restricted.Restrictions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class GalleryRestrictedRepository(private val context: Context) : RestrictedRepository {
    override fun restrictedModeFlow(restrictions: Restrictions): Flow<RestrictedMode> {
        check(restrictions is GalleryRestrictions)
        return enableRestrictionsFlow.map { enableRestrictions ->
            if (enableRestrictions && restrictions.isRestricted) {
                object : BlockedWithDetails {
                    override val switchPreferenceOverridesFlow =
                        combine(summaryOnFlow, summaryOffFlow) { summaryOn, summaryOff ->
                            SwitchPreferenceOverrides(
                                summaryOn = summaryOn,
                                summaryOff = summaryOff,
                            )
                        }

                    override fun showDetails() {
                        context.startActivity(Intent("android.settings.SHOW_ADMIN_SUPPORT_DETAILS"))
                    }
                }
            } else {
                NoRestricted
            }
        }
    }

    companion object {
        var enableRestrictionsFlow = MutableStateFlow(true)
        var summaryOnFlow = MutableStateFlow("Force on")
        var summaryOffFlow = MutableStateFlow("Force off")
    }
}
