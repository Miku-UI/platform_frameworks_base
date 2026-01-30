/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.TaskSnapshotController.SNAPSHOT_MODE_APP_THEME;
import static com.android.server.wm.TaskSnapshotController.SNAPSHOT_MODE_REAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.platform.test.annotations.Presubmit;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Test class for {@link TaskSnapshotController}.
 *
 * Build/Install/Run:
 *  *  atest WmTests:TaskSnapshotControllerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class TaskSnapshotControllerTest extends WindowTestsBase {

    @Test
    public void testGetSnapshotMode() {
        final WindowState disabledWindow = newWindowBuilder("disabledWindow",
                FIRST_APPLICATION_WINDOW).setDisplay(mDisplayContent).build();
        disabledWindow.mActivityRecord.setRecentsScreenshotEnabled(false);
        assertEquals(SNAPSHOT_MODE_APP_THEME,
                mWm.mTaskSnapshotController.getSnapshotMode(disabledWindow.getTask()));

        final WindowState normalWindow = newWindowBuilder("normalWindow",
                FIRST_APPLICATION_WINDOW).setDisplay(mDisplayContent).build();
        assertEquals(SNAPSHOT_MODE_REAL,
                mWm.mTaskSnapshotController.getSnapshotMode(normalWindow.getTask()));

        final WindowState secureWindow = newWindowBuilder("secureWindow",
                FIRST_APPLICATION_WINDOW).setDisplay(mDisplayContent).build();
        secureWindow.mAttrs.flags |= FLAG_SECURE;
        assertEquals(SNAPSHOT_MODE_APP_THEME,
                mWm.mTaskSnapshotController.getSnapshotMode(secureWindow.getTask()));

        // Verifies that if the snapshot can be cached, then getSnapshotMode should be respected.
        // Otherwise a real snapshot can be taken even if the activity disables recents screenshot.
        spyOn(mWm.mTaskSnapshotController);
        final int disabledInRecentsTaskId = disabledWindow.getTask().mTaskId;
        mAtm.takeTaskSnapshot(disabledInRecentsTaskId, true /* updateCache */);
        verify(mWm.mTaskSnapshotController, never()).prepareTaskSnapshot(any(), any());
        mAtm.takeTaskSnapshot(disabledInRecentsTaskId, false /* updateCache */);
        verify(mWm.mTaskSnapshotController).prepareTaskSnapshot(any(), any());
    }

    @Test
    public void testSnapshotBuilder() {
        final HardwareBuffer buffer = Mockito.mock(HardwareBuffer.class);
        final ColorSpace sRGB = ColorSpace.get(ColorSpace.Named.SRGB);
        final long id = 1234L;
        final ComponentName activityComponent = new ComponentName("package", ".Class");
        final int windowingMode = WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
        final int appearance = APPEARANCE_LIGHT_STATUS_BARS;
        final int pixelFormat = PixelFormat.RGBA_8888;
        final int orientation = Configuration.ORIENTATION_PORTRAIT;
        final float scaleFraction = 0.25f;
        final Rect contentInsets = new Rect(1, 2, 3, 4);
        final Rect letterboxInsets = new Rect(5, 6, 7, 8);
        final Point taskSize = new Point(9, 10);
        final int densityDpi = 400;

        try {
            TaskSnapshot.Builder builder =
                    new TaskSnapshot.Builder();
            builder.setId(id);
            builder.setTopActivityComponent(activityComponent);
            builder.setAppearance(appearance);
            builder.setWindowingMode(windowingMode);
            builder.setColorSpace(sRGB);
            builder.setOrientation(orientation);
            builder.setContentInsets(contentInsets);
            builder.setLetterboxInsets(letterboxInsets);
            builder.setIsTranslucent(true);
            builder.setSnapshot(buffer);
            builder.setIsRealSnapshot(true);
            builder.setPixelFormat(pixelFormat);
            builder.setTaskSize(taskSize);
            builder.setDensityDpi(densityDpi);

            // Not part of TaskSnapshot itself, used in screenshot process
            assertEquals(pixelFormat, builder.getPixelFormat());

            TaskSnapshot snapshot = builder.build();
            assertEquals(id, snapshot.getId());
            assertEquals(activityComponent, snapshot.getTopActivityComponent());
            assertEquals(appearance, snapshot.getAppearance());
            assertEquals(windowingMode, snapshot.getWindowingMode());
            assertEquals(sRGB, snapshot.getColorSpace());
            // Snapshots created with the Builder class are always high-res. The only way to get a
            // low-res snapshot is to load it from the disk in TaskSnapshotLoader.
            assertFalse(snapshot.isLowResolution());
            assertEquals(orientation, snapshot.getOrientation());
            assertEquals(contentInsets, snapshot.getContentInsets());
            assertEquals(letterboxInsets, snapshot.getLetterboxInsets());
            assertTrue(snapshot.isTranslucent());
            assertTrue(snapshot.isSameHardwareBuffer(buffer));
            assertTrue(snapshot.isRealSnapshot());
            assertEquals(taskSize, snapshot.getTaskSize());
            assertEquals(densityDpi, snapshot.getDensityDpi());
        } finally {
            if (buffer != null) {
                buffer.close();
            }
        }
    }

    @SetupWindows(addWindows = { W_ACTIVITY, W_INPUT_METHOD })
    @Test
    public void testCreateTaskSnapshotWithExcludingIme() {
        Task task = mAppWindow.mActivityRecord.getTask();
        spyOn(task);
        spyOn(mDisplayContent);
        when(task.getDisplayContent().shouldImeAttachedToApp()).thenReturn(false);
        // Intentionally set the SurfaceControl of input method window as null.
        mDisplayContent.mInputMethodWindow.setSurfaceControl(null);
        // Verify no NPE happens when calling createTaskSnapshot.
        try {
            final TaskSnapshot.Builder builder = new TaskSnapshot.Builder();
            mWm.mTaskSnapshotController.createSnapshot(mAppWindow.mActivityRecord.getTask(),
                    1f /* scaleFraction */, new Rect() /* crop */, builder);
        } catch (NullPointerException e) {
            fail("There should be no exception when calling createTaskSnapshot");
        }
    }

    @SetupWindows(addWindows = { W_ACTIVITY, W_INPUT_METHOD })
    @Test
    public void testCreateTaskSnapshotWithIncludingIme() {
        Task task = mAppWindow.mActivityRecord.getTask();
        spyOn(task);
        spyOn(mDisplayContent);
        spyOn(mDisplayContent.mInputMethodWindow);
        when(task.getDisplayContent().shouldImeAttachedToApp()).thenReturn(true);
        // Intentionally set the IME window is in visible state.
        doReturn(true).when(mDisplayContent.mInputMethodWindow).isVisible();
        // Verify no NPE happens when calling createTaskSnapshot.
        try {
            final TaskSnapshot.Builder builder = new TaskSnapshot.Builder();
            spyOn(builder);
            mWm.mTaskSnapshotController.createSnapshot(
                    mAppWindow.mActivityRecord.getTask(), 1f /* scaleFraction */,
                    new Rect() /* crop */, builder);
            // Verify the builder should includes IME surface.
            verify(builder).setHasImeSurface(eq(true));
            builder.setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            builder.setTaskSize(new Point(100, 100));
            final TaskSnapshot snapshot = builder.build();
            assertTrue(snapshot.hasImeSurface());
        } catch (NullPointerException e) {
            fail("There should be no exception when calling createTaskSnapshot");
        }
    }

    @SetupWindows(addWindows = W_ACTIVITY)
    @Test
    public void testPrepareTaskSnapshot() {
        mAppWindow.mWinAnimator.mLastAlpha = 1f;
        spyOn(mAppWindow.mWinAnimator);
        doReturn(true).when(mAppWindow.mWinAnimator).getShown();

        final TaskSnapshot.Builder builder =
                new TaskSnapshot.Builder();
        boolean success = mWm.mTaskSnapshotController.prepareTaskSnapshot(
                mAppWindow.mActivityRecord.getTask(), builder) != null;

        assertTrue(success);
        // The pixel format should be selected automatically.
        assertNotEquals(PixelFormat.UNKNOWN, builder.getPixelFormat());

        // Snapshot should not be taken while the rotation of activity and task are different.
        doReturn(true).when(mAppWindow.mActivityRecord).hasFixedRotationTransform();
        success = mWm.mTaskSnapshotController.prepareTaskSnapshot(
                mAppWindow.mActivityRecord.getTask(), builder) != null;

        assertFalse(success);
    }

    @Test
    public void testRecordTaskSnapshot() {
        spyOn(mWm.mTaskSnapshotController.mCache);
        spyOn(mWm.mTaskSnapshotController);
        doReturn(false).when(mWm.mTaskSnapshotController).shouldDisableSnapshots();

        final WindowState normalWindow = newWindowBuilder("normalWindow",
                FIRST_APPLICATION_WINDOW).setDisplay(mDisplayContent).build();
        final TaskSnapshot snapshot = new TaskSnapshotPersisterTestBase.TaskSnapshotBuilder()
                .setTopActivityComponent(normalWindow.mActivityRecord.mActivityComponent).build();
        doReturn(snapshot).when(mWm.mTaskSnapshotController).snapshot(any());
        final Task task = normalWindow.mActivityRecord.getTask();
        mWm.mTaskSnapshotController.recordSnapshot(task);
        verify(mWm.mTaskSnapshotController.mCache).putSnapshot(eq(task), any());
        clearInvocations(mWm.mTaskSnapshotController.mCache);

        normalWindow.mAttrs.flags |= FLAG_SECURE;
        mWm.mTaskSnapshotController.recordSnapshot(task);
        waitHandlerIdle(mWm.mH);
        verify(mWm.mTaskSnapshotController.mCache).putSnapshot(eq(task), any());
    }

    @Test
    public void testGetTaskSnapshotFromClient() {
        spyOn(mWm.mTaskSnapshotController.mCache);
        spyOn(mWm.mTaskSnapshotController);
        final long captureTime = 100;
        final WindowState normalWindow = newWindowBuilder("normalWindow",
                FIRST_APPLICATION_WINDOW).setDisplay(mDisplayContent).build();
        final Task task = normalWindow.mActivityRecord.getTask();

        final TaskSnapshot diskSnapshot = new TaskSnapshotPersisterTestBase.TaskSnapshotBuilder()
                .setTopActivityComponent(normalWindow.mActivityRecord.mActivityComponent)
                .build();
        doReturn(diskSnapshot).when(mWm.mTaskSnapshotController)
                .getSnapshotFromDisk(anyInt(), anyInt(), anyBoolean(), anyInt());
        doReturn(null).when(mWm.mTaskSnapshotController.mCache)
                .getSnapshot(anyInt(), anyInt(), anyInt());

        // Client process doesn't has snapshot.
        TaskSnapshot result = mWm.mSnapshotController.getTaskSnapshotInner(task.mTaskId, task,
                -1 /* latestCaptureTime */, TaskSnapshotManager.RESOLUTION_ANY);
        assertEquals(result, diskSnapshot);

        // Put snapshot in cache
        final TaskSnapshot snapshot = new TaskSnapshotPersisterTestBase.TaskSnapshotBuilder()
                .setTopActivityComponent(normalWindow.mActivityRecord.mActivityComponent)
                .setCaptureTime(captureTime).build();
        doReturn(snapshot).when(mWm.mTaskSnapshotController.mCache)
                .getSnapshot(anyInt(), anyInt(), anyInt());

        // Client process doesn't has snapshot.
        result = mWm.mSnapshotController.getTaskSnapshotInner(task.mTaskId, task,
                -1 /* latestCaptureTime */, TaskSnapshotManager.RESOLUTION_ANY);
        assertEquals(result, snapshot);

        // Snapshot in client process is older than in system server.
        result = mWm.mSnapshotController.getTaskSnapshotInner(task.mTaskId, task,
                captureTime - 10 /* latestCaptureTime */, TaskSnapshotManager.RESOLUTION_ANY);
        assertEquals(result, snapshot);

        // Snapshot in client process is the same as in system server.
        result = mWm.mSnapshotController.getTaskSnapshotInner(task.mTaskId, task,
                captureTime, TaskSnapshotManager.RESOLUTION_ANY);
        assertNull(result);

        // Snapshot in client process is newer than in system server?
        result = mWm.mSnapshotController.getTaskSnapshotInner(task.mTaskId, task,
                captureTime + 10 /* latestCaptureTime */, TaskSnapshotManager.RESOLUTION_ANY);
        assertNull(result);
    }
}
