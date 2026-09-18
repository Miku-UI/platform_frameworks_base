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

package android.media.tv.extension.signal;

import android.media.tv.extension.signal.IVideoSignalInfoListener;
import android.os.Bundle;


/**
 * @hide
 */
interface IVideoSignalInfo {
    /**
     * Registers a listener to receive notifications when video signal information changes.
     *
     * @param clientToken A string that uniquely identifies the client registering the listener.
     * @param listener An IVideoSignalInfoListener to be called with updates.
     */
    void addVideoSignalInfoListener(String clientToken, in IVideoSignalInfoListener listener);
    /**
     * Unregisters a previously added listener for video signal information updates.
     *
     * @param listener The IVideoSignalInfoListener instance to remove.
     */
    void removeVideoSignalInfoListener(in IVideoSignalInfoListener listener);
    /**
     * Retrieves a comprehensive set of video signal information for the current session.
     *
     * @param sessionToken A string used to identify the TIS session.
     * @return A Bundle containing the video signal information,
     * may contain the following keys and more:
     * <ul>
     * <li>KEY_VIDEO_SIGNAL_STATUS: The general status of the video signal.</li>
     * <li>KEY_VIDEO_WIDTH: The video width.</li>
     * <li>KEY_VIDEO_HEIGHT: The video height</li>
     * <li>KEY_VIDEO_IS_PROGRESSIVE: Boolean indicating if the video signal is progressive.</li>
     * <li>KEY_VIDEO_FRAME_RATE: The video frame rate</li>
     * <li>...</li>
     * </ul>
     */
    Bundle getVideoSignalInfo(String sessionToken);
}
