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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Utilities for {@link AnimatedSurface}.
 */
public class AnimatedSurfaceUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        AnimatedSurface.Mode.CLOSING,
        AnimatedSurface.Mode.OPENING,
        AnimatedSurface.Mode.OTHER,
    })
    public @interface AnimatedSurfaceMode  {}

    /** See {@link AnimatedSurfaceUtils#from(RemoteAnimationTarget, WindowAnimationState)}. */
    public static AnimatedSurface from(RemoteAnimationTarget target) {
        return from(target, null /* startState */);
    }

    /** Utility function to convert RemoteAnimationTarget array to AnimatedSurface array. */
    public static AnimatedSurface[] mapFromTargets(RemoteAnimationTarget[] targets) {
        return Arrays.stream(targets)
                .map(AnimatedSurfaceUtils::from)
                .toArray(AnimatedSurface[]::new);
    }

    /** Factory for compat use with the pre-Shell APIs. */
    public static AnimatedSurface from(
            RemoteAnimationTarget target, @Nullable WindowAnimationState startState) {
        WindowAnimationState endState = new WindowAnimationState();
        endState.bounds = new RectF(target.screenSpaceBounds);

        int mode = switch (target.mode) {
            case RemoteAnimationTarget.MODE_CLOSING -> AnimatedSurface.Mode.CLOSING;
            case RemoteAnimationTarget.MODE_OPENING -> AnimatedSurface.Mode.OPENING;
            default -> AnimatedSurface.Mode.OTHER;
        };

        return createAnimatedSurface(
                /* leash= */ target.leash,
                /* startLeash= */ target.startLeash,
                /* startState= */ startState,
                /* endState= */ endState,
                /* backgroundColor= */ target.backgroundColor,
                /* isTranslucent= */ target.isTranslucent,
                /* taskInfo= */ target.taskInfo,
                /* mode= */ mode,
                /* screenSpaceBounds= */ target.screenSpaceBounds,
                /* localBounds= */ target.localBounds,
                /* startBounds= */ target.startBounds,
                /* contentInsets= */ target.contentInsets,
                /* position= */ target.position,
                /* rotationChange= */ target.rotationChange,
                /* windowConfiguration= */ target.windowConfiguration,
                /* taskId= */ target.taskId,
                /* windowType= */ target.windowType,
                /* willShowImeOnTarget= */ target.willShowImeOnTarget,
                /* isNotInRecents= */ target.isNotInRecents,
                /* allowEnterPip= */ target.allowEnterPip,
                /* order= */ target.prefixOrderIndex);
    }

    /** See {@link AnimatedSurfaceUtils#from(TransitionInfo.Change, WindowAnimationState, int)}. */
    public static AnimatedSurface from(TransitionInfo.Change change, int order) {
        return from(change, null /* startState */, order);
    }

    /** Factory for direct use with the Shell APIs. */
    public static AnimatedSurface from(
            TransitionInfo.Change change, @Nullable WindowAnimationState startState, int order) {
        WindowAnimationState endState = new WindowAnimationState();
        endState.bounds = new RectF(change.getEndAbsBounds());

        int mode = switch (change.getMode()) {
            case WindowManager.TRANSIT_CLOSE, WindowManager.TRANSIT_TO_BACK ->
                    AnimatedSurface.Mode.CLOSING;
            case WindowManager.TRANSIT_OPEN, WindowManager.TRANSIT_TO_FRONT ->
                    AnimatedSurface.Mode.OPENING;
            default -> AnimatedSurface.Mode.OTHER;
        };

        Rect localBounds = new Rect(change.getEndAbsBounds());
        localBounds.offsetTo(change.getEndRelOffset().x, change.getEndRelOffset().y);

        int rotationChange = change.getEndRotation() - change.getStartRotation();

        ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        WindowConfiguration windowConfiguration;
        int taskId;
        boolean isNotInRecents;

        if (taskInfo != null) {
            taskId = taskInfo.taskId;
            windowConfiguration = taskInfo.configuration.windowConfiguration;
            // TODO(b/449709471): This is probably incorrect but left since it's pre-existing
            // in "TransitionUtil" class. Revisit and fix it after targets migration.
            isNotInRecents = !taskInfo.isRunning;
        } else {
            taskId = INVALID_TASK_ID;
            windowConfiguration = new WindowConfiguration();
            isNotInRecents = true;
        }

        boolean willShowImeOnTarget = (change.getFlags() & TransitionInfo.FLAG_WILL_IME_SHOWN) != 0;

        return createAnimatedSurface(
                /* leash= */ change.getLeash(),
                /* startLeash= */ null,
                /* startState= */ startState,
                /* endState= */ endState,
                /* backgroundColor= */ change.getBackgroundColor(),
                /* isTranslucent= */ change.hasFlags(TransitionInfo.FLAG_TRANSLUCENT),
                /* taskInfo= */ taskInfo,
                /* mode= */ mode,
                /* screenSpaceBounds= */ new Rect(change.getEndAbsBounds()),
                /* localBounds= */ localBounds,
                /* startBounds= */ new Rect(change.getStartAbsBounds()),
                /* contentInsets= */ new Rect(0, 0, 0, 0),
                /* position= */ null,
                /* rotationChange= */ rotationChange,
                /* windowConfiguration= */ windowConfiguration,
                /* taskId= */ taskId,
                /* windowType= */ INVALID_WINDOW_TYPE,
                /* willShowImeOnTarget= */ willShowImeOnTarget,
                /* isNotInRecents= */ isNotInRecents,
                /* allowEnterPip= */ change.isAllowEnterPip(),
                /* order= */ order);
    }

    private static AnimatedSurface createAnimatedSurface(
            SurfaceControl leash,
            SurfaceControl startLeash,
            @Nullable WindowAnimationState startState,
            WindowAnimationState endState,
            int backgroundColor,
            boolean isTranslucent,
            @Nullable ActivityManager.RunningTaskInfo taskInfo,
            int mode,
            Rect screenSpaceBounds,
            Rect localBounds,
            Rect startBounds,
            Rect contentInsets,
            Point position,
            int rotationChange,
            WindowConfiguration windowConfiguration,
            int taskId,
            int windowType,
            boolean willShowImeOnTarget,
            boolean isNotInRecents,
            boolean allowEnterPip,
            int order) {
        AnimatedSurface surface = new AnimatedSurface();
        surface.leash = leash;
        surface.startLeash = startLeash;
        surface.startState = startState;
        surface.endState = endState;
        surface.backgroundColor = backgroundColor;
        surface.isTranslucent = isTranslucent;
        surface.taskInfo = taskInfo;
        surface.mode = mode;
        surface.screenSpaceBounds = screenSpaceBounds;
        surface.localBounds = localBounds;
        surface.startBounds = startBounds;
        surface.contentInsets = contentInsets;
        surface.position = position;
        surface.rotationChange = rotationChange;
        surface.windowConfiguration = windowConfiguration;
        surface.taskId = taskId;
        surface.windowType = windowType;
        surface.willShowImeOnTarget = willShowImeOnTarget;
        surface.isNotInRecents = isNotInRecents;
        surface.allowEnterPip = allowEnterPip;
        surface.order = order;
        return surface;
    }

    /**
     * Util to obtain mapped {@link AnimatedSurface.Mode} from {@link RemoteAnimationTarget.Mode}.
     */
    public static @AnimatedSurfaceMode int mappedModeFromTarget(
            @RemoteAnimationTarget.Mode int mode) {
        return switch (mode) {
            case RemoteAnimationTarget.MODE_OPENING -> AnimatedSurface.Mode.OPENING;
            case RemoteAnimationTarget.MODE_CLOSING -> AnimatedSurface.Mode.CLOSING;
            default -> AnimatedSurface.Mode.OTHER;
        };
    }

    /** Checks whether this surface is closing based on its mode. */
    public static boolean isClosing(AnimatedSurface surface) {
        return surface.mode == AnimatedSurface.Mode.CLOSING;
    }

    /** Checks whether this surface is opening based on its mode. */
    public static boolean isOpening(AnimatedSurface surface) {
        return surface.mode == AnimatedSurface.Mode.OPENING;
    }
}
