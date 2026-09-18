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

package com.android.systemui.qs.footer.domain.model

import androidx.compose.runtime.Stable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R

@Stable
sealed interface FooterTextButtonModel {
    val icon: Icon
    val text: String
    val hasNewChanges: Boolean

    data class FooterBasicButtonModel(
        override val icon: Icon,
        override val text: String,
        override val hasNewChanges: Boolean = false,
    ) : FooterTextButtonModel

    data class FooterForegroundServicesButtonModel(
        override val icon: Icon =
            Icon.Resource(R.drawable.ic_qs_footer_info, contentDescription = null),
        override val text: String,
        override val hasNewChanges: Boolean = false,
        val displayText: Boolean,
        val foregroundServicesCount: Int,
    ) : FooterTextButtonModel
}
