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

package android.app.jank.tests;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.app.jank.AppJankStats;
import android.app.jank.Flags;
import android.app.jank.StateTracker;
import android.os.Trace;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Choreographer;

import androidx.test.annotation.UiThreadTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.MockedVoidMethod;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
public class StateTrackerTraceTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private StateTracker mStateTracker;
    private Choreographer mChoreographer;
    private MockitoSession mSession;

    @BeforeClass
    public static void classSetup() {
        JankUtils.forceEnableJankTrackingConfig();
    }

    @AfterClass
    public static void classTearDown() {
        JankUtils.resetJankTrackingConfigDefaults();
    }

    @Before
    @UiThreadTest
    public void setup() {
        mSession =
                mockitoSession()
                        .spyStatic(Trace.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        mChoreographer = Choreographer.getInstance();
        mStateTracker = new StateTracker(mChoreographer);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        mSession.finishMocking();
        // Workaround for potential leaks in mockito-target-extended
        Mockito.framework().clearInlineMocks();
    }

    /** Verify that trace markers are emitted when tracing is enabled. */
    @Test
    @UiThreadTest
    public void testTrackState_VerifyTraceMarkers() {
        ExtendedMockito.when(Trace.isTagEnabled(anyLong())).thenReturn(true);

        mStateTracker.putState(
                AppJankStats.WIDGET_CATEGORY_SCROLL,
                "TestWidget",
                AppJankStats.WIDGET_STATE_SCROLLING);

        String expectedKey = "J<scroll::scrolling>";
        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.asyncTraceForTrackBegin(
                                        eq(Trace.TRACE_TAG_APP),
                                        eq(expectedKey),
                                        eq(expectedKey),
                                        anyInt()));
        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.instantForTrack(
                                        eq(Trace.TRACE_TAG_APP),
                                        eq(expectedKey),
                                        matches("FT#beginVsync#-?\\d+")));
        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.instantForTrack(
                                        eq(Trace.TRACE_TAG_APP),
                                        eq(expectedKey),
                                        matches("FT#UIThread-?\\d+")));

        mStateTracker.removeState(
                AppJankStats.WIDGET_CATEGORY_SCROLL,
                "TestWidget",
                AppJankStats.WIDGET_STATE_SCROLLING);

        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.asyncTraceForTrackEnd(
                                        eq(Trace.TRACE_TAG_APP), eq(expectedKey), anyInt()));
        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.instantForTrack(
                                        eq(Trace.TRACE_TAG_APP),
                                        eq(expectedKey),
                                        matches("FT#endVsync#-?\\d+")));
        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.instantForTrack(
                                        eq(Trace.TRACE_TAG_APP), eq(expectedKey), eq("FT#end")));
    }

    /** Verify that trace markers are not emitted for none/unspecified state. */
    @Test
    @UiThreadTest
    public void testTrackState_NoneUnspecifiedState_NoTraceMarkers() {
        ExtendedMockito.when(Trace.isTagEnabled(anyLong())).thenReturn(true);

        mStateTracker.putState(
                AppJankStats.WIDGET_CATEGORY_SCROLL, "TestWidget", AppJankStats.WIDGET_STATE_NONE);

        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.asyncTraceForTrackBegin(
                                        anyLong(), anyString(), anyString(), anyInt()),
                Mockito.never());
        verify(
                (MockedVoidMethod) () -> Trace.instantForTrack(anyLong(), anyString(), anyString()),
                Mockito.never());

        mStateTracker.putState(
                AppJankStats.WIDGET_CATEGORY_SCROLL,
                "TestWidget",
                AppJankStats.WIDGET_STATE_UNSPECIFIED);

        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.asyncTraceForTrackBegin(
                                        anyLong(), anyString(), anyString(), anyInt()),
                Mockito.never());
        verify(
                (MockedVoidMethod) () -> Trace.instantForTrack(anyLong(), anyString(), anyString()),
                Mockito.never());
    }

    /** Verify that no trace markers are emitted when tracing is disabled. */
    @Test
    @UiThreadTest
    public void testTrackState_WhenTracingDisabled_NoMarkers() {
        ExtendedMockito.when(Trace.isTagEnabled(anyLong())).thenReturn(false);

        mStateTracker.putState(
                AppJankStats.WIDGET_CATEGORY_SCROLL,
                "TestWidget",
                AppJankStats.WIDGET_STATE_SCROLLING);

        verify(
                (MockedVoidMethod)
                        () ->
                                Trace.asyncTraceForTrackBegin(
                                        anyLong(), anyString(), anyString(), anyInt()),
                Mockito.never());
        verify(
                (MockedVoidMethod) () -> Trace.instantForTrack(anyLong(), anyString(), anyString()),
                Mockito.never());
    }

    private static String matches(String regex) {
        return ArgumentMatchers.matches(regex);
    }
}
