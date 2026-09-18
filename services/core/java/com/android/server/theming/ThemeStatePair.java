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
import android.content.theming.ThemeStyle;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.monet.ColorScheme;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Holds the current and pending {@link ThemeState} for a user, managing updates and handling
 * potential deferments due to wallpaper changes from background apps.
 * <p>
 * <h3>Concurrency and Locking</h3>
 * This class has an internal lock ({@code mLock}) to ensure thread safety for all state mutations
 * and reads. This strategy reduces contention on the global {@link ThemeStateManager} lock.
 * <p>
 * The {@link #commitAndGetOverlayData()} method is important for this Design. It atomically:
 * <ol>
 *   <li>Commits the {@code pending} state to {@code current}.</li>
 *   <li>Generates necessary {@link ColorScheme} objects if overlays need updating.</li>
 *   <li>Returns an immutable {@link OverlaySnapshot}.</li>
 * </ol>
 * This snapshot contains all data required by {@link ThemeOverlayHelper} to apply overlays,
 * allowing that expensive operation to execute <b>without holding any locks</b>.
 *
 * @hide
 */
@VisibleForTesting(visibility =
        VisibleForTesting.Visibility.PACKAGE)
public class ThemeStatePair {
    private static final String TAG = "ThemeStatePair";

    public final int userId;

    private final ThemeEnvironment mEnvironment;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private ThemeState mCurrent;
    @GuardedBy("mLock")
    private ThemeState mPending;
    @GuardedBy("mLock")
    private boolean mThemeUpdatesDeferredOnLock = false;
    @GuardedBy("mLock")
    private ScheduledFuture<?> mFuture;

    // We are storing only the currently applied Schemes and Overlays
    @GuardedBy("mLock")
    private ColorScheme mDarkScheme;
    @GuardedBy("mLock")
    private ColorScheme mLightScheme;

    /**
     * Constructs a new ThemeStatePair object.
     *
     * @param userId      The ID of the user associated with this state pair.
     * @param isSetup     Indicates whether the user has completed setup.
     * @param seedColors  The initial seed colors for the user's theme.
     * @param contrast    The initial contrast value for the user's theme.
     * @param style       The initial style for the user's theme.
     * @param environment The system-wide theme environment.
     */
    @SuppressLint("WrongConstant")
    ThemeStatePair(
            int userId,
            boolean isSetup,
            List<Integer> seedColors,
            float contrast,
            @ThemeStyle.Type Integer style,
            ThemeEnvironment environment) {

        this.userId = userId;
        this.mEnvironment = environment;

        ThemeState initialState = new ThemeState(userId, isSetup, seedColors, contrast, style,
                Collections.unmodifiableSet(new HashSet<>()), 0);

        mDarkScheme = new ColorScheme(seedColors, true, style, contrast,
                environment.getConfig().specVersion(), environment.getConfig().platform());
        mLightScheme = new ColorScheme(seedColors, false, style, contrast,
                environment.getConfig().specVersion(), environment.getConfig().platform());

        mPending = initialState;
        mCurrent = initialState;
    }

    /**
     * Applies a new seed color list to the pending theme state.
     *
     * @param newSeedColors The new seed colors to apply.
     */
    void applySeedColors(List<Integer> newSeedColors) {
        synchronized (mLock) {
            mPending = mPending.withSeedColors(newSeedColors);
        }
    }

    /**
     * Applies a new seed color to the pending theme state.
     *
     * @param newSeedColor The new seed color to apply.
     */
    void applySeedColor(int newSeedColor) {
        synchronized (mLock) {
            mPending = mPending.withSeedColor(newSeedColor);
        }
    }

    /**
     * Applies a new style to the pending theme state.
     *
     * @param newStyle The new style to apply.
     */
    void applyStyle(@ThemeStyle.Type Integer newStyle) {
        synchronized (mLock) {
            mPending = mPending.withStyle(newStyle);
        }
    }

    /**
     * Applies a new contrast value to the pending theme state.
     *
     * @param newContrast The new contrast value to apply.
     */
    void applyContrast(float newContrast) {
        synchronized (mLock) {
            mPending = mPending.withContrast(newContrast);
        }
    }

    /**
     * Marks the pending theme state as setup complete.
     */
    void applySetupComplete() {
        synchronized (mLock) {
            mPending = mPending.withSetupComplete();
        }
    }

    /**
     * Adds a new profile ID to the pending theme state.
     *
     * @param profileId The ID of the new profile.
     */
    void addProfile(int profileId) {
        synchronized (mLock) {
            mPending = mPending.addProfile(profileId);
        }
    }

    /**
     * Forces an update to the theme by applying a new timestamp to the pending state.
     * This ensures that the theme will be reevaluated and overlays will be updated.
     */
    void forceUpdate() {
        synchronized (mLock) {
            mPending = mPending.withTimeStamp();
        }
    }


    // setters and getters

    /**
     * Returns the current {@link ThemeState}. The returned object is immutable.
     *
     * @return The current state.
     */
    ThemeState getCurrentState() {
        synchronized (mLock) {
            return mCurrent;
        }
    }

    /**
     * Returns the pending {@link ThemeState}. The returned object is immutable.
     *
     * @return The pending state, or {@code null} if there are no scheduled updates.
     */
    @Nullable
    ThemeState getPendingState() {
        synchronized (mLock) {
            return mPending.equals(mCurrent) ? null : mPending;
        }
    }

    /**
     * Returns the {@link ScheduledFuture} associated with the current theme update task,
     * or {@code null} if there is no task scheduled.
     */
    @Nullable
    ScheduledFuture<?> getFuture() {
        synchronized (mLock) {
            return mFuture;
        }
    }

    /**
     * Sets the {@link ScheduledFuture} associated with the current theme update task.
     *
     * @param newTask The new task to set.
     */
    void setFuture(ScheduledFuture<?> newTask) {
        synchronized (mLock) {
            mFuture = newTask;
        }
    }

    /**
     * Clears the current theme update task, effectively cancelling any pending updates.
     */
    void clearTimer() {
        synchronized (mLock) {
            mFuture = null;
        }
    }

    /**
     * Checks if theme updates are currently deferred until the device is locked.
     *
     * @return {@code true} if updates are deferred, {@code false} otherwise.
     * @see #setDeferUpdatesOnLock(boolean)
     */
    boolean areUpdatesDeferredOnLock() {
        synchronized (mLock) {
            return mThemeUpdatesDeferredOnLock;
        }
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
    void setDeferUpdatesOnLock(boolean defer) {
        synchronized (mLock) {
            mThemeUpdatesDeferredOnLock = defer;
        }
    }

    /**
     * Returns the set of child profile IDs associated with the pending theme state.
     */
    Set<Integer> getPendingChildProfiles() {
        synchronized (mLock) {
            return mPending.childProfiles();
        }
    }

    /**
     * Returns the current dark color scheme.
     */
    ColorScheme getDarkScheme() {
        synchronized (mLock) {
            return mDarkScheme;
        }
    }

    /**
     * Returns the current light color scheme.
     */
    ColorScheme getLightScheme() {
        synchronized (mLock) {
            return mLightScheme;
        }
    }

    /**
     * Commits the pending state to the current state, generating new ColorSchemes if necessary.
     * Returns a snapshot of the state needed to apply overlays, allowing the actual
     * application to happen outside the lock.
     *
     * @return A snapshot of the theme state for overlay application.
     */
    @Nullable
    OverlaySnapshot commitAndGetOverlayData() {
        ThemeState stateToCommit;
        synchronized (mLock) {
            // If the timestamps are different, this was a forced update.
            // We should treat it as a content change to ensure re-registration.
            final boolean forceContentChange = mCurrent.timeStamp() != mPending.timeStamp();

            if (!shouldUpdateOverlaysLocked() && !forceContentChange) {
                mCurrent = mPending;
                return new OverlaySnapshot(userId, mPending.childProfiles(), mLightScheme,
                        mDarkScheme, /*contentChanged*/ false);
            }
            stateToCommit = mPending;
        }

        ColorScheme newDarkScheme = new ColorScheme(stateToCommit.seedColors(), true,
                stateToCommit.style(), stateToCommit.contrast(),
                mEnvironment.getConfig().specVersion(),
                mEnvironment.getConfig().platform());
        ColorScheme newLightScheme = new ColorScheme(stateToCommit.seedColors(), false,
                stateToCommit.style(), stateToCommit.contrast(),
                mEnvironment.getConfig().specVersion(),
                mEnvironment.getConfig().platform());

        synchronized (mLock) {
            // If the pending state has changed while we were calculating, our calculated schemes
            // are now old. We should throw them away and let the next scheduled task deal with
            // the new state.
            if (!stateToCommit.equals(mPending)) {
                Slog.d(TAG, "State changed during processing, discarding intermediate update.");
                return null;
            }

            mDarkScheme = newDarkScheme;
            mLightScheme = newLightScheme;
            Slog.d(TAG, "User " + userId + " generating new overlays");
            mCurrent = stateToCommit;

            return new OverlaySnapshot(userId, mCurrent.childProfiles(), mLightScheme, mDarkScheme,
                    /*contentChanged*/ true);
        }
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
    boolean shouldUpdateOverlays() {
        synchronized (mLock) {
            return shouldUpdateOverlaysLocked();
        }
    }

    @GuardedBy("mLock")
    private boolean shouldUpdateOverlaysLocked() {
        if (mPending.equals(mCurrent)) {
            Slog.d(TAG, "No change in State for user " + userId + ". Skipping. ");
            return false;
        }

        // Checks if ColorScheme related state attributes (contrast, seedColor and Style) are
        // different. Only in this case we must regenerate a new Overlay
        if (Objects.equals(mCurrent.seedColors(), mPending.seedColors())
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
    boolean shouldUpdate() {
        synchronized (mLock) {
            // force update in case of different timeStamp
            if (mCurrent.timeStamp() != mPending.timeStamp()) {
                Slog.d(TAG, "User " + userId + " requested forced update");
                return true;
            }

            if (mPending.equals(mCurrent)) {
                // No change in State for this user, Skipping
                return false;
            }

            // If we are booting (in bootanimation), we want to allow updates even if setup is not
            // complete or if updates would otherwise be deferred.
            if (mEnvironment.isBooting()) {
                return true;
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

            Slog.d(TAG, "User " + userId + " should update.");
            return true;
        }
    }

    /**
     * Immutable snapshot of the data required to apply overlays.
     */
    record OverlaySnapshot(int userId, Set<Integer> profiles, ColorScheme lightScheme,
                           ColorScheme darkScheme, boolean contentChanged) {
    }

    /**
     * Dumps the current state of the ThemeStatePair to the provided PrintWriter.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("    userId: " + userId);
            pw.println("    isDeferred: " + mThemeUpdatesDeferredOnLock);
            pw.println("    Current State:");
            mCurrent.dump(pw, "      ");
            ThemeState pending = mPending.equals(mCurrent) ? null : mPending;
            if (pending != null) {
                pw.println("    Pending State:");
                pending.dump(pw, "      ");
            } else {
                pw.println("    Pending State: (same as current)");
            }
        }
    }
}
