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

package com.android.wm.shell.scenarios

import android.graphics.Rect
import android.tools.Rotation
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore

/** Base test class for window resize CUJ. */
@Ignore("Base Test Class")
abstract class ResizeAppScenarioTestBase(rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    /** Assert that the app window has expanded while its left and bottom remain stable. */
    fun assertWindowExpandedFromTopRight(initialBounds: Rect, finalBounds: Rect) {
        assertWithMessage("Window width should have increased")
            .that(finalBounds.width())
            .isGreaterThan(initialBounds.width())
        assertWithMessage("Window height should have increased")
            .that(finalBounds.height())
            .isGreaterThan(initialBounds.height())
        assertWithMessage("Window left position should remain same")
            .that(finalBounds.left)
            .isEqualTo(initialBounds.left)
        assertWithMessage("Window bottom position should remain same")
            .that(finalBounds.bottom)
            .isEqualTo(initialBounds.bottom)
    }
}
