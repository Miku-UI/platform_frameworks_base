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

package com.android.settingslib.spa.restricted

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.R
import com.android.settingslib.spa.coroutines.SpaDispatchers
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

@Composable
internal fun rememberRestrictedRepository(): RestrictedRepository {
    val context = LocalContext.current
    val repository = remember {
        checkNotNull(SpaEnvironmentFactory.instance.getRestrictedRepository(context)) {
            "RestrictedRepository not set"
        }
    }
    return repository
}

@Composable
internal fun RestrictedBaseSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    repository: RestrictedRepository,
    ifBlockedOverrideCheckedTo: Boolean? = null,
    content: @Composable (SwitchPreferenceModel, Modifier) -> Unit,
) {
    if (restrictions.isEmpty()) {
        content(model, Modifier)
        return
    }
    val restrictedModeFlow =
        remember(restrictions) {
            repository
                .restrictedModeFlow(restrictions)
                .conflate()
                .flowOn(SpaDispatchers.Default)
        }
    val restrictedMode by restrictedModeFlow.collectAsStateWithLifecycle(initialValue = null)
    val presenter =
        remember(model, ifBlockedOverrideCheckedTo) {
            RestrictedSwitchPreferencePresenter(model, ifBlockedOverrideCheckedTo)
        }
    val restrictedModel by
        remember(presenter, restrictedMode) { presenter.restrictedModelFlow(restrictedMode) }
            .collectAsStateWithLifecycle(initialValue = presenter.indeterminateModel)

    RestrictionWrapper(restrictedMode, restrictedModel, content)
}

@Composable
private fun RestrictionWrapper(
    restrictedMode: RestrictedMode?,
    restrictedModel: SwitchPreferenceModel,
    content: @Composable (SwitchPreferenceModel, Modifier) -> Unit,
) {
    val modifier =
        when (restrictedMode) {
            is BlockedWithDetails -> {
                val statusDescription = stringResource(R.string.spa_unavailable)
                Modifier.clickable(
                        onClickLabel = stringResource(R.string.spa_learn_more),
                        role = Role.Switch,
                        onClick = { restrictedMode.showDetails() },
                    )
                    .semantics {
                        contentDescription = statusDescription
                        toggleableState = toggleableState(restrictedModel.checked())
                    }
            }

            else -> Modifier
        }
    content(restrictedModel, modifier)
}

private fun toggleableState(value: Boolean?) =
    when (value) {
        true -> ToggleableState.On
        false -> ToggleableState.Off
        null -> ToggleableState.Indeterminate
    }
