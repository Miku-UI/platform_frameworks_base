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

import android.app.permissionui.ILocationButtonClient;
import android.app.permissionui.LocationButtonRequest;
import android.os.IBinder;

/**
 * The binder interface for creating location button sessions.
 *
 * @hide
 */
oneway interface ILocationButtonService {
    void openSession(in String packageName,
            in IBinder hostToken,
            int displayId,
            in LocationButtonRequest request,
            in ILocationButtonClient client
            );
}
