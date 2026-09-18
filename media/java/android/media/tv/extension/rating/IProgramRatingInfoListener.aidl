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

package android.media.tv.extension.rating;

import android.os.Bundle;

/**
 * @hide
 */
interface IProgramRatingInfoListener {
    /**
     * Called when program rating information for the currently-viewed program is updated.
     *
     * @param sessionToken The token that associates this update with a specific viewing session.
     * @param changedProgramInfo A Bundle containing the program's rating information,
     *                           the Bundle must contain the following key:
     *                           <ul><li>KEY_CONTENT_RATINGS: The content ratings applicable to the
     *                           current channel.</li></ul>
     */
    void onProgramInfoChanged(String sessionToken,in Bundle changedProgramInfo);
}
