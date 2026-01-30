/**
 * Copyright (c) 2024, The Android Open Source Project
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

package android.app.supervision;

import android.content.Intent;
import android.app.supervision.ISupervisionListener;
import android.app.supervision.SupervisionRecoveryInfo;

/**
 * Internal IPC interface to the supervision service.
 * @hide
 */
interface ISupervisionManager {
    Intent createConfirmSupervisionCredentialsIntent(int userId);
    boolean isSupervisionEnabledForUser(int userId);
    void setSupervisionEnabledForUser(int userId, boolean enabled);
    String getActiveSupervisionAppPackage(int userId);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.MANAGE_ROLE_HOLDERS)")
    boolean shouldAllowBypassingSupervisionRoleQualification();
    oneway void setSupervisionRecoveryInfo(in SupervisionRecoveryInfo recoveryInfo);
    SupervisionRecoveryInfo getSupervisionRecoveryInfo();
    boolean hasSupervisionCredentials();
    oneway void registerSupervisionListener(int userId, in ISupervisionListener listener);
    oneway void unregisterSupervisionListener(in ISupervisionListener listener);
}
