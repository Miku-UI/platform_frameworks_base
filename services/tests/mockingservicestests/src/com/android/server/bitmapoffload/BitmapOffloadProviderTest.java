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

package com.android.server.bitmapoffload;

import static com.android.server.bitmapoffload.BitmapOffload.BITMAP_SOURCE_NOTIFICATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class BitmapOffloadProviderTest {
    private BitmapOffloadProvider mProvider;
    private File mTestFile;

    @Before
    public void setUp() throws Exception {
        mProvider = new BitmapOffloadProvider();
        mTestFile = new File(InstrumentationRegistry.getContext().getCacheDir(), "test_bitmap");
        if (mTestFile.exists()) {
            mTestFile.delete();
        }
    }

    @Test
    public void testDeleteRemovesFileAndEntry() throws Exception {
        assertTrue(mTestFile.createNewFile());
        assertTrue(mTestFile.exists());

        ContentValues values = new ContentValues();
        values.put(BitmapOffloadContract.COLUMN_FILE_NAME, mTestFile.getAbsolutePath());
        values.put(BitmapOffloadContract.COLUMN_WIDTH, 100);
        values.put(BitmapOffloadContract.COLUMN_HEIGHT, 100);
        values.put(BitmapOffloadContract.COLUMN_OWNER_UID, 1000);
        values.put(BitmapOffloadContract.COLUMN_SOURCE, BITMAP_SOURCE_NOTIFICATIONS);

        Uri uri = mProvider.insert(BitmapOffloadContract.CONTENT_URI, values);

        int deleted = mProvider.delete(uri, null, null);

        assertEquals(1, deleted);
        assertFalse(mTestFile.exists());
    }

    @Test
    public void testDeleteNonExistentUri() {
        Uri uri = Uri.parse("content://com.android.server.bitmapoffload/bitmaps/999");
        int deleted = mProvider.delete(uri, null, null);
        assertEquals(0, deleted);
    }
}
