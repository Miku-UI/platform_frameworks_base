/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.service.contentsafety;

import com.android.internal.infra.AndroidFuture;
import android.app.contentsafety.IIsFeatureEnabledCallback;
import android.os.UserHandle;

 /**
 * Interface for the service that provides content safety settings.
 *
 * @hide
 */

 oneway interface IContentSafetySettingsService {

   /**
    * Checks if a specific content safety feature is enabled for a user.
    *
    * @param featureType The feature to check.
    * @param userId The user for whom to check the feature status.
    * @param cancellationSignal A future to receive a cancellation signal transport.
    * @param remoteCallback The callback to receive the result.
    */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
  void requestIsFeatureEnabled(in int featureType, in UserHandle userId, in AndroidFuture cancellationSignal, in IIsFeatureEnabledCallback remoteCallback) = 1;

 }

