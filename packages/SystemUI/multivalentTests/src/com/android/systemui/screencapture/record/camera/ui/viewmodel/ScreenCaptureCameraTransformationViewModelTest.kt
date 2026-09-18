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

package com.android.systemui.screencapture.record.camera.ui.viewmodel

import android.graphics.Region
import android.media.projection.StopReason
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.core.graphics.toRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.record.camera.data.repository.fakeScreenRecordCameraRepository
import com.android.systemui.screencapture.record.camera.domain.interactor.screenCaptureCameraTransformationInteractor
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParametersFactory.screenRecordingParameters
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureCameraTransformationViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val uiBounds = Rect(0f, 0f, 1080f, 1920f)
    private val safeGestureBounds = uiBounds.deflate(10f)
    private val surfaceScreenBounds = safeGestureBounds.deflate(10f)

    private val underTest by lazy {
        kosmos.screenCaptureCameraTransformationViewModel.apply { activateIn(kosmos.testScope) }
    }

    @Test
    fun recordingStarting_transformableByTouchAnywhere_isFalse() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecordingDelayed(
                screenRecordingParameters(),
                100.days,
            )

            assertThat(underTest.transformableByTouchAnywhere).isFalse()
        }

    @Test
    fun isTransforming_isPropagated() =
        kosmos.runTest {
            val transform = CompletableDeferred<Unit>()

            testScope.launch { underTest.transformableState.transform { transform.await() } }
            assertThat(screenCaptureCameraTransformationInteractor.isTransforming).isTrue()

            transform.complete(Unit)
            assertThat(screenCaptureCameraTransformationInteractor.isTransforming).isFalse()
            assertThat(uiEventLoggerFake.logs.map { it.eventId })
                .containsExactly(uiEventLoggerFake.eventId(0))
                .equals(ScreenRecordEvent.SCREEN_RECORD_SURFACE_ADJUSTED_PRE_RECORDING)
        }

    @Test
    fun recordingInProgress_useCameraBounds() =
        kosmos.runTest {
            underTest.onUiBoundsChanged(uiBounds = uiBounds, safeGestureBounds = safeGestureBounds)
            underTest.onSurfaceScreenBoundsUpdated(surfaceScreenBounds)
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())
            fakeScreenRecordCameraRepository.setCameraSubjectBounds(Region(20, 20, 100, 100))

            underTest.transform()

            assertThat(underTest.transformableByTouchAnywhere).isFalse()
            assertThat(underTest.touchableRegion.bounds)
                .isEqualTo(Region(434, 659, 461, 686).bounds)
        }

    @Test
    fun noRecordingInProgress_useGlobalBounds() =
        kosmos.runTest {
            underTest.onUiBoundsChanged(uiBounds = uiBounds, safeGestureBounds = safeGestureBounds)
            underTest.onSurfaceScreenBoundsUpdated(surfaceScreenBounds)

            screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
            underTest.transform()

            assertThat(underTest.transformableByTouchAnywhere).isTrue()
            assertThat(underTest.touchableRegion.bounds)
                .isEqualTo(uiBounds.toAndroidRectF().toRect())
        }

    @Test
    fun transformation_isCoercedInSafeGestureBounds() =
        kosmos.runTest {
            underTest.onUiBoundsChanged(uiBounds = uiBounds, safeGestureBounds = safeGestureBounds)
            underTest.onSurfaceScreenBoundsUpdated(surfaceScreenBounds)

            // Pan by a large amount to move it outside safeGestureBounds
            underTest.transformableState.transform { transformBy(panChange = Offset(1000f, 1000f)) }

            assertThat(underTest.offsetX).isEqualTo(530f)
            assertThat(underTest.offsetY).isEqualTo(950f)
        }

    @Test
    fun isTransforming_midRecording_uiEventTracked() =
        kosmos.runTest {
            screenRecordingServiceInteractor.startRecording(screenRecordingParameters())
            val transform = CompletableDeferred<Unit>()

            testScope.launch { underTest.transformableState.transform { transform.await() } }
            transform.complete(Unit)

            assertThat(uiEventLoggerFake.logs.map { it.eventId })
                .containsExactly(uiEventLoggerFake.eventId(0))
                .equals(ScreenRecordEvent.SCREEN_RECORD_SURFACE_ADJUSTED_MID_RECORDING)
        }

    @Test
    fun boundsUpdated_isCoercedInSafeGestureBounds() =
        kosmos.runTest {
            underTest.onUiBoundsChanged(uiBounds = uiBounds, safeGestureBounds = safeGestureBounds)
            underTest.onSurfaceScreenBoundsUpdated(surfaceScreenBounds)

            // Pan by a large amount to move it outside safeGestureBounds
            underTest.transformableState.transform { transformBy(panChange = Offset(1000f, 1000f)) }

            val initialOffsetX = underTest.offsetX
            val initialOffsetY = underTest.offsetY

            // Shrink safeGestureBounds to be smaller
            underTest.onUiBoundsChanged(
                uiBounds = uiBounds,
                safeGestureBounds = safeGestureBounds.deflate(100f),
            )

            // Verify offsets changed
            assertThat(underTest.offsetX).isNotEqualTo(initialOffsetX)
            assertThat(underTest.offsetY).isNotEqualTo(initialOffsetY)

            val newOffsetX = underTest.offsetX
            val newOffsetY = underTest.offsetY

            // Shrink surface screen bounds to be smaller and shift its center
            underTest.onSurfaceScreenBoundsUpdated(
                surfaceScreenBounds.deflate(100f).translate(100f, 100f)
            )

            // Verify offsets changed again due to center recalculation
            assertThat(underTest.offsetX).isNotEqualTo(newOffsetX)
            assertThat(underTest.offsetY).isNotEqualTo(newOffsetY)
        }

    private suspend fun ScreenCaptureCameraTransformationViewModel.transform() {
        transformableState.transform {
            transformByWithCentroid(
                centroid = uiBounds.center,
                zoomChange = 0.01f,
                panChange = Offset(10f, 10f),
                rotationChange = 10f,
            )
        }
    }
}
