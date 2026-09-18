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
package com.android.ravenwoodtest.mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Test;

/**
 * Mockito 5 has changed the way {@link ArgumentMatcher} matches varargs. (Release notes:
 * https://github.com/mockito/mockito/releases/tag/v5.0.0)
 *
 * Because we still use Mockito 2/3 on the device side but Mockito 5 on Ravenwood,
 * this could cause troubles. However, fortunately, because it's just a matcher, we can basically
 * just set up two matchers to work it around. This class is a bivalent test for the workaround.
 */
public class RavenwoodMockitoVarargTest {
    interface VarargTester {
        int foo(String... args);
    }

    private static VarargTester newVarargTesterForMultipleMockitoVersions() {
        var mock = mock(VarargTester.class);

        // This would match on Mockito 5 and above
        doReturn(1).when(mock).foo(any(String[].class));

        // This would match on Mockito 4 and below.
        // On Mockito 5 too, it'd match for the null argument case.
        doReturn(1).when(mock).foo(any());


        return mock;
    }

    @Test
    public void testMultipleMockitoVersions_varArg0() {
        assertEquals(1, newVarargTesterForMultipleMockitoVersions().foo());
    }

    @Test
    public void testMultipleMockitoVersions_varArg1() {
        assertEquals(1, newVarargTesterForMultipleMockitoVersions().foo("a"));
    }

    @Test
    public void testMultipleMockitoVersions_varArg2() {
        assertEquals(1, newVarargTesterForMultipleMockitoVersions().foo("a", "b"));
    }

    @Test
    public void testMultipleMockitoVersions_varArgNull() {
        assertEquals(1, newVarargTesterForMultipleMockitoVersions().foo((String[]) null));
    }
}
