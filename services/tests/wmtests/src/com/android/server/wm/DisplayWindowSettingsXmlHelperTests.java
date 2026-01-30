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

package com.android.server.wm;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import com.android.server.wm.DisplayWindowSettings.SettingsProvider.SettingsEntry;
import com.android.server.wm.DisplayWindowSettingsXmlHelper.FileData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for the {@link DisplayWindowSettingsXmlHelper} class.
 */
@SmallTest
@Presubmit
@WindowTestsBase.UseTestDisplay
@RunWith(WindowTestRunner.class)
public class DisplayWindowSettingsXmlHelperTests {

    private static final String DISPLAY_NAME = "test_id";

    @Test
    public void readSettings_withFullXml_createsCorrectFileData() {
        String xml = createFullXmlString();

        FileData actualData = DisplayWindowSettingsXmlHelper.FileData.readSettings(
                createInputStream(xml));

        FileData expectedData = createFullFileData();
        assertThat(actualData).isEqualTo(expectedData);
    }

    @Test
    public void readSettings_withMalformedXml_returnsEmptyFileData() {
        String xml = createFaultyXmlString();

        FileData result = DisplayWindowSettingsXmlHelper.FileData.readSettings(
                createInputStream(xml));

        assertThat(result.mSettings).isEmpty();
    }

    @Test
    public void writeSettings_forBackupFalse_includesAllAttributes() {
        FileData data = createFullFileData();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        DisplayWindowSettingsXmlHelper.writeSettings(os, data, false);
        InputStream is = new ByteArrayInputStream(os.toByteArray());

        FileData roundtripData = DisplayWindowSettingsXmlHelper.FileData.readSettings(is);
        assertThat(roundtripData).isEqualTo(data);
    }

    @Test
    public void writeSettings_forBackupTrue_excludesDeviceSpecificAttributes() {
        FileData data = createFullFileData();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        DisplayWindowSettingsXmlHelper.writeSettings(os, data, true);
        InputStream is = new ByteArrayInputStream(os.toByteArray());

        FileData roundtripData = DisplayWindowSettingsXmlHelper.FileData.readSettings(is);
        assertThat(roundtripData).isNotEqualTo(data);
    }

    @Test
    public void readAndFilterSettings_createsByteArrayFromFilteredData() {
        String xml = createFullXmlString();

        byte[] payload = DisplayWindowSettingsXmlHelper.readAndFilterSettings(
                createInputStream(xml));
        FileData data = DisplayWindowSettingsXmlHelper.FileData.readSettings(
                new ByteArrayInputStream(payload));

        assertThat(data).isEqualTo(createFilteredFileData());
    }

    /**
     * Creates a fully populated FileData object.
     */
    private FileData createFullFileData() {
        FileData data = new FileData();
        data.mIdentifierType = 1;

        SettingsEntry entry = new SettingsEntry();
        entry.mWindowingMode = 0;
        entry.mUserRotationMode = 0;
        entry.mUserRotation = 0;
        entry.mForcedWidth = 1080;
        entry.mForcedHeight = 1920;
        entry.mForcedDensity = 480;
        entry.mForcedDensityRatio = 1.2f;
        entry.mForcedScalingMode = 0;
        entry.mRemoveContentMode = 0;
        entry.mShouldShowWithInsecureKeyguard = true;
        entry.mShouldShowSystemDecors = false;
        entry.mImePolicy = 2;
        entry.mFixedToUserRotation = 2;
        entry.mIgnoreOrientationRequest = true;
        entry.mIgnoreDisplayCutout = false;
        entry.mDontMoveToTop = true;
        entry.mIsHomeSupported = false;

        data.mSettings.put(DISPLAY_NAME, entry);
        return data;
    }

    /**
     * Creates a filtered version of the FileData object.
     */
    private FileData createFilteredFileData() {
        FileData data = new FileData();
        data.mIdentifierType = 1;

        SettingsEntry entry = new SettingsEntry();
        entry.mUserRotationMode = 0;
        entry.mUserRotation = 0;
        entry.mForcedDensityRatio = 1.2f;

        data.mSettings.put(DISPLAY_NAME, entry);
        return data;
    }

    /**
     * Creates a XML string that corresponds to the data from createFullFileData().
     */
    private static String createFullXmlString() {
        return "<?xml version='1.0' encoding='utf-8' ?>"
                + "<display-settings>"
                + "  <config identifier=\"1\" />"
                + "  <display name=\"" + DISPLAY_NAME + "\""
                + "    windowingMode=\"0\""
                + "    userRotationMode=\"0\""
                + "    userRotation=\"0\""
                + "    forcedWidth=\"1080\""
                + "    forcedHeight=\"1920\""
                + "    forcedDensity=\"480\""
                + "    forcedDensityRatio=\"1.2\""
                + "    forcedScalingMode=\"0\""
                + "    removeContentMode=\"0\""
                + "    shouldShowWithInsecureKeyguard=\"true\""
                + "    shouldShowSystemDecors=\"false\""
                + "    imePolicy=\"2\""
                + "    fixedToUserRotation=\"2\""
                + "    ignoreOrientationRequest=\"true\""
                + "    ignoreDisplayCutout=\"false\""
                + "    dontMoveToTop=\"true\""
                + "    isHomeSupported=\"false\" />"
                + "</display-settings>";
    }

    private static String createFaultyXmlString() {
        return "<?xml version='1.0' encoding='utf-8' ?>"
                + "<display-settings>"
                + "  <config identifier=\"1\" />"
                + "  <display name=\"" + DISPLAY_NAME + "\"";
    }

    /**
     * Creates an {@link InputStream} from a given String.
     */
    private static InputStream createInputStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
