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

package com.android.server.companion.datatransfer.crossdevicesync.services;

import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.feature.FeatureManager;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;

import java.util.Map;
import java.util.function.Supplier;

/** Interface for injecting dependencies into {@link SyncService}. */
public interface SyncServiceInjector {
    /** Returns the {@link NetworkManager} for cross-device communication. */
    NetworkManager getNetworkManager();

    /** Returns the {@link MetadataPublisher} for sharing device state. */
    MetadataPublisher getMetadataPublisher();

    /** Returns the {@link NotificationHelper} for managing sync-related notifications. */
    NotificationHelper getNotificationHelper();

    /** Returns the {@link IStorage} for global data persistence. */
    IStorage getGlobalStorage();

    /** Returns the {@link SyncServiceShellCommand} for handling shell commands. */
    SyncServiceShellCommand getSyncServiceShellCommand();

    /** Returns the map of {@link FeatureManager}s that implement specific sync features. */
    Map<String, Supplier<FeatureManager>> getFeatureManagers();
}
