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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringConverterTest extends SyncServiceTestBase {

    @Test
    public void testNullString() {
        byte[] bytes = mStringConverter.serialize(null);

        assertThat(mStringConverter.deserialize(bytes)).isNull();
    }

    @Test
    public void testEmptyString() {
        byte[] bytes = mStringConverter.serialize("");

        assertThat(mStringConverter.deserialize(bytes)).isEmpty();
    }

    @Test
    public void testNormalString() {
        byte[] bytes = mStringConverter.serialize("abc");

        assertThat(mStringConverter.deserialize(bytes)).isEqualTo("abc");
    }

    @Test
    public void testDeserialize_nullBytes_returnNull() {
        assertThat(mStringConverter.deserialize(null)).isNull();
    }

    @Test
    public void testDeserialize_emptyBytes_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> mStringConverter.deserialize(new byte[0]));
    }

    @Test
    public void testDeserialize_illegalFlag_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mStringConverter.deserialize(new byte[] {0x02}));
    }
}
