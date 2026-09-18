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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlDataStoreTest {

    private File mFile;
    private ComputerControlDataStore mDataStore;

    @Before
    public void setUp() throws IOException {
        mFile = File.createTempFile("test_automatable_apps", ".xml");
        mDataStore = new ComputerControlDataStore(mFile);
    }

    @After
    public void tearDown() {
        mFile.delete();
    }

    @Test
    public void writeAndReadAutomatableApps_multipleAgentsAndTargets() {
        SparseArray<Map<String, Set<String>>> data = new SparseArray<>();
        int agentUid1 = 12345;
        String agentPkg1 = "com.agent1";
        String targetPkg1 = "com.target1";
        String targetPkg2 = "com.target2";

        int agentUid2 = 67890;
        String agentPkg2 = "com.agent2";
        String targetPkg3 = "com.target3";

        Map<String, Set<String>> agentMap1 = new ArrayMap<>();
        agentMap1.put(agentPkg1, new ArraySet<>(Set.of(targetPkg1, targetPkg2)));
        data.put(agentUid1, agentMap1);

        Map<String, Set<String>> agentMap2 = new ArrayMap<>();
        agentMap2.put(agentPkg2, new ArraySet<>(Set.of(targetPkg3)));
        data.put(agentUid2, agentMap2);

        mDataStore.writeAutomatableAppList(data);

        SparseArray<Map<String, Set<String>>> result = mDataStore.readAutomatableAppList();
        assertEquals(2, result.size());

        Map<String, Set<String>> resultAgentMap1 = result.get(agentUid1);
        assertEquals(1, resultAgentMap1.size());
        Set<String> resultTargets1 = resultAgentMap1.get(agentPkg1);
        assertEquals(2, resultTargets1.size());
        assertTrue(resultTargets1.contains(targetPkg1));
        assertTrue(resultTargets1.contains(targetPkg2));

        Map<String, Set<String>> resultAgentMap2 = result.get(agentUid2);
        assertEquals(1, resultAgentMap2.size());
        Set<String> resultTargets2 = resultAgentMap2.get(agentPkg2);
        assertEquals(1, resultTargets2.size());
        assertTrue(resultTargets2.contains(targetPkg3));
    }

    @Test
    public void readAutomatableApps_empty() {
        SparseArray<Map<String, Set<String>>> result = mDataStore.readAutomatableAppList();
        assertEquals(0, result.size());
    }

    @Test
    public void readAutomatableApps_corruptedFile() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(mFile)) {
            fos.write("corrupt data".getBytes());
        }
        SparseArray<Map<String, Set<String>>> result = mDataStore.readAutomatableAppList();
        assertEquals(0, result.size());
    }

    @Test
    public void writeAutomatableApps_corruptedFile_overwritesData() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(mFile)) {
            fos.write("corrupt data".getBytes());
        }
        SparseArray<Map<String, Set<String>>> data = new SparseArray<>();
        int agentUid = 12345;
        Map<String, Set<String>> agentMap = new ArrayMap<>();
        agentMap.put("com.agent", new ArraySet<>(Set.of("com.target")));
        data.put(agentUid, agentMap);
        mDataStore.writeAutomatableAppList(data);

        SparseArray<Map<String, Set<String>>> result = mDataStore.readAutomatableAppList();
        assertEquals(1, result.size());
    }
}
