/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settingslib.media;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InfoMediaManagerIntegTest {

    private static final String FAKE_PACKAGE = "FAKE_PACKAGE";

    private Context mContext;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void createInstance_withValidPackage_returnsRouterInfoMediaManager() {
        InfoMediaManager manager =
                InfoMediaManager.createInstance(
                        mContext, mContext.getPackageName(), mContext.getUser(), null, null);
        assertThat(manager).isInstanceOf(RouterInfoMediaManager.class);
    }

    @Test
    public void createInstance_withFakePackage_returnsNoOpInfoMediaManager() {
        InfoMediaManager manager =
                InfoMediaManager.createInstance(mContext, FAKE_PACKAGE, null, null, null);
        assertThat(manager).isInstanceOf(NoOpInfoMediaManager.class);
    }

    @Test
    public void createInstance_withNullPackage_returnsRouterInfoMediaManager() {
        InfoMediaManager manager =
                InfoMediaManager.createInstance(mContext, null, null, null, null);
        assertThat(manager).isInstanceOf(RouterInfoMediaManager.class);
    }
}
