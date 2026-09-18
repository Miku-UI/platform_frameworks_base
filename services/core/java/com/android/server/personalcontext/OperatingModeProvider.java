/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.personalcontext;

import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link OperatingModeProvider} is responsible for storing the operating state for a given user.
 *
 * @hide
 */
public class OperatingModeProvider {

    public static final int OPERATING_PROPERTY_FLAG_UNKNOWN = 0;
    public static final int OPERATING_PROPERTY_FLAG_ENFORCE_ALLOW_LIST = 1;
    public static final int OPERATING_PROPERTY_FLAG_ENFORCE_PERMISSIONS = 1 << 1;
    public static final int OPERATING_PROPERTY_FLAG_TEST_PACKAGES_ONLY = 1 << 2;
    public static final int OPERATING_PROPERTY_FLAG_BUILT_IN_COMPONENTS = 1 << 3;
    public static final int OPERATING_PROPERTY_FLAG_ENFORCE_PCC = 1 << 4;
    public static final int OPERATING_PROPERTY_FLAG_ENFORCE_BIND_PERMISSIONS = 1 << 5;

    @IntDef(flag = true, value = {
            OPERATING_PROPERTY_FLAG_UNKNOWN,
            OPERATING_PROPERTY_FLAG_ENFORCE_ALLOW_LIST,
            OPERATING_PROPERTY_FLAG_ENFORCE_PERMISSIONS,
            OPERATING_PROPERTY_FLAG_TEST_PACKAGES_ONLY,
            OPERATING_PROPERTY_FLAG_BUILT_IN_COMPONENTS,
            OPERATING_PROPERTY_FLAG_ENFORCE_PCC,
            OPERATING_PROPERTY_FLAG_ENFORCE_BIND_PERMISSIONS,
    })
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface OperatingPropertyFlag {
    }

    /**
     * Filters flags based on the operating mode.
     */
    public @AccessController.Access int filterAccessFlags(@AccessController.Access int flags) {
        if (!hasProperties(OperatingModeProvider.OPERATING_PROPERTY_FLAG_ENFORCE_ALLOW_LIST)) {
            flags &= ~AccessController.ACCESS_ALL_ALLOWLISTS;
        }

        if (!hasProperties(OperatingModeProvider.OPERATING_PROPERTY_FLAG_ENFORCE_PERMISSIONS)) {
            flags &= ~AccessController.ACCESS_ALL_PERMISSIONS;
        }

        if (!hasProperties(OperatingModeProvider.OPERATING_PROPERTY_FLAG_ENFORCE_PCC)) {
            flags &= ~AccessController.ACCESS_PCC_OR_AUTO_COMPANION_ROLE;
            flags &= ~AccessController.ACCESS_PCC_OR_TRUSTED_PACKAGE;
        }

        if (!hasProperties(OPERATING_PROPERTY_FLAG_ENFORCE_BIND_PERMISSIONS)) {
            flags &= ~AccessController.ACCESS_BIND_CONTEXT_PERMISSION;
        }

        return flags;
    }
    private static @OperatingPropertyFlag int getFlagsEnabledForMode(
            @PersonalContextManager.OperatingMode int mode) {
        final @AccessController.Access int testAccessFlags =
                OPERATING_PROPERTY_FLAG_TEST_PACKAGES_ONLY;

        final @AccessController.Access int defaultAccessFlags =
                (Flags.enforcePersonalContextAllowlistAccessControl()
                        ? OPERATING_PROPERTY_FLAG_ENFORCE_ALLOW_LIST : 0)
                        | (Flags.enforcePersonalContextPermissions()
                        ? OPERATING_PROPERTY_FLAG_ENFORCE_PERMISSIONS : 0)
                        | (Flags
                        .enforcePersonalContextPccAccessControl()
                        ? OPERATING_PROPERTY_FLAG_ENFORCE_PCC : 0)
                        | OPERATING_PROPERTY_FLAG_BUILT_IN_COMPONENTS
                        | OPERATING_PROPERTY_FLAG_ENFORCE_BIND_PERMISSIONS;

        return switch (mode) {
            case PersonalContextManager.OPERATING_MODE_TEST ->
                    testAccessFlags;
            case PersonalContextManager.OPERATING_MODE_DEFAULT ->
                    defaultAccessFlags;
            default ->
                    defaultAccessFlags;
        };
    }

    private @PersonalContextManager.OperatingMode int mCurrentMode;

    /**
     * Default constructor.
     */
    public OperatingModeProvider() {
        this(PersonalContextManager.OPERATING_MODE_DEFAULT);
    }

    /**
     * Constructor allowing initial mode specification.
     * @param mode The {@link PersonalContextManager.OperatingMode} that the provider should start
     *             with.
     */
    public OperatingModeProvider(@PersonalContextManager.OperatingMode int mode) {
        mCurrentMode = mode;
    }

    /**
     * Sets the current mode for a given user.
     */
    public void setMode(@PersonalContextManager.OperatingMode int mode) {
        mCurrentMode = mode;
    }

    /**
     * Returns {@code true} if the specified properties are present in this mode. {@code false}
     * otherwise.
     */
    public boolean hasProperties(@OperatingPropertyFlag int propertyFlags) {
        return hasProperties(mCurrentMode, propertyFlags);
    }

    private static boolean hasProperties(int mode, @OperatingPropertyFlag int propertyFlags) {
        return propertyFlags == (propertyFlags & getFlagsEnabledForMode(mode));
    }

    /**
     * Returns properties with those not present filetered out.
     */
    public @OperatingPropertyFlag int filterProperties(@OperatingPropertyFlag int propertyFlags) {
        return propertyFlags & getFlagsEnabledForMode(mCurrentMode);
    }

    @Override
    public String toString() {
        return "OperatingModeProvider[mode=" + mCurrentMode + " Flags="
                + getFlagsEnabledForMode(mCurrentMode) + "]";
    }
}
