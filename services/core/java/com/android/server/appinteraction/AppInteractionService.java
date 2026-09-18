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

package com.android.server.appinteraction;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppInteractionAttribution;

import com.android.server.SystemService;

/** Local service to note App Interactions. */
public interface AppInteractionService {
    /**
     * Called after an existing user is unlocked.
     *
     * <p>This will start tracking {@code user}'s App Interaction history.
     */
    void onUserUnlocked(@NonNull SystemService.TargetUser user);

    /**
     * Called when an existing user is stopping.
     *
     * <p>This will stop tracking {@code user}'s App Interaction history.
     */
    void onUserStopping(@NonNull SystemService.TargetUser user);

    /**
     * Notes the app interaction.
     *
     * <p>Called when an app interaction between {@code sourcePackage} and {@code targetPackage}
     * occurred.
     *
     * <p>The app interaction will be store into platform's database that is accessible with {@link
     * AppInteractionHistoryProvider}. The privacy setting might use these records to show the
     * interaction histories to the user for auditing.
     *
     * <p>If either {@code sourcePackage} or {@code targetPackage} does not exist in {@code userId},
     * the operation will fail silently.
     *
     * @param sourcePackage The source package name.
     * @param targetPackage The target package name.
     * @param appInteractionAttribution The {@link AppInteractionAttribution}.
     * @param accessTime The timestamp when the interaction first started.
     * @param userId The user id.
     */
    void noteAppInteraction(
            @NonNull String sourcePackage,
            @NonNull String targetPackage,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @CurrentTimeMillisLong long accessTime,
            int userId);
}
