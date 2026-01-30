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

package android.content.pm.verify.developer;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.pm.SharedLibraryInfo;
import android.content.pm.SigningInfo;
import android.content.pm.VersionedPackage;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DeveloperVerificationSessionTest {
    private static final int TEST_ID = 100;
    private static final int TEST_INSTALL_SESSION_ID = 33;
    private static final String TEST_PACKAGE_NAME = "com.foo";
    private static final Uri TEST_PACKAGE_URI = Uri.parse("test://test");
    private static final SigningInfo TEST_SIGNING_INFO = new SigningInfo();
    private static final SharedLibraryInfo TEST_SHARED_LIBRARY_INFO1 =
            new SharedLibraryInfo("sharedLibPath1", TEST_PACKAGE_NAME,
                    Collections.singletonList("path1"), "sharedLib1", 101,
                    SharedLibraryInfo.TYPE_DYNAMIC, new VersionedPackage(TEST_PACKAGE_NAME, 1),
                    null, null, false);
    private static final SharedLibraryInfo TEST_SHARED_LIBRARY_INFO2 =
            new SharedLibraryInfo("sharedLibPath2", TEST_PACKAGE_NAME,
                    Collections.singletonList("path2"), "sharedLib2", 102,
                    SharedLibraryInfo.TYPE_DYNAMIC,
                    new VersionedPackage(TEST_PACKAGE_NAME, 2), null, null, false);
    private static final long TEST_TIMEOUT_TIME = System.currentTimeMillis();
    private static final long TEST_EXTEND_TIME = 2000L;
    private static final String TEST_KEY = "test key";
    private static final String TEST_VALUE = "test value";
    private static final int TEST_POLICY = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;

    private final ArrayList<SharedLibraryInfo> mTestDeclaredLibraries = new ArrayList<>();
    private final PersistableBundle mTestExtensionParams = new PersistableBundle();
    @Mock
    private IDeveloperVerificationSessionInterface mTestSessionInterface;
    private DeveloperVerificationSession mTestSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestDeclaredLibraries.add(TEST_SHARED_LIBRARY_INFO1);
        mTestDeclaredLibraries.add(TEST_SHARED_LIBRARY_INFO2);
        mTestExtensionParams.putString(TEST_KEY, TEST_VALUE);
        mTestSession = new DeveloperVerificationSession(TEST_ID, TEST_INSTALL_SESSION_ID,
                TEST_PACKAGE_NAME, TEST_PACKAGE_URI, TEST_SIGNING_INFO, mTestDeclaredLibraries,
                mTestExtensionParams, TEST_POLICY, mTestSessionInterface);
    }

    @Test
    public void testParcel() {
        Parcel parcel = Parcel.obtain();
        mTestSession.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeveloperVerificationSession sessionFromParcel =
                DeveloperVerificationSession.CREATOR.createFromParcel(parcel);
        assertThat(sessionFromParcel.getId()).isEqualTo(TEST_ID);
        assertThat(sessionFromParcel.getInstallSessionId()).isEqualTo(TEST_INSTALL_SESSION_ID);
        assertThat(sessionFromParcel.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(sessionFromParcel.getStagedPackageUri()).isEqualTo(TEST_PACKAGE_URI);
        assertThat(sessionFromParcel.getSigningInfo().getSigningDetails())
                .isEqualTo(TEST_SIGNING_INFO.getSigningDetails());
        List<SharedLibraryInfo> declaredLibrariesFromParcel =
                sessionFromParcel.getDeclaredLibraries();
        assertThat(declaredLibrariesFromParcel).hasSize(2);
        // SharedLibraryInfo doesn't have a "equals" method, so we have to check it indirectly
        assertThat(declaredLibrariesFromParcel.getFirst().toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO1.toString());
        assertThat(declaredLibrariesFromParcel.get(1).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO2.toString());
        // We can't directly test with PersistableBundle.equals() because the parceled bundle's
        // structure is different, but all the key/value pairs should be preserved as before.
        assertThat(sessionFromParcel.getExtensionParams().getString(TEST_KEY))
                .isEqualTo(mTestExtensionParams.getString(TEST_KEY));
        assertThat(sessionFromParcel.getPolicy()).isEqualTo(TEST_POLICY);
    }

    @Test
    public void testParcelWithNullExtensionParams() {
        DeveloperVerificationSession session = new DeveloperVerificationSession(TEST_ID,
                TEST_INSTALL_SESSION_ID,
                TEST_PACKAGE_NAME, TEST_PACKAGE_URI, TEST_SIGNING_INFO, mTestDeclaredLibraries,
                /* extensionParams= */ null, TEST_POLICY, mTestSessionInterface);
        Parcel parcel = Parcel.obtain();
        session.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeveloperVerificationSession sessionFromParcel =
                DeveloperVerificationSession.CREATOR.createFromParcel(parcel);
        assertThat(sessionFromParcel.getExtensionParams().isEmpty()).isTrue();
    }

    @Test
    public void testInterface() throws Exception {
        when(mTestSessionInterface.getTimeoutTimeMillis(anyInt()))
                .thenAnswer(i -> TEST_TIMEOUT_TIME);
        when(mTestSessionInterface.extendTimeoutMillis(anyInt(), anyLong())).thenAnswer(
                i -> i.getArguments()[1]);

        assertThat(mTestSession.getTimeoutTime().toEpochMilli()).isEqualTo(TEST_TIMEOUT_TIME);
        verify(mTestSessionInterface, times(1)).getTimeoutTimeMillis(eq(TEST_ID));
        assertThat(mTestSession.extendTimeout(Duration.ofMillis(TEST_EXTEND_TIME)).toMillis())
                .isEqualTo(TEST_EXTEND_TIME);
        verify(mTestSessionInterface, times(1)).extendTimeoutMillis(
                eq(TEST_ID), eq(TEST_EXTEND_TIME));

        PersistableBundle response = new PersistableBundle();
        response.putString("test key", "test value");
        final DeveloperVerificationStatus status =
                new DeveloperVerificationStatus.Builder().setVerified(true).build();
        mTestSession.reportVerificationComplete(status);
        verify(mTestSessionInterface, times(1)).reportVerificationComplete(
                eq(TEST_ID), eq(status), eq(null));
        mTestSession.reportVerificationComplete(status, response);
        verify(mTestSessionInterface, times(1))
                .reportVerificationComplete(
                        eq(TEST_ID), eq(status), eq(response));

        final int reason = DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;
        mTestSession.reportVerificationIncomplete(reason);
        verify(mTestSessionInterface, times(1)).reportVerificationIncomplete(
                eq(TEST_ID), eq(reason));

        final int bypassReason =
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_BYPASSED_REASON_ADB;
        mTestSession.reportVerificationBypassed(bypassReason);
        verify(mTestSessionInterface, times(1)).reportVerificationBypassed(
                eq(TEST_ID), eq(bypassReason));
    }

    @Test
    public void testPolicyNoOverride() {
        assertThat(mTestSession.getPolicy()).isEqualTo(TEST_POLICY);
        // This "set" is a no-op
        assertThat(mTestSession.setPolicy(TEST_POLICY)).isTrue();
        assertThat(mTestSession.getPolicy()).isEqualTo(TEST_POLICY);
        verifyNoMoreInteractions(mTestSessionInterface);
    }

    @Test
    public void testPolicyOverrideFail() throws Exception {
        final int newPolicy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN;
        when(mTestSessionInterface.setVerificationPolicy(anyInt(), anyInt())).thenReturn(false);
        assertThat(mTestSession.setPolicy(newPolicy)).isFalse();
        verify(mTestSessionInterface, times(1))
                .setVerificationPolicy(eq(TEST_ID), eq(newPolicy));
        // Next "get" should not trigger binder call because the previous "set" has failed
        assertThat(mTestSession.getPolicy()).isEqualTo(TEST_POLICY);
        verifyNoMoreInteractions(mTestSessionInterface);
    }

    @Test
    public void testPolicyOverrideSuccess() throws Exception {
        final int newPolicy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN;
        when(mTestSessionInterface.setVerificationPolicy(anyInt(), anyInt())).thenReturn(true);
        assertThat(mTestSession.setPolicy(newPolicy)).isTrue();
        verify(mTestSessionInterface, times(1))
                .setVerificationPolicy(eq(TEST_ID), eq(newPolicy));
        assertThat(mTestSession.getPolicy()).isEqualTo(newPolicy);
        assertThat(mTestSession.getPolicy()).isEqualTo(newPolicy);

        // Setting back to the original policy should still trigger binder calls
        assertThat(mTestSession.setPolicy(TEST_POLICY)).isTrue();
        verify(mTestSessionInterface, times(1))
                .setVerificationPolicy(eq(TEST_ID), eq(TEST_POLICY));
        assertThat(mTestSession.getPolicy()).isEqualTo(TEST_POLICY);
    }
}
