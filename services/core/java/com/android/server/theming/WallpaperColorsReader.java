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

import android.annotation.UserIdInt;
import android.app.WallpaperColors;
import android.app.WallpaperManager;

/**
 * An interface that abstracts the reading of wallpaper colors, primarily to facilitate testing.
 * <p>
 * This interface serves as a wrapper around {@link android.app.WallpaperManager}, allowing
 * dependencies that rely on wallpaper colors to be unit-tested by injecting a mock
 * implementation.
 * <p>
 * In the context of theming, this is used to retrieve the colors from the current home or
 * lock screen wallpaper, which can then be used as a seed for generating dynamic color schemes.
 */
interface WallpaperColorsReader {
    WallpaperColors getWallpaperColors(@WallpaperManager.SetWallpaperFlags int which,
            @UserIdInt int userId);
}