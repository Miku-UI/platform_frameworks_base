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

import static android.util.TypedValue.TYPE_INT_COLOR_ARGB8;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManagerTransaction;
import android.content.res.Resources;
import android.os.UserHandle;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.monet.DynamicColors;

import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * A utility class responsible for creating and applying color-based theme overlays.
 * <p>
 * This class encapsulates the logic for generating {@link FabricatedOverlay} instances
 * from a {@link ColorScheme} and committing them to the {@link OverlayManagerInternal}.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeOverlayHelper {
    private static final String TAG = "ThemeOverlayHelper";
    private static final String ANDROID_PACKAGE = "android";

    private static final String OVERLAY_NAME_DYNAMIC = "dynamic";

    private final OverlayManagerInternal mOverlayManager;

    ThemeOverlayHelper() {
        mOverlayManager = LocalServices.getService(OverlayManagerInternal.class);
    }

    /**
     * Applies color overlays for a given user based on their current theme state.
     *
     * @param snapshot       The snapshot containing all necessary user, profile, and color info.
     * @param applyToSystem  Whether to apply overlays to the system user as well.
     * @param shouldRegister Whether to register the overlays (true) or just enable them (false).
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void applyCurrentStateOverlays(ThemeStatePair.OverlaySnapshot snapshot,
            boolean applyToSystem, boolean shouldRegister) throws CancellationException {
        if (shouldRegister || areOverlaysMissing(snapshot.userId())) {
            registerAndEnableOverlays(snapshot, applyToSystem);
        } else if (applyToSystem) {
            enableOverlaysOnly(snapshot);
        }
    }

    private boolean areOverlaysMissing(int userId) {
        final OverlayIdentifier identifier = new OverlayIdentifier(ANDROID_PACKAGE,
                OVERLAY_NAME_DYNAMIC + "_" + userId);
        try {
            if (mOverlayManager.getOverlayInfo(identifier, UserHandle.of(userId)) == null) {
                return true;
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to check if overlay exists: " + identifier, e);
            return true;
        }

        return false;
    }

    /**
     * Checks if the dynamic overlay for the given user is correctly enabled in OMS.
     *
     * @param userId The user ID to check.
     * @return {@code true} if the overlay is registered and enabled for the user.
     */
    public boolean isOverlayEnabled(int userId) {
        final OverlayIdentifier identifier = new OverlayIdentifier(ANDROID_PACKAGE,
                OVERLAY_NAME_DYNAMIC + "_" + userId);
        try {
            OverlayInfo info = mOverlayManager.getOverlayInfo(identifier, UserHandle.of(userId));
            return info != null && info.isEnabled();
        } catch (Exception e) {
            Slog.w(TAG, "Failed to check if overlay is enabled: " + identifier, e);
            return false;
        }
    }

    /**
     * Checks if the given ColorSchemes are correctly applied to the user's resources.
     * <p>
     * Note: This is a heuristic check and does not verify every single color. It checks a
     * representative subset of colors to determine if the ColorScheme is generally applied.
     *
     * @param context     The system context.
     * @param userId      The user ID to check.
     * @param darkScheme  The expected dark color scheme.
     * @param lightScheme The expected light color scheme.
     * @return {@code true} if the colors match the expected schemes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean isColorSchemeApplied(Context context, int userId, ColorScheme darkScheme,
            ColorScheme lightScheme) {
        Resources res = context.createContextAsUser(UserHandle.of(userId), 0).getResources();

        try {
            return res.getColor(R.color.system_accent1_500_dark)
                    == darkScheme.getAccent1().getS500()
                    && res.getColor(R.color.system_accent1_500_light)
                    == lightScheme.getAccent1().getS500()

                    && res.getColor(com.android.internal.R.color.system_accent2_500_dark)
                    == darkScheme.getAccent2().getS500()
                    && res.getColor(R.color.system_accent2_500_light)
                    == lightScheme.getAccent2().getS500()

                    && res.getColor(com.android.internal.R.color.system_accent3_500_dark)
                    == darkScheme.getAccent3().getS500()
                    && res.getColor(R.color.system_accent3_500_light)
                    == lightScheme.getAccent3().getS500()

                    && res.getColor(com.android.internal.R.color.system_neutral1_500_dark)
                    == darkScheme.getNeutral1().getS500()
                    && res.getColor(R.color.system_neutral1_500_light)
                    == lightScheme.getNeutral1().getS500()

                    && res.getColor(com.android.internal.R.color.system_neutral2_500_dark)
                    == darkScheme.getNeutral2().getS500()
                    && res.getColor(R.color.system_neutral2_500_light)
                    == lightScheme.getNeutral2().getS500()

                    && res.getColor(android.R.color.system_outline_variant_dark)
                    == darkScheme.getMaterialScheme().getOutlineVariant()
                    && res.getColor(android.R.color.system_outline_variant_light)
                    == lightScheme.getMaterialScheme().getOutlineVariant()

                    && res.getColor(android.R.color.system_primary_container_dark)
                    == darkScheme.getMaterialScheme().getPrimaryContainer()
                    && res.getColor(android.R.color.system_primary_container_light)
                    == lightScheme.getMaterialScheme().getPrimaryContainer();
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the dynamic overlay for the given user is registered.
     *
     * @param userId The user ID to check.
     * @return {@code true} if the overlay is registered, {@code false} otherwise.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean isOverlayRegistered(int userId) {
        OverlayIdentifier identifier = new OverlayIdentifier(ANDROID_PACKAGE,
                OVERLAY_NAME_DYNAMIC + "_" + userId);
        return mOverlayManager.getOverlayInfo(identifier, UserHandle.of(userId)) != null;
    }

    /**
     * The "Light" path: Only enables existing overlays.
     * Avoids expensive color calculation and object allocation.
     */
    private void enableOverlaysOnly(ThemeStatePair.OverlaySnapshot snapshot) {
        final int userId = snapshot.userId();

        Slog.d(TAG, "Enabling existing overlays for user " + userId);
        if (userId != UserHandle.SYSTEM.getIdentifier()) {
            Slog.d(TAG, "Enabling existing overlays for System User");
        }

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();

        final OverlayIdentifier identifier = new OverlayIdentifier(ANDROID_PACKAGE,
                OVERLAY_NAME_DYNAMIC + "_" + userId);
        addToTransaction(transaction, identifier, userId, /* applyToSystem */ true,
                snapshot.profiles());
        checkCancellation();

        commitTransaction(transaction);
    }

    /**
     * The "Heavy" path: Creates, Registers, and Enables overlays.
     */
    private void registerAndEnableOverlays(ThemeStatePair.OverlaySnapshot snapshot,
            boolean applyToSystem) {
        final int userId = snapshot.userId();
        final FabricatedOverlay overlay = createDynamicOverlay(snapshot.lightScheme(),
                snapshot.darkScheme(), userId);

        Slog.d(TAG, "Registering and Enabling overlay " + OVERLAY_NAME_DYNAMIC + " for user "
                + userId);

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();

        transaction.registerFabricatedOverlay(overlay);
        addToTransaction(transaction, overlay.getIdentifier(), userId, applyToSystem,
                snapshot.profiles());
        checkCancellation();

        commitTransaction(transaction);
    }

    private void addToTransaction(OverlayManagerTransaction.Builder transaction,
            OverlayIdentifier identifier, int userId, boolean applyToSystem,
            Set<Integer> profileIds) {
        transaction.setEnabled(identifier, true, userId);

        // All generated color overlays must also be applied to the system user for SystemUI.
        if (applyToSystem && userId != UserHandle.SYSTEM.getIdentifier()) {
            transaction.setEnabled(identifier, true, UserHandle.SYSTEM.getIdentifier());
        }

        // And to all associated managed profiles.
        for (int profileId : profileIds) {
            transaction.setEnabled(identifier, true, profileId);
        }
    }

    private boolean commitTransaction(OverlayManagerTransaction.Builder transaction) {
        try {
            mOverlayManager.commit(transaction.build());
            return true;
        } catch (SecurityException | IllegalStateException e) {
            Slog.w(TAG, "Could not commit overlays to OverlayManager");
            return false;
        }
    }

    /**
     * Cleans up legacy overlays from previous controllers.
     * This ensures we don't have duplicate or orphaned overlays persisting.
     *
     * @param legacyOverlays A list of legacy overlay identifiers in the format
     *                       "packageName:overlayName".
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void cleanupLegacyOverlays(List<String> legacyOverlays) {
        if (legacyOverlays == null || legacyOverlays.isEmpty()) {
            return;
        }

        final OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder();
        boolean hasRemovals = false;

        for (String overlay : legacyOverlays) {
            String[] split = overlay.split("\\|");
            if (split.length != 2) {
                Slog.w(TAG, "Invalid legacy overlay format: " + overlay);
                continue;
            }

            OverlayIdentifier identifier = new OverlayIdentifier(split[0], split[1]);
            if (mOverlayManager.getOverlayInfo(identifier, UserHandle.SYSTEM) != null) {
                Slog.d(TAG, "Cleaning up legacy overlay: " + overlay);
                transaction.unregisterFabricatedOverlay(identifier);
                hasRemovals = true;
            }
        }

        if (!hasRemovals) {
            return;
        }

        try {
            mOverlayManager.commit(transaction.build());
        } catch (SecurityException | IllegalStateException e) {
            Slog.w(TAG, "Failed to cleanup legacy overlays (this is likely harmless): " + e);
        }
    }


    /**
     * Creates a fabricated overlay for dynamic colors.
     *
     * @param lightColorScheme The color scheme for light theme.
     * @param darkColorScheme  The color scheme for dark theme.
     * @return A fabricated overlay containing dynamic colors.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public FabricatedOverlay createDynamicOverlay(ColorScheme lightColorScheme,
            ColorScheme darkColorScheme, int userId) {
        FabricatedOverlay overlay = new FabricatedOverlay.Builder(ANDROID_PACKAGE,
                OVERLAY_NAME_DYNAMIC + "_" + userId, ANDROID_PACKAGE).build();

        // Neutral palette
        assignColorsToOverlay(overlay, DynamicColors.getAllNeutralPalette(),
                lightColorScheme, darkColorScheme);

        // Accent palette
        assignColorsToOverlay(overlay, DynamicColors.getAllAccentPalette(),
                lightColorScheme, darkColorScheme);

        //Themed Colors
        assignColorsToOverlay(overlay, DynamicColors.getAllDynamicColorsMapped(),
                lightColorScheme, darkColorScheme);

        //Fixed Colors
        assignColorsToOverlay(overlay, DynamicColors.getFixedColorsMapped(),
                lightColorScheme, darkColorScheme);

        //Custom Colors
        assignColorsToOverlay(overlay, DynamicColors.getCustomColorsMapped(),
                lightColorScheme, darkColorScheme);

        return overlay;
    }

    private void assignColorsToOverlay(FabricatedOverlay overlay,
            List<Pair<String, DynamicColor>> colors, ColorScheme lightColorScheme,
            ColorScheme darkColorScheme) {
        for (Pair<String, DynamicColor> p : colors) {
            String prefix = "android:color/system_" + p.first;
            overlay.setResourceValue(prefix + "_light", TYPE_INT_COLOR_ARGB8,
                    p.second.getArgb(lightColorScheme.getMaterialScheme()), null);
            overlay.setResourceValue(prefix + "_dark", TYPE_INT_COLOR_ARGB8,
                    p.second.getArgb(darkColorScheme.getMaterialScheme()), null);
        }
    }

    private void checkCancellation() throws CancellationException {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Operation cancelled");
        }
    }
}
