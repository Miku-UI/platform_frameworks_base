/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.user

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.user.domain.interactor.UserSwitcherInteractor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/** Tests for [UserDialogReceiver]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class UserDialogReceiverTest : SysuiTestCase() {

    // Mock the interactor dependency to verify interactions.
    @Mock private lateinit var mockUserSwitcherInteractor: UserSwitcherInteractor

    // The instance of the BroadcastReceiver we are testing.
    private lateinit var userDialogReceiver: UserDialogReceiver

    @Before
    fun setUp() {
        // Initialize mocks created with the @Mock annotation.
        MockitoAnnotations.initMocks(this)
        // Create an instance of the receiver with the mocked dependency.
        userDialogReceiver = UserDialogReceiver(mockUserSwitcherInteractor)
    }

    /**
     * Verifies that when the correct action is received, the user switcher dialog is shown. This
     * assumes the system has already granted permission and allowed the broadcast.
     */
    @Test
    fun onReceive_withLaunchUserSwitcherAction_showsUserSwitcher() {
        // Arrange: Create an intent with the specific action the receiver listens for.
        val intent = Intent(UserDialogReceiver.LAUNCH_USER_SWITCHER_DIALOG)

        // Act: Trigger the onReceive method.
        userDialogReceiver.onReceive(context, intent)

        // Assert: Verify that the interactor's showUserSwitcher method was called correctly.
        verify(mockUserSwitcherInteractor).showUserSwitcher(null, context)
    }

    /** Verifies that if an unknown action is received, the interactor is not called. */
    @Test
    fun onReceive_withUnknownAction_doesNotShowUserSwitcher() {
        // Arrange: Create an intent with an action that the receiver should ignore.
        val intent = Intent("com.android.systemui.action.SOME_OTHER_ACTION")

        // Act: Trigger the onReceive method.
        userDialogReceiver.onReceive(context, intent)

        // Assert: Verify that the showUserSwitcher method was never called.
        verify(mockUserSwitcherInteractor, never()).showUserSwitcher(any(), any())
    }

    /** Verifies that if the intent action is null, the interactor is not called. */
    @Test
    fun onReceive_withNullAction_doesNotShowUserSwitcher() {
        // Arrange: Create an intent with no action set.
        val intent = Intent()

        // Act: Trigger the onReceive method.
        userDialogReceiver.onReceive(context, intent)

        // Assert: Verify that the showUserSwitcher method was never called.
        verify(mockUserSwitcherInteractor, never()).showUserSwitcher(any(), any())
    }
}
