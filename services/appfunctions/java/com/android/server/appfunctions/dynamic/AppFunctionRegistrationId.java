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

package com.android.server.appfunctions.dynamic;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionName;

import java.util.Objects;

/**
 * An identifier for an app function registration.
 */
final class AppFunctionRegistrationId {
    @Nullable
    private final RegistrationScopeId mScopeId;

    @NonNull
    private final AppFunctionName mName;

    AppFunctionRegistrationId(
            @NonNull AppFunctionName name, @Nullable RegistrationScopeId scopeId) {
        mName = name;
        mScopeId = scopeId;
    }

    @NonNull
    AppFunctionName getFunctionName() {
        return mName;
    }

    @Nullable
    RegistrationScopeId getScopeId() {
        return mScopeId;
    }

    @Override
    public String toString() {
        return "AppFunctionRegistrationId{" + mName + ":scopeId(" + mScopeId + ")}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mScopeId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AppFunctionRegistrationId)) {
            return false;
        }
        if (this == o) {
            return true;
        }
        AppFunctionRegistrationId that = (AppFunctionRegistrationId) o;
        return Objects.equals(mName, that.mName) && Objects.equals(mScopeId, that.mScopeId);
    }
}
