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

package com.android.systemui.statusbar.notification.row.domain.interactor

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.row.dagger.BundleRowScope
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.BundleRepository
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider
import com.android.systemui.util.icuMessageFormat
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Provides functionality for UI to interact with a Notification Bundle. */
@BundleRowScope
class BundleInteractor
@Inject
constructor(
    private val repository: BundleRepository,
    private val appIconProvider: AppIconProvider,
    private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val systemClock: SystemClock,
) {
    @get:StringRes
    val titleText: Int
        get() = repository.titleText

    val numberOfChildren: Int?
        get() = repository.numberOfChildren

    @get:DrawableRes
    val bundleIcon: Int
        get() = repository.bundleIcon

    val numberOfChildrenContentDescription: String
        get() =
            icuMessageFormat(
                context.resources,
                R.string.notification_bundle_header_counter,
                numberOfChildren ?: 0,
            )

    val headerContentDescription: String
        get() =
            context.resources.getString(
                R.string.notification_bundle_header_joined_description,
                context.resources.getString(titleText),
                numberOfChildrenContentDescription,
            )

    /** Filters the list of AppData based on time of last collapse by user. */
    private fun filterByCollapseTime(
        rawAppDataList: List<AppData>,
        collapseTime: Long,
    ): List<AppData> {
        // Always show app icons in bundle header
        // and keep filtering infra for now
        return rawAppDataList
    }

    /** Converts a list of AppData to a list of Drawables by fetching icons */
    private suspend fun convertAppDataToDrawables(
        filteredAppDataList: List<AppData>
    ): List<Drawable> {
        return withContext(backgroundDispatcher) {
            filteredAppDataList.asSequence().distinct().mapNotNull(::fetchAppIcon).take(3).toList()
        }
    }

    /**
     * A cold flow of app icon [Drawable]s. It filters AppData based on the bundle's last expansion
     * time and then fetches icons. Takes up to 3 distinct app icons for preview.
     */
    val previewIcons: Flow<List<Drawable>> =
        combine(repository.appDataList, snapshotFlow { repository.lastCollapseTime }) {
                appList: List<AppData>,
                collapseTime: Long ->
                filterByCollapseTime(appList, collapseTime)
            }
            .mapLatestConflated { filteredList: List<AppData> ->
                convertAppDataToDrawables(filteredList)
            }

    var state: MutableSceneTransitionLayoutState? by repository::state

    var composeScope: CoroutineScope? = null

    fun setExpansionState(isExpanded: Boolean) {
        state?.setTargetScene(
            if (isExpanded) BundleHeader.Scenes.Expanded else BundleHeader.Scenes.Collapsed,
            composeScope!!,
        )
        if (!isExpanded) {
            repository.lastCollapseTime = systemClock.uptimeMillis()
        }
    }

    fun setTargetScene(scene: SceneKey) {
        state?.setTargetScene(scene, composeScope!!)

        // [setTargetScene] does not immediately update [currentScene] so we must check [scene]
        if (scene == BundleHeader.Scenes.Collapsed) {
            repository.lastCollapseTime = systemClock.uptimeMillis()
        }
    }

    private fun fetchAppIcon(appData: AppData): Drawable? {
        return try {
            appIconProvider.getOrFetchAppIcon(
                packageName = appData.packageName,
                userHandle = appData.user,
                instanceKey = "bundle:${repository.bundleType}",
            )
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "Failed to load app icon for ${appData.packageName}", e)
            null
        }
    }

    companion object {
        private const val TAG = "BundleInteractor"
    }
}
