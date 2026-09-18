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

package com.android.wm.shell.shared.compat;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.window.WindowAnimationState;

/**
 * Abstraction layer for surfaces that can be animated as part of a remote animation or remote
 * transition.
 *
 * This interface is meant to be a drop-in replacement for usages of RemoteAnimationTarget as an
 * intermediate step of migrating to the Shell APIs. Once support for the old APIs is removed, this
 * abstraction can be swapped out for TransitionInfo.Change.
 */
parcelable AnimatedSurface {

    /** Whether this Surface is opening, closing, or doing something else during the animation. */
    @Backing(type="int")
    enum Mode {
        CLOSING = 0,
        OPENING = 1,
        OTHER = 2
    }

    /** Used to animate this window. */
    SurfaceControl leash;

    /**
     * The {@link SurfaceControl} for the starting state if this transition's mode is Mode.OTHER
     * (MODE_CHANGING) in {@link RemoteAnimationTarget}), {@code null)} otherwise. This is relative
     * to the app window.
     */
    SurfaceControl startLeash;

    /** The state of this window at the beginning of the animation, if any. */
    @nullable WindowAnimationState startState;

    /** The state of this window at the end of the animation. */
    WindowAnimationState endState;

    /** The background color of the task populating this window. */
    int backgroundColor;

    /** Whether this window's background should be ignored and considered transparent. */
    boolean isTranslucent;

    /** The [TaskInfo] representing the content of this window. */
    @nullable RunningTaskInfo taskInfo;

    /** Whether this Surface is opening, closing, or doing something else during the animation. */
    Mode mode;

    /** Bounds of the surface relative to the screen. */
    Rect screenSpaceBounds;

    /** Bounds of the surface relative to its parent */
    Rect localBounds;

    /** The starting bounds of the source container in screen space coordinates. */
    Rect startBounds;

    /** The insets of the main app window. */
    Rect contentInsets;

    /** The source position of the app, in screen spaces coordinates. */
    Point position;

    /** Difference in surface's start and end rotation. */
    int rotationChange;

    /** The window configuration of the surface. */
    WindowConfiguration windowConfiguration;

    /** The id of the task this app belongs to. */
    int taskId;

    /** The {@link android.view.WindowManager.LayoutParams.WindowType} of this window. */
    int windowType;

    /** Whether the activity is going to show IME on the target window after the app transition. */
    boolean willShowImeOnTarget;

    /** Whether the task is not presented in Recents UI. */
    boolean isNotInRecents;

    /**
     * {@code true} if picture-in-picture permission is granted in
     * {@link android.app.AppOpsManager}
     */
    boolean allowEnterPip;

    /**
     * The index of the element in the tree in prefix order. This should be used for z-layering
     * to preserve original z-layer order in the hierarchy tree assuming no "boosting" needs to
     * happen.
     */
    int order;
}
