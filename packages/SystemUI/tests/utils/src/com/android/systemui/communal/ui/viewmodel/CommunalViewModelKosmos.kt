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

package com.android.systemui.communal.ui.viewmodel

import com.android.systemui.classifier.domain.interactor.falsingInteractor
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.communalTutorialInteractor
import com.android.systemui.communal.shared.log.communalSceneLogger
import com.android.systemui.communal.smartspace.SmartspaceInteractionHandler
import com.android.systemui.communal.ui.compose.CommunalContent
import com.android.systemui.communal.ui.compose.section.AmbientStatusBarSection
import com.android.systemui.communal.ui.compose.section.CommunalPopupSection
import com.android.systemui.communal.ui.compose.section.HubOnboardingSection
import com.android.systemui.communal.ui.view.layout.sections.CommunalAppWidgetSection
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.ui.composable.elements.IndicationAreaElementProvider
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockIconElementProvider
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.mediaCarouselController
import com.android.systemui.media.controls.ui.view.qsMediaHost
import com.android.systemui.media.remedia.ui.viewmodel.factory.mediaViewModelFactory
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import org.mockito.kotlin.mock

val Kosmos.communalViewModel by
    Kosmos.Fixture {
        CommunalViewModel(
            mainDispatcher = testDispatcher,
            scope = applicationCoroutineScope,
            bgScope = backgroundScope,
            keyguardTransitionInteractor = keyguardTransitionInteractor,
            keyguardInteractor = keyguardInteractor,
            keyguardIndicationController = keyguardIndicationController,
            communalSceneInteractor = communalSceneInteractor,
            communalInteractor = communalInteractor,
            communalSettingsInteractor = communalSettingsInteractor,
            tutorialInteractor = communalTutorialInteractor,
            shadeInteractor = shadeInteractor,
            mediaHost = qsMediaHost,
            logBuffer = logcatLogBuffer(),
            metricsLogger = mock(),
            mediaCarouselController = mediaCarouselController,
            blurConfig = blurConfig,
            swipeToHub = true,
            communalSceneLogger = communalSceneLogger,
            falsingInteractor = falsingInteractor,
            mediaViewModelFactory = mediaViewModelFactory,
            mediaCarouselInteractorLazy = { mediaCarouselInteractor },
        )
    }
val Kosmos.keyguardIndicationController by Kosmos.Fixture { mock<KeyguardIndicationController>() }
val Kosmos.ambientStatusBarSection by Kosmos.Fixture { mock<AmbientStatusBarSection>() }
val Kosmos.communalContent by
    Kosmos.Fixture {
        CommunalContent(
            viewModel = communalViewModel,
            interactionHandler = smartSpaceInteractionHandler,
            communalSettingsInteractor = communalSettingsInteractor,
            dialogFactory = systemUIDialogFactory,
            lockElement = lockIconElementProvider,
            indicationAreaElement = indicationAreaElementProvider,
            communalPopupSection = communalPopupSection,
            widgetSection = communalAppWidgetSection,
            hubOnboardingSection = hubOnboardingSection,
            lockscreenElements = lockscreenElements,
        )
    }
val Kosmos.hubOnboardingSection by Kosmos.Fixture { mock<HubOnboardingSection>() }
val Kosmos.lockscreenElements by Kosmos.Fixture { mock<LockscreenElements>() }
val Kosmos.communalAppWidgetSection by Kosmos.Fixture { mock<CommunalAppWidgetSection>() }
val Kosmos.communalPopupSection by Kosmos.Fixture { mock<CommunalPopupSection>() }
val Kosmos.smartSpaceInteractionHandler by Kosmos.Fixture { mock<SmartspaceInteractionHandler>() }

val Kosmos.indicationAreaElementProvider by Kosmos.Fixture { mock<IndicationAreaElementProvider>() }
