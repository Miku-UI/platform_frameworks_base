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

package com.android.server.display.persistence;

import static com.android.server.display.persistence.DisplayState.BRIGHTNESS_CONFIGURATION_KEY;
import static com.android.server.display.persistence.DisplayState.BRIGHTNESS_KEY;
import static com.android.server.display.persistence.DisplayState.COLOR_MODE_KEY;
import static com.android.server.display.persistence.DisplayState.CONNECTION_PREFERENCE_KEY;
import static com.android.server.display.persistence.DisplayState.DISPLAY_MODE_KEY;
import static com.android.server.display.persistence.DisplayState.HDR_PREFERENCE_KEY;
import static com.android.server.display.persistence.PersistentDataStore.BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY;
import static com.android.server.display.persistence.PersistentDataStore.Key;
import static com.android.server.display.persistence.PersistentDataStore.REMEMBERED_WIFI_DISPLAYS_KEY;
import static com.android.server.display.persistence.PersistentDataStore.STABLE_DEVICE_VALUES_KEY;
import static com.android.server.display.persistence.PersistentDataStoreTestUtils.createTestDisplayDevice;
import static com.android.server.testutils.TestUtils.flushLoopers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.graphics.Point;
import android.hardware.display.BrightnessCorrection;
import android.hardware.display.WifiDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.TestLooperManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.display.DisplayAdapter;
import com.android.server.display.DisplayDevice;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PersistentDataStoreTest {
    private static final int[] USER_SERIALS = new int[]{123, 234};
    private static final String[] DISPLAY_UNIQUE_IDS = new String[]{"test:12345", "test:678"};

    private static final String XML_CONTENTS = String.format("""
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <display-manager-state version="2">

                <remembered-wifi-displays>
                  <wifi-display deviceAddress="01:20:30:06:88:10" deviceName="someName1"
                      deviceAlias="someAlias1" />
                  <wifi-display deviceAddress="08:02:00:10:00:22" deviceName="someName2"
                      deviceAlias="someAlias2" />
                </remembered-wifi-displays>
                <stable-device-values>
                  <stable-display-width>1920</stable-display-width>
                  <stable-display-height>1080</stable-display-height>
                </stable-device-values>
                <brightness-nits-for-default-display>600</brightness-nits-for-default-display>

                <user-states>
                  <user user-serial="%d">
                    <brightness-configuration package-name="com.example" timestamp="1234">
                      <brightness-curve description="some text">
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="20" nits="35.94"/>
                      </brightness-curve>
                      <brightness-corrections>
                        <brightness-correction package-name="com.correction">
                          <scale-and-translate-log scale="0.2" translate="0.3" />
                        </brightness-correction>
                        <brightness-correction package-name="com.correction2">
                          <scale-and-translate-log scale="0.1" translate="0.2" />
                        </brightness-correction>
                        <brightness-correction category="2">
                          <scale-and-translate-log scale="0.2" translate="0.3" />
                        </brightness-correction>
                      </brightness-corrections>
                      <brightness-params collect-color="true" model-timeout="2000"
                          model-lower-bound="0.2" model-upper-bound="0.8" />
                    </brightness-configuration>

                    <display-states>
                      <display unique-id="%s">
                        <color-mode>2</color-mode>
                        <brightness>0.23</brightness>
                        <brightness-configuration package-name="com.example" timestamp="1234">
                          <brightness-curve description="some text">
                            <brightness-point lux="0" nits="13.25"/>
                            <brightness-point lux="20" nits="35.94"/>
                          </brightness-curve>
                        </brightness-configuration>
                        <display-mode>
                          <resolution-width>1000</resolution-width>
                          <resolution-height>500</resolution-height>
                          <refresh-rate>70</refresh-rate>
                        </display-mode>
                        <connection-preference>1</connection-preference>
                        <hdr-preference>2</hdr-preference>
                      </display>

                      <display unique-id="%s">
                        <color-mode>5</color-mode>
                        <brightness>0.73</brightness>
                        <brightness-configuration package-name="com.example2" timestamp="809">
                          <brightness-curve description="some text 2">
                            <brightness-point lux="0" nits="33.25"/>
                            <brightness-point lux="30" nits="55.94"/>
                            <brightness-point lux="50" nits="85.94"/>
                          </brightness-curve>
                          <brightness-corrections>
                            <brightness-correction package-name="com.correction.display">
                              <scale-and-translate-log scale="0.25" translate="0.35" />
                            </brightness-correction>
                            <brightness-correction package-name="com.correction.display2">
                              <scale-and-translate-log scale="0.15" translate="0.25" />
                            </brightness-correction>
                            <brightness-correction category="3">
                              <scale-and-translate-log scale="0.25" translate="0.35" />
                            </brightness-correction>
                          </brightness-corrections>
                          <brightness-params collect-color="true" model-timeout="5000"
                              model-lower-bound="0.25" model-upper-bound="0.85" />
                        </brightness-configuration>
                        <display-mode>
                          <resolution-width>1200</resolution-width>
                          <resolution-height>800</resolution-height>
                          <refresh-rate>80</refresh-rate>
                        </display-mode>
                        <connection-preference>2</connection-preference>
                        <hdr-preference>1</hdr-preference>
                      </display>
                    </display-states>
                  </user>

                  <user user-serial="%d">
                    <brightness-configuration package-name="com.example" timestamp="1234">
                      <brightness-curve description="some text">
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="20" nits="35.94"/>
                      </brightness-curve>
                    </brightness-configuration>

                    <display-states>
                      <display unique-id="%s">
                        <color-mode>2</color-mode>
                        <brightness>0.25</brightness>
                        <brightness-configuration package-name="com.example" timestamp="1234">
                          <brightness-curve description="some text">
                            <brightness-point lux="0" nits="13.25"/>
                            <brightness-point lux="20" nits="35.94"/>
                          </brightness-curve>
                        </brightness-configuration>
                        <display-mode>
                          <resolution-width>1200</resolution-width>
                          <resolution-height>600</resolution-height>
                          <refresh-rate>100</refresh-rate>
                        </display-mode>
                        <connection-preference>4</connection-preference>
                        <hdr-preference>0</hdr-preference>
                      </display>

                      <display unique-id="%s">
                        <color-mode>5</color-mode>
                        <brightness>0.75</brightness>
                        <connection-preference>2</connection-preference>
                        <hdr-preference>1</hdr-preference>
                      </display>
                    </display-states>
                  </user>
                </user-states>
              </display-manager-state>
            """,
            USER_SERIALS[0], DISPLAY_UNIQUE_IDS[0], DISPLAY_UNIQUE_IDS[1],
            USER_SERIALS[1], DISPLAY_UNIQUE_IDS[0], DISPLAY_UNIQUE_IDS[1]);

    private static final String XML_CONTENTS_BAD_NUMBER_FORMAT = String.format("""
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <display-manager-state version="2">

                <stable-device-values>
                  <stable-display-width>bad</stable-display-width>
                  <stable-display-height>bad</stable-display-height>
                </stable-device-values>
                <brightness-nits-for-default-display>bad</brightness-nits-for-default-display>

                <user-states>
                  <user user-serial="%d">
                    <display-states>
                      <display unique-id="%s">
                        <color-mode>fff</color-mode>
                        <brightness>ggg</brightness>
                        <display-mode>
                          <resolution-width>width</resolution-width>
                          <resolution-height>height</resolution-height>
                          <refresh-rate>rr</refresh-rate>
                        </display-mode>
                        <connection-preference>aaa</connection-preference>
                        <hdr-preference>bbb</hdr-preference>
                      </display>
                    </display-states>
                  </user>
                </user-states>
              </display-manager-state>
            """,
            USER_SERIALS[0], DISPLAY_UNIQUE_IDS[0]);

    private static final String XML_CONTENTS_ID_MISSING = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <display-manager-state version="2">

                <remembered-wifi-displays>
                  <wifi-display deviceName="someName1"
                      deviceAlias="someAlias1" />
                </remembered-wifi-displays>

                <user-states>
                  <user>
                    <display-states>
                      <display>
                        <color-mode>2</color-mode>
                        <brightness>0.23</brightness>
                        <brightness-configuration package-name="com.example" timestamp="1234">
                          <brightness-curve description="some text">
                            <brightness-point lux="0" nits="13.25"/>
                            <brightness-point lux="20" nits="35.94"/>
                          </brightness-curve>
                        </brightness-configuration>
                        <display-mode>
                          <resolution-width>1000</resolution-width>
                          <resolution-height>500</resolution-height>
                          <refresh-rate>70</refresh-rate>
                        </display-mode>
                        <connection-preference>1</connection-preference>
                        <hdr-preference>2</hdr-preference>
                      </display>
                    </display-states>
                  </user>
                </user-states>
              </display-manager-state>
            """;

    private static final String XML_CONTENTS_UNRECOGNIZED_KEY = String.format("""
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <display-manager-state version="2">

                <unrecognized-key />

                <user-states>
                  <user user-serial="%d">
                    <unrecognized-key />

                    <display-states>
                      <display unique-id="%s">
                        <unrecognized-key />
                      </display>
                    </display-states>
                  </user>
                </user-states>
              </display-manager-state>
            """,
            USER_SERIALS[0], DISPLAY_UNIQUE_IDS[0]);

    private static final String XML_CONTENTS_DUPLICATE_KEY = String.format("""
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <display-manager-state version="2">

                <remembered-wifi-displays>
                  <wifi-display deviceAddress="01:20:30:06:88:10" deviceName="someName1"
                      deviceAlias="someAlias1" />
                  <wifi-display deviceAddress="01:20:30:06:88:10" deviceName="someName2"
                      deviceAlias="someAlias2" />
                </remembered-wifi-displays>

                <user-states>
                  <user user-serial="%d">
                    <display-states>
                      <display unique-id="%s">
                        <color-mode>2</color-mode>
                        <color-mode>3</color-mode>
                      </display>

                      <display unique-id="%s">
                        <hdr-preference>1</hdr-preference>
                      </display>
                    </display-states>
                    <display-states />
                  </user>

                  <user user-serial="%d">
                    <brightness-configuration package-name="com.example" timestamp="1234">
                      <brightness-curve description="some text">
                        <brightness-point lux="0" nits="13.25"/>
                        <brightness-point lux="20" nits="35.94"/>
                      </brightness-curve>
                    </brightness-configuration>
                  </user>
                </user-states>
                <user-states />
              </display-manager-state>
            """,
            USER_SERIALS[0], DISPLAY_UNIQUE_IDS[0], DISPLAY_UNIQUE_IDS[0],
            USER_SERIALS[0]);

    private static final String XML_CONTENTS_MAIN_TAG_MISSING = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
              <something />
            """;

    // New data to set

    private static Map<Key<?>, Object> getGlobalData() {
        Map<String, WifiDisplay> wifiDisplays = ImmutableMap.of("newAddress",
                new WifiDisplay("newAddress", "newDeviceName", "newAlias", /* available= */ true,
                        /* canConnect= */ true, /* remembered= */ true));
        return ImmutableMap.of(
                REMEMBERED_WIFI_DISPLAYS_KEY, wifiDisplays,
                STABLE_DEVICE_VALUES_KEY, new StableDeviceValues(new Point(650, 300)),
                BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY, 123f);
    }

    private static Map<Integer, Map<Key<?>, Object>> getUserData() {
        android.hardware.display.BrightnessConfiguration.Builder brightnessConfigBuilder =
                new android.hardware.display.BrightnessConfiguration.Builder(new float[]{0, 20},
                        new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text912");
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction932",
                BrightnessCorrection.createScaleAndTranslateLog(0.28f, 0.3f));
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction22",
                BrightnessCorrection.createScaleAndTranslateLog(0.14f, 0.2f));
        brightnessConfigBuilder.addCorrectionByCategory(2,
                BrightnessCorrection.createScaleAndTranslateLog(0.3f, 0.3f));
        brightnessConfigBuilder.setShouldCollectColorSamples(true);
        brightnessConfigBuilder.setShortTermModelTimeoutMillis(5000);
        brightnessConfigBuilder.setShortTermModelLowerLuxMultiplier(0.2f);
        brightnessConfigBuilder.setShortTermModelUpperLuxMultiplier(0.8f);
        BrightnessConfiguration brightnessConfig = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 5678, "com.example21");

        return ImmutableMap.of(
                USER_SERIALS[0], ImmutableMap.of(BRIGHTNESS_CONFIGURATION_KEY, brightnessConfig),
                USER_SERIALS[1], ImmutableMap.of());
    }

    private static Map<Integer, Map<String, Map<Key<?>, Object>>> getDisplayData() {
        android.hardware.display.BrightnessConfiguration.Builder brightnessConfigBuilder =
                new android.hardware.display.BrightnessConfiguration.Builder(new float[]{0, 20},
                        new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text9");
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction9",
                BrightnessCorrection.createScaleAndTranslateLog(0.2f, 0.3f));
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction2",
                BrightnessCorrection.createScaleAndTranslateLog(0.1f, 0.2f));
        brightnessConfigBuilder.addCorrectionByCategory(2,
                BrightnessCorrection.createScaleAndTranslateLog(0.2f, 0.3f));
        brightnessConfigBuilder.setShouldCollectColorSamples(true);
        brightnessConfigBuilder.setShortTermModelTimeoutMillis(2000);
        brightnessConfigBuilder.setShortTermModelLowerLuxMultiplier(0.2f);
        brightnessConfigBuilder.setShortTermModelUpperLuxMultiplier(0.8f);
        BrightnessConfiguration brightnessConfig1 = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 1234, "com.example2");

        brightnessConfigBuilder = new android.hardware.display.BrightnessConfiguration.Builder(
                new float[]{0, 20}, new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text4");
        BrightnessConfiguration brightnessConfig2 = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 1234, "com.example3");

        return ImmutableMap.of(
                USER_SERIALS[0], ImmutableMap.of(
                        DISPLAY_UNIQUE_IDS[0], ImmutableMap.of(
                                COLOR_MODE_KEY, 7,
                                BRIGHTNESS_KEY, 0.29f,
                                BRIGHTNESS_CONFIGURATION_KEY, brightnessConfig2,
                                DISPLAY_MODE_KEY, new DisplayMode(/* width= */ 1200,
                                        /* height= */ 800, /* refreshRate= */ 80),
                                CONNECTION_PREFERENCE_KEY, 4,
                                HDR_PREFERENCE_KEY, 0),
                        DISPLAY_UNIQUE_IDS[1], ImmutableMap.of(
                                COLOR_MODE_KEY, 1,
                                BRIGHTNESS_KEY, 0f)),
                USER_SERIALS[1], ImmutableMap.of(
                        DISPLAY_UNIQUE_IDS[0], ImmutableMap.of(
                                BRIGHTNESS_CONFIGURATION_KEY, brightnessConfig1,
                                CONNECTION_PREFERENCE_KEY, 2,
                                HDR_PREFERENCE_KEY, 4),
                        DISPLAY_UNIQUE_IDS[1], ImmutableMap.of(
                                COLOR_MODE_KEY, 4,
                                BRIGHTNESS_KEY, 1f,
                                BRIGHTNESS_CONFIGURATION_KEY, brightnessConfig1)));
    }

    private PersistentDataStore mDataStore;
    private final TestInjector mInjector = new TestInjector();
    private final Looper mLooper = Looper.getMainLooper();
    private TestLooperManager mLooperManager;
    // Map from display unique ID to display device
    private final Map<String, DisplayDevice> mDisplayDevices = new HashMap<>();
    private AutoCloseable mMocksCloseable;

    @Mock
    private DisplayAdapter mDisplayAdapter;

    @Before
    public void setUp() {
        mMocksCloseable = MockitoAnnotations.openMocks(this);
        mLooperManager = InstrumentationRegistry.getInstrumentation().acquireLooperManager(mLooper);
        Handler handler = new Handler(mLooper);
        mDataStore = new PersistentDataStore(mInjector, handler);

        mDisplayDevices.put(DISPLAY_UNIQUE_IDS[0],
                createTestDisplayDevice(mDisplayAdapter, DISPLAY_UNIQUE_IDS[0]));
        mDisplayDevices.put(DISPLAY_UNIQUE_IDS[1],
                createTestDisplayDevice(mDisplayAdapter, DISPLAY_UNIQUE_IDS[1]));
    }

    @After
    public void tearDown() throws Exception {
        flushLoopers(mLooperManager);
        mLooperManager.release();
        mMocksCloseable.close();
    }

    @Test
    public void testParseGlobalProperties() {
        loadStore();

        Map<String, WifiDisplay> rememberedWifiDisplays = mDataStore.getGlobalProperty(
                REMEMBERED_WIFI_DISPLAYS_KEY);
        assertNotNull(rememberedWifiDisplays);
        assertEquals(2, rememberedWifiDisplays.size());
        String wifiDisplayAddress1 = "01:20:30:06:88:10";
        WifiDisplay wifiDisplay1 = rememberedWifiDisplays.get(wifiDisplayAddress1);
        assertNotNull(wifiDisplay1);
        assertEquals(wifiDisplayAddress1, wifiDisplay1.getDeviceAddress());
        assertEquals("someName1", wifiDisplay1.getDeviceName());
        assertEquals("someAlias1", wifiDisplay1.getDeviceAlias());
        String wifiDisplayAddress2 = "08:02:00:10:00:22";
        WifiDisplay wifiDisplay2 = rememberedWifiDisplays.get(wifiDisplayAddress2);
        assertNotNull(wifiDisplay2);
        assertEquals(wifiDisplayAddress2, wifiDisplay2.getDeviceAddress());
        assertEquals("someName2", wifiDisplay2.getDeviceName());
        assertEquals("someAlias2", wifiDisplay2.getDeviceAlias());

        StableDeviceValues sdv = mDataStore.getGlobalProperty(STABLE_DEVICE_VALUES_KEY);
        assertNotNull(sdv);
        assertEquals(1920, sdv.getDisplaySize().x);
        assertEquals(1080, sdv.getDisplaySize().y);

        assertEquals(Float.valueOf(600f), mDataStore.getGlobalProperty(
                BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY));
    }

    @Test
    public void testParseUserProperties() {
        loadStore();

        // User 0
        android.hardware.display.BrightnessConfiguration.Builder brightnessConfigBuilder =
                new android.hardware.display.BrightnessConfiguration.Builder(new float[]{0, 20},
                        new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text");
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction",
                BrightnessCorrection.createScaleAndTranslateLog(0.2f, 0.3f));
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction2",
                BrightnessCorrection.createScaleAndTranslateLog(0.1f, 0.2f));
        brightnessConfigBuilder.addCorrectionByCategory(2,
                BrightnessCorrection.createScaleAndTranslateLog(0.2f, 0.3f));
        brightnessConfigBuilder.setShouldCollectColorSamples(true);
        brightnessConfigBuilder.setShortTermModelTimeoutMillis(2000);
        brightnessConfigBuilder.setShortTermModelLowerLuxMultiplier(0.2f);
        brightnessConfigBuilder.setShortTermModelUpperLuxMultiplier(0.8f);
        BrightnessConfiguration expectedBrightnessConfig = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 1234, "com.example");
        assertEquals(expectedBrightnessConfig, mDataStore.getUserProperty(USER_SERIALS[0],
                BRIGHTNESS_CONFIGURATION_KEY));

        // User 1
        brightnessConfigBuilder = new android.hardware.display.BrightnessConfiguration.Builder(
                new float[]{0, 20}, new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text");
        expectedBrightnessConfig = new BrightnessConfiguration(brightnessConfigBuilder.build(),
                /* timestamp= */ 1234, "com.example");
        assertEquals(expectedBrightnessConfig,
                mDataStore.getUserProperty(USER_SERIALS[1], BRIGHTNESS_CONFIGURATION_KEY));
    }

    @Test
    public void testParseDisplayProperties() {
        loadStore();

        // User 0, display 0
        int userSerial = USER_SERIALS[0];
        DisplayDevice displayDevice = mDisplayDevices.get(DISPLAY_UNIQUE_IDS[0]);

        assertEquals(Integer.valueOf(2),
                mDataStore.getDisplayProperty(userSerial, displayDevice, COLOR_MODE_KEY));
        assertEquals(Float.valueOf(0.23f),
                mDataStore.getDisplayProperty(userSerial, displayDevice, BRIGHTNESS_KEY));
        assertEquals(Integer.valueOf(1), mDataStore.getDisplayProperty(userSerial, displayDevice,
                CONNECTION_PREFERENCE_KEY));
        assertEquals(Integer.valueOf(2),
                mDataStore.getDisplayProperty(userSerial, displayDevice, HDR_PREFERENCE_KEY));

        android.hardware.display.BrightnessConfiguration.Builder brightnessConfigBuilder =
                new android.hardware.display.BrightnessConfiguration.Builder(new float[]{0, 20},
                        new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text");
        BrightnessConfiguration expectedBrightnessConfig = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 1234, "com.example");
        assertEquals(expectedBrightnessConfig,
                mDataStore.getDisplayProperty(userSerial, displayDevice,
                        BRIGHTNESS_CONFIGURATION_KEY));

        DisplayMode displayMode = mDataStore.getDisplayProperty(userSerial, displayDevice,
                DISPLAY_MODE_KEY);
        assertNotNull(displayMode);
        assertEquals(1000, displayMode.getResolution().x);
        assertEquals(500, displayMode.getResolution().y);
        assertEquals(70, displayMode.getRefreshRate(), /* delta= */ 0);

        // User 0, display 1
        displayDevice = mDisplayDevices.get(DISPLAY_UNIQUE_IDS[1]);

        assertEquals(Integer.valueOf(5),
                mDataStore.getDisplayProperty(userSerial, displayDevice, COLOR_MODE_KEY));
        assertEquals(Float.valueOf(0.73f),
                mDataStore.getDisplayProperty(userSerial, displayDevice, BRIGHTNESS_KEY));
        assertEquals(Integer.valueOf(2), mDataStore.getDisplayProperty(userSerial, displayDevice,
                CONNECTION_PREFERENCE_KEY));
        assertEquals(Integer.valueOf(1),
                mDataStore.getDisplayProperty(userSerial, displayDevice, HDR_PREFERENCE_KEY));

        brightnessConfigBuilder = new android.hardware.display.BrightnessConfiguration.Builder(
                new float[]{0, 30, 50}, new float[]{33.25f, 55.94f, 85.94f});
        brightnessConfigBuilder.setDescription("some text 2");
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction.display",
                BrightnessCorrection.createScaleAndTranslateLog(0.25f, 0.35f));
        brightnessConfigBuilder.addCorrectionByPackageName("com.correction.display2",
                BrightnessCorrection.createScaleAndTranslateLog(0.15f, 0.25f));
        brightnessConfigBuilder.addCorrectionByCategory(3,
                BrightnessCorrection.createScaleAndTranslateLog(0.25f, 0.35f));
        brightnessConfigBuilder.setShouldCollectColorSamples(true);
        brightnessConfigBuilder.setShortTermModelTimeoutMillis(5000);
        brightnessConfigBuilder.setShortTermModelLowerLuxMultiplier(0.25f);
        brightnessConfigBuilder.setShortTermModelUpperLuxMultiplier(0.85f);
        expectedBrightnessConfig = new BrightnessConfiguration(
                brightnessConfigBuilder.build(), /* timestamp= */ 809, "com.example2");
        assertEquals(expectedBrightnessConfig,
                mDataStore.getDisplayProperty(userSerial, displayDevice,
                        BRIGHTNESS_CONFIGURATION_KEY));

        displayMode = mDataStore.getDisplayProperty(userSerial, displayDevice, DISPLAY_MODE_KEY);
        assertNotNull(displayMode);
        assertEquals(1200, displayMode.getResolution().x);
        assertEquals(800, displayMode.getResolution().y);
        assertEquals(80, displayMode.getRefreshRate(), /* delta= */ 0);

        // User 1, display 0
        userSerial = USER_SERIALS[1];
        displayDevice = mDisplayDevices.get(DISPLAY_UNIQUE_IDS[0]);

        assertEquals(Integer.valueOf(2),
                mDataStore.getDisplayProperty(userSerial, displayDevice, COLOR_MODE_KEY));
        assertEquals(Float.valueOf(0.25f),
                mDataStore.getDisplayProperty(userSerial, displayDevice, BRIGHTNESS_KEY));
        assertEquals(Integer.valueOf(4), mDataStore.getDisplayProperty(userSerial, displayDevice,
                CONNECTION_PREFERENCE_KEY));
        assertEquals(Integer.valueOf(0),
                mDataStore.getDisplayProperty(userSerial, displayDevice, HDR_PREFERENCE_KEY));

        brightnessConfigBuilder = new android.hardware.display.BrightnessConfiguration.Builder(
                new float[]{0, 20}, new float[]{13.25f, 35.94f});
        brightnessConfigBuilder.setDescription("some text");
        expectedBrightnessConfig = new BrightnessConfiguration(brightnessConfigBuilder.build(),
                /* timestamp= */ 1234, "com.example");
        assertEquals(expectedBrightnessConfig,
                mDataStore.getDisplayProperty(userSerial, displayDevice,
                        BRIGHTNESS_CONFIGURATION_KEY));

        displayMode = mDataStore.getDisplayProperty(userSerial, displayDevice, DISPLAY_MODE_KEY);
        assertNotNull(displayMode);
        assertEquals(1200, displayMode.getResolution().x);
        assertEquals(600, displayMode.getResolution().y);
        assertEquals(100, displayMode.getRefreshRate(), /* delta= */ 0);

        // User 1, display 1
        displayDevice = mDisplayDevices.get(DISPLAY_UNIQUE_IDS[1]);

        assertEquals(Integer.valueOf(5),
                mDataStore.getDisplayProperty(userSerial, displayDevice, COLOR_MODE_KEY));
        assertEquals(Float.valueOf(0.75f),
                mDataStore.getDisplayProperty(userSerial, displayDevice, BRIGHTNESS_KEY));
        assertEquals(Integer.valueOf(2), mDataStore.getDisplayProperty(userSerial, displayDevice,
                CONNECTION_PREFERENCE_KEY));
        assertEquals(Integer.valueOf(1),
                mDataStore.getDisplayProperty(userSerial, displayDevice, HDR_PREFERENCE_KEY));
        assertNull(mDataStore.getDisplayProperty(userSerial, displayDevice,
                BRIGHTNESS_CONFIGURATION_KEY));
        assertNull(mDataStore.getDisplayProperty(userSerial, displayDevice, DISPLAY_MODE_KEY));
    }

    @Test
    public void testUpdateEmptyStore() {
        verifyStoreEmpty();
        testSaveAndRestore();
    }

    @Test
    public void testUpdateExistingStore() {
        loadStore();
        testSaveAndRestore();
    }

    @Test
    public void testAddToAndRemoveFromGlobalPropertyMap() {
        Map<String, WifiDisplay> wifiDisplays =
                (Map<String, WifiDisplay>) getGlobalData().get(REMEMBERED_WIFI_DISPLAYS_KEY);

        // Add to map
        for (Map.Entry<String, WifiDisplay> entry : wifiDisplays.entrySet()) {
            boolean result = mDataStore.addToGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY,
                    entry.getKey(), entry.getValue());
            assertTrue(
                    "Adding to map should return true: " + REMEMBERED_WIFI_DISPLAYS_KEY.mName + " "
                            + entry.getKey() + " " + entry.getValue(), result);
        }
        Map<String, WifiDisplay> actualWifiDisplays = mDataStore.get(REMEMBERED_WIFI_DISPLAYS_KEY);
        assertNotNull(actualWifiDisplays);
        assertEquals(wifiDisplays.size(), actualWifiDisplays.size());
        for (Map.Entry<String, WifiDisplay> entry : wifiDisplays.entrySet()) {
            assertEquals(entry.getValue(), actualWifiDisplays.get(entry.getKey()));
        }

        // Add to map again
        for (Map.Entry<String, WifiDisplay> entry : wifiDisplays.entrySet()) {
            boolean result = mDataStore.addToGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY,
                    entry.getKey(), entry.getValue());
            assertFalse("Adding the same value to map should return false: "
                    + REMEMBERED_WIFI_DISPLAYS_KEY.mName + " " + entry.getKey() + " "
                    + entry.getValue(), result);
        }

        // Remove from map
        for (Map.Entry<String, WifiDisplay> entry : wifiDisplays.entrySet()) {
            boolean result = mDataStore.removeFromGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY,
                    entry.getKey());
            assertTrue("Removing from map should return true: " + REMEMBERED_WIFI_DISPLAYS_KEY.mName
                    + " " + entry.getKey() + " " + entry.getValue(), result);
        }
        actualWifiDisplays = mDataStore.get(REMEMBERED_WIFI_DISPLAYS_KEY);
        assertTrue(actualWifiDisplays == null || actualWifiDisplays.isEmpty());

        // Remove from map again
        for (Map.Entry<String, WifiDisplay> entry : wifiDisplays.entrySet()) {
            boolean result = mDataStore.removeFromGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY,
                    entry.getKey());
            assertFalse("Removing an absent value from map should return false: "
                    + REMEMBERED_WIFI_DISPLAYS_KEY.mName + " " + entry.getKey() + " "
                    + entry.getValue(), result);
        }
    }

    @Test
    public void testRemove() {
        loadStore();

        // Remove the values
        Map<Integer, Map<Key<?>, Object>> userData = getUserData();
        for (Map.Entry<Integer, Map<Key<?>, Object>> userEntry : userData.entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : userEntry.getValue().entrySet()) {
                Key key = entry.getKey();
                boolean result = mDataStore.removeUserProperty(userEntry.getKey(), key);
                assertTrue("Removing the property should return true: " + key.mName, result);
            }
        }

        PersistentDataStore newDataStore = saveAndLoadNewStore();

        // Verify the new store
        for (Map.Entry<Integer, Map<Key<?>, Object>> userEntry : userData.entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : userEntry.getValue().entrySet()) {
                Key key = entry.getKey();
                assertNull("The value should be removed from the existing store: " + key.mName,
                        mDataStore.getUserProperty(userEntry.getKey(), key));

                boolean result = mDataStore.removeUserProperty(userEntry.getKey(), key);
                assertFalse("Removing an absent value return false: " + key.mName, result);

                assertNull("The property should be unset after a save/load cycle: " + key.mName,
                        newDataStore.getUserProperty(userEntry.getKey(), key));
            }
        }
    }

    @Test
    public void testDisplayPropertyReturnsNull() {
        loadStore();
        for (Map.Entry<Integer, Map<String, Map<Key<?>, Object>>> userEntry :
                getDisplayData().entrySet()) {
            for (Map.Entry<String, Map<Key<?>, Object>> displayEntry :
                    userEntry.getValue().entrySet()) {
                for (Map.Entry<Key<?>, Object> entry : displayEntry.getValue().entrySet()) {
                    Object result = mDataStore.getDisplayProperty(
                            userEntry.getKey(), /* displayDevice= */ null, entry.getKey());
                    assertNull("Null should be returned if display device is null", result);

                    result = mDataStore.getDisplayProperty(userEntry.getKey(),
                            createTestDisplayDevice(mDisplayAdapter, /* uniqueId= */ null),
                            entry.getKey());
                    assertNull("Null should be returned if unique ID is null", result);

                    result = mDataStore.getDisplayProperty(userEntry.getKey(),
                            createTestDisplayDevice(mDisplayAdapter,
                                    displayEntry.getKey(), /* hasStableUniqueId= */ false),
                            entry.getKey());
                    assertNull("Null should be returned if no stable unique ID", result);
                }
            }
        }
    }

    @Test
    public void testPropertyNotPermitted() {
        assertThrows(IllegalArgumentException.class,
                () -> mDataStore.setGlobalProperty(BRIGHTNESS_KEY, 2f));
        assertThrows(IllegalArgumentException.class,
                () -> mDataStore.setUserProperty(USER_SERIALS[0],
                        BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY, 2f));
        assertThrows(IllegalArgumentException.class,
                () -> mDataStore.setDisplayProperty(USER_SERIALS[0],
                        mDisplayDevices.get(DISPLAY_UNIQUE_IDS[0]),
                        BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY, 2f));
    }

    @Test
    public void testRemoveUserData() {
        loadStore();
        int userSerial = USER_SERIALS[0];
        Map<Key<?>, Object> userData = getUserData().get(USER_SERIALS[0]);

        mDataStore.removeUserData(userSerial);

        for (Map.Entry<Key<?>, Object> entry : userData.entrySet()) {
            assertNull("User data should have been removed: " + entry.getKey(),
                    mDataStore.getUserProperty(userSerial, entry.getKey()));
        }
        for (Map.Entry<String, Map<Key<?>, Object>> displayEntry : getDisplayData().get(
                userSerial).entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : displayEntry.getValue().entrySet()) {
                assertNull("User display-specific data should have been removed: " + entry.getKey(),
                        mDataStore.getDisplayProperty(userSerial,
                                mDisplayDevices.get(displayEntry.getKey()), entry.getKey()));
            }
        }
    }

    @Test
    public void testBadNumberFormat() {
        loadStore(XML_CONTENTS_BAD_NUMBER_FORMAT);
        verifyStoreEmpty();
    }

    @Test
    public void testMissingId() {
        loadStore(XML_CONTENTS_ID_MISSING);
        verifyStoreEmpty();
    }

    @Test
    public void testUnrecognizedKey() {
        loadStore(XML_CONTENTS_UNRECOGNIZED_KEY);
        verifyStoreEmpty();
    }

    @Test
    public void testDuplicateKey() {
        loadStore(XML_CONTENTS_DUPLICATE_KEY);
        verifyStoreEmpty();
    }

    @Test
    public void testMainTagMissing() {
        loadStore(XML_CONTENTS_MAIN_TAG_MISSING);
        verifyStoreEmpty();
    }

    private void verifyStoreEmpty() {
        for (Map.Entry<Key<?>, Object> entry : getGlobalData().entrySet()) {
            assertNull("Initial data store should be empty",
                    mDataStore.getGlobalProperty(entry.getKey()));
        }
        for (Map.Entry<Integer, Map<Key<?>, Object>> userEntry : getUserData().entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : userEntry.getValue().entrySet()) {
                assertNull("Initial data store should be empty",
                        mDataStore.getUserProperty(userEntry.getKey(), entry.getKey()));
            }
        }
        for (Map.Entry<Integer, Map<String, Map<Key<?>, Object>>> userEntry :
                getDisplayData().entrySet()) {
            for (Map.Entry<String, Map<Key<?>, Object>> displayEntry :
                    userEntry.getValue().entrySet()) {
                for (Key<?> key : displayEntry.getValue().keySet()) {
                    assertNull("Initial data store should be empty",
                            mDataStore.getDisplayProperty(userEntry.getKey(),
                                    mDisplayDevices.get(displayEntry.getKey()), key));
                }
            }
        }
    }

    private void testSaveAndRestore() {
        // Set new values

        Map<Key<?>, Object> globalData = getGlobalData();
        for (Map.Entry<Key<?>, Object> entry : globalData.entrySet()) {
            Key key = entry.getKey();
            Object value = entry.getValue();
            boolean result = mDataStore.setGlobalProperty(key, value);
            assertTrue("Setting the property should return true: " + key.mName + " " + value,
                    result);
        }

        Map<Integer, Map<Key<?>, Object>> userData = getUserData();
        for (Map.Entry<Integer, Map<Key<?>, Object>> userEntry : userData.entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : userEntry.getValue().entrySet()) {
                Key key = entry.getKey();
                Object value = entry.getValue();
                boolean result = mDataStore.setUserProperty(userEntry.getKey(), key, value);
                assertTrue("Setting the property should return true: " + key.mName + " " + value,
                        result);
            }
        }

        Map<Integer, Map<String, Map<Key<?>, Object>>> displayData = getDisplayData();
        for (Map.Entry<Integer, Map<String, Map<Key<?>, Object>>> userEntry :
                displayData.entrySet()) {
            for (Map.Entry<String, Map<Key<?>, Object>> displayEntry :
                    userEntry.getValue().entrySet()) {
                for (Map.Entry<Key<?>, Object> entry : displayEntry.getValue().entrySet()) {
                    Key key = entry.getKey();
                    Object value = entry.getValue();
                    boolean result = mDataStore.setDisplayProperty(userEntry.getKey(),
                            mDisplayDevices.get(displayEntry.getKey()), key, value);
                    assertTrue(
                            "Setting the property should return true: " + key.mName + " " + value,
                            result);
                }
            }
        }

        PersistentDataStore newDataStore = saveAndLoadNewStore();

        // Verify the new store

        for (Map.Entry<Key<?>, Object> entry : globalData.entrySet()) {
            Key key = entry.getKey();
            Object value = entry.getValue();
            assertEquals("The new value should be saved in the existing store: " + key.mName, value,
                    mDataStore.getGlobalProperty(key));

            boolean result = mDataStore.setGlobalProperty(key, value);
            assertFalse("Setting the same value should return false: " + key.mName + " " + value,
                    result);

            assertEquals(
                    "The new value should be restored after a save/load cycle: " + key.mName + " "
                            + value, value, newDataStore.getGlobalProperty(key));
        }

        for (Map.Entry<Integer, Map<Key<?>, Object>> userEntry : userData.entrySet()) {
            for (Map.Entry<Key<?>, Object> entry : userEntry.getValue().entrySet()) {
                Key key = entry.getKey();
                Object value = entry.getValue();
                assertEquals(
                        "The new value should be saved in the existing store: " + key.mName + " "
                                + value, value,
                        mDataStore.getUserProperty(userEntry.getKey(), key));

                boolean result = mDataStore.setUserProperty(userEntry.getKey(), key, value);
                assertFalse(
                        "Setting the same value should return false: " + key.mName + " " + value,
                        result);

                assertEquals(
                        "The new value should be restored after a save/load cycle: " + key.mName
                                + " " + value, value,
                        newDataStore.getUserProperty(userEntry.getKey(), key));
            }
        }

        for (Map.Entry<Integer, Map<String, Map<Key<?>, Object>>> userEntry :
                displayData.entrySet()) {
            for (Map.Entry<String, Map<Key<?>, Object>> displayEntry :
                    userEntry.getValue().entrySet()) {
                for (Map.Entry<Key<?>, Object> entry : displayEntry.getValue().entrySet()) {
                    Key key = entry.getKey();
                    Object value = entry.getValue();
                    assertEquals("The new value should be saved in the existing store: " + key.mName
                            + " " + value, value, mDataStore.getDisplayProperty(userEntry.getKey(),
                            mDisplayDevices.get(displayEntry.getKey()), key));

                    boolean result = mDataStore.setDisplayProperty(userEntry.getKey(),
                            mDisplayDevices.get(displayEntry.getKey()), key, value);
                    assertFalse("Setting the same value should return false: " + key.mName + " "
                            + value, result);

                    assertEquals(
                            "The new value should be restored after a save/load cycle: " + key.mName
                                    + " " + value, value,
                            newDataStore.getDisplayProperty(userEntry.getKey(),
                                    mDisplayDevices.get(displayEntry.getKey()), key));
                }
            }
        }
    }

    private void loadStore() {
        loadStore(XML_CONTENTS);
    }

    private void loadStore(String contents) {
        InputStream is = new ByteArrayInputStream(contents.getBytes(UTF_8));
        mInjector.setReadStream(is);
    }

    private PersistentDataStore saveAndLoadNewStore() {
        // Save to XML and create new data store
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mInjector.setWriteStream(baos);
        mDataStore.saveIfNeeded();
        flushLoopers(mLooperManager);
        assertTrue("A save should have been triggered because the data was dirty",
                mInjector.wasWriteSuccessful());

        TestInjector newInjector = new TestInjector();
        PersistentDataStore newDataStore = new PersistentDataStore(newInjector,
                new Handler(mLooper));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        newInjector.setReadStream(bais);
        return newDataStore;
    }
}
