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

package com.android.systemui.activity.data.repository

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ActivityIntentRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { realActivityIntentRepository }

    @Test
    fun wouldPendingIntentShowOverLockscreen_activityFlagShowWhenLocked_true() =
        kosmos.runTest {
            val activityInfo = ActivityInfo().apply { flags = ActivityInfo.FLAG_SHOW_WHEN_LOCKED }
            val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
            val pendingIntent =
                mock<PendingIntent>().apply {
                    whenever(this.queryIntentComponents(any())).thenReturn(listOf(resolveInfo))
                    whenever(this.intent).thenReturn(Intent())
                }

            var result: Boolean? = null
            underTest.wouldPendingIntentShowOverLockscreen(
                pendingIntent,
                currentUserId = 0,
                fakeExecutor,
                callbackParameters = Unit,
            ) { wouldShow, _ ->
                result = wouldShow
            }
            fakeExecutor.runAllReady()

            assertThat(result).isTrue()
        }

    @Test
    fun wouldPendingIntentShowOverLockscreen_activityFlagShowForAllUsers_true() =
        kosmos.runTest {
            val activityInfo = ActivityInfo().apply { flags = ActivityInfo.FLAG_SHOW_FOR_ALL_USERS }
            val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
            val pendingIntent =
                mock<PendingIntent>().apply {
                    whenever(this.queryIntentComponents(any())).thenReturn(listOf(resolveInfo))
                    whenever(this.intent).thenReturn(Intent())
                }

            var result: Boolean? = null
            underTest.wouldPendingIntentShowOverLockscreen(
                pendingIntent,
                currentUserId = 0,
                fakeExecutor,
                callbackParameters = Unit,
            ) { wouldShow, _ ->
                result = wouldShow
            }
            fakeExecutor.runAllReady()

            assertThat(result).isTrue()
        }

    @Test
    fun wouldPendingIntentShowOverLockscreen_activityNoFlags_false() =
        kosmos.runTest {
            val activityInfo = ActivityInfo()
            val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
            val pendingIntent =
                mock<PendingIntent>().apply {
                    whenever(this.queryIntentComponents(any())).thenReturn(listOf(resolveInfo))
                    whenever(this.intent).thenReturn(Intent())
                }

            var result: Boolean? = null
            underTest.wouldPendingIntentShowOverLockscreen(
                pendingIntent,
                currentUserId = 0,
                fakeExecutor,
                callbackParameters = Unit,
            ) { wouldShow, _ ->
                result = wouldShow
            }
            fakeExecutor.runAllReady()

            assertThat(result).isFalse()
        }

    @Test
    fun wouldPendingIntentShowOverLockscreen_noIntentComponents_false() =
        kosmos.runTest {
            val pendingIntent =
                mock<PendingIntent>().apply {
                    whenever(this.queryIntentComponents(any())).thenReturn(emptyList<ResolveInfo>())
                    whenever(this.intent).thenReturn(Intent())
                }

            var result: Boolean? = null
            underTest.wouldPendingIntentShowOverLockscreen(
                pendingIntent,
                currentUserId = 0,
                fakeExecutor,
                callbackParameters = Unit,
            ) { wouldShow, _ ->
                result = wouldShow
            }
            fakeExecutor.runAllReady()

            assertThat(result).isFalse()
        }

    @Test
    fun wouldPendingIntentShowOverLockscreen_sendsParamsBack() =
        kosmos.runTest {
            val pendingIntent =
                mock<PendingIntent>().apply {
                    whenever(this.queryIntentComponents(any())).thenReturn(emptyList<ResolveInfo>())
                    whenever(this.intent).thenReturn(Intent())
                }

            var returnedParams: TestParams? = null
            underTest.wouldPendingIntentShowOverLockscreen(
                pendingIntent,
                currentUserId = 0,
                fakeExecutor,
                callbackParameters = TestParams(a = true, b = 412),
            ) { _, receivedParams ->
                returnedParams = receivedParams
            }
            fakeExecutor.runAllReady()

            assertThat(returnedParams!!.a).isTrue()
            assertThat(returnedParams!!.b).isEqualTo(412)
        }

    private data class TestParams(val a: Boolean, val b: Int)
}
