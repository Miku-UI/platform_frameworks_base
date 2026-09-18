/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED
import android.platform.test.annotations.RequiresFlagsEnabled
import android.testing.TestableLooper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.IconCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.internal.logging.UiEventLogger
import com.android.media.flags.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.dialogTransitionAnimator
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class MediaOutputDialogDelegateTest : SysuiTestCase() {

    private val mediaOutputAdapter = mock<MediaOutputAdapter>()
    private val broadcastSender = mock<BroadcastSender>()
    private val uiEventLogger = mock<UiEventLogger>()

    private lateinit var mediaSwitchingController: MediaSwitchingController
    private lateinit var dialogTransitionAnimator: DialogTransitionAnimator
    private lateinit var systemUIDialogFactory: SystemUIDialog.Factory
    private lateinit var mediaOutputDialogDelegate: MediaOutputDialogDelegate

    @Before
    fun setUp() {
        val mockedColorScheme = mock<MediaOutputColorScheme>()
        mediaSwitchingController =
            mock<MediaSwitchingController>() {
                on { getColorScheme() } doReturn mockedColorScheme
                on { getStopButtonStringRes() } doReturn
                    R.string.media_output_dialog_button_stop_casting
            }

        val kosmos = testKosmos()
        dialogTransitionAnimator = kosmos.dialogTransitionAnimator
        systemUIDialogFactory = kosmos.systemUIDialogDotFactory
    }

    @Test
    fun onCreate_noAppOpenIntent_metadataSectionNonClickable() {
        mediaSwitchingController.stub { on { getAppLaunchIntent() } doReturn null }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())
        val mediaMetadataSectionLayout =
            mediaOutputDialogDelegate.mDialogView.requireViewById<LinearLayout>(
                R.id.media_metadata_section
            )

        assertThat(mediaMetadataSectionLayout.isClickable).isFalse()
    }

    @Test
    fun onCreate_appOpenIntentAvailable_metadataSectionClickable() {
        mediaSwitchingController.stub { on { getAppLaunchIntent() } doReturn Intent(TEST_PACKAGE) }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())
        val mediaMetadataSectionLayout =
            mediaOutputDialogDelegate.mDialogView.requireViewById<LinearLayout>(
                R.id.media_metadata_section
            )

        assertThat(mediaMetadataSectionLayout.isClickable).isTrue()
    }

    @Test
    fun refresh_withIconCompat_iconIsVisible() {
        mediaSwitchingController.stub {
            on { getHeaderIcon() } doReturn
                IconCompat.createWithBitmap(
                    Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888)
                )
        }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val view =
            mediaOutputDialogDelegate.mDialogView.requireViewById<ImageView>(R.id.header_icon)

        assertThat(view.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun refresh_noIcon_iconLayoutNotVisible() {
        mediaSwitchingController.stub { on { getHeaderIcon() } doReturn null }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val view =
            mediaOutputDialogDelegate.mDialogView.requireViewById<ImageView>(R.id.header_icon)

        assertThat(view.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun refresh_checkTitle() {
        val headerTitle = "test_string"
        mediaSwitchingController.stub { on { getHeaderTitle() } doReturn headerTitle }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val titleView =
            mediaOutputDialogDelegate.mDialogView.requireViewById<TextView>(R.id.header_title)

        assertThat(titleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(titleView.text.toString()).isEqualTo(headerTitle)
    }

    @Test
    fun refresh_withSubtitle_checkSubtitle() {
        val headerSubtitle = "test_string"
        mediaSwitchingController.stub { on { getHeaderSubTitle() } doReturn headerSubtitle }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val subtitleView =
            mediaOutputDialogDelegate.mDialogView.requireViewById<TextView>(R.id.header_subtitle)

        assertThat(subtitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(subtitleView.text.toString()).isEqualTo(headerSubtitle)
    }

    @Test
    fun refresh_noSubtitle_checkSubtitle() {
        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val subtitleView =
            mediaOutputDialogDelegate.mDialogView.requireViewById<TextView>(R.id.header_subtitle)

        assertThat(subtitleView.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun refresh_inDragging_notUpdateAdapter() {
        mediaOutputAdapter.stub { on { isDragging() } doReturn true }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.mAdapter = mediaOutputAdapter
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        verify(mediaOutputAdapter, never()).notifyDataSetChanged()
    }

    @Test
    fun refresh_inDragging_directSetRefreshingToFalse() {
        mediaOutputAdapter.stub { on { isDragging() } doReturn true }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.mAdapter = mediaOutputAdapter
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        assertThat(mediaSwitchingController.isRefreshing()).isFalse()
    }

    @Test
    fun refresh_notInDragging_verifyUpdateAdapter() {
        mediaOutputAdapter.stub {
            on { getCurrentActivePosition() } doReturn -1
            on { isDragging() } doReturn false
        }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.mAdapter = mediaOutputAdapter
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        verify(mediaOutputAdapter).updateItems()
    }

    @Test
    fun dismissDialog_closesDialogByBroadcastSender() {
        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.dismissDialog()

        verify(broadcastSender).closeSystemDialogs()
    }

    @Test
    fun refresh_checkStopText() {
        val stopResId = R.string.media_output_dialog_button_stop_casting
        mediaSwitchingController.stub { on { getStopButtonStringRes() } doReturn stopResId }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()
        val stop = mediaOutputDialogDelegate.mDialogView.requireViewById<Button>(R.id.stop)

        assertThat(stop.text.toString()).isEqualTo(mContext.getString(stopResId))
    }

    @Test
    fun onCreate_normalScreenHeight_showsNormalIconAndMetadata() {
        mediaSwitchingController.stub { on { getAppIcon() } doReturn BitmapDrawable() }
        mContext.resources.configuration.screenHeightDp = 500

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon_small_screen_height)
                    .visibility
            )
            .isEqualTo(View.GONE)
        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon)
                    .visibility
            )
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun onCreate_smallScreenHeight_showsSmallIconAndHidesMetadata() {
        mediaSwitchingController.stub { on { getAppIcon() } doReturn BitmapDrawable() }
        mContext.resources.configuration.screenHeightDp = 300

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon)
                    .visibility
            )
            .isEqualTo(View.GONE)
        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon_small_screen_height)
                    .visibility
            )
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun refresh_fromNormalToSmallScreenHeight_showsSmallIcon() {
        mediaSwitchingController.stub { on { getAppIcon() } doReturn BitmapDrawable() }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mContext.resources.configuration.screenHeightDp = 500
        mediaOutputDialogDelegate.refresh()
        mContext.resources.configuration.screenHeightDp = 300
        mediaOutputDialogDelegate.refresh()

        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon)
                    .visibility
            )
            .isEqualTo(View.GONE)
        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon_small_screen_height)
                    .visibility
            )
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun refresh_fromSmallToNormalScreenHeight_showsNormalIcon() {
        mediaSwitchingController.stub { on { getAppIcon() } doReturn BitmapDrawable() }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mContext.resources.configuration.screenHeightDp = 300
        mediaOutputDialogDelegate.refresh()
        mContext.resources.configuration.screenHeightDp = 500
        mediaOutputDialogDelegate.refresh()

        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon_small_screen_height)
                    .visibility
            )
            .isEqualTo(View.GONE)
        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.app_source_icon)
                    .visibility
            )
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun onStopButtonClick_releaseSession() {
        spyOn(dialogTransitionAnimator)
        mediaSwitchingController.stub {
            on { getStopButtonStringRes() } doReturn
                R.string.media_output_dialog_button_stop_casting
        }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.onStopButtonClick()

        verify(mediaSwitchingController).releaseSession()
        verify(dialogTransitionAnimator).disableAllCurrentDialogsExitAnimations()
    }

    @Test
    fun onCreate_ShouldLogVisibility() {
        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        verify(uiEventLogger)
            .log(MediaOutputDialogDelegate.MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun refresh_noMissingPermissionsWarning_warningSectionGone() {
        mediaSwitchingController.stub { on { getMissingPermissionsWarning() } doReturn null }

        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        mediaOutputDialogDelegate.refresh()

        assertThat(
                mediaOutputDialogDelegate.mDialogView
                    .requireViewById<View>(R.id.warning_section)
                    .visibility
            )
            .isEqualTo(View.GONE)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun onWarningFixButtonClick_callsController() {
        mediaOutputDialogDelegate = createDelegate()
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())
        mediaSwitchingController.stub {
            on { getMissingPermissionsWarning() } doReturn MissingPermissionsWarning("Test App")
        }

        mediaOutputDialogDelegate.refresh()
        mediaOutputDialogDelegate.mDialogView
            .requireViewById<View>(R.id.warning_fix_button)
            .performClick()

        verify(mediaSwitchingController).tryToLaunchMissingPermissionsResolveIntent()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun refresh_withoutUsingSystemColors_updatesColorScheme() {
        mediaOutputDialogDelegate = createDelegate(useSystemColors = false)
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        val icon =
            IconCompat.createWithBitmap(
                Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888)
            )
        mediaSwitchingController.stub { on { getHeaderIcon() } doReturn icon }

        mediaOutputDialogDelegate.refresh()

        verify(mediaSwitchingController).updateCurrentColorScheme(any(), any())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun refresh_withUsingSystemColors_doesNotUpdateColorScheme() {
        mediaOutputDialogDelegate = createDelegate(useSystemColors = true)
        val dialog = mediaOutputDialogDelegate.createDialog()
        mediaOutputDialogDelegate.onCreate(dialog, Bundle())

        val icon =
            IconCompat.createWithBitmap(
                Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888)
            )
        mediaSwitchingController.stub { on { getHeaderIcon() } doReturn icon }

        mediaOutputDialogDelegate.refresh()

        verify(mediaSwitchingController, never()).updateCurrentColorScheme(any(), any())
    }

    private fun createDelegate(useSystemColors: Boolean = true): MediaOutputDialogDelegate =
        MediaOutputDialogDelegate(
            false,
            mediaSwitchingController,
            true,
            null,
            useSystemColors,
            mContext,
            broadcastSender,
            uiEventLogger,
            systemUIDialogFactory,
        )

    companion object {
        private const val TEST_PACKAGE = "test_package"
    }
}
