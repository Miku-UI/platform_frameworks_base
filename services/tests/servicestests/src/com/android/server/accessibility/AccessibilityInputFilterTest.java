/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.accessibility;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS;
import static android.view.WindowManagerPolicyConstants.FLAG_PASS_TO_USER;

import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_AUTOCLICK;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_CONTROL_SCREEN_MAGNIFIER;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_FILTER_KEY_EVENTS;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_INJECT_MOTION_EVENTS;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_TOUCH_EXPLORATION;
import static com.android.server.accessibility.AccessibilityInputFilter.FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.input.IInputManager;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.accessibility.autoclick.AutoclickController;
import com.android.server.accessibility.gestures.TouchExplorer;
import com.android.server.accessibility.magnification.FullScreenMagnificationGestureHandler;
import com.android.server.accessibility.magnification.MagnificationGestureHandler;
import com.android.server.accessibility.magnification.MagnificationKeyHandler;
import com.android.server.accessibility.magnification.MagnificationProcessor;
import com.android.server.accessibility.magnification.WindowMagnificationGestureHandler;
import com.android.server.input.InputManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for AccessibilityInputFilterTest
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityInputFilterTest {
    private static int sNextDisplayId = DEFAULT_DISPLAY;
    private static final int SECOND_DISPLAY = DEFAULT_DISPLAY + 1;
    private static final float DEFAULT_X = 100f;
    private static final float DEFAULT_Y = 100f;

    private final SparseArray<EventStreamTransformation> mEventHandler = new SparseArray<>(0);
    private final SparseArray<MagnificationGestureHandler> mMagnificationGestureHandler =
            new SparseArray<>(0);
    private final ArrayList<Display> mDisplayList = new ArrayList<>();
    private final int mFeatures = FLAG_FEATURE_AUTOCLICK
            | FLAG_FEATURE_TOUCH_EXPLORATION
            | FLAG_FEATURE_CONTROL_SCREEN_MAGNIFIER
            | FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER
            | FLAG_FEATURE_INJECT_MOTION_EVENTS
            | FLAG_FEATURE_FILTER_KEY_EVENTS;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // The expected order of EventStreamTransformations.
    private final Class[] mExpectedEventHandlerTypes =
            {MagnificationKeyHandler.class, KeyboardInterceptor.class, MotionEventInjector.class,
                    FullScreenMagnificationGestureHandler.class, TouchExplorer.class,
                    AutoclickController.class, AccessibilityInputFilter.class};

    @Mock private WindowManagerInternal.AccessibilityControllerInternal mMockA11yController;
    @Mock private WindowManagerInternal mMockWindowManagerService;
    @Mock private MagnificationProcessor mMockMagnificationProcessor;
    @Mock
    private IInputManager mMockInputManager;
    @Mock
    private InputManagerInternal mMockInputManagerInternal;
    private InputManagerGlobal.TestSession mInputManagerGlobalSession;
    private AccessibilityManagerService mAms;
    private AccessibilityInputFilter mA11yInputFilter;
    private EventCaptor mCaptor1;
    private EventCaptor mCaptor2;
    private long mLastDownTime = Integer.MIN_VALUE;

    private class EventCaptor implements EventStreamTransformation {
        List<InputEvent> mEvents = new ArrayList<>();

        @Override
        public void onMotionEvent(MotionEvent event, MotionEvent rawEvent, int policyFlags) {
            mEvents.add(event.copy());
        }

        @Override
        public void onKeyEvent(KeyEvent event, int policyFlags) {
            mEvents.add(event.copy());
        }

        @Override
        public void setNext(EventStreamTransformation next) {
        }

        @Override
        public EventStreamTransformation getNext() {
            return null;
        }

        @Override
        public void clearEvents(int inputSource) {
            clear();
        }

        private void clear() {
            mEvents.clear();
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = Mockito.spy(new ContextWrapper(InstrumentationRegistry.getContext()));
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(
                WindowManagerInternal.class, mMockWindowManagerService);
        LocalServices.removeServiceForTest(InputManagerInternal.class);
        LocalServices.addService(
                InputManagerInternal.class, mMockInputManagerInternal);
        when(mMockWindowManagerService.getAccessibilityController()).thenReturn(
                mMockA11yController);
        when(mMockA11yController.isAccessibilityTracingEnabled()).thenReturn(false);
        mInputManagerGlobalSession = InputManagerGlobal.createTestSession(mMockInputManager);
        InputManager inputManager = new InputManager(context);
        when(context.getSystemService(eq(Context.INPUT_SERVICE))).thenReturn(inputManager);

        setDisplayCount(1);
        mAms = spy(new AccessibilityManagerService(context));
        mA11yInputFilter = new AccessibilityInputFilter(
                context, mAms, mEventHandler, mMagnificationGestureHandler);
        mA11yInputFilter.onInstalled();

        doReturn(mDisplayList).when(mAms).getValidDisplayList();
        doReturn(mMockMagnificationProcessor).when(mAms).getMagnificationProcessor();
    }

    @After
    public void tearDown() {
        mA11yInputFilter.onUninstalled();
        mInputManagerGlobalSession.close();
        mAms.unregisterObservers();
    }

    @Test
    public void testEventHandler_shouldChangeAfterSetUserAndEnabledFeatures() {
        prepareLooper();

        // Check if there is no mEventHandler when no feature is set.
        assertEquals(0, mEventHandler.size());

        // Check if mEventHandler is added/removed after setting a11y features.
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(1, mEventHandler.size());

        mA11yInputFilter.setUserAndEnabledFeatures(0, 0);
        assertEquals(0, mEventHandler.size());
    }

    @Test
    public void testEventHandler_shouldIncreaseAndHaveCorrectOrderAfterOnDisplayAdded() {
        prepareLooper();

        // Check if there is only one mEventHandler when there is one default display.
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(1, mEventHandler.size());

        // Check if it has correct numbers of mEventHandler for corresponding displays.
        setDisplayCount(2);
        mA11yInputFilter.onDisplayAdded(mDisplayList.get(SECOND_DISPLAY));
        assertEquals(2, mEventHandler.size());

        EventStreamTransformation next = mEventHandler.get(SECOND_DISPLAY);
        assertNotNull(next);

        // Start from index 2 because KeyboardInterceptor and MagnificationKeyHandler only exist in
        // EventHandler for DEFAULT_DISPLAY.
        for (int i = 2; next != null; i++) {
            assertEquals(next.getClass(), mExpectedEventHandlerTypes[i]);
            next = next.getNext();
        }
    }

    @Test
    public void testEventHandler_shouldDecreaseAfterOnDisplayRemoved() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(2, mEventHandler.size());

        // Check if it has correct numbers of mEventHandler for corresponding displays.
        mA11yInputFilter.onDisplayRemoved(SECOND_DISPLAY);
        assertEquals(1, mEventHandler.size());

        EventStreamTransformation eventHandler = mEventHandler.get(SECOND_DISPLAY);
        assertNull(eventHandler);
    }

    @Test
    public void testEventHandler_shouldNoChangedInOtherDisplayAfterOnDisplayRemoved() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        EventStreamTransformation eventHandlerBeforeDisplayRemoved =
                mEventHandler.get(DEFAULT_DISPLAY);

        mA11yInputFilter.onDisplayRemoved(SECOND_DISPLAY);
        EventStreamTransformation eventHandlerAfterDisplayRemoved =
                mEventHandler.get(DEFAULT_DISPLAY);

        assertEquals(eventHandlerBeforeDisplayRemoved, eventHandlerAfterDisplayRemoved);
    }

    @Test
    public void testEventHandler_shouldHaveCorrectOrderForEventStreamTransformation() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(2, mEventHandler.size());

        // Check if mEventHandler for each display has correct order of the
        // EventStreamTransformations.
        EventStreamTransformation next = mEventHandler.get(DEFAULT_DISPLAY);
        for (int i = 0; next != null; i++) {
            assertEquals(next.getClass(), mExpectedEventHandlerTypes[i]);
            next = next.getNext();
        }

        next = mEventHandler.get(SECOND_DISPLAY);
        // Start from index 2 because KeyboardInterceptor and MagnificationKeyHandler only exist
        // in EventHandler for DEFAULT_DISPLAY.
        for (int i = 2; next != null; i++) {
            assertEquals(next.getClass(), mExpectedEventHandlerTypes[i]);
            next = next.getNext();
        }
    }

    @Test
    public void testInputEvent_shouldDispatchToCorrespondingEventHandlers() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(2, mEventHandler.size());

        mCaptor1 = new EventCaptor();
        mCaptor2 = new EventCaptor();
        mEventHandler.put(DEFAULT_DISPLAY, mCaptor1);
        mEventHandler.put(SECOND_DISPLAY, mCaptor2);

        // InputEvent with different displayId should be dispatched to corresponding EventHandler.
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(SECOND_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));

        assertEquals(1, mCaptor1.mEvents.size());
        assertEquals(1, mCaptor2.mEvents.size());
    }

    @Test
    public void testInputEvent_shouldClearEventsForDisplayEventHandlers() {
        prepareLooper();

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(1, mEventHandler.size());

        mCaptor1 = new EventCaptor();
        mEventHandler.put(DEFAULT_DISPLAY, mCaptor1);

        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        assertEquals(2, mCaptor1.mEvents.size());

        // InputEvent with different input source to the same display should trigger
        // clearEvents() for the EventHandler in this display.
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_MOUSE));
        assertEquals(1, mCaptor1.mEvents.size());
    }

    @Test
    public void testInputEvent_shouldNotClearEventsForOtherDisplayEventHandlers() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        assertEquals(2, mEventHandler.size());

        mCaptor1 = new EventCaptor();
        mCaptor2 = new EventCaptor();
        mEventHandler.put(DEFAULT_DISPLAY, mCaptor1);
        mEventHandler.put(SECOND_DISPLAY, mCaptor2);

        // InputEvent with different displayId should be dispatched to corresponding EventHandler.
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(SECOND_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));

        // InputEvent with different input source should not trigger clearEvents() for
        // the EventHandler in the other display.
        send(downEvent(SECOND_DISPLAY, InputDevice.SOURCE_MOUSE));
        assertEquals(2, mCaptor1.mEvents.size());
    }

    @Test
    public void testInputEvent_shouldNotClearEventsForOtherDisplayAfterOnDisplayAdded() {
        prepareLooper();

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        mCaptor1 = new EventCaptor();
        mEventHandler.put(DEFAULT_DISPLAY, mCaptor1);

        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        assertEquals(2, mCaptor1.mEvents.size());

        setDisplayCount(2);
        mA11yInputFilter.onDisplayAdded(mDisplayList.get(SECOND_DISPLAY));
        assertEquals(2, mCaptor1.mEvents.size());
    }

    @Test
    public void testInputEvent_shouldNotClearEventsForOtherDisplayAfterOnDisplayRemoved() {
        prepareLooper();

        setDisplayCount(2);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        mCaptor1 = new EventCaptor();
        mEventHandler.put(DEFAULT_DISPLAY, mCaptor1);

        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        send(downEvent(DEFAULT_DISPLAY, InputDevice.SOURCE_TOUCHSCREEN));
        assertEquals(2, mCaptor1.mEvents.size());

        mA11yInputFilter.onDisplayRemoved(SECOND_DISPLAY);
        assertEquals(2, mCaptor1.mEvents.size());
    }

    @Test
    public void testEnabledFeatures_windowMagnificationMode_expectedMagnificationGestureHandler() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        MagnificationGestureHandler handler =
                getMagnificationGestureHandlerFromEventHandler(DEFAULT_DISPLAY);
        assertNotNull(handler);
        assertEquals(WindowMagnificationGestureHandler.class, handler.getClass());
    }

    @Test
    public void testEnabledFeaturesChanged_magFeatureKeepsEnabled_flagOn_doNotResetMagnification() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN)
                .when(mAms).getMagnificationMode(DEFAULT_DISPLAY);
        // Create FullScreenMagnificationGestureHandler
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        MagnificationGestureHandler handler = mock(MagnificationGestureHandler.class);
        mMagnificationGestureHandler.put(DEFAULT_DISPLAY, handler);
        // Any feature changes causes the AccessibilityInputFilter to destroy the gesture handler
        // and recreate new one. Since the magnification feature is still enabled, the destroying
        // of gesture handler should not cause the magnification reset
        mA11yInputFilter.setUserAndEnabledFeatures(0,
                mFeatures | FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP);

        verify(handler).onDestroy(/* resetMagnification= */ eq(false));
    }

    @Test
    public void testDisablingMagFeatures_magFeatureWasEnabled_flagOn_resetMagnification() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN)
                .when(mAms).getMagnificationMode(DEFAULT_DISPLAY);
        // Create FullScreenMagnificationGestureHandler
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        MagnificationGestureHandler handler = mock(MagnificationGestureHandler.class);
        mMagnificationGestureHandler.put(DEFAULT_DISPLAY, handler);
        // Disable all features including magnification, which causes the destroying of the
        // magnification gesture handler and resetting magnification.
        mA11yInputFilter.setUserAndEnabledFeatures(0, 0x0);

        verify(handler).onDestroy(/* resetMagnification= */ eq(true));
    }

    @Test
    public void testChangeMagnificationModeToWindow_expectedMagnificationGestureHandler() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);
        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);
        EventStreamTransformation nextEventStream = getMagnificationGestureHandlerFromEventHandler(
                DEFAULT_DISPLAY).getNext();

        mA11yInputFilter.refreshMagnificationMode(mDisplayList.get(DEFAULT_DISPLAY));

        MagnificationGestureHandler handler =
                getMagnificationGestureHandlerFromEventHandler(DEFAULT_DISPLAY);
        assertNotNull(handler);
        assertEquals(WindowMagnificationGestureHandler.class, handler.getClass());
        assertEquals(nextEventStream.getClass(), handler.getNext().getClass());
    }

    @Test public void
    testChangeMagnificationModeToWindow_magnifierFeature_expectedMagnificationGestureHandler() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);
        final int feature = FLAG_FEATURE_CONTROL_SCREEN_MAGNIFIER
                | FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER | FLAG_FEATURE_TOUCH_EXPLORATION;
        mA11yInputFilter.setUserAndEnabledFeatures(0, feature);
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);
        EventStreamTransformation nextEventStream = getMagnificationGestureHandlerFromEventHandler(
                DEFAULT_DISPLAY).getNext();

        mA11yInputFilter.refreshMagnificationMode(mDisplayList.get(DEFAULT_DISPLAY));

        MagnificationGestureHandler handler =
                getMagnificationGestureHandlerFromEventHandler(DEFAULT_DISPLAY);
        assertNotNull(handler);
        assertEquals(WindowMagnificationGestureHandler.class, handler.getClass());
        assertEquals(nextEventStream.getClass(), handler.getNext().getClass());
    }

    @Test
    public void testEnabledFeatures_windowMagnificationMode_expectedMagnificationKeyHandler() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        MagnificationKeyHandler handler = getMagnificationKeyHandlerFromEventHandler();
        assertNotNull(handler);
    }

    @Test
    public void testEnabledFeatures_fullscreenMagnificationMode_expectedMagnificationKeyHandler() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        MagnificationKeyHandler handler = getMagnificationKeyHandlerFromEventHandler();
        assertNotNull(handler);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MAGNIFICATION_FOLLOWS_MOUSE_WITH_POINTER_MOTION_FILTER)
    public void testEnabledFeatures_fullscreenMagnificationMode_expectedPointerMotionFilter() {
        prepareLooper();
        doReturn(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN).when(
                mAms).getMagnificationMode(DEFAULT_DISPLAY);

        mA11yInputFilter.setUserAndEnabledFeatures(0, mFeatures);

        assertNotNull(mA11yInputFilter.getFullScreenMagnificationPointerMotionEventFilter());
        verify(mMockInputManagerInternal, times(1)).registerAccessibilityPointerMotionFilter(
                notNull());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MAGNIFICATION_FOLLOWS_MOUSE_WITH_POINTER_MOTION_FILTER)
    public void
            testRegisterPointerMotionFilter_fullscreenMagnificationMode_delegatesToInputManager() {
        mA11yInputFilter.registerPointerMotionFilter(true);
        assertNotNull(mA11yInputFilter.getFullScreenMagnificationPointerMotionEventFilter());
        verify(mMockInputManagerInternal, times(1)).registerAccessibilityPointerMotionFilter(
                notNull());

        mA11yInputFilter.registerPointerMotionFilter(false);
        assertNull(mA11yInputFilter.getFullScreenMagnificationPointerMotionEventFilter());
        verify(mMockInputManagerInternal, times(1)).registerAccessibilityPointerMotionFilter(
                isNull());
    }

    private static void prepareLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    private Display createStubDisplay(DisplayInfo displayInfo) {
        final int displayId = sNextDisplayId++;
        final Display display = new Display(DisplayManagerGlobal.getInstance(), displayId,
                displayInfo, DEFAULT_DISPLAY_ADJUSTMENTS);
        return display;
    }

    private void setDisplayCount(int count) {
        sNextDisplayId = DEFAULT_DISPLAY;
        mDisplayList.clear();
        for (int i = 0; i < count; i++) {
            mDisplayList.add(createStubDisplay(new DisplayInfo()));
        }
    }

    private void send(InputEvent event) {
        mA11yInputFilter.onInputEvent(event, /* policyFlags */ FLAG_PASS_TO_USER);
    }

    private MotionEvent downEvent(int displayId, int source) {
        mLastDownTime = SystemClock.uptimeMillis();
        final MotionEvent ev = MotionEvent.obtain(mLastDownTime, mLastDownTime,
                MotionEvent.ACTION_DOWN, DEFAULT_X, DEFAULT_Y, 0);
        ev.setDisplayId(displayId);
        ev.setSource(source);
        return ev;
    }

    @Nullable
    private MagnificationGestureHandler getMagnificationGestureHandlerFromEventHandler(
            int displayId) {
        EventStreamTransformation next = mEventHandler.get(displayId);
        while (next != null) {
            if (next instanceof MagnificationGestureHandler) {
                return (MagnificationGestureHandler) next;
            }
            next = next.getNext();
        }
        return null;
    }

    @Nullable
    private MagnificationKeyHandler getMagnificationKeyHandlerFromEventHandler() {
        EventStreamTransformation next = mEventHandler.get(DEFAULT_DISPLAY);
        while (next != null) {
            if (next instanceof MagnificationKeyHandler) {
                return (MagnificationKeyHandler) next;
            }
            next = next.getNext();
        }
        return null;
    }
}
