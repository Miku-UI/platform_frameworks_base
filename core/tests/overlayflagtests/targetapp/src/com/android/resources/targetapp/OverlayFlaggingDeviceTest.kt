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
package com.android.resources.targetapp

import android.content.res.Configuration
import android.content.res.Resources

import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4

import java.util.Locale.US

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class OverlayFlaggingDeviceTest {
    companion object {
        private const val WITH_OVERLAYS_PACKAGE =
            "com.android.resources.targetappwithoverlayablexml"
        private const val WITHOUT_OVERLAYS_PACKAGE =
            "com.android.resources.targetappwithoutoverlayablexml"
    }

    private var mResources: Resources? = null

    @Before
    @kotlin.Throws(Exception::class)
    fun setUp() {
        val defaultLocaleConfiguration: Configuration = Configuration()
        defaultLocaleConfiguration.setLocale(US)
        mResources = InstrumentationRegistry
            .getInstrumentation()
            .getContext()
            .createConfigurationContext(defaultLocaleConfiguration)
            .getResources()
    }

    @Test
    @kotlin.Throws(Exception::class)
    fun testWithOverlaysNotOverlaid() {
        assertEquals("App Resource", getValue(WITH_OVERLAYS_PACKAGE))
    }

    @Test
    @kotlin.Throws(Exception::class)
    fun testWithOverlaysOverlaid() {
        assertEquals("Overlaid App Resource", getValue(WITH_OVERLAYS_PACKAGE))
    }

    @Test
    @kotlin.Throws(Exception::class)
    fun testWithoutOverlaysNotOverlaid() {
        assertEquals("App Resource", getValue(WITHOUT_OVERLAYS_PACKAGE))
    }

    @Test
    @kotlin.Throws(Exception::class)
    fun testWithoutOverlaysOverlaid() {
        assertEquals("Overlaid App Resource", getValue(WITHOUT_OVERLAYS_PACKAGE))
    }

    private fun getValue(pkg: String): String {
        return mResources!!.getString(R.string.app_resource)
    }
}