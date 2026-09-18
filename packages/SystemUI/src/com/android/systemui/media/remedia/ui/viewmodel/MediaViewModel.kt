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

package com.android.systemui.media.remedia.ui.viewmodel

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.android.systemui.animation.Expandable
import com.android.systemui.classifier.Classifier
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.media.controls.shared.MediaLogger
import com.android.systemui.media.controls.util.MediaUiEventLogger
import com.android.systemui.media.remedia.domain.interactor.MediaInteractor
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/** Models UI state for a media element. */
class MediaViewModel
@AssistedInject
constructor(
    private val interactor: MediaInteractor,
    private val falsingSystem: MediaFalsingSystem,
    val mediaLogger: MediaLogger,
    val mediaUiEventLogger: MediaUiEventLogger,
    @Assisted private val context: Context,
    @Assisted private val carouselVisibility: MediaCarouselVisibility,
) : HydratedActivatable() {

    /** Whether the user is actively moving the thumb of the seek bar. */
    private var isScrubbing: Boolean by mutableStateOf(false)
    /** The position of the thumb of the seek bar as the user is scrubbing it. */
    private var seekProgress: Float by mutableFloatStateOf(0f)
    /** Whether the internal "guts" are visible. */
    private val isGutsVisible: Boolean
        get() = interactor.isGutsVisible

    /** The index of the currently-selected card. */
    private var selectedCardIndex: Int by mutableIntStateOf(interactor.currentCarouselIndex)
        private set

    /** The index of the currently visible card across different locations of media carousel */
    val currentIndex: Int by derivedStateOf {
        // allow update cards index on locations if the number of cards stays the same.
        if (isVisible() || interactor.sessions.size == latestVersion.size) {
            latestCurrentIndex = interactor.currentCarouselIndex
        }
        latestCurrentIndex
    }

    /** Whether media carousel should scroll to the first card in the list after composition */
    val scrollToFirst: Boolean by derivedStateOf { interactor.shouldScrollToFirst }

    var cardMaxWidth: Int by mutableIntStateOf(0)

    // TODO: b/397989775 these variables are only used when scene_container flag is off
    private var latestVersion = emptyList<MediaCardViewModel>()
    private var latestCurrentIndex = 0
    private var isVisible: () -> Boolean = { true }

    private val isOnLockscreen: Boolean by
        interactor.isOnLockscreen.hydratedStateOf(initialValue = true)

    /** The current list of cards to show in the UI. */
    val cards: List<MediaCardViewModel> by derivedStateOf {
        interactor.sessions
            .mapIndexed { sessionIndex, session ->
                val isCurrentSession = sessionIndex == selectedCardIndex
                val isCurrentSessionAndScrubbing = isScrubbing && isCurrentSession
                object : MediaCardViewModel {
                    override val key = session.key
                    override val icon = session.appIcon
                    override val background: Icon?
                        get() = session.background

                    override val colorScheme: MediaColorScheme?
                        get() = session.colorScheme

                    override val title = session.title
                    override val subtitle = session.subtitle
                    override val isExplicit = session.isExplicit
                    override val actionButtonLayout = session.actionButtonLayout
                    override val playPauseAction =
                        session.playPauseAction.toPlayPauseActionViewModel(session)
                    override val additionalActions: List<MediaSecondaryActionViewModel>
                        get() {
                            return session.additionalActions.map { action ->
                                action.toSecondaryActionViewModel(session)
                            }
                        }

                    override val navigation: MediaNavigationViewModel
                        get() {
                            val onScrubChange = { progress: Float ->
                                check(selectedCardIndex == sessionIndex) {
                                    "Can't seek on a card that's not the selected card!"
                                }
                                isScrubbing = true
                                seekProgress = progress
                            }

                            val onScrubFinished = { dragDelta: Offset, isValidVelocity: Boolean ->
                                if (
                                    isValidVelocity &&
                                        dragDelta.isHorizontal() &&
                                        !falsingSystem.isFalseTouch(Classifier.MEDIA_SEEKBAR)
                                ) {
                                    mediaUiEventLogger.logSeek(
                                        session.uid,
                                        session.packageName,
                                        session.key,
                                    )
                                    interactor.seek(
                                        sessionKey = session.key,
                                        to = (seekProgress * session.durationMs).roundToLong(),
                                    )
                                }
                                isScrubbing = false
                            }

                            return if (session.canShowSeekbar) {
                                MediaNavigationViewModel.Showing(
                                    progress =
                                        if (!isCurrentSessionAndScrubbing) {
                                            session.positionMs.toFloat() / session.durationMs
                                        } else {
                                            seekProgress
                                        },
                                    left = session.leftAction.toSecondaryActionViewModel(session),
                                    right = session.rightAction.toSecondaryActionViewModel(session),
                                    isSquiggly =
                                        session.state != MediaSessionState.Paused &&
                                            !isCurrentSessionAndScrubbing,
                                    isScrubbing = isCurrentSessionAndScrubbing,
                                    onScrubChange =
                                        if (session.canBeScrubbed) onScrubChange else null,
                                    onScrubFinished =
                                        if (session.canBeScrubbed) onScrubFinished else null,
                                    contentDescription =
                                        context.getString(
                                            R.string.controls_media_seekbar_description,
                                            formatTimeContentDescription(session.positionMs),
                                            formatTimeContentDescription(session.durationMs),
                                        ),
                                    progressText =
                                        if (isCurrentSessionAndScrubbing) {
                                            formatTimeLabel(
                                                (seekProgress * session.durationMs).toLong()
                                            )
                                        } else {
                                            formatTimeLabel(session.positionMs)
                                        },
                                    durationText = formatTimeLabel(session.durationMs),
                                )
                            } else {
                                MediaNavigationViewModel.Hidden(
                                    left = session.leftAction.toSecondaryActionViewModel(session),
                                    right = session.rightAction.toSecondaryActionViewModel(session),
                                )
                            }
                        }

                    override val guts: MediaCardGutsViewModel
                        get() {
                            return MediaCardGutsViewModel(
                                isVisible = isGutsVisible && isCurrentSession,
                                text =
                                    if (session.canBeHidden) {
                                        context.getString(
                                            R.string.controls_media_close_session,
                                            session.appName,
                                        )
                                    } else {
                                        context.getString(R.string.controls_media_active_session)
                                    },
                                primaryAction =
                                    if (session.canBeHidden) {
                                        MediaGutsButtonViewModel(
                                            text =
                                                context.getString(
                                                    R.string.controls_media_dismiss_button
                                                ),
                                            onClick = {
                                                falsingSystem.runIfNotFalseTap(
                                                    FalsingManager.LOW_PENALTY
                                                ) {
                                                    mediaUiEventLogger.logLongPressDismiss(
                                                        session.uid,
                                                        session.packageName,
                                                        session.key,
                                                    )
                                                    interactor.hide(
                                                        session.key,
                                                        MEDIA_PLAYER_ANIMATION_DELAY_MS,
                                                        userInitiated = true,
                                                    )
                                                    interactor.setIsGutsVisible(false)
                                                }
                                            },
                                        )
                                    } else {
                                        MediaGutsButtonViewModel(
                                            text = context.getString(R.string.cancel),
                                            onClick = {
                                                falsingSystem.runIfNotFalseTap(
                                                    FalsingManager.LOW_PENALTY
                                                ) {
                                                    interactor.setIsGutsVisible(false)
                                                }
                                            },
                                        )
                                    },
                                secondaryAction =
                                    MediaGutsButtonViewModel(
                                            text = context.getString(R.string.cancel),
                                            onClick = {
                                                falsingSystem.runIfNotFalseTap(
                                                    FalsingManager.LOW_PENALTY
                                                ) {
                                                    interactor.setIsGutsVisible(false)
                                                }
                                            },
                                        )
                                        .takeIf { session.canBeHidden },
                                settingsButton =
                                    MediaGutsSettingsButtonViewModel(
                                        icon =
                                            Icon.Resource(
                                                resId = R.drawable.ic_settings,
                                                contentDescription =
                                                    ContentDescription.Resource(
                                                        res =
                                                            R.string.controls_media_settings_button
                                                    ),
                                            ),
                                        onClick = {
                                            falsingSystem.runIfNotFalseTap(
                                                FalsingManager.LOW_PENALTY
                                            ) {
                                                mediaUiEventLogger.logLongPressSettings(
                                                    session.uid,
                                                    session.packageName,
                                                    session.key,
                                                )
                                                interactor.openMediaSettings()
                                            }
                                        },
                                    ),
                                onLongClick = { interactor.setIsGutsVisible(false) },
                            )
                        }

                    override val deviceSuggestionChip: MediaDeviceChipViewModel?
                        get() {
                            return session.suggestedOutputDevice?.let {
                                MediaDeviceChipViewModel(
                                    icon = it.icon,
                                    text =
                                        context.getString(
                                            R.string.media_suggestion_disconnected_text,
                                            it.name,
                                        ),
                                    isConnecting = it.isInProgress,
                                    onClick = { expandable ->
                                        falsingSystem.runIfNotFalseTap(
                                            FalsingManager.MODERATE_PENALTY
                                        ) {
                                            it.onClick(expandable)
                                        }
                                    },
                                    defaultDescription =
                                        context.getString(R.string.controls_media_output_chip),
                                )
                            }
                        }

                    override val outputSwitcherChip: MediaDeviceChipViewModel
                        get() {
                            return MediaDeviceChipViewModel(
                                icon = session.outputDevice.icon,
                                text =
                                    if (session.suggestedOutputDevice == null)
                                        session.outputDevice.name
                                    else null,
                                onClick = { expandable ->
                                    falsingSystem.runIfNotFalseTap(
                                        FalsingManager.MODERATE_PENALTY
                                    ) {
                                        mediaUiEventLogger.logOpenOutputSwitcher(
                                            session.uid,
                                            session.packageName,
                                            session.key,
                                        )
                                        session.outputDevice.onClick(expandable)
                                    }
                                },
                                defaultDescription =
                                    context.getString(R.string.controls_media_output_chip),
                            )
                        }

                    override val outputSwitcherChipButton: MediaSecondaryActionViewModel.Action
                        get() {
                            return MediaSecondaryActionViewModel.Action(
                                icon = session.outputDevice.icon,
                                onClick = {
                                    falsingSystem.runIfNotFalseTap(
                                        FalsingManager.MODERATE_PENALTY
                                    ) {
                                        session.outputDevice.onClick(null)
                                    }
                                },
                            )
                        }

                    override val onClick = { expandable: Expandable ->
                        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) {
                            if (isCurrentSession) {
                                mediaUiEventLogger.logTapContentView(
                                    session.uid,
                                    session.packageName,
                                    session.key,
                                )
                                session.onClick(expandable)
                            } else {
                                onCardSelected(sessionIndex)
                            }
                        }
                    }
                    override val contentDescription =
                        context.getString(
                            R.string.controls_media_playing_item_description,
                            session.title,
                            session.subtitle,
                            session.appName,
                        )
                    override val onLongClick = {
                        mediaUiEventLogger.logLongPressOpen(
                            session.uid,
                            session.packageName,
                            session.key,
                        )
                        interactor.setIsGutsVisible(true)
                    }
                }
            }
            .let {
                if (isVisible()) {
                    if (latestVersion.size != it.size) {
                        mediaLogger.logMediaCarouselSize(it.size)
                    }
                    latestVersion = it
                }
                latestVersion
            }
    }

    val settingsButtonViewModel =
        MediaSettingsButtonViewModel(
            icon =
                Icon.Resource(
                    resId = R.drawable.ic_media_settings,
                    contentDescription =
                        ContentDescription.Resource(res = R.string.controls_media_settings_button),
                ),
            onClick = {
                falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) {
                    mediaUiEventLogger.logCarouselSettings()
                    interactor.openMediaSettings()
                }
            },
        )

    /** Whether the carousel should be visible. */
    val isCarouselVisible: Boolean
        get() =
            when (carouselVisibility) {
                MediaCarouselVisibility.WhenNotEmpty ->
                    interactor.hasAnyMedia && (!isOnLockscreen || interactor.allowMediaOnLockscreen)

                MediaCarouselVisibility.WhenAnyCardIsActive ->
                    interactor.hasActiveMedia &&
                        (!isOnLockscreen || interactor.allowMediaOnLockscreen)
            }

    fun setVisibility(visible: () -> Boolean) {
        isVisible = visible
    }

    /** Notifies that the card at [cardIndex] has been selected in the UI. */
    fun onCardSelected(cardIndex: Int) {
        if (cardIndex == selectedCardIndex) return
        check(!isVisible() || cardIndex >= 0 && cardIndex < cards.size) {
            "Invalid card index $cardIndex"
        }
        selectedCardIndex = cardIndex
        if (selectedCardIndex != currentIndex) {
            mediaUiEventLogger.logMediaCarouselPage(selectedCardIndex)
        }
        interactor.storeCurrentCarouselIndex(selectedCardIndex)
        interactor.setIsGutsVisible(false)
    }

    /** Notifies that the card at [cardIndex] is visible on screen. */
    fun onCardVisible(cardIndex: Int, areDeviceChipsVisible: Boolean) {
        interactor.sessions.getOrNull(cardIndex)?.onSessionVisible(areDeviceChipsVisible)
    }

    /** Notifies that the carousel is reordered and first card is now visible on screen. */
    fun onScrollToFirstCard() {
        interactor.resetScrollToFirst()
    }

    private fun MediaActionModel.toPlayPauseActionViewModel(
        session: MediaSessionModel
    ): MediaPlayPauseActionViewModel? {
        return when (this) {
            is MediaActionModel.Action ->
                MediaPlayPauseActionViewModel(
                    state = session.state,
                    icon = icon,
                    onClick =
                        onClick?.let {
                            {
                                falsingSystem.runIfNotFalseTap(FalsingManager.MODERATE_PENALTY) {
                                    mediaUiEventLogger.logTapAction(
                                        id,
                                        session.uid,
                                        session.packageName,
                                        session.key,
                                    )
                                    it()
                                }
                            }
                        },
                )
            is MediaActionModel.None,
            is MediaActionModel.ReserveSpace -> null
        }
    }

    private fun MediaActionModel.toSecondaryActionViewModel(
        session: MediaSessionModel
    ): MediaSecondaryActionViewModel {
        return when (this) {
            is MediaActionModel.Action ->
                MediaSecondaryActionViewModel.Action(
                    icon = icon,
                    onClick =
                        onClick?.let {
                            {
                                falsingSystem.runIfNotFalseTap(FalsingManager.MODERATE_PENALTY) {
                                    mediaUiEventLogger.logTapAction(
                                        id,
                                        session.uid,
                                        session.packageName,
                                        session.key,
                                    )
                                    it()
                                }
                            }
                        },
                )
            is MediaActionModel.ReserveSpace -> MediaSecondaryActionViewModel.ReserveSpace
            is MediaActionModel.None -> MediaSecondaryActionViewModel.None
        }
    }

    /**
     * Returns a time string suitable for content description, e.g. "12 minutes 34 seconds"
     *
     * Follows same logic as Chronometer#formatDuration
     */
    private fun formatTimeContentDescription(milliseconds: Long): String {
        var seconds = milliseconds.milliseconds.inWholeSeconds

        val hours =
            if (seconds >= OneHourInSec) {
                seconds / OneHourInSec
            } else {
                0
            }
        seconds -= hours * OneHourInSec

        val minutes =
            if (seconds >= OneMinuteInSec) {
                seconds / OneMinuteInSec
            } else {
                0
            }
        seconds -= minutes * OneMinuteInSec

        val measures = arrayListOf<Measure>()
        if (hours > 0) {
            measures.add(Measure(hours, MeasureUnit.HOUR))
        }
        if (minutes > 0) {
            measures.add(Measure(minutes, MeasureUnit.MINUTE))
        }
        measures.add(Measure(seconds, MeasureUnit.SECOND))

        return MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
            .formatMeasures(*measures.toTypedArray())
    }

    /** Returns a time string suitable for display, e.g. "12:34" */
    private fun formatTimeLabel(milliseconds: Long): String {
        return DateUtils.formatElapsedTime(milliseconds / DateUtils.SECOND_IN_MILLIS)
    }

    /**
     * Returns `true` if this [Offset] is the same or larger on the horizontal axis than the
     * vertical axis.
     */
    private fun Offset.isHorizontal(): Boolean {
        return abs(x) >= abs(y)
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, carouselVisibility: MediaCarouselVisibility): MediaViewModel
    }

    companion object {
        private const val OneMinuteInSec = 60
        private const val OneHourInSec = OneMinuteInSec * 60
        private const val MEDIA_PLAYER_ANIMATION_DELAY_MS = 334L
    }
}
