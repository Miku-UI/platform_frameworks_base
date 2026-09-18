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

package com.android.wm.shell.desktopai.api.config

import com.android.wm.shell.desktopai.api.ContextQueryFactory
import com.android.wm.shell.desktopai.api.CujHandlerId

/**
 * Configuration for a Critical User Journey (CUJ) in the Desktop AI framework.
 *
 * This class defines the identity of a CUJ and the [TriggerStrategy] used to determine when it
 * should be activated based on incoming [TriggerEvent]s.
 *
 * @property cujId A unique identifier for the CUJ (e.g., "DESKTOP_AI_OVERVIEW").
 * @property triggerStrategy The strategy that defines the conditions under which this CUJ is
 *   triggered.
 * @property contextQueryFactory The factory for the query that defines what information about the
 *   user are needed for this CUJ.
 * @property handlerIds The identifiers of the component responsible for executing the CUJ's flow.
 */
data class CujConfiguration(
    val cujId: String,
    val triggerStrategy: TriggerStrategy,
    val contextQueryFactory: ContextQueryFactory,
    val handlerIds: List<CujHandlerId>,
)
