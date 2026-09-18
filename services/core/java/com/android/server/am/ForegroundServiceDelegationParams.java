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

package com.android.server.am;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ForegroundServiceDelegationOptions;
import android.app.ForegroundServiceDelegationOptions.DelegationService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A service module such as MediaSessionService, VOIP can ask ActivityManagerService to start a
 * foreground service delegate on behalf of the actual app, by which the client app's process state
 * can be promoted to a higher process state, equivalent to that of a foreground service, which may
 * be higher than the app's actual process state if the app is in the background. This can help keep
 * the app in memory and potentially give it extra run-time. The app does not need to define an
 * actual service component nor add it to their manifest file.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@FlaggedApi(Flags.FLAG_FGS_DELEGATE_SYSTEM_API)
public final class ForegroundServiceDelegationParams {

    /**
     * Used for special purposes like testing.
     * @hide
     */
    public static final int DELEGATION_REASON_SPECIAL_USE = 0;

    /**
     * Used to identify delegation service related to voip.
     */
    public static final int DELEGATION_REASON_VOIP = 1;

    /**
     * The reason for the delegation.
     * @hide
     */
    // LINT.IfChange(delegationReasons)
    @IntDef(flag = false, prefix = { "DELEGATION_REASON_" }, value = {
            DELEGATION_REASON_SPECIAL_USE,
            DELEGATION_REASON_VOIP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DelegationReason {}
    // LINT.ThenChange(:delegationReasonsConverter)

    /**
     * The actual app's PID
     * @hide
     */
    public final int clientPid;

    /**
     * The actual app's UID
     * @hide
     */
    public final int clientUid;

    /**
     * The actual app's package name
     * @hide
     */
    @NonNull
    public final String clientPackageName;

    /**
     * Whether it is a sticky service or not.
     * @hide
     */
    public final boolean sticky;

    /**
     * The delegation service's instance name which is to identify the delegate.
     * @hide
     */
    @NonNull
    public String clientInstanceName;

    /**
     * The foreground service types it consists of.
     * @hide
     */
    public final int foregroundServiceTypes;

    /**
     * The delegation reason identifying the service such as MediaSessionService, VOIP.
     * This is the internal module's name which actually starts the FGS delegate on behalf of
     * the client app.
     * @hide
     */
    public final @DelegationReason int delegationReason;

    /**
     * Constructor for {@link ForegroundServiceDelegationParams}.
     *
     * @param clientPid The PID of the application on behalf of which the foreground service
     *                  is being delegated.
     * @param clientUid The UID of the application on behalf of which the foreground service
     *                  is being delegated.
     * @param clientPackageName The package name of the application on behalf of which the
     *                          foreground service is being delegated.
     * @param isSticky Whether the delegated foreground service should be sticky.
     * @param clientInstanceName A unique name for this specific delegation instance, used to
     *                           distinguish multiple delegations for the same client.
     * @param foregroundServiceTypes The types of foreground services being delegated,
     *                               as defined in {@link android.content.pm.ServiceInfo}.
     * @param delegationReason The reason for the delegation indicating the type of the system
     *                         service that is requesting the delegation,
     *                          e.g., {@link #DELEGATION_REASON_VOIP}.
     */
    public ForegroundServiceDelegationParams(int clientPid,
            int clientUid,
            @NonNull String clientPackageName,
            boolean isSticky,
            @NonNull String clientInstanceName,
            int foregroundServiceTypes,
            @DelegationReason int delegationReason) {
        this.clientPid = clientPid;
        this.clientUid = clientUid;
        this.clientPackageName = clientPackageName;
        this.sticky = isSticky;
        this.clientInstanceName = clientInstanceName;
        this.foregroundServiceTypes = foregroundServiceTypes;
        this.delegationReason = delegationReason;
    }

    /**
     * @return a {@link ForegroundServiceDelegationOptions} object corresponding to this object.
     * @hide
     */
    public ForegroundServiceDelegationOptions getForegroundServiceDelegationOptions() {
        return new ForegroundServiceDelegationOptions(this.clientPid,
                this.clientUid,
                this.clientPackageName,
                null /* clientAppThread */,
                this.sticky,
                this.clientInstanceName,
                this.foregroundServiceTypes,
                getDelegationService());
    }

    /**
     * @return corresponding {@link DelegationService} from this object.
     * @hide
     */
    // LINT.IfChange(delegationReasonsConverter)
    private @DelegationService int getDelegationService() {
        return switch (this.delegationReason) {
            case DELEGATION_REASON_SPECIAL_USE ->
                ForegroundServiceDelegationOptions.DELEGATION_SERVICE_SPECIAL_USE;
            case DELEGATION_REASON_VOIP ->
                ForegroundServiceDelegationOptions.DELEGATION_SERVICE_VOIP;
            default -> throw new IllegalArgumentException(
                    String.format("Unrecognized delegation reason %d", this.delegationReason));
        };
    }
    // LINT.ThenChange(:delegationReasons)
}
