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

package com.android.server.wm;

import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_SUCCESS;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.pm.GenericAllowlist.AllowlistStatus;
import static com.android.server.pm.UserActivitiesAllowlist.ALLOWLIST_MODE_ENABLED;
import static com.android.server.pm.UserActivitiesAllowlist.ALLOWLIST_MODE_DISABLED;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_USER_VISIBILITY;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_ATM;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_WITH_CLASS_NAME;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.pm.UserActivitiesAllowlist;
import com.android.server.pm.UserManagerInternal;
import com.android.server.utils.Slogf;
import com.android.server.wm.ActivityStarter.Request;

import java.io.PrintWriter;

// NOTE: UserController would be be a better name, but there's one on c.a.server.am. already...
/**
 * Provides integration with user-related features like activities allowlist.
 */
final class UserHelper {

    private static final String TAG = TAG_WITH_CLASS_NAME
            ? UserHelper.class.getSimpleName()
            : TAG_ATM;

    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED = 1;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM = -1;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST = -2;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_EXPLICITLY = -3;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_INVALID_MODE = -4;

    @IntDef(prefix = { "ACTIVITY_LAUNCH_INTEGRATION_STATUS_" }, value = {
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_EXPLICITLY,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_INVALID_MODE
    })
    private @interface ActivityLaunchIntegrationStatus {}

    private final boolean mIsHeadlessSystemUserMode;
    private final UserManagerInternal mUmi;
    private final @Nullable UserActivitiesAllowlist mHsuActivitiesAllowlist;

    UserHelper(UserManagerInternal umi) {
        mIsHeadlessSystemUserMode = umi.isHeadlessSystemUserMode();
        mUmi = umi;
        mHsuActivitiesAllowlist = mIsHeadlessSystemUserMode
                ? mUmi.getActivitiesAllowlist(USER_SYSTEM)
                : null;
    }

    private @ActivityLaunchIntegrationStatus int getActivityLaunchIntegrationStatus() {
        if (!mIsHeadlessSystemUserMode) {
            return ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM;
        }
        if (mHsuActivitiesAllowlist == null) {
            return ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST;
        }
        int mode = mHsuActivitiesAllowlist.getMode();
        return switch (mode) {
            case ALLOWLIST_MODE_ENABLED -> ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED;
            case ALLOWLIST_MODE_DISABLED -> ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_EXPLICITLY;
            default -> {
                Slogf.e(TAG, "invalid HSU activity allowlist mode: %d", mode);
                yield ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_INVALID_MODE;
            }
        };
    }

    /**
     * Checks if the request is valid.
     *
     * @return {@code START_SUCCESS} is valid, or specific error code if it isn't.
     */
    public int checkRequest(Request request) {
        int activityLaunchIntegrationStatus = getActivityLaunchIntegrationStatus();
        if (activityLaunchIntegrationStatus != ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "checkRequest(%s): skipping because status is %s", request,
                        activityLaunchIntegrationStatusToString(activityLaunchIntegrationStatus));
            }
            return START_SUCCESS;
        }

        // Currently, the goal of the allowlist is to avoid activities being shown when the current
        // user is the HSU, so it's explicitly checking for these conditions, i.e.:
        // - Device is HSUM
        // - Current user is the HSU (regardless of userId in the request)
        // - Allowlist for HSU is enabled
        //
        // In the future, if we want to extend this mechanism to all users, we should check if the
        // activity is allowed to any user visible in the display insteads.
        int currentUserId = getCurrentUserId();
        if (currentUserId != USER_SYSTEM) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "checkRequest(%s): skipping because current user (id=%d) is not the "
                        + "Headless System User", request, currentUserId);
            }
            return START_SUCCESS;
        }

        ActivityInfo aInfo = request.activityInfo;
        if (aInfo == null) {
            // The caller should have checked before, but it doesn't hurt to double check...
            Slogf.wtf(TAG, "checkRequest(%s): returning START_CLASS_NOT_FOUND because aInfo is "
                    + "null", request);
            return START_CLASS_NOT_FOUND;
        }

        Intent intent = request.intent;
        var compName = intent.getComponent();
        if (compName == null) {
            // Should not be happen, but better be safe than sorry....
            Slogf.wtf(TAG, "Could not check if %s is allowed for HSU because its intent (%s) "
                    + "doesn't have a Component Name", aInfo, intent);
            return START_SUCCESS;
        }

        request.userAllowlistStatus = mHsuActivitiesAllowlist.getAllowlistStatus(compName);
        if (!UserActivitiesAllowlist.isAllowed(request.userAllowlistStatus)) {
            int userId = getUserId(aInfo);
            if (userId == USER_SYSTEM) {
                Slogf.w(TAG, "Activity %s not allowed for system user",
                        compName.flattenToShortString());
            } else {
                Slogf.w(TAG, "Activity %s is intended for user %d, but it's not allowlisted for "
                        + "USER_SYSTEM (which is the current user)",
                        compName.flattenToShortString(), userId);
            }
            mUmi.logActivityLaunchStatus(compName, USER_SYSTEM, request.userAllowlistStatus);
            return START_NOT_ALLOWED_FOR_USER;
        }

        if (DEBUG_USER_VISIBILITY) {
            Slogf.d(TAG, "Activity %s (intended for user %d) is allowlisted for USER_SYSTEM (which "
                    + "is the current user)", compName.flattenToShortString(), getUserId(aInfo));
        }

        return START_SUCCESS;
    }

    private @UserIdInt int getCurrentUserId() {
        return ActivityManager.getCurrentUser();
    }

    /**
     * Logs whether the given activity was started.
     */
    public void logActivityStarted(ActivityRecord started, boolean isStarted,
            @AllowlistStatus int allowlistStatus) {
        if (!isStarted || !mIsHeadlessSystemUserMode) {
            return;
        }

        logActivityStarted(started.mUserId, started.mActivityComponent, allowlistStatus);
    }

    // NOTE: method below is only used by UserHelperTest, otherwise it would be a pain to create a
    // ActivityRecord to test the "real" method above...
    @VisibleForTesting
    void logActivityStarted(@UserIdInt int userId, ComponentName started,
            @AllowlistStatus int allowlistStatus) {
        if (userId != USER_SYSTEM) {
            return;
        }
        mUmi.logActivityLaunchStatus(started, userId, allowlistStatus);
    }

    /**
     * Gets the id of the user associated with the application info, or {@link #USER_SYSTEM} when
     * it's not set.
     */
    static @UserIdInt int getUserId(@Nullable ActivityInfo aInfo) {
        return aInfo != null && aInfo.applicationInfo != null
                ? UserHandle.getUserId(aInfo.getUid())
                : USER_SYSTEM;
    }

    /**
     * Dumps its internal state.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.printf("%sUserHelper:\n", prefix);
        String prefix2 = prefix + "  ";
        pw.printf("%sTAG=%s\n", prefix2, TAG);
        pw.printf("%smIsHeadlessSystemUserMode=%b\n", prefix2, mIsHeadlessSystemUserMode);
        int activityLaunchIntegrationStatus = getActivityLaunchIntegrationStatus();
        pw.printf("%sactivityLaunchIntegrationStatus=%d (%s)\n", prefix2,
                activityLaunchIntegrationStatus,
                activityLaunchIntegrationStatusToString(activityLaunchIntegrationStatus));
        pw.printf("%smHsuActivitiesAllowlist=%s\n", prefix2, mHsuActivitiesAllowlist);
    }

    private static String activityLaunchIntegrationStatusToString(@ActivityLaunchIntegrationStatus
            int status) {
        // Cannot use DebugUtils.constantToString because status constants are private
        return switch (status) {
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED -> "ENABLED";
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM -> "DISABLED_NOT_HSUM";
            case
                ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST -> "DISABLED_NO_ALLOWLIST";
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_EXPLICITLY -> "DISABLED_EXPLICITLY";
            case
                ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_INVALID_MODE -> "DISABLED_INVALID_MODE";
            default -> "UNKNOWN_" + status;
        };
    }
}
