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

package android.app.supervision;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.app.Service;
import android.app.supervision.flags.Flags;
import android.content.Intent;
import android.os.IBinder;

/**
 * Base class for a service that the holders of the
 * {@link android.app.role.RoleManager#ROLE_SYSTEM_SUPERVISION} or
 * {@link android.app.role.RoleManager#ROLE_SUPERVISION} roles must extend.
 *
 * <p>When supervision is enabled, the system searches for this service from each supervision role
 * holder using an intent filter for the {@link #ACTION_SUPERVISION_APP_SERVICE} action. The system
 * attempts to maintain a bound connection to the service, keeping it in the foreground.
 *
 * <p>If a supervision role holder's process crashes, the system will restart it and automatically
 * rebind to the service after a backoff period.
 *
 * <p>The service must be protected with the permission
 * {@link android.Manifest.permission#BIND_SUPERVISION_APP_SERVICE}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
public class SupervisionAppService extends Service {
    /**
     * Service Action: Action for a service that a supervision role holder must extend.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.SERVICE_ACTION)
    public static final String ACTION_SUPERVISION_APP_SERVICE =
            "android.app.action.SUPERVISION_APP_SERVICE";

    private final ISupervisionListener mBinder =
            new ISupervisionListener.Stub() {
                @Override
                public void onSetSupervisionEnabled(int userId, boolean enabled) {
                    if (enabled) {
                        SupervisionAppService.this.onSupervisionEnabled();
                    } else {
                        SupervisionAppService.this.onSupervisionDisabled();
                    }
                }
            };

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        return mBinder.asBinder();
    }

    /**
     * Called when supervision is enabled.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
    public void onSupervisionEnabled() {}

    /**
     * Called when supervision is disabled.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
    public void onSupervisionDisabled() {}
}
