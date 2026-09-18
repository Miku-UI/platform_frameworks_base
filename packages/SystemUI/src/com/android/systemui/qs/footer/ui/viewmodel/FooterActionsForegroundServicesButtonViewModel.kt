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

package com.android.systemui.qs.footer.ui.viewmodel

import android.content.Context
import com.android.systemui.animation.Expandable
import com.android.systemui.qs.footer.domain.model.FooterTextButtonModel.FooterForegroundServicesButtonModel

/** A ViewModel for the foreground services button. */
class FooterActionsForegroundServicesButtonViewModel(
    override val model: FooterForegroundServicesButtonModel,
    override val onClick: (Context, Expandable) -> Unit,
) : FooterTextButtonViewModel {
    constructor(
        text: String,
        foregroundServicesCount: Int,
        displayText: Boolean,
        onClick: (quickSettingsContext: Context, Expandable) -> Unit,
        hasNewChanges: Boolean = false,
    ) : this(
        model =
            FooterForegroundServicesButtonModel(
                text = text,
                hasNewChanges = hasNewChanges,
                displayText = displayText,
                foregroundServicesCount = foregroundServicesCount,
            ),
        onClick = onClick,
    )
}
