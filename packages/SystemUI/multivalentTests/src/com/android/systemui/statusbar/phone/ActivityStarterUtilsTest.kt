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

package com.android.systemui.statusbar.phone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ActivityStarterUtilsTest : SysuiTestCase() {
    @Mock private lateinit var controller: ActivityTransitionAnimator.Controller

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun addCookieIfNeeded_addsACookie() {
        // GIVEN a controller with no cookie.
        whenever(controller.transitionCookie).thenReturn(null)

        // WHEN addCookieIfNeeded() is called with the given controller.
        val controllerWithCookie = addCookieIfNeeded(controller)

        // THEN the returned controller has a new cookie.
        assertNotNull(controllerWithCookie)
        assertNotNull(controllerWithCookie.transitionCookie)
        assertEquals(
            ActivityTransitionAnimator.TransitionCookie("$controller"),
            controllerWithCookie.transitionCookie,
        )
    }

    @Test
    fun addCookieIfNeeded_doesNotAddACookie_ifControllerIsNull() {
        // WHEN addCookieIfNeeded() is called with a null controller.
        val controllerWithCookie = addCookieIfNeeded(null)

        // THEN the returned controller is also null.
        assertNull(controllerWithCookie)
    }

    @Test
    fun addCookieIfNeeded_doesNotAddACookie_ifControllerAlreadyHasOne() {
        // GIVEN a controller with a cookie
        val cookie = mock<ActivityTransitionAnimator.TransitionCookie>()
        whenever(controller.transitionCookie).thenReturn(cookie)

        // WHEN addCookieIfNeeded() is called with the given controller.
        val controllerWithCookie = addCookieIfNeeded(controller)

        // THEN the returned controller has the same cookie.
        assertNotNull(controllerWithCookie)
        assertNotNull(controllerWithCookie.transitionCookie)
        assertEquals(controller.transitionCookie, controllerWithCookie.transitionCookie)
    }
}
