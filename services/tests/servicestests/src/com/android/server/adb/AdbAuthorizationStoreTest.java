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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.Xml;

import com.android.modules.utils.TypedXmlSerializer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class AdbAuthorizationStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File mKeyStoreFile;
    private AdbAuthorizationStore mStore;

    @Before
    public void setUp() throws Exception {
        mKeyStoreFile = folder.newFile("adb_temp_keys.xml");
        mStore = new AdbAuthorizationStore(mKeyStoreFile);
    }

    @Test
    public void testLoad_nonExistentFile() {
        AdbAuthorizationStore.Entries entries = mStore.load();
        assertTrue(entries.keys().isEmpty());
        assertTrue(entries.trustedNetworks().isEmpty());
    }

    @Test
    public void testSaveAndLoad_empty() {
        AdbAuthorizationStore.Entries entries = new AdbAuthorizationStore.Entries();
        mStore.save(entries);
        assertFalse(mKeyStoreFile.exists());

        AdbAuthorizationStore.Entries loadedEntries = mStore.load();
        assertTrue(loadedEntries.keys().isEmpty());
        assertTrue(loadedEntries.trustedNetworks().isEmpty());
    }

    @Test
    public void testSaveAndLoad_withData() {
        Map<String, Long> keys = new HashMap<>();
        keys.put("key1", 12345L);
        keys.put("key2", 67890L);

        List<AdbAuthorizationStore.WifiNetwork> trustedNetworks = new ArrayList<>();
        trustedNetworks.add(new AdbAuthorizationStore.WifiNetwork("bssid1", "ssid1"));
        trustedNetworks.add(new AdbAuthorizationStore.WifiNetwork("bssid2", ""));

        AdbAuthorizationStore.Entries entries = new AdbAuthorizationStore.Entries(keys,
                trustedNetworks);
        mStore.save(entries);
        assertTrue(mKeyStoreFile.exists());

        AdbAuthorizationStore.Entries loadedEntries = mStore.load();
        assertEquals(keys, loadedEntries.keys());
        assertEquals(trustedNetworks, loadedEntries.trustedNetworks());
    }

    @Test
    public void testDelete() {
        Map<String, Long> keys = new HashMap<>();
        keys.put("key1", 12345L);
        AdbAuthorizationStore.Entries entries = new AdbAuthorizationStore.Entries(keys,
                new ArrayList<>());
        mStore.save(entries);
        assertTrue(mKeyStoreFile.exists());

        mStore.delete();
        assertFalse(mKeyStoreFile.exists());
    }

    @Test
    public void testLoad_unsupportedVersion() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(mKeyStoreFile)) {
            TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
            serializer.startDocument(null, true);
            serializer.startTag(null, "keyStore");
            serializer.attributeInt(null, "version", 99);
            serializer.startTag(null, "adbKey");
            serializer.attribute(null, "key", "key1");
            serializer.attributeLong(null, "lastConnection", 12345L);
            serializer.endTag(null, "adbKey");
            serializer.endTag(null, "keyStore");
            serializer.endDocument();
        }

        AdbAuthorizationStore.Entries entries = mStore.load();
        assertTrue(entries.keys().isEmpty());
        assertTrue(entries.trustedNetworks().isEmpty());
    }

    @Test
    public void testLoad_invalidXml() throws IOException {
        String xml = "this is not valid xml";
        try (FileOutputStream fos = new FileOutputStream(mKeyStoreFile)) {
            fos.write(xml.getBytes(StandardCharsets.UTF_8));
        }

        AdbAuthorizationStore.Entries entries = mStore.load();
        assertTrue(entries.keys().isEmpty());
        assertTrue(entries.trustedNetworks().isEmpty());
    }

    @Test
    public void testLoad_missingLastConnectionAttribute() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(mKeyStoreFile)) {
            TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
            serializer.startDocument(null, true);
            serializer.startTag(null, "keyStore");
            serializer.attributeInt(null, "version", 1);

            // Malformed entry no last connection time.
            serializer.startTag(null, "adbKey");
            serializer.attribute(null, "key", "key1");
            serializer.endTag(null, "adbKey");

            // Valid entry
            serializer.startTag(null, "adbKey");
            serializer.attribute(null, "key", "key2");
            serializer.attributeLong(null, "lastConnection", 54321L);
            serializer.endTag(null, "adbKey");

            serializer.endTag(null, "keyStore");
            serializer.endDocument();
        }

        AdbAuthorizationStore.Entries entries = mStore.load();
        assertEquals(1, entries.keys().size());
        assertTrue(entries.keys().containsKey("key2"));
        assertEquals(54321L, (long) entries.keys().get("key2"));
        assertTrue(entries.trustedNetworks().isEmpty());
    }

    @Test
    public void testLoadSaveLoad_fileCreatedBeforeSSIDTag() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(mKeyStoreFile)) {
            TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
            serializer.startDocument(null, true);
            serializer.startTag(null, "keyStore");
            serializer.attributeInt(null, "version", 1);

            serializer.startTag(null, "wifiAP");
            serializer.attribute(null, "bssid", "bssid1");
            serializer.endTag(null, "wifiAP");

            serializer.endTag(null, "keyStore");
            serializer.endDocument();
        }

        AdbAuthorizationStore.Entries entries = mStore.load();
        assertEquals(1, entries.trustedNetworks().size());
        assertEquals("bssid1", entries.trustedNetworks().getFirst().bssid());
        assertEquals("", entries.trustedNetworks().getFirst().ssid());
        assertTrue(entries.keys().isEmpty());


        entries.trustedNetworks().add(new AdbAuthorizationStore.WifiNetwork("bssid2", "ssid2"));
        mStore.save(entries);

        entries = mStore.load();
        assertEquals(2, entries.trustedNetworks().size());
        assertEquals("bssid1", entries.trustedNetworks().get(0).bssid());
        assertEquals("", entries.trustedNetworks().get(0).ssid());
        assertEquals("bssid2", entries.trustedNetworks().get(1).bssid());
        assertEquals("ssid2", entries.trustedNetworks().get(1).ssid());
        assertTrue(entries.keys().isEmpty());
    }
}
