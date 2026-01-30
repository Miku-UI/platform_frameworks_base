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

package com.android.settingslib.spa.framework.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

object SettingsShape {
    val CornerFull = CircleShape
    val CornerExtraSmall2 = RoundedCornerShape(SettingsRadius.extraSmall2)
    val CornerMedium = RoundedCornerShape(SettingsRadius.medium)
    val CornerLarge1 = RoundedCornerShape(SettingsRadius.large1)
    val CornerLarge2 = RoundedCornerShape(SettingsRadius.large2)
    val CornerLarge3 = RoundedCornerShape(SettingsRadius.large3)
    val CornerExtraLarge1 = RoundedCornerShape(SettingsRadius.extraLarge1)
}
