/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutChangeCallback;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.ShortcutService.ConfigConstants;

import org.mockito.ArgumentCaptor;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.List;

/**
 * Tests for {@link android.content.pm.LauncherApps.ShortcutChangeCallback} and relevant APIs.
 *
 atest -c com.android.server.pm.ShortcutManagerTest11
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ShortcutManagerTest11 extends BaseShortcutManagerTest {

    @ClassRule
    public static final SetFlagsRule.ClassRule mSetFlagsClassRule = new SetFlagsRule.ClassRule();
    @Rule public final SetFlagsRule mSetFlagsRule = mSetFlagsClassRule.createSetFlagsRule();

    private static final ShortcutQuery QUERY_MATCH_ALL = createShortcutQuery(
            ShortcutQuery.FLAG_MATCH_ALL_KINDS_WITH_ALL_PINNED);

    private static final int CACHE_OWNER_0 = LauncherApps.FLAG_CACHE_NOTIFICATION_SHORTCUTS;
    private static final int CACHE_OWNER_1 = LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS;

    private final TestLooper mTestLooper = new TestLooper();

    @Test
    public void testShortcutChangeCallback_setDynamicShortcuts() {
        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_setDynamicShortcuts_replaceSameId() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s2", "s3")));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_setDynamicShortcuts_pinnedAndCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(
                    list(makeShortcut("s1"), makeLongLivedShortcut("s2"))));
        });

        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s1"), HANDLE_USER_10);
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s3", "s4")));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2", "s3", "s4");
        });
    }

    public void disabled_testShortcutChangeCallback_pinShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s1"), HANDLE_USER_10);
        });

        mTestLooper.dispatchAll();
        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_pinShortcuts_unpinOthers() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2", "s3")));
        });

        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s1", "s2"), HANDLE_USER_10);
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeDynamicShortcuts(list("s1", "s2"));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s2", "s3"), HANDLE_USER_10);
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_cacheShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeLongLivedShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeLongLivedShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_1);
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(2)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s3");
        });
    }

    @Test
    public void testShortcutChangeCallback_cacheShortcuts_alreadyCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeLongLivedShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeLongLivedShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            // Should not cause any callback events
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            // Should cause a change event
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_1);
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s3");
        });
    }

    @Test
    public void testShortcutChangeCallback_uncacheShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeLongLivedShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeLongLivedShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            mLauncherApps.uncacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_uncacheShortcuts_causeDeletion() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeLongLivedShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeLongLivedShortcut("s3"))));
        });

        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1", "s2", "s3"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10);
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s1"), HANDLE_USER_10,
                    CACHE_OWNER_1);
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeDynamicShortcuts(list("s2", "s3"));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
            mLauncherApps.uncacheShortcuts(CALLING_PACKAGE_1, list("s1", "s2", "s3"),
                    HANDLE_USER_10, CACHE_OWNER_0);
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            // s1 is still cached for owner1, s2 is pinned.
            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s3");
        });
    }

    @Test
    public void testShortcutChangeCallback_updateShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeShortcut("s1"),
                    makeShortcutWithActivity("s2", new ComponentName(CALLING_PACKAGE_1, "test")))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        final ComponentName updatedCn = new ComponentName(CALLING_PACKAGE_1, "updated activity");
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.updateShortcuts(list(makeShortcutWithActivity("s2", updatedCn))));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2");
            assertEquals(updatedCn, ((ShortcutInfo) shortcuts.getValue().get(0)).getActivity());
        });
    }

    @Test
    public void testShortcutChangeCallback_addDynamicShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.addDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_pushDynamicShortcut() {
        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.pushDynamicShortcut(makeShortcut("s1"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    private void verifyWithRetry(Runnable verification) {
        try {
            verification.run();
            return;
        } catch (Throwable t) {
            // This schedules a task to be run in the future.
            new Handler(Looper.getMainLooper()).postDelayed(
                () -> verifyWithRetry(verification), 500);
        }
    }

    @Test
    public void testShortcutChangeCallback_pushDynamicShortcut_existingId() {
        // Change the max number of shortcuts.
        mService.updateConfigurationLocked(ConfigConstants.KEY_MAX_SHORTCUTS + "=3");

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts((makeShortcuts("s1", "s2", "s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.pushDynamicShortcut(makeShortcut("s2"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_pushDynamicShortcut_causeDeletion() {
        // Change the max number of shortcuts.
        mService.updateConfigurationLocked(ConfigConstants.KEY_MAX_SHORTCUTS + "=3");

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts((makeShortcuts("s1", "s2", "s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.pushDynamicShortcut(makeShortcut("s4"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s4");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s3");
        });
    }

    @Test
    public void testShortcutChangeCallback_pushDynamicShortcut_causeDeletionButCached() {
        // Change the max number of shortcuts.
        mService.updateConfigurationLocked(ConfigConstants.KEY_MAX_SHORTCUTS + "=3");

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts((makeShortcuts("s1", "s2"))));
            ShortcutInfo s3 = makeLongLivedShortcut("s3");
            s3.setRank(3);
            mManager.pushDynamicShortcut(s3);  // Add a long lived shortcut to the end of the list.
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.pushDynamicShortcut(makeShortcut("s4"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s3", "s4");
        });
    }

    @Test
    public void testShortcutChangeCallback_disableShortcuts() {
        updatePackageVersion(CALLING_PACKAGE_1, 1);
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.disableShortcuts(list("s2"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            verify(callback, times(0)).onShortcutsAddedOrUpdated(any(), any(), any());

            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_disableShortcuts_pinnedAndCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(
                    list(makeShortcut("s1"), makeLongLivedShortcut("s2"), makeShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.disableShortcuts(list("s1", "s2", "s3"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_enableShortcuts() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(
                    list(makeShortcut("s1"), makeLongLivedShortcut("s2"), makeShortcut("s3"))));
        });

        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10);
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.disableShortcuts(list("s1", "s2", "s3"));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.enableShortcuts(list("s1", "s2", "s3"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));
            verify(callback, times(0)).onShortcutsRemoved(any(), any(), any());

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeDynamicShortcuts() {
        updatePackageVersion(CALLING_PACKAGE_1, 1);
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeDynamicShortcuts(list("s2"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            verify(callback, times(0)).onShortcutsAddedOrUpdated(any(), any(), any());

            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeDynamicShortcuts_pinnedAndCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeShortcut("s3"), makeShortcut("s4"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeDynamicShortcuts(list("s1", "s2", "s3"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeAllDynamicShortcuts() {
        updatePackageVersion(CALLING_PACKAGE_1, 1);
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeAllDynamicShortcuts();
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            verify(callback, times(0)).onShortcutsAddedOrUpdated(any(), any(), any());

            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeAllDynamicShortcuts_pinnedAndCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(
                    list(makeShortcut("s1"), makeLongLivedShortcut("s2"), makeShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeAllDynamicShortcuts();
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s2", "s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeLongLivedShortcuts_notCached() {
        updatePackageVersion(CALLING_PACKAGE_1, 1);
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeShortcut("s3"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeLongLivedShortcuts(list("s1", "s2"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            verify(callback, times(0)).onShortcutsAddedOrUpdated(any(), any(), any());

            ArgumentCaptor<List> shortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), shortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(shortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");
        });
    }

    @Test
    public void testShortcutChangeCallback_removeLongLivedShortcuts_pinnedAndCached() {
        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(list(makeShortcut("s1"),
                    makeLongLivedShortcut("s2"), makeShortcut("s3"), makeShortcut("s4"))));
        });

        ShortcutChangeCallback callback = mock(ShortcutChangeCallback.class);
        runWithCaller(LAUNCHER_1, USER_10, () -> {
            mInjectCheckAccessShortcutsPermission = true;
            mLauncherApps.cacheShortcuts(CALLING_PACKAGE_1, list("s2"), HANDLE_USER_10,
                    CACHE_OWNER_0);
            mLauncherApps.pinShortcuts(CALLING_PACKAGE_1, list("s3"), HANDLE_USER_10);
            mLauncherApps.registerShortcutChangeCallback(callback, QUERY_MATCH_ALL,
                    mTestLooper.getNewExecutor());
        });

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            mManager.removeLongLivedShortcuts(list("s1", "s2", "s3"));
        });

        mTestLooper.dispatchAll();

        verifyWithRetry(() -> {
            ArgumentCaptor<List> changedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsAddedOrUpdated(
                    eq(CALLING_PACKAGE_1), changedShortcuts.capture(), eq(HANDLE_USER_10));

            ArgumentCaptor<List> removedShortcuts = ArgumentCaptor.forClass(List.class);
            verify(callback, times(1)).onShortcutsRemoved(
                    eq(CALLING_PACKAGE_1), removedShortcuts.capture(), eq(HANDLE_USER_10));

            assertWith(changedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s3");

            assertWith(removedShortcuts.getValue())
                    .areAllWithKeyFieldsOnly()
                    .haveIds("s1", "s2");
        });
    }

    @Test
    @EnableFlags(android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL)
    public void testsetDynamicShortcuts_flagEnabled_appLockEnabled_assertFalse() {
        doAnswer(invocation -> {
                return true;
            }).when(mMockPackageManagerInternal).isPackageAppLockEnabled(
                eq(CALLING_PACKAGE_1), eq(USER_10));

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertFalse(mManager.setDynamicShortcuts(makeShortcuts("s3", "s4")));
        });
    }

    @Test
    @EnableFlags(android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL)
    public void testsetDynamicShortcuts_flagEnabled_appLockDisabled_assertTrue() {
        doAnswer(invocation -> {
                return false;
            }).when(mMockPackageManagerInternal).isPackageAppLockEnabled(
                eq(CALLING_PACKAGE_1), eq(USER_10));

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s3", "s4")));
        });
    }

    @Test
    @DisableFlags(android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL)
    public void testSetDynamicShortcuts_flagDisabled_appLockEnabled_assertTrue() {
        doAnswer(invocation -> {
                return true;
            }).when(mMockPackageManagerInternal).isPackageAppLockEnabled(
                eq(CALLING_PACKAGE_1), eq(USER_10));

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });
    }

    @Test
    @DisableFlags(android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL)
    public void testSetDynamicShortcuts_flagDisabled_appLockDisabled_assertTrue() {
        doAnswer(invocation -> {
                return false;
            }).when(mMockPackageManagerInternal).isPackageAppLockEnabled(
                eq(CALLING_PACKAGE_1), eq(USER_10));

        runWithCaller(CALLING_PACKAGE_1, USER_10, () -> {
            assertTrue(mManager.setDynamicShortcuts(makeShortcuts("s1", "s2")));
        });
    }

    private static ShortcutQuery createShortcutQuery(int queryFlags) {
        ShortcutQuery q = new ShortcutQuery();
        return q.setQueryFlags(ShortcutQuery.FLAG_MATCH_ALL_KINDS_WITH_ALL_PINNED);
    }
}
