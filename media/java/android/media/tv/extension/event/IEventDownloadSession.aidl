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
package android.media.tv.extension.event;

import android.net.Uri;
import android.os.Bundle;

/**
 * Interface for managing a dedicated tuning session for downloading broadcast event and metadata.
 * This interface controls the lifecycle of a background or foreground data acquisition process.
 * It is used to fetch DVB/ATSC tables without necessarily rendering videos.
 *
 * @hide
 */
interface IEventDownloadSession {
    /**
     * Determine to execute barker channel or silent tune flow for related service type
     *
     * @param eventDownloadParams A Bundle containing parameters for the session, keys should follow
     *                            {@link EventConstants.EventDownloadKeys}.
     * @return {@link EventConstants.FlowType} value indicating is barker channel or slient tune
     */
    int isBarkerOrSequentialDownloadByServiceType(in Bundle eventDownloadParams);
    /**
     * Determine whether to start barker channel or silent tune flow for related service record.
     *
     * @param eventDownloadParams A Bundle containing parameters for the session, keys should follow
     *                            {@link EventConstants.EventDownloadKeys}.
     * @return {@link EventConstants.FlowType} value indicating is barker channel or slient tune
     */
    int isBarkerOrSequentialDownloadByServiceRecord(in Bundle eventDownloadParams);
    /**
     * Starts multiplex tuning process for a specific channel.
     *
     * @param channelUri The URI identifying the channel, format defined by TvContract.
     */
    void startTuningMultiplex(in Uri channelUri);
    /**
     * Sets active window channels.
     *
     * @param activeWinChannelInfos A list of channel URIs to be set in the active window.
     */
    void setActiveWindowChannelInfo(in Uri[] activeWinChannelInfos);
    /**
     * Cancels the currently running event download flow (either barker channel or silent tune).
     */
    void cancel();
    /**
     * Releases all resources associated with the event download flow.
     */
    void release();
}
