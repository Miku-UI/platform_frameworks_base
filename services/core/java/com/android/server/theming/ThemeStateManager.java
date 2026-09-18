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
import android.content.Context;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class manages the application of theme overlays across the system for all users.
 * It handles changes in seed color, style, contrast, and user settings to ensure the
 * correct theme overlays are applied.
 * The ThemeStateManager class is responsible for:
 * <ol>
 * Maintaining theme state: It tracks the current and pending theme state for each user,
 * including seed color, style, contrast, and associated profile IDs.
 * </ol><ol>
 * Reacting to theme events: It listens for and processes events that affect the theme state,
 * such as wallpaper changes, user settings modifications, and system events
 * (e.g., user start, setup completion).
 * </ol><ol>
 * Applying theme overlays: It generates ColorSchemes based on the current theme state
 * and coordinates with {@link ThemeOverlayHelper} to apply them.
 * </ol><ol>
 * Debouncing overlay updates: It debounces rapid theme changes to prevent excessive
 * overlay updates, ensuring a smooth user experience.
 * </ol><ol>
 * Handling user lifecycle: It manages theme state for different users and profiles,
 * including adding new users, handling user switching, and applying overlays
 * to the appropriate profiles.
 * </ol><ol>
 * Verifying overlay application: It checks if the applied overlays match the current theme
 * settings and forces an update if necessary, especially during boot complete.
 * </ol>
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeStateManager {
    private static final String TAG = "ThemeStateManager";

    protected static final long DEBOUNCE_MS = 50;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private int mCurrentUserId = UserHandle.USER_NULL;

    // We are storing states for users only. Profiles should target their parent users.
    @GuardedBy("mLock")
    private final SparseArray<ThemeStatePair> mThemeStates = new SparseArray<>();

    private final Context mContext;
    private final ScheduledExecutorService mSchedulerExecutor;
    private final ThemeEnvironment mEnvironment;

    private UserManagerInternal mUserManager;
    private ThemeOverlayHelper mThemeOverlayHelper;

    ThemeStateManager(Context context, ThemeEnvironment environment) {
        this(context, Executors.newSingleThreadScheduledExecutor(), environment);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    ThemeStateManager(Context context, ScheduledExecutorService schedulerExecutor,
            ThemeEnvironment environment) {
        mSchedulerExecutor = schedulerExecutor;
        mContext = context;
        mEnvironment = environment;
    }

    // HANDLERS

    /**
     * Called when all system services are ready.
     * Initializes the necessary internal components.
     */
    void onServicesReady() {
        mUserManager = LocalServices.getService(UserManagerInternal.class);

        if (mThemeOverlayHelper == null) {
            mThemeOverlayHelper = new ThemeOverlayHelper();
        }

        mCurrentUserId = mEnvironment.getCurrentUserId();
    }

    @VisibleForTesting
    void setThemeOverlayHelper(ThemeOverlayHelper themeOverlayHelper) {
        mThemeOverlayHelper = themeOverlayHelper;
    }

    /**
     * Called when the wallpaper colors change or user chooses a preset.
     *
     * @param userId            The ID of the user updating the theme.
     * @param seedColor         Seed color to generate palettes.
     * @param fromForegroundApp Boolean indicating if the event came from a foreground app.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onSeedColorChange(int userId, int seedColor, boolean fromForegroundApp) {
        onSeedColorChange(userId, List.of(seedColor), fromForegroundApp);
    }

    /**
     * Called when the wallpaper colors change or user chooses a preset.
     *
     * @param userId            The ID of the user updating the theme.
     * @param seedColors        Seed colors to generate palettes.
     * @param fromForegroundApp Boolean indicating if the event came from a foreground app.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onSeedColorChange(int userId, List<Integer> seedColors, boolean fromForegroundApp) {
        ThemeStatePair statePair = getState(userId);

        if (!fromForegroundApp && !mEnvironment.isDeviceLocked()) {
            statePair.setDeferUpdatesOnLock(true);
            Slog.w(TAG, "Wallpaper changed from background app, deferring color change ["
                    + seedColors.stream().map(Integer::toHexString).collect(
                    Collectors.joining(", ")) + "]");
        } else if (statePair.areUpdatesDeferredOnLock()) {
            Slog.d(TAG, "Foreground app explicitly changed color, clearing deferral. ["
                    + seedColors.stream().map(Integer::toHexString).collect(
                    Collectors.joining(", ")) + "]");
            statePair.setDeferUpdatesOnLock(false);
        }

        statePair.applySeedColors(seedColors);
        reevaluateSystemTheme();
    }

    /**
     * Called when the theming style is changed.
     *
     * @param userId    The ID of the user updating the theme.
     * @param userStyle The {@link ThemeStyle} to be applied.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onStyleChange(int userId, @ThemeStyle.Type Integer userStyle) {
        ThemeStatePair statePair = getState(userId);

        if (statePair.areUpdatesDeferredOnLock()) {
            Slog.d(TAG, "User explicitly changed style, clearing deferral.");
            statePair.setDeferUpdatesOnLock(false);
        }
        statePair.applyStyle(userStyle);
        reevaluateSystemTheme();
    }

    /**
     * Called when the display contrast setting changes.
     *
     * @param userId The ID of the user updating the theme.
     * @param value  The new contrast value.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onContrastChange(int userId, float value) {
        ThemeStatePair statePair = getState(userId);

        if (statePair.areUpdatesDeferredOnLock()) {
            Slog.d(TAG, "User explicitly changed contrast, clearing deferral.");
            statePair.setDeferUpdatesOnLock(false);
        }
        statePair.applyContrast(value);
        reevaluateSystemTheme();
    }

    /**
     * Called when the setup process is completed for a user.
     *
     * @param userId The ID of the user who completed setup.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onFinishSetup(int userId) {
        ThemeStatePair statePair = getState(userId);

        if (statePair.areUpdatesDeferredOnLock()) {
            Slog.d(TAG, "User finished setup, clearing any boot-time deferrals.");
            statePair.setDeferUpdatesOnLock(false);
        }
        statePair.applySetupComplete();
        reevaluateSystemTheme();
    }

    /**
     * Called when a new profile is added for a user.
     *
     * @param userId  The ID of the user the profile belongs to.
     * @param profile The ID of the new profile.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onProfileAdd(int userId, int profile) {
        getState(userId).addProfile(profile);
        reevaluateSystemTheme();
    }

    /**
     * Called when the device locks or unlocks.
     *
     * @param isDeviceLocked {@code true} if the device is locked, {@code false} otherwise.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onLockStateChange(boolean isDeviceLocked) {
        if (!isDeviceLocked) return;

        for (ThemeStatePair statePair : getPairsSnapshot()) {
            if (statePair.areUpdatesDeferredOnLock()) {
                statePair.setDeferUpdatesOnLock(false);
                Slog.w(TAG, "Applying deferred wallpaper color change on user " + statePair.userId);
            }
        }
        reevaluateSystemTheme();
    }

    /**
     * Called when a user is loaded (e.g. at boot or when created).
     *
     * @param userId     The ID of the user loading.
     * @param isSetup    {@code true} if the user has completed setup, {@code false} otherwise.
     * @param seedColors The initial seed colors for the user's theme.
     * @param contrast   The initial contrast value for the user's theme.
     * @param style      The initial style for the user's theme.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserLoad(int userId, boolean isSetup, List<Integer> seedColors, float contrast,
            @ThemeStyle.Type Integer style) {
        synchronized (mLock) {
            Integer parentId = mEnvironment.parentOf(userId);

            // CASE 1: userId is a profile, not a full user
            if (parentId != null) {
                ThemeStatePair parentState = mThemeStates.get(parentId);
                if (parentState != null) {
                    Slog.d(TAG,
                            "Profile " + userId + " loaded, added to existing parent " + parentId);
                    parentState.addProfile(userId);
                } else {
                    Slog.d(TAG, "Profile " + userId + " loaded; waiting for parent " + parentId
                            + " to load.");
                }
            } else if (mThemeStates.contains(userId)) {
                // CASE 2: userId is an existing user
                Slog.w(TAG, "ThemeStatePair already exists for user " + userId);
            } else {
                // CASE 3: userId is a new user
                ThemeStatePair newState = new ThemeStatePair(userId, isSetup, seedColors, contrast,
                        style, mEnvironment);
                int[] profiles = Objects.requireNonNullElse(
                        mUserManager.getProfileIds(userId, false), new int[0]);

                for (int profileId : profiles) {
                    if (profileId != userId) {
                        Slog.d(TAG, "Full user " + userId + " found existing profile " + profileId);
                        newState.addProfile(profileId);
                    }
                }
                mThemeStates.put(userId, newState);
            }
        }
    }

    /**
     * Called when a user starts (logs in or unlocks their profile).
     *
     * @param userHandle The {@link UserHandle} of the user starting.
     * @param isSetup    {@code true} if the user has completed setup, {@code false} otherwise.
     * @param seedColors The initial seed colors for the user's theme.
     * @param contrast   The initial contrast value for the user's theme.
     * @param style      The initial style for the user's theme.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserStart(UserHandle userHandle, boolean isSetup, List<Integer> seedColors,
            float contrast, @ThemeStyle.Type Integer style) {
        // Ensure state is loaded. This is idempotent.
        onUserLoad(userHandle.getIdentifier(), isSetup, seedColors, contrast, style);
        reevaluateSystemTheme();
    }

    /**
     * Called when switching between users.
     *
     * @param from The ID of the previous user, or {@code null} if there was none.
     * @param to   The ID of the new user.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserSwitching(@Nullable Integer from, int to) {
        Slog.d(TAG, "User switching from " + from + " to " + to + ". Re-applying theme.");
        synchronized (mLock) {
            mCurrentUserId = to;
        }
        getState(to).forceUpdate();
        reevaluateSystemTheme();
    }

    /**
     * Checks if the applied overlays match the current theme settings for all users and forces
     * an update if necessary.
     *
     * @param isPaletteOutdated A boolean indicating the palette version is outdated and should be
     *                          recalculated.
     * @return {@code true} if an update was requested for at least one user; {@code false}
     * otherwise.
     */
    boolean evaluateAllUsers(boolean isPaletteOutdated) {
        boolean shouldEvaluate = false;

        for (ThemeStatePair statePair : getPairsSnapshot()) {
            boolean colorsMatch = mThemeOverlayHelper.isColorSchemeApplied(mContext,
                    statePair.userId, statePair.getDarkScheme(), statePair.getLightScheme());
            boolean overlayEnabled = mThemeOverlayHelper.isOverlayEnabled(statePair.userId);

            if (!colorsMatch || !overlayEnabled || isPaletteOutdated) {
                Slog.d(TAG, "Color palette does not match user " + statePair.userId
                        + " settings (match=" + colorsMatch + ", enabled=" + overlayEnabled
                        + ", outdated=" + isPaletteOutdated + "), requesting update.");
                statePair.forceUpdate();
                shouldEvaluate = true;
            } else {
                Slog.d(TAG, "Applied color palette for user " + statePair.userId
                        + " matches settings.");
            }
        }

        if (shouldEvaluate) {
            Slog.d(TAG, "One or more users have outdated color palettes; update requested.");
            reevaluateSystemTheme();
        }
        return shouldEvaluate;
    }

    @NonNull
    ThemeStatePair getState(int userId) {
        synchronized (mLock) {
            ThemeStatePair state = mThemeStates.get(userId);
            if (state == null) {
                throw new IllegalStateException("State not found for user " + userId);
            }
            return state;
        }
    }

    /**
     * Checks if the theme state for the given user exists.
     *
     * @param userId The ID of the user to check.
     * @return {@code true} if the state exists, {@code false} otherwise.
     */
    boolean hasState(int userId) {
        synchronized (mLock) {
            return mThemeStates.contains(userId);
        }
    }

    /**
     * Re-evaluates the current system theme for all users and updates overlays if necessary.
     * Performs the update asynchronously.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void reevaluateSystemTheme() {
        for (ThemeStatePair statePair : getPairsSnapshot()) {
            if (!statePair.shouldUpdate()) {
                continue;
            }

            ScheduledFuture<?> existingFuture = statePair.getFuture();
            if (existingFuture != null) {
                existingFuture.cancel(true);
                Slog.d(TAG, "Debouncing update for user " + statePair.userId);
            }

            ScheduledFuture<?> scheduled = mSchedulerExecutor.schedule(() -> {
                applyUpdate(statePair);
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);

            statePair.setFuture(scheduled);
        }
    }

    private void applyUpdate(ThemeStatePair statePair) {
        Slog.d(TAG, "Updating user " + statePair.userId);
        long beginT = System.currentTimeMillis();

        ThemeStatePair.OverlaySnapshot overlaySnapshot =
                statePair.commitAndGetOverlayData();

        if (overlaySnapshot == null) {
            Slog.d(TAG, "Snapshot aborted for user " + statePair.userId);
            statePair.clearTimer();
            return;
        }

        // TODO: b/477901630 (Move this color spec to MCU)
        ThemeStatePair.OverlaySnapshot effectiveSnapshot = overlaySnapshot;
        if (mEnvironment.getConfig().platform() == Platform.WATCH) {
            effectiveSnapshot = new ThemeStatePair.OverlaySnapshot(
                    overlaySnapshot.userId(),
                    overlaySnapshot.profiles(),
                    overlaySnapshot.darkScheme(),
                    overlaySnapshot.darkScheme(),
                    overlaySnapshot.contentChanged()
            );
        }

        int currentUserId;
        synchronized (mLock) {
            currentUserId = mCurrentUserId;
        }

        // Whether to update existing (register) overlays or just turn them on.
        // We use contentChanged (which now includes forced updates) to drive registration.
        // We also check if colors are already applied heuristic-wise as a fallback registration
        // trigger.
        boolean shouldRegister = overlaySnapshot.contentChanged()
                || !mThemeOverlayHelper.isOverlayRegistered(statePair.userId)
                || !mThemeOverlayHelper.isColorSchemeApplied(mContext, statePair.userId,
                statePair.getDarkScheme(), statePair.getLightScheme());

        mThemeOverlayHelper.applyCurrentStateOverlays(
                /*statePair     */ effectiveSnapshot,
                /*applyToSystem */ effectiveSnapshot.userId() == currentUserId,
                /*shouldRegister*/ shouldRegister);

        statePair.clearTimer();

        Slog.d(TAG,
                "Overlay application for user " + statePair.userId + " completed in " + (
                        System.currentTimeMillis() - beginT) + "ms");
    }

    private List<ThemeStatePair> getPairsSnapshot() {
        synchronized (mLock) {
            List<ThemeStatePair> snapshot = new ArrayList<>(mThemeStates.size());
            for (int i = 0; i < mThemeStates.size(); i++) {
                snapshot.add(mThemeStates.valueAt(i));
            }
            return snapshot;
        }
    }

    /**
     * Dumps the current state of the ThemeStateManager to the provided PrintWriter.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("In-Memory Theme States per User:");
            for (int i = 0; i < mThemeStates.size(); i++) {
                int userId = mThemeStates.keyAt(i);
                ThemeStatePair statePair = mThemeStates.valueAt(i);
                pw.println("  User " + userId + ":");
                statePair.dump(pw);
            }
        }
    }
}
