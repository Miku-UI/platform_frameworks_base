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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.PersistableBundle;
import com.android.internal.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditModeTestUtils {

    static final long TEST_REALTIME_NANOS = 1764409688L;
    static final long TEST_CURRENT_TIME_MILLIS = 1764409688000L;
    static final String TEST_PACKAGE_NAME = "test_package";
    static final int TEST_UID = 12;

    private static final String KEY_BOOLEAN = "boolean";
    private static final boolean VALUE_BOOLEAN = true;
    private static final String KEY_DOUBLE = "double";
    private static final double VALUE_DOUBLE = 1.0;
    private static final String KEY_INT = "int";
    private static final int VALUE_INT = 10;
    private static final String KEY_LONG = "long";
    private static final long VALUE_LONG = 1L;
    private static final String KEY_STRING = "string";
    private static final String VALUE_STRING = "test";
    private static final String KEY_BOOLEAN_ARRAY = "boolean_array";
    private static final boolean[] VALUE_BOOLEAN_ARRAY = new boolean[] {true, false};
    private static final String KEY_INT_ARRAY = "int_array";
    private static final int[] VALUE_INT_ARRAY = new int[] {10, 20};
    private static final String KEY_LONG_ARRAY = "long_array";
    private static final long[] VALUE_LONG_ARRAY = new long[] {1L, 2L};
    private static final String KEY_STRING_ARRAY = "string_array";
    private static final String[] VALUE_STRING_ARRAY = new String[] {"test", "test2"};
    private static final String KEY_BYTE_ARRAY = "bundle_array";
    private static final byte[] VALUE_BYTE_ARRAY = new byte[] {1, 2, 3};

    static AuditLogEntry getTestEntry() {
        return new AuditLogEntry(
                getTestBundle(),
                TEST_REALTIME_NANOS,
                TEST_CURRENT_TIME_MILLIS,
                TEST_PACKAGE_NAME,
                TEST_UID);
    }

    static PersistableBundle getTestBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(KEY_BOOLEAN, VALUE_BOOLEAN);
        bundle.putDouble(KEY_DOUBLE, VALUE_DOUBLE);
        bundle.putInt(KEY_INT, VALUE_INT);
        bundle.putLong(KEY_LONG, VALUE_LONG);
        bundle.putString(KEY_STRING, VALUE_STRING);
        bundle.putBooleanArray(KEY_BOOLEAN_ARRAY, VALUE_BOOLEAN_ARRAY);
        bundle.putIntArray(KEY_INT_ARRAY, VALUE_INT_ARRAY);
        bundle.putLongArray(KEY_LONG_ARRAY, VALUE_LONG_ARRAY);
        bundle.putStringArray(KEY_STRING_ARRAY, VALUE_STRING_ARRAY);
        bundle.putByteArray(KEY_BYTE_ARRAY, VALUE_BYTE_ARRAY);
        // TODO: Test for nested bundles.
        return bundle;
    }

    // Needed because .equals() on Bundle doesn't work for inner arrays.
    static void assertEqualsToTestBundle(PersistableBundle bundle) {
        assertEquals(10, bundle.size());
        assertEquals(VALUE_BOOLEAN, bundle.getBoolean(KEY_BOOLEAN));
        assertEquals(VALUE_DOUBLE, bundle.getDouble(KEY_DOUBLE), 0.0);
        assertEquals(VALUE_INT, bundle.getInt(KEY_INT));
        assertEquals(VALUE_LONG, bundle.getLong(KEY_LONG));
        assertEquals(VALUE_STRING, bundle.getString(KEY_STRING));
        assertArrayEquals(VALUE_BOOLEAN_ARRAY, bundle.getBooleanArray(KEY_BOOLEAN_ARRAY));
        assertArrayEquals(VALUE_INT_ARRAY, bundle.getIntArray(KEY_INT_ARRAY));
        assertArrayEquals(VALUE_LONG_ARRAY, bundle.getLongArray(KEY_LONG_ARRAY));
        assertArrayEquals(VALUE_STRING_ARRAY, bundle.getStringArray(KEY_STRING_ARRAY));
        assertArrayEquals(VALUE_BYTE_ARRAY, bundle.getByteArray(KEY_BYTE_ARRAY));
        // TODO: Test for nested bundles.
    }

    /** Reads the audit log file from an output stream. */
    static List<AuditLogEntry> readAuditLogFileFromStream(ByteArrayOutputStream stream)
            throws Exception {
        byte[] bytes = stream.toByteArray();
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytes));
        return readAuditLogFileFromStream(dataInputStream);
    }

    /** Reads the audit log file from a file. */
    static List<AuditLogEntry> readAuditLogFileFromFile(File file) throws Exception {
        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
        return readAuditLogFileFromStream(dataInputStream);
    }

    /** Reads the audit log file from an input stream. */
    static List<AuditLogEntry> readAuditLogFileFromStream(DataInputStream stream) throws Exception {
        int version = stream.readInt();
        if (version != AuditLogFileWriter.AUDIT_FILE_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported version: " + version + " in file.");
        }
        return readLogEntriesFromStream(stream);
    }

    /** Reads a list of length-prefixed {@link AuditLogEntry} from an output stream. */
    static List<AuditLogEntry> readLogEntriesFromStream(ByteArrayOutputStream stream)
            throws Exception {
        byte[] bytes = stream.toByteArray();
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytes));
        return readLogEntriesFromStream(dataInputStream);
    }

    /** Reads a list of length-prefixed {@link AuditLogEntry} from an output stream. */
    static List<AuditLogEntry> readLogEntriesFromFile(File file) throws Exception {
        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
        return readLogEntriesFromStream(dataInputStream);
    }

    /** Reads a list of length-prefixed {@link AuditLogEntry} from an input stream. */
    static List<AuditLogEntry> readLogEntriesFromStream(DataInputStream stream) throws Exception {
        List<AuditLogEntry> result = new ArrayList<>();
        while (stream.available() > 0) {
            long realTimeNanos = stream.readLong();
            long currentTimeMillis = stream.readLong();
            int callingUid = stream.readInt();
            int callingPackageLength = stream.readInt();
            byte[] callingPackageBytes = new byte[callingPackageLength];
            stream.read(callingPackageBytes);
            String callingPackage = new String(callingPackageBytes);
            int bundleLength = stream.readInt();
            byte[] bundleBytes = new byte[bundleLength];
            stream.read(bundleBytes);
            ByteArrayInputStream dataStream = new ByteArrayInputStream(bundleBytes);
            PersistableBundle bundle = PersistableBundle.readFromStream(dataStream);
            result.add(
                    new AuditLogEntry(
                            bundle, realTimeNanos, currentTimeMillis, callingPackage, callingUid));
        }
        return result;
    }
}
