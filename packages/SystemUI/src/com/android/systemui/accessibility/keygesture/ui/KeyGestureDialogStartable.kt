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

package com.android.systemui.accessibility.keygesture.ui

import android.annotation.StringRes
import android.content.ComponentName
import android.content.Context
import android.hardware.input.KeyGestureEvent
import android.text.Annotation
import android.text.Spanned
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.internal.R as InternalR
import com.android.internal.accessibility.util.AccessibilityUtils
import com.android.internal.accessibility.util.TtsPrompt
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.shared.model.DialogContentSection
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class KeyGestureDialogStartable
@Inject
constructor(
    private val context: Context,
    private val displayRepository: DisplayRepository,
    private val interactor: KeyGestureDialogInteractor,
    private val dialogFactory: SystemUIDialogFactory,
    @Application private val mainScope: CoroutineScope,
) : CoreStartable {
    @VisibleForTesting var currentDialog: ComponentSystemUIDialog? = null

    @VisibleForTesting var dialogType: Int = 0

    /**
     * Defines a strategy for handling the specific behaviors of different key gesture confirmation
     * dialogs. This allows `createDialog` to be agnostic to the specific logic of each dialog type.
     */
    private interface DialogBehaviorDelegate {
        @get:StringRes val negativeButtonTextId: Int

        @get:StringRes val positiveButtonTextId: Int

        /** Called after the dialog is created but before it's shown. */
        fun onDialogCreated(info: KeyGestureConfirmInfo) {}

        /** Called when the positive button is clicked. */
        fun onPositiveButtonClick(info: KeyGestureConfirmInfo)

        /** Called when the dialog is canceled. */
        fun onDialogCanceled(info: KeyGestureConfirmInfo) {}

        /** Called when the dialog is dismissed for any reason. */
        fun onDialogDismissed(info: KeyGestureConfirmInfo) {}
    }

    /**
     * A base class for dialog delegates that handles the most common behavior: enabling a shortcut
     * when the positive button is clicked and using standard button texts.
     */
    private abstract class BaseDialogDelegate(
        protected val interactor: KeyGestureDialogInteractor
    ) : DialogBehaviorDelegate {
        override val negativeButtonTextId: Int =
            R.string.accessibility_key_gesture_shortcut_not_yet_enabled_negative_button_text
        override val positiveButtonTextId: Int =
            R.string.accessibility_key_gesture_shortcut_not_yet_enabled_positive_button_text

        override fun onPositiveButtonClick(info: KeyGestureConfirmInfo) {
            interactor.enableShortcutsForTargets(enable = true, info.targetName)
        }
    }

    /**
     * Delegate for standard keyboard shortcuts (e.g., Select to Speak) that only require the common
     * "enable" action.
     */
    private class DefaultDialogDelegate(interactor: KeyGestureDialogInteractor) :
        BaseDialogDelegate(interactor)

    /**
     * Delegate for the screen reader shortcut, which extends the base behavior by adding a
     * Text-to-Speech prompt for accessibility.
     */
    private class ScreenReaderDialogDelegate(
        private val context: Context,
        interactor: KeyGestureDialogInteractor,
    ) : BaseDialogDelegate(interactor) {
        private var ttsPrompt: TtsPrompt? = null

        override fun onDialogCreated(info: KeyGestureConfirmInfo) {
            ComponentName.unflattenFromString(info.targetName)
                ?.flattenToString()
                ?.takeUnless { AccessibilityUtils.isAccessibilityServiceEnabled(context, it) }
                ?.let {
                    info.ttsText?.let { text ->
                        ttsPrompt = interactor.createTtsPromptForText(text)
                    }
                }

            interactor.enableShortcutsForTargets(enable = true, info.targetName)
        }

        override fun onPositiveButtonClick(info: KeyGestureConfirmInfo) {}

        override fun onDialogCanceled(info: KeyGestureConfirmInfo) {
            // We need to remove the shortcut target if the user clicks the negative
            // button or clicks outside of the dialog.
            interactor.enableShortcutsForTargets(enable = false, info.targetName)
        }

        override fun onDialogDismissed(info: KeyGestureConfirmInfo) {
            ttsPrompt?.dismiss()
        }
    }

    /**
     * Delegate for the magnification shortcut which turns on the magnification shortcuts and zoom
     * in automatically
     */
    private class MagnifyMagnificationDialogDelegate(interactor: KeyGestureDialogInteractor) :
        BaseDialogDelegate(interactor) {
        override val negativeButtonTextId: Int =
            R.string.accessibility_key_gesture_magnification_dialog_negative_button_text
        override val positiveButtonTextId: Int =
            R.string.accessibility_key_gesture_magnification_dialog_positive_button_text

        override fun onDialogCreated(info: KeyGestureConfirmInfo) {
            interactor.enableShortcutsForTargets(enable = true, info.targetName)
            interactor.enableMagnificationAndZoomIn(info.displayId)
        }

        override fun onPositiveButtonClick(info: KeyGestureConfirmInfo) {}

        override fun onDialogCanceled(info: KeyGestureConfirmInfo) {
            // We need to remove the shortcut target if the user clicks the negative
            // button or clicks outside of the dialog.
            interactor.enableShortcutsForTargets(enable = false, info.targetName)
        }
    }

    private fun getDialogDelegate(
        dialogContext: Context,
        keyGestureType: Int,
    ): DialogBehaviorDelegate {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                MagnifyMagnificationDialogDelegate(interactor)

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                ScreenReaderDialogDelegate(dialogContext, interactor)

            else -> DefaultDialogDelegate(interactor)
        }
    }

    override fun start() {
        mainScope.launch {
            interactor.keyGestureConfirmDialogRequest.collectLatest { requestPair ->
                processDialogRequest(requestPair)
            }
        }
    }

    private fun processDialogRequest(requestPair: Pair<Boolean, KeyGestureConfirmInfo?>) {
        if (requestPair.first) {
            createDialog(requestPair.second)
            return
        }

        dismissDialog(requestPair.second)
    }

    private fun dismissDialog(keyGestureConfirmInfo: KeyGestureConfirmInfo?) {
        if (dialogType == keyGestureConfirmInfo?.keyGestureType) {
            currentDialog?.dismiss()
        }
    }

    @VisibleForTesting
    fun createDialog(keyGestureConfirmInfo: KeyGestureConfirmInfo?) {
        // Ignore other type of first-time keyboard shortcuts while there is an existing dialog.
        // `currentDialog` will be reset when the dialog dismissal listener is called, which will be
        // executed asynchronously. Thus, to avoid race condition, we should check the nullable of
        // this value to determine if the current dialog is fully finished.
        if (currentDialog != null) {
            return
        }

        if (keyGestureConfirmInfo == null) {
            return
        }

        var dialogContext = context

        if (context.displayId != keyGestureConfirmInfo.displayId) {
            val displayToMagnifyId = keyGestureConfirmInfo.displayId
            val displayToMagnify = displayRepository.getDisplay(displayToMagnifyId)
            if (displayToMagnify == null) {
                Log.w(
                    TAG,
                    "Unable to open dialog on display $displayToMagnifyId: display not found - " +
                        "will fallback to default display",
                )
            } else {
                // Use the display context here so that the dialog shows up on the correct display
                dialogContext = context.createDisplayContext(displayToMagnify)
            }
        }
        val delegate = getDialogDelegate(dialogContext, keyGestureConfirmInfo.keyGestureType)

        currentDialog =
            dialogFactory.create(context = dialogContext) { dialog ->
                PlatformTheme {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isFocusRingEnabled =
                        Flags.enableInsetFocusRings() &&
                            booleanResource(InternalR.bool.config_enableInsetFocusRingsInSuw)

                    AlertDialogContent(
                        title = { Text(text = keyGestureConfirmInfo.title) },
                        content = {
                            Column {
                                TextWithIcon(
                                    keyGestureConfirmInfo.contentText,
                                    keyGestureConfirmInfo.actionKeyIconResId,
                                    textAlign =
                                        if (keyGestureConfirmInfo.contentSections.isNotEmpty()) {
                                            TextAlign.Start
                                        } else {
                                            null
                                        },
                                )
                                if (keyGestureConfirmInfo.contentSections.isNotEmpty()) {
                                    FormattedDialogContent(
                                        sections = keyGestureConfirmInfo.contentSections
                                    )
                                }
                            }
                        },
                        negativeButton = {
                            PlatformOutlinedButton(
                                modifier =
                                    if (isFocusRingEnabled) {
                                        Modifier.borderOnFocus(
                                            // TODO(b/501501800): revert the changes for
                                            // borderOnFocus in ag/39299067 and move the modifier to
                                            // PlatformOutlinedButton.
                                            color = MaterialTheme.colorScheme.primary,
                                            cornerSize = FOCUS_RING_CORNER_SIZE,
                                            strokeWidth = FOCUS_RING_STROKE_WIDTH,
                                            paddingHorizontal = FOCUS_RING_PADDING_HORIZONTAL,
                                            paddingVertical = FOCUS_RING_PADDING_VERTICAL,
                                        )
                                    } else {
                                        Modifier
                                    },
                                onClick = {
                                    // We need explicitly call `cancel` here; otherwise, clicking
                                    // the negative button, the cancel listener won't be triggered.
                                    dialog.cancel()
                                },
                            ) {
                                Text(stringResource(id = delegate.negativeButtonTextId))
                            }
                        },
                        positiveButton = {
                            PlatformButton(
                                modifier =
                                    if (isFocusRingEnabled) {
                                        // TODO(b/501501800): revert the changes for borderOnFocus
                                        // in ag/39299067 and move the modifier to PlatformButton.
                                        Modifier.borderOnFocus(
                                            color = MaterialTheme.colorScheme.primary,
                                            cornerSize = FOCUS_RING_CORNER_SIZE,
                                            strokeWidth = FOCUS_RING_STROKE_WIDTH,
                                            paddingHorizontal = FOCUS_RING_PADDING_HORIZONTAL,
                                            paddingVertical = FOCUS_RING_PADDING_VERTICAL,
                                        )
                                    } else {
                                        Modifier
                                    },
                                onClick = {
                                    delegate.onPositiveButtonClick(keyGestureConfirmInfo)
                                    dialog.dismiss()
                                },
                            ) {
                                Text(stringResource(id = delegate.positiveButtonTextId))
                            }
                        },
                    )
                }
            }

        currentDialog?.let { dialog ->
            dialogType = keyGestureConfirmInfo.keyGestureType
            delegate.onDialogCreated(keyGestureConfirmInfo)
            dialog.setOnCancelListener { delegate.onDialogCanceled(keyGestureConfirmInfo) }
            dialog.setOnDismissListener {
                delegate.onDialogDismissed(keyGestureConfirmInfo)
                currentDialog = null
            }
            dialog.show()
        }
    }

    @Composable
    private fun FormattedDialogContent(sections: List<DialogContentSection>) {
        Column {
            sections.forEach { section ->
                Spacer(modifier = Modifier.height(16.dp))
                section.heading?.let {
                    Text(
                        text = it.toString(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                section.message?.let {
                    Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    private fun buildAnnotatedStringFromResource(resourceText: CharSequence): AnnotatedString {
        // `resourceText` is an instance of SpannableStringBuilder, so we can cast it to a Spanned.
        val spanned = resourceText as? Spanned ?: return AnnotatedString(resourceText.toString())

        // get all the annotation spans from the text
        val annotations = spanned.getSpans(0, spanned.length, Annotation::class.java)

        return buildAnnotatedString {
            var startIndex = 0
            for (annotationSpan in annotations) {
                if (annotationSpan.key == "id" && annotationSpan.value == "action_key_icon") {
                    val annotationStart = spanned.getSpanStart(annotationSpan)
                    val annotationEnd = spanned.getSpanEnd(annotationSpan)
                    append(spanned.substring(startIndex, annotationStart))
                    appendInlineContent(ICON_INLINE_CONTENT_ID)
                    startIndex = annotationEnd
                }
            }

            if (startIndex < spanned.length) {
                append(spanned.substring(startIndex))
            }
        }
    }

    @Composable
    private fun TextWithIcon(
        text: CharSequence,
        modifierKeyIconResId: Int,
        textAlign: TextAlign? = null,
    ) {
        // TODO: b/419026315 - Update the icon drawable based on keyboard device.
        val inlineContentMap =
            mapOf(
                ICON_INLINE_CONTENT_ID to
                    InlineTextContent(
                        Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        Icon(
                            painter = painterResource(modifierKeyIconResId),
                            contentDescription = null, // decorative icon
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            )

        Text(
            buildAnnotatedStringFromResource(text),
            inlineContent = inlineContentMap,
            textAlign = textAlign,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
    }

    companion object {
        const val TAG = "KeyGestureDialogStartable"
        const val ICON_INLINE_CONTENT_ID = "iconInlineContentId"

        private val FOCUS_RING_CORNER_SIZE = CornerSize(28.dp)
        private val FOCUS_RING_STROKE_WIDTH = 2.dp
        private val FOCUS_RING_PADDING_HORIZONTAL = 3.dp
        private val FOCUS_RING_PADDING_VERTICAL = (-3).dp
    }
}
