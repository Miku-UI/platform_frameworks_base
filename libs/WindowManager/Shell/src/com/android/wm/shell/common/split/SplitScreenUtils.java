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

package com.android.wm.shell.common.split;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_ACTIVITY_TYPES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_45_45_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.window.DesktopExperienceFlags;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerToken;

import com.android.internal.util.ArrayUtils;
import com.android.wm.shell.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.split.SplitScreenConstants;
import com.android.wm.shell.splitscreen.BranchNode;
import com.android.wm.shell.splitscreen.LayoutNode;
import com.android.wm.shell.splitscreen.LeafNode;
import com.android.wm.shell.splitscreen.StageTaskListener;

import java.util.Objects;

/** Helper utility class for split screen components to use. */
public class SplitScreenUtils {
    private static final int LARGE_SCREEN_MIN_EDGE_DP = 600;

    /**
     * Creates a pretty-printed string representation of a LayoutNode tree for debugging.
     * @param node The root of the tree to dump.
     * @return A string representing the tree structure.
     */
    public static String dumpLayoutNodeTree(LayoutNode node) {
        StringBuilder sb = new StringBuilder("Split Screen Layout:\n");
        dumpLayoutNodeTreeRecursive(node, "", true, sb);
        return sb.toString();
    }

    private static void dumpLayoutNodeTreeRecursive(LayoutNode node, String prefix, boolean isLast,
            StringBuilder sb) {
        sb.append(prefix);
        if (!prefix.isEmpty()) {
            sb.append(isLast ? "└─ " : "├─ ");
        }

        if (node instanceof BranchNode bn) {
            String orientation = bn.getOrientation() == BranchNode.ORIENTATION_VERTICAL ? "V" : "H";
            sb.append("Branch (")
                    .append(orientation)
                    .append(", w=").append(String.format("%.2f", bn.getWeight()))
                    .append(", off=").append(bn.isOffscreen())
                    .append(", main=").append(bn.getMainChildIndex())
                    .append(")\n");

            String childPrefix = prefix + (isLast ? "    " : "│   ");
            for (int i = 0; i < bn.getChildren().size(); i++) {
                LayoutNode child = bn.getChildren().get(i);
                dumpLayoutNodeTreeRecursive(child, childPrefix, i == bn.getChildren().size() - 1,
                        sb);
            }
        } else if (node instanceof LeafNode ln) {
            ln.getTaskInfo();
            sb.append("Leaf (task=")
                    .append(ln.getTaskInfo().taskId)
                    .append(", w=").append(String.format("%.2f", ln.getWeight()))
                    .append(")\n");
        }
    }

    /** Reverse the split position. */
    @SplitScreenConstants.SplitPosition
    public static int reverseSplitPosition(@SplitScreenConstants.SplitPosition int position) {
        switch (position) {
            case SPLIT_POSITION_TOP_OR_LEFT:
                return SPLIT_POSITION_BOTTOM_OR_RIGHT;
            case SPLIT_POSITION_BOTTOM_OR_RIGHT:
                return SPLIT_POSITION_TOP_OR_LEFT;
            case SPLIT_POSITION_UNDEFINED:
            default:
                return SPLIT_POSITION_UNDEFINED;
        }
    }

    /** Returns true if the task is valid for split screen. */
    public static boolean isValidToSplit(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo != null && taskInfo.supportsMultiWindowWithoutConstraints
                && ArrayUtils.contains(CONTROLLED_ACTIVITY_TYPES, taskInfo.getActivityType())
                && ArrayUtils.contains(CONTROLLED_WINDOWING_MODES, taskInfo.getWindowingMode());
    }

    /** Retrieve user id from a taskId using {@link ShellTaskOrganizer}. */
    public static int getUserId(int taskId, ShellTaskOrganizer taskOrganizer) {
        final TaskInfo taskInfo = taskOrganizer.getRunningTaskInfo(taskId);
        return getUserIdFromTaskInfo(taskInfo);
    }

    /** Retrieve user id from a taskId using {@link RecentTasksController}. */
    public static int getUserId(int taskId,
            @Nullable RecentTasksController recentTasksController) {
        if (recentTasksController == null) {
            return -1;
        }
        final TaskInfo taskInfo = recentTasksController.findTaskInBackground(taskId);
        return getUserIdFromTaskInfo(taskInfo);
    }

    private static int getUserIdFromTaskInfo(TaskInfo taskInfo) {
        return taskInfo != null ? taskInfo.userId : -1;
    }

    /** Generates a common log message for split screen failures */
    public static String splitFailureMessage(String caller, String reason) {
        return "(" + caller + ") Splitscreen aborted: " + reason;
    }

    /**
     * Returns whether left/right split is allowed in portrait.
     */
    public static boolean allowLeftRightSplitInPortrait(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_leftRightSplitInPortrait);
    }

    /**
     * Returns whether left/right split is supported in the given configuration.
     */
    public static boolean isLeftRightSplit(boolean allowLeftRightSplitInPortrait,
            Configuration config, int displayId) {
        // Compare the max bounds sizes as on near-square devices, the insets may result in a
        // configuration in the other orientation
        final Rect maxBounds = config.windowConfiguration.getMaxBounds();
        final boolean isLandscape = maxBounds.width() >= maxBounds.height();
        return isLeftRightSplit(allowLeftRightSplitInPortrait, isLargeScreen(config), isLandscape,
                displayId);
    }

    /**
     * Returns whether left/right split is supported in the given configuration state. This method
     * is useful for cases where we need to calculate this given last saved state.
     */
    public static boolean isLeftRightSplit(boolean allowLeftRightSplitInPortrait,
            boolean isLargeScreen, boolean isLandscape, int displayId) {
        if (allowLeftRightSplitInPortrait && isLargeScreen) {
            if (displayId == DEFAULT_DISPLAY
                    || !DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue()) {
                return !isLandscape;
            } else {
                // If split is started in external display and the non_default_display_split_bugfix
                // is enabled, set isLeftRightSplit to true in landscape mode.
                return isLandscape;
            }
        } else {
            return isLandscape;
        }
    }

    /**
     * Returns whether the current config is a large screen (tablet or unfolded foldable)
     */
    public static boolean isLargeScreen(Configuration config) {
        return config.smallestScreenWidthDp >= LARGE_SCREEN_MIN_EDGE_DP;
    }

    /**
     * Convenience function for {@link #isLargeScreen(Configuration)}.
     */
    public static boolean isLargeScreen(Resources res) {
        return isLargeScreen(res.getConfiguration());
    }

    /**
     * Returns whether the current device is a foldable
     */
    public static boolean isFoldable(Resources res) {
        return res.getIntArray(com.android.internal.R.array.config_foldedDeviceStates).length != 0;
    }

    /**
     * Returns whether we should allow split ratios to go offscreen or not. If the device is a phone
     * or a foldable (either screen), we allow it.
     */
    public static boolean allowOffscreenRatios(Resources res) {
        return Flags.enableFlexibleTwoAppSplit() && (!isLargeScreen(res) || isFoldable(res));
    }

    /**
     * Within a particular split layout, we label the stages numerically: 0, 1, 2... from left to
     * right (or top to bottom). This function takes in a stage index (0th, 1st, 2nd...) and a
     * PersistentSnapPosition and returns if that particular stage is offscreen in that layout.
     */
    public static boolean isPartiallyOffscreen(int stageIndex,
            @SplitScreenConstants.PersistentSnapPosition int snapPosition) {
        switch(snapPosition) {
            case SNAP_TO_2_10_90:
            case SNAP_TO_3_10_45_45:
                return stageIndex == 0;
            case SNAP_TO_2_90_10:
                return stageIndex == 1;
            case SNAP_TO_3_45_45_10:
                return stageIndex == 2;
            default:
                return false;
        }
    }

    /**
     * Retrieves the new parent WindowContainerToken for tasks in the main or stage display area
     * after the split pair is dismissed. This token will be used for reparenting tasks. The
     * specific stage (main or side) from which the display ID is obtained does not alter the
     * resulting parent token, as it's based on the display area of the display itself.
     *
     * @param stage The StageTaskListener representing the current stage.
     * @param rootTDAOrganizer The RootTaskDisplayAreaOrganizer to query for DisplayAreaInfo.
     * @return The WindowContainerToken of the parent display area if
     *         DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX is true and a valid
     *         DisplayAreaInfo is found for the main stage's display; otherwise, returns null.
     */
    @Nullable
    public static WindowContainerToken getNewParentTokenForStage(
            @Nullable StageTaskListener stage,
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer) {
        if (!DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue()
                || stage == null || stage.getRunningTaskInfo() == null) {
            return null;
        }

        final int displayId = stage.getRunningTaskInfo().displayId;
        final DisplayAreaInfo displayAreaInfo = rootTDAOrganizer.getDisplayAreaInfo(displayId);
        return displayAreaInfo != null ? displayAreaInfo.token : null;
    }

    /**
     * Retrieves DisplayAreaInfo for a given task and updates the SplitLayout's configuration.
     *
     * @param rootTDAOrganizer The RootTaskDisplayAreaOrganizer instance.
     * @param displayId The RunningTaskInfo displayId for which to get display information.
     * @param splitLayout The SplitLayout to update. Can be null.
     */
    public static void updateSplitLayoutConfig(
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer,
            int displayId,
            @Nullable SplitLayout splitLayout) {
        if (!DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue()) {
            return;
        }

        DisplayAreaInfo displayAreaInfo = rootTDAOrganizer.getDisplayAreaInfo(displayId);
        if (displayAreaInfo == null) {
            return;
        }

        Configuration displayConfiguration = displayAreaInfo.configuration;
        if (splitLayout != null) {
            if (splitLayout.updateConfiguration(displayConfiguration, displayId)) {
                splitLayout.update(null /* t */, false /* resetImePosition */);
            }
        }
    }

    /**
     * Returns the target windowing mode for a task when leaving split-screen.
     *
     * In a freeform-first environment (like desktop), explicitly set the windowing mode to
     * fullscreen when leaving split-screen. On a standard display, setting it to UNDEFINED allows
     * the task to inherit the display's default windowing mode (usually fullscreen).
     *
     * @param rootTDAOrganizer The {@link RootTaskDisplayAreaOrganizer} used to retrieve display
     *                         area information.
     * @param displayId        The ID of the display the task is moving to.
     * @return {@link android.app.WindowConfiguration#WINDOWING_MODE_FULLSCREEN} if the target
     *         display is in freeform mode; otherwise
     *         {@link android.app.WindowConfiguration#WINDOWING_MODE_UNDEFINED}.
     */
    public static int getTargetWindowingModeWhenExitSplit(
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer, int displayId) {
        final DisplayAreaInfo tdaInfo = rootTDAOrganizer.getDisplayAreaInfo(displayId);
        Objects.requireNonNull(tdaInfo);
        final int displayWindowingMode =
                tdaInfo.configuration.windowConfiguration.getWindowingMode();
        return displayWindowingMode == WINDOWING_MODE_FREEFORM
                ? WINDOWING_MODE_FULLSCREEN : WINDOWING_MODE_UNDEFINED;
    }
}
