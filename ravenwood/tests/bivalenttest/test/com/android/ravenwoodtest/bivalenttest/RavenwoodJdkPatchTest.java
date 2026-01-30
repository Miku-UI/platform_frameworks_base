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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.ravenwood.RavenwoodRule;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.junit.Test;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public class RavenwoodJdkPatchTest {

    @Test
    public void testUnicodeRegex() {
        var pattern = Pattern.compile("\\w+");
        assertTrue(pattern.matcher("über").matches());
    }

    @Test
    public void testLinkedHashMapEldest() {
        var map = new LinkedHashMap<String, String>();
        map.put("a", "b");
        map.put("x", "y");
        assertEquals(map.entrySet().iterator().next(), map.eldest());
    }

    @Test
    public void testFileDescriptorGetSetInt() throws ErrnoException {
        FileDescriptor fd = Os.open("/dev/zero", OsConstants.O_RDONLY, 0);
        try {
            int fdRaw = fd.getInt$();
            assertNotEquals(-1, fdRaw);
            fd.setInt$(-1);
            assertEquals(-1, fd.getInt$());
            fd.setInt$(fdRaw);
            Os.close(fd);
            assertEquals(-1, fd.getInt$());
        } finally {
            if (fd.valid()) {
                Os.close(fd);
            }
        }
    }

    private static class SubFileInputStream extends FileInputStream {

        SubFileInputStream(FileDescriptor fdObj) {
            super(fdObj);
        }
    }

    private static class SubFileOutputStream extends FileOutputStream {

        SubFileOutputStream(FileDescriptor fdObj) {
            super(fdObj);
        }
    }

    @Test
    public void testFileInputOutputStream() throws ErrnoException, IOException {
        FileDescriptor fd = Os.open("/dev/urandom", OsConstants.O_RDWR, 0);
        try {
            try (var fis = new FileInputStream(fd)) {
                if (RavenwoodRule.isOnRavenwood()) {
                    assertEquals("com.android.ravenwood.RavenwoodFileInputStream",
                            fis.getClass().getName());
                }
                // It should be the exact same instance, don't use assertEquals here
                assertTrue(fis.getFD() == fd);
            }
            assertTrue(fd.valid());
            try (var fos = new FileOutputStream(fd)) {
                if (RavenwoodRule.isOnRavenwood()) {
                    assertEquals("com.android.ravenwood.RavenwoodFileOutputStream",
                            fos.getClass().getName());
                }
                // It should be the exact same instance, don't use assertEquals here
                assertTrue(fos.getFD() == fd);
            }
            assertTrue(fd.valid());
            new SubFileInputStream(fd).close();
            assertTrue(fd.valid());
            new SubFileOutputStream(fd).close();
            assertTrue(fd.valid());
            new SubFileInputStream(fd) {}.close();
            assertTrue(fd.valid());
            new SubFileOutputStream(fd) {}.close();
        } finally {
            Os.close(fd);
        }

        fd = Os.open("/dev/urandom", OsConstants.O_RDONLY, 0);
        new FileInputStream(fd, true).close();
        assertFalse(fd.valid());

        // For some reason, FileOutputStream(FileDescriptor, boolean) does not exist in any stub
        // JARs during build time. We cannot check whether the constructor patch works in this test.
    }
}
