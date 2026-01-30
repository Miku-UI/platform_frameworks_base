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

package com.android.server.bitmapoffload;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.server.bitmapoffload.BitmapOffload.BITMAP_SOURCE_NOTIFICATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.storage.StorageManager;
import android.util.DataUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class BitmapOffloadServiceTest {
    private final Context mRealContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().getTargetContext();

    @Mock
    private Context mContext;
    @Mock
    private ContentResolver mResolver;
    @Mock
    private StorageManager mStorageManager;

    private BitmapOffloadService mService;

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mStorageManager = mock(StorageManager.class);
        mResolver = mock(ContentResolver.class);
        when(mContext.getSystemService(StorageManager.class)).thenReturn(mStorageManager);
        when(mStorageManager.getStorageLowBytes(any())).thenReturn(DataUnit.MEBIBYTES.toBytes(256));
        when(mContext.getContentResolver()).thenReturn(mResolver);

        mService = new BitmapOffloadService(mContext);
        mService.mBitmapDir = spy(mRealContext.getFilesDir());

        mService.mOffloadThread = new HandlerThread("BitmapOffloadThread");
        mService.mOffloadThread.start();
    }

    @Test
    public void testOffloadBitmap_offloadBitmap() {
        final Uri offloadUri = ContentUris.withAppendedId(BitmapOffloadContract.CONTENT_URI, 0);

        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
        when(mResolver.insert(eq(BitmapOffloadContract.CONTENT_URI), values.capture()))
                .thenReturn(offloadUri);
        Bitmap mockBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);

        Uri uri = mService.mInternalService.offloadBitmap(BITMAP_SOURCE_NOTIFICATIONS, mockBitmap);

        assertNotNull(uri);
        assertEquals(100, values.getValue().get(BitmapOffloadContract.COLUMN_HEIGHT));
        assertEquals(200, values.getValue().get(BitmapOffloadContract.COLUMN_WIDTH));
    }

    @Test
    public void testOffloadBitmap_offloadBitmapNoFreeSpace() {
        // Simulate only 5MB of free space
        doReturn((5L * 1024 * 1024)).when(mService.mBitmapDir).getFreeSpace();
        Bitmap mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        Uri uri = mService.mInternalService.offloadBitmap(BITMAP_SOURCE_NOTIFICATIONS, mockBitmap);

        assertNull(uri);
    }

    @Test
    public void testRegisterAndExecutePermissionHandler() {
        final Uri offloadUri = ContentUris.withAppendedId(BitmapOffloadContract.CONTENT_URI, 0);

        BitmapOffloadInternal.PermissionHandler mockHandler = mock(
                BitmapOffloadInternal.PermissionHandler.class);
        mService.mInternalService.registerPermissionHandler(BITMAP_SOURCE_NOTIFICATIONS,
                mockHandler);

        mService.mInternalService.checkPermission(BITMAP_SOURCE_NOTIFICATIONS, offloadUri, 1481,
                1482);

        verify(mockHandler).isAllowedToOpen(offloadUri, 1481, 1482);

    }

    @Test
    public void testNoPermissionHandlerThrows() {
        final Uri offloadUri = ContentUris.withAppendedId(BitmapOffloadContract.CONTENT_URI, 0);

        assertThrows(SecurityException.class,
                () -> mService.mInternalService.checkPermission(BITMAP_SOURCE_NOTIFICATIONS,
                        offloadUri, 1481, 1482));
    }
}
