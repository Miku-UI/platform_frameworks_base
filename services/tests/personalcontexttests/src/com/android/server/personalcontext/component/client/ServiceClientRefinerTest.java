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

package com.android.server.personalcontext.component.client;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.refiner.IGetFilterCallback;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.testing.TestableContext;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceClientRefinerTest {
    private static final String TEST_PACKAGE_NAME = "com.test.package";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final TestableContext mContext = new TestableContext(getInstrumentation().getContext());

    @Mock private UserHandle mUserHandle;
    @Mock private PermissionManager mPermissionManager;
    @Mock private Handler mHandler;
    @Mock private AccessController mAccessController;

    @Mock private OperatingModeProvider mOperatingModeProvider;

    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    private ServiceClientRefiner mServiceClientRefiner;
    private TestIRefiner mIRefiner;
    private FakePermissionEnforcer mFakePermissionEnforcer;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mOperatingModeProvider.filterAccessFlags(anyInt())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        mFakePermissionEnforcer = new FakePermissionEnforcer();
        mIRefiner = new TestIRefiner();

        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        mContext.addMockSystemService(Context.PERMISSION_ENFORCER_SERVICE, mFakePermissionEnforcer);
        mContext.addMockSystemService(PermissionManager.class, mPermissionManager);

        // Add mock service for bindServiceAsUser call.
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceInfo.name = "baz";
        mContext.addMockService(
                new ComponentName(serviceInfo.packageName, serviceInfo.name), mIRefiner.asBinder());

        mServiceClientRefiner =
                new ServiceClientRefiner(
                        mContext,
                        mAccessController,
                        UUID.randomUUID(),
                        serviceInfo,
                        mUserHandle,
                        mFakeExecutor,
                        mHandler,
                        mOperatingModeProvider);
        mFakeExecutor.runAll();
    }

    @Test
    public void testRefine() throws Exception {
        when(mAccessController.isClientAllowed(
                any(), eq(AccessController.ACCESS_RECEIVE_HINTS_PERMISSION))).thenReturn(true);

        BundleHint bundleHint = new BundleHint.Builder().build();
        PublishedContextHint hint =
                new PublishedContextHint.Builder(
                        bundleHint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Set<PublishedContextHint> signedHints = Set.of(hint);

        // Submit hints to refine.
        final Set<ContextHint> refinedHints = new HashSet<>();
        mServiceClientRefiner.refine(
                signedHints,
                (hints) -> {
                    refinedHints.addAll(hints);
                },
                (componentId, insights) -> {});
        mFakeExecutor.runAll();
        mServiceClientRefiner.onStarted(mIRefiner);
        mFakeExecutor.runAll();

        // Hints are sent to the refiner service.
        assertThat(mIRefiner.mInputHints).hasSize(1);
        final PublishedContextHint refinedHint =
                mIRefiner.mInputHints.getFirst().getPublishedContextHint();
        assertThat(refinedHint).isEqualTo(hint);

        // Refiner generates a new hint.
        final Bundle dataBundle = new Bundle();
        dataBundle.putString("foo", "bar");
        BundleHint bundleHint2 = new BundleHint.Builder().build();
        assertThat(mIRefiner.mIRefineCallback).isNotNull();
        mIRefiner.mIRefineCallback.onHintsRefined(List.of(new ContextHintWrapper(bundleHint2)));

        // Generated hint is delivered back to service.
        assertThat(refinedHints).containsExactly(bundleHint2);
    }

    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testRefine_noPublishHintPermissions_doesNotInvokeCallback() throws Exception {
        when(mAccessController.isClientAllowed(
                any(), eq(AccessController.ACCESS_RECEIVE_HINTS_PERMISSION))).thenReturn(true);
        doThrow(new SecurityException()).when(mAccessController).enforcePermissions(anyInt(),
                anyInt(), eq(AccessController.ACCESS_PUBLISH_HINTS_PERMISSION));


        BundleHint bundleHint = new BundleHint.Builder().build();
        PublishedContextHint hint =
                new PublishedContextHint.Builder(
                                bundleHint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Set<PublishedContextHint> signedHints = Set.of(hint);

        // Submit hints to refine.
        final Set<ContextHint> refinedHints = new HashSet<>();
        mServiceClientRefiner.refine(
                signedHints, (hints) -> refinedHints.addAll(hints), (componentId, insights) -> {});
        mFakeExecutor.runAll();
        mServiceClientRefiner.onStarted(mIRefiner);
        mFakeExecutor.runAll();

        // Hints are sent to the refiner service.
        assertThat(mIRefiner.mInputHints).hasSize(1);
        final PublishedContextHint refinedHint =
                mIRefiner.mInputHints.getFirst().getPublishedContextHint();
        assertThat(refinedHint).isEqualTo(hint);

        // Refiner generates a hint.
        assertThat(mIRefiner.mIRefineCallback).isNotNull();
        assertThrows(
                SecurityException.class,
                () ->
                        mIRefiner.mIRefineCallback.onHintsRefined(
                                List.of(new ContextHintWrapper(bundleHint))));

        // Generated hint is NOT provided to the callback because the permissions check failed.
        assertThat(refinedHints).isEmpty();
    }

    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testRefine_noReceiveHintPermissions_failsImmediately() throws Exception {
        when(mPermissionManager.checkPackageNamePermission(
                        eq(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS),
                        eq(TEST_PACKAGE_NAME),
                        anyInt(),
                        anyInt()))
                .thenReturn(PermissionManager.PERMISSION_HARD_DENIED);

        BundleHint bundleHint = new BundleHint.Builder().build();
        PublishedContextHint hint =
                new PublishedContextHint.Builder(
                                bundleHint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Set<PublishedContextHint> signedHints = Set.of(hint);

        // Submit hints to refine
        AtomicReference<Set<ContextHint>> refinedHints = new AtomicReference<>();
        mServiceClientRefiner.refine(
                signedHints, (hints) -> refinedHints.set(hints), (componentId, insights) -> {});

        mFakeExecutor.runAll();
        mServiceClientRefiner.onStarted(mIRefiner);
        mFakeExecutor.runAll();

        // Hints are not sent to refiner, and an empty set is returned immediately the callback.
        assertThat(mIRefiner.mInputHints).isNull();
        assertThat(refinedHints.get()).isEmpty();
    }

    private static final class FakeExecutor implements Executor {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            mQueue.add(command);
        }

        public void runAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove().run();
            }
        }

        public void clearAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove();
            }
        }
    }

    private static class TestIRefiner extends IRefiner.Stub {
        List<PublishedContextHintWrapper> mInputHints;
        IRefineCallback mIRefineCallback;

        @Override
        public void refine(
                ParcelUuid componentId,
                List<PublishedContextHintWrapper> inputHints,
                IRefineCallback callback,
                IOpCallback opCallback) {
            mInputHints = inputHints;
            mIRefineCallback = callback;
        }

        @Override
        public void getFilter(
                ParcelUuid componentId, IGetFilterCallback callback, IOpCallback opCallback) {}

        @Override
        public void handleEvent(
                ParcelUuid componentId,
                String packageName,
                InsightEvent event,
                IOpCallback opCallback) {}

        @Override
        public void handleFeedback(
                ParcelUuid componentId,
                PublishedContextInsightWrapper insight,
                Bundle feedback,
                IOpCallback opCallback) {}
    }
}
