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

package com.android.systemui.statusbar.featurepods.av.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.statusbar.featurepods.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.av.shared.model.SensorActivityModel
import com.android.systemui.testKosmos
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
abstract class AvControlsChipInteractorTestBase() : SysuiTestCase() {
    protected val kosmos = testKosmos().useUnconfinedTestDispatcher()
    protected val Kosmos.underTest by Kosmos.Fixture { avControlsChipInteractor }

    protected val cameraItem =
        PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("fakepackage", 0))
    protected val microphoneItem =
        PrivacyItem(PrivacyType.TYPE_MICROPHONE, PrivacyApplication("fakepackage", 0))

    protected fun cameraModel() =
        AvControlsChipModel(SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA))

    protected fun microphoneModel() =
        AvControlsChipModel(
            SensorActivityModel.Active(SensorActivityModel.Active.Sensors.MICROPHONE)
        )

    protected fun cameraAndMicrophoneModel() =
        AvControlsChipModel(
            SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE)
        )

    protected fun inactiveModel() = AvControlsChipModel(SensorActivityModel.Inactive)

    protected fun Kosmos.lastModel(): AvControlsChipModel? =
        collectLastValue(underTest.model).invoke()
}
