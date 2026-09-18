/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import static com.android.systemui.activity.data.repository.ActivityIntentRepository.toActivityInfo;
import static com.android.systemui.activity.data.repository.ActivityIntentRepository.getTargetActivityInfoFlags;
import static com.android.systemui.activity.data.repository.ActivityIntentRepository.wouldActivityInfoShowOverLockscreen;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.systemui.dagger.SysUISingleton;

import java.util.List;

import javax.inject.Inject;

/**
 * Contains useful methods for querying properties of an Activity Intent.
 *
 * Instead of adding new functionality here, please add new functionality to
 * [com.android.systemui.activity.data.repository.ActivityIntentRepository].
 */
@SysUISingleton
@SuppressLint("MissingPermission")
public class ActivityIntentHelper {

    private final PackageManager mPm;

    @Inject
    public ActivityIntentHelper(Context context) {
        // TODO: inject a package manager, not a context.
        mPm = context.getPackageManager();
    }

    /**
     * Determines if sending the given intent would result in starting an Intent resolver activity,
     * instead of resolving to a specific component.
     *
     * @param intent the intent
     * @param currentUserId the id for the user to resolve as
     * @return true if the intent would launch a resolver activity
     */
    public boolean wouldLaunchResolverActivity(Intent intent, int currentUserId) {
        ActivityInfo targetActivityInfo = getTargetActivityInfo(intent, currentUserId,
                false /* onlyDirectBootAware */);
        return targetActivityInfo == null;
    }

    /**
     * @see #wouldLaunchResolverActivity(Intent, int)
     */
    public boolean wouldPendingLaunchResolverActivity(PendingIntent intent, int currentUserId) {
        ActivityInfo targetActivityInfo = getPendingTargetActivityInfo(intent, currentUserId);
        return targetActivityInfo == null;
    }

    /**
     * Returns info about the target Activity of a given intent, or null if the intent does not
     * resolve to a specific component meeting the requirements.
     *
     * @param onlyDirectBootAware a boolean indicating whether the matched activity packages must
     *         be direct boot aware when in direct boot mode if false, all packages are considered
     *         a match even if they are not aware.
     * @return the target activity info of the intent it resolves to a specific package or
     *         {@code null} if it resolved to the resolver activity
     */
    public ActivityInfo getTargetActivityInfo(Intent intent, int currentUserId,
            boolean onlyDirectBootAware) {
        int flags = getTargetActivityInfoFlags(onlyDirectBootAware);
        final List<ResolveInfo> appList = mPm.queryIntentActivitiesAsUser(
                intent, flags, currentUserId);
        return toActivityInfo(appList, intent, flags, currentUserId, mPm);
    }

    private ActivityInfo getPendingTargetActivityInfo(PendingIntent intent, int currentUserId) {
        int flags = getTargetActivityInfoFlags(/* onlyDirectBootAware= */ false);
        final List<ResolveInfo> appList = intent.queryIntentComponents(flags);
        return toActivityInfo(appList, intent.getIntent(), flags, currentUserId, mPm);
    }

    /**
     * Determines if the given intent resolves to an Activity which is allowed to appear above
     * the lock screen.
     *
     * @param intent the intent to resolve
     * @return true if the launched Activity would appear above the lock screen
     */
    public boolean wouldShowOverLockscreen(Intent intent, int currentUserId) {
        ActivityInfo targetActivityInfo = getTargetActivityInfo(intent,
                currentUserId, false /* onlyDirectBootAware */);
        return wouldActivityInfoShowOverLockscreen(targetActivityInfo);
    }

    /**
     * @see #wouldShowOverLockscreen(Intent, int)
     *
     * @deprecated due to making binder calls on the main thread. Use
     * [ActivityIntentRepository.wouldPendingShowOverLockscreen] instead.
     */
    @Deprecated
    public boolean wouldPendingShowOverLockscreen(PendingIntent intent, int currentUserId) {
        ActivityInfo targetActivityInfo = getPendingTargetActivityInfo(intent, currentUserId);
        return wouldActivityInfoShowOverLockscreen(targetActivityInfo);
    }
}
