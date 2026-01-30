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

import static android.os.UserHandle.SYSTEM;

import static com.android.modules.utils.ravenwood.RavenwoodHelper.RavenwoodInternal.RAVENWOOD_RUNTIME_PATH_JAVA_SYSPROP;

import static org.junit.Assert.assertThrows;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AppCompatCallbacks;
import android.app.RavenwoodAppDriver;
import android.app.UiAutomation_ravenwood;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;
import android.icu.util.ULocale;
import android.os.Binder;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process_ravenwood;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.os.SystemProperties;
import android.provider.DeviceConfig_ravenwood;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.Log_ravenwood;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.RuntimeInit;
import com.android.ravenwood.OpenJdkWorkaround;
import com.android.ravenwood.RavenwoodRuntimeNative;
import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;
import com.android.server.LocalServices;
import com.android.server.compat.PlatformCompat;

import org.junit.internal.management.ManagementFactory;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Responsible for initializing and the environment.
 */
public class RavenwoodDriver {
    private static final String TAG = RavenwoodInternalUtils.TAG;

    private RavenwoodDriver() {
    }

    /**
     * The following 2 PrintStreams is a backup of the original stdin/stdout streams.
     * System.out/err will be modified after calling {@link RuntimeInit#redirectLogStreams()}.
     */
    public static final PrintStream sRawStdOut = System.out;
    public static final PrintStream sRawStdErr = System.err;

    /**
     * The current directory when the test started.
     */
    private static final File sInitialDirectory;
    static {
        try {
            sInitialDirectory = new File(".").getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to get the current directory", e);
        }
    }

    private static final String LIBRAVENWOOD_INITIALIZER_NAME = "ravenwood_initializer";
    private static final String RAVENWOOD_NATIVE_RUNTIME_NAME = "ravenwood_runtime";

    private static final String ANDROID_LOG_TAGS = "ANDROID_LOG_TAGS";
    private static final String RAVENWOOD_ANDROID_LOG_TAGS = "RAVENWOOD_" + ANDROID_LOG_TAGS;

    /** Do not dump environments matching this pattern. */
    private static final Pattern sSecretEnvPattern = Pattern.compile(
            "(KEY|AUTH|API)", Pattern.CASE_INSENSITIVE);

    // TODO: expose packCallingIdentity function in libbinder and use it directly
    // See: packCallingIdentity in frameworks/native/libs/binder/IPCThreadState.cpp
    static long packBinderIdentityToken(
            boolean hasExplicitIdentity, int callingUid, int callingPid) {
        long res = ((long) callingUid << 32) | callingPid;
        if (hasExplicitIdentity) {
            res |= (0x1 << 30);
        } else {
            res &= ~(0x1 << 30);
        }
        return res;
    }

    private static final Object sInitializationLock = new Object();

    @GuardedBy("sInitializationLock")
    private static boolean sInitialized = false;

    @GuardedBy("sInitializationLock")
    private static Throwable sExceptionFromGlobalInit;

    /**
     * Initialize the global environment.
     */
    public static void globalInitOnce() {
        synchronized (sInitializationLock) {
            if (!sInitialized) {
                // globalInitOnce() is called from class initializer, which cause
                // this method to be called recursively,
                sInitialized = true;

                // This is the first call.
                final long start = System.currentTimeMillis();
                try {
                    globalInitInner();
                } catch (Throwable th) {
                    Log.e(TAG, "globalInit() failed", th);

                    sExceptionFromGlobalInit = th;
                    SneakyThrow.sneakyThrow(th);
                }
                final long end = System.currentTimeMillis();
                // TODO Show user/system time too
                Log.e(TAG, "globalInit() took " + (end - start) + "ms");
            } else {
                // Subsequent calls. If the first call threw, just throw the same error, to prevent
                // the test from running.
                if (sExceptionFromGlobalInit != null) {
                    Log.e(TAG, "globalInit() failed re-throwing the same exception",
                            sExceptionFromGlobalInit);

                    SneakyThrow.sneakyThrow(sExceptionFromGlobalInit);
                }

                // If an uncaught exception has been detected, don't run subsequent test classes.
                RavenwoodErrorHandler.maybeThrowUnrecoverableUncaughtException();
            }
        }
    }

    private static void globalInitInner() throws Exception {
        // We haven't initialized liblog yet, so directly write to System.out here.
        RavenwoodInternalUtils.log(TAG, "globalInitInner()");

        // Parse ravenwood properties and initialize the "environment".
        // This also unlocks the ability to use android.util.Log.
        var mainThread = new HandlerThread(RavenwoodEnvironment.MAIN_THREAD_NAME);
        RavenwoodEnvironment.init(mainThread);
        Log_ravenwood.setLogLevels(getLogTags());

        // Set up global error handling infrastructure
        RavenwoodErrorHandler.init();

        // Some process-wide initialization:
        // - maybe redirect stdout/stderr
        // - override native system property functions
        var lib = RavenwoodInternalUtils.getJniLibraryPath(LIBRAVENWOOD_INITIALIZER_NAME);
        System.load(lib);
        RavenwoodRuntimeNative.reloadNativeLibrary(lib);

        // Redirect stdout/stdin to the Log API.
        RuntimeInit.redirectLogStreams();

        dumpRavenwoodProperties();
        dumpCommandLineArgs();
        dumpEnvironment();
        dumpJavaProperties();
        dumpOtherInfo();

        Log.i(TAG, "PWD=" + System.getProperty("user.dir"));
        Log.i(TAG, "RuntimePath=" + System.getProperty(RAVENWOOD_RUNTIME_PATH_JAVA_SYSPROP));

        // Make sure libravenwood_runtime is loaded.
        System.load(RavenwoodInternalUtils.getJniLibraryPath(RAVENWOOD_NATIVE_RUNTIME_NAME));

        // We can start to use native code
        Log_ravenwood.sUseRealTid = true;

        // Do the basic set up for the android sysprops.
        RavenwoodSystemProperties.initialize();

        // Enable all log levels for native logging, until we'll have a way to change the native
        // side log level at runtime.
        // Do this after loading RAVENWOOD_NATIVE_RUNTIME_NAME (which backs Os.setenv()),
        // before loadFrameworkNativeCode() (which uses $ANDROID_LOG_TAGS).
        // This would also prevent libbase from crashing the process (b/381112373) because
        // the string format it accepts is very limited.
        try {
            Os.setenv("ANDROID_LOG_TAGS", "*:v", true);
        } catch (ErrnoException e) {
            throw new RuntimeException(e);
        }

        // Make sure libandroid_runtime is loaded.
        RavenwoodNativeLoader.loadFrameworkNativeCode();

        // Start method logging.
        RavenwoodMethodCallLogger.getInstance().enable(sRawStdOut);

        // Touch some references early to ensure they're <clinit>'ed
        Objects.requireNonNull(Build.TYPE);
        Objects.requireNonNull(Build.VERSION.SDK);

        // Fonts can only be initialized once
        Typeface.init();
        Typeface.loadPreinstalledSystemFontMap();
        Typeface.loadNativeSystemFonts();

        // This will let AndroidJUnit4 use the original runner.
        System.setProperty("android.junit.runner",
                "androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner");

        assertMockitoVersion();

        RavenwoodUtils.sPendingExceptionThrower =
                RavenwoodErrorHandler::maybeThrowPendingRecoverableUncaughtExceptionNoClear;

        ServiceManager.init$ravenwood();
        LocalServices.removeAllServicesForTest();
        ActivityManager.init$ravenwood(SYSTEM.getIdentifier());

        // Start the main thread.
        mainThread.start();
        Looper.setMainLooperForTest(mainThread.getLooper());

        // Start app lifecycle.
        RavenwoodAppDriver.init();

        // TODO(b/428775903) Make sure nothing would try to access compat-IDs before this call.
        // We may want to do it within initAppDriver().
        initializeCompatIds();

        // `pkill -USR2 -f tradefed-isolation.jar` will interrupt the test thread.
        final Thread testThread = Thread.currentThread();
        OpenJdkWorkaround.registerSignalHandler("USR2", () -> {
            sRawStdErr.println("-----SIGUSR2 HANDLER-----");
            testThread.interrupt();
        });
    }

    /**
     * Get log tags from environmental variable.
     */
    @Nullable
    private static String getLogTags() {
        var logTags = System.getenv(RAVENWOOD_ANDROID_LOG_TAGS);
        if (logTags == null) {
            logTags = System.getenv(ANDROID_LOG_TAGS);
        }
        return logTags;
    }

    /**
     * Partially reset and initialize before each test class invocation
     */
    public static void initForRunner() {
        // Reset some global state
        UiAutomation_ravenwood.reset();
        Process_ravenwood.reset();
        DeviceConfig_ravenwood.reset();
        Binder.restoreCallingIdentity(
                RavenwoodEnvironment.getInstance().getDefaultCallingIdentity());

        SystemProperties.clearChangeCallbacksForTest();

        RavenwoodErrorHandler.maybeThrowPendingRecoverableUncaughtExceptionAndClear();
    }

    /**
     * Called when a test method is about to be started.
     */
    public static void enterTestMethod(Description description) {
        // TODO(b/375272444): this is a hacky workaround to ensure binder identity
        Binder.restoreCallingIdentity(
                RavenwoodEnvironment.getInstance().getDefaultCallingIdentity());
    }

    private static void initializeCompatIds() {
        // Set up compat-IDs for the app side.
        // TODO: Inside the system server, all the compat-IDs should be enabled,
        // Due to the `AppCompatCallbacks.install(new long[0], new long[0] ...` call in
        // SystemServer.

        var env = RavenwoodEnvironment.getInstance();

        // Compat framework only uses the package name and the target SDK level.
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = env.getTargetPackageName();
        appInfo.targetSdkVersion = env.getTargetSdkLevel();

        PlatformCompat platformCompat = null;
        try {
            platformCompat = (PlatformCompat) ServiceManager.getServiceOrThrow(
                    Context.PLATFORM_COMPAT_SERVICE);
        } catch (ServiceNotFoundException e) {
            throw new RuntimeException(e);
        }

        var disabledChanges = platformCompat.getDisabledChanges(appInfo);
        var loggableChanges = platformCompat.getLoggableChanges(appInfo);

        AppCompatCallbacks.install(disabledChanges, loggableChanges, false);
    }

    private static final String MOCKITO_ERROR = "FATAL: Unsupported Mockito detected!"
            + " Your test or its dependencies use one of the \"mockito-target-*\""
            + " modules as static library, which is unusable on host side."
            + " Please switch over to use \"mockito-ravenwood-prebuilt\" as shared library, or"
            + " as a last resort, set `ravenizer: { strip_mockito: true }` in your test module.";

    /**
     * Assert the Mockito version at runtime to ensure no incorrect Mockito classes are loaded.
     */
    private static void assertMockitoVersion() {
        // DexMaker should not exist
        assertThrows(
                MOCKITO_ERROR,
                ClassNotFoundException.class,
                () -> Class.forName("com.android.dx.DexMaker"));
        // Mockito 2 should not exist
        assertThrows(
                MOCKITO_ERROR,
                ClassNotFoundException.class,
                () -> Class.forName("org.mockito.Matchers"));
    }

    private static void dumpCommandLineArgs() {
        Log.i(TAG, "JVM arguments:");

        // Note, we use the wrapper in JUnit4, not the actual class (
        // java.lang.management.ManagementFactory), because we can't see the later at the build
        // because this source file is compiled for the device target, where ManagementFactory
        // doesn't exist.
        var args = ManagementFactory.getRuntimeMXBean().getInputArguments();

        for (var arg : args) {
            Log.i(TAG, "  " + arg);
        }
    }

    private static void dumpRavenwoodProperties() {
        Log.i(TAG, "Ravenwood properties:");
        var env = RavenwoodEnvironment.getInstance();
        Log.i(TAG, "  targetPackageName=" + env.getTargetPackageName());
        Log.i(TAG, "  testPackageName=" + env.getInstPackageName());
        Log.i(TAG, "  targetSdkLevel=" + env.getTargetSdkLevel());
    }

    private static void dumpJavaProperties() {
        Log.i(TAG, "JVM properties:");
        dumpMap(System.getProperties());
    }

    private static void dumpEnvironment() {
        Log.i(TAG, "Environment:");

        // Dump the environments, but don't print the values for "secret" ones.
        dumpMap(System.getenv().entrySet().stream()
                .collect(Collectors.toMap(
                        // The key remains the same
                        Map.Entry::getKey,

                        // Hide the values as needed.
                        entry -> sSecretEnvPattern.matcher(entry.getKey()).find()
                                ? "[redacted]" : entry.getValue(),
                        (oldValue, newValue) -> newValue,
                        HashMap::new
                )));
    }

    private static void dumpMap(Map<?, ?> map) {
        for (var key : map.keySet().stream().sorted().toList()) {
            Log.i(TAG, "  " + key + "=" + map.get(key));
        }
    }

    private static void dumpOtherInfo() {
        Log.i(TAG, "Other key information:");
        var jloc = Locale.getDefault();
        Log.i(TAG, "  java.util.Locale=" + jloc + " / " + jloc.toLanguageTag());
        var uloc = ULocale.getDefault();
        Log.i(TAG, "  android.icu.util.ULocale=" + uloc + " / " + uloc.toLanguageTag());

        var jtz = java.util.TimeZone.getDefault();
        Log.i(TAG, "  java.util.TimeZone=" + jtz.getDisplayName() + " / " + jtz);

        var itz = android.icu.util.TimeZone.getDefault();
        Log.i(TAG, "  android.icu.util.TimeZone="  + itz.getDisplayName() + " / " + itz);
    }
}
