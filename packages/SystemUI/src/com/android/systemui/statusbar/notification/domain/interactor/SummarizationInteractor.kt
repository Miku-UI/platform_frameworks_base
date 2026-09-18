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

package com.android.systemui.statusbar.notification.domain.interactor

import android.graphics.drawable.AnimatedVectorDrawable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.shared.NotificationSummarizationAllowAnimation
import com.android.systemui.statusbar.notification.data.repository.AnimationState
import com.android.systemui.statusbar.notification.data.repository.SummarizationAnimationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Coordinator for notification summarization features. */
@SysUISingleton
class SummarizationInteractor
@Inject
constructor(
    val repository: SummarizationAnimationRepository,
    val shadeInteractor: ShadeInteractor,
    val activeNotificationsInteractor: ActiveNotificationsInteractor,
) {

    fun trackDecoratedEntry(entry: NotificationEntry, drawable: AnimatedVectorDrawable?) {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return
        if (drawable != null) {
            repository.animationHistory.putIfAbsent(entry.key, AnimationState.NEEDS_ANIMATION)
            repository.drawables[entry.key] = drawable
        } else {
            repository.animationHistory.remove(entry.key)
            repository.drawables.remove(entry.key)
        }
    }

    fun cleanupEntry(entry: NotificationEntry) {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return
        repository.drawables.remove(entry.key)
        repository.animationHistory.remove(entry.key)
    }

    fun iconForEntry(entryKey: String): AnimatedVectorDrawable? {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return null
        return repository.drawables[entryKey]
    }

    fun onTriggered(entryKey: String) {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return
        repository.animationHistory[entryKey] = AnimationState.HAS_ANIMATED
    }

    val shadeStateAllowsAnimation: Flow<Boolean> =
        shadeInteractor.isShadeFullyExpanded
            .map { isExpanded ->
                isExpanded // TODO(b/486942439): flesh out logic for this condition
            }
            .distinctUntilChanged()

    val animationReadyEntryKeys: Flow<List<String>> =
        combine(
            activeNotificationsInteractor.topLevelRepresentativeNotifications,
            shadeStateAllowsAnimation,
        ) { notifications, canAnimate ->
            if (!canAnimate) {
                emptyList()
            } else {
                notifications.mapNotNull {
                    it.key.takeIf { key ->
                        repository.animationHistory[key] == AnimationState.NEEDS_ANIMATION
                    }
                }
            }
        }
}
