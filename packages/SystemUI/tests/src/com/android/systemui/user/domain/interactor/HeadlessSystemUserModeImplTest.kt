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
 *
 */
package com.android.systemui.user.domain.interactor

import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadlessSystemUserModeImplTest : SysuiTestCase() {

    @get:Rule val expect: Expect = Expect.create()

    private lateinit var mockitoSession: MockitoSession

    private val kosmos = testKosmos()
    private val underTest = kosmos.headlessSystemUserMode

    @Before
    fun startSession() {
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .mockStatic(UserManager::class.java)
                .startMocking()
    }

    @After
    fun closeSession() {
        mockitoSession.finishMocking()
    }

    @Test
    fun isHeadlessSystemUserMode_whenDeviceIsNotHsum_false() {
        mockIsHsum(false)

        assertWithMessage("HeadlessSystemUserMode.isHeadlessSystemUserMode()")
            .that(underTest.isHeadlessSystemUserMode())
            .isFalse()
    }

    @Test
    fun isHeadlessSystemUserMode_whenDeviceIsHsum_true() {
        mockIsHsum(true)

        assertWithMessage("HeadlessSystemUserMode.isHeadlessSystemUserMode()")
            .that(underTest.isHeadlessSystemUserMode())
            .isTrue()
    }

    @Test
    fun isHeadlessSystemUser_whenDeviceIsNotHsum() =
        kosmos.runTest {
            mockIsHsum(false)

            expect
                .withMessage("HeadlessSystemUserMode.isHeadlessSystemUser(%s)", SYSTEM_USER)
                .that(underTest.isHeadlessSystemUser(SYSTEM_USER))
                .isFalse()
            expect
                .withMessage("HeadlessSystemUserMode.isHeadlessSystemUser(%s)", NON_SYSTEM_USER)
                .that(underTest.isHeadlessSystemUser(NON_SYSTEM_USER))
                .isFalse()
        }

    @Test
    fun isHeadlessSystemUser_whenDeviceIsHsum() =
        kosmos.runTest {
            mockIsHsum(true)

            expect
                .withMessage("HeadlessSystemUserMode.isHeadlessSystemUser(%s)", SYSTEM_USER)
                .that(underTest.isHeadlessSystemUser(SYSTEM_USER))
                .isTrue()
            expect
                .withMessage("HeadlessSystemUserMode.isHeadlessSystemUser(%s)", NON_SYSTEM_USER)
                .that(underTest.isHeadlessSystemUser(NON_SYSTEM_USER))
                .isFalse()
        }

    private fun mockIsHsum(hsum: Boolean) {
        ExtendedMockito.doReturn(hsum).`when`(UserManager::isHeadlessSystemUserMode)
    }

    companion object {
        private const val SYSTEM_USER = UserHandle.USER_SYSTEM
        private const val NON_SYSTEM_USER = 42
    }
}
