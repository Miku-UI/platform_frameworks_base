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

import android.os.Bundle;

/**
 * @hide
 */
interface ITeletextPageSubCode {
    /**
     * Gets Teletext page number related to the current session.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @return The current Teletext page number.
     */
    int getTeletextPageNumber(String sessionToken);
    /**
     * Sets the Teletext page number.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @param pageNumber   The page number to set.
     */
    void setTeletextPageNumber(String sessionToken, int pageNumber);
    /**
     * Gets the current Teletext sub-page number (subcode).
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @return The current Teletext page subcode.
     */
    int getTeletextPageSubCode(String sessionToken);
    /**
     * Sets the Teletext sub-page number (subcode).
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @param pageSubCode  The page subcode to set.
     */
    void setTeletextPageSubCode(String sessionToken, int pageSubCode);
    /**
     * Gets the Teletext TOP (Table of Pages) information status.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @return true if TOP info is available, false otherwise.
     */
    boolean getTeletextHasTopInfo(String sessionToken);
    /**
     * Gets the list of Teletext TOP blocks.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @return A Bundle containing the list of block information, with "KEY_TOP_BLOCK_LIST" as key.
     *         This key corresponds to a list of Bundle objects, each representing a block. For
     *         each block, "KEY_BLOCK_NAME" and "KEY_BLOCK_INDEX" are expected to be present.
     *         Please refer to @TeletextConstants for more details.
     */
    Bundle getTeletextTopBlockList(String sessionToken);
    /**
     * Gets the list of Teletext TOP groups inside a specific block.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @param blockIndex The specific block index.
     * @return A Bundle containing the list of group information, with "KEY_TOP_GROUP_LIST" as key.
     *         This key corresponds to a list of Bundle objects, each representing a group. For
     *         each group, "KEY_GROUP_NAME" and "KEY_GROUP_INDEX" are expected to be present.
     *         Please refer to @TeletextConstants for more details.
     */
    Bundle getTeletextTopGroupList(String sessionToken, int blockIndex);
    /**
     * Gets the list of Teletext TOP pages inside a specific group.
     *
     * @param sessionToken The per-session token provided by the host during session creation.
     * @param groupIndex The specific group index.
     * @return A Bundle containing the list of page information, with "KEY_TOP_PAGE_LIST" as key.
     *         This key corresponds to a list of Bundle objects, each representing a page. For
     *         each page, "KEY_PAGE_NUMBER", "KEY_PAGE_SUBCODE", "KEY_PAGE_NAME" must be present.
     *         Please refer to @TeletextConstants for more details.
     */
    Bundle getTeletextTopPageList(String sessionToken, int groupIndex);
}
