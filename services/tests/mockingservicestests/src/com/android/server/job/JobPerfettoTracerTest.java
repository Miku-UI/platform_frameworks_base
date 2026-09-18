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

package com.android.server.job;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class JobPerfettoTracerTest {

    @Test
    public void testCreate_returnsNonNullTracer() {
        JobPerfettoTracer tracer = JobPerfettoTracer.create();

        // Due to the way the Perfetto class provides a static cache of the
        // runtime flag to check the state of the Perfetto API version, there is
        // no safe way to validate the distinction between JobPerfettoTracerV3
        // and JobPerfettoTracerLegacy versions of the object. Therefore, just
        // verify that the tracer is not null.
        assertNotNull("Tracer should not be null", tracer);

        try {
            // The fluent API of the tracer should be safe to call.
            tracer.startEvent("event")
                    .addField(1L, 1)
                    .addField(2L, 2L)
                    .emit();
        } catch (Exception e) {
            throw new AssertionError("Tracer methods should not throw exceptions", e);
        }
    }
}
