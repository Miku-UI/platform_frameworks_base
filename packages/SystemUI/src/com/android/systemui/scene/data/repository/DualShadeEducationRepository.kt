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

package com.android.systemui.scene.data.repository

import android.annotation.UserIdInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.unit.IntRect
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.systemui.common.data.datastore.DataStoreWrapper
import com.android.systemui.common.data.datastore.DataStoreWrapperFactory
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.scene.data.model.DualShadeEducationImpressionModel
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class DualShadeEducationRepository
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val dataStoreWrapperFactory: DataStoreWrapperFactory,
) {

    /**
     * Impression data for overlays and tooltips.
     *
     * This should be used by downstream logic to decide whether educational tooltips should still
     * be shown.
     */
    var impressions: DualShadeEducationImpressionModel by
        mutableStateOf(DualShadeEducationImpressionModel())
        private set

    private val _elementBounds: SnapshotStateMap<DualShadeEducationElement, IntRect> =
        mutableStateMapOf(
            DualShadeEducationElement.Notifications to IntRect.Zero,
            DualShadeEducationElement.QuickSettings to IntRect.Zero,
        )
    val elementBounds: Map<DualShadeEducationElement, IntRect>
        get() = _elementBounds

    private var dataStore: DataStoreWrapper? = null
    private var dataStoreJob: Job? = null

    /**
     * Keeps the repository data up-to-date for the user identified by [selectedUserId]; runs until
     * cancelled by the caller.
     */
    suspend fun activateFor(@UserIdInt selectedUserId: Int) {
        dataStoreJob?.cancelAndJoin()
        val dataStoreScope =
            CoroutineScope(
                context =
                    backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job])
            )
        dataStoreJob = dataStoreScope.coroutineContext[Job]
        val newDataStore =
            dataStoreWrapperFactory.create(
                dataStoreFileName = DATA_STORE_FILE_NAME,
                userId = selectedUserId,
                scope = dataStoreScope,
            )
        dataStore = newDataStore

        dataStoreScope.launch {
            repeatWhenPrefsChange(newDataStore) { prefs ->
                if (!isActive) {
                    return@repeatWhenPrefsChange
                }

                impressions =
                    DualShadeEducationImpressionModel(
                        everShownNotificationsShade =
                            prefs[Keys.EverShownNotificationsShade.name].asBoolean(),
                        everShownQuickSettingsShade =
                            prefs[Keys.EverShownQuickSettingsShade.name].asBoolean(),
                        everShownNotificationsTooltip =
                            prefs[Keys.EverShownNotificationsTooltip.name].asBoolean(),
                        everShownQuickSettingsTooltip =
                            prefs[Keys.EverShownQuickSettingsTooltip.name].asBoolean(),
                    )
            }
        }
    }

    suspend fun setEverShownNotificationsShade(value: Boolean) {
        persist(Keys.EverShownNotificationsShade, value)
    }

    suspend fun setEverShownQuickSettingsShade(value: Boolean) {
        persist(Keys.EverShownQuickSettingsShade, value)
    }

    suspend fun setEverShownNotificationsTooltip(value: Boolean) {
        persist(Keys.EverShownNotificationsTooltip, value)
    }

    suspend fun setEverShownQuickSettingsTooltip(value: Boolean) {
        persist(Keys.EverShownQuickSettingsTooltip, value)
    }

    fun setElementBounds(element: DualShadeEducationElement, bounds: IntRect) {
        _elementBounds[element] = bounds
    }

    /** Each time data store data changes, passes it to the given [receiver]. */
    private suspend fun repeatWhenPrefsChange(
        dataStore: DataStoreWrapper,
        receiver: (Map<String, String>) -> Unit,
    ) {
        dataStore.data.collect { prefs -> receiver(prefs.mapValues { it.value }) }
    }

    /** Persists the given [value] to the given [key]. */
    private suspend fun persist(key: Preferences.Key<String>, value: Boolean) {
        withContext(backgroundScope.coroutineContext) {
            checkNotNull(dataStore) {
                    "Cannot persist values to a null data store. Call activateFor before doing so"
                }
                .edit { prefs -> prefs[key.name] = value.toString() }
        }
    }

    private fun String?.asBoolean(): Boolean {
        return this == "true"
    }

    companion object {
        private const val DATA_STORE_FILE_NAME = "dual_shade_education.preferences_pb"

        private object Keys {
            val EverShownNotificationsShade = stringPreferencesKey("ever_shown_notifications_shade")
            val EverShownQuickSettingsShade =
                stringPreferencesKey("ever_shown_quick_settings_shade")
            val EverShownNotificationsTooltip =
                stringPreferencesKey("ever_shown_notifications_tooltip")
            val EverShownQuickSettingsTooltip =
                stringPreferencesKey("ever_shown_quick_settings_tooltip")
        }
    }
}
