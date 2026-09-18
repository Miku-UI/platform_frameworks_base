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

import static android.os.UserHandle.USER_SYSTEM;
import static android.view.Display.TYPE_OVERLAY;
import static android.view.Display.TYPE_VIRTUAL;

import static com.android.server.wm.DisplayWindowSettingsXmlHelper.DisplayIdentifierType;
import static com.android.server.wm.DisplayWindowSettingsXmlHelper.IDENTIFIER_PORT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_DISPLAY_SETTINGS;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.backup.BackupManager;
import android.content.Context;
import android.os.Environment;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.LruCache;
import android.util.Slog;
import android.view.DisplayAddress;
import android.view.DisplayInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wm.DisplayWindowSettings.SettingsProvider;
import com.android.server.wm.DisplayWindowSettingsXmlHelper.FileData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Implementation of {@link SettingsProvider} that reads the base settings provided in a display
 * settings file stored in /vendor/etc and then overlays those values with the settings provided in
 * /data/system.
 *
 * @see DisplayWindowSettings
 */
class DisplayWindowSettingsProvider implements SettingsProvider {
    /** Logging tag, abbreviated for Logcat tag length limit if {@code TAG_WITH_CLASS_NAME}. */
    private static final String TAG = TAG_WITH_CLASS_NAME ? "DispWinSettingsProvider" : TAG_WM;

    private static final String DISPLAY_SETTINGS_FILENAME = "display_settings.xml";
    private static final String VENDOR_DISPLAY_SETTINGS_FILE_PATH =
            "etc/" + DISPLAY_SETTINGS_FILENAME;
    private static final String WM_DISPLAY_COMMIT_TAG = "wm-displays";
    /**
     * Maximum number of display settings entries cached in LruCache. When limit is reached,
     * least recently used entries are evicted instead of proactively removing stale settings
     * from dynamic display changes (user switching, system restarts). Aligns with DisplayTopology's
     * LRU approach using DisplayTopologyXmlStore#MAX_NUMBER_OF_TOPOLOGIES.
     */
    private static final int MAX_NUMBER_OF_DISPLAY_SETTINGS = 100;

    /** Interface that allows reading the display window settings. */
    interface ReadableSettingsStorage {
        InputStream openRead() throws IOException;
    }

    /** Interface that allows reading and writing the display window settings. */
    interface WritableSettingsStorage extends ReadableSettingsStorage {
        OutputStream startWrite() throws IOException;
        void finishWrite(OutputStream os, boolean success);
    }

    @NonNull
    private ReadableSettings mBaseSettings;
    @NonNull
    private WritableSettings mOverrideSettings;
    @NonNull
    private final BackupManager mBackupManager;

    DisplayWindowSettingsProvider(@NonNull Context context) {
        this(new AtomicFileStorage(getVendorSettingsFile()),
                new AtomicFileStorage(getOverrideSettingsFileForUser(USER_SYSTEM)),
                new BackupManager(context));
    }

    @VisibleForTesting
    DisplayWindowSettingsProvider(@NonNull ReadableSettingsStorage baseSettingsStorage,
            @NonNull WritableSettingsStorage overrideSettingsStorage,
            @NonNull BackupManager backupManager) {
        setBaseSettingsStorage(baseSettingsStorage);
        setOverrideSettingsStorage(overrideSettingsStorage);
        mBackupManager = backupManager;
    }

    /**
     * Overrides the path for the file that should be used to read base settings. If {@code null} is
     * passed the default base settings file path will be used.
     *
     * @see #VENDOR_DISPLAY_SETTINGS_FILE_PATH
     */
    void setBaseSettingsFilePath(@Nullable String path) {
        AtomicFile settingsFile;
        File file = path != null ? new File(path) : null;
        if (file != null && file.exists()) {
            settingsFile = new AtomicFile(file, WM_DISPLAY_COMMIT_TAG);
        } else {
            Slog.w(TAG, "Base display settings " + path + " does not exist, "
                    + "using vendor defaults");
            settingsFile = getVendorSettingsFile();
        }
        setBaseSettingsStorage(new AtomicFileStorage(settingsFile));
    }

    /**
     * Overrides the storage that should be used to read base settings.
     *
     * @see #setBaseSettingsFilePath(String)
     */
    @VisibleForTesting
    void setBaseSettingsStorage(@NonNull ReadableSettingsStorage baseSettingsStorage) {
        mBaseSettings = new ReadableSettings("base", baseSettingsStorage);
    }

    /**
     * Overrides the storage that should be used to save override settings for a user.
     *
     * @see #getOverrideSettingsFileForUser
     */
    void setOverrideSettingsForUser(@UserIdInt int userId) {
        final AtomicFile settingsFile = getOverrideSettingsFileForUser(userId);
        setOverrideSettingsStorage(new AtomicFileStorage(settingsFile));
    }

    /**
     * Overrides the storage that should be used to save override settings.
     *
     * @see #setOverrideSettingsForUser(int)
     */
    @VisibleForTesting
    void setOverrideSettingsStorage(@NonNull WritableSettingsStorage overrideSettingsStorage) {
        mOverrideSettings = new WritableSettings("override", overrideSettingsStorage);
    }

    @Override
    @NonNull
    public SettingsEntry getSettings(@NonNull DisplayInfo info) {
        SettingsEntry baseSettings = mBaseSettings.getSettingsEntry(info);
        SettingsEntry overrideSettings = mOverrideSettings.getOrCreateSettingsEntry(info);
        if (baseSettings == null) {
            return new SettingsEntry(overrideSettings);
        } else {
            SettingsEntry mergedSettings = new SettingsEntry(baseSettings);
            mergedSettings.updateFrom(overrideSettings);
            return mergedSettings;
        }
    }

    @Override
    @NonNull
    public SettingsEntry getOverrideSettings(@NonNull DisplayInfo info) {
        return new SettingsEntry(mOverrideSettings.getOrCreateSettingsEntry(info));
    }

    @Override
    public void updateOverrideSettings(@NonNull DisplayInfo info,
            @NonNull SettingsEntry overrides) {
        mOverrideSettings.updateSettingsEntry(info, overrides);
        mBackupManager.dataChanged();
    }

    @Override
    public void onDisplayRemoved(@NonNull DisplayInfo info) {
        mOverrideSettings.onDisplayRemoved(info);
    }

    @Override
    public void clearDisplaySettings(@NonNull DisplayInfo info) {
        mOverrideSettings.clearDisplaySettings(info);
        mBackupManager.dataChanged();
    }

    @VisibleForTesting
    int getOverrideSettingsSize() {
        return mOverrideSettings.mSettings.size();
    }

    /**
     * Class that allows reading {@link SettingsEntry entries} from a
     * {@link ReadableSettingsStorage}.
     */
    private static class ReadableSettings {
        /** The name of these settings, used for logging. */
        @NonNull
        protected final String mName;
        /**
         * The preferred type of a display identifier to use when storing and retrieving entries
         * from the settings entries.
         *
         * @see #getIdentifier(DisplayInfo)
         */
        @DisplayIdentifierType
        protected int mIdentifierType;
        @NonNull
        protected final LruCache<String, SettingsEntry> mSettings =
                new LruCache<>(MAX_NUMBER_OF_DISPLAY_SETTINGS);

        ReadableSettings(@NonNull String name, @NonNull ReadableSettingsStorage settingsStorage) {
            mName = name;
            loadSettings(settingsStorage);
        }

        @Nullable
        final SettingsEntry getSettingsEntry(@NonNull DisplayInfo info) {
            final String identifier = getIdentifier(info);
            SettingsEntry settings;
            // Try to get corresponding settings using preferred identifier for the current config.
            if ((settings = mSettings.get(identifier)) != null) {
                return settings;
            }
            // Else, fall back to the display name.
            if (info.name != null && (settings = mSettings.get(info.name)) != null) {
                // Found an entry stored with old identifier.
                mSettings.remove(info.name);
                mSettings.put(identifier, settings);
                return settings;
            }
            return null;
        }

        /** Gets the identifier of choice for the current config. */
        @NonNull
        protected final String getIdentifier(@NonNull DisplayInfo displayInfo) {
            if (mIdentifierType == IDENTIFIER_PORT && displayInfo.address != null) {
                // Config suggests using port as identifier for physical displays.
                if (displayInfo.address.getPort() != DisplayAddress.INVALID_PORT) {
                    return "port:" + displayInfo.address.getPort();
                }
            }
            return displayInfo.uniqueId;
        }

        private void loadSettings(@NonNull ReadableSettingsStorage settingsStorage) {
            final InputStream stream;
            try {
                stream = settingsStorage.openRead();
            } catch (IOException e) {
                Slog.i(TAG, "No existing " + mName + " display settings at " + settingsStorage
                        + ", starting empty");
                return;
            }

            final FileData fileData = FileData.readSettings(stream);
            mIdentifierType = fileData.mIdentifierType;
            for (final Map.Entry<String, SettingsEntry> entry : fileData.mSettings.entrySet()) {
                mSettings.put(entry.getKey(), entry.getValue());
            }
            if (DEBUG_DISPLAY_SETTINGS) {
                Slog.v(TAG, "Loaded " + mName + " display settings from " + settingsStorage);
            }
        }
    }

    /**
     * Class that allows reading {@link SettingsEntry entries} from, and writing entries to, a
     * {@link WritableSettingsStorage}.
     */
    private static final class WritableSettings extends ReadableSettings {
        @NonNull
        private final WritableSettingsStorage mSettingsStorage;
        @NonNull
        private final ArraySet<String> mNonPhysicalDisplayIdentifiers = new ArraySet<>();

        WritableSettings(@NonNull String name, @NonNull WritableSettingsStorage settingsStorage) {
            super(name, settingsStorage);
            mSettingsStorage = settingsStorage;
        }

        @NonNull
        SettingsEntry getOrCreateSettingsEntry(@NonNull DisplayInfo info) {
            final String identifier = getIdentifier(info);
            SettingsEntry settings;
            // Try to get corresponding settings using preferred identifier for the current config.
            if ((settings = mSettings.get(identifier)) != null) {
                return settings;
            }
            // Else, fall back to the display name.
            if (info.name != null && (settings = mSettings.get(info.name)) != null) {
                // Found an entry stored with old identifier.
                mSettings.remove(info.name);
                mSettings.put(identifier, settings);
                writeSettings();
                return settings;
            }

            settings = new SettingsEntry();
            mSettings.put(identifier, settings);
            if (info.type == TYPE_VIRTUAL || info.type == TYPE_OVERLAY) {
                // Keep track of virtual display. We don't want to write virtual display settings to
                // file.
                mNonPhysicalDisplayIdentifiers.add(identifier);
            }
            return settings;
        }

        void updateSettingsEntry(@NonNull DisplayInfo info, @NonNull SettingsEntry settings) {
            final SettingsEntry overrideSettings = getOrCreateSettingsEntry(info);
            final boolean changed = overrideSettings.setTo(settings);
            if (changed && info.type != TYPE_VIRTUAL && info.type != TYPE_OVERLAY) {
                writeSettings();
            }
        }

        void onDisplayRemoved(@NonNull DisplayInfo info) {
            final String identifier = getIdentifier(info);
            if (mSettings.get(identifier) == null) {
                return;
            }
            if (mNonPhysicalDisplayIdentifiers.remove(identifier)
                    || mSettings.get(identifier).isEmpty()) {
                // Don't keep track of virtual display or empty settings to avoid growing the cached
                // map.
                mSettings.remove(identifier);
            }
        }

        void clearDisplaySettings(@NonNull DisplayInfo info) {
            final String identifier = getIdentifier(info);
            mSettings.remove(identifier);
            mNonPhysicalDisplayIdentifiers.remove(identifier);
        }

        private void writeSettings() {
            final FileData fileData = new FileData();
            fileData.mIdentifierType = mIdentifierType;
            for (final Map.Entry<String, SettingsEntry> entry : mSettings.snapshot().entrySet()) {
                final String identifier = entry.getKey();
                if (mNonPhysicalDisplayIdentifiers.contains(identifier)) {
                    // Do not write virtual display settings to file.
                    continue;
                }
                fileData.mSettings.put(identifier, entry.getValue());
            }
            OutputStream stream = null;
            boolean success = false;
            try {
                stream = mSettingsStorage.startWrite();
                success = DisplayWindowSettingsXmlHelper.writeSettings(stream, fileData, false);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write " + mName + " display settings at " + this, e);
            } finally {
                if (stream != null) {
                    mSettingsStorage.finishWrite(stream, success);
                }
                if (DEBUG_DISPLAY_SETTINGS && success) {
                    Slog.v(TAG, "Wrote " + mName + " display settings at " + this);
                }
            }
        }
    }

    @NonNull
    private static AtomicFile getVendorSettingsFile() {
        // First look under product path for treblized builds.
        File vendorFile = new File(Environment.getProductDirectory(),
                VENDOR_DISPLAY_SETTINGS_FILE_PATH);
        if (!vendorFile.exists()) {
            // Try and look in vendor path.
            vendorFile = new File(Environment.getVendorDirectory(),
                VENDOR_DISPLAY_SETTINGS_FILE_PATH);
        }
        return new AtomicFile(vendorFile, WM_DISPLAY_COMMIT_TAG);
    }

    /**
     * Returns the file used to store display settings for a given user.
     *
     * <p>The path is {@code /data/system/display_settings.xml} for the system user
     * ({@code USER_SYSTEM}), used for the owner on phones/tablets and the login screen on desktop.
     * For other users, it's {@code /data/system_de/<user_id>/display_settings.xml} when the flag is
     * enabled, or {@code /data/system_ce/<user_id>/system/display_settings.xml} otherwise.
     *
     * @param userId The user to retrieve the settings file for.
     * @return The file for storing override display settings.
     */
    @NonNull
    static AtomicFile getOverrideSettingsFileForUser(@UserIdInt int userId) {
        final File directory;
        if (userId == USER_SYSTEM) {
            directory = new File(Environment.getDataDirectory(), "system");
        } else {
            directory = Environment.getDataSystemDeDirectory(userId);
        }
        final File overrideSettingsFile = new File(directory, DISPLAY_SETTINGS_FILENAME);
        return new AtomicFile(overrideSettingsFile, WM_DISPLAY_COMMIT_TAG);
    }

    /** Provides I/O stream access to the immutable {@code mAtomicFile} reference. */
    private record AtomicFileStorage(@NonNull AtomicFile mAtomicFile) implements
            WritableSettingsStorage {

        @Override
        public InputStream openRead() throws FileNotFoundException {
            return mAtomicFile.openRead();
        }

        @Override
        public OutputStream startWrite() throws IOException {
            return mAtomicFile.startWrite();
        }

        @Override
        public void finishWrite(OutputStream os, boolean success) {
            if (!(os instanceof FileOutputStream fos)) {
                throw new IllegalArgumentException("Unexpected OutputStream as argument: " + os);
            }
            if (success) {
                mAtomicFile.finishWrite(fos);
            } else {
                mAtomicFile.failWrite(fos);
            }
        }

        @Override
        public String toString() {
            return "AtomicFileStorage[" + mAtomicFile.getBaseFile() + "]";
        }
    }
}
