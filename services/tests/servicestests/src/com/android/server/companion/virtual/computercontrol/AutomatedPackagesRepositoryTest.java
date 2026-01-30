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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.IAutomatedPackageListener;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.TestLooperManager;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Consumer;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class AutomatedPackagesRepositoryTest {

    private static final String PACKAGE1 = "com.foo";
    private static final String PACKAGE2 = "com.bar";

    private static final Pair<Integer, String> UID_USER1_PACKAGE1 = new Pair<>(100001, PACKAGE1);
    private static final Pair<Integer, String> UID_USER1_PACKAGE2 = new Pair<>(100002, PACKAGE2);
    private static final Pair<Integer, String> UID_USER2_PACKAGE1 = new Pair<>(200001, PACKAGE1);
    private static final Pair<Integer, String> UID_USER2_PACKAGE2 = new Pair<>(200002, PACKAGE2);

    private static final UserHandle USER1 = UserHandle.of(1);
    private static final UserHandle USER2 = UserHandle.of(2);

    private static final int DEVICE_ID1 = 7;
    private static final int DEVICE_ID2 = 8;
    private static final String DEVICE_OWNER = "com.device.owner";

    @Mock
    private IAutomatedPackageListener mListener;
    @Mock
    private Consumer<Integer> mCloseVirtualDeviceMock;

    @Captor
    private ArgumentCaptor<List<String>> mPackageNamesCaptor;

    private AutomatedPackagesRepository mRepo;

    private final HandlerThread mTestHandlerThread = new HandlerThread("TestHandlerThread");
    private TestLooperManager mTestLooperManager;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        IBinder binder = new Binder();
        when(mListener.asBinder()).thenReturn(binder);

        mTestHandlerThread.start();
        Looper looper = mTestHandlerThread.getLooper();
        mTestLooperManager = new TestLooperManager(looper);
        mRepo = new AutomatedPackagesRepository(new Handler(looper));
        mRepo.registerAutomatedPackageListener(mListener);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
        mTestHandlerThread.quit();
        mTestLooperManager.release();
    }


    @Test
    public void update_singleDevice_singleUser_notifiesListeners() throws Exception {
        // Add a single package.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        assertListenerReceived(List.of(PACKAGE1), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Add a second package.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(UID_USER1_PACKAGE1, UID_USER1_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE1, PACKAGE2), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Remove the first package.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE2), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Remove all packages.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>());
        assertListenerReceived(List.of(), USER1);
        assertThat(mTestLooperManager.poll()).isNull();
    }

    @Test
    public void update_singleDevice_multiUser_notifiesListeners() throws Exception {
        // Add one package for two different users.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(UID_USER1_PACKAGE1, UID_USER2_PACKAGE1)));
        assertListenerReceived(List.of(PACKAGE1), USER1);
        assertListenerReceived(List.of(PACKAGE1), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Change the package for user 2. Only user 2 should be notified.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(UID_USER1_PACKAGE1, UID_USER2_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE2), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Add a package for user 2. Only user 2 should be notified.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(
                        UID_USER1_PACKAGE1, UID_USER2_PACKAGE1, UID_USER2_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE1, PACKAGE2), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Remove all packages for user 1, and one package for user 2. Both should be notified.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER2_PACKAGE2)));
        assertListenerReceived(List.of(), USER1);
        assertListenerReceived(List.of(PACKAGE2), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Remove all remaining packages.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>());
        assertListenerReceived(List.of(), USER2);
        assertThat(mTestLooperManager.poll()).isNull();
    }

    @Test
    public void update_multiDevice_singleUser_notifiesListeners() throws Exception {
        // Device 1 adds a package for user 1.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        assertListenerReceived(List.of(PACKAGE1), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 2 adds another package for user 1. The total should be an aggregation.
        mRepo.update(DEVICE_ID2, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE1, PACKAGE2), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 1 updates its list, but the total aggregated list for the user doesn't change.
        // No notification should be sent.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(UID_USER1_PACKAGE1, UID_USER1_PACKAGE2)));
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 2 removes its packages, but the total aggregated list still doesn't change.
        // No notification should be sent.
        mRepo.update(DEVICE_ID2, DEVICE_OWNER, new ArraySet<>());
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 1 removes its packages. Now the user has no packages.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>());
        assertListenerReceived(List.of(), USER1);
        assertThat(mTestLooperManager.poll()).isNull();
    }

    @Test
    public void update_multiDevice_multiUser_notifiesListeners() throws Exception {
        // Device 1 adds a package for user 1.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        assertListenerReceived(List.of(PACKAGE1), USER1);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 2 adds a package for user 2.
        // The repository should now report packages for both users under the same device owner.
        mRepo.update(DEVICE_ID2, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER2_PACKAGE2)));
        assertListenerReceived(List.of(PACKAGE2), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 1 adds a package for user 2.
        // The packages for user 2 should be aggregated from both devices.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER,
                new ArraySet<>(List.of(UID_USER1_PACKAGE1, UID_USER2_PACKAGE1)));
        assertListenerReceived(List.of(PACKAGE1, PACKAGE2), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 2 is removed.
        // The packages for user 2 should now only reflect what's on device 1.
        mRepo.update(DEVICE_ID2, DEVICE_OWNER, new ArraySet<>());
        assertListenerReceived(List.of(PACKAGE1), USER2);
        assertThat(mTestLooperManager.poll()).isNull();

        // Device 1 is removed.
        // All packages for both users should be gone.
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>());
        assertListenerReceived(List.of(), USER1);
        assertListenerReceived(List.of(), USER2);
        assertThat(mTestLooperManager.poll()).isNull();
    }

    @Test
    public void createAutomatedAppLaunchWarningIntent_appNotAutomated_returnsNull() {
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));

        assertThat(mRepo.createAutomatedAppLaunchWarningIntent(
                PACKAGE2, USER1.getIdentifier(), PACKAGE2, null, mCloseVirtualDeviceMock))
                .isNull();
    }

    @Test
    public void createAutomatedAppLaunchWarningIntent_displayBelongsToSessionOwner_returnsNull() {
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        assertThat(mRepo.createAutomatedAppLaunchWarningIntent(
                PACKAGE1, USER1.getIdentifier(), PACKAGE2, DEVICE_OWNER, mCloseVirtualDeviceMock))
                .isNull();
    }

    @Test
    public void createAutomatedAppLaunchWarningIntent_callerIsSessionOwner_returnsNull() {
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        assertThat(mRepo.createAutomatedAppLaunchWarningIntent(
                PACKAGE1, USER1.getIdentifier(), DEVICE_OWNER, null, mCloseVirtualDeviceMock))
                .isNull();
    }

    @Test
    public void createAutomatedAppLaunchWarningIntent_createsInterceptionIntent() {
        mRepo.update(DEVICE_ID1, DEVICE_OWNER, new ArraySet<>(List.of(UID_USER1_PACKAGE1)));
        processOneHandlerMessage();

        Intent intent = mRepo.createAutomatedAppLaunchWarningIntent(
                PACKAGE1, USER1.getIdentifier(), PACKAGE2, null, mCloseVirtualDeviceMock);
        assertThat(intent).isNotNull();
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(PACKAGE1);
        assertThat(intent.getStringExtra(ComputerControlSession.EXTRA_AUTOMATING_PACKAGE_NAME))
                .isEqualTo(DEVICE_OWNER);
        ResultReceiver resultReceiver =
                intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

        // The user chose to stop the automation.
        resultReceiver.send(Activity.RESULT_OK, null);

        // Subsequent launch is not intercepted.
        processOneHandlerMessage();
        assertThat(mRepo.createAutomatedAppLaunchWarningIntent(
                PACKAGE1, USER1.getIdentifier(), PACKAGE2, null, mCloseVirtualDeviceMock))
                .isNull();

        // The session is closed.
        processOneHandlerMessage();
        verify(mCloseVirtualDeviceMock).accept(DEVICE_ID1);
    }

    private void assertListenerReceived(List<String> packages, UserHandle user) throws Exception {
        processOneHandlerMessage();
        verify(mListener).onAutomatedPackagesChanged(
                eq(DEVICE_OWNER), mPackageNamesCaptor.capture(), eq(user));
        assertThat(mPackageNamesCaptor.getValue()).containsExactlyElementsIn(packages);
        reset(mListener);
    }

    private void processOneHandlerMessage() {
        var msg = mTestLooperManager.poll();
        assertThat(msg).isNotNull();
        mTestLooperManager.execute(msg);
        mTestLooperManager.recycle(msg);
    }
}
