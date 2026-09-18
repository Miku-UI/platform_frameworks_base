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

package com.android.systemui.qs.composefragment.viewmodel

import android.app.StatusBarManager
import android.content.testableContext
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.media.controls.domain.pipeline.legacyMediaDataManagerImpl
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.controller.mediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaHostState
import com.android.systemui.media.controls.ui.view.qqsMediaHost
import com.android.systemui.media.controls.ui.view.qsMediaHost
import com.android.systemui.media.remedia.data.repository.setHasMedia
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import com.android.systemui.qs.composefragment.viewmodel.MediaState.ACTIVE_MEDIA
import com.android.systemui.qs.composefragment.viewmodel.MediaState.ANY_MEDIA
import com.android.systemui.qs.composefragment.viewmodel.MediaState.NO_MEDIA
import com.android.systemui.qs.fgsManagerController
import com.android.systemui.qs.panels.domain.interactor.tileSquishinessInteractor
import com.android.systemui.qs.panels.ui.viewmodel.setConfigurationForMediaInRow
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.largeScreenHeaderHelper
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.sysuiStatusBarStateController
import com.android.systemui.util.animation.DisappearParameters
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class QSFragmentComposeViewModelTest : AbstractQSFragmentComposeViewModelTest() {

    @Test
    fun qsExpansionValueChanges_correctExpansionState() = runTest {
        underTest.setQsExpansionValue(0f)
        assertThat(underTest.expansionState.progress).isEqualTo(0f)

        underTest.setQsExpansionValue(0.3f)
        assertThat(underTest.expansionState.progress).isEqualTo(0.3f)

        underTest.setQsExpansionValue(1f)
        assertThat(underTest.expansionState.progress).isEqualTo(1f)
    }

    @Test
    fun qsExpansionValueChanges_whenOverScrolling_zeroExpansionState() = runTest {
        underTest.isStackScrollerOverscrolling = true
        underTest.setQsExpansionValue(0f)
        assertThat(underTest.expansionState.progress).isEqualTo(0f)

        underTest.setQsExpansionValue(0.3f)
        assertThat(underTest.expansionState.progress).isEqualTo(0f)

        underTest.setQsExpansionValue(1f)
        assertThat(underTest.expansionState.progress).isEqualTo(0f)
    }

    @Test
    fun qsExpansionValueChanges_clamped() = runTest {
        underTest.setQsExpansionValue(-1f)
        assertThat(underTest.expansionState.progress).isEqualTo(0f)

        underTest.setQsExpansionValue(2f)
        assertThat(underTest.expansionState.progress).isEqualTo(1f)
    }

    @Test
    fun qqsHeaderHeight_largeScreenHeader_0() = runTest {
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_use_large_screen_shade_header,
            true,
        )
        fakeConfigurationRepository.onConfigurationChange()

        assertThat(underTest.qqsHeaderHeight).isEqualTo(0)
    }

    @Test
    fun qqsHeaderHeight_noLargeScreenHeader_providedByHelper() = runTest {
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_use_large_screen_shade_header,
            false,
        )
        fakeConfigurationRepository.onConfigurationChange()

        assertThat(underTest.qqsHeaderHeight)
            .isEqualTo(largeScreenHeaderHelper.getLargeScreenHeaderHeight())
    }

    @Test
    fun footerActionsControllerInit() = runTest {
        underTest
        runCurrent()
        assertThat(fgsManagerController.initialized).isTrue()
    }

    @Test
    fun statusBarState_followsController() = runTest {
        sysuiStatusBarStateController.setState(StatusBarState.SHADE)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.SHADE)

        sysuiStatusBarStateController.setState(StatusBarState.KEYGUARD)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.KEYGUARD)

        sysuiStatusBarStateController.setState(StatusBarState.SHADE_LOCKED)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)
    }

    @Test
    fun statusBarState_changesEarlyIfUpcomingStateIsKeyguard() = runTest {
        sysuiStatusBarStateController.setState(StatusBarState.SHADE)
        sysuiStatusBarStateController.setUpcomingState(StatusBarState.SHADE_LOCKED)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.SHADE)

        sysuiStatusBarStateController.setUpcomingState(StatusBarState.KEYGUARD)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.KEYGUARD)

        sysuiStatusBarStateController.setUpcomingState(StatusBarState.SHADE)
        runCurrent()
        assertThat(underTest.statusBarState).isEqualTo(StatusBarState.KEYGUARD)
    }

    @Test
    fun qsEnabled_followsRepository() = runTest {
        fakeDisableFlagsRepository.disableFlags.value =
            DisableFlagsModel(disable2 = QS_DISABLE_FLAG)
        runCurrent()

        assertThat(underTest.isQsEnabled).isFalse()

        fakeDisableFlagsRepository.disableFlags.value = DisableFlagsModel()
        runCurrent()

        assertThat(underTest.isQsEnabled).isTrue()
    }

    @Test
    fun squishinessInExpansion_setInInteractor() = runTest {
        val squishiness by collectLastValue(tileSquishinessInteractor.squishiness)

        underTest.squishinessFraction = 0.3f
        assertThat(squishiness).isWithin(epsilon).of(0.3f.constrainSquishiness())

        underTest.squishinessFraction = 0f
        assertThat(squishiness).isWithin(epsilon).of(0f.constrainSquishiness())

        underTest.squishinessFraction = 1f
        assertThat(squishiness).isWithin(epsilon).of(1f.constrainSquishiness())

        underTest.squishinessFraction = Float.NaN
        assertThat(squishiness).isWithin(epsilon).of(1f.constrainSquishiness())
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun qqsMediaHost_initializedCorrectly() = runTest {
        assertThat(underTest.qqsMediaHost.location).isEqualTo(MediaHierarchyManager.LOCATION_QQS)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)
        assertThat(underTest.qqsMediaHost.showsOnlyActiveMedia).isTrue()
        assertThat(underTest.qqsMediaHost.hostView).isNotNull()
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun qsMediaHost_initializedCorrectly() = runTest {
        assertThat(underTest.qsMediaHost.location).isEqualTo(MediaHierarchyManager.LOCATION_QS)
        assertThat(underTest.qsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)
        assertThat(underTest.qsMediaHost.showsOnlyActiveMedia).isFalse()
        assertThat(underTest.qsMediaHost.hostView).isNotNull()
    }

    @Test
    fun qqsMediaVisible_onlyWhenActiveMedia() = runTest {
        if (!MediaControlsInComposeFlag.isEnabled) {
            whenever(mediaCarouselController.isLockedAndHidden()).thenReturn(false)
            assertThat(underTest.qqsMediaVisible).isEqualTo(underTest.qqsMediaHost.visible)
        }

        setMediaState(NO_MEDIA)
        assertThat(underTest.qqsMediaVisible).isFalse()

        setMediaState(ANY_MEDIA)
        assertThat(underTest.qqsMediaVisible).isFalse()

        setMediaState(ACTIVE_MEDIA)
        assertThat(underTest.qqsMediaVisible).isTrue()
    }

    @Test
    fun qsMediaVisible_onAnyMedia() = runTest {
        if (!MediaControlsInComposeFlag.isEnabled) {
            whenever(mediaCarouselController.isLockedAndHidden()).thenReturn(false)
            assertThat(underTest.qsMediaVisible).isEqualTo(underTest.qsMediaHost.visible)
        }

        setMediaState(NO_MEDIA)
        assertThat(underTest.qsMediaVisible).isFalse()

        setMediaState(ANY_MEDIA)
        assertThat(underTest.qsMediaVisible).isTrue()

        setMediaState(ACTIVE_MEDIA)
        assertThat(underTest.qsMediaVisible).isTrue()
    }

    @Test
    fun notUsingMedia_mediaNotVisible() =
        runTest(usingMedia = false) {
            setMediaState(ACTIVE_MEDIA)

            assertThat(underTest.qqsMediaVisible).isFalse()
            assertThat(underTest.qsMediaVisible).isFalse()
        }

    @Test
    fun mediaNotInRow() = runTest {
        setConfigurationForMediaInRow(mediaInRow = false)
        setMediaState(ACTIVE_MEDIA)

        assertThat(underTest.qqsMediaInRow).isFalse()
        assertThat(underTest.qsMediaInRow).isFalse()
    }

    @Test
    fun mediaInRow_mediaActive_bothInRow() = runTest {
        skipInSplitShade() // mediaInRow is always false on Split shade.

        setConfigurationForMediaInRow(mediaInRow = true)
        setMediaState(ACTIVE_MEDIA)

        assertThat(underTest.qqsMediaInRow).isTrue()
        assertThat(underTest.qsMediaInRow).isTrue()
    }

    @Test
    fun mediaInRow_mediaNotActive_onlyQSInRow() = runTest {
        skipInSplitShade() // mediaInRow is always false on Split shade.

        setConfigurationForMediaInRow(mediaInRow = true)
        setMediaState(ANY_MEDIA)

        assertThat(underTest.qqsMediaInRow).isFalse()
        assertThat(underTest.qsMediaInRow).isTrue()
    }

    @Test
    fun mediaInRow_correctConfig_noMediaVisible_noMediaInRow() = runTest {
        setConfigurationForMediaInRow(mediaInRow = true)
        setMediaState(NO_MEDIA)

        assertThat(underTest.qqsMediaInRow).isFalse()
        assertThat(underTest.qsMediaInRow).isFalse()
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun qqsMediaExpansion_collapsedMediaInLandscape() = runTest {
        skipInSplitShade() // QQS is unavailable in Split shade mode.

        setCollapsedMediaInLandscape(true)
        setMediaState(ACTIVE_MEDIA)

        setConfigurationForMediaInRow(mediaInRow = false)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)

        setConfigurationForMediaInRow(mediaInRow = true)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.COLLAPSED)
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun qqsMediaExpansion_notCollapsedMediaInLandscape_alwaysExpanded() = runTest {
        skipInSplitShade() // QQS is unavailable in Split shade mode.

        setCollapsedMediaInLandscape(false)
        setMediaState(ACTIVE_MEDIA)

        setConfigurationForMediaInRow(mediaInRow = false)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)

        setConfigurationForMediaInRow(mediaInRow = true)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun qqsMediaExpansion_reactsToChangesInCollapsedMediaInLandscape() = runTest {
        skipInSplitShade() // QQS is unavailable in Split shade mode.

        setConfigurationForMediaInRow(mediaInRow = true)
        setMediaState(ACTIVE_MEDIA)

        setCollapsedMediaInLandscape(false)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.EXPANDED)

        setCollapsedMediaInLandscape(true)
        assertThat(underTest.qqsMediaHost.expansion).isEqualTo(MediaHostState.COLLAPSED)
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun applyQsScrollPositionForClipping() = runTest {
        val left = 1f
        val top = 3f
        val right = 5f
        val bottom = 7f

        underTest.applyNewQsScrollerBounds(left, top, right, bottom)

        assertThat(qsMediaHost.currentClipping)
            .isEqualTo(Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun shouldUpdateMediaSquishiness_inSplitShadeFalse_mediaSquishinessSet() = runTest {
        skipInSplitShade()

        underTest.isInSplitShade = false
        underTest.squishinessFraction = 0.3f

        underTest.shouldUpdateSquishinessOnMedia = true
        runCurrent()

        assertThat(underTest.qsMediaHost.squishFraction).isWithin(0.01f).of(0.3f)

        underTest.shouldUpdateSquishinessOnMedia = false
        runCurrent()
        assertThat(underTest.qsMediaHost.squishFraction).isWithin(0.01f).of(1f)
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun inSplitShade_differentStatusBarState_mediaSquishinessSet() = runTest {
        val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
        assumeTrue(shadeMode is ShadeMode.Split)

        underTest.isInSplitShade = true
        underTest.squishinessFraction = 0.3f

        sysuiStatusBarStateController.setState(StatusBarState.SHADE)
        runCurrent()
        assertThat(underTest.qsMediaHost.squishFraction).isWithin(epsilon).of(0.3f)

        sysuiStatusBarStateController.setState(StatusBarState.KEYGUARD)
        runCurrent()
        assertThat(underTest.qsMediaHost.squishFraction).isWithin(epsilon).of(1f)

        sysuiStatusBarStateController.setState(StatusBarState.SHADE_LOCKED)
        runCurrent()
        assertThat(underTest.qsMediaHost.squishFraction).isWithin(epsilon).of(1f)
    }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun disappearParams() = runTest {
        val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
        setMediaState(ACTIVE_MEDIA)

        setConfigurationForMediaInRow(false)

        if (shadeMode is ShadeMode.Split) {
            // mediaInRow is always false on Split shade, and QQS is never shown.
            assertThat(underTest.qsMediaHost.disappearParameters).isEqualTo(disappearParamsColumn)
        } else {
            assertThat(underTest.qqsMediaHost.disappearParameters).isEqualTo(disappearParamsColumn)
            assertThat(underTest.qsMediaHost.disappearParameters).isEqualTo(disappearParamsColumn)

            setConfigurationForMediaInRow(true)

            assertThat(underTest.qqsMediaHost.disappearParameters).isEqualTo(disappearParamsRow)
            assertThat(underTest.qsMediaHost.disappearParameters).isEqualTo(disappearParamsRow)
        }
    }

    @Test
    fun qsVisibleAndAnyShadeExpanded() = runTest {
        underTest.isPanelExpanded = false
        underTest.isQsVisible = false
        assertThat(underTest.isQsVisibleAndAnyShadeExpanded).isFalse()

        underTest.isPanelExpanded = false
        underTest.isQsVisible = true
        assertThat(underTest.isQsVisibleAndAnyShadeExpanded).isFalse()

        underTest.isPanelExpanded = true
        underTest.isQsVisible = false
        assertThat(underTest.isQsVisibleAndAnyShadeExpanded).isFalse()

        underTest.isPanelExpanded = true
        underTest.isQsVisible = true
        assertThat(underTest.isQsVisibleAndAnyShadeExpanded).isTrue()
    }

    @Test
    fun isEditing() = runTest {
        underTest.containerViewModel.editModeViewModel.startEditing()
        runCurrent()
        assertThat(underTest.isEditing).isTrue()

        underTest.containerViewModel.editModeViewModel.stopEditing()
        runCurrent()
        assertThat(underTest.isEditing).isFalse()
    }

    @Test
    fun minExpansion_expanded_earlyExpansion() = runTest {
        underTest.isQsExpanded = true
        underTest.setQsExpansionValue(0f)
        // The shade is not being collapsed
        underTest.panelExpansionFraction = 1f
        underTest.squishinessFraction = 1f

        assertThat(underTest.expansionState.progress).isGreaterThan(0f)
    }

    @Test
    fun minExpansion_expanded_collapsingShade_panelExpansion_noEarlyExpansion() = runTest {
        underTest.isQsExpanded = true
        underTest.setQsExpansionValue(0f)
        underTest.panelExpansionFraction = 0.9f

        assertThat(underTest.expansionState.progress).isEqualTo(0f)
    }

    @Test
    fun minExpansion_expanded_collapsingShade_squishiness_noEarlyExpansion() = runTest {
        underTest.isQsExpanded = true
        underTest.setQsExpansionValue(0f)
        underTest.squishinessFraction = 0.9f

        assertThat(underTest.expansionState.progress).isEqualTo(0f)
    }

    private fun Kosmos.setMediaState(state: MediaState) {
        val activeMedia = state == ACTIVE_MEDIA
        val anyMedia = state != NO_MEDIA
        setHasMedia(visible = anyMedia, active = activeMedia)

        if (MediaControlsInComposeFlag.isEnabled) return

        whenever(legacyMediaDataManagerImpl.hasActiveMedia()).thenReturn(activeMedia)
        whenever(legacyMediaDataManagerImpl.hasAnyMedia()).thenReturn(anyMedia)
        qqsMediaHost.showsOnlyActiveMedia = true
        qqsMediaHost.updateViewVisibility()
        qsMediaHost.showsOnlyActiveMedia = false
        qsMediaHost.updateViewVisibility()
        runCurrent()
    }

    private fun Kosmos.setCollapsedMediaInLandscape(collapsed: Boolean) {
        overrideResource(R.bool.config_quickSettingsMediaLandscapeCollapsed, collapsed)
        fakeConfigurationRepository.onAnyConfigurationChange()
        runCurrent()
    }

    // TODO(b/478844187): This should be removed once the bug is fixed.
    private fun Kosmos.skipInSplitShade() {
        val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
        runCurrent()
        assumeFalse(shadeMode is ShadeMode.Split)
    }

    companion object {
        private const val QS_DISABLE_FLAG = StatusBarManager.DISABLE2_QUICK_SETTINGS

        private fun Float.constrainSquishiness(): Float {
            return (0.1f + this * 0.9f).coerceIn(0f, 1f)
        }

        private const val epsilon = 0.001f

        private val disappearParamsColumn =
            DisappearParameters().apply {
                fadeStartPosition = 0.95f
                disappearStart = 0f
                disappearEnd = 0.95f
                disappearSize.set(1f, 0f)
                gonePivot.set(0f, 0f)
                contentTranslationFraction.set(0f, 1f)
            }

        private val disappearParamsRow =
            DisappearParameters().apply {
                fadeStartPosition = 0.95f
                disappearStart = 0f
                disappearEnd = 0.6f
                disappearSize.set(0f, 0.4f)
                gonePivot.set(1f, 0f)
                contentTranslationFraction.set(0.25f, 1f)
            }
    }
}

private enum class MediaState {
    ACTIVE_MEDIA,
    ANY_MEDIA,
    NO_MEDIA,
}
