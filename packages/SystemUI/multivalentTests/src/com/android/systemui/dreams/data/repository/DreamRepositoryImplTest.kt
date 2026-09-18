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

package com.android.systemui.dreams.data.repository

import android.app.DreamManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.RemoteException
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.service.dreams.DreamItem
import android.service.dreams.DreamPlaylist
import android.service.dreams.Flags.FLAG_DREAMS_SWITCHER
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.shared.model.DreamAppModel
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.utils.FakeUserScopedService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DreamRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.dreamManager by
        Kosmos.Fixture {
            mock<DreamManager> {
                on { dreamPlaylist } doReturn DreamPlaylist.EMPTY
                on { setActiveDreamComponent(any()) } doReturn true
            }
        }

    private val Kosmos.packageManager by
        Kosmos.Fixture {
            mock<PackageManager> {
                on { getApplicationLabel(any()) } doReturn TEST_APP_NAME
                on { getLaunchIntentForPackage(any()) } doReturn TEST_LAUNCH_INTENT
                on { getApplicationInfo(any<String>(), any<Int>()) } doReturn ApplicationInfo()
            }
        }

    private val Kosmos.userScopedDreamManager by
        Kosmos.Fixture { FakeUserScopedService(defaultImplementation = dreamManager) }

    private val Kosmos.userContextProvider by
        Kosmos.Fixture {
            val pm = packageManager
            val mockContext = mock<Context> { on { packageManager } doReturn pm }
            mock<UserContextProvider> { on { userContext } doReturn mockContext }
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            DreamRepositoryImpl(
                bgDispatcher = testDispatcher,
                bgScope = applicationCoroutineScope,
                tableLogBuffer = logcatTableLogBuffer(this, "DreamRepositoryLog"),
                userScopedDreamManager = userScopedDreamManager,
                userContextProvider = userContextProvider,
                userRepository = userRepository,
                context = mContext,
            )
        }

    @Test
    fun dreamState_initialValue_fromManager() =
        kosmos.runTest {
            val playlist = createDreamPlaylist("Dream1")
            whenever(dreamManager.dreamPlaylist).thenReturn(playlist)

            val dreamState by collectLastValue(underTest.dreamState)
            assertThat(dreamState).isEqualTo(createExpectedPlaylistModel(playlist))
        }

    @Test
    fun dreamState_updatesOnPlaylistChanged() =
        kosmos.runTest {
            val dreamState by collectLastValue(underTest.dreamState)

            val listenerCaptor = argumentCaptor<DreamManager.DreamListener>()
            verify(dreamManager).registerListener(any(), listenerCaptor.capture())

            val playlist = createDreamPlaylist("Dream1", "Dream2")
            listenerCaptor.lastValue.onPlaylistChanged(playlist)

            assertThat(dreamState).isEqualTo(createExpectedPlaylistModel(playlist))
        }

    @Test
    fun dreamState_updatesOnUserChange() =
        kosmos.runTest {
            val dreamState by collectLastValue(underTest.dreamState)

            // Verify initial registration
            verify(dreamManager).registerListener(any(), any())
            clearInvocations(dreamManager)

            // Create a new secondary user
            val newUser = UserInfo(10, "Secondary", UserInfo.FLAG_FULL)

            // Create a new mock for the new user, to ensure we use the right DreamManager for the
            // correct user.
            val newDreamManager =
                mock<DreamManager> { on { dreamPlaylist } doReturn DreamPlaylist.EMPTY }
            userScopedDreamManager.addImplementation(newUser.userHandle, newDreamManager)

            // Swap to the new user
            fakeUserRepository.setUserInfos(listOf(newUser))
            fakeUserRepository.setSelectedUserInfo(newUser)

            // Verify re-registration on the new manager and unregistration on the old one
            verify(newDreamManager).registerListener(any(), any())
            verify(dreamManager).unregisterListener(any())
            verify(dreamManager, never()).registerListener(any(), any())

            // Ensure flow is still active
            assertThat(dreamState).isNotNull()
        }

    @Test
    fun setActiveDream_returnsTrue() =
        kosmos.runTest {
            val componentName = ComponentName("com.android.systemui", "Dream1")
            val user = UserHandle.of(0)

            assertThat(underTest.setActiveDream(componentName, user)).isTrue()

            verify(dreamManager).setActiveDreamComponent(componentName)
        }

    @Test
    fun setActiveDream_returnsFalse() =
        kosmos.runTest {
            whenever(dreamManager.setActiveDreamComponent(any())).thenReturn(false)
            val componentName = ComponentName("com.android.systemui", "Dream1")
            val user = UserHandle.of(0)

            assertThat(underTest.setActiveDream(componentName, user)).isFalse()

            verify(dreamManager).setActiveDreamComponent(componentName)
        }

    @Test
    fun setActiveDream_returnsFalseOnRemoteException() =
        kosmos.runTest {
            whenever(dreamManager.setActiveDreamComponent(any()))
                .thenThrow(RuntimeException(RemoteException("Fail")))

            val componentName = ComponentName("com.android.systemui", "Dream1")
            val user = UserHandle.of(0)

            assertThat(underTest.setActiveDream(componentName, user)).isFalse()
        }

    @Test
    fun dreamState_registerListener_remoteException_doesNotCrash() =
        kosmos.runTest {
            whenever(dreamManager.registerListener(any(), any()))
                .thenThrow(RuntimeException(RemoteException("Fail")))

            val dreamState by collectLastValue(underTest.dreamState)

            assertThat(dreamState).isEqualTo(DreamPlaylistModel.EMPTY)
        }

    @Test
    fun dreamState_fetchInitialPlaylist_remoteException_doesNotCrash() =
        kosmos.runTest {
            whenever(dreamManager.dreamPlaylist)
                .thenThrow(RuntimeException(RemoteException("Fail")))

            val dreamState by collectLastValue(underTest.dreamState)

            assertThat(dreamState).isEqualTo(DreamPlaylistModel.EMPTY)
        }

    @Test
    fun dreamState_unregisterListener_remoteException_doesNotCrash() =
        kosmos.runTest {
            whenever(dreamManager.unregisterListener(any()))
                .thenThrow(RuntimeException(RemoteException("Fail")))

            val job = underTest.dreamState.launchIn(testScope)
            verify(dreamManager).registerListener(any(), any())
            job.cancel()
        }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    fun isDreamSwitcherEnabled_returnsTrue_whenConfigAndFlagAreEnabled() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamSwitcherEnabled,
            true,
        )
        assertThat(kosmos.underTest.isDreamSwitcherEnabled).isTrue()
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    fun isDreamSwitcherEnabled_returnsFalse_whenConfigIsDisabled() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamSwitcherEnabled,
            false,
        )
        assertThat(kosmos.underTest.isDreamSwitcherEnabled).isFalse()
    }

    @Test
    @DisableFlags(FLAG_DREAMS_SWITCHER)
    fun isDreamSwitcherEnabled_returnsFalse_whenFlagIsDisabled() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamSwitcherEnabled,
            true,
        )
        assertThat(kosmos.underTest.isDreamSwitcherEnabled).isFalse()
    }

    @Test
    fun dreamSwitcherDialogShowing_defaultsToFalse() =
        kosmos.runTest {
            val showing by collectLastValue(underTest.dreamSwitcherDialogShowing)
            assertThat(showing).isFalse()
        }

    @Test
    fun setSwitcherDialogShowing_updatesDreamSwitcherDialogShowing() =
        kosmos.runTest {
            val showing by collectLastValue(underTest.dreamSwitcherDialogShowing)
            underTest.setSwitcherDialogShowing(true)
            assertThat(showing).isTrue()
            underTest.setSwitcherDialogShowing(false)
            assertThat(showing).isFalse()
        }

    private fun createDreamPlaylist(vararg names: String, activeIndex: Int = 0): DreamPlaylist {
        return DreamPlaylist(
            names.map { DreamItem.Builder(ComponentName("com.android.systemui", it)).build() },
            activeIndex,
        )
    }

    private fun createExpectedPlaylistModel(playlist: DreamPlaylist): DreamPlaylistModel {
        val appInfo = DreamAppModel(TEST_APP_NAME, TEST_LAUNCH_INTENT)
        return DreamPlaylistModel(
            playlist.dreams.map {
                DreamItemModel(
                    componentName = it.componentName,
                    title = it.title,
                    description = it.description,
                    icon = it.icon,
                    previewImage = it.previewImage,
                    settingsActivity = it.settingsActivity,
                    appInfo = appInfo,
                )
            },
            playlist.activeIndex,
        )
    }

    private companion object {
        const val TEST_APP_NAME = "Test App"
        val TEST_LAUNCH_INTENT =
            Intent().setComponent(ComponentName("com.android.systemui", "TestActivity"))
    }
}
