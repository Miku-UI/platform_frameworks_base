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
package com.android.ravenwoodtest.coretest;

import static org.junit.Assert.assertEquals;

import android.platform.test.ravenwood.RavenwoodExperimentalApiChecker;
import android.platform.test.ravenwood.RavenwoodUnsupportedApiException;

import com.android.internal.ravenwood.RavenwoodHelperBridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RavenwoodExperimentalApiTest {
    private volatile boolean mWasAllowed;

    @Before
    public void setUp() throws Exception {
        mWasAllowed = RavenwoodExperimentalApiChecker.isExperimentalApiEnabled();
    }

    @After
    public void tearDown() throws Exception {
        RavenwoodExperimentalApiChecker.setExperimentalApiEnabledOnlyForTesting(mWasAllowed);
    }

    @Test(expected = RavenwoodUnsupportedApiException.class)
    public void testDisallowed() {
        RavenwoodExperimentalApiChecker.setExperimentalApiEnabledOnlyForTesting(false);

        RavenwoodHelperBridge.forExperimentalApiTest();
    }

    @Test
    public void testAllowed() {
        RavenwoodExperimentalApiChecker.setExperimentalApiEnabledOnlyForTesting(true);

        assertEquals(1, RavenwoodHelperBridge.forExperimentalApiTest());
    }

    @Test(expected = RavenwoodUnsupportedApiException.class)
    public void testOnExperimentalApiCalledWhenDisallowed() {
        RavenwoodExperimentalApiChecker.setExperimentalApiEnabledOnlyForTesting(false);

        RavenwoodExperimentalApiChecker.onExperimentalApiCalled(0);
    }

    @Test
    public void testOnExperimentalApiCalledWhenAllowed() {
        RavenwoodExperimentalApiChecker.setExperimentalApiEnabledOnlyForTesting(true);

        RavenwoodExperimentalApiChecker.onExperimentalApiCalled(0);
    }

    /**
     * This is for manually testing with $RAVENWOOD_ENABLE_EXP_API.
     */
    @Test
    @android.platform.test.annotations.DisabledOnRavenwood
    public void testAllowedWithExplicitCheckForManualTest() {
        assertEquals(1, RavenwoodHelperBridge.forExperimentalApiTest());
    }
}
