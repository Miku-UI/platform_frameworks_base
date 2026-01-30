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

package com.android.systemui.statusbar.chips.ui.viewmodel

import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel

/**
 * An [OngoingActivityChipsRefiner] takes the entire set of chips produced by the
 * [OngoingActivityChipsViewModel] and can apply a transform on them to produce a modified set of
 * chips.
 */
interface OngoingActivityChipsRefiner {
    fun transform(input: MultipleOngoingActivityChipsModel): MultipleOngoingActivityChipsModel
}
