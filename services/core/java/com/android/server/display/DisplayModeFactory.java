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

package com.android.server.display;

import static android.view.Display.Mode.INVALID_MODE_ID;

import static com.android.server.display.DisplayDeviceConfig.DEFAULT_LOW_REFRESH_RATE;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.server.display.LocalDisplayAdapter.DisplayModeRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayModeFactory {
    private static final String TAG = "DisplayModeFactory";

    /**
     * Used to generate globally unique display mode ids.
     */
    private static final AtomicInteger NEXT_DISPLAY_MODE_ID = new AtomicInteger(1);  // 0 = no mode.


    private static final float FLOAT_TOLERANCE = 0.01f;
    private static final float SYNTHETIC_MODE_REFRESH_RATE = DEFAULT_LOW_REFRESH_RATE;
    private static final float SYNTHETIC_MODE_HIGH_BOUNDARY =
            SYNTHETIC_MODE_REFRESH_RATE + FLOAT_TOLERANCE;

    /**
     * if one of the anisotropic mode dimensions differ less than this boundary from any isotropic
     * mode, no synthetic mode will be generated
     */
    private static final float SYNTHETIC_ANISOTROPY_CORRECTION_BOUNDARY = 0.05f;


    static Display.Mode createMode(int width, int height, float refreshRate) {
        return new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                INVALID_MODE_ID, INVALID_MODE_ID, 0,
                width, height, refreshRate, refreshRate,
                new float[0], new int[0]
        );
    }

    @SuppressLint("WrongConstant")
    static Display.Mode createMode(SurfaceControl.DisplayMode mode, float[] alternativeRefreshRates,
            boolean hasArrSupport, boolean sizeOverrideEnabled) {
        int flags = 0;
        if (hasArrSupport && mode.peakRefreshRate <= SYNTHETIC_MODE_HIGH_BOUNDARY) {
            flags |= Display.Mode.FLAG_ARR_RENDER_RATE;
        }

        if (sizeOverrideEnabled) {
            flags |= Display.Mode.FLAG_SIZE_OVERRIDE;
        }

        return new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                INVALID_MODE_ID, mode.id, flags,
                mode.width, mode.height, mode.peakRefreshRate, mode.vsyncRate,
                alternativeRefreshRates, mode.supportedHdrTypes
        );
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    static List<DisplayModeRecord> createArrSyntheticModes(List<DisplayModeRecord> records,
            boolean hasArrSupport) {
        if (!hasArrSupport) {
            return Collections.emptyList();
        }

        List<Display.Mode> modesToSkipForArrSyntheticMode = new ArrayList<>();
        for (DisplayModeRecord record : records) {
            // already have < 60Hz mode, don't need to add synthetic
            if ((record.mMode.getFlags() & Display.Mode.FLAG_ARR_RENDER_RATE) != 0) {
                modesToSkipForArrSyntheticMode.add(record.mMode);
            }
        }

        List<Display.Mode> modesForArrSyntheticMode = new ArrayList<>();
        for (DisplayModeRecord record : records) {
            if (!is60HzAchievable(record.mMode)) {
                continue;
            }
            // already have < 60Hz mode, don't need to add synthetic
            if ((record.mMode.getFlags() & Display.Mode.FLAG_ARR_RENDER_RATE) != 0) {
                continue;
            }
            // already added OR should be skipped
            boolean skipAdding = hasMatchingForArr(modesForArrSyntheticMode, record.mMode)
                    || hasMatchingForArr(modesToSkipForArrSyntheticMode, record.mMode);

            if (!skipAdding) {
                modesForArrSyntheticMode.add(record.mMode);
            }
        }

        List<DisplayModeRecord> syntheticModes = new ArrayList<>();
        for (Display.Mode mode : modesForArrSyntheticMode) {
            syntheticModes.add(new DisplayModeRecord(
                    new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                            mode.getModeId(), INVALID_MODE_ID, Display.Mode.FLAG_ARR_RENDER_RATE,
                            mode.getPhysicalWidth(), mode.getPhysicalHeight(),
                            SYNTHETIC_MODE_REFRESH_RATE, SYNTHETIC_MODE_REFRESH_RATE,
                            new float[0], mode.getSupportedHdrTypes()
                    )));
        }

        return syntheticModes;
    }

    /**
     * If the aspect ratio of the resolution of the display does not match the physical aspect
     * ratio of the display, then without this feature enabled, picture would appear stretched to
     * the user. This is because applications assume that they are rendered on square pixels
     * (meaning density of pixels in x and y directions are equal). This would result into circles
     * appearing as ellipses to the user.
     * 1. To compensate for non-square (anisotropic) pixels, this method will create synthetic modes
     * with more pixels for applications to render on, as if the pixels were square and occupied
     * the full display.
     * 2. SurfaceFlinger will squeeze this taller/wider surface into the available number of
     * physical pixels in the current display resolution.
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    static List<DisplayModeRecord> createAnisotropyCorrectedModes(List<DisplayModeRecord> records,
            SparseArray<SurfaceControl.DisplayMode> modeIdToSfMode, boolean sizeOverrideEnabled) {
        int modeFlag = Display.Mode.FLAG_ANISOTROPY_CORRECTION;

        if (sizeOverrideEnabled) {
            modeFlag |= Display.Mode.FLAG_SIZE_OVERRIDE;
        }

        // used for filtering anisotropic modes with size similar to isotropic
        List<Display.Mode> isotropicModes = new ArrayList<>();

        List<Display.Mode> tallAnisotropicModes = new ArrayList<>();
        List<Display.Mode> wideAnisotropicModes = new ArrayList<>();

        for (DisplayModeRecord record : records) {
            Display.Mode mode = record.mMode;
            SurfaceControl.DisplayMode sfMode = modeIdToSfMode.get(mode.getModeId());
            if (sfMode == null) {
                continue;
            }
            if (sfMode.xDpi <= 0 || sfMode.yDpi <= 0) {
                continue;
            }
            if (sfMode.xDpi > sfMode.yDpi * DisplayDevice.MAX_ANISOTROPY) { // "tall" pixels
                tallAnisotropicModes.add(mode);
            } else if (sfMode.yDpi > sfMode.xDpi * DisplayDevice.MAX_ANISOTROPY) { // "wide" pixels
                wideAnisotropicModes.add(mode);
            } else {
                isotropicModes.add(mode);
            }
        }

        List<DisplayModeRecord> syntheticModes = new ArrayList<>();
        for (Display.Mode mode : tallAnisotropicModes) {
            Display.Mode correspondingIsotropic = findCorrespondingIsotropic(mode, isotropicModes);
            if (correspondingIsotropic == null) {
                SurfaceControl.DisplayMode sfMode = modeIdToSfMode.get(mode.getModeId());
                // scale up height in "logical" pixels
                int correctedHeight =
                        (int) (mode.getPhysicalHeight() * sfMode.xDpi / sfMode.yDpi + 0.5);
                syntheticModes.add(new DisplayModeRecord(
                        new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                                mode.getModeId(), INVALID_MODE_ID, modeFlag,
                                mode.getPhysicalWidth(), correctedHeight,
                                mode.getRefreshRate(), mode.getVsyncRate(),
                                mode.getAlternativeRefreshRates(), mode.getSupportedHdrTypes()
                        )));
                Slog.d(TAG, "anisotropic mode generated for tall mode with id=" + mode.getModeId());
            } else {
                Slog.d(TAG,
                        "anisotropic mode not generated for tall mode with id=" + mode.getModeId()
                                + ", corresponding isotropic mode found modeId="
                                + correspondingIsotropic.getModeId());
            }
        }

        for (Display.Mode mode : wideAnisotropicModes) {
            Display.Mode correspondingIsotropic = findCorrespondingIsotropic(mode, isotropicModes);
            if (correspondingIsotropic == null) {
                SurfaceControl.DisplayMode sfMode = modeIdToSfMode.get(mode.getModeId());
                // scale up width in "logical" pixels
                int correctedWidth =
                        (int) (mode.getPhysicalWidth() * sfMode.yDpi / sfMode.xDpi + 0.5);
                syntheticModes.add(new DisplayModeRecord(
                        new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                                mode.getModeId(), INVALID_MODE_ID, modeFlag,
                                correctedWidth, mode.getPhysicalHeight(),
                                mode.getRefreshRate(), mode.getVsyncRate(),
                                mode.getAlternativeRefreshRates(), mode.getSupportedHdrTypes()
                        )));
                Slog.d(TAG, "anisotropic mode generated for wide mode with id=" + mode.getModeId());
            } else {
                Slog.d(TAG,
                        "anisotropic mode not generated for wide mode with id=" + mode.getModeId()
                                + ", corresponding isotropic mode found modeId="
                                + correspondingIsotropic.getModeId());
            }
        }
        return syntheticModes;
    }

    @Nullable
    private static Display.Mode findCorrespondingIsotropic(
            Display.Mode anisotropicMode, List<Display.Mode> isotropicModes) {
        int widthToCheck = anisotropicMode.getPhysicalWidth();
        int heightToCheck = anisotropicMode.getPhysicalHeight();
        for (Display.Mode mode : isotropicModes) {
            if ((float) Math.abs(widthToCheck - mode.getPhysicalWidth()) / widthToCheck
                    < SYNTHETIC_ANISOTROPY_CORRECTION_BOUNDARY || (float) Math.abs(
                    heightToCheck - mode.getPhysicalHeight()) / heightToCheck
                    < SYNTHETIC_ANISOTROPY_CORRECTION_BOUNDARY) {
                return mode;
            }
        }
        return null;
    }

    private static boolean hasMatchingForArr(List<Display.Mode> modes, Display.Mode modeToMatch) {
        for (Display.Mode mode : modes) {
            if (matchingForSyntheticArr(modeToMatch, mode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is60HzAchievable(Display.Mode mode) {
        float divisor = mode.getVsyncRate() / SYNTHETIC_MODE_REFRESH_RATE;
        return Math.abs(divisor - Math.round(divisor)) < FLOAT_TOLERANCE;
    }

    private static boolean matchingForSyntheticArr(Display.Mode mode1, Display.Mode mode2) {
        return mode1.getPhysicalWidth() == mode2.getPhysicalWidth()
                && mode1.getPhysicalHeight() == mode2.getPhysicalHeight()
                && Arrays.equals(mode1.getSupportedHdrTypes(), mode2.getSupportedHdrTypes());
    }
}
