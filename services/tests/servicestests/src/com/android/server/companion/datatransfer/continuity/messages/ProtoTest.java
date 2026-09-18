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

package com.android.server.companion.datatransfer.continuity.messages;

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
public abstract class ProtoTest<T extends Proto> {

    protected abstract Proto.Builder<T> newBuilder();

    @Test
    public void testDefaultValue_roundTrip_works() throws Exception {
        verifyRoundTrip(newBuilder().build());
    }

    protected void verifyRoundTrip(T value) throws Exception {
        assertThat(newBuilder().readFromBytes(Proto.toBytes(value)).build()).isEqualTo(value);
    }
}
