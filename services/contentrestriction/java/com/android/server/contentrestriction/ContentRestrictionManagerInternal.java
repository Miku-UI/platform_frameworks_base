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

package com.android.server.contentrestriction;

import android.annotation.UserIdInt;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Local system service interface for {@link ContentRestrictionService}.
 *
 * @hide
 */
public abstract class ContentRestrictionManagerInternal {
    /**
     * Returns whether content restriction is enabled for a given user.
     *
     * @param userId the user to retrieve the content restriction state for
     * @return whether the user has content restriction enabled
     */
    public abstract boolean isContentRestrictionEnabledForUser(@UserIdInt int userId);

    /**
     * Sets the content restriction packages for a given user.
     *
     * @param userId the user to set the content restriction packages for
     * @param packageNames the list of package names to set
     * @param systemEntity the service entity that is setting the content restriction packages. The
     *                     content restriction packages set by a service entity can only be cleared
     *                     by the same entity.
     *
     */
    public abstract void setContentRestrictionPackages(
            @UserIdInt int userId,
            @Nullable List<String> packageNames,
            @NonNull String systemEntity);
}
