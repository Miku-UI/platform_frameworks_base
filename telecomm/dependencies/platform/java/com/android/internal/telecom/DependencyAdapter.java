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

package com.android.internal.telecom;

import android.content.Context;
import android.telecom.TelecomFrameworkInitializer;
import android.telecom.TelecomManager;

/**
 * The DependencyAdapter that is included when Telecom is still part of the platform.
 * @hide
 */
public class DependencyAdapter {

    public static boolean isMainlineBuildFlagEnabled() {
        return false;
    }

    /** Use the @hide constructor to create the TelecomManager */
    public static TelecomManager createTelecomManager(Context context) {
        return new TelecomManager(context);
    }

    /** This method should not be called if we are not building Telecom as a mainline module. */
    public static void registerServiceWrapper() {
        TelecomFrameworkInitializer.registerServiceWrapper();
    }
}
