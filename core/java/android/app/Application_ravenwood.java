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

import android.content.Context;

/**
 * Used on Ravenwood to access {@link Application}'s package private APIs.
 *
 * Unlike the name suggests, this is not used as a @RavenwoodRedirect class.
 * It's more of a "reverse" redirect class.
 */
public class Application_ravenwood {
    /** Backdoor to {@link Application#attach}, which is package-private */
    public static void attachBaseContext(Application application, Context baseContext) {
        application.attach(baseContext);
    }
}
