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
package android.app.permissionui;

import android.app.PendingIntent;
import android.app.permissionui.LocationButtonSessionResponse;
import android.os.ParcelableException;

 /**
  * A one-way callback interface used by location button service to communicate
  * with the client/host application.
  *
  * <p>An implementation of this interface is provided by the client when it calls
  * {@code LocationButtonProvider.openSession}. The service uses it to deliver
  * asynchronous results, such as session creation, permission grants, and errors.
  *
  * @hide
  */
oneway interface ILocationButtonClient {
    void onSessionOpened(in LocationButtonSessionResponse response);
    void onPermissionsResult(boolean isPermissionGranted);
    void onSessionError(in ParcelableException e);
    void onRequestPermissions(in PendingIntent pendingIntent);
}
