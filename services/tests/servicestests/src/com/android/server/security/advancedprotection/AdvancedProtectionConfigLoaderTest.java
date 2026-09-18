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

package com.android.server.security.advancedprotection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.security.advancedprotection.AdvancedProtectionManager;

import com.android.internal.pm.pkg.component.AconfigFlags;
import com.android.server.security.advancedprotection.AdvancedProtectionConfigLoader.Injector;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tests for {@link AdvancedProtectionConfigLoader}. */
@RunWith(TestParameterInjector.class)
public class AdvancedProtectionConfigLoaderTest {

    private static final String CONFIG_ALL_FEATURES_AVAILABLE =
            """
            <advanced-protection-config>
                <available-protections>
                    <protection id="DISALLOW_CELLULAR_2G" />
                    <protection id="DISALLOW_INSTALL_UNKNOWN_SOURCES" />
                    <protection id="DISALLOW_USB" />
                    <protection id="ENABLE_MTE" />
                </available-protections>
            </advanced-protection-config>
            """;

    private static final String CONFIG_EMPTY =
            """
            <advanced-protection-config>
            </advanced-protection-config>
            """;

    private static final String CONFIG_EMPTY_PROTECTIONS =
            """
            <advanced-protection-config>
                <available-protections>
                </available-protections>
            </advanced-protection-config>
            """;

    private static final String CONFIG_MIXED_FEATURES =
            """
            <advanced-protection-config>
                <available-protections>
                    <protection id="DISALLOW_CELLULAR_2G" />
                    <protection id="DISALLOW_USB" />
                </available-protections>
            </advanced-protection-config>
            """;

    private static final String CONFIG_UNEXPECTED_TAG_XML =
            """
            <advanced-protection-config>
                <unexpected-tag />
                <available-protections>
                    <protection id="DISALLOW_CELLULAR_2G" />
                    <protection id="DISALLOW_USB" />
                </available-protections>
            </advanced-protection-config>
            """;

    private static final String CONFIG_WITH_UNKNOWN_FEATURE =
            """
            <advanced-protection-config>
                <available-protections>
                    <protection id="DISALLOW_CELLULAR_2G" />
                    <protection id="UNKNOWN_FEATURE" />
                </available-protections>
            </advanced-protection-config>
            """;

    private static final String CONFIG_MALFORMED_XML =
            """
            <advanced-protection-config>
                <available-protections>
                    <protection id="DISALLOW_CELLULAR_2G" />
                </available-protections>
            </advanced-protection-config
            """;

    private Injector mInjector;

    @Before
    public void setUp() {
        mInjector = mock(Injector.class);
    }

    @Test
    public void constructor_isFeatureIdAvailable_withValidSystemConfig_succeeds() throws Exception {
        when(mInjector.readSystemConfig())
                .thenReturn(getInputStream(CONFIG_ALL_FEATURES_AVAILABLE));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertTrue(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
        assertTrue(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES));
        assertTrue(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB));
        assertTrue(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE));
    }

    @Test
    public void constructor_withEmptySystemConfig_succeeds() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_EMPTY));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
    }

    @Test
    public void constructor_withEmptyProtectionsSystemConfig_succeeds() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_EMPTY_PROTECTIONS));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
    }

    @Test
    public void constructor_withUnexpectedTagSystemConfig_succeeds() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_UNEXPECTED_TAG_XML));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertTrue(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
    }

    @Test
    public void constructor_withMissingSystemConfig_throwsIllegalStateException() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(null);

        assertThrows(
                IllegalStateException.class, () -> new AdvancedProtectionConfigLoader(mInjector));
    }

    @Test
    public void constructor_withIoExceptionOnSystemConfig_throwsIllegalStateException()
            throws Exception {
        when(mInjector.readSystemConfig()).thenThrow(new IOException());

        assertThrows(
                IllegalStateException.class, () -> new AdvancedProtectionConfigLoader(mInjector));
    }

    @Test
    public void constructor_withMalformedSystemConfig_throwsIllegalStateException()
            throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_MALFORMED_XML));

        assertThrows(
                IllegalStateException.class, () -> new AdvancedProtectionConfigLoader(mInjector));
    }

    @Test
    public void constructor_withUnknownFeature_throwsIllegalArgumentException() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_WITH_UNKNOWN_FEATURE));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvancedProtectionConfigLoader(mInjector));
    }

    @Test
    public void constructor_withUnknownFeatureFlag_throwsIllegalArgumentException()
            throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(
                """
                <advanced-protection-config>
                    <available-protections>
                        <protection id="DISALLOW_CELLULAR_2G" featureFlag="android.test.feature" />
                    </available-protections>
                </advanced-protection-config>
                """));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvancedProtectionConfigLoader(mInjector));
    }

    @Test
    public void isFeatureIdAvailable_whenAllFeaturesUnavailable_returnsFalseForAll()
            throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_EMPTY));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES));
        assertFalse(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB));
        assertFalse(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE));
    }

    @Test
    public void isFeatureIdAvailable_withMixedFeatures_returnsCorrectly() throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(CONFIG_MIXED_FEATURES));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertTrue(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G));
        assertTrue(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB));
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES));
        assertFalse(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE));
    }

    @Test
    public void isFeatureIdAvailable_withMixedFeaturesWithFeatureFlags_returnsCorrectly(
            @TestParameter boolean feature1Enabled, @TestParameter boolean feature2Enabled)
            throws Exception {
        when(mInjector.readSystemConfig()).thenReturn(getInputStream(
                """
                <advanced-protection-config>
                    <available-protections>
                        <protection id="DISALLOW_CELLULAR_2G" featureFlag="android.test.feature1" />
                        <protection id="DISALLOW_USB" featureFlag="!android.test.feature2" />
                    </available-protections>
                </advanced-protection-config>
                """));
        String feature1Flag = "android.test.feature1";
        String feature2Flag = "android.test.feature2";

        AconfigFlags.getInstance()
                .addFlagValuesForTesting(
                        Map.of(feature1Flag, feature1Enabled, feature2Flag, feature2Enabled));

        AdvancedProtectionConfigLoader loader = new AdvancedProtectionConfigLoader(mInjector);
        assertEquals(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G),
                feature1Enabled);
        assertEquals(
                loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB),
                !feature2Enabled);
        assertFalse(
                loader.isFeatureIdAvailable(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES));
        assertFalse(loader.isFeatureIdAvailable(AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE));
    }

    private InputStream getInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
