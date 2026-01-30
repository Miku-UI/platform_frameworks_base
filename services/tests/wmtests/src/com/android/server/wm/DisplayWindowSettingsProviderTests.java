/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.TYPE_VIRTUAL;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;

import android.annotation.Nullable;
import android.app.backup.BackupManager;
import android.platform.test.annotations.Presubmit;
import android.util.Xml;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.server.wm.DisplayWindowSettings.SettingsProvider.SettingsEntry;
import com.android.server.wm.TestDisplayWindowSettingsProvider.TestStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Tests for the {@link DisplayWindowSettingsProvider} class.
 *
 * Build/Install/Run:
 *  atest WmTests:DisplayWindowSettingsProviderTests
 */
@SmallTest
@Presubmit
@WindowTestsBase.UseTestDisplay
@RunWith(WindowTestRunner.class)
public class DisplayWindowSettingsProviderTests extends WindowTestsBase {
    private static final int DISPLAY_PORT = 0xFF;
    private static final long DISPLAY_MODEL = 0xEEEEEEEEL;
    private static final int MAX_NUMBER_OF_DISPLAY_SETTINGS = 100;

    private static final File TEST_FOLDER = getInstrumentation().getTargetContext().getCacheDir();

    private TestStorage mDefaultVendorSettingsStorage;
    private TestStorage mSecondaryVendorSettingsStorage;
    private TestStorage mOverrideSettingsStorage;
    private DisplayWindowSettingsProvider mProvider;

    private DisplayContent mPrimaryDisplay;
    private DisplayInfo mPrimaryDisplayInfo;
    private String mPrimaryDisplayIdentifier;

    private DisplayContent mSecondaryDisplay;
    private String mSecondaryDisplayIdentifier;

    @Before
    public void setUp() throws Exception {
        deleteRecursively(TEST_FOLDER);

        mDefaultVendorSettingsStorage = new TestStorage();
        mSecondaryVendorSettingsStorage = new TestStorage();
        mOverrideSettingsStorage = new TestStorage();
        mProvider = readDisplayWindowSettingsFromStorage();

        mPrimaryDisplay = mWm.getDefaultDisplayContentLocked();
        mPrimaryDisplayInfo = mPrimaryDisplay.getDisplayInfo();
        mPrimaryDisplayIdentifier = mPrimaryDisplayInfo.uniqueId;

        mSecondaryDisplay = mDisplayContent;
        mSecondaryDisplayIdentifier = mSecondaryDisplay.getDisplayInfo().uniqueId;
        assertNotEquals(Display.DEFAULT_DISPLAY, mSecondaryDisplay.getDisplayId());
    }

    @After
    public void tearDown() {
        deleteRecursively(TEST_FOLDER);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage() {
        prepareOverrideDisplaySettings(mSecondaryDisplayIdentifier);

        final SettingsEntry expectedSettings = new SettingsEntry();
        expectedSettings.mWindowingMode = WINDOWING_MODE_PINNED;
        readAndAssertExpectedSettings(mSecondaryDisplay, expectedSettings);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_LegacyDisplayId() {
        prepareOverrideDisplaySettings(mPrimaryDisplayIdentifier);

        final SettingsEntry expectedSettings = new SettingsEntry();
        expectedSettings.mWindowingMode = WINDOWING_MODE_PINNED;
        readAndAssertExpectedSettings(mPrimaryDisplay, expectedSettings);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_LegacyDisplayId_UpdateAfterAccess()
            throws Exception {
        // Store display settings with legacy display identifier.
        prepareOverrideDisplaySettings(mPrimaryDisplayIdentifier);

        // Update settings with new value, should trigger write to injector.
        final DisplayWindowSettingsProvider provider = readDisplayWindowSettingsFromStorage();
        updateOverrideSettings(provider, mPrimaryDisplayInfo,
                overrideSettings -> overrideSettings.mForcedDensity = 200);
        assertTrue(mOverrideSettingsStorage.wasWriteSuccessful());

        // Verify that display identifier was updated.
        final String newDisplayIdentifier = getStoredDisplayAttributeValue(
                mOverrideSettingsStorage, "name");
        assertEquals("Display identifier must be updated to use uniqueId",
                mPrimaryDisplayIdentifier, newDisplayIdentifier);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_UsePortAsId() {
        mPrimaryDisplayInfo.address = DisplayAddress.fromPortAndModel(DISPLAY_PORT, DISPLAY_MODEL);

        final String displayIdentifier = "port:" + DISPLAY_PORT;
        prepareOverrideDisplaySettings(displayIdentifier, true /* usePortAsId */);

        final SettingsEntry expectedSettings = new SettingsEntry();
        expectedSettings.mWindowingMode = WINDOWING_MODE_PINNED;
        readAndAssertExpectedSettings(mPrimaryDisplay, expectedSettings);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_UsePortAsId_IncorrectAddress() {
        prepareOverrideDisplaySettings(mPrimaryDisplayIdentifier, true /* usePortAsId */);

        mPrimaryDisplayInfo.address = DisplayAddress.fromPhysicalDisplayId(123456);

        // Verify that the entry is not matched and default settings are returned instead.
        final SettingsEntry expectedSettings = new SettingsEntry();
        readAndAssertExpectedSettings(mPrimaryDisplay, expectedSettings);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_secondaryVendorDisplaySettingsLocation() {
        prepareSecondaryDisplaySettings(mSecondaryDisplayIdentifier);
        final DisplayWindowSettingsProvider provider = readDisplayWindowSettingsFromStorage();

        // Expected settings should be empty because the default is to read from the primary vendor
        // settings location.
        final SettingsEntry expectedSettings = new SettingsEntry();
        assertExpectedSettings(provider, mSecondaryDisplay, expectedSettings);

        // Now switch to secondary vendor settings and assert proper settings.
        provider.setBaseSettingsStorage(mSecondaryVendorSettingsStorage);
        expectedSettings.mWindowingMode = WINDOWING_MODE_FULLSCREEN;
        assertExpectedSettings(provider, mSecondaryDisplay, expectedSettings);

        // Switch back to primary and assert settings are empty again.
        provider.setBaseSettingsStorage(mDefaultVendorSettingsStorage);
        expectedSettings.mWindowingMode = WINDOWING_MODE_UNDEFINED;
        assertExpectedSettings(provider, mSecondaryDisplay, expectedSettings);
    }

    @Test
    public void testReadingDisplaySettingsFromStorage_overrideSettingsTakePrecedenceOverVendor() {
        prepareOverrideDisplaySettings(mSecondaryDisplayIdentifier);
        prepareSecondaryDisplaySettings(mSecondaryDisplayIdentifier);

        final DisplayWindowSettingsProvider provider = readDisplayWindowSettingsFromStorage();
        provider.setBaseSettingsStorage(mSecondaryVendorSettingsStorage);

        // The windowing mode should be set to WINDOWING_MODE_PINNED because the override settings
        // take precedence over the vendor provided settings.
        final SettingsEntry expectedSettings = new SettingsEntry();
        expectedSettings.mWindowingMode = WINDOWING_MODE_PINNED;
        assertExpectedSettings(provider, mSecondaryDisplay, expectedSettings);
    }

    @Test
    public void testWritingDisplaySettingsToStorage() throws Exception {
        final DisplayInfo secondaryDisplayInfo = mSecondaryDisplay.getDisplayInfo();

        // Write some settings to storage.
        updateOverrideSettings(mProvider, secondaryDisplayInfo, overrideSettings -> {
            overrideSettings.mShouldShowSystemDecors = true;
            overrideSettings.mImePolicy = DISPLAY_IME_POLICY_LOCAL;
            overrideSettings.mDontMoveToTop = true;
        });
        assertTrue(mOverrideSettingsStorage.wasWriteSuccessful());

        // Verify that settings were stored correctly.
        assertEquals("Attribute value must be stored", mSecondaryDisplayIdentifier,
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "name"));
        assertEquals("Attribute value must be stored", "true",
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "shouldShowSystemDecors"));
        assertEquals("Attribute value must be stored", "0",
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "imePolicy"));
        assertEquals("Attribute value must be stored", "true",
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "dontMoveToTop"));
    }

    @Test
    public void testWritingDisplaySettingsToStorage_secondaryUserDisplaySettingsLocation() {
        final TestStorage secondaryUserOverrideSettingsStorage = new TestStorage();
        final SettingsEntry expectedSettings = new SettingsEntry();
        expectedSettings.mForcedDensity = 356;

        // Write some settings to storage from default user.
        updateOverrideSettings(mProvider, mPrimaryDisplayInfo,
                settings -> settings.mForcedDensity = 356);
        assertThat(mOverrideSettingsStorage.wasWriteSuccessful()).isTrue();

        // Now switch to secondary user override settings and write some settings.
        mProvider.setOverrideSettingsStorage(secondaryUserOverrideSettingsStorage);
        updateOverrideSettings(mProvider, mPrimaryDisplayInfo,
                settings -> settings.mForcedDensity = 420);
        assertThat(secondaryUserOverrideSettingsStorage.wasWriteSuccessful()).isTrue();

        // Switch back to primary and assert default user settings remain unchanged.
        mProvider.setOverrideSettingsStorage(mOverrideSettingsStorage);
        assertExpectedSettings(mProvider, mPrimaryDisplay, expectedSettings);
    }

    @Test
    public void testDoNotWriteVirtualDisplaySettingsToStorage() throws Exception {
        final DisplayInfo secondaryDisplayInfo = mSecondaryDisplay.getDisplayInfo();
        secondaryDisplayInfo.type = TYPE_VIRTUAL;

        // No write to storage on virtual display change.
        updateOverrideSettings(mProvider, secondaryDisplayInfo, virtualSettings -> {
            virtualSettings.mShouldShowSystemDecors = true;
            virtualSettings.mImePolicy = DISPLAY_IME_POLICY_LOCAL;
            virtualSettings.mDontMoveToTop = true;
        });
        assertFalse(mOverrideSettingsStorage.wasWriteSuccessful());
    }

    @Test
    public void testWritingDisplaySettingsToStorage_UsePortAsId() throws Exception {
        prepareOverrideDisplaySettings(null /* displayIdentifier */, true /* usePortAsId */);

        // Store config to use port as identifier.
        final DisplayInfo secondaryDisplayInfo = mSecondaryDisplay.getDisplayInfo();
        secondaryDisplayInfo.address = DisplayAddress.fromPortAndModel(DISPLAY_PORT, DISPLAY_MODEL);

        // Write some settings to storage.
        final DisplayWindowSettingsProvider provider = readDisplayWindowSettingsFromStorage();
        updateOverrideSettings(provider, secondaryDisplayInfo, overrideSettings -> {
            overrideSettings.mShouldShowSystemDecors = true;
            overrideSettings.mImePolicy = DISPLAY_IME_POLICY_LOCAL;
        });
        assertTrue(mOverrideSettingsStorage.wasWriteSuccessful());

        // Verify that settings were stored correctly.
        assertEquals("Attribute value must be stored", "port:" + DISPLAY_PORT,
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "name"));
        assertEquals("Attribute value must be stored", "true",
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "shouldShowSystemDecors"));
        assertEquals("Attribute value must be stored", "0",
                getStoredDisplayAttributeValue(mOverrideSettingsStorage, "imePolicy"));
    }

    @Test
    public void testCleanUpEmptyDisplaySettingsOnDisplayRemoved() {
        final int initialSize = 0;
        final DisplayInfo secondaryDisplayInfo = mSecondaryDisplay.getDisplayInfo();

        updateOverrideSettings(mProvider, secondaryDisplayInfo, overrideSettings -> {
            // Size + 1 when query for a new display.
            assertEquals(initialSize + 1, mProvider.getOverrideSettingsSize());

            // When a display is removed, its override Settings is not removed if there is any
            // override.
            overrideSettings.mShouldShowSystemDecors = true;
        });
        mProvider.onDisplayRemoved(secondaryDisplayInfo);

        assertEquals(initialSize + 1, mProvider.getOverrideSettingsSize());

        // When a display is removed, its override Settings is removed if there is no override.
        mProvider.updateOverrideSettings(secondaryDisplayInfo, new SettingsEntry());
        mProvider.onDisplayRemoved(secondaryDisplayInfo);

        assertEquals(initialSize, mProvider.getOverrideSettingsSize());
    }

    @Test
    public void testCleanUpVirtualDisplaySettingsOnDisplayRemoved() {
        final int initialSize = 0;
        final DisplayInfo secondaryDisplayInfo = mSecondaryDisplay.getDisplayInfo();
        secondaryDisplayInfo.type = TYPE_VIRTUAL;

        updateOverrideSettings(mProvider, secondaryDisplayInfo, overrideSettings -> {
            // Size + 1 when query for a new display.
            assertEquals(initialSize + 1, mProvider.getOverrideSettingsSize());

            // When a virtual display is removed, its override Settings is removed
            // even if it has override.
            overrideSettings.mShouldShowSystemDecors = true;
        });
        mProvider.onDisplayRemoved(secondaryDisplayInfo);

        assertEquals(initialSize, mProvider.getOverrideSettingsSize());
    }

    @Test
    public void testUpdateOverrideSettings_whenExceedingCapacity_forgetsOldestDisplaySettings() {
        for (int i = 0; i < MAX_NUMBER_OF_DISPLAY_SETTINGS; i++) {
            final DisplayInfo info = createDisplayInfo("test_id_" + i, "test_display_" + i);
            addDisplayWithDensity(info, 100 + i);
        }

        final DisplayInfo exceedMaxDisplay = createDisplayInfo("id_exceed", "display_exceed");
        addDisplayWithDensity(exceedMaxDisplay, 100 + MAX_NUMBER_OF_DISPLAY_SETTINGS);

        assertEquals("Stored settings count should be capped at max capacity",
                MAX_NUMBER_OF_DISPLAY_SETTINGS, mProvider.getOverrideSettingsSize());
        final DisplayInfo evictedDisplay = createDisplayInfo("test_id_0", "test_display_0");
        assertEquals("Forgotten entry should have default density",
                0, mProvider.getSettings(evictedDisplay).mForcedDensity);
    }

    @Test
    public void testUpdateOverrideSettings_onAccessingOldEntry_preventsPurge() {
        for (int i = 0; i < MAX_NUMBER_OF_DISPLAY_SETTINGS; i++) {
            final DisplayInfo info = createDisplayInfo("test_id_" + i, "test_display_" + i);
            addDisplayWithDensity(info, 100 + i);
        }
        final DisplayInfo oldestDisplay = createDisplayInfo("test_id_0", "test_display_0");
        final DisplayInfo nextOldestDisplay = createDisplayInfo("test_id_1", "test_display_1");

        mProvider.getSettings(oldestDisplay);
        final DisplayInfo exceedMaxDisplay = createDisplayInfo("id_exceed", "display_exceed");
        addDisplayWithDensity(exceedMaxDisplay, 100 + MAX_NUMBER_OF_DISPLAY_SETTINGS);

        assertEquals("Recently accessed entry should not be purged",
                100, mProvider.getSettings(oldestDisplay).mForcedDensity);
        assertEquals("The oldest un-accessed entry should be purged",
                0, mProvider.getSettings(nextOldestDisplay).mForcedDensity);
    }

    @Test
    public void testUpdateOverrideSettings_withNullDisplayName_addsDisplaySettingsSucceeds() {
        final DisplayInfo displayInfoWithNullName = createDisplayInfo("test_id", null /* name */);

        addDisplayWithDensity(displayInfoWithNullName, 123);

        assertEquals("Settings should be created even with a null display name.", 123,
                mProvider.getSettings(displayInfoWithNullName).mForcedDensity);
    }

    /** Helper method to create a DisplayInfo object with specific identifiers. */
    private static DisplayInfo createDisplayInfo(String uniqueId, String name) {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.uniqueId = uniqueId;
        displayInfo.name = name;
        return displayInfo;
    }

    /** Helper method to add a display with a specific density setting. */
    private void addDisplayWithDensity(DisplayInfo displayInfo, int density) {
        updateOverrideSettings(mProvider, displayInfo,
                settings -> settings.mForcedDensity = density);
    }

    /**
     * Updates the override settings for a specific display.
     *
     * @param provider the provider to obtain and update the settings from.
     * @param displayInfo the information about the display to be updated.
     * @param modifier a function that modifies the settings for the display.
     */
    private static void updateOverrideSettings(DisplayWindowSettingsProvider provider,
            DisplayInfo displayInfo, Consumer<SettingsEntry> modifier) {
        final SettingsEntry settings = provider.getOverrideSettings(displayInfo);
        modifier.accept(settings);
        provider.updateOverrideSettings(displayInfo, settings);
    }

    /**
     * Prepares display settings and stores in {@link #mOverrideSettingsStorage}. Uses provided
     * display identifier and stores windowingMode=WINDOWING_MODE_PINNED.
     */
    private void prepareOverrideDisplaySettings(String displayIdentifier) {
        prepareOverrideDisplaySettings(displayIdentifier, false /* usePortAsId */);
    }

    private void prepareOverrideDisplaySettings(String displayIdentifier, boolean usePortAsId) {
        String contents = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<display-settings>\n";
        if (usePortAsId) {
            contents += "  <config identifier=\"1\"/>\n";
        }
        if (displayIdentifier != null) {
            contents += "  <display\n"
                    + "    name=\"" + displayIdentifier + "\"\n"
                    + "    windowingMode=\"" + WINDOWING_MODE_PINNED + "\"/>\n";
        }
        contents += "</display-settings>\n";

        final InputStream is = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        mOverrideSettingsStorage.setReadStream(is);
    }

    /**
     * Prepares display settings and stores in {@link #mSecondaryVendorSettingsStorage}. Uses
     * provided display identifier and stores windowingMode=WINDOWING_MODE_FULLSCREEN.
     */
    private void prepareSecondaryDisplaySettings(String displayIdentifier) {
        String contents = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<display-settings>\n";
        if (displayIdentifier != null) {
            contents += "  <display\n"
                    + "    name=\"" + displayIdentifier + "\"\n"
                    + "    windowingMode=\"" + WINDOWING_MODE_FULLSCREEN + "\"/>\n";
        }
        contents += "</display-settings>\n";

        final InputStream is = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        mSecondaryVendorSettingsStorage.setReadStream(is);
    }

    private void readAndAssertExpectedSettings(DisplayContent displayContent,
            SettingsEntry expectedSettings) {
        final DisplayWindowSettingsProvider provider = readDisplayWindowSettingsFromStorage();
        assertExpectedSettings(provider, displayContent, expectedSettings);
    }

    private static void assertExpectedSettings(DisplayWindowSettingsProvider provider,
            DisplayContent displayContent, SettingsEntry expectedSettings) {
        assertEquals(expectedSettings, provider.getSettings(displayContent.getDisplayInfo()));
    }

    private DisplayWindowSettingsProvider readDisplayWindowSettingsFromStorage() {
        return new DisplayWindowSettingsProvider(
                mDefaultVendorSettingsStorage, mOverrideSettingsStorage, mock(
                BackupManager.class));
    }

    @Nullable
    private String getStoredDisplayAttributeValue(TestStorage storage, String attr)
            throws Exception {
        try (InputStream stream = storage.openRead()) {
            TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Do nothing.
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("no start tag found");
            }

            int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("display")) {
                    return parser.getAttributeValue(null, attr);
                }
            }
        } finally {
            storage.closeRead();
        }
        return null;
    }

    private static boolean deleteRecursively(File file) {
        boolean fullyDeleted = true;
        if (file.isFile()) {
            return file.delete();
        } else if (file.isDirectory()) {
            final File[] files = file.listFiles();
            for (File child : files) {
                fullyDeleted &= deleteRecursively(child);
            }
            fullyDeleted &= file.delete();
        }
        return fullyDeleted;
    }
}
