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

package com.android.server.personalcontext.notifications;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;

import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;

/** Default implementation of {@link PendingIntentFactory}. */
class PendingIntentFactoryImpl implements PendingIntentFactory {
    private static final String TAG = "PendingIntentFactoryImpl";

    @Override
    public PendingIntent create(
            @NonNull Context context,
            int requestCode,
            @NonNull Intent intent,
            int flags,
            @NonNull ActionType actionType) {
        final int finalFlags = flags | PendingIntent.FLAG_IMMUTABLE;
        switch (actionType) {
            case ACTIVITY:
                return PendingIntent.getActivity(
                        context, requestCode, intent, finalFlags, /* options= */ null);
            case SERVICE:
                return PendingIntent.getService(context, requestCode, intent, finalFlags);
            case BROADCAST:
                return PendingIntent.getBroadcast(context, requestCode, intent, finalFlags);
            case UNKNOWN:
            default:
                Slog.e(TAG, "Unhandled ActionType: " + actionType);
                return null;
        }
    }
}
