/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification.row

import android.annotation.ColorInt
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.testing.TestableLooper.RunWithLooper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.animation.Interpolators
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.FakeShadowView
import com.android.systemui.statusbar.notification.NotificationUtils
import com.android.systemui.statusbar.notification.SourceType
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ActivatableNotificationViewTest : SysuiTestCase() {
    private val mContentView: View = mock()
    private lateinit var mView: ActivatableNotificationView

    @ColorInt private var mNormalColor = 0

    private val finalWidth = 1000
    private val finalHeight = 200

    @Before
    fun setUp() {
        mView =
            object : ActivatableNotificationView(mContext, null) {

                init {
                    onFinishInflate()
                }

                override fun getContentView(): View {
                    return mContentView
                }

                override fun <T : View> findViewTraversal(id: Int): T? =
                    when (id) {
                        R.id.backgroundNormal -> mock<NotificationBackgroundView>()
                        R.id.fake_shadow -> mock<FakeShadowView>()
                        else -> null
                    }
                        as T?
            }

        mNormalColor =
            mContext.getColor(com.android.internal.R.color.materialColorSurfaceContainerHigh)
    }

    @Test
    fun testBackgroundBehaviors() {
        // Color starts with the normal color
        mView.updateBackgroundColors()
        if (Flags.notificationRowTransparency()) {
            mNormalColor = mView.currentBackgroundTint
        }
        assertThat(mView.currentBackgroundTint).isEqualTo(mNormalColor)

        // Setting a tint changes the background to that color specifically
        mView.setTintColor(Color.BLUE)
        assertThat(mView.currentBackgroundTint).isEqualTo(Color.BLUE)

        // Setting an override tint blends with the previous tint
        mView.setOverrideTintColor(Color.RED, 0.5f)
        assertThat(mView.currentBackgroundTint)
            .isEqualTo(NotificationUtils.interpolateColors(Color.BLUE, Color.RED, 0.5f))

        // Updating the background colors resets tints, as those won't match the latest theme
        mView.updateBackgroundColors()
        assertThat(mView.currentBackgroundTint).isEqualTo(mNormalColor)
    }

    @Test
    fun roundnessShouldBeTheSame_after_onDensityOrFontScaleChanged() {
        val roundableState = mView.roundableState
        assertThat(mView.topRoundness).isEqualTo(0f)
        mView.requestTopRoundness(1f, SourceType.from(""))
        assertThat(mView.topRoundness).isEqualTo(1f)

        mView.onDensityOrFontScaleChanged()

        assertThat(mView.topRoundness).isEqualTo(1f)
        assertThat(mView.roundableState.hashCode()).isEqualTo(roundableState.hashCode())
    }

    @Test
    fun getBackgroundBottom_respects_clipBottomAmount() {
        mView.actualHeight = 100
        assertThat(mView.backgroundBottom).isEqualTo(100)

        mView.clipBottomAmount = 10
        assertThat(mView.backgroundBottom).isEqualTo(90)

    }

    @Test
    fun updateAppearRect_forClipSideBottom_atAnimationStart_setsLocalZeroHeightOutline() {
        // Set state for start of animation
        mView.mTargetPoint = null
        mView.appearAnimationFraction = 0.0f
        mView.setCurrentAppearInterpolator(Interpolators.LINEAR)

        // Call method under test
        mView.updateAppearRect(ExpandableView.ClipSide.BOTTOM, finalWidth, finalHeight)

        // Assert that outline is zero-height rect at local top
        val outline = mock<Outline>()
        mView.outlineProvider.getOutline(mView, outline)

        verify(outline).setPath(argThat { pathArgument: Path -> pathArgument.isEmpty })
    }

    @Test
    fun updateAppearRect_forClipSideBottom_midAnimation_setsLocalPartialHeightOutline() {
        // Set state for mid animation
        val fraction = 0.5f
        mView.mTargetPoint = null
        mView.appearAnimationFraction = fraction
        mView.setCurrentAppearInterpolator(Interpolators.LINEAR)

        // Call method under test
        mView.updateAppearRect(ExpandableView.ClipSide.BOTTOM, finalWidth, finalHeight)

        // Assert that outline has a partial height based on the fraction.
        val outline = mock<Outline>()
        mView.outlineProvider.getOutline(mView, outline)

        verifyOutline(
            outline,
            expectedLeft = 0f,
            expectedTop = 0f,
            expectedRight = finalWidth.toFloat(),
            expectedBottom = finalHeight * fraction,
        )
    }

    @Test
    fun updateAppearRect_forClipSideBottom_atAnimationEnd_setsLocalFullHeightOutline() {
        // Set state for end of animation
        mView.mTargetPoint = null
        mView.appearAnimationFraction = 1.0f
        mView.setCurrentAppearInterpolator(Interpolators.LINEAR)

        // Call method under test
        mView.updateAppearRect(ExpandableView.ClipSide.BOTTOM, finalWidth, finalHeight)

        // Assert that outline has the full final height
        val outline = mock<Outline>()
        mView.outlineProvider.getOutline(mView, outline)

        verifyOutline(
            outline,
            expectedLeft = 0f,
            expectedTop = 0f,
            expectedRight = finalWidth.toFloat(),
            expectedBottom = finalHeight.toFloat(),
        )
    }

    /** Helper to verify the bounds of a Path set on an Outline. */
    private fun verifyOutline(
        outline: Outline,
        expectedLeft: Float,
        expectedTop: Float,
        expectedRight: Float,
        expectedBottom: Float,
    ) {
        verify(outline)
            .setPath(
                argThat { pathArgument: Path ->
                    val bounds = RectF()
                    pathArgument.computeBounds(bounds, /* exact= */ true)
                    bounds.left == expectedLeft &&
                        bounds.top == expectedTop &&
                        bounds.right == expectedRight &&
                        bounds.bottom == expectedBottom
                }
            )
    }
}
