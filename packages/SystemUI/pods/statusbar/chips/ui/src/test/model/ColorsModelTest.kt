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
import com.android.internal.R
import com.android.internal.util.ContrastColorUtil
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ColorsModelTest : SysuiTestCase() {

    @Test
    fun systemThemedWithOverride_backgroundOverride_adjustsContrast() {
        val colors =
            ColorsModel.SystemThemedWithOverride(
                backgroundRes = R.color.semanticBlueOnContainerVariant
            )

        assertThat(colors.background(context).defaultColor)
            .isEqualTo(context.getColor(R.color.semanticBlueOnContainerVariant))
        assertThat(colors.text(context))
            .isEqualTo(
                ContrastColorUtil.ensureContrast(
                    ColorsModel.SystemThemed.text(context),
                    context.getColor(R.color.semanticBlueOnContainerVariant),
                    4.5,
                )
            )
        assertThat(colors.outline(context)).isEqualTo(ColorsModel.SystemThemed.outline(context))
    }

    @Test
    fun systemThemedWithOverride_textOverride_adjustsContrast() {
        val colors =
            ColorsModel.SystemThemedWithOverride(textRes = R.color.semanticBlueOnContainerVariant)

        assertThat(colors.background(context).defaultColor)
            .isEqualTo(ColorsModel.SystemThemed.background(context).defaultColor)
        assertThat(colors.text(context))
            .isEqualTo(
                ContrastColorUtil.ensureContrast(
                    context.getColor(R.color.semanticBlueOnContainerVariant),
                    ColorsModel.SystemThemed.background(context).defaultColor,
                    4.5,
                )
            )
        assertThat(colors.outline(context)).isEqualTo(ColorsModel.SystemThemed.outline(context))
    }

    @Test
    fun systemThemedWithOverride_outlineOverride() {
        val colors =
            ColorsModel.SystemThemedWithOverride(
                outlineRes = R.color.semanticBlueOnContainerVariant
            )

        assertThat(colors.background(context).defaultColor)
            .isEqualTo(ColorsModel.SystemThemed.background(context).defaultColor)
        assertThat(colors.text(context)).isEqualTo(ColorsModel.SystemThemed.text(context))
        assertThat(colors.outline(context))
            .isEqualTo(context.getColor(R.color.semanticBlueOnContainerVariant))
    }
}
