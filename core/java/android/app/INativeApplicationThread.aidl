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

import java.util.List;
import java.util.Map;

/**
 * System private API for communicating with the application.  This is given to
 * the activity manager by an application  when it starts up, for the activity
 * manager to tell the application about things it needs to do.
 *
 * @hide
 */
oneway interface INativeApplicationThread {
    @UnsupportedAppUsage
    void scheduleCreateService(IBinder serviceToken,
            in List<String> libraryPaths, in String permittedLibsDir,
            in String libraryName, in String baseSymbolName, int processState);

    @UnsupportedAppUsage
    void scheduleDestroyService(IBinder serviceToken);

    @UnsupportedAppUsage
    void scheduleBindService(IBinder serviceToken, IBinder bindToken,
            int intentHash,
            in @nullable @utf8InCpp String action,
            in @nullable @utf8InCpp String data,
            boolean rebind, int processState, long bindSeq);

    @UnsupportedAppUsage
    void scheduleUnbindService(IBinder serviceToken, IBinder bindToken, int intentHash);

    @UnsupportedAppUsage
    void scheduleTrimMemory(int level);

    @UnsupportedAppUsage
    void bindApplication();

    @UnsupportedAppUsage
    void setProcessState(int state);
}
