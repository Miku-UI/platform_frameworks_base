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

package com.android.systemui.statusbar.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.wm.shell.scrolltotop.ScrollToTop
import java.util.Optional
import javax.inject.Inject

/** Interactor for the scroll-to-top feature. */
@SysUISingleton
class ScrollToTopInteractor
@Inject
constructor(private val scrollToTopOptional: Optional<ScrollToTop>) {
    /** Notifies that the status bar was tapped for a scroll-to-top action. */
    fun onScrollToTop(displayId: Int, x: Int) {
        scrollToTopOptional.ifPresent { it.onScrollToTop(displayId, x) }
    }
}
