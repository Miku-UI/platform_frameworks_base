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

package com.android.settingslib.metadata.preferencesapi.category

/**
 * The category describing which option in the settings home page must be chosen to reach the
 * desired screen.
 */
enum class Category(val value: String) {
    NETWORK("top_level_network"),
    COMMUNAL("top_level_communal"),
    CONNECTED_DEVICES("top_level_connected_devices"),
    DEVICE("top_level_device"),
    APPS("top_level_apps"),
    NOTIFICATIONS("top_level_notifications"),
    BATTERY("top_level_battery"),
    STORAGE("top_level_storage"),
    SOUND("top_level_sound"),
    PRIORITY_MODES("top_level_priority_modes"),
    DISPLAY("top_level_display"),
    WALLPAPER("top_level_wallpaper"),
    ACCESSIBILITY("top_level_accessibility"),
    SAFETY_CENTER("top_level_safety_center"),
    SECURITY("top_level_security"),
    PRIVACY("top_level_privacy"),
    LOCATION("top_level_location"),
    EMERGENCY("top_level_emergency"),
    ACCOUNTS("top_level_accounts"),
    SYSTEM("top_level_system"),
    ABOUT_DEVICE("top_level_about_device"),
    SUPPORT("top_level_support"),
    SUPERVISION("top_level_supervision")
}