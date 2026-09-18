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

package com.android.server.theming;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.os.Handler;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.wallpaper.WallpaperManagerInternal;
import com.android.systemui.monet.ColorScheme;

import java.util.Collections;
import java.util.List;

/**
 * A safe wrapper for WallpaperManagerInternal.
 * <p>
 * This class handles cases where WallpaperManager might be missing (for example in Android Auto).
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeWallpaperManager implements WallpaperColorsReader {

    @Nullable
    private final WallpaperManagerInternal mService;

    ThemeWallpaperManager() {
        this(LocalServices.getService(WallpaperManagerInternal.class));
    }

    @VisibleForTesting
    ThemeWallpaperManager(@Nullable WallpaperManagerInternal service) {
        mService = service;
    }

    /**
     * Registers a listener for wallpaper color changes.
     * If the WallpaperManager is not available, this is a no-op.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void addOnColorsChangedListener(
            @NonNull WallpaperManagerInternal.ColorsChangedCallbackInternal listener,
            @NonNull Handler handler) {
        if (mService != null) {
            mService.addOnColorsChangedListener(listener, handler);
        }
    }

    /**
     * Removes a listener for wallpaper color changes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void removeOnColorsChangedListener(
            @NonNull WallpaperManagerInternal.ColorsChangedCallbackInternal listener) {
        if (mService != null) {
            mService.removeOnColorsChangedListener(listener);
        }
    }

    /**
     * Retrieves the current wallpaper colors.
     * Returns {@code null} if the WallpaperManager is unavailable or if no colors are set.
     */
    @Override
    @Nullable
    public WallpaperColors getWallpaperColors(int which, int userId) {
        if (mService == null) {
            return null;
        }
        return mService.getWallpaperColors(which, userId);
    }

    /**
     * Retrieves the seed color from the current system wallpaper.
     * <p>
     * This method uses {@link ColorScheme#getSeedColor(WallpaperColors)} to ensure consistency
     * with the rest of the theming system.
     *
     * @param userId The user ID to get the wallpaper color for.
     * @return The seed color, or null if no wallpaper color is available.
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public Integer getSeedColor(int userId) {
        WallpaperColors colors = getWallpaperColors(WallpaperManager.FLAG_SYSTEM, userId);
        if (colors == null) {
            return null;
        }
        return ColorScheme.getSeedColor(colors);
    }

    /**
     * Retrieves the prioritized seed colors from the current system wallpaper.
     *
     * @param userId The user ID to get the wallpaper colors for.
     * @return A list of seed colors, or an empty list if none are available.
     */
    @NonNull
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public List<Integer> getSeedColors(int userId) {
        WallpaperColors colors = getWallpaperColors(WallpaperManager.FLAG_SYSTEM, userId);
        if (colors == null) {
            return Collections.emptyList();
        }
        return ColorScheme.getSeedColors(colors);
    }

    /**
     * Retrieves prioritized seed colors from the provided WallpaperColors.
     */
    @NonNull
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public List<Integer> getSeedColors(@Nullable WallpaperColors colors, boolean filter) {
        if (colors == null) {
            return Collections.emptyList();
        }
        return ColorScheme.getSeedColors(colors, filter);
    }

    /**
     * Checks if the WallpaperManager is available on this device.
     */
    @VisibleForTesting
    public boolean isWallpaperManagerAvailable() {
        return mService != null;
    }
}
