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

package com.android.server.pm.parsing.library;

import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.compat.CompatChanges;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.view.WindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.parsing.pkg.ParsedPackage;
import com.android.window.flags.Flags;

import java.util.Map;

/**
 * Updates a package to ensure that the WindowManager Extensions is included as an optional shared
 * library if the app has app compat flags such as {@link OVERRIDE_ENABLE_VIRTUAL_GAMEPAD}.
 *
 * @hide
 */
@VisibleForTesting
public class WindowManagerExtensionsUpdater extends PackageSharedLibraryUpdater {
    @VisibleForTesting
    static final String LIBRARY_NAME = "androidx.window.extensions";

    /** @return whether any feature flag is enabled that requires this updater. */
    public static boolean isFlagEnabled() {
        return Flags.virtualGamepadOverride();
    }

    @RequiresPermission(allOf = {android.Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
            android.Manifest.permission.LOG_COMPAT_CHANGE})
    @Override
    public void updatePackage(@NonNull ParsedPackage pkg, boolean isSystemApp,
            boolean isUpdatedSystemApp) {
        if (WindowManager.HAS_WINDOW_EXTENSIONS_ON_DEVICE && CompatChanges.isChangeEnabled(
                OVERRIDE_ENABLE_VIRTUAL_GAMEPAD, pkg.getPackageName(), UserHandle.CURRENT)
                && isVirtualGamepadOverrideAllowed(pkg)) {
            prefixRequiredLibrary(pkg, LIBRARY_NAME);
        }
    }

    private static boolean isVirtualGamepadOverrideAllowed(@NonNull ParsedPackage pkg) {
        final Map<String, PackageManager.Property> properties = pkg.getProperties();
        if (properties.containsKey(PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE)) {
            return properties.get(PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE).getBoolean();
        }
        return true;
    }
}
