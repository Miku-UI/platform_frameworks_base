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

package com.android.server.wallpaper;

import android.app.WallpaperColors;
import android.os.Handler;


/**
 * Wallpaper manager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class WallpaperManagerInternal {

    /**
     * Notifies the display is ready for adding wallpaper on it.
     */
    public abstract void onDisplayAddSystemDecorations(int displayId);

    /** Notifies when display stop showing system decorations and wallpaper. */
    public abstract void onDisplayRemoveSystemDecorations(int displayId);

    /** Notifies when the screen finished turning on and is visible to the user. */
    public abstract void onScreenTurnedOn(int displayId);

    /** Notifies when the screen starts turning on and is not yet visible to the user. */
    public abstract void onScreenTurningOn(int displayId);

    /**
     * Get the primary colors of the wallpaper configured in the given user.
     * @param which wallpaper type. Must be either FLAG_SYSTEM or FLAG_LOCK
     * @param userId Owner of the wallpaper.
     * @return {@link WallpaperColors} or null if colors are unknown.
     * @hide
     */
    public abstract WallpaperColors getWallpaperColors(int which, int userId);

    /**
     * Callback for receiving notifications about wallpaper color changes.
     *
     * @see #addOnColorsChangedListener(ColorsChangedCallbackInternal, Handler)
     */
    public interface ColorsChangedCallbackInternal {
        /**
         * Called when the wallpaper colors have changed for a specific user and display.
         *
         * @param colors            The new {@link WallpaperColors} of the wallpaper.
         * @param which             A combination of
         *                          {@link android.app.WallpaperManager#FLAG_SYSTEM} and/or
         *                          {@link android.app.WallpaperManager#FLAG_LOCK} to indicate which
         *                          wallpaper's colors have changed.
         * @param displayId         The id of the display which wallpaper colors have changed.
         * @param userId            The id of the user whose wallpaper colors have changed.
         * @param fromForegroundApp true if the color change originated from a foreground app.
         */
        void onColorsChanged(WallpaperColors colors, int which, int displayId, int userId,
                boolean fromForegroundApp);
    }

    /**
     * Adds a listener for wallpaper color changes.
     *
     * @param listener The listener to add.
     * @param handler  The handler on which the listener will be invoked.
     */
    public abstract void addOnColorsChangedListener(ColorsChangedCallbackInternal listener,
            Handler handler);
}