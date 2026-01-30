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

package com.android.systemui.qs.panels.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.icons.BaseIconFactory
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

interface AppIconRepository {
    fun loadIcon(applicationInfo: ApplicationInfo): Icon.Loaded?

    interface Factory {
        fun create(): AppIconRepository
    }
}

class AppIconRepositoryImpl
@AssistedInject
constructor(@ShadeDisplayAware private val context: Context) : AppIconRepository {
    private val appIconFactory: BaseIconFactory = buildAppIconFactory()

    override fun loadIcon(applicationInfo: ApplicationInfo): Icon.Loaded? {
        return applicationInfo.loadUnbadgedIcon(context.packageManager)?.let {
            appIconFactory
                .createBadgedIconBitmap(it)
                .newIcon(context)
                .asIcon(ContentDescription.Loaded(null))
        }
    }

    private fun buildAppIconFactory(): BaseIconFactory {
        val res = context.resources
        val iconSize = res.getDimensionPixelSize(R.dimen.qs_edit_mode_app_icon)
        return BaseIconFactory(context, res.configuration.densityDpi, iconSize)
    }

    @AssistedFactory
    interface Factory : AppIconRepository.Factory {
        override fun create(): AppIconRepositoryImpl
    }
}
