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

package android.media.tv.extension.cam;

import android.media.tv.extension.cam.ICamHostControlAskReleaseReplyCallback;
import android.media.tv.extension.cam.ICamHostControlInfoListener;

/**
 * @hide
 */
interface ICamHostControlService {
    /**
     * Registers a listener to monitor updates for the CICAM Host Control session.
     *
     * @param listener The ICamHostControlInfoListener to add.
     */
    void addCamHostcontrolInfoListener(ICamHostControlInfoListener listener);
    /**
     * Unregisters a listener to stop monitoring CICAM Host Control session updates.
     *
     * @param listener The ICamHostControlInfoListener to remove.
     */
    void removeCamHostcontrolInfoListener(ICamHostControlInfoListener listener);
    /**
     * Sends an asynchronous request to the CICAM, asking it to release control of
     * a shared resource. The result of the request is delivered via the provided
     * callback.
     *
     * @param sessionToken The unique token identifying the session requested the release.
     * @param callback An ICamHostControlAskReleaseReplyCallback instance that
     *                 will be invoked with the CAM's reply.
     * @return @CamConstants.AskReleaseReplyStatus to indicate the cam reply status.
     */
    int sendCamHostControlAskRelease(String sessionToken,
            ICamHostControlAskReleaseReplyCallback callback);
}
