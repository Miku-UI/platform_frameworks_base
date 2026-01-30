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

package com.android.systemui.common.shared.model

import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class IconTest : SysuiTestCase() {
    @Test
    fun loadedIcons_withSameDrawableAndDescription_areEqual() {
        val drawable = TestStubDrawable("icon")
        val icon1 = Icon.Loaded(drawable, ContentDescription.Loaded("description"))
        val icon2 = Icon.Loaded(drawable, ContentDescription.Loaded("description"))

        assertThat(icon1 == icon2).isTrue()
        assertThat(icon1.hashCode()).isEqualTo(icon2.hashCode())
    }

    @Test
    fun loadedIcons_withSameDrawableAndDifferentDescription_areNotEqual() {
        val drawable = TestStubDrawable("icon")
        val icon1 = Icon.Loaded(drawable, ContentDescription.Loaded("description"))
        val icon2 = Icon.Loaded(drawable, ContentDescription.Loaded("different description"))

        assertThat(icon1 == icon2).isFalse()
        assertThat(icon1.hashCode()).isNotEqualTo(icon2.hashCode())
    }

    @Test
    fun loadedIcons_withDifferentDrawables_areNotEqual() {
        val icon1 = Icon.Loaded(TestStubDrawable("icon1"), null)
        val icon2 = Icon.Loaded(TestStubDrawable("icon2"), null)

        assertThat(icon1 == icon2).isFalse()
        assertThat(icon1.hashCode()).isNotEqualTo(icon2.hashCode())
    }

    @Test
    fun loadedIcons_withSameResIdAndDescription_areEqual() {
        val icon1 = Icon.Loaded(TestStubDrawable("icon1"), null, 123)
        val icon2 = Icon.Loaded(TestStubDrawable("icon2"), null, 123)

        assertThat(icon1 == icon2).isTrue()
        assertThat(icon1.hashCode()).isEqualTo(icon2.hashCode())
    }

    @Test
    fun loadedIcons_withSameResIdAndDifferentPackage_areNotEqual() {
        val drawable = TestStubDrawable("icon")
        val icon1 = Icon.Loaded(drawable, null, 123, "package1")
        val icon2 = Icon.Loaded(drawable, null, 123, "package2")

        assertThat(icon1 == icon2).isFalse()
        assertThat(icon1.hashCode()).isNotEqualTo(icon2.hashCode())
    }

    @Test
    fun loadedIcons_withOneNullResId_areNotEqual() {
        val drawable = TestStubDrawable("icon")
        val icon1 = Icon.Loaded(drawable, null, 123)
        val icon2 = Icon.Loaded(drawable, null)

        assertThat(icon1 == icon2).isFalse()
        assertThat(icon1.hashCode()).isNotEqualTo(icon2.hashCode())
    }
}
