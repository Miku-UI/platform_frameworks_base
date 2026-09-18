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

package com.android.systemui.dreams.ui.viewmodel

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.data.repository.dreamRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.domain.interactor.dreamInteractor
import com.android.systemui.dreams.shared.model.DreamAppModel
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.dreams.ui.DreamSwitcherDialogDelegate
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamOverlayContainerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private lateinit var activatableJob: Job

    private val Kosmos.dialog: SystemUIDialog by
        Kosmos.Fixture {
            var dismissListener: DialogInterface.OnDismissListener? = null
            var showListener: DialogInterface.OnShowListener? = null

            mock {
                on { setOnDismissListener(any()) } doAnswer
                    { invocation ->
                        dismissListener = invocation.getArgument(0)
                    }

                on { dismiss() } doAnswer
                    { invocation ->
                        dismissListener?.onDismiss(invocation.mock as DialogInterface)
                        Unit
                    }

                on { setOnShowListener(any()) } doAnswer
                    { invocation ->
                        showListener = invocation.getArgument(0)
                    }

                on { show() } doAnswer
                    { invocation ->
                        showListener?.onShow(invocation.mock as DialogInterface)
                        Unit
                    }
            }
        }

    private val Kosmos.dialogDelegate: DreamSwitcherDialogDelegate by
        Kosmos.Fixture {
            val d = dialog
            mock { on { createDialog() } doReturn d }
        }

    private val Kosmos.dialogDelegateFactory: DreamSwitcherDialogDelegate.Factory by
        Kosmos.Fixture { DreamSwitcherDialogDelegate.Factory { dialogDelegate } }

    private val Kosmos.underTest: DreamOverlayContainerViewModel by
        Kosmos.Fixture {
            DreamOverlayContainerViewModel(
                dreamInteractor,
                dialogDelegateFactory,
                dreamEdgeSwipeViewModelFactory,
            )
        }

    @Before
    fun setUp() {
        activatableJob = with(kosmos) { underTest.activateIn(testScope) }
        onTeardown { activatableJob.cancel() }
    }

    @Test
    fun showDialog_succeedsIfSingleDream() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_2, activeIndex = 0)

            assertThat(underTest.showDialog()).isTrue()
            assertThat(underTest.dialogShowing).isTrue()
        }

    @Test
    fun showDialog_succeedsIfMultipleDreams() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, TEST_DREAM_2, activeIndex = 0)

            assertThat(underTest.showDialog()).isTrue()
            assertThat(underTest.dialogShowing).isTrue()
        }

    @Test
    fun dismissDialog_resetsState() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, TEST_DREAM_2, activeIndex = 0)

            assertThat(underTest.showDialog()).isTrue()
            assertThat(underTest.dialogShowing).isTrue()

            underTest.dismissDialog()

            assertThat(underTest.dialogShowing).isFalse()
        }

    @Test
    fun accessibilityAction_nonNullIfSingleDream() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_2, activeIndex = 0)

            assertThat(underTest.dreamSwitcherAction).isNotNull()
            assertThat(underTest.dialogShowing).isFalse()

            underTest.dreamSwitcherAction?.action?.invoke()
            assertThat(underTest.dialogShowing).isTrue()
        }

    @Test
    fun accessibilityAction_nonNullIfMultipleDreams() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, TEST_DREAM_2, activeIndex = 0)

            assertThat(underTest.dreamSwitcherAction).isNotNull()
            assertThat(underTest.dialogShowing).isFalse()

            underTest.dreamSwitcherAction?.action?.invoke()
            assertThat(underTest.dialogShowing).isTrue()
        }

    private fun Kosmos.setDreamPlaylist(
        vararg dreams: DreamItemModel,
        activeIndex: Int = 0,
    ): DreamPlaylistModel {
        val playlist = DreamPlaylistModel(dreams.toList(), activeIndex)
        dreamRepository.fake.setDreamState(userTracker.userHandle, playlist)
        return playlist
    }

    private companion object {
        val TEST_DREAM_1_SETTINGS_ACTIVITY = ComponentName("pkg", "settings")
        val TEST_DREAM_1 =
            DreamItemModel(
                componentName = ComponentName("pkg", "cls"),
                settingsActivity = TEST_DREAM_1_SETTINGS_ACTIVITY,
                title = "title1",
                description = "desc1",
            )

        val TEST_DREAM_2_LAUNCH_INTENT =
            Intent().apply { component = ComponentName("pkg", "dummyapp") }
        val TEST_DREAM_2 =
            DreamItemModel(
                componentName = ComponentName("pkg", "cls2"),
                appInfo = DreamAppModel("appName", TEST_DREAM_2_LAUNCH_INTENT),
                title = "title2",
            )
    }
}
