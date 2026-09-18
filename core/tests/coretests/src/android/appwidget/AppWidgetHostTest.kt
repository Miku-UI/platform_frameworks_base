/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.appwidget

import android.appwidget.AppWidgetHost.AppWidgetHostListener
import android.content.pm.ParceledListSlice
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.appwidget.IAppWidgetService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.AdditionalMatchers.aryEq
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for AppWidgetHost
 */
@RunWith(AndroidJUnit4::class)
class AppWidgetHostTest {
    private val context = InstrumentationRegistry.getInstrumentation().context!!
    private val service = mock<IAppWidgetService>()
    private val host = AppWidgetHost(context, 1234, null, context.mainLooper) { service }

    @Test
    fun setListener() {
        val views = RemoteViews(context.packageName, android.R.id.empty)
        val listener = mock<AppWidgetHostListener>()
        whenever(service.getAppWidgetViews(anyString(), anyInt())).thenReturn(views)

        host.setListener(1, listener)

        verify(service).getAppWidgetViews(context.packageName, 1)
        verify(listener).updateAppWidget(views)
    }

    @Test
    fun startListening() {
        val appWidgetId = 1
        val views = RemoteViews(context.packageName, android.R.layout.activity_list_item)
        val info = AppWidgetProviderInfo()
        val listener = mock<AppWidgetHostListener>()
        whenever(service.startListening(any(), anyString(), anyInt(), any()))
            .thenReturn (
                ParceledListSlice(
                    listOf(
                        PendingHostUpdate.updateAppWidget(appWidgetId, views),
                        PendingHostUpdate.providerChanged(appWidgetId, info),
                        PendingHostUpdate.viewDataChanged(appWidgetId, android.R.id.empty),
                    )
                )
            )

        host.setListener(appWidgetId, listener)
        host.startListening()

        verify(service).startListening(
            any(),
            eq(context.packageName),
            eq(1234),
            aryEq(intArrayOf(appWidgetId))
        )
        verify(listener).updateAppWidget(views)
        verify(listener).onUpdateProviderInfo(info)
        verify(listener).onViewDataChanged(android.R.id.empty)
    }

    @Test
    fun stopListening() {
        host.stopListening()
        verify(service).stopListening(context.packageName, 1234)
    }

    @Test
    fun testReportAllWidgetEvents() {
        val events = arrayOf(
            AppWidgetEvent.Builder()
                .setAppWidgetId(1)
                .build(),
            AppWidgetEvent.Builder()
                .setAppWidgetId(2)
                .build(),
        )
        val listeners = events.map { event ->
            val listener = mock<AppWidgetHostListener>()
            whenever(listener.collectWidgetEvent()).thenReturn(event)
            host.setListener(event.appWidgetId, listener)
            listener
        }

        host.reportAllWidgetEvents()

        listeners.forEach { verify(it).collectWidgetEvent() }
        verify(service).reportWidgetEvents(
            eq(context.packageName),
            argThat { contentEquals(events) }
        )
    }

    @Test
    fun testReportEventForWidget() {
        val event =
            AppWidgetEvent.Builder()
                .setAppWidgetId(1)
                .build()
        val listener = mock<AppWidgetHostListener>()
        whenever(listener.collectWidgetEvent()).thenReturn(event)

        host.setListener(1, listener)
        host.reportEventForWidget(1)

        verify(listener).collectWidgetEvent()
        verify(service).reportWidgetEvents(
            eq(context.packageName),
            argThat { contentEquals(arrayOf(event)) }
        )
    }
}
