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

package com.android.server.wm;

import static android.view.InsetsSource.ID_IME;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.InsetsSource;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class InsetsSourceProviderTest extends WindowTestsBase {

    @NonNull
    private final InsetsSource mSource = new InsetsSource(
            InsetsSource.createId(null, 0, statusBars()), statusBars());
    @NonNull
    private InsetsSourceProvider mProvider;
    @NonNull
    private final InsetsSource mImeSource = new InsetsSource(ID_IME, ime());
    @NonNull
    private InsetsSourceProvider mImeProvider;

    @Before
    public void setUp() throws Exception {
        mSource.setVisible(true);
        mProvider = new InsetsSourceProvider(mSource,
                mDisplayContent.getInsetsStateController(), mDisplayContent);
        mImeProvider = new InsetsSourceProvider(mImeSource,
                mDisplayContent.getInsetsStateController(), mDisplayContent);
    }

    @Test
    public void testPostLayout() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        statusBar.setBounds(0, 0, 500, 1000);
        statusBar.getFrame().set(0, 0, 500, 100);
        statusBar.mHasSurface = true;
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateSourceFrame(statusBar.getFrame());
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            // serverVisibility is updated in onPreLayout
            mProvider.onPreLayout();
        }
        mProvider.onPostLayout();
        assertEquals(new Rect(0, 0, 500, 100), mProvider.getSource().getFrame());
        assertEquals(Insets.of(0, 100, 0, 0), mProvider.getInsetsHint());

        // Change the bounds and call onPostLayout. Make sure the insets hint gets updated.
        statusBar.setBounds(0, 10, 500, 1000);
        mProvider.onPostLayout();
        assertEquals(Insets.of(0, 90, 0, 0), mProvider.getInsetsHint());
    }

    @Test
    public void testPostLayout_invisible() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        statusBar.setBounds(0, 0, 500, 1000);
        statusBar.getFrame().set(0, 0, 500, 100);
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateSourceFrame(statusBar.getFrame());
        mProvider.onPostLayout();
        assertTrue(mProvider.getSource().getFrame().isEmpty());
        assertEquals(Insets.NONE, mProvider.getInsetsHint());
    }

    @Test
    public void testPostLayout_frameProvider() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);
        statusBar.mHasSurface = true;
        mProvider.setWindow(statusBar,
                (displayFrames, windowState, rect) -> {
                    rect.set(10, 10, 20, 20);
                    return 0;
                }, null);
        mProvider.updateSourceFrame(statusBar.getFrame());
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            // serverVisibility is updated in onPreLayout
            mProvider.onPreLayout();
        }
        mProvider.onPostLayout();
        assertEquals(new Rect(10, 10, 20, 20), mProvider.getSource().getFrame());
    }

    @Test
    public void testUpdateControlForTarget() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);

        // We must not have control or control target before we have the insets source window.
        mProvider.updateControlForTarget(target, true /* force */, null /* statsToken */);
        assertNull(mProvider.getControl(target));
        assertNull(mProvider.getControlTarget());

        // We can have the control or the control target after we have the insets source window.
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        assertNotNull(mProvider.getControl(target));
        assertNotNull(mProvider.getControlTarget());

        // We can clear the control and the control target.
        mProvider.updateControlForTarget(null, false /* force */, null /* statsToken */);
        assertNull(mProvider.getControl(target));
        assertNull(mProvider.getControlTarget());

        // We must not have control or control target if the insets source window doesn't have a
        // surface.
        final SurfaceControl sc = statusBar.mSurfaceControl;
        statusBar.setSurfaceControl(null);
        mProvider.updateControlForTarget(target, true /* force */, null /* statsToken */);
        assertNull(mProvider.getControl(target));
        assertNull(mProvider.getControlTarget());

        // Verifies that the control is revoked immediately if the target becomes null even if
        // InsetsSourceProvider#mHasPendingPosition is true.
        statusBar.setSurfaceControl(sc);
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        assertNotNull(statusBar.getAnimationLeash());
        statusBar.getFrame().offset(100, 100);
        spyOn(statusBar.mWinAnimator);
        doReturn(true).when(statusBar.mWinAnimator).getShown();
        mProvider.updateInsetsControlPosition(statusBar);
        assertTrue(statusBar.shouldSyncWithBuffers());
        mProvider.updateControlForTarget(null, false /* force */, null /* statsToken */);
        assertNull(statusBar.getAnimationLeash());
    }

    @Test
    public void testUpdateControlForFakeTarget() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateFakeControlTarget(target);
        final var control = mProvider.getControl(target);
        assertNotNull(control);
        assertNull(control.getLeash());
        mProvider.updateFakeControlTarget(null);
        assertNull(mProvider.getControl(target));
    }

    @Test
    public void testGetLeash() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();
        final WindowState fakeTarget = newWindowBuilder("fakeTarget", TYPE_APPLICATION).build();
        final WindowState otherTarget = newWindowBuilder("otherTarget", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);

        // We must not have control or control target before we have the insets source window,
        // so also no leash.
        mProvider.updateControlForTarget(target, true /* force */, null /* statsToken */);
        assertNull(mProvider.getControl(target));
        assertNull(mProvider.getControlTarget());
        assertNull(mProvider.getLeash(target));

        // We can have the control or the control target after we have the insets source window,
        // but no leash as this is not yet ready for dispatching.
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        assertNotNull(mProvider.getControl(target));
        assertNotNull(mProvider.getControlTarget());
        assertEquals(mProvider.getControlTarget(), target);
        assertNull(mProvider.getLeash(target));

        // Set the leash to be ready for dispatching.
        mProvider.mIsLeashInitialized = true;
        assertNotNull(mProvider.getLeash(target));

        // We do have fake control for the fake control target, but that has no leash.
        mProvider.updateFakeControlTarget(fakeTarget);
        assertNotNull(mProvider.getControl(fakeTarget));
        assertNotNull(mProvider.getFakeControlTarget());
        assertNotEquals(mProvider.getControlTarget(), fakeTarget);
        assertNull(mProvider.getLeash(fakeTarget));

        // We don't have any control for a different (non-fake control target), so also no leash.
        assertNull(mProvider.getControl(otherTarget));
        assertNotNull(mProvider.getControlTarget());
        assertNotEquals(mProvider.getControlTarget(), otherTarget);
        assertNull(mProvider.getLeash(otherTarget));
    }

    @Test
    public void testUpdateSourceFrame() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        mProvider.setWindow(statusBar, null, null);
        statusBar.setBounds(0, 0, 500, 1000);

        mProvider.setServerVisible(true);
        statusBar.getFrame().set(0, 0, 500, 100);
        mProvider.updateSourceFrame(statusBar.getFrame());
        assertEquals(statusBar.getFrame(), mProvider.getSource().getFrame());
        assertEquals(Insets.of(0, 100, 0, 0), mProvider.getInsetsHint());

        // Only change the source frame but not the visibility.
        statusBar.getFrame().set(0, 0, 500, 90);
        mProvider.updateSourceFrame(statusBar.getFrame());
        assertEquals(statusBar.getFrame(), mProvider.getSource().getFrame());
        assertEquals(Insets.of(0, 90, 0, 0), mProvider.getInsetsHint());

        mProvider.setServerVisible(false);
        statusBar.getFrame().set(0, 0, 500, 80);
        mProvider.updateSourceFrame(statusBar.getFrame());
        assertTrue(mProvider.getSource().getFrame().isEmpty());
        assertEquals(Insets.of(0, 90, 0, 0), mProvider.getInsetsHint());

        // Only change the visibility but not the frame.
        mProvider.setServerVisible(true);
        assertEquals(statusBar.getFrame(), mProvider.getSource().getFrame());
        assertEquals(Insets.of(0, 80, 0, 0), mProvider.getInsetsHint());
    }

    @Test
    public void testUpdateSourceFrameForIme() {
        final WindowState inputMethod = newWindowBuilder("inputMethod", TYPE_INPUT_METHOD).build();

        inputMethod.getFrame().set(new Rect(0, 400, 500, 500));

        mImeProvider.setWindow(inputMethod, null, null);
        mImeProvider.setServerVisible(false);
        mImeSource.setVisible(true);
        mImeProvider.updateSourceFrame(inputMethod.getFrame());
        assertEquals(new Rect(0, 0, 0, 0), mImeSource.getFrame());
        Insets insets = mImeSource.calculateInsets(new Rect(0, 0, 500, 500),
                null /* hostBounds */, false /* ignoreVisibility */);
        assertEquals(Insets.of(0, 0, 0, 0), insets);

        mImeProvider.setServerVisible(true);
        mImeSource.setVisible(true);
        mImeProvider.updateSourceFrame(inputMethod.getFrame());
        assertEquals(inputMethod.getFrame(), mImeSource.getFrame());
        insets = mImeSource.calculateInsets(new Rect(0, 0, 500, 500),
                null /* hostBounds */, false /* ignoreVisibility */);
        assertEquals(Insets.of(0, 0, 0, 100), insets);
    }

    @Test
    public void testUpdateInsetsControlPosition() {
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();

        final WindowState ime1 = newWindowBuilder("ime1", TYPE_INPUT_METHOD).build();
        ime1.getFrame().set(new Rect(0, 0, 0, 0));
        mImeProvider.setWindow(ime1, null, null);
        mImeProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        ime1.getFrame().set(new Rect(0, 400, 500, 500));
        mImeProvider.updateInsetsControlPosition(ime1);
        var control = mImeProvider.getControl(target);
        assertNotNull(control);
        assertEquals(new Point(0, 400), control.getSurfacePosition());

        final WindowState ime2 = newWindowBuilder("ime2", TYPE_INPUT_METHOD).build();
        ime2.getFrame().set(new Rect(0, 0, 0, 0));
        mImeProvider.setWindow(ime2, null, null);
        mImeProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        ime2.getFrame().set(new Rect(0, 400, 500, 500));
        mImeProvider.updateInsetsControlPosition(ime2);
        control = mImeProvider.getControl(target);
        assertNotNull(control);
        assertEquals(new Point(0, 400), control.getSurfacePosition());
    }

    @Test
    public void testSetRequestedVisibleTypes() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateControlForTarget(target, false /* force */, null /* statsToken */);
        target.setRequestedVisibleTypes(0, statusBars());
        mProvider.updateClientVisibility(target, null /* statsToken */);
        assertFalse(mSource.isVisible());
    }

    @Test
    public void testSetRequestedVisibleTypes_noControl() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        final WindowState target = newWindowBuilder("target", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);
        mProvider.setWindow(statusBar, null, null);
        target.setRequestedVisibleTypes(0, statusBars());
        mProvider.updateClientVisibility(target, null /* statsToken */);
        assertTrue(mSource.isVisible());
    }

    @Test
    public void testInsetGeometries() {
        final WindowState statusBar = newWindowBuilder("statusBar", TYPE_APPLICATION).build();
        statusBar.getFrame().set(0, 0, 500, 100);
        statusBar.mHasSurface = true;
        mProvider.setWindow(statusBar, null, null);
        mProvider.updateSourceFrame(statusBar.getFrame());
        if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            // serverVisibility is updated in onPreLayout
            mProvider.onPreLayout();
        }
        mProvider.onPostLayout();
        assertEquals(new Rect(0, 0, 500, 100), mProvider.getSource().getFrame());
        // Still apply top insets if window overlaps even if it's top doesn't exactly match
        // the inset-window's top.
        assertEquals(Insets.of(0, 100, 0, 0),
                mProvider.getSource().calculateInsets(new Rect(0, -100, 500, 400),
                        null /* hostBounds */, false /* ignoreVisibility */));

        // Don't apply left insets if window is left-of inset-window but still overlaps
        statusBar.getFrame().set(100, 0, 0, 0);
        assertEquals(Insets.of(0, 0, 0, 0),
                mProvider.getSource().calculateInsets(new Rect(-100, 0, 400, 500),
                        null /* hostBounds */, false /* ignoreVisibility */));
    }
}
