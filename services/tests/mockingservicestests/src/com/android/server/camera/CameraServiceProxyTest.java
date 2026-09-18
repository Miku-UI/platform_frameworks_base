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
package com.android.server.camera;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.camera.CameraServiceProxy.CAMERA_SERVICE_PROXY_BINDER_NAME;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.hardware.usb.UsbManager;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemService.TargetUser;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Run as {@code atest FrameworksMockingServicesTests:CameraServiceProxyTest}
 */
public final class CameraServiceProxyTest {

    private static final String TAG = CameraServiceProxyTest.class.getSimpleName();

    @Rule public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(LocalServices.class)
            .mockStatic(ServiceManager.class)
            .build();

    private final Context mRealContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();

    // Need to spy the real context otherwise we'd have to mock resources, which is a PITA...
    private final Context mSpiedContext = spy(mRealContext);

    private final CameraServiceProxy mProxy = new CameraServiceProxy(mSpiedContext);

    @Test
    public void testOnStart_publishesServices() {
        mProxy.onStart();

        verifyBinderServicePublished(CAMERA_SERVICE_PROXY_BINDER_NAME);
        verifyLocalServicePublished(CameraServiceProxy.class, mProxy);
    }

    @Test
    public void testOnStart_registersReceiver() {
        mProxy.onStart();

        var actions = verifyIntentFilterRegisteredAndGetActions(mSpiedContext);

        expect.withMessage("filter actions").that(actions).containsExactly(
                Intent.ACTION_USER_ADDED,
                Intent.ACTION_USER_REMOVED,
                Intent.ACTION_USER_INFO_CHANGED,
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED);
    }

    @Test
    public void testUpdateBroadcastReceiver_registersNewReceiver() {
        TargetUser from = null;
        TargetUser to = newTargetUser(42);
        Context contextTo = mockCreateContextAsUser(to);

        mProxy.updateBroadcastReceiver(from, to);

        var actions = verifyIntentFilterRegisteredAndGetActions(contextTo);
        expect.withMessage("filter actions").that(actions).containsExactly(
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_REMOVED);
    }

    @Test
    public void testUpdateBroadcastReceiver_unregistersPreviousReceiver() {
        TargetUser from = newTargetUser(108);
        TargetUser to = newTargetUser(42);
        Context contextFrom = mockCreateContextAsUser(from);
        Context contextTo = mockCreateContextAsUser(to);

        mProxy.updateBroadcastReceiver(from, to);

        var receiver = verifyIntentFilterRegisteredAndGetReceiver(contextTo);
        verifyContextUnregistered(contextFrom, receiver);
    }

    private Context mockCreateContextAsUser(UserHandle user) {
        Context mockContext = mock(Context.class);
        Log.d(TAG, "mockCreateContextAsUser(" + user + "): returning " + mockContext + ")");

        doReturn(mockContext).when(mSpiedContext).createContextAsUser(user, /* flags=*/ 0);

        return mockContext;
    }

    private Context mockCreateContextAsUser(TargetUser user) {
        return mockCreateContextAsUser(user.getUserHandle());
    }

    private Pair<BroadcastReceiver, IntentFilter> verifyIntentFilterRegistered(Context context) {
        Log.d(TAG, "verifyIntentFilterRegistered(" + context + ")");
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> filter =
                ArgumentCaptor.forClass(IntentFilter.class);

        verify(context).registerReceiver(receiver.capture(), filter.capture());

        return new Pair<>(receiver.getValue(), filter.getValue());
    }

    private ImmutableList<String> verifyIntentFilterRegisteredAndGetActions(Context context) {
        var filter = verifyIntentFilterRegistered(context).second;
        ImmutableList<String> actions = ImmutableList.copyOf(filter.actionsIterator());
        Log.v(TAG, "filter actions: " + actions);
        return actions;
    }

    private BroadcastReceiver verifyIntentFilterRegisteredAndGetReceiver(Context context) {
        return verifyIntentFilterRegistered(context).first;
    }

    private void verifyContextUnregistered(Context context, BroadcastReceiver receiver) {
        Log.d(TAG, "verifyContextUnregistered(" + context + ", " + receiver + ")");
        verify(context).unregisterReceiver(receiver);
    }

    private static void verifyBinderServicePublished(String name) {
        Log.d(TAG, "verifyBinderServicePublished(" + name + ")");
        verify(()-> ServiceManager.addService(eq(name), any(), anyBoolean(), anyInt()));
    }

    private static <T extends SystemService> void verifyLocalServicePublished(Class<T> serviceClass,
            T service) {
        Log.d(TAG, "verifyLocalServicePublished(" + serviceClass + ", " + service + ")");
        verify(()-> LocalServices.addService(serviceClass, service));
    }

    private static TargetUser newTargetUser(@UserIdInt int userId) {
        @SuppressWarnings("deprecation")
        var userInfo = new UserInfo();
        userInfo.id = userId;
        return new TargetUser(userInfo);
    }
}
