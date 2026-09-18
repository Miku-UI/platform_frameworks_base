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
import android.app.contentsafety.ICheckContentCallback;
import android.service.contentsafety.ILoadFeatureCallback;

 /**
 * Interface for a concrete implementation to provide conent safety services executed by the sandboxed service.
 *
 * @hide
 */

  oneway interface IContentSafetySandboxedService {

   @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
   oneway void requestCheckContent(in int featureType, in Bundle input, in AndroidFuture cancellationSignal, in ICheckContentCallback remoteCallback) = 1;

   @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
   oneway void requestLoadFeature(in Bundle feature, in AndroidFuture cancellationSignal, in ILoadFeatureCallback callback ) = 2;
 }
