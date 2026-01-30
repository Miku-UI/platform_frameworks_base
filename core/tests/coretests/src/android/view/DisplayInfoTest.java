/*
 * Copyright (C) 2023 The Android Open Source Project
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


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DisplayInfoTest {
    private static final float FLOAT_EQUAL_DELTA = 0.0001f;

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Test
    public void testDefaultDisplayInfosAreEqual() {
        DisplayInfo displayInfo1 = new DisplayInfo();
        DisplayInfo displayInfo2 = new DisplayInfo();

        assertTrue(displayInfo1.equals(displayInfo2));
    }

    @Test
    public void testDefaultDisplayInfoRefreshRateIs0() {
        DisplayInfo displayInfo = new DisplayInfo();

        assertEquals(0, displayInfo.getRefreshRate(), FLOAT_EQUAL_DELTA);
    }

    @Test
    public void testRefreshRateOverride() {
        DisplayInfo displayInfo = new DisplayInfo();

        displayInfo.refreshRateOverride = 50;

        assertEquals(50, displayInfo.getRefreshRate(), FLOAT_EQUAL_DELTA);

    }

    @Test
    public void testRefreshRateOverride_keepsDisplayInfosEqual() {
        Display.Mode mode = new Display.Mode(
                /*modeId=*/1, /*width=*/1000, /*height=*/1000, /*refreshRate=*/120);
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedMode(displayInfo1, mode);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedMode(displayInfo2, mode);
        displayInfo2.refreshRateOverride = 120;

        assertTrue(displayInfo1.equals(displayInfo2));
    }

    @Test
    public void testRefreshRateOverride_keepsDisplayInfosEqualWhenOverrideIsSame() {
        Display.Mode mode = new Display.Mode(
                /*modeId=*/1, /*width=*/1000, /*height=*/1000, /*refreshRate=*/120);
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedMode(displayInfo1, mode);
        displayInfo1.renderFrameRate = 60;
        displayInfo1.refreshRateOverride = 30;

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedMode(displayInfo2, mode);
        displayInfo2.renderFrameRate = 30;
        displayInfo2.refreshRateOverride = 30;

        assertTrue(displayInfo1.equals(displayInfo2));
    }

    @Test
    public void testRefreshRateOverride_makeDisplayInfosDifferent() {
        Display.Mode mode = new Display.Mode(
                /*modeId=*/1, /*width=*/1000, /*height=*/1000, /*refreshRate=*/120);
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedMode(displayInfo1, mode);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedMode(displayInfo2, mode);
        displayInfo2.refreshRateOverride = 90;

        assertFalse(displayInfo1.equals(displayInfo2));
    }

    @Test
    public void testResolutionChange_makesDisplayInfosDifferent() {
        var modes = new Display.Mode[] {
            new Display.Mode(/*modeId=*/1, /*width=*/1024, /*height=*/768, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/2, /*width=*/1024, /*height=*/768, /*refreshRate=*/120),
            new Display.Mode(/*modeId=*/3, /*width=*/800, /*height=*/600, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/4, /*width=*/800, /*height=*/600, /*refreshRate=*/120)
        };
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedModes(displayInfo1, modes, 1);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedModes(displayInfo2, modes, 3);

        assertFalse(displayInfo1.equals(displayInfo2, /* compareOnlyBasicChanges= */ true));
    }

    @Test
    public void testNoResolutionChange_keepsDisplayInfosEqual() {
        var modes = new Display.Mode[] {
            new Display.Mode(/*modeId=*/1, /*width=*/1024, /*height=*/768, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/2, /*width=*/1024, /*height=*/768, /*refreshRate=*/120),
            new Display.Mode(/*modeId=*/3, /*width=*/800, /*height=*/600, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/4, /*width=*/800, /*height=*/600, /*refreshRate=*/120)
        };
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedModes(displayInfo1, modes, 1);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedModes(displayInfo2, modes, 2);

        assertTrue(displayInfo1.equals(displayInfo2, /* compareOnlyBasicChanges= */ true));
    }

    @Test
    public void testOneModeNotFound_makesDisplayInfosDifferent() {
        var modes = new Display.Mode[] {
            new Display.Mode(/*modeId=*/1, /*width=*/1024, /*height=*/768, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/2, /*width=*/1024, /*height=*/768, /*refreshRate=*/120),
            new Display.Mode(/*modeId=*/3, /*width=*/800, /*height=*/600, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/4, /*width=*/800, /*height=*/600, /*refreshRate=*/120)
        };
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedModes(displayInfo1, modes, 1);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedModes(displayInfo2, modes, 0);

        assertFalse(displayInfo1.equals(displayInfo2, /* compareOnlyBasicChanges= */ true));
    }

    @Test
    public void testBothModesNotFound_makesDisplayInfosEqual() {
        var modes = new Display.Mode[] {
            new Display.Mode(/*modeId=*/1, /*width=*/1024, /*height=*/768, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/2, /*width=*/1024, /*height=*/768, /*refreshRate=*/120),
            new Display.Mode(/*modeId=*/3, /*width=*/800, /*height=*/600, /*refreshRate=*/60),
            new Display.Mode(/*modeId=*/4, /*width=*/800, /*height=*/600, /*refreshRate=*/120)
        };
        DisplayInfo displayInfo1 = new DisplayInfo();
        setSupportedModes(displayInfo1, modes, 0);

        DisplayInfo displayInfo2 = new DisplayInfo();
        setSupportedModes(displayInfo2, modes, 0);

        assertTrue(displayInfo1.equals(displayInfo2, /* compareOnlyBasicChanges= */ true));
    }

    @Test
    public void getBasicChangedGroups_noChanges_returnsEmptyList() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups).isEqualTo(0);
    }

    @Test
    public void getBasicChangedGroups_basicPropertiesChanged_returnsOnlyBasicProperties() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.flags = Display.FLAG_PRIVATE; // Change a basic property

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES.getMask());
    }

    @Test
    public void getBasicChangedGroups_dimensionsChanged_returnsOnlyDimensionsAndShapes() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.logicalWidth = 1440; // Change a dimension

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask());
    }

    @Test
    public void getBasicChangedGroups_rotationChanged_returnsOnlyOrientationAndRotation() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.rotation = Surface.ROTATION_90; // Change rotation

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask());
    }

    @Test
    public void getBasicChangedGroups_modeSizeChanged_returnsOnlyRefreshRateAndMode() {
        DisplayInfo base = new DisplayInfo();
        base.supportedModes = new Display.Mode[]{new Display.Mode(1, 1080, 1920, 60f)};
        base.modeId = 1;

        DisplayInfo other = new DisplayInfo(base);
        // Change the mode to one with a different physical size.
        other.supportedModes = new Display.Mode[]{new Display.Mode(2, 1440, 2560, 60f)};
        other.modeId = 2;

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.REFRESH_RATE_AND_MODE.getMask());
    }

    @Test
    public void getBasicChangedGroups_colorChanged_returnsOnlyColorAndBrightness() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.brightnessDefault = 0.9f; // Change a brightness property

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.COLOR_AND_BRIGHTNESS.getMask());
    }

    @Test
    public void getBasicChangedGroups_stateChanged_returnsOnlyState() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.state = Display.STATE_OFF; // Change state

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.STATE.getMask());
    }

    @Test
    public void getBasicChangedGroups_multipleGroupsChanged_returnsAllChangedGroups() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.ownerUid = 1001; // BASIC_PROPERTIES
        other.appHeight = 100; // DIMENSIONS_AND_SHAPES
        other.state = Display.STATE_DOZE; // STATE

        int changedGroups = base.getBasicChangedGroups(other);

        assertThat(changedGroups)
                .isEqualTo(
                        DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES.getMask()
                        | DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask()
                        | DisplayInfo.DisplayInfoGroup.STATE.getMask());
    }

    @Test
    public void getBasicChangedGroups_nonBasicChanges_returnsEmptyList() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        // This field is not considered "basic" changes and should not be reported.
        other.appVsyncOffsetNanos = 2L;

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups).isEqualTo(0);
    }

    @Test
    public void getBasicChangedGroups_modeChangedToSameSizeMode_returnsEmptyList() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo();

        Display.Mode[] modes = {
            new Display.Mode(1, 1080, 1920, 60f), new Display.Mode(2, 1080, 1920, 90f)
        };

        setSupportedModes(base, modes, 1);
        // Change mode ID to another mode that has the same physical dimensions.
        // This is not a basic change.
        setSupportedModes(other, modes, 2);

        int changedGroups = base.getBasicChangedGroups(other);
        assertThat(changedGroups).isEqualTo(0);
    }

    @Test
    public void getBasicChangedGroups_otherIsNull_returns_all_groups() {
        DisplayInfo base = new DisplayInfo();
        int changedGroups = base.getBasicChangedGroups(null);
        assertThat(changedGroups).isEqualTo(
                DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES.getMask()
                | DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask()
                | DisplayInfo.DisplayInfoGroup.REFRESH_RATE_AND_MODE.getMask()
                | DisplayInfo.DisplayInfoGroup.COLOR_AND_BRIGHTNESS.getMask()
                | DisplayInfo.DisplayInfoGroup.STATE.getMask()
                | DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask()
        );
    }

    @Test
    public void getBasicChangedGroups_otherIsNull_returns_some_groups() {
        DisplayInfo base = new DisplayInfo();
        int changedGroups = base.getBasicChangedGroups(null,
                EnumSet.of(DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES,
                        DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION));
        assertThat(changedGroups).isEqualTo(
                DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES.getMask()
                        | DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask()
        );
    }

    @Test
    public void getBasicChangedGroups_withGroupsToCompare_singleGroupChanged_Matches() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.logicalWidth = 1440; // Belongs to DIMENSIONS_AND_SHAPES

        EnumSet<DisplayInfo.DisplayInfoGroup> groupsToCompare =
                EnumSet.of(DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES);

        int changedGroups = base.getBasicChangedGroups(other, groupsToCompare);

        assertThat(changedGroups)
                .isEqualTo(DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask());
    }

    @Test
    public void getBasicChangedGroups_withGroupsToCompare_singleGroupChanged_DoesNotMatch() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.logicalWidth = 1440; // Belongs to DIMENSIONS_AND_SHAPES

        // Compare a different group that has not changed.
        EnumSet<DisplayInfo.DisplayInfoGroup> groupsToCompare =
                EnumSet.of(DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES);

        int changedGroups = base.getBasicChangedGroups(other, groupsToCompare);

        assertThat(changedGroups).isEqualTo(0);
    }

    @Test
    public void getBasicChangedGroups_withGroupsToCompare_multipleGroupsChanged() {
        DisplayInfo base = new DisplayInfo();
        DisplayInfo other = new DisplayInfo(base);
        other.flags = Display.FLAG_PRIVATE; // BASIC_PROPERTIES
        other.rotation = Surface.ROTATION_180; // ORIENTATION_AND_ROTATION
        other.state = Display.STATE_VR; // STATE

        // Only compare two of the three changed groups.
        EnumSet<DisplayInfo.DisplayInfoGroup> groupsToCompare =
                EnumSet.of(DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES,
                        DisplayInfo.DisplayInfoGroup.STATE);

        int changedGroups = base.getBasicChangedGroups(other, groupsToCompare);

        assertThat(changedGroups).isEqualTo(
                DisplayInfo.DisplayInfoGroup.BASIC_PROPERTIES.getMask()
                        | DisplayInfo.DisplayInfoGroup.STATE.getMask());
    }

    private void setSupportedModes(DisplayInfo info, Display.Mode[] modes, int modeId) {
        info.supportedModes = modes;
        info.modeId = modeId;
        info.refreshRateOverride = 90;
    }

    private void setSupportedMode(DisplayInfo info, Display.Mode mode) {
        info.supportedModes = new Display.Mode[]{mode};
        info.modeId = mode.getModeId();
    }

}
