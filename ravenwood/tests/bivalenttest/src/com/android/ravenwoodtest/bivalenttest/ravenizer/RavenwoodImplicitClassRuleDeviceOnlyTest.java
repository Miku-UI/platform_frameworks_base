/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ravenwoodtest.bivalenttest.ravenizer;

import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test shouldn't be executed on Ravenwood.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood
public class RavenwoodImplicitClassRuleDeviceOnlyTest {
    public static final String TAG = "RavenwoodImplicitClassRuleDeviceOnlyTest";

    @BeforeClass
    public static void beforeClass() {
        // This method shouldn't be called
        assertFalse(RavenwoodRule.isOnRavenwood());
    }

    @Test
    public void testDeviceOnly() {
        assertFalse(RavenwoodRule.isOnRavenwood());
    }

    @AfterClass
    public static void afterClass() {
        assertFalse(RavenwoodRule.isOnRavenwood());
    }
}
