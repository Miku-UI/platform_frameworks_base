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
package com.android.ravenwoodtest.coretest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.ravenwood.annotation.RavenwoodRemove;

import org.junit.Test;

import java.util.Arrays;

public class RavenwoodRavenizerTest {
    @Test
    public void testRemoveClass() {
        try {
            var c = ToBeRemoved.class;
            fail("Class ToBeRemoved expected to be removed");
        } catch (java.lang.NoClassDefFoundError e) {
            // these are okay
        }
    }

    @Test
    public void testRemoveMethod() {
        var c = ToBeKept.class;
        assertThat(Arrays.stream(c.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("toBeRemoved"))).isFalse();
    }
}

@RavenwoodRemove
class ToBeRemoved {
}

class ToBeKept {
    @RavenwoodRemove
    public static void toBeRemoved() {
    }
}
