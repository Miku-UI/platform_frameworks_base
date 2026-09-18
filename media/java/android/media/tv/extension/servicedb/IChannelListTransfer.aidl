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

import android.os.ParcelFileDescriptor;

/**
 * Interface for transferring channel lists via XML files.
 * <p>
 * This interface allows for the bulk import and export of channel data
 * using a standardized XML format.
 * @hide
 */
interface IChannelListTransfer {
    /**
     * Parses an XML file from the provided file descriptor and imports the channel information
     * into the system database.
     * <p>
     * <b>Note:</b> This operation blocks until the parsing and insertion are complete.
     * The file descriptor is automatically closed after usage.
     *
     * @param pfd A {@link ParcelFileDescriptor} pointing to a readable XML file.
     * Must be opened with {@link android.os.ParcelFileDescriptor#MODE_READ_ONLY}.
     */
    void importChannelList(in ParcelFileDescriptor pfd);
    /**
     * Retrieves the current channel information from the database and writes it
     * as an XML file to the provided file descriptor.
     * <p>
     * <b>Note:</b> This operation blocks until the XML generation and writing are complete.
     * The file descriptor is automatically closed after usage.
     *
     * @param pfd A {@link ParcelFileDescriptor} pointing to a writable file location.
     * Must be opened with {@link android.os.ParcelFileDescriptor#MODE_WRITE_ONLY}
     * or {@code MODE_READ_WRITE}.
     */
    void exportChannelList(in ParcelFileDescriptor pfd);
}
