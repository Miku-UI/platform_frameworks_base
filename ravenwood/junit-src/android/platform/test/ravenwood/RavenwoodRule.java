/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.ravenwood.common.RavenwoodInternalUtils;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Reach out to g/ravenwood if you need any features in it.
 */
public final class RavenwoodRule implements TestRule {
    final RavenwoodTestProperties mProperties = new RavenwoodTestProperties();

    public static class Builder {

        private final RavenwoodRule mRule = new RavenwoodRule();

        public Builder() {
        }

        /**
         * Configure the given system property as immutable for the duration of the test.
         * Read access to the key is allowed, and write access will fail. When {@code value} is
         * {@code null}, the value is left as undefined.
         *
         * All properties in the {@code debug.*} namespace are automatically mutable, with no
         * developer action required.
         *
         * Has no effect on non-Ravenwood environments.
         */
        public Builder setSystemPropertyImmutable(@NonNull String key, @Nullable Object value) {
            mRule.mProperties.setValue(key, value);
            mRule.mProperties.setAccessReadOnly(key);
            return this;
        }

        /**
         * Configure the given system property as mutable for the duration of the test.
         * Both read and write access to the key is allowed, and its value will be reset between
         * each test. When {@code value} is {@code null}, the value is left as undefined.
         *
         * All properties in the {@code debug.*} namespace are automatically mutable, with no
         * developer action required.
         *
         * Has no effect on non-Ravenwood environments.
         */
        public Builder setSystemPropertyMutable(@NonNull String key, @Nullable Object value) {
            mRule.mProperties.setValue(key, value);
            mRule.mProperties.setAccessReadWrite(key);
            return this;
        }

        public RavenwoodRule build() {
            return mRule;
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (!isOnRavenwood()) {
            return base;
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                RavenwoodAwareTestRunner.onRavenwoodRuleEnter(description, RavenwoodRule.this);
                try {
                    base.evaluate();
                } finally {
                    RavenwoodAwareTestRunner.onRavenwoodRuleExit(description, RavenwoodRule.this);
                }
            }
        };
    }

    /**
     * Returns the "real" result from {@link System#currentTimeMillis()}.
     *
     * Currently, it's the same thing as calling {@link System#currentTimeMillis()},
     * but this one is guaranteeed to return the real value, even when Ravenwood supports
     * injecting a time to{@link System#currentTimeMillis()}.
     */
    public static long realCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Return if the current process is running on a Ravenwood test environment.
     */
    public static boolean isOnRavenwood() {
        return RavenwoodInternalUtils.isOnRavenwood();
    }

    private static void ensureOnRavenwood(String featureName) {
        if (!isOnRavenwood()) {
            throw new UnsupportedOperationException(
                    featureName + " is only supported on Ravenwood.");
        }
    }

    /**
     * Equivalent to setting the ANDROID_LOG_TAGS environmental variable.
     *
     * See https://developer.android.com/tools/logcat#filteringOutput for the string format.
     *
     * NOTE: this works only on Ravenwood.
     */
    public static void setAndroidLogTags(@Nullable String androidLogTags) {
        ensureOnRavenwood("RavenwoodRule.setAndroidLogTags()");
        try {
            Class<?> logRavenwoodClazz = Class.forName("android.util.Log_ravenwood");
            var setter = logRavenwoodClazz.getMethod("setLogLevels", String.class);
            setter.invoke(null, androidLogTags);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set a log level for a given tag. Pass NULL to {@code tag} to change the default level.
     *
     * NOTE: this works only on Ravenwood.
     */
    public static void setLogLevel(@Nullable String tag, int level) {
        ensureOnRavenwood("RavenwoodRule.setLogLevel()");
        try {
            Class<?> logRavenwoodClazz = Class.forName("android.util.Log_ravenwood");
            var setter = logRavenwoodClazz.getMethod("setLogLevel", String.class, int.class);
            setter.invoke(null, tag, level);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the full directory path that contains the "ravenwood-runtime" files.
     *
     * This method throws if called on the device side.
     */
    public static String getRavenwoodRuntimePath() {
        ensureOnRavenwood("RavenwoodRule.getRavenwoodRuntimePath()");
        return RavenwoodInternalUtils.getRavenwoodRuntimePath();
    }

    /**
     * Load a JNI library respecting {@code java.library.path}
     * (which reflects {@code LD_LIBRARY_PATH}).
     *
     * <p>{@code libname} must be the library filename without:
     * - directory
     * - "lib" prefix
     * - and the ".so" extension
     *
     * <p>For example, in order to load "libmyjni.so", then pass "myjni".
     *
     * <p>This is basically the same thing as Java's {@link System#loadLibrary(String)},
     * but this API works slightly different on ART and on the desktop Java, namely
     * the desktop Java version uses a different entry point method name
     * {@code JNI_OnLoad_libname()} (note the included "libname")
     * while ART always seems to use {@code JNI_OnLoad()}.
     *
     * <p>This method provides the same behavior on both the device side and on Ravenwood --
     * it uses {@code JNI_OnLoad()} as the entry point name on both.
     */
    public static void loadJniLibrary(String libname) {
        RavenwoodInternalUtils.loadJniLibrary(libname);
    }
}
