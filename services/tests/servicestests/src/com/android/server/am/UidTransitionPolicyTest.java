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

package com.android.server.am;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link UidTransitionPolicy}. */
@Presubmit
public class UidTransitionPolicyTest {

    private UidTransitionPolicy mPolicy;
    private Path mPolicyFile;

    @Before
    public void setUp() throws IOException {
        mPolicyFile = Files.createTempFile("uid_policy", ".txt");
        mPolicy = new UidTransitionPolicy(mPolicyFile);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(mPolicyFile);
    }

    @Test
    public void testClear() throws IOException {
        mPolicy.allowUidTransition(1001, 1002);
        mPolicy.clear();
        assertEquals(Collections.emptyList(), Files.readAllLines(mPolicyFile));
    }

    @Test
    public void testAllowUidTransition_addNewRule() throws IOException {
        mPolicy.allowUidTransition(1001, 1002);

        List<String> expectedPolicy = List.of("1001:1002", "1002:1002");
        assertEquals(expectedPolicy, Files.readAllLines(mPolicyFile));
    }

    @Test
    public void testAllowUidTransition_addDuplicateRule() throws IOException {
        List<String> existingPolicy = List.of("1001:1002", "1002:1002");
        Files.write(mPolicyFile, existingPolicy, StandardCharsets.UTF_8);

        mPolicy.allowUidTransition(1001, 1002);

        assertEquals(existingPolicy, Files.readAllLines(mPolicyFile));
    }

    @Test
    public void testDisallowAllUidTransitionsFrom() throws IOException {
        List<String> existingPolicy = new ArrayList<>(List.of("1001:1002", "1003:1004"));
        Files.write(mPolicyFile, existingPolicy, StandardCharsets.UTF_8);

        mPolicy.disallowAllUidTransitionsFrom(1001);

        List<String> expectedPolicy = List.of("1003:1004", "1001:1001");
        assertEquals(expectedPolicy, Files.readAllLines(mPolicyFile));
    }

    @Test
    public void testPurgeFromPolicy() throws IOException {
        List<String> existingPolicy =
                new ArrayList<>(List.of("1001:1002", "1003:1004", "1004:1004", "1004:1007"));
        Files.write(mPolicyFile, existingPolicy, StandardCharsets.UTF_8);

        mPolicy.purgeFromPolicy(1004);

        List<String> expectedPolicy = List.of("1001:1002");
        assertEquals(expectedPolicy, Files.readAllLines(mPolicyFile));
    }
}
