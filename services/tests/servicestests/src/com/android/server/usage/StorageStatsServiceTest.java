/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.usage;

import static com.google.common.truth.Truth.assertThat;

import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StorageStatsServiceTest {
    private MockContentResolver mContentResolver;

    @Before
    public void setUp() throws Exception {
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
    }

    @Test
    public void testDoNotRunWhenDisabledFromSettingsGlobal() {
        Settings.Global.putInt(mContentResolver, Settings.Global.ENABLE_CACHE_QUOTA_CALCULATION, 0);

        assertThat(StorageStatsService.isCacheQuotaCalculationsEnabled(mContentResolver)).isFalse();
    }

    @Test
    public void testCalculationTaskIsEnabledByDefault() {
        // Put null to act as though there is no value here.
        Settings.Global.putString(
                mContentResolver, Settings.Global.ENABLE_CACHE_QUOTA_CALCULATION, null);

        assertThat(StorageStatsService.isCacheQuotaCalculationsEnabled(mContentResolver)).isTrue();
    }
}
