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
package android.platform.test.ravenwood;

import static android.os.Process.FIRST_APPLICATION_UID;

import static com.android.ravenwood.common.RavenwoodInternalUtils.parseNullableInt;
import static com.android.ravenwood.common.RavenwoodInternalUtils.withDefault;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityThread;
import android.app.LoadedApk;
import android.app.RavenwoodAppDriver;
import android.os.Build;

import com.android.internal.annotations.GuardedBy;
import com.android.ravenwood.RavenwoodVmState;
import com.android.ravenwood.common.RavenwoodInternalUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A singleton class that manages various "static" states about the test
 * (test package name, UID, etc) and things that can be inferred from them.
 * (e.g. app data directory path, resources, etc)
 *
 * It's also responsible for initializing {@link RavenwoodVmState}.
 *
 * This class's constructor has no external dependencies, so we can instantiate this class
 * at an early stage of initialization. Various other classes such as the {@code *_ravenwood}
 * classes often get information from this class.
 *
 * More dynamic states are managed by {@link RavenwoodAppDriver}, whose constructor has a lot
 * more complicated initialization logic that involves
 * {@link ActivityThread}, {@link LoadedApk}, etc, and their {@code *_ravenwood}
 * counterpart. Such code is allowed to access {@link RavenwoodEnvironment} but not
 * {@link RavenwoodAppDriver} yet, since it's not initialized yet.
 */
public final class RavenwoodEnvironment {
    public static final String TAG = "RavenwoodEnvironment";

    private static final AtomicReference<RavenwoodEnvironment> sInstance = new AtomicReference<>();

    /**
     * Returns the singleton instance.
     */
    public static RavenwoodEnvironment getInstance() {
        return Objects.requireNonNull(sInstance.get(), "RavenwoodEnvironment not initialized");
    }

    private static final File RAVENWOOD_EMPTY_RESOURCES_APK =
            new File(RavenwoodInternalUtils.getRavenwoodRuntimePath(),
                    "/ravenwood-data/ravenwood-empty-res.apk");

    public static final String MAIN_THREAD_NAME = "Ravenwood:Main";
    private static final String TEST_THREAD_NAME = "Ravenwood:Test";

    private static final int DEFAULT_TARGET_SDK_LEVEL = Build.VERSION_CODES.CUR_DEVELOPMENT;
    private static final String DEFAULT_PACKAGE_NAME = "com.android.ravenwoodtests.defaultname";
    private static final String DEFAULT_INSTRUMENTATION_CLASS =
            "androidx.test.runner.AndroidJUnitRunner";

    private final Object mLock = new Object();

    private final int mUid;
    private final int mPid;
    private final int mTargetSdkLevel;

    @NonNull
    private final String mTargetPackageName;

    @NonNull
    private final String mInstPackageName;

    @NonNull
    private final String mInstrumentationClass;

    @NonNull
    private final String mModuleName;

    @Nullable
    private final String mResourceApk;

    @Nullable
    private final String mTargetResourceApk;

    /** Represents the filesystem root. */
    @NonNull
    private final File mRootDir;

    @NonNull
    private final File mTempDir;

    @NonNull
    private final File mArtifactsDir;

    @GuardedBy("mLock")
    private final Map<String, File> mAppDataDirs = new HashMap<>();

    /**
     * Constructor. There should be only simple initialization here. More complicated
     * initialization logic should be done its methods, or by RavenwoodAppDriver.
     */
    private RavenwoodEnvironment(
            int uid,
            int pid,
            int targetSdkLevel,
            @NonNull String targetPackageName,
            @NonNull String instPackageName,
            @NonNull String instrumentationClass,
            @NonNull String moduleName,
            @Nullable String resourceApk,
            @Nullable String targetResourceApk
    ) throws IOException {
        mUid = uid;
        mPid = pid;
        mTargetSdkLevel = targetSdkLevel;
        mTargetPackageName = Objects.requireNonNull(targetPackageName);
        mInstPackageName = Objects.requireNonNull(instPackageName);
        mInstrumentationClass = Objects.requireNonNull(instrumentationClass);
        mModuleName = Objects.requireNonNull(moduleName);
        mResourceApk = resourceApk;
        mTargetResourceApk = targetResourceApk;

        mRootDir = Files.createTempDirectory("ravenwood-root-dir-").toFile();

        var tempDir = System.getProperty("java.io.tmpdir");
        mTempDir = new File(tempDir);

        // Create the artifact directory. The path is passed by tradefed.
        // Note, after running each test, tradefed will upload all files in it
        // and delete the whole directory.
        var artifactsDir = System.getProperty("android.ravenwood.artifacts_path");
        if (artifactsDir == null) {
            artifactsDir = Files.createTempDirectory("ravenwood-artifacts-default")
                    .toAbsolutePath().toString();
        }
        mArtifactsDir = new File(artifactsDir);
        mArtifactsDir.mkdirs();
    }

    /**
     * Create and initialize the singleton instance. Also initializes {@link RavenwoodVmState}.
     */
    public static void init() throws IOException {
        final var propFile = System.getProperty(
                "android.ravenwood.prop_file", "ravenwood.properties");

        final var props = RavenwoodSystemProperties.readProperties(propFile);

        // TODO: Why do we use a random PID? We can get the real PID via JNI. Why not use that?
        final int pid = new Random().nextInt(100, 32768);

        // TODO(b/377765941) Read them from the manifest too?
        var targetSdkLevel = withDefault(
                parseNullableInt(props.get("targetSdkVersionInt")), DEFAULT_TARGET_SDK_LEVEL);
        var testPackageName = withDefault(props.get("packageName"), DEFAULT_PACKAGE_NAME);
        var targetPackageName = withDefault(props.get("targetPackageName"), testPackageName);
        var instrumentationClass = withDefault(props.get("instrumentationClass"),
                DEFAULT_INSTRUMENTATION_CLASS);
        var moduleName = withDefault(props.get("moduleName"), testPackageName);
        var resourceApk = withDefault(props.get("resourceApk"), null);
        var targetResourceApk = withDefault(props.get("targetResourceApk"), null);

        Thread.currentThread().setName(TEST_THREAD_NAME);
        final var instance = new RavenwoodEnvironment(
                FIRST_APPLICATION_UID,
                pid,
                targetSdkLevel,
                targetPackageName,
                testPackageName,
                instrumentationClass,
                moduleName,
                resourceApk,
                targetResourceApk
        );
        if (!sInstance.compareAndSet(null, instance)) {
            throw new RuntimeException("RavenwoodEnvironment already initialized!");
        }
        RavenwoodVmState.init(instance.getUid(), instance.getPid(), instance.getTargetSdkLevel());
    }

    /**
     * If the test is self-instrumenting. i.e. if the test package name and the target package
     * name are the same.
     */
    public boolean isSelfInstrumenting() {
        return mTargetPackageName.equals(mInstPackageName);
    }

    public int getUid() {
        return mUid;
    }

    public int getPid() {
        return mPid;
    }

    public int getTargetSdkLevel() {
        return mTargetSdkLevel;
    }

    @NonNull
    public String getTargetPackageName() {
        return mTargetPackageName;
    }

    @NonNull
    public String getInstPackageName() {
        return mInstPackageName;
    }

    public String getTestModuleName() {
        return mModuleName;
    }

    @NonNull
    public String getInstrumentationClass() {
        return mInstrumentationClass;
    }

    /**
     * @return the directory that we use as the "root" directory.
     */
    @NonNull
    public File getRootDir() {
        return mRootDir;
    }

    @NonNull
    public File getTempDir() {
        return mTempDir;
    }

    @NonNull
    public File getBugreportDir() {
        return new File(getEnvVar("RAVENWOOD_BUGREPORT_DIR", mTempDir.toString()));
    }

    @NonNull
    public File getArtifactsDir() {
        return mArtifactsDir;
    }

    public long getDefaultCallingIdentity() {
        return RavenwoodDriver.packBinderIdentityToken(false, mUid, mPid);
    }

    /**
     * Returns a data dir for a given package. The package must be "known" to the environment.
     * (Note "android" doesn't work because in reality too, the system server doesn't have
     * an app data directory.)
     */
    @NonNull
    public File getAppDataDir(@NonNull String packageName) {
        synchronized (mLock) {
            var cached = mAppDataDirs.get(packageName);
            if (cached != null) {
                return cached;
            }

            // Create a directory, but only if it's a known package.
            if (!packageName.equals(mInstPackageName)
                    && !packageName.equals(mTargetPackageName)) {
                throw new RuntimeException("Unknown package " + packageName);
            }

            var dir = new File(mRootDir, "data/app/" + packageName + "-appdatadir/");
            dir.mkdirs();

            mAppDataDirs.put(packageName, dir);
            return dir;
        }
    }

    public static RuntimeException makeUnknownPackageException(@Nullable String packageName) {
        return new RuntimeException("Unknown package name: " + packageName);
    }

    /**
     * Get the resource APK file for a given package's resources.
     * @param packageName package name, or "android" to load the system resources.
     */
    public File getResourcesApkFile(@NonNull String packageName) {
        if (packageName.equals(getInstPackageName())) {
            if (mResourceApk != null) {
                return new File(mResourceApk);
            }
            // fall-through and use the default resources.
        } else if (packageName.equals(getTargetPackageName())) {
            if (mTargetResourceApk != null) {
                return new File(mTargetResourceApk);
            }
            // fall-through and use the default resources.
        } else if (packageName.equals(RavenwoodInternalUtils.ANDROID_PACKAGE_NAME)) {
            // fall-through and use the default resources.

        } else {
            throw makeUnknownPackageException(packageName);
        }
        return RAVENWOOD_EMPTY_RESOURCES_APK;
    }

    /** Reads a per-module environmental variable. */
    public String getEnvVar(String keyName, String defValue) {
        var value = System.getenv(keyName + "_" + getTestModuleName());
        if (value == null) {
            value = System.getenv(keyName);
        }
        if (value == null) {
            value = defValue;
        }
        return value;
    }

    /** Reads a per-module environmental boolean variable. */
    public boolean getBoolEnvVar(String keyName) {
        return getBoolEnvVar(keyName, false);
    }

    /**
     * Reads a per-module environmental boolean variable with a default value.
     */
    public boolean getBoolEnvVar(String keyName, boolean defValue) {
        return "1".equals(getEnvVar(keyName, defValue ? "1" : ""));
    }

    /**
     * Return if $RAVENWOOD_HIDE_DISABLED_TESTS is set to 1, in which case we don't show
     * @DisabledOnRavenwood tests in log or in atest output.
     */
    public boolean isHidingDisabledTests() {
        return getBoolEnvVar("RAVENWOOD_HIDE_DISABLED_TESTS") && !isDumpingTestsOnly();
    }

    /**
     * Returns a regex that can filter what tests to run.
     */
    public String getRunFilterRegex() {
        return getEnvVar("RAVENWOOD_FILTER_REGEX", null);
    }

    /** If true, we skip tests marked as "large" in the enablement policy file. */
    public boolean isSkippingLargeTests() {
        return getBoolEnvVar("RAVENWOOD_SKIP_LARGE_TESTS");
    }

    /**
     * If this is true, we skip all test methods, while still keeping the classes enabled.
     *
     * This is used to just dump all test names. If this is true, {@link #isHidingDisabledTests}
     * will always false.
     */
    public boolean isDumpingTestsOnly() {
        return getBoolEnvVar("RAVENWOOD_DUMP_TESTS_ONLY");
    }

    private static final int DEFAULT_SLOW_TIMEOUT_SECONDS = 10;

    /**
     * If a test takes more time than this timeout, we'll dump all the thread stacks at this
     * timeout.
     *
     * Note, this timeout will _not_ stop the test, as there isn't really a clean way to do it.
     * It'll merely print stacktraces.
     *
     * Returns 0 if the timeout should be disabled.
     */
    public int getSlowTestTimeoutSeconds() {
        if (RavenwoodImplUtils.isDebuggerAttached()) {
            // If the debugger is attached, never do it.
            return 0;
        }
        return getIntEnvVar("RAVENWOOD_SLOW_TIMEOUT_SECONDS", DEFAULT_SLOW_TIMEOUT_SECONDS);
    }

    private static final int DEFAULT_DIE_TIMEOUT_SECONDS = 60 * 60 * 1; // 1 hour

    /**
     * If a test takes more time than this timeout, we'll dump all the thread stacks at this
     * timeout _and crash the current process_.
     *
     * Returns 0 if the timeout should be disabled.
     */
    public int getDieTimeoutSeconds() {
        if (RavenwoodImplUtils.isDebuggerAttached()) {
            // If the debugger is attached, never do it.
            return 0;
        }
        return getIntEnvVar("RAVENWOOD_DIE_TIMEOUT_SECONDS", DEFAULT_DIE_TIMEOUT_SECONDS);
    }

    /**
     * When calling a dump() method on the main thread, we use this timeout.
     */
    public int getDumpTimeout() {
        return getIntEnvVar("RAVENWOOD_DUMP_TIMEOUT_SECONDS", 2);
    }

    /** Reads a per-module environmental int variable. */
    public int getIntEnvVar(String keyName, int defValue) {
        var v = getEnvVar(keyName, "");
        try {
            if (!v.isEmpty()) {
                return Integer.parseInt(v);
            }
        } catch (NumberFormatException ignore) {
        }
        return defValue;
    }

    /**
     * Reads a per-module environmental string variable, and split it with whitespace.
     * Default is an empty array;
     */
    public String[] getArrayEnvVar(String keyName) {
        var val = getEnvVar(keyName, "");
        if (val.isEmpty()) {
            return new String[0];
        }
        return val.split("\\s+");
    }

    /**
     * Default filename of the per-test {@link android.os.SystemProperties} override.
     */
    private static final String PER_TEST_SYSPROP = "ravenwood.sysprop";

    /**
     * @return Filename of the per-test {@link android.os.SystemProperties} override.
     * Default is {@link #PER_TEST_SYSPROP}.
     */
    public String perTestSyspropFile() {
        return System.getProperty("android.ravenwood.per_test_prop_file", PER_TEST_SYSPROP);
    }
}
