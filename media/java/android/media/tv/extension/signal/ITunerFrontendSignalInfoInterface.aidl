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

import android.media.tv.extension.signal.ITunerFrontendSignalInfoListener;
import android.os.Bundle;

/**
 * Interface for passing FrontendStatus TIS extracted from Tuner to client app.
 * This interface is used because client app and TIS currently cannot init the same Tuner instance,
 * thus TIS need to pass in necessary tuner information to client app.
 * @hide
 */
interface ITunerFrontendSignalInfoInterface {
    /**
     * Gets the current frontend signal status for the specified session.
     *
     * @param sessionToken The token that identifies the TIS session.
     * @return A Bundle that resembles {@code android.media.tv.tuner.frontend.FrontendStatus},
     *          containing the current signal information, such as strength, quality and more.
     *          See @SignalConstant.FrontendSignalInfoKeys for key definitions.
     */
    Bundle getFrontendSignalInfo(String sessionToken);
    /**
     * Registers a listener to receive notifications about frontend signal status changes.
     *
     * @param listener An ITunerFrontendSignalInfoListener to be called when it is updated.
     */
    void setFrontendSignalInfoListener(in ITunerFrontendSignalInfoListener listener);
}
