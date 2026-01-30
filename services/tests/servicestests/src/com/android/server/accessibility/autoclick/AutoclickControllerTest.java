/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.accessibility.autoclick.AutoclickController.CONTINUOUS_SCROLL_INTERVAL;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK;
import static com.android.server.testutils.MockitoUtilsKt.eq;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.accessibility.util.AccessibilityUtils;
import com.android.server.accessibility.AccessibilityTraceManager;
import com.android.server.accessibility.BaseEventStreamTransformation;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

/** Test cases for {@link AutoclickController}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickControllerTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    private TestableLooper mTestableLooper;
    @Mock
    private AccessibilityTraceManager mMockTrace;
    @Mock
    private WindowManager mMockWindowManager;
    @Mock private AutoclickController.InputManagerWrapper mMockInputManagerWrapper;
    private AutoclickController mController;
    private MotionEventCaptor mMotionEventCaptor;

    private static class MotionEventCaptor extends BaseEventStreamTransformation {
        public MotionEvent downEvent;
        public MotionEvent buttonPressEvent;
        public MotionEvent buttonReleaseEvent;
        public MotionEvent upEvent;
        public MotionEvent moveEvent;
        public MotionEvent cancelEvent;
        public int eventCount = 0;
        private List<MotionEvent> mEventList = new ArrayList<>();

        @Override
        public void onMotionEvent(MotionEvent event, MotionEvent rawEvent, int policyFlags) {
            MotionEvent eventCopy = MotionEvent.obtain(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downEvent = eventCopy;
                    break;
                case MotionEvent.ACTION_BUTTON_PRESS:
                    buttonPressEvent = eventCopy;
                    break;
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    buttonReleaseEvent = eventCopy;
                    break;
                case MotionEvent.ACTION_UP:
                    upEvent = eventCopy;
                    break;
                case MotionEvent.ACTION_MOVE:
                    moveEvent = eventCopy;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cancelEvent = eventCopy;
                    break;
                default:
                    return;
            }
            mEventList.add(eventCopy);
            eventCount++;
        }

        public void assertCapturedEvents(int... actionsInOrder) {
            assertThat(eventCount).isEqualTo(mEventList.size());
            for (int i = 0; i < eventCount; i++) {
                assertThat(actionsInOrder[i])
                        .isEqualTo(mEventList.get(i).getAction());
            }
        }
    }

    public static class ScrollEventCaptor extends BaseEventStreamTransformation {
        public MotionEvent scrollEvent;
        public int eventCount = 0;

        @Override
        public void onMotionEvent(MotionEvent event, MotionEvent rawEvent, int policyFlags) {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                if (scrollEvent != null) {
                    scrollEvent.recycle();
                }
                scrollEvent = MotionEvent.obtain(event);
                eventCount++;
            }
            super.onMotionEvent(event, rawEvent, policyFlags);
        }
    }

    @Before
    public void setUp() {
        mTestableLooper = TestableLooper.get(this);
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mController =
                new AutoclickController(mTestableContext, mTestableContext.getUserId(), mMockTrace);
    }

    @After
    public void tearDown() {
        mController.onDestroy();
        mTestableLooper.processAllMessages();
        TestableLooper.remove(this);
    }

    @Test
    public void onMotionEvent_lazyInitClickScheduler() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mClickScheduler).isNotNull();
    }

    @Test
    public void onMotionEvent_nonMouseSource_notInitClickScheduler() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeNonMouseActionHoverMoveEvent();

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    public void onMotionEvent_lazyInitAutoclickSettingsObserver() {
        assertThat(mController.mAutoclickSettingsObserver).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickSettingsObserver).isNotNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickIndicatorScheduler() {
        assertThat(mController.mAutoclickIndicatorScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorScheduler).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickIndicatorScheduler() {
        assertThat(mController.mAutoclickIndicatorScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorScheduler).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickIndicatorView() {
        assertThat(mController.mAutoclickIndicatorView).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorView).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickIndicatorView() {
        assertThat(mController.mAutoclickIndicatorView).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorView).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickTypePanelView() {
        assertThat(mController.mAutoclickTypePanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickTypePanel).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickTypePanelView() {
        assertThat(mController.mAutoclickTypePanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickTypePanel).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_addAutoclickIndicatorViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();

        verify(mMockWindowManager).addView(eq(mController.mAutoclickIndicatorView), any());
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_removeAutoclickIndicatorViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        verify(mMockWindowManager).removeView(mController.mAutoclickIndicatorView);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_removeAutoclickTypePanelViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        mController.onDestroy();

        verify(mockAutoclickTypePanel).hide();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_initClickSchedulerDelayFromSetting() {
        injectFakeMouseActionHoverMoveEvent();

        int delay =
                Settings.Secure.getIntForUser(
                        mTestableContext.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_DELAY,
                        mController.DEFAULT_AUTOCLICK_DELAY_TIME,
                        mTestableContext.getUserId());
        assertThat(mController.mClickScheduler.getDelayForTesting()).isEqualTo(delay);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_initCursorAreaSizeFromSetting() {
        injectFakeMouseActionHoverMoveEvent();

        int size =
                Settings.Secure.getIntForUser(
                        mTestableContext.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                        AccessibilityManager.AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT,
                        mTestableContext.getUserId());
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(size);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_doNotUpdateMetaStateWhenControllerIsNull() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON);

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_updateMetaStateWhenControllerNotNull() {
        injectFakeMouseActionHoverMoveEvent();

        int metaState = KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;
        injectFakeKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, metaState);

        assertThat(mController.mClickScheduler).isNotNull();
        assertThat(mController.mClickScheduler.getMetaStateForTesting()).isEqualTo(metaState);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_cancelAutoClickWhenAdditionalRegularKeyPresssed() {
        injectFakeMouseActionHoverMoveEvent();

        injectFakeKeyEvent(KeyEvent.KEYCODE_J, KeyEvent.META_ALT_ON);

        assertThat(mController.mClickScheduler).isNotNull();
        assertThat(mController.mClickScheduler.getMetaStateForTesting()).isEqualTo(0);
    }

    @Test
    public void onDestroy_clearClickScheduler() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    public void onDestroy_clearAutoclickSettingsObserver() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mAutoclickSettingsObserver).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_clearAutoclickIndicatorScheduler() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mAutoclickIndicatorScheduler).isNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_hoverEnter_doesNotScheduleClick() {
        injectFakeMouseActionHoverMoveEvent();

        // Send hover enter event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_ENTER);

        // Verify there is no pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_hoverMove_scheduleClick() {
        injectFakeMouseActionHoverMoveEvent();

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);

        // Verify there is a pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
    }

    @Test
    public void smallJitteryMovement_doesNotTriggerClick() {
        // Initial hover move to set an anchor point.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 40f, MotionEvent.ACTION_HOVER_MOVE);

        // Get the initial scheduled click time.
        long initialScheduledTime = mController.mClickScheduler.getScheduledClickTimeForTesting();

        // Simulate small, jittery movements (all within the default slop).
        injectFakeMouseMoveEvent(/* x= */ 31f, /* y= */ 41f, MotionEvent.ACTION_HOVER_MOVE);

        injectFakeMouseMoveEvent(/* x= */ 30.5f, /* y= */ 39.8f, MotionEvent.ACTION_HOVER_MOVE);

        // Verify that the scheduled click time has NOT changed.
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting())
                .isEqualTo(initialScheduledTime);
    }

    @Test
    public void singleSignificantMovement_triggersClick() {
        // Initial hover move to set an anchor point.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 40f, MotionEvent.ACTION_HOVER_MOVE);

        // Get the initial scheduled click time.
        long initialScheduledTime = mController.mClickScheduler.getScheduledClickTimeForTesting();

        // Significant change in x (30f difference) and y (30f difference)
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);

        // Verify that the scheduled click time has changed (click was rescheduled).
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting())
                .isNotEqualTo(initialScheduledTime);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onCursorAreaSizeSettingsChange_moveWithinCustomRadius_clickNotTriggered() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(customSize);

        // Move the mouse down, less than customSize radius so a click is not triggered.
        float moveDownY = customSize - 25;
        injectFakeMouseMoveEvent(/* x= */ 0, /* y= */ moveDownY, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onCursorAreaSizeSettingsChange_moveOutsideCustomRadius_clickTriggered() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(customSize);

        // Move the mouse right, greater than customSize radius so a click is triggered.
        float moveRightX = customSize + 100;
        injectFakeMouseMoveEvent(/* x= */ moveRightX, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onNotIgnoreCursorMovement_clickNotTriggered_whenMoveIsWithinSlop() {
        // Send initial mouse movement.
        injectFakeMouseActionHoverMoveEvent();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));

        // Move the mouse down less than customSize radius. Even if ignore custom movement is not
        // enabled, a click is not triggered as long as the move is within the slop.
        float moveDownY = customSize - 100;
        injectFakeMouseMoveEvent(/* x= */ 0, /* y= */ moveDownY, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_ignoreMinorMovementTrue_clicksAtAnchorPosition() {
        initializeAutoclick();
        enableIgnoreMinorCursorMovement();

        // First move event to set the anchor.
        float anchorX = 50f;
        float anchorY = 60f;
        injectFakeMouseMoveEvent(anchorX, anchorY, MotionEvent.ACTION_HOVER_MOVE);

        // Second move event to trigger the click.
        float lastX = 80f;
        float lastY = 80f;
        injectFakeMouseMoveEvent(lastX, lastY, MotionEvent.ACTION_HOVER_MOVE);
        mController.mClickScheduler.run();

        // Verify click happened at anchor position, not the last position.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getX()).isEqualTo(anchorX);
        assertThat(mMotionEventCaptor.downEvent.getY()).isEqualTo(anchorY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_ignoreMinorMovementFalse_clicksAtLastPosition() {
        initializeAutoclick();

        // Ensure setting is off.
        Settings.Secure.putIntForUser(
                mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT,
                AccessibilityUtils.State.OFF,
                mTestableContext.getUserId());
        mController.onChangeForTesting(
                /* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT));

        // First move event to set the anchor.
        float anchorX = 50f;
        float anchorY = 60f;
        injectFakeMouseMoveEvent(anchorX, anchorY, MotionEvent.ACTION_HOVER_MOVE);

        // Second move event to trigger the click.
        float lastX = 80f;
        float lastY = 80f;
        injectFakeMouseMoveEvent(lastX, lastY, MotionEvent.ACTION_HOVER_MOVE);
        mController.mClickScheduler.run();

        // Verify click happened at the last position.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getX()).isEqualTo(lastX);
        assertThat(mMotionEventCaptor.downEvent.getY()).isEqualTo(lastY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onIgnoreCursorMovement_clickNotTriggered_whenMoveIsWithinSlop() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));

        // No matter if ignore custom movement is enabled or not, a click won't be triggered as long
        // as the move is inside the slop.
        float moveRightX = customSize - 100;
        injectFakeMouseMoveEvent(/* x= */ moveRightX, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void triggerRightClickWithRevertToLeftClickEnabled_resetClickType() {
        // Move mouse to initialize autoclick panel.
        injectFakeMouseActionHoverMoveEvent();

        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;
        mController.clickPanelController.handleAutoclickTypeChange(AUTOCLICK_TYPE_RIGHT_CLICK);

        // Set ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK to true.
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK));
        when(mockAutoclickTypePanel.isPaused()).thenReturn(false);
        mController.mClickScheduler.run();
        assertThat(mController.mClickScheduler.getRevertToLeftClickForTesting()).isTrue();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_flagOn_clickNotTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);

        // Verify there is not a pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);

        // Resume autoclick.
        when(mockAutoclickTypePanel.isPaused()).thenReturn(false);

        // Send initial move event again. Because this is the first recorded move, a click won't be
        // scheduled.
        injectFakeMouseActionHoverMoveEvent();
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);

        // Send move again to trigger click and verify there is now a pending click.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_panelNotHovered_clickNotTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and ensure the panel is not hovered.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(false);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);

        // Verify click is not triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_panelHovered_clickTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and hover the panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);

        // Verify click is triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_unhoveringCancelsClickWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and hover the panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);

        // Verify click is triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);

        // Now simulate the pointer being moved outside the panel.
        when(mockAutoclickTypePanel.isHovered()).thenReturn(false);
        mController.clickPanelController.onHoverChange(/* hovered= */ false);

        // Verify pending click is canceled.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void toggleAutoclickPause_inScrollMode_exitsScrollMode() {
        // Initialize the controller.
        injectFakeMouseActionHoverMoveEvent();

        // Set the active click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);
        mController.mAutoclickScrollPanel.show();

        // Verify it's visible before pause.
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isTrue();

        // Pause autoclick.
        mController.clickPanelController.toggleAutoclickPause(true);

        // Verify scroll panel is now hidden.
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickScrollPanel() {
        assertThat(mController.mAutoclickScrollPanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickScrollPanel).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickScrollPanel() {
        assertThat(mController.mAutoclickScrollPanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickScrollPanel).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_hideAutoclickScrollPanel() {
        injectFakeMouseActionHoverMoveEvent();
        AutoclickScrollPanel mockAutoclickScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickScrollPanel = mockAutoclickScrollPanel;

        mController.onDestroy();

        verify(mockAutoclickScrollPanel).hide();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void changeFromScrollToOtherClickType_hidesScrollPanel() {
        injectFakeMouseActionHoverMoveEvent();

        // Set active click type to SCROLL.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Show the scroll panel.
        mController.mAutoclickScrollPanel.show();
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isTrue();

        // Change click type to LEFT_CLICK.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);

        // Verify scroll panel is hidden.
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isFalse();
    }

    @Test
    public void sendClick_clickType_leftClick() {
        initializeAutoclick();

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify left click sent.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
    }

    @Test
    public void sendClick_clickType_rightClick() {
        initializeAutoclick();

        // Set click type to right click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK);
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify right click sent.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_SECONDARY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_scroll_showsScrollPanelOnlyOnce() {
        initializeAutoclick();

        // Set click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Mock the scroll panel to verify interactions.
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // First hover move event.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify scroll panel is shown once.
        verify(mockScrollPanel, times(1)).show(anyFloat(), anyFloat());
        assertThat(mMotionEventCaptor.downEvent).isNull();

        // Second significant hover move event to trigger another autoclick.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify scroll panel is still only shown once (not called again).
        verify(mockScrollPanel, times(1)).show(anyFloat(), anyFloat());
        assertThat(mMotionEventCaptor.downEvent).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void scrollPanelController_directionalButtonsHideIndicator() {
        injectFakeMouseActionHoverMoveEvent();

        // Create a spy on the real object to verify method calls.
        AutoclickIndicatorView spyIndicatorView = spy(mController.mAutoclickIndicatorView);
        mController.mAutoclickIndicatorView = spyIndicatorView;

        // Simulate hover on direction button.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify clearIndicator was called.
        verify(spyIndicatorView).clearIndicator();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void hoverOnAutoclickPanel_rightClickType_forceTriggerLeftClick() {
        initializeAutoclick();

        // Set click type to right click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK);
        // Set mouse to hover panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify left click is sent due to the mouse hovering the panel.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void hoverOnAutoclickPanel_dragClickType_forceTriggerLeftClick() {
        initializeAutoclick();

        // Set click type to right click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_DRAG);
        // Set mouse to hover panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify both the down and up left click events are sent due to the mouse hovering the
        // panel.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(mMotionEventCaptor.upEvent).isNotNull();
        assertThat(mController.isDraggingForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void hoverOnAutoclickPanel_scrollClickType_forceTriggerLeftClick() {
        initializeAutoclick();

        // Set click type to scrolling click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);
        // Set mouse to hover panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify left click is sent and the scroll panel is hidden due to the mouse hovering the
        // panel.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void hoverOnAutoclickPanel_useDefaultCursorArea() {
        initializeAutoclick();

        // Set an extra large cursor area size and enable ignore minor cursor movement.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));
        enableIgnoreMinorCursorMovement();

        // Set mouse to hover panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send a hover move event that's within than the cursor area size. Normally because
        // ignoreMinorCursorMovement is enabled this wouldn't trigger a click. But since the panel
        // is hovered a click is expected.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify the expected left click.
        assertThat(mMotionEventCaptor.eventCount).isEqualTo(
                getNumEventsExpectedFromClick(/* numClicks= */ 1));
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_updateLastCursorAndScrollAtThatLocation() {
        // Set up event capturer to track scroll events.
        ScrollEventCaptor scrollCaptor = new ScrollEventCaptor();
        mController.setNext(scrollCaptor);

        // Initialize controller with mouse event.
        injectFakeMouseActionHoverMoveEvent();

        // Mock the scroll panel.
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Set click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Set cursor position.
        float expectedX = 75f;
        float expectedY = 125f;
        mController.mScrollCursorX = expectedX;
        mController.mScrollCursorY = expectedY;

        // Trigger scroll action in up direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify scroll event happens at last cursor location.
        assertThat(scrollCaptor.scrollEvent).isNotNull();
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(expectedX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(expectedY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void handleScroll_generatesCorrectScrollEvents() {
        ScrollEventCaptor scrollCaptor = new ScrollEventCaptor();
        mController.setNext(scrollCaptor);

        // Initialize controller.
        injectFakeMouseActionHoverMoveEvent();

        // Set cursor position.
        final float expectedX = 100f;
        final float expectedY = 200f;
        mController.mScrollCursorX = expectedX;
        mController.mScrollCursorY = expectedY;

        // Test UP direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify basic event properties.
        assertThat(scrollCaptor.eventCount).isEqualTo(1);
        assertThat(scrollCaptor.scrollEvent).isNotNull();
        assertThat(scrollCaptor.scrollEvent.getAction()).isEqualTo(MotionEvent.ACTION_SCROLL);
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(expectedX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(expectedY);

        // Verify UP direction uses correct axis values.
        float vScrollUp = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float hScrollUp = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
        assertThat(vScrollUp).isGreaterThan(0);
        assertThat(hScrollUp).isEqualTo(0);

        // Test DOWN direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_DOWN, true);

        // Verify DOWN direction uses correct axis values.
        assertThat(scrollCaptor.eventCount).isEqualTo(2);
        float vScrollDown = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float hScrollDown = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
        assertThat(vScrollDown).isLessThan(0);
        assertThat(hScrollDown).isEqualTo(0);

        // Test LEFT direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_LEFT, true);

        // Verify LEFT direction uses correct axis values.
        assertThat(scrollCaptor.eventCount).isEqualTo(3);
        float vScrollLeft = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float hScrollLeft = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
        assertThat(hScrollLeft).isGreaterThan(0);
        assertThat(vScrollLeft).isEqualTo(0);

        // Test RIGHT direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_RIGHT, true);

        // Verify RIGHT direction uses correct axis values.
        assertThat(scrollCaptor.eventCount).isEqualTo(4);
        float vScrollRight = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float hScrollRight = scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
        assertThat(hScrollRight).isLessThan(0);
        assertThat(vScrollRight).isEqualTo(0);

        // Verify scroll cursor position is preserved.
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(expectedX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(expectedY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void scrollCursor_maintainsScrollPositionWhenPanelHovered() {
        ScrollEventCaptor scrollCaptor = new ScrollEventCaptor();
        mController.setNext(scrollCaptor);

        // Initialize controller.
        injectFakeMouseActionHoverMoveEvent();
        mController.mClickScheduler.updateDelay(0);

        // Set click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Set cursor position.
        float initialX = 100f;
        float initialY = 200f;
        mController.mScrollCursorX = initialX;
        mController.mScrollCursorY = initialY;

        // Create mock panel that is hovered.
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        when(mockScrollPanel.isHovered()).thenReturn(true);
        when(mockScrollPanel.isVisible()).thenReturn(true);
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Move cursor to panel position.
        float newX = 300f;
        float newY = 400f;
        injectFakeMouseMoveEvent(newX, newY, MotionEvent.ACTION_HOVER_MOVE);
        mController.mClickScheduler.updateDelay(0);
        mTestableLooper.processAllMessages();

        // Trigger scroll action in up direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify scroll event still happens at the original position instead of new location.
        assertThat(scrollCaptor.scrollEvent).isNotNull();
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(initialX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(initialY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void scrollCursor_updateScrollPositionWhenPanelNotHovered() {
        ScrollEventCaptor scrollCaptor = new ScrollEventCaptor();
        mController.setNext(scrollCaptor);

        // Initialize controller.
        injectFakeMouseActionHoverMoveEvent();
        mController.mClickScheduler.updateDelay(0);

        // Set click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Set cursor position.
        float initialX = 100f;
        float initialY = 200f;
        mController.mScrollCursorX = initialX;
        mController.mScrollCursorY = initialY;

        // Create mock panel that is not hovered.
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        when(mockScrollPanel.isHovered()).thenReturn(false);
        when(mockScrollPanel.isVisible()).thenReturn(true);
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Move cursor to new position.
        float newX = 300f;
        float newY = 400f;
        injectFakeMouseMoveEvent(newX, newY, MotionEvent.ACTION_HOVER_MOVE);
        mController.mClickScheduler.updateDelay(0);
        mTestableLooper.processAllMessages();

        // Trigger scroll action in up direction.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify scroll event happens at the new position.
        assertThat(scrollCaptor.scrollEvent).isNotNull();
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(newX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(newY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_doubleclick_triggerClickTwice() {
        initializeAutoclick();

        // Set click type to double click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_DOUBLE_CLICK);
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 30f, /* y= */ 100f, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // When all messages (with delays) are processed.
        mTestableLooper.moveTimeForward(2 * mController.LONG_PRESS_TIMEOUT);
        mTestableLooper.processAllMessages();

        // Verify left click sent.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(mMotionEventCaptor.eventCount).isEqualTo(
                getNumEventsExpectedFromClick(/* numClicks= */ 2));
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_drag_simulateDragging() {
        initializeAutoclick();

        // Set click type to drag click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_DRAG);

        injectFakeMouseMoveEvent(/* x= */ 100, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify only two motion events were sent.
        assertThat(mMotionEventCaptor.eventCount).isEqualTo(2);

        // Verify both events have the same down time.
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.buttonPressEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.buttonPressEvent.getDownTime());

        // Move the mouse again to simulate dragging and verify the new mouse event is
        // transformed to a MOVE action and its down time matches the drag initiating click's
        // down time.
        injectFakeMouseMoveEvent(/* x= */ 40, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();
        assertThat(mMotionEventCaptor.eventCount).isEqualTo(3);
        assertThat(mMotionEventCaptor.moveEvent).isNotNull();
        assertThat(mMotionEventCaptor.moveEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.downEvent.getDownTime());

        // Move the mouse again further now to simulate ending the drag session.
        mMotionEventCaptor.moveEvent = null;
        mMotionEventCaptor.eventCount = 0;
        injectFakeMouseMoveEvent(/* x= */ 300, /* y= */ 300, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Verify the final 3 clicks were sent: the 1 move event + 2 up type events to end the drag.
        assertThat(mMotionEventCaptor.eventCount).isEqualTo(3);

        // Verify each event matches the same down time as the initiating drag click.
        assertThat(mMotionEventCaptor.moveEvent).isNotNull();
        assertThat(mMotionEventCaptor.moveEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.downEvent.getDownTime());
        assertThat(mMotionEventCaptor.buttonReleaseEvent).isNotNull();
        assertThat(mMotionEventCaptor.buttonReleaseEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.downEvent.getDownTime());
        assertThat(mMotionEventCaptor.upEvent).isNotNull();
        assertThat(mMotionEventCaptor.upEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.downEvent.getDownTime());

        // Verify the button release & up event have the correct click parameters.
        assertThat(mMotionEventCaptor.buttonReleaseEvent.getActionButton()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(mMotionEventCaptor.buttonReleaseEvent.getButtonState()).isEqualTo(0);
        assertThat(mMotionEventCaptor.upEvent.getActionButton()).isEqualTo(0);
        assertThat(mMotionEventCaptor.upEvent.getButtonState()).isEqualTo(0);
    }


    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_drag_keyEventCancelsDrag() {
        initializeAutoclick();

        // Set click type to drag click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_DRAG);

        // Initiate drag event.
        injectFakeMouseMoveEvent(/* x= */ 100, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();
        assertThat(mController.isDraggingForTesting()).isTrue();

        // Move the mouse to start the click scheduler.
        injectFakeMouseActionHoverMoveEvent();
        injectFakeMouseMoveEvent(/* x= */ 200, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        assertThat(mController.isDraggingForTesting()).isTrue();

        // Press a key to see the drag canceled and reset.
        injectFakeKeyEvent(KeyEvent.KEYCODE_A, /* modifiers= */ 0);
        assertThat(mController.isDraggingForTesting()).isFalse();

        // Verify the ACTION_UP was sent for alerting the system that dragging has ended.
        assertThat(mMotionEventCaptor.upEvent).isNotNull();
        assertThat(mMotionEventCaptor.downEvent).isNotNull();
        assertThat(mMotionEventCaptor.upEvent.getDownTime()).isEqualTo(
                mMotionEventCaptor.downEvent.getDownTime());
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_drag_clickTypeDoesNotRevertAfterFirstClick() {
        initializeAutoclick();

        // Set ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK to true.
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK));

        // Set click type to drag click.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_DRAG);

        // Initiate drag event.
        injectFakeMouseMoveEvent(/* x= */ 100, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();

        // Even after the click, the click type should not be reset.
        assertThat(mController.getActiveClickTypeForTest())
                .isEqualTo(AutoclickTypePanel.AUTOCLICK_TYPE_DRAG);
        verify(mockAutoclickTypePanel, Mockito.never()).collapsePanelWithClickType(anyInt());
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void exitScrollMode_revertToLeftClickEnabled_resetsClickType() {
        initializeAutoclick();

        // Set ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK to true.
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK));

        // Set click type to scroll.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Show the scroll panel.
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        when(mockScrollPanel.isVisible()).thenReturn(true);
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Exit scroll mode.
        mController.exitScrollMode();

        // Verify click type is reset when exiting scroll mode.
        verify(mockAutoclickTypePanel).collapsePanelWithClickType(
                AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_longPress_triggerPressAndHold() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);
        // Set click type to long press.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS);
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();
        assertThat(motionEventCaptor.downEvent).isNotNull();
        assertThat(motionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(motionEventCaptor.upEvent).isNull();

        // When all messages (with delays) are processed.
        mTestableLooper.moveTimeForward(mController.LONG_PRESS_TIMEOUT);
        mTestableLooper.processAllMessages();
        assertThat(motionEventCaptor.upEvent).isNotNull();
        motionEventCaptor.assertCapturedEvents(
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS,
                MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_UP);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_longPress_interruptCancelsLongPress() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);
        // Set click type to long press.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS);
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        injectFakeMouseMoveEvent(/* x= */ 100f, /* y= */ 0, MotionEvent.ACTION_HOVER_MOVE);
        mTestableLooper.processAllMessages();
        assertThat(motionEventCaptor.downEvent).isNotNull();
        assertThat(motionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
        assertThat(motionEventCaptor.upEvent).isNull();
        assertThat(mController.hasOngoingLongPressForTesting()).isTrue();
        assertThat(motionEventCaptor.cancelEvent).isNull();

        // Send another hover move event to interrupt the long press.
        mTestableLooper.moveTimeForward(mController.LONG_PRESS_TIMEOUT / 2);
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);
        injectFakeMouseMoveEvent(/* x= */ 0, /* y= */ 30f, MotionEvent.ACTION_HOVER_MOVE);
        mController.mClickScheduler.run();
        mTestableLooper.processAllMessages();
        assertThat(motionEventCaptor.cancelEvent).isNotNull();
        assertThat(mController.hasOngoingLongPressForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void sendClick_clickType_longPress_revertsToLeftClick() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        // Move mouse to initialize autoclick panel.
        injectFakeMouseActionHoverMoveEvent();

        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS);

        // Set ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK to false.
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                AccessibilityUtils.State.OFF,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK));
        when(mockAutoclickTypePanel.isPaused()).thenReturn(false);
        assertThat(mController.mClickScheduler.getRevertToLeftClickForTesting()).isFalse();
        assertThat(mController.getActiveClickTypeForTest()).isEqualTo(
                AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS);

        // Send hover move event to trigger long press.
        when(mockAutoclickTypePanel.isPaused()).thenReturn(false);
        mController.mClickScheduler.run();
        mTestableLooper.moveTimeForward(mController.LONG_PRESS_TIMEOUT);
        mTestableLooper.processAllMessages();

        motionEventCaptor.assertCapturedEvents(
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS,
                MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_UP);

        verify(mockAutoclickTypePanel).collapsePanelWithClickType(
                AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void continuousScroll_completeLifecycle() {
        // Set up event capturer to track scroll events.
        ScrollEventCaptor scrollCaptor = new ScrollEventCaptor();
        mController.setNext(scrollCaptor);

        // Initialize controller.
        injectFakeMouseActionHoverMoveEvent();

        // Set cursor position.
        float expectedX = 100f;
        float expectedY = 200f;
        mController.mScrollCursorX = expectedX;
        mController.mScrollCursorY = expectedY;

        // Start scrolling by hovering UP button.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, true);

        // Verify initial hover state and event.
        assertThat(mController.mHoveredDirection).isEqualTo(AutoclickScrollPanel.DIRECTION_UP);
        assertThat(scrollCaptor.eventCount).isEqualTo(1);
        assertThat(scrollCaptor.scrollEvent.getAxisValue(MotionEvent.AXIS_VSCROLL)).isGreaterThan(
                0);

        // Simulate continuous scrolling by triggering runnable.
        scrollCaptor.eventCount = 0;

        // Advance time by CONTINUOUS_SCROLL_INTERVAL (30ms) and process messages.
        mTestableLooper.moveTimeForward(CONTINUOUS_SCROLL_INTERVAL);
        mTestableLooper.processAllMessages();

        // Advance time again to trigger second scroll event.
        mTestableLooper.moveTimeForward(CONTINUOUS_SCROLL_INTERVAL);
        mTestableLooper.processAllMessages();

        // Verify multiple scroll events were generated.
        assertThat(scrollCaptor.eventCount).isEqualTo(2);
        assertThat(scrollCaptor.scrollEvent.getX()).isEqualTo(expectedX);
        assertThat(scrollCaptor.scrollEvent.getY()).isEqualTo(expectedY);

        // Stop scrolling by un-hovering the button.
        mController.mScrollPanelController.onHoverButtonChange(
                AutoclickScrollPanel.DIRECTION_UP, false);

        // Verify direction is reset.
        assertThat(mController.mHoveredDirection).isEqualTo(AutoclickScrollPanel.DIRECTION_NONE);

        // Verify no more scroll events are generated after stopping.
        int countBeforeRunnable = scrollCaptor.eventCount;
        mTestableLooper.moveTimeForward(CONTINUOUS_SCROLL_INTERVAL);
        mTestableLooper.processAllMessages();
        assertThat(scrollCaptor.eventCount).isEqualTo(countBeforeRunnable);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void typePanelDrag_completeLifeCycle() {
        injectFakeMouseActionHoverMoveEvent();

        // Store initial position for comparison.
        WindowManager.LayoutParams initialParams =
                mController.mAutoclickTypePanel.getLayoutParamsForTesting();
        int initialX = initialParams.x;
        int initialY = initialParams.y;

        // Test onDragStart - should enable dragging and change cursor.
        MotionEvent dragStartEvent = MotionEvent.obtain(
                /* downTime= */ 0, /* eventTime= */ 0, MotionEvent.ACTION_DOWN,
                /* x= */ 100f, /* y= */ 100f, /* metaState= */ 0);
        mController.mAutoclickTypePanel.onDragStart(dragStartEvent);
        assertThat(mController.mAutoclickTypePanel.getIsDragging()).isTrue();
        assertThat(mController.mAutoclickTypePanel.getCurrentCursorForTesting().getType())
                .isEqualTo(PointerIcon.TYPE_GRABBING);

        // Test onDragMove - should update position and maintain drag state.
        MotionEvent dragMoveEvent = MotionEvent.obtain(
                /* downTime= */ 0, /* eventTime= */ 50, MotionEvent.ACTION_MOVE,
                /* x= */ 150f, /* y= */ 150f, /* metaState= */ 0);
        mController.mAutoclickTypePanel.onDragMove(dragMoveEvent);

        // Verify drag state maintained and gravity changed to absolute positioning
        assertThat(mController.mAutoclickTypePanel.getIsDragging()).isTrue();
        assertThat(mController.mAutoclickTypePanel.getCurrentCursorForTesting().getType())
                .isEqualTo(PointerIcon.TYPE_GRABBING);
        assertThat(mController.mAutoclickTypePanel.getLayoutParamsForTesting().gravity)
                .isEqualTo(Gravity.LEFT | Gravity.TOP);

        // Verify position coordinates actually changed from drag movement.
        WindowManager.LayoutParams dragParams =
                mController.mAutoclickTypePanel.getLayoutParamsForTesting();
        assertThat(dragParams.x).isNotEqualTo(initialX);
        assertThat(dragParams.y).isNotEqualTo(initialY);

        // Test onDragEnd - should reset state, change cursor, and snap to edge.
        mController.mAutoclickTypePanel.onDragEnd();
        assertThat(mController.mAutoclickTypePanel.getIsDragging()).isFalse();
        assertThat(mController.mAutoclickTypePanel.getCurrentCursorForTesting().getType())
                .isEqualTo(PointerIcon.TYPE_GRAB);

        // Verify panel snapped to edge.
        WindowManager.LayoutParams finalParams =
                mController.mAutoclickTypePanel.getLayoutParamsForTesting();
        boolean snappedToLeftEdge = (finalParams.gravity & Gravity.START) == Gravity.START;
        boolean snappedToRightEdge = (finalParams.gravity & Gravity.END) == Gravity.END;
        assertThat(snappedToLeftEdge || snappedToRightEdge).isTrue();

        dragStartEvent.recycle();
        dragMoveEvent.recycle();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void exitButton_exitsScrollMode() {
        // Initialize the controller.
        injectFakeMouseActionHoverMoveEvent();

        // Set the active click type to scroll.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Enable revert to left click setting.
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK));

        // Show the scroll panel and verify it's visible before pause.
        mController.mAutoclickScrollPanel.show();
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isTrue();

        // Simulate exit button click.
        mController.mScrollPanelController.onExitScrollMode();

        // Verify that the scroll panel is hidden.
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isFalse();

        // Verify that the click type is reset to left click.
        assertThat(mController.getActiveClickTypeForTest())
                .isEqualTo(AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onConfigurationChanged_notifiesIndicatorToUpdateTheme() throws Exception {
        injectFakeMouseActionHoverMoveEvent();

        // Create a spy on the real object to verify method calls.
        AutoclickIndicatorView spyIndicatorView = spy(mController.mAutoclickIndicatorView);
        mController.mAutoclickIndicatorView = spyIndicatorView;

        // Simulate a theme change.
        Configuration newConfig = new Configuration();
        mController.onConfigurationChanged(newConfig);

        // Verify updateConfiguration was called.
        verify(spyIndicatorView).onConfigurationChanged(newConfig);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onConfigurationChanged_notifiesTypePanelToUpdateTheme() throws Exception {
        injectFakeMouseActionHoverMoveEvent();

        // Create a spy on the real object to verify method calls.
        AutoclickTypePanel spyTypePanel = spy(mController.mAutoclickTypePanel);
        mController.mAutoclickTypePanel = spyTypePanel;

        // Simulate a theme change.
        Configuration newConfig = new Configuration();
        mController.onConfigurationChanged(newConfig);

        // Verify onThemeChanged was called.
        verify(spyTypePanel).onConfigurationChanged(newConfig);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onConfigurationChanged_notifiesScrollPanelToUpdateTheme() throws Exception {
        injectFakeMouseActionHoverMoveEvent();

        // Create a spy on the real object to verify method calls.
        AutoclickScrollPanel spyScrollPanel = spy(mController.mAutoclickScrollPanel);
        mController.mAutoclickScrollPanel = spyScrollPanel;

        // Simulate a theme change.
        Configuration newConfig = new Configuration();
        mController.onConfigurationChanged(newConfig);

        // Verify onConfigurationChanged was called.
        verify(spyScrollPanel).onConfigurationChanged(newConfig);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onInputDeviceChanged_disconnectAndReconnect_hidesAndShowsTypePanel() {
        // Setup: one mouse connected initially.
        mController.mInputManagerWrapper = mMockInputManagerWrapper;
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[] {1});
        AutoclickController.InputDeviceWrapper mockMouse =
                mock(AutoclickController.InputDeviceWrapper.class);
        when(mockMouse.supportsSource(InputDevice.SOURCE_MOUSE)).thenReturn(true);
        when(mockMouse.isEnabled()).thenReturn(true);
        when(mockMouse.isVirtual()).thenReturn(false);
        when(mMockInputManagerWrapper.getInputDevice(1)).thenReturn(mockMouse);

        // Initialize controller and panels.
        injectFakeMouseActionHoverMoveEvent();

        // Capture the listener.
        ArgumentCaptor<InputManager.InputDeviceListener> listenerCaptor =
                ArgumentCaptor.forClass(InputManager.InputDeviceListener.class);
        verify(mMockInputManagerWrapper)
                .registerInputDeviceListener(listenerCaptor.capture(), any());
        InputManager.InputDeviceListener listener = listenerCaptor.getValue();

        // Mock panels to verify interactions.
        AutoclickTypePanel mockTypePanel = mock(AutoclickTypePanel.class);
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickTypePanel = mockTypePanel;
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Action: disconnect mouse.
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[0]);
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify panels are hidden.
        verify(mockTypePanel).hide();
        verify(mockScrollPanel).hide();

        // Action: reconnect mouse.
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[] {1});
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify type panel is shown, but scroll panel is not.
        verify(mockTypePanel).show();
        verify(mockScrollPanel, Mockito.never()).show(anyFloat(), anyFloat());
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onInputDeviceChanged_noConnectionChange_panelsStateUnchanged() {
        // Setup: one mouse connected initially.
        mController.mInputManagerWrapper = mMockInputManagerWrapper;
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[] {1});
        AutoclickController.InputDeviceWrapper mockMouse =
                mock(AutoclickController.InputDeviceWrapper.class);
        when(mockMouse.supportsSource(InputDevice.SOURCE_MOUSE)).thenReturn(true);
        when(mockMouse.isEnabled()).thenReturn(true);
        when(mockMouse.isVirtual()).thenReturn(false);
        when(mMockInputManagerWrapper.getInputDevice(1)).thenReturn(mockMouse);

        // Initialize controller and panels.
        injectFakeMouseActionHoverMoveEvent();

        // Capture the listener.
        ArgumentCaptor<InputManager.InputDeviceListener> listenerCaptor =
                ArgumentCaptor.forClass(InputManager.InputDeviceListener.class);
        verify(mMockInputManagerWrapper)
                .registerInputDeviceListener(listenerCaptor.capture(), any());
        InputManager.InputDeviceListener listener = listenerCaptor.getValue();

        // Manually trigger once to establish initial connected state.
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Mock panels to verify interactions.
        AutoclickTypePanel mockTypePanel = mock(AutoclickTypePanel.class);
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickTypePanel = mockTypePanel;
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Action: trigger change, but connection state is the same (connected).
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify panels state is unchanged.
        verify(mockTypePanel, Mockito.never()).hide();
        verify(mockScrollPanel, Mockito.never()).hide();
        verify(mockTypePanel, Mockito.never()).show();

        // Action: disconnect mouse.
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[0]);
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify hide was called once.
        verify(mockTypePanel, times(1)).hide();
        verify(mockScrollPanel, times(1)).hide();

        // Action: trigger change, but connection state is the same (disconnected).
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify panels state is unchanged (hide not called again).
        verify(mockTypePanel, times(1)).hide();
        verify(mockScrollPanel, times(1)).hide();
        verify(mockTypePanel, Mockito.never()).show();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onInputDeviceChanged_touchpad_hidesAndShowsTypePanel() {
        // Setup: one touchpad connected initially.
        mController.mInputManagerWrapper = mMockInputManagerWrapper;
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[]{1});
        AutoclickController.InputDeviceWrapper mockTouchpad =
                mock(AutoclickController.InputDeviceWrapper.class);
        when(mockTouchpad.supportsSource(InputDevice.SOURCE_TOUCHPAD)).thenReturn(true);
        when(mockTouchpad.isEnabled()).thenReturn(true);
        when(mockTouchpad.isVirtual()).thenReturn(false);
        when(mMockInputManagerWrapper.getInputDevice(1)).thenReturn(mockTouchpad);

        // Initialize controller and panels.
        injectFakeMouseActionHoverMoveEvent();

        // Capture the listener.
        ArgumentCaptor<InputManager.InputDeviceListener> listenerCaptor =
                ArgumentCaptor.forClass(InputManager.InputDeviceListener.class);
        verify(mMockInputManagerWrapper)
                .registerInputDeviceListener(listenerCaptor.capture(), any());
        InputManager.InputDeviceListener listener = listenerCaptor.getValue();

        // Mock panels to verify interactions.
        AutoclickTypePanel mockTypePanel = mock(AutoclickTypePanel.class);
        AutoclickScrollPanel mockScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickTypePanel = mockTypePanel;
        mController.mAutoclickScrollPanel = mockScrollPanel;

        // Action: disconnect touchpad.
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[0]);
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify panels are hidden.
        verify(mockTypePanel).hide();
        verify(mockScrollPanel).hide();

        // Action: reconnect touchpad.
        when(mMockInputManagerWrapper.getInputDeviceIds()).thenReturn(new int[]{1});
        listener.onInputDeviceChanged(1);
        mTestableLooper.processAllMessages();

        // Verify type panel is shown, but scroll panel is not.
        verify(mockTypePanel).show();
        verify(mockScrollPanel, Mockito.never()).show(anyFloat(), anyFloat());
    }

    /**
     * =========================================================================
     * Helper Functions
     * =========================================================================
     */

    private void injectFakeMouseActionHoverMoveEvent() {
        injectFakeMouseMoveEvent(0, 0, MotionEvent.ACTION_HOVER_MOVE);
    }

    private void injectFakeMouseMoveEvent(float x, float y, int action) {
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ action,
                /* x= */ x,
                /* y= */ y,
                /* metaState= */ 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(event, event, /* policyFlags= */ 0);
    }

    private void injectFakeNonMouseActionHoverMoveEvent() {
        MotionEvent event = getFakeMotionHoverMoveEvent();
        event.setSource(InputDevice.SOURCE_KEYBOARD);
        mController.onMotionEvent(event, event, /* policyFlags= */ 0);
    }

    private void injectFakeKeyEvent(int keyCode, int modifiers) {
        KeyEvent keyEvent = new KeyEvent(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ KeyEvent.ACTION_DOWN,
                /* code= */ keyCode,
                /* repeat= */ 0,
                /* metaState= */ modifiers);
        mController.onKeyEvent(keyEvent, /* policyFlags= */ 0);
    }

    private MotionEvent getFakeMotionHoverMoveEvent() {
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);
    }

    private void enableIgnoreMinorCursorMovement() {
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT));
    }

    // The 4 events represented are DOWN, BUTTON_PRESS, BUTTON_RELEASE, and UP.
    private int getNumEventsExpectedFromClick(int numClicks) {
        return numClicks * 4;
    }

    private void initializeAutoclick() {
        mMotionEventCaptor = new MotionEventCaptor();
        mController.setNext(mMotionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);
    }
}
