/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.os;

import static android.system.OsConstants.PROT_READ;

import android.app.privatecompute.flags.Flags;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SharedMemoryTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @DisabledOnRavenwood(reason = "SharedMemory.create() not supported on Ravenwood")
    @EnableFlags(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsRegionReadOnly() throws Exception {
        SharedMemory sharedMemory = SharedMemory.create("test", 1024);
        try {
            Assert.assertFalse(sharedMemory.isRegionReadOnly());

            sharedMemory.setProtect(PROT_READ);
            Assert.assertTrue(sharedMemory.isRegionReadOnly());
        } finally {
            sharedMemory.close();
        }
    }

    @Test
    @DisabledOnRavenwood(reason = "SharedMemory.create() not supported on Ravenwood")
    @EnableFlags(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsRegionReadOnlyOnClosedFd() throws Exception {
        SharedMemory sharedMemory = SharedMemory.create("test", 1024);
        FileDescriptor fd = sharedMemory.getFileDescriptor();
        android.system.Os.close(fd);

        try {
            sharedMemory.isRegionReadOnly();
            Assert.fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "SharedMemory is closed", e.getMessage());
        } finally {
            // Attempt to close, although the fd is already closed.
            sharedMemory.close();
        }
    }
}
