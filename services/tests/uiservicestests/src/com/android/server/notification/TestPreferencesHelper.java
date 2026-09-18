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
package com.android.server.notification;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.permission.PermissionManager;

import com.android.server.uri.UriGrantsManagerInternal;

import java.time.Clock;

// Test version of PreferencesHelper whose only functional difference is that it does not
// interact with the real IpcDataCache, and instead tracks whether or not the cache has been
// invalidated since creation or the last reset.
class TestPreferencesHelper extends PreferencesHelper {
    private boolean mChannelCacheInvalidated = false;
    private boolean mGroupCacheInvalidated = false;

    TestPreferencesHelper(Context context, PackageManager pm, RankingHandler rankingHandler,
            ZenModeHelper zenHelper, PermissionHelper permHelper, PermissionManager permManager,
            NotificationChannelLogger notificationChannelLogger,
            AppOpsManager appOpsManager, ManagedServices.UserProfiles userProfiles,
            UriGrantsManagerInternal ugmInternal, boolean showReviewPermissionsNotification,
            Clock clock, NotificationManagerPrivate nmPrivate) {
        super(context, pm, rankingHandler, zenHelper, permHelper, permManager,
                notificationChannelLogger, appOpsManager, userProfiles,
                ugmInternal, showReviewPermissionsNotification, clock, nmPrivate);
    }

    @Override
    protected void invalidateNotificationChannelCache() {
        mChannelCacheInvalidated = true;
    }

    @Override
    protected void invalidateNotificationChannelGroupCache() {
        mGroupCacheInvalidated = true;
    }

    boolean hasChannelCacheBeenInvalidated() {
        return mChannelCacheInvalidated;
    }

    boolean hasGroupCacheBeenInvalidated() {
        return mGroupCacheInvalidated;
    }

    void resetCacheInvalidation() {
        mChannelCacheInvalidated = false;
        mGroupCacheInvalidated = false;
    }
}
