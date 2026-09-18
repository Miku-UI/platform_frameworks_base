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
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object SettingsUtils {
    fun checkPermission(context: Context): Boolean {
        return Settings.System.canWrite(context) ||
            context.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun put(context: Context, key: String, value: String, isBool: Boolean) {
        try {
            if (isBool) {
                val intVal = if (value == "true") 1 else 0
                Settings.Global.putInt(context.contentResolver, key, intVal)
            } else {
                Settings.Global.putString(context.contentResolver, key, value)
            }
        } catch (e: Exception) {
            Log.e("SettingsUtils", "Failed to write settings", e)
            Toast.makeText(context, "Permission Error: Check Logcat", Toast.LENGTH_SHORT).show()
        }
    }

    fun get(context: Context, key: String, isBool: Boolean): String {
        return try {
            if (isBool) {
                val intVal = Settings.Global.getInt(context.contentResolver, key, 0)
                (intVal == 1).toString()
            } else {
                Settings.Global.getString(context.contentResolver, key) ?: ""
            }
        } catch (e: Exception) {
            Log.e("SettingsUtils", "Failed to read settings", e)
            if (isBool) "false" else ""
        }
    }
}
