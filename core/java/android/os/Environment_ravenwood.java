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
package android.os;

import android.platform.test.ravenwood.RavenwoodEnvironment;
import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;

import java.io.File;

/**
 * Ravenwood redirect target class from {@link Environment}.
 */
public class Environment_ravenwood {
    private static final String TAG = "Environment_ravenwood";

    private static final boolean VERBOSE = RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;

    private Environment_ravenwood() {
    }

    /** Redirected from the corresponding {@link Environment} method. */
    static String getEnvPath(String variableName) {
        return System.getProperty("ravenwood.env." + variableName);
    }

    /** Redirected from the corresponding {@link Environment} method. */
    static File prep(File path) {
        prep(path.getAbsolutePath());
        return path;
    }

    /** Redirected from the corresponding {@link Environment} method. */
    static String prep(String path) {
        if (path != null) {
            path = translateAbsolutePathForRavenwood(path);
            if (VERBOSE) {
                Log.v(TAG, "mkdirs: " + path);
            }
            new File(path).mkdirs();
        }
        return path;
    }

    private static String translateAbsolutePathForRavenwood(String path) {
        if (!path.startsWith("/")) {
            throw new RuntimeException(
                    "Path doesn't start with a '/'. Actual=" + path);
        }
        var root = RavenwoodEnvironment.getInstance().getRootDir();
        if (path.startsWith(root.toString())) {
            if (VERBOSE) {
                Log.v(TAG, "translate: " + path + " is already translated");
            }
            return path;
        }
        var ret = new File(root, path).getAbsolutePath();
        if (VERBOSE) {
            Log.v(TAG, "translate: " + path + " -> " + ret);
        }
        return ret;
    }
}
