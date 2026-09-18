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

package com.android.server.wm.utils;

import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;
import android.window.ITaskOrganizer;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;

import java.util.List;

public class StubOrganizer extends ITaskOrganizer.Stub {
    public ActivityManager.RunningTaskInfo mInfo;
    public ActivityManager.RunningTaskInfo mLastOccludingTaskInfo;

    @Override
    public void addStartingWindow(StartingWindowInfo info) { }
    @Override
    public void removeStartingWindow(StartingWindowRemovalInfo removalInfo) { }
    @Override
    public void copySplashScreenView(int taskId) { }
    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo info, SurfaceControl leash)
            throws RemoteException {
        mInfo = info;
    }
    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo info) {
    }
    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo info) {
    }
    @Override
    public void onImeDrawnOnTask(int taskId) throws RemoteException {
    }
    @Override
    public void onAppSplashScreenViewRemoved(int taskId) {
    }
    @Override
    public void onTransitionReady(IBinder iBinder, TransitionInfo transitionInfo,
            SurfaceControl.Transaction t, SurfaceControl.Transaction finishT) {
    }
    @Override
    public void requestStartTransition(IBinder iBinder, TransitionRequestInfo request) {
    }

    @Override
    public void onBackOnTaskRoot(@NonNull ActivityManager.RunningTaskInfo taskInfo,
            boolean isFromBackPress, boolean isOptInOnBackInvoked,
            boolean hasOpaqueSibling) {}

    @Override
    public void onPackageUpdateRequested(
            List<ActivityManager.RunningTaskInfo> updatingTaskInfos) {
    }

    @Override
    public void onPackageUpdateFinished(List<ActivityManager.RunningTaskInfo> updatedTaskInfos) {}

    @Override
    public void onKeyguardOccludingTaskChanged(
            int displayId, @Nullable ActivityManager.RunningTaskInfo taskInfo) {
        if (displayId == DEFAULT_DISPLAY) {
            mLastOccludingTaskInfo = taskInfo;
        }
    }
}
