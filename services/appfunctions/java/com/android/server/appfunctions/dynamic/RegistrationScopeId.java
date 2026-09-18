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

package com.android.server.appfunctions.dynamic;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionActivityId;

import java.util.Objects;

/**
 * Identifier for the source of an app function registration.
 */
public final class RegistrationScopeId {
    @Nullable private final AppFunctionActivityId mAppFunctionActivityId;

    public static final RegistrationScopeId GLOBAL_SCOPE = new RegistrationScopeId(null);

    public RegistrationScopeId(@Nullable AppFunctionActivityId appFunctionActivityId) {
        mAppFunctionActivityId = appFunctionActivityId;
    }

    @Nullable
    public AppFunctionActivityId getAppFunctionActivityId() {
        return mAppFunctionActivityId;
    }

    @Override
    public String toString() {
        return mAppFunctionActivityId == null ? "global" : mAppFunctionActivityId.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAppFunctionActivityId);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegistrationScopeId)) {
            return false;
        }
        return Objects.equals(
                mAppFunctionActivityId, ((RegistrationScopeId) obj).mAppFunctionActivityId);
    }
}
