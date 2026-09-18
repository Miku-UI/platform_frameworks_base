/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.wm.utils.LastCallVerifier.lastCall;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;

import android.graphics.Color;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.window.ITaskFragmentOrganizer;
import android.window.TaskFragmentOrganizer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.VerificationMode;

/**
 * Build/Install/Run:
 *  atest WmTests:DimmerTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class DimmerTests extends WindowTestsBase {

    @Before
    public void setUp() throws Exception {
        spyOn(mWm.mSurfaceAnimationRunner);
        // Avoid scheduling frame on animation thread.
        mWm.mSurfaceAnimationRunner.mChoreographer = mock(Choreographer.class);
    }

    @Test
    public void testBoundsInActivityEmbeddingForWholeTask() {
        final WindowState win = createNonFillEmbeddedActivityWindow();
        final TaskFragment taskFragment = win.getTaskFragment();
        taskFragment.setEmbeddedDimArea(TaskFragment.EMBEDDED_DIM_AREA_PARENT_TASK);
        final Task task = win.getTask();
        final Dimmer dimmer = task.mDimmer;

        dimmer.adjustAppearance(win, 1 /* alpha */, 1 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);
        verifySurfaceCrop(dimmer.getDimLayer(), task.getBounds(), 0 /* x */, 0 /* y */);
    }

    @Test
    public void testBoundsInActivityEmbeddingForTaskFragmentOnly() {
        final WindowState win = createNonFillEmbeddedActivityWindow();
        final TaskFragment taskFragment = win.getTaskFragment();
        taskFragment.setEmbeddedDimArea(TaskFragment.EMBEDDED_DIM_AREA_TASK_FRAGMENT);
        final Task task = win.getTask();
        final Dimmer dimmer = task.mDimmer;

        dimmer.adjustAppearance(win, 1 /* alpha */, 1 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);
        final Rect tfBounds = taskFragment.getBounds();
        final int expectedRelativeX = tfBounds.left - task.getBounds().left;
        final int expectedRelativeY = tfBounds.top - task.getBounds().top;
        verifySurfaceCrop(dimmer.getDimLayer(), tfBounds, expectedRelativeX, expectedRelativeY);
        assertEquals(tfBounds, dimmer.getDimBounds());
    }

    /**
     * Creates an embedded activity window which belongs to a TF that doesn't fill the task.
     * The dimmer will be from the task.
     */
    private WindowState createNonFillEmbeddedActivityWindow() {
        final TaskFragmentOrganizer organizer = new TaskFragmentOrganizer(Runnable::run);
        registerTaskFragmentOrganizer(ITaskFragmentOrganizer.Stub.asInterface(
                organizer.getOrganizerToken().asBinder()));
        final Task task = createTask(mDisplayContent, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        // Use a non-zero position to verify the dim crop.
        final Rect shrinkBounds = new Rect(task.getBounds());
        shrinkBounds.inset(10, 10, 10, 10);
        task.setBounds(shrinkBounds);
        final TaskFragment taskFragment = createTaskFragmentWithEmbeddedActivity(task, organizer);
        // Simulate a right-side split to verify EmbeddedDimArea.
        final Rect halfBounds = new Rect(taskFragment.getBounds());
        halfBounds.left += halfBounds.width() / 2;
        taskFragment.setBounds(halfBounds);
        final WindowState win = newWindowBuilder("embedded", TYPE_BASE_APPLICATION)
                .setWindowToken(taskFragment.getTopMostActivity()).build();
        win.mActivityRecord.setVisibleRequested(true);
        win.mActivityRecord.setVisible(true);
        // Reduce the unrelated recorded operations of the global mocked transaction.
        clearInvocations(mTransaction);
        return win;
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testDimBoundsAdaptToResizing() {
        final Task task = mAppWindow.getTask();
        final Dimmer dimmer = task.mDimmer;
        // First call with some generic bounds
        dimmer.adjustAppearance(mAppWindow, 0.5f /* alpha */, 1 /* blurRadius */);
        dimmer.adjustPosition(mAppWindow, mAppWindow);
        dimmer.updateDims(mTransaction);
        verifySurfaceCrop(dimmer.getDimLayer(), task.getBounds(), 0 /* x */, 0 /* y */);

        // Change bounds
        final Rect newBounds = new Rect(task.getBounds());
        newBounds.left += 5;
        newBounds.top += 6;
        task.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        task.setBounds(newBounds);
        dimmer.adjustAppearance(mAppWindow, 1 /* alpha */, 1 /* blurRadius */);
        dimmer.adjustPosition(mAppWindow, mAppWindow);
        clearInvocations(mTransaction);
        dimmer.updateDims(mTransaction);
        verifySurfaceCrop(dimmer.getDimLayer(), newBounds, 0 /* x */, 0 /* y */);
    }

    private void verifySurfaceCrop(SurfaceControl dimLayer, Rect crop, int x, int y) {
        crop = new Rect(crop);
        crop.offsetTo(x, y);
        verify(mTransaction).setCrop(dimLayer, crop);
    }

    @Test
    public void testDimBelowWithChildSurfaceCreatesSurfaceBelowChild() {
        final float alpha = 0.7f;
        final int blur = 50;
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win, alpha, blur);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();

        assertNotNull("Dimmer should have created a surface", dimLayer);

        dimmer.updateDims(mTransaction);
        invokeAnimationEndCallback();
        verify(mTransaction).setRelativeLayer(dimLayer, win.getSurfaceControl(), -1);
        verify(mTransaction, lastCall()).setAlpha(dimLayer, alpha);
        verify(mTransaction).setBackgroundBlurRadius(dimLayer, blur);
    }

    @Test
    public void testDimBelowWithChildSurfaceDestroyedWhenReset() {
        final float alpha = 0.8f;
        final int blur = 50;
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        // Dim once
        dimmer.adjustAppearance(win, alpha, blur);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        updateDimsToRequestedState(dimmer);
        // Reset, and don't dim
        dimmer.resetDimStates();
        dimmer.adjustPosition(win, win);
        updateDimsToRequestedState(dimmer);
        verify(mTransaction).show(dimLayer);
        verify(mTransaction).remove(dimLayer);
    }

    @Test
    public void testDimBelowWithChildSurfaceNotDestroyedWhenPersisted() {
        final float alpha = 0.8f;
        final int blur = 20;
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        // Dim once
        dimmer.adjustAppearance(win, alpha, blur);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        dimmer.updateDims(mTransaction);
        // Reset and dim again
        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, alpha, blur);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);
        verify(mTransaction).show(dimLayer);
        verify(mTransaction, never()).remove(dimLayer);
    }

    @Test
    public void testRemoveDimImmediately() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win, 1 /* alpha */, 2 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        dimmer.updateDims(mTransaction);
        verify(mTransaction, times(1)).show(dimLayer);

        clearInvocations(mWm.mSurfaceAnimationRunner);
        dimmer.dontAnimateExit();
        dimmer.resetDimStates();
        dimmer.updateDims(mTransaction);
        invokeAnimationEndCallback(never());
        verify(mTransaction).remove(dimLayer);
    }

    /**
     * A window is requesting the dim values to be set directly. In this case, dim won't play the
     * standard animation, but directly apply the window's requests to the dim surface.
     */
    @Test
    public void testContainerDimsOpeningAnimationByItself() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win, 0.1f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        updateDimsToRequestedState(dimmer);

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, 0.2f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, 0.3f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);

        verify(mTransaction).setAlpha(dimLayer, 0.2f);
        verify(mTransaction).setAlpha(dimLayer, 0.3f);
    }

    /**
     * Same as testContainerDimsOpeningAnimationByItself, but this is a more specific case in which
     * alpha is animated to 0. This corner case is needed to verify that the layer is removed anyway
     */
    @Test
    public void testContainerDimsClosingAnimationByItself() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, 0.2f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        dimmer.updateDims(mTransaction);

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, 0.1f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win, 0f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        dimmer.updateDims(mTransaction);

        dimmer.resetDimStates();
        dimmer.updateDims(mTransaction);
        verify(mTransaction).remove(dimLayer);
    }

    /**
     * Check the handover of the dim between two windows and the consequent dim animation in between
     */
    @Test
    public void testMultipleContainersDimmingConsecutively() {
        final WindowState win1 = createSystemWindow("win1");
        final WindowState win2 = createSystemWindow("win2");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win1, 0.5f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win1, win1);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        dimmer.updateDims(mTransaction);
        invokeAnimationEndCallback();

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win2, 0.9f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win1, win2);
        dimmer.updateDims(mTransaction);
        invokeAnimationEndCallback();

        verify(mTransaction).setAlpha(dimLayer, 0.5f);
        verify(mTransaction).setAlpha(dimLayer, 0.9f);
    }

    /**
     * Two windows are trying to modify the dim at the same time, but only the last request before
     * updateDims will be satisfied
     */
    @Test
    public void testMultipleContainersDimmingAtTheSameTime() {
        final WindowState win1 = createSystemWindow("win1");
        final WindowState win2 = createSystemWindow("win2");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win1, 0.5f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win1, win1);
        final SurfaceControl dimLayer = dimmer.getDimLayer();
        final SurfaceControl.Transaction t = dimmer.mDimState.mHostContainer.getSyncTransaction();
        dimmer.adjustAppearance(win2, 0.9f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win1, win2);
        updateDimsToRequestedState(dimmer);

        verify(t, never()).setAlpha(dimLayer, 0.5f);
        verify(t).setAlpha(dimLayer, 0.9f);
    }

    /**
     * A window requesting to dim to 0 and without blur would cause the dim to be created and
     * destroyed continuously.
     * Ensure the dim layer is not created until the window is requesting valid values.
     */
    @Test
    public void testDimNotCreatedIfNoAlphaNoBlur() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win, 0.0f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        assertNull(dimmer.getDimLayer());
        dimmer.updateDims(mTransaction);
        assertNull(dimmer.getDimLayer());

        dimmer.adjustAppearance(win, 0.9f /* alpha */, 0 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        assertNotNull(dimmer.getDimLayer());
    }

    /**
     * If there is a blur, then the dim layer is created even though alpha is 0
     */
    @Test
    public void testDimCreatedIfNoAlphaButHasBlur() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;
        dimmer.adjustAppearance(win, 0.0f /* alpha */, 10 /* blurRadius */);
        dimmer.adjustPosition(win, win);
        assertNotNull(dimmer.getDimLayer());
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_SUPPORT_CUSTOM_DIM_COLOR)
    public void testCustomDimColorApplied() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;

        long customColor = Color.pack(Color.GREEN);
        float[] expectedColor = new float[] {0f, 1f, 0f};

        dimmer.adjustAppearance(win, 0.7f , 20 , customColor);
        dimmer.adjustPosition(win, win);

        updateDimsToRequestedState(dimmer);

        verify(mTransaction).setColor(dimmer.getDimLayer(), expectedColor);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_SUPPORT_CUSTOM_DIM_COLOR)
    public void testDefaultDimColorIsBlack() {
        final WindowState win = createSystemWindow("win");
        final Dimmer dimmer = mDisplayContent.mDimmer;

        float[] expectedBlack = new float[] {0f, 0f, 0f};

        dimmer.adjustAppearance(win, 0.5f, 10);
        dimmer.adjustPosition(win, win);
        updateDimsToRequestedState(dimmer);

        verify(mTransaction).setColor(dimmer.getDimLayer(), expectedBlack);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_SUPPORT_CUSTOM_DIM_COLOR)
    public void testMultipleWindowsDifferentDimColors() {
        final WindowState bottomWin = createSystemWindow("bottomWin");
        final WindowState topWin = createSystemWindow("topWin");
        final Dimmer dimmer = mDisplayContent.mDimmer;

        long bottomColor = Color.pack(Color.RED);
        long topColor = Color.pack(Color.BLUE);
        float[] expectedTopColor = new float[] {0f, 0f, 1f}; // BLUE
        float[] bottomColorArray = new float[] {1f, 0f, 0f}; // RED

        dimmer.adjustAppearance(bottomWin, 0.5f, 10, bottomColor);
        dimmer.adjustAppearance(topWin, 0.5f, 10, topColor);
        dimmer.adjustPosition(topWin, topWin);

        updateDimsToRequestedState(dimmer);

        verify(mTransaction).setColor(dimmer.getDimLayer(), expectedTopColor);
        verify(mTransaction, never()).setColor(dimmer.getDimLayer(), bottomColorArray);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_SUPPORT_CUSTOM_DIM_COLOR)
    public void testDimColorHandover() {
        final WindowState win1 = createSystemWindow("win1");
        final WindowState win2 = createSystemWindow("win2");
        final Dimmer dimmer = mDisplayContent.mDimmer;

        dimmer.adjustAppearance(win1, 1.0f, 0, Color.pack(Color.RED));
        dimmer.adjustPosition(win1, win1);
        updateDimsToRequestedState(dimmer);

        dimmer.resetDimStates();
        dimmer.adjustAppearance(win2, 1.0f, 0, Color.pack(Color.BLUE));
        dimmer.adjustPosition(win1, win2);

        updateDimsToRequestedState(dimmer);

        // Assert: Verify the final color applied to the dim layer's surface is BLUE.
        final ArgumentCaptor<float[]> colorCaptor = ArgumentCaptor.forClass(float[].class);
        // The animation, when finished, should have set the final color on the transaction.
        // We verify the last call to setColor for the dim layer.
        verify(mTransaction, atLeastOnce())
                .setColor(eq(dimmer.getDimLayer()), colorCaptor.capture());

        float[] expectedBlue = new float[] { 0f, 0f, 1f }; // Color.BLUE
        assertArrayEquals("Dimmer color should be set to BLUE after the animation.",
                expectedBlue, colorCaptor.getValue(), 0.0f);
    }

    /** Called after {@link Dimmer#updateDims} to apply the end state of dim. */
    private void invokeAnimationEndCallback() {
        invokeAnimationEndCallback(times(1));
    }

    /** This also asserts that the dim animation is played. */
    private void updateDimsToRequestedState(Dimmer dimmer) {
        dimmer.updateDims(mTransaction);
        invokeAnimationEndCallback(times(1));
    }

    private void invokeAnimationEndCallback(VerificationMode verification) {
        final ArgumentCaptor<Runnable> endCallbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWm.mSurfaceAnimationRunner, verification).startAnimation(
                any(LocalAnimationAdapter.AnimationSpec.class), any(SurfaceControl.class),
                any(SurfaceControl.Transaction.class), endCallbackCaptor.capture());
        for (Runnable finishCallback : endCallbackCaptor.getAllValues()) {
            finishCallback.run();
        }
        clearInvocations(mWm.mSurfaceAnimationRunner);
    }

    /** Creates a non-activity window. The dimmer will be from DisplayArea.Dimmable. */
    private WindowState createSystemWindow(String name) {
        final WindowState win = newWindowBuilder(name, TYPE_SYSTEM_DIALOG).build();
        // Reduce the unrelated recorded operations of the global mocked transaction.
        clearInvocations(mTransaction);
        return win;
    }
}
