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

package com.android.systemui.screenrecord.domain

import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit

class ScreenRecordingPreferenceUtil(private val context: Context) {
    fun updateShowTaps(whileRecording: Boolean) {
        val originalSetting = getShowTaps()
        setShowTaps(whileRecording)
        if (whileRecording != originalSetting) {
            sharedPreference().edit {
                putBoolean(STORED_SHOW_TAPS_VALUE, originalSetting)
                putBoolean(UPDATE_SHOW_TAPS, true)
            }
        }
    }

    fun maybeRestoreShowTapsSetting() {
        if (sharedPreference().getBoolean(UPDATE_SHOW_TAPS, false)) {
            restoreShowTapsSetting()
        }
    }

    fun restoreShowTapsSetting() {
        setShowTaps(sharedPreference().getBoolean(STORED_SHOW_TAPS_VALUE, false))
        sharedPreference().edit { putBoolean(UPDATE_SHOW_TAPS, false) }
    }

    private fun setShowTaps(isOn: Boolean) {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SHOW_TOUCHES,
            if (isOn) 1 else 0,
        )
    }

    private fun getShowTaps(): Boolean {
        return Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES, 0) != 0
    }

    private fun sharedPreference(): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)
    }

    companion object {
        const val SHARED_PREFERENCES_NAME = "com.android.systemui.screenrecord"
        const val STORED_SHOW_TAPS_VALUE = "stored_show_taps_value"
        const val UPDATE_SHOW_TAPS = "update_show_taps"
    }
}
