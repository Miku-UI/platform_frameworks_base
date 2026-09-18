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

package com.android.server.personalcontext;

import android.content.Context;
import android.os.UserHandle;

import com.android.internal.content.PackageMonitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link PackageMonitorProxy} handles sending package monitor updates to multiple different
 * monitoring destinations.
 * @hide
 */
public class PackageMonitorProxy extends PackageMonitor {
    interface PackageMonitorProvider {
        Set<PackageMonitor> getProxiesByUid(int uid);
    }

    final HashSet<PackageMonitorProvider> mMonitorProviders = new HashSet<>();

    final Context mContext;

    public PackageMonitorProxy(Context context) {
        super();
        mContext = context;
    }

    void addProvider(PackageMonitorProvider provider) {
        mMonitorProviders.add(provider);

        // First provider, register listener. Listen to all users as listening to particular users
        // only works if you're said user as the caller.
        if (mMonitorProviders.size() == 1) {
            register(mContext, null, UserHandle.ALL, false);
        }
    }

    void removeProvider(PackageMonitorProvider provider) {
        mMonitorProviders.remove(provider);

        if (mMonitorProviders.isEmpty()) {
            unregister();
        }
    }

    @Override
    public boolean onPackageChanged(String packageName, int uid, String[] components) {
        applyToMatchingMonitors(uid,
                packageMonitor -> packageMonitor.onPackageChanged(packageName, uid, components));
        return true;
    }

    @Override
    public void onPackageUpdateFinished(String packageName, int uid) {
        applyToMatchingMonitors(uid,
                packageMonitor -> packageMonitor.onPackageUpdateFinished(packageName, uid));
    }

    @Override
    public void onPackageAdded(String packageName, int uid) {
        applyToMatchingMonitors(uid,
                packageMonitor -> packageMonitor.onPackageAdded(packageName, uid));
    }

    @Override
    public void onPackageRemoved(String packageName, int uid) {
        applyToMatchingMonitors(uid,
                packageMonitor -> packageMonitor.onPackageRemoved(packageName, uid));
    }

    private void applyToMatchingMonitors(int uid, Consumer<PackageMonitor> consumer) {
        for (PackageMonitor monitor : getMatchingMonitors(uid)) {
            consumer.accept(monitor);
        }
    }

    private Collection<PackageMonitor> getMatchingMonitors(int uid) {
        final HashSet<PackageMonitor> monitors = new HashSet<>();
        for (PackageMonitorProvider provider : mMonitorProviders) {
            monitors.addAll(provider.getProxiesByUid(uid));
        }

        return monitors;
    }
}
