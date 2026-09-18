/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.startingsurface;

import android.annotation.NonNull;
import android.os.RemoteException;
import android.window.StartingWindowInfo;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;

import com.android.internal.protolog.ProtoLog;
import com.android.window.flags.Flags;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

class SnapshotWindowCreator {
    // Redraw using the high-resolution snapshot if the low-resolution snapshot scale falls below
    // the threshold.
    private static final float REDRAW_HIGH_RES_SCALE_THRESHOLD = 0.5f;
    private final float mLowResSnapshotScale;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mBgExecutor;
    private final StartingSurfaceDrawer.StartingWindowRecordManager
            mStartingWindowRecordManager;

    SnapshotWindowCreator(float lowResSnapshotScale, ShellExecutor mainExecutor,
            ShellExecutor bgExecutor,
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager) {
        mLowResSnapshotScale = lowResSnapshotScale;
        mMainExecutor = mainExecutor;
        mBgExecutor = bgExecutor;
        mStartingWindowRecordManager = startingWindowRecordManager;
    }

    void makeTaskSnapshotWindow(StartingWindowInfo startingWindowInfo, TaskSnapshot snapshot) {
        final int taskId = startingWindowInfo.taskInfo.taskId;
        // Remove any existing starting window for this task before adding.
        mStartingWindowRecordManager.removeWindow(taskId);
        final TaskSnapshotWindow surface = TaskSnapshotWindow.create(startingWindowInfo,
                startingWindowInfo.appToken, snapshot, mMainExecutor,
                () -> mStartingWindowRecordManager.removeWindow(taskId));
        if (surface != null) {
            final SnapshotWindowRecord tView = new SnapshotWindowRecord(surface,
                    startingWindowInfo.taskInfo.topActivityType, mMainExecutor,
                    taskId, mStartingWindowRecordManager);
            mStartingWindowRecordManager.addRecord(taskId, tView);
            if (Flags.onlyCacheLowResTaskSnapshot() && snapshot.isLowResolution()
                    && (mLowResSnapshotScale < REDRAW_HIGH_RES_SCALE_THRESHOLD)) {
                tView.scheduleRedrawSnapshot(mBgExecutor, () -> {
                    final TaskSnapshot replace;
                    try {
                        replace = TaskSnapshotManager.getInstance()
                                .getTaskSnapshot(taskId, TaskSnapshotManager.RESOLUTION_HIGH);
                    } catch (RemoteException r) {
                        return;
                    }
                    if (replace != null) {
                        mMainExecutor.execute(() -> {
                            if (mStartingWindowRecordManager.getRecord(taskId) == tView) {
                                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                                        "redrawSnapshot for task=%d", taskId);
                                surface.redrawSnapshot(replace, startingWindowInfo.taskBounds);
                            } else {
                                // Starting window was removed.
                                replace.closeBuffer();
                            }
                        });
                    }
                });
            }
        }
    }

    private static class SnapshotWindowRecord extends StartingSurfaceDrawer.SnapshotRecord {
        private final TaskSnapshotWindow mTaskSnapshotWindow;
        private ShellExecutor mBgExecutor;
        private Runnable mRedrawSnapshotCallback;

        SnapshotWindowRecord(TaskSnapshotWindow taskSnapshotWindow,
                int activityType, ShellExecutor removeExecutor, int id,
                StartingSurfaceDrawer.StartingWindowRecordManager recordManager) {
            super(activityType, removeExecutor, id, recordManager);
            mTaskSnapshotWindow = taskSnapshotWindow;
            mBGColor = mTaskSnapshotWindow.getBackgroundColor();
        }

        @Override
        protected void removeImmediately() {
            super.removeImmediately();
            mTaskSnapshotWindow.removeImmediately();
            if (mBgExecutor != null && mRedrawSnapshotCallback != null) {
                mBgExecutor.removeCallbacks(mRedrawSnapshotCallback);
                mRedrawSnapshotCallback = null;
            }
        }

        @Override
        protected boolean hasImeSurface() {
            return mTaskSnapshotWindow.hasImeSurface();
        }

        void scheduleRedrawSnapshot(@NonNull ShellExecutor bgExecutor,
                @NonNull Runnable redrawCallback) {
            mBgExecutor = bgExecutor;
            mRedrawSnapshotCallback = redrawCallback;
            mBgExecutor.execute(mRedrawSnapshotCallback);
        }
    }
}
