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
package android.app;

import android.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

public class UiAutomation_ravenwood {
    private static Set<String> sAdoptedPermissions = Collections.emptySet();

    public static void reset() {
        sAdoptedPermissions = Collections.emptySet();
    }

    public static void adoptShellPermissionIdentity(UiAutomation self) {
        sAdoptedPermissions = UiAutomation.ALL_PERMISSIONS;
    }

    public static void adoptShellPermissionIdentity(UiAutomation self,
            @Nullable String... permissions) {
        if (permissions == null) {
            sAdoptedPermissions = UiAutomation.ALL_PERMISSIONS;
        } else {
            sAdoptedPermissions = Set.of(permissions);
        }
    }

    public static void dropShellPermissionIdentity(UiAutomation self) {
        sAdoptedPermissions = Collections.emptySet();
    }

    public static Set<String> getAdoptedShellPermissions(UiAutomation self) {
        return sAdoptedPermissions;
    }
}
