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

package com.android.wm.shell.draganddrop;

import static android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.DragEvent.ACTION_DRAG_STARTED;

import static com.android.wm.shell.draganddrop.DragTestUtils.createAppClipData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.Context;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.view.Display;
import android.view.DragEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.UiEventLogger;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.bar.DragToBubbleController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for the drag and drop controller.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DragAndDropControllerTest extends ShellTestCase {

    @Mock
    private Context mContext;
    @Mock
    private ShellInit mShellInit;
    @Mock
    private ShellController mShellController;
    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private ShellTaskOrganizer mShellTaskOrganizer;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private UiEventLogger mUiEventLogger;
    @Mock
    private DragAndDropController.DragAndDropListener mDragAndDropListener;
    @Mock
    private IconProvider mIconProvider;
    @Mock
    private ShellExecutor mMainExecutor;
    @Mock
    private Transitions mTransitions;
    @Mock
    private GlobalDragListener mGlobalDragListener;
    @Mock
    private BubbleFeatureConfig mBubbleFeatureConfig;
    @Mock
    private DragToBubbleController mDragToBubbleController;
    private FakeDesktopState mDesktopState;

    private DragAndDropController mController;

    @Before
    public void setUp() throws RemoteException {
        mDesktopState = new FakeDesktopState();
        MockitoAnnotations.initMocks(this);

        mController = new DragAndDropController(mContext, mShellInit, mShellController,
                mShellCommandHandler, mShellTaskOrganizer, mDisplayController, mUiEventLogger,
                mIconProvider, mGlobalDragListener, mTransitions, () -> mDragToBubbleController,
                mMainExecutor, mDesktopState, mBubbleFeatureConfig);
        mController.onInit();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void testIgnoreNonDefaultDisplays() {
        final int nonDefaultDisplayId = 12345;
        final View dragLayout = mock(View.class);
        final Display display = mock(Display.class);
        doReturn(nonDefaultDisplayId).when(display).getDisplayId();
        doReturn(display).when(dragLayout).getDisplay();

        // Expect no per-display layout to be added
        mController.onDisplayAdded(nonDefaultDisplayId);
        assertFalse(mController.onDrag(dragLayout, mock(DragEvent.class)));
    }

    @Test
    public void testListenerOnDragStarted() {
        final View dragLayout = mock(View.class);
        final Display display = mock(Display.class);
        doReturn(display).when(dragLayout).getDisplay();
        doReturn(DEFAULT_DISPLAY).when(display).getDisplayId();

        final ClipData clipData = createAppClipData(MIMETYPE_APPLICATION_SHORTCUT);
        final DragEvent event = mock(DragEvent.class);
        doReturn(ACTION_DRAG_STARTED).when(event).getAction();
        doReturn(clipData).when(event).getClipData();
        doReturn(clipData.getDescription()).when(event).getClipDescription();

        mController.addListener(mDragAndDropListener);

        // Ensure there's a target so that onDrag will execute
        mController.addDisplayDropTarget(0, mContext, mock(WindowManager.class),
                mock(FrameLayout.class), mock(DragLayout.class));

        // Verify the listener is called on a valid drag action.
        mController.onDrag(dragLayout, event);
        verify(mDragAndDropListener, times(1)).onDragStarted();

        // Verify the listener isn't called after removal.
        reset(mDragAndDropListener);
        mController.removeListener(mDragAndDropListener);
        mController.onDrag(dragLayout, event);
        verify(mDragAndDropListener, never()).onDragStarted();
    }

    @Test
    public void testOnDragStarted_withNoClipDataOrDescription() {
        final View dragLayout = mock(View.class);
        final Display display = mock(Display.class);
        doReturn(display).when(dragLayout).getDisplay();
        doReturn(DEFAULT_DISPLAY).when(display).getDisplayId();

        final DragEvent event = mock(DragEvent.class);
        doReturn(ACTION_DRAG_STARTED).when(event).getAction();
        doReturn(null).when(event).getClipData();
        doReturn(null).when(event).getClipDescription();

        // Ensure there's a target so that onDrag will execute
        mController.addDisplayDropTarget(0, mContext, mock(WindowManager.class),
                mock(FrameLayout.class), mock(DragLayout.class));

        // Verify the listener is called on a valid drag action.
        mController.onDrag(dragLayout, event);
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    public void testOnInit_addBubblesListener_enabled() {
        when(mBubbleFeatureConfig.areAppBubblesSupported()).thenReturn(true);

        mController = spy(new DragAndDropController(mContext, mShellInit, mShellController,
                mShellCommandHandler, mShellTaskOrganizer, mDisplayController, mUiEventLogger,
                mIconProvider, mGlobalDragListener, mTransitions, () -> mDragToBubbleController,
                mMainExecutor, mDesktopState, mBubbleFeatureConfig));

        mController.onInit();

        verify(mController).addListener(mDragToBubbleController);
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    public void testOnInit_addBubblesListener_disabled() {
        when(mBubbleFeatureConfig.areAppBubblesSupported()).thenReturn(false);

        mController = spy(new DragAndDropController(mContext, mShellInit, mShellController,
                mShellCommandHandler, mShellTaskOrganizer, mDisplayController, mUiEventLogger,
                mIconProvider, mGlobalDragListener, mTransitions, () -> mDragToBubbleController,
                mMainExecutor, mDesktopState, mBubbleFeatureConfig));

        mController.onInit();

        verify(mController, never()).addListener(mDragToBubbleController);
    }

    @Test
    public void testOnCrossWindowDrop_reordersWithIncludingParents() {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.token = mock(WindowContainerToken.class);

        mController.onCrossWindowDrop(taskInfo);

        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        // Verify that a transition is started with the expected transaction
        verify(mTransitions)
                .startTransition(eq(WindowManager.TRANSIT_TO_FRONT), wctCaptor.capture(), any());

        // Verify the transaction contains the reorder operation with includingParents=true
        WindowContainerTransaction wct = wctCaptor.getValue();
        List<WindowContainerTransaction.HierarchyOp> ops = wct.getHierarchyOps();
        assertEquals(1, ops.size());
        WindowContainerTransaction.HierarchyOp op = ops.get(0);
        assertEquals(
                WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER, op.getType());
        assertTrue("Reorder should include parents", op.includingParents());
    }
}
