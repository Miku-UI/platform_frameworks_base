/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.media.audio;

import android.media.audio.IAudioModeSession;

/**
 * Callback interface for {@link IAudioModeSession}.
 * @hide
 */
oneway interface IAudioModeSessionCallback {
    void onAvailableRoutesChanged(
            in List<IAudioModeSession.Route> availableRoutes);
    void onExternalRequestedRouteChanged(in @nullable IAudioModeSession.Route newRoute,
                                         int requestId);
    void onPaused();
    void onResumed(int requestId);
    void onClosed();
    void onRoutingResult(int requestId, in @nullable IAudioModeSession.Route route, int status);
}
