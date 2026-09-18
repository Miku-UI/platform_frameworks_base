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
package com.android.server.inputmethod

import android.os.Binder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.WindowManager
import android.view.inputmethod.Flags
import android.view.inputmethod.ImeTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputMethodManagerServiceSetMyImeVisibilityTest : InputMethodManagerServiceTestBase() {
    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var userData: UserData

    @Before
    override fun setUp() {
        super.setUp()

        userData = mInputMethodManagerService.getUserData(mUserId)
        val client =
            synchronized(ImfLock::class.java) {
                requireNotNull(
                    mInputMethodManagerService.getClientStateLocked(mMockInputMethodClient)
                )
            }
        userData.mImeBindingState =
            ImeBindingState(
                mUserId,
                Binder(),
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
                client,
                mEditorInfo,
            )
        userData.mCurClient = client
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SET_SELF_VISIBILITY_ONLY_ONCE)
    fun testSetMyImeVisibilityToTrue() {
        runTestSetMyImeVisibility(visible = true)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SET_SELF_VISIBILITY_ONLY_ONCE)
    fun testSetMyImeVisibilityToFalse() {
        runTestSetMyImeVisibility(visible = false)
    }

    fun runTestSetMyImeVisibility(visible: Boolean) {
        synchronized<Unit>(ImfLock::class.java) {
            mInputMethodManagerService.setMyImeVisibilityLocked(
                visible,
                ImeTracker.Token.empty(),
                userData,
            )
        }
        verifySetImeVisibility(/* setVisible= */ visible, /* invoked= */ true)
    }
}
