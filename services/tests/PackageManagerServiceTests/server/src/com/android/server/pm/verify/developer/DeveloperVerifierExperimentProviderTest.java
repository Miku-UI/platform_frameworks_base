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

package com.android.server.pm.verify.developer;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.pm.PackageInstallerSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DeveloperVerifierExperimentProviderTest {
    @Spy
    Handler mHandler = new Handler(Looper.getMainLooper());
    @Mock
    PackageInstallerSession.DeveloperVerifierCallback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void addExperiment_hasExperiment_success() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package";

        provider.addExperiment(
                packageName, DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                List.of(DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS));

        assertThat(provider.hasExperiments(packageName)).isTrue();
    }

    @Test
    public void addExperiment_invalidStatus_fails() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package";

        provider.addExperiment(
                packageName, DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                List.of(DeveloperVerificationStatusInternal.STATUS_UNKNOWN,
                        DeveloperVerificationStatusInternal.STATUS_INFEASIBLE + 1));

        assertThat(provider.hasExperiments(packageName)).isFalse();
    }

    @Test
    public void addExperiment_timeout_removesExperiment() {
        // Mimic a timeout by executing the runnable immediately
        doAnswer((Answer<Boolean>) invocation -> {
            ((Message) invocation.getArguments()[0]).getCallback().run();
            return true;
        }).when(mHandler).sendMessageAtTime(any(Message.class), anyLong());
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package";

        provider.addExperiment(
                packageName, DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                List.of(DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS));

        // The experiment should have been removed because of the handler task
        assertThat(provider.hasExperiments(packageName)).isFalse();
    }

    @Test
    public void hasExperiment_noExperiment_returnsFalse() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package";

        assertThat(provider.hasExperiments(packageName)).isFalse();
    }

    @Test
    public void addExperiment_sequentialDifferentPolicies_overridesCorrectly() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package.policy.sequential";
        int policyA = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
        int policyB = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
        List<Integer> statusesA = List.of(
                DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS);
        List<Integer> statusesB = List.of(
                DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_REJECT);

        provider.addExperiment(packageName, policyA, statusesA);
        provider.addExperiment(packageName, policyB, statusesB);

        assertThat(provider.hasExperiments(packageName)).isTrue();

        reset(mCallback);
        assertThat(provider.runNextExperiment(packageName, mCallback)).isTrue();

        // The second addExperiment should remove the first experiment because policy is different
        verify(mCallback).onVerificationPolicyOverridden(eq(policyB));
        ArgumentCaptor<DeveloperVerificationStatus> statusCaptor =
                ArgumentCaptor.forClass(DeveloperVerificationStatus.class);
        verify(mCallback).onVerificationCompleteReceived(statusCaptor.capture(), eq(null));
        assertThat(statusCaptor.getValue().isVerified()).isFalse();
        verifyNoMoreInteractions(mCallback);
        assertThat(provider.hasExperiments(packageName)).isFalse();
    }

    @Test
    public void runNextExperiment_pass_success() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName = "test.package.pass";
        int verificationPolicy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
        List<Integer> statuses = List.of(
                DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS);
        provider.addExperiment(packageName, verificationPolicy, statuses);

        boolean experimentRun = provider.runNextExperiment(packageName, mCallback);

        assertThat(experimentRun).isTrue();
        verify(mCallback, times(1)).onVerificationPolicyOverridden(verificationPolicy);
        ArgumentCaptor<DeveloperVerificationStatus> statusCaptor =
                ArgumentCaptor.forClass(DeveloperVerificationStatus.class);
        verify(mCallback, times(1)).onVerificationCompleteReceived(
                statusCaptor.capture(), eq(null));
        verifyNoMoreInteractions(mCallback);

        DeveloperVerificationStatus capturedStatus = statusCaptor.getValue();
        assertThat(capturedStatus.isVerified()).isTrue();
        // After a successful run, the experiment for this package should be removed.
        assertThat(provider.hasExperiments(packageName)).isFalse();
    }

    @Test
    public void runNextExperiment_multipleExperiments_consumesCorrectly() {
        DeveloperVerifierExperimentProvider provider =
                new DeveloperVerifierExperimentProvider(mHandler);
        String packageName1 = "test.package.one";
        String packageName2 = "test.package.two";
        int verificationPolicy1 = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
        int verificationPolicy2 = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;

        // Add one experiment for packageName1
        provider.addExperiment(packageName1, verificationPolicy1, List.of(
                DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS));
        // Add another experiment with a same policy should override the previous experiment
        provider.addExperiment(packageName1, verificationPolicy1, List.of(
                DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_REJECT));
        // Add two experiments for packageName2 at once
        provider.addExperiment(packageName2, verificationPolicy2, List.of(
                DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_UNKNOWN,
                DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_NETWORK_UNAVAILABLE));

        // Run one experiment for packageName1
        assertThat(provider.runNextExperiment(packageName1, mCallback)).isTrue();
        // Verify interactions for the first run on packageName1
        verify(mCallback).onVerificationPolicyOverridden(eq(verificationPolicy1));
        verify(mCallback, times(1)).onVerificationCompleteReceived(
                any(DeveloperVerificationStatus.class), eq(null));

        // Check hasExperiments for packageName1 - should not have any experiment left
        assertThat(provider.hasExperiments(packageName1)).isFalse();

        // Run two experiment for packageName2
        reset(mCallback);
        assertThat(provider.runNextExperiment(packageName2, mCallback)).isTrue();
        assertThat(provider.hasExperiments(packageName2)).isTrue();
        assertThat(provider.runNextExperiment(packageName2, mCallback)).isTrue();
        // Verify interactions for the run on packageName2
        verify(mCallback, times(2)).onVerificationPolicyOverridden(
                eq(verificationPolicy2));
        verify(mCallback, times(1)).onVerificationIncompleteReceived(
                eq(DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN));
        verify(mCallback, times(1)).onVerificationIncompleteReceived(
                eq(DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE));

        // Check hasExperiments for packageName2 - should have no experiments left
        assertThat(provider.hasExperiments(packageName2)).isFalse();
        verifyNoMoreInteractions(mCallback);
    }
}
