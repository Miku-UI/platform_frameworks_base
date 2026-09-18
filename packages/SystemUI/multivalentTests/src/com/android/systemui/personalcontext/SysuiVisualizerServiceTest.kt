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

package com.android.systemui.personalcontext

import android.content.Context
import android.content.res.Resources
import android.service.personalcontext.RenderToken
import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.insight.BundleInsight
import android.util.DisplayMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.personalcontext.ace.visualizer.connector.VisualizerServiceConnector
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class SysuiVisualizerServiceTest : SysuiTestCase() {
    val kosmos = testKosmosNew()

    private val displayMetrics: DisplayMetrics = mock()
    private var resources: Resources = mock { on { displayMetrics } doReturn (displayMetrics) }
    private var context: Context = mock { on { resources } doReturn (resources) }
    private var connector: VisualizerServiceConnector = mock()

    private val Kosmos.underTest: SysuiVisualizerService by
        Kosmos.Fixture { SysuiVisualizerService(connector) }

    @Test
    fun testOnCreateEmbeddedView() {
        kosmos.runTest {
            val clientId = UUID.randomUUID()
            val clientInfo = createClient(clientId)
            val insight = BundleInsight.Builder().build().fakePublish()
            val renderToken = RenderToken(UUID.randomUUID(), null)

            val view = underTest.onCreateEmbeddedView(context, insight, renderToken, clientInfo)
            verify(connector).onCreateEmbeddedView(any(), any(), any(), any())
        }
    }

    @Test
    fun testOnClientConnected() {
        kosmos.runTest {
            val clientId = UUID.randomUUID()
            val clientInfo = createClient(clientId)

            underTest.onClientConnected(clientInfo)
            verify(connector).onClientConnected(any())
        }
    }

    @Test
    fun testOnClientDisconnected() {
        kosmos.runTest {
            val clientId = UUID.randomUUID()
            val clientInfo = createClient(clientId)

            underTest.onClientDisconnected(clientInfo)
            verify(connector).onClientDisconnected(any())
        }
    }

    private fun createClient(clientId: UUID): InsightSurfaceClientInfo {
        return mock<InsightSurfaceClientInfo>().also { whenever(it.id).thenReturn(clientId) }
    }
}
