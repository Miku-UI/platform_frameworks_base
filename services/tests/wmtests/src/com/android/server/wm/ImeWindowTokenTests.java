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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.inputmethod.Flags;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link ImeWindowToken} class.
 *
 * <p>Build/Install/Run:
 * atest WmTests:ImeWindowTokenTests
 */
@SmallTest
@Presubmit
@RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
@RunWith(WindowTestRunner.class)
public final class ImeWindowTokenTests extends WindowTestsBase {

    /**
     * Verifies that the ImeWindowToken visibility does not depend on its children visibility
     * (in contrast with the normal WindowToken).
     */
    @Test
    public void testVisibilityIndependentOfChildren() {
        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        assertWithMessage("Token should be initially not visible")
                .that(token.isVisible())
                .isFalse();

        final WindowState win = newWindowBuilder("win", TYPE_APPLICATION).setWindowToken(token)
                .build();
        verifyTokenAndChildVisibility(token, win, false /* clientVisible */, false /* visible */,
                "Initially");

        token.setClientVisible(true);
        verifyTokenAndChildVisibility(token, win, true /* clientVisible */, false /* visible */,
                "After token became clientVisible");

        makeWindowVisibleAndDrawn(win);
        verifyTokenAndChildVisibility(token, win, true /* clientVisible */, true /* visible */,
                "After window became visible and drawn");

        token.setClientVisible(false);
        verifyTokenAndChildVisibility(token, win, false /* clientVisible */, false /* visible */,
                "After token became not client visible");

        win.hide(false, false);
        verifyTokenAndChildVisibility(token, win, false /* clientVisible */, false /* visible */,
                "After window was hidden");

        win.show(false, false);
        verifyTokenAndChildVisibility(token, win, false /* clientVisible */, false /* visible */,
                "After window was shown while token was not clientVisible");
    }

    /**
     * Verifies that the {@link ImeContainer} is only visible if the ImeWindowToken has a visible
     * child window.
     */
    @Test
    public void testImeContainerVisibility() {
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        assertWithMessage("IME Container should be initially not visible")
                .that(imeContainer.isVisible())
                .isFalse();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        imeContainer.setImeWindowToken(token);
        verifyTokenAndChildVisibility(token, null /* child */, true /* clientVisible */,
                false /* visible */, "Initially");
        assertWithMessage("IME Container should still be not visible")
                .that(imeContainer.isVisible())
                .isFalse();

        final WindowState imeWin = newWindowBuilder("imeWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        verifyTokenAndChildVisibility(token, imeWin, true /* clientVisible */, false /* visible */,
                "After imeWin was added");
        assertWithMessage("IME Container should still not be visible with no visible window")
                .that(imeContainer.isVisible())
                .isFalse();

        makeWindowVisibleAndDrawn(imeWin);
        verifyTokenAndChildVisibility(token, imeWin, true /* clientVisible */, true /* visible */,
                "After imeWin became visible and drawn");
        assertWithMessage("IME Container should become visible")
                .that(imeContainer.isVisible())
                .isTrue();

        token.setClientVisible(false);
        verifyTokenAndChildVisibility(token, imeWin, false /* clientVisible */,
                false /* visible */, "After token became not clientVisible");
        assertWithMessage("IME Container become not visible")
                .that(imeContainer.isVisible())
                .isFalse();
    }

    /**
     * Verifies that removing an IME Window Token will update the DisplayContent state only if
     * it is the current token.
     */
    @Test
    public void testRemoveImmediately_updatesDisplayImeWindowToken() {
        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();

        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token1 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);

        token2.removeImmediately();
        assertWithMessage("Token1 should still be the current token after token2 is removed")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);

        token1.removeImmediately();
        assertWithMessage("Token1 should no longer be the current token after it is removed")
                .that(imeContainer.getImeWindowToken())
                .isNull();
    }

    /**
     * Verifies that setting an ImeWindowToken on the display sets it visible, and also sets all the
     * other ImeWindowTokens not visible.
     */
    @Test
    public void testOnImeWindowTokenChanged_setsOnlyCurrentTokenVisible() {
        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();

        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token1 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);
        verifyTokenAndChildVisibility(token1, null /* child */, true /* clientVisible */,
                false /* visible */, "Initially");

        imeContainer.setImeWindowToken(token2);
        assertWithMessage("Token2 should be the current display token")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token2);
        verifyTokenAndChildVisibility(token2, null /* child */, true /* clientVisible */,
                false /* visible */, "After token2 became the current token");
        verifyTokenAndChildVisibility(token1, null /* child */, false /* clientVisible */,
                false /* visible */, "After token1 was not the current token");
    }

    /** Verifies that setting a null ImeWindowToken will also set the IME window to null. */
    @Test
    public void testOnImeWindowTokenChanged_nullTokenSetsNullImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final WindowState imeWin = newWindowBuilder("imeWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token);
        assertWithMessage("IME window should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin);

        imeContainer.setImeWindowToken(null /* token */);
        assertWithMessage("IME window should now be null after ImeWindowToken was set to null")
                .that(mDisplayContent.getImeWindow())
                .isNull();
    }

    /** Verifies that setting an empty ImeWindowToken will also set the IME window to null. */
    @SetupWindows(addWindows = W_INPUT_METHOD)
    @Test
    public void testOnImeWindowTokenChanged_emptyTokenSetsNullImeWindow() {
        assertWithMessage("IME window should be initially not null")
                .that(mDisplayContent.getImeWindow())
                .isNotNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token);
        assertWithMessage("IME window should now be null after an empty ImeWindowToken was set")
                .that(mDisplayContent.getImeWindow())
                .isNull();
    }

    /**
     * Verifies that setting a new ImeWindowToken will set one of its children as the IME window
     * only if the current IME window is null.
     */
    @Test
    public void testOnImeWindowTokenChanged_setChildAsImeWindowOnlyIfCurrentNullImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token1 = createImeWindowToken(mDisplayContent);
        final WindowState imeWin1 = newWindowBuilder("imeWin1", TYPE_INPUT_METHOD)
                .setWindowToken(token1).build();
        final ImeContainer imeContainer = mDisplayContent.getImeContainer();
        imeContainer.setImeWindowToken(token1);
        assertWithMessage("Token 1 should be the current one")
                .that(imeContainer.getImeWindowToken())
                .isEqualTo(token1);
        assertWithMessage("IME window 1 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin1);

        final ImeWindowToken token2 = createImeWindowToken(mDisplayContent);
        newWindowBuilder("imeWin2", TYPE_INPUT_METHOD).setWindowToken(token2).build();
        imeContainer.setImeWindowToken(token2);
        assertWithMessage("Token 2 should be the current one")
                .that(imeContainer.getImeWindowToken())
                    .isEqualTo(token2);
        assertWithMessage("IME window 1 should still be the current one")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin1);
    }

    /**
     * Verifies that setting a new ImeWindowToken will set one of its children as the IME window
     * only if valid (window of type INPUT_METHOD that is touchable).
     */
    @Test
    public void testOnImeWindowTokenChanged_setChildAsImeWindowOnlyIfValid() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        newWindowBuilder("appWin", TYPE_APPLICATION).setWindowToken(token).build();
        final WindowState inkWin = newWindowBuilder("inkWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        inkWin.mAttrs.flags |= FLAG_NOT_TOUCHABLE;
        final WindowState imeWin = newWindowBuilder("imeWin", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        mDisplayContent.getImeContainer().setImeWindowToken(token);
        assertWithMessage("IME window 1 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin);
    }

    /**
     * Verifies that setting a new ImeWindowToken will set the first valid one of its children
     * (in top-to-bottom traversal order) as the IME window.
     */
    @Test
    public void testOnImeWindowTokenChanged_setFirstValidChildAsImeWindow() {
        assertWithMessage("IME window should be initially null")
                .that(mDisplayContent.getImeWindow())
                .isNull();

        final ImeWindowToken token = createImeWindowToken(mDisplayContent);
        newWindowBuilder("imeWin1", TYPE_INPUT_METHOD).setWindowToken(token).build();
        final WindowState imeWin2 = newWindowBuilder("imeWin2", TYPE_INPUT_METHOD)
                .setWindowToken(token).build();
        mDisplayContent.getImeContainer().setImeWindowToken(token);
        assertWithMessage("IME window 2 should be set on the display")
                .that(mDisplayContent.getImeWindow())
                .isEqualTo(imeWin2);
    }

    /**
     * Verifies the expected visibility of the given token and child window.
     *
     * @param token         the token to verify the visibility of.
     * @param child         the child to verify the visibility or, or {@code null} if there is no
     *                      child.
     * @param clientVisible the expected client visibility.
     * @param visible       the expected visibility.
     * @param message       the assertion message as a prefix to the failed state.
     */
    private static void verifyTokenAndChildVisibility(@NonNull ImeWindowToken token,
            @Nullable WindowState child, boolean clientVisible, boolean visible,
            @NonNull String message) {
        if (token.isClientVisible() != clientVisible || token.isVisible() != visible
                || (child != null && child.isVisibleRequested() != clientVisible)
                || (child != null && child.isVisible() != visible)) {
            final var sb = new StringBuilder(message);
            sb.append("\nExpected: {").append(getTokenVisibilityString(clientVisible, visible));
            if (child != null) {
                sb.append(" && ").append(getWindowVisibilityString(clientVisible, visible));
            }
            sb.append("}\nActual: {")
                    .append(getTokenVisibilityString(token.isClientVisible(), token.isVisible()));
            if (child != null) {
                sb.append(" && ").append(getWindowVisibilityString(child.isVisibleRequested(),
                        child.isVisible()));
            }
            sb.append("}");
            fail(sb.toString());
        }
    }

    /**
     * Gets a string describing the visibility of a {@link WindowToken}.
     *
     * @param clientVisible the client visibility.
     * @param visible       the visibility.
     */
    @NonNull
    private static String getTokenVisibilityString(boolean clientVisible, boolean visible) {
        return "token=" + (clientVisible ? "" : "!") + "clientVisible,"
                + (visible ? "" : "!") + "visible";
    }

    /**
     * Gets a string describing the visibility of a {@link WindowState}.
     *
     * @param visibleRequested the visibleRequested state.
     * @param visible          the visibility.
     */
    @NonNull
    private static String getWindowVisibilityString(boolean visibleRequested, boolean visible) {
        return "win=" + (visibleRequested ? "" : "!") + "visibleRequested,"
                + (visible ? "" : "!") + "visible";
    }
}
