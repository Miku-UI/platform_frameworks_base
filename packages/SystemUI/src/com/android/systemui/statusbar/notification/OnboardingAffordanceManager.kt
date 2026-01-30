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

package com.android.systemui.statusbar.notification

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.collection.provider.SectionHeaderVisibilityProvider
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.stack.OnboardingAffordanceView
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingAffordanceManager(
    private val label: String,
    private val sectionHeaderVisibilityProvider: SectionHeaderVisibilityProvider,
) {
    private val _view = MutableStateFlow<OnboardingAffordanceView?>(null)
    val view: StateFlow<OnboardingAffordanceView?> = _view.asStateFlow()

    private var _nodeController: NodeController? = null
    val nodeController: NodeController?
        get() = _nodeController?.takeIf { sectionHeaderVisibilityProvider.sectionHeadersVisible }

    fun setOnboardingAffordanceView(view: OnboardingAffordanceView?) {
        if (view != _view.value) {
            _view.value = view
            _nodeController = view?.let { AffordanceNodeController(label, view) }
        }
    }

    private class AffordanceNodeController(
        override val nodeLabel: String,
        override val view: OnboardingAffordanceView,
    ) : NodeController {
        override fun offerToKeepInParentForAnimation(): Boolean = false

        override fun removeFromParentIfKeptForAnimation(): Boolean = false

        override fun resetKeepInParentForAnimation() {}

        override fun onViewAdded() {
            view.setContentVisibleAnimated(true)
        }
    }
}

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Summarization

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Bundles

@Module
object NotificationOnboardingAffordanceManagerModule {
    @Provides
    @SysUISingleton
    @Summarization
    fun provideSummaryAffordanceManager(headerVisProvider: SectionHeaderVisibilityProvider) =
        OnboardingAffordanceManager(SUMMARY_ONBOARDING_LABEL, headerVisProvider)

    @Provides
    @SysUISingleton
    @Bundles
    fun provideBundleAffordanceManager(headerVisProvider: SectionHeaderVisibilityProvider) =
        OnboardingAffordanceManager(BUNDLE_ONBOARDING_LABEL, headerVisProvider)
}

private const val BUNDLE_ONBOARDING_LABEL = "bundle onboarding"
private const val SUMMARY_ONBOARDING_LABEL = "summary onboarding"
