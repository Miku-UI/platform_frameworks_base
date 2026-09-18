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

package android.app.privatecompute;

import android.app.privatecompute.IMigrationRequestResultReceiver;
import android.os.PersistableBundle;

/**
 * @hide
 */
interface IPccSandboxManager {
    boolean isPrivateComputeServicesUid(int uid);

    boolean isPccTrustedSystemComponent(int uid, String packageName);

    oneway void writeToAuditLog(in PersistableBundle data, in String packageName);

    oneway void batchWriteToAuditLog(in List<PersistableBundle> data, in String packageName);

    oneway void startNonPccProcessForDataMigration(in IMigrationRequestResultReceiver callback);
}
