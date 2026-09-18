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

package com.android.systemui.statusbar.quickactions.assistant.data.repository

import android.content.ComponentName
import android.content.pm.UserInfo
import android.os.UserManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.assist.mockAssistManager
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.argThat
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AssistantRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val assistManager = kosmos.mockAssistManager
    private val userRepository = kosmos.fakeUserRepository
    private val securitySettings = kosmos.fakeSettings

    private val underTest = kosmos.assistantRepository

    @Before
    fun setup() {
        runBlocking { userRepository.setUserInfos(listOf(USER)) }
    }

    @Test
    fun assistantInfo_afterSettingChanged() =
        kosmos.runTest {
            whenever(assistManager.getAssistInfoForUser(any<Int>()))
                .thenReturn(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))

            val assistInfo by collectLastValue(underTest.assistInfo)
            assertThat(assistInfo).isNull()

            // Selected user changed, [AssistantRepository.assistInfo] is refreshed with the
            // current value in AssistManager.
            runBlocking { userRepository.setSelectedUserInfo(USER) }

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEqualTo(ASSISTANT_PACKAGE)

            // Assistant Setting changed, [AssistantRepository.assistInfo] is refreshed with the
            // current value in AssistManager.
            whenever(assistManager.getAssistInfoForUser(any<Int>()))
                .thenReturn(ComponentName(ASSISTANT_PACKAGE_2, ASSISTANT_CLASS))
            securitySettings.putIntForUser(Settings.Secure.ASSISTANT, 1, 0)
            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEqualTo(ASSISTANT_PACKAGE_2)
        }

    @Test
    fun assistantInfo_verifyInvocationType() =
        kosmos.runTest {
            underTest.startAssistant(context)
            verify(assistManager)
                .startAssist(
                    /* context = */ eq(context),
                    /* args = */ argThat { bundle ->
                        bundle.getInt(AssistManager.INVOCATION_TYPE_KEY) ==
                            AssistManager.INVOCATION_TYPE_STATUS_BAR_ICON
                    },
                )
        }

    private companion object {
        private const val ASSISTANT_PACKAGE = "the.assistant.app"
        private const val ASSISTANT_PACKAGE_2 = "the.assistant.app2"
        private const val ASSISTANT_CLASS = "the.assistant.app.class"
        private val USER =
            UserInfo(0, "test", "", UserInfo.FLAG_FULL, UserManager.USER_TYPE_FULL_SYSTEM)
    }
}
