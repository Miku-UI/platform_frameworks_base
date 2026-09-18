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

package com.android.systemui.notifications.intelligence.rules.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FreeformRuleRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { realFreeformRuleRepository }

    @Test
    fun createDraftRuleFromFreeformText_success() =
        kosmos.runTest {
            val result =
                underTest.createDraftRuleFromFreeformText(
                    action = ActionModel.Block,
                    text = "sample text",
                )

            assertThat(result).isInstanceOf(ResponseModel.Success::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule)
                .isInstanceOf(DraftRuleModel.New::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule.action)
                .isEqualTo(ActionModel.Block)
        }

    // TODO: b/491180820 - Add a test for delay & error once Kosmos variables can be modified.
}
