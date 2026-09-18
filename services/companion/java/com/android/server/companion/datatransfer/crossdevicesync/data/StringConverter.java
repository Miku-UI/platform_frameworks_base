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

import android.annotation.Nullable;

import com.google.android.submerge.Converter;

import java.nio.charset.StandardCharsets;

/** Class responsible for serializing/deserializing @Nullable strings. */
public class StringConverter implements Converter<String> {
    private static final String TAG = "StringConverter";
    private static final byte FLAG_NULL = 0x00;
    private static final byte FLAG_NON_NULL = 0x01;

    public StringConverter() {}

    @Nullable
    @Override
    public String deserialize(byte[] bytes) {
        if (bytes == null) {
            // Submerge uses null byte[] when there is no data in persistent storage. So
            // gracefully handle this case instead of crashing.
            return null;
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Invalid byte array length.");
        }
        byte flag = bytes[0];
        if (flag == FLAG_NULL) {
            return null; // Original string was null
        } else if (flag == FLAG_NON_NULL) {
            if (bytes.length > 1) {
                // Decode the rest of the byte array as the string
                return new String(bytes, 1, bytes.length - 1, StandardCharsets.UTF_8);
            } else {
                // The rest of the byte array is empty.
                return new String(new byte[0], StandardCharsets.UTF_8);
            }
        } else {
            throw new IllegalArgumentException("Invalid encoding flag: " + flag);
        }
    }

    @Override
    public byte[] serialize(@Nullable String value) {
        if (value == null) {
            return new byte[] {FLAG_NULL}; // Flag for null, no string data follows
        } else {
            byte[] stringBytes = value.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[1 + stringBytes.length];
            bytes[0] = FLAG_NON_NULL; // Flag for non-null
            System.arraycopy(stringBytes, 0, bytes, 1, stringBytes.length);
            return bytes;
        }
    }
}
