/*
 * Copyright 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.os.ParcelableException;
import android.platform.test.annotations.Postsubmit;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.PackageUserStateImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Postsubmit
@RunWith(AndroidJUnit4.class)
public class PackageManagerServiceInternalTest {
    private static final String TEST_PACKAGE_NAME = "test.package";
    private static final int TEST_PROCESS_ID = 111;
    private static final int TEST_USER_ID = 234;

    @Rule public final MockSystemRule rule = new MockSystemRule();

    @Mock InstantAppRegistry mInstantAppRegistry;
    @Mock Computer mSnapshotComputer;
    @Mock PackageStateInternal mPackageStateInternal;
    @Mock PackageSetting mPackageSetting;

    private PackageManagerInternalBase mPackageManagerInternal;
    private AutoCloseable mCloseable;

    @Before
    public void setUp() throws Exception {
        mCloseable = MockitoAnnotations.openMocks(this);

        PackageManagerServiceInjector injector = rule.mocks().getInjector();
        rule.system().stageNominalSystemState();

        PackageUserStateImpl packageUserStateImpl = new PackageUserStateImpl();

        when(mSnapshotComputer.getPackageStateForInstalledAndFiltered(
                        eq(TEST_PACKAGE_NAME), anyInt(), anyInt()))
                .thenReturn(mPackageStateInternal);
        when(mPackageStateInternal.getUserStateOrDefault(eq(TEST_USER_ID)))
                .thenReturn(packageUserStateImpl);

        when(rule.mocks().getSettings().getPackageLPr(TEST_PACKAGE_NAME))
                .thenReturn(mPackageSetting);
        when(mPackageSetting.getOrCreateUserState(TEST_USER_ID)).thenReturn(packageUserStateImpl);

        PackageManagerServiceTestParams mTestParams = new PackageManagerServiceTestParams();
        mTestParams.packages = new ArrayMap<>();
        mTestParams.instantAppRegistry = mInstantAppRegistry;
        PackageManagerService packageManagerService =
                spy(new PackageManagerService(injector, mTestParams));
        doReturn(mSnapshotComputer).when(packageManagerService).snapshotComputer();

        mPackageManagerInternal = packageManagerService.new PackageManagerInternalImpl();
    }

    @After
    public void tearDown() throws Exception {
        mCloseable.close();
    }

    @Test
    public void testGetPersonalContextMode_defaultUnset() {
        assertThat(
                        mPackageManagerInternal.getPersonalContextMode(
                                TEST_PACKAGE_NAME, TEST_PROCESS_ID, TEST_USER_ID))
                .isEqualTo(PackageManager.PERSONAL_CONTEXT_MODE_UNSET);
    }

    @Test
    public void testGetPersonalContextMode_invalidPackageName_throws() {

        ParcelableException parcelableException =
                assertThrows(
                        ParcelableException.class,
                        () ->
                                mPackageManagerInternal.getPersonalContextMode(
                                        "invalid.package", TEST_PROCESS_ID, TEST_USER_ID));
        assertThat(parcelableException.getCause())
                .isInstanceOf(PackageManager.NameNotFoundException.class);
    }

    @Test
    public void testSetPersonalContextMode_succeeds() {
        int expectedValue = PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF;
        boolean setResult =
                mPackageManagerInternal.setPersonalContextMode(
                        TEST_PACKAGE_NAME, TEST_PROCESS_ID, TEST_USER_ID, expectedValue);

        // Newly set value is returned in the get call.
        assertThat(
                        mPackageManagerInternal.getPersonalContextMode(
                                TEST_PACKAGE_NAME, TEST_PROCESS_ID, TEST_USER_ID))
                .isEqualTo(expectedValue);
        assertThat(setResult).isTrue();
    }

    @Test
    public void testSetPersonalContextMode_valueUnchanged_succeeds() {
        int expectedValue = PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF;
        mPackageManagerInternal.setPersonalContextMode(
                TEST_PACKAGE_NAME, TEST_PROCESS_ID, TEST_USER_ID, expectedValue);

        // Second set does not throw, but returns false to indicate the value did not actually
        // change.
        boolean secondSetResult =
                mPackageManagerInternal.setPersonalContextMode(
                        TEST_PACKAGE_NAME, TEST_PROCESS_ID, TEST_USER_ID, expectedValue);
        assertThat(secondSetResult).isFalse();
    }

    @Test
    public void testSetPersonalContextMode_invalidPackageName_throws() {
        ParcelableException parcelableException =
                assertThrows(
                        ParcelableException.class,
                        () ->
                                mPackageManagerInternal.setPersonalContextMode(
                                        "invalid.package",
                                        TEST_PROCESS_ID,
                                        TEST_USER_ID,
                                        PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF));
        assertThat(parcelableException.getCause())
                .isInstanceOf(PackageManager.NameNotFoundException.class);
    }
}
