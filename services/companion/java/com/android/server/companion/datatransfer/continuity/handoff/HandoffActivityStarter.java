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

package com.android.server.companion.datatransfer.continuity.handoff;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.HandoffActivityData;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Slog;

import java.util.List;
import java.util.Objects;

final class HandoffActivityStarter {

    private static final String TAG = "HandoffActivityStarter";

    /**
     * Starts the activities specified by {@code handoffActivityData}.
     *
     * @param context the context to use for starting the activities.
     * @param handoffActivityData the list of activities to start.
     * @return {@code true} if an activity was started (including web fallback),
     * {@code false} otherwise.
     */
    public static boolean start(
        @NonNull Context context,
        @NonNull List<HandoffActivityData> handoffActivityData) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handoffActivityData);

        if (handoffActivityData.isEmpty()) {
            Slog.w(TAG, "No activities to start.");
            return false;
        }

        // Attempt to launch the activities natively.
        if (startNativeActivities(context, handoffActivityData)) {
            return true;
        }

        // Attempt to launch a web fallback.
        Uri fallbackUri = handoffActivityData.get(handoffActivityData.size() - 1).getFallbackUri();
        return startWebFallback(context, fallbackUri);
    }

    private static boolean startNativeActivities(
        @NonNull Context context,
        @NonNull List<HandoffActivityData> handoffActivityData) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handoffActivityData);

        if (handoffActivityData.isEmpty()) {
            Slog.w(TAG, "No activities to start.");
            return false;
        }

        // Try to build an intent for the top activity handed off. This will be used as a fallback
        // if any of the lower activities cannot be launched.
        Intent topActivityIntent = createIntent(context,
            handoffActivityData.get(handoffActivityData.size() - 1));

        // If the top activity cannot launch, we don't have anything to fall back to and should
        // return false.
        if (topActivityIntent == null) {
            Slog.w(TAG, "Top activity cannot be launched.");
            return false;
        }

        Intent[] intentsToLaunch = new Intent[handoffActivityData.size()];
        for (int i = 0; i < handoffActivityData.size(); i++) {
            Intent intent = createIntent(context, handoffActivityData.get(i));
            if (intent != null) {
                intentsToLaunch[i] = intent;
            } else {
                Slog.w(
                    TAG,
                    "Failed to create intent for activity, falling back to launch top activity.");
                return startIntents(context, new Intent[] {topActivityIntent});
            }
        }

        if (!startIntents(context, intentsToLaunch)) {
            Slog.w(TAG, "Failed to launch activities, falling back to launch top activity.");
            return startIntents(context, new Intent[] {topActivityIntent});
        }

        return true;
    }

    private static boolean startWebFallback(@NonNull Context context, @Nullable Uri fallbackUri) {
        Objects.requireNonNull(context);

        if (fallbackUri == null) {
            Slog.w(TAG, "No fallback URI specified.");
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, fallbackUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Add a flag to allow this URI to be handled by a non-browser app.
        intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return startIntents(context, new Intent[] {intent});
    }

    private static boolean startIntents(@NonNull Context context, @NonNull Intent[] intents) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(intents);

        intents[intents.length - 1].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            int result = context.startActivitiesAsUser(
                intents,
                ActivityOptions.makeBasic().toBundle(),
                UserHandle.CURRENT);
            Slog.i(TAG, "Launched activities: " + result);
            return result == ActivityManager.START_SUCCESS;
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "Unable to launch activities: " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private static Intent createIntent(
        @NonNull Context context,
        @NonNull HandoffActivityData handoffActivityData) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handoffActivityData);

        // Check if the package is installed on this device.
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getActivityInfo(
                handoffActivityData.getComponentName(),
                PackageManager.MATCH_DEFAULT_ONLY);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Package not installed on device: "
                + handoffActivityData.getComponentName().getPackageName());
            return null;
        }

        Intent intent = new Intent();
        intent.setComponent(handoffActivityData.getComponentName());
        intent.putExtras(new Bundle(handoffActivityData.getExtras()));
        return intent;
    }
}