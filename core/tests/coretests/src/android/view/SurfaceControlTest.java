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

package android.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.graphics.surfaceflinger.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class SurfaceControlTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testMirrorSurface_invalidMirrorRoot() {
        SurfaceControl invalidSurfaceControl = new SurfaceControl();
        SurfaceControl mirror = SurfaceControl.mirrorSurface(invalidSurfaceControl);
        assertFalse(mirror.isValid());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MIRROR_WITH_CROP)
    public void testMirrorSurfaceWithCrop_invalidMirrorRoot() {
        SurfaceControl invalidSurfaceControl = new SurfaceControl();
        SurfaceControl cropBy = new SurfaceControl.Builder().setName("cropBy").build();
        SurfaceControl mirror = SurfaceControl.mirrorWithCrop(invalidSurfaceControl, cropBy);
        assertFalse(mirror.isValid());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MIRROR_WITH_CROP)
    public void testMirrorSurfaceWithCrop_nullBounds() {
        SurfaceControl source = new SurfaceControl.Builder().setName("source").build();
        SurfaceControl mirror = SurfaceControl.mirrorWithCrop(source, null);
        assertTrue(mirror.isValid());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MIRROR_WITH_CROP)
    public void testMirrorSurfaceWithCrop_validBounds() {
        SurfaceControl source = new SurfaceControl.Builder().setName("source").build();
        SurfaceControl cropBy = new SurfaceControl.Builder().setName("cropBy").build();
        SurfaceControl mirror = SurfaceControl.mirrorWithCrop(source, cropBy);
        assertTrue(mirror.isValid());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MIRROR_WITH_CROP)
    public void testMirrorSurfaceWithCrop_withStopAt() {
        SurfaceControl source = new SurfaceControl.Builder().setName("source").build();
        SurfaceControl stopAt = new SurfaceControl.Builder().setName("stopAt").build();
        SurfaceControl cropBy = new SurfaceControl.Builder().setName("cropBy").build();
        SurfaceControl mirror = SurfaceControl.mirrorWithCrop(source, stopAt, cropBy);
        assertTrue(mirror.isValid());
    }
}
