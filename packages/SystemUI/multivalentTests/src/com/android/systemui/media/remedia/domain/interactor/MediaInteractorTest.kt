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

package com.android.systemui.media.remedia.domain.interactor

import android.media.session.MediaSession
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.flags.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.media.mediaOutputDialogManager
import com.android.systemui.media.remedia.data.repository.fakeActiveMediaData
import com.android.systemui.media.remedia.data.repository.setFakeCurrentMediaData
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@UiThreadTest
class MediaInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val testScope = kosmos.testScope

    private val underTest: MediaInteractor = kosmos.mediaInteractor

    @Test
    fun onUpdateIsGutsVisible_valueIsUpdatedVisible() =
        testScope.runTest {
            assertThat(underTest.isGutsVisible).isFalse()
            underTest.setIsGutsVisible(true)

            assertThat(underTest.isGutsVisible).isTrue()

            underTest.setIsGutsVisible(false)

            assertThat(underTest.isGutsVisible).isFalse()
        }

    @Test
    fun onUpdateSelectedIndex_valueIsUpdated() =
        testScope.runTest {
            assertThat(underTest.currentCarouselIndex).isEqualTo(0)

            underTest.storeCurrentCarouselIndex(1)

            assertThat(underTest.currentCarouselIndex).isEqualTo(1)
        }

    @Test
    fun reorderMedia_resetsIndex_resetsIsGutsVisible() =
        testScope.runTest {
            underTest.reorderMedia()

            assertThat(underTest.isGutsVisible).isFalse()
            assertThat(underTest.currentCarouselIndex).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun onOutputDeviceClick_multiUserFlagEnabled_showsOutputDialog() =
        testScope.runTest {
            val packageName = "com.test.app"
            val appUid = 12345
            val token = MediaSession(context, "TEST").sessionToken
            val mediaData =
                kosmos.fakeActiveMediaData.copy(
                    packageName = packageName,
                    appUid = appUid,
                    token = token,
                )
            kosmos.setFakeCurrentMediaData(listOf(mediaData))

            val session = underTest.sessions[0]
            session.outputDevice.onClick(null)

            verify(kosmos.mediaOutputDialogManager)
                .createAndShowWithController(
                    packageName = packageName,
                    aboveStatusBar = true,
                    controller = null,
                    userHandle = UserHandle.getUserHandleForUid(appUid),
                    token = token,
                )
        }

    @Test
    @DisableFlags(Flags.FLAG_FIX_OUTPUT_SWITCHER_MULTIUSER_SUPPORT)
    fun onOutputDeviceClick_multiUserFlagDisabled_showsOutputDialog() =
        testScope.runTest {
            val packageName = "com.test.app"
            val appUid = 12345
            val token = MediaSession(context, "TEST").sessionToken
            val mediaData =
                kosmos.fakeActiveMediaData.copy(
                    packageName = packageName,
                    appUid = appUid,
                    token = token,
                )
            kosmos.setFakeCurrentMediaData(listOf(mediaData))

            val session = underTest.sessions[0]
            session.outputDevice.onClick(null)

            verify(kosmos.mediaOutputDialogManager)
                .createAndShowWithController(
                    packageName = packageName,
                    aboveStatusBar = true,
                    controller = null,
                    userHandle = null,
                    token = token,
                )
        }
}
