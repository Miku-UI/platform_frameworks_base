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
package com.android.systemui.dreams

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.complication.Complication
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamOverlayStateControllerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    val complication: Complication = mock()

    val callback: DreamOverlayStateController.Callback = mock()

    val Kosmos.underTest by Kosmos.Fixture { dreamOverlayStateController }

    @Test
    fun testStateChange_overlayActive() =
        kosmos.runTest {
            underTest.addCallback(callback)
            underTest.isOverlayActive = true

            verify(callback).onStateChanged()
            Truth.assertThat(underTest.isOverlayActive).isTrue()

            clearInvocations(callback)
            underTest.isOverlayActive = true
            verify(callback, never()).onStateChanged()

            underTest.isOverlayActive = false
            verify(callback).onStateChanged()
            Truth.assertThat(underTest.isOverlayActive).isFalse()
        }

    @Test
    fun testCallback() =
        kosmos.runTest {
            underTest.addCallback(callback)

            // Add complication and verify callback is notified.
            underTest.addComplication(complication)

            verify(callback, times(1)).onComplicationsChanged()

            val complications: Collection<Complication?> = underTest.complications
            Assert.assertEquals(complications.size.toLong(), 1)
            Assert.assertTrue(complications.contains(complication))

            clearInvocations(callback)

            // Remove complication and verify callback is notified.
            underTest.removeComplication(complication)
            verify(callback, times(1)).onComplicationsChanged()
            Assert.assertTrue(underTest.complications.isEmpty())
        }

    @Test
    fun testNotifyOnCallbackAdd() =
        kosmos.runTest {
            underTest.addComplication(complication)

            // Verify callback occurs on add when an overlay is already present.
            underTest.addCallback(callback)
            verify(callback, times(1)).onComplicationsChanged()
        }

    private fun createComplicationMock(requiredTypeAvailability: Int): Complication {
        return mock { on { getRequiredTypeAvailability() } doReturn requiredTypeAvailability }
    }

    @Test
    fun testComplicationFilteringWhenShouldShowComplications() =
        kosmos.runTest {
            underTest.shouldShowComplications = true

            val alwaysAvailableComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_NONE)
            val weatherComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_WEATHER)

            underTest.addComplication(alwaysAvailableComplication)
            underTest.addComplication(weatherComplication)

            val callback: DreamOverlayStateController.Callback = mock()

            underTest.addCallback(callback)

            run {
                Truth.assertThat(underTest.complications.contains(alwaysAvailableComplication))
                    .isTrue()
                Truth.assertThat(underTest.complications.contains(weatherComplication)).isFalse()
            }

            underTest.availableComplicationTypes = Complication.COMPLICATION_TYPE_WEATHER
            verify(callback).onAvailableComplicationTypesChanged()

            run {
                Truth.assertThat(underTest.complications.contains(alwaysAvailableComplication))
                    .isTrue()
                Truth.assertThat(underTest.complications.contains(weatherComplication)).isTrue()
            }
        }

    @Test
    fun testComplicationFilteringWhenShouldHideComplications() =
        kosmos.runTest {
            underTest.shouldShowComplications = true

            val alwaysAvailableComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_NONE)
            val weatherComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_WEATHER)

            underTest.addComplication(alwaysAvailableComplication)
            underTest.addComplication(weatherComplication)

            val callback: DreamOverlayStateController.Callback = mock()

            underTest.availableComplicationTypes = Complication.COMPLICATION_TYPE_WEATHER
            underTest.addCallback(callback)

            run {
                clearInvocations(callback)
                underTest.shouldShowComplications = true

                verify(callback).onAvailableComplicationTypesChanged()
                Truth.assertThat(underTest.complications.contains(alwaysAvailableComplication))
                    .isTrue()
                Truth.assertThat(underTest.complications.contains(weatherComplication)).isTrue()
            }

            run {
                clearInvocations(callback)
                underTest.shouldShowComplications = false

                verify(callback).onAvailableComplicationTypesChanged()
                Truth.assertThat(underTest.complications.contains(alwaysAvailableComplication))
                    .isTrue()
                Truth.assertThat(underTest.complications.contains(weatherComplication)).isFalse()
            }
        }

    @Test
    fun testComplicationWithNoTypeNotFiltered() =
        kosmos.runTest {
            val complication: Complication = mock()
            underTest.addComplication(complication)
            Truth.assertThat(underTest.getComplications(true).contains(complication)).isTrue()
        }

    @Test
    fun testComplicationsNotShownForHomeControlPanelDream() =
        kosmos.runTest {
            val complication: Complication = mock()

            // Add a complication and verify it's returned in getComplications.
            underTest.addComplication(complication)
            Truth.assertThat(underTest.complications.contains(complication)).isTrue()

            underTest.setHomeControlPanelActive(true)

            Truth.assertThat(underTest.complications).isEmpty()
        }

    @Test
    fun testComplicationsNotShownForLowLight() =
        kosmos.runTest {
            val complication: Complication = mock()

            // Add a complication and verify it's returned in getComplications.
            underTest.addComplication(complication)
            Truth.assertThat(underTest.complications.contains(complication)).isTrue()

            underTest.isLowLightActive = true
            Truth.assertThat(underTest.complications).isEmpty()
        }

    @Test
    fun testNotifyLowLightChanged() =
        kosmos.runTest {
            underTest.addCallback(callback)
            Truth.assertThat(underTest.isLowLightActive).isFalse()

            underTest.isLowLightActive = true

            verify(callback, times(1)).onStateChanged()
            Truth.assertThat(underTest.isLowLightActive).isTrue()
        }

    @Test
    fun testNotifyLowLightExit() =
        kosmos.runTest {
            underTest.addCallback(callback)
            Truth.assertThat(underTest.isLowLightActive).isFalse()

            // Turn low light on then off to trigger the exiting callback.
            underTest.isLowLightActive = true
            underTest.isLowLightActive = false

            // Callback was only called once, when
            verify(callback, times(1)).onExitLowLight()
            Truth.assertThat(underTest.isLowLightActive).isFalse()

            // Set with false again, which should not cause the callback to trigger again.
            underTest.isLowLightActive = false
            verify(callback, times(1)).onExitLowLight()
        }

    @Test
    fun testNotifyEntryAnimationsFinishedChanged() =
        kosmos.runTest {
            underTest.addCallback(callback)
            Truth.assertThat(underTest.areEntryAnimationsFinished()).isFalse()

            underTest.setEntryAnimationsFinished(true)

            verify(callback, times(1)).onStateChanged()
            Truth.assertThat(underTest.areEntryAnimationsFinished()).isTrue()
        }

    @Test
    fun testNotifyDreamOverlayStatusBarVisibleChanged() =
        kosmos.runTest {
            underTest.addCallback(callback)
            Truth.assertThat(underTest.isDreamOverlayStatusBarVisible).isFalse()

            underTest.isDreamOverlayStatusBarVisible = true

            verify(callback, times(1)).onStateChanged()
            Truth.assertThat(underTest.isDreamOverlayStatusBarVisible).isTrue()
        }

    @Test
    fun testNotifyHasAssistantAttentionChanged() =
        kosmos.runTest {
            underTest.addCallback(callback)
            Truth.assertThat(underTest.hasAssistantAttention()).isFalse()

            underTest.setHasAssistantAttention(true)

            verify(callback, times(1)).onStateChanged()
            Truth.assertThat(underTest.hasAssistantAttention()).isTrue()
        }

    @Test
    fun testShouldShowComplicationsSetToFalse_stillShowsHomeControls_featureEnabled() =
        kosmos.runTest {
            val underTest = prepareDreamOverlayStateController(alwaysShowHomeControls = true)

            underTest.shouldShowComplications = true

            val homeControlsComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_HOME_CONTROLS)

            underTest.addComplication(homeControlsComplication)

            val callback: DreamOverlayStateController.Callback = mock()

            underTest.availableComplicationTypes = Complication.COMPLICATION_TYPE_HOME_CONTROLS
            underTest.addCallback(callback)

            run {
                clearInvocations(callback)
                underTest.shouldShowComplications = true

                verify(callback).onAvailableComplicationTypesChanged()
                Truth.assertThat(underTest.complications.contains(homeControlsComplication))
                    .isTrue()
            }

            run {
                clearInvocations(callback)
                underTest.shouldShowComplications = false

                verify(callback).onAvailableComplicationTypesChanged()
                Truth.assertThat(underTest.complications.contains(homeControlsComplication))
                    .isTrue()
            }
        }

    @Test
    fun testHomeControlsDoNotShowIfNotAvailable_featureEnabled() =
        kosmos.runTest {
            val underTest = prepareDreamOverlayStateController(alwaysShowHomeControls = true)
            underTest.shouldShowComplications = true

            val homeControlsComplication: Complication =
                createComplicationMock(Complication.COMPLICATION_TYPE_HOME_CONTROLS)

            underTest.addComplication(homeControlsComplication)

            val callback: DreamOverlayStateController.Callback = mock()

            underTest.addCallback(callback)

            // No home controls since it is not available.
            Truth.assertThat(underTest.complications).doesNotContain(homeControlsComplication)

            underTest.availableComplicationTypes =
                (Complication.COMPLICATION_TYPE_HOME_CONTROLS or
                    Complication.COMPLICATION_TYPE_WEATHER)
            Truth.assertThat(underTest.complications).contains(homeControlsComplication)
        }

    @Test
    fun testCallbacksIgnoredWhenWeakReferenceCleared() =
        kosmos.runTest {
            val callback1: DreamOverlayStateController.Callback = mock()
            val callback2: DreamOverlayStateController.Callback = mock()

            underTest.addCallback(callback1)
            underTest.addCallback(callback2)

            // Simulate callback1 getting GC'd by clearing the reference
            dreamOverlayStateControllerWeakReferenceFactory.clear(callback1)
            underTest.isOverlayActive = true

            // Callback2 should still be called, but never callback1
            verify(callback1, never()).onStateChanged()
            verify(callback2).onStateChanged()
            Truth.assertThat(underTest.isOverlayActive).isTrue()
        }
}
