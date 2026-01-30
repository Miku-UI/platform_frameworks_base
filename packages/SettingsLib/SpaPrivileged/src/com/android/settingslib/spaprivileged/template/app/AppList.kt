/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spaprivileged.template.app

import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.settingslib.spa.framework.compose.LifecycleEffect
import com.android.settingslib.spa.framework.compose.LogCompositions
import com.android.settingslib.spa.framework.compose.TimeMeasurer.Companion.rememberTimeMeasurer
import com.android.settingslib.spa.framework.compose.rememberLazyListStateAndHideKeyboardWhenStartScroll
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.widget.preference.ZeroStatePreference
import com.android.settingslib.spa.widget.ui.CategoryTitle
import com.android.settingslib.spa.widget.ui.LazyCategory
import com.android.settingslib.spa.widget.ui.PlaceholderTitle
import com.android.settingslib.spa.widget.ui.Spinner
import com.android.settingslib.spa.widget.ui.SpinnerOption
import com.android.settingslib.spaprivileged.R
import com.android.settingslib.spaprivileged.framework.compose.DisposableBroadcastReceiverAsUser
import com.android.settingslib.spaprivileged.model.app.AppEntry
import com.android.settingslib.spaprivileged.model.app.AppListData
import com.android.settingslib.spaprivileged.model.app.AppListModel
import com.android.settingslib.spaprivileged.model.app.AppListViewModel
import com.android.settingslib.spaprivileged.model.app.AppRecord
import com.android.settingslib.spaprivileged.model.app.IAppListViewModel
import com.android.settingslib.spaprivileged.model.app.userId
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "AppList"
private const val CONTENT_TYPE_HEADER = "header"

/** The config used to load the App List. */
data class AppListConfig(
    val userIds: List<Int>,
    val showInstantApps: Boolean,
    val matchAnyUserForAdmin: Boolean,
)

data class AppListState(val showSystem: () -> Boolean, val searchQuery: () -> String)

data class AppListInput<T : AppRecord>(
    val config: AppListConfig,
    val listModel: AppListModel<T>,
    val state: AppListState,
    val header: @Composable () -> Unit,
    val noItemMessage: String? = null,
    val bottomPadding: Dp,
    val noAppInfo: NoAppInfo = NoAppInfo(),
)

data class NoAppInfo(
    val icon: ImageVector = Icons.Filled.Apps,
    val title: Int = R.string.no_applications,
    val description: Int? = null,
)

/**
 * The template to render an App List.
 *
 * This UI element will take the remaining space on the screen to show the App List.
 */
@Composable
fun <T : AppRecord> AppListInput<T>.AppList(noAppInfo: NoAppInfo = NoAppInfo()) {
    AppListImpl(noAppInfo) { rememberViewModel(config, listModel, state) }
}

@Composable
internal fun <T : AppRecord> AppListInput<T>.AppListImpl(
    noAppInfo: NoAppInfo = NoAppInfo(),
    viewModelSupplier: @Composable () -> IAppListViewModel<T>,
) {
    LogCompositions(TAG, config.userIds.toString())
    val viewModel = viewModelSupplier()
    Column(Modifier.fillMaxSize()) {
        val optionsState = viewModel.spinnerOptionsFlow.collectAsStateWithLifecycle(null)
        SpinnerOptions(optionsState, viewModel.optionFlow)
        val appListData = viewModel.appListDataFlow.collectAsStateWithLifecycle(null)
        listModel.AppListWidget(appListData, header, bottomPadding, noItemMessage, noAppInfo)
    }
}

@Composable
private fun SpinnerOptions(
    optionsState: State<List<SpinnerOption>?>,
    optionFlow: MutableStateFlow<Int?>,
) {
    val options = optionsState.value
    LaunchedEffect(options) {
        if (options != null && !options.any { it.id == optionFlow.value }) {
            // Reset to first option if the available options changed, and the current selected one
            // does not in the new options.
            optionFlow.value = options.let { it.firstOrNull()?.id ?: -1 }
        }
    }
    if (options != null) {
        Spinner(options, optionFlow.collectAsState().value) { optionFlow.value = it }
    }
}

@Composable
private fun <T : AppRecord> AppListModel<T>.AppListWidget(
    appListData: State<AppListData<T>?>,
    header: @Composable () -> Unit,
    bottomPadding: Dp,
    noItemMessage: String?,
    noAppInfo: NoAppInfo = NoAppInfo(),
) {
    val timeMeasurer = rememberTimeMeasurer(TAG)
    appListData.value?.let { (list, option) ->
        timeMeasurer.logFirst("app list first loaded")
        if (list.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                header()

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSpaExpressiveEnabled) {
                        ZeroStatePreference(noAppInfo.icon, stringResource(noAppInfo.title))
                    } else {
                        PlaceholderTitle(noItemMessage ?: stringResource(R.string.no_applications))
                    }
                }
            }
            return
        }
        if (isSpaExpressiveEnabled) {
            LazyCategory(
                count = list.size,
                key = { index -> list[index].record.itemKey(option) },
                bottomPadding = bottomPadding,
                state = rememberLazyListStateAndHideKeyboardWhenStartScroll(),
                header = header,
                groupTitle = { index -> getGroupTitle(option, list[index].record) },
            ) { index: Int ->
                val appEntry = list[index]
                val summary = getSummary(option, appEntry.record) ?: { "" }
                remember(appEntry) { AppListItemModel(appEntry.record, appEntry.label, summary) }
                    .AppItem()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListStateAndHideKeyboardWhenStartScroll(),
                contentPadding = PaddingValues(bottom = bottomPadding),
            ) {
                item(contentType = CONTENT_TYPE_HEADER) { header() }

                items(count = list.size, key = { list[it].record.itemKey(option) }) {
                    remember(list) { getGroupTitleIfFirst(option, list, it) }
                        ?.let { group -> CategoryTitle(title = group) }

                    val appEntry = list[it]
                    val summary = getSummary(option, appEntry.record) ?: { "" }
                    remember(appEntry) {
                            AppListItemModel(appEntry.record, appEntry.label, summary)
                        }
                        .AppItem()
                }
            }
        }
    }
}

private fun <T : AppRecord> T.itemKey(option: Int) = listOf(option, app.packageName, app.userId)

/** Returns group title if this is the first item of the group. */
private fun <T : AppRecord> AppListModel<T>.getGroupTitleIfFirst(
    option: Int,
    list: List<AppEntry<T>>,
    index: Int,
): String? =
    getGroupTitle(option, list[index].record)?.takeIf {
        index == 0 || it != getGroupTitle(option, list[index - 1].record)
    }

@Composable
private fun <T : AppRecord> rememberViewModel(
    config: AppListConfig,
    listModel: AppListModel<T>,
    state: AppListState,
): AppListViewModel<T> {
    val viewModel: AppListViewModel<T> = viewModel(key = config.userIds.toString())
    viewModel.appListConfig.setIfAbsent(config)
    viewModel.listModel.setIfAbsent(listModel)
    viewModel.showSystem.Sync(state.showSystem)
    viewModel.searchQuery.Sync(state.searchQuery)

    LifecycleEffect(onStart = { viewModel.reloadApps() })
    val intentFilter =
        IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
    for (userId in config.userIds) {
        DisposableBroadcastReceiverAsUser(
            intentFilter = intentFilter,
            userHandle = UserHandle.of(userId),
        ) {
            viewModel.reloadApps()
        }
    }
    return viewModel
}
