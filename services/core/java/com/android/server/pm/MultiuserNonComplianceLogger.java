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

package com.android.server.pm;

import android.annotation.Nullable;
import android.annotation.SpecialUsers.CanBeALL;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.IndentingPrintWriter;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Class used to report events that indicate the potential existence of non-multiuser-compliant
 * features, such as the API calls to check for "main user" (deprecated) and the activities and
 * notifications on headless system user.
 */
final class MultiuserNonComplianceLogger {

    private static final String PROP_ENABLE_IT = "fw.user.log_non_compliance";

    /**
     * When this property is set to {@code true}, the title of the notifications shown in the HSU
     * (Headless System User) will be logged.
     *
     * <p>This is fine because it's unlikely that the title will contain PII:
     *   <ul>
     *     <li>If the target user is USER_SYSTEM, it wouldn't have PII because the HSU is not
     *         associated with a “human” user (for example, it cannot have accounts or apps manually
     *         installed from the app store).
     *     <li>If it's USER_ALL, the same title would be shown to all users – hence, if a
     *         system app / service is adding PII about a particular user in the title of a
     *         notification sent to USER_ALL, it’s leaking that PII to the other users.
     *   </ul>
     */
    @VisibleForTesting
    static final String PROP_SHOW_HSU_NOTIFICATION_TITLE = "fw.user.log_hsu_notification_title";

    private static final int PROP_ENABLED = 1;
    private static final int PROP_DEFAULT = -1;

    private final Context mContext;

    private final Handler mHandler;

    // TODO(b/414326600): merge collections below and/or use the proper proto / structure. For
    // example, instead of having 2 sets for mLaunchedHsuActivities and mBlockedHsuActivities,
    // we should have just one for the activity and an @IntDef with the result.

    /** Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count. */
    @Nullable
    private final SparseIntArray mGetMainUserCalls;

    /** Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count. */
    @Nullable
    private final SparseIntArray mIsMainUserCalls;

    /**
     * Activities launched while the current user is the headless system user.
     *
     * <p>Key is activity name, value is number of times it was launched.
     */
    @Nullable
    private final Map<ComponentName, Integer> mLaunchedHsuActivities;

    /**
     * Activities blocked while the current user is the headless system user.
     *
     * <p>Key is activity name, value is number of times it was blocked.
     */
    @Nullable
    private final Map<ComponentName, Integer> mBlockedHsuActivities;

    /**
     * Notifications posted while the current user is the headless system user.
     *
     * <p>Key is notification, value is number of times it was blocked.
     */
    @Nullable
    private final Map<HsuNotification, Integer> mPostedHsuNotifications;

    /**
     * Notifications blocked while the current user is the headless system user.
     *
     * <p>Key is notification, value is number of times it was blocked.
     */
    @Nullable
    private final Map<HsuNotification, Integer> mBlockedHsuNotifications;

    @VisibleForTesting
    record HsuNotification(
            String pkg,
            @Nullable String tag,
            int id,
            @CanBeALL @UserIdInt int targetUserId,
            int visibility,
            @Nullable String title,
            boolean redacted,
            @Nullable String category,
            @Nullable String channel) {

        HsuNotification {
            if (title == null) {
                redacted = false;
            } else {
                // TODO(b/414326600): current notifications are only logged for the HSU, but if /
                // once the mechanism becomes more generic, it should check that the actual user is
                // the HSU (notice that's different than the target user, which could be USER_ALL).
                redacted = !SystemProperties.getBoolean(PROP_SHOW_HSU_NOTIFICATION_TITLE, false);
                if (redacted) {
                    title = title.length() + "_chars";
                }
            }
        }

        void dump(IndentingPrintWriter pw) {
            pw.print("[pkg="); pw.print(pkg);
            pw.print(", tag="); pw.print(tag);
            pw.print(", id="); pw.print(id);
            pw.print(", targetUserId="); pw.print(targetUserId);
            pw.print(", title=");
            if (title == null) {
                pw.print("null");
            } else if (redacted) {
                // Don't need to wrap with "" (i.e. "title") as it will be X_chars
                pw.printf("%s (redacted)", title);
            } else {
                pw.printf("\"%s\"", title);
            }
            pw.print(", vis="); pw.print(Notification.visibilityToString(visibility));
            pw.print(", category="); pw.print(category);
            pw.print(", channel="); pw.print(channel);
            pw.print("]");
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, channel, id, pkg, redacted, tag, targetUserId, title,
                    visibility);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof HsuNotification)) {
                return false;
            }
            HsuNotification other = (HsuNotification) obj;
            return Objects.equals(category, other.category)
                    && Objects.equals(channel, other.channel) && id == other.id
                    && Objects.equals(pkg, other.pkg) && redacted == other.redacted
                    && Objects.equals(tag, other.tag) && targetUserId == other.targetUserId
                    && Objects.equals(title, other.title) && visibility == other.visibility;
        }
    }

    MultiuserNonComplianceLogger(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        if (Build.isDebuggable()
                || SystemProperties.getInt(PROP_ENABLE_IT, PROP_DEFAULT) == PROP_ENABLED) {
            mGetMainUserCalls = new SparseIntArray();
            mIsMainUserCalls = new SparseIntArray();
            mLaunchedHsuActivities = new LinkedHashMap<>();
            mBlockedHsuActivities = new LinkedHashMap<>();
            mPostedHsuNotifications = new LinkedHashMap<>();
            mBlockedHsuNotifications = new LinkedHashMap<>();
        } else {
            mGetMainUserCalls = null;
            mIsMainUserCalls = null;
            mLaunchedHsuActivities = null;
            mBlockedHsuActivities = null;
            mPostedHsuNotifications = null;
            mBlockedHsuNotifications = null;
        }
    }

    void logGetMainUserCall(int callingUid) {
        logMainUserCall(mGetMainUserCalls, callingUid);
    }

    void logIsMainUserCall(int callingUid) {
        logMainUserCall(mIsMainUserCalls, callingUid);
    }

    private void logMainUserCall(@Nullable SparseIntArray calls, int callingUid) {
        if (calls == null) {
            return;
        }

        mHandler.post(() -> {
            int canonicalUid = UserHandle.getAppId(callingUid);
            int newCount = calls.get(canonicalUid, 0) + 1;
            calls.put(canonicalUid, newCount);
        });
    }

    void logLaunchedHsuActivity(ComponentName activity) {
        incrementCount(mLaunchedHsuActivities, activity);
    }

    void logBlockedHsuActivity(ComponentName activity) {
        incrementCount(mBlockedHsuActivities, activity);
    }

    void logBlockedHsuNotification(StatusBarNotification sbn) {
        logHsuNotification(mBlockedHsuNotifications, sbn);
    }

    void logPostedHsuNotification(StatusBarNotification sbn) {
        logHsuNotification(mPostedHsuNotifications, sbn);
    }

    void logHsuNotification(@Nullable Map<HsuNotification, Integer> notifications,
            StatusBarNotification sbn) {
        if (notifications == null) {
            return;
        }
        Notification notification = sbn.getNotification();
        CharSequence tmpTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        String title = tmpTitle == null ? null : tmpTitle.toString();
        // NOTE: title could have PII, so it must be redacted, but that's done in the
        // HsuNotification constructor.
        // TODO(b/483110541, 414326600): the channel on the notification object is not
        // guaranteed to be the channel the notification is actually posted to, so we might
        // need to add it as an argument (on this method and related places)
        HsuNotification notif = new HsuNotification(
                sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUser().getIdentifier(),
                notification.visibility, title, /* redacted= */ true, notification.category,
                notification.getChannelId());
        incrementCount(notifications, notif);
    }

    private <T> void incrementCount(Map<T, Integer> map, T element) {
        if (map == null) {
            return;
        }
        mHandler.post(() -> {
            Integer currentCount = map.get(element);
            if (currentCount == null) {
                map.put(element, 1);
            } else {
                map.put(element, currentCount + 1);
            }
        });
    }

    void dump(IndentingPrintWriter pw) {
        dumpDeprecatedCalls(pw, "getMainUser", mGetMainUserCalls);
        pw.println();
        dumpDeprecatedCalls(pw, "isMainUser", mIsMainUserCalls);
        pw.println();
        dumpHsuActivities(pw, mBlockedHsuActivities, "blocked");
        pw.println();
        dumpHsuActivities(pw, mLaunchedHsuActivities, "launched");
        pw.println();
        dumpHsuNotifications(pw, mBlockedHsuNotifications, "blocked");
        pw.println();
        dumpHsuNotifications(pw, mPostedHsuNotifications, "posted");
    }

    private void dumpDeprecatedCalls(
            IndentingPrintWriter pw, String method, @Nullable SparseIntArray calls) {
        if (calls == null) {
            pw.printf("Not logging %s() calls\n", method);
            return;
        }

        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done

        // TODO(b/414326600): should also dump on proto, but we need to wait until the format is
        // properly defined (for example, we might want to log a generic "user violation" that would
        // include other metrics such as stuff that shouldn't be called when the current user is the
        // headless system user)
        int size = calls.size();
        pw.printf("%d apps called %s()\n", size, method);
        pw.increaseIndent();
        for (int i = 0; i < size; i++) {
            int canonicalUid = calls.keyAt(i);
            int count = calls.valueAt(i);
            String pkgName = getPackageNameForLoggingPurposes(canonicalUid);
            // uid is the canonical UID, but including "canonical" would add extra churn / bytes
            pw.printf("UID %d (%s): %d calls\n", canonicalUid, pkgName, count);
        }
        pw.decreaseIndent();
    }

    // NOTE: we could squash / refactor the 2 dump() methods below into one, but it would require
    // another String arg for the element ("activities", "notifications") and a lambda to dump the
    // keys, so it's not worth it.
    private void dumpHsuActivities(IndentingPrintWriter pw, Map<ComponentName, Integer> activities,
            String what) {
        if (activities == null) {
            pw.printf("Not logging %s HSU activities\n", what);
            return;
        }
        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done
        int size = activities.size();

        pw.printf("%d activities %s on HSU\n", size, what);
        pw.increaseIndent();
        for (Map.Entry<ComponentName, Integer> entry : activities.entrySet()) {
            String activity = entry.getKey().flattenToShortString();
            int count = entry.getValue();
            pw.printf("%s: %d times\n", activity, count);
        }
        pw.decreaseIndent();
    }

    private void dumpHsuNotifications(IndentingPrintWriter pw,
            Map<HsuNotification, Integer> notifications, String what) {
        if (notifications == null) {
            pw.printf("Not logging %s HSU notifications\n", what);
            return;
        }

        int size = notifications.size();
        pw.printf("%d notifications %s on HSU\n", size, what);
        pw.increaseIndent();
        for (Map.Entry<HsuNotification, Integer> entry : notifications.entrySet()) {
            entry.getKey().dump(pw);
            pw.printf(": %d times\n", entry.getValue());
        }
        pw.decreaseIndent();
    }

    void reset() {
        // TODO(b/414326600): should reset in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done

        clear(mGetMainUserCalls);
        clear(mIsMainUserCalls);
        clear(mBlockedHsuActivities);
        clear(mLaunchedHsuActivities);
        clear(mBlockedHsuNotifications);
        clear(mPostedHsuNotifications);
    }

    private static void clear(@Nullable SparseIntArray array) {
        if (array != null) {
            array.clear();
        }
    }

    private static void clear(@Nullable Map<?, ?> map) {
        if (map != null) {
            map.clear();
        }
    }

    private String getPackageNameForLoggingPurposes(int uid) {
        // pkgs is either null or an array with one or more elements.
        String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) {
            // The caller package might have been uninstsalled or not installed for user 0.
            return "unknown";
        }
        if (pkgs.length > 1) {
            // The UID is shared by multiple packages with sharedUserId.
            return "shared";
        }
        return pkgs[0];
    }
}
