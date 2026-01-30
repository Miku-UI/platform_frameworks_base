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

package com.android.server.backup.crossplatform;

import static com.google.common.truth.Truth.assertThat;

import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config
public class PlatformConfigParserTest {
    private XmlPullParserFactory mFactory;
    private XmlPullParser mXmlParser;
    private PlatformConfigParser mParser;

    @Before
    public void setUp() throws Exception {
        mFactory = XmlPullParserFactory.newInstance();
        mXmlParser = mFactory.newPullParser();
        mParser = new PlatformConfigParser();
    }

    @Test
    public void testValidConfig_success() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cloud-backup><include domain=\"file\""
                            + " path=\"backup\" /></cloud-backup><cross-platform-transfer"
                            + " platform=\"ios\"><exclude domain=\"database\" path=\".\""
                            + " /><platform-specific-params bundleId=\"bundle id\" teamId=\"team"
                            + " id\" contentVersion=\"1.0\" /></cross-platform-transfer>"
                            + "</data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).hasSize(1);
        List<PlatformSpecificParams> iosParams = result.get("ios");
        assertThat(iosParams)
                .containsExactly(new PlatformSpecificParams("bundle id", "team id", "1.0"));
    }

    @Test
    public void testMultipleParams_returnsAll() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cross-platform-transfer"
                            + " platform=\"ios\"><platform-specific-params bundleId=\"bundle id\""
                            + " teamId=\"team id\" contentVersion=\"1.0\""
                            + " /><platform-specific-params bundleId=\"other bundle id\""
                            + " teamId=\"other team id\" contentVersion=\"1.1\" />"
                            + "</cross-platform-transfer></data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).hasSize(1);
        List<PlatformSpecificParams> iosParams = result.get("ios");
        assertThat(iosParams)
                .containsExactly(
                        new PlatformSpecificParams("bundle id", "team id", "1.0"),
                        new PlatformSpecificParams("other bundle id", "other team id", "1.1"));
    }

    @Test
    public void testNoParams_empty() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cross-platform-transfer"
                                + " platform=\"ios\"></cross-platform-transfer>"
                                + "</data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }

    @Test
    public void testMissingConfig_empty() throws Exception {
        mXmlParser.setInput(new StringReader("<data-extraction-rules></data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }

    @Test
    public void testUnsupportedPlatform_ignored() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cross-platform-transfer"
                            + " platform=\"android\"><platform-specific-params bundleId=\"bundle"
                            + " id\" teamId=\"team id\" contentVersion=\"1.0\" />"
                            + "</cross-platform-transfer></data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }

    @Test
    public void testInvalidParentTag_ignored() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cloud-backup><platform-specific-params"
                            + " bundleId=\"bundle id\" teamId=\"team id\" contentVersion=\"1.0\" />"
                            + "</cloud-backup></data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }

    @Test
    public void testInvalidNesting_ignored() throws Exception {
        mXmlParser.setInput(
                new StringReader(
                        "<data-extraction-rules><cross-platform-transfer platform=\"ios\">"
                                + "<include><platform-specific-params bundleId=\"bundle id\""
                                + " teamId=\"team id\" contentVersion=\"1.0\" /></include>"
                                + "</cross-platform-transfer></data-extraction-rules>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }

    @Test
    public void testParseWrongDocument_empty() throws Exception {
        mXmlParser.setInput(new StringReader("<full-backup-content></full-backup-content>"));

        Map<String, List<PlatformSpecificParams>> result = mParser.parseConfig(mXmlParser);

        assertThat(result).isEmpty();
    }
}
