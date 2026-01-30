/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.dialog.domain.interactor

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_QS_TILE_DETAILED_VIEW
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.accessibilityRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.fakeVolumeDialogController
import com.android.systemui.res.R
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmos
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.shared.model.VolumeDialogSafetyWarningModel
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

private val dialogTimeoutDuration = 3.seconds

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VolumeDialogVisibilityInteractorTest : SysuiTestCase() {

    private val kosmos: Kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            accessibilityRepository.setRecommendedTimeout(dialogTimeoutDuration)
            volumeDialogStateInteractor.setHovering(false)
            volumeDialogStateInteractor.setSafetyWarning(VolumeDialogSafetyWarningModel.Invisible)
        }

    private val underTest: VolumeDialogVisibilityInteractor by lazy {
        kosmos.volumeDialogVisibilityInteractor
    }

    @Test
    fun testShowRequest_visible() =
        kosmos.runTest {
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            assertThat(visibilityModel!!)
                .isEqualTo(
                    VolumeDialogVisibilityModel.Visible(
                        Events.SHOW_REASON_VOLUME_CHANGED,
                        false,
                        ActivityManager.LOCK_TASK_MODE_LOCKED,
                    )
                )
        }

    @Test
    fun testDismissRequest_dismissed() =
        kosmos.runTest {
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            fakeVolumeDialogController.onDismissRequested(Events.DISMISS_REASON_SCREEN_OFF)

            assertThat(visibilityModel!!)
                .isEqualTo(VolumeDialogVisibilityModel.Dismissed(Events.DISMISS_REASON_SCREEN_OFF))
        }

    @Test
    fun testTimeout_dismissed() =
        kosmos.runTest {
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            advanceTimeBy(1.days)

            assertThat(visibilityModel!!)
                .isEqualTo(VolumeDialogVisibilityModel.Dismissed(Events.DISMISS_REASON_TIMEOUT))
        }

    @Test
    fun testResetTimeoutInterruptsEvents() =
        kosmos.runTest {
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            advanceTimeBy(dialogTimeoutDuration / 2)
            underTest.resetDismissTimeout()
            advanceTimeBy(dialogTimeoutDuration / 2)
            underTest.resetDismissTimeout()
            advanceTimeBy(dialogTimeoutDuration / 2)

            assertThat(visibilityModel)
                .isInstanceOf(VolumeDialogVisibilityModel.Visible::class.java)
            assertThat(fakeVolumeDialogController.hasUserActivity).isTrue()
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testShowRequest_whenQsSliderFeatureEnabledAndQsNotExpanded_dialogVisible() {
        kosmos.runTest {
            overrideResource(R.bool.config_enableDesktopAudioTileDetailsView, true)
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            shadeTestUtil.setQsExpansion(0f)

            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            assertThat(visibilityModel!!)
                .isEqualTo(
                    VolumeDialogVisibilityModel.Visible(
                        Events.SHOW_REASON_VOLUME_CHANGED,
                        false,
                        ActivityManager.LOCK_TASK_MODE_LOCKED,
                    )
                )
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testShowRequest_whenQsSliderFeatureEnabledAndQsExpanded_dialogInvisible() {
        kosmos.runTest {
            overrideResource(R.bool.config_enableDesktopAudioTileDetailsView, true)
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            shadeTestUtil.setQsExpansion(1f)

            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            assertThat(visibilityModel!!).isEqualTo(VolumeDialogVisibilityModel.Invisible)
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testShowRequest_whenQsSliderFeatureEnabledAndQsBecomesExpanded_dialogDismissed() {
        kosmos.runTest {
            overrideResource(R.bool.config_enableDesktopAudioTileDetailsView, true)
            val visibilityModel by collectLastValue(underTest.dialogVisibility)
            shadeTestUtil.setQsExpansion(0f)

            fakeVolumeDialogController.onShowRequested(
                Events.SHOW_REASON_VOLUME_CHANGED,
                false,
                ActivityManager.LOCK_TASK_MODE_LOCKED,
            )

            shadeTestUtil.setQsExpansion(1f)

            assertThat(visibilityModel!!)
                .isEqualTo(
                    VolumeDialogVisibilityModel.Dismissed(
                        Events.DISMISS_REASON_QUICK_SETTINGS_EXPANDED
                    )
                )
        }
    }
}
