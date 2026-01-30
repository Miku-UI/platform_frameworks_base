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

import android.annotation.Nullable;
import android.content.theming.ThemeStyle;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Represents the immutable theme state for a specific user.
 * <p>
 * This class encapsulates attributes like seed color, style, contrast, and associated user
 * profiles. This record provides a convenient way to store and update the theme-related
 * settings for a user, ensuring immutability and ease of comparison between different states.
 * <p>
 * The 'apply' methods (e.g., {@code applySeedColor}, {@code applyStyle}) are designed to
 * return the same {@code ThemeState} instance if no changes are made. This facilitates
 * efficient comparison between pending and current states, allowing for optimized theme updates.
 *
 * @param userId        The ID of the user.
 * @param isSetup       {@code true} if the user has completed the setup wizard.
 * @param seedColor     The seed color used for theme generation.
 * @param contrast      The user-selected contrast level.
 * @param style         The theme style, e.g., TONAL_SPOT, VIBRANT.
 * @param childProfiles A set of user IDs for associated profiles.
 * @param timeStamp     A timestamp also used to force updates.
 */
record ThemeState(
        int userId,
        boolean isSetup,
        int seedColor,
        float contrast,
        int style,
        Set<Integer> childProfiles,
        long timeStamp
) {
    ThemeState(int userId, boolean isSetup, int seedColor, float contrast,
            @ThemeStyle.Type Integer style, Set<Integer> childProfiles, long timeStamp) {
        this(userId, isSetup, seedColor, contrast, style == null ? 0 : style, childProfiles,
                timeStamp); // Delegates to canonical
    }

    ThemeState withSeedColor(int newSeedColor) {
        if (seedColor == newSeedColor) {
            return this;
        }

        return new ThemeState(
                userId,
                isSetup,
                newSeedColor,
                contrast,
                style,
                childProfiles,
                timeStamp
        );
    }

    ThemeState withStyle(@Nullable @ThemeStyle.Type Integer newStyle) {
        if (newStyle == null || newStyle.equals(style)) {
            return this;
        }

        return new ThemeState(
                userId,
                isSetup,
                seedColor,
                contrast,
                newStyle,
                childProfiles,
                timeStamp
        );
    }

    ThemeState withContrast(float newContrast) {
        if (contrast == newContrast) {
            return this;
        }

        return new ThemeState(
                userId,
                isSetup,
                seedColor,
                newContrast,
                style,
                childProfiles,
                timeStamp
        );
    }

    ThemeState withSetupComplete() {
        if (isSetup) {
            return this;
        }

        return new ThemeState(
                userId,
                true,
                seedColor,
                contrast,
                style,
                childProfiles,
                timeStamp
        );
    }

    ThemeState addProfile(int profileId) {
        if (childProfiles.contains(profileId)) {
            return this;
        }

        HashSet<Integer> newChildProfiles = new HashSet<>(childProfiles);
        newChildProfiles.add(profileId);

        return new ThemeState(
                userId,
                isSetup,
                seedColor,
                contrast,
                style,
                Collections.unmodifiableSet(newChildProfiles),
                timeStamp
        );
    }

    // Use this to cause a difference between states, forcing an update.
    ThemeState withTimeStamp() {
        return new ThemeState(
                userId,
                true,
                seedColor,
                contrast,
                style,
                childProfiles,
                System.currentTimeMillis()
        );
    }

    /**
     * Dumps the current state of the ThemeState to the provided PrintWriter.
     *
     * @param pw     The PrintWriter to dump the state to.
     * @param prefix A prefix to prepend to each line for indentation.
     */
    public void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "isSetup: " + isSetup);
        pw.println(
                prefix + "seedColor: #" + Integer.toHexString(seedColor).toUpperCase(Locale.ROOT));
        pw.println(prefix + "contrast: " + contrast);
        pw.println(prefix + "style: " + ThemeStyle.toString(style));
        pw.println(prefix + "childProfiles: " + childProfiles);
        pw.println(prefix + "timeStamp: " + timeStamp);
    }
}


