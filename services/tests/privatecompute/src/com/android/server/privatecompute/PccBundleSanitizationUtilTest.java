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

package com.android.server.privatecompute;

import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.system.OsConstants;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PccBundleSanitizationUtilTest {

    @Test
    public void sanitizeBundle_bundle_allowsSafeTypes() throws Exception {
        Bundle bundle = getBundleWithPersistableTypes();

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Sanitization failed for safe types.", e);
        }
    }

    @Test
    public void sanitizeBundle_persistableBundle_allowsSafeTypes() throws Exception {
        PersistableBundle bundle = getPersistableBundleWithPersistableTypes();

        try {
            PccBundleSanitizationUtil.sanitizeBundle(bundle);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Sanitization failed for safe types.", e);
        }
    }

    @Test
    public void sanitizeBundle_disallowsBinders() {
        Bundle bundle = new Bundle();
        bundle.putBinder("binder", new Binder());
        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        });
    }

    @Test
    public void sanitizeBundle_nestedBundleWithBinder() {
        Bundle outerBundle = new Bundle();
        Bundle innerBundle = new Bundle();
        innerBundle.putBinder("nestedBinder", new Binder());
        outerBundle.putBundle("innerBundle", innerBundle);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(outerBundle));
        });
    }

    @Test
    public void sanitizeBundle_disallowsWritablePfd() throws IOException {
        File tempFile = File.createTempFile("test", "tmp");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile,
                ParcelFileDescriptor.MODE_READ_WRITE);

        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", pfd);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        });
    }

    @Test
    public void sanitizeBundle_allowsReadOnlyPfd() throws IOException {
        File tempFile = File.createTempFile("test", "tmp");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY);

        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", pfd);

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Sanitization failed for read-only PFD.", e);
        }
    }

    @Test
    public void sanitizeBundle_allowsReadOnlyParcelableArray() throws IOException {
        ParcelFileDescriptor pfd1 = ParcelFileDescriptor.open(File.createTempFile("test", "tmp"),
                ParcelFileDescriptor.MODE_READ_ONLY);

        Bundle bundle = new Bundle();
        bundle.putParcelableArray("pfdArray", new Parcelable[]{pfd1});
        addTriggerPfd(bundle);

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Sanitization failed for read-only PFD array.", e);
        }
    }

    @Test
    public void sanitizeBundle_disallowsCustomParcelable() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putParcelable("custom_parcelable", Uri.EMPTY);
        bundle.putParcelableArray("custom_parcelable_array", new Parcelable[]{Uri.EMPTY});
        addTriggerPfd(bundle);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        });
    }

    @Test
    public void sanitizeBundle_doesNotSkipCheckForCustomParcelable_withNoFdPresent() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("custom_parcelable", Uri.EMPTY);
        bundle.putParcelableArray("custom_parcelable_array", new Parcelable[]{Uri.EMPTY});

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        });
    }

    @Test
    public void sanitizeBundle_withBundleAndFd_checkDepth() throws Exception {
        Bundle nestedBundle100 = getBundleOfDepth100();
        addTriggerPfd(nestedBundle100);

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(nestedBundle100));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Bundle of depth 100 not accepted.", e);
        }

        Bundle nestedBundle101 = new Bundle();
        addTriggerPfd(nestedBundle101);
        nestedBundle101.putBundle("NESTED_BUNDLE_KEY", nestedBundle100);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(nestedBundle101));
        });
    }

    @Test
    public void sanitizeBundle_withBundleWithoutFd_checkDepth() throws Exception {
        Bundle nestedBundle100 = getBundleOfDepth100();

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(nestedBundle100));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Bundle of depth 100 not accepted.", e);
        }

        Bundle nestedBundle101 = new Bundle();
        nestedBundle101.putBundle("NESTED_BUNDLE_KEY", nestedBundle100);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(nestedBundle101));
        });
    }

    @Test
    public void sanitizeBundle_withPersistable_checkDepth() throws Exception {
        PersistableBundle nestedBundle100 = getPersistableBundleOfDepth100();

        try {
            PccBundleSanitizationUtil.sanitizeBundle(nestedBundle100);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("PersistableBundle of depth 100 not accepted.", e);
        }

        PersistableBundle nestedBundle101 = new PersistableBundle();
        nestedBundle101.putPersistableBundle("NESTED_BUNDLE_KEY", nestedBundle100);

        assertThrows(IllegalArgumentException.class, () -> {
            PccBundleSanitizationUtil.sanitizeBundle(nestedBundle101);
        });
    }

    @Test
    public void sanitizeBundle_allowsAllBitmaps() throws Exception {
        Bundle bundle = new Bundle();
        addVariousBitmaps(bundle);
        addTriggerPfd(bundle);

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        } catch (Exception e) {
            throw new AssertionError("Sanitization should not fail for Bitmaps.", e);
        }
    }

    @Test
    public void sanitizeBundle_handlesWritableSharedMemory() throws Exception {
        SharedMemory sm = SharedMemory.create("test", 1024);
        Bundle bundle = new Bundle();
        bundle.putParcelable("sm", sm);

        if (com.android.libcore.Flags.enablePccFrameworkSupport()) {
            assertThrows(IllegalArgumentException.class, () -> {
                PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
            });
        } else {
            // If the flag is disabled, we attempt to make the shared memory read-only instead of
            // throwing. Ideally we would verify that it is now read-only, but the
            // isRegionReadOnly() API is also guarded by the same flag. So we just verify it
            // doesn't throw.
            try {
                PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
            } catch (IllegalArgumentException e) {
                throw new AssertionError("Should not throw when flag is disabled", e);
            }
        }
    }

    @Test
    public void sanitizeBundle_allowsReadOnlySharedMemory() throws Exception {
        SharedMemory sm = SharedMemory.create("test", 1024);
        sm.setProtect(OsConstants.PROT_READ);
        Bundle bundle = new Bundle();
        bundle.putParcelable("sm", sm);

        try {
            PccBundleSanitizationUtil.sanitizeBundle(parcelAndUnparcel(bundle));
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Sanitization failed for read-only SharedMemory.", e);
        }
    }

    private void addVariousBitmaps(Bundle bundle) {
        Bitmap memoryBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Bitmap sharedMemoryBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        sharedMemoryBitmap.createAshmemBitmap();
        bundle.putParcelable("shared_memory_bitmap", sharedMemoryBitmap.createAshmemBitmap());
        bundle.putParcelableArray("bitmap_array",
                new Parcelable[]{sharedMemoryBitmap, memoryBitmap});
        bundle.putParcelable("in_memory_bitmap", memoryBitmap);
    }

    /** Helper to force the sanitization to run by adding a read-only PFD. */
    private void addTriggerPfd(Bundle bundle) throws IOException {
        File tempFile = File.createTempFile("trigger", "tmp");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
        bundle.putParcelable("trigger_pfd", pfd);
    }

    private Bundle getBundleWithPersistableTypes() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putByte("byte", (byte) 1);
        bundle.putByteArray("byte_array", new byte[]{(byte) 1, (byte) 2});
        bundle.putChar("char", 'a');
        bundle.putCharArray("char_array", new char[]{'a', 'b'});
        bundle.putShort("short", (short) 2);
        bundle.putShortArray("short_array", new short[]{(short) 2, (short) 3});
        bundle.putInt("int", 3);
        bundle.putIntArray("int_array", new int[]{3, 4});
        bundle.putLong("long", 4L);
        bundle.putLongArray("long_array", new long[]{4L, 5L});
        bundle.putFloat("float", 5.0f);
        bundle.putFloatArray("float_array", new float[]{5.0f, 6.0f});
        bundle.putDouble("double", 6.0);
        bundle.putDoubleArray("double_array", new double[]{6.0, 7.0});
        bundle.putBoolean("boolean", true);
        bundle.putBooleanArray("boolean_array", new boolean[]{true, false});
        bundle.putString("string", "hello");
        bundle.putStringArray("string_array", new String[]{"hello", "world"});
        bundle.putParcelable("persistableBundle", new PersistableBundle());
        addTriggerPfd(bundle);

        return bundle;
    }

    private PersistableBundle getPersistableBundleWithPersistableTypes() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putByteArray("byte_array", new byte[]{(byte) 1, (byte) 2});
        bundle.putInt("int", 3);
        bundle.putIntArray("int_array", new int[]{3, 4});
        bundle.putLong("long", 4L);
        bundle.putLongArray("long_array", new long[]{4L, 5L});
        bundle.putDouble("double", 6.0);
        bundle.putDoubleArray("double_array", new double[]{6.0, 7.0});
        bundle.putBoolean("boolean", true);
        bundle.putBooleanArray("boolean_array", new boolean[]{true, false});
        bundle.putString("string", "hello");
        bundle.putStringArray("string_array", new String[]{"hello", "world"});
        return bundle;
    }


    /** Create a nested bundle of depth 100. */
    private static Bundle getBundleOfDepth100() {
        Bundle rootBundle = new Bundle();

        Bundle currentBundle = rootBundle;
        for (int i = 0; i < 99; i++) {
            Bundle newInnerBundle = new Bundle();
            currentBundle.putBundle("NESTED_BUNDLE_KEY", newInnerBundle);
            currentBundle = newInnerBundle; // Move down one level
        }

        currentBundle.putBundle("NESTED_BUNDLE_KEY", new Bundle());

        return rootBundle;
    }


    /** Create a nested bundle of depth 100. */
    private static PersistableBundle getPersistableBundleOfDepth100() {
        PersistableBundle rootBundle = new PersistableBundle();

        PersistableBundle currentBundle = rootBundle;
        for (int i = 0; i < 99; i++) {
            PersistableBundle newInnerBundle = new PersistableBundle();
            currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", newInnerBundle);
            currentBundle = newInnerBundle; // Move down one level
        }

        currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", new PersistableBundle());

        return rootBundle;
    }

    /**
     * The bundle must be marshalled and unmarshalled once to get the correct value for
     * {@code bundle.hasBinders()}.
     */
    private Bundle parcelAndUnparcel(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(bundle);
            parcel.setDataPosition(0);
            return parcel.readBundle();
        } finally {
            parcel.recycle();
        }
    }
}
