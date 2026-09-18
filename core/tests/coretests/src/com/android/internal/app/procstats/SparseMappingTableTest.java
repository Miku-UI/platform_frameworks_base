/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.app.procstats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.testng.Assert.assertEquals;

import android.os.Parcel;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Provides test cases for SparseMappingTable.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class SparseMappingTableTest {
    private static final String TAG = "SparseMappingTableTest";

    private static final byte ID1 = 1;
    private static final byte ID2 = 2;

    private static final long VALUE1 = 100L;
    private static final long VALUE2 = 10000000000L;

    /**
     * Test the parceling and unparceling logic when there is no data.
     */
    @Test
    public void testParcelingEmpty() {
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        final Parcel dataParcel = Parcel.obtain();
        data.writeToParcel(dataParcel);

        final Parcel tableParcel = Parcel.obtain();
        table.writeToParcel(tableParcel);

        dataParcel.setDataPosition(0);
        final SparseMappingTable data1 = new SparseMappingTable();
        data1.readFromParcel(dataParcel);
        assertEquals(dataParcel.dataAvail(), 0);
        dataParcel.recycle();

        tableParcel.setDataPosition(0);
        final SparseMappingTable.Table table1 = new SparseMappingTable.Table(data1);
        table1.readFromParcel(tableParcel);
        assertEquals(tableParcel.dataAvail(), 0);
        tableParcel.recycle();
    }

    /**
     * Test the parceling and unparceling logic.
     */
    @Test
    public void testParceling()  {
        int key;
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        key = table.getOrAddKey(ID1, 1);
        table.setValue(key, VALUE1);

        key = table.getOrAddKey(ID2, 1);
        table.setValue(key, VALUE2);

        final Parcel dataParcel = Parcel.obtain();
        data.writeToParcel(dataParcel);

        final Parcel tableParcel = Parcel.obtain();
        table.writeToParcel(tableParcel);

        dataParcel.setDataPosition(0);
        final SparseMappingTable data1 = new SparseMappingTable();
        data1.readFromParcel(dataParcel);
        assertEquals(dataParcel.dataAvail(), 0);
        dataParcel.recycle();

        tableParcel.setDataPosition(0);
        final SparseMappingTable.Table table1 = new SparseMappingTable.Table(data1);
        table1.readFromParcel(tableParcel);
        assertEquals(tableParcel.dataAvail(), 0);
        tableParcel.recycle();

        key = table1.getKey(ID1);
        assertEquals(table1.getValue(key), VALUE1);

        key = table1.getKey(ID2);
        assertEquals(table1.getValue(key), VALUE2);
    }

    /**
     * Test that after resetting you can still read data, you just get no values.
     */
    @Test
    public void testParcelingWithReset() {
        int key;
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        key = table.getOrAddKey(ID1, 1);
        table.setValue(key, VALUE1);

        data.reset();
        table.resetTable();

        key = table.getOrAddKey(ID2, 1);
        table.setValue(key, VALUE2);

        Log.d(TAG, "before: " + data.dumpInternalState(true));
        Log.d(TAG, "before: " + table.dumpInternalState());

        final Parcel dataParcel = Parcel.obtain();
        data.writeToParcel(dataParcel);

        final Parcel tableParcel = Parcel.obtain();
        table.writeToParcel(tableParcel);

        dataParcel.setDataPosition(0);
        final SparseMappingTable data1 = new SparseMappingTable();
        data1.readFromParcel(dataParcel);
        assertEquals(dataParcel.dataAvail(), 0);
        dataParcel.recycle();

        tableParcel.setDataPosition(0);
        final SparseMappingTable.Table table1 = new SparseMappingTable.Table(data1);
        table1.readFromParcel(tableParcel);
        assertEquals(tableParcel.dataAvail(), 0);
        tableParcel.recycle();

        key = table1.getKey(ID1);
        assertEquals(key, SparseMappingTable.INVALID_KEY);

        key = table1.getKey(ID2);
        assertEquals(table1.getValue(key), VALUE2);

        Log.d(TAG, " after: " + data1.dumpInternalState(true));
        Log.d(TAG, " after: " + table1.dumpInternalState());
    }

    /**
     * Test that it fails if you reset the data and not the table.
     * <p>
     * Resetting the table and not the data is basically okay. The data in the
     * SparseMappingTable will be leaked.
     */
    @Test
    public void testResetDataOnlyFails() {
        int key;
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        key = table.getOrAddKey(ID1, 1);
        table.setValue(key, VALUE1);

        assertEquals(table.getValue(key), VALUE1);

        data.reset();

        try {
            table.getValue(key);
            // Turn off this assertion because the check in SparseMappingTable.assertConsistency
            // is also turned off.
            //throw new Exception("Exception not thrown after mismatched reset calls.");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test that trying to get data that you didn't add fails correctly.
     */
    @Test
    public void testInvalidKey() {
        int key;
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        key = table.getKey(ID1);

        // The key should be INVALID_KEY
        assertEquals(key, SparseMappingTable.INVALID_KEY);

        // If you get the value with getValueForId you get 0.
        assertEquals(table.getValueForId(ID1), 0);
    }


    /**
     * Test that getArrayForKey returns the correct array for a valid key,
     * and throws an exception for an invalid key.
     */
    @Test
    public void testGetArrayForKey() {
        final SparseMappingTable data = new SparseMappingTable();
        final SparseMappingTable.Table table = new SparseMappingTable.Table(data);

        // Test with a valid key
        int key1 = table.getOrAddKey(ID1, 1);
        table.setValue(key1, VALUE1);
        long[] array1 = table.getArrayForKey(key1);
        assertNotNull(array1);
        assertEquals(array1[SparseMappingTable.getIndexFromKey(key1)], VALUE1);

        // Test with an invalid key - one that is out of bounds
        int invalidKey = 0xFFFFFFFF;
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> table.getArrayForKey(invalidKey));
    }
}



