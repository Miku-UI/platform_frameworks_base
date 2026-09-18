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

import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Document;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Record;

/**
 * Utility class hosting commonly used methods to interacting with {@link SharedDataStore.Document}
 * and {@link SharedDataStore.MutableDocument} .
 */
public class Docs {

    /** Get a string value of the given path from a {@code SharedDataStore<String>}. */
    @Nullable
    public static String getString(Document<String> doc, String path) {
        Record<String> r = doc.getRecord(path);
        return r == null ? null : r.get();
    }

    /** Get an integer value of the given path from a {@code SharedDataStore<String>}. */
    public static int getInt(Document<String> doc, String path) {
        return Integer.parseInt(getString(doc, path));
    }

    /** Put a string value to the given path in a {@code SharedDataStore<String>}. */
    public static void putString(MutableDocument<String> doc, String path, String val) {
        doc.putData(path, val);
    }

    /** Put an int value to the given path in a {@code SharedDataStore<String>}. */
    public static void putInt(MutableDocument<String> doc, String path, int val) {
        putString(doc, path, Integer.toString(val));
    }
}
