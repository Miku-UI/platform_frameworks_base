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

import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_ASK;
import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP;
import static android.os.UserHandle.USER_CURRENT;

import android.content.Context;
import android.hardware.display.DisplayManager.ExternalDisplayConnection;
import android.provider.Settings;
import android.util.Log;

import java.util.function.BooleanSupplier;

/** Policy class for managing the setting of mirroring the built-in display. */
public class SecondaryDisplayPolicy {

    private static final String TAG = "SecondaryDisplayPolicy";

    private final Context mContext;
    private final BooleanSupplier mIsDesktopModeSupportedSupplier;

    public SecondaryDisplayPolicy(Context context, BooleanSupplier isDesktopModeSupportedSupplier) {
        mContext = context;
        mIsDesktopModeSupportedSupplier = isDesktopModeSupportedSupplier;
    }

    /**
     * Forces the {@link Settings.Secure#MIRROR_BUILT_IN_DISPLAY} setting to be enabled if desktop
     * mode is not supported.
     *
     * @return {@code true} if the mirroring setting was forced enabled, {@code false} otherwise.
     */
    public boolean forceEnableMirrorBuiltInDisplaySettingIfNeeded() {
        if (isMirrorBuiltInDisplaySettingDisabled()
                && shouldForceEnableMirrorBuiltInDisplaySetting()) {
            Log.d(TAG, "Force enable mirroring");
            // If someone changed the setting to disable mirroring and enable desktop mode, even
            // though it is not supported, let's force the mirroring setting to be enabled.
            enableMirrorBuiltInDisplaySetting();
            return true;
        }
        return false;
    }

    /**
     * Returns the connection preference, adjusting it if desktop mode is not supported but the
     * preference is set to desktop.
     *
     * @param persistedConnectionPreference The currently persisted connection preference.
     * @return The policy-aware connection preference.
     */
    @ExternalDisplayConnection
    public int getPolicyAwareConnectionPreference(
            @ExternalDisplayConnection int persistedConnectionPreference) {
        if (persistedConnectionPreference == EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP
                && !mIsDesktopModeSupportedSupplier.getAsBoolean()) {
            Log.d(TAG, "Desktop mode not supported, resetting connection preference");
            // Desktop mode was previously supported, and the connected display had the
            // connection preference set to DESKTOP.
            // Desktop is no longer supported, therefore resetting the preference to the
            // default option (ask).
            return EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_ASK;
        }
        return persistedConnectionPreference;
    }

    private boolean isMirrorBuiltInDisplaySettingDisabled() {
        return Settings.Secure.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.MIRROR_BUILT_IN_DISPLAY,
                        /* def= */ 0,
                        USER_CURRENT)
                == 0;
    }

    private boolean shouldForceEnableMirrorBuiltInDisplaySetting() {
        if (mIsDesktopModeSupportedSupplier.getAsBoolean()) {
            // Desktop Mode supported. We don't need to force enable the mirroring setting.
            return false;
        }
        // Desktop Mode is not supported. We should force enable the mirroring setting if for some
        // reason it is enabled.
        // One reason could be that desktop mode was previously supported, and now it is not.
        return isMirrorBuiltInDisplaySettingDisabled();
    }

    private void enableMirrorBuiltInDisplaySetting() {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.MIRROR_BUILT_IN_DISPLAY,
                /* value= */ 1,
                USER_CURRENT);
    }
}
