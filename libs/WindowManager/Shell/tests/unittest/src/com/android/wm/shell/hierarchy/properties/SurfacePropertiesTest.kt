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
package com.android.wm.shell.hierarchy.properties

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [SurfaceProperties].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:SurfacePropertiesTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class SurfacePropertiesTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy().apply {
        root.leash = mock<SurfaceControl>()
    }

    @Test
    fun testCopy() {
        val tx = mock<SurfaceControl.Transaction>()
        val properties = SurfaceProperties(hierarchy.root)
        properties.setLayer(tx, 1)
        properties.setAlpha(tx, 0.5f)
        properties.setCrop(tx, Rect(0, 0, 100, 100))
        properties.setBackgroundBlur(tx, 1, 2f)
        properties.setCornerRadius(tx, 1f, 2f, 3f, 4f)
        properties.show(tx)

        val copy = properties.copy()

        assertThat(copy.layer).isEqualTo(properties.layer)
        assertThat(copy.alpha).isEqualTo(properties.alpha)
        assertThat(copy.crop).isEqualTo(properties.crop)
        assertThat(copy.backgroundBlurRadius).isEqualTo(properties.backgroundBlurRadius)
        assertThat(copy.backgroundBlurScale).isEqualTo(properties.backgroundBlurScale)
        assertThat(copy.cornerRadius).isEqualTo(properties.cornerRadius)
        assertThat(copy.visibleRequested).isEqualTo(properties.visibleRequested)

        // Ensure it's a deep copy
        assertThat(copy).isNotSameInstanceAs(properties)
        assertThat(copy.crop).isNotSameInstanceAs(properties.crop)
        assertThat(copy.cornerRadius).isNotSameInstanceAs(properties.cornerRadius)
    }
}