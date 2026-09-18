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

/**
 * @hide
 */
oneway interface IHdmiSignalInfoListener {
    /**
     * Called when the HDMI signal information changes.
     *
     * @param sessionToken The token that identifies the session for which the signal has changed.
     */
    void onSignalInfoChanged(String sessionToken);
    /**
     * Called when the low latency mode status changes.
     *
     * @param enable The new status of the mode. 1 indicates that low latency mode is enabled, and
     *               0 indicates it is disabled.
     */
    void onLowLatencyModeChanged(int enable);
}
