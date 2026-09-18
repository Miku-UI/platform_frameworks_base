/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.window.TaskSnapshot;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test class for {@link TaskSnapshotCache}.
 *
 * Build/Install/Run:
 *  atest WmTests:TaskSnapshotCacheTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class TaskSnapshotCacheTest extends TaskSnapshotPersisterTestBase {

    private TaskSnapshotCache mCache;
    @Mock
    TaskSnapshot mSnapshot;

    public TaskSnapshotCacheTest() {
        super(0.8f, 0.5f);
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mCache = new TaskSnapshotCache(mLoader, mWm.mH);
    }

    @Test
    public void testAppRemoved() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mCache.putSnapshot(window.getTask(), createSnapshot());
        assertNotNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
        mCache.onAppRemoved(window.mActivityRecord);
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
    }

    @Test
    public void testAppDied() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mCache.putSnapshot(window.getTask(), createSnapshot());
        assertNotNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
        mCache.onAppDied(window.mActivityRecord);
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
    }

    @Test
    public void testTaskRemoved() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mCache.putSnapshot(window.getTask(), createSnapshot());
        assertNotNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
        mCache.onIdRemoved(window.getTask().mTaskId);
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
    }

    @Test
    public void testReduced_notCached() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mPersister.persistSnapshot(window.getTask().mTaskId, mWm.mCurrentUserId, createSnapshot());
        mSnapshotPersistQueue.waitForQueueEmpty();
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));

        // Load it from disk
        assertNotNull(mCache.getSnapshotFromDisk(window.getTask().mTaskId, mWm.mCurrentUserId,
                true /* isLowResolution */, TaskSnapshot.REFERENCE_NONE));

        // Make sure it's not in the cache now.
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
    }

    @Test
    public void testRestoreFromDisk() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mPersister.persistSnapshot(window.getTask().mTaskId, mWm.mCurrentUserId, createSnapshot());
        mSnapshotPersistQueue.waitForQueueEmpty();
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));

        // Load it from disk
        assertNotNull(mCache.getSnapshotFromDisk(window.getTask().mTaskId, mWm.mCurrentUserId,
                false/* isLowResolution */, TaskSnapshot.REFERENCE_NONE));
    }

    @Test
    @EnableFlags(Flags.FLAG_ONLY_CACHE_LOW_RES_TASK_SNAPSHOT)
    public void testRestoreFromDiskDeferRemove() {
        spyOn(mCache.mDeferRemoveCache);
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        final Task task = window.getTask();
        final int taskId = task.mTaskId;
        final TaskSnapshot snapshot = createSnapshot();
        mPersister.persistSnapshot(taskId, mWm.mCurrentUserId, snapshot);
        mSnapshotPersistQueue.waitForQueueEmpty();
        assertNull(mCache.getSnapshot(taskId, false /* isLowResolution */));
        // Simulate replaced by the low-resolution snapshot.
        mCache.putSnapshot(window.getTask(), snapshot);
        // Load it from disk
        final TaskSnapshot loaded = mCache.getSnapshotFromDisk(taskId, mWm.mCurrentUserId,
                false/* isLowResolution */, TaskSnapshot.REFERENCE_NONE);
        assertNotNull(loaded);
        verify(mCache.mDeferRemoveCache).putSnapshot(eq(taskId), eq(loaded));
        verify(mCache.mDeferRemoveCache).scheduleRemoval(eq(taskId));
    }

    @Test
    public void testClearCache() {
        final WindowState window = newWindowBuilder("window", FIRST_APPLICATION_WINDOW).build();
        mCache.putSnapshot(window.getTask(), mSnapshot);
        assertEquals(mSnapshot, mCache.getSnapshot(window.getTask().mTaskId,
                false /* isLowResolution */));
        mCache.clearRunningCache();
        assertNull(mCache.getSnapshot(window.getTask().mTaskId, false /* isLowResolution */));
    }
}
