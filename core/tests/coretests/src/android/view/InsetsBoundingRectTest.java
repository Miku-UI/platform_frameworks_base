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

package android.view;

import static android.view.WindowInsets.Side.BOTTOM;
import static android.view.WindowInsets.Side.LEFT;
import static android.view.WindowInsets.Side.RIGHT;
import static android.view.WindowInsets.Side.TOP;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link InsetsBoundingRect}.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:InsetsBoundingRectTest
 *
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class InsetsBoundingRectTest {

    @Test
    public void testScale() {
        final InsetsBoundingRect r = new InsetsBoundingRect(LEFT, 1, 2, 3, 4);
        r.scale(2f);
        assertEquals(new InsetsBoundingRect(LEFT, 2, 4, 6, 8), r);
    }

    @Test
    public void testToRect() {
        final Rect sourceFrame = new Rect(0, 0, 10, 10);
        final Rect actualRect = new Rect();

        // left aligned
        InsetsBoundingRect r = new InsetsBoundingRect(LEFT, 2, 0, 4, 10);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(2, 0, 6, 10), actualRect);

        // horizontal center aligned
        r = new InsetsBoundingRect(0, 2, 0, 4, 10);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(5, 0, 9, 10), actualRect);

        // right aligned
        r = new InsetsBoundingRect(RIGHT, 2, 0, 4, 10);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(8, 0, 12, 10), actualRect);

        // both horizontal sides aligned
        r = new InsetsBoundingRect(LEFT | RIGHT, 2, 0, 4, 10);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(2, 0, 12, 10), actualRect);

        // top aligned
        r = new InsetsBoundingRect(TOP, 0, 2, 10, 4);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(0, 2, 10, 6), actualRect);

        // vertical center aligned
        r = new InsetsBoundingRect(0, 0, 2, 10, 4);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(0, 5, 10, 9), actualRect);

        // bottom aligned
        r = new InsetsBoundingRect(BOTTOM, 0, 2, 10, 4);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(0, 8, 10, 12), actualRect);

        // both vertical sides aligned
        r = new InsetsBoundingRect(TOP | BOTTOM, 0, 2, 10, 4);
        r.toRect(sourceFrame, actualRect);
        assertEquals(new Rect(0, 2, 10, 12), actualRect);
    }
}
