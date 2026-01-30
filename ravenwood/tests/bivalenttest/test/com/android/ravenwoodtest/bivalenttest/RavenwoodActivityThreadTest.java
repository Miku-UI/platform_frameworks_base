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
import static org.junit.Assert.assertNotNull;

import android.app.ActivityThread;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public class RavenwoodActivityThreadTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void testActivityThreadCurrentSystemContext() {
        assertNotNull(ActivityThread.currentSystemContext());
    }

    @Test
    public void testActivityThreadCurrentSystemContextPackage() {
        assertEquals("android", ActivityThread.currentSystemContext().getPackageName());
    }

    @Test
    public void testActivityThreadCurrentApplication() {
        assertEquals(ActivityThread.currentApplication().getPackageName(),
                sContext.getPackageName());
    }
}
