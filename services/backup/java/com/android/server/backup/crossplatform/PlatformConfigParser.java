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

import static com.android.server.backup.BackupManagerService.TAG;

import android.app.backup.FullBackup;
import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * XML parser that extracts the cross platform configuration from a data extraction rules XML file.
 *
 * <p>This is similar to {@link FullBackup.BackupScheme}, but it does not require to be called
 * within an application context.
 *
 * @hide
 */
public class PlatformConfigParser {
    // Supported platforms
    public static final String PLATFORM_IOS = "ios";

    // XML sections and attributes
    private static final String DATA_EXTRACTION_RULES_SECTION = "data-extraction-rules";
    private static final String CROSS_PLATFORM_TRANSFER_SECTION = "cross-platform-transfer";
    private static final String PLATFORM_SPECIFIC_PARAMS_SECTION = "platform-specific-params";
    private static final String ATTR_PLATFORM = "platform";

    // Platform specific params for PLATFORM_IOS
    private static final String ATTR_BUNDLE_ID = "bundleId";
    private static final String ATTR_TEAM_ID = "teamId";
    private static final String ATTR_CONTENT_VERSION = "contentVersion";

    /**
     * Parses the platform-specific configuration from the data-extraction-rules of the given app.
     * If no data-extraction-rules are configured, an empty map is returned.
     *
     * @throws IOException when the package is not found or on malformed XML.
     */
    public static Map<String, List<PlatformSpecificParams>> parsePlatformSpecificConfig(
            PackageManager packageManager, ApplicationInfo applicationInfo) throws IOException {
        if (applicationInfo == null || applicationInfo.dataExtractionRulesRes == 0) {
            Slog.d(TAG, "No data extraction rules");
            return Collections.emptyMap();
        }
        int resourceId = applicationInfo.dataExtractionRulesRes;
        Resources resources;
        try {
            resources = packageManager.getResourcesForApplication(applicationInfo.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Package not found", e);
        }
        try (XmlResourceParser xmlParser = resources.getXml(resourceId)) {
            return new PlatformConfigParser().parseConfig(xmlParser);
        } catch (XmlPullParserException e) {
            throw new IOException("Invalid XML", e);
        }
    }

    /**
     * Uses the {@link XmlPullParser} to parse the XML document and returns any valid platform
     * specific configuration for supported platforms. Unsupported platforms or any other unexpected
     * XML tags are ignored or skipped.
     *
     * @return A map containing the list of platform specific params for each platform.
     */
    @VisibleForTesting
    Map<String, List<PlatformSpecificParams>> parseConfig(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        if (!hasExpectedRootTag(parser, DATA_EXTRACTION_RULES_SECTION)) {
            Slog.e(TAG, "Not a valid data-extraction-rules configuration.");
            return Collections.emptyMap();
        }
        return parsePlatformSpecificParams(parser);
    }

    /**
     * Checks whether the XML document starts with the given root tag.
     *
     * @return {@code true} if the first {@code START_TAG} of the {@link XmlPullParser} is equal to
     *     the given {@code tagName}, {@code false} otherwise.
     */
    private boolean hasExpectedRootTag(XmlPullParser parser, String tagName)
            throws IOException, XmlPullParserException {
        int event = parser.getEventType();
        while (event != XmlPullParser.START_TAG) {
            event = parser.next();
        }
        return TextUtils.equals(parser.getName(), tagName);
    }

    /**
     * Parses all {@code <cross-platform-transfer>} sections and the {@code
     * <platform-specific-params>} contained within. Ignores everything else.
     */
    private Map<String, List<PlatformSpecificParams>> parsePlatformSpecificParams(
            XmlPullParser parser) throws IOException, XmlPullParserException {
        Map<String, List<PlatformSpecificParams>> platformSpecificParamsPerPlatform =
                new ArrayMap<>();
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            event = parser.next();

            if (event != XmlPullParser.START_TAG) {
                continue;
            }

            String tag = parser.getName();
            if (!TextUtils.equals(tag, CROSS_PLATFORM_TRANSFER_SECTION)) {
                skipSection(parser, tag);
                continue;
            }

            String platform = parser.getAttributeValue(/* namespace= */ null, ATTR_PLATFORM);
            if (!TextUtils.equals(platform, PLATFORM_IOS)) {
                Slog.w(
                        TAG,
                        "Cross-platform transfer does not support platform: \"" + platform + "\"");
                skipSection(parser, tag);
                continue;
            }

            List<PlatformSpecificParams> params = parseCrossPlatformTransferSection(parser);
            if (!params.isEmpty()) {
                platformSpecificParamsPerPlatform.put(platform, params);
            }
        }
        return platformSpecificParamsPerPlatform;
    }

    private void skipSection(XmlPullParser parser, String tag)
            throws IOException, XmlPullParserException {
        int event = parser.next();
        while (event != XmlPullParser.END_TAG && !TextUtils.equals(parser.getName(), tag)) {
            event = parser.next();
        }
    }

    private List<PlatformSpecificParams> parseCrossPlatformTransferSection(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        List<PlatformSpecificParams> paramsList = new ArrayList<>();

        int event = parser.getEventType();
        while (!(event == XmlPullParser.END_TAG
                && TextUtils.equals(parser.getName(), CROSS_PLATFORM_TRANSFER_SECTION))) {
            event = parser.next();
            if (event != XmlPullParser.START_TAG) {
                continue;
            }

            String tag = parser.getName();
            if (!TextUtils.equals(tag, PLATFORM_SPECIFIC_PARAMS_SECTION)) {
                skipSection(parser, tag);
                continue;
            }

            String bundleId = parser.getAttributeValue(null, ATTR_BUNDLE_ID);
            String teamId = parser.getAttributeValue(null, ATTR_TEAM_ID);
            String contentVersion = parser.getAttributeValue(null, ATTR_CONTENT_VERSION);
            paramsList.add(new PlatformSpecificParams(bundleId, teamId, contentVersion));
            Slog.d(
                    TAG,
                    "Found platform specific params (bundleId=\""
                            + bundleId
                            + "\", teamId=\""
                            + teamId
                            + "\")");
        }

        return paramsList;
    }
}
