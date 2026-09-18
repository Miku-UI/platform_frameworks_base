/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.view.contentcapture;

import static android.view.contentcapture.CustomTestActivity.VIEW_TYPE_CUSTOM_VIEW;
import static android.view.contentcapture.CustomTestActivity.VIEW_TYPE_TEXT_VIEW;
import static android.view.contentcapture.CustomTestActivity.VIEW_TYPE_VIEW_GROUP_WITH_A11Y_TEXT;
import static android.view.contentcapture.CustomTestActivity.VIEW_TYPE_VIEW_GROUP_WITH_CONTENT_DESCRIPTION;

import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.DESTROYED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.RemoteCallback;
import android.perftests.utils.BenchmarkState;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.contentcapture.ContentCaptureService;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.flags.Flags;
import android.widget.TextView;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;
import com.android.perftests.contentcapture.R;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@LargeTest
public class LoginTest extends AbstractContentCapturePerfTestCase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testLaunchActivity() throws Throwable {
        enableService();

        testActivityLaunchTime(R.layout.test_login_activity, 0);
    }

    @Test
    public void testLaunchActivity_contain100Views() throws Throwable {
        enableService();

        testActivityLaunchTime(R.layout.test_container_activity, 100);
    }

    @Test
    public void testLaunchActivity_contain300Views() throws Throwable {
        enableService();

        testActivityLaunchTime(R.layout.test_container_activity, 300);
    }

    @Test
    public void testLaunchActivity_contain500Views() throws Throwable {
        enableService();

        testActivityLaunchTime(R.layout.test_container_activity, 500);
    }

    @Test
    public void testLaunchActivity_noService() throws Throwable {
        testActivityLaunchTime(R.layout.test_login_activity, 0);
    }

    @Test
    public void testLaunchActivity_noService_contain100Views() throws Throwable {
        testActivityLaunchTime(R.layout.test_container_activity, 100);
    }

    @Test
    public void testLaunchActivity_noService_contain300Views() throws Throwable {
        testActivityLaunchTime(R.layout.test_container_activity, 300);
    }

    @Test
    public void testLaunchActivity_noService_contain500Views() throws Throwable {
        testActivityLaunchTime(R.layout.test_container_activity, 500);
    }

    @Test
    public void testSendEventsLatency() throws Throwable {
        enableService();

        testSendEventLatency(R.layout.test_container_activity, 0);
    }

    @Test
    public void testSendEventsLatency_contains100Views() throws Throwable {
        enableService();

        testSendEventLatency(R.layout.test_container_activity, 100);
    }

    @Test
    public void testSendEventsLatency_contains300Views() throws Throwable {
        enableService();

        testSendEventLatency(R.layout.test_container_activity, 300);
    }

    @Test
    public void testSendEventsLatency_contains500Views() throws Throwable {
        enableService();

        testSendEventLatency(R.layout.test_container_activity, 500);
    }

    private void testActivityLaunchTime(int layoutId, int numViews) throws Throwable {
        final Object drawNotifier = new Object();
        final Intent intent = getLaunchIntent(layoutId, numViews);
        intent.putExtra(CustomTestActivity.INTENT_EXTRA_FINISH_ON_IDLE, true);
        intent.putExtra(
                CustomTestActivity.INTENT_EXTRA_DRAW_CALLBACK,
                new RemoteCallback(
                        result -> {
                            synchronized (drawNotifier) {
                                drawNotifier.notifyAll();
                            }
                        }));
        final ActivityWatcher watcher = startWatcher();

        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            mEntryActivity.startActivity(intent);
            synchronized (drawNotifier) {
                try {
                    drawNotifier.wait(GENERIC_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // Ignore the time to finish the activity
            state.pauseTiming();
            watcher.waitFor(DESTROYED);
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();
        }
    }

    private void testSendEventLatency(int layoutId, int numViews) throws Throwable {
        final Object drawNotifier = new Object();
        final Intent intent = getLaunchIntent(layoutId, numViews);
        intent.putExtra(CustomTestActivity.INTENT_EXTRA_FINISH_ON_IDLE, true);
        intent.putExtra(
                CustomTestActivity.INTENT_EXTRA_DRAW_CALLBACK,
                new RemoteCallback(
                        result -> {
                            synchronized (drawNotifier) {
                                drawNotifier.notifyAll();
                            }
                        }));
        final ActivityWatcher watcher = startWatcher();

        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            mEntryActivity.startActivity(intent);
            synchronized (drawNotifier) {
                try {
                    drawNotifier.wait(GENERIC_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            waitForSessionPaused();

            // Ignore the time to finish the activity
            state.pauseTiming();
            watcher.waitFor(DESTROYED);
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();
        }
    }

    @Test
    public void testOnVisibilityAggregated_visibleChanged() throws Throwable {
        enableService();
        final CustomTestActivity activity = launchActivity();
        final View root = activity.getWindow().getDecorView();
        final View username = root.findViewById(R.id.username);

        testOnVisibilityAggregated(username);
    }

    @Test
    public void testOnVisibilityAggregated_visibleChanged_noService() throws Throwable {
        final CustomTestActivity activity = launchActivity();
        final View root = activity.getWindow().getDecorView();
        final View username = root.findViewById(R.id.username);

        testOnVisibilityAggregated(username);
    }

    @Test
    public void testOnVisibilityAggregated_visibleChanged_noOptions() throws Throwable {
        enableService();
        clearOptions();
        final CustomTestActivity activity = launchActivity();
        final View root = activity.getWindow().getDecorView();
        final View username = root.findViewById(R.id.username);

        testOnVisibilityAggregated(username);
    }

    @Test
    public void testOnVisibilityAggregated_visibleChanged_notImportant() throws Throwable {
        enableService();
        final CustomTestActivity activity = launchActivity();
        final View root = activity.getWindow().getDecorView();
        final View username = root.findViewById(R.id.username);
        username.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);

        testOnVisibilityAggregated(username);
    }

    private void testOnVisibilityAggregated(View view) throws Throwable {
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            // Only count the time of onVisibilityAggregated()
            state.pauseTiming();
            sInstrumentation.runOnMainSync(
                    () -> {
                        state.resumeTiming();
                        view.onVisibilityAggregated(false);
                        state.pauseTiming();
                    });
            sInstrumentation.runOnMainSync(
                    () -> {
                        state.resumeTiming();
                        view.onVisibilityAggregated(true);
                        state.pauseTiming();
                    });
            state.resumeTiming();
        }
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_EXPORT_ASSIST_VIRTUAL_NODE_TO_CCAPI,
        Flags.FLAG_NEW_HEURISTICS_FOR_IMPORTANCE_ENABLED
    })
    public void testNotifyVirtualChildrenAppearedWithCustomView_1() throws Throwable {
        testNumberOfTypeViewAppearedEvents(
                R.layout.test_export_virtual_assist_node_activity,
                /* numberOfViews= */ 1,
                /* expectedViewAppearedCounts= */ 6,
                VIEW_TYPE_CUSTOM_VIEW);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_EXPORT_ASSIST_VIRTUAL_NODE_TO_CCAPI,
        Flags.FLAG_NEW_HEURISTICS_FOR_IMPORTANCE_ENABLED
    })
    public void testNotifyVirtualChildrenAppearedWithCustomView_10() throws Throwable {
        testNumberOfTypeViewAppearedEvents(
                R.layout.test_export_virtual_assist_node_activity,
                /* numberOfViews= */ 10,
                /* expectedViewAppearedCounts= */ 51,
                VIEW_TYPE_CUSTOM_VIEW);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_EXPORT_ASSIST_VIRTUAL_NODE_TO_CCAPI,
        Flags.FLAG_NEW_HEURISTICS_FOR_IMPORTANCE_ENABLED
    })
    public void testNotifyVirtualChildrenAppearedWithCustomView_100() throws Throwable {
        testNumberOfTypeViewAppearedEvents(
                R.layout.test_export_virtual_assist_node_activity,
                /* numberOfViews= */ 100,
                /* expectedViewAppearedCounts= */ 501,
                VIEW_TYPE_CUSTOM_VIEW);
    }

    @Test
    public void testWithTextView_500() throws Throwable {
        testNumberOfTypeViewAppearedEvents(
                R.layout.test_export_virtual_assist_node_activity,
                /* numberOfViews= */ 500,
                /* expectedViewAppearedCounts= */ 501,
                VIEW_TYPE_TEXT_VIEW);
    }

    private void testNumberOfTypeViewAppearedEvents(
            int layoutId, int numberOfViews, int expectedViewAppearedCounts, int viewType)
            throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity = launchActivity(layoutId, numberOfViews, viewType);
        View rootView = activity.findViewById(R.id.group_root_view);
        long eventTimeoutMs = 20000;
        int expectedFlushCount = 2;
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Force flush the initial events.
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        // Act
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();
            // Trigger content capture structure provision.
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
            sInstrumentation.waitForIdleSync();
            state.pauseTiming();
            service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);

            // Assert
            Assert.assertEquals(
                    "Expected " + expectedViewAppearedCounts + " TYPE_VIEW_APPEARED events",
                    expectedViewAppearedCounts,
                    service.getAppearedCount());
            state.resumeTiming();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NEW_HEURISTICS_FOR_IMPORTANCE_ENABLED)
    public void testNotifyViewGroupWithContentDescription() throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity =
                launchActivity(
                        R.layout.test_view_group_activity,
                        3,
                        VIEW_TYPE_VIEW_GROUP_WITH_CONTENT_DESCRIPTION);
        View rootView = activity.findViewById(R.id.view_group_root_view);
        long eventTimeoutMs = 20000;
        int expectedFlushCount = 2;
        int expectedViewAppearedCount = 4; // 3 ViewGroup + 1 container
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Force flush the initial events.
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        // Act
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();
            // Trigger content capture structure provision.
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
            sInstrumentation.waitForIdleSync();
            state.pauseTiming();
            service.waitForAppearedEvents(expectedViewAppearedCount, eventTimeoutMs);

            // Assert
            assertThat(service.getAppearedCount()).isEqualTo(expectedViewAppearedCount);
            state.resumeTiming();
        }
    }

    @Test
    public void testBatchFlushMetrics() throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity =
                launchActivity(
                        R.layout.test_export_virtual_assist_node_activity, 3, VIEW_TYPE_TEXT_VIEW);
        View groupRootView = activity.findViewById(R.id.group_root_view);
        int sessionId = groupRootView.getContentCaptureSession().getId();
        int expectedFlushCount = 2;
        int expectedViewAppearedCount = 4; // 3 TextViews + 1 container
        int eventTimeoutMs = 10000;
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Force flush the initial events.
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> groupRootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> groupRootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        // Act
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();
            ContentCaptureService.PendingMetrics pendingMetrics =
                    service.mPendingMetrics.get(sessionId);
            if (pendingMetrics != null) {
                pendingMetrics.getMetrics().reset();
            }
            sInstrumentation.runOnMainSync(() -> groupRootView.setVisibility(View.GONE));
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();

            sInstrumentation.runOnMainSync(() -> groupRootView.setVisibility(View.VISIBLE));
            sInstrumentation.waitForIdleSync();
            state.pauseTiming();
            service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);

            // Assert
            assertThat(service.getAppearedCount()).isEqualTo(expectedViewAppearedCount);
            assertThat(service.mPendingMetrics.get(sessionId).getMetrics().viewAppearedCount)
                    .isEqualTo(expectedViewAppearedCount);
            state.resumeTiming();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CONTENT_INTERACTION_API_ENABLED)
    public void testButtonClick_flagEnabled() throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity = launchActivity(R.layout.test_button_activity, 0);
        View rootView = activity.findViewById(R.id.root_view);
        View buttonView = rootView.findViewById(R.id.button);
        int sessionId = rootView.getContentCaptureSession().getId();
        int expectedFlushCount = 2;
        int expectedViewAppearedCount = 2; // 1 Button + 1 container
        int expectedInteractionCount = 1;
        int eventTimeoutMs = 10000;
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Force flush the initial events.
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        // Act
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();
            ContentCaptureService.PendingMetrics pendingMetrics =
                    service.mPendingMetrics.get(sessionId);
            if (pendingMetrics != null) {
                pendingMetrics.getMetrics().reset();
            }
            state.resumeTiming();
            sInstrumentation.runOnMainSync(() -> buttonView.performClick());
            sInstrumentation.waitForIdleSync();
            state.pauseTiming();
            service.waitForFlushEvents(1, eventTimeoutMs);

            // Assert
            assertThat(service.getAppearedCount()).isEqualTo(expectedViewAppearedCount);
            assertThat(service.getInteractionCount()).isEqualTo(expectedInteractionCount);
            assertThat(service.getCapturedEvents().get(0).getType()).isEqualTo(
                    ContentCaptureEvent.TYPE_CONTENT_INTERACTION);
            state.resumeTiming();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NEW_HEURISTICS_FOR_IMPORTANCE_ENABLED)
    public void testNotifyViewGroupWithA11yText() throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity =
                launchActivity(
                        R.layout.test_view_group_activity, 3, VIEW_TYPE_VIEW_GROUP_WITH_A11Y_TEXT);
        View rootView = activity.findViewById(R.id.view_group_root_view);
        long eventTimeoutMs = 20000;
        int expectedFlushCount = 2;
        int expectedViewAppearedCount = 4; // 3 ViewGroup + 1 container
        int expectedA11yTextCount = 3; // 3 ViewGrou
        String expectedA11yText = "a11yText";
        String frameLayoutClassName = "android.widget.FrameLayout";
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Force flush the initial events.
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        // Act
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();
            // Trigger content capture structure provision.
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
            sInstrumentation.waitForIdleSync();
            state.resumeTiming();
            sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
            sInstrumentation.waitForIdleSync();
            state.pauseTiming();
            service.waitForAppearedEvents(expectedViewAppearedCount, eventTimeoutMs);

            // Assert
            assertThat(service.getAppearedCount()).isEqualTo(expectedViewAppearedCount);
            List<ContentCaptureEvent> captureEvents = service.getCapturedEvents();
            long correctA11yTextCount =
                    captureEvents.stream()
                            .map(ContentCaptureEvent::getViewNode)
                            .filter(
                                    node ->
                                            node != null
                                                    && frameLayoutClassName.equals(
                                                            node.getClassName()))
                            .map(node -> node.getContentDescription().toString())
                            .filter(expectedA11yText::equals)
                            .count();
            assertThat(correctA11yTextCount).isEqualTo(expectedA11yTextCount);
            state.resumeTiming();
        }
    }

    @Test
    public void testFlushOnTextChange() throws Throwable {
        // Arrange
        MyContentCaptureService service = enableService();
        CustomTestActivity activity = launchActivity(R.layout.test_login_activity, 0);
        TextView textView = activity.findViewById(R.id.username);
        View rootView = textView.getRootView();

        long eventTimeoutMs = 15000;
        int expectedFlushCount = 2;

        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        // Initial Stabilization (Exclude from timing)
        state.pauseTiming();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.GONE));
        sInstrumentation.waitForIdleSync();
        sInstrumentation.runOnMainSync(() -> rootView.setVisibility(View.VISIBLE));
        sInstrumentation.waitForIdleSync();
        service.waitForFlushEvents(expectedFlushCount, eventTimeoutMs);
        state.resumeTiming();

        int iteration = 0;

        // Act & Benchmark Loop
        while (state.keepRunning()) {
            state.pauseTiming();
            service.clearEvents();

            // Generate unique text so the pipeline doesn't drop it as a duplicate
            final String textToInject = "new text " + iteration++;

            ContentCaptureSession session = textView.getContentCaptureSession();
            AutofillId autofillId = textView.getAutofillId();
            state.resumeTiming();

            // Inject the event directly into the session
            sInstrumentation.runOnMainSync(() -> {
                session.notifyViewTextChanged(autofillId, textToInject);
            });
            sInstrumentation.waitForIdleSync();

            // Wait for exactly 1 flush triggered by the text change timeout
            state.pauseTiming();
            boolean flushEventReceived = service.waitForFlushEvents(1, eventTimeoutMs);

            // Assert
            assertThat(flushEventReceived).isTrue();
            List<ContentCaptureEvent> events = service.getCapturedEvents();
            boolean hasTextChange = events.stream()
                    .anyMatch(event ->
                            event.getType() == ContentCaptureEvent.TYPE_VIEW_TEXT_CHANGED);
            boolean hasSessionFlush = events.stream()
                    .anyMatch(event ->
                            event.getType() == ContentCaptureEvent.TYPE_SESSION_FLUSH);
            assertThat(hasTextChange).isTrue();
            assertThat(hasSessionFlush).isTrue();

            state.resumeTiming();
        }
    }
}
