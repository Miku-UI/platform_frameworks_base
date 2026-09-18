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
package android.app;

import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.annotation.NonNull;
import android.app.ActivityThread.AppBindData;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.platform.test.ravenwood.RavenwoodDriver;
import android.platform.test.ravenwood.RavenwoodEnvironment;
import android.platform.test.ravenwood.RavenwoodErrorHandler;
import android.platform.test.ravenwood.RavenwoodProxyHelper;
import android.platform.test.ravenwood.RavenwoodSettingsProvider;
import android.platform.test.ravenwood.RavenwoodSystemServer;
import android.platform.test.ravenwood.RavenwoodUtils;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.compat.CompatibilityChangeInfo;
import com.android.internal.compat.CompatibilityRules;
import com.android.internal.ravenwood.RavenwoodHelperBridge.CompatIdsForTest;
import com.android.ravenwood.common.SneakyThrow;
import com.android.server.compat.PlatformCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for initializing and handling the app / process lifeclyce related
 * stuff. See also {@link RavenwoodEnvironment}.
 */
public final class RavenwoodAppDriver {
    private static final String TAG = RavenwoodDriver.TAG;

    /** Singleton instance. */
    private static final AtomicReference<RavenwoodAppDriver> sInstance = new AtomicReference<>();

    /**
     * Returns the singleton instance.
     */
    public static RavenwoodAppDriver getInstance() {
        return Objects.requireNonNull(sInstance.get(), "RavenwoodAppDriver not initialized");
    }

    /**
     * Initialize the singleton instance.
     */
    public static void init() {
        RavenwoodUtils.runOnMainThreadSync(() -> {
            if (!sInstance.compareAndSet(null, new RavenwoodAppDriver())) {
                throw new RuntimeException("RavenwoodAppDriver already initialized!");
            }
        });
    }

    @GuardedBy("mLoadedApkCache")
    private final Map<String, LoadedApk> mLoadedApkCache = new HashMap<>();

    @GuardedBy("mProviders")
    private final Map<String, IContentProvider> mProviders = new HashMap<>();

    /** This is an empty instance created by Objenesis. None of its fields are initialized. */
    private final ActivityThread mActivityThread;

    private final ContextImpl mSystemContextImpl;

    private final LoadedApk mTargetLoadedApk;
    private final LoadedApk mInstLoadedApk;

    private final ContextImpl mInstContext;
    private final Context mTargetContext;

    private final Instrumentation mInstrumentation;

    private final RavenwoodSettingsProvider mSettingsProvider;

    /**
     * All the known compat-IDs. We use it to ensure no unknown compat-IDs may be used.
     *
     * We need to cache them, rather than asking PlatformCompat every time, because technically
     * compat-IDs may be added anytime, even after we fetch the "disabled ID" list.
     */
    private static volatile long[] sAllKnownCompatIds = new long[0];

    /**
     * Constructor. It essentially simulates the start of an app lifecycle.
     *
     * TODO: This is basically a scale down version of what ActivityThread.bindApplication() and
     * handleBindApplication() do.
     * Use more of the real ActivityThread code, and move it to ActivityThread.
     */
    private RavenwoodAppDriver() {
        Log.i(TAG, "RavenwoodAppDriver initializing...");
        var env = RavenwoodEnvironment.getInstance();

        // This must happen on the main thread.
        mActivityThread = ActivityThread_ravenwood.createInstance();
        mTargetLoadedApk = makeLoadedApk(mActivityThread, env.getTargetPackageName());
        mInstLoadedApk = makeLoadedApk(mActivityThread, env.getInstPackageName());

        // Create the system context.
        mSystemContextImpl = ContextImpl.createSystemContext(mActivityThread);

        RavenwoodSystemServer.init(mSystemContextImpl);

        // We want to do it ASAP, but it depends on RavenwoodSystemServer.init(), so we can't
        // move it above.
        initializeCompatIds();

        // Create the target's context. Note, it's called "appContext" in handleBindApplication,
        // but its _not_ an of the Application class. We'll create the app object later,
        // using makeApplicationInner.
        var appContext = ContextImpl.createAppContext(
                mActivityThread, mTargetLoadedApk);

        // Create the instrumentation context.
        // (in handleBindApplication too, this happens before creating the target context.)
        mInstContext = ContextImpl.createAppContext(mActivityThread, mInstLoadedApk);

        // Initialize the instrumentation, using the "inst" context.
        // See also ActivityThread.initInstrumentation().
        var uiAutomation = new UiAutomation(
                mInstContext, new IUiAutomationConnection.Default());

        try {
            var clazz = Class.forName(env.getInstrumentationClass());
            mInstrumentation = (Instrumentation) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            SneakyThrow.sneakyThrow(e);
            throw new RuntimeException(); // Let javac know this branch is unreachable.
        }

        mInstrumentation.basicInit(mInstContext, appContext, uiAutomation);

        // Need to set it, as it's used by LoadedApk afer this.
        mActivityThread.mInstrumentation = mInstrumentation;

        // Create the Application instance, which will be the "target" context.
        var application = mTargetLoadedApk.makeApplicationInner(
                // We don't support custom application classes yet, but we'll let the
                // LoadedApk decide that, so pass false here.
                /*forceDefaultAppClass=*/ false,

                // When we make the app's Application object, we pass null.
                // That's what ActivityThread does in handleBindApplication() too.
                /*instrumentation=*/ null
        );
        // AndroidX's test runner will override the default handler, in makeApplication(),
        // so we override it again.
        // We could have HostStubGen redirect `Thread.setDefaultUncaughtExceptionHandler()`,
        // but currently we exclude all androidx.* classes from HSG processing, and
        // changing that could impact the build time, so let's just go with this hacky solution.
        RavenwoodErrorHandler.setDefaultUncaughtExceptionHandler();

        mActivityThread.mInitialApplication = application;
        mTargetContext = appContext;

        var data = new AppBindData();
        data.appInfo = application.getApplicationInfo();
        mActivityThread.mBoundApplication = data;

        mInstrumentation.onCreate(Bundle.EMPTY);
        mInstrumentation.callApplicationOnCreate(application);

        // Register a stub settings provider that always return nothing
        mSettingsProvider = new RavenwoodSettingsProvider();
        var mockSettings = RavenwoodProxyHelper.newProxy(
                IContentProvider.class,
                IContentProvider.descriptor,
                mSettingsProvider);
        synchronized (mProviders) {
            mProviders.put(Settings.AUTHORITY, mockSettings);
        }

        reset();

        Log.i(TAG, "RavenwoodAppDriver initialized");
    }

    private void initializeCompatIds() {
        // Set up compat-IDs for the app side.
        // TODO: Inside the system server, all the compat-IDs should be enabled,
        // Due to the `AppCompatCallbacks.install(new long[0], new long[0] ...` call in
        // SystemServer.

        // Register test rules.
        CompatibilityRules.initRulesForTest(
                new CompatibilityChangeInfo(CompatIdsForTest.TEST_COMPAT_ID_1,
                        "TEST_COMPAT_ID_1", -1, -1, false, false, false, "", false),
                new CompatibilityChangeInfo(CompatIdsForTest.TEST_COMPAT_ID_2,
                        "TEST_COMPAT_ID_2", -1, -1, true, false, false, "", false),
                new CompatibilityChangeInfo(CompatIdsForTest.TEST_COMPAT_ID_3,
                        "TEST_COMPAT_ID_3", -1, S, false, false, false, "", false),
                new CompatibilityChangeInfo(CompatIdsForTest.TEST_COMPAT_ID_4,
                        "TEST_COMPAT_ID_4", -1, UPSIDE_DOWN_CAKE, false, false, false, "", false),
                new CompatibilityChangeInfo(CompatIdsForTest.TEST_COMPAT_ID_5,
                        "TEST_COMPAT_ID_5", -1, -1, false, false, false, "", false)
        );

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

        sAllKnownCompatIds = platformCompat.getAllChangeIds();
        var disabledChanges = platformCompat.getDisabledChanges(appInfo);
        var enabledChanges = platformCompat.getEnabledChanges(appInfo);
        var loggableChanges = platformCompat.getLoggableChanges(appInfo);

        AppCompatCallbacks.install(disabledChanges, enabledChanges, loggableChanges, false,
                appInfo.targetSdkVersion);

        Log.i(TAG, "CompatChanges initialized");
    }

    private static boolean isChangeIdKnown(long changeId) {
        return Arrays.binarySearch(sAllKnownCompatIds, changeId) >= 0;
    }

    /**
     * Throws if a change ID is unknown.
     */
    public static void validateChangeId(long changeId) {
        if (sAllKnownCompatIds.length == 0) {
            throw new IllegalStateException("@ChangeId's not initialized yet!");
        }
        var known = isChangeIdKnown(changeId);
        if (!known) {
            throw new IllegalStateException("@ChangeId " + changeId
                    + " is not known to Ravenwood. Reach out to g/ravenwood to"
                    + " get it available on Ravenwood");
        }
    }

    /**
     * Create an {@link ApplicationInfo} for a package.
     *
     * The package must be "known" to Ravenwood; for now, that means it must be an instrumentation
     * or target package name. "android" isn't supported.
     *
     * User-id is assumed to be 0.
     */
    private static ApplicationInfo makeApplicationInfo(@NonNull String packageName) {
        var env = RavenwoodEnvironment.getInstance();
        if (!packageName.equals(env.getTargetPackageName())
                && !packageName.equals(env.getInstPackageName())) {
            throw RavenwoodEnvironment.makeUnknownPackageException(packageName);
        }

        // As an example, here's how ApplicationInfo is initialized for the launcher:
        // http://screen/A8ggwXFuERBBUqt

        ApplicationInfo ai = new ApplicationInfo();
        ai.uid = env.getUid();
        ai.targetSdkVersion = env.getTargetSdkLevel();
        ai.packageName = packageName;

        ai.dataDir = env.getAppDataDir(packageName).getAbsolutePath();
        ai.credentialProtectedDataDir = ai.dataDir;
        // ai.deviceProtectedDataDir // On device, it's: /data/user_de/0/com.xxx./.

        ai.sourceDir = env.getResourcesApkFile(packageName).getAbsolutePath();
        ai.publicSourceDir = ai.sourceDir;

        ai.processName = packageName;

        // TODO: Set CE/DE data dirs too.

        return ai;
    }

    /**
     * Make a LoadedApk instance for a package.
     *
     * TODO: Use ActivityThread.getPackageInfo().
     */
    private LoadedApk makeLoadedApk(
            ActivityThread activityThread,
            String packageName
    ) {
        // This is a scaled down version of ActivityThread.getPackageInfo()
        synchronized (mLoadedApkCache) {
            final var cached = mLoadedApkCache.get(packageName);
            if (cached != null) {
                return cached;
            }

            ApplicationInfo ai = makeApplicationInfo(packageName);

            // As an example, here's how LoadedApk is initialized for the launcher:
            // http://screen/6amYMbsRCJ7e5s6

            var loadedApk = new LoadedApk(
                    /* activityThread= */ activityThread,
                    /* aInfo= */ ai,
                    /* compatInfo= */ null,
                    /* baseLoader= */ RavenwoodAppDriver.class.getClassLoader(),
                    /* securityViolation= */ false,
                    /* includeCode= */ true,
                    /* registerPackage= */ false
            );
            mLoadedApkCache.put(packageName, loadedApk);
            return loadedApk;
        }
    }

    public ActivityThread getActivityThread() {
        return mActivityThread;
    }

    public ContextImpl getSystemContext() {
        return mSystemContextImpl;
    }

    public LoadedApk getTargetLoadedApk() {
        return mTargetLoadedApk;
    }

    public Context getInstContext() {
        return mInstContext;
    }

    public Context getTargetContext() {
        return mTargetContext;
    }

    public Application getApplication() {
        return (Application) mTargetContext.getApplicationContext();
    }

    public Instrumentation getInstrumentation() {
        return mInstrumentation;
    }

    public IContentProvider getProvider(Context context, String auth) {
        synchronized (mProviders) {
            return mProviders.get(ContentProvider.getAuthorityWithoutUserId(auth));
        }
    }

    /**
     * Reset some global state.
     */
    public void reset() {
        InstrumentationRegistry.registerInstance(mInstrumentation, Bundle.EMPTY);
        mSettingsProvider.reset();
    }
}
