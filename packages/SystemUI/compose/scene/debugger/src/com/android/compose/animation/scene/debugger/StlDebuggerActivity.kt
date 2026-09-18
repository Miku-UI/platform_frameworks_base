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

package com.android.compose.animation.scene.debugger

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.android.compose.animation.scene.debug.StlDebugKeys
import com.android.compose.theme.PlatformTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StlDebuggerActivity : ComponentActivity() {

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { setupOnFirstRun() }
        setContent { PlatformTheme { StlDebuggerScreen() } }
    }

    private suspend fun setupOnFirstRun() {
        val isFirstRunPreferenceKey = booleanPreferencesKey("is_first_run")

        val isFirstRun =
            dataStore.data
                .map { preferences -> preferences[isFirstRunPreferenceKey] != false }
                .first()

        if (isFirstRun) {
            setupFirstRun()

            dataStore.edit { settings -> settings[isFirstRunPreferenceKey] = false }
        }
    }

    private fun setupFirstRun() {
        if (SettingsUtils.get(this, StlDebugKeys.EXCLUDE_STLS.key, false) == "") {
            // We exclude BouncerTopSTL because it overlays SceneContainer 1:1 and blocks its labels
            SettingsUtils.put(this, StlDebugKeys.EXCLUDE_STLS.key, "BouncerTopSTL", false)
        }
    }
}
