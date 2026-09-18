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

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the "Scroll to Top" feature.
 * <p>
 * Build/Install/Run:
 * atest WmTests:WindowScrollToTopTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowScrollToTopTest extends WindowTestsBase {

    private void setBounds(WindowState w, Rect bounds) {
        if (w.getTask() != null) {
            w.getTask().setBounds(bounds);
        }
        w.getWindowConfiguration().setBounds(bounds);
        w.onRequestedOverrideConfigurationChanged(w.getRequestedOverrideConfiguration());
    }

    private WindowState createUniqueAppWindow(Task task, String name) {
        final ActivityRecord activity = createNonAttachedActivityRecord(task.getDisplayContent());
        task.addChild(activity, 0);
        activity.setState(ActivityRecord.State.RESUMED, "Test");
        return newWindowBuilder(name, WindowManager.LayoutParams.TYPE_BASE_APPLICATION)
                .setWindowToken(activity)
                .setClientWindow(new TestIWindow())
                .build();
    }

    @Test
    public void testScrollToTop_DefaultFocus() throws Exception {
        final Task task = createTask(mDisplayContent);
        task.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final WindowState appWindow = createUniqueAppWindow(task, "AppWindow");
        setBounds(appWindow, new Rect(0, 0, 1000, 2000));
        makeWindowVisible(appWindow);
        spyOn(appWindow.mClient);

        mDisplayContent.setFocusedApp(appWindow.getActivityRecord());
        mDisplayContent.mCurrentFocus = appWindow;

        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 500, -1);

        verify(appWindow.mClient).dispatchScrollToTop(500);
    }

    @Test
    public void testScrollToTop_ExplicitTarget() throws Exception {
        final Task topTask = createTask(mDisplayContent);
        topTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final WindowState topApp = createUniqueAppWindow(topTask, "TopApp");
        setBounds(topApp, new Rect(0, 0, 1000, 1000));
        makeWindowVisible(topApp);
        spyOn(topApp.mClient);

        final Task bottomTask = createTask(mDisplayContent);
        bottomTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final WindowState bottomApp = createUniqueAppWindow(bottomTask, "BottomApp");
        setBounds(bottomApp, new Rect(0, 1000, 1000, 2000));
        makeWindowVisible(bottomApp);
        spyOn(bottomApp.mClient);

        mDisplayContent.setFocusedApp(bottomApp.getActivityRecord());
        mDisplayContent.mCurrentFocus = bottomApp;

        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 500, topTask.mTaskId);

        verify(topApp.mClient).dispatchScrollToTop(500);
        verify(bottomApp.mClient, never()).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testScrollToTop_OutsideBounds_Ignored() throws Exception {
        final Task task = createTask(mDisplayContent);
        task.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final WindowState appWindow = createUniqueAppWindow(task, "AppWindow");
        setBounds(appWindow, new Rect(200, 0, 800, 2000));
        makeWindowVisible(appWindow);
        spyOn(appWindow.mClient);

        mDisplayContent.setFocusedApp(appWindow.getActivityRecord());

        // 1. Tap to the left and right of the window
        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 100, -1);
        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 900, -1);
        verify(appWindow.mClient, never()).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testScrollToTop_ActivityEmbedding() throws Exception {
        final Task task = createTask(mDisplayContent);
        task.setWindowingMode(WINDOWING_MODE_FREEFORM);
        task.setBounds(new Rect(0, 0, 1000, 2000));

        final WindowState leftApp = createUniqueAppWindow(task, "LeftApp");
        leftApp.getWindowConfiguration().setBounds(new Rect(0, 0, 500, 2000));
        makeWindowVisible(leftApp);
        spyOn(leftApp.mClient);

        final WindowState rightApp = createUniqueAppWindow(task, "RightApp");
        rightApp.getWindowConfiguration().setBounds(new Rect(500, 0, 1000, 2000));
        makeWindowVisible(rightApp);
        spyOn(rightApp.mClient);

        mDisplayContent.setFocusedApp(rightApp.getActivityRecord());

        // Tap on Left side (x=250)
        // Should dispatch to LeftApp even though RightApp is focused
        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 250, -1);

        verify(leftApp.mClient).dispatchScrollToTop(250);
        verify(rightApp.mClient, never()).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testScrollToTop_DialogFocus() throws Exception {
        final Task task = createTask(mDisplayContent);
        task.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final WindowState appWindow = createUniqueAppWindow(task, "AppWindow");
        setBounds(appWindow, new Rect(0, 0, 1000, 2000));
        makeWindowVisible(appWindow);
        spyOn(appWindow.mClient);

        // Create a dialog window on top of the appWindow
        final WindowState dialogWindow = newWindowBuilder("DialogWindow",
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                .setWindowToken(appWindow.mToken)
                .setParent(appWindow)
                .setClientWindow(new TestIWindow())
                .build();
        setBounds(dialogWindow, new Rect(0, 100, 900, 2000));
        makeWindowVisible(dialogWindow);
        spyOn(dialogWindow.mClient);



        mDisplayContent.setFocusedApp(appWindow.getActivityRecord());
        mDisplayContent.mCurrentFocus = dialogWindow;
        mWm.dispatchScrollToTop(DEFAULT_DISPLAY, 500, -1);

        verify(dialogWindow.mClient).dispatchScrollToTop(500);
        verify(appWindow.mClient, never()).dispatchScrollToTop(anyInt());
    }
}
