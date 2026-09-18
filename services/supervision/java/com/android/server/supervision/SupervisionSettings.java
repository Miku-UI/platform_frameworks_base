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

package com.android.server.supervision;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.supervision.PackageUsagePolicy;
import android.app.supervision.Policy;
import android.app.supervision.SupervisionRecoveryInfo;
import android.app.supervision.flags.Flags;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import java.time.Duration;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Provides storage and retrieval of device supervision recovery information and user data.
 *
 * <p>The storage is managed as a singleton, ensuring a single point of access for persistent user
 * data and recovery info.
 */
public class SupervisionSettings {
    /** Current version of the supervision settings XML file. */
    public static final int VERSION = 2;

    private static SupervisionSettings sInstance;
    private static final Object sLock = new Object();

    private final SparseArray<SupervisionUserData> mUserData = new SparseArray<>();

    private int mVersion = 0;
    private int mPreviousVersion = 0;
    private boolean mUpgraded = false;
    private SupervisionRecoveryInfo mRecoveryInfo = null;

    private static final String PREF_RECOVERY = "supervision_recovery_info";
    private static final String KEY_ACCOUNT_TYPE = "account_type";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_DATA = "account_data";
    private static final String KEY_STATE = "state";

    private static final String KEY_VERSION = "version";
    private static final String KEY_PREVIOUS_VERSION = "previous_version";
    private static final String KEY_UPGRADED = "upgraded";

    private static final String PREF_DATA = "supervision_data";
    private static final String PREF_USER_DATA = "supervision_user_data";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ENABLED = "supervision_enabled";
    private static final String KEY_APP_PACKAGE = "supervision_app_package";
    private static final String KEY_ROLE_HOLDERS_LIST = "supervision_role_holders_list";
    private static final String KEY_ROLE_HOLDER = "supervision_role_holder";
    private static final String KEY_LOCK_SCREEN_ENABLED = "supervision_lockscreen_enabled";
    private static final String KEY_ESCROW_TOKEN_REQUIRED = "supervision_escrow_token_required";
    private static final String KEY_LOCK_SCREEN_OPTIONS = "supervision_lockscreen_options";

    // Policy related keys.
    private static final String KEY_POLICIES_LIST = "policies_list";
    private static final String KEY_POLICY_HOLDER = "policy_holder";
    private static final String KEY_POLICY_TYPE = "policy_type";
    private static final String KEY_POLICY_VERSION = "policy_version";
    // PackageUsagePolicy related keys.
    private static final String KEY_PACKAGE_NAME = "package_name";
    private static final String KEY_PACKAGE_TYPE = "package_type";
    private static final String KEY_PACKAGE_TIME_LIMIT_MILLIS = "time_limit_millis";

    private AtomicFile recoveryInfoFile =
            new AtomicFile(
                    new File(Environment.getDataSystemDirectory(), "supervision_recovery_info.xml"),
                    "supervision");
    private AtomicFile userDataFile =
            new AtomicFile(
                    new File(Environment.getDataSystemDirectory(), "supervision_settings.xml"),
                    "supervision");

    private SupervisionSettings() {
        loadUserData();
        maybePerformDataMigration();

        loadRecoveryInfo();
    }

    public static SupervisionSettings getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new SupervisionSettings();
            }
            return sInstance;
        }
    }

    /**
     * Performs the given action for each user data entry in the settings.
     *
     * <p>This method iterates over all stored {@link SupervisionUserData} entries and applies the
     * provided {@link BiConsumer} action, passing the user ID and the corresponding user data.
     *
     * @param action The action to be performed for each user data entry.
     */
    public void forEachUserData(BiConsumer<Integer, SupervisionUserData> action) {
        for (int i = 0; i < mUserData.size(); i++) {
            action.accept(mUserData.keyAt(i), mUserData.valueAt(i));
        }
    }

    @VisibleForTesting
    public void changeDirForTesting(File parent) {
        recoveryInfoFile =
                new AtomicFile(new File(parent, "supervision_recovery_info.xml"), "supervision");
        userDataFile = new AtomicFile(new File(parent, "supervision_settings.xml"), "supervision");
        mUserData.clear();
    }

    /** Gets data about a specific user. */
    @NonNull
    public SupervisionUserData getUserData(@UserIdInt int userId) {
        SupervisionUserData data = mUserData.get(userId);
        if (data == null) {
            // TODO(b/362790738): Do not create user data for nonexistent users.
            data = new SupervisionUserData(userId);
            mUserData.append(userId, data);
        }
        return data;
    }

    /** Removes data of a specific user. */
    public void removeUserData(int userId) {
        mUserData.remove(userId);
        saveUserData();
    }

    /** Checks if there is at least one supervised user in the device. */
    public boolean anySupervisedUser() {
        for (int i = 0; i < mUserData.size(); i++) {
            if (mUserData.valueAt(i).supervisionEnabled) {
                return true;
            }
        }
        return false;
    }

    /** Loads user data from persistent storage. */
    public void loadUserData() {
        Slog.d(SupervisionLog.TAG, "Restoring supervision state");
        mUserData.clear();
        if (!userDataFile.getBaseFile().exists()) {
            return;
        }
        try (FileInputStream stream = userDataFile.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            XmlUtils.beginDocument(parser, PREF_DATA);
            if (Flags.enableSupervisionManagerPolicyApis()) {
                mVersion = parser.getAttributeInt(null, KEY_VERSION, 0);
                mPreviousVersion = parser.getAttributeInt(null, KEY_PREVIOUS_VERSION, 0);
                mUpgraded = parser.getAttributeBoolean(null, KEY_UPGRADED, false);
            }
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (PREF_USER_DATA.equals(parser.getName())) {
                    parseUserData(parser);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(SupervisionLog.TAG, "Failed to restore supervision state", e);
        }
    }

    /** Saves user data to persistent storage. */
    public void saveUserData() {
        FileOutputStream stream = null;
        Slog.d(SupervisionLog.TAG, "Writing supervision state");
        try {
            stream = userDataFile.startWrite();
            final TypedXmlSerializer xml = Xml.resolveSerializer(stream);
            xml.startDocument(null, true);
            xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xml.startTag(null, PREF_DATA);
            if (Flags.enableSupervisionManagerPolicyApis()) {
                xml.attributeInt(null, KEY_VERSION, mVersion);
                xml.attributeInt(null, KEY_PREVIOUS_VERSION, mPreviousVersion);
                xml.attributeBoolean(null, KEY_UPGRADED, mUpgraded);
            }
            for (int i = 0; i < mUserData.size(); i++) {
                SupervisionUserData data = mUserData.valueAt(i);
                xml.startTag(null, PREF_USER_DATA);
                xml.attributeInt(null, KEY_USER_ID, data.userId);
                xml.attributeBoolean(null, KEY_ENABLED, data.supervisionEnabled);
                xml.attribute(
                        null,
                        KEY_APP_PACKAGE,
                        data.supervisionAppPackage == null ? "" : data.supervisionAppPackage);
                xml.attributeBoolean(
                        null, KEY_LOCK_SCREEN_ENABLED, data.supervisionLockScreenEnabled);
                xml.attributeBoolean(null, KEY_ESCROW_TOKEN_REQUIRED, data.escrowTokenRequired);
                if (data.supervisionRoleHolders != null && !data.supervisionRoleHolders.isEmpty()) {
                    xml.startTag(null, KEY_ROLE_HOLDERS_LIST);
                    for (String roleHolder : data.supervisionRoleHolders) {
                        xml.startTag(null, KEY_ROLE_HOLDER);
                        xml.attribute(null, "package", roleHolder);
                        xml.endTag(null, KEY_ROLE_HOLDER);
                    }
                    xml.endTag(null, KEY_ROLE_HOLDERS_LIST);
                }

                // Add policies to the XML.
                if (Flags.enableSupervisionManagerPolicyApis()) {
                    addPoliciesToXml(xml, data.policies.getPolicies());
                }

                if (data.supervisionLockScreenOptions != null) {
                    xml.startTag(null, KEY_LOCK_SCREEN_OPTIONS);
                    data.supervisionLockScreenOptions.saveToXml(xml);
                    xml.endTag(null, KEY_LOCK_SCREEN_OPTIONS);
                }
                xml.endTag(null, PREF_USER_DATA);
            }
            xml.endTag(null, PREF_DATA);
            xml.endDocument();
            userDataFile.finishWrite(stream);
        } catch (IOException | XmlPullParserException e) {
            userDataFile.failWrite(stream);
            Slog.e(SupervisionLog.TAG, "Failed to save supervision state", e);
        }
    }

    /**
     * Loads the device supervision recovery information from persistent storage.
     *
     * @return The {@link SupervisionRecoveryInfo} if found, otherwise {@code null}.
     */
    public SupervisionRecoveryInfo getRecoveryInfo() {
        return mRecoveryInfo;
    }

    /**
     * Loads the device supervision recovery information from persistent storage.
     *
     * @return The {@link SupervisionRecoveryInfo} if found, otherwise {@code null}.
     */
    public void loadRecoveryInfo() {
        Slog.d(SupervisionLog.TAG, "Retrieving recovery info");
        if (!recoveryInfoFile.getBaseFile().exists()) {
            Slog.d(SupervisionLog.TAG, "Recovery info file does not exist");
            mRecoveryInfo = null;
            return;
        }
        try (FileInputStream stream = recoveryInfoFile.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            XmlUtils.beginDocument(parser, PREF_RECOVERY);
            int outerDepth = parser.getDepth();
            String accountType = parser.getAttributeValue(null, KEY_ACCOUNT_TYPE);
            String accountName = parser.getAttributeValue(null, KEY_ACCOUNT_NAME);
            int state =
                    parser.getAttributeInt(null, KEY_STATE, SupervisionRecoveryInfo.STATE_PENDING);
            PersistableBundle accountData = null;
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(KEY_ACCOUNT_DATA)) {
                    accountData = PersistableBundle.restoreFromXml(parser);
                }
            }
            if (!accountType.isEmpty() && !accountName.isEmpty()) {
                mRecoveryInfo =
                        new SupervisionRecoveryInfo(accountName, accountType, state, accountData);
                return;
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(SupervisionLog.TAG, "Failed to get recovery info from xml file", e);
        }

        mRecoveryInfo = null;
    }

    /**
     * Saves the device supervision recovery information to persistent storage.
     *
     * @param recoveryInfo The {@link SupervisionRecoveryInfo} to save or {@code null} to clear the
     *     stored information.
     */
    public void saveRecoveryInfo(SupervisionRecoveryInfo recoveryInfo) {
        Slog.d(SupervisionLog.TAG, "Saving recovery info");
        if (recoveryInfo == null) {
            recoveryInfoFile.delete();
            mRecoveryInfo = null;
            return;
        }

        FileOutputStream stream = null;
        try {
            stream = recoveryInfoFile.startWrite();
            final TypedXmlSerializer xml = Xml.resolveSerializer(stream);
            xml.startDocument(null, true);
            xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xml.startTag(null, PREF_RECOVERY);
            xml.attribute(null, KEY_ACCOUNT_TYPE, recoveryInfo.getAccountType());
            xml.attribute(null, KEY_ACCOUNT_NAME, recoveryInfo.getAccountName());
            xml.attributeInt(null, KEY_STATE, recoveryInfo.getState());
            xml.startTag(null, KEY_ACCOUNT_DATA);
            recoveryInfo.getAccountData().saveToXml(xml);
            xml.endTag(null, KEY_ACCOUNT_DATA);
            xml.endTag(null, PREF_RECOVERY);
            xml.endDocument();
            recoveryInfoFile.finishWrite(stream);
            mRecoveryInfo = recoveryInfo;
        } catch (IOException | XmlPullParserException e) {
            userDataFile.failWrite(stream);
            Slog.e(SupervisionLog.TAG, "Failed to save recovery info", e);
        }
    }

    private void parseUserData(TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int userId = parser.getAttributeInt(null, KEY_USER_ID);
        final SupervisionUserData data = getUserData(userId);

        data.supervisionRoleHolders = new ArraySet<>();
        data.supervisionEnabled = parser.getAttributeBoolean(null, KEY_ENABLED);
        final String appPackage = parser.getAttributeValue(null, KEY_APP_PACKAGE);
        data.supervisionAppPackage = "".equals(appPackage) ? null : appPackage;
        data.supervisionLockScreenEnabled =
                parser.getAttributeBoolean(null, KEY_LOCK_SCREEN_ENABLED);
        data.escrowTokenRequired =
                parser.getAttributeBoolean(null, KEY_ESCROW_TOKEN_REQUIRED, false);

        final int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            final String tagName = parser.getName();
            switch (tagName) {
                case KEY_LOCK_SCREEN_OPTIONS ->
                        data.supervisionLockScreenOptions =
                                PersistableBundle.restoreFromXml(parser);
                case KEY_ROLE_HOLDERS_LIST -> parseRoleHolders(parser, data);
                case KEY_POLICIES_LIST -> {
                    if (Flags.enableSupervisionManagerPolicyApis()) {
                        parsePolicies(parser, data);
                    }
                }
                default -> {
                    if (Flags.enableSupervisionManagerPolicyApis()) {
                        Slog.w(
                                SupervisionLog.TAG,
                                "Skipping unknown tag in supervision settings: " + tagName);
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
            }
        }
    }

    private void parseRoleHolders(TypedXmlPullParser parser, SupervisionUserData data)
            throws IOException, XmlPullParserException {
        final int roleHoldersDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, roleHoldersDepth)) {
            final String tagName = parser.getName();
            switch (tagName) {
                case KEY_ROLE_HOLDER -> {
                    final String roleHolder = parser.getAttributeValue(null, "package");
                    if (roleHolder != null) {
                        data.supervisionRoleHolders.add(roleHolder);
                    }
                }
                default -> {
                    if (Flags.enableSupervisionManagerPolicyApis()) {
                        Slog.w(
                                SupervisionLog.TAG,
                                "Skipping unknown tag in role holders list: " + tagName);
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
            }
        }
    }

    /**
     * Prepares for data migration by recording the current on-disk version.
     *
     * <p>The actual migration is triggered by {@link SupervisionService} after all required
     * services are ready.
     */
    private void maybePerformDataMigration() {
        if (mVersion < VERSION) {
            mPreviousVersion = mVersion;
        }
    }

    private void addPoliciesToXml(TypedXmlSerializer xml, List<Policy> policies)
            throws XmlPullParserException, IOException {

        if (policies == null || policies.isEmpty()) {
            return;
        }

        xml.startTag(null, KEY_POLICIES_LIST);
        for (Policy policy : policies) {
            xml.startTag(null, KEY_POLICY_HOLDER);
            xml.attribute(null, KEY_POLICY_TYPE, policy.getIdentifier());

            // Add policy specific data to the XML.
            switch (policy) {
                case PackageUsagePolicy pp -> {
                    xml.attributeLong(null, KEY_POLICY_VERSION, pp.getVersion());
                    xml.attribute(null, KEY_PACKAGE_NAME, pp.getPackageName());
                    xml.attributeInt(null, KEY_PACKAGE_TYPE, pp.getType());
                    if (Flags.enableSupervisionPackageUsageApis() && pp.getTimeLimit() != null) {
                        xml.attributeLong(
                                null, KEY_PACKAGE_TIME_LIMIT_MILLIS, pp.getTimeLimit().toMillis());
                    }
                }
                default -> {
                    Slog.e(
                            SupervisionLog.TAG,
                            "Unsupported policy type: " + policy.getClass().getSimpleName());
                }
            }

            xml.endTag(null, KEY_POLICY_HOLDER);
        }
        xml.endTag(null, KEY_POLICIES_LIST);
    }

    private void parsePolicies(TypedXmlPullParser parser, SupervisionUserData data)
            throws IOException, XmlPullParserException {
        final int policiesDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, policiesDepth)) {
            final String tagName = parser.getName();
            switch (tagName) {
                case KEY_POLICY_HOLDER -> {
                    Policy policy = restorePolicyFromXml(parser);
                    if (policy != null) {
                        data.policies.add(policy);
                    }
                }
                default -> {
                    Slog.w(SupervisionLog.TAG, "Skipping unknown tag in policies list: " + tagName);
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        }
    }

    @Nullable
    private Policy restorePolicyFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        String policyType = parser.getAttributeValue(null, KEY_POLICY_TYPE);
        long version = parser.getAttributeLong(null, KEY_POLICY_VERSION);

        switch (policyType) {
            case Policy.PACKAGE_POLICY_IDENTIFIER -> {
                return parsePackageUsagePolicy(parser, version);
            }
            default -> {
                Slog.e(SupervisionLog.TAG, "Unsupported policy type: " + policyType);
                return null;
            }
        }
    }

    @Nullable
    private PackageUsagePolicy parsePackageUsagePolicy(
            TypedXmlPullParser parser, long policyVersion)
            throws XmlPullParserException, IOException {
        String packageName = parser.getAttributeValue(null, KEY_PACKAGE_NAME);
        int type = parser.getAttributeInt(null, KEY_PACKAGE_TYPE);

        PackageUsagePolicy.Builder builder =
                new PackageUsagePolicy.Builder(packageName, type).setVersion(policyVersion);

        if (!Flags.enableSupervisionPackageUsageApis()) {
            if (type == PackageUsagePolicy.TYPE_TIME_LIMIT) {
                Slog.e(SupervisionLog.TAG, "Time limit policy type is not supported");
                return null;
            }
            return builder.build();
        }

        Duration timeLimit = null;
        int timeLimitIndex = parser.getAttributeIndex(null, KEY_PACKAGE_TIME_LIMIT_MILLIS);
        if (timeLimitIndex != -1) {
            timeLimit =
                    Duration.ofMillis(parser.getAttributeLong(null, KEY_PACKAGE_TIME_LIMIT_MILLIS));
        }
        builder.setTimeLimit(timeLimit);

        try {
            return builder.build();
        } catch (IllegalStateException e) {
            Slog.e(SupervisionLog.TAG, "Failed to build package usage policy", e);
            return null;
        }
    }

    public int getVersion() {
        return mVersion;
    }

    public void setVersion(int version) {
        mPreviousVersion = mVersion;
        mVersion = version;
        mUpgraded = true;
    }

    void dump(IndentingPrintWriter pw) {
        pw.println("SupervisionSettings state:");
        pw.increaseIndent();
        pw.println("version: " + mVersion);
        pw.println("previousVersion: " + mPreviousVersion);
        pw.println("upgraded: " + mUpgraded);
        pw.decreaseIndent();
    }
}
