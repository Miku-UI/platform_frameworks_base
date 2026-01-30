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
package com.android.systemui.statusbar.systemstatusicons.profile.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.policy.profile.domain.interactor.ManagedProfileInteractor
import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * View model for the managed profile system status icon. Emits a managed profile icon when a
 * managed profile is active, null otherwise.
 */
class ManagedProfileIconViewModel
@AssistedInject
constructor(@Assisted private val context: Context, interactor: ManagedProfileInteractor) :
    SystemStatusIconViewModel.Default, ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("ManagedProfileIconViewModel.hydrator")

    override val slotName: String =
        context.getString(com.android.internal.R.string.status_bar_managed_profile)

    private val profileInfo by
        hydrator.hydratedStateOf(
            traceName = null,
            initialValue = null,
            source = interactor.currentProfileInfo,
        )

    override val visible: Boolean
        get() = profileInfo != null

    override val icon: Icon?
        get() = profileInfo?.toIcon()

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun ProfileInfo.toIcon(): Icon {
        val contentDescriptionString =
            contentDescription.takeUnless { it.isNullOrBlank() }
                ?: context.resources.getString(
                    com.android.systemui.res.R.string.accessibility_managed_profile
                )

        return Icon.Resource(
            resId = iconResId,
            contentDescription = ContentDescription.Loaded(contentDescriptionString),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): ManagedProfileIconViewModel
    }
}
