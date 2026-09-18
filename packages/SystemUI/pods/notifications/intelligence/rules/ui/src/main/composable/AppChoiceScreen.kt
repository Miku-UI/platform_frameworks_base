/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders a fullscreen page to select 1 or more apps matching a search string. */
@Composable
fun AppChoiceScreen(viewState: RulesScreenViewState.EditField.Apps, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewState.viewModel

    val initialSelection: List<AppModel> =
        when (val apps = viewModel.rule.filter.includedApps) {
            is RuleValue.Specified -> apps.value.apps
            is RuleValue.Ambiguous -> emptyList()
            null -> emptyList()
        }

    // All apps on the device. Null while the apps are being fetched.
    val allApps by
        produceState<List<AppModel>?>(initialValue = null, key1 = viewModel) {
            value = viewModel.fetchInstalledApps(context)
        }

    EditScreenWithSearch(
        title = stringResource(R.string.notification_rules_field_app),
        initialSelection = initialSelection,
        onSelectionSaved = { viewState.onAppsSaved(it) },
        onDismissRequest = onDismissRequest,
        allSearchResults = allApps,
        fetchSearchResults = { query -> getSearchResults(allApps, query) },
        sortKey = { it.label },
        uniqueId = { it.uniqueId },
        icon = { AppIcon(it) },
        text = { it.label },
    )
}

@Composable
fun AppIcon(appModel: AppModel, modifier: Modifier = Modifier) {
    Image(
        rememberDrawablePainter(appModel.icon),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
    )
}

/** Returns the subset of [allApps] that match [query]. */
private fun getSearchResults(allApps: List<AppModel>?, query: String): List<AppModel>? {
    return allApps?.filter { it.label.contains(query, ignoreCase = true) }
}
