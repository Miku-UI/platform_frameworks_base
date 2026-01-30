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

package com.android.systemui.screencapture.common

import javax.inject.Scope

/**
 * Scope annotation for Screen Capture scoped items within the [ScreenCaptureUiComponent].
 *
 * This scope exists when Screen Capture UI is visible.
 */
@Scope
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenCaptureUiScope

/**
 * Scope annotation for Screen Capture scoped items within the [ScreenCaptureComponent].
 *
 * This scope exists from the first Screen Capture UI appearance throughout the capturing.
 */
@Scope
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenCaptureScope
