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

package com.android.systemui.communal

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class CommunalMediaStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val mediaHost = mock<MediaHost>()

    private val Kosmos.underTest by
        Kosmos.Fixture { CommunalMediaStartable(communalSettingsInteractor, mediaHost) }

    @DisableSceneContainer
    @DisableFlags(FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    @Test
    fun onStart_mediaControlsInComposeDisabled_initializeMediaHostForCommunal() =
        kosmos.runTest {
            underTest.start()
            verify(mediaHost).init(MediaHierarchyManager.LOCATION_COMMUNAL_HUB)
        }

    @EnableFlags(FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    @Test
    fun onStart_mediaControlsInComposeEnabled_doNotInitializeMediaHost() =
        kosmos.runTest {
            underTest.start()
            verify(mediaHost, never()).init(any())
        }
}
