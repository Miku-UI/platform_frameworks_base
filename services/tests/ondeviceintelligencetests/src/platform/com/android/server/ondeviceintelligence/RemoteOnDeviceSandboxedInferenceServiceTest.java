/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class RemoteOnDeviceSandboxedInferenceServiceTest {

    @Mock Context mMockContext;
    @Mock Handler mMockHandler;

    private static final String TEST_SERVICE_NAME = "com.test.package/.TestService";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(TEST_SERVICE_NAME);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any(UserHandle.class)))
                .thenReturn(true);
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mMockContext);
    }

    @Test
    public void testRemoteInferenceServiceConstructor_defaultFlags() throws Exception {
        RemoteOnDeviceSandboxedInferenceService service =
                new RemoteOnDeviceSandboxedInferenceService(
                        mMockContext, TEST_COMPONENT_NAME, UserHandle.SYSTEM.getIdentifier(),
                        mMockHandler);

        Field bindingFlagsField = ServiceConnector.Impl.class.getDeclaredField("mBindingFlags");
        bindingFlagsField.setAccessible(true);
        int flags = (int) bindingFlagsField.get(service);

        assertTrue((flags & Context.BIND_FOREGROUND_SERVICE) != 0);
        assertTrue((flags & Context.BIND_INCLUDE_CAPABILITIES) != 0);
    }

    @Test
    public void testRemoteInferenceServiceConstructor_customFlags() throws Exception {
        int customFlags = Context.BIND_SCHEDULE_LIKE_TOP_APP;
        RemoteOnDeviceSandboxedInferenceService service =
                new RemoteOnDeviceSandboxedInferenceService(
                        mMockContext,
                        TEST_COMPONENT_NAME,
                        UserHandle.SYSTEM.getIdentifier(),
                        customFlags,
                        mMockHandler);

        Field bindingFlagsField = ServiceConnector.Impl.class.getDeclaredField("mBindingFlags");
        bindingFlagsField.setAccessible(true);
        int flags = (int) bindingFlagsField.get(service);

        assertEquals(customFlags, flags);
    }
}
