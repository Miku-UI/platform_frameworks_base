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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserManager;
import android.util.Log;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.am.ActivityManagerService;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

// TODO(b/): rename to HsumBootUserInitializerTest once HsumBootUserInitializerTest itself is
// renamed to HsumBootUserInitializerInitMethodTest
public final class HsumBootUserInitializerNonParameterizedTest {

    private static final String TAG = HsumBootUserInitializerNonParameterizedTest.class
            .getSimpleName();

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule extendedMockito = new ExtendedMockitoRule.Builder(this)
            .mockStatic(UserManager.class)
            .build();

    @Mock
    private UserManagerService mMockUms;

    @Mock
    private ActivityManagerService mMockAms;

    @Mock
    private PackageManagerService mMockPms;

    @Mock
    private ContentResolver mMockContentResolver;

    // NOTE: not mocking yet, but need a real one because of resources
    private final Context mRealContext = androidx.test.InstrumentationRegistry.getInstrumentation()
            .getTargetContext();

    @Test
    public void testCreateInstance_hsum() {
        mockIsHsum(true);

        var instance = HsumBootUserInitializer.createInstance(mMockUms, mMockAms, mMockPms,
                mMockContentResolver, mRealContext);

        expect.withMessage("result of createInstance()").that(instance).isNotNull();
    }

    @Test
    public void testCreateInstance_nonSsum() {
        mockIsHsum(false);

        var instance = HsumBootUserInitializer.createInstance(mMockUms, mMockAms, mMockPms,
                mMockContentResolver, mRealContext);

        expect.withMessage("result of createInstance()").that(instance).isNull();
    }

    private void mockIsHsum(boolean value) {
        Log.v(TAG, "mockIsHsum(" + value + ")");
        doReturn(value).when(UserManager::isHeadlessSystemUserMode);
    }
}
