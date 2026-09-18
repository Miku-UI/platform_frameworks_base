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

package com.android.systemui.notifications.intelligence.rules.ui

import android.app.Activity
import com.android.systemui.notifications.intelligence.rules.ui.composable.NotificationRulesActivity
import com.android.systemui.notifications.intelligence.rules.ui.composable.NotificationRulesScreen
import com.android.systemui.notifications.intelligence.rules.ui.composable.NotificationRulesScreenImpl
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModelImpl
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModelImpl
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesParentViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesParentViewModelImpl
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModelImpl
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
interface NotificationRulesUiModule {
    @Binds
    public fun bindRulesParentViewModelFactory(
        impl: NotificationRulesParentViewModelImpl.Factory
    ): NotificationRulesParentViewModel.Factory

    @Binds
    public fun bindCurrentRulesViewModelFactory(
        impl: NotificationRulesScreenViewModelImpl.Factory
    ): NotificationRulesScreenViewModel.Factory

    @Binds
    public fun bindEditViewModelFactory(
        impl: NotificationRuleEditViewModelImpl.Factory
    ): NotificationRuleEditViewModel.Factory

    @Binds
    public fun bindFreeformViewModelFactory(
        impl: NotificationRuleFreeformTextCreationViewModelImpl.Factory
    ): NotificationRuleFreeformTextCreationViewModel.Factory

    @Binds
    public fun bindNotificationRulesScreen(
        impl: NotificationRulesScreenImpl
    ): NotificationRulesScreen

    @Binds
    @IntoMap
    @ClassKey(NotificationRulesActivity::class)
    abstract fun provideActivity(activity: NotificationRulesActivity): Activity
}
