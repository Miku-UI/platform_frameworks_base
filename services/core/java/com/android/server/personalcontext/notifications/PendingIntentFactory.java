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

import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;

/** An interface for creating {@link PendingIntent}s, allowing for dependency injection in tests. */
public interface PendingIntentFactory {
    /**
     * Creates a {@link PendingIntent} from the given {@link Intent} and {@link ActionType}.
     *
     * @param context The context in which the PendingIntent should be created.
     * @param requestCode The request code for the PendingIntent.
     * @param intent The intent to wrap in a PendingIntent.
     * @param flags The flags for the PendingIntent. {@link PendingIntent#FLAG_IMMUTABLE} will
     *     always be added to these flags.
     * @param actionType The type of action (Activity, Service, Broadcast).
     * @return A new PendingIntent.
     */
    PendingIntent create(
            @NonNull Context context,
            int requestCode,
            @NonNull Intent intent,
            int flags,
            @NonNull ActionType actionType);
}
