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

import android.os.Bundle;
import com.android.internal.infra.AndroidFuture;
import android.service.contentsafety.IGetFeatureCallback;

 /**
 * Interface for a concrete implementation to provide content safety services.
 *
 * @hide
 */

 interface IContentSafetyService {

  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
  oneway void requestGetFeature(in int featureType, in AndroidFuture cancellationSignal, in IGetFeatureCallback callback) = 1;

  oneway void notifySandboxedServiceConnected() = 2;
  oneway void notifySandboxedServiceDisconnected() = 3;
  oneway void ready() = 4;
  oneway void notifySettingsServiceConnected() = 5;
  oneway void notifySettingsServiceDisconnected() = 6;


 }

