/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.statusbar.window

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.fakeWindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.fragments.fragmentService
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.policy.mockStatusBarConfigurationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarWindowControllerImplTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().also { it.statusBarWindowViewInflater = it.fakeStatusBarWindowViewInflater }

    private val fakeExecutor = kosmos.fakeExecutor
    private val fakeWindowManager = kosmos.fakeWindowManager
    private val mockFragmentService = kosmos.fragmentService
    private val fakeStatusBarWindowViewInflater = kosmos.fakeStatusBarWindowViewInflater
    private val statusBarConfigurationController = kosmos.mockStatusBarConfigurationController

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun attach_connectedDisplaysFlagEnabled_setsConfigControllerOnWindowView() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.attach()

        verify(windowView).setStatusBarConfigurationController(statusBarConfigurationController)
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun attach_connectedDisplaysFlagDisabled_doesNotSetConfigControllerOnWindowView() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val mockWindowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.attach()

        verify(mockWindowView, never()).setStatusBarConfigurationController(any())
    }

    @Test
    @EnableFlags(StatusBarRootModernization.FLAG_NAME, StatusBarConnectedDisplays.FLAG_NAME)
    fun stop_statusBarModernizationFlagEnabled_doesNotRemoveFragment() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.stop()
        fakeExecutor.runAllReady()

        verify(mockFragmentService, never()).removeAndDestroy(windowView)
    }

    @Test
    @DisableFlags(StatusBarRootModernization.FLAG_NAME)
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun stop_statusBarModernizationFlagDisabled_removesFragment() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.stop()
        fakeExecutor.runAllReady()

        verify(mockFragmentService).removeAndDestroy(windowView)
    }

    @Test
    @DisableFlags(StatusBarRootModernization.FLAG_NAME)
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun stop_statusBarModernizationFlagDisabled_removesFragmentOnExecutor() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.stop()

        verify(mockFragmentService, never()).removeAndDestroy(windowView)
        fakeExecutor.runAllReady()
        verify(mockFragmentService).removeAndDestroy(windowView)
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun stop_removesWindowViewFromWindowManager() {
        val underTest = kosmos.statusBarWindowControllerImpl

        underTest.attach()
        underTest.stop()

        assertThat(fakeWindowManager.addedViews).isEmpty()
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun stop_connectedDisplaysFlagDisabled_crashes() {
        val underTest = kosmos.statusBarWindowControllerImpl

        assertThrows(IllegalStateException::class.java) {
            underTest.stop()
        }
    }

    @Test
    fun attach_windowViewAddedToWindowManager() {
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.attach()

        assertThat(fakeWindowManager.addedViews.keys).containsExactly(windowView)
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun attachThenStops_connectedDisplaysFlagEnabled_registersAndUnregistersConfigControllerListener() {
        val underTest = kosmos.statusBarWindowControllerImpl
        underTest.attach()

        verify(statusBarConfigurationController).addCallback(any())

        underTest.stop()

        verify(statusBarConfigurationController).removeCallback(any())
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun attach_connectedDisplaysFlagDisabled_doesNotRegisterConfigControllerListener() {
        val underTest = kosmos.statusBarWindowControllerImpl

        underTest.attach()

        verify(statusBarConfigurationController, never()).addCallback(any())
    }

    @Test
    fun attach_defaultDisplay_attachedWindowHasDefaultTitle() {
        kosmos.statusBarWindowControllerImplDisplayId = Display.DEFAULT_DISPLAY
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.attach()

        val windowParams = fakeWindowManager.addedViews[windowView]!!
        assertThat(windowParams.title).isEqualTo("StatusBar")
    }

    @Test
    fun attach_nonDefaultDisplay_attachedWindowHasTitleWithDisplayId() {
        kosmos.statusBarWindowControllerImplDisplayId = 123
        val underTest = kosmos.statusBarWindowControllerImpl
        val windowView = fakeStatusBarWindowViewInflater.inflatedMockViews.first()

        underTest.attach()

        val windowParams = fakeWindowManager.addedViews[windowView]!!
        assertThat(windowParams.title).isEqualTo("StatusBar(displayId=123)")
    }
}
