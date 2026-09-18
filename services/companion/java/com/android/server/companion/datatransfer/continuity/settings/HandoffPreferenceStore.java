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

package com.android.server.companion.datatransfer.continuity.settings;

import android.annotation.NonNull;
import android.os.Environment;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Cross-user preferences file for user Handoff settings. */
public class HandoffPreferenceStore {

    private static final String TAG = HandoffPreferenceStore.class.getSimpleName();
    private static final String PREFERENCES_FILE_NAME = "handoff_preferences.xml";

    private static final String XML_TAG_HANDOFF_PREFERENCES = "handoff_preferences";
    private static final String XML_TAG_HANDOFF_DISABLED_USERS = "handoff_disabled_users";
    private static final String XML_TAG_HANDOFF_DISABLED_USER = "handoff_disabled_user";
    private static final String XML_ATTR_USER_ID = "userId";

    @GuardedBy("this")
    private final ArraySet<Integer> mHandoffDisabledUserIds = new ArraySet<>();

    @GuardedBy("this")
    private final AtomicFile mPreferencesFile;

    public HandoffPreferenceStore() {
        this(new AtomicFile(new File(Environment.getDataSystemDirectory(), PREFERENCES_FILE_NAME)));
    }

    public HandoffPreferenceStore(@NonNull AtomicFile preferencesFile) {
        mPreferencesFile = Objects.requireNonNull(preferencesFile);
        readPreferencesFromFile();
    }

    public boolean isHandoffEnabledForUser(int userId) {
        synchronized (this) {
            Slog.v(TAG, "isHandoffEnabledForUser: " + userId);
            return !mHandoffDisabledUserIds.contains(userId);
        }
    }

    public void setHandoffEnabledForUser(int userId, boolean enabled) {
        synchronized (this) {
            if (enabled) {
                Slog.v(TAG, "Enabling handoff for user: " + userId);
                mHandoffDisabledUserIds.remove(userId);
            } else {
                Slog.v(TAG, "Disabling handoff for user: " + userId);
                mHandoffDisabledUserIds.add(userId);
            }

            writePreferencesToFile();
        }
    }

    private void readPreferencesFromFile() {
        synchronized (this) {
            if (!mPreferencesFile.exists()) {
                Slog.i(TAG, "Preferences file does not exist");
                return;
            }

            try (FileInputStream fis = mPreferencesFile.openRead()) {
                TypedXmlPullParser parser = Xml.resolvePullParser(fis);
                XmlUtils.beginDocument(parser, XML_TAG_HANDOFF_PREFERENCES);
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    XmlUtils.nextElement(parser);
                    if (XML_TAG_HANDOFF_DISABLED_USERS.equals(parser.getName())) {
                        Slog.v(TAG, "Reading users with Handoff disabled");
                        int depth = parser.getDepth();
                        while (XmlUtils.nextElementWithin(parser, depth)) {
                            if (XML_TAG_HANDOFF_DISABLED_USER.equals(parser.getName())) {
                                int userId = parser.getAttributeInt(null, XML_ATTR_USER_ID);
                                Slog.v(TAG, "Read disabled user: " + userId);
                                mHandoffDisabledUserIds.add(userId);
                            }
                        }
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                Slog.e(TAG, "Failed to read preferences file", e);
            }
        }
    }

    private void writePreferencesToFile() {
        synchronized (this) {
            Slog.v(TAG, "Writing preferences file");
            try (FileOutputStream fos = mPreferencesFile.startWrite()) {
                TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
                serializer.startDocument(null, true);
                serializer.startTag(null, XML_TAG_HANDOFF_PREFERENCES);
                serializer.startTag(null, XML_TAG_HANDOFF_DISABLED_USERS);
                Slog.v(TAG, "Writing disabled users: " + mHandoffDisabledUserIds.size());
                for (int userId : mHandoffDisabledUserIds) {
                    Slog.v(TAG, "Writing disabled user: " + userId);
                    serializer.startTag(null, XML_TAG_HANDOFF_DISABLED_USER);
                    serializer.attributeInt(null, XML_ATTR_USER_ID, userId);
                    serializer.endTag(null, XML_TAG_HANDOFF_DISABLED_USER);
                }
                serializer.endTag(null, XML_TAG_HANDOFF_DISABLED_USERS);
                serializer.endTag(null, XML_TAG_HANDOFF_PREFERENCES);
                serializer.endDocument();
                mPreferencesFile.finishWrite(fos);
            } catch (IOException e) {
                Slog.e(TAG, "Failed to write preferences file", e);
            }
        }
    }
}
