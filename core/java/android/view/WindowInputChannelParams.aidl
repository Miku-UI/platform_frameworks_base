/**
 * Copyright (c) 2026, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import android.graphics.Region;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.InputTransferToken;

/**
 * Params used with {@link IWindowSession#grantInputChannel()} and
 * {@link IWindowSession#updateInputChannel()}.
 * @hide
 */
parcelable WindowInputChannelParams {
    int displayId;
    int flags;
    int privateFlags;
    int inputFeatures;
    int type;
    SurfaceControl surface;
    // The input channel token for the channel created from grantInputChannel(), only used for
    // updateInputChannel()
    @nullable IBinder channelToken;
    @nullable IBinder clientToken;
    @nullable InputTransferToken hostInputTransferToken;
    @nullable IBinder windowToken;
    @nullable InputTransferToken inputTransferToken;
    @nullable String inputHandleName;
    @nullable Region region;
}