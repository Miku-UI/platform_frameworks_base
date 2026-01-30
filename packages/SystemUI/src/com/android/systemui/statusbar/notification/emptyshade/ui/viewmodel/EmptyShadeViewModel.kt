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

package com.android.systemui.statusbar.notification.emptyshade.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.MessageFormat
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.statusbar.notification.NotificationActivityStarter.SettingsIntent
import com.android.systemui.statusbar.notification.domain.interactor.SeenNotificationsInteractor
import com.android.systemui.statusbar.notification.emptyshade.ui.shared.flag.ShowIconInEmptyShade
import com.android.systemui.statusbar.notification.emptyshade.ui.shared.model.IconMessageModel
import com.android.systemui.statusbar.notification.footer.ui.viewmodel.FooterMessageViewModel
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import com.android.systemui.statusbar.policy.domain.model.ActiveZenModes
import com.android.systemui.util.kotlin.FlowDumperImpl
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * ViewModel for the empty shade (aka the "No notifications" text shown when there are no
 * notifications.
 */
@SuppressLint("FlowExposedFromViewModel")
class EmptyShadeViewModel
@AssistedInject
constructor(
    @ShadeDisplayAware private val context: Context,
    zenModeInteractor: ZenModeInteractor,
    seenNotificationsInteractor: SeenNotificationsInteractor,
    notificationSettingsInteractor: NotificationSettingsInteractor,
    configurationInteractor: ConfigurationInteractor,
    @Background bgDispatcher: CoroutineDispatcher,
    dumpManager: DumpManager,
) : FlowDumperImpl(dumpManager) {
    val areNotificationsHiddenInShade: Flow<Boolean> by lazy {
        zenModeInteractor.areNotificationsHiddenInShade
            .dumpWhileCollecting("areNotificationsHiddenInShade")
            .flowOn(bgDispatcher)
    }

    val hasFilteredOutSeenNotifications: StateFlow<Boolean> =
        seenNotificationsInteractor.hasFilteredOutSeenNotifications.dumpValue(
            "hasFilteredOutSeenNotifications"
        )

    private val primaryLocale by lazy {
        configurationInteractor.configurationValues
            .map { it.locales.get(0) ?: Locale.getDefault() }
            .onStart { emit(Locale.getDefault()) }
            .distinctUntilChanged()
    }

    /**
     * A combination of icon + text that replaces the old approach with the text followed by a
     * footer that shows an icon.
     */
    val message: Flow<IconMessageModel> by lazy {
        if (ShowIconInEmptyShade.isUnexpectedlyInLegacyMode()) {
            flowOf(
                IconMessageModel(
                    message = "Something went wrong",
                    icon = Icon.Resource(R.drawable.ic_error_outline, null),
                )
            )
        } else {
            combine(
                    zenModeInteractor.modesHidingNotifications,
                    primaryLocale,
                    hasFilteredOutSeenNotifications,
                ) { modes, locale, hasFilteredOutSeenNotificationsValue ->
                    when {
                        hasFilteredOutSeenNotificationsValue ->
                            IconMessageModel(
                                message = context.getString(R.string.unlock_to_see_notif_text),
                                icon = Icon.Resource(R.drawable.ic_friction_lock_closed, null),
                            )
                        modes.main == null ->
                            IconMessageModel(
                                message = context.getString(R.string.caught_up_shade_text),
                                icon = Icon.Resource(R.drawable.ic_trophy, null),
                            )
                        else ->
                            IconMessageModel(
                                message = formatModesString(locale, modes),
                                icon = modes.main.icon,
                            )
                    }
                }
                .distinctUntilChanged()
                .flowOn(bgDispatcher)
        }
    }

    val text: Flow<String> by lazy {
        ShowIconInEmptyShade.assertInLegacyMode()
        combine(zenModeInteractor.modesHidingNotifications, primaryLocale) { modes, locale ->
                formatModesString(locale, modes)
            }
            .flowOn(bgDispatcher)
    }

    private fun formatModesString(locale: Locale?, modes: ActiveZenModes): String {
        // Create a string that is either "No notifications" if no modes are filtering them
        // out, or something like "Notifications paused by SomeMode" otherwise.
        val msgFormat =
            MessageFormat(context.getString(R.string.modes_suppressing_shade_text), locale)
        val args: MutableMap<String, Any> = HashMap()
        args["count"] = modes.count
        if (modes.main != null) {
            args["mode"] = modes.main.name
        }
        return msgFormat.format(args)
    }

    val footer: FooterMessageViewModel by lazy {
        ShowIconInEmptyShade.assertInLegacyMode()
        FooterMessageViewModel(
            messageId = R.string.unlock_to_see_notif_text,
            iconId = R.drawable.ic_friction_lock_closed,
            isVisible = hasFilteredOutSeenNotifications,
        )
    }

    val onClick: Flow<SettingsIntent> by lazy {
        combine(
                zenModeInteractor.modesHidingNotifications,
                notificationSettingsInteractor.isNotificationHistoryEnabled,
            ) { modes, isNotificationHistoryEnabled ->
                if (modes.main != null) {
                    if (modes.count == 1) {
                        SettingsIntent.forModeSettings(modes.main.id)
                    } else {
                        SettingsIntent.forModesSettings()
                    }
                } else {
                    if (isNotificationHistoryEnabled) {
                        SettingsIntent.forNotificationHistory()
                    } else {
                        SettingsIntent.forNotificationSettings()
                    }
                }
            }
            .flowOn(bgDispatcher)
    }

    @AssistedFactory
    interface Factory {
        fun create(): EmptyShadeViewModel
    }
}
