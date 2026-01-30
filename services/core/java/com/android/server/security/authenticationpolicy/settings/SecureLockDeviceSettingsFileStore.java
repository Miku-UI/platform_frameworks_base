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

package com.android.server.security.authenticationpolicy.settings;

import android.annotation.NonNull;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import androidx.annotation.GuardedBy;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Class for I/O file operations to store / retrieve the state of secure lock device. This includes
 * writing the current state of settings and enabled state upon enabling secure lock device,
 * restoring the original state of settings upon disabling secure lock device, and restoring the
 * enabled/disabled state upon boot.
 */
public class SecureLockDeviceSettingsFileStore {
    private static final String TAG = "SLDSettingsStorageHandler";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String FILE_NAME = "secure_lock_device_state.xml";
    private static final String XML_TAG_ROOT = "secure-lock-device-state";
    private static final String XML_TAG_ENABLED = "enabled";
    private static final String XML_TAG_CLIENT_ID = "client-id";
    private static final String XML_TAG_ORIGINAL_SETTINGS = "original-settings";
    private static final String XML_TAG_SETTING = "setting";
    private static final String XML_ATTR_SETTING_KEY = "setting-key";
    private static final String XML_ATTR_SETTING_TYPE = "setting-type";
    private static final String XML_TAG_SETTING_ORIGINAL_VALUE = "setting-original-value";

    @NonNull private final Object mFileLock = new Object();
    @NonNull private final SecureLockDeviceSettingsManager mSettingsManager;
    private final Handler mHandler;
    @GuardedBy("mFileLock")
    private AtomicFile mStateFile;
    @GuardedBy("mFileLock")
    private boolean mIsEnabled = false;
    @GuardedBy("mFileLock")
    private int mClientUserId = UserHandle.USER_NULL;

    /**
     * Constructor for SecureLockDeviceSettingsFileStore.
     *
     * @param handler The handler to post tasks to write to the file in a background thread.
     * @param settingsManager The settings manager to manage the settings.
     */
    public SecureLockDeviceSettingsFileStore(Handler handler,
            @NonNull SecureLockDeviceSettingsManager settingsManager) {
        File systemDir = Environment.getDataSystemDirectory();
        File filePath = new File(systemDir, FILE_NAME);
        mStateFile = new AtomicFile(filePath);
        if (DEBUG) {
            Slog.d(TAG, "Secure lock device state file initialized at "
                    + filePath.getAbsolutePath());
        }

        mHandler = handler;
        mSettingsManager = settingsManager;
    }

    /**
     * Loads the persisted state (isEnabled and clientId) from the XML file.
     * If the file doesn't exist or is corrupted, it defaults to a disabled state.
     */
    void loadStateFromFile() {
        synchronized (mFileLock) {
            try {
                resetToDefaults();

                if (!mStateFile.getBaseFile().exists()) {
                    Slog.d(TAG, "Secure lock device state file does not exist.");
                    return;
                }

                try (FileInputStream fis = mStateFile.openRead()) {
                    TypedXmlPullParser parser = Xml.resolvePullParser(fis);
                    XmlUtils.beginDocument(parser, XML_TAG_ROOT);
                    int outerDepth = parser.getDepth();

                    while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                        String tagName = parser.getName();
                        switch (tagName) {
                            case XML_TAG_ENABLED ->
                                    mIsEnabled = Boolean.parseBoolean(parser.nextText());
                            case XML_TAG_CLIENT_ID ->
                                    mClientUserId = Integer.parseInt(parser.nextText());
                            case XML_TAG_ORIGINAL_SETTINGS -> {
                                if (mIsEnabled) {
                                    loadOriginalSettingsFromXml(parser, mSettingsManager);
                                } else {
                                    XmlUtils.skipCurrentTag(parser);
                                }
                            }
                            case null, default -> {
                                Slog.w(TAG, "Unknown tag in state file: " + tagName);
                                XmlUtils.skipCurrentTag(parser);
                            }
                        }
                    }
                    if (DEBUG) {
                        Slog.d(TAG, "Loaded state: isEnabled=" + mIsEnabled + ", clientId="
                                + mClientUserId);
                    }
                } catch (IOException | XmlPullParserException | NumberFormatException e) {
                    Slog.e(TAG, "Error reading secure lock device state file, resetting to "
                            + "defaults.", e);
                    resetToDefaults();
                }
            } catch (Exception e) {
                Slog.e(TAG, "Exception during loadStateFromFile(): ", e);
                resetToDefaults();
            }
        }
    }

    /**
     * Loads the original settings from the XML file.
     * @param parser The XML parser.
     * @param settingsManager The settings manager.
     * @throws IOException IOException encountered with provided file
     * @throws XmlPullParserException XmlPullParserException encountered while parsing XML
     */
    void loadOriginalSettingsFromXml(
            @NonNull TypedXmlPullParser parser,
            @NonNull SecureLockDeviceSettingsManager settingsManager)
            throws IOException, XmlPullParserException {
        int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            if (XML_TAG_SETTING.equals(parser.getName())) {
                String key = parser.getAttributeValue(null, XML_ATTR_SETTING_KEY);
                String type = parser.getAttributeValue(null, XML_ATTR_SETTING_TYPE);
                if (key == null || type == null) {
                    Slog.w(TAG, "Setting tag missing key attribute. Skipping tag: "
                            + parser.getPositionDescription());
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }

                Map<String, ManagedSetting<?>> managedSettings =
                        settingsManager.getManagedSettings();
                ManagedSetting<?> setting = managedSettings.get(key);

                int settingDepth = parser.getDepth();
                while (XmlUtils.nextElementWithin(parser, settingDepth)) {
                    if (XML_TAG_SETTING_ORIGINAL_VALUE.equals(parser.getName())) {
                        setting.deserializeAndRestoreOriginalValueFromXml(parser, key);
                        break; // Found and processed <setting-original-value>
                    } else { // Tag does not match <setting-original-value>
                        Slog.w(TAG, "Unexpected tag not matching "
                                + "<setting-original-value>: " + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
            } else { // Tag does not match <setting>
                Slog.w(TAG, "Unexpected tag not matching <setting>: " + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    /**
     * Writes to the secure lock device state atomic file.
     */
    void saveToFile(AtomicFile stateFile, boolean isEnabled, int clientUserId,
            SecureLockDeviceSettingsManager settingsManager) {
        FileOutputStream fos = null;
        try {
            fos = stateFile.startWrite();
            TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
            serializer.setOutput(fos, StandardCharsets.UTF_8.name());

            serializer.startDocument(null, true);
            serializer.startTag(null, XML_TAG_ROOT);

            serializer.startTag(null, XML_TAG_ENABLED);
            serializer.text(Boolean.toString(isEnabled));
            serializer.endTag(null, XML_TAG_ENABLED);

            serializer.startTag(null, XML_TAG_CLIENT_ID);
            serializer.text(Integer.toString(clientUserId));
            serializer.endTag(null, XML_TAG_CLIENT_ID);

            if (isEnabled) {
                Slog.i(TAG, "Saving state of settings because Secure Lock Device is enabled.");
                serializer.startTag(null, XML_TAG_ORIGINAL_SETTINGS);

                final Map<String, ManagedSetting<?>> settingsCopy =
                        settingsManager.getManagedSettings();

                settingsCopy.forEach((key, setting) -> {
                    try {
                        String settingKey = setting.getSettingKey();
                        String settingType = setting.getSettingType().name();
                        serializer.startTag(null, XML_TAG_SETTING);
                        serializer.attribute(null, XML_ATTR_SETTING_KEY, settingKey);
                        serializer.attribute(null, XML_ATTR_SETTING_TYPE, settingType);
                        setting.serializeOriginalValue(serializer);
                        serializer.endTag(null, XML_TAG_SETTING);
                        if (DEBUG) {
                            Slog.d(TAG, "Saved state: setting with key " + settingKey
                                    + ", type " + settingType);
                        }
                    } catch (Exception e) {
                        Slog.w(TAG, "Error serializing setting: " + key, e);
                    }
                });
                serializer.endTag(null, XML_TAG_ORIGINAL_SETTINGS);
            } else {
                Slog.i(TAG, "Not saving state of settings because secure lock device "
                        + "is disabled.");
            }
            serializer.endTag(null, XML_TAG_ROOT);
            serializer.endDocument();

            stateFile.finishWrite(fos);
            fos = null;

            if (DEBUG) {
                Slog.d(TAG, "Saved state: isEnabled=" + isEnabled + ", clientId="
                        + clientUserId);
            }
        } catch (IOException e) {
            Slog.e(TAG, "Error writing secure lock device state file: ", e);
            if (fos != null) {
                stateFile.failWrite(fos);
            }
        } finally {
            if (fos != null) {
                Slog.e(TAG, "Failure during write to secure lock device state file, "
                        + "closing file output stream.");
                stateFile.failWrite(fos);
            }
        }
    }

    /**
     * Updates the in-memory state and schedules a write to the persistent file.
     *
     * @param enabled The new enabled state.
     * @param userId  The userId associated with the client enabling secure lock device state,
     *                or USER_NULL if disabled.
     */
    void updateStateAndWriteToFile(boolean enabled, int userId) {
        boolean changed;
        synchronized (mFileLock) {
            changed = (mIsEnabled != enabled) || (mClientUserId != userId);

            if (!changed) {
                return;
            }
            mIsEnabled = enabled;
            mClientUserId = userId;
        }
        mHandler.post(() -> {
            synchronized (mFileLock) {
                saveToFile(mStateFile, mIsEnabled, mClientUserId, mSettingsManager);
            }
        });
    }

    /**
     * Whether Secure Lock Device is enabled.
     * @return True if enabled, false otherwise.
     */
    boolean retrieveSecureLockDeviceEnabled() {
        synchronized (mFileLock) {
            return mIsEnabled;
        }
    }

    /**
     * Retrieves the user id of the client that enabled secure lock device.
     * @return the user id of the client that enabled secure lock device, or USER_NULL if disabled.
     */
    int retrieveSecureLockDeviceClientId() {
        synchronized (mFileLock) {
            return mClientUserId;
        }
    }

    /**
     * Returns the atomic file for storing secure lock device settings state.
     * @return The atomic file.
     */
    AtomicFile getStateFile() {
        synchronized (mFileLock) {
            return mStateFile;
        }
    }

    /**
     * Overrides file for storing secure lock device settings state.
     * For test purposes only.
     * @param testFile The new file.
     */
    void overrideStateFile(File testFile) {
        synchronized (mFileLock) {
            mStateFile = new AtomicFile(testFile);

            if (DEBUG) {
                Slog.d(TAG, "SecureLockDeviceStore initialized at " + testFile.getAbsolutePath());
            }
        }
    }

    /**
     * Resets the current state to defaults.
     * This is used in the case of error parsing the file.
     */
    @GuardedBy("mFileLock")
    private void resetToDefaults() {
        mIsEnabled = false;
        mClientUserId = UserHandle.USER_NULL;
    }
}
