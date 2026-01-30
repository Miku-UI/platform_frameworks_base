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
package com.android.ravenwoodtest.bivalenttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.ravenwood.RavenwoodRule;

import com.android.modules.utils.ravenwood.RavenwoodHelper;

import org.junit.Test;

import java.io.File;

public class RavenwoodHelperTest {
    @Test
    public void testIsRunningOnRavenwood() {
        assertEquals(RavenwoodRule.isOnRavenwood(), RavenwoodHelper.isRunningOnRavenwood());
    }

    @Test
    public void testRavenwoodRuntimePath() {
        // getRavenwoodRuntimePath() only works on Ravenwood.
        assumeTrue(RavenwoodRule.isOnRavenwood());

        var path = new File(RavenwoodHelper.getRavenwoodRuntimePath());

        assertTrue(path.exists());
        assertTrue(path.isDirectory());
        assertTrue(new File(path, "framework-minus-apex.ravenwood.jar").exists());
    }

    @Test
    public void testGetRavenwoodAconfigStoragePath() {
        // getRavenwoodAconfigStoragePath() only works on Ravenwood.
        assumeTrue(RavenwoodRule.isOnRavenwood());

        var path = new File(RavenwoodHelper.getRavenwoodAconfigStoragePath());

        assertTrue(path.exists());
        assertTrue(path.isDirectory());
        assertTrue(new File(path, "metadata").exists());
    }
}
