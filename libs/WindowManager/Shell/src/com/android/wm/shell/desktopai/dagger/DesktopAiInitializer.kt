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

package com.android.wm.shell.desktopai.dagger

import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopai.api.ITriggerManager
import com.android.wm.shell.desktopai.core.CujHandlerRegistry
import javax.inject.Inject

/** Singleton used to initialize all the DesktopAi dependencies in a single place */
@WMSingleton
class DesktopAiInitializer
@Inject
constructor(triggerManager: ITriggerManager, cujHandlerRegistry: CujHandlerRegistry)
