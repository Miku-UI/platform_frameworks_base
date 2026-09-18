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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Key identifying admin entity for policy accounting purposes. Two admins with equal keys are
 * treated as the same entity by DevicePolicyEngine.
 */
public sealed interface AdminKey permits AdminKey.System, AdminKey.Package, AdminKey.Legacy {
    /**
     * Returns user id of the admin, USER_SYSTEM for system entities.
     */
    @UserIdInt int getUserId();

    /**
     * Returns package name for package based admin, empty string otherwise.
     */
    // Ideally it should return null, but keeping the existing behavior to avoid causing NPEs.
    String getPackageName();

    /**
     * Returns system entity for system admins, null otherwise.
     */
    String getSystemEntity();

    record System(@NonNull String systemEntity) implements AdminKey {
        public System {
            Objects.requireNonNull(systemEntity);
        }

        @Override
        public @UserIdInt int getUserId() {
            return UserHandle.USER_SYSTEM;
        }

        @Override
        public String getPackageName() {
            return "";
        }

        @Override
        public String getSystemEntity() {
            return systemEntity;
        }
    }

    record Package(@UserIdInt int userId, @NonNull String packageName) implements AdminKey {
        public Package {
            Objects.requireNonNull(packageName);
        }

        @Override
        public @UserIdInt int getUserId() {
            return userId;
        }

        @Override
        public String getPackageName() {
            return packageName;
        }

        @Override
        public String getSystemEntity() {
            return null;
        }
    }

    record Legacy(@UserIdInt int userId, @NonNull ComponentName component) implements AdminKey {
        public Legacy {
            Objects.requireNonNull(component);
        }

        @Override
        public @UserIdInt int getUserId() {
            return userId;
        }

        @Override
        public String getPackageName() {
            return component.getPackageName();
        }

        @Override
        public String getSystemEntity() {
            return null;
        }
    }
}
