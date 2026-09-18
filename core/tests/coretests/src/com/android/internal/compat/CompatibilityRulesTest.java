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

package com.android.internal.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.LongSparseArray;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CompatibilityRulesTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setUp() {
        CompatibilityRules.reset();
    }

    private void writeToFile(File file, String data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testLoadConfig() throws Exception {
        File configDir = tempDir.newFolder("etc", "compatconfig");
        File configFile = new File(configDir, "compat-config.xml");
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <compat-config>
                    <compat-change id="1234" name="TEST_CHANGE" enableAfterTargetSdk="28" \
                description="Test Description" overridable="true" />
                    <compat-change id="5678" name="DISABLED_CHANGE" disabled="true" />
                </compat-config>""";
        writeToFile(configFile, xml);

        LongSparseArray<CompatibilityChangeInfo> rules = new LongSparseArray<>();
        CompatibilityRules.loadConfigFromDir(configDir, rules, (file) -> true);

        assertEquals(2, rules.size());

        CompatibilityChangeInfo info1 = rules.get(1234L);
        assertNotNull(info1);
        assertEquals("TEST_CHANGE", info1.getName());
        assertEquals(29, info1.getEnableSinceTargetSdk());
        assertEquals("Test Description", info1.getDescription());
        assertTrue(info1.getOverridable());
        assertFalse(info1.getDisabled());

        CompatibilityChangeInfo info2 = rules.get(5678L);
        assertNotNull(info2);
        assertEquals("DISABLED_CHANGE", info2.getName());
        assertTrue(info2.getDisabled());
    }

    @Test
    public void testLoadConfig_missingAttributes() throws Exception {
        File configDir = tempDir.newFolder("etc", "compatconfig");
        File configFile = new File(configDir, "compat-config.xml");
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <compat-config>
                    <compat-change id="1234" />
                </compat-config>""";
        writeToFile(configFile, xml);

        LongSparseArray<CompatibilityChangeInfo> rules = new LongSparseArray<>();
        CompatibilityRules.loadConfigFromDir(configDir, rules, (file) -> true);

        assertEquals(1, rules.size());
        CompatibilityChangeInfo info = rules.get(1234L);
        assertNotNull(info);
        assertEquals(1234L, info.getId());
        assertEquals(null, info.getName());
        assertEquals(-1, info.getEnableSinceTargetSdk());
        assertFalse(info.getDisabled());
    }

    @Test
    public void testIsChangeEnabled() {
        CompatibilityRules.initRulesForTest(
                new CompatibilityChangeInfo(1L, "ENABLED", -1, -1, false, false, false, "", false),
                new CompatibilityChangeInfo(2L, "DISABLED", -1, -1, true, false, false, "", false),
                new CompatibilityChangeInfo(3L, "SDK_GATE", -1, 29, false, false, false, "", false)
        );

        // Unknown change
        assertTrue(CompatibilityRules.isChangeEnabled(999L, 30));

        // Explicitly enabled
        assertTrue(CompatibilityRules.isChangeEnabled(1L, 30));

        // Explicitly disabled
        assertFalse(CompatibilityRules.isChangeEnabled(2L, 30));

        // SDK gated - Target SDK 30 (>= 29) -> Enabled
        assertTrue(CompatibilityRules.isChangeEnabled(3L, 30));

        // SDK gated - Target SDK 28 (< 29) -> Disabled
        assertFalse(CompatibilityRules.isChangeEnabled(3L, 28));
    }

    @Test
    public void testInitDefensiveCopy() {
        LongSparseArray<CompatibilityChangeInfo> rules = new LongSparseArray<>();
        rules.put(1L, new CompatibilityChangeInfo(1L, "TEST", -1, -1, false, false, false, "",
                false));

        CompatibilityRules.init(rules);

        // Modify the original array
        rules.clear();

        // CompatibilityRules should still have the original rules
        assertEquals(1, CompatibilityRules.getRules().size());
        assertNotNull(CompatibilityRules.getRules().get(1L));
    }
}
