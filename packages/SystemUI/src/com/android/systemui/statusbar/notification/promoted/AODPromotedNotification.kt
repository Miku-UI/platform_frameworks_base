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

import android.app.Flags.notificationsRedesignTemplates
import android.app.Notification
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import androidx.compose.runtime.key
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
import com.android.internal.widget.BigPictureNotificationImageView
import com.android.internal.widget.CachingIconView
import com.android.internal.widget.ImageFloatingTextView
import com.android.internal.widget.NotificationExpandButton
import com.android.internal.widget.NotificationProgressBar
import com.android.internal.widget.NotificationProgressDrawable
import com.android.internal.widget.NotificationProgressModel
import com.android.systemui.FontStyles
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R as systemuiR
import com.android.systemui.statusbar.notification.promoted.AodPromotedNotificationColor.Background
import com.android.systemui.statusbar.notification.promoted.AodPromotedNotificationColor.PrimaryText
import com.android.systemui.statusbar.notification.promoted.AodPromotedNotificationColor.SecondaryText
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.promoted.ui.viewmodel.AODPromotedNotificationViewModel
import com.android.systemui.statusbar.notification.row.shared.ImageModel
import com.android.systemui.statusbar.notification.row.shared.isNullOrEmpty
import kotlin.math.min

@Composable
fun AODPromotedNotification(
    viewModelFactory: AODPromotedNotificationViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    if (!PromotedNotificationUi.isEnabled) {
        return
    }

    val viewModel = rememberViewModel(traceName = "$TAG.viewModel") { viewModelFactory.create() }

    val content = viewModel.content ?: return
    val audiblyAlertedIconVisible = viewModel.audiblyAlertedIconVisible

    val notificationView = content.notificationView
    if (notificationView == null) {
        Log.w(TAG, "not displaying promoted notif with ineligible style on AOD")
        return
    }
    key(content.identity, notificationView.getTag(viewInflationIdentity)) {
        AODPromotedNotificationView(
            notificationViewFactory = { notificationView },
            content = content,
            audiblyAlertedIconVisible = audiblyAlertedIconVisible,
            modifier = modifier,
        )
    }
}

@Composable
fun AODPromotedNotificationView(
    notificationViewFactory: (Context) -> View,
    content: PromotedNotificationContentModel,
    audiblyAlertedIconVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val sidePaddings = dimensionResource(systemuiR.dimen.notification_side_paddings)
    val sidePaddingValues = PaddingValues(horizontal = sidePaddings, vertical = 0.dp)

    val boxModifier = modifier.padding(sidePaddingValues)

    val borderStroke = BorderStroke(0.5.dp, SecondaryText.brush.value.copy(alpha = 0.32f))

    val borderRadius = dimensionResource(systemuiR.dimen.notification_corner_radius)
    val borderShape = RoundedCornerShape(borderRadius)

    val maxHeight =
        with(LocalDensity.current) {
                scaledFontHeight(systemuiR.dimen.notification_max_height_for_promoted_ongoing)
                    .toPx()
            }
            .toInt()

    val viewModifier = Modifier.border(borderStroke, borderShape)

    Box(modifier = boxModifier) {
        AndroidView(
            factory = { context ->
                val notificationView = notificationViewFactory(context)
                if (notificationView.parent != null) {
                    (notificationView.parent as ViewGroup).removeView(notificationView)
                }

                val updater =
                    traceSection("$TAG.findViews") {
                        AODPromotedNotificationViewUpdater(notificationView)
                    }

                val frame = FrameLayoutWithMaxHeight(maxHeight, context)
                frame.addView(notificationView)
                frame.setTag(viewUpdaterTagId, updater)

                frame
            },
            update = { frame ->
                val updater = frame.getTag(viewUpdaterTagId) as AODPromotedNotificationViewUpdater

                traceSection("$TAG.update") { updater.update(content, audiblyAlertedIconVisible) }
                frame.maxHeight = maxHeight
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

private val PromotedNotificationContentModel.layoutResource: Int?
    get() {
        return if (notificationsRedesignTemplates()) {
            when (style) {
                Style.Base -> R.layout.notification_2025_template_expanded_base
                Style.CollapsedBase -> R.layout.notification_2025_template_collapsed_base
                Style.BigPicture -> R.layout.notification_2025_template_expanded_big_picture
                Style.BigText -> R.layout.notification_2025_template_expanded_big_text
                Style.Call -> R.layout.notification_2025_template_expanded_call
                Style.CollapsedCall -> R.layout.notification_2025_template_collapsed_call
                Style.Progress -> R.layout.notification_2025_template_expanded_progress
                Style.Ineligible -> null
            }
        } else {
            when (style) {
                Style.Base -> R.layout.notification_template_material_big_base
                Style.CollapsedBase -> R.layout.notification_template_material_base
                Style.BigPicture -> R.layout.notification_template_material_big_picture
                Style.BigText -> R.layout.notification_template_material_big_text
                Style.Call -> R.layout.notification_template_material_big_call
                Style.CollapsedCall -> R.layout.notification_template_material_call
                Style.Progress -> R.layout.notification_template_material_progress
                Style.Ineligible -> null
            }
        }
    }

private class AODPromotedNotificationViewUpdater(root: View) {
    private val alertedIcon: ImageView? = root.findViewById(R.id.alerted_icon)
    private val alternateExpandTarget: View? = root.findViewById(R.id.alternate_expand_target)
    private val appNameDivider: TextView? = root.findViewById(R.id.app_name_divider)
    private val appNameText: TextView? = root.findViewById(R.id.app_name_text)
    private val bigPicture: BigPictureNotificationImageView? = root.findViewById(R.id.big_picture)
    private val bigText: ImageFloatingTextView? = root.findViewById(R.id.big_text)
    private var chronometerStub: ViewStub? = null
    private var chronometer: Chronometer? = null
    private val closeButton: View? = root.findViewById(R.id.close_button)
    private val conversationIconBadge: View? = root.findViewById(R.id.conversation_icon_badge)
    private val conversationIcon: CachingIconView? = root.findViewById(R.id.conversation_icon)
    private val conversationText: TextView? =
        root.findViewById(
            if (notificationsRedesignTemplates()) R.id.title else R.id.conversation_text
        )
    private val expandButton: NotificationExpandButton? = root.findViewById(R.id.expand_button)
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
    private val header: NotificationHeaderView? = root.findViewById(R.id.notification_header)
    private val topLine: NotificationTopLineView? = root.findViewById(R.id.notification_top_line)
    private val actionsContainer: FrameLayout? = root.findViewById(R.id.actions_container)
    private val verificationDivider: TextView? = root.findViewById(R.id.verification_divider)
    private val verificationIcon: ImageView? = root.findViewById(R.id.verification_icon)
    private val verificationText: TextView? = root.findViewById(R.id.verification_text)

    private var oldProgressBarStub = root.findViewById<View>(R.id.progress) as? ViewStub
    private var oldProgressBar: ProgressBar? = null
    private val newProgressBar = root.findViewById<View>(R.id.progress) as? NotificationProgressBar

    private val defaultLargeIconSizePx: Int =
        root.context.resources.getDimensionPixelSize(R.dimen.notification_right_icon_size)
    private val defaultTypeface: Typeface? = getNotificationTypeFace(root.context)

    private val marginPx: Int =
        if (notificationsRedesignTemplates()) {
            root.context.resources.getDimensionPixelSize(R.dimen.notification_2025_margin)
        } else {
            root.context.resources.getDimensionPixelSize(
                systemuiR.dimen.notification_shade_content_margin_horizontal
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
        bigPicture?.visibility = GONE
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

        setTextViewColor(appNameDivider, SecondaryText)
        setTextViewColor(headerTextDivider, SecondaryText)
        setTextViewColor(headerTextSecondaryDivider, SecondaryText)
        setTextViewColor(timeDivider, SecondaryText)
        setTextViewColor(verificationDivider, SecondaryText)

        adjustPromotedNotificationTextFonts()

        if (notificationsRedesignTemplates()) {
            (mainColumn?.layoutParams as? MarginLayoutParams)?.let { mainColumnMargins ->
                mainColumnMargins.topMargin =
                    Notification.Builder.getContentMarginTop(
                        root.context,
                        R.dimen.notification_2025_content_margin_top,
                    )
            }
        }
    }

    fun update(content: PromotedNotificationContentModel, audiblyAlertedIconVisible: Boolean) {
        when (content.style) {
            Style.Base -> updateBase(content, collapsed = false)
            Style.CollapsedBase -> updateBase(content, collapsed = true)
            Style.BigPicture -> updateBigPictureStyle(content)
            Style.BigText -> updateBigTextStyle(content)
            Style.Call -> updateCallStyle(content, collapsed = false)
            Style.CollapsedCall -> updateCallStyle(content, collapsed = true)
            Style.Progress -> updateProgressStyle(content)
            Style.Ineligible -> {}
        }

        alertedIcon?.isVisible = audiblyAlertedIconVisible
    }

    private fun updateBase(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
        textView: ImageFloatingTextView? = text,
    ) {
        val headerTitleView = if (collapsed) title else null
        updateHeader(content, headerTitleView = headerTitleView, collapsed = collapsed)

        if (headerTitleView == null) {
            updateTitle(title, content)
        }
        updateText(textView, content)
        updateNotifIcon(icon, content.skeletonNotifIcon, content.iconLevel)
        updateRightIconAndSpacing(content.skeletonLargeIcon)
        updateOldProgressBar(content)
    }

    private fun updateBigPictureStyle(content: PromotedNotificationContentModel) {
        updateBase(content, collapsed = false)
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

    private fun updateHeader(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
        headerTitleView: TextView?,
    ) {
        val hasTitleInHeader = headerTitleView != null && content.title != null
        val hasSubText = content.subText != null

        // Determine if the notification has no content *below* the header/top line
        val hasTextBelowHeader = content.text != null
        val hasTitleBelowHeader = content.title != null && headerTitleView == null
        val isSingleLine = !hasTitleBelowHeader && !hasTextBelowHeader

        // the collapsed form doesn't show the app name unless there is no other text in the header
        val appNameRequired = !hasTitleInHeader && !hasSubText
        val hideAppName = (!appNameRequired && collapsed)

        // We're only showing the top line (e.g. for redacted notifs), so center it
        header?.centerTopLine(isSingleLine)
        // We normally use the (empty) actions container for the bottom padding of the notification,
        // but that's not necessary when single line
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
        val hasAppName = content.appName != null && !hideAppName
        val hasSubText = content.subText != null
        val hasHeader = content.title != null && !hideTitle
        val hasTimeOrChronometer = content.time != null

        val hasTextBeforeSubText = hasAppName
        val hasTextBeforeHeader = hasAppName || hasSubText
        val hasTextBeforeTime = hasAppName || hasSubText || hasHeader

        val showDividerBeforeSubText = hasTextBeforeSubText && hasSubText
        val showDividerBeforeHeader = hasTextBeforeHeader && hasHeader
        val showDividerBeforeTime = hasTextBeforeTime && hasTimeOrChronometer

        headerTextSecondaryDivider?.isVisible = showDividerBeforeSubText
        headerTextDivider?.isVisible = showDividerBeforeHeader
        timeDivider?.isVisible = showDividerBeforeTime
    }

    private fun updateConversationHeader(
        content: PromotedNotificationContentModel,
        collapsed: Boolean,
    ) {
        updateAppName(content, forceHide = collapsed)
        updateTimeAndChronometer(content)
        updateProfileBadge(content)

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
        val hasTitle = content.title != null && !hideTitle
        val hasAppName = content.appName != null && !hideAppName
        val hasTimeOrChronometer = content.time != null
        val hasVerification =
            !content.verificationIcon.isNullOrEmpty() || content.verificationText != null

        val hasTextBeforeAppName = hasTitle
        val hasTextBeforeTime = hasTitle || hasAppName
        val hasTextBeforeVerification = hasTitle || hasAppName || hasTimeOrChronometer

        val showDividerBeforeAppName = hasTextBeforeAppName && hasAppName
        val showDividerBeforeTime = hasTextBeforeTime && hasTimeOrChronometer
        val showDividerBeforeVerification = hasTextBeforeVerification && hasVerification

        appNameDivider?.isVisible = showDividerBeforeAppName
        timeDivider?.isVisible = showDividerBeforeTime
        verificationDivider?.isVisible = showDividerBeforeVerification
    }

    private fun updateConversationIcon(content: PromotedNotificationContentModel) {
        updateNotifIcon(conversationIcon, content.skeletonNotifIcon, content.iconLevel)
        (conversationIcon?.layoutParams as? MarginLayoutParams)?.let {
            it.bottomMargin = marginPx
            conversationIcon?.layoutParams = it
        }
    }

    private fun updateAppName(content: PromotedNotificationContentModel, forceHide: Boolean) {
        updateTextView(appNameText, content.appName?.takeUnless { forceHide })
    }

    private fun updateTitle(titleView: TextView?, content: PromotedNotificationContentModel) {
        updateTextView(titleView, content.title, color = PrimaryText)
    }

    private fun updateTimeAndChronometer(content: PromotedNotificationContentModel) {
        setTextViewColor(time, SecondaryText)
        setTextViewColor(chronometer, SecondaryText)

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

        time?.isVisible = (content.time is When.Time)
        chronometer?.isVisible = (content.time is When.Chronometer)
    }

    private fun updateRightIconAndSpacing(image: ImageModel?) {
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
        (title?.layoutParams as? MarginLayoutParams)?.let {
            it.marginEnd = spaceBasedOnRightIcon
            title.layoutParams = it
        }
        topLine?.headerTextMarginEnd = spaceBasedOnRightIcon
    }

    private fun calculateRightIconDimensions(drawable: Drawable?): Size {
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

    private fun updateProfileBadge(content: PromotedNotificationContentModel) {
        if (content.profileBadgeBitmap != null) {
            profileBadge?.setImageBitmap(content.profileBadgeBitmap)
            profileBadge?.visibility = VISIBLE
            profileBadge?.setColorFilter(PrimaryText.colorInt, PorterDuff.Mode.SRC_IN)
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
                smallIconView.setBackgroundColor(Background.colorInt)
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
        view?.setNumIndentLines(if (content.title != null) 0 else 1)
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
        view.setImageDrawable(drawable)
        view.isVisible = drawable != null
    }

    private fun setTextViewColor(view: TextView?, color: AodPromotedNotificationColor) {
        view?.setTextColor(color.colorInt)
    }

    private fun adjustPromotedNotificationTextFonts() {
        adjustTextViewFont(appNameDivider)
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
    }

    private fun getNotificationTypeFace(context: Context): Typeface? =
        try {
            val defaultFontFamily =
                if (notificationsRedesignTemplates()) {
                    FontStyles.GSF_BODY_MEDIUM
                } else {
                    context.resources.getString(R.string.config_bodyFontFamily)
                }
            Typeface.create(defaultFontFamily, Typeface.NORMAL)
        } catch (throwable: Throwable) {
            Log.wtf(TAG, "Font is not found for Promoted Notifications")
            null
        }

    private fun adjustTextViewFont(view: TextView?) {
        view?.setTypeface(defaultTypeface, Typeface.NORMAL)
    }

    companion object {
        /** Maximum aspect ratio of the large icon. 16:9 */
        private const val MAX_LARGE_ICON_ASPECT_RATIO: Float = 16f / 9f
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

@Composable
private fun scaledFontHeight(@DimenRes dimenId: Int): Dp {
    return dimensionResource(dimenId) * LocalDensity.current.fontScale.coerceAtLeast(1f)
}

private val viewUpdaterTagId = systemuiR.id.aod_promoted_notification_view_updater_tag
private val viewInflationIdentity = systemuiR.id.aod_promoted_notification_inflation_identity

private const val TAG = "AODPromotedNotification"
