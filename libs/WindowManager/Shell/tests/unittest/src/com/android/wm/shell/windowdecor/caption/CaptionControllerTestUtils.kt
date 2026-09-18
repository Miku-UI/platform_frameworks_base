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

package com.android.wm.shell.windowdecor.caption

import com.android.wm.shell.windowdecor.HandleMenu
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Verifies a handle menu was created the given number of times matching the given [appToWebData]
 * matcher.
 */
fun HandleMenu.HandleMenuFactory.verifyHandleMenuCreated(
    times: Int = 1,
    appToWebData: ArgumentMatcher<HandleMenu.AppToWebData> =
        ArgumentMatcher<HandleMenu.AppToWebData> { true },
) {
    verify(this, times(times))
        .create(
            mainDispatcher = any(),
            mainScope = any(),
            context = any(),
            taskInfo = any(),
            parentSurface = any(),
            display = any(),
            windowManagerWrapper = any(),
            windowDecorationActions = any(),
            taskResourceLoader = any(),
            layoutResId = any(),
            splitScreenController = any(),
            shouldShowWindowingPill = any(),
            shouldShowNewWindowButton = any(),
            shouldShowManageWindowsButton = any(),
            shouldShowChangeAspectRatioButton = any(),
            shouldShowGameControlsButton = any(),
            shouldShowDesktopModeButton = any(),
            shouldShowRestartButton = any(),
            appToWebData = argThat(appToWebData),
            desktopModeUiEventLogger = any(),
            captionView = any(),
            captionWidth = any(),
            captionHeight = any(),
            captionX = any(),
            captionY = any(),
            decorThemeUtilFactory = any(),
            surfaceControlBuilderSupplier = any(),
            surfaceControlTransactionSupplier = any(),
            surfaceControlViewHostFactory = any(),
        )
}
