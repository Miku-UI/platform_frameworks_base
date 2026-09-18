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

/**
 * @hide
 */
interface IMmiSession {
    /**
     * Sends the user's selection from a menu list displayed by the CAM.
     *
     * @param response The index of the menu item selected by the user.
     */
    void setMenuListAnswer(int response);
    /**
     * Sends the user's answer to an enquiry dialog displayed by the CAM.
     *
     * @param answerId @CamConstants.MmiAnswerId. The answer id corresponding to the enquiry.
     * @param answer The text entered by the user in response to the enquiry.
     */
    void setEnquiryAnswer(int answerId, String answer);
    /**
     * Sends a command to close the current MMI interaction, sending CloseMmi APDU to CAM.
     */
    void closeMmi();
    /**
     * Releases all resources associated with this MMI session and terminates the
     * communication channel. This should be called when the MMI interaction is
     * completely finished.
     */
    void close();
}
