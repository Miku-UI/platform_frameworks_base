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

package android.content.pm;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo.WindowLayout;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Rule;
import org.junit.Test;


@Presubmit
@SmallTest
public class WindowLayoutTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @EnableFlags(Flags.FLAG_RUNTIME_DENSITY_RESOLUTION_FOR_WINDOW_LAYOUT_BUGFIX)
    @Test
    public void testWindowLayout_resolvesDensityCorrectly() {
        final DisplayMetrics metrics = new DisplayMetrics();
        metrics.density = 1.5f;
        final int dpValue = 50;
        final int complex = TypedValue.createComplexDimension(dpValue, TypedValue.COMPLEX_UNIT_DIP);

        final WindowLayout layout = new WindowLayout(
                complex /* complexWidth */, -1f /* widthFraction */, complex /* complexHeight */,
                -1f /* heightFraction */, 0 /* gravity */, complex /* complexMinWidth */,
                complex /* complexMinHeight */, null /* windowLayoutAffinity */, metrics
        );

        int pxValue = (int) (dpValue * metrics.density);
        assertEquals(pxValue, layout.getDefaultWidth(metrics));
        assertEquals(pxValue, layout.getDefaultHeight(metrics));
        assertEquals(pxValue, layout.getMinWidth(metrics));
        assertEquals(pxValue, layout.getMinHeight(metrics));
        assertEquals(pxValue, layout.width);
        assertEquals(pxValue, layout.height);
        assertEquals(pxValue, layout.minWidth);
        assertEquals(pxValue, layout.minHeight);

        // Change the density and verify that the values reflect the new density.
        metrics.density = metrics.density * 2;
        pxValue = pxValue * 2;
        assertEquals(pxValue, layout.getDefaultWidth(metrics));
        assertEquals(pxValue, layout.getDefaultHeight(metrics));
        assertEquals(pxValue, layout.getMinWidth(metrics));
        assertEquals(pxValue, layout.getMinHeight(metrics));
    }
}
