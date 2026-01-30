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

import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;
import static android.view.WindowManager.REMOVE_CONTENT_MODE_UNDEFINED;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WindowConfiguration;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.wm.DisplayWindowSettings.SettingsProvider.SettingsEntry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A helper class that handles XML serialization and parsing for display window settings.
 */
class DisplayWindowSettingsXmlHelper {
    private static final String TAG = "DWSXmlHelper";
    static final int IDENTIFIER_UNIQUE_ID = 0;
    static final int IDENTIFIER_PORT = 1;
    @IntDef(prefix = { "IDENTIFIER_" }, value = {
            IDENTIFIER_UNIQUE_ID,
            IDENTIFIER_PORT,
    })
    @interface DisplayIdentifierType {}



    /**
     * Writes display window settings to an OutputStream from a FileData object.
     * @param stream The output stream to write the settings to.
     * @param data The {@link FileData} object containing the settings to write.
     * @param forBackup If {@code true}, settings that should not be restored from a backup
     *                  (e.g., forced dimensions and density) will be omitted from the output.
     *  @return {@code true} if the write was successful, {@code false} otherwise.
     */
    static boolean writeSettings(@NonNull OutputStream stream,
            @NonNull FileData data, boolean forBackup) {
        boolean success = false;
        try {
            TypedXmlSerializer out = Xml.resolveSerializer(stream);
            out.setOutput(stream, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);

            out.startTag(null, "display-settings");

            out.startTag(null, "config");
            out.attributeInt(null, "identifier", data.mIdentifierType);
            out.endTag(null, "config");

            for (Map.Entry<String, SettingsEntry> entry
                    : data.mSettings.entrySet()) {
                String displayIdentifier = entry.getKey();
                SettingsEntry settingsEntry = entry.getValue();
                if (settingsEntry.isEmpty()) {
                    continue;
                }

                out.startTag(null, "display");
                out.attribute(null, "name", displayIdentifier);

                if (settingsEntry.mUserRotationMode != null) {
                    out.attributeInt(null, "userRotationMode",
                            settingsEntry.mUserRotationMode);
                }
                if (settingsEntry.mUserRotation != null) {
                    out.attributeInt(null, "userRotation",
                            settingsEntry.mUserRotation);
                }
                if (settingsEntry.mForcedDensityRatio != 0.0f) {
                    out.attributeFloat(null, "forcedDensityRatio",
                            settingsEntry.mForcedDensityRatio);
                }
                if (!forBackup) {
                    if (settingsEntry.mWindowingMode
                            != WindowConfiguration.WINDOWING_MODE_UNDEFINED) {
                        out.attributeInt(null, "windowingMode", settingsEntry.mWindowingMode);
                    }
                    if (settingsEntry.mForcedWidth != 0 && settingsEntry.mForcedHeight != 0) {
                        out.attributeInt(null, "forcedWidth", settingsEntry.mForcedWidth);
                        out.attributeInt(null, "forcedHeight", settingsEntry.mForcedHeight);
                    }
                    if (settingsEntry.mForcedDensity != 0) {
                        out.attributeInt(null, "forcedDensity", settingsEntry.mForcedDensity);
                    }
                    if (settingsEntry.mForcedScalingMode != null) {
                        out.attributeInt(null, "forcedScalingMode",
                                settingsEntry.mForcedScalingMode);
                    }
                    if (settingsEntry.mRemoveContentMode != REMOVE_CONTENT_MODE_UNDEFINED) {
                        out.attributeInt(null, "removeContentMode",
                                settingsEntry.mRemoveContentMode);
                    }
                    if (settingsEntry.mShouldShowWithInsecureKeyguard != null) {
                        out.attributeBoolean(null, "shouldShowWithInsecureKeyguard",
                                settingsEntry.mShouldShowWithInsecureKeyguard);
                    }
                    if (settingsEntry.mShouldShowSystemDecors != null) {
                        out.attributeBoolean(null, "shouldShowSystemDecors",
                                settingsEntry.mShouldShowSystemDecors);
                    }
                    if (settingsEntry.mImePolicy != null) {
                        out.attributeInt(null, "imePolicy", settingsEntry.mImePolicy);
                    }
                    if (settingsEntry.mFixedToUserRotation != null) {
                        out.attributeInt(null, "fixedToUserRotation",
                                settingsEntry.mFixedToUserRotation);
                    }
                    if (settingsEntry.mIgnoreOrientationRequest != null) {
                        out.attributeBoolean(null, "ignoreOrientationRequest",
                                settingsEntry.mIgnoreOrientationRequest);
                    }
                    if (settingsEntry.mIgnoreDisplayCutout != null) {
                        out.attributeBoolean(null, "ignoreDisplayCutout",
                                settingsEntry.mIgnoreDisplayCutout);
                    }
                    if (settingsEntry.mDontMoveToTop != null) {
                        out.attributeBoolean(null, "dontMoveToTop",
                                settingsEntry.mDontMoveToTop);
                    }
                    if (settingsEntry.mIsHomeSupported != null) {
                        out.attributeBoolean(null, "isHomeSupported",
                                settingsEntry.mIsHomeSupported);
                    }
                }
                out.endTag(null, "display");
            }

            out.endTag(null, "display-settings");
            out.endDocument();
            success = true;
        } catch (IOException e) {
            Slog.w(TAG, "Failed to write display window settings.", e);
        }
        return success;
    }

    /**
     * Reads display window settings from an {@link InputStream}, filters out device-specific
     * attributes that should not be backed up, and returns the result as a byte array.
     * <p>
     * This method is specifically designed for backup operations. It parses the full settings
     * file and then re-serializes it, omitting attributes like forced width, height, and
     * density, which are not suitable for restoration on a different device.
     *
     * @param stream The input stream to read the settings from. This stream is closed by the
     *               method upon completion.
     * @return A byte array containing the filtered XML data, ready for backup.
     */
    public static byte[] readAndFilterSettings(InputStream stream) {
        FileData data = FileData.readSettings(stream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeSettings(byteArrayOutputStream, data, /* forBackup= */ true);
        return byteArrayOutputStream.toByteArray();
    }

    static final class FileData {
        int mIdentifierType;
        @NonNull
        final Map<String, SettingsEntry> mSettings = new ArrayMap<>();
        FileData() {}

        /**
         * Reads display window settings and parses XML data from an InputStream and populates and
         * returns a FileData object with the extracted settings.
         *
         * @param stream The input stream to read the settings from. This stream is closed by the
         *               method upon completion or failure.
         * @return A {@link FileData} object containing the parsed settings. If parsing fails for
         *         any reason, an empty {@link FileData} object is returned. This method never
         *         returns {@code null}.
         **/
        static @NonNull FileData readSettings(@NonNull InputStream stream) {
            FileData fileData = new FileData();
            boolean success = false;
            try {
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
                        readDisplay(parser, fileData);
                    } else if (tagName.equals("config")) {
                        readConfig(parser, fileData);
                    } else {
                        Slog.w(TAG, "Unknown element under <display-settings>: "
                                + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
                success = true;
            } catch (IllegalStateException | NullPointerException | NumberFormatException
                     | XmlPullParserException | IOException | IndexOutOfBoundsException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } finally {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
            if (!success) {
                fileData.mSettings.clear();
            }
            return fileData;
        }

        private static void readDisplay(@NonNull TypedXmlPullParser parser,
                @NonNull FileData fileData)
                throws NumberFormatException, XmlPullParserException, IOException {
            String name = parser.getAttributeValue(null, "name");
            if (name != null) {
                SettingsEntry settingsEntry = new SettingsEntry();
                settingsEntry.mWindowingMode = getIntAttribute(parser, "windowingMode",
                        WindowConfiguration.WINDOWING_MODE_UNDEFINED /* defaultValue */);
                settingsEntry.mUserRotationMode = getIntegerAttribute(parser, "userRotationMode",
                        null /* defaultValue */);
                settingsEntry.mUserRotation = getIntegerAttribute(parser, "userRotation",
                        null /* defaultValue */);
                settingsEntry.mForcedWidth = getIntAttribute(parser, "forcedWidth",
                        0 /* defaultValue */);
                settingsEntry.mForcedHeight = getIntAttribute(parser, "forcedHeight",
                        0 /* defaultValue */);
                settingsEntry.mForcedDensity = getIntAttribute(parser, "forcedDensity",
                        0 /* defaultValue */);
                settingsEntry.mForcedDensityRatio = parser.getAttributeFloat(null,
                        "forcedDensityRatio", 0.0f /* defaultValue */);
                settingsEntry.mForcedScalingMode = getIntegerAttribute(parser, "forcedScalingMode",
                        null /* defaultValue */);
                settingsEntry.mRemoveContentMode = getIntAttribute(parser, "removeContentMode",
                        REMOVE_CONTENT_MODE_UNDEFINED /* defaultValue */);
                settingsEntry.mShouldShowWithInsecureKeyguard = getBooleanAttribute(parser,
                        "shouldShowWithInsecureKeyguard", null /* defaultValue */);
                settingsEntry.mShouldShowSystemDecors = getBooleanAttribute(parser,
                        "shouldShowSystemDecors", null /* defaultValue */);
                final Boolean shouldShowIme = getBooleanAttribute(parser, "shouldShowIme",
                        null /* defaultValue */);
                if (shouldShowIme != null) {
                    settingsEntry.mImePolicy = shouldShowIme ? DISPLAY_IME_POLICY_LOCAL
                            : DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
                } else {
                    settingsEntry.mImePolicy = getIntegerAttribute(parser, "imePolicy",
                            null /* defaultValue */);
                }
                settingsEntry.mFixedToUserRotation = getIntegerAttribute(parser,
                        "fixedToUserRotation", null /* defaultValue */);
                settingsEntry.mIgnoreOrientationRequest = getBooleanAttribute(parser,
                        "ignoreOrientationRequest", null /* defaultValue */);
                settingsEntry.mIgnoreDisplayCutout = getBooleanAttribute(parser,
                        "ignoreDisplayCutout", null /* defaultValue */);
                settingsEntry.mDontMoveToTop = getBooleanAttribute(parser,
                        "dontMoveToTop", null /* defaultValue */);
                settingsEntry.mIsHomeSupported = getBooleanAttribute(parser,
                        "isHomeSupported", null /* defaultValue */);
                fileData.mSettings.put(name, settingsEntry);
            }
            XmlUtils.skipCurrentTag(parser);
        }

        private static void readConfig(@NonNull TypedXmlPullParser parser,
                @NonNull FileData fileData) throws NumberFormatException, XmlPullParserException,
                IOException {
            fileData.mIdentifierType = getIntAttribute(parser, "identifier",
                    IDENTIFIER_UNIQUE_ID);
            XmlUtils.skipCurrentTag(parser);
        }

        private static int getIntAttribute(@NonNull TypedXmlPullParser parser, @NonNull String name,
                int defaultValue) {
            return parser.getAttributeInt(null, name, defaultValue);
        }

        @Nullable
        private static Integer getIntegerAttribute(@NonNull TypedXmlPullParser parser,
                @NonNull String name, @Nullable Integer defaultValue) {
            try {
                return parser.getAttributeInt(null, name);
            } catch (Exception ignored) {
                return defaultValue;
            }
        }

        @Nullable
        private static Boolean getBooleanAttribute(@NonNull TypedXmlPullParser parser,
                @NonNull String name, @Nullable Boolean defaultValue) {
            try {
                return parser.getAttributeBoolean(null, name);
            } catch (Exception ignored) {
                return defaultValue;
            }
        }

        @Override
        public String toString() {
            return "FileData{"
                    + "mIdentifierType=" + mIdentifierType
                    + ", mSettings=" + mSettings
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileData that = (FileData) o;
            return mIdentifierType == that.mIdentifierType
                    && mSettings.equals(that.mSettings);
        }
    }
}
