/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app.ondeviceintelligence.imagedescription;

import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.os.PersistableBundle;

/**
 * Interface for receiving image description model info.
 * @hide
 */
oneway interface IImageDescriptionModelCallback {
    void onSuccess(in ImageDescriptionModel result);
    void onFailure(int errorCode, in String errorMessage, in PersistableBundle errorParams);
}
