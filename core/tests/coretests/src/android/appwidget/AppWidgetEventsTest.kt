/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.appwidget

import android.app.Activity
import android.app.EmptyActivity
import android.app.PendingIntent
import android.appwidget.AppWidgetEvent.MAX_NUM_ITEMS
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.frameworks.coretests.R
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.concurrent.CountDownLatch
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppWidgetEventsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext!!
    private val hostView = AppWidgetHostView(context).apply {
        setAppWidget(1, AppWidgetManager.getInstance(context).installedProviders.first())
    }
    private val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    )

    @Test
    fun appWidgetEvent_toBundle() {
        val appWidgetId = 1
        val position = Rect(1, 2, 3, 4)
        val clicked = intArrayOf(1, 2, 3)
        val scrolled = intArrayOf(4, 5, 6)
        val event = AppWidgetEvent.Builder().run {
            setAppWidgetId(appWidgetId)
            startVisibility()
            endVisibility()
            setPosition(position)
            for (i in clicked) { addClickedId(i) }
            for (i in scrolled) { addScrolledId(i) }
            build()
        }
        val bundle = event.toBundle()

        assertThat(bundle.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)).isEqualTo(appWidgetId)
        assertThat(bundle.getLong(AppWidgetManager.EXTRA_EVENT_DURATION_MS))
            .isEqualTo(event.visibleDuration.toMillis())
        assertThat(bundle.getLong(AppWidgetManager.EXTRA_EVENT_START))
            .isEqualTo(event.start.toEpochMilli())
        assertThat(bundle.getLong(AppWidgetManager.EXTRA_EVENT_END))
            .isEqualTo(event.end.toEpochMilli())
        assertThat(bundle.getIntArray(AppWidgetManager.EXTRA_EVENT_POSITION_RECT))
            .asList().containsExactly(position.left, position.top, position.right, position.bottom)
        assertThat(bundle.getIntArray(AppWidgetManager.EXTRA_EVENT_CLICKED_VIEWS))
            .asList().containsExactly(clicked[0], clicked[1], clicked[2])
        assertThat(bundle.getIntArray(AppWidgetManager.EXTRA_EVENT_SCROLLED_VIEWS))
            .asList().containsExactly(scrolled[0], scrolled[1], scrolled[2])
    }

    @Test
    fun interactionLogger_click() {
        val itemCount = MAX_NUM_ITEMS + 1
        // Set a different value for the viewId to test that the logger always uses the
        // metrics tag if available.
        fun viewId(i: Int) = i + Int.MIN_VALUE
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_test).apply {
            for (i in 0 until itemCount) {
                val metricsTag = i
                val item =
                    RemoteViews(context.packageName, R.layout.remote_views_text, viewId(i)).apply {
                        setAppWidgetEventTag(viewId(i), metricsTag)
                        setOnClickPendingIntent(viewId(i), pendingIntent)
                    }
                addView(R.id.layout, item)
            }
        }
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.interactionLogger.event.clickedIds).isNull()


        for (i in 0 until itemCount.minus(1)) {
            val item = hostView.findViewById<View>(viewId(i))
            assertThat(item).isNotNull()
            assertThat(item.performClick()).isTrue()
            assertThat(hostView.interactionLogger.event.clickedIds).asList()
                .containsExactlyElementsIn(0..i)
        }
        assertThat(hostView.interactionLogger.event.clickedIds).asList().hasSize(MAX_NUM_ITEMS)

        // Last item click should not be recorded because we've reached MAX_VIEW_IDS
        val lastItem = hostView.findViewById<View>(viewId(itemCount - 1))
        assertThat(lastItem).isNotNull()
        assertThat(lastItem.performClick()).isTrue()
        assertThat(hostView.interactionLogger.event.clickedIds).asList().hasSize(MAX_NUM_ITEMS)
        assertThat(hostView.interactionLogger.event.clickedIds).asList()
            .containsExactlyElementsIn(0..itemCount.minus(2))
    }

    @Test
    fun interactionLogger_click_listItem() {
        val itemCount = 5
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_list).apply {
            setPendingIntentTemplate(R.id.list, pendingIntent)
            setRemoteAdapter(
                R.id.list,
                RemoteViews.RemoteCollectionItems.Builder().run {
                    for (i in 0 until itemCount) {
                        val item = RemoteViews(context.packageName, R.layout.remote_views_test)
                        item.setOnClickFillInIntent(R.id.text, Intent())
                        item.setAppWidgetEventTag(R.id.text, i)
                        addItem(i.toLong(), item)
                    }
                    build()
                }
            )
            setAppWidgetEventTag(R.id.list, -1)
        }
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.interactionLogger.event.clickedIds).isNull()

        val list = hostView.findViewById<ListView>(R.id.list)
        assertThat(list).isNotNull()
        list.layout(0, 0, 500, 500)
        for (i in 0 until itemCount) {
            val item = list.getChildAt(i).findViewById<View>(R.id.text)
            assertThat(item.performClick()).isTrue()
            assertThat(hostView.interactionLogger.event.clickedIds).asList()
                .containsExactlyElementsIn(0..i)
        }
    }

    @Test
    fun interactionLogger_scroll() {
        val itemCount = MAX_NUM_ITEMS + 1
        // Set a different value for the viewId to test that the logger always uses the
        // metrics tag if available.
        fun viewId(i: Int) = i + Int.MIN_VALUE
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_test).apply {
            for (i in 0 until itemCount) {
                val metricsTag = i
                val item =
                    RemoteViews(context.packageName, R.layout.remote_views_list, viewId(i)).apply {
                        setAppWidgetEventTag(viewId(i), metricsTag)
                        setRemoteAdapter(
                            viewId(i),
                            RemoteViews.RemoteCollectionItems.Builder().run {
                                addItem(
                                    0L,
                                    RemoteViews(context.packageName, R.layout.remote_views_test)
                                )
                                build()
                            }
                        )
                    }
                addView(R.id.layout, item)
            }
        }
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.interactionLogger.event.scrolledIds).isNull()

        for (i in 0 until itemCount.minus(1)) {
            val item = hostView.findViewById<ListView>(viewId(i))
            assertThat(item).isNotNull()
            item.fling(/* velocityY= */ 100)
            assertThat(hostView.interactionLogger.event.scrolledIds).asList()
                .containsExactlyElementsIn(0..i)
        }
        assertThat(hostView.interactionLogger.event.scrolledIds).asList().hasSize(MAX_NUM_ITEMS)

        // Last item scroll should not be recorded because we've reached MAX_VIEW_IDS
        val lastItem = hostView.findViewById<ListView>(viewId(itemCount - 1))
        assertThat(lastItem).isNotNull()
        lastItem.fling(/* velocityY= */ 100)
        assertThat(hostView.interactionLogger.event.scrolledIds).asList().hasSize(MAX_NUM_ITEMS)
        assertThat(hostView.interactionLogger.event.scrolledIds).asList()
            .containsExactlyElementsIn(0..itemCount.minus(2))
    }

    @Test
    fun interactionLogger_impression() {
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_test)
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.interactionLogger.event.visibleDuration.toMillis()).isEqualTo(0)
        assertThat(hostView.interactionLogger.event.start).isEqualTo(Instant.ofEpochMilli(Long.MAX_VALUE))
        assertThat(hostView.interactionLogger.event.end).isEqualTo(Instant.ofEpochMilli(Long.MIN_VALUE))

        ActivityScenario<Activity>.launch(EmptyActivity::class.java).use { scenario ->
            val start = Instant.now()
            scenario.onActivity { activity ->
                activity.setContentView(hostView)
                hostView.layout(0, 0, 500, 500)
                hostView.startVisibilityTracking()
            }
            Thread.sleep(2000L)
            scenario.onActivity { activity ->
                hostView.stopVisibilityTracking()
            }
            val end = Instant.now()
            assertThat(hostView.interactionLogger.event.visibleDuration.toMillis())
                .isGreaterThan(2000L)
            assertThat(hostView.interactionLogger.event.start).isGreaterThan(start)
            assertThat(hostView.interactionLogger.event.end).isLessThan(end)
        }
    }

    @Test
    fun interactionLogger_position() {
        val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_test)
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.interactionLogger.event.position).isNull()

        ActivityScenario<Activity>.launch(EmptyActivity::class.java).use { scenario ->
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.setContentView(hostView)
                hostView.layout(0, 0, 500, 500)
                hostView.post {
                    val rect = Rect()
                    assertThat(hostView.getGlobalVisibleRect(rect)).isTrue()
                    assertThat(hostView.interactionLogger.event.position).isEqualTo(rect)
                    latch.countDown()
                }
            }
            latch.await()
        }
    }
}
