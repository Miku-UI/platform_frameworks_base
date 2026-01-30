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

package com.android.systemui.statusbar.chips.ui.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import org.junit.Assert.assertThrows
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class OngoingActivityChipModelTest : SysuiTestCase() {
    @Test
    fun contentIconOnly_butNullIcon_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            OngoingActivityChipModel.Active(
                icon = null,
                content = OngoingActivityChipModel.Content.IconOnly,
                key = "test",
                colors = ColorsModel.SystemThemed,
                onClickListenerLegacy = null,
                clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
            )
        }
    }

    @Test
    fun contentCountdown_withClickListenerLegacyNotNull_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            OngoingActivityChipModel.Active(
                content = OngoingActivityChipModel.Content.Countdown(secondsUntilStarted = 2),
                onClickListenerLegacy = {},
                icon = null,
                key = "test",
                colors = ColorsModel.SystemThemed,
                clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
            )
        }
    }

    @Test
    fun contentCountdown_withClickListenerNotNone_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            OngoingActivityChipModel.Active(
                content = OngoingActivityChipModel.Content.Countdown(secondsUntilStarted = 2),
                clickBehavior = OngoingActivityChipModel.ClickBehavior.ExpandAction {},
                onClickListenerLegacy = null,
                icon = null,
                key = "test",
                colors = ColorsModel.SystemThemed,
            )
        }
    }

    @Test
    fun contentCountdown_withIconNotNull_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            OngoingActivityChipModel.Active(
                content = OngoingActivityChipModel.Content.Countdown(secondsUntilStarted = 2),
                icon =
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                    Icon.Resource(
                        R.drawable.ic_present_to_all,
                        ContentDescription.Resource(R.string.share_to_app_chip_accessibility_label),
                    )
                ),
                clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                onClickListenerLegacy = null,
                key = "test",
                colors = ColorsModel.SystemThemed,
            )
        }
    }
}
