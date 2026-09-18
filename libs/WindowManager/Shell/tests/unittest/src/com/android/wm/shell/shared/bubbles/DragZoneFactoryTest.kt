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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.Companion.EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_SMALL_TABLET_DP
import com.android.wm.shell.shared.bubbles.DragZoneFactory.Companion.EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_TABLET_DP
import com.android.wm.shell.shared.bubbles.DragZoneFactory.DesktopWindowModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private typealias DragZoneVerifier = (dragZone: DragZone) -> Unit

@SmallTest
@EnableFlags(FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
/** Unit tests for [DragZoneFactory]. */
class DragZoneFactoryTest {

    @get:Rule val flagsRule = SetFlagsRule()

    private val context = getApplicationContext<Context>()
    private lateinit var dragZoneFactory: DragZoneFactory
    private val tabletPortrait =
        DeviceConfig(
            windowBounds = Rect(0, 0, 1400, 2000),
            isLargeScreen = true,
            isSmallTablet = false,
            isLandscape = false,
            isRtl = false,
            insets = Insets.of(0, 24, 0, 0),
        )
    private val tabletLandscape =
        tabletPortrait.copy(windowBounds = Rect(0, 0, 2000, 1400), isLandscape = true)
    private val foldablePortrait =
        tabletPortrait.copy(windowBounds = Rect(0, 0, 1000, 1200), isSmallTablet = true)
    private val foldableLandscape =
        foldablePortrait.copy(windowBounds = Rect(0, 0, 1200, 1000), isLandscape = true)
    private var splitScreenMode = SplitScreenMode.NONE
    private val splitScreenModeChecker = SplitScreenModeChecker { splitScreenMode }
    private var isDesktopWindowModeSupported = true
    private val desktopWindowModeChecker = DesktopWindowModeChecker { isDesktopWindowModeSupported }
    private val bubbleBarPropertiesProvider =
        object : BubbleBarPropertiesProvider {
            override fun getHeight() = 80

            override fun getWidth() = 100

            override fun getBottomPadding() = 50
        }

    @Test
    fun dragZonesForBubbleBar_tablet() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.BubbleBar(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_tablet_portrait() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_tablet_landscape() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_foldable_portrait() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldablePortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_foldable_portrait_splitUnsupported() {
        splitScreenMode = SplitScreenMode.UNSUPPORTED
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldablePortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_foldable_landscape() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForExpandedView_tablet_portrait() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_TABLET_DP)
        }
    }

    @Test
    fun dragZonesForExpandedView_tablet_landscape() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_TABLET_DP)
        }
    }

    @Test
    fun dragZonesForExpandedView_tablet_landscape_splitUnsupported() {
        splitScreenMode = SplitScreenMode.UNSUPPORTED
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_TABLET_DP)
        }
    }

    @Test
    fun dragZonesForExpandedView_foldable_portrait() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldablePortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_SMALL_TABLET_DP)
        }
    }

    @Test
    fun dragZonesForExpandedView_foldable_landscape() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(EXPANDED_VIEW_DROP_TARGET_HEIGHT_LIMIT_SMALL_TABLET_DP)
        }
    }

    @Test
    fun dragZonesForExpandedView_shortFoldable_landscapeExpandedDropTargetOccupiesAvailableSpace() {
        val screenHeight = 800
        dragZoneFactory =
            createDragZoneFactory(
                deviceConfig = foldableLandscape.copy(windowBounds = Rect(0, 0, 1200, screenHeight))
            )
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )

        // available space is equal to:
        // screenHeight(800) - bubbleBarBottomHeight (80 + 50)
        // - EXPANDED_VIEW_BUBBLE_BAR_SPACING_DP (32) - EXPANDED_VIEW_STATUS_BAR_SPACING_DP(60)
        // - topInset (24)
        val availableSpace = 554
        dragZones.getExpandedViewDropTargetHeights().forEach {
            assertThat(it).isEqualTo(availableSpace)
        }
    }

    @Test
    fun dragZonesForBubble_desktopModeDisabled() {
        isDesktopWindowModeSupported = false
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        assertThat(dragZones.filterIsInstance<DragZone.DesktopWindow>()).isEmpty()
    }

    @Test
    fun dragZonesForExpandedView_desktopModeDisabled() {
        isDesktopWindowModeSupported = false
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        assertThat(dragZones.filterIsInstance<DragZone.DesktopWindow>()).isEmpty()
    }

    @Test
    fun dragZonesForBubble_splitScreenModeUnsupported() {
        splitScreenMode = SplitScreenMode.UNSUPPORTED
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        assertThat(dragZones.filterIsInstance<DragZone.Split>()).isEmpty()
    }

    @Test
    fun dragZonesForExpandedView_splitScreenModeUnsupported() {
        splitScreenMode = SplitScreenMode.UNSUPPORTED
        dragZoneFactory = createDragZoneFactory(deviceConfig = foldableLandscape)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        assertThat(dragZones.filterIsInstance<DragZone.Split>()).isEmpty()
    }

    @Test
    fun dragZonesForLauncherIcon_bubbleBarHasBubbles() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(verifyInstance<DragZone.Bubble.Left>(), verifyInstance<DragZone.Bubble.Right>())
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) ->
            instanceVerifier(zone)
            zone.verifySecondaryDropZone(isPresent = false)
        }
    }

    @Test
    fun dragZonesForLauncherIcon_bubbleBarHasNoBubbles() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(verifyInstance<DragZone.Bubble.Left>(), verifyInstance<DragZone.Bubble.Right>())
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) ->
            instanceVerifier(zone)
            zone.verifySecondaryDropZone(isPresent = true)
        }
    }

    @Test
    fun dragZonesForLauncherIcon_bubbleBarHasNoBubblesDoNotShowDropTarget() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.LauncherIcon(
                    showExpandedViewDropTarget = false,
                    showBubbleBarPillowDropTarget = true,
                )
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(verifyInstance<DragZone.Bubble.Left>(), verifyInstance<DragZone.Bubble.Right>())
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) ->
            instanceVerifier(zone)
            zone.verifyDropZone(isPresent = false)
            zone.verifySecondaryDropZone(isPresent = true)
        }
    }

    @Test
    fun dragExpandedView_expandedViewDropTarget_shouldBeAboveBubbleBar() {
        dragZoneFactory = createDragZoneFactory(deviceConfig = tabletPortrait)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val bubbleZone = dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
        val bubbleBarTopPosition =
            tabletPortrait.windowBounds.bottom -
                bubbleBarPropertiesProvider.getBubbleBarTopFromScreenBottom()
        assertThat(bubbleZone.dropTarget!!.rect.bottom).isLessThan(bubbleBarTopPosition)
    }

    private inline fun <reified T> verifyInstance(): DragZoneVerifier = { dragZone ->
        assertThat(dragZone).isInstanceOf(T::class.java)
    }

    private fun DragZone.verifyDropZone(isPresent: Boolean) {
        assertThat(dropTarget != null == isPresent).isTrue()
    }

    private fun DragZone.verifySecondaryDropZone(isPresent: Boolean) {
        assertThat(secondDropTarget != null == isPresent).isTrue()
    }

    private fun createDragZoneFactory(
        context: Context = this.context,
        deviceConfig: DeviceConfig,
        splitScreenModeChecker: SplitScreenModeChecker = this.splitScreenModeChecker,
        desktopWindowModeChecker: DesktopWindowModeChecker = this.desktopWindowModeChecker,
        bubbleBarPropertiesProvider: BubbleBarPropertiesProvider = this.bubbleBarPropertiesProvider,
        dpToPxResolver: (Int, Context) -> Int = { dp, _ -> dp }, // in tests dp will be equals to px
    ): DragZoneFactory {
        return DragZoneFactory(
            context,
            deviceConfig,
            splitScreenModeChecker,
            desktopWindowModeChecker,
            bubbleBarPropertiesProvider,
            dpToPxResolver,
        )
    }

    private fun List<DragZone>.getExpandedViewDropTargetHeights(): List<Int> =
        filter { it is DragZone.Bubble.Left || it is DragZone.Bubble.Right }
            .mapNotNull { it.dropTarget?.rect?.height() }
}
