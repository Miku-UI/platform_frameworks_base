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

package android.app.jank.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.jank.AppJankStats;
import android.app.jank.Flags;
import android.app.jank.JankDataProcessor;
import android.app.jank.JankTracker;
import android.app.jank.StateTracker;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This file contains tests that verify the proper functionality of the Jank Tracking feature.
 * All tests should obtain references to necessary objects through View type interfaces, rather
 * than direct instantiation. When operating outside of a testing environment, the expected
 * behavior is to retrieve the necessary objects using View type interfaces. This approach ensures
 * that calls are correctly routed down to the activity level. Any modifications to the call
 * routing should result in test failures, which might happen with direct instantiations.
 */
@RunWith(AndroidJUnit4.class)
public class IntegrationTests {
    public static final int WAIT_FOR_TIMEOUT_MS = 5000;
    public static final int WAIT_FOR_PENDING_JANKSTATS_MS = 1000;
    public static final int WAIT_FOR_COMPONENT_INITIALIZATION_MS = 2000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private Activity mEmptyActivity;

    public UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private ActivityTestRule<JankTrackerActivity> mJankTrackerActivityRule =
            new ActivityTestRule<>(
                    JankTrackerActivity.class,
                    false,
                    false);

    private ActivityTestRule<EmptyActivity> mEmptyActivityRule =
            new ActivityTestRule<>(EmptyActivity.class, false , true);

    @BeforeClass
    public static void classSetup() {
        JankUtils.forceEnableJankTrackingConfig();
    }

    @AfterClass
    public static void classTearDown() {
        JankUtils.resetJankTrackingConfigDefaults();
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
    }


    /**
     * Get a JankTracker object from a view and confirm it's not null.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void getJankTacker_confirmNotNull() {
        Activity jankTrackerActivity = mJankTrackerActivityRule.launchActivity(null);
        EditText editText = jankTrackerActivity.findViewById(R.id.edit_text);

        mDevice.wait(Until.findObject(By.text("Edit Text")), WAIT_FOR_TIMEOUT_MS);

        JankTracker jankTracker = editText.getJankTracker();
        assertTrue(jankTracker != null);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_DETAILED_APP_JANK_METRICS_API,
        Flags.FLAG_RETAIN_WIDGET_PARAMETERS
    })
    public void reportJankStats_confirmPendingStatsIncreases() {
        Activity jankTrackerActivity = mJankTrackerActivityRule.launchActivity(null);
        mDevice.wait(Until.findObject(
                By.text(jankTrackerActivity.getString(R.string.continue_test))),
                WAIT_FOR_TIMEOUT_MS);

        EditText editText = jankTrackerActivity.findViewById(R.id.edit_text);
        JankTracker jankTracker = editText.getJankTracker();

        HashMap<String, JankDataProcessor.PendingJankStat> pendingStats =
                jankTracker.getPendingJankStats();
        assertEquals(0, pendingStats.size());

        AppJankStats jankStat = JankUtils.getAppJankStats();
        editText.reportAppJankStats(jankStat);

        // wait until pending results are available.
        JankUtils.waitForResults(jankTracker, WAIT_FOR_PENDING_JANKSTATS_MS);

        pendingStats = jankTracker.getPendingJankStats();
        assertEquals(1, pendingStats.size());

        JankDataProcessor.PendingJankStat pendingJankStat =
                pendingStats.entrySet().iterator().next().getValue();
        assertNotNull(pendingJankStat);

        assertEquals(jankStat.getWidgetId(), pendingJankStat.getWidgetId());
        assertEquals(jankStat.getJankyFrameCount(), pendingJankStat.getJankyFrames());
        assertEquals(jankStat.getTotalFrameCount(), pendingJankStat.getTotalFrames());
        assertEquals(jankStat.getUid(), pendingJankStat.getUid());
        assertEquals(jankStat.getWidgetCategory(), pendingJankStat.getWidgetCategory());
        assertEquals(jankStat.getWidgetState(), pendingJankStat.getWidgetState());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void simulateWidgetStateChanges_confirmStateChangesAreTracked() {
        JankTrackerActivity jankTrackerActivity =
                mJankTrackerActivityRule.launchActivity(null);
        mDevice.wait(Until.findObject(
                        By.text(jankTrackerActivity.getString(R.string.continue_test))),
                WAIT_FOR_TIMEOUT_MS);

        TestWidget testWidget = jankTrackerActivity.findViewById(R.id.jank_tracker_widget);
        JankTracker jankTracker = testWidget.getJankTracker();
        jankTracker.forceListenerRegistration();

        ArrayList<StateTracker.StateData> uiStates = new ArrayList<>();
        // Get the current UI states, at this point only the activity name should be in the UI
        // states list.
        jankTracker.getAllUiStates(uiStates);

        assertEquals(1, uiStates.size());

        // This should add a UI state to be tracked.
        testWidget.simulateAnimationStarting();
        uiStates.clear();
        jankTracker.getAllUiStates(uiStates);

        assertEquals(2, uiStates.size());

        // Stop the animation
        testWidget.simulateAnimationEnding();
        uiStates.clear();
        jankTracker.getAllUiStates(uiStates);

        assertEquals(2, uiStates.size());

        // Confirm the Animation state has a VsyncIdEnd that is not default, indicating the end
        // of that state.
        for (int i = 0; i < uiStates.size(); i++) {
            StateTracker.StateData stateData = uiStates.get(i);
            if (stateData.mWidgetCategory.equals(AppJankStats.WIDGET_CATEGORY_ANIMATION)) {
                assertNotEquals(Long.MAX_VALUE, stateData.mVsyncIdEnd);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void jankTrackingPaused_whenActivityNoLongerVisible() {
        JankTrackerActivity jankTrackerActivity =
                mJankTrackerActivityRule.launchActivity(null);
        TestWidget testWidget = jankTrackerActivity.findViewById(R.id.jank_tracker_widget);
        JankTracker jankTracker = testWidget.getJankTracker();
        jankTracker.forceListenerRegistration();
        JankUtils.waitForShouldTrackTrue(jankTracker, WAIT_FOR_COMPONENT_INITIALIZATION_MS);

        assertTrue(jankTracker.shouldTrack());

        // Send jankTrackerActivity to the background
        mDevice.pressHome();
        mDevice.waitForIdle(WAIT_FOR_TIMEOUT_MS);

        assertFalse(jankTracker.shouldTrack());
    }

    /**
     * Confirm no NPE is thrown when a widget attempts to update state prior to state object
     * initialization.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void jankTracking_noNullPointerException_whenViewsUpdateStatesEarly() {
        JankTrackerActivity jankTrackerActivity = mJankTrackerActivityRule.launchActivity(null);
        TestWidget testWidget = jankTrackerActivity.findViewById(R.id.jank_tracker_widget);
        JankTracker jankTracker = testWidget.getJankTracker();

        // Forcing the registration allows widgets to start updating state, however, widgets
        // could update state before state tracking objects are initialized resulting in NPE.
        jankTracker.forceListenerRegistration();

        // Previously this would throw an NPE when called immediately after registration.
        // The additional check in JankTracker.shouldTrack() will prevent the NPE.
        jankTracker.updateUiState(
                AppJankStats.WIDGET_CATEGORY_OTHER,
                "TEST_WIDGET_ID_01",
                AppJankStats.WIDGET_STATE_NONE,
                AppJankStats.WIDGET_STATE_SCROLLING);

        JankUtils.waitForShouldTrackTrue(jankTracker, WAIT_FOR_COMPONENT_INITIALIZATION_MS);
        assertTrue(jankTracker.shouldTrack());

        jankTracker.updateUiState(
                AppJankStats.WIDGET_CATEGORY_OTHER,
                "TEST_WIDGET_ID_02",
                AppJankStats.WIDGET_STATE_NONE,
                AppJankStats.WIDGET_STATE_SCROLLING);
        ArrayList<StateTracker.StateData> stateData = new ArrayList<>();
        jankTracker.getAllUiStates(stateData);
        assertEquals(2, stateData.size()); // Activity Name + TEST_WIDGET_ID_02
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void jankTrackingResumed_whenActivityBecomesVisibleAgain() {
        mEmptyActivityRule.launchActivity(null);
        mEmptyActivity = mEmptyActivityRule.getActivity();
        JankTrackerActivity jankTrackerActivity =
                mJankTrackerActivityRule.launchActivity(null);
        TestWidget testWidget = jankTrackerActivity.findViewById(R.id.jank_tracker_widget);
        JankTracker jankTracker = testWidget.getJankTracker();
        jankTracker.forceListenerRegistration();

        // Send jankTrackerActivity to the background
        mDevice.pressHome();
        mDevice.waitForIdle(WAIT_FOR_TIMEOUT_MS);

        assertFalse(jankTracker.shouldTrack());

        Intent resumeJankTracker = new Intent(mInstrumentation.getContext(),
                JankTrackerActivity.class);
        resumeJankTracker.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        mEmptyActivity.startActivity(resumeJankTracker);
        mDevice.wait(Until.findObject(By.text(mEmptyActivity.getString(R.string.continue_test))),
                WAIT_FOR_TIMEOUT_MS);

        assertTrue(jankTracker.shouldTrack());
    }

    /*
       When JankTracker is first instantiated it gets passed the apps UID the same UID should be
       passed when reporting AppJankStats. To make sure frames and metrics are all associated with
       the same app these UIDs need to match. This test confirms that mismatched IDs are not
       counted.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void reportJankStats_statNotMerged_onMisMatchedAppIds() {
        Activity jankTrackerActivity = mJankTrackerActivityRule.launchActivity(null);
        mDevice.wait(Until.findObject(
                        By.text(jankTrackerActivity.getString(R.string.continue_test))),
                WAIT_FOR_TIMEOUT_MS);

        EditText editText = jankTrackerActivity.findViewById(R.id.edit_text);
        JankTracker jankTracker = editText.getJankTracker();

        HashMap<String, JankDataProcessor.PendingJankStat> pendingStats =
                jankTracker.getPendingJankStats();
        assertEquals(0, pendingStats.size());

        int mismatchedAppUID = 25;
        editText.reportAppJankStats(JankUtils.getAppJankStats(mismatchedAppUID));

        // wait until pending results should be available.
        JankUtils.waitForResults(jankTracker, WAIT_FOR_PENDING_JANKSTATS_MS);

        pendingStats = jankTracker.getPendingJankStats();

        assertEquals(0, pendingStats.size());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INSTRUMENT_LISTVIEW_SCROLL_STATES)
    public void confirmListView_reportsStateChanges() {
        String resourceIdPkg = "android.app.jank.tests";

        try (ActivityScenario<ListViewActivity> scenario =
                ActivityScenario.launch(ListViewActivity.class)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 listview = device.findObject(By.res(resourceIdPkg, "list_view_id"));
            assertNotNull(listview);

            // This should result in two widget state additions being added the active state being
            // fling and the none state.
            listview.fling(Direction.DOWN, 1000);

            scenario.onActivity(
                    activity -> {
                        ListView listView = activity.findViewById(R.id.list_view_id);
                        JankTracker jankTracker = listView.getJankTracker();

                        ArrayList<StateTracker.StateData> stateData = new ArrayList<>();
                        jankTracker.getAllUiStates(stateData);
                        Map<String, Integer> aggregateData =
                                JankUtils.aggregateCountsByState(stateData);

                        long flingCount = aggregateData.get(AppJankStats.WIDGET_STATE_FLINGING);
                        long idleCount = aggregateData.get(AppJankStats.WIDGET_STATE_NONE);
                        assertEquals(1, flingCount);
                        assertEquals(1, idleCount);
                    });

        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INSTRUMENT_SCROLLVIEW_SCROLL_STATES)
    public void confirmScrollView_reportsFlingStateChanges() {
        String resourceIdPkg = "android.app.jank.tests";

        try (ActivityScenario<ScrollViewActivity> scenario =
                ActivityScenario.launch(ScrollViewActivity.class)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 scrollview = device.findObject(By.res(resourceIdPkg, "scroll_view_id"));
            assertNotNull(scrollview);

            scrollview.fling(Direction.DOWN, 1000);
            scenario.onActivity(
                    activity -> {
                        ScrollView scrollView = activity.findViewById(R.id.scroll_view_id);
                        JankTracker jankTracker = scrollView.getJankTracker();

                        HashMap<String, JankDataProcessor.PendingJankStat> pendingStats =
                                jankTracker.getPendingJankStats();
                        assertNotNull(pendingStats);

                        int flingCount = 0;
                        for (var entry : pendingStats.keySet()) {
                            if (entry.contains(AppJankStats.WIDGET_STATE_FLINGING)) {
                                flingCount++;
                            }
                        }

                        assertEquals("ScrollView Fling State Count", 1, flingCount);

                        ArrayList<StateTracker.StateData> stateData = new ArrayList<>();
                        jankTracker.getAllUiStates(stateData);
                        int scrollStateNoneCount = 0;
                        for (StateTracker.StateData state : stateData) {
                            if (state.mWidgetState.equals(AppJankStats.WIDGET_STATE_NONE)) {
                                scrollStateNoneCount++;
                            }
                        }
                        assertEquals("ScrollView None State Count", 1, scrollStateNoneCount);
                    });

        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INSTRUMENT_SCROLLVIEW_SCROLL_STATES)
    public void confirmScrollView_reportsScrollStateChanges() {
        String resourceIdPkg = "android.app.jank.tests";

        try (ActivityScenario<ScrollViewActivity> scenario =
                ActivityScenario.launch(ScrollViewActivity.class)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 scrollview = device.findObject(By.res(resourceIdPkg, "scroll_view_id"));
            assertNotNull(scrollview);

            scrollview.scroll(Direction.DOWN, .1f);
            scenario.onActivity(
                    activity -> {
                        ScrollView scrollView = activity.findViewById(R.id.scroll_view_id);
                        JankTracker jankTracker = scrollView.getJankTracker();

                        HashMap<String, JankDataProcessor.PendingJankStat> pendingStats =
                                jankTracker.getPendingJankStats();

                        assertNotNull(pendingStats);
                        int scrollStateCount = 0;
                        for (var entry : pendingStats.keySet()) {
                            if (entry.contains(AppJankStats.WIDGET_STATE_SCROLLING)) {
                                scrollStateCount++;
                            }
                        }
                        assertEquals("ScrollView Scroll State Count", 1, scrollStateCount);

                        ArrayList<StateTracker.StateData> stateData = new ArrayList<>();
                        jankTracker.getAllUiStates(stateData);
                        int scrollStateNoneCount = 0;
                        for (StateTracker.StateData state : stateData) {
                            if (state.mWidgetState.equals(AppJankStats.WIDGET_STATE_NONE)) {
                                scrollStateNoneCount++;
                            }
                        }
                        assertEquals("ScrollView None State Count", 1, scrollStateNoneCount);
                    });

        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }
}
