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

package android.app.memory.tests;

/**
 * This class manages how types are reprepresented in a heap dump.  The important attribute of a
 * type for scanning is its width.
 */
public enum Type {
    TypeObject(0),
    TypeBoolean(1),
    TypeChar(2),
    TypeFloat(4),
    TypeDouble(8),
    TypeByte(1),
    TypeShort(2),
    TypeInt(4),
    TypeLong(8);

    // The number of bytes used to represent objects of this type.  A size of 0 signfifies
    // a variable size of IDs.
    private final int mSize;

    Type(int size) {
        mSize = size;
    }

    int size() {
        return mSize;
    }
}
