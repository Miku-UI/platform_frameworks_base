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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.view.Display.TYPE_INTERNAL;
import static android.window.DesktopExperienceFlags.ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_COMPAT_MODE;
import static android.window.DesktopExperienceFlags.ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS;

import android.annotation.NonNull;
import android.content.pm.ApplicationInfo;

import java.io.PrintWriter;

/**
 * Encapsulate app-compat logic for multi-display environments.
 *
 * <p>The primary feature is "display compat mode", which suppresses automatic activity restart
 * caused by display-specific config changes to prevent unexpected crashes.
 * The current conditions of an app being in display compat mode are:
 * <ul>
 *     <li>The app is a game.
 *     <li>The app doesn't support all of {@link DISPLAY_COMPAT_MODE_CONFIG_MASK}.
 *     <li>The app has moved to a different display but not restarted yet.
 * </ul>
 *
 * <p>Display compat mode comes with restart handle menu, with which the app process gets recreated,
 * and all the config changes get reloaded by the app, at the timing the user wants to do so with
 * the risk of losing the current state of the app.
 */
class AppCompatDisplayCompatModePolicy {

    private static final int DISPLAY_COMPAT_MODE_CONFIG_MASK =
            CONFIG_DENSITY | CONFIG_TOUCHSCREEN | CONFIG_COLOR_MODE;

    @NonNull
    private final ActivityRecord mActivityRecord;

    /**
     * {@code true} if the activity has moved to a different display and has not been restarted yet.
     */
    private boolean mDisplayChangedWithoutRestart;

    AppCompatDisplayCompatModePolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
    }

    /**
     * Returns whether the restart menu is enabled for display move. Currently it only gets shown
     * when an app is in display or size compat mode.
     *
     * @return {@code true} if the restart menu should be enabled for display move.
     */
    boolean isRestartMenuEnabledForDisplayMove() {
        // Restart menu is only available to apps in display/size compat mode.
        return ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS.isTrue()
                && (isInDisplayCompatMode()
                || (mActivityRecord.inSizeCompatMode() && mDisplayChangedWithoutRestart));
    }

    /**
     * Returns whether the app should be restarted when moved to a different display for app-compat.
     */
    boolean shouldRestartOnDisplayMove() {
        // TODO(b/427878712): Discuss opt-in/out policies.
        return mActivityRecord.mAppCompatController.getDisplayOverrides()
                .shouldRestartOnDisplayMove();
    }

    /**
     * Called when the activity is moved to a different display.
     *
     * @param previousDisplay The display the app was on before this display transition.
     * @param newDisplay The new display the app got moved onto.
     */
    void onMovedToDisplay(@NonNull DisplayContent previousDisplay,
            @NonNull DisplayContent newDisplay) {
        if (previousDisplay.getDisplayInfo().type == TYPE_INTERNAL
                && newDisplay.getDisplayInfo().type == TYPE_INTERNAL) {
            // A transition between internal displays (fold<->unfold on foldable) is not considered
            // display move here for now because they generally have many configurations in common,
            // thus are less likely to cause compat issues.
            return;
        }
        mDisplayChangedWithoutRestart = true;

        if (ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE.isTrue() && shouldRestartOnDisplayMove()) {
            // At this point, a transition for moving the app between displays should be running, so
            // the restarting logic below will be queued as a new transition, which means the
            // configuration change for the display move has been processed when the process is
            // restarted. This allows the app to be launched in the latest configuration.
            mActivityRecord.restartProcessIfVisible();
        }
    }

    /**
     * Returns {@code true} if the activity has moved to a different display and has not been
     * restarted yet.
     */
    boolean getDisplayChangedWithoutRestart() {
        return mDisplayChangedWithoutRestart;
    }

    /**
     * Called when the activity's process is restarted.
     */
    void onProcessRestarted() {
        mDisplayChangedWithoutRestart = false;
    }

    private boolean isInDisplayCompatMode() {
        return getDisplayCompatModeConfigMask() != 0;
    }

    private boolean isEligibleForDisplayCompatMode() {
        return getStaticDisplayCompatModeConfigMask() != 0;
    }

    /**
     * Returns the mask of the config changes that should not trigger activity restart with display
     * move for app-compat reasons.
     *
     * @return the mask of the config changes that should not trigger activity restart or 0 if
     * display compat mode is not enabled for the activity.
     */
    int getDisplayCompatModeConfigMask() {
        // Enable display compat mode only when display move is involved.
        return mDisplayChangedWithoutRestart ? getStaticDisplayCompatModeConfigMask() : 0;
    }

    private int getStaticDisplayCompatModeConfigMask() {
        if (!ENABLE_DISPLAY_COMPAT_MODE.isTrue()) return 0;

        if (mActivityRecord.info.applicationInfo.category != ApplicationInfo.CATEGORY_GAME) {
            // A large majority of apps that crash with display move are games. Apply this compat
            // treatment only to games to minimize risk.
            return 0;
        }

        // If a specific config change is supported by the activity, it's exempted from this compat
        // treatment. This way, apps can opt out from display compat mode by handling all the config
        // changes that happen with display move by themselves.
        final int supportedConfigChanged = mActivityRecord.info.getRealConfigChanged();
        return DISPLAY_COMPAT_MODE_CONFIG_MASK & (~supportedConfigChanged);
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        if (isEligibleForDisplayCompatMode()) {
            pw.println(prefix + "isEligibleForDisplayCompatMode=true");
        }
        if (isInDisplayCompatMode()) {
            pw.println(prefix + "isInDisplayCompatMode=true");
        }
    }
}
