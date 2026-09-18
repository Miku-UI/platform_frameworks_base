/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.ravenwoodtest.bivalenttest;

import static android.Manifest.permission.BIND_JOB_SERVICE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

public class RavenwoodContextTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testPermissionCheck() {
        assertEquals(PERMISSION_DENIED, mContext.checkPermission(
                BIND_JOB_SERVICE, Process.myPid(), Process.myUid()));
        assertEquals(PERMISSION_DENIED, mContext.checkSelfPermission(BIND_JOB_SERVICE));
        assertEquals(PERMISSION_DENIED, mContext.checkCallingPermission(BIND_JOB_SERVICE));
        assertEquals(PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(BIND_JOB_SERVICE));
        assertThrows(SecurityException.class, () ->
                mContext.enforcePermission(BIND_JOB_SERVICE, Process.myPid(), Process.myUid(), ""));
        assertThrows(SecurityException.class, () ->
                mContext.enforceCallingPermission(BIND_JOB_SERVICE, ""));
        assertThrows(SecurityException.class, () ->
                mContext.enforceCallingOrSelfPermission(BIND_JOB_SERVICE, ""));
    }

    @Test
    public void testPermissionCheckMockGrant() {
        Context context = spy(mContext);
        doReturn(PERMISSION_GRANTED).when(context).checkPermission(anyString(), anyInt(), anyInt());

        assertEquals(PERMISSION_GRANTED, context.checkPermission(
                BIND_JOB_SERVICE, Process.myPid(), Process.myUid()));
        assertEquals(PERMISSION_GRANTED, context.checkSelfPermission(BIND_JOB_SERVICE));
        assertEquals(PERMISSION_DENIED, context.checkCallingPermission(BIND_JOB_SERVICE));
        assertEquals(PERMISSION_GRANTED, context.checkCallingOrSelfPermission(BIND_JOB_SERVICE));
        context.enforcePermission(BIND_JOB_SERVICE, Process.myPid(), Process.myUid(), "");
        assertThrows(SecurityException.class, () ->
                context.enforceCallingPermission(BIND_JOB_SERVICE, ""));
        context.enforceCallingOrSelfPermission(BIND_JOB_SERVICE, "");
    }

    @Test
    public void testCreateConfigurationContext() {
        assertEquals("Test", mContext.getString(R.string.test_string));
        var config = new Configuration(mContext.getResources().getConfiguration());
        config.setLocale(Locale.of("zu"));
        var overrideContext = mContext.createConfigurationContext(config);
        assertEquals("Test-ZU", overrideContext.getString(R.string.test_string));
    }
}
