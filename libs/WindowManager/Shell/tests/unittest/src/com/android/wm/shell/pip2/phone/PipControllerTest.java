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

package com.android.wm.shell.pip2.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.times;
import static org.mockito.kotlin.VerificationKt.verify;

import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TabletopModeController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PhonePipKeepClearAlgorithm;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipController}
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(AndroidTestingRunner.class)
public class PipControllerTest extends ShellTestCase {
    private PipController mPipController;
    private PipController mSpyPipController;
    private PipBoundsState mSpyPipBoundsState;
    @Mock private ShellInit mShellInit;
    @Mock private ShellCommandHandler mMockShellCommandHandler;
    @Mock private ShellController mShellController;
    @Mock private DisplayController mMockDisplayController;
    @Mock private DisplayInsetsController mMockDisplayInsetsController;
    @Mock private PipBoundsAlgorithm mMockPipBoundsAlgorithm;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    @Mock private PipScheduler mMockPipScheduler;
    @Mock private TaskStackListenerImpl mMockTaskStackListener;
    @Mock private ShellTaskOrganizer mMockShellTaskOrganizer;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipTouchHandler mMockPipTouchHandler;
    @Mock private PipAppOpsListener mMockPipAppOpsListener;
    @Mock private PhonePipMenuController mMockPhonePipMenuController;
    @Mock private PipUiEventLogger mMockPipUiEventLogger;
    @Mock private PipMediaController mMockPipMediaController;
    @Mock private TabletopModeController mMockTabletopModeController;
    @Mock private PhonePipKeepClearAlgorithm mMockPipKeepClearAlgorithm;
    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;
    @Mock private PipDesktopState mMockPipDesktopState;
    @Mock private PipSnapAlgorithm mMockPipSnapAlgorithm;
    @Mock private SizeSpecSource mMockSizeSpecSource;
    @Mock private ShellExecutor mMockMainExecutor;
    private static final int DISPLAY_ID_MAIN = Display.DEFAULT_DISPLAY;
    private static final int DISPLAY_ID_SECONDARY = DISPLAY_ID_MAIN + 1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(DISPLAY_ID_MAIN);
        when(mMockPipBoundsAlgorithm.getSnapAlgorithm()).thenReturn(mMockPipSnapAlgorithm);
        mSpyPipBoundsState = spy(
                new PipBoundsState(mContext, mMockSizeSpecSource, mMockPipDisplayLayoutState));

        mPipController = PipController.create(mContext, mShellInit, mMockShellCommandHandler,
                mShellController, mMockDisplayController, mMockDisplayInsetsController,
                mSpyPipBoundsState, mMockPipBoundsAlgorithm, mMockPipDisplayLayoutState,
                mMockPipScheduler, mMockTaskStackListener, mMockShellTaskOrganizer,
                mMockPipTransitionState, mMockPipTouchHandler, mMockPipAppOpsListener,
                mMockPhonePipMenuController, mMockPipUiEventLogger, mMockPipMediaController,
                mMockTabletopModeController, mMockPipKeepClearAlgorithm,
                mMockPipSurfaceTransactionHelper, mMockPipDesktopState, mMockMainExecutor);
        mSpyPipController = spy(mPipController);

        // Directly init mPipController instead of using ShellInit
        mPipController.onInit();
    }

    @Test
    public void instantiatePipController_addGlobalInsetsChangedListener() {
        verify(mMockDisplayInsetsController, times(1)).addGlobalInsetsChangedListener(any());
    }

    @Test
    public void testInsetsListenerPerDisplay_addedOnInsetsChange_thenRemovedOnDisplayRemove() {
        // Capture the global insets listener
        ArgumentCaptor<DisplayInsetsController.OnInsetsChangedListener> captor =
                ArgumentCaptor.forClass(
                        DisplayInsetsController.OnInsetsChangedListener.class);
        verify(mMockDisplayInsetsController).addGlobalInsetsChangedListener(captor.capture());
        DisplayInsetsController.OnInsetsChangedListener globalListener = captor.getValue();

        // Simulate insets change on a new display
        int newDisplayId = DISPLAY_ID_MAIN + 1;
        android.view.InsetsState insetsState = new android.view.InsetsState();
        globalListener.insetsChanged(newDisplayId, insetsState);

        // Verify listener creation
        assertNotNull(mPipController.mListenersPerDisplay.get(newDisplayId));
        // Verify that both Ime and nav bar listeners were added in a list
        assertEquals(2, mPipController.mListenersPerDisplay.get(newDisplayId).size());

        // Simulate display removal
        mPipController.onDisplayRemoved(newDisplayId);

        // Verify listener removal
        assertNull(mPipController.mListenersPerDisplay.get(newDisplayId));
    }

    @Test
    public void onDisplayRemoved_pipInRemovedDisplay_displayUpdatedToDefault() {
        final DisplayLayout mainDisplayLayout = new DisplayLayout();
        when(mMockDisplayController.getDisplayLayout(DISPLAY_ID_MAIN)).thenReturn(
                mainDisplayLayout);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(DISPLAY_ID_SECONDARY);
        when(mMockPipTransitionState.isInPip()).thenReturn(true);

        clearInvocations(mMockPipDisplayLayoutState);
        mPipController.onDisplayRemoved(DISPLAY_ID_SECONDARY);

        verify(mMockPipDisplayLayoutState).setDisplayId(DISPLAY_ID_MAIN);
        verify(mMockPipDisplayLayoutState).setDisplayLayout(mainDisplayLayout);
    }

    @Test
    public void onDisplayRemoved_pipNotInRemovedDisplay_freeFloating_noOp() {
        when(mMockPipTransitionState.isInPip()).thenReturn(true);
        when(mMockPipDesktopState.isFreeFloatingPipEnabled()).thenReturn(true);

        mPipController.onDisplayRemoved(DISPLAY_ID_SECONDARY);

        verify(mMockPipTransitionState, never()).setOnIdlePipTransitionStateRunnable(any());
    }

    @Test
    public void onDisplayRemoved_pipNotInRemovedDisplay_notFreeFloating_boundsUpdated() {
        doNothing().when(mSpyPipController).updateBoundsOnDisplayChange(anyFloat());
        final float snapFraction = 0.5f;
        when(mMockPipSnapAlgorithm.getSnapFraction(any(), any(), anyInt())).thenReturn(
                snapFraction);
        Rect currentBounds = new Rect(300, 0, 400, 100);
        Rect simulatedToBounds = new Rect(10, 0, 110, 100);
        when(mSpyPipBoundsState.getBounds())
                .thenReturn(currentBounds)
                .thenReturn(simulatedToBounds);
        when(mMockPipTransitionState.isInPip()).thenReturn(true);
        when(mMockPipDesktopState.isFreeFloatingPipEnabled()).thenReturn(false);

        mSpyPipController.onDisplayRemoved(DISPLAY_ID_SECONDARY);

        verify(mSpyPipController).updateBoundsOnDisplayChange(eq(snapFraction));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockPipTransitionState).setOnIdlePipTransitionStateRunnable(
                runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(mMockPipTransitionState).setState(
                eq(PipTransitionState.SCHEDULED_BOUNDS_CHANGE),
                any()
        );
    }

    @Test
    public void insetsChanged_sendNewInsetsStateWithRotation_updatesDisplayLayout() {
        // Capture the global insets listener
        ArgumentCaptor<DisplayInsetsController.OnInsetsChangedListener> captor =
                ArgumentCaptor.forClass(DisplayInsetsController.OnInsetsChangedListener.class);
        verify(mMockDisplayInsetsController).addGlobalInsetsChangedListener(captor.capture());
        DisplayInsetsController.OnInsetsChangedListener globalListener = captor.getValue();

        // Setup mock layout
        DisplayLayout mockLayout = new DisplayLayout();
        when(mMockDisplayController.getDisplayLayout(DISPLAY_ID_MAIN)).thenReturn(mockLayout);

        // Simulate insets change
        android.view.InsetsState insetsState = new android.view.InsetsState();
        globalListener.insetsChanged(DISPLAY_ID_MAIN, insetsState);

        // Capture the runnable posted to MainExecutor
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());

        // Run the async update
        runnableCaptor.getValue().run();

        // Verify that setDisplayLayout was called with the mock layout
        verify(mMockPipDisplayLayoutState).setDisplayLayout(mockLayout);
    }
}
