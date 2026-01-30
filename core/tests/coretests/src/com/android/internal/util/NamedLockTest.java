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
package com.android.internal.util;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class NamedLockTest {

    @Test
    public void testFactoryMethod_null() {
        assertThrows(NullPointerException.class, () -> NamedLock.create(null));
    }

    @Test
    public void testFactoryMethod_empty() {
        assertThrows(IllegalArgumentException.class, () -> NamedLock.create(""));
        assertThrows(IllegalArgumentException.class, () -> NamedLock.create(" "));
        assertThrows(IllegalArgumentException.class, () -> NamedLock.create("\t"));
    }

    @Test
    public void testFactoryMethod_startsWithSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedLock.create(" NAME, Y U START WITH SPACE?"));
    }

    @Test
    public void testFactoryMethod_startsWithTab() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedLock.create("\tNAME, Y U START WITH TAB?"));
    }

    @Test
    public void testFactoryMethod_endsWithSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedLock.create("NAME, Y U END WITH SPACE? "));
    }

    @Test
    public void testFactoryMethod_endsWithTab() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedLock.create("NAME, Y U END WITH TAB?\t"));
    }

    @Test
    public void testOneInstance() {
        var namedLock = NamedLock.create("Bond, James Bond");

        assertWithMessage("create()").that(namedLock).isNotNull();

        assertWithMessage("toString()").that(namedLock.toString()).isEqualTo("Bond, James Bond");
    }

    @Test
    public void testMultipleInstances_sameName() {
        String commonName = "Bond, James Bond";
        var namedLock1 = NamedLock.create(commonName);
        var namedLock2 = NamedLock.create(commonName);

        assertWithMessage("namedLock1").that(namedLock1).isNotSameInstanceAs(namedLock2);
        assertWithMessage("namedLock1").that(namedLock1).isNotEqualTo(namedLock2);
        assertWithMessage("namedLock2").that(namedLock2).isNotEqualTo(namedLock1);
    }

    @Test
    public void testMultipleInstances_differentNames() {
        var namedLock1 = NamedLock.create("Bond, James Bond");
        var namedLock2 = NamedLock.create("A Lock has a Name");

        assertWithMessage("namedLock1").that(namedLock1).isNotSameInstanceAs(namedLock2);
        assertWithMessage("namedLock1").that(namedLock1).isNotEqualTo(namedLock2);
        assertWithMessage("namedLock2").that(namedLock2).isNotEqualTo(namedLock1);
    }
}
