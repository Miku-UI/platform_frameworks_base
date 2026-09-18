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

import static android.app.Flags.nmContextualDisplayLaunch;
import static android.app.Notification.FLAG_PROMOTED_ONGOING;
import static android.app.NotificationLoggingConstants.DATA_TYPE_NOTIFICATION_RULES;
import static android.app.NotificationLoggingConstants.ERROR_XML_PARSING;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_NONE;
import static android.app.NotificationRule.Filter.CONVERSATION_LEVEL_PRIORITY;
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.app.NotificationRule.RULE_TAG;
import static android.app.NotificationRule.USER_ATTR;
import static android.os.UserHandle.USER_ALL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.service.notification.Adjustment.KEY_BREAKTHROUGH_ALL_MODES;
import static android.service.notification.Adjustment.KEY_DYNAMIC_BUNDLE;
import static android.service.notification.Adjustment.KEY_HIGHLIGHT;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_LIGHT;
import static android.service.notification.Adjustment.KEY_MODE_BREAKTHROUGH_LIST;
import static android.service.notification.Adjustment.KEY_SOUND;
import static android.service.notification.Adjustment.KEY_TYPE;
import static android.service.notification.Adjustment.TYPE_NEWS;
import static android.service.notification.Adjustment.TYPE_PROMOTION;

import static com.android.server.notification.ManagedServices.ATT_USER_ID;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.ATT_DENIED_KEY;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.ATT_DENIED_KEY_APPS;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.ATT_USER_LIST;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.TAG_DENIED;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.TAG_DENIED_KEY;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.TAG_ENABLED_TYPES;
import static com.android.server.notification.NotificationManagerService.NotificationAssistants.TAG_SET_BY_USERS;
import static com.android.server.notification.NotificationManagerService.NotificationListeners.ATT_TYPES;
import static com.android.server.notification.NotificationManagerService.TAG_NOTIFICATION_RULES;
import static com.android.server.pm.UserManagerInternal.USER_FILTER_WITH_DYING_USERS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.NotificationChannel;
import android.app.NotificationRule;
import android.app.backup.BackupRestoreEventLogger;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.Adjustment;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class manages each user's {@link NotificationRule}s. It also provides some helper methods
 * to convert and sort Adjustments according to rule behavior and priority.
 */
public class NotificationRuleManager {
    private static final String TAG = "NotificationRuleManager";
    private final Object mLock = new Object();
    private Context mContext;
    private NotificationManagerPrivate mNmPrivate;
    private UserManagerInternal mUmInternal;
    PackageManagerInternal mPmInternal;

    // tags and attributes for persistence
    private final int mVersion = 1;
    private static final String VERSION_ATTR = "version";
    private static final String RULES_TAG = "rules";

    // key: user id. value: notification rules in priority order
    private final Map<Integer, List<NotificationRule>> mNotificationRules = new ArrayMap<>();

    // Bundles
    static final Integer[] DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES = new Integer[] {
            TYPE_PROMOTION,
            TYPE_NEWS
    };

    // Map of user ID -> set of allowed classification types for that user.
    @GuardedBy("mLock")
    private final Map<Integer, Set<Integer>> mAllowedClassificationTypes = new ArrayMap<>();
    // Set of user IDs for which the classification setting was ever explicitly changed (in
    // other words, the current setting -- allowed or disallowed -- is not default). Used for
    // handling default behavior for profiles until the user sets a preference.
    @GuardedBy("mLock")
    private Set<Integer> mClassificationPrefSetByUsers = new ArraySet<>();
    // Map of user ID -> the disallowed packages for {@link Adjustment#KEY_TYPE}
    @GuardedBy("mLock")
    private final Map<Integer, Set<String>> mClassificationDeniedPackages =
            new ArrayMap<>();


    public NotificationRuleManager(Context context, NotificationManagerPrivate nmPrivate) {
        mContext = context;
        mNmPrivate = nmPrivate;
        mUmInternal = LocalServices.getService(UserManagerInternal.class);
        mPmInternal = LocalServices.getService(PackageManagerInternal.class);
    }

    // for bugreports
    void dump(PrintWriter pw) {
        synchronized (mLock) {
            for (@UserIdInt int user : mNotificationRules.keySet()) {
                pw.println("  User " + user);
                for (NotificationRule rule : mNotificationRules.get(user)) {
                    pw.println("    rule: " + rule);
                }
            }
            pw.println("  Allowed bundle types: ");
            for (int userId : mAllowedClassificationTypes.keySet()) {
                pw.println("    user " + userId + ": " + mAllowedClassificationTypes.get(userId));
            }
        }
    }

    // for backup and restore
    void readXml(TypedXmlPullParser parser, boolean forRestore, @UserIdInt int userId,
            @Nullable BackupRestoreEventLogger logger) throws XmlPullParserException, IOException {
        if (!forRestore && userId != USER_ALL) {
            throw new IllegalArgumentException("reading from disk with userId != USER_ALL");
        }
        int type = parser.getEventType();
        if (type != XmlPullParser.START_TAG) return;
        String tag = parser.getName();
        if (!TAG_NOTIFICATION_RULES.equals(tag)) return;
        int successfulReads = 0;
        int unsuccessfulReads = 0;
        synchronized (mLock) {
            type = parser.next();
            tag = parser.getName();
            while (type != XmlPullParser.END_DOCUMENT
                    && !(TAG_NOTIFICATION_RULES.equals(tag) && type == XmlPullParser.END_TAG)) {
                tag = parser.getName();
                if (RULE_TAG.equals(tag) && type == XmlPullParser.START_TAG) {
                    int ruleUser = parser.getAttributeInt(null, USER_ATTR, USER_ALL);
                    int targetUser = forRestore ? userId : ruleUser;
                    if (targetUser != USER_ALL) {
                        try {
                            NotificationRule rule = NotificationRule.readXml(parser, forRestore,
                                    mContext);
                            List<NotificationRule> rulesForUser =
                                    mNotificationRules.getOrDefault(targetUser, new ArrayList<>());
                            // the static bundle rule might already exist if we're upgrading
                            // since the policy xml is read before the rules xml. update it with
                            // the latest info if so.
                            if (RESERVED_ID_STATIC_BUNDLES == rule.getId() &&
                                    doesStaticBundleRuleExist(targetUser)) {
                                updateNotificationRule(targetUser, rule);
                            } else {
                                rulesForUser.add(rule);
                                mNotificationRules.put(targetUser, rulesForUser);
                            }
                            successfulReads++;
                        } catch (Exception e) {
                            Slog.d(TAG, "failed to restore rule", e);
                            unsuccessfulReads++;
                        }
                    } else {
                        unsuccessfulReads++;
                    }
                }
                type = parser.next();
                tag = parser.getName();
            }

            if (logger != null) {
                logger.logItemsRestored(DATA_TYPE_NOTIFICATION_RULES, successfulReads);
                if (unsuccessfulReads > 0) {
                    logger.logItemsRestoreFailed(
                            DATA_TYPE_NOTIFICATION_RULES, unsuccessfulReads, ERROR_XML_PARSING);
                }
            }
        }
    }

    void writeXml(TypedXmlSerializer out, boolean forBackup, @UserIdInt int userId,
            @Nullable BackupRestoreEventLogger logger)
            throws IOException {
        if (!forBackup && userId != USER_ALL) {
            throw new IllegalArgumentException("writing to disk with userId != USER_ALL");
        }
        synchronized (mLock) {
            out.startTag(null, TAG_NOTIFICATION_RULES);
            out.attributeInt(null, VERSION_ATTR, mVersion);
            out.startTag(null, RULES_TAG);
            int persistedRules = 0;
            for (@UserIdInt int user : mNotificationRules.keySet()) {
                if (userId == USER_ALL || userId == user) {
                    for (NotificationRule rule : mNotificationRules.get(user)) {
                        try {
                            rule.writeXml(out, forBackup, user, mContext);
                            persistedRules++;
                        } catch (Exception e) {
                            Slog.d(TAG, "Failed to backup " + rule.getId(), e);
                        }
                    }
                }
            }
            out.endTag(null, RULES_TAG);
            // TODO(b/478826998): add default highlighted behavior
            out.endTag(null, TAG_NOTIFICATION_RULES);

            if (logger != null) {
                logger.logItemsBackedUp(DATA_TYPE_NOTIFICATION_RULES, persistedRules);
            }
        }
    }

    // Bundles block

    @GuardedBy("mLock")
    protected void addDefaultClassificationTypes(int userId) {
        if (nmContextualDisplayLaunch()) {
            Slog.wtf(TAG, new IllegalStateException("Legacy method called with flag enabled"));
            return;
        }
        // Add the default classification types if the list is empty or not present.
        // Will do so for the profile's parent if the user ID is a profile user.
        final @UserIdInt int parentId = mUmInternal.getProfileParentId(userId);
        // will be null if not present
        Set<Integer> allowedTypes = mAllowedClassificationTypes.get(parentId);
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            mAllowedClassificationTypes.put(parentId,
                    new ArraySet<>(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES));
        }
    }

    // Convenience method to enforce defaults and shared settings:
    // - if the passed-in user is a profile user, get the data for its parent instead, as
    //   the allowed classification types are shared across all profiles of a full user.
    // - if a user's allowed classification types do not exist yet, return the default set of
    //   permitted classification types.
    // - if addIfNotPresent is true, then this method will additionally ADD the default set
    //   of adjustments for the relevant user and insert it into the allowed classification
    //   types map. This is for ease of modification when enabling/disabling types.
    @GuardedBy("mLock")
    private Set<Integer> allowedClassificationTypesForUser(@UserIdInt int userId,
            boolean addIfNotPresent) {
        if (nmContextualDisplayLaunch()) {
            Slog.wtf(TAG, new IllegalStateException("Legacy method called with flag enabled"));
            return null;
        }
        // if userId does not refer to a profile user, parentId will just be the same as userId
        final @UserIdInt int parentId = mUmInternal.getProfileParentId(userId);
        if (addIfNotPresent) {
            mAllowedClassificationTypes.putIfAbsent(parentId,
                    new ArraySet<>(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES));
        }
        return mAllowedClassificationTypes.containsKey(parentId)
                ? mAllowedClassificationTypes.get(parentId)
                : new ArraySet<>(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES);
    }

    protected boolean isClassificationTypeAllowed(@UserIdInt int userId, int type) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                return staticBundleRule.getFilters().getFirst()
                        .getStaticBundleTypes().contains(type);
            } else {
                return allowedClassificationTypesForUser(userId, false).contains(type);
            }
        }
    }

    // TODO(b/438704204): this entire method should be removed when nmContextualDisplayLaunch is
    // cleaned up
    protected void writeLegacyBundleStorageTags(TypedXmlSerializer out) throws IOException {
        if (nmContextualDisplayLaunch()) {
            for (int user : mNotificationRules.keySet()) {
                out.startTag(null, TAG_ENABLED_TYPES);
                out.attributeInt(null, ATT_USER_ID, user);
                out.attribute(null, ATT_TYPES,
                        TextUtils.join(",", getAllowedClassificationTypes(user)));
                out.endTag(null, TAG_ENABLED_TYPES);
            }

            List<Integer> classificationPrefSetByUsers = new ArrayList<>();
            for (int user : mNotificationRules.keySet()) {
                NotificationRule staticBundleRule = getStaticBundleRule(user);
                List<UserHandle> users = staticBundleRule.getFilters().getFirst().getUsers();
                if (users.isEmpty() || !users.contains(UserHandle.of(user))) {
                    classificationPrefSetByUsers.add(user);
                }
                Optional<UserHandle> profileUser = users.stream().filter(userHandle ->
                        !UserHandle.of(user).equals(userHandle)).findFirst();
                if (profileUser.isPresent()) {
                    classificationPrefSetByUsers.add(profileUser.get().getIdentifier());
                }
            }
            out.startTag(null, TAG_SET_BY_USERS);
            out.attribute(null, ATT_USER_LIST, TextUtils.join(",", classificationPrefSetByUsers));
            out.endTag(null, TAG_SET_BY_USERS);

            for (int user : mNotificationRules.keySet()) {
                Set<String> pkgs = getClassificationDeniedPackages(user);
                if (!pkgs.isEmpty()) {
                    out.startTag(null, TAG_DENIED_KEY);
                    out.attributeInt(null, ATT_USER_ID, user);
                    out.attribute(null, ATT_DENIED_KEY, KEY_TYPE);
                    out.attribute(null, ATT_DENIED_KEY_APPS, TextUtils.join(",", pkgs));
                    out.endTag(null, TAG_DENIED_KEY);
                }
            }
        } else {
            for (int user : mAllowedClassificationTypes.keySet()) {
                out.startTag(null, TAG_ENABLED_TYPES);
                out.attributeInt(null, ATT_USER_ID, user);
                out.attribute(null, ATT_TYPES,
                        TextUtils.join(",", mAllowedClassificationTypes.get(user)));
                out.endTag(null, TAG_ENABLED_TYPES);
            }
            out.startTag(null, TAG_SET_BY_USERS);
            out.attribute(null, ATT_USER_LIST, TextUtils.join(",", mClassificationPrefSetByUsers));
            out.endTag(null, TAG_SET_BY_USERS);

            for (int user : mClassificationDeniedPackages.keySet()) {
                Set<String> pkgs = mClassificationDeniedPackages.get(user);
                if (pkgs != null && !pkgs.isEmpty()) {
                    out.startTag(null, TAG_DENIED_KEY);
                    out.attributeInt(null, ATT_USER_ID, user);
                    out.attribute(null, ATT_DENIED_KEY, KEY_TYPE);
                    out.attribute(null, ATT_DENIED_KEY_APPS, TextUtils.join(",", pkgs));
                    out.endTag(null, TAG_DENIED_KEY);
                }
            }
        }
    }

    /**
     * Reads bundle information from the legacy notification_policy xml (or from a legacy restore).
     *
     * If the nmContextualDisplayLaunch flag is off, just read this into the legacy data structures
     *
     * If the flag is on, we need to consider whether this is a fresh install, upgrader, or a
     * restore on a previous fresh install
     *
     * First boot:
     * In the fresh install scenario, this method is never called, so the default bundle definition
     * is created from {@link #onUserAdded(int)}.
     *
     * In the upgrade scenario, this method is called before {@link #onUserAdded(int)}. The bundle
     * rule definition will be created from the user's previous settings. When we're initializing
     * the system rules, the static bundle rule creation will be skipped because it already exists.
     *
     * In the B&R scenario, we have a default bundle rule from {@link #onUserAdded(int)}. We should
     * overwrite any conflicting settings from this xml.
     *
     * On subsequent boots with the flag enabled, the bundle rule will be created from this legacy
     * xml. However, that information might be replaced with newer user settings when we read the
     * notification_rules xml.
     */
    protected void readLegacyBundleStorageTag(TypedXmlPullParser parser, String tag)
            throws IOException {
        if (TAG_ENABLED_TYPES.equals(tag)) {
            readAllowedClassificationTypes(parser);
        } else if (TAG_SET_BY_USERS.equals(tag)) {
            readSetByUsersTag(parser);
        } else if (TAG_DENIED.equals(tag)) {
            readLegacyClassificationAdjustmentStateTags(parser);
        } else if (TAG_DENIED_KEY.equals(tag)) {
            readClassificationDeniedPkgsTag(parser);
        }
    }

    private void readLegacyClassificationAdjustmentStateTags(TypedXmlPullParser parser) {
        final int user = XmlUtils.readIntAttribute(parser, ATT_USER_ID,
                mContext.getUserId());
        final String keys = XmlUtils.readStringAttribute(parser, ATT_TYPES);
        synchronized (mLock) {
            boolean disabled = Arrays.asList(keys.split(",")).contains(KEY_TYPE);
            getOrCreateStaticBundleRule(user);
            for (int userId : mUmInternal.getProfileIds(user, false)) {
                UserInfo userInfo = mUmInternal.getUserInfo(userId);
                if (userInfo.isFull() || userInfo.isManagedProfile()) {
                    setClassificationAdjustmentState(userId, !disabled);
                }
            }
        }
    }

    private void readAllowedClassificationTypes(TypedXmlPullParser parser) {
        final int user = XmlUtils.readIntAttribute(parser, ATT_USER_ID,
                mContext.getUserId());
        final String types = XmlUtils.readStringAttribute(parser, ATT_TYPES);
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getOrCreateStaticBundleRule(user);
                List<Integer> staticBundleTypes =
                        staticBundleRule.getFilters().getFirst().getStaticBundleTypes();
                staticBundleTypes.clear();
                if (!TextUtils.isEmpty(types)) {
                    List<String> typeList = Arrays.asList(types.split(","));
                    for (String type : typeList) {
                        staticBundleTypes.add(Integer.parseInt(type));
                    }
                }
            } else {
                Set<Integer> userAllowedTypes = mAllowedClassificationTypes.getOrDefault(user,
                        new ArraySet<>());
                userAllowedTypes.clear();
                if (!TextUtils.isEmpty(types)) {
                    List<String> typeList = Arrays.asList(types.split(","));
                    for (String type : typeList) {
                        try {
                            userAllowedTypes.add(Integer.parseInt(type));
                        } catch (NumberFormatException e) {
                            Slog.wtf(TAG, "Bad integer specified", e);
                        }
                    }
                    mAllowedClassificationTypes.put(user, userAllowedTypes);
                }
            }
        }
    }

    /**
     * Contains a list of users where the user manually changed the 'classification allowed'
     * setting.
     *
     * Although the values are written for all users, it's only used to determine if classification
     * is disabled for work profile users. That is, if there is a work profile user, and it does not
     * have an entry in this list, then classification should be disabled for that profile.
     */
    private void readSetByUsersTag(TypedXmlPullParser parser) {
        final String users = XmlUtils.readStringAttribute(parser, ATT_USER_LIST);
        if (!TextUtils.isEmpty(users)) {
            if (nmContextualDisplayLaunch()) {
                List<Integer> userSet = new ArrayList<>();
                for (String userIdString : Arrays.asList(users.split(","))) {
                    userSet.add(Integer.parseInt(userIdString));
                }
                for (UserInfo user : mUmInternal.getUsers(USER_FILTER_WITH_DYING_USERS)) {
                    if (user.isManagedProfile() && !userSet.contains(user.id)) {
                        int parent = mUmInternal.getProfileParentId(user.id);
                        NotificationRule staticBundleRule = getOrCreateStaticBundleRule(parent);
                        staticBundleRule.getFilters().getFirst().getUsers()
                                .remove(UserHandle.of(user.id));
                    }
                }
            } else {
                for (String userIdString : Arrays.asList(users.split(","))) {
                    try {
                        setClassificationPrefSetByUser(Integer.parseInt(userIdString), true);
                    } catch (NumberFormatException e) {
                        Slog.wtf(TAG, "Bad type specified", e);
                    }
                }
            }
        }
    }

    private @NonNull NotificationRule getOrCreateStaticBundleRule(@UserIdInt int userId) {
        if (!doesStaticBundleRuleExist(userId)) {
            addNotificationRule(userId, getDefaultStaticBundleRule(userId));
        }
        return getStaticBundleRule(userId);
    }

    @NonNull NotificationRule getStaticBundleRule(@UserIdInt int userId) {
        int parentOrSelf = mUmInternal.getProfileParentId(userId);
        return getNotificationRule(parentOrSelf, RESERVED_ID_STATIC_BUNDLES);
    }

    private void readClassificationDeniedPkgsTag(TypedXmlPullParser parser) {
        final int user = XmlUtils.readIntAttribute(parser, ATT_USER_ID,
                mContext.getUserId());
        final String key = XmlUtils.readStringAttribute(parser, ATT_DENIED_KEY);
        final String pkgs = XmlUtils.readStringAttribute(parser, ATT_DENIED_KEY_APPS);
        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(pkgs)) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getOrCreateStaticBundleRule(user);
                List<Integer> excluded =
                        staticBundleRule.getFilters().getFirst().getExcludedPackageUids();
                excluded.clear();
                for (String pkg : pkgs.split(",")) {
                    setClassificationSupportedForPackage(user, pkg, false);
                }
            } else {
                Set<String> userDeniedPackages =
                        mClassificationDeniedPackages.getOrDefault(user, new ArraySet<>());
                List<String> pkgList = Arrays.asList(pkgs.split(","));
                userDeniedPackages.addAll(pkgList);
                mClassificationDeniedPackages.put(user, userDeniedPackages);
            }
        }
    }

    public @NonNull List<Integer> getAllowedClassificationTypes(@UserIdInt int userId) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                return staticBundleRule.getFilters().getFirst().getStaticBundleTypes();
            } else {
                return allowedClassificationTypesForUser(userId, false).stream().toList();
            }
        }
    }

    public void setAssistantClassificationTypeState(@UserIdInt int userId,
            int type, boolean enabled) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                if (enabled) {
                    if (!staticBundleRule.getFilters().getFirst().getStaticBundleTypes()
                            .contains(type)) {
                        staticBundleRule.getFilters().getFirst().getStaticBundleTypes().add(type);
                    }
                } else {
                    staticBundleRule.getFilters().getFirst().getStaticBundleTypes().remove(
                            new Integer(type));
                }
            } else {
                if (enabled) {
                    allowedClassificationTypesForUser(userId, true).add(type);
                } else {
                    allowedClassificationTypesForUser(userId, true).remove(type);
                }
            }
        }
    }

    private void setClassificationPrefSetByUser(@UserIdInt int user, boolean setByUser) {
        synchronized (mLock) {
            if (setByUser) {
                mClassificationPrefSetByUsers.add(user);
            } else {
                mClassificationPrefSetByUsers.remove(user);
            }
        }
    }

    public boolean isClassificationAllowedForManagedProfile(@UserIdInt int userId) {
        if (nmContextualDisplayLaunch()) {
           Slog.wtf(TAG, new IllegalStateException("Legacy method called with flag enabled"));
            return false;
        }
        synchronized (mLock) {
            return mClassificationPrefSetByUsers.contains(userId);
        }
    }

    protected @NonNull Set<String> getClassificationDeniedPackages(@UserIdInt int userId) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                List<Integer> excludedUids =
                        staticBundleRule.getFilters().getFirst().getExcludedPackageUids();
                Set<String> excludedPackages = new ArraySet<>();
                for (int excludedUid : excludedUids) {
                    if (UserHandle.getUserId(excludedUid) == userId) {
                        excludedPackages.add(mPmInternal.getPackage(excludedUid).getPackageName());
                    }
                }
                return excludedPackages;
            } else {
                if (mClassificationDeniedPackages.containsKey(userId)) {
                    return mClassificationDeniedPackages.getOrDefault(userId,
                            new ArraySet<>());
                }
            }
        }
        return new ArraySet<>();
    }

    protected boolean isClassificationAllowedForPackage(@UserIdInt int userId, String pkg) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                List<Integer> excludedUids =
                        staticBundleRule.getFilters().getFirst().getExcludedPackageUids();
                int targetUid = mPmInternal.getPackageUid(pkg, 0L, userId);
                return !excludedUids.contains(targetUid);
            } else {
                if (mClassificationDeniedPackages.containsKey(userId)) {
                    return !mClassificationDeniedPackages.getOrDefault(userId,
                            new ArraySet<>()).contains(pkg);
                }
            }
        }
        return true;
    }

    public void setClassificationSupportedForPackage(@UserIdInt int userId, String pkg,
            boolean enabled) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                NotificationRule staticBundleRule = getStaticBundleRule(userId);
                List<Integer> excludedUids =
                        staticBundleRule.getFilters().getFirst().getExcludedPackageUids();
                int uid = mPmInternal.getPackageUid(pkg, 0L, userId);
                if (enabled) {
                    excludedUids.remove(new Integer(uid));
                } else {
                    excludedUids.add(uid);
                }
            } else {
                mClassificationDeniedPackages.putIfAbsent(userId, new ArraySet<>());
                if (enabled) {
                    mClassificationDeniedPackages.get(userId).remove(pkg);
                } else {
                    mClassificationDeniedPackages.get(userId).add(pkg);
                }
            }
        }
    }

    /**
     * Enables or disables the static bundle rule for a user.
     */
    protected void setClassificationAdjustmentState(@UserIdInt int userId, boolean enabled) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                int parentOrSelf = mUmInternal.getProfileParentId(userId);
                NotificationRule staticBundleRule = getStaticBundleRule(parentOrSelf);
                NotificationRule.Filter filter = staticBundleRule.getFilters().getFirst();
                if (userId == parentOrSelf) {
                    staticBundleRule.setEnabled(enabled);
                }
                UserHandle user = UserHandle.of(userId);
                if (enabled && !filter.getUsers().contains(user)) {
                    filter.getUsers().add(user);
                }

                if (!enabled) {
                    filter.getUsers().remove(user);
                    if (filter.getStaticBundleTypes().isEmpty()) {
                        filter.getStaticBundleTypes().addAll(
                                List.of(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES));
                    }
                }
            } else {
                setClassificationPrefSetByUser(userId, true);
                if (!enabled) {
                    addDefaultClassificationTypes(userId);
                }
            }
        }
    }

    protected boolean isClassificationAdjustmentAllowed(@UserIdInt int userId) {
        NotificationRule staticBundleRule = getStaticBundleRule(userId);
        NotificationRule.Filter filter = staticBundleRule.getFilters().getFirst();
        return staticBundleRule.isEnabled() && (filter.getUsers().isEmpty()
                || filter.getUsers().contains(UserHandle.of(userId)));
    }

    // For logging preferences:
    // Add KEY_TYPE data to the map of user id -> package name -> list of denied keys
    // This is essentially a reconfiguration of the contents of mAdjustmentKeyDeniedPackages.
    @NonNull
    void getClassificationDeniedPkgsForUsersAndPackages(Map<Integer, Map<String,
            List<String>>> out) {
        synchronized (mLock) {
            if (nmContextualDisplayLaunch()) {
                for (int userId : mNotificationRules.keySet()) {
                    Set<String> deniedForUser = getClassificationDeniedPackages(userId);
                    if (!deniedForUser.isEmpty()) {
                        Map<String, List<String>> pkgMapForUser = out.get(userId);
                        for (String pkgName : deniedForUser) {
                            pkgMapForUser.putIfAbsent(pkgName, new ArrayList<>());
                            pkgMapForUser.get(pkgName).add(KEY_TYPE);
                        }
                    }
                }
            } else {
                for (int userId : mClassificationDeniedPackages.keySet()) {
                    Set<String> pkgsByType = mClassificationDeniedPackages.get(userId);
                    if (!pkgsByType.isEmpty()) {
                        out.putIfAbsent(userId, new ArrayMap<>());
                        Map<String, List<String>> pkgMapForUser = out.get(userId);
                        for (String pkgName : pkgsByType) {
                            pkgMapForUser.putIfAbsent(pkgName, new ArrayList<>());
                            pkgMapForUser.get(pkgName).add(KEY_TYPE);
                        }
                    }
                }
            }
        }
    }

    // Rule management

    /**
     * Returns the notifications rules for a given user.
     */
    List<NotificationRule> getNotificationRules(@UserIdInt int user) {
        synchronized (mLock) {
            return mNotificationRules.getOrDefault(user, new ArrayList<>());
        }
    }

    /**
     * Returns the notification rule for a given user with the given id.
     */
    @Nullable NotificationRule getNotificationRule(@UserIdInt int user, int ruleId) {
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            Optional<NotificationRule> match = rulesForUser.stream().filter(
                    notificationRule -> ruleId == notificationRule.getId()).findFirst();
            return match.get();
        }
    }

    private boolean doesStaticBundleRuleExist(@UserIdInt int user) {
        List<NotificationRule> rulesForUser =
                mNotificationRules.getOrDefault(user, new ArrayList<>());
        return rulesForUser.stream().anyMatch(
                notificationRule -> RESERVED_ID_STATIC_BUNDLES == notificationRule.getId());
    }

    /**
     * Updates a notification rule without changing its position.
     * @return true if the rule was updated, false otherwise
     */
    boolean updateNotificationRule(@UserIdInt int user, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            AtomicReference<Boolean> appliedChange = new AtomicReference<>(false);
            synchronized (mLock) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                rulesForUser.replaceAll(notificationRule -> {
                    if (notificationRule.getId() == rule.getId()) {
                        appliedChange.set(true);
                        return rule;
                    }
                    return notificationRule;
                });
                mNotificationRules.put(user, rulesForUser);
            }
            return appliedChange.get();
        }
        return false;
    }

    /**
     * Inserts a new rule for a given user at a given position.
     */
    boolean addNotificationRule(@UserIdInt int user, int position, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            synchronized (mLock) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                position = Math.clamp(position, 0, rulesForUser.size());
                // duplicate ids are not allowed
                if (!rulesForUser.stream().filter(
                                notificationRule -> notificationRule.getId() == rule.getId())
                        .findFirst().isEmpty()) {
                    return false;
                }
                rulesForUser.add(position, rule);
                mNotificationRules.put(user, rulesForUser);
            }
            return true;

        }
        return false;
    }

    /**
     * Inserts a new notification rule at the end of the list
     */
    private void addNotificationRule(@UserIdInt int user, NotificationRule rule) {
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            synchronized (mLock) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                // duplicate ids are not allowed
                if (!rulesForUser.stream().filter(
                                notificationRule -> notificationRule.getId() == rule.getId())
                        .findFirst().isEmpty()) {
                    return;
                }
                rulesForUser.add(rule);
                mNotificationRules.put(user, rulesForUser);
            }
        }
    }

    /**
     * Removes a rule for a given user with a given id.
     * @return true if there was a rule removed, false otherwise
     */
    boolean removeNotificationRule(@UserIdInt int user, int ruleId) {
        if (NotificationRule.isSystemRule(ruleId)) {
            return false;
        }
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            boolean removed = false;
            synchronized (mLock) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                removed = rulesForUser.removeIf(
                        notificationRule -> ruleId == notificationRule.getId());
                mNotificationRules.put(user, rulesForUser);
            }
            return removed;
        }
        return false;
    }

    /**
     * Replaces all rules for the given user.
     */
    void setNotificationRules(int user, List<NotificationRule> rules) {
        // TODO(b/477961511): validate the rules first
        // TODO(b/477961511): don't remove system rules
        synchronized (mLock) {
            int potentialParent = mUmInternal.getProfileParentId(user);
            if (potentialParent == user) {
                mNotificationRules.put(user, new ArrayList<>(rules));
            }
        }
    }

    /**
     * Adds system/NAS rules for the given user.
     */
    void onUserAdded(@UserIdInt int user) {
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            synchronized (mLock) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                rulesForUser.addAll(getDefaultSystemRules(user));
                mNotificationRules.put(user, rulesForUser);
            }
        }
    }

    /**
     * Clears rule data for the given user
     */
    void onUserRemoved(@UserIdInt int user) {
        synchronized (mLock) {
            mNotificationRules.remove(user);
        }
    }

    /**
     * Validates incoming ids, and denormalizes the single rule adjustment into a list of behavioral
     * adjustments. Each valid ruleId in the provided Adjustment will turn into an Adjustment with
     * a collection of behavioral signals to apply. In order to be 'valid', a rule must have the
     * same primary action as the highest priority matching rule.
     */
    List<Adjustment> getAdjustmentsForRules(Adjustment ruleIdAdjustment) {
        List<Integer> ruleIds = ruleIdAdjustment.getSignals().getIntegerArrayList(
                Adjustment.KEY_NOTIFICATION_RULES);
        List<Adjustment> behavioralAdjustments = new ArrayList<>();
        int userId = ruleIdAdjustment.getUser();
        if (userId == USER_ALL) {
            userId = USER_SYSTEM;
        }
        synchronized (mLock) {
            // these MUST be sorted in priority order
            List<NotificationRule> matchingRules =
                    mNotificationRules.getOrDefault(userId, new ArrayList<>())
                            .stream().filter(notificationRule ->
                                    ruleIds.contains(notificationRule.getId())
                                            && notificationRule.isEnabled())
                            .toList();
            int highestPriorityPrimaryAction = PRIMARY_ACTION_NONE;
            for (NotificationRule rule : matchingRules) {
                NotificationRule.Action action = rule.getAction();
                Bundle signals = new Bundle();
                if (highestPriorityPrimaryAction == PRIMARY_ACTION_NONE) {
                    highestPriorityPrimaryAction = action.getPrimaryAction();
                }
                if (highestPriorityPrimaryAction == action.getPrimaryAction()) {
                    switch (action.getPrimaryAction()) {
                        case PRIMARY_ACTION_HIGHLIGHT_AND_ALERT:
                            signals.putBoolean(KEY_HIGHLIGHT, true);
                            signals.putBoolean(KEY_BREAKTHROUGH_ALL_MODES, true);
                            break;
                        case PRIMARY_ACTION_HIGHLIGHT:
                            signals.putBoolean(KEY_HIGHLIGHT, true);
                            break;
                        case NotificationRule.Action.PRIMARY_ACTION_LOW:
                            signals.putInt(KEY_IMPORTANCE, IMPORTANCE_LOW);
                            break;
                        case PRIMARY_ACTION_BUNDLE:
                            signals.putParcelable(KEY_DYNAMIC_BUNDLE,
                                    new NotificationRule.DynamicBundle(
                                            NotificationChannel.getChannelIdForBundleType(
                                                    rule.getId()),
                                            action.getDynamicBundleName(),
                                            action.getDynamicBundleEmojiIcon()));
                            break;
                        case PRIMARY_ACTION_BLOCK:
                            signals.putInt(KEY_IMPORTANCE, IMPORTANCE_NONE);
                            break;
                        default:
                            break;
                    }
                    addActionOverrideBehaviors(action, signals);
                    Adjustment adjustment = new Adjustment(ruleIdAdjustment.getPackage(),
                            ruleIdAdjustment.getKey(), signals, null,
                            ruleIdAdjustment.getUserHandle());
                    adjustment.setOriginatingRuleId(rule.getId());
                    behavioralAdjustments.add(adjustment);
                }
            }
        }
        return behavioralAdjustments;
    }

    private void addActionOverrideBehaviors(NotificationRule.Action action, Bundle signals) {
        if (action.getLightColorOverride() != 0) {
            signals.putInt(KEY_LIGHT, action.getLightColorOverride());
        }
        if (action.getSoundHapticOverride() != null) {
            signals.putParcelable(KEY_SOUND, action.getSoundHapticOverride());
        }
        if (!action.getModeBreakthroughIds().isEmpty()) {
            signals.putStringArrayList(KEY_MODE_BREAKTHROUGH_LIST,
                    new ArrayList<>(action.getModeBreakthroughIds()));
        }
    }

    /**
     * Disables all NAS owned rules and deletes all user owned rules when the NAS is disabled.
     */
    void onNotificationAssistantChanged(@UserIdInt int user) {
        synchronized (mLock) {
            int potentialParent = mUmInternal.getProfileParentId(user);
            if (potentialParent == user) {
                List<NotificationRule> rulesForUser =
                        mNotificationRules.getOrDefault(user, new ArrayList<>());
                rulesForUser.removeIf(notificationRule ->
                        !(RESERVED_ID_PRIORITY_CONVERSATIONS == notificationRule.getId()
                                || RESERVED_ID_PROMOTED == notificationRule.getId()));
                mNotificationRules.put(user, rulesForUser);
            }
        }
    }

    private ArrayList<NotificationRule> getDefaultSystemRules(@UserIdInt int userId) {
        ArrayList<NotificationRule> rules = new ArrayList<>();
        NotificationRule promoted = new NotificationRule.Builder(RESERVED_ID_PROMOTED,
                new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(false)
                .setEditIntentAction("android.settings.MANAGE_APP_POST_PROMOTED_NOTIFICATIONS")
                .addFilter(new NotificationRule.Filter.Builder().setFlags(FLAG_PROMOTED_ONGOING)
                        .build())
                .build();
        rules.add(promoted);
        NotificationRule convos = new NotificationRule.Builder(RESERVED_ID_PRIORITY_CONVERSATIONS,
                new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(false)
                .setEditIntentAction("android.settings.CONVERSATION_SETTINGS")
                .addFilter(new NotificationRule.Filter.Builder().setConversationLevel(
                        CONVERSATION_LEVEL_PRIORITY).build())
                .build();
        rules.add(convos);
        if (!doesStaticBundleRuleExist(userId)) {
            rules.add(getDefaultStaticBundleRule(userId));
        }
        NotificationRule important = new NotificationRule.Builder(
                RESERVED_ID_IMPORTANT_NOTIFICATIONS,
                new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(true)
                .build();
        rules.add(important);

        return rules;
    }

    private NotificationRule getDefaultStaticBundleRule(@UserIdInt int userId) {
        return new NotificationRule.Builder(
                RESERVED_ID_STATIC_BUNDLES,
                new NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build())
                .setEditIntentAction("android.settings.NOTIFICATION_BUNDLES")
                .addFilter(new NotificationRule.Filter.Builder()
                        .setStaticBundleTypes(List.of(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES))
                        // bundles are only enabled for primary users by default
                        .addUser(UserHandle.of(userId))
                        .build())
                .setCanBeDisabled(true)
                .build();
    }
}
