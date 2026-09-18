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

import android.media.tv.extension.event.IEventMonitorListener;
import android.net.Uri;
import android.os.Bundle;

/**
 * Interface for monitoring real-time broadcast event data (EIT) and service status (SDT).
 * This interface is for the "Now & Next" information by provding instant access to what is playing
 * currently and allows apps to listen for real-time changes.
 *
 * @hide
 */
interface IEventMonitor {
    /**
     * Gets present event information for a specific channel.
     *
     * @param serviceInfoId Identification ID for a channel. It is _id in tv db's channel table.
     * @return A Bundle containing the present event information with the keys as define in
     *         {@link EventConstants.PresentEventInfoKeys}.
     */
    Bundle getPresentEventInfo(long serviceInfoId);
    /**
     * Registers a listener to receive notifications when the present event information is updated.
     *
     * @param listener The IEventMonitorListener to be called with updates.
     */
    void addPresentEventInfoListener(in IEventMonitorListener listener);
    /**
     * Unregisters a previously added listener for present event information updates.
     *
     * @param listener The IEventMonitorListener to be removed.
     */
    void removePresentEventInfoListener(in IEventMonitorListener listener);
    /**
     * Get following event information for a specific channel.
     *
     * @param serviceInfoId Identification ID for a channel. It is _id in tv db's channel table.
     * @return A Bundle containing the next event's information. The keys
     *         are the same as those used in {@link #getPresentEventInfo(long)}.
     */
    Bundle getFollowingEventInfo(long serviceInfoId);
    /**
     * Registers a listener to receive notifications when the following event information is updated.
     *
     * @param listener The IEventMonitorListener to be called with updates.
     */
    void addFollowingEventInfoListener(in IEventMonitorListener listener);
    /**
     * Unregisters a previously added listener for following event information updates.
     *
     * @param listener The IEventMonitorListener to be removed.
     */
    void removeFollowingEventInfoListener(in IEventMonitorListener listener);
    /**
     * Gets SDT (Service Description Table) guidance information.
     *
     * @param serviceInfoId Identification ID for a channel. It is _id in tv db's channel table.
     * @return A Bundle containing the SDT guidance information, keys defined as
     *         {@link EventConstants.SdtGuidanceKeys}.
     */
    Bundle getSdtGuidanceInfo(long serviceInfoId);
    /**
     * Sets a list of channels that the system should monitor event information in the background.
     *
     * @param tunedChannelInfos A list of channel database IDs to be monitored.
     *                          Each is _id in tv db's channel table.
     */
    void setBackgroundMonitorList(in Uri[] tuneChannelInfos);
}
