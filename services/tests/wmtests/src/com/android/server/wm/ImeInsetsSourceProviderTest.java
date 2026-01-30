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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.android.server.wm.WindowStateAnimator.HAS_DRAWN;
import static com.android.server.wm.WindowStateAnimator.NO_SURFACE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.graphics.PixelFormat;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.view.WindowInsets;
import android.view.inputmethod.ImeTracker;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Tests for the {@link ImeInsetsSourceProvider} class.
 *
 * <p> Build/Install/Run:
 * atest WmTests:ImeInsetsSourceProviderTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class ImeInsetsSourceProviderTest extends WindowTestsBase {

    private ImeInsetsSourceProvider mImeProvider;
    private WindowManagerInternal.OnImeRequestedChangedListener mMockListener;

    @Before
    public void setUp() throws Exception {
        mImeProvider = mDisplayContent.getInsetsStateController().getImeSourceProvider();
        mImeProvider.getSource().setVisible(true);
        mMockListener = Mockito.mock(
                WindowManagerInternal.OnImeRequestedChangedListener.class);
        mWm.mOnImeRequestedChangedListener = mMockListener;

        mWm.mAnimator.ready();
    }

    @Test
    public void testTransparentControlTargetWindowCanShowIme() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);

        final WindowState appWin = newWindowBuilder("app", TYPE_APPLICATION).build();
        final WindowState popup = newWindowBuilder("popup", TYPE_APPLICATION).setParent(
                appWin).build();
        popup.mAttrs.format = PixelFormat.TRANSPARENT;
        appWin.setRequestedVisibleTypes(
                WindowInsets.Type.defaultVisible() | WindowInsets.Type.ime());
        mDisplayContent.setImeLayeringTarget(appWin);
        mDisplayContent.updateImeInputAndControlTarget(popup);
        performSurfacePlacementAndWaitForWindowAnimator();

        mImeProvider.onPostLayout();
        assertTrue(mImeProvider.isImeShowing());
    }

    @Test
    public void testSetFrozen() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        mImeProvider.updateVisibility();
        assertTrue(mImeProvider.getSource().isVisible());

        // Freezing IME states and set the server visible as false.
        mImeProvider.setFrozen(true);
        mImeProvider.setServerVisible(false);
        // Expect the IME insets visible won't be changed.
        assertTrue(mImeProvider.getSource().isVisible());

        // Unfreeze IME states and expect the IME insets became invisible due to pending IME
        // visible state updated.
        mImeProvider.setFrozen(false);
        assertFalse(mImeProvider.getSource().isVisible());
    }

    @Test
    public void testUpdateControlForTarget_remoteInsetsControlTarget() throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var controlTarget = mDisplayContent.mRemoteInsetsControlTarget;

        inputTarget.setRequestedVisibleTypes(
                WindowInsets.Type.defaultVisible() | WindowInsets.Type.ime());
        mDisplayContent.setImeInputTarget(inputTarget);
        mDisplayContent.setImeControlTargetForTesting(controlTarget);

        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse(controlTarget.isRequestedVisible(WindowInsets.Type.ime()));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        verify(displayWindowInsetsController, times(1)).setImeInputTargetRequestedVisibility(
                eq(true), any());
    }

    @Test
    public void testUpdateControlForTarget_remoteInsetsControlTargetUnchanged()
            throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        mImeProvider.setWindow(ime, null, null);
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var controlTarget = mDisplayContent.mRemoteInsetsControlTarget;
        mDisplayContent.setImeInputTarget(inputTarget);
        mDisplayContent.setImeControlTargetForTesting(controlTarget);

        // Test for visible
        inputTarget.setRequestedVisibleTypes(WindowInsets.Type.ime());
        controlTarget.updateRequestedVisibleTypes(WindowInsets.Type.ime(), WindowInsets.Type.ime());
        clearInvocations(mDisplayContent);
        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertTrue((controlTarget.isRequestedVisible(WindowInsets.Type.ime())));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        waitUntilHandlersIdle();
        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                anyBoolean(), any());
        verify(mMockListener, times(1)).onImeRequestedChanged(
                eq(inputTarget.getWindowToken()), eq(true), any());

        // Test for not visible
        inputTarget.setRequestedVisibleTypes(0);
        controlTarget.updateRequestedVisibleTypes(0 /* visibleTypes */, WindowInsets.Type.ime());
        clearInvocations(mDisplayContent);
        clearInvocations(mMockListener);
        assertFalse(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse((controlTarget.isRequestedVisible(WindowInsets.Type.ime())));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        waitUntilHandlersIdle();
        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                anyBoolean(), any());
        verify(mMockListener, times(1)).onImeRequestedChanged(
                eq(inputTarget.getWindowToken()), eq(false), any());
    }

    @Test
    public void testOnPreLayout_resetServerVisibilityWhenImeIsNotDrawn() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        mImeProvider.updateVisibility();
        mImeProvider.updateControlForTarget(inputTarget, true /* force */,
                ImeTracker.Token.empty());

        // Calling onPreLayout, as the drawn state is initially false.
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            mImeProvider.onPreLayout();
        } else {
            mImeProvider.onPostLayout();
        }
        assertTrue(mImeProvider.isSurfaceVisible());

        // Reset window's drawn state
        ime.mWinAnimator.mDrawState = NO_SURFACE;
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            mImeProvider.onPreLayout();
        } else {
            mImeProvider.onPostLayout();
        }
        assertFalse(mImeProvider.isServerVisible());
        assertFalse(mImeProvider.isSurfaceVisible());

        // Set it back to drawn
        ime.mWinAnimator.mDrawState = HAS_DRAWN;
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            mImeProvider.onPreLayout();
        } else {
            mImeProvider.onPostLayout();
        }
        assertTrue(mImeProvider.isServerVisible());
        assertTrue(mImeProvider.isSurfaceVisible());
    }

    /**
     * Verifies that {@code onPostLayout} can reset {@code isImeShowing} when the server visibility
     * was already set to false before the call.
     */
    @Test
    public void testOnPostLayout_resetImeShowingWhenAlreadyNotServerVisible() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        final WindowState target = newWindowBuilder("app", TYPE_APPLICATION).build();
        makeWindowVisibleAndDrawn(ime);

        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.updateControlForTarget(target, true /* force */, ImeTracker.Token.empty());

        mImeProvider.onPostLayout();
        assertTrue("Server visibility is still true after onPostLayout",
                mImeProvider.isServerVisible());
        assertTrue("IME showing is true after onPostLayout", mImeProvider.isImeShowing());

        // Removing the window container will set server visibility to false.
        mImeProvider.setWindow(null, null, null);
        assertFalse("Server visibility is false after removing window container",
                mImeProvider.isServerVisible());
        assertTrue("IME showing is still true before onPostLayout", mImeProvider.isImeShowing());

        mImeProvider.onPostLayout();
        assertFalse("Server visibility is still false after onPostLayout",
                mImeProvider.isServerVisible());
        assertFalse("IME showing is false after onPostLayout", mImeProvider.isImeShowing());
    }

    @Test
    public void testUpdateControlForTarget_differentControlTarget() {
        final WindowState oldTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final WindowState newTarget = newWindowBuilder("newApp", TYPE_APPLICATION).build();

        oldTarget.setRequestedVisibleTypes(
                WindowInsets.Type.defaultVisible() | WindowInsets.Type.ime());
        mDisplayContent.setImeControlTargetForTesting(oldTarget);
        mDisplayContent.setImeInputTarget(newTarget);

        // Having a null windowContainer will early return in updateControlForTarget
        mImeProvider.setWindow(null, null, null);

        clearInvocations(mDisplayContent);
        mImeProvider.updateControlForTarget(newTarget, false /* force */, ImeTracker.Token.empty());
        verify(mDisplayContent, never()).getImeInputTarget();
    }

    @Test
    public void testOnImeInputTargetChanged_invokesDisplayWindowInsetsController()
            throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);

        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var remoteControlTarget = mDisplayContent.mRemoteInsetsControlTarget;
        mDisplayContent.setImeInputTarget(inputTarget);
        mImeProvider.updateControlForTarget(
                remoteControlTarget, /* force= */ true, /* token= */ null);

        // IME should be visible, but remote control target currently doesn't request it
        inputTarget.setRequestedVisibleTypes(WindowInsets.Type.ime());
        remoteControlTarget.updateRequestedVisibleTypes(0, WindowInsets.Type.ime());
        clearInvocations(displayWindowInsetsController);

        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse(remoteControlTarget.isRequestedVisible(WindowInsets.Type.ime()));

        mImeProvider.onImeInputTargetChanged(inputTarget);

        verify(displayWindowInsetsController, times(1)).setImeInputTargetRequestedVisibility(
                eq(true), any());
        verifyNoMoreInteractions(displayWindowInsetsController);
    }

    @Test
    public void testOnImeInputTargetChanged_sameVisibility_invokesListener()
            throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindow(ime, null, null);
        mImeProvider.setServerVisible(true); // Ensure provider thinks IME *could* be showing

        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var remoteControlTarget = mDisplayContent.mRemoteInsetsControlTarget;
        mDisplayContent.setImeInputTarget(inputTarget);
        mImeProvider.updateControlForTarget(
                remoteControlTarget, /* force= */ true, /* token= */ null);

        // IME should be visible, and remote control target already requests it
        inputTarget.setRequestedVisibleTypes(WindowInsets.Type.ime());
        remoteControlTarget.updateRequestedVisibleTypes(WindowInsets.Type.ime(),
                WindowInsets.Type.ime());
        clearInvocations(displayWindowInsetsController);
        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertTrue(remoteControlTarget.isRequestedVisible(WindowInsets.Type.ime()));

        // Even though visibilities match, the listener should still be invoked.
        mImeProvider.onImeInputTargetChanged(inputTarget);
        waitUntilHandlersIdle();

        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                eq(true), any());
        verify(mMockListener, times(1)).onImeRequestedChanged(
                eq(inputTarget.getWindowToken()), eq(true), any());


        // The same test for invisible.
        inputTarget.setRequestedVisibleTypes(0);
        remoteControlTarget.updateRequestedVisibleTypes(0, WindowInsets.Type.ime());
        clearInvocations(displayWindowInsetsController);
        clearInvocations(mMockListener);
        assertFalse(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse(remoteControlTarget.isRequestedVisible(WindowInsets.Type.ime()));

        mImeProvider.onImeInputTargetChanged(inputTarget);
        waitUntilHandlersIdle();

        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                eq(true), any());
        verify(mMockListener, times(1)).onImeRequestedChanged(
                eq(inputTarget.getWindowToken()), eq(false), any());
    }
}
