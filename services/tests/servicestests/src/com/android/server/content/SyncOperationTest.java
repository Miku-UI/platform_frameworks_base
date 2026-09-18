/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.flags.Flags;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for SyncOperation.
 *
 * atest ${ANDROID_BUILD_TOP}/frameworks/base/services/tests/servicestests/src/com/android/server/content/SyncOperationTest.java
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SyncOperationTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    Account mDummy;
    /** Indicate an unimportant long that we're not testing. */
    long mUnimportantLong = 0L;
    /** Empty bundle. */
    Bundle mEmpty;
    /** Silly authority. */
    String mAuthority;

    @Before
    public void setUp() {
        mDummy = new Account("account1", "type1");
        mEmpty = new Bundle();
        mAuthority = "authority1";
    }

    @Test
    public void testToKey() {
        Account account1 = new Account("account1", "type1");
        Account account2 = new Account("account2", "type2");

        Bundle b1 = new Bundle();
        Bundle b2 = new Bundle();
        b2.putBoolean("b2", true);

        SyncOperation op1 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different time infos
        SyncOperation op2 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different authority
        SyncOperation op3 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority2",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different account
        SyncOperation op4 = new SyncOperation(account2, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different bundle
        SyncOperation op5 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b2,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        assertEquals(op1.key, op2.key);
        assertNotSame(op1.key, op3.key);
        assertNotSame(op1.key, op4.key);
        assertNotSame(op1.key, op5.key);
    }

    @Test
    public void testConversionToExtras() {
        Account account1 = new Account("account1", "type1");
        Bundle b1 = new Bundle();
        b1.putParcelable("acc", account1);
        b1.putString("str", "String");

        SyncOperation op1 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        PersistableBundle pb = op1.toJobInfoExtras();
        SyncOperation op2 = SyncOperation.maybeCreateFromJobExtras(pb);

        assertTrue("Account fields in extras not persisted.",
                account1.equals(op2.getClonedExtras().get("acc")));
        assertTrue("Fields in extras not persisted", "String".equals(
                op2.getClonedExtras().getString("str")));
    }

    @Test
    public void testConversionFromExtras() {
        PersistableBundle extras = new PersistableBundle();
        SyncOperation op = SyncOperation.maybeCreateFromJobExtras(extras);
        assertTrue("Non sync operation bundle falsely converted to SyncOperation.", op == null);
    }

    /**
     * Tests whether a failed periodic sync operation is converted correctly into a one time
     * sync operation, and whether the periodic sync can be re-created from the one-time operation.
     */
    @Test
    public void testFailedPeriodicConversion() {
        SyncStorageEngine.EndPoint ep = new SyncStorageEngine.EndPoint(new Account("name", "type"),
                "provider", 0);
        Bundle extras = new Bundle();
        SyncOperation periodic = new SyncOperation(ep, 0, "package", 0, 0, extras, false, true,
                SyncOperation.NO_JOB_ID, 60000, 10000,
                ContentResolver.SYNC_EXEMPTION_NONE);
        SyncOperation oneoff = periodic.createOneTimeSyncOperation();
        assertFalse("Conversion to oneoff sync failed.", oneoff.isPeriodic);
        assertEquals("Period not restored", periodic.periodMillis, oneoff.periodMillis);
        assertEquals("Flex not restored", periodic.flexMillis, oneoff.flexMillis);
    }

    @Test
    public void testScheduleAsEjIsInExtras() {
        Account account1 = new Account("account1", "type1");
        Bundle b1 = new Bundle();
        b1.putBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true);

        SyncOperation op1 = new SyncOperation(account1, 0, 1, "foo", 0,
                SyncOperation.REASON_USER_START, "authority1", b1, false,
                ContentResolver.SYNC_EXEMPTION_NONE);
        assertTrue(op1.isScheduledAsExpeditedJob());

        PersistableBundle pb = op1.toJobInfoExtras();
        assertTrue("EJ extra not found in job extras",
                ((PersistableBundle) pb.get("syncExtras"))
                        .containsKey(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB));

        SyncOperation op2 = SyncOperation.maybeCreateFromJobExtras(pb);
        assertTrue("EJ extra not found in extras", op2.getClonedExtras()
                .getBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB));
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras() {
        Bundle extras = new Bundle();
        extras.putString("key1", "val1");

        // Test String truncation
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("a");
        String longString = sb.toString();
        extras.putString("key2", longString);

        // Test String array truncation
        extras.putStringArray("key3", new String[]{longString});

        SyncOperation op = new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);

        Bundle result = op.getClonedExtras();
        assertEquals("val1", result.getString("key1"));
        assertEquals(127, result.getString("key2").length());
        assertEquals(longString.substring(0, 127), result.getString("key2"));
        assertEquals(127, result.getStringArray("key3")[0].length());
        assertEquals(longString.substring(0, 127), result.getStringArray("key3")[0]);
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_nestedBundle() {
        Bundle extras = new Bundle();
        extras.putBundle("nested", new Bundle());
        try {
            new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                    false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_nullAndEmpty() {
        // Test null extras
        SyncOperation opNull = new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, null,
                false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);
        assertTrue(opNull.getClonedExtras().isEmpty());

        // Test empty extras
        SyncOperation opEmpty = new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority,
                new Bundle(), false, ContentResolver.SYNC_EXEMPTION_NONE,
                true /* validateExtras */);
        assertTrue(opEmpty.getClonedExtras().isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_validTypes() {
        Bundle extras = new Bundle();
        extras.putString("nullValue", null);
        Account acc = new Account("a", "b");
        extras.putParcelable("account", acc);
        extras.putBoolean("bool", true);
        extras.putInt("int", 1);
        extras.putLong("long", 1L);
        extras.putFloat("float", 1.1f);
        extras.putDouble("double", 1.1);

        // Arrays (valid length)
        extras.putStringArray("strArr", new String[] {"a"});
        extras.putIntArray("intArr", new int[] {1});
        extras.putLongArray("longArr", new long[] {1L});
        extras.putFloatArray("floatArr", new float[] {1.1f});
        extras.putDoubleArray("doubleArr", new double[] {1.1});
        extras.putBooleanArray("boolArr", new boolean[] {true});

        SyncOperation op = new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);
        Bundle result = op.getClonedExtras();

        assertTrue(result.containsKey("nullValue"));
        assertNull(result.get("nullValue"));

        assertEquals(acc, result.getParcelable("account"));
        assertTrue(result.getBoolean("bool"));
        assertEquals(1, result.getInt("int"));
        assertEquals(1L, result.getLong("long"));
        assertEquals(1.1f, result.getFloat("float"), 0.0001f);
        assertEquals(1.1, result.getDouble("double"), 0.0001);

        assertEquals("a", result.getStringArray("strArr")[0]);
        assertEquals(1, result.getIntArray("intArr")[0]);
        assertEquals(1L, result.getLongArray("longArr")[0]);
        assertEquals(1.1f, result.getFloatArray("floatArr")[0], 0.0001f);
        assertEquals(1.1, result.getDoubleArray("doubleArr")[0], 0.0001);
        assertTrue(result.getBooleanArray("boolArr")[0]);
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_arraysTooLong() {
        // String[]
        checkArrayTooLong(b -> b.putStringArray("key", new String[11]));
        // int[]
        checkArrayTooLong(b -> b.putIntArray("key", new int[11]));
        // long[]
        checkArrayTooLong(b -> b.putLongArray("key", new long[11]));
        // double[]
        checkArrayTooLong(b -> b.putDoubleArray("key", new double[11]));
        // float[]
        checkArrayTooLong(b -> b.putFloatArray("key", new float[11]));
        // boolean[]
        checkArrayTooLong(b -> b.putBooleanArray("key", new boolean[11]));
    }

    private interface BundlePopulator {
        void populate(Bundle b);
    }

    private void checkArrayTooLong(BundlePopulator populator) {
        Bundle extras = new Bundle();
        populator.populate(extras);
        try {
            new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                    false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);
            fail("Should have thrown IllegalArgumentException for array too long");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_unsupportedType() {
        Bundle extras = new Bundle();
        extras.putSerializable("key", java.util.UUID.randomUUID()); // UUID implements Serializable
        try {
            new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                    false, ContentResolver.SYNC_EXEMPTION_NONE, true /* validateExtras */);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNCOPERATION_ENFORCE_BUNDLE_SANITIZATION)
    public void testSanitizeExtras_flagDisabled() {
        Bundle extras = new Bundle();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("a");
        String longString = sb.toString();
        extras.putString("key1", longString);
        extras.putBundle("nested", new Bundle());
        extras.putSerializable("uuid", java.util.UUID.randomUUID());

        SyncOperation op = new SyncOperation(mDummy, 0, 0, "package", 0, 0, mAuthority, extras,
                false, ContentResolver.SYNC_EXEMPTION_NONE);

        Bundle result = op.getClonedExtras();
        // Should not be truncated
        assertEquals(longString, result.getString("key1"));
        // Should contain nested bundle
        assertTrue(result.containsKey("nested"));
        // Should contain serializable
        assertTrue(result.containsKey("uuid"));
    }
}
