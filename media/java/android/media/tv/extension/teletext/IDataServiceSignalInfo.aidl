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

package android.media.tv.extension.teletext;

import android.media.tv.extension.teletext.IDataServiceSignalInfoListener;
import android.os.Bundle;


/**
 * Interface for retrieving Teletext signal information.
 * @hide
 */
interface IDataServiceSignalInfo {
    /**
     * Gets data service signal information on Teletext.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @return A Bundle containing keys as defined in @TeletextConstants.DataServiceSignalInfoKey.
     * </ul>
     */
     Bundle getDataServiceSignalInfo(String sessionToken);
     /**
      * Adds a listener to receive notifications about Teletext running status updates.
      *
      * @param clientToken A token representing the client.
      * @param listener An IDataServiceSignalInfoListener listener that will be called when the
      *                 information is updated.
      */
     void addDataServiceSignalInfoListener(String clientToken,
        IDataServiceSignalInfoListener listener);
     /**
      * Removes a previously registered listener for Teletext running status updates.
      *
      * @param clientToken A token representing the client.
      * @param listener The IDataServiceSignalInfoListener that was previously registered.
      */
     void removeDataServiceSignalInfoListener(String clientToken,
        IDataServiceSignalInfoListener listener);
}
