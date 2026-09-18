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

package com.android.server.privatecompute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.privatecompute.IPccService;
import android.app.privatecompute.IResultCallback;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.Manifest;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelableException;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService;
import com.android.server.pm.pkg.AndroidPackage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Tests for {@link PccSandboxManagerServiceInternal}.
 */
@RunWith(AndroidJUnit4.class)
public class PccSandboxManagerInternalTest {

    private static final int USER_ID_1 = 1;
    private static final int USER_ID_2 = 2;
    private static final int NON_PCC_CLIENT_UID = 10001;
    private static final int PCC_CLIENT_UID = 30001;
    private static final int TRUSTED_PACKAGE_UID = 10002;
    private static final String TRUSTED_PACKAGE = "com.trusted.package";
    private static final String CORRECT_CALLING_PACKAGE = "com.example.client";
    private static final String WRONG_CALLING_PACKAGE = "com.wrong.package";
    // Example UIDs for different process types
    private static final int PCC_UID_1 = Process.FIRST_PCC_UID;
    private static final int PCC_UID_2 = Process.LAST_PCC_UID;
    private static final int PCS_UID = 10199;
    private static final int PCS_UID_2 = 10201;
    private static final int REGULAR_UID = 10200;

    private static final String PCC_PACKAGE_1 = "com.pcc.package1";
    private static final String PCC_PACKAGE_2 = "com.pcc.package2";
    private static final String PCS_PACKAGE = "com.pcs.package";
    private static final String REGULAR_PACKAGE = "com.regular.package";

    @Mock
    private PccSandboxManagerServiceImpl mMockPccSandboxManagerService;

    private PccSandboxManagerInternal mPccSandboxManagerInternal;
    private IBinder mRealBinder;
    private ComponentName mServiceName;
    private Intent mIntent;

    @Mock
    private PackageManagerInternal mPackageManagerInternal;
    @Mock
    private android.app.ActivityManagerInternal mMockActivityManagerInternal;
    @Mock
    private RoleManager mMockRoleManager;
    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = spy(InstrumentationRegistry.getInstrumentation().getContext());
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        LocalServices.removeServiceForTest(android.app.ActivityManagerInternal.class);
        LocalServices.addService(android.app.ActivityManagerInternal.class,
                mMockActivityManagerInternal);

        int testUid = android.os.Process.myUid();
        when(mPackageManagerInternal.isSameApp(CORRECT_CALLING_PACKAGE, testUid,
                UserHandle.getUserId(testUid))).thenReturn(true);
        when(mPackageManagerInternal.isSameApp(WRONG_CALLING_PACKAGE, testUid,
                UserHandle.getUserId(testUid))).thenReturn(false);

        when(mMockPccSandboxManagerService.getExecutorService())
                .thenReturn(Executors.newSingleThreadExecutor());
        when(context.getSystemService(RoleManager.class)).thenReturn(mMockRoleManager);
        when(context.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        // Mock UserManager to return some users
        when(mMockUserManager.getUserHandles(anyBoolean())).thenReturn(
                Arrays.asList(UserHandle.of(USER_ID_1), UserHandle.of(USER_ID_2)));

        PccSandboxManagerInternal realInstance = new PccSandboxManagerInternal(
                context, mMockPccSandboxManagerService);
        mPccSandboxManagerInternal = spy(realInstance);
        mRealBinder = new IPccService.Stub() {
            @Override
            public void sendData(Bundle data, String packageName, IResultCallback callback) {
                try {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } catch (RemoteException e) {
                    // Ignore
                }
            }
        };
        mServiceName = new ComponentName("com.example.test", "com.example.test.MyPccService");
        mIntent = new Intent().setComponent(mServiceName);
        mPccSandboxManagerInternal.awaitPccInitialization();
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.removeServiceForTest(android.app.ActivityManagerInternal.class);
    }

    @Test
    public void createPccProxyIfNeeded_asInstrumentedClient_trustDisabled_returnsProxyBinder() {
        int instrumentedUid = 12345;
        when(mMockActivityManagerInternal.getInstrumentationSourceUid(instrumentedUid))
                .thenReturn(67890);
        mPccSandboxManagerInternal.setTrustInstrumentedClients(false);

        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, instrumentedUid,
                false /* hasSysConfigExemption */);

        assertNotEquals("Should return a proxy binder when trust is disabled", mRealBinder,
                returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asInstrumentedClient_trustEnabled_returnsDirectBinder() {
        int instrumentedUid = 12345;
        when(mMockActivityManagerInternal.getInstrumentationSourceUid(instrumentedUid))
                .thenReturn(67890);
        mPccSandboxManagerInternal.setTrustInstrumentedClients(true);

        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, instrumentedUid,
                false /* hasSysConfigExemption */);

        assertEquals("Should return a direct binder when trust is enabled", mRealBinder,
                returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asNonInstrumentedClient_trustEnabled_returnsProxyBinder() {
        int nonInstrumentedUid = 12345;
        when(mMockActivityManagerInternal.getInstrumentationSourceUid(nonInstrumentedUid))
                .thenReturn(Process.INVALID_UID);
        mPccSandboxManagerInternal.setTrustInstrumentedClients(true);

        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, nonInstrumentedUid,
                false /* hasSysConfigExemption */);

        assertNotEquals("Should return a proxy binder for non-instrumented client even if trust is "
                        + "enabled", mRealBinder, returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asRegularClient_returnsProxyBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);

        assertNotEquals("Should return a proxy binder", mRealBinder, returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asTrustedPackage_returnsDirectBinder() {
        mPccSandboxManagerInternal.mPccTrustedPackages.add(TRUSTED_PACKAGE);
        AndroidPackage mockAndroidPackage = mock(AndroidPackage.class);
        when(mockAndroidPackage.getPackageName()).thenReturn(TRUSTED_PACKAGE);
        when(mPackageManagerInternal.getPackage(TRUSTED_PACKAGE_UID)).thenReturn(
                mockAndroidPackage);
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);

        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, TRUSTED_PACKAGE_UID,
                false /* hasSysConfigExemption */);

        assertEquals("Should return a direct binder for trusted package", mRealBinder,
                returnedBinder);
        assertEquals("Connection should be tracked for trusted package", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void createPccProxyIfNeeded_asPccClient_returnsDirectBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);

        assertEquals("Should return a direct binder", mRealBinder, returnedBinder);
        assertEquals("Connection should be tracked for pcc client", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void createPccProxyIfNeeded_asSystemService_returnsDirectBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, Process.SYSTEM_UID,
                false /* hasSysConfigExemption */);

        assertEquals("Should return a direct binder", mRealBinder, returnedBinder);
        assertEquals("Connection should be tracked for system service", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void createPccProxyIfNeeded_asTrustedPackage_noProxyInitialized() {
        mPccSandboxManagerInternal.mPccTrustedPackages.add(TRUSTED_PACKAGE);
        AndroidPackage mockAndroidPackage = mock(AndroidPackage.class);
        when(mockAndroidPackage.getPackageName()).thenReturn(TRUSTED_PACKAGE);
        when(mPackageManagerInternal.getPackage(TRUSTED_PACKAGE_UID)).thenReturn(
                mockAndroidPackage);
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);

        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, TRUSTED_PACKAGE_UID,
                false /* hasSysConfigExemption */);

        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNull("Proxy should NOT be initialized for trusted clients",
                serviceInfo.getPccServiceProxy());
    }

    @Test
    public void createPccProxyIfNeeded_asPccClient_noProxyInitialized() {
        IBinder unusedReturnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, 0, mIntent, mRealBinder, PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);

        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNull("Proxy should NOT be initialized for PCC clients",
                serviceInfo.getPccServiceProxy());
    }

    @Test
    public void createPccProxyIfNeeded_asSystemService_noProxyInitialized() {
        IBinder unusedReturnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, 0, mIntent, mRealBinder, Process.SYSTEM_UID,
                false /* hasSysConfigExemption */);

        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNull("Proxy should NOT be initialized for system server as client",
                serviceInfo.getPccServiceProxy());
    }

    @Test
    public void fetchPccProxyIfNeeded_afterTrustedClientBinds_returnsProxy() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        IBinder proxy = mPccSandboxManagerInternal.fetchPccProxyIfNeeded(mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        assertNotNull("Proxy should be found even if only trusted clients are bound", proxy);
        assertNotEquals("Fetched binder should be a proxy, not the real binder", mRealBinder,
                proxy);
    }

    @Test
    public void fetchPccProxyIfNeeded_triggersLazyInitialization() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                PCC_CLIENT_UID, false /* hasSysConfigExemption */);
        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNull(serviceInfo.getPccServiceProxy());

        IBinder proxy = mPccSandboxManagerInternal.fetchPccProxyIfNeeded(mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        assertNotNull("Proxy should be created on first untrusted access", proxy);
        assertNotNull("Internal proxy field should now be populated",
                serviceInfo.getPccServiceProxy());
    }

    @Test
    public void multipleClientsBind_singleServiceInstance() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        assertEquals("Should only have one service connection info", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertEquals("Should have two clients for the service", 2,
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getConnectionCount());
    }

    @Test
    public void allClientsUnbind_serviceDestroyed() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);
        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNotNull(serviceInfo);
        PccSandboxManagerInternal.PccServiceProxy proxy = serviceInfo.getWrappedBinder();

        mPccSandboxManagerInternal.removePccProxyIfNeeded(
                mServiceName, 0, mIntent, mRealBinder, NON_PCC_CLIENT_UID);

        assertNull("realBinder should be null after destroy()", proxy.getRealBinder());
        assertEquals(0, mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void removePccProxy_oneOfMultipleClients() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        // Unbind one client
        mPccSandboxManagerInternal.removePccProxyIfNeeded(
                mServiceName, 0, mIntent, mRealBinder, NON_PCC_CLIENT_UID);

        assertEquals("Should still have one service connection info", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertEquals("Should have one client remaining", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getConnectionCount());
    }

    @Test
    public void singleClientUnbinds_thenRebinds_newProxyCreated() {
        IBinder proxyBinder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, USER_ID_1, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID);
        assertNotNull(proxyBinder1);

        IBinder proxyBinder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);

        assertNotNull(proxyBinder2);
        assertNotEquals(proxyBinder1, proxyBinder2);
    }

    @Test
    public void multipleClients_oneUnbindsAndRebinds_sameProxyReturned() {
        IBinder proxyBinder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_2, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        assertNotNull(proxyBinder1);

        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, USER_ID_1, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID);
        IBinder proxyBinder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        assertNotNull(proxyBinder2);

        assertEquals(proxyBinder1, proxyBinder2);
    }

    @Test
    public void removePccProxyIfNeeded_asTrustedClient_removesConnection() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                PCC_CLIENT_UID, false /* hasSysConfigExemption */);
        assertEquals(1, mPccSandboxManagerInternal.mPccServiceConnections.size());

        mPccSandboxManagerInternal.removePccProxyIfNeeded(
                mServiceName, 0, mIntent, mRealBinder, PCC_CLIENT_UID);

        assertEquals("Connection should be removed for trusted client", 0,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void bindWithInvalidServiceBinder_returnsNullBinder() {
        IBinder nullBinder = null;
        IBinder invalidBinder = createInvalidBinder();

        IBinder binder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, nullBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        IBinder binder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, invalidBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);

        assertNull(binder1);
        assertNull(binder2);
    }

    @Test
    public void binderDied_removesConnectionAndDestroysProxy() throws Exception {
        IBinder binder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                false /* hasSysConfigExemption */);
        assertEquals("Should have one active connection", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());

        IBinder.DeathRecipient deathRecipient =
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getDeathRecipient();
        deathRecipient.binderDied();

        assertEquals("Connection should be removed after binder death", 0,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertNull("Proxy should be destroyed after binder death",
                ((PccSandboxManagerInternal.PccServiceProxy) binder).getRealBinder());
    }

    @Test
    public void sendData_packageNameVerificationSuccess() throws Exception {
        PccSandboxManagerInternal.PccServiceProxy proxy =
                mPccSandboxManagerInternal.new PccServiceProxy(mRealBinder);

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Binder.withCleanCallingIdentity(() -> {
            try {
                proxy.sendData(new Bundle(), CORRECT_CALLING_PACKAGE, new IResultCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        result.complete(true);
                    }

                    @Override
                    public void onFailure(ParcelableException e) {
                        result.complete(false);
                    }
                });
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        assertTrue(result.get());
    }

    @Test
    public void sendData_packageNameVerificationFailure() throws Exception {
        PccSandboxManagerInternal.PccServiceProxy proxy =
                mPccSandboxManagerInternal.new PccServiceProxy(mRealBinder);

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Binder.withCleanCallingIdentity(() -> {
            try {
                proxy.sendData(new Bundle(), WRONG_CALLING_PACKAGE, new IResultCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        result.complete(true);
                    }

                    @Override
                    public void onFailure(ParcelableException e) {
                        result.complete(false);
                    }
                });
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        assertFalse(result.get());
    }

    private IBinder createInvalidBinder() {
        return new IBinder() {
            @Override
            public String getInterfaceDescriptor() {
                return "";
            }

            @Override
            public boolean pingBinder() {
                return false;
            }

            @Override
            public boolean isBinderAlive() {
                return false;
            }

            @Nullable
            @Override
            public IInterface queryLocalInterface(@NonNull String descriptor) {
                return null;
            }

            @Override
            public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) {

            }

            @Override
            public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) {

            }

            @Override
            public void shellCommand(@Nullable FileDescriptor in, @Nullable FileDescriptor out,
                    @Nullable FileDescriptor err, @NonNull String[] args,
                    @Nullable ShellCallback shellCallback, @NonNull ResultReceiver resultReceiver) {

            }


            @Override
            public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                    int flags) {
                return false;
            }

            @Override
            public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {

            }

            @Override
            public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
                return false;
            }
        };
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_pccToPcc_isAllowed() {
        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                PCC_UID_1, PCC_PACKAGE_1, PCC_UID_2, PCC_PACKAGE_2,
                ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());

        assertTrue("Association between two PCC UIDs should be allowed", allowed);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_pccAndPcs_isAllowed()
            throws android.os.RemoteException {
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(PCS_UID)).thenReturn(true);
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                PCC_UID_1, PCC_PACKAGE_1, PCS_UID, PCS_PACKAGE,
                ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());
        assertTrue("Association between a PCC UID and a PCS UID should be allowed", allowed);
        allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
            PCS_UID, PCS_PACKAGE, PCC_UID_1, PCC_PACKAGE_1,
            ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());
        assertTrue("Association between a PCS UID and PCC UID should be allowed", allowed);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_pccToRegular_isDenied()
            throws android.os.RemoteException {
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(REGULAR_UID))
                .thenReturn(false);

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                PCC_UID_1, PCC_PACKAGE_1, REGULAR_UID, REGULAR_PACKAGE,
                ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());

        assertFalse("Association between a PCC UID and a regular UID should be denied", allowed);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_regularToPcc_isAllowed()
            throws android.os.RemoteException {
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(REGULAR_UID))
                .thenReturn(false);

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                REGULAR_UID, REGULAR_PACKAGE, PCC_UID_1, PCC_PACKAGE_1,
                ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());

        assertTrue("Association between a regular UID and a PCC UID should be allowed", allowed);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_pccToTrustedUid_isAllowed() {
        boolean allowed;
        for (int trustedUid : PccSandboxManagerInternal.TRUSTED_UIDS) {
            allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                    PCC_UID_1, PCC_PACKAGE_1, trustedUid, REGULAR_PACKAGE,
                    ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());
            assertTrue("Association between a PCC UID and a trusted UID should be"
                    + " allowed", allowed);

            int trustedUidUser2 = UserHandle.getUid(USER_ID_2, trustedUid);
            allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                    PCC_UID_1, PCC_PACKAGE_1, trustedUidUser2, REGULAR_PACKAGE,
                    ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());
            assertTrue("Association between a PCC UID and a trusted UID (user 2) should be"
                    + " allowed", allowed);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_pccToTrustedPackage_isAllowed() {
        mPccSandboxManagerInternal.populatePccTrustedPackages();
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);
        for (String trustedPackage : mPccSandboxManagerInternal.mPccTrustedPackages) {
            boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                    PCC_UID_1, PCC_PACKAGE_1, REGULAR_UID, trustedPackage,
                    ActivityManagerService.ASSOCIATION_TYPE_SERVICE, new Bundle());
            assertTrue("Association between a PCC UID and a trusted package should be allowed",
                    allowed);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_regularToPcc_Provider_isDenied()
            throws android.os.RemoteException {
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(REGULAR_UID))
                .thenReturn(false);
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                REGULAR_UID, REGULAR_PACKAGE, PCC_UID_1, PCC_PACKAGE_1,
                ActivityManagerService.ASSOCIATION_TYPE_PROVIDER, new Bundle());

        assertFalse("Provider association between a regular UID and a PCC UID should be denied",
                allowed);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_trustedToPcc_Provider_isAllowed() {
        mPccSandboxManagerInternal.populatePccTrustedPackages();
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);
        for (String trustedPackage : mPccSandboxManagerInternal.mPccTrustedPackages) {
            boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                    TRUSTED_PACKAGE_UID, trustedPackage, PCC_UID_1, PCC_PACKAGE_1,
                    ActivityManagerService.ASSOCIATION_TYPE_PROVIDER, new Bundle());
            assertTrue("Provider association between a trusted package and a PCC UID "
                            + "should be allowed",
                    allowed);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_receiver_bundleWithBinder_isDenied() {
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);
        Bundle extras = new Bundle();
        extras.putBinder("binder", new Binder());

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                REGULAR_UID, REGULAR_PACKAGE, PCC_UID_1, PCC_PACKAGE_1,
                ActivityManagerService.ASSOCIATION_TYPE_RECEIVER, extras);

        assertFalse("Association with a Bundle containing a Binder should be denied", allowed);
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(REGULAR_UID);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_service_bundleWithBinder_isDenied() {
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);
        Bundle extras = new Bundle();
        extras.putBinder("binder", new Binder());

        boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                REGULAR_UID, REGULAR_PACKAGE, PCC_UID_1, PCC_PACKAGE_1,
                ActivityManagerService.ASSOCIATION_TYPE_SERVICE, extras);

        assertFalse("Service association with a Bundle containing a Binder should be denied",
                allowed);
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(REGULAR_UID);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testValidateAssociationAllowed_service_pccToTrustedPkg_binderBundle_isAllowed() {
        // Reset the mock to clear any previous stubs
        reset(mPackageManagerInternal);
        // Mock isSameApp for trusted packages used in tests
        when(mPackageManagerInternal.isSameApp(any(), anyInt(), anyInt())).thenReturn(true);

        mPccSandboxManagerInternal.populatePccTrustedPackages();
        Bundle bundle = new Bundle();
        bundle.putBinder("binder", new Binder());

        for (String trustedPackage : mPccSandboxManagerInternal.mPccTrustedPackages) {
            boolean allowed = mPccSandboxManagerInternal.validateAssociationAllowed(
                    PCC_UID_1, PCC_PACKAGE_1, REGULAR_UID, trustedPackage,
                    ActivityManagerService.ASSOCIATION_TYPE_SERVICE, bundle);
            assertTrue("Association between a PCC UID and a trusted package with "
                    + "binders should be allowed", allowed);
            verify(mMockPccSandboxManagerService, times(0)).isPrivateComputeServicesUid(PCC_UID_1);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPopulatePccAllowedPackages_populatesCorrectly() {
        // Setup mocks
        String rolePackage = "com.example.role";
        String testRole = "android.app.role.ASSISTANT";
        when(mMockRoleManager.getRoleHoldersAsUser(eq(testRole), any(UserHandle.class)))
                .thenReturn(Collections.singletonList(rolePackage));

        when(mPccSandboxManagerInternal.checkPermission(
                eq(android.Manifest.permission.MANAGE_HOTWORD_DETECTION),
                eq(rolePackage),
                anyInt())).thenReturn(android.content.pm.PackageManager.PERMISSION_GRANTED);

        // Call populate
        mPccSandboxManagerInternal.populatePccAllowedPackages();

        // Verify
        assertTrue(mPccSandboxManagerInternal.isPccAllowedPackage(rolePackage, USER_ID_1));
        assertTrue(mPccSandboxManagerInternal.isPccAllowedPackage(rolePackage, USER_ID_2));
        assertFalse(mPccSandboxManagerInternal.isPccAllowedPackage("com.other.package", USER_ID_1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testOnRoleHoldersChanged_updatesAllowedPackages() {
        // Setup initial state
        String rolePackage = "com.example.role";
        String testRole = "android.app.role.ASSISTANT";
        when(mMockRoleManager.getRoleHoldersAsUser(eq(testRole), eq(UserHandle.of(USER_ID_1))))
                .thenReturn(Collections.singletonList(rolePackage));

        when(mPccSandboxManagerInternal.checkPermission(
                eq(android.Manifest.permission.MANAGE_HOTWORD_DETECTION),
                anyString(),
                anyInt())).thenReturn(android.content.pm.PackageManager.PERMISSION_GRANTED);

        // Initial populate
        mPccSandboxManagerInternal.updateAllowedPackagesForUser(USER_ID_1);
        assertTrue(mPccSandboxManagerInternal.isPccAllowedPackage(rolePackage, USER_ID_1));

        // Change role holder
        String newRolePackage = "com.example.newrole";
        // Resetting mock to ensure no interference
        reset(mMockRoleManager);
        when(mMockRoleManager.getRoleHoldersAsUser(eq(testRole), eq(UserHandle.of(USER_ID_1))))
                .thenReturn(Collections.singletonList(newRolePackage));

        // Trigger change
        mPccSandboxManagerInternal.onRoleHoldersChanged(testRole, UserHandle.of(USER_ID_1));

        // Verify update
        assertTrue(mPccSandboxManagerInternal.isPccAllowedPackage(newRolePackage, USER_ID_1));
        assertFalse(mPccSandboxManagerInternal.isPccAllowedPackage(rolePackage, USER_ID_1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsPccAllowedPackage_respectsTestList() {
        String testPackage = "com.test.package";
        mPccSandboxManagerInternal.addTestAllowedPackage(testPackage);
        assertTrue(mPccSandboxManagerInternal.isPccAllowedPackage(testPackage, USER_ID_1));

        mPccSandboxManagerInternal.removeTestAllowedPackage(testPackage);
        assertFalse(mPccSandboxManagerInternal.isPccAllowedPackage(testPackage, USER_ID_1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPopulatePcsUids_populatesCacheCorrectly() {
        PackageInfo mockPackageInfo1 = new PackageInfo();
        mockPackageInfo1.applicationInfo = new ApplicationInfo();
        mockPackageInfo1.applicationInfo.uid = PCS_UID;
        PackageInfo mockPackageInfo2 = new PackageInfo();
        mockPackageInfo2.applicationInfo = new ApplicationInfo();
        mockPackageInfo2.applicationInfo.uid = PCS_UID_2;
        Context mockContext = mock(Context.class);
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getPackagesHoldingPermissions(
                eq(new String[]{Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES}),
                anyInt()))
                .thenReturn(Arrays.asList(mockPackageInfo1, mockPackageInfo2));
        PccSandboxManagerInternal pccSandboxManagerInternal = new PccSandboxManagerInternal(
                mockContext, mMockPccSandboxManagerService);

        pccSandboxManagerInternal.populatePcsUids();

        assertTrue(pccSandboxManagerInternal.isPrivateComputeServicesUid(PCS_UID));
        assertTrue(pccSandboxManagerInternal.isPrivateComputeServicesUid(PCS_UID_2));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsPrivateComputeServicesUid_cachesPcsUidResult() {
        int testUid = PCS_UID;
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(testUid)).thenReturn(true);

        // First call should trigger a call to the service
        assertTrue(mPccSandboxManagerInternal.isPrivateComputeServicesUid(testUid));
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(testUid);

        // Second call should use the cache and not call the service again
        assertTrue(mPccSandboxManagerInternal.isPrivateComputeServicesUid(testUid));
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(testUid);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsPrivateComputeServicesUid_cachesNonPcsUidResult() {
        int testUid = REGULAR_UID;
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(testUid)).thenReturn(false);

        // First call should trigger a call to the service
        assertFalse(mPccSandboxManagerInternal.isPrivateComputeServicesUid(testUid));
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(testUid);

        // Second call should use the cache and not call the service again
        assertFalse(mPccSandboxManagerInternal.isPrivateComputeServicesUid(testUid));
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(testUid);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsPrivateComputeServicesUid_doesNotCacheShellUidResult() {
        int shellUid = Process.SHELL_UID;
        when(mMockPccSandboxManagerService.isPrivateComputeServicesUid(shellUid))
                .thenReturn(true)
                .thenReturn(false);

        // First call should trigger a call to the service
        assertTrue(mPccSandboxManagerInternal.isPrivateComputeServicesUid(shellUid));
        verify(mMockPccSandboxManagerService, times(1)).isPrivateComputeServicesUid(shellUid);

        // Second call should NOT use the cache and call the service again
        assertFalse(mPccSandboxManagerInternal.isPrivateComputeServicesUid(shellUid));
        verify(mMockPccSandboxManagerService, times(2)).isPrivateComputeServicesUid(shellUid);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPopulatePccAllowedPackages_AssistantRole_WithPermission_IsAllowed() {
        String assistantPackage = "com.example.assistant";

        when(mMockRoleManager.getRoleHoldersAsUser(
                eq(android.app.role.RoleManager.ROLE_ASSISTANT), any(UserHandle.class)))
                .thenReturn(Collections.singletonList(assistantPackage));

        when(mPccSandboxManagerInternal.checkPermission(
                eq(android.Manifest.permission.MANAGE_HOTWORD_DETECTION),
                eq(assistantPackage),
                eq(USER_ID_1))).thenReturn(android.content.pm.PackageManager.PERMISSION_GRANTED);

        mPccSandboxManagerInternal.populatePccAllowedPackages();

        assertTrue("Assistant package with permission should be allowed",
                mPccSandboxManagerInternal.isPccAllowedPackage(assistantPackage, USER_ID_1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPopulatePccAllowedPackages_AssistantRole_NoPermission_IsDenied() {
        String assistantPackage = "com.example.assistant.noperm";

        when(mMockRoleManager.getRoleHoldersAsUser(
                eq(android.app.role.RoleManager.ROLE_ASSISTANT), any(UserHandle.class)))
                .thenReturn(Collections.singletonList(assistantPackage));


        when(mPccSandboxManagerInternal.checkPermission(
                eq(android.Manifest.permission.MANAGE_HOTWORD_DETECTION),
                eq(assistantPackage),
                eq(USER_ID_1))).thenReturn(android.content.pm.PackageManager.PERMISSION_DENIED);

        mPccSandboxManagerInternal.populatePccAllowedPackages();

        assertFalse("Assistant package without permission should be denied",
                mPccSandboxManagerInternal.isPccAllowedPackage(assistantPackage, USER_ID_1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void createPccProxyIfNeeded_sysConfigExemption_returnsDirectBinderAndTracksConnection() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                true /* hasSysConfigExemption */);

        assertEquals("Should return direct binder for exempted client", mRealBinder,
                returnedBinder);

        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNotNull("Connection should be tracked", serviceInfo);
        assertNull("Proxy should not be eagerly initialized for exempted connections",
                serviceInfo.getPccServiceProxy());
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void fetchPccProxyIfNeeded_withSysConfigExemption_returnsDirectBinder() {
        // Arrange: Bind the exempted client
        mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                true /* hasSysConfigExemption */);

        // Act: Simulate a peekService call
        IBinder fetchedBinder = mPccSandboxManagerInternal.fetchPccProxyIfNeeded(
                mRealBinder, NON_PCC_CLIENT_UID, true /* hasSysConfigExemption */);

        // Assert: Return the raw binder
        assertEquals("fetchPccProxyIfNeeded should return the raw binder",
                mRealBinder, fetchedBinder);
    }


    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void removePccProxyIfNeeded_withSysConfigExemption_removesSafelyWithoutNPE() {
        // Arrange: Create an exempted connection (which means mProxy is null inside PccServiceInfo)
        mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID,
                true /* hasSysConfigExemption */);

        // Act: Simulate the client unbinding
        mPccSandboxManagerInternal.removePccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID);

        // Assert: Safely removed, no NPE on destroy()
        assertEquals("Connection should be removed from the map", 0,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void createPccProxyIfNeeded_sysConfigExemption_skipsValidationForInvalidBinder() {
        IBinder nonPccBinder = new Binder(); // Not an IPccService

        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, nonPccBinder, NON_PCC_CLIENT_UID,
                true /* hasSysConfigExemption */);

        // If validation was NOT bypassed, this would return null
        assertEquals("Should bypass validation and return the raw invalid binder",
                nonPccBinder, returnedBinder);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void fetchPccProxyIfNeeded_invalidBinderCachedByExemptedClient_returnsNull() {
        // Arrange: An exempted client binds to an INVALID service. Validation is skipped.
        IBinder invalidBinder = createInvalidBinder();
        mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, invalidBinder, NON_PCC_CLIENT_UID,
                true /* hasSysConfigExemption */);

        // Act: A non-exempted client peeks at that same invalid service.
        IBinder fetchedBinder = mPccSandboxManagerInternal.fetchPccProxyIfNeeded(
                invalidBinder, NON_PCC_CLIENT_UID, false /* hasSysConfigExemption */);

        // Assert: It must return null rather than attempting to wrap an invalid binder.
        assertNull("Should validate and reject the invalid cached binder", fetchedBinder);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void removePccProxyIfNeeded_clientUidPreventsTeardownCollisions() {
        IBinder invalidBinder = createInvalidBinder(); // Not an IPccService
        int exemptedUid = NON_PCC_CLIENT_UID;
        int nonExemptedUid = NON_PCC_CLIENT_UID + 1; // Different client

        // 1. Exempted client binds (bypasses validation, connection is tracked)
        mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, invalidBinder, exemptedUid,
                true /* hasSysConfigExemption */);
        assertEquals("Exempted connection should be tracked",
                1, mPccSandboxManagerInternal.mPccServiceConnections.size());

        // 2. Non-exempted client attempts to bind (fails validation, tracking size stays 1)
        mPccSandboxManagerInternal.createPccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, invalidBinder, nonExemptedUid,
                false /* hasSysConfigExemption */);
        assertEquals("Failed non-exempted connection should not increase tracking size",
                1, mPccSandboxManagerInternal.mPccServiceConnections.size());

        // 3. Non-exempted client unbinds (triggering removePccProxyIfNeeded)
        mPccSandboxManagerInternal.removePccProxyIfNeeded(
                mServiceName, USER_ID_1, mIntent, invalidBinder, nonExemptedUid);

        // 4. Assert: Because of the clientUid check, the exempted client's connection MUST survive
        assertEquals(
                "Exempted client's connection should not be removed by another client's unbind",
                1, mPccSandboxManagerInternal.mPccServiceConnections.size());
    }
}

