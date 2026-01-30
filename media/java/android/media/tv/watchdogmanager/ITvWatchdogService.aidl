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

package android.media.tv.watchdogmanager;

import android.media.tv.watchdogmanager.IResourceOveruseListener;
import android.media.tv.watchdogmanager.PackageKillableState;
import android.media.tv.watchdogmanager.ResourceOveruseConfiguration;
import android.media.tv.watchdogmanager.ResourceOveruseStats;
import android.os.UserHandle;

/** @hide */
interface ITvWatchdogService {

    ResourceOveruseStats getResourceOveruseStats(
        in int resourceOveruseFlag, in int maxStatsPeriod);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.COLLECT_TV_WATCHDOG_METRICS)")
    List<ResourceOveruseStats> getAllResourceOveruseStats(
        in int resourceOveruseFlag, in int minimumStatsFlag, in int maxStatsPeriod);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.COLLECT_TV_WATCHDOG_METRICS)")
    ResourceOveruseStats getResourceOveruseStatsForUserPackage(
        in String packageName, in UserHandle userHandle, in int resourceOveruseFlag,
            in int maxStatsPeriod);

    // addResourceOveruseListener needs to get callingUid, so cannot be oneway.
    void addResourceOveruseListener(
        in int resourceOveruseFlag, in IResourceOveruseListener listener);
    oneway void removeResourceOveruseListener(in IResourceOveruseListener listener);

    // Following APIs need to get calling pid/uid for permission checking, so cannot be oneway.
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.COLLECT_TV_WATCHDOG_METRICS)")
    void addResourceOveruseListenerForSystem(
        in int resourceOveruseFlag, in IResourceOveruseListener listener);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.COLLECT_TV_WATCHDOG_METRICS)")
    void removeResourceOveruseListenerForSystem(in IResourceOveruseListener listener);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CONTROL_TV_WATCHDOG_CONFIG)")
    void setKillablePackageAsUser(in String packageName, in UserHandle userHandle,
        in boolean isKillable);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CONTROL_TV_WATCHDOG_CONFIG)")
    List<PackageKillableState> getPackageKillableStatesAsUser(in UserHandle user);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CONTROL_TV_WATCHDOG_CONFIG)")
    int setResourceOveruseConfigurations(
        in List<ResourceOveruseConfiguration> configurations, in int resourceOveruseFlag);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(anyOf = {android.Manifest.permission.COLLECT_TV_WATCHDOG_METRICS, android.Manifest.permission.CONTROL_TV_WATCHDOG_CONFIG})")
    List<ResourceOveruseConfiguration> getResourceOveruseConfigurations(
        in int resourceOveruseFlag);
}