/*
 * Copyright 2026 The Android Open Source Project
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

package android.companion.virtual.computercontrol;


import android.annotation.RequiresPermission;

/**
 * Automation consent manager for {@link ComputerControlSession}. To be used to manage which agent
 * application can automate which target applications.
 *
 * @hide
 */
public interface ComputerControlConsentManager {
    /**
     * Adds a package to the automatable app list for the agent.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_COMPUTER_CONTROL_CONSENT)
    void addAppToAutomatableAppListForAgent(int agentUid, String agentPackageName,
            String packageName);

    /**
     * Removes a package from the automatable app list for the agent.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_COMPUTER_CONTROL_CONSENT)
    void removeAppFromAutomatableAppListForAgent(int agentUid, String agentPackageName,
            String packageName);

    /**
     * Clears the automatable app list for the agent.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_COMPUTER_CONTROL_CONSENT)
    void clearAutomatableAppListForAgent(int agentUid, String agentPackageName);

    /**
     * Returns the automatable app list for the agent.
     */
    String[] getAutomatableAppListForAgent(int agentUid, String agentPackageName);
}
