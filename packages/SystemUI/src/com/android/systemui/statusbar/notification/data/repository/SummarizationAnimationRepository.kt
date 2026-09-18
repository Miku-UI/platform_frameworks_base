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

package com.android.systemui.statusbar.notification.data.repository

import android.graphics.drawable.AnimatedVectorDrawable
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/**
 * Repository for animation summarization - tracks whether an entry still needs animation, and which
 * drawables correspond to which entries.
 */
@SysUISingleton
class SummarizationAnimationRepository @Inject constructor() {
    val animationHistory: MutableMap<String, AnimationState> = mutableMapOf()
    val drawables: MutableMap<String, AnimatedVectorDrawable> = mutableMapOf()
}

enum class AnimationState {
    NEEDS_ANIMATION,
    HAS_ANIMATED,
}
