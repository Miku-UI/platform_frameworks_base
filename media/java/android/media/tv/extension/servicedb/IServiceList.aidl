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

package android.media.tv.extension.servicedb;

import android.os.Bundle;

/**
 * Interface for retrieving service list information.
 * @hide
 */
interface IServiceList {
    /**
     * Retrieves a list of available Service List IDs from tv.db.
     * <p>These correspond to {@code COLUMN_CHANNEL_LIST_ID} in the TvProvider.</p>
     *
     * @return An array of Service List IDs.
     */
    String[] getServiceListIds();
    /**
     * Retrieves detailed information for a specific Service List from tv.db.
     *
     * @param serviceListId The ID grouping services by broadcast standard (e.g., DVB-T, DVB-C).
     * @param keys The metadata keys to retrieve.
     * @return A bundle containing the Service List information, bundle keys defined but not
     *         limited to @ServicedbConstants.ServiceListInfoKeys.
     */
    Bundle getServiceListInfo(String serviceListId, in String[] keys);
}
