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

package com.android.systemui.headline.ui

import com.android.systemui.headline.ui.compose.Headline
import com.android.systemui.headline.ui.compose.HeadlineImpl
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.MutableHeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.MutableHeadlineViewModelImpl
import dagger.Binds
import dagger.Module

/** Dagger module for the Headline pod. */
@Module
public interface HeadlineModule {
    /** Binds [HeadlineImpl] to [Headline]. */
    @Binds public fun bindHeadline(impl: HeadlineImpl): Headline

    /** Binds [MutableHeadlineViewModelImpl] to [HeadlineViewModel] */
    @Binds
    public fun bindMutableHeadlineViewModel(
        impl: MutableHeadlineViewModelImpl
    ): MutableHeadlineViewModel
}
