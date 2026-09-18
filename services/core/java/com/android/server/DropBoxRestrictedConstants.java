/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server;

import android.view.ViewRootImpl.CalledFromWrongThreadException;

import java.util.List;

/**
 * Limited set of DropBox-related defaults that require additional review.
 */
final class DropBoxRestrictedConstants {
    // Tags that we should drop by default.
    public static final List<String> DISABLED_BY_DEFAULT_TAGS =
            List.of("data_app_wtf", "system_app_wtf", "system_server_wtf");

    // Exceptions that should be enabled by default, overriding the default tag setting.
    // WARNING: This list should be *extremely* limited. Any new additions should go through the
    // proper system heath and telemetry review channels for approval.
    // See b/481975725 for additional context.
    public static final List<String> ENABLED_BY_DEFAULT_EXCEPTIONS =
            List.of(CalledFromWrongThreadException.class.getName());

    private DropBoxRestrictedConstants() {}
}
