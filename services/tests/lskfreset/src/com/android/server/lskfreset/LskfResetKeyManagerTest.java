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

package com.android.server.lskfreset;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.lskfreset.flags.Flags;
import android.content.Context;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;

@RunWith(AndroidJUnit4.class)
public class LskfResetKeyManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private LskfResetKeyManager mLskfResetKeyManager;

    @Before
    public void setUp() throws GeneralSecurityException {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLskfResetKeyManager = new LskfResetKeyManager(mContext);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testGenerateKeyRuns() {
        assertNotNull(mLskfResetKeyManager.generateAndStoreLskfResetKey("TestAlias"));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testGenerateLocalAndDeviceKeysRuns() {
        assertTrue(mLskfResetKeyManager.generateDeviceAndAccountKeys("TestAlias"));
    }

}
