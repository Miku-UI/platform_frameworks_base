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

import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.os.Process.FIRST_PCC_UID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.IntArray;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for {@link KillAppBlocker}.
 */
@RunWith(JUnit4.class)
@SmallTest
@Presubmit
public class KillAppBlockerTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();


    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final int TEST_USER_ID = 0;
    private static final int TEST_OWNER_UID = 10123;
    private static final int TEST_ISOLATED_UID = 99001;
    private static final int TEST_PCC_UID = FIRST_PCC_UID;

    @Mock private IActivityManager mIActivityManager;
    @Mock private ActivityManagerInternal mActivityManagerInternal;
    @Mock private Computer mSnapshot;
    @Mock private UserManagerService mUserManagerService;

    private KillAppBlocker mKillAppBlocker;
    private IUidObserver mUidObserver;
    private MockitoSession mMockitoSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMockitoSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager.class)
                .startMocking();

        // Replace the real ActivityManager service with our mock.
        ExtendedMockito.doReturn(mIActivityManager).when(ActivityManager::getService);

        // Capture the IUidObserver when it's registered.
        doAnswer((Answer<Void>) invocation -> {
            mUidObserver = (IUidObserver) invocation.getArguments()[0];
            return null;
        }).when(mIActivityManager).registerUidObserver(any(IUidObserver.class), anyInt(), anyInt(),
                any());

        mKillAppBlocker = new KillAppBlocker(5000); // 5-second timeout for tests
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testWaitsForIsolatedUid_andUnblocksWhenGone() throws Exception {
        // 1. Setup mocks
        when(mUserManagerService.getUserIds()).thenReturn(new int[]{TEST_USER_ID});
        when(mSnapshot.getPackageUidInternal(eq(TEST_PACKAGE_NAME), anyLong(), eq(TEST_USER_ID),
                anyInt())).thenReturn(TEST_OWNER_UID);
        IntArray isolatedUids = new IntArray();
        isolatedUids.add(TEST_ISOLATED_UID);
        when(mSnapshot.getIsolatedUidsForUid(TEST_OWNER_UID))
                .thenReturn(isolatedUids);

        // Initially, both UIDs are active
        when(mActivityManagerInternal.getUidProcessState(TEST_OWNER_UID))
                .thenReturn(PROCESS_STATE_TOP);
        when(mActivityManagerInternal.getUidProcessState(TEST_ISOLATED_UID))
                .thenReturn(PROCESS_STATE_TOP);

        final AtomicBoolean waitFinished = new AtomicBoolean(false);

        final CountDownLatch waiterStartedLatch = new CountDownLatch(1);
        // 2. Start waiting in a background thread
        Thread waiterThread = new Thread(() -> {
            mKillAppBlocker.register();
            waiterStartedLatch.countDown();
            mKillAppBlocker.waitAppProcessGone(mActivityManagerInternal, mSnapshot,
                    mUserManagerService, TEST_PACKAGE_NAME);
            waitFinished.set(true);
        });

        waiterThread.start();
        waiterStartedLatch.await(1, TimeUnit.SECONDS); // Wait for thread to start


        // 3. Assert that it is blocking
        assertFalse("waitAppProcessGone should be blocking", waitFinished.get());
        assertTrue("UidObserver should have been registered", mUidObserver != null);

        // 4. Simulate UIDs dying
        mUidObserver.onUidGone(TEST_OWNER_UID, false);

        // It should still be waiting for the isolated UID
        assertFalse("waitAppProcessGone should still be blocking for isolated UID",
                waitFinished.get());

        mUidObserver.onUidGone(TEST_ISOLATED_UID, false);

        // 5. Assert that it unblocks
        waiterThread.join(2000); // Wait for the thread to finish
        assertTrue("waitAppProcessGone should have unblocked after UID is gone",
                waitFinished.get());
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWaitsForPccUid_andUnblocksWhenGone() throws Exception {
        // 1. Setup mocks
        when(mUserManagerService.getUserIds()).thenReturn(new int[]{TEST_USER_ID});

        // Mock the normal owner UID as nonexistent so we isolate testing the PCC UID logic
        when(mSnapshot.getPackageUidInternal(eq(TEST_PACKAGE_NAME), anyLong(), eq(TEST_USER_ID),
                anyInt())).thenReturn(TEST_OWNER_UID);
        when(mActivityManagerInternal.getUidProcessState(TEST_OWNER_UID))
                .thenReturn(PROCESS_STATE_NONEXISTENT);
        when(mSnapshot.getIsolatedUidsForUid(TEST_OWNER_UID))
                .thenReturn(new IntArray());

        // Mock the PCC UID lookup
        when(mSnapshot.getPackageUidInternal(eq(TEST_PACKAGE_NAME), anyLong(), eq(TEST_USER_ID),
                anyInt(), eq(true))).thenReturn(TEST_PCC_UID);
        when(mActivityManagerInternal.getUidProcessState(TEST_PCC_UID))
                .thenReturn(PROCESS_STATE_TOP);

        final AtomicBoolean waitFinished = new AtomicBoolean(false);
        final CountDownLatch waiterStartedLatch = new CountDownLatch(1);

        // 2. Start waiting in a background thread
        Thread waiterThread = new Thread(() -> {
            mKillAppBlocker.register();
            waiterStartedLatch.countDown();
            mKillAppBlocker.waitAppProcessGone(mActivityManagerInternal, mSnapshot,
                    mUserManagerService, TEST_PACKAGE_NAME);
            waitFinished.set(true);
        });

        waiterThread.start();
        waiterStartedLatch.await(1, TimeUnit.SECONDS);

        // 3. Assert that it is blocking for the PCC UID
        assertFalse("waitAppProcessGone should be blocking for PCC UID", waitFinished.get());
        assertTrue("UidObserver should have been registered", mUidObserver != null);

        // 4. Simulate the PCC UID dying
        mUidObserver.onUidGone(TEST_PCC_UID, false);

        // 5. Assert that it unblocks
        waiterThread.join(2000);
        assertTrue("waitAppProcessGone should have unblocked after PCC UID is gone",
                waitFinished.get());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testWaitsForIsolatedUid_flagDisabled_doesNotWait() throws Exception {
        // 1. Setup mocks
        when(mUserManagerService.getUserIds()).thenReturn(new int[]{TEST_USER_ID});
        when(mSnapshot.getPackageUidInternal(eq(TEST_PACKAGE_NAME), anyLong(), eq(TEST_USER_ID),
                anyInt())).thenReturn(TEST_OWNER_UID);
        // Make sure the owner UID is not considered active, so the call doesn't block.
        when(mActivityManagerInternal.getUidProcessState(TEST_OWNER_UID))
                .thenReturn(PROCESS_STATE_NONEXISTENT);

        // 2. Call the method directly
        mKillAppBlocker.register();
        mKillAppBlocker.waitAppProcessGone(mActivityManagerInternal, mSnapshot,
                mUserManagerService, TEST_PACKAGE_NAME);

        // 3. Verify that we never tried to get the isolated UIDs
        verify(mSnapshot, never()).getIsolatedUidsForUid(anyInt());
    }
}

