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
package com.android.server.adb;

import android.annotation.NonNull;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persists ADB authorization data to an XML file.
 *
 * <p>This class manages the storage of data such as:
 *
 * <ul>
 *   <li>A mapping of trusted ADB public keys to their last connection timestamp.
 *   <li>A set of trusted Wi-Fi access points (APs).
 * </ul>
 *
 * It is responsible for all I/O and XML serialization/deserialization logic, separating the
 * persistence mechanism from the core business logic in AdbKeyStore.
 */
class AdbAuthorizationStore {
    private static final String TAG = AdbAuthorizationStore.class.getSimpleName();

    private static final int KEYSTORE_VERSION = 1;
    private static final int MAX_SUPPORTED_KEYSTORE_VERSION = 1;
    private static final String XML_KEYSTORE_START_TAG = "keyStore";
    private static final String XML_ATTRIBUTE_VERSION = "version";
    private static final String XML_TAG_ADB_KEY = "adbKey";
    private static final String XML_ATTRIBUTE_KEY = "key";
    private static final String XML_ATTRIBUTE_LAST_CONNECTION = "lastConnection";
    private static final String XML_TAG_WIFI_ACCESS_POINT = "wifiAP";
    private static final String XML_ATTRIBUTE_WIFI_BSSID = "bssid";

    private final File mStoreFile;

    /**
     * Constructs a new storage manager for the given file.
     *
     * @param storeFile The file to read from and write to. Must not be null.
     */
    AdbAuthorizationStore(@NonNull File storeFile) {
        Objects.requireNonNull(storeFile, "storeFile must not be null");
        this.mStoreFile = storeFile;
    }

    /**
     * Loads the ADB key data from the XML file. If the file does not exist or an error occurs
     * during parsing, an empty Entries object is returned.
     *
     * @return A {@link Entries} object with the loaded data.
     */
    Entries load() {
        AtomicFile atomicFile = new AtomicFile(mStoreFile);
        if (!atomicFile.exists()) {
            return new Entries();
        }

        Map<String, Long> trustedKeys = new HashMap<>();
        List<String> trustedNetworks = new ArrayList<>();
        try (FileInputStream keyStream = atomicFile.openRead()) {
            TypedXmlPullParser parser;
            try {
                parser = Xml.resolvePullParser(keyStream);
                XmlUtils.beginDocument(parser, XML_KEYSTORE_START_TAG);

                int keystoreVersion = parser.getAttributeInt(null, XML_ATTRIBUTE_VERSION);
                if (keystoreVersion > MAX_SUPPORTED_KEYSTORE_VERSION) {
                    Slog.e(
                            TAG,
                            "Keystore version="
                                    + keystoreVersion
                                    + " not supported (max_supported="
                                    + MAX_SUPPORTED_KEYSTORE_VERSION
                                    + ")");
                    return new Entries();
                }
            } catch (XmlPullParserException e) {
                // This could be because the XML document doesn't start with
                // XML_KEYSTORE_START_TAG. Try again, instead just starting the document with
                // the adbKey tag (the old format).
                parser = Xml.resolvePullParser(keyStream);
            }
            readKeyStoreContents(parser, trustedKeys, trustedNetworks);
        } catch (IOException e) {
            Slog.e(TAG, "Caught an IOException parsing the XML key file: ", e);
            return new Entries();
        } catch (XmlPullParserException e) {
            Slog.e(TAG, "Caught XmlPullParserException parsing the XML key file: ", e);
            return new Entries();
        }
        return new Entries(trustedKeys, trustedNetworks);
    }

    /**
     * Saves the provided ADB key data to the XML file. If the data is empty, the file will be
     * deleted.
     *
     * @param data The {@link Entries} to persist.
     */
    void save(@NonNull Entries data) {
        Objects.requireNonNull(data, "Entries cannot be null");

        if (data.keys().isEmpty() && data.trustedNetworks().isEmpty()) {
            delete();
            return;
        }

        FileOutputStream keyStream = null;
        AtomicFile atomicFile = new AtomicFile(mStoreFile);
        try {
            keyStream = atomicFile.startWrite();
            TypedXmlSerializer serializer = Xml.resolveSerializer(keyStream);
            serializer.startDocument(null, true);

            serializer.startTag(null, XML_KEYSTORE_START_TAG);
            serializer.attributeInt(null, XML_ATTRIBUTE_VERSION, KEYSTORE_VERSION);
            for (Map.Entry<String, Long> keyEntry : data.keys().entrySet()) {
                serializer.startTag(null, XML_TAG_ADB_KEY);
                serializer.attribute(null, XML_ATTRIBUTE_KEY, keyEntry.getKey());
                serializer.attributeLong(null, XML_ATTRIBUTE_LAST_CONNECTION, keyEntry.getValue());
                serializer.endTag(null, XML_TAG_ADB_KEY);
            }
            for (String bssid : data.trustedNetworks()) {
                serializer.startTag(null, XML_TAG_WIFI_ACCESS_POINT);
                serializer.attribute(null, XML_ATTRIBUTE_WIFI_BSSID, bssid);
                serializer.endTag(null, XML_TAG_WIFI_ACCESS_POINT);
            }
            serializer.endTag(null, XML_KEYSTORE_START_TAG);
            serializer.endDocument();
            atomicFile.finishWrite(keyStream);
        } catch (IOException e) {
            Slog.e(TAG, "Caught an exception writing the key map: ", e);
            atomicFile.failWrite(keyStream);
        }
    }

    /** Deletes the underlying XML key file. */
    void delete() {
        AtomicFile atomicFile = new AtomicFile(mStoreFile);
        if (atomicFile.exists()) {
            atomicFile.delete();
        }
    }

    private void readKeyStoreContents(
            TypedXmlPullParser parser, Map<String, Long> keyMap, List<String> trustedNetworks)
            throws XmlPullParserException, IOException {
        while ((parser.next()) != XmlPullParser.END_DOCUMENT) {
            // This parser is very forgiving. For backwards-compatibility, we simply iterate through
            // all the tags in the file, skipping over anything that's not an <adbKey> tag or a
            // <wifiAP> tag. Invalid tags (such as ones that don't have a valid "lastConnection"
            // attribute) are simply ignored.
            String tagName = parser.getName();
            if (XML_TAG_ADB_KEY.equals(tagName)) {
                addAdbKeyToKeyMap(parser, keyMap);
            } else if (XML_TAG_WIFI_ACCESS_POINT.equals(tagName)) {
                addTrustedNetworkToTrustedNetworks(parser, trustedNetworks);
            } else {
                Slog.w(TAG, "Ignoring tag '" + tagName + "'. Not recognized.");
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void addAdbKeyToKeyMap(TypedXmlPullParser parser, Map<String, Long> keyMap) {
        String key = parser.getAttributeValue(null, XML_ATTRIBUTE_KEY);
        try {
            long connectionTime = parser.getAttributeLong(null, XML_ATTRIBUTE_LAST_CONNECTION);
            keyMap.put(key, connectionTime);
        } catch (XmlPullParserException e) {
            Slog.e(TAG, "Error reading adbKey attributes", e);
        }
    }

    private void addTrustedNetworkToTrustedNetworks(
            TypedXmlPullParser parser, List<String> trustedNetworks) {
        String bssid = parser.getAttributeValue(null, XML_ATTRIBUTE_WIFI_BSSID);
        trustedNetworks.add(bssid);
    }

    /**
     * Represents the data model for the AdbAuthorizationStore.
     *
     * @param keys A map of public keys to the last connection time.
     * @param trustedNetworks A list of trusted WiFi networks BSSIDs.
     */
    record Entries(Map<String, Long> keys, List<String> trustedNetworks) {

        Entries() {
            this(new HashMap<>(), new ArrayList<>());
        }

        boolean isEmpty() {
            return keys.isEmpty() && trustedNetworks.isEmpty();
        }

        void clear() {
            keys.clear();
            trustedNetworks.clear();
        }
    }
}
