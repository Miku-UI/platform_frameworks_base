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

package com.android.systemui.statusbar.systemstatusicons.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel

/** Common interface for all system status icon view models. */
sealed interface SystemStatusIconViewModel {
    /** The associated slot in the status bar that this icon should be displayed on. */
    val slotName: String

    /** Whether this icon should be visible in the status bar. */
    val visible: Boolean

    interface Default : SystemStatusIconViewModel {
        /**
         * [Icon] to be displayed on the right side of the status bar. This should be implemented as
         * a hydrated value by individual system status icon view models.
         */
        val icon: Icon?
    }

    interface MobileIcons : SystemStatusIconViewModel {
        val mobileIconsViewModel: MobileIconsViewModel
        val stackedMobileIconViewModel: StackedMobileIconViewModel
    }
}
