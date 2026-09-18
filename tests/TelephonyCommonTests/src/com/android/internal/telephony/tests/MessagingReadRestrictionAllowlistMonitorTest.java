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

package com.android.internal.telephony.tests;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.PackageUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BackgroundThread;
import com.android.internal.telephony.MessagingReadRestrictionAllowlistMonitor;
import com.android.internal.R;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(AndroidJUnit4.class)
public class MessagingReadRestrictionAllowlistMonitorTest {
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static final int DEFAULT_USER_ID = 0;
    private static final int SECOND_USER_ID = 10;
    private static final int HSUM_PRIMARY_UID = 10;
    private static final int HSUM_SECONDARY_UID = 11;
    private static final Random RANDOM = new Random();
    private static final String TEST_PACKAGE_ONE = "com.example.app.one";
    private static final String TEST_PACKAGE_TWO = "com.example.app.two";
    private static final String KEY_READ_RESTRICTION_ALLOWLIST
        = "messaging_read_restriction_allowlist";
    private static final String ALLOWLIST_FILE_NAME = "messaging_read_restriction_allowlist.txt";

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Executor mBackgroundExecutor = BackgroundThread.getExecutor();

    @Mock private PackageManager mPackageManager;
    @Mock private AppOpsManager mAppOpsManager;
    @Mock private UserManager mUserManager;
    @Mock private Resources mResources;

    private File mReadRestrictionAllowlistFile;
    private Context mSpyContext;
    private MessagingReadRestrictionAllowlistMonitor mAllowlistMonitor;
    private String[] mResourcePackageAllowlist = new String[0];

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSpyContext = spy(new ContextWrapper(mContext));
        when(mSpyContext.getResources()).thenReturn(mResources);
        when(mSpyContext.getSystemService(AppOpsManager.class)).thenReturn(mAppOpsManager);
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
        when(mSpyContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        doNothing().when(mSpyContext).enforceCallingOrSelfPermission(anyString(), anyString());
        UserInfo defaultUserInfo = new UserInfo();
        defaultUserInfo.id = DEFAULT_USER_ID;
        UserInfo secondUserInfo = new UserInfo();
        secondUserInfo.id = SECOND_USER_ID;
        when(mUserManager.getUsers()).thenReturn(Arrays.asList(defaultUserInfo, secondUserInfo));
        when(mResources.getStringArray(R.array.config_messaging_read_restriction_allowlist))
                .thenReturn(mResourcePackageAllowlist);
        // Use a separate folder for each test case, for better isolation.
        final String folderName = String.valueOf(RANDOM.nextInt() & Integer.MAX_VALUE);
        mReadRestrictionAllowlistFile = new File(mTemporaryFolder.newFolder(folderName),
                ALLOWLIST_FILE_NAME);
        mAllowlistMonitor = new MessagingReadRestrictionAllowlistMonitor(mSpyContext,
                mReadRestrictionAllowlistFile, mBackgroundExecutor);
    }

    @After
    public void tearDown() throws Exception {
        mReadRestrictionAllowlistFile.delete();
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, null);
    }

    @Test
    public void initialize_storageIsEmpty_deviceConfigHasAllowlist_setsAppOps()
            throws Exception {
        InstalledPackageInfo packageInfo =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, packageInfo.getConfigValue());

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, packageInfo.getConfigValue());
    }

    @Test
    public void initialize_deviceConfigHasAllowlist_appGetsInstalledLater_setsAppOps()
            throws Exception {
        InstalledPackageInfo packageInfo =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        packageInfo.uninstallPackage(mPackageManager);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, packageInfo.getConfigValue());

        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();
        verifyNoInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, packageInfo.getConfigValue());

        packageInfo.reinstallPackage(mPackageManager);
        mAllowlistMonitor.onPackageInstalled(TEST_PACKAGE_ONE, DEFAULT_USER_ID);

        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, packageInfo.getConfigValue());
    }

    @Test
    public void initialize_storageHasAllowlist_deviceConfigHasSameAllowlist_doesNotSetAppOpsAgain()
            throws Exception {
        InstalledPackageInfo packageInfo =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        Files.writeString(mReadRestrictionAllowlistFile.toPath(), packageInfo.getConfigValue());
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, packageInfo.getConfigValue());

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, packageInfo.getConfigValue());

        // OnPropertiesChanged is called with the same value as before.
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
            DeviceConfig.NAMESPACE_TELEPHONY)
            .setString(KEY_READ_RESTRICTION_ALLOWLIST, packageInfo.getConfigValue())
        .build());

        awaitForUpdateInBackground();
        verifyNoMoreInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, packageInfo.getConfigValue());
    }

    @Test
    public void initialize_staticAllowlistIsMergedWithDeviceConfigAllowlist()
            throws Exception {
        InstalledPackageInfo packageInfoOne =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        InstalledPackageInfo packageInfoTwo =
            installAllowlistedPackage(TEST_PACKAGE_TWO, /* isSystem= */ true,
                /* anyCertificate = */ true);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, packageInfoOne.getConfigValue());
        updateStaticAllowlist(new String[] {TEST_PACKAGE_TWO});

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_TWO), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, List.of(packageInfoOne.getConfigValue(),
                packageInfoTwo.getConfigValue()));
    }

    @Test
    public void initialize_multipleCertificatesForTheSamePackage_allCertificatesArePreserved()
            throws Exception {
        InstalledPackageInfo packageInfoOne =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        InstalledPackageInfo packageInfoTwo =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        InstalledPackageInfo packageInfoThree =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        List<String> deviceConfigValuesList = Arrays.asList(TEST_PACKAGE_ONE + ":*",
                    packageInfoOne.getConfigValue(),
                    packageInfoTwo.getConfigValue(),
                    packageInfoThree.getConfigValue());
        final String deviceConfigValues
            = deviceConfigValuesList
                .stream()
                .distinct()
                .collect(Collectors.joining(","));
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, deviceConfigValues);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        assertFileContents(mReadRestrictionAllowlistFile, deviceConfigValuesList);
    }

    @Test
    public void initialize_storageHasAllowlist_deviceConfigHasDifferentAllowlist_updatesAppOps()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final InstalledPackageInfo initialStoragePackage =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ false,
                /* anyCertificate = */ false);
        final InstalledPackageInfo deviceConfigPackage =
            installAllowlistedPackage(TEST_PACKAGE_TWO, /* isSystem= */ false,
                /* anyCertificate = */ false);
        // Write the one config to storage and another to DeviceConfig.
        Files.writeString(mReadRestrictionAllowlistFile.toPath(),
            initialStoragePackage.getConfigValue());
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST,
            deviceConfigPackage.getConfigValue());

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_TWO), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, deviceConfigPackage.getConfigValue());
    }

    @Test
    public void initialize_deviceConfigEmpty_readsAllowlistFromStorage_thenResetsAppOps()
            throws Exception {
        final InstalledPackageInfo packageInfo =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ true,
                /* anyCertificate = */ false);
        // Write the allowlist only to storage. DeviceConfig remains empty.
        Files.writeString(mReadRestrictionAllowlistFile.toPath(), packageInfo.getConfigValue());

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_DEFAULT));
        assertFileContents(mReadRestrictionAllowlistFile, "");
    }

    @Test
    public void initialize_readsAllowlistFromDeviceConfig_signatureMismatch_doesNotSetAppOps()
            throws Exception {
        final Signature expectedSignature = generateSignature((byte) 1);
        final String expectedCertificateDigest
                = PackageUtils.computeSha256Digest(expectedSignature.toByteArray());
        final String configValue = TEST_PACKAGE_ONE + ":" + expectedCertificateDigest;
        final Signature actualSignature = generateSignature((byte) 2);
        installInPackageManager(TEST_PACKAGE_ONE, actualSignature, true);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verifyNoInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void initialize_readsAllowlistFromDeviceConfig_packageNotInstalled_doesNotSetAppOps()
            throws Exception {
        final Signature expectedSignature = generateSignature((byte) 1);
        final String expectedCertificateDigest
                = PackageUtils.computeSha256Digest(expectedSignature.toByteArray());
        final String configValue = TEST_PACKAGE_ONE + ":" + expectedCertificateDigest;
        final Signature actualSignature = generateSignature((byte) 2);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verifyNoInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void deviceConfigUpdated_revokeAppOpForOldPackage_grantAppOpForNewPackage()
            throws Exception {
        final InstalledPackageInfo oldPackage =
            installAllowlistedPackage(TEST_PACKAGE_ONE, /* isSystem= */ true,
                /* anyCertificate = */ false);
        final InstalledPackageInfo newPackage =
            installAllowlistedPackage(TEST_PACKAGE_TWO, /* isSystem= */ true,
                /* anyCertificate = */ false);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, oldPackage.getConfigValue());
        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();

        // Update the allowlist in DeviceConfig.
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, newPackage.getConfigValue());
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
                    DeviceConfig.NAMESPACE_TELEPHONY)
                    .setString(KEY_READ_RESTRICTION_ALLOWLIST, newPackage.getConfigValue())
                .build());

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_TWO), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, newPackage.getConfigValue());
    }

    @Test
    public void deviceConfig_HSUMDevice_forStaticAllowlist_updatesAppOpsForTargetUserIds()
            throws Exception {
        // Setup HSUM device with three users (headless system user, 2 x user).
        UserInfo headlessSystemUser = new UserInfo();
        headlessSystemUser.id = DEFAULT_USER_ID;
        UserInfo primaryUser = new UserInfo();
        primaryUser.id = HSUM_PRIMARY_UID;
        UserInfo secondaryUser = new UserInfo();
        secondaryUser.id = HSUM_SECONDARY_UID;
        when(mUserManager.getUsers())
            .thenReturn(Arrays.asList(headlessSystemUser, primaryUser, secondaryUser));

        final List<InstalledPackageInfo> staticSystemListPackages = Arrays.asList(
            installAllowlistedPackageForUser("com.one", /* isSystem= */ true,
                /* anyCertificate = */ true, HSUM_PRIMARY_UID),
            installAllowlistedPackageForUser("com.two", /* isSystem= */ true,
                /* anyCertificate = */ true, HSUM_PRIMARY_UID),
            installAllowlistedPackageForUser("com.two", /* isSystem= */ true,
                /* anyCertificate = */ true, HSUM_SECONDARY_UID),
            installAllowlistedPackageForUser("com.three", /* isSystem= */ true,
                /* anyCertificate = */ true, HSUM_SECONDARY_UID),
            installAllowlistedPackageForUser("com.four", /* isSystem= */ true,
                /* anyCertificate = */ true, HSUM_SECONDARY_UID)
            );
        // Installable packages are not installed on the device at first.
        final List<InstalledPackageInfo> staticInstallablePackages = Arrays.asList(
            installAllowlistedPackageForUser("com.five", /* isSystem= */ false,
                /* anyCertificate = */ false, HSUM_PRIMARY_UID),
            installAllowlistedPackageForUser("com.six", /* isSystem= */ false,
                /* anyCertificate = */ false, HSUM_SECONDARY_UID)
            );
        String[] staticAllowlist = Stream.concat(
                    staticSystemListPackages.stream().map(InstalledPackageInfo::packageName),
                    staticInstallablePackages.stream().map(InstalledPackageInfo::getConfigValue))
                .toArray(String[]::new);

        staticInstallablePackages.forEach(pkgInfo -> {
            pkgInfo.uninstallPackage(mPackageManager);
        });

        updateStaticAllowlist(staticAllowlist);

        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();

        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_PRIMARY_UID, Process.myUid())), eq("com.one"),
                eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_PRIMARY_UID, Process.myUid())), eq("com.two"),
                eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_SECONDARY_UID, Process.myUid())), eq("com.two"),
                eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_SECONDARY_UID, Process.myUid())), eq("com.three"),
                eq(AppOpsManager.MODE_ALLOWED));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_SECONDARY_UID, Process.myUid())), eq("com.four"),
                eq(AppOpsManager.MODE_ALLOWED));
        verifyNoMoreInteractions(mAppOpsManager);

        InstalledPackageInfo packageToInstall = staticInstallablePackages.get(0);
        packageToInstall.reinstallPackage(mPackageManager);
        // Package com.five gets installed on the device.
        mAllowlistMonitor.onPackageInstalled("com.five", HSUM_PRIMARY_UID);

        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(UserHandle.getUid(HSUM_PRIMARY_UID, Process.myUid())), eq("com.five"),
                eq(AppOpsManager.MODE_ALLOWED));
        verifyNoMoreInteractions(mAppOpsManager);
    }

    @Test
    public void deviceConfigUpdated_multiplePackageUpdates_onlyAppOpsForChangedPackagesAreUpdated()
            throws Exception {
        final List<String> allInitialPackages = List.of("com.one", "com.two", "com.three",
                "com.four", "com.five");
        final List<InstalledPackageInfo> deviceConfigPackages = Arrays.asList(
            installAllowlistedPackage("com.one", /* isSystem= */ false,
                /* anyCertificate = */ false),
            installAllowlistedPackage("com.two", /* isSystem= */ false,
                /* anyCertificate = */ false),
            installAllowlistedPackage("com.three", /* isSystem= */ false,
                /* anyCertificate = */ false)
            );

        final List<InstalledPackageInfo> staticListPackages = Arrays.asList(
            installAllowlistedPackage("com.three", /* isSystem= */ true,
                /* anyCertificate = */ true),
            installAllowlistedPackage("com.four", /* isSystem= */ true,
                /* anyCertificate = */ true),
            installAllowlistedPackage("com.five", /* isSystem= */ true,
                /* anyCertificate = */ true)
            );

        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST,
                deviceConfigPackages.stream().map(InstalledPackageInfo::getConfigValue)
                    .collect(Collectors.joining(",")));
        updateStaticAllowlist(staticListPackages.stream().map(InstalledPackageInfo::packageName)
                    .toArray(String[]::new));
        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();

        // Verify that AppOps are set correctly for all initial packages.
        for (String pkg : allInitialPackages) {
            verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                    eq(Process.myUid()), eq(pkg), eq(AppOpsManager.MODE_ALLOWED));
        }

        // DeviceConfig is updated.
        InstalledPackageInfo updatedPackage = installAllowlistedPackage("com.six",
            /* isSystem= */ false, /* anyCertificate = */ false);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, updatedPackage.getConfigValue());
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
                    DeviceConfig.NAMESPACE_TELEPHONY)
                    .setString(KEY_READ_RESTRICTION_ALLOWLIST, updatedPackage.getConfigValue())
                .build());

        // Verify that AppOps are updated correctly.
        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(Process.myUid()), eq("com.one"), eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(Process.myUid()), eq("com.two"), eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(Process.myUid()), eq("com.six"), eq(AppOpsManager.MODE_ALLOWED));
        List<String> expectedFileContents = new ArrayList<>();
        expectedFileContents.addAll(
            staticListPackages.stream().map(InstalledPackageInfo::getConfigValue)
                .collect(Collectors.toList()));
        expectedFileContents.add(updatedPackage.getConfigValue());
        assertFileContents(mReadRestrictionAllowlistFile, expectedFileContents);
    }

  @Test
    public void deviceConfigUpdatedAcrossUsers_onlyAppOpsForChangedPackagesAreUpdatedInTargetUser()
            throws Exception {
        final List<String> allInitialPackagesInDefaultUser = List.of("com.one", "com.two",
            "com.three");
        final List<String> allInitialPackagesInSecondUser = List.of("com.three", "com.four",
            "com.five");
        final List<InstalledPackageInfo> deviceConfigPackages = Arrays.asList(
            installAllowlistedPackageForUser("com.one", /* isSystem= */ false,
                /* anyCertificate = */ false, DEFAULT_USER_ID),
            installAllowlistedPackageForUser("com.two", /* isSystem= */ false,
                /* anyCertificate = */ false, DEFAULT_USER_ID),
            installAllowlistedPackageForUser("com.three", /* isSystem= */ false,
                /* anyCertificate = */ false, DEFAULT_USER_ID)
            );

        final List<InstalledPackageInfo> staticListPackages = Arrays.asList(
            installAllowlistedPackageForUser("com.three", /* isSystem= */ true,
                /* anyCertificate = */ true, SECOND_USER_ID),
            installAllowlistedPackageForUser("com.four", /* isSystem= */ true,
                /* anyCertificate = */ true, SECOND_USER_ID),
            installAllowlistedPackageForUser("com.five", /* isSystem= */ true,
                /* anyCertificate = */ true, SECOND_USER_ID)
            );

        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST,
                deviceConfigPackages.stream().map(InstalledPackageInfo::getConfigValue)
                    .collect(Collectors.joining(",")));
        updateStaticAllowlist(staticListPackages.stream().map(InstalledPackageInfo::packageName)
                    .toArray(String[]::new));
        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();

        // Verify that AppOps are set correctly for all initial packages.
        for (String pkg : allInitialPackagesInDefaultUser) {
            verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                    eq(UserHandle.getUid(DEFAULT_USER_ID, Process.myUid())), eq(pkg),
                    eq(AppOpsManager.MODE_ALLOWED));
        }
        for (String pkg : allInitialPackagesInSecondUser) {
            verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                    eq(UserHandle.getUid(SECOND_USER_ID, Process.myUid())), eq(pkg),
                    eq(AppOpsManager.MODE_ALLOWED));
        }

        // DeviceConfig is updated.
        InstalledPackageInfo updatedPackage = installAllowlistedPackageForUser("com.six",
            /* isSystem= */ false, /* anyCertificate = */ false, SECOND_USER_ID);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, updatedPackage.getConfigValue());
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
                    DeviceConfig.NAMESPACE_TELEPHONY)
                    .setString(KEY_READ_RESTRICTION_ALLOWLIST, updatedPackage.getConfigValue())
                .build());

        // Verify that AppOps are updated correctly.
        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(UserHandle.getUid(DEFAULT_USER_ID, Process.myUid())), eq("com.one"),
            eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(UserHandle.getUid(DEFAULT_USER_ID, Process.myUid())), eq("com.two"),
            eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
        eq(UserHandle.getUid(SECOND_USER_ID, Process.myUid())), eq("com.six"),
            eq(AppOpsManager.MODE_ALLOWED));
        List<String> expectedFileContents = new ArrayList<>();
        expectedFileContents.addAll(
            staticListPackages.stream().map(InstalledPackageInfo::getConfigValue)
                .collect(Collectors.toList()));
        expectedFileContents.add(updatedPackage.getConfigValue());
        assertFileContents(mReadRestrictionAllowlistFile, expectedFileContents);
    }

    private void awaitForUpdateInBackground() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mBackgroundExecutor.execute(() -> {
            // Wait for the initialization to complete on the background thread.
            latch.countDown();
        });
        assertTrue("Background task took too long", latch.await(5, TimeUnit.SECONDS));
    }

    private void writeDeviceConfigAllowlist(@NonNull String key, @Nullable String value)
            throws Exception {
        assertTrue(DeviceConfig.setProperty(DeviceConfig.NAMESPACE_TELEPHONY, key, value,
                    /* makeDefault */ false));
    }

    private void updateStaticAllowlist(@NonNull String[] allowlist) {
        mResourcePackageAllowlist = allowlist;
        when(mResources.getStringArray(R.array.config_messaging_read_restriction_allowlist))
                .thenReturn(mResourcePackageAllowlist);
    }

    /**
     * A record that holds information about an installed package in the test environment.
     *
     * @param packageName The name of the installed and allowlisted package.
     * @param certificateDigest True SHA-256 digest of the installed package's certificate.
     * @param preinstalled Whether the package is preinstalled.
     * @param acceptedCertificate The certificate used for allowlisting.
     */
    record InstalledPackageInfo(String packageName,
            String certificateDigest,
            boolean preinstalled,
            String acceptedCertificate,
            int userId) {

        String getConfigValue() {
            return packageName + ":" + acceptedCertificate;
        }

        void uninstallPackage(PackageManager packageManager) {
            try {
                when(packageManager.getPackageUidAsUser(eq(packageName), eq(userId)))
                    .thenThrow(new NameNotFoundException());
                when(packageManager.getApplicationInfoAsUser(eq(packageName), any(), eq(userId)))
                    .thenThrow(new NameNotFoundException());
            } catch (NameNotFoundException e) {
                fail("Package not found: " + packageName);
            }
        }

        void reinstallPackage(PackageManager packageManager) {
            int uid = UserHandle.getUid(userId, Process.myUid());
            final ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.uid = uid;
            if (preinstalled) {
                applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
            }
            try {
                when(packageManager.getPackageUidAsUser(eq(packageName), eq(userId)))
                        .thenReturn(uid);
                when(packageManager.getApplicationInfoAsUser(eq(packageName), any(), eq(userId)))
                        .thenReturn(applicationInfo);
            } catch (NameNotFoundException e) {
                fail("Package not found: " + packageName);
            }
        }
    }

    private InstalledPackageInfo installAllowlistedPackage(@NonNull String packageName,
        boolean isSystem, boolean anyCertificate) throws Exception {
        return installAllowlistedPackageForUser(packageName, isSystem, anyCertificate,
            DEFAULT_USER_ID);
    }

    private InstalledPackageInfo installAllowlistedPackageForUser(@NonNull String packageName,
        boolean isSystem, boolean anyCertificate, int userId) throws Exception {
        final Signature signature = generateSignature((byte) (RANDOM.nextInt(127)));
        final String certificateDigest =
            installInPackageManagerForUser(packageName, signature, isSystem, userId);
        final String acceptedCertificate = anyCertificate ? "*" : certificateDigest;
        return new InstalledPackageInfo(packageName, certificateDigest, isSystem,
            acceptedCertificate, userId);
    }

    private String installInPackageManager(@NonNull String packageName,
        @NonNull Signature signature, boolean preinstalled) throws Exception {
        return installInPackageManagerForUser(packageName, signature, preinstalled,
            DEFAULT_USER_ID);
    }

    private String installInPackageManagerForUser(@NonNull String packageName,
        @NonNull Signature signature, boolean preinstalled, int userId) throws Exception {
        final String certificateDigest = PackageUtils.computeSha256Digest(signature.toByteArray());
        final PackageInfo packageInfo = generatePackageInfo(signature);
        when(mPackageManager.getPackageInfoAsUser(packageName,
            PackageManager.GET_SIGNING_CERTIFICATES, userId)).thenReturn(packageInfo);
        // Test uid = userId * 100000 + Process.myUid()
        int uid = UserHandle.getUid(userId, Process.myUid());
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = uid;
        if (preinstalled) {
            applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        when(mPackageManager.getPackageUidAsUser(eq(packageName), eq(userId)))
                .thenReturn(uid);
        when(mPackageManager.getApplicationInfoAsUser(eq(packageName), any(), eq(userId)))
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

    private void assertFileContents(@NonNull File file, @NonNull List<String> values)
            throws Exception {
        final String fileContents = Files.readString(file.toPath());
        String[] fileContentsArray = fileContents.split(",");
        Set<String> fileContentsList = new HashSet<>();
        for(String item : fileContentsArray) {
            fileContentsList.add(item);
        }
        assertEquals(new HashSet<>(values), fileContentsList);
    }

    private void assertFileContents(@NonNull File file, @NonNull String value) throws Exception {
        final String fileContents = Files.readString(file.toPath());
        assertEquals(value, fileContents);
    }
}