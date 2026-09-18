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

package com.android.server.appfunctions.dynamic;

import android.annotation.NonNull;
import android.app.appfunctions.AppFunctionName;

import java.util.Set;

/**
 * Listener for changes in the registration state of app functions.
 */
public interface OnRegistrationStateChangedListener {
    /**
     * Called when the registration state of one or more app functions has changed.
     *
     * @param changedFunctionNames The names of the app functions whose registration state has
     *     changed.
     */
    void onRegistrationChanged(@NonNull Set<AppFunctionName> changedFunctionNames);
}
