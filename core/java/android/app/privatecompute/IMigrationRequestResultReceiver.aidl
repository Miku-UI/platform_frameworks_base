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

package android.app.privatecompute;

import android.app.privatecompute.MigrationRequestResult;

/**
 * Interface for receiving the result of a startNonPccProcessForDataMigration request.
 * @hide
 */
oneway interface IMigrationRequestResultReceiver {
    /** Called when the non-PCC process provides a result (Accepted/Rejected). */
    void onResult(in MigrationRequestResult result);

    /** Called if the system fails to start the non-PCC process or it times out. */
    void onError(int errorCode, @nullable String errorMessage);
}
