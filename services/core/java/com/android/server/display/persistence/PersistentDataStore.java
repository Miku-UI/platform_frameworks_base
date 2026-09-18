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

import static com.android.server.display.persistence.UserState.DISPLAY_STATES_KEY;

import android.hardware.display.WifiDisplay;
import android.os.Handler;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.display.DisplayDevice;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Manages persistent state recorded by the display manager service as an XML file.
 * Caller must acquire lock on the data store before accessing it.
 * Anything returned by this class should be immutable, or a copy of the object stored.
 *
 * <p>File format:
 * <pre>{@code
 * <display-manager-state version="2">
 *
 *   <remembered-wifi-displays>
 *     <wifi-display deviceAddress="00:00:00:00:00:00" deviceName="XXXX" deviceAlias="YYYY" />
 *   </remembered-wifi-displays>
 *   <stable-device-values>
 *     <stable-display-height>1920</stable-display-height>
 *     <stable-display-width>1080</stable-display-width>
 *   </stable-device-values>
 *   <brightness-nits-for-default-display>600</brightness-nits-for-default-display>
 *
 *   <user-states>
 *     <user user-serial="123">
 *       <brightness-configuration package-name="com.example" timestamp="1234">
 *         <brightness-curve description="some text">
 *           <brightness-point lux="0" nits="13.25"/>
 *           <brightness-point lux="20" nits="35.94"/>
 *         </brightness-curve>
 *       </brightness-configuration>
 *
 *       <display-states>
 *         <display unique-id="XXXXXXX">
 *           <color-mode>0</color-mode>
 *           <brightness>0</brightness>
 *           <brightness-configuration package-name="com.example" timestamp="1234">
 *             <brightness-curve description="some text">
 *               <brightness-point lux="0" nits="13.25"/>
 *               <brightness-point lux="20" nits="35.94"/>
 *             </brightness-curve>
 *           </brightness-configuration>
 *           <display-mode>
 *             <resolution-width>1080</resolution-width>
 *             <resolution-height>1920</resolution-height>
 *             <refresh-rate>60</refresh-rate>
 *           </display-mode>
 *           <connection-preference>1</connection-preference>
 *           <hdr-preference>2</hdr-preference>
 *         </display>
 *       </display-states>
 *     </user>
 *   </user-states>
 *
 * </display-manager-state>
 * }</pre>
 */
public final class PersistentDataStore extends GenericStore {
    private static final String TAG = "DisplayManager.PersistentDataStore";
    static final String FILE_NAME = "/data/system/display-manager-state-v2.xml";
    private static final String XML_TAG_DISPLAY_MANAGER_STATE = "display-manager-state";

    interface XmlProcessor<T> {
        /**
         * Load the object from the XML file.
         */
        T loadFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException;

        /**
         * Save the object into the XML file.
         */
        void saveToXml(TypedXmlSerializer serializer, T value) throws IOException;
    }

    public abstract static class Key<T> {
        final String mName;

        private Key(String name) {
            this.mName = name;
        }

        abstract T loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException;

        abstract void saveToXml(TypedXmlSerializer serializer, Object value) throws IOException;

        T getCopy(@Nullable T value) {
            return value;
        }

        void dump(IndentingPrintWriter ipw, Object value) {
            ipw.println(value);
        }
    }

    /**
     * Key type used for primitive types.
     */
    abstract static class SimpleKey<T> extends Key<T> {
        SimpleKey(String name) {
            super(name);
        }

        @Override
        void saveToXml(TypedXmlSerializer serializer, Object value) throws IOException {
            serializer.text(value.toString());
        }

        @Override
        void dump(IndentingPrintWriter ipw, Object value) {
            ipw.println(mName + "=" + value);
        }
    }

    static class IntegerKey extends SimpleKey<Integer> {
        IntegerKey(String name) {
            super(name);
        }

        @Override
        Integer loadFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException {
            try {
                return Integer.parseInt(parser.nextText());
            } catch (NumberFormatException e) {
                throw new XmlPullParserException(
                        "Failed to parse integer: " + e.getLocalizedMessage());
            }
        }
    }

    static class FloatKey extends SimpleKey<Float> {
        FloatKey(String name) {
            super(name);
        }

        @Override
        Float loadFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException {
            try {
                return Float.parseFloat(parser.nextText());
            } catch (NumberFormatException e) {
                throw new XmlPullParserException(
                        "Failed to parse float: " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Key type used for classes/custom types. If public, the objects stored with this key should
     * be immutable, or the key should override {@link Key#getCopy}.
     */
    abstract static class ComplexKey<T> extends Key<T> {
        final XmlProcessor<T> mXmlProcessor;

        ComplexKey(String name, XmlProcessor<T> xmlProcessor) {
            super(name);
            mXmlProcessor = xmlProcessor;
        }

        @Override
        T loadFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException {
            return mXmlProcessor.loadFromXml(parser);
        }

        @Override
        void saveToXml(TypedXmlSerializer serializer, Object value) throws IOException {
            mXmlProcessor.saveToXml(serializer, (T) value);
        }
    }

    /**
     * Key type used for maps. If public, the objects stored in the map should be immutable.
     */
    abstract static class MapKey<T, K> extends Key<Map<T, K>> {
        private final String mXmlTagForEntry;
        private final Function<K, T> mIdSupplier;
        private final XmlProcessor<K> mXmlProcessorForEntry;

        MapKey(String name, String xmlTagForEntry, Function<K, T> idSupplier,
                XmlProcessor<K> xmlProcessorForEntry) {
            super(name);
            mXmlTagForEntry = xmlTagForEntry;
            mIdSupplier = idSupplier;
            mXmlProcessorForEntry = xmlProcessorForEntry;
        }

        @Override
        Map<T, K> loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            final int outerDepth = parser.getDepth();
            Map<T, K> map = new HashMap<>();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(mXmlTagForEntry)) {
                    K value = mXmlProcessorForEntry.loadFromXml(parser);
                    T id = mIdSupplier.apply(value);
                    if (map.containsKey(id)) {
                        throw new XmlPullParserException("Found a duplicate key: " + id);
                    }
                    map.put(id, value);
                }
            }
            return map;
        }

        @Override
        void saveToXml(TypedXmlSerializer serializer, Object value) throws IOException {
            Map<T, K> map = (Map<T, K>) value;
            for (K v : map.values()) {
                serializer.startTag(null, mXmlTagForEntry);
                mXmlProcessorForEntry.saveToXml(serializer, v);
                serializer.endTag(null, mXmlTagForEntry);
            }
        }

        @Override
        Map<T, K> getCopy(@Nullable Map<T, K> value) {
            if (value == null) {
                return null;
            }
            return Map.copyOf(value);
        }

        @Override
        void dump(IndentingPrintWriter ipw, Object value) {
            ipw.println(mName + ":");
            ipw.increaseIndent();
            for (Map.Entry<T, K> entry : ((Map<T, K>) value).entrySet()) {
                ipw.println(entry.getValue());
            }
            ipw.decreaseIndent();
        }
    }

    public static final Key<Map<String, WifiDisplay>> REMEMBERED_WIFI_DISPLAYS_KEY = new MapKey<>(
            "remembered-wifi-displays", "wifi-display", WifiDisplay::getDeviceAddress,
            new WifiDisplayXmlProcessor()) {};
    public static final Key<StableDeviceValues> STABLE_DEVICE_VALUES_KEY = new ComplexKey<>(
            "stable-device-values", StableDeviceValues.XML_PROCESSOR) {};
    public static final Key<Float> BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY = new FloatKey(
            "brightness-nits-for-default-display");
    private static final Key<Map<Integer, UserState>> USER_STATES_KEY = new MapKey<>(
            "user-states", UserState.XML_TAG, UserState.ID_SUPPLIER, UserState.XML_PROCESSOR) {
        @Override
        void dump(IndentingPrintWriter ipw, Object value) {
            for (UserState userState : ((Map<Integer, UserState>) value).values()) {
                userState.dump(ipw);
            }
        }
    };

    private static final Key<?>[] KEYS = new Key<?>[]{
            REMEMBERED_WIFI_DISPLAYS_KEY,
            STABLE_DEVICE_VALUES_KEY,
            BRIGHTNESS_NITS_FOR_DEFAULT_DISPLAY_KEY,
            USER_STATES_KEY
    };

    // True if the data has been loaded.
    private boolean mLoaded;

    // True if there are changes to be saved.
    private boolean mDirty;

    // The interface for methods which should be replaced by the test harness.
    private final PersistentDataStoreDelegate.Injector mInjector;

    private final Handler mHandler;
    private final Object mFileAccessLock = new Object();

    PersistentDataStore(PersistentDataStoreDelegate.Injector injector) {
        this(injector, new Handler(BackgroundThread.getHandler().getLooper()));
    }

    @VisibleForTesting
    PersistentDataStore(PersistentDataStoreDelegate.Injector injector, Handler handler) {
        super(KEYS);
        mInjector = injector;
        mHandler = handler;
    }

    /**
     * Update the store with the XML file if it is marked as dirty.
     */
    public void loadIfNeeded() {
        if (!mLoaded) {
            load();
            mLoaded = true;
        }
    }

    /**
     * Update the XML file if the store is marked as dirty.
     */
    public void saveIfNeeded() {
        if (mDirty) {
            save();
            mDirty = false;
        }
    }

    /**
     * Removes all persistent data associated with a specific deleted user id. This should be called
     * when a device user is removed from the system.
     */
    public void removeUserData(int userSerial) {
        removeFromGlobalPropertyMap(USER_STATES_KEY, userSerial);
    }

    private void setDirty() {
        mDirty = true;
    }

    private void clearState() {
        mStore.clear();
        mLoaded = false;
    }

    private void load() {
        synchronized (mFileAccessLock) {
            clearState();

            final InputStream is;
            try {
                is = mInjector.openRead();
            } catch (FileNotFoundException ex) {
                Slog.e(TAG, "The file does not exist.", ex);
                return;
            }

            TypedXmlPullParser parser;
            try {
                parser = Xml.resolvePullParser(is);
                XmlUtils.beginDocument(parser, XML_TAG_DISPLAY_MANAGER_STATE);
                loadFromXml(parser);
            } catch (IOException | XmlPullParserException ex) {
                Slog.e(TAG, "Failed to load display manager persistent store data.", ex);
                clearState();
            } finally {
                IoUtils.closeQuietly(is);
            }
        }
    }

    private void save() {
        final ByteArrayOutputStream os;
        try {
            os = new ByteArrayOutputStream();

            TypedXmlSerializer serializer = Xml.resolveSerializer(os);
            serializer.startDocument(null, true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startTag(null, XML_TAG_DISPLAY_MANAGER_STATE);
            saveToXml(serializer);
            serializer.endTag(null, XML_TAG_DISPLAY_MANAGER_STATE);
            serializer.endDocument();
            serializer.flush();

            mHandler.removeCallbacksAndMessages(/* token */ null);
            mHandler.post(() -> {
                synchronized (mFileAccessLock) {
                    OutputStream fileOutput = null;
                    try {
                        fileOutput = mInjector.startWrite();
                        os.writeTo(fileOutput);
                        fileOutput.flush();
                    } catch (IOException ex) {
                        Slog.e(TAG, "Failed to save display manager persistent store data.", ex);
                    } finally {
                        if (fileOutput != null) {
                            mInjector.finishWrite(fileOutput, true);
                        }
                    }
                }
            });
        } catch (IOException ex) {
            Slog.e(TAG, "Failed to process the XML serializer.", ex);
        }
    }

    /**
     * Get a global property.
     *
     * @return An immutable object or a copy of the object stored.
     */
    @Nullable
    public <T> T getGlobalProperty(Key<T> key) {
        loadIfNeeded();
        return key.getCopy(super.get(key));
    }

    /**
     * Set a global property.
     *
     * @param key   The key
     * @param value The value to store. It should be immutable, or the key should override
     *              {@link Key#getCopy}.
     * @return True if the value provided is not equal to one stored previously (or it was not
     * stored before).
     */
    public <T> boolean setGlobalProperty(Key<T> key, T value) {
        loadIfNeeded();
        boolean result = super.put(key, key.getCopy(value));
        if (result) {
            setDirty();
        }
        return result;
    }

    /**
     * Add a value to a global property stored as a map.
     * Creates a new map if it is not present already.
     *
     * @param key          The key of the global property stored as a map
     * @param keyInsideMap The key inside the map
     * @param value        The value to add to the map. It should be immutable.
     * @return True if the value provided is not equal to one stored previously (or it was not
     * stored before).
     */
    public <T, K> boolean addToGlobalPropertyMap(Key<Map<T, K>> key, T keyInsideMap, K value) {
        if (value == null) {
            return removeFromGlobalPropertyMap(key, keyInsideMap);
        }
        loadIfNeeded();
        Map<T, K> map = super.get(key);
        if (map == null) {
            map = new HashMap<>();
            mStore.put(key.mName, map);
        }
        if (Objects.equals(map.get(keyInsideMap), value)) {
            return false;
        }
        map.put(keyInsideMap, value);
        setDirty();
        return true;
    }

    /**
     * Remove a value from a global property stored as a map.
     *
     * @param key          The key of the global property stored as a map
     * @param keyInsideMap The key inside the map
     * @return True if the key provided had a value in the map and has been removed.
     */
    public <T, K> boolean removeFromGlobalPropertyMap(Key<Map<T, K>> key, T keyInsideMap) {
        loadIfNeeded();
        Map<T, K> map = super.get(key);
        if (map == null || !map.containsKey(keyInsideMap)) {
            return false;
        }
        map.remove(keyInsideMap);
        if (map.isEmpty()) {
            mStore.remove(key.mName);
        }
        setDirty();
        return true;
    }

    /**
     * Get a property for a specific user.
     *
     * @return An immutable object or a copy of the object stored.
     */
    @Nullable
    public <T> T getUserProperty(int userSerial, Key<T> key) {
        loadIfNeeded();
        UserState userState = getUserState(userSerial);
        return userState != null ? key.getCopy(userState.get(key)) : null;
    }

    /**
     * Set a property for a specific user.
     *
     * @param userSerial The user serial
     * @param key        The key
     * @param value      The value to store. It should be immutable, or the key should override
     *                   {@link Key#getCopy}.
     * @return True if the value provided is not equal to one stored previously (or it was not
     * stored before).
     */
    public <T> boolean setUserProperty(int userSerial, Key<T> key, T value) {
        loadIfNeeded();
        boolean result = getOrCreateUserState(userSerial).put(key, key.getCopy(value));
        if (result) {
            setDirty();
        }
        return result;
    }

    /**
     * Remove a property for a specific user.
     *
     * @return True if the key provided had a value and has been removed.
     */
    public <T> boolean removeUserProperty(int userSerial, Key<T> key) {
        loadIfNeeded();
        UserState userState = getUserState(userSerial);
        if (userState == null || !userState.mStore.containsKey(key.mName)) {
            return false;
        }
        userState.mStore.remove(key.mName);
        setDirty();
        return true;
    }

    /**
     * Get a property for a specific user and display.
     *
     * @return An immutable object or a copy of the object stored. Null if the device is null, or it
     * does not have a stable unique ID.
     */
    @Nullable
    public <T> T getDisplayProperty(int userSerial, DisplayDevice displayDevice, Key<T> key) {
        loadIfNeeded();
        if (displayDevice == null || !displayDevice.hasStableUniqueId()
                || displayDevice.getUniqueId() == null) {
            return null;
        }
        UserState userState = getUserState(userSerial);
        if (userState == null) {
            return null;
        }
        Map<String, DisplayState> displayStates = userState.get(DISPLAY_STATES_KEY);
        if (displayStates == null || !displayStates.containsKey(displayDevice.getUniqueId())) {
            return null;
        }
        return key.getCopy(displayStates.get(displayDevice.getUniqueId()).get(key));
    }

    /**
     * Set a property for a specific user and display.
     *
     * @param userSerial    The user serial
     * @param displayDevice The display device
     * @param key           The key
     * @param value         The value to store. It should be immutable, or the key should override
     *                      {@link Key#getCopy}.
     * @return True if the value provided is not equal to one stored previously (or it was not
     * stored before).
     */
    public <T> boolean setDisplayProperty(int userSerial, DisplayDevice displayDevice, Key<T> key,
            T value) {
        loadIfNeeded();
        if (displayDevice == null || !displayDevice.hasStableUniqueId()
                || displayDevice.getUniqueId() == null) {
            return false;
        }
        UserState userState = getOrCreateUserState(userSerial);
        Map<String, DisplayState> displayStates = userState.get(DISPLAY_STATES_KEY);
        if (displayStates == null) {
            displayStates = new HashMap<>();
            userState.put(DISPLAY_STATES_KEY, displayStates);
        }
        DisplayState displayState = displayStates.get(displayDevice.getUniqueId());
        if (displayState == null) {
            displayState = new DisplayState(displayDevice.getUniqueId());
            displayStates.put(displayDevice.getUniqueId(), displayState);
        }
        boolean result = displayState.put(key, key.getCopy(value));
        if (result) {
            setDirty();
        }
        return result;
    }

    /**
     * Print the state of the store.
     */
    public void dump(PrintWriter pw) {
        pw.println("PersistentDataStore:");
        pw.println("--------------------");
        pw.println("  mLoaded=" + mLoaded);
        pw.println("  mDirty=" + mDirty);
        super.dump(new IndentingPrintWriter(pw));
    }

    @Nullable
    private UserState getUserState(int userSerial) {
        Map<Integer, UserState> userStates = super.get(USER_STATES_KEY);
        return userStates != null ? userStates.get(userSerial) : null;
    }

    private UserState getOrCreateUserState(int userSerial) {
        Map<Integer, UserState> userStates = super.get(USER_STATES_KEY);
        if (userStates == null) {
            userStates = new HashMap<>();
            mStore.put(USER_STATES_KEY.mName, userStates);
        }
        UserState userState = userStates.get(userSerial);
        if (userState == null) {
            userState = new UserState(userSerial);
            userStates.put(userSerial, userState);
        }
        return userState;
    }
}
