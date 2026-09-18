/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.systemui.personalcontext

import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightDisplayDetails
import android.service.personalcontext.insight.interaction.InsightEvent
import android.service.personalcontext.personalContextManager
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidTestingRunner::class)
class AutofillAttributionStartableTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val underTest: AutofillAttributionStartable by lazy {
        with(kosmos) {
            AutofillAttributionStartable(
                broadcastDispatcher = broadcastDispatcher,
                personalContextManager = personalContextManager,
            )
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testSendAttributionIntent_reportsEvent() =
        kosmos.runTest {
            underTest.start()

            assertThat(broadcastDispatcher.numReceiversRegistered).isEqualTo(1)

            val title = "test title"
            val publishedContextInsight =
                DisplayInsight.Builder(InsightDisplayDetails.Builder(title).build())
                    .build()
                    .fakePublish()
            val renderToken = RenderToken(UUID.randomUUID(), null)

            val attributionIntent =
                AutofillAttributionStartable.getAttributionIntent(
                    context,
                    publishedContextInsight,
                    renderToken,
                    /*index=*/ 0,
                )

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, attributionIntent, null)

            verify(personalContextManager)
                .reportInsightEvent(
                    publishedContextInsight,
                    InsightEvent.EVENT_USER_ATTRIBUTION_REQUESTED,
                    renderToken,
                )
        }
}
