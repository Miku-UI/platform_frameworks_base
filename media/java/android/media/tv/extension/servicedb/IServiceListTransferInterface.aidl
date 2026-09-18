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

import android.media.tv.extension.servicedb.IServiceListExportListener;
import android.media.tv.extension.servicedb.IServiceListImportListener;
import android.media.tv.extension.servicedb.IServiceListSetChannelListListener;
import android.os.IBinder;

/**
 * Factory interface for creating Export, Import, and Service List Set sessions.
 * @hide
 */
interface IServiceListTransferInterface {
    /**
     * Creates a new session for exporting the service list.
     *
     * @param listener Callback for export completion.
     * @return An IBinder for {@link IServiceListExportSession}.
     */
    IBinder createExportSession(in IServiceListExportListener listener);
    /**
     * Creates a new session for importing a service list.
     *
     * @param listener Callback for import completion.
     * @return An IBinder for {@link IServiceListImportSession}.
     */
    IBinder createImportSession(in IServiceListImportListener listener);
    /**
     * Creates a new session for setting the channel list.
     *
     * @param listener Callback for completion.
     * @return An IBinder for {@link IServiceListSetChannelListSession}.
     */
    IBinder createSetChannelListSession(in IServiceListSetChannelListListener listener);
}
