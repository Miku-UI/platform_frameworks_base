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

package com.android.systemui.statusbar.notification.promoted

import android.app.Flags.apiMetricStyle
import android.app.Flags.richOngoingImprovements
import android.app.Notification
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.Size
import android.view.NotificationHeaderView
import android.view.NotificationTopLineView
import android.view.View
import android.view.View.GONE
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewStub
import android.widget.Chronometer
import android.widget.DateTimeView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.tracing.trace
import com.android.app.tracing.traceSection
import com.android.internal.R
import com.android.internal.widget.CachingIconView
import com.android.internal.widget.ImageFloatingTextView
import com.android.internal.widget.NotificationExpandButton
import com.android.internal.widget.NotificationMetricTextView
import com.android.internal.widget.NotificationProgressBar
import com.android.internal.widget.NotificationProgressDrawable
import com.android.internal.widget.NotificationProgressModel
import com.android.systemui.FontStyles
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R as systemuiR
import com.android.systemui.statusbar.notification.promoted.AodPromotedNotificationColor.PrimaryText
import com.android.systemui.statusbar.notification.promoted.AodPromotedNotificationColor.SecondaryText
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.promoted.ui.viewmodel.AODPromotedNotificationViewModel
import com.android.systemui.statusbar.notification.row.shared.ImageModel
import com.android.systemui.statusbar.notification.row.shared.isNullOrEmpty
import com.android.systemui.statusbar.notification.shared.Metric
import com.android.systemui.util.dpToPx
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun AODPromotedNotification(
    viewModelFactory: AODPromotedNotificationViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel(traceName = "$TAG.viewModel") { viewModelFactory.create() }

    val content = viewModel.content ?: return
    val audiblyAlertedIconVisible = viewModel.audiblyAlertedIconVisible
    val useLowFrequencyMode = viewModel.useLowFrequencyMode

    val notificationView = content.notificationView
    if (notificationView == null) {
        Log.w(TAG, "not displaying promoted notif with ineligible style on AOD")
        return
    }

    var hasBindingError by remember(content.identity) { mutableStateOf(false) }

    if (hasBindingError) {
        Log.w(TAG, "Not rendering due to previous binding error for ${content.identity}")
        return
    }

    key(content.identity, notificationView.getTag(viewInflationIdentity)) {
        // TODO(b/488459485): make sidePaddings response to shadeMode
        val sidePaddings = dimensionResource(systemuiR.dimen.notification_side_paddings_single)
        val sidePaddingValues = PaddingValues(horizontal = sidePaddings, vertical = 0.dp)
        AODPromotedNotificationView(
            notificationViewFactory = { notificationView },
            content = content,
            audiblyAlertedIconVisible = { audiblyAlertedIconVisible },
            useLowFrequencyMode = { useLowFrequencyMode },
            onBindingError = { hasBindingError = true },
            modifier = modifier.padding(sidePaddingValues),
        )
    }
}

@Composable
fun AODPromotedNotificationView(
    notificationViewFactory: (Context) -> View,
    content: PromotedNotificationContentModel,
    audiblyAlertedIconVisible: () -> Boolean,
    onBindingError: () -> Unit,
    modifier: Modifier = Modifier,
    useLowFrequencyMode: () -> Boolean = { true },
) {
    val borderStroke =
        BorderStroke(BORDER_WIDTH_DP.dp, SecondaryText.brush.value.copy(alpha = BORDER_ALPHA))

    val borderRadius = dimensionResource(systemuiR.dimen.notification_corner_radius)
    val borderShape = RoundedCornerShape(borderRadius)

    val maxHeight =
        with(LocalDensity.current) {
                scaledFontHeight(systemuiR.dimen.notification_max_height_for_promoted_ongoing)
                    .toPx()
            }
            .toInt()

    val viewModifier = Modifier.border(borderStroke, borderShape)
    Box(modifier) {
        AndroidView(
            factory = { context ->
                val notificationView = notificationViewFactory(context)
                if (notificationView.parent != null) {
                    (notificationView.parent as ViewGroup).removeView(notificationView)
                }
                val updater =
                    try {
                        traceSection("$TAG.findViews") {
                            AODPromotedNotificationViewUpdater(notificationView)
                        }
                    } catch (tr: Throwable) {
                        Log.wtf(TAG, "ViewUpdater creation failed", tr)
                        onBindingError()
                        null
                    }

                val frame =
                    FrameLayoutWithMaxHeight(
                        maxHeight = if (updater == null) 0 else maxHeight,
                        context = context,
                    )
                frame.addView(notificationView)
                frame.setTag(viewUpdaterTagId, updater)
                frame
            },
            update = { frame ->
                val updater = frame.getTag(viewUpdaterTagId) as? AODPromotedNotificationViewUpdater
                if (updater == null) {
                    return@AndroidView
                }

                try {
                    traceSection("$TAG.update") {
                        updater.update(content, audiblyAlertedIconVisible(), useLowFrequencyMode())
                    }
                    frame.maxHeight = maxHeight
                } catch (tr: Throwable) {
                    Log.wtf(TAG, "ViewUpdater update failed", tr)
                    onBindingError()
                    frame.maxHeight = 0
                }
            },
            modifier = viewModifier,
        )
    }
}

private class FrameLayoutWithMaxHeight(maxHeight: Int, context: Context) : FrameLayout(context) {
    var maxHeight = maxHeight
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    // This mirrors the logic in NotificationContentView.onMeasure.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        trace("AODPromotedNotif#onMeasure") {
            if (childCount != 1) {
                Log.wtf(TAG, "Should contain exactly one child.")
                return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            val horizPadding = paddingStart + paddingEnd
            val vertPadding = paddingTop + paddingBottom

            val ownWidthSize = MeasureSpec.getSize(widthMeasureSpec)
            val ownHeightMode = MeasureSpec.getMode(heightMeasureSpec)
            val ownHeightSize = MeasureSpec.getSize(heightMeasureSpec)

            val availableHeight =
                if (ownHeightMode != UNSPECIFIED) {
                    maxHeight.coerceAtMost(ownHeightSize)
                } else {
                    maxHeight
                }

            val child = getChildAt(0)
            val childWidthSpec = makeMeasureSpec(ownWidthSize, EXACTLY)
            val childHeightSpec =
                child.layoutParams.height
                    .takeIf { it >= 0 }
                    ?.let { makeMeasureSpec(availableHeight.coerceAtMost(it), EXACTLY) }
                    ?: run { makeMeasureSpec(availableHeight, AT_MOST) }
            measureChildWithMargins(
                child,
                childWidthSpec,
                horizPadding,
                childHeightSpec,
                vertPadding,
            )
            val childMeasuredHeight = child.measuredHeight

            val ownMeasuredWidth = MeasureSpec.getSize(widthMeasureSpec)
            val ownMeasuredHeight =
                if (ownHeightMode != UNSPECIFIED) {
                    childMeasuredHeight.coerceAtMost(ownHeightSize)
                } else {
                    childMeasuredHeight
                }
            setMeasuredDimension(ownMeasuredWidth, ownMeasuredHeight)
        }
    }
}

private class AODPromotedNotificationViewUpdater(root: View) {
    private val alertedIcon: ImageView? = root.findViewById(R.id.alerted_icon)
    private val alternateExpandTarget: View? = root.findViewById(R.id.alternate_expand_target)
    private val appNameText: TextView? = root.findViewById(R.id.app_name_text)
    private val bigText: ImageFloatingTextView? = root.findViewById(R.id.big_text)
    private var chronometerStub: ViewStub? = null
    private var chronometer: Chronometer? = null
    private val closeButton: View? = root.findViewById(R.id.close_button)
    private val conversationIconBadge: View? = root.findViewById(R.id.conversation_icon_badge)
    private val conversationIcon: CachingIconView? = root.findViewById(R.id.conversation_icon)
    private val conversationText: TextView? = root.findViewById(R.id.title)
    private val expandButton: NotificationExpandButton? = root.findViewById(R.id.expand_button)
    // TODO : This is not used here! consider removing it with its divider.
    private val headerText: TextView? = root.findViewById(R.id.header_text)
    private val headerTextDivider: TextView? = root.findViewById(R.id.header_text_divider)
    private val headerTextSecondary: TextView? = root.findViewById(R.id.header_text_secondary)
    private val headerTextSecondaryDivider: TextView? =
        root.findViewById(R.id.header_text_secondary_divider)
    private val icon: CachingIconView? = root.findViewById(R.id.icon)
    private val leftIcon: ImageView? = root.findViewById(R.id.left_icon)
    private val mainColumn: View? = root.findViewById(R.id.notification_main_column)
    private val notificationProgressEndIcon: CachingIconView? =
        root.findViewById(R.id.notification_progress_end_icon)
    private val notificationProgressStartIcon: CachingIconView? =
        root.findViewById(R.id.notification_progress_start_icon)
    private val profileBadge: ImageView? = root.findViewById(R.id.profile_badge)
    private val rightIcon: ImageView? = root.findViewById(R.id.right_icon)
    private val text: ImageFloatingTextView? = root.findViewById(R.id.text)
    private val time: DateTimeView? = root.findViewById(R.id.time)
    private val timeDivider: TextView? = root.findViewById(R.id.time_divider)
    private val title: TextView? = root.findViewById(R.id.title)
    private val altTitle: TextView? = root.findViewById(R.id.alt_title)
    private val altSubtext: TextView? = root.findViewById(R.id.alt_subtext)
    private val appNameTextDivider: TextView? = root.findViewById(R.id.app_name_text_divider)
    private val header: NotificationHeaderView? = root.findViewById(R.id.notification_header)
    private val topLine: NotificationTopLineView? = root.findViewById(R.id.notification_top_line)
    private val actionsContainer: FrameLayout? = root.findViewById(R.id.actions_container)
    private val verificationDivider: TextView? = root.findViewById(R.id.verification_divider)
    private val verificationIcon: ImageView? = root.findViewById(R.id.verification_icon)
    private val verificationText: TextView? = root.findViewById(R.id.verification_text)

    private var oldProgressBarStub = root.findViewById<View>(R.id.progress) as? ViewStub
    private var oldProgressBar: ProgressBar? = null
    private val newProgressBar = root.findViewById<View>(R.id.progress) as? NotificationProgressBar

    // MetricStyle notifications support up to 3 metrics.
    private val metricViews: List<MetricView> =
        if (!apiMetricStyle()) {
            emptyList<MetricView>()
        } else {
            listOf(
                MetricView(
                    container = root.findViewById(R.id.metric_view_0),
                    label = root.findViewById(R.id.metric_label_0),
                    textValue = root.findViewById(R.id.metric_value_0),
                    chronometer = root.findViewById<Chronometer?>(R.id.metric_chronometer_0),
                ),
                MetricView(
                    container = root.findViewById(R.id.metric_view_1),
                    label = root.findViewById(R.id.metric_label_1),
                    textValue = root.findViewById(R.id.metric_value_1),
                    chronometer = root.findViewById(R.id.metric_chronometer_1),
                ),
                MetricView(
                    container = root.findViewById(R.id.metric_view_2),
                    label = root.findViewById(R.id.metric_label_2),
                    textValue = root.findViewById(R.id.metric_value_2),
                    chronometer = root.findViewById(R.id.metric_chronometer_2),
                ),
            )
        }
    private val defaultLargeIconSizePx: Int =
        root.context.resources.getDimensionPixelSize(R.dimen.notification_right_icon_size)

    private val defaultTypeface = Typeface.create(FontStyles.GSF_BODY_MEDIUM, Typeface.NORMAL)
    private val metricValueTypeface =
        Typeface.create(FontStyles.GSF_DISPLAY_SMALL_EMPHASIZED_LIGHT, Typeface.NORMAL)

    private val marginPx: Int =
        root.context.resources.getDimensionPixelSize(R.dimen.notification_2025_margin)
    private val iconPaddingPx: Int =
        root.context.resources.getDimensionPixelSize(R.dimen.notification_2025_icon_circle_padding)

    private val smallIconBackgroundDrawable: Drawable =
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            shape = GradientDrawable.OVAL
            setStroke(
                // This should look similar to the Compose border from
                // AODPromotedNotification, so we're using ceil to emulate how Compose
                // makes this conversion.
                ceil(BORDER_WIDTH_DP.dpToPx(root.context)).toInt().coerceAtLeast(1),
                Color.argb((255 * BORDER_ALPHA).toInt(), 255, 255, 255),
            )
        }

    private val progressStyleProgressThickness: Float =
        root.context.resources.getDimension(
            systemuiR.dimen.notification_aod_progress_style_progress_thickness
        )
    private val progressStyleProgressAheadThickness: Float =
        root.context.resources.getDimension(
            systemuiR.dimen.notification_aod_progress_style_ahead_progress_thickness
        )

    private data class SmallIconSavedState(val background: Drawable?, val padding: Rect)

    private var smallIconSavedState: SmallIconSavedState? = null

    init {
        val chronometerView = root.findViewById<View>(R.id.chronometer)
        if (chronometerView is ViewStub) {
            chronometerStub = chronometerView
        } else if (chronometerView is Chronometer) {
            chronometer = chronometerView
        }
        // Hide views that are never visible in the skeleton promoted notification.
        alternateExpandTarget?.visibility = GONE
        closeButton?.visibility = GONE
        conversationIconBadge?.visibility = GONE
        expandButton?.visibility = GONE
        leftIcon?.visibility = GONE
        notificationProgressEndIcon?.visibility = GONE
        notificationProgressStartIcon?.visibility = GONE

        // Make one-time changes needed for the skeleton promoted notification.
        alertedIcon
            ?.drawable
            ?.mutate()
            ?.setColorFilter(SecondaryText.colorInt, PorterDuff.Mode.SRC_IN)

        adjustPromotedNotificationTextColors()
        adjustPromotedNotificationTextFonts()

        (mainColumn?.layoutParams as? MarginLayoutParams)?.let { mainColumnMargins ->
            mainColumnMargins.topMargin =
                Notification.Builder.getContentMarginTop(
                    root.context,
                    R.dimen.notification_2025_content_margin_top,
                )
        }
    }

    fun update(
        content: PromotedNotificationContentModel,
        audiblyAlertedIconVisible: Boolean,
        useLowFrequencyMode: Boolean,
    ) {
        when (content.style) {
            Style.Base -> updateBase(content, collapsed = false)
            Style.CollapsedBase -> updateBase(content, collapsed = true)
            Style.BigText -> updateBigTextStyle(content)
            Style.Call -> updateCallStyle(content, collapsed = false)
            Style.CollapsedCall -> updateCallStyle(content, collapsed = true)
            Style.Progress -> updateProgressStyle(content)
            Style.Metric,
            Style.MetricSingle -> updateMetricStyle(content, useLowFrequencyMode)

            Style.Ineligible -> {}
        }
        chronometer?.setLowFrequency(useLowFrequencyMode)
        alertedIcon?.isVisible = audiblyAlertedIconVisible
    }

    private fun updateBase(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
        textView: ImageFloatingTextView? = text,
    ) {
        val headerTitleView =
            when {
                collapsed -> title
                richOngoingImprovements() -> altTitle
                else -> null
            }

        title?.isVisible = headerTitleView !== altTitle

        updateHeader(content, headerTitleView = headerTitleView, collapsed = collapsed)

        if (headerTitleView == null) {
            updateTitle(title, content)
        }
        updateText(textView, content)
        updateNotifIcon(icon, content.skeletonNotifIcon, content.iconLevel)
        updateRightIconAndSpacing(content.skeletonLargeIcon)
        updateOldProgressBar(content)
    }

    private fun updateBigTextStyle(content: PromotedNotificationContentModel) {
        updateBase(content, collapsed = false, textView = bigText)
    }

    private fun updateCallStyle(content: PromotedNotificationContentModel, collapsed: Boolean) {
        updateConversationHeader(content, collapsed = collapsed)

        updateText(text, content)
    }

    private fun updateProgressStyle(content: PromotedNotificationContentModel) {
        updateBase(content, collapsed = false)

        updateNewProgressBar(content)
    }

    private fun updateOldProgressBar(content: PromotedNotificationContentModel) {
        if (
            content.style == Style.Progress ||
                content.oldProgress == null ||
                content.oldProgress.max == 0 ||
                content.oldProgress.isIndeterminate
        ) {
            oldProgressBar?.visibility = GONE
            return
        }

        inflateOldProgressBar()

        val oldProgressBar = oldProgressBar ?: return

        if (richOngoingImprovements()) {
            oldProgressBar.progressTintList = ColorStateList.valueOf(SecondaryText.colorInt)
        }

        oldProgressBar.progress = content.oldProgress.progress
        oldProgressBar.max = content.oldProgress.max
        oldProgressBar.isIndeterminate = content.oldProgress.isIndeterminate
        oldProgressBar.visibility = VISIBLE
    }

    private fun updateNewProgressBar(content: PromotedNotificationContentModel) {
        val newProgressBar = newProgressBar ?: return

        (newProgressBar.notificationProgressDrawable.mutate() as? NotificationProgressDrawable)
            ?.setSegmentHeight(progressStyleProgressThickness)
        (newProgressBar.notificationProgressDrawable.mutate() as? NotificationProgressDrawable)
            ?.setFadedSegmentHeight(progressStyleProgressAheadThickness)

        if (content.newProgress != null && !content.newProgress.isIndeterminate) {
            newProgressBar.setProgressModel(content.newProgress.toSkeleton().toBundle())
            newProgressBar.visibility = VISIBLE
        } else {
            newProgressBar.visibility = GONE
        }
    }

    private fun updateMetricStyle(
        content: PromotedNotificationContentModel,
        useLowFrequencyMode: Boolean,
    ) {
        if (!richOngoingImprovements()) {
            updateHeader(content, collapsed = false, null)
            updateNotifIcon(icon, content.skeletonNotifIcon, content.iconLevel)
            updateRightIconAndSpacing(content.skeletonLargeIcon)
            val hasTitle = !content.title.isNullOrEmpty()
            altTitle?.text = content.title
            altTitle?.isVisible = hasTitle
            appNameTextDivider?.isVisible = altTitle != null && hasTitle
        } else {
            updateBase(content = content, collapsed = false, textView = null)
        }

        metricViews.forEach {
            it.container?.isVisible = false
            it.label?.isVisible = false
            it.textValue?.isVisible = false
            it.chronometer?.isVisible = false
        }
        val metrics = content.metrics ?: return
        for (i in metrics.indices.take(MAX_METRICS)) {
            val metric = metrics[i]
            val metricView = metricViews[i]

            metricView.container?.isVisible = true

            metricView.label?.isVisible = true
            metricView.label?.text = metric.label

            when (metric) {
                is Metric.TimeDifference -> {
                    metricView.chronometer?.isVisible = true
                    metricView.chronometer?.isCountDown = metric.isTimer
                    metricView.chronometer?.isUseAdaptiveFormat = metric.useAdaptiveFormat
                    metricView.chronometer?.format = null
                    val isPaused = metric is Metric.TimeDifference.Paused
                    metricView.chronometer?.setStarted(!isPaused)
                    metricView.chronometer?.setLowFrequency(!isPaused && useLowFrequencyMode)
                    when (metric) {
                        is Metric.TimeDifference.ElapsedRealtime ->
                            metricView.chronometer?.setBase(metric.zeroElapsedRealtime)

                        is Metric.TimeDifference.Instant ->
                            metricView.chronometer?.setBase(metric.zeroTime)

                        is Metric.TimeDifference.Paused ->
                            metricView.chronometer?.setPausedDuration(metric.pausedDuration)
                    }
                }

                is Metric.Text -> {
                    metricView.textValue?.isVisible = true
                    metricView.textValue?.setTextVariants(metric.textVariants)
                }
            }
        }
    }

    private fun updateHeader(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
        headerTitleView: TextView?,
    ) {
        if (richOngoingImprovements()) {
            when (content.style) {
                Style.Base,
                Style.CollapsedBase,
                Style.BigText -> {
                    val hasSubText = !content.subText.isNullOrEmpty()
                    val hasText = !content.text.isNullOrEmpty()
                    val hasProgress = content.oldProgress != null
                    updateTitle(headerTitleView, content)
                    val showingAppName = updateAppName(content, maybeHide = true)
                    updateTextView(altSubtext, content.subText)
                    updateTimeAndChronometer(content)
                    updateProfileBadge(content)

                    val isTopLineOnly = !hasSubText && !hasText
                    header?.centerTopLine(isTopLineOnly && !hasProgress)
                    actionsContainer?.isVisible = !isTopLineOnly
                    updateHeaderDividers(content, hideTitle = false, hideAppName = !showingAppName)
                }

                Style.Progress -> {
                    val hasSubText = !content.subText.isNullOrEmpty()
                    val hasText = !content.text.isNullOrEmpty()
                    updateTitle(headerTitleView, content)
                    val showingAppName = updateAppName(content, maybeHide = true)
                    updateTextView(altSubtext, content.subText)
                    updateTimeAndChronometer(content)
                    updateProfileBadge(content)

                    val isTopLineOnly = !hasSubText && !hasText

                    header?.centerTopLine(isTopLineOnly)
                    actionsContainer?.isVisible = true
                    updateHeaderDividers(content, hideTitle = false, hideAppName = !showingAppName)
                }

                Style.Metric,
                Style.MetricSingle -> {
                    val hasTitle = !content.title.isNullOrEmpty()
                    updateTitle(headerTitleView, content)
                    val showingAppName = updateAppName(content, maybeHide = hasTitle)
                    updateTextView(headerTextSecondary, content.subText)
                    updateTimeAndChronometer(content)
                    updateProfileBadge(content)

                    header?.centerTopLine(false)
                    actionsContainer?.isVisible = true
                    updateHeaderDividers(
                        content,
                        hideTitle = !hasTitle,
                        hideAppName = !showingAppName,
                    )
                }

                Style.Call,
                Style.CollapsedCall -> {
                    // #updateConversationHeader takes care of header
                    // handling for Notification.CallStyle
                    Log.wtf(
                        TAG,
                        "updateHeader called for call style. " + "Style is: ${content.style}",
                    )
                }

                Style.Ineligible -> {
                    Log.wtf(TAG, "updateHeader called for ineligible style.")
                }
            }

            return
        }

        val hasTitleInHeader = headerTitleView != null && !content.title.isNullOrEmpty()
        val hasSubText = !content.subText.isNullOrEmpty()

        // Determine if the notification has no content *below* the header/top line
        val hasTextBelowHeader = !content.text.isNullOrEmpty()
        val hasTitleBelowHeader = !content.title.isNullOrEmpty() && headerTitleView == null

        val isSingleLine = !hasTitleBelowHeader && !hasTextBelowHeader

        // the collapsed form doesn't show the app name unless there is no other text in the header
        val appNameRequired = !hasTitleInHeader && !hasSubText
        val hideAppName = (!appNameRequired && collapsed)

        // We're only showing the top line (e.g. for redacted notifs), so center it
        header?.centerTopLine(isSingleLine)
        // We normally use the (empty) actions container for the bottom padding of the notification,
        // but that's not necessary when single line
        // NOTE: Metric Style notifications show title in topline and
        // they have only 1 line below topline for single metric
        actionsContainer?.isVisible = !isSingleLine

        updateAppName(content, forceHide = hideAppName)
        updateTextView(headerTextSecondary, content.subText)
        updateTitle(headerTitleView, content)
        updateTimeAndChronometer(content)
        updateProfileBadge(content)

        updateHeaderDividers(content, hideTitle = !hasTitleInHeader, hideAppName = hideAppName)
    }

    private fun updateHeaderDividers(
        content: PromotedNotificationContentModel,
        hideAppName: Boolean,
        hideTitle: Boolean,
    ) {
        if (richOngoingImprovements()) {
            // only metricStyle has headers on the top line view.
            val isSubTextOnHeader = !content.metrics.isNullOrEmpty()
            val hasSubText = isSubTextOnHeader && !content.subText.isNullOrEmpty()
            val hasAppName = !content.appName.isNullOrEmpty() && !hideAppName

            val hasHeader = !content.title.isNullOrEmpty() && !hideTitle
            val hasChronometer = content.time is When.Chronometer || content.canShowTime
            val hasTextBeforeSubText = hasAppName || hasHeader
            val hasTextBeforeTime = hasAppName || hasSubText || hasHeader

            val showDividerBeforeAppName = hasHeader && hasAppName
            val showDividerBeforeSubText = hasTextBeforeSubText && hasSubText
            val showDividerBeforeTime = hasTextBeforeTime && hasChronometer

            headerTextSecondaryDivider?.isVisible = showDividerBeforeSubText
            timeDivider?.isVisible = showDividerBeforeTime
            appNameTextDivider?.isVisible = showDividerBeforeAppName
        } else {
            val hasAppName = !content.appName.isNullOrEmpty() && !hideAppName
            val hasSubText = !content.subText.isNullOrEmpty()
            val hasHeader = !content.title.isNullOrEmpty() && !hideTitle
            val hasTimeOrChronometer = content.time != null

            val hasTextBeforeHeader = hasAppName || hasSubText
            val hasTextBeforeTime = hasAppName || hasSubText || hasHeader

            val showDividerBeforeSubText = hasAppName && hasSubText
            val showDividerBeforeHeader = hasTextBeforeHeader && hasHeader
            val showDividerBeforeTime = hasTextBeforeTime && hasTimeOrChronometer

            headerTextSecondaryDivider?.isVisible = showDividerBeforeSubText
            headerTextDivider?.isVisible = showDividerBeforeHeader
            timeDivider?.isVisible = showDividerBeforeTime
        }
    }

    private fun updateConversationHeader(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
    ) {
        updateAppName(content, forceHide = collapsed)
        updateTimeAndChronometer(content)
        updateProfileBadge(content, isCallStyle = true)

        updateImageView(verificationIcon, content.verificationIcon)
        updateTextView(verificationText, content.verificationText)

        updateConversationHeaderDividers(content, hideTitle = true, hideAppName = collapsed)

        updateConversationIcon(content)
        updateTitle(conversationText, content)
    }

    private fun updateConversationHeaderDividers(
        content: PromotedNotificationContentModel,
        hideTitle: Boolean,
        hideAppName: Boolean,
    ) {
        val hasTitle = !content.title.isNullOrEmpty() && !hideTitle
        val hasAppName = !content.appName.isNullOrEmpty() && !hideAppName
        val hasTimeOrChronometer = content.time != null
        val hasVerification =
            !content.verificationIcon.isNullOrEmpty() || content.verificationText != null

        val hasTextBeforeTime = hasTitle || hasAppName
        val hasTextBeforeVerification = hasTitle || hasAppName || hasTimeOrChronometer

        val showDividerBeforeTime = hasTextBeforeTime && hasTimeOrChronometer
        val showDividerBeforeVerification = hasTextBeforeVerification && hasVerification

        timeDivider?.isVisible = showDividerBeforeTime
        verificationDivider?.isVisible = showDividerBeforeVerification
    }

    private fun updateConversationIcon(content: PromotedNotificationContentModel) {
        // Unlike other templates, CallStyle icon rendering differs from standard
        // notification icons. This unifies the appearance of the conversation
        // icon for use small_icon case, guaranteeing that the small icon is
        // rendered in the same way.
        if (content.skeletonNotifIcon is PromotedNotificationContentModel.NotifIcon.SmallIcon) {
            conversationIcon?.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)

            // For rendering performance:
            conversationIcon?.clipToOutline = false
        }
        updateNotifIcon(conversationIcon, content.skeletonNotifIcon, content.iconLevel)
        (conversationIcon?.layoutParams as? MarginLayoutParams)?.let {
            it.bottomMargin = marginPx
            conversationIcon?.layoutParams = it
        }
    }

    private fun updateAppName(
        content: PromotedNotificationContentModel,
        forceHide: Boolean = false,
        maybeHide: Boolean = false,
    ): Boolean {
        val hide = forceHide || (richOngoingImprovements() && maybeHide && !content.preferSmallIcon)
        val appName = content.appName?.takeUnless { hide }
        updateTextView(appNameText, appName)
        return !appName.isNullOrEmpty()
    }

    private fun updateTitle(titleView: TextView?, content: PromotedNotificationContentModel) {
        updateTextView(titleView, content.title, color = PrimaryText)
    }

    private val PromotedNotificationContentModel.canShowTime: Boolean
        get() =
            when {
                !richOngoingImprovements() -> true
                this.style == Style.Call || this.style == Style.CollapsedCall -> true
                time is When.Time -> {
                    val timeValue = Instant.ofEpochMilli(time.currentTimeMillis)
                    val nowValue = Instant.now()
                    timeValue.isAfter(nowValue)
                }
                else -> false
            }

    private fun updateTimeAndChronometer(content: PromotedNotificationContentModel) {
        if (content.canShowTime) {
            if (content.time is When.Time) {
                time?.setTime(content.time.currentTimeMillis)
            }

            if (content.time is When.Chronometer) {
                inflateChronometer()
                chronometer?.base = content.time.elapsedRealtimeMillis
                chronometer?.isCountDown = content.time.isCountDown
                chronometer?.setStarted(true)
            } else {
                chronometer?.stop()
            }
            setTextViewColor(time, SecondaryText)
            setTextViewColor(chronometer, SecondaryText)
            time?.isVisible = (content.time is When.Time)
            chronometer?.isVisible = (content.time is When.Chronometer)
        } else {
            time?.isVisible = false
            chronometer?.isVisible = false
            chronometer?.stop()
            if (content.time is When.Chronometer) {
                inflateChronometer()
                chronometer?.base = content.time.elapsedRealtimeMillis
                chronometer?.isCountDown = content.time.isCountDown
                chronometer?.setStarted(true)
                chronometer?.setLowFrequency(true)
                chronometer?.isVisible = true
                setTextViewColor(chronometer, SecondaryText)
            }
        }
    }

    private fun updateRightIconAndSpacing(image: ImageModel?) {
        if (richOngoingImprovements()) {
            updateImageView(rightIcon, image)

            val rightIconSizePx = calculateRightIconDimensions(image?.drawable)
            rightIcon?.setRightIconState(
                width = rightIconSizePx.width,
                height = rightIconSizePx.height,
                marginEnd = marginPx,
            )

            val hasRightIcon = image?.drawable != null
            val spaceBasedOnRightIcon =
                if (hasRightIcon) rightIconSizePx.width + 2 * marginPx else marginPx

            topLine?.headerTextMarginEnd = spaceBasedOnRightIcon
            val hasAltSubText = !altSubtext?.text.isNullOrBlank()
            if (hasAltSubText) {
                altSubtext.setMarginEnd(spaceBasedOnRightIcon)
                bigText?.setImageEndMargin(marginPx)
                text?.setImageEndMargin(marginPx)
            } else {
                bigText?.setImageEndMargin(spaceBasedOnRightIcon)
                text?.setImageEndMargin(spaceBasedOnRightIcon)
            }
        } else {
            updateImageView(rightIcon, image)

            val rightIconSizePx = calculateRightIconDimensions(image?.drawable)
            rightIcon?.setRightIconState(
                width = rightIconSizePx.width,
                height = rightIconSizePx.height,
                marginEnd = marginPx,
            )

            bigText?.setImageEndMargin(rightIconSizePx.width)
            text?.setImageEndMargin(rightIconSizePx.width)

            val hasRightIcon = image?.drawable != null
            val spaceBasedOnRightIcon =
                if (hasRightIcon) rightIconSizePx.width + 2 * marginPx else marginPx
            title.setMarginEnd(spaceBasedOnRightIcon)
            topLine?.headerTextMarginEnd = spaceBasedOnRightIcon
        }
    }

    fun View?.setMarginEnd(marginEnd: Int) {
        val view = this ?: return
        val lp = view.layoutParams as? MarginLayoutParams ?: return
        lp.marginEnd = marginEnd
        view.layoutParams = lp
    }

    private fun calculateRightIconDimensions(drawable: Drawable?): Size {
        if (richOngoingImprovements()) {
            if (drawable == null) {
                return Size(0, 0)
            }
        }
        var viewWidthPx = defaultLargeIconSizePx
        val viewHeightPx = defaultLargeIconSizePx

        drawable?.let {
            val iconWidth = drawable.intrinsicWidth
            val iconHeight = drawable.intrinsicHeight

            if (iconWidth > 0 && iconHeight > 0) {
                if (iconWidth > iconHeight) {
                    val maxViewWidthPx = viewHeightPx * MAX_LARGE_ICON_ASPECT_RATIO
                    viewWidthPx = (viewHeightPx.toFloat() * iconWidth / iconHeight).toInt()
                    viewWidthPx = min(viewWidthPx, maxViewWidthPx.toInt())
                }
            }
        }
        return Size(viewWidthPx, viewHeightPx)
    }

    private fun updateProfileBadge(
        content: PromotedNotificationContentModel,
        isCallStyle: Boolean = false,
    ) {
        val showProfileBadge = !richOngoingImprovements() || content.preferSmallIcon || isCallStyle
        if (showProfileBadge && content.profileBadgeBitmap != null) {
            profileBadge?.setImageBitmap(content.profileBadgeBitmap)
            profileBadge?.visibility = VISIBLE
            profileBadge?.setColorFilter(PrimaryText.colorInt, PorterDuff.Mode.SRC_IN)
        } else {
            profileBadge?.visibility = GONE
        }
    }

    private fun updateNotifIcon(
        smallIconView: CachingIconView?,
        notifIcon: PromotedNotificationContentModel.NotifIcon?,
        iconLevel: Int,
    ) {
        smallIconView ?: return

        when (notifIcon) {
            is PromotedNotificationContentModel.NotifIcon.SmallIcon -> {
                restoreNotifIconState(smallIconView)

                // Icon binding must be called in this order
                updateImageView(smallIconView, notifIcon.imageModel)
                smallIconView.setImageLevel(iconLevel)
                smallIconView.background = smallIconBackgroundDrawable
                // The background is an outline with a transparent center, so it should be tinted
                // the same as the icon itself.
                smallIconView.setBackgroundColor(PrimaryText.colorInt)
                smallIconView.originalIconColor = PrimaryText.colorInt
            }

            is PromotedNotificationContentModel.NotifIcon.AppIcon -> {
                saveNotifIconState(smallIconView)
                resetNotifIconState(smallIconView)

                updateImageView(smallIconView, notifIcon.drawable)
            }

            else -> {
                smallIconView.isVisible = false
            }
        }
    }

    private fun saveNotifIconState(smallIconView: CachingIconView) {
        smallIconSavedState == null || return

        smallIconSavedState =
            smallIconView.let {
                SmallIconSavedState(
                    background = it.background,
                    padding = Rect(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom),
                )
            }
    }

    private fun resetNotifIconState(smallIconView: CachingIconView) {
        smallIconView.background = null
        smallIconView.setPadding(0, 0, 0, 0)
    }

    private fun restoreNotifIconState(smallIconView: CachingIconView) {
        val savedState = smallIconSavedState ?: return

        smallIconView.background = savedState.background
        savedState.padding.let { smallIconView.setPadding(it.left, it.top, it.right, it.bottom) }

        smallIconSavedState = null
    }

    private fun inflateChronometer() {
        if (chronometer != null) {
            return
        }

        chronometer = chronometerStub?.inflate() as Chronometer
        chronometerStub = null

        chronometer?.appendFontFeatureSetting("tnum")
    }

    private fun inflateOldProgressBar() {
        if (oldProgressBar != null) {
            return
        }

        oldProgressBar = oldProgressBarStub?.inflate() as ProgressBar
        oldProgressBarStub = null
    }

    private fun updateText(
        view: ImageFloatingTextView?,
        content: PromotedNotificationContentModel,
    ) {
        view?.setHasImage(!content.skeletonLargeIcon.isNullOrEmpty())
        val line =
            if (!richOngoingImprovements()) {
                if (content.title != null) 0 else 1
            } else {
                if (content.subText.isNullOrBlank()) 1 else 0
            }
        view?.setNumIndentLines(line)
        updateTextView(view, content.text)
    }

    private fun updateTextView(
        view: TextView?,
        text: CharSequence?,
        color: AodPromotedNotificationColor = SecondaryText,
    ) {
        if (view == null) return
        setTextViewColor(view, color)

        view.text = text?.toSkeleton() ?: ""
        view.isVisible = !text.isNullOrEmpty()
    }

    private fun updateImageView(view: ImageView?, model: ImageModel?) {
        updateImageView(view, model?.drawable)
    }

    private fun updateImageView(view: ImageView?, drawable: Drawable?) {
        view ?: return
        // for AOD we are trying to get the best monochrome icon we can, so if
        // this is an adaptive drawable (like a launcher icon) that has a monochrome version,
        // show it here
        view.setImageDrawable((drawable as? AdaptiveIconDrawable)?.monochrome ?: drawable)
        view.isVisible = drawable != null
    }

    private fun setTextViewColor(view: TextView?, color: AodPromotedNotificationColor) {
        view?.setTextColor(color.colorInt)
    }

    private fun adjustPromotedNotificationTextColors() {
        setTextViewColor(headerTextDivider, SecondaryText)
        setTextViewColor(headerTextSecondaryDivider, SecondaryText)
        setTextViewColor(timeDivider, SecondaryText)
        setTextViewColor(verificationDivider, SecondaryText)
        setTextViewColor(altTitle, SecondaryText)
        setTextViewColor(appNameTextDivider, SecondaryText)
        metricViews.forEach { metricView ->
            metricView.label?.let { setTextViewColor(it, SecondaryText) }
            metricView.chronometer?.let { setTextViewColor(it, SecondaryText) }
            metricView.textValue?.let { setTextViewColor(it, SecondaryText) }
        }
    }

    private fun adjustPromotedNotificationTextFonts() {
        adjustTextViewFont(appNameText)
        adjustTextViewFont(bigText)
        adjustTextViewFont(conversationText)
        adjustTextViewFont(headerText)
        adjustTextViewFont(headerTextDivider)
        adjustTextViewFont(headerTextSecondary)
        adjustTextViewFont(headerTextSecondaryDivider)
        adjustTextViewFont(text)
        adjustTextViewFont(title)
        adjustTextViewFont(verificationDivider)
        adjustTextViewFont(verificationText)
        adjustTextViewFont(time)
        adjustTextViewFont(timeDivider)
        adjustTextViewFont(altTitle)
        adjustTextViewFont(appNameTextDivider)
        metricViews.forEach { metricView ->
            metricView.label?.let(::adjustTextViewFont)
            metricView.textValue?.let(::adjustMetricStyleValue)
            metricView.chronometer?.let(::adjustMetricStyleValue)
        }
    }

    private fun adjustTextViewFont(view: TextView?) {
        view?.setTypeface(defaultTypeface, Typeface.NORMAL)
    }

    private fun adjustMetricStyleValue(view: TextView?) {
        view?.setTypeface(metricValueTypeface, Typeface.NORMAL)
    }

    companion object {
        /** Maximum aspect ratio of the large icon. 16:9 */
        private const val MAX_LARGE_ICON_ASPECT_RATIO: Float = 16f / 9f

        /**
         * Maximum allowed Notification.Metric count. Same as Notification.MetricStyle.MAX_METRICS
         */
        private const val MAX_METRICS: Int = 3
    }
}

private fun CharSequence.toSkeleton(): CharSequence {
    return this.toString()
}

private fun NotificationProgressModel.toSkeleton(): NotificationProgressModel {
    if (isIndeterminate) {
        return NotificationProgressModel(/* indeterminateColor= */ SecondaryText.colorInt)
    }

    return NotificationProgressModel(
        listOf(Notification.ProgressStyle.Segment(progressMax).toSkeleton()),
        points.map { it.toSkeleton() }.toList(),
        progress,
        /* isStyledByProgress = */ true,
        /* segmentsFallbackColor = */ SecondaryText.colorInt,
    )
}

private fun Notification.ProgressStyle.Segment.toSkeleton(): Notification.ProgressStyle.Segment {
    return Notification.ProgressStyle.Segment(length).also {
        it.id = id
        it.color = SecondaryText.colorInt
    }
}

private fun Notification.ProgressStyle.Point.toSkeleton(): Notification.ProgressStyle.Point {
    return Notification.ProgressStyle.Point(position).also {
        it.id = id
        it.color = SecondaryText.colorInt
    }
}

private fun TextView.appendFontFeatureSetting(newSetting: String) {
    fontFeatureSettings = (fontFeatureSettings?.let { "$it," } ?: "") + newSetting
}

private fun ImageView.setRightIconState(width: Int, height: Int, marginEnd: Int) {

    val lp = (layoutParams as? MarginLayoutParams) ?: return
    lp.width = width
    lp.height = height
    lp.marginEnd = marginEnd

    layoutParams = lp
}

private fun NotificationTopLineView.setEndMargin(marginEnd: Int) {
    val lp = (layoutParams as? MarginLayoutParams) ?: return
    lp.marginEnd = marginEnd
    layoutParams = lp
}

private enum class AodPromotedNotificationColor(val colorInt: Int) {
    Background(android.graphics.Color.BLACK),
    PrimaryText(android.graphics.Color.WHITE),
    SecondaryText(android.graphics.Color.WHITE);

    val brush = SolidColor(androidx.compose.ui.graphics.Color(colorInt))
}

class MetricView(
    val container: View?,
    val label: TextView?,
    val textValue: NotificationMetricTextView?,
    val chronometer: Chronometer?,
)

@Composable
private fun scaledFontHeight(@DimenRes dimenId: Int): Dp {
    return dimensionResource(dimenId) * LocalDensity.current.fontScale.coerceAtLeast(1f)
}

private val viewUpdaterTagId = systemuiR.id.aod_promoted_notification_view_updater_tag
private val viewInflationIdentity = systemuiR.id.aod_promoted_notification_inflation_identity

private const val TAG = "AODPromotedNotification"
private const val BORDER_WIDTH_DP = 0.5
private const val BORDER_ALPHA = 0.32f
