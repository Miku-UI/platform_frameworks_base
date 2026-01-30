/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.view.WindowInsets.Type.ALL;
import static android.view.WindowInsets.Type.TYPES;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.indexOf;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsets.Type.systemBars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Insets;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.WindowInsets.Type.InsetsType;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class WindowInsetsTest {

    /**
     * Verifies that all the values in {@link WindowInsets.Type#TYPES} are included in
     * {@link WindowInsets.Type#ALL}.
     */
    @Test
    public void testTypesInAll() {
        int combinedTypes = 0;
        for (@InsetsType int type : TYPES) {
            assertTrue("type in TYPES should be included in ALL", (type & ALL) != 0);
            combinedTypes |= type;
        }
        assertEquals("ALL should match bitmask of combined types", ALL, combinedTypes);
    }

    /**
     * Verifies that all the values in {@link WindowInsets.Type#ALL} are included in
     * {@link WindowInsets.Type#TYPES}.
     */
    @Test
    public void testAllInTypes() {
        for (int index = 0; index < Integer.SIZE; index++) {
            final int type = 1 << index;
            if ((type & WindowInsets.Type.ALL) != 0) {
                assertTrue("index of bit in ALL should be less than number of TYPES",
                        index < TYPES.length);
                assertEquals("type in ALL should be included in TYPES", type, TYPES[index]);
            }
        }
    }

    /**
     * Verifies that all the values in {@link WindowInsets.Type#TYPES} are included in
     * {@link WindowInsets.Type#indexOf}.
     */
    @Test
    public void testTypesInIndexOf() {
        for (@InsetsType int type : TYPES) {
            assertEquals("type should match", type, TYPES[WindowInsets.Type.indexOf(type)]);
        }
        assertThrows(IllegalArgumentException.class, () -> WindowInsets.Type.indexOf(-1));
    }

    /**
     * Verifies that all the values returned by {@link WindowInsets.Type#indexOf} are included in
     * {@link WindowInsets.Type#TYPES}.
     */
    @Test
    public void testIndexOfInTypes() {
        for (int i = 0; i < Integer.SIZE; i++) {
            final int type = 1 << i;
            try {
                final int index = WindowInsets.Type.indexOf(type);
                assertTrue("index should be non-negative", index >= 0);
                assertTrue("index should be less than number of TYPES", index < TYPES.length);
                assertEquals(type, TYPES[index]);
            } catch (IllegalArgumentException e) {
                // ignore undefined indexOf case, handled through testTypesInIndexOf above.
            }
        }
    }

    /**
     * Verifies that all the values in {@link WindowInsets.Type#TYPES} are included in
     * {@link WindowInsets.Type#toString}.
     */
    @Test
    public void testTypesInToString() {
        for (@InsetsType int type : TYPES) {
            assertFalse("type toString should not be empty",
                    WindowInsets.Type.toString(type).isEmpty());
        }
        assertTrue("invalid type toString should be empty",
                WindowInsets.Type.toString(0).isEmpty());
    }

    /**
     * Verifies that all the values accepted by {@link WindowInsets.Type#toString} are included in
     * {@link WindowInsets.Type#TYPES}.
     */
    @Test
    public void testToStringInTypes() {
        for (int i = 0; i < Integer.SIZE; i++) {
            final int type = 1 << i;
            if (!WindowInsets.Type.toString(type).isEmpty()) {
                assertEquals("type should match", type, TYPES[i]);
            }
        }
    }

    @Test
    public void systemWindowInsets_afterConsuming_isConsumed() {
        assertTrue(new WindowInsets(WindowInsets.createCompatTypeMap(new Rect(1, 2, 3, 4)), null,
                null, false, 0, false, 0, null, null, null, null,
                WindowInsets.Type.systemBars(), false, null, null, 0, 0)
                .consumeSystemWindowInsets().isConsumed());
    }

    @Test
    public void multiNullConstructor_isConsumed() {
        assertTrue(new WindowInsets(null, null, null, false, 0, false, 0, null, null, null, null,
                WindowInsets.Type.systemBars(), false, null, null, 0, 0).isConsumed());
    }

    @Test
    public void singleNullConstructor_isConsumed() {
        assertTrue(new WindowInsets((Rect) null).isConsumed());
    }

    @Test
    public void compatInsets_layoutStable() {
        Insets[] insets = new Insets[TYPES.length];
        Insets[] maxInsets = new Insets[TYPES.length];
        boolean[] visible = new boolean[TYPES.length];
        WindowInsets.assignCompatInsets(maxInsets, new Rect(0, 10, 0, 0));
        WindowInsets.assignCompatInsets(insets, new Rect(0, 0, 0, 0));
        WindowInsets windowInsets = new WindowInsets(insets, maxInsets, visible, false, 0,
                false, 0, null, null, null, DisplayShape.NONE, systemBars(),
                true /* compatIgnoreVisibility */, null, null, 0, 0);
        assertEquals(Insets.of(0, 10, 0, 0), windowInsets.getSystemWindowInsets());
    }

    @Test
    public void builder_copy_compatInsetTypes() {
        final Insets[] insets = new Insets[TYPES.length];
        final Insets[] maxInsets = new Insets[TYPES.length];
        final boolean[] visible = new boolean[TYPES.length];
        final int compatInsetTypes = systemBars() | displayCutout() | ime();
        final WindowInsets windowInsets = new WindowInsets(insets, maxInsets, visible, false, 0,
                false, 0, null, null, null, DisplayShape.NONE, compatInsetTypes,
                false /* compatIgnoreVisibility */, null, null, 0, 0);
        final WindowInsets modified = new WindowInsets.Builder(windowInsets)
                .setInsets(statusBars(), Insets.of(0, 10, 0, 0))
                .setInsets(navigationBars(), Insets.of(0, 0, 20, 0))
                .setInsets(displayCutout(), Insets.of(30, 0, 0, 0))
                .setInsets(ime(), Insets.of(0, 0, 0, 40))
                .build();
        assertEquals(Insets.of(30, 10, 20, 40), modified.getSystemWindowInsets());
    }

    @Test
    public void builder_copy_compatIgnoreVisibility() {
        final Insets[] insets = new Insets[TYPES.length];
        final Insets[] maxInsets = new Insets[TYPES.length];
        final boolean[] visible = new boolean[TYPES.length];
        final int compatInsetTypes = systemBars() | displayCutout();
        final WindowInsets windowInsets = new WindowInsets(insets, maxInsets, visible, false, 0,
                false, 0, null, null, null, DisplayShape.NONE, compatInsetTypes,
                true /* compatIgnoreVisibility */, null, null, 0, 0);
        final WindowInsets modified = new WindowInsets.Builder(windowInsets)
                .setInsetsIgnoringVisibility(statusBars(), Insets.of(0, 10, 0, 0))
                .setInsetsIgnoringVisibility(navigationBars(), Insets.of(0, 0, 20, 0))
                .setInsetsIgnoringVisibility(displayCutout(), Insets.of(30, 0, 0, 0))
                .build();
        assertEquals(Insets.of(30, 10, 20, 0), modified.getSystemWindowInsets());
    }

    @Test
    public void builder_setSystemWindowInsets() {
        final Insets[] insets = new Insets[TYPES.length];
        final Insets[] maxInsets = new Insets[TYPES.length];
        final boolean[] visible = new boolean[TYPES.length];
        final int compatInsetTypes = systemBars() | displayCutout() | ime();
        maxInsets[indexOf(captionBar())] = Insets.of(0, 10, 0, 0);
        maxInsets[indexOf(navigationBars())] = Insets.of(0, 0, 20, 0);
        maxInsets[indexOf(displayCutout())] = Insets.of(30, 0, 0, 0);
        insets[indexOf(ime())] = Insets.of(0, 0, 0, 40);
        final WindowInsets windowInsets = new WindowInsets(insets, maxInsets, visible, false, 0,
                false, 0, null, null, null, DisplayShape.NONE, compatInsetTypes,
                true /* compatIgnoreVisibility */, null, null, 0, 0);
        assertEquals(Insets.of(30, 10, 20, 40), windowInsets.getSystemWindowInsets());
        final WindowInsets modified = new WindowInsets.Builder(windowInsets)
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .build();
        assertEquals(Insets.of(1, 2, 3, 4), modified.getSystemWindowInsets());
    }

    @Test
    public void testSetBoundingRectsInBuilder_noInsets_preservedInWindowInsets() {
        final List<Rect> rects = List.of(new Rect(0, 0, 50, 100));
        final WindowInsets insets =
                new WindowInsets.Builder()
                        .setBoundingRects(captionBar(), rects)
                        .setBoundingRectsIgnoringVisibility(captionBar(), rects)
                        .build();

        assertEquals(rects, insets.getBoundingRects(captionBar()));
        assertEquals(rects, insets.getBoundingRectsIgnoringVisibility(captionBar()));
    }
}
