/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.os.Environment;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Manages persistent data for computer control.
 */
class ComputerControlDataStore {
    private static final String TAG = "ComputerControlDataStore";

    private static final String COMPUTER_CONTROL_DIR = "computercontrol";
    private static final String AUTOMATABLE_APPS_FILE_NAME = "automatable_apps.xml";

    private static final String TAG_ROOT = "computer-control-automatable-apps";
    private static final String TAG_AGENT = "agent";
    private static final String TAG_TARGET = "target";

    private static final String ATTR_AGENT_UID = "uid";
    private static final String ATTR_AGENT_PACKAGE = "package";
    private static final String ATTR_TARGET_PACKAGE = "package";

    private final AtomicFile mAtomicFile;

    ComputerControlDataStore() {
        this(new File(
                new File(Environment.getDataSystemDeDirectory(UserHandle.SYSTEM.getIdentifier()),
                        COMPUTER_CONTROL_DIR),
                AUTOMATABLE_APPS_FILE_NAME));
    }

    @VisibleForTesting
    ComputerControlDataStore(@NonNull File file) {
        mAtomicFile = new AtomicFile(file);
    }

    /**
     * Reads the automatable apps list from disk.
     *
     * @return A map of agent UID to a map of agent package name to a set of target package names.
     */
    @NonNull
    synchronized SparseArray<Map<String, Set<String>>> readAutomatableAppList() {
        SparseArray<Map<String, Set<String>>> automatableApps = new SparseArray<>();
        if (!mAtomicFile.exists()) {
            return automatableApps;
        }

        try (FileInputStream stream = mAtomicFile.openRead()) {
            TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                if (TAG_ROOT.equals(parser.getName())) {
                    readAllAgentDataFromXml(parser, automatableApps);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Failed to read automatable apps from disk", e);
        }
        return automatableApps;
    }

    /**
     * Writes the automatable apps list to disk.
     *
     * @param automatableApps A map of agent UID to a map of agent package name to a set of target
     *                        package names.
     */
    synchronized void writeAutomatableAppList(
            @NonNull SparseArray<Map<String, Set<String>>> automatableApps) {
        FileOutputStream stream = null;
        try {
            stream = mAtomicFile.startWrite();
            TypedXmlSerializer serializer = Xml.resolveSerializer(stream);
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_ROOT);

            for (int i = 0; i < automatableApps.size(); i++) {
                int agentUid = automatableApps.keyAt(i);
                Map<String, Set<String>> agentMap = automatableApps.valueAt(i);
                for (Map.Entry<String, Set<String>> packageEntry : agentMap.entrySet()) {
                    String agentPackageName = packageEntry.getKey();
                    Set<String> targetPackages = packageEntry.getValue();
                    writeAgentAutomatableListToXml(serializer, agentUid, agentPackageName,
                            targetPackages);
                }
            }

            serializer.endTag(null, TAG_ROOT);
            serializer.endDocument();
            mAtomicFile.finishWrite(stream);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to write automatable apps to disk", e);
            if (stream != null) {
                mAtomicFile.failWrite(stream);
            }
        }
    }

    private void readAllAgentDataFromXml(TypedXmlPullParser parser,
            SparseArray<Map<String, Set<String>>> automatableApps)
            throws XmlPullParserException, IOException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_AGENT.equals(parser.getName())) {
                readAgentAutomatableListFromXml(parser, automatableApps);
            }
        }
    }

    private void readAgentAutomatableListFromXml(TypedXmlPullParser parser,
            SparseArray<Map<String, Set<String>>> automatableApps)
            throws XmlPullParserException, IOException {
        int agentUid = parser.getAttributeInt(null, ATTR_AGENT_UID);
        String agentPackageName = parser.getAttributeValue(null, ATTR_AGENT_PACKAGE);
        Set<String> targetPackages = new ArraySet<>();

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_TARGET.equals(parser.getName())) {
                String targetPackage = parser.getAttributeValue(null, ATTR_TARGET_PACKAGE);
                if (targetPackage != null) {
                    targetPackages.add(targetPackage);
                }
            }
        }

        if (agentPackageName != null && !targetPackages.isEmpty()) {
            Map<String, Set<String>> agentMap = automatableApps.get(agentUid);
            if (agentMap == null) {
                agentMap = new ArrayMap<>();
                automatableApps.put(agentUid, agentMap);
            }
            agentMap.put(agentPackageName, targetPackages);
        }
    }

    private void writeAgentAutomatableListToXml(TypedXmlSerializer serializer, int agentUid,
            String agentPackageName, Set<String> targetPackages) throws IOException {
        serializer.startTag(null, TAG_AGENT);
        serializer.attributeInt(null, ATTR_AGENT_UID, agentUid);
        serializer.attribute(null, ATTR_AGENT_PACKAGE, agentPackageName);

        for (String targetPackage : targetPackages) {
            serializer.startTag(null, TAG_TARGET);
            serializer.attribute(null, ATTR_TARGET_PACKAGE, targetPackage);
            serializer.endTag(null, TAG_TARGET);
        }

        serializer.endTag(null, TAG_AGENT);
    }
}
