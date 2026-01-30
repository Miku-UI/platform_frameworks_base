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
package com.android.server.pm;

import static com.android.server.pm.HsumBootUserInitializer.createInitialUserOnBoot;

import android.util.Log;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

// TODO(b/402486365): using a parameterized  test seems to be an overkill for now, but it will
// make it easier to add more parameters (like a flag)
public final class HsumBootUserInitializerCreateInitialUserOnBootTest
        extends AbstractHsumBootUserInitializerConstructorHelpersTestCase {

    private final boolean mConfigCreateInitialUser;
    private final boolean mResult;

    /** Useless javadoc to make checkstyle happy... */
    @Parameters(name = "{index}: cfgCreateIU={0},result={1}")
    public static Collection<Object[]> junitParametersPassedToConstructor() {
        return Arrays.asList(new Object[][] {
            { CFG_CREAT_INITIAL(false), RESULT(false) },
            { CFG_CREAT_INITIAL(true), RESULT(true) }
        });
    }

    public HsumBootUserInitializerCreateInitialUserOnBootTest(boolean configCreateInitialUser,
            boolean result) {
        mConfigCreateInitialUser = configCreateInitialUser;
        mResult = result;
        Log.v(mTag, "Constructor: configCreateInitialUser=" + configCreateInitialUser
                + ", result=" + result);
    }

    @Test
    public void testCreateInitialUserOnBoot() {
        mockConfigCreateInitialUser(mConfigCreateInitialUser);

        boolean result = createInitialUserOnBoot(mMockContext);

        expect.withMessage("createInitialUserOnBoot()").that(result).isEqualTo(mResult);
    }
}
