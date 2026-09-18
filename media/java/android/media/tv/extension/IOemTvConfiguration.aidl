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

package android.media.tv.extension;

/**
 * Interface for querying OEM-specific TV configurations.
 *
 * <p>OEM should implement this interface to provide OEM-specific TV configurations as a service
 * that shall be started at boot. Client App should bind to this service by query the intent instead
 * of accessing through {@link android.media.tv.TvInputManager#getExtensionInterfaces}
 * @hide
 */
interface IOemTvConfiguration {
    /**
     * Check if a specific feature is enabled by the OEM configuration.
     * @param featureId The ID of the feature to check.
     * @return true if enabled, false otherwise.
     */
    boolean isFeatureEnabled(in String featureId);
    /**
     * Get a string configuration value.
     * @param configId The ID of the configuration to get.
     * @return The configuration value, or null if not found.
     */
    String getStringConfig(in String configId);
}
