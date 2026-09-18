/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.qs.flags

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QsSplitInternetTileTest : SysuiTestCase() {

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun bothFlagsDisabled_splitTileDisabled() {
        assertThat(QsSplitInternetTile.isEnabled).isFalse()
    }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    @EnableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun baseFlagDisabledSuppressionFlagEnabled_splitTileDisabled() {
        assertThat(QsSplitInternetTile.isEnabled).isFalse()
    }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @EnableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun bothFlagsEnabled_splitTileDisabled() {
        assertThat(QsSplitInternetTile.isEnabled).isFalse()
    }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun baseFlagEnabledSuppressionFlagDisabled_splitTileDisabled() {
        assertThat(QsSplitInternetTile.isEnabled).isTrue()
    }
}
