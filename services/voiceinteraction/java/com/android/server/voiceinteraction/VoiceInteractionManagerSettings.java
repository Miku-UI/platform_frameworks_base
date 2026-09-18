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

package com.android.server.voiceinteraction;

import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.os.BackgroundThread;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Provides storage for voice interaction manager settings that need to persist across reboots.
 */
class VoiceInteractionManagerSettings {
    private static final String TAG = "VoiceInteractionManagerSettings";
    private static final Object sLock = new Object();
    private static VoiceInteractionManagerSettings sInstance;

    private static final String FILE_NAME = "voice_interaction_manager_settings.xml";

    private static final String TAG_ROOT = "voice-interaction-manager-settings";
    private static final String TAG_MIGRATION = "migration";
    private static final String ATTR_ASSIST_MIGRATION_COMPLETE = "assist-migration-complete";

    private final AtomicFile mFile;
    private boolean mAssistMigrationComplete = false;

    private VoiceInteractionManagerSettings() {
        mFile = new AtomicFile(new File(Environment.getDataSystemDirectory(), FILE_NAME));
        loadSettings();
    }

    public static VoiceInteractionManagerSettings getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new VoiceInteractionManagerSettings();
            }
            return sInstance;
        }
    }

    public boolean isAssistMigrationComplete() {
        return mAssistMigrationComplete;
    }

    public void setAssistMigrationComplete() {
        if (mAssistMigrationComplete) {
            return;
        }
        mAssistMigrationComplete = true;
        BackgroundThread.getHandler().post(() -> {
            saveSettings();
        });
    }

    private void loadSettings() {
        if (!mFile.getBaseFile().exists()) {
            return;
        }
        try (FileInputStream stream = mFile.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            XmlUtils.beginDocument(parser, TAG_ROOT);
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(TAG_MIGRATION)) {
                    mAssistMigrationComplete = parser.getAttributeBoolean(
                            null, ATTR_ASSIST_MIGRATION_COMPLETE, false);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Failed to load voice interaction manager settings", e);
        }
    }

    private void saveSettings() {
        FileOutputStream stream = null;
        try {
            stream = mFile.startWrite();
            final TypedXmlSerializer xml = Xml.resolveSerializer(stream);
            xml.startDocument(null, true);
            xml.startTag(null, TAG_ROOT);

            xml.startTag(null, TAG_MIGRATION);
            xml.attributeBoolean(null, ATTR_ASSIST_MIGRATION_COMPLETE,
                    mAssistMigrationComplete);
            xml.endTag(null, TAG_MIGRATION);

            xml.endTag(null, TAG_ROOT);
            xml.endDocument();
            mFile.finishWrite(stream);
        } catch (IOException e) {
            mFile.failWrite(stream);
            Slog.e(TAG, "Failed to save voice interaction manager settings", e);
        }
    }
}
