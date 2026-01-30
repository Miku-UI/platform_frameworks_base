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

package com.android.systemfeatures;

import static com.android.systemfeatures.SystemFeaturesLookup.getDeclaredFeatureVarNameFromValue;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SystemFeaturesLookupGeneratorTest {

    @Test
    public void testDeclaredFeatures() {
        // Public hardware features
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.bluetooth"))
                .isEqualTo("FEATURE_BLUETOOTH");
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.type.automotive"))
                .isEqualTo("FEATURE_AUTOMOTIVE");
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.type.pc"))
                .isEqualTo("FEATURE_PC");
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.type.television"))
                .isEqualTo("FEATURE_TELEVISION");
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.type.watch"))
                .isEqualTo("FEATURE_WATCH");

        // Public software features
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.print"))
                .isEqualTo("FEATURE_PRINTING");
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.webview"))
                .isEqualTo("FEATURE_WEBVIEW");

        // Public deprecated feature
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.vr.mode"))
                .isEqualTo("FEATURE_VR_MODE");

        // TestApi feature
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.adoptable_storage"))
                .isEqualTo("FEATURE_ADOPTABLE_STORAGE");

        // SystemApi feature
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.incremental_delivery"))
                .isEqualTo("FEATURE_INCREMENTAL_DELIVERY");

        // Flagged feature
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.xr.input.controller"))
                .isEqualTo("FEATURE_XR_INPUT_CONTROLLER");
    }

    @Test
    public void testUndeclaredFeatures() {
        assertThat(getDeclaredFeatureVarNameFromValue("")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("com.foo.")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("com.my.custom.feature")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("android.")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("android.hardware.nonexistent")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.")).isNull();
        assertThat(getDeclaredFeatureVarNameFromValue("android.software.nonexistent")).isNull();
    }
}
