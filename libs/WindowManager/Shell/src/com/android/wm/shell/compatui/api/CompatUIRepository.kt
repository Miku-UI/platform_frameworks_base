/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.api

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl

/** Repository of all the available CompatUISpec */
class CompatUIRepository() :
    GenericRepository<String, CompatUISpec> by MemoryRepositoryImpl(
        logger = { msg -> ProtoLog.v(WM_SHELL_APP_COMPAT, "CompatUIRepository: %s", msg) }
    ) {

    /** Simple way to register a [CompatUISpec]. */
    fun registerSpec(spec: CompatUISpec): Boolean = insert(spec.name, spec, true)
}
