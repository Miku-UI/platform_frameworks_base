/*
 * Copyright (C) 2024 The Android Open Source Project
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

/** This interface is used to expose configs to the AppInteractionService. */
public interface ServiceConfig {
    String NAMESPACE_APP_INTERACTION = "appinteraction";

    /**
     * Returns the maximum age, in milliseconds, for an App Interaction history record.
     *
     * <p>Access history records older than this retention period will be removed during the next
     * maintenance cleanup.
     */
    long getAppInteractionHistoryRetentionMillis();

    /**
     * Returns the interval, in milliseconds, at which the maintenance job runs to delete expired
     * App Interaction histories.
     */
    long getAppInteractionExpiredHistoryDeletionIntervalMillis();
}
