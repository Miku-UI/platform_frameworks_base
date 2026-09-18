/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android;

import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.systemui.VerifyRequiredTestPropertiesTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is named AAAPlusPlusVerifySysuiRequiredTestPropertiesTest for two reasons.
 * a) Its so awesome it deserves an AAA++
 * b) It should run first to draw attention to itself.
 *
 * For trues though: this test verifies that all the sysui tests extend the right classes.
 * This matters because including tests with different context implementations in the same
 * test suite causes errors, such as the incorrect settings provider being cached.
 * For an example, see {@link com.android.systemui.DependencyTest}.
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class AAAPlusPlusVerifySysuiRequiredTestPropertiesTest extends
        VerifyRequiredTestPropertiesTestBase {
    @Test
    public void testAllClassInheritance() throws Throwable {
        runTest();
    }
}
