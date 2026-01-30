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

package com.android.systemui.keyboard.shortcut.data.repository

import android.content.Context
import android.content.pm.LauncherActivityInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyboard.shortcut.shared.model.Shortcut
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategory
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutHelperState
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutIcon
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutSubCategory
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@SysUISingleton
class AppsShortcutCategoryRepository
@Inject
constructor(
    userVisibleAppsRepository: UserVisibleAppsRepository,
    context: Context,
    stateRepository: ShortcutHelperStateRepository,
) : ShortcutCategoriesRepository {

    override val categories: Flow<List<ShortcutCategory>> =
        stateRepository.state.combine(userVisibleAppsRepository.userVisibleApps) {
            state,
            userVisibleApps ->
            if (state is ShortcutHelperState.Inactive || userVisibleApps.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ShortcutCategory(
                        ShortcutCategoryType.AppCategories,
                        ShortcutSubCategory(
                            label =
                                context.getString(R.string.keyboard_shortcut_group_applications),
                            shortcuts = convertLauncherActivityInfoToShortcutModel(userVisibleApps),
                        ),
                    )
                )
            }
        }

    private fun convertLauncherActivityInfoToShortcutModel(
        activityInfos: List<LauncherActivityInfo>
    ): List<Shortcut> {
        return activityInfos.map { activityInfo ->
            Shortcut(
                label = activityInfo.label.toString(),
                commands = emptyList(),
                icon =
                    ShortcutIcon(
                        packageName = activityInfo.applicationInfo.packageName,
                        resourceId = activityInfo.applicationInfo.iconRes,
                    ),
                pkgName = activityInfo.componentName.packageName,
                className = activityInfo.componentName.className,
            )
        }
    }
}
