/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.platform.test.ravenwood.RavenwoodExperimentalApiChecker.onExperimentalApiCalled;

import android.content.pm.PackageManager;
import android.os.FileUtils;
import android.os.IBinder;
import android.platform.test.ravenwood.RavenwoodPackageManager;

import java.io.File;

public class ContextImpl_ravenwood {
    private static final String TAG = "ContextImpl_ravenwood";

    static PackageManager getPackageManagerInner(ContextImpl contextImpl) {
        return RavenwoodPackageManager.create(contextImpl);
    }

    static File ensurePrivateDirExists(File file, int mode, int gid, String xattr) {
        if (!file.exists()) {
            final String path = file.getAbsolutePath();
            file.mkdirs();

            // setPermissions() may fail and return an error status, but we just ignore
            // it just like the real method ignores exceptions.
            // setPermissions() does a log though.
            FileUtils.setPermissions(path, mode, -1, -1);
        }
        return file;
    }

    /** Experimental implementation */
    static int checkPermission(ContextImpl self, String permission, int pid, int uid) {
        onExperimentalApiCalled(2);
        return PERMISSION_GRANTED;
    }

    /** Experimental implementation */
    static int checkPermission(ContextImpl self, String permission, int pid, int uid,
            IBinder callerToken) {
        onExperimentalApiCalled(2);
        return PERMISSION_GRANTED;
    }
}
