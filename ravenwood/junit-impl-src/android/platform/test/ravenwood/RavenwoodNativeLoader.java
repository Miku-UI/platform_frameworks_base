/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.ravenwood;

import static com.android.ravenwood.common.RavenwoodInternalUtils.getRavenwoodRuntimePath;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.KeyCharacterMap;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * We use this class to load libandroid_runtime.
 * In the future, we may load other native libraries.
 */
public final class RavenwoodNativeLoader {
    public static final String CORE_NATIVE_CLASSES = "core_native_classes";
    public static final String ICU_DATA_PATH = "icu.data.path";
    public static final String KEYBOARD_PATHS = "keyboard_paths";
    public static final String GRAPHICS_NATIVE_CLASSES = "graphics_native_classes";

    /**
     * Classes with native methods that are backed by libandroid_runtime.
     *
     * See frameworks/base/core/jni/platform/host/HostRuntime.cpp
     */
    private static final Class<?>[] sLibandroidClasses = {
//            android.util.Log.class, // Not using native log: b/377377826
            android.os.Parcel.class,
            android.os.Binder.class,
            android.os.MessageQueue.class,
            android.os.SystemProperties.class,
            android.content.res.ApkAssets.class,
            android.content.res.AssetManager.class,
            android.content.res.StringBlock.class,
            android.content.res.XmlBlock.class,
            android.text.AndroidCharacter.class,
            android.text.Hyphenator.class,
            com.android.internal.util.VirtualRefBasePtr.class,
            android.view.KeyCharacterMap.class,
            android.view.KeyEvent.class,
            android.view.InputChannel.class,
            android.view.InputDevice.class,
            android.view.InputEventReceiver.class,
            android.view.InputEventSender.class,
            android.view.MotionEvent.class,
            android.view.MotionPredictor.class,
    };

    /**
     * When experimental APIs are enabled, we additionally initialize the following
     * native code too.
     */
    private static final Class<?>[] sLibandroidExperimentalClasses = {
            android.animation.PropertyValuesHolder.class,
    };

    /**
     * Classes with native methods that are backed by libhwui.
     *
     * See frameworks/base/libs/hwui/apex/LayoutlibLoader.cpp
     */
    private static final Class<?>[] sLibhwuiClasses = {
            android.graphics.Interpolator.class,
            android.graphics.Matrix.class,
            android.graphics.Path.class,
            android.graphics.Color.class,
            android.graphics.ColorSpace.class,
            android.graphics.Bitmap.class,
            android.graphics.BitmapFactory.class,
            android.graphics.BitmapRegionDecoder.class,
            android.graphics.Camera.class,
            android.graphics.Canvas.class,
            android.graphics.CanvasProperty.class,
            android.graphics.ColorFilter.class,
            android.graphics.DrawFilter.class,
            android.graphics.FontFamily.class,
            android.graphics.Gainmap.class,
            android.graphics.ImageDecoder.class,
            android.graphics.MaskFilter.class,
            android.graphics.NinePatch.class,
            android.graphics.Paint.class,
            android.graphics.PathEffect.class,
            android.graphics.PathIterator.class,
            android.graphics.PathMeasure.class,
            android.graphics.Picture.class,
            android.graphics.RecordingCanvas.class,
            android.graphics.Region.class,
            android.graphics.RenderNode.class,
            android.graphics.Shader.class,
            android.graphics.RenderEffect.class,
            android.graphics.Typeface.class,
            android.graphics.YuvImage.class,
            android.graphics.fonts.Font.class,
            android.graphics.fonts.FontFamily.class,
            android.graphics.text.LineBreaker.class,
            android.graphics.text.MeasuredText.class,
            android.graphics.text.TextRunShaper.class,
            android.graphics.text.GraphemeBreak.class,
            android.graphics.drawable.VectorDrawable.class,
            android.util.PathParser.class,
    };

    /**
     * When experimental APIs are enabled, we additionally initialize the following
     * native code too.
     */
    private static final Class<?>[] sLibhwuiExperimentalClasses = {
            android.graphics.drawable.AnimatedVectorDrawable.class,
    };

    /**
     * Extra strings needed to pass to register_android_graphics_classes().
     *
     * Several entries are not actually a class, so we just hardcode them here.
     */
    private static final String[] GRAPHICS_EXTRA_INIT_PARAMS = new String[] {
            "android.graphics.Graphics",
            "android.graphics.ByteBufferStreamAdaptor",
            "android.graphics.CreateJavaOutputStreamAdaptor"
    };

    private RavenwoodNativeLoader() {
    }

    private static void log(String message) {
        System.out.println("RavenwoodNativeLoader: " + message);
    }

    private static void log(String fmt, Object... args) {
        log(String.format(fmt, args));
    }

    private static void ensurePropertyNotSet(String key) {
        if (System.getProperty(key) != null) {
            throw new RuntimeException("System property \"" + key + "\" is set unexpectedly");
        }
    }

    private static void setProperty(String key, String value) {
        System.setProperty(key, value);
        log("Property set: %s=\"%s\"", key, value);
    }

    private static void dumpSystemProperties() {
        for (var prop : System.getProperties().entrySet()) {
            log("  %s=\"%s\"", prop.getKey(), prop.getValue());
        }
    }

    private static native void initFrameworkNativeCode(String runtimePath);

    /**
     * Creates a KeyCharacterMap from a key character map file.
     */
    public static native KeyCharacterMap createKeyCharacterMap(int deviceId, String keyMapFile);

    /**
     * libandroid_runtime uses Java's system properties to decide what JNI methods to set up.
     * Set up these properties and load the native library
     */
    public static void loadFrameworkNativeCode() {
        if ("1".equals(System.getenv("RAVENWOOD_DUMP_PROPERTIES"))) {
            log("Java system properties:");
            dumpSystemProperties();
        }

        // Make sure these properties are not set.
        ensurePropertyNotSet(CORE_NATIVE_CLASSES);
        ensurePropertyNotSet(ICU_DATA_PATH);
        ensurePropertyNotSet(KEYBOARD_PATHS);
        ensurePropertyNotSet(GRAPHICS_NATIVE_CLASSES);

        // Build the property values

        // Libandroid classes. Maybe enable experimental classes too.
        final var libandroidClasses = getLoadingClassesAsCsv(
                sLibandroidClasses, sLibandroidExperimentalClasses, null);

        // Libhwui classes.
        final var libhwuiClasses = getLoadingClassesAsCsv(
                sLibhwuiClasses, sLibhwuiExperimentalClasses, GRAPHICS_EXTRA_INIT_PARAMS);

        // Initialize the libraries
        setProperty(CORE_NATIVE_CLASSES, libandroidClasses);
        setProperty(GRAPHICS_NATIVE_CLASSES, libhwuiClasses);
        log("Initializing android_runtime for '" + libandroidClasses + "' and '"
                + libhwuiClasses + "'");

        initFrameworkNativeCode(getRavenwoodRuntimePath());
    }

    private static String getLoadingClassesAsCsv(
            @NonNull Class<?>[] alwaysLoadClasses,
            @Nullable Class<?>[] experimentalClasses,
            @Nullable String[] extraParams) {
        RavenwoodIntegrityChecker.checkForNativeAllocationRegistry(alwaysLoadClasses);

        var all = Arrays.stream(alwaysLoadClasses).map(Class::getName);
        if (experimentalClasses != null
                && RavenwoodExperimentalApiChecker.isExperimentalApiEnabled()) {
            RavenwoodIntegrityChecker.checkForNativeAllocationRegistry(experimentalClasses);
            all = Stream.concat(all, Arrays.stream(experimentalClasses).map(Class::getName));
        }
        if (extraParams != null) {
            all = Stream.concat(all, Arrays.stream(extraParams));
        }
        return all.collect(Collectors.joining(","));
    }
}
