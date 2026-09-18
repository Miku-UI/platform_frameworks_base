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

import static android.hardware.display.DisplayManager.EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DEFAULT;
import static android.hardware.display.DisplayManager.DEFAULT_HDR_PREFERENCE;

import static com.android.server.display.BrightnessMappingStrategy.INVALID_NITS;
import static com.android.server.display.persistence.DisplayState.BRIGHTNESS_CONFIGURATION_KEY;
import static com.android.server.display.persistence.DisplayState.BRIGHTNESS_KEY;
import static com.android.server.display.persistence.DisplayState.COLOR_MODE_KEY;
import static com.android.server.display.persistence.DisplayState.CONNECTION_PREFERENCE_KEY;
import static com.android.server.display.persistence.DisplayState.DISPLAY_MODE_KEY;
import static com.android.server.display.persistence.DisplayState.HDR_PREFERENCE_KEY;
import static com.android.server.display.persistence.PersistentDataStore.BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY;
import static com.android.server.display.persistence.PersistentDataStore.REMEMBERED_WIFI_DISPLAYS_KEY;
import static com.android.server.display.persistence.PersistentDataStore.STABLE_DEVICE_VALUES_KEY;

import android.graphics.Point;
import android.hardware.display.BrightnessConfiguration;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.view.Display;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.DisplayDevice;
import com.android.server.display.LegacyPersistentDataStore;
import com.android.server.display.feature.flags.Flags;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Delegates calls to the old or new implementation of Persistent Data Store based on the feature
 * flag status.
 * Setters call both stores so that both stores are maintained and the feature flag can be easily
 * reverted.
 */
public final class PersistentDataStoreDelegate {
    private final LegacyPersistentDataStore mOldStore;

    @Nullable
    private PersistentDataStore mNewStore;

    /**
     * @see LegacyPersistentDataStore#LegacyPersistentDataStore()
     */
    public PersistentDataStoreDelegate() {
        mOldStore = new LegacyPersistentDataStore();
        Injector injectorForNewStore = new Injector(PersistentDataStore.FILE_NAME);
        // TODO(b/494473504): Migrate the old store to the new store if the file for the new
        //  store does not exist
        if (Flags.refactorPersistentDataStore() && injectorForNewStore.mAtomicFile.exists()) {
            mNewStore = new PersistentDataStore(injectorForNewStore);
        }
    }

    @VisibleForTesting
    PersistentDataStoreDelegate(Injector injector) {
        mOldStore = new LegacyPersistentDataStore(injector);
        if (Flags.refactorPersistentDataStore()) {
            mNewStore = new PersistentDataStore(injector);
        }
    }

    /**
     * @see LegacyPersistentDataStore#loadIfNeeded()
     */
    public void loadIfNeeded() {
        mOldStore.loadIfNeeded();
        if (mNewStore != null) {
            mNewStore.loadIfNeeded();
        }
    }

    /**
     * @see LegacyPersistentDataStore#saveIfNeeded()
     */
    public void saveIfNeeded() {
        mOldStore.saveIfNeeded();
        if (mNewStore != null) {
            mNewStore.saveIfNeeded();
        }
    }

    /**
     * @see LegacyPersistentDataStore#removeUserData(int)
     */
    public void removeUserData(int userSerial) {
        mOldStore.removeUserData(userSerial);
        if (mNewStore != null) {
            mNewStore.removeUserData(userSerial);
        }
    }

    /**
     * @see LegacyPersistentDataStore#dump(PrintWriter)
     */
    public void dump(PrintWriter pw) {
        if (mNewStore != null) {
            mNewStore.dump(pw);
            pw.println();
        }
        mOldStore.dump(pw);
    }

    /**
     * @see LegacyPersistentDataStore#getRememberedWifiDisplay(String)
     */
    @Nullable
    public WifiDisplay getRememberedWifiDisplay(String deviceAddress) {
        if (mNewStore != null) {
            Map<String, WifiDisplay> displays = mNewStore.getGlobalProperty(
                    REMEMBERED_WIFI_DISPLAYS_KEY);
            return displays != null ? displays.get(deviceAddress) : null;
        } else {
            return mOldStore.getRememberedWifiDisplay(deviceAddress);
        }
    }

    /**
     * @see LegacyPersistentDataStore#getRememberedWifiDisplays()
     */
    public WifiDisplay[] getRememberedWifiDisplays() {
        if (mNewStore != null) {
            Map<String, WifiDisplay> persistedDisplays = mNewStore.getGlobalProperty(
                    REMEMBERED_WIFI_DISPLAYS_KEY);
            if (persistedDisplays == null) {
                return WifiDisplay.EMPTY_ARRAY;
            }
            Iterator<WifiDisplay> iterator = persistedDisplays.values().iterator();
            WifiDisplay[] displays = new WifiDisplay[persistedDisplays.size()];
            for (int i = 0; i < persistedDisplays.size(); i++) {
                displays[i] = iterator.next();
            }
            return displays;
        } else {
            return mOldStore.getRememberedWifiDisplays();
        }
    }

    /**
     * @see LegacyPersistentDataStore#applyWifiDisplayAlias(WifiDisplay)
     */
    public WifiDisplay applyWifiDisplayAlias(WifiDisplay display) {
        if (mNewStore != null) {
            if (display == null) {
                return null;
            }
            String alias = null;
            Map<String, WifiDisplay> displays = mNewStore.getGlobalProperty(
                    REMEMBERED_WIFI_DISPLAYS_KEY);
            if (displays != null && displays.containsKey(display.getDeviceAddress())) {
                alias = displays.get(display.getDeviceAddress()).getDeviceAlias();
            }
            if (!Objects.equals(display.getDeviceAlias(), alias)) {
                return new WifiDisplay(display.getDeviceAddress(), display.getDeviceName(),
                        alias, display.isAvailable(), display.canConnect(), display.isRemembered());
            } else {
                return display;
            }
        } else {
            return mOldStore.applyWifiDisplayAlias(display);
        }
    }

    /**
     * Applies the saved alias to each Wi-Fi display in the given array.
     * @param displays The array of displays to apply aliases to.
     */
    public void applyWifiDisplayAliases(WifiDisplay[] displays) {
        for (int i = 0; i < displays.length; i++) {
            displays[i] = applyWifiDisplayAlias(displays[i]);
        }
    }

    /**
     * @see LegacyPersistentDataStore#rememberWifiDisplay(WifiDisplay)
     */
    public boolean rememberWifiDisplay(WifiDisplay display) {
        boolean result = mOldStore.rememberWifiDisplay(display);
        if (mNewStore != null) {
            result |= mNewStore.addToGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY,
                    display.getDeviceAddress(), display);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#forgetWifiDisplay(String)
     */
    public boolean forgetWifiDisplay(String deviceAddress) {
        boolean result = mOldStore.forgetWifiDisplay(deviceAddress);
        if (mNewStore != null) {
            mNewStore.removeFromGlobalPropertyMap(REMEMBERED_WIFI_DISPLAYS_KEY, deviceAddress);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getColorMode(DisplayDevice)
     */
    public int getColorMode(DisplayDevice device) {
        if (mNewStore != null) {
            Integer colorMode = mNewStore.getDisplayProperty(UserHandle.USER_SERIAL_SYSTEM, device,
                    COLOR_MODE_KEY
            );
            return colorMode != null ? colorMode : Display.COLOR_MODE_INVALID;
        } else {
            return mOldStore.getColorMode(device);
        }
    }

    /**
     * @see LegacyPersistentDataStore#setColorMode(DisplayDevice, int)
     */
    public boolean setColorMode(DisplayDevice device, int colorMode) {
        boolean result = mOldStore.setColorMode(device, colorMode);
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(UserHandle.USER_SERIAL_SYSTEM, device,
                    COLOR_MODE_KEY,
                    colorMode);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getBrightness(DisplayDevice, int)
     */
    public float getBrightness(DisplayDevice device, int userSerial) {
        if (mNewStore != null) {
            Float brightness = mNewStore.getDisplayProperty(userSerial, device, BRIGHTNESS_KEY);
            return brightness != null ? brightness : PowerManager.BRIGHTNESS_INVALID_FLOAT;
        } else {
            return mOldStore.getBrightness(device, userSerial);
        }
    }

    /**
     * @see LegacyPersistentDataStore#setBrightness(DisplayDevice, float, int)
     */
    public boolean setBrightness(DisplayDevice displayDevice, float brightness, int userSerial) {
        boolean result = mOldStore.setBrightness(displayDevice, brightness, userSerial);
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(userSerial, displayDevice, BRIGHTNESS_KEY,
                    brightness);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getBrightnessNitsForDefaultDisplay()
     */
    public float getBrightnessNitsForDefaultDisplay() {
        if (mNewStore != null) {
            Float nits = mNewStore.getGlobalProperty(BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY);
            return nits != null ? nits : INVALID_NITS;
        } else {
            return mOldStore.getBrightnessNitsForDefaultDisplay();
        }
    }

    /**
     * @see LegacyPersistentDataStore#setBrightnessNitsForDefaultDisplay(float)
     */
    public boolean setBrightnessNitsForDefaultDisplay(float nits) {
        boolean result = mOldStore.setBrightnessNitsForDefaultDisplay(nits);
        if (mNewStore != null) {
            return mNewStore.setGlobalProperty(BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY, nits);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getUserPreferredResolution(DisplayDevice)
     * @see LegacyPersistentDataStore#getUserPreferredRefreshRate(DisplayDevice)
     */
    @Nullable
    public DisplayMode getUserPreferredDisplayMode(int userSerial, DisplayDevice device) {
        if (mNewStore != null) {
            return mNewStore.getDisplayProperty(userSerial, device, DISPLAY_MODE_KEY);
        } else {
            Point resolution = mOldStore.getUserPreferredResolution(device);
            float refreshRate = mOldStore.getUserPreferredRefreshRate(device);
            if (resolution == null && refreshRate == Display.INVALID_DISPLAY_REFRESH_RATE) {
                return null;
            } else if (resolution == null) {
                resolution = new Point(Display.INVALID_DISPLAY_WIDTH,
                        Display.INVALID_DISPLAY_HEIGHT);
            }
            return new DisplayMode(resolution.x, resolution.y, refreshRate);
        }
    }

    /**
     * @see LegacyPersistentDataStore#setUserPreferredResolution(DisplayDevice, int, int)
     * @see LegacyPersistentDataStore#setUserPreferredRefreshRate(DisplayDevice, float)
     */
    public boolean setUserPreferredDisplayMode(int userSerial, DisplayDevice displayDevice,
            DisplayMode mode) {
        boolean result = mOldStore.setUserPreferredResolution(displayDevice, mode.getResolution().x,
                mode.getResolution().y) || mOldStore.setUserPreferredRefreshRate(displayDevice,
                mode.getRefreshRate());
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(userSerial, displayDevice, DISPLAY_MODE_KEY,
                    mode);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getStableDisplaySize()
     */
    public Point getStableDisplaySize() {
        if (mNewStore != null) {
            StableDeviceValues sdv = mNewStore.getGlobalProperty(STABLE_DEVICE_VALUES_KEY);
            return sdv != null ? sdv.getDisplaySize() : new Point(0, 0);
        } else {
            return mOldStore.getStableDisplaySize();
        }
    }

    /**
     * @see LegacyPersistentDataStore#setStableDisplaySize(Point)
     */
    public void setStableDisplaySize(Point size) {
        mOldStore.setStableDisplaySize(size);
        if (mNewStore != null) {
            mNewStore.setGlobalProperty(STABLE_DEVICE_VALUES_KEY, new StableDeviceValues(size));
        }
    }

    /**
     * @see LegacyPersistentDataStore#setBrightnessConfigurationForUser(BrightnessConfiguration,
     * int, String)
     */
    public void setBrightnessConfigurationForUser(BrightnessConfiguration c, int userSerial,
            @android.annotation.Nullable String packageName) {
        mOldStore.setBrightnessConfigurationForUser(c, userSerial, packageName);
        if (mNewStore != null) {
            mNewStore.setUserProperty(userSerial, BRIGHTNESS_CONFIGURATION_KEY,
                    new com.android.server.display.persistence.BrightnessConfiguration(c,
                            System.currentTimeMillis(), packageName));
        }
    }

    /**
     * @see LegacyPersistentDataStore#setBrightnessConfigurationForDisplayLocked(
     * BrightnessConfiguration, DisplayDevice, int, String)
     */
    public boolean setBrightnessConfigurationForDisplayLocked(BrightnessConfiguration configuration,
            DisplayDevice device, int userSerial, String packageName) {
        boolean result = mOldStore.setBrightnessConfigurationForDisplayLocked(configuration,
                device, userSerial, packageName);
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(userSerial, device, BRIGHTNESS_CONFIGURATION_KEY,
                    new com.android.server.display.persistence.BrightnessConfiguration(
                            configuration, System.currentTimeMillis(), packageName));
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getBrightnessConfigurationForDisplayLocked(String, int)
     */
    @Nullable
    public BrightnessConfiguration getBrightnessConfigurationForDisplayLocked(DisplayDevice device,
            int userSerial) {
        if (mNewStore != null) {
            com.android.server.display.persistence.BrightnessConfiguration config =
                    mNewStore.getDisplayProperty(userSerial, device, BRIGHTNESS_CONFIGURATION_KEY);
            return config != null ? config.getConfiguration() : null;
        } else {
            return mOldStore.getBrightnessConfigurationForDisplayLocked(device.getUniqueId(),
                    userSerial);
        }
    }

    /**
     * @see LegacyPersistentDataStore#getBrightnessConfiguration(int)
     */
    @Nullable
    public BrightnessConfiguration getBrightnessConfiguration(int userSerial) {
        if (mNewStore != null) {
            com.android.server.display.persistence.BrightnessConfiguration config =
                    mNewStore.getUserProperty(userSerial, BRIGHTNESS_CONFIGURATION_KEY);
            return config != null ? config.getConfiguration() : null;
        } else {
            return mOldStore.getBrightnessConfiguration(userSerial);
        }
    }

    /**
     * @see LegacyPersistentDataStore#setConnectionPreference(DisplayDevice, int)
     */
    public boolean setConnectionPreference(int userSerial, DisplayDevice device,
            @DisplayManager.ExternalDisplayConnection int preference) {
        boolean result = mOldStore.setConnectionPreference(device, preference);
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(userSerial, device, CONNECTION_PREFERENCE_KEY,
                    preference);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getConnectionPreference(DisplayDevice)
     */
    @DisplayManager.ExternalDisplayConnection
    public int getConnectionPreference(int userSerial, DisplayDevice device) {
        if (mNewStore != null) {
            Integer preference = mNewStore.getDisplayProperty(userSerial, device,
                    CONNECTION_PREFERENCE_KEY
            );
            return preference != null ? preference : EXTERNAL_DISPLAY_CONNECTION_PREFERENCE_DEFAULT;
        } else {
            return mOldStore.getConnectionPreference(device);
        }
    }

    /**
     * @see LegacyPersistentDataStore#setUserPreferredHdrMode(DisplayDevice, int)
     */
    public boolean setUserPreferredHdrMode(int userSerial, DisplayDevice displayDevice,
            @DisplayManager.HdrPreference int preference) {
        boolean result = mOldStore.setUserPreferredHdrMode(displayDevice, preference);
        if (mNewStore != null) {
            result |= mNewStore.setDisplayProperty(userSerial, displayDevice, HDR_PREFERENCE_KEY,
                    preference);
        }
        return result;
    }

    /**
     * @see LegacyPersistentDataStore#getUserPreferredHdrMode(DisplayDevice)
     */
    @DisplayManager.HdrPreference
    public int getUserPreferredHdrMode(int userSerial, DisplayDevice device) {
        if (mNewStore != null) {
            Integer preference = mNewStore.getDisplayProperty(userSerial, device, HDR_PREFERENCE_KEY
            );
            return preference != null ? preference : DEFAULT_HDR_PREFERENCE;
        } else {
            return mOldStore.getUserPreferredHdrMode(device);
        }
    }

    public static class Injector {
        private final AtomicFile mAtomicFile;

        public Injector(String fileName) {
            mAtomicFile = new AtomicFile(new File(fileName), "display-state");
        }

        /**
         * Opens the file for reading.
         * <p>
         * Use this to retrieve a stream for the current "committed" state of the file.
         *
         * @return A new {@link InputStream} for reading the file content.
         * @throws FileNotFoundException If the file does not exist.
         */
        public InputStream openRead() throws FileNotFoundException {
            return mAtomicFile.openRead();
        }

        /**
         * Starts the process of atomically writing to the file.
         * <p>
         * This creates a temporary file to hold the data until
         * {@link #finishWrite(OutputStream, boolean)}
         * is called. If the system crashes or the write fails before finishing, the original
         * file remains intact.
         *
         * @return A new {@link OutputStream} to receive the new file content.
         * @throws IOException If the stream cannot be created or the file cannot be accessed.
         */
        public OutputStream startWrite() throws IOException {
            return mAtomicFile.startWrite();
        }

        /**
         * Finalizes the write operation initiated by {@link #startWrite()}.
         * <p>
         * If {@code success} is true, the temporary data is committed (usually via a rename
         * or sync) and becomes the new file content. If false, the changes are discarded
         * and the original file is preserved.
         *
         * @param os The {@link OutputStream} previously returned by {@link #startWrite()}.
         * @param success {@code true} to commit the changes, {@code false} to abort and
         * roll back.
         * @throws IllegalArgumentException If the provided {@code os} is not a valid
         * {@link FileOutputStream} managed by this class.
         */
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
    }
}
