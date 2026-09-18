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

package com.android.systemui.statusbar.quickactions.av.domain.interactor

import android.content.pm.UserInfo
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.fakeCaptioningRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_BLUR_LEVEL_KEY
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_CAMERA_FRAMING_KEY
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_FACE_RETOUCH_KEY
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor.Companion.DESKTOP_EFFECTS_STUDIO_MIC_KEY
import com.android.systemui.statusbar.quickactions.av.shared.model.BlurLevel
import com.android.systemui.statusbar.quickactions.av.shared.model.DesktopEffectModel
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@ExperimentalCoroutinesApi
class DesktopEffectInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { desktopEffectInteractor }
    private val latest by kosmos.collectLastValue(kosmos.underTest.model)

    private val FIRST_USER = UserInfo(100, "first user", 0)
    private val SECOND_USER = UserInfo(200, "second user", 0)

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(listOf(FIRST_USER, SECOND_USER))
        kosmos.testScope.launch { kosmos.fakeUserRepository.setSelectedUserInfo(FIRST_USER) }
    }

    @Test
    fun initialValue() =
        kosmos.runTest {
            assertThat(latest).isEqualTo(DesktopEffectModel())
            assertThat(latest)
                .isEqualTo(
                    DesktopEffectModel(
                        portraitRelight = false,
                        faceRetouch = false,
                        studioMic = false,
                        blurLevel = BlurLevel.OFF,
                    )
                )
        }

    @Test
    fun receivePortraitRelight() =
        kosmos.runTest {
            assertThat(latest!!.portraitRelight).isEqualTo(false)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY,
                value = true,
            )

            assertThat(latest!!.portraitRelight).isEqualTo(true)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY,
                value = false,
            )

            assertThat(latest!!.portraitRelight).isEqualTo(false)
        }

    @Test
    fun receiveFaceRetouch() =
        kosmos.runTest {
            assertThat(latest!!.faceRetouch).isEqualTo(false)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_FACE_RETOUCH_KEY,
                value = true,
            )

            assertThat(latest!!.faceRetouch).isEqualTo(true)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_FACE_RETOUCH_KEY,
                value = false,
            )

            assertThat(latest!!.faceRetouch).isEqualTo(false)
        }

    @Test
    fun receiveStudioMic() =
        kosmos.runTest {
            assertThat(latest!!.studioMic).isEqualTo(false)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_STUDIO_MIC_KEY,
                value = true,
            )

            assertThat(latest!!.studioMic).isEqualTo(true)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_STUDIO_MIC_KEY,
                value = false,
            )

            assertThat(latest!!.studioMic)
        }

    @Test
    fun receiveBlurLevel() =
        kosmos.runTest {
            assertThat(latest!!.blurLevel).isEqualTo(BlurLevel.OFF)

            fakeSettings.putIntForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_BLUR_LEVEL_KEY,
                value = BlurLevel.LIGHT.code,
            )

            assertThat(latest!!.blurLevel).isEqualTo(BlurLevel.LIGHT)

            fakeSettings.putIntForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_BLUR_LEVEL_KEY,
                value = BlurLevel.FULL.code,
            )

            assertThat(latest!!.blurLevel).isEqualTo(BlurLevel.FULL)

            fakeSettings.putIntForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_BLUR_LEVEL_KEY,
                value = BlurLevel.OFF.code,
            )

            assertThat(latest!!.blurLevel).isEqualTo(BlurLevel.OFF)
        }

    @Test
    fun receiveCameraFraming() =
        kosmos.runTest {
            assertThat(latest!!.cameraFraming).isEqualTo(false)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_CAMERA_FRAMING_KEY,
                value = true,
            )

            assertThat(latest!!.cameraFraming).isEqualTo(true)

            fakeSettings.putBoolForUser(
                userHandle = FIRST_USER.id,
                name = DESKTOP_EFFECTS_CAMERA_FRAMING_KEY,
                value = false,
            )

            assertThat(latest!!.cameraFraming).isEqualTo(false)
        }

    @Test
    fun receiveLiveCaptions() =
        kosmos.runTest {
            assertThat(latest!!.liveCaptions).isEqualTo(false)

            fakeCaptioningRepository.setIsSystemAudioCaptioningEnabled(true)

            assertThat(latest!!.liveCaptions).isEqualTo(true)

            fakeCaptioningRepository.setIsSystemAudioCaptioningEnabled(false)

            assertThat(latest!!.liveCaptions)
        }

    @Test
    fun setPortraitRelight_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setPortraitRelight(true)

            assertThat(latest!!.portraitRelight).isEqualTo(true)
            assertThat(
                    fakeSettings.getBoolForUser(
                        DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY,
                        false,
                        FIRST_USER.id,
                    )
                )
                .isEqualTo(true)
        }

    @Test
    fun setFaceRetouch_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setFaceRetouch(true)

            assertThat(latest!!.faceRetouch).isEqualTo(true)
            assertThat(
                    fakeSettings.getBoolForUser(
                        DESKTOP_EFFECTS_FACE_RETOUCH_KEY,
                        false,
                        FIRST_USER.id,
                    )
                )
                .isEqualTo(true)
        }

    @Test
    fun setStudioMic_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setStudioMic(true)

            assertThat(latest!!.studioMic).isEqualTo(true)
            assertThat(
                    fakeSettings.getBoolForUser(
                        DESKTOP_EFFECTS_STUDIO_MIC_KEY,
                        false,
                        FIRST_USER.id,
                    )
                )
                .isEqualTo(true)
        }

    @Test
    fun setLiveCaptions_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setLiveCaptions(true)

            assertThat(latest!!.liveCaptions).isEqualTo(true)
        }

    @Test
    fun setBlurLevel_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setBlurLevel(BlurLevel.LIGHT)

            assertThat(latest!!.blurLevel).isEqualTo(BlurLevel.LIGHT)
            assertThat(
                    fakeSettings.getIntForUser(
                        DESKTOP_EFFECTS_BLUR_LEVEL_KEY,
                        BlurLevel.OFF.code,
                        FIRST_USER.id,
                    )
                )
                .isEqualTo(BlurLevel.LIGHT.code)
        }

    @Test
    fun setCameraFraming_propagatesUpAndBack() =
        kosmos.runTest {
            underTest.setCameraFraming(true)

            assertThat(latest!!.cameraFraming).isEqualTo(true)
            assertThat(
                    fakeSettings.getBoolForUser(
                        DESKTOP_EFFECTS_CAMERA_FRAMING_KEY,
                        false,
                        FIRST_USER.id,
                    )
                )
                .isEqualTo(true)
        }

    @Test
    fun switchUser_resetsToInitialValue() =
        kosmos.runTest {
            assertThat(collectLastValue(underTest.model).invoke()).isEqualTo(DesktopEffectModel())
            val newModel =
                DesktopEffectModel(
                    portraitRelight = true,
                    faceRetouch = true,
                    studioMic = true,
                    blurLevel = BlurLevel.FULL,
                )

            underTest.setPortraitRelight(newModel.portraitRelight)
            underTest.setFaceRetouch(newModel.faceRetouch)
            underTest.setBlurLevel(newModel.blurLevel)
            underTest.setStudioMic(newModel.studioMic)

            assertThat(latest).isEqualTo(newModel)

            fakeUserRepository.setSelectedUserInfo(SECOND_USER)

            assertThat(latest).isEqualTo(DesktopEffectModel())
        }
}
