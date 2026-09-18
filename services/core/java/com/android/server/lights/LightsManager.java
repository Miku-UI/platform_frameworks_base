/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.lights;

import android.annotation.Nullable;
import android.hardware.light.LightType;

public abstract class LightsManager {
    public static final int LIGHT_ID_BACKLIGHT = LightType.BACKLIGHT;
    public static final int LIGHT_ID_KEYBOARD = LightType.KEYBOARD;
    public static final int LIGHT_ID_BUTTONS = LightType.BUTTONS;
    public static final int LIGHT_ID_BATTERY = LightType.BATTERY;
    public static final int LIGHT_ID_NOTIFICATIONS = LightType.NOTIFICATIONS;
    public static final int LIGHT_ID_ATTENTION = LightType.ATTENTION;
    public static final int LIGHT_ID_BLUETOOTH = LightType.BLUETOOTH;
    public static final int LIGHT_ID_WIFI = LightType.WIFI;
    public static final int LIGHT_ID_COUNT = 8;
    /*
     * The list of lights above is kept for backwards compatibility with HIDL
     * based HALs. Specifically, COUNT should remain constant from now on since
     * it only plays a role in HIDL.
     * <p>
     * New system lights can be added below without updating COUNT. On AIDL HALs
     * the system accounts for gaps and interleaved system and non-system light
     * types.
     */
    public static final int LIGHT_ID_PRIORITY_NOTIFICATIONS = LightType.PRIORITY_NOTIFICATIONS;

    /**
     * Returns the logical light with the given type, if it exists, or null.
     */
    @Nullable
    public abstract LogicalLight getLight(int id);

    /**
     * Set overall lights muted/unmuted state, when it is necessary to limit light emission to
     * avoid interfering with use cases such as video recording.
     * <p>
     * Critical lights like backlight, keyboard or buttons won't be muted but user and the other
     * system lights will be turned off until the muted state is set to false.
     */
    public abstract void setMutedState(boolean muted);

}
