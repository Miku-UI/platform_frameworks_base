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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.ravenwood.annotation.RavenwoodSupported.RavenwoodProvidingImplementation;
import android.util.Log;

@RavenwoodProvidingImplementation(target = PackageManager.class)
public class RavenwoodPackageManager extends RavenwoodBasePackageManager {
    private static final String TAG = "RavenwoodPackageManager";

    final Context mContext;

    public RavenwoodPackageManager(Context context) {
        mContext = context;
    }

    /**
     * Create a new instance, which may support experimental APIs if they're enabled.
     */
    public static RavenwoodPackageManager create(Context context) {
        if (!RavenwoodExperimentalApiChecker.isExperimentalApiEnabled()) {
            return new RavenwoodPackageManager(context);
        } else {
            return new WithExperimentalApi(context);
        }
    }

    @Override
    public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags)
            throws NameNotFoundException {
        return new InstrumentationInfo();
    }

    /**
     * PackageManager implementation with experimental APIs.
     *
     * We extracted it into a subclass, so that RavenwoodSupportedAnnotationTest ignores
     * experimental APIs.
     */
    private static class WithExperimentalApi extends RavenwoodPackageManager {
        WithExperimentalApi(Context context) {
            super(context);
        }

        // TODO: Support features with RavenwoodRule.
        @Override
        public boolean hasSystemFeature(String featureName) {
            Log.w(TAG, "hasSystemFeature: " + featureName);
            return true;
        }

        @Override
        public Drawable getDefaultActivityIcon() {
            return mContext.getDrawable(com.android.internal.R.drawable.sym_def_app_icon);
        }
    }
}
