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
package com.android.hoststubgen.test.tinyframework;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TinyFrameworkHooks {

    private TinyFrameworkHooks() {
    }

    public static final Set<Class<?>> sLoadedClasses = new HashSet<>();

    /**
     * Called by classes annotated with @HostSideTestClassLoadHook
     */
    public static void onClassLoaded(Class<?> clazz) {
        sLoadedClasses.add(clazz);
    }

    /**
     * The default class load hook set in hoststubgen-standard-options.txt
     */
    public static void defaultClassLoadHook(Class<?> clazz) {
        // Do nothing
    }

    public record MethodInfo(Class<?> owner, String method, String desc) {}

    public static volatile List<MethodInfo> sCalledMethods = null;

    /**
     * The default method call hook set in hoststubgen-standard-options.txt
     */
    public static void onMethodCalled(Class<?> clazz, String method, String desc) {
        var list = sCalledMethods;
        if (list != null) {
            list.add(new MethodInfo(clazz, method, desc));
        }
    }

    public static final List<MethodInfo> sExperimentalMethodCalls = new ArrayList<>();

    /**
     * The method call hook when experimental APIs are called
     */
    public static boolean onExperimentalMethodCalled(Class<?> clazz, String method, String desc) {
        sExperimentalMethodCalls.add(new MethodInfo(clazz, method, desc));
        return true;
    }
}
