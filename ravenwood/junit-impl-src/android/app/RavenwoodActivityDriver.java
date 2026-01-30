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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.platform.test.ravenwood.RavenwoodExperimentalApiChecker;
import android.platform.test.ravenwood.RavenwoodUtils;
import android.view.Display;
import android.view.WindowManager;

/**
 * Experimental implementation of launching activities.
 */
@SuppressWarnings("unchecked")
public class RavenwoodActivityDriver {
    private static final RavenwoodActivityDriver sInstance = new RavenwoodActivityDriver();

    private RavenwoodActivityDriver() {
    }

    public static RavenwoodActivityDriver getInstance() {
        return sInstance;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Activity> Class<T> resolveActivityClass(Intent intent) {
        try {
            // TODO: Handle the case where the component name is null.
            return (Class<T>) Class.forName(intent.getComponent().getClassName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start an activity", e);
        }
    }

    private static ActivityInfo makeActivityInfo(
            @NonNull ApplicationInfo appInfo, @NonNull Intent intent) {
        var clazz = resolveActivityClass(intent);

        // Here's an example ActivityInfo from the launcher:
        // http://screen/Beq9oSdYSntJr5k
        ActivityInfo acti = new ActivityInfo();
        acti.applicationInfo = appInfo;
        acti.enabled = true;
        acti.exported = true;
        acti.packageName = appInfo.packageName;

        acti.labelRes = 0; // TODO
        acti.launchMode = 2; // from the example
        acti.resizeMode = 2; // from the example
        acti.flags = 0; // TODO
        acti.name = clazz.getName();

        acti.processName = appInfo.packageName;

        return acti;
    }

    /**
     * Instantiate an activity and driver it to the "RESUMED" state.
     */
    public <T extends Activity> T createResumedActivity(
            Intent intent,
            Bundle activityOptions // ignored for now.
    ) throws Exception {
        RavenwoodExperimentalApiChecker.onExperimentalApiCalled(0);

        Application app = RavenwoodAppDriver.getInstance().getApplication();
        ContextImpl appImpl = ContextImpl.getImpl(app);
        ApplicationInfo appInfo = appImpl.getApplicationInfo();
        ActivityInfo activityInfo = makeActivityInfo(appInfo, intent);
        Binder token = new Binder();
        int displayId = Display.DEFAULT_DISPLAY;

        ContextImpl activityContextImpl = ContextImpl.createActivityContext(
                appImpl.mMainThread,
                appImpl.mPackageInfo,
                activityInfo,
                token,
                displayId,
                null // overrideConfiguration
        );

        T activity = (T) resolveActivityClass(intent).getConstructor().newInstance();
        activity.attach(
                activityContextImpl,
                appImpl.mMainThread,
                appImpl.mMainThread.mInstrumentation,
                token,
                0, // mIdent -- ??
                app,
                intent,
                activityInfo,
                "Activity title",
                null, // parent
                "embeddedID", // mEmbeddedID -- ??
                null, // lastNonConfigurationInstances
                null, // config
                "referrer",
                null, // IVoiceInteractor,
                null, // window
                null, // activityConfigCallback
                null, // assistToken
                null, // shareableActivityToken
                null // initialCallerInfoAccessToken
        );

        // ActivityController has this.
        // return create().start().postCreate(null).resume().visible().topActivityResumed(true);

        Bundle savedInstanceState = null;
        activity.performCreate(savedInstanceState);
        activity.performStart("Called by Ravenwood");
        activity.onPostCreate(savedInstanceState);
        activity.performResume(false, // Followed by pause
                "Called by Ravenwood");
        // ActivityController has
        // "emulate logic of ActivityThread#handleResumeActivity"

        // Make visible -- this requires some setup, which is copied from ActivityController.
        activity.getWindow().getAttributes().type =
                WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
        activity.mDecor = activity.getWindow().getDecorView();
        activity.makeVisible();

        activity.performTopResumedActivityChanged(
                true, // isTop
                "Called by Ravenwood");

        RavenwoodUtils.getMainHandler().post(() ->
                activity.getWindow().getDecorView().getViewRootImpl().windowFocusChanged(true));

        return activity;
    }
}
