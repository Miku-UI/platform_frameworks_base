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
import android.annotation.UserIdInt;
import android.app.supervision.SupervisionRecoveryInfo;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Provides storage and retrieval of device supervision recovery information and user data.
 *
 * <p>The storage is managed as a singleton, ensuring a single point of access for persistent user
 * data and recovery info.
 */
public class SupervisionSettings {

    private static SupervisionSettings sInstance;
    private static final Object sLock = new Object();

    private final SparseArray<SupervisionUserData> mUserData = new SparseArray<>();

    private static final String PREF_RECOVERY = "supervision_recovery_info";
    private static final String KEY_ACCOUNT_TYPE = "account_type";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_DATA = "account_data";
    private static final String KEY_STATE = "state";

    private static final String PREF_DATA = "supervision_data";
    private static final String PREF_USER_DATA = "supervision_user_data";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ENABLED = "supervision_enabled";
    private static final String KEY_APP_PACKAGE = "supervision_app_package";
    private static final String KEY_ROLE_HOLDERS_LIST = "supervision_role_holders_list";
    private static final String KEY_ROLE_HOLDER = "supervision_role_holder";
    private static final String KEY_LOCK_SCREEN_ENABLED = "supervision_lockscreen_enabled";
    private static final String KEY_LOCK_SCREEN_OPTIONS = "supervision_lockscreen_options";

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
    }

    public static SupervisionSettings getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new SupervisionSettings();
            }
            return sInstance;
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
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(PREF_USER_DATA)) {
                    int userId = parser.getAttributeInt(null, KEY_USER_ID);
                    SupervisionUserData data = getUserData(userId);
                    data.supervisionRoleHolders = new ArraySet<>();
                    data.supervisionEnabled = parser.getAttributeBoolean(null, KEY_ENABLED);
                    data.supervisionAppPackage = parser.getAttributeValue(null, KEY_APP_PACKAGE);
                    if (data.supervisionAppPackage.isEmpty()) {
                        data.supervisionAppPackage = null;
                    }
                    data.supervisionLockScreenEnabled =
                            parser.getAttributeBoolean(null, KEY_LOCK_SCREEN_ENABLED);
                    while (XmlUtils.nextElementWithin(parser, outerDepth + 1)) {
                        if (parser.getName().equals(KEY_LOCK_SCREEN_OPTIONS)) {
                            data.supervisionLockScreenOptions =
                                    PersistableBundle.restoreFromXml(parser);
                        } else if (parser.getName().equals(KEY_ROLE_HOLDERS_LIST)) {
                            final int roleHoldersDepth = parser.getDepth();
                            while (XmlUtils.nextElementWithin(parser, roleHoldersDepth)) {
                                if (parser.getName().equals(KEY_ROLE_HOLDER)) {
                                    String roleHolder = parser.getAttributeValue(null, "package");
                                    data.supervisionRoleHolders.add(roleHolder);
                                }
                            }
                        }
                    }
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
                if (data.supervisionRoleHolders != null && !data.supervisionRoleHolders.isEmpty()) {
                    xml.startTag(null, KEY_ROLE_HOLDERS_LIST);
                    for (String roleHolder : data.supervisionRoleHolders) {
                        xml.startTag(null, KEY_ROLE_HOLDER);
                        xml.attribute(null, "package", roleHolder);
                        xml.endTag(null, KEY_ROLE_HOLDER);
                    }
                    xml.endTag(null, KEY_ROLE_HOLDERS_LIST);
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
     * Gets the device supervision recovery information from persistent storage.
     *
     * @return The {@link SupervisionRecoveryInfo} if found, otherwise {@code null}.
     */
    public SupervisionRecoveryInfo getRecoveryInfo() {
        Slog.d(SupervisionLog.TAG, "Retrieving recovery info");
        if (!recoveryInfoFile.getBaseFile().exists()) {
            Slog.d(SupervisionLog.TAG, "Recovery info file does not exist");
            return null;
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
                return new SupervisionRecoveryInfo(accountName, accountType, state, accountData);
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(SupervisionLog.TAG, "Failed to get recovery info from xml file", e);
        }

        return null;
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
        } catch (IOException | XmlPullParserException e) {
            userDataFile.failWrite(stream);
            Slog.e(SupervisionLog.TAG, "Failed to save recovery info", e);
        }
    }
}
