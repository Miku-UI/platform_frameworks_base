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

package com.android.server.companion.virtual.computercontrol;

import static android.Manifest.permission.ACCESS_COMPUTER_CONTROL;

import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_NAMESPACE;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtualdevice.flags.Flags;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.PackageUtils;
import android.util.SparseArray;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.permission.PermissionManagerServiceInterface;

import com.google.common.util.concurrent.MoreExecutors;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Presubmit
@RunWith(JUnitParamsRunner.class)
public class ComputerControlAllowlistControllerTest {

    private static final long TIMEOUT_MILLIS = 1000L;
    private static final Random RANDOM = new Random();
    private static final String AGENT_PACKAGE = "com.normal.agent";
    private static final String SUPER_AGENT_PACKAGE = "com.super.agent";
    private static final String PERMISSION_CONTROLLER_PACKAGE = "permission.controller.package";
    private static final UserHandle USER_HANDLE = new UserHandle(0);

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private PermissionManagerServiceInterface mPermissionManager;
    @Mock
    private ComputerControlSessionImpl mSession;
    @Mock
    private Resources mResources;
    @Mock
    private ComputerControlDataStore mDataStore;
    @Mock
    private RoleManager mRoleManager;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private AutoCloseable mMockitoSession;
    private ComputerControlAllowlistController mAllowlistController;
    private File mSessionOwnerAllowlistFile;
    private File mAutomatableAppAllowlistFile;
    private File mAutomatableAppDenylistFile;
    private Context mSpyContext;
    private final DeviceConfigWriter mDeviceConfigWriter = new DeviceConfigWriter();
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mSpyContext = spy(new ContextWrapper(mContext));
        doReturn(mResources).when(mSpyContext).getResources();
        doNothing().when(mSpyContext).enforceCallingOrSelfPermission(anyString(), anyString());
        doReturn(mRoleManager).when(mSpyContext).getSystemService(RoleManager.class);
        doReturn(mSpyContext).when(mSpyContext).createContextAsUser(any(), anyInt());
        doReturn(mPackageManager).when(mSpyContext).getPackageManager();

        final Signature signature = generateSignature((byte) 42);
        final String superAgentCertificateDigest = preparePackage(SUPER_AGENT_PACKAGE, signature);
        when(mResources.getStringArray(anyInt()))
                .thenReturn(new String[]{SUPER_AGENT_PACKAGE + ":" + superAgentCertificateDigest});

        when(mPackageManager.getPermissionControllerPackageName())
                .thenReturn(PERMISSION_CONTROLLER_PACKAGE);
        when(mPackageManager.getLaunchIntentForPackage(anyString())).thenReturn(new Intent());
        // Use a separate folder for each test case, for better isolation.
        final String folderName = String.valueOf(RANDOM.nextInt() & Integer.MAX_VALUE);
        mSessionOwnerAllowlistFile =
                new File(new File(mContext.getFilesDir(), folderName), "session_owners.txt");
        mAutomatableAppAllowlistFile =
                new File(new File(mContext.getFilesDir(), folderName), "automatable_apps.txt");
        mAutomatableAppDenylistFile =
                new File(new File(mContext.getFilesDir(), folderName), "blocked_apps.txt");
        final SparseArray<Map<String, Set<String>>> persistedData = new SparseArray<>();
        when(mDataStore.readAutomatableAppList()).thenReturn(persistedData);
        createAllowlistController(/* buildIsDebuggable */ true);

        when(mSession.isTestSession()).thenReturn(false);
        when(mSession.getPackageManager()).thenReturn(mPackageManager);
        when(mSession.getKeyguardManager()).thenReturn(mKeyguardManager);

        when(mPermissionManager.checkUidPermission(anyInt(), eq(ACCESS_COMPUTER_CONTROL), any()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mRoleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_ASSISTANT), any()))
                .thenReturn(List.of(AGENT_PACKAGE));
    }

    @After
    public void tearDown() throws Exception {
        DeviceConfig.removeOnPropertiesChangedListener(mAllowlistController);
        mDeviceConfigWriter.reset();
        mSessionOwnerAllowlistFile.delete();
        mAutomatableAppDenylistFile.delete();
        mAutomatableAppAllowlistFile.delete();
        mMockitoSession.close();
    }

    @Test
    public void isPackageAllowedToCreateSession_nullPackageName_returnsFalse() {
        assertFalse(mAllowlistController
                .isPackageAllowedToCreateSession(null, mPackageManager,
                        USER_HANDLE, VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAutomatable_nullPackageName_returnsFalse() {
        assertFalse(mAllowlistController.isPackageAutomatable("hello", null, mPackageManager));
        assertFalse(mAllowlistController.isPackageAutomatable(null, "hello", mPackageManager));
        assertFalse(mAllowlistController.isPackageAutomatable(null, null, mPackageManager));
    }

    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwner_sameUid_returnsTrue()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwners_sameUid_returnsTrue()
            throws Exception {
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(AGENT_PACKAGE, signature1);
        final String packageName2 = "com.hello.appp2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final List<ComputerControlAllowlistController.SignedPackage> sessionOwners = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        AGENT_PACKAGE, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));
        // Make PackageManager infer that any package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(any(), anyInt()))
                .thenReturn(Process.myUid());
        when(mRoleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_ASSISTANT), any()))
                .thenReturn(List.of(AGENT_PACKAGE, packageName2));

        mDeviceConfigWriter.allowlistSessionOwners(sessionOwners);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                packageName2, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwner_differentUid_returnsFalse()
            throws Exception {
        final Signature signature = generateSignature((byte) 2);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is not associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid() + 1);

        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_notAllowlistedSessionOwner_sameUid_returnsFalse()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    @EnableFlags({Flags.FLAG_COMPUTER_CONTROL_ROLE_ASSISTANT_REQUIREMENT,
            Flags.FLAG_COMPUTER_CONTROL_SUPPORT_V5})
    public void isPackageAllowedToCreateSession_isNotAssistant_returnsFalse()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());
        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);
        when(mRoleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_ASSISTANT), any()))
                .thenReturn(List.of("com.another.app"));

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ROLE_ASSISTANT_REQUIREMENT)
    public void isPackageAllowedToCreateSession_isNotAssistant_returnsTrue()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());
        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);
        when(mRoleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_ASSISTANT), any()))
                .thenReturn(List.of("com.another.app"));

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_superAgent_debuggableBuild_returnsTrue()
            throws Exception {
        doCallRealMethod().when(mSpyContext)
                .enforceCallingOrSelfPermission(anyString(), anyString());

        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(SUPER_AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                SUPER_AGENT_PACKAGE, mPackageManager,
                USER_HANDLE, VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_superAgent_nonDebuggableBuild_throws()
            throws Exception {
        doCallRealMethod().when(mSpyContext)
                .enforceCallingOrSelfPermission(anyString(), anyString());

        createAllowlistController(/* buildIsDebuggable */ false);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(SUPER_AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        assertThrows(SecurityException.class, () ->
                mAllowlistController.isPackageAllowedToCreateSession(
                        SUPER_AGENT_PACKAGE, mPackageManager,
                        USER_HANDLE, VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_noPermission_testOnly_returnsTrue()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        preparePackage(AGENT_PACKAGE, signature, /* preinstalled= */ false, /* testOnly= */ true);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());
        when(mPermissionManager.checkUidPermission(
                eq(Process.myUid()), eq(ACCESS_COMPUTER_CONTROL), any()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertTrue(
                mAllowlistController.isPackageAllowedToCreateSession(AGENT_PACKAGE, mPackageManager,
                        USER_HANDLE, VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAllowedToCreateSession_noPermission_nonTestOnly_returnsFalse()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());
        when(mPermissionManager.checkUidPermission(
                eq(Process.myUid()), eq(ACCESS_COMPUTER_CONTROL), any()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void isPackageAutomatable_testSession_nullPackage_returnsFalse() {
        when(mSession.isTestSession()).thenReturn(true);
        assertFalse(mAllowlistController.isPackageAutomatable(null, mSession));
    }

    @Test
    public void isPackageAutomatable_testSession_nonTestOnlyApp_returnsFalse() throws Exception {
        when(mSession.isTestSession()).thenReturn(true);
        final String packageName = "com.hello.test";
        preparePackage(packageName, generateSignature((byte) 2));

        assertFalse(mAllowlistController.isPackageAutomatable(packageName, mSession));
    }

    @Test
    public void isPackageAutomatable_testSession_secureKeyguard_returnsFalse() throws Exception {
        when(mSession.isTestSession()).thenReturn(true);
        final String packageName = "com.hello.test";
        preparePackage(packageName, generateSignature((byte) 2), /* preinstalled= */ false,
                /* testOnly= */ true);
        when(mKeyguardManager.isKeyguardSecure()).thenReturn(true);

        assertFalse(mAllowlistController.isPackageAutomatable(packageName, mSession));
    }

    @Test
    public void isPackageAutomatable_testSession_returnsTrue() throws Exception {
        when(mSession.isTestSession()).thenReturn(true);
        final String packageName = "com.hello.test";
        preparePackage(packageName, generateSignature((byte) 2), /* preinstalled= */ false,
                /* testOnly= */ true);
        when(mKeyguardManager.isKeyguardSecure()).thenReturn(false);

        assertTrue(mAllowlistController.isPackageAutomatable(packageName, mSession));
    }

    @Test
    public void isPackageAutomatable_targetIsApprovedAgent_returnsFalse() throws Exception {
        final String packageName = "com.hello.agent";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(
                mAllowlistController.isPackageAutomatable(
                        packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_allowlistedApp_returnsTrue() throws Exception {
        final String packageName = "com.hello.good";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_allowlistedApps_returnsTrue() throws Exception {
        final String packageName1 = "com.hello.appp1";
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.appp2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final List<ComputerControlAllowlistController.SignedPackage> apps = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));

        mDeviceConfigWriter.allowlistAutomatableApps(apps);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName1, "com.some.owner1", mPackageManager));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName2, "com.some.owner2", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_allowlistedApp_preinstalled_returnsTrue() throws Exception {
        final String packageName = "com.hello.appp1";
        final Signature signature = generateSignature((byte) 1);
        final String unused = preparePackage(packageName, signature, /* preinstalled= */ true,
                /* testOnly= */ false);
        final List<ComputerControlAllowlistController.SignedPackage> apps = List.of(
                new ComputerControlAllowlistController.SignedPackage(packageName, "PREINSTALLED"));

        mDeviceConfigWriter.allowlistAutomatableApps(apps);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner1", mPackageManager));
    }

    @Test
    @Parameters(method = "getAllowlistStringsIncludingEverything")
    public void isPackageAutomatable_allAppsAllowlisted_returnsTrue(
            String allowlistStringIncludingEverything) throws Exception {
        final String packageName1 = "com.hello.foo";
        final Signature signature1 = generateSignature((byte) 1);
        preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.bar";
        final Signature signature2 = generateSignature((byte) 5);
        preparePackage(packageName2, signature2);

        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY,
                allowlistStringIncludingEverything);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName1, "com.some.owner1", mPackageManager));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName2, "com.some.owner2", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_notAllowlistedApp_returnsFalse() throws Exception {
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);

        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_sessionOwnedBySuperAgent_debuggableBuild_returnsTrue()
            throws Exception {
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, SUPER_AGENT_PACKAGE, mPackageManager));
    }

    @Test
    public void isPackageAutomatable_sessionOwnedBySuperAgent_nonDebuggableBuild_returnsFalse()
            throws Exception {
        createAllowlistController(/* buildIsDebuggable */ false);
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);

        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, SUPER_AGENT_PACKAGE, mPackageManager));
    }

    @Test
    public void isPackageAutomatable_noLaunchIntent_returnsFalse() throws Exception {
        when(mPackageManager.getLaunchIntentForPackage(anyString())).thenReturn(null);
        final String packageName = "com.hello.good";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_permissionController_returnsFalse() throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(PERMISSION_CONTROLLER_PACKAGE, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(
                PERMISSION_CONTROLLER_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(
                PERMISSION_CONTROLLER_PACKAGE, "com.some.owner", mPackageManager));
    }

    @Test
    public void isPackageAutomatable_sessionOwnedBySuperAgent_noLaunchIntent_returnsTrue()
            throws Exception {
        when(mPackageManager.getLaunchIntentForPackage(anyString())).thenReturn(null);
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, SUPER_AGENT_PACKAGE, mPackageManager));
    }

    @Test
    public void isPackageAutomatable_sessionOwnedBySuperAgent_permissionController_returnsTrue()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        preparePackage(PERMISSION_CONTROLLER_PACKAGE, signature);

        assertTrue(mAllowlistController.isPackageAutomatable(
                PERMISSION_CONTROLLER_PACKAGE, SUPER_AGENT_PACKAGE, mPackageManager));
    }

    @Test
    public void isPackageAutomatable_multipleAllowlistedApps_multipleDenylistedApps()
            throws Exception {
        final String packageName1 = "com.hello.app1";
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.app2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final String packageName3 = "com.hello.app3";
        final Signature signature3 = generateSignature((byte) 3);
        final String certificateDigest3 = preparePackage(packageName3, signature3);
        final List<ComputerControlAllowlistController.SignedPackage> apps = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName3, certificateDigest3));

        mDeviceConfigWriter.allowlistAutomatableApps(apps);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName1, "com.some.owner1", mPackageManager));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName2, "com.some.owner2", mPackageManager));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName3, "com.some.owner3", mPackageManager));

        final List<ComputerControlAllowlistController.SignedPackage> blocked = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));
        mDeviceConfigWriter.denylistAutomatableApps(blocked);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName1, "com.some.owner1", mPackageManager));
        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName2, "com.some.owner2", mPackageManager));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName3, "com.some.owner3", mPackageManager));
    }

    @Test
    @Parameters(method = "getAllowlistStringsIncludingEverything")
    public void isPackageAutomatable_allAppsAllowlisted_denylistedApp_returnsFalse(
            String allowlistStringIncludingEverything) throws Exception {
        final String packageName = "com.hello.fun";
        final Signature signature = generateSignature((byte) 7);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist all packages via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY,
                allowlistStringIncludingEverything);
        // Denylist the given package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    @Parameters(method = "getMalformedValues")
    public void deviceConfigMalformedValue_sessionOwnerAllowlist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mSessionOwnerAllowlistFile.getAbsolutePath());
        final String expectedFileContent = AGENT_PACKAGE + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still allowlisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Test
    public void deviceConfigEmptyString_clearsSessionOwnerAllowlist() throws Exception {
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(AGENT_PACKAGE, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(AGENT_PACKAGE), anyInt()))
                .thenReturn(Process.myUid());

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistSessionOwner(AGENT_PACKAGE, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mSessionOwnerAllowlistFile.getAbsolutePath());
        final String expectedFileContent = AGENT_PACKAGE + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(
                mAllowlistController.isPackageAllowedToCreateSession(AGENT_PACKAGE, mPackageManager,
                        USER_HANDLE, VirtualDeviceManager.COMPUTER_CONTROL_VERSION));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the allowlist is cleared.
        assertEquals("", Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(
                AGENT_PACKAGE, mPackageManager, USER_HANDLE,
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION));
    }

    @Parameters(method = "getMalformedValues")
    @Test
    public void deviceConfigMalformedValue_automatableAppAllowlist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 5);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still allowlisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void deviceConfigEmptyString_clearsAutomatableAppAllowlist() throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the allowlist is cleared.
        assertEquals("", Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    @Parameters(method = "getMalformedValues")
    public void deviceConfigMalformedValue_automatableAppDenylist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 8);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist all packages via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "*");
        // Denylist the package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually denylisted and the denylist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppDenylistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still denylisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(
                packageName, "com.some.owner", mPackageManager));
    }

    @Test
    public void deviceConfigEmptyString_clearsAutomatableAppDenylist() throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);

        // Denylist the package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the denylist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppDenylistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the denylist is cleared.
        assertEquals("", Files.readString(filePath));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void isPackageAutomatableByAgent_perAppConsentDisabled_returnsTrue() {
        assertTrue(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                Process.myUid(), "com.agent", "com.target"));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void isPackageAutomatableByAgent_perAppConsentEnabled_returnsTrueIfAdded() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";

        assertFalse(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                agentUid, agentPkg, targetPkg));
        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);

        assertTrue(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void getAutomatableAppListForAgent_returnsCorrectList() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg1 = "com.target1";
        String targetPkg2 = "com.target2";

        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg1);
        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg2);

        String[] result = mAllowlistController.getAutomatableAppListForAgent(agentUid, agentPkg);
        assertEquals(2, result.length);
        List<String> resultList = List.of(result);
        assertTrue(resultList.contains(targetPkg1));
        assertTrue(resultList.contains(targetPkg2));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void removeAppFromAutomatableAppListForAgent_removesApp() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";

        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);
        mAllowlistController.removeAppFromAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);

        assertFalse(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(agentUid, agentPkg,
                targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void clearAutomatableAppListForAgent_clearsList() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";

        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);
        mAllowlistController.clearAutomatableAppListForAgent(agentUid, agentPkg);

        assertFalse(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void onPackageRemoved_perAppConsentEnabled_removesAgentFromMap() {
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mSpyContext).registerReceiver(receiverCaptor.capture(), any());
        BroadcastReceiver receiver = receiverCaptor.getValue();

        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";
        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);

        // Simulate agent package removal
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.parse("package:" + agentPkg));
        intent.putExtra(Intent.EXTRA_UID, agentUid);
        receiver.onReceive(mContext, intent);

        assertFalse(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void onPackageRemoved_perAppConsentEnabled_removesTargetFromMap() {
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mSpyContext).registerReceiver(receiverCaptor.capture(), any());
        BroadcastReceiver receiver = receiverCaptor.getValue();

        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";
        int targetUid = Process.myUid();

        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);

        // Simulate target package removal
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.parse("package:" + targetPkg));
        intent.putExtra(Intent.EXTRA_UID, targetUid);
        receiver.onReceive(mContext, intent);

        assertFalse(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void initialize_loadsPersistedAutomatableList() {
        int agentUid = 12345;
        String agentPkg = "com.agent";
        String targetPkg = "com.target";
        SparseArray<Map<String, Set<String>>> persistedData = new SparseArray<>();
        Map<String, Set<String>> agentMap = new ArrayMap<>();
        agentMap.put(agentPkg, Set.of(targetPkg));
        persistedData.put(agentUid, agentMap);

        when(mDataStore.readAutomatableAppList()).thenReturn(persistedData);

        // Re-initialize controller to trigger data loading
        mAllowlistController.initialize();
        // Wait for background thread
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(agentUid, agentPkg,
                targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void doesAgentHaveConsent_testAgentAndTestTarget_returnsTrueWithoutExplicitConsent()
            throws Exception {
        int agentUid = Process.myUid();
        String agentPkg = "com.test.agent";
        String targetPkg = "com.test.target";

        Signature agentSignature = generateSignature((byte) 10);
        preparePackage(agentPkg, agentSignature, /* preinstalled= */ false, /* testOnly= */ true);
        when(mPermissionManager.checkUidPermission(
                eq(agentUid), eq(ACCESS_COMPUTER_CONTROL), any()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        Signature targetSignature = generateSignature((byte) 11);
        preparePackage(targetPkg, targetSignature, /* preinstalled= */ false, /* testOnly= */ true);

        assertTrue("Test agent should be allowed to automate test target without explicit consent",
                mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                        agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void doesAgentHaveConsent_testAgentAndNormalTarget_returnsFalseWithoutExplicitConsent()
            throws Exception {
        int agentUid = Process.myUid();
        String agentPkg = "com.test.agent";
        String targetPkg = "com.normal.target";

        preparePackage(agentPkg, generateSignature((byte) 10), /* preinstalled= */
                false, /* testOnly= */ true);
        when(mPermissionManager.checkUidPermission(eq(agentUid), anyString(), any()))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        preparePackage(targetPkg, generateSignature((byte) 12), /* preinstalled= */
                false, /* testOnly= */ false);

        assertFalse("Test agent should NOT be allowed to automate normal target without consent",
                mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                        agentUid, agentPkg, targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void doesAgentHaveConsent_normalAgentAndTestTarget_returnsFalseWithoutExplicitConsent()
            throws Exception {
        int agentUid = Process.myUid();
        String agentPkg = "com.normal.agent";
        String targetPkg = "com.test.target";

        preparePackage(agentPkg, generateSignature((byte) 20), /* preinstalled= */ false,
                /* testOnly= */ false);
        when(mPermissionManager.checkUidPermission(
                eq(agentUid), eq(ACCESS_COMPUTER_CONTROL), any()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        preparePackage(targetPkg, generateSignature((byte) 21), /* preinstalled= */ false,
                /* testOnly= */ true);

        assertFalse("Normal agent should require explicit consent even for test targets",
                mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                        agentUid, agentPkg, targetPkg));
    }

    @Test
    public void isPackageApprovedToRunAutomation_notAllowlisted_returnsFalse() throws Exception {
        final String packageName = "com.not.allowlisted";
        preparePackage(packageName, generateSignature((byte) 1));

        assertFalse(mAllowlistController.isPackageApprovedToRunAutomation(packageName,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageApprovedToRunAutomation_allowlisted_returnsTrue() throws Exception {
        final String packageName = "com.allowlisted.agent";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageApprovedToRunAutomation(packageName,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageApprovedToRunAutomation_superAgent_debuggableBuild_returnsTrue() {
        assertTrue(mAllowlistController.isPackageApprovedToRunAutomation(SUPER_AGENT_PACKAGE,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageApprovedToRunAutomation_superAgent_nonDebuggableBuild_throws() {
        createAllowlistController(/* buildIsDebuggable */ false);
        assertFalse(mAllowlistController.isPackageApprovedToRunAutomation(SUPER_AGENT_PACKAGE,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageTargetableForAutomation_notAllowlisted_returnsFalse() throws Exception {
        final String packageName = "com.not.allowlisted.target";
        preparePackage(packageName, generateSignature((byte) 1));

        assertFalse(mAllowlistController.isPackageTargetableForAutomation(packageName,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageTargetableForAutomation_allowlisted_returnsTrue() throws Exception {
        final String packageName = "com.allowlisted.target";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageTargetableForAutomation(packageName,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    public void isPackageTargetableForAutomation_denylisted_returnsFalse() throws Exception {
        final String packageName = "com.denylisted.target";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageTargetableForAutomation(packageName,
                UserHandle.SYSTEM.getIdentifier()));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void addAppToAutomatableAppListForAgent_persistsData() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";

        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);
        // Wait for background thread
        SystemClock.sleep(TIMEOUT_MILLIS);

        ArgumentCaptor<SparseArray<Map<String, Set<String>>>> captor =
                ArgumentCaptor.forClass(SparseArray.class);
        verify(mDataStore).writeAutomatableAppList(captor.capture());
        SparseArray<Map<String, Set<String>>> capturedData = captor.getValue();
        assertEquals(1, capturedData.size());
        assertEquals(agentUid, capturedData.keyAt(0));
        Map<String, Set<String>> agentMap = capturedData.valueAt(0);
        assertTrue(agentMap.containsKey(agentPkg));
        assertTrue(agentMap.get(agentPkg).contains(targetPkg));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void removeAppFromAutomatableAppListForAgent_persistsData() {
        int agentUid = Process.myUid();
        String agentPkg = "com.agent";
        String targetPkg = "com.target";

        // Pre-populate data store
        SparseArray<Map<String, Set<String>>> data = new SparseArray<>();
        Map<String, Set<String>> agentMap = new ArrayMap<>();
        agentMap.put(agentPkg, new ArraySet<>(Set.of(targetPkg)));
        data.put(agentUid, agentMap);
        when(mDataStore.readAutomatableAppList()).thenReturn(data);
        DeviceConfig.removeOnPropertiesChangedListener(mAllowlistController);
        createAllowlistController(/* buildIsDebuggable= */ true);
        SystemClock.sleep(TIMEOUT_MILLIS);

        mAllowlistController.removeAppFromAutomatableAppListForAgent(agentUid, agentPkg, targetPkg);
        SystemClock.sleep(TIMEOUT_MILLIS);

        ArgumentCaptor<SparseArray<Map<String, Set<String>>>> captor =
                ArgumentCaptor.forClass(SparseArray.class);
        verify(mDataStore).writeAutomatableAppList(captor.capture());

        SparseArray<Map<String, Set<String>>> writtenData = captor.getValue();
        assertEquals(0, writtenData.size());
    }

    private void createAllowlistController(boolean buildIsDebuggable) {
        mAllowlistController = new ComputerControlAllowlistController(mSpyContext,
                MoreExecutors.directExecutor(), mSessionOwnerAllowlistFile,
                mAutomatableAppAllowlistFile, mAutomatableAppDenylistFile, mPermissionManager,
                mDataStore, buildIsDebuggable);
        mAllowlistController.initialize();
    }

    private String preparePackage(@NonNull String packageName, @NonNull Signature signature)
            throws Exception {
        return preparePackage(packageName, signature, /* preinstalled= */ false,
                /* testOnly= */ false);
    }

    private String preparePackage(@NonNull String packageName, @NonNull Signature signature,
            boolean preinstalled, boolean testOnly) throws Exception {
        final String certificateDigest = PackageUtils.computeSha256Digest(signature.toByteArray());
        final PackageInfo packageInfo = generatePackageInfo(signature);
        when(mPackageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(packageInfo);
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        if (preinstalled) {
            applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        if (testOnly) {
            applicationInfo.flags |= ApplicationInfo.FLAG_TEST_ONLY;
        }
        when(mPackageManager.getApplicationInfo(eq(packageName), any()))
                .thenReturn(applicationInfo);
        return certificateDigest;
    }

    private static PackageInfo generatePackageInfo(@NonNull Signature signature) {
        final SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_SIGNING_BLOCK_V4,
                List.of(signature), null, null);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.signingInfo = signingInfo;
        return packageInfo;
    }

    private static Signature generateSignature(byte i) {
        byte[] signatureBytes = new byte[256];
        signatureBytes[0] = i;
        return new Signature(signatureBytes);
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Object[][] getMalformedValues() {
        return new Object[][]{
                {null},
                {"garbage"},
                {"1234"},
                {"**"},
                {","},
                {",,,,,,"},
                {" , "},
                {"This is a sentence."},
                {"Hello,Goodbye"},
                {"com.android.app"},
                {"com.android.app:123456QWERTY,#"},
                {"@#$%^&*"}
        };
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Object[][] getAllowlistStringsIncludingEverything() {
        return new Object[][]{
                {"*"},
                {"*,"},
                {"*,*,"},
                {"com.android.app:123456QWERTY,*,"},
                {"*,com.android.app:123456QWERTY,"},
                {"*,com.android.app:123456QWERTY,*,"},
                {"com.android.app1:98765QWERTY,*,com.android.app2:123456QWERTY,"}
        };
    }

    private static final class DeviceConfigWriter {

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final ArrayMap<String, String> mOriginalValues = new ArrayMap<>();

        void allowlistSessionOwner(@NonNull String packageName, @NonNull String certificateDigest)
                throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, value);
        }

        void allowlistSessionOwners(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> sessionOwners)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage sessionOwner : sessionOwners) {
                value += sessionOwner.getPackageName() + ":" + sessionOwner.getCertificateDigest()
                        + ",";
            }
            writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, value);
        }

        void allowlistAutomatableApp(@NonNull String packageName,
                @NonNull String certificateDigest) throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, value);
        }

        void allowlistAutomatableApps(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> apps)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage app : apps) {
                value += app.getPackageName() + ":" + app.getCertificateDigest() + ",";
            }
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, value);
        }

        void denylistAutomatableApp(@NonNull String packageName,
                @NonNull String certificateDigest) throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, value);
        }

        void denylistAutomatableApps(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> apps)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage app : apps) {
                value += app.getPackageName() + ":" + app.getCertificateDigest() + ",";
            }
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, value);
        }

        void writeValue(@NonNull String key, @NonNull String value) throws Exception {
            synchronized (mLock) {
                if (!mOriginalValues.containsKey(key)) {
                    final String originalValue = DeviceConfig.getProperty(
                            COMPUTER_CONTROL_NAMESPACE, key);
                    if (originalValue != null) {
                        mOriginalValues.put(key, originalValue);
                    }
                }
            }
            assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE, key, value,
                    /* makeDefault */ false));
        }

        void reset() throws Exception {
            synchronized (mLock) {
                if (mOriginalValues.isEmpty()) {
                    // Write empty strings as ComputerControlAllowlistController ignores null
                    // values.
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                } else {
                    for (int i = 0; i < mOriginalValues.size(); ++i) {
                        assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                                mOriginalValues.keyAt(i), mOriginalValues.valueAt(i),
                                /* makeDefault */ false));
                    }
                    mOriginalValues.clear();
                }
                // Wait for changes to propagate.
                SystemClock.sleep(TIMEOUT_MILLIS);
            }
        }
    }
}
