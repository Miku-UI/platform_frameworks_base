/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settingslib.display;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import java.util.function.Predicate;

/** Utility methods for controlling the display density. */
public class DisplayDensityConfiguration {
    private static final String LOG_TAG = "DisplayDensityConfig";
    /*
     * The default display size in inches used to estimate PPI when physical dimensions are unknown.
     */
    public static final double DEFAULT_DISPLAY_SIZE = 24.0;

    /**
     * Touch target size 10.4mm in inches (divided by mm per inch 25.4).
     */
    public static final double EXTERNAL_DISPLAY_BASE_TOUCH_TARGET_SIZE_IN_INCHES = 10.4 / 25.4;

    /*
     * The minimum allowable density in DPI for an external display.
     */
    public static final int EXTERNAL_DISPLAY_MIN_DENSITY_DPI = 100;
    /*
     * The standard touch target size in density-independent pixels (dp) used for density
     * calculations.
     */
    public static final double BASE_TOUCH_TARGET_SIZE_DP = 48.0;

    /**
     * Returns the default density for the specified display.
     *
     * @param displayId the identifier of the display
     * @return the default density of the specified display, or {@code -1} if the display does not
     *     exist or the density could not be obtained
     */
    static int getDefaultDisplayDensity(int displayId) {
        try {
            final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            return wm.getInitialDisplayDensity(displayId);
        } catch (RemoteException exc) {
            return -1;
        }
    }

    /**
     * Asynchronously applies display density changes to the specified display.
     *
     * <p>The change will be applied to the user specified by the value of {@link
     * UserHandle#myUserId()} at the time the method is called.
     *
     * @param displayId the identifier of the display to modify
     */
    public static void clearForcedDisplayDensity(final int displayId) {
        final int userId = UserHandle.myUserId();
        AsyncTask.execute(
                () -> {
                    try {
                        final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                        wm.clearForcedDisplayDensityForUser(displayId, userId);
                    } catch (RemoteException exc) {
                        Log.w(LOG_TAG, "Unable to clear forced display density setting");
                    }
                });
    }

    /**
     * Asynchronously applies display density changes to the specified display.
     *
     * <p>The change will be applied to the user specified by the value of {@link
     * UserHandle#myUserId()} at the time the method is called.
     *
     * @param displayId the identifier of the display to modify
     * @param density the density to force for the specified display
     */
    public static void setForcedDisplayDensity(final int displayId, final int density) {
        final int userId = UserHandle.myUserId();
        AsyncTask.execute(
                () -> {
                    try {
                        final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                        wm.setForcedDisplayDensityForUser(displayId, density, userId);
                    } catch (RemoteException exc) {
                        Log.w(LOG_TAG, "Unable to save forced display density setting");
                    }
                });
    }

    /**
     * Asynchronously applies display density changes to all displays that satisfy the predicate.
     *
     * <p>The change will be applied to the user specified by the value of
     * {@link UserHandle#myUserId()} at the time the method is called.
     *
     * @param context The context
     * @param predicate Determines which displays to set the density to
     * @param density The density to force
     */
    public static void setForcedDisplayDensity(@NonNull Context context,
            @NonNull Predicate<DisplayInfo> predicate, final int density) {
        final int userId = UserHandle.myUserId();
        DisplayManager dm = context.getSystemService(DisplayManager.class);
        AsyncTask.execute(() -> {
            try {
                for (Display display : dm.getDisplays(
                        DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)) {
                    int displayId = display.getDisplayId();
                    DisplayInfo info = new DisplayInfo();
                    if (!display.getDisplayInfo(info)) {
                        Log.w(LOG_TAG, "Unable to save forced display density setting "
                                + "for display " + displayId);
                        continue;
                    }
                    if (!predicate.test(info)) {
                        continue;
                    }

                    final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                    wm.setForcedDisplayDensityForUser(displayId, density, userId);
                }
            } catch (RemoteException exc) {
                Log.w(LOG_TAG, "Unable to save forced display density setting");
            }
        });
    }

    /**
     * Calculates the base density for a display based on its physical dimensions and DPI.
     */
    public static int calculateBaseDensity(float xDpi, float yDpi, int width, int height) {
        // physical pixel density of the display
        double ppi;
        if (xDpi > 0 && yDpi > 0) {
            ppi = Math.sqrt((Math.pow(xDpi, 2)
                    + Math.pow(yDpi, 2)) / 2);
        } else {
            // xDPI and yDPI is missing, calculate DPI from display resolution and
            // default display size
            ppi = Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2))
                    / DEFAULT_DISPLAY_SIZE;
        }
        // pixels needed to achieve target touch target size
        double pixels = ppi * EXTERNAL_DISPLAY_BASE_TOUCH_TARGET_SIZE_IN_INCHES;
        double dpi =
                pixels * DisplayMetrics.DENSITY_DEFAULT / BASE_TOUCH_TARGET_SIZE_DP;
        return Math.max((int) (dpi + 0.5), EXTERNAL_DISPLAY_MIN_DENSITY_DPI);
    }
}
