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

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.platform.test.ravenwood.RavenwoodEnvironment;
import android.platform.test.ravenwood.RavenwoodSystemServer;
import android.platform.test.ravenwood.RavenwoodUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;
import com.android.ravenwood.common.SneakyThrow;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for initializing and handling the app / process lifeclyce related
 * stuff. See also {@link RavenwoodEnvironment}.
 */
public final class RavenwoodAppDriver {
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

    /** This is an empty instance created by Objenesis. None of its fields are initialized. */
    private final ActivityThread mActivityThread;

    private final ContextImpl mSystemContextImpl;

    private final LoadedApk mTargetLoadedApk;
    private final LoadedApk mInstLoadedApk;

    private final ContextImpl mInstContext;
    private final Context mTargetContext;

    private final Instrumentation mInstrumentation;

    private final RavenwoodActivityDriver mActivityDriver = RavenwoodActivityDriver.getInstance();

    /**
     * Constructor. It essentially simulates the start of an app lifecycle.
     *
     * TODO: This is basically a scale down version of what ActivityThread.bindApplication() and
     * handleBindApplication() do.
     * Use more of the real ActivityThread code, and move it to ActivityThread.
     */
    private RavenwoodAppDriver() {
        var env = RavenwoodEnvironment.getInstance();

        // This must happen on the main thread.
        mActivityThread = ActivityThread_ravenwood.createInstance();
        mTargetLoadedApk = makeLoadedApk(mActivityThread, env.getTargetPackageName());
        mInstLoadedApk = makeLoadedApk(mActivityThread, env.getInstPackageName());

        // Create the system context.
        mSystemContextImpl = ContextImpl.createSystemContext(mActivityThread);

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

        var instArgs = Bundle.EMPTY;
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
        InstrumentationRegistry.registerInstance(mInstrumentation, instArgs);

        // Create the Application instance, which will be the "target" context.
        var application = mTargetLoadedApk.makeApplicationInner(
                // We don't support custom application classes yet, but we'll let the
                // LoadedApk decide that, so pass false here.
                /*forceDefaultAppClass=*/ false,

                // When we make the app's Application object, we pass null.
                // That's what ActivityThread does in handleBindApplication() too.
                /*instrumentation=*/ null
        );

        mActivityThread.mInitialApplication = application;
        mTargetContext = appContext;

        mInstrumentation.onCreate(instArgs);
        mInstrumentation.callApplicationOnCreate(application);

        // Maybe do it first?
        RavenwoodSystemServer.init(mSystemContextImpl);
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
}
