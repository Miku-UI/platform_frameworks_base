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

package com.android.systemui.keyguard.domain.interactor

import android.app.KeyguardManager.LOCK_ON_USER_SWITCH_CALLBACK
import android.content.pm.UserInfo
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.keyguardServiceShowLockscreenRepository
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class KeyguardServiceShowLockscreenInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val repository = kosmos.keyguardServiceShowLockscreenRepository
    private val underTest = kosmos.keyguardServiceShowLockscreenInteractor

    val primaryUser = UserInfo(10, "user 10", 0)
    val secondaryUser = UserInfo(11, "user 11", 0)

    @Before
    fun setup() {
        underTest.start()

        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(None)
        kosmos.sceneInteractor.setTransitionState(
            flowOf(ObservableTransitionState.Idle(Scenes.Gone))
        )

        runBlocking {
            kosmos.fakeUserRepository.setUserInfos(listOf(primaryUser, secondaryUser))
            kosmos.fakeUserRepository.setSelectedUserInfo(primaryUser)
        }
    }

    @Test
    fun onKeyguardServiceShowDismissibleKeyguard_emitsFoldedWithSwipeUp() =
        kosmos.runTest {
            val values by collectValues(underTest.showNowEvents)

            underTest.onKeyguardServiceShowDismissibleKeyguard()

            assertThat(values)
                .containsExactly(ShowWhileAwakeReason.FOLDED_WITH_SWIPE_UP_TO_CONTINUE)
        }

    @Test
    fun onKeyguardServiceDoKeyguardTimeout_emitsTimeout() =
        kosmos.runTest {
            val values by collectValues(underTest.showNowEvents)

            underTest.onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            assertThat(values)
                .containsExactly(ShowWhileAwakeReason.KEYGUARD_TIMEOUT_WHILE_SCREEN_ON)
        }

    @Test
    fun onKeyguardServiceDoKeyguardTimeout_notifyCallbacks_whenLockscreenBecomesVisible() =
        kosmos.runTest {
            val callback = onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            verify(callback).sendResult(null)
        }

    @Test
    fun onKeyguardServiceDoKeyguardTimeout_notifyCallbacks_immediately_ifKeyguardDisabled() =
        kosmos.runTest {
            kosmos.fakeKeyguardRepository.setKeyguardEnabled(false)

            val callback = onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            verify(callback).sendResult(null)
        }

    @Test
    fun onKeyguardServiceDoKeyguardTimeout_notifyCallbacks_immediately_ifLockscreenAlreadyVisible() =
        kosmos.runTest {
            kosmos.sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )

            val callback = onKeyguardServiceDoKeyguardTimeout()
            runCurrent()

            verify(callback).sendResult(null)
        }

    @Test
    fun onKeyguardServiceDoKeyguardTimeout_doNotNotifyCallbacks_ifUserMismatch() =
        kosmos.runTest {
            val callback = onKeyguardServiceDoKeyguardTimeout()

            // Switch users before the scene transitions to lockscreen
            kosmos.fakeUserRepository.setSelectedUserInfo(secondaryUser)
            runCurrent()

            kosmos.sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )

            verify(callback, never()).sendResult(null)
        }

    private suspend fun onKeyguardServiceDoKeyguardTimeout(): IRemoteCallback {
        val callback = createMockCallback()
        repository.addShowLockscreenCallback(primaryUser.id, callback)
        underTest.onKeyguardServiceDoKeyguardTimeout(
            Bundle().apply { putBinder(LOCK_ON_USER_SWITCH_CALLBACK, callback.asBinder()) }
        )

        return callback
    }

    private fun createMockCallback(): IRemoteCallback {
        val binder = mock<IBinder>()
        return mock<IRemoteCallback> { on { asBinder() } doReturn binder }
    }
}
