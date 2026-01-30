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

import android.app.appsearch.AppSearchManager;

import org.junit.Test;

import java.io.File;
import java.util.Scanner;

/**
 * Random small tests.
 */
public class RavenwoodCoreBasicTest {

    private static String readFile(String file) throws Exception {
        try (var s = new Scanner(new File(file)).useDelimiter("\\Z")) {
            return s.next();
        }
    }

    // Use it for manual testing -- "run-ravenwood-tests.sh -s" should skip it.
    @Test
    @android.platform.test.annotations.LargeTest
    public void testLargeTest1() {
    }

    // Use it for manual testing -- "run-ravenwood-tests.sh -s" should skip it.
    @Test
    @androidx.test.filters.LargeTest
    public void testLargeTest2() {
    }

    @Test
    public void testDataFilesExist() throws Exception {
        assertEquals("datafile1", readFile("data/datafile1.txt"));
        assertEquals("datafile2", readFile("data/datafile2.txt"));
        assertEquals("morefile", readFile("data/subdir/morefile.txt"));
    }

    @Test
    public void testTestConfig() throws Exception {
        // These properties are injected via the Android.bp, using the -D java option.
        assertEquals("value1", System.getProperty("xxx-extra-tradefed-option"));
        assertEquals("value2", System.getProperty("xxx-extra-runner-option"));
    }

    @Test
    public void testClassPath() throws Exception {
        // The mainline stub jar has this class too, but our own copy should be used at runtime.
        assertEquals(42, AppSearchManager.foo());
    }
}
