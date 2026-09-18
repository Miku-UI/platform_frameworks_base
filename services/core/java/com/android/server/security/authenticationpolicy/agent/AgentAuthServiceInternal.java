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

package com.android.server.security.authenticationpolicy.agent;

import android.annotation.UserIdInt;
import android.companion.DeviceId;

/** Internal interface for the AgentAuthService. */
public interface AgentAuthServiceInternal {

    /**
     * Checks if the agent can perform automation at this time.
     *
     * @param userId user id
     * @param deviceId device id
     * @param companionDeviceId CDM DeviceId of the agent connection
     * @return current status
     */
    boolean isAgentAuthorized(@UserIdInt int userId, int deviceId, DeviceId companionDeviceId);

    /**
     * Checks if the local agent can perform automation at this time using the local
     * associationId.
     *
     * @param userId user id
     * @param deviceId VMD device id of the agent connection
     * @return current status
     */
    boolean isAgentAuthorizedByDeviceId(@UserIdInt int userId, int deviceId);

    /**
     * Checks if the remote agent can perform automation at this time using the local
     * associationId.
     *
     * @param userId user id
     * @param associationId CDM local association id of the agent connection
     * @return current status
     */
    boolean isAgentAuthorizedByAssociationId(@UserIdInt int userId, int associationId);

    /**
     * Directly set the authorization status of an existing remote session.
     *
     * This will always return false and perform no action unless the build is debuggable.
     *
     * @param userId user id
     * @param associationId CDM local association id of the agent connection
     * @param authorized override value
     * @return current status
     */
    boolean setOverrideForAssociationId(@UserIdInt int userId,
            int associationId, boolean authorized);
    /**
     * Directly set the authorization status of an existing local session.
     *
     * This will always return false and perform no action unless the build is debuggable.
     *
     * @param userId user id
     * @param deviceId VDM device id of the agent connection
     * @param authorized override value
     * @return current status
     */
    boolean setOverrideForDeviceId(@UserIdInt int userId,
            int deviceId, boolean authorized);
}
