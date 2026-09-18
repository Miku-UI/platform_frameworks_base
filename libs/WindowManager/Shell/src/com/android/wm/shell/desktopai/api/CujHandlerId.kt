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

package com.android.wm.shell.desktopai.api

/**
 * Type-safe identifiers for all registered [CujHandler]s. Each object represents a unique handler
 * that can be registered in the [CujHandlerRegistry].
 */
sealed class CujHandlerId(val id: String) {
    object PersonalContextCujHandler : CujHandlerId("CONTEXT_ENGINE_HINT_HANDLER")

    object ShellCujHandler : CujHandlerId("SHELL_CONTEXT_HANDLER")
    // Add new handler identifiers here as needed.
}
