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

import static android.provider.Settings.Global.DEVICE_PROVISIONED;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public final class HsuDeviceProvisionerTest {

    private static final String TAG = HsuDeviceProvisionerTest.class.getSimpleName();

    @Rule public final Expect expect = Expect.create();
    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(UserManager.class)
                    .spyStatic(Settings.Global.class)
                    .spyStatic(Settings.Secure.class)
                    .build();
    @Mock private ContentResolver mMockContentResolver;
    @Captor private ArgumentCaptor<ContentObserver> mCaptorContentObserver;

    private HsuDeviceProvisioner mFixture;

    @Before
    public void setFixtures() {
        mFixture =
                new HsuDeviceProvisioner(
                    new Handler(Looper.getMainLooper()), mMockContentResolver);
    }

    @Test
    public void testInit_UserSetupComplete_provisioned() {

        mockIsDeviceProvisioned(true);
        mFixture.init();

        verifyUserSetupCompleteNeverCalled();
        verifyContentObserverNeverRegistered();
    }

    @Test
    public void testInit_UserSetupNotComplete_notProvisioned() {

        mockIsDeviceProvisioned(false);
        mFixture.init();

        verify(mMockContentResolver)
                .registerContentObserver(
                        eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)),
                        eq(false),
                        mCaptorContentObserver.capture());

        var contentObserver = mCaptorContentObserver.getValue();

        mockIsDeviceProvisioned(true);
        mFixture.onChange(true);

        verifyUserSetupCompleteCalled();
        verifyContentObserverUnregistered(contentObserver);
    }

    private void mockIsDeviceProvisioned(boolean value) {
        Log.v(TAG, "mockIsDeviceProvisioned(" + value + ")");
        doReturn(value ? 1 : 0).when(() -> Settings.Global.getInt(any(), eq(DEVICE_PROVISIONED)));
    }

    private void verifyUserSetupCompleteCalled() {
        try {
            verify(() -> Settings.Secure.putInt(mMockContentResolver, USER_SETUP_COMPLETE, 1));
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("USER_SETUP_COMPLETE was not set").fail();
        }
    }

    private void verifyUserSetupCompleteNeverCalled() {
        try {
            verify(() -> Settings.Secure.putInt(any(), eq(USER_SETUP_COMPLETE), anyInt()), never());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("USER_SETUP_COMPLETE should not have been set").fail();
        }
    }

    private void verifyContentObserverUnregistered(ContentObserver contentObserver) {
        try {
            verify(mMockContentResolver).unregisterContentObserver(contentObserver);
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("ContentResolver (%s) was not unregistered", contentObserver).fail();
        }
    }

    private void verifyContentObserverNeverRegistered() {
        try {
            verify(mMockContentResolver, never())
                    .registerContentObserver(any(), anyBoolean(), any());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("should not have registered a content observer").fail();
        }
    }
}
