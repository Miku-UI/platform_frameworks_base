/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.server.display;

import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_ASK;
import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP;
import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_MIRROR;
import static android.hardware.display.DisplayManager.HDR_PREFERENCE_HDR_ALLOWED;
import static android.hardware.display.DisplayManager.HDR_PREFERENCE_SDR_ONLY;

import static com.android.server.display.persistence.PersistentDataStoreTestUtils.createTestDisplayDevice;
import static com.android.server.display.persistence.PersistentDataStoreTestUtils.stateBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.BrightnessConfiguration;
import android.os.Handler;
import android.os.test.TestLooper;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.display.persistence.TestInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LegacyPersistentDataStoreTest {
    private LegacyPersistentDataStore mDataStore;
    private TestInjector mInjector;
    private TestLooper mTestLooper;

    @Mock
    private DisplayAdapter mDisplayAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mInjector = new TestInjector();
        mTestLooper = new TestLooper();
        Handler handler = new Handler(mTestLooper.getLooper());
        mDataStore = new LegacyPersistentDataStore(mInjector, handler);
    }

    @Test
    public void getConnectionPreference_withStoredValues_returnsExpectedValues() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        final DisplayDevice testDisplayDevice2 =
                createTestDisplayDevice(mDisplayAdapter, "test:456");

        String xml = stateBuilder()
                .display("test:123", display -> display
                        .withConnectionPreference(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP))
                .display("test:456", display -> display
                        .withConnectionPreference(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_MIRROR))
                .build();

        InputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();

        int connectionPreference = mDataStore.getConnectionPreference(testDisplayDevice);
        int connectionPreference2 = mDataStore.getConnectionPreference(testDisplayDevice2);
        assertEquals(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP, connectionPreference);
        assertEquals(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_MIRROR, connectionPreference2);
    }

    @Test
    public void getConnectionPreference_whenDisplayHasUnstableId_returnsDefault() {
        final DisplayDevice unstableDisplayDevice = createTestDisplayDevice(
                mDisplayAdapter, "not:found",  /* hasStableUniqueId= */ false);

        mDataStore.loadIfNeeded();

        int preference = mDataStore.getConnectionPreference(unstableDisplayDevice);
        assertEquals("Should return default for unstable display IDs",
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_ASK, preference);
    }

    @Test
    public void getConnectionPreference_whenDisplayStateNotFound_returnsDefault() {
        final DisplayDevice unknownDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:0");

        String emptyContents = stateBuilder().build();
        mInjector.setReadStream(new ByteArrayInputStream(emptyContents.getBytes()));
        mDataStore.loadIfNeeded();

        int preference = mDataStore.getConnectionPreference(unknownDisplayDevice);
        assertEquals("Should return default for a display with no saved state",
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_ASK, preference);
    }

    @Test
    public void setConnectionPreference_newDisplay_setsPreferenceAndMarksDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:555");
        mDataStore.loadIfNeeded();

        boolean result = mDataStore.setConnectionPreference(testDisplayDevice,
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP);
        assertTrue("setConnectionPreference should return true for a new value", result);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue("A save should have been triggered because the data was dirty",
                mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector,
                new Handler(mTestLooper.getLooper()));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();

        assertEquals("The new preference should be restored after a save/load cycle",
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP,
                newDataStore.getConnectionPreference(testDisplayDevice));
    }

    @Test
    public void setConnectionPreference_updateExisting_setsPreferenceAndMarksDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:321");
        String xml = stateBuilder()
                .display("test:321", display -> display
                        .withConnectionPreference(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_MIRROR))
                .build();
        mInjector.setReadStream(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        mDataStore.loadIfNeeded();
        assertEquals(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_MIRROR,
                mDataStore.getConnectionPreference(testDisplayDevice));

        boolean result = mDataStore.setConnectionPreference(testDisplayDevice,
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP);
        assertTrue("setConnectionPreference should return true when updating the value", result);
        assertEquals("The preference should be updated from MIRROR(2) to DESKTOP(1)",
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP,
                mDataStore.getConnectionPreference(testDisplayDevice));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector,
                new Handler(mTestLooper.getLooper()));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();

        assertEquals("The updated preference should be DESKTOP(1) after a save/load cycle",
                EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP,
                newDataStore.getConnectionPreference(testDisplayDevice));
    }

    @Test
    public void setConnectionPreference_sameValue_returnsFalseAndNotDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        String xml = stateBuilder()
                .display("test:123", display -> display
                        .withConnectionPreference(EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP))
                .build();
        mInjector.setReadStream(new ByteArrayInputStream(xml.getBytes()));
        mDataStore.loadIfNeeded();

        boolean result = mDataStore.setConnectionPreference(
                testDisplayDevice, EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DESKTOP);

        assertFalse("setConnectionPreference should return false if the value is unchanged",
                result);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertFalse("A save should NOT be triggered if the data was not dirty",
                mInjector.wasWriteSuccessful());
    }

    @Test
    public void getHdrPreference_withStoredValues_returnsExpectedValues() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        final DisplayDevice testDisplayDevice2 =
                createTestDisplayDevice(mDisplayAdapter, "test:456");

        String xml =
                stateBuilder()
                        .display(
                                "test:123",
                                display ->
                                        display.withHdrPreference(
                                                HDR_PREFERENCE_HDR_ALLOWED))
                        .display(
                                "test:456",
                                display ->
                                        display.withHdrPreference(
                                                HDR_PREFERENCE_SDR_ONLY))
                        .build();

        InputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();

        int hdrPreference = mDataStore.getUserPreferredHdrMode(testDisplayDevice);
        int hdrPreference2 = mDataStore.getUserPreferredHdrMode(testDisplayDevice2);
        assertEquals(HDR_PREFERENCE_HDR_ALLOWED, hdrPreference);
        assertEquals(HDR_PREFERENCE_SDR_ONLY, hdrPreference2);
    }

    @Test
    public void getHdrPreference_whenDisplayHasUnstableId_returnsDefault() {
        final DisplayDevice unstableDisplayDevice =
                createTestDisplayDevice(
                        mDisplayAdapter, "not:found", /* hasStableUniqueId= */ false);

        mDataStore.loadIfNeeded();

        int preference = mDataStore.getUserPreferredHdrMode(unstableDisplayDevice);
        assertEquals(
                "Should return default for unstable display IDs",
                HDR_PREFERENCE_HDR_ALLOWED,
                preference);
    }

    @Test
    public void getHdrPreference_whenDisplayStateNotFound_returnsDefault() {
        final DisplayDevice unknownDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:0");

        String emptyContents = stateBuilder().build();
        mInjector.setReadStream(new ByteArrayInputStream(emptyContents.getBytes()));
        mDataStore.loadIfNeeded();

        int preference = mDataStore.getUserPreferredHdrMode(unknownDisplayDevice);
        assertEquals(
                "Should return default for a display with no saved state",
                HDR_PREFERENCE_HDR_ALLOWED,
                preference);
    }

    @Test
    public void getHdrPreference_newDisplay_setsPreferenceAndMarksDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:555");
        mDataStore.loadIfNeeded();

        boolean result =
                mDataStore.setUserPreferredHdrMode(
                        testDisplayDevice, HDR_PREFERENCE_SDR_ONLY);
        assertTrue("setHdrPreference should return true for a new value", result);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(
                "A save should have been triggered because the data was dirty",
                mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore =
                new LegacyPersistentDataStore(newInjector, new Handler(mTestLooper.getLooper()));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();

        assertEquals(
                "The new preference should be restored after a save/load cycle",
                HDR_PREFERENCE_SDR_ONLY,
                newDataStore.getUserPreferredHdrMode(testDisplayDevice));
    }

    @Test
    public void setHdrPreference_updateExisting_setsPreferenceAndMarksDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:321");
        String xml =
                stateBuilder()
                        .display(
                                "test:321",
                                display ->
                                        display.withHdrPreference(
                                                HDR_PREFERENCE_HDR_ALLOWED))
                        .build();
        mInjector.setReadStream(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        mDataStore.loadIfNeeded();
        assertEquals(
                HDR_PREFERENCE_HDR_ALLOWED,
                mDataStore.getUserPreferredHdrMode(testDisplayDevice));

        boolean result =
                mDataStore.setUserPreferredHdrMode(
                        testDisplayDevice, HDR_PREFERENCE_SDR_ONLY);
        assertTrue("setHdrPreference should return true when updating the value", result);
        assertEquals(
                "The preference should be updated from HDR_ALLOWED to SDR_ONLY",
                HDR_PREFERENCE_SDR_ONLY,
                mDataStore.getUserPreferredHdrMode(testDisplayDevice));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore =
                new LegacyPersistentDataStore(newInjector, new Handler(mTestLooper.getLooper()));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();

        assertEquals(
                "The updated preference should be SDR_ONLY after a save/load cycle",
                HDR_PREFERENCE_SDR_ONLY,
                newDataStore.getUserPreferredHdrMode(testDisplayDevice));
    }

    @Test
    public void setHdrPreference_sameValue_returnsFalseAndNotDirty() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        String xml =
                stateBuilder()
                        .display(
                                "test:123",
                                display ->
                                        display.withHdrPreference(
                                                HDR_PREFERENCE_HDR_ALLOWED))
                        .build();
        mInjector.setReadStream(new ByteArrayInputStream(xml.getBytes()));
        mDataStore.loadIfNeeded();

        boolean result =
                mDataStore.setUserPreferredHdrMode(
                        testDisplayDevice, HDR_PREFERENCE_HDR_ALLOWED);

        assertFalse("setHdrPreference should return false if the value is unchanged", result);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertFalse(
                "A save should NOT be triggered if the data was not dirty",
                mInjector.wasWriteSuccessful());
    }

    @Test
    public void testLoadBrightness() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");

        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <display-states>
                    <display unique-id="test:123">
                      <brightness-value user-serial="1">0.1</brightness-value>
                      <brightness-value user-serial="2">0.2</brightness-value>
                    </display>
                  </display-states>
                </display-manager-state>
                """;

        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();

        float brightness = mDataStore.getBrightness(testDisplayDevice, 1);
        assertEquals(0.1, brightness, 0.01);

        brightness = mDataStore.getBrightness(testDisplayDevice, 2);
        assertEquals(0.2, brightness, 0.01);
    }

    @Test
    public void testSetBrightness_brightnessTagWithNoUserId_updatesToBrightnessTagWithUserId() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");

        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <display-states>
                    <color-mode>0</color-mode>
                    <display unique-id="test:123">
                      <brightness-value>0.5</brightness-value>
                    </display>
                  </display-states>
                </display-manager-state>
                """;

        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();

        float user1Brightness = mDataStore.getBrightness(testDisplayDevice, 1 /* userSerial */);
        float user2Brightness = mDataStore.getBrightness(testDisplayDevice, 2 /* userSerial */);
        assertEquals(0.5, user1Brightness, 0.01);
        assertEquals(0.5, user2Brightness, 0.01);

        // Override the value for user 2. Default user must have been removed.
        mDataStore.setBrightness(testDisplayDevice, 0.2f, 2 /* userSerial */  /* brightness*/);

        user1Brightness = mDataStore.getBrightness(testDisplayDevice, 1 /* userSerial */);
        user2Brightness = mDataStore.getBrightness(testDisplayDevice, 2 /* userSerial */);
        assertTrue(Float.isNaN(user1Brightness));
        assertEquals(0.2f, user2Brightness, 0.01);

        // Override the value for user 1. User-specific brightness values should co-exist.
        mDataStore.setBrightness(testDisplayDevice, 0.1f, 1 /* userSerial */  /* brightness*/);
        user1Brightness = mDataStore.getBrightness(testDisplayDevice, 1 /* userSerial */);
        user2Brightness = mDataStore.getBrightness(testDisplayDevice, 2 /* userSerial */);
        assertEquals(0.1f, user1Brightness, 0.01);
        assertEquals(0.2f, user2Brightness, 0.01);

        // Validate saveIfNeeded writes user-specific brightnes.
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();

        user1Brightness = newDataStore.getBrightness(testDisplayDevice, 1 /* userSerial */);
        user2Brightness = newDataStore.getBrightness(testDisplayDevice, 2 /* userSerial */);
        float unknownUserBrightness =
                newDataStore.getBrightness(testDisplayDevice, 999 /* userSerial */);
        assertEquals(0.1f, user1Brightness, 0.01);
        assertEquals(0.2f, user2Brightness, 0.01);
        assertTrue(Float.isNaN(unknownUserBrightness));
    }

    @Test
    public void testLoadingBrightnessConfigurations() {
        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <brightness-configurations>
                    <brightness-configuration\
                         user-serial="1"\
                         package-name="example.com"\
                         timestamp="123456">
                      <brightness-curve description="something">
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="25" nits="35.94"/>
                      </brightness-curve>
                    </brightness-configuration>
                    <brightness-configuration user-serial="3">
                      <brightness-curve>
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="10.2" nits="15"/>
                      </brightness-curve>
                    </brightness-configuration>
                  </brightness-configurations>
                </display-manager-state>
                """;
        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();
        BrightnessConfiguration config = mDataStore.getBrightnessConfiguration(1 /*userSerial*/);
        Pair<float[], float[]> curve = config.getCurve();
        float[] expectedLux = { 0f, 25f };
        float[] expectedNits = { 13.25f, 35.94f };
        assertArrayEquals(expectedLux, curve.first, "lux");
        assertArrayEquals(expectedNits, curve.second, "nits");
        assertEquals("something", config.getDescription());

        config = mDataStore.getBrightnessConfiguration(3 /*userSerial*/);
        curve = config.getCurve();
        expectedLux = new float[] { 0f, 10.2f };
        expectedNits = new float[] { 13.25f, 15f };
        assertArrayEquals(expectedLux, curve.first, "lux");
        assertArrayEquals(expectedNits, curve.second, "nits");
        assertNull(config.getDescription());
    }

    @Test
    public void testBrightnessConfigWithInvalidCurveIsIgnored() {
        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <brightness-configurations>
                    <brightness-configuration user-serial="0">
                      <brightness-curve>
                        <brightness-point lux="1" nits="13.25"/>
                        <brightness-point lux="25" nits="35.94"/>
                      </brightness-curve>
                    </brightness-configuration>
                  </brightness-configurations>
                </display-manager-state>
                """;
        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfiguration(0 /*userSerial*/));
    }

    @Test
    public void testBrightnessConfigWithInvalidFloatsIsIgnored() {
        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <brightness-configurations>
                    <brightness-configuration user-serial="0">
                      <brightness-curve>
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="0xFF" nits="foo"/>
                      </brightness-curve>
                    </brightness-configuration>
                  </brightness-configurations>
                </display-manager-state>
                """;
        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfiguration(0 /*userSerial*/));
    }

    @Test
    public void testEmptyBrightnessConfigurationsDoesNotCrash() {
        String contents = """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <display-manager-state>
                  <brightness-configurations />
                </display-manager-state>
                """;
        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfiguration(0 /*userSerial*/));
    }

    @Test
    public void testStoreAndReloadOfDisplayBrightnessConfigurations() {
        final String uniqueDisplayId = "test:123";
        int userSerial = 0;
        String packageName = "pdsTestPackage";
        final float[] lux = { 0f, 10f };
        final float[] nits = {1f, 100f };
        final BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits)
                .setDescription("a description")
                .build();
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfigurationForDisplayLocked(uniqueDisplayId,
                userSerial));

        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, uniqueDisplayId);

        mDataStore.setBrightnessConfigurationForDisplayLocked(config, testDisplayDevice, userSerial,
                packageName);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        assertNotNull(newDataStore.getBrightnessConfigurationForDisplayLocked(uniqueDisplayId,
                userSerial));
        assertEquals(mDataStore.getBrightnessConfigurationForDisplayLocked(uniqueDisplayId,
                userSerial), newDataStore.getBrightnessConfigurationForDisplayLocked(
                uniqueDisplayId, userSerial));
    }

    @Test
    public void testSetBrightnessConfigurationFailsWithUnstableId() {
        final String uniqueDisplayId = "test:123";
        int userSerial = 0;
        String packageName = "pdsTestPackage";
        final float[] lux = { 0f, 10f };
        final float[] nits = {1f, 100f };
        final BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits)
                .setDescription("a description")
                .build();
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfigurationForDisplayLocked(uniqueDisplayId,
                userSerial));

        final DisplayDevice testDisplayDevice = createTestDisplayDevice(
                mDisplayAdapter, uniqueDisplayId, /* hasStableUniqueId= */ false);

        assertFalse(mDataStore.setBrightnessConfigurationForDisplayLocked(
                config, testDisplayDevice, userSerial, packageName));
    }

    @Test
    public void testStoreAndReloadOfBrightnessConfigurations() {
        final float[] lux = { 0f, 10f };
        final float[] nits = {1f, 100f };
        final BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits)
                .setDescription("a description")
                .build();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String packageName = context.getPackageName();

        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfiguration(0 /*userSerial*/));
        mDataStore.setBrightnessConfigurationForUser(config, 0, packageName);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        assertNotNull(newDataStore.getBrightnessConfiguration(0 /*userSerial*/));
        assertEquals(mDataStore.getBrightnessConfiguration(0 /*userSerial*/),
                newDataStore.getBrightnessConfiguration(0 /*userSerial*/));
    }

    @Test
    public void testNullBrightnessConfiguration() {
        final float[] lux = { 0f, 10f };
        final float[] nits = {1f, 100f };
        int userSerial = 0;
        final BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits)
                .setDescription("a description")
                .build();
        mDataStore.loadIfNeeded();
        assertNull(mDataStore.getBrightnessConfiguration(userSerial));

        mDataStore.setBrightnessConfigurationForUser(config, userSerial, "packageName");
        assertNotNull(mDataStore.getBrightnessConfiguration(userSerial));

        mDataStore.setBrightnessConfigurationForUser(null, userSerial, "packageName");
        assertNull(mDataStore.getBrightnessConfiguration(userSerial));
    }

    @Test
    public void testStoreAndRestoreResolution() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        int width = 35;
        int height = 45;
        mDataStore.loadIfNeeded();
        mDataStore.setUserPreferredResolution(testDisplayDevice, width, height);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        Point resolution = mDataStore.getUserPreferredResolution(testDisplayDevice);
        Point newResolution = newDataStore.getUserPreferredResolution(testDisplayDevice);
        assertNotNull(resolution);
        assertNotNull(newResolution);
        assertEquals(35, newResolution.x);
        assertEquals(35, resolution.x);
        assertEquals(45, newResolution.y);
        assertEquals(45, resolution.y);
    }

    @Test
    public void testStoreAndRestoreRefreshRate() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");
        float refreshRate = 85.3f;
        mDataStore.loadIfNeeded();
        mDataStore.setUserPreferredRefreshRate(testDisplayDevice, refreshRate);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        assertEquals(85.3f, mDataStore.getUserPreferredRefreshRate(testDisplayDevice), 01.f);
        assertEquals(85.3f, newDataStore.getUserPreferredRefreshRate(testDisplayDevice), 0.1f);
    }

    @Test
    public void testBrightnessInitialisesWithInvalidFloat() {
        final DisplayDevice testDisplayDevice =
                createTestDisplayDevice(mDisplayAdapter, "test:123");

        // Set any value which initializes Display state
        float refreshRate = 85.3f;
        mDataStore.loadIfNeeded();
        mDataStore.setUserPreferredRefreshRate(testDisplayDevice, refreshRate);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        assertTrue(Float.isNaN(mDataStore.getBrightness(testDisplayDevice, 1 /* userSerial */)));
    }

    @Test
    public void testStoreAndRestoreBrightnessNitsForDefaultDisplay() {
        float brightnessNitsForDefaultDisplay = 190;
        mDataStore.loadIfNeeded();
        mDataStore.setBrightnessNitsForDefaultDisplay(brightnessNitsForDefaultDisplay);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        mTestLooper.dispatchAll();
        assertTrue(mInjector.wasWriteSuccessful());
        TestInjector newInjector = new TestInjector();
        LegacyPersistentDataStore newDataStore = new LegacyPersistentDataStore(newInjector);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        newDataStore.loadIfNeeded();
        assertEquals(brightnessNitsForDefaultDisplay,
                mDataStore.getBrightnessNitsForDefaultDisplay(), 0);
        assertEquals(brightnessNitsForDefaultDisplay,
                newDataStore.getBrightnessNitsForDefaultDisplay(), 0);
    }

    @Test
    public void testInitialBrightnessNitsForDefaultDisplay() {
        mDataStore.loadIfNeeded();
        assertEquals(-1, mDataStore.getBrightnessNitsForDefaultDisplay(), 0);
    }

    @Test
    public void testUserRemoval() {
        final float[] lux = { 0f, 10f };
        final float[] nits = {1f, 100f };
        int userSerial = 0;
        final BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits)
                .setDescription("description")
                .build();
        mDataStore.loadIfNeeded();
        mDataStore.setBrightnessConfigurationForUser(config, userSerial, "packageName");
        assertNotNull(mDataStore.getBrightnessConfiguration(userSerial));

        mDataStore.removeUserData(userSerial);
        assertNull(mDataStore.getBrightnessConfiguration(userSerial));
    }

    private static void assertArrayEquals(float[] expected, float[] actual, String name) {
        assertEquals("Expected " + name + " arrays to be the same length!",
                expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Expected " + name + " arrays to be equivalent when value " + i
                    + "differs", expected[i], actual[i], 0.01 /*tolerance*/);
        }
    }
}
