/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.tests.font.screenshot

import androidx.test.platform.app.InstrumentationRegistry
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig

private const val ASSETS_PATH = "frameworks/base/core/tests/text/FontRenderingScreenshotTests/assets"

/** GoldenPathManager for Font rendering tests. */
class FontGoldenPathManager() :
    GoldenPathManager(
        appContext = InstrumentationRegistry.getInstrumentation().context,
        assetsPathRelativeToBuildRoot = ASSETS_PATH,
        deviceLocalPath =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .filesDir
                .absolutePath
                .toString() + "/font_screenshots",
        pathConfig = PathConfig(),
    )
