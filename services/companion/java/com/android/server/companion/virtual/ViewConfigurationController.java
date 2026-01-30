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

package com.android.server.companion.virtual;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.virtual.ViewConfigurationParams;
import android.content.ContentResolver;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Binder;
import android.provider.Settings;
import android.util.Slog;
import android.util.TypedValue;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/**
 * Controls the application of {@link ViewConfigurationParams} for a virtual device.
 */
public class ViewConfigurationController {

    private static final String TAG = "ViewConfigurationController";
    private static final String FRAMEWORK_PACKAGE_NAME = "android";

    private static final String TAP_TIMEOUT_RESOURCE_NAME = "integer/config_tapTimeoutMillis";
    private static final String DOUBLE_TAP_TIMEOUT_RESOURCE_NAME =
            "integer/config_doubleTapTimeoutMillis";
    private static final String DOUBLE_TAP_MIN_TIME_RESOURCE_NAME =
            "integer/config_doubleTapMinTimeMillis";
    private static final String TOUCH_SLOP_RESOURCE_NAME =
            "dimen/config_viewConfigurationTouchSlop";
    private static final String MIN_FLING_VELOCITY_RESOURCE_NAME =
            "dimen/config_viewMinFlingVelocity";
    private static final String MAX_FLING_VELOCITY_RESOURCE_NAME =
            "dimen/config_viewMaxFlingVelocity";
    private static final String SCROLL_FRICTION_RESOURCE_NAME = "dimen/config_scrollFriction";

    private final Context mContext;
    private final OverlayManager mOverlayManager;
    private final SettingsWriter mSettingsWriter;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private OverlayIdentifier mOverlayIdentifier = null;

    ViewConfigurationController(@NonNull Context context) {
        this(context, Settings.Secure::putInt);
    }

    @VisibleForTesting
    ViewConfigurationController(@NonNull Context context, @NonNull SettingsWriter settingsWriter) {
        mContext = Objects.requireNonNull(context);
        mOverlayManager = context.getSystemService(OverlayManager.class);
        mSettingsWriter = settingsWriter;
    }

    /**
     * Applies given {@link ViewConfigurationParams} for the given {@code deviceId}.
     */
    public void applyViewConfigurationParams(int deviceId,
            @Nullable ViewConfigurationParams viewConfigurationParams) {
        if (viewConfigurationParams == null) {
            return;
        }

        applyResourceOverlays(deviceId, viewConfigurationParams);
        applySettings(deviceId, viewConfigurationParams);
    }

    /**
     * Clears the applied {@link ViewConfigurationParams}.
     */
    public void close() {
        OverlayManagerTransaction transaction;
        synchronized (mLock) {
            if (mOverlayIdentifier == null) {
                return;
            }

            transaction = new OverlayManagerTransaction.Builder().unregisterFabricatedOverlay(
                            mOverlayIdentifier).build();
        }

        Binder.withCleanCallingIdentity(() -> mOverlayManager.commit(transaction));
    }

    private void applyResourceOverlays(int deviceId,
            @NonNull ViewConfigurationParams viewConfigurationParams) {
        FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                FRAMEWORK_PACKAGE_NAME /* owningPackage */,
                "vdOverlay" + deviceId /* overlayName */,
                FRAMEWORK_PACKAGE_NAME /* targetPackage */)
                .build();
        OverlayIdentifier overlayIdentifier = overlay.getIdentifier();
        boolean change = false;
        change |= setResourcePixelValue(overlay, TOUCH_SLOP_RESOURCE_NAME,
                viewConfigurationParams.getTouchSlopPixels());
        change |= setResourcePixelValue(overlay, MIN_FLING_VELOCITY_RESOURCE_NAME,
                viewConfigurationParams.getMinimumFlingVelocityPixelsPerSecond());
        change |= setResourcePixelValue(overlay, MAX_FLING_VELOCITY_RESOURCE_NAME,
                viewConfigurationParams.getMaximumFlingVelocityPixelsPerSecond());
        change |= setResourceFloatValue(overlay, SCROLL_FRICTION_RESOURCE_NAME,
                viewConfigurationParams.getScrollFriction());
        change |= setResourceIntValue(overlay, TAP_TIMEOUT_RESOURCE_NAME,
                (int) viewConfigurationParams.getTapTimeoutDuration().toMillis());
        change |= setResourceIntValue(overlay, DOUBLE_TAP_TIMEOUT_RESOURCE_NAME,
                (int) viewConfigurationParams.getDoubleTapTimeoutDuration().toMillis());
        change |= setResourceIntValue(overlay, DOUBLE_TAP_MIN_TIME_RESOURCE_NAME,
                (int) viewConfigurationParams.getDoubleTapMinTimeDuration().toMillis());
        if (!change) {
            return;
        }

        int callingUserId = Binder.getCallingUserHandle().getIdentifier();
        Binder.withCleanCallingIdentity(() -> {
            mOverlayManager.commit(
                    new OverlayManagerTransaction.Builder()
                            .registerFabricatedOverlay(overlay)
                            .setEnabled(overlayIdentifier, true /* enable */, callingUserId,
                                    List.of(new OverlayConstraint(OverlayConstraint.TYPE_DEVICE_ID,
                                            deviceId)))
                            .build());
            synchronized (mLock) {
                mOverlayIdentifier = overlayIdentifier;
            }
        });
    }

    private void applySettings(int deviceId,
            @NonNull ViewConfigurationParams viewConfigurationParams) {
        int longPressTimeout =
                (int) viewConfigurationParams.getLongPressTimeoutDuration().toMillis();
        int multiPressTimeout =
                (int) viewConfigurationParams.getMultiPressTimeoutDuration().toMillis();
        boolean isLongPressTimeoutInvalid = isInvalid(longPressTimeout);
        boolean isMultiPressTimeoutInvalid = isInvalid(multiPressTimeout);
        if (isLongPressTimeoutInvalid && isMultiPressTimeoutInvalid) {
            return;
        }

        Context deviceContext = mContext.createDeviceContext(deviceId);
        ContentResolver contentResolver = deviceContext.getContentResolver();
        Binder.withCleanCallingIdentity(() -> {
            if (!isLongPressTimeoutInvalid) {
                mSettingsWriter.writeSettings(contentResolver, Settings.Secure.LONG_PRESS_TIMEOUT,
                        longPressTimeout);
            }
            if (!isMultiPressTimeoutInvalid) {
                mSettingsWriter.writeSettings(contentResolver, Settings.Secure.MULTI_PRESS_TIMEOUT,
                        multiPressTimeout);
            }
        });
    }

    private static boolean setResourcePixelValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, int value) {
        if (isInvalid(value)) {
            return false;
        }

        if (!android.content.res.Flags.dimensionFrro()) {
            Slog.e(TAG, "Dimension resource overlay is not supported");
            return false;
        }

        overlay.setResourceValue(resourceName, (float) value, TypedValue.COMPLEX_UNIT_PX,
                null /* configuration */);
        return true;
    }

    private static boolean setResourceFloatValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, float value) {
        if (isInvalid(value)) {
            return false;
        }

        if (!android.content.res.Flags.dimensionFrro()) {
            Slog.e(TAG, "Dimension resource overlay is not supported");
            return false;
        }

        overlay.setResourceValue(resourceName, value, null /* configuration */);
        return true;
    }

    private static boolean setResourceIntValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, int value) {
        if (isInvalid(value)) {
            return false;
        }

        overlay.setResourceValue(resourceName, TypedValue.TYPE_INT_DEC, value,
                null /* configuration */);
        return true;
    }

    private static boolean isInvalid(float value) {
        return value == ViewConfigurationParams.INVALID_VALUE;
    }

    @VisibleForTesting
    interface SettingsWriter {
        void writeSettings(@NonNull ContentResolver contentResolver, @NonNull String key,
                int value);
    }
}
