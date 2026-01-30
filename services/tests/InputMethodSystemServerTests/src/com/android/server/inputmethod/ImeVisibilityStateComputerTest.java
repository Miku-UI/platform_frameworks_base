/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.accessibilityservice.AccessibilityService.SHOW_MODE_HIDDEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.MotionEvent.TOOL_TYPE_UNKNOWN;
import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_HIDE;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.ImeTargetWindowState;
import static com.android.server.inputmethod.InputMethodManagerService.FALLBACK_DISPLAY_ID;
import static com.android.server.inputmethod.InputMethodManagerService.ImeDisplayValidator;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;

import android.annotation.UserIdInt;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Test the behavior of {@link ImeVisibilityStateComputer} and {@link DefaultImeVisibilityApplier}
 * when requesting the IME visibility.
 *
 * <p> Build/Install/Run:
 * atest FrameworksInputMethodSystemServerTests:ImeVisibilityStateComputerTest
 */
@RunWith(AndroidJUnit4.class)
public class ImeVisibilityStateComputerTest extends InputMethodManagerServiceTestBase {
    private ImeVisibilityStateComputer mComputer;
    private int mImeDisplayPolicy = DISPLAY_IME_POLICY_LOCAL;

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        ImeVisibilityStateComputer.Injector injector = new ImeVisibilityStateComputer.Injector() {
            @Override
            public UserManagerInternal getUserManagerService() {
                return mMockUserManagerInternal;
            }

            @Override
            public WindowManagerInternal getWmService() {
                return mMockWindowManagerInternal;
            }

            @Override
            public ImeDisplayValidator getImeValidator() {
                return displayId -> mImeDisplayPolicy;
            }

            @UserIdInt
            @Override
            public int getUserId() {
                return mUserId;
            }
        };
        mComputer = new ImeVisibilityStateComputer(mInputMethodManagerService, injector);
    }

    @Test
    public void testRequestImeVisibility_showImplicit() {
        synchronized (ImfLock.class) {
            initImeTargetWindowState(mWindowToken);
            boolean res = mComputer.isAllowedByAccessibilityAndDisplayPolicy();
            mComputer.requestImeVisibility(mWindowToken, res);

            final ImeTargetWindowState state = mComputer.getWindowStateOrNull(mWindowToken);
            assertThat(state).isNotNull();
            assertThat(state.hasEditorFocused()).isTrue();
            assertThat(state.getSoftInputModeState()).isEqualTo(SOFT_INPUT_STATE_UNCHANGED);
            assertThat(state.isRequestedImeVisible()).isTrue();
        }
    }

    @Test
    public void testRequestImeVisibility_showExplicit() {
        synchronized (ImfLock.class) {
            initImeTargetWindowState(mWindowToken);
            boolean res = mComputer.isAllowedByAccessibilityAndDisplayPolicy();
            mComputer.requestImeVisibility(mWindowToken, res);

            final ImeTargetWindowState state = mComputer.getWindowStateOrNull(mWindowToken);
            assertThat(state).isNotNull();
            assertThat(state.hasEditorFocused()).isTrue();
            assertThat(state.getSoftInputModeState()).isEqualTo(SOFT_INPUT_STATE_UNCHANGED);
            assertThat(state.isRequestedImeVisible()).isTrue();
        }
    }

    @Test
    public void testRequestImeVisibility_showImplicit_a11yNoImePolicy() {
        synchronized (ImfLock.class) {
            // Precondition: set AccessibilityService#SHOW_MODE_HIDDEN policy
            mComputer.getImePolicy().setA11yRequestNoSoftKeyboard(SHOW_MODE_HIDDEN);

            initImeTargetWindowState(mWindowToken);
            boolean res = mComputer.isAllowedByAccessibilityAndDisplayPolicy();
            mComputer.requestImeVisibility(mWindowToken, res);

            final ImeTargetWindowState state = mComputer.getWindowStateOrNull(mWindowToken);
            assertThat(state).isNotNull();
            assertThat(state.hasEditorFocused()).isTrue();
            assertThat(state.getSoftInputModeState()).isEqualTo(SOFT_INPUT_STATE_UNCHANGED);
            assertThat(state.isRequestedImeVisible()).isFalse();
        }
    }

    @Test
    public void testRequestImeVisibility_showImplicit_imeHiddenPolicy() {
        synchronized (ImfLock.class) {
            // Precondition: set IME hidden display policy before calling showSoftInput
            mComputer.getImePolicy().setImeHiddenByDisplayPolicy(true);

            initImeTargetWindowState(mWindowToken);
            boolean res = mComputer.isAllowedByAccessibilityAndDisplayPolicy();
            mComputer.requestImeVisibility(mWindowToken, res);

            final ImeTargetWindowState state = mComputer.getWindowStateOrNull(mWindowToken);
            assertThat(state).isNotNull();
            assertThat(state.hasEditorFocused()).isTrue();
            assertThat(state.getSoftInputModeState()).isEqualTo(SOFT_INPUT_STATE_UNCHANGED);
            assertThat(state.isRequestedImeVisible()).isFalse();
        }
    }

    @Test
    public void testRequestImeVisibility_hideNotAlways() {
        synchronized (ImfLock.class) {
            // Precondition: ensure IME has shown before hiding request.
            mComputer.setInputShown(true);

            initImeTargetWindowState(mWindowToken);
            mComputer.requestImeVisibility(mWindowToken, false);

            final ImeTargetWindowState state = mComputer.getWindowStateOrNull(mWindowToken);
            assertThat(state).isNotNull();
            assertThat(state.hasEditorFocused()).isTrue();
            assertThat(state.getSoftInputModeState()).isEqualTo(SOFT_INPUT_STATE_UNCHANGED);
            assertThat(state.isRequestedImeVisible()).isFalse();
        }
    }

    @Test
    public void testComputeImeDisplayId() {
        synchronized (ImfLock.class) {
            final ImeTargetWindowState state = mComputer.getOrCreateWindowState(mWindowToken);

            mImeDisplayPolicy = DISPLAY_IME_POLICY_LOCAL;
            mComputer.computeImeDisplayId(state, DEFAULT_DISPLAY);
            assertThat(mComputer.getImePolicy().isImeHiddenByDisplayPolicy()).isFalse();
            assertThat(state.getImeDisplayId()).isEqualTo(DEFAULT_DISPLAY);

            mComputer.computeImeDisplayId(state, 10 /* displayId */);
            assertThat(mComputer.getImePolicy().isImeHiddenByDisplayPolicy()).isFalse();
            assertThat(state.getImeDisplayId()).isEqualTo(10);

            mImeDisplayPolicy = DISPLAY_IME_POLICY_HIDE;
            mComputer.computeImeDisplayId(state, 10 /* displayId */);
            assertThat(mComputer.getImePolicy().isImeHiddenByDisplayPolicy()).isTrue();
            assertThat(state.getImeDisplayId()).isEqualTo(INVALID_DISPLAY);

            mImeDisplayPolicy = DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
            mComputer.computeImeDisplayId(state, 10 /* displayId */);
            assertThat(mComputer.getImePolicy().isImeHiddenByDisplayPolicy()).isFalse();
            assertThat(state.getImeDisplayId()).isEqualTo(FALLBACK_DISPLAY_ID);
        }
    }

    @Test
    public void testComputeState_lastImeRequestedVisible_preserved_When_StateUnChanged() {
        synchronized (ImfLock.class) {
            // Assume the last IME targeted window has requested IME visible
            final IBinder lastImeTargetWindowToken = new Binder();
            mComputer.setLastImeTargetWindow(lastImeTargetWindowToken);
            mComputer.requestImeVisibility(lastImeTargetWindowToken, true);
            final ImeTargetWindowState lastState = mComputer.getWindowStateOrNull(
                    lastImeTargetWindowToken);
            assertThat(lastState.isRequestedImeVisible()).isTrue();

            // Verify when focusing the next window with STATE_UNCHANGED flag, the last IME
            // visibility state will be preserved to the current window state.
            final ImeTargetWindowState stateWithUnChangedFlag = initImeTargetWindowState(
                    mWindowToken);
            mComputer.computeState(stateWithUnChangedFlag, true /* allowVisible */,
                    true /* imeRequestedVisible */);
            assertThat(stateWithUnChangedFlag.isRequestedImeVisible()).isEqualTo(
                    lastState.isRequestedImeVisible());
        }
    }

    @Test
    public void testShouldShowImeScreenshot() {
        synchronized (ImfLock.class) {
            mComputer.getOrCreateWindowState(mWindowToken);
            // Precondition: ensure IME has shown before screenshot request.
            mComputer.requestImeVisibility(mWindowToken, true);
            mComputer.setInputShown(true);

            // No screenshot visibility change when IME initially shown while interactive.
            Boolean shouldShow = mComputer.shouldShowImeScreenshot(mWindowToken,
                    true /* interactive */);
            assertThat(shouldShow).isNull();

            // Show the screenshot when the device became non-interactive.
            shouldShow = mComputer.shouldShowImeScreenshot(mWindowToken, false /* interactive */);
            assertThat(shouldShow).isNotNull();
            assertThat(shouldShow).isTrue();

            // Remove the screenshot when the device became interactive again.
            shouldShow = mComputer.shouldShowImeScreenshot(mWindowToken, true /* interactive */);
            assertThat(shouldShow).isNotNull();
            assertThat(shouldShow).isFalse();
        }
    }

    @Test
    public void testOnApplyImeVisibilityFromComputer() {
        synchronized (ImfLock.class) {
            final IBinder testImeInputTarget = new Binder();

            // Simulate a test IME input target was visible.
            mComputer.onImeInputTargetVisibilityChanged(testImeInputTarget,
                    true /* visibleAndNotRemoved */);

            // Simulate a test IME layering target overlay fully occluded the IME input target.
            mComputer.setHasVisibleImeLayeringOverlay(true /* visibleAndNotRemoved */);
            mComputer.onImeInputTargetVisibilityChanged(testImeInputTarget,
                    false /* visibleAndNotRemoved */);
            final ArgumentCaptor<UserData> userDataCaptor = ArgumentCaptor.forClass(UserData.class);
            verify(mInputMethodManagerService).setImeVisibilityOnFocusedWindowClient(
                    eq(false) /* visible */, userDataCaptor.capture(), notNull() /* statsToken */);
            final UserData userData = userDataCaptor.getValue();

            // Verify the computer will callback hiding IME state to IMMS.
            assertThat(userData).isNotNull();
            assertThat(userData.mUserId).isEqualTo(mUserId);
        }
    }

    @GuardedBy("ImfLock.class")
    @NonNull
    private ImeTargetWindowState initImeTargetWindowState(@NonNull IBinder windowToken) {
        final var state = new ImeTargetWindowState(SOFT_INPUT_STATE_UNCHANGED,
                0 /* windowFlags */, true /* imeFocusChanged */, true /* hasFocusedEditor */,
                true /* isStartInputByWindowGainFocus */, TOOL_TYPE_UNKNOWN);
        mComputer.setWindowState(windowToken, state);
        return state;
    }
}
