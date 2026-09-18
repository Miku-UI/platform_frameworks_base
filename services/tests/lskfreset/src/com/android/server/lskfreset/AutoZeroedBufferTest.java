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

package com.android.server.lskfreset;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutoZeroedBufferTest {
    @Test
    public void testAllocatedBuffer() {
        AutoZeroedBuffer buffer = new AutoZeroedBuffer(1024);
        assertNotNull(buffer.getBuffer());
        assertEquals(1024, buffer.getBuffer().length);
    }

    @Test
    public void testBufferIsZeroedOut() {
        byte[] testData = new byte[128];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) i;
        }

        // Make a copy to give to the AutoZeroedBuffer. We want to keep the original so that we can
        // compare against it without worrying if the original has been modified.
        byte[] ownedData = testData.clone();

        // Wrap the buffer in an auto-zero object and verify it is unchanged.
        try (var buffer = new AutoZeroedBuffer(ownedData)) {
            assertArrayEquals(testData, buffer.getBuffer());
        }

        // Normally we shouldn't use the array after giving it to the AutoZeroedBuffer, but for
        // testing purposes we will verify that it has been zeroed out.
        for (byte b : ownedData) {
            assertEquals(0, b);
        }
    }
}
