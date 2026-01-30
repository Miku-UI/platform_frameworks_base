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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.R;
import com.android.systemui.monet.ColorScheme;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Holds the current and pending {@link ThemeState} for a user, managing updates
 * and handling potential deferments due to wallpaper changes from background apps.
 * <p>
 * This class facilitates theme updates by tracking both the current and pending theme states,
 * preventing unnecessary overlay updates. It also handles deferments when wallpaper changes
 * originate from background apps, ensuring that theme updates are applied at the appropriate time.
 */
class ThemeStatePair {
    private static final String TAG = ThemeStatePair.class.getSimpleName();

    public final int userId;
    private ThemeState mCurrent;
    private ThemeState mPending;
    private boolean mThemeUpdatesDeferredOnLock = false;
    private ScheduledFuture<?> mFuture;

    // We are storing only the currently applied Schemes and Overlays
    private ColorScheme mDarkScheme;
    private ColorScheme mLightScheme;

    /**
     * Constructs a new ThemeStatePair object.
     *
     * @param userId    The ID of the user associated with this state pair.
     * @param isSetup   Indicates whether the user has completed setup.
     * @param seedColor The initial seed color for the user's theme.
     * @param contrast  The initial contrast value for the user's theme.
     * @param style     The initial style for the user's theme.
     */
    @SuppressLint("WrongConstant")
    protected ThemeStatePair(
            int userId,
            boolean isSetup,
            int seedColor,
            float contrast,
            @ThemeStyle.Type Integer style) {

        this.userId = userId;

        ThemeState initialState = new ThemeState(userId, isSetup, seedColor, contrast, style,
                Collections.unmodifiableSet(new HashSet<>()), 0);

        mDarkScheme = new ColorScheme(seedColor, true, style, contrast);
        mLightScheme = new ColorScheme(seedColor, false, style, contrast);

        mPending = initialState;
        mCurrent = initialState;
    }

    /**
     * Applies a new seed color to the pending theme state.
     *
     * @param newSeedColor The new seed color to apply.
     */
    protected void applySeedColor(int newSeedColor) {
        mPending = mPending.withSeedColor(newSeedColor);
    }

    /**
     * Applies a new style to the pending theme state.
     *
     * @param newStyle The new style to apply.
     */
    protected void applyStyle(@ThemeStyle.Type Integer newStyle) {
        mPending = mPending.withStyle(newStyle);
    }

    /**
     * Applies a new contrast value to the pending theme state.
     *
     * @param newContrast The new contrast value to apply.
     */
    protected void applyContrast(float newContrast) {
        mPending = mPending.withContrast(newContrast);
    }

    /**
     * Marks the pending theme state as setup complete.
     */
    protected void applySetupComplete() {
        mPending = mPending.withSetupComplete();
    }

    /**
     * Adds a new profile ID to the pending theme state.
     *
     * @param profileId The ID of the new profile.
     */
    protected void addProfile(int profileId) {
        mPending = mPending.addProfile(profileId);
    }

    /**
     * Forces an update to the theme by applying a new timestamp to the pending state.
     * This ensures that the theme will be reevaluated and overlays will be updated.
     */
    protected void forceUpdate() {
        mPending = mPending.withTimeStamp();
    }


    // setters and getters

    /**
     * Returns the current {@link ThemeState}. The returned object is immutable.
     *
     * @return The current state.
     */
    protected ThemeState getCurrentState() {
        return mCurrent;
    }

    /**
     * Returns the pending {@link ThemeState}. The returned object is immutable.
     *
     * @return The pending state, or {@code null} if there are no scheduled updates.
     */
    @Nullable
    protected ThemeState getPendingState() {
        return mPending.equals(mCurrent) ? null : mPending;
    }

    /**
     * Returns the {@link ScheduledFuture} associated with the current theme update task,
     * or {@code null} if there is no task scheduled.
     */
    @Nullable
    protected ScheduledFuture<?> getFuture() {
        return mFuture;
    }

    /**
     * Sets the {@link ScheduledFuture} associated with the current theme update task.
     *
     * @param newTask The new task to set.
     */
    protected void setFuture(ScheduledFuture<?> newTask) {
        mFuture = newTask;
    }

    /**
     * Clears the current theme update task, effectively cancelling any pending updates.
     */
    protected void clearTimer() {
        mFuture = null;
    }

    /**
     * Checks if theme updates are currently deferred until the device is locked.
     *
     * @return {@code true} if updates are deferred, {@code false} otherwise.
     * @see #setDeferUpdatesOnLock(boolean)
     */
    protected boolean areUpdatesDeferredOnLock() {
        return mThemeUpdatesDeferredOnLock;
    }

    /**
     * Sets whether to defer theme updates until the device is locked.
     *
     * <p>This is used to prevent jarring theme changes when a background application
     * (e.g., a live wallpaper) changes the color scheme while the user is actively
     * using the device. When deferred, the pending theme update will be applied the
     * next time the device enters the locked state.
     *
     * @param defer {@code true} to defer updates until the next lock, {@code false} to allow
     *              immediate updates.
     */
    protected void setDeferUpdatesOnLock(boolean defer) {
        mThemeUpdatesDeferredOnLock = defer;
    }

    /**
     * Returns the set of child profile IDs associated with the pending theme state.
     */
    protected Set<Integer> getPendingChildProfiles() {
        return mPending.childProfiles();
    }

    protected ColorScheme getDarkScheme() {
        return mDarkScheme;
    }

    protected ColorScheme getLightScheme() {
        return mLightScheme;
    }

    /**
     * Updates the current theme state with the provided ColorSchemes and sets the current
     * state to the pending state, finalizing the theme update.
     *
     * @param newDarkScheme  The new dark {@link ColorScheme}.
     * @param newLightScheme The new light {@link ColorScheme}.
     */
    protected void update(ColorScheme newDarkScheme, ColorScheme newLightScheme) {
        mDarkScheme = newDarkScheme;
        mLightScheme = newLightScheme;
        mCurrent = mPending;
    }

    /**
     * Generates a new ColorScheme based on the pending theme state and the provided
     * darkness flag.
     *
     * @param isDark {@code true} to generate a dark scheme, {@code false} for light.
     * @return The newly generated ColorScheme.
     */
    protected ColorScheme generatePendingScheme(boolean isDark) {
        return new ColorScheme(mPending.seedColor(), isDark, mPending.style(), mPending.contrast());
    }

    // Useful checks before updating state

    /**
     * Checks if the current state warrants an update to the applied overlays.
     * <p>
     * This method considers various factors, including:
     * - Whether the theme state has changed.
     * - Whether the ColorScheme requires a new overlay.
     *
     * @return {@code true} if an update is necessary, {@code false} otherwise.
     */
    protected boolean shouldUpdateOverlays() {
        if (mPending.equals(mCurrent)) {
            Slog.d(TAG, "No change in State for user " + userId + ". Skipping. ");
            return false;
        }

        // Checks if ColorScheme related state attributes (contrast, seedColor and Style) are
        // different. Only in this case we must regenerate a new Overlay
        if (mCurrent.seedColor() == mPending.seedColor()
                && mCurrent.contrast() == mPending.contrast()
                && mCurrent.style() == mPending.style()) {
            Slog.d(TAG, "User " + userId + " state updated, but new overlay was not necessary");
            return false;
        }

        return true;
    }

    /**
     * Checks if the current state warrants an update to the applied overlays.
     * <p>
     * This method considers various factors, including:
     * - Whether the user has completed setup.
     * - Whether changes are deferred due to a wallpaper change from a background app.
     * - Whether the theme state has changed.
     * - Whether the ColorScheme requires a new overlay.
     *
     * @return {@code true} if an update is necessary, {@code false} otherwise.
     */
    protected boolean shouldUpdate() {
        // force update in case of different timeStamp
        if (mCurrent.timeStamp() != mPending.timeStamp()) {
            Slog.d(TAG, "User " + userId + " requested forced update");
            return true;
        }

        if (mPending.equals(mCurrent)) {
            Slog.d(TAG, "No change in State for user " + userId + ". Skipping. ");
            return false;
        }

        // never update if user is not setup, even if forced
        if (!mPending.isSetup()) {
            Slog.d(TAG, "Deferring theme evaluation for user " + userId + " during setup");
            return false;
        }

        if (mThemeUpdatesDeferredOnLock) {
            Slog.d(TAG, "Deferring theme evaluation of user " + userId
                    + " due to wallpaper change from background app");
            return false;
        }

        if (shouldUpdateOverlays()) {
            return true;
        }


        Slog.d(TAG, "User " + userId + " will update.");
        return true;
    }


    /**
     * Checks if the current ColorScheme is correctly applied across all user profiles.
     * <p>
     * This method verifies that the colors extracted from the ColorScheme match the
     * actual colors applied in the system resources for each profile associated with
     * the current state.
     * <p>
     * Note: This is a heuristic check and does not verify every single color. It checks a
     * representative subset of colors to determine if the ColorScheme is generally applied.
     *
     * @param mainContext The main application context.
     * @return {@code true} if the ColorScheme is correctly applied, {@code false} otherwise.
     */
    protected boolean isColorSchemeApplied(Context mainContext) {
        final Set<Integer> allProfiles = new HashSet<>(mCurrent.childProfiles());
        allProfiles.add(userId);

        for (Integer userId : allProfiles) {
            Resources res = mainContext.createContextAsUser(UserHandle.of(userId),
                    0).getResources();

            if (!(res.getColor(R.color.system_accent1_500_dark)
                    == mDarkScheme.getAccent1().getS500()
                    && res.getColor(R.color.system_accent1_500_light)
                    == mLightScheme.getAccent1().getS500()

                    && res.getColor(com.android.internal.R.color.system_accent2_500_dark)
                    == mDarkScheme.getAccent2().getS500()
                    && res.getColor(R.color.system_accent2_500_light)
                    == mLightScheme.getAccent2().getS500()

                    && res.getColor(com.android.internal.R.color.system_accent3_500_dark)
                    == mDarkScheme.getAccent3().getS500()
                    && res.getColor(R.color.system_accent3_500_light)
                    == mLightScheme.getAccent3().getS500()

                    && res.getColor(com.android.internal.R.color.system_neutral1_500_dark)
                    == mDarkScheme.getNeutral1().getS500()
                    && res.getColor(R.color.system_neutral1_500_light)
                    == mLightScheme.getNeutral1().getS500()

                    && res.getColor(com.android.internal.R.color.system_neutral2_500_dark)
                    == mDarkScheme.getNeutral2().getS500()
                    && res.getColor(R.color.system_neutral2_500_light)
                    == mLightScheme.getNeutral2().getS500()

                    && res.getColor(android.R.color.system_outline_variant_dark)
                    == mDarkScheme.getMaterialScheme().getOutlineVariant()
                    && res.getColor(android.R.color.system_outline_variant_light)
                    == mLightScheme.getMaterialScheme().getOutlineVariant()

                    && res.getColor(android.R.color.system_primary_container_dark)
                    == mDarkScheme.getMaterialScheme().getPrimaryContainer()
                    && res.getColor(android.R.color.system_primary_container_light)
                    == mLightScheme.getMaterialScheme().getPrimaryContainer())
            ) {
                return false;
            }
        }

        return true;
    }

    /**
     * Dumps the current state of the ThemeStatePair to the provided PrintWriter.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    public void dump(PrintWriter pw) {
        pw.println("    userId: " + userId);
        pw.println("    isDeferred: " + mThemeUpdatesDeferredOnLock);
        pw.println("    Current State:");
        mCurrent.dump(pw, "      ");
        ThemeState pending = getPendingState();
        if (pending != null) {
            pw.println("    Pending State:");
            pending.dump(pw, "      ");
        } else {
            pw.println("    Pending State: (same as current)");
        }
    }
}
