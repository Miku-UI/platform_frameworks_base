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
package com.android.ravenwoodtest.bivalenttest.aconfig;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.os.Flags;
import com.android.ravenwoodtest.bivalenttest.RavenwoodJniTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RavenwoodAconfigNativeFlagsTest {
    static {
        RavenwoodJniTest.initializeJni();
    }

    private static native boolean getRavenwoodFlagRo1();
    private static native boolean getRavenwoodFlagRo2();
    private static native boolean getRavenwoodFlagRw1();
    private static native boolean getRavenwoodFlagRw2();

    @Test
    public void testRavenwoodFlagRo1() {
        assertEquals(Flags.ravenwoodFlagRo1(), getRavenwoodFlagRo1());
    }

    @Test
    public void testRavenwoodFlagRo2() {
        assertEquals(Flags.ravenwoodFlagRo2(), getRavenwoodFlagRo2());
    }

    @Test
    public void testRavenwoodFlagRw1() {
        assertEquals(Flags.ravenwoodFlagRw1(), getRavenwoodFlagRw1());
    }

    @Test
    public void testRavenwoodFlagRw2() {
        assertEquals(Flags.ravenwoodFlagRw2(), getRavenwoodFlagRw2());
    }
}
