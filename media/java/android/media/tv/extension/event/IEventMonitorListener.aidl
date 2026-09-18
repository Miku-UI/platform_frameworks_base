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

import android.os.Bundle;

/**
 * @hide
 */
oneway interface IEventMonitorListener {
    /**
     * Invoked when the present and following event information for a channel is updated.
     *
     * @param channelDbId The tv.db ID of the channel that was updated.
     * @param eventId The ID of the specific event that triggered the update.
     * @param eventInfo A bundle containing the updated event information. The keys
     *                 defined as the {@link IEventMonoitor#getPresentEventInfo}
     */
    void onInfoChanged(long channelDbId, in Bundle eventinfo);
}
