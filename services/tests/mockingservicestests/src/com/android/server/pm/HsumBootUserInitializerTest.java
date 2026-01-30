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
import android.content.Context;
import android.database.ContentObserver;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.am.ActivityManagerService;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public final class HsumBootUserInitializerTest {

    private static final String TAG = HsumBootUserInitializerTest.class.getSimpleName();

    @Rule
    public final Expect expect = Expect.create();
    @Rule
    public final ExtendedMockitoRule extendedMockito = new ExtendedMockitoRule.Builder(this)
            .mockStatic(UserManager.class)
            .spyStatic(Settings.Global.class)
            .spyStatic(Settings.Secure.class)
            .build();
    @Mock
    private UserManagerService mMockUms;
    @Mock
    private ActivityManagerService mMockAms;
    @Mock
    private PackageManagerService mMockPms;
    @Mock
    private ContentResolver mMockContentResolver;
    @Captor
    private ArgumentCaptor<ContentObserver> mCaptorContentObserver;

    // NOTE: not mocking yet, but need a real one because of resources
    private final Context mRealContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private HsumBootUserInitializer mFixture;

    @Before
    public void setFixtures() {
        mFixture = new HsumBootUserInitializer(mMockUms, mMockAms, mMockPms, mMockContentResolver,
                // value of args below don't matter
                /* shouldDesignateMainUser= */ false, /* shouldCreateInitialUser= */ false);
    }

    @Test
    public void testCreateInstance_hsum() {
        mockIsHsum(true);

        var instance = HsumBootUserInitializer.createInstance(mMockUms, mMockAms, mMockPms,
                mMockContentResolver, mRealContext);

        expect.withMessage("result of createInstance()").that(instance).isNotNull();
    }
    @Test
    public void testCreateInstance_nonHsum() {
        mockIsHsum(false);

        var instance = HsumBootUserInitializer.createInstance(mMockUms, mMockAms, mMockPms,
                mMockContentResolver, mRealContext);

        expect.withMessage("result of createInstance()").that(instance).isNull();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_DEVICE_PROVISIONER)
    public void testObserveDeviceProvisioning_flagDisabled_provisioned() {
        mockIsDeviceProvisioned(true);

        mFixture.observeDeviceProvisioning();

        verifyUserSetupCompleteNeverCalled();
        verifyContentObserverNeverRegistered();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_DEVICE_PROVISIONER)
    public void testObserveDeviceProvisioning_flagDisabled_notProvisioned() {
        mockIsDeviceProvisioned(false);

        // First trigger setting an observer...
        mFixture.observeDeviceProvisioning();
        verify(mMockContentResolver).registerContentObserver(
                eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)), eq(false),
                mCaptorContentObserver.capture());
        var contentObserver = mCaptorContentObserver.getValue();

        // ...then trigger the observer itself
        mockIsDeviceProvisioned(true); // onChange() expected it has changed
        contentObserver.onChange(true);
        verifyUserSetupCompleteCalled();
        verifyContentObserverUnregistered(contentObserver);
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_DEVICE_PROVISIONER)
    public void testObserveDeviceProvisioning_flagEnabled_provisioned() {
        testObserveDeviceProvisioning_flagDisabled_provisioned();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_DEVICE_PROVISIONER)
    public void testObserveDeviceProvisioning_flagEnabled_notprovisioned() {
        testObserveDeviceProvisioning_flagDisabled_notProvisioned();
    }

    private void mockIsHsum(boolean value) {
        Log.v(TAG, "mockIsHsum(" + value + ")");
        doReturn(value).when(UserManager::isHeadlessSystemUserMode);
    }

    private void mockIsDeviceProvisioned(boolean value) {
        Log.v(TAG, "mockIsDeviceProvisioned(" + value + ")");
        doReturn(value ? 1 : 0).when(() -> Settings.Global.getInt(any(), eq(DEVICE_PROVISIONED)));
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
            verify(mMockContentResolver, never()).registerContentObserver(any(), anyBoolean(),
                    any());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("should not have registered a content observer").fail();
        }
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
}
