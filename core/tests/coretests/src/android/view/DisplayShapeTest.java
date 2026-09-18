/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;
import android.util.PathParser;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link DisplayShape}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class DisplayShapeTest {
    // Rectangle with w=100, height=200
    private static final String SPEC_RECTANGULAR_SHAPE = "M0,0 L100,0 L100,200 L0,200 Z";

    @Test
    public void testGetPath() {
        final DisplayShape displayShape = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200);
        final Path path = displayShape.getPath();
        final RectF actualRect = new RectF();
        path.computeBounds(actualRect, false);

        final RectF expectRect = new RectF(0f, 0f, 100f, 200f);
        assertEquals(actualRect, expectRect);
    }

    @Test
    public void testDefaultShape_screenIsRound() {
        final DisplayShape displayShape = DisplayShape.createDefaultDisplayShape(100, 100, true);

        // A circle with radius = 50.
        final String expectSpec = "M0,50.0 A50.0,50.0 0 1,1 100,50.0 A50.0,50.0 0 1,1 0,50.0 Z";
        final Path expectedPath = PathParser.createPathFromPathData(expectSpec);
        final Path actualPath = displayShape.getPath();
        final Path diffPath = new Path();
        diffPath.op(actualPath, expectedPath, Path.Op.XOR);
        assertTrue(diffPath.isEmpty());
    }

    @Test
    public void testDefaultShape_screenIsNotRound() {
        final DisplayShape displayShape = DisplayShape.createDefaultDisplayShape(100, 200, false);

        // A rectangle with width/height = 100/200.
        final String expectSpec = "M0,0 L100,0 L100,200 L0,200 Z";
        final Path expectedPath = PathParser.createPathFromPathData(expectSpec);
        final Path actualPath = displayShape.getPath();
        final Path diffPath = new Path();
        diffPath.op(actualPath, expectedPath, Path.Op.XOR);
        assertTrue(diffPath.isEmpty());
    }

    @Test
    public void testDefaultShape_cache() {
        final DisplayShape shapeA = DisplayShape.createDefaultDisplayShape(100, 100, true);
        // This should be a new instance and update the cache.
        DisplayShape.createDefaultDisplayShape(200, 200, false);
        // This should be a new instance because the cache was updated.
        final DisplayShape shapeC = DisplayShape.createDefaultDisplayShape(100, 100, true);
        // This should be the same instance as shapeC.
        final DisplayShape shapeD = DisplayShape.createDefaultDisplayShape(100, 100, true);

        assertThat(shapeC, not(sameInstance(shapeA)));
        assertThat(shapeD, sameInstance(shapeC));
    }

    @Test
    public void testFromResources_cache() {
        final DisplayShape shapeA = DisplayShape.fromResources("local:1", 100, 200, 100, 200);
        // This should be a new instance with different logical dimensions.
        final DisplayShape shapeB = DisplayShape.fromResources("local:1", 100, 200, 200, 100);
        // This should be a new instance because the cache was updated by shapeB.
        final DisplayShape shapeC = DisplayShape.fromResources("local:1", 100, 200, 100, 200);
        // This should be the same instance as shapeC.
        final DisplayShape shapeD = DisplayShape.fromResources("local:1", 100, 200, 100, 200);


        assertThat(shapeA, not(sameInstance(shapeB)));
        assertThat(shapeC, not(sameInstance(shapeA)));
        assertThat(shapeD, sameInstance(shapeC));
    }

    @Test
    public void testFromSpecString_cache() {
        final DisplayShape cached = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200);
        assertThat(DisplayShape.fromSpecString(SPEC_RECTANGULAR_SHAPE, 1f, 100, 200),
                sameInstance(cached));
    }

    @Test
    public void testGetPath_cache() {
        final Path cached = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200).getPath();
        assertThat(DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200).getPath(),
                sameInstance(cached));
    }

    @Test
    public void testRotate_90() {
        DisplayShape displayShape = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200);
        displayShape = displayShape.setRotation(ROTATION_90);
        final Path path = displayShape.getPath();
        final RectF actualRect = new RectF();
        path.computeBounds(actualRect, false);

        final RectF expectRect = new RectF(0f, 0f, 200f, 100f);
        assertEquals(actualRect, expectRect);
    }

    @Test
    public void testRotate_270() {
        DisplayShape displayShape = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200);
        displayShape = displayShape.setRotation(ROTATION_270);
        final Path path = displayShape.getPath();
        final RectF actualRect = new RectF();
        path.computeBounds(actualRect, false);

        final RectF expectRect = new RectF(0f, 0f, 200f, 100f);
        assertEquals(actualRect, expectRect);
    }

    @Test
    public void testOffset() {
        DisplayShape displayShape = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 1f, 100, 200);
        displayShape = displayShape.setOffset(-10, -20);
        final Path path = displayShape.getPath();
        final RectF actualRect = new RectF();
        path.computeBounds(actualRect, false);

        final RectF expectRect = new RectF(-10f, -20f, 90f, 180f);
        assertEquals(actualRect, expectRect);
    }

    @Test
    public void testPhysicalPixelDisplaySizeRatio() {
        final DisplayShape displayShape = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 0.5f, 100, 200);
        final Path path = displayShape.getPath();
        final RectF actualRect = new RectF();
        path.computeBounds(actualRect, false);

        final RectF expectRect = new RectF(0f, 0f, 50f, 100f);
        assertEquals(actualRect, expectRect);
    }

    @Test
    public void testParcelable() {
        final DisplayShape original = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 0.5f, 100, 200);
        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final DisplayShape restored = DisplayShape.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(original, restored);
    }

    @Test
    public void testParcelable_caching() {
        final DisplayShape original = DisplayShape.fromSpecString(
                SPEC_RECTANGULAR_SHAPE, 0.5f, 100, 200);
        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final DisplayShape restored1 = DisplayShape.CREATOR.createFromParcel(parcel);
        parcel.setDataPosition(0);
        final DisplayShape restored2 = DisplayShape.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(restored2, sameInstance(restored1));
    }
}
