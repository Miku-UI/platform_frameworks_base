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

package com.android.server.display

import android.hardware.display.DisplayTopology
import android.hardware.display.DisplayTopologyGraph
import android.util.SparseArray
import android.view.Display
import android.view.DisplayInfo
import com.android.server.display.feature.DisplayManagerFlags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DisplayTopologyCoordinatorTest {
    private lateinit var coordinator: DisplayTopologyCoordinator
    private lateinit var displayInfos: List<DisplayInfo>
    private val topologyChangeExecutor = Runnable::run

    private lateinit var uniqueIdToDisplayIdMapping: Map<String, Int>
    private lateinit var displayIdToUniqueIdMapping: SparseArray<String>

    private val mockTopologyStore = mock<DisplayTopologyStore>()
    private val mockTopology = mock<DisplayTopology>()
    private val mockTopologyCopy = mock<DisplayTopology>()
    private val mockTopologyGraph = mock<DisplayTopologyGraph>()
    private val mockIsExtendedDisplayAllowed = mock<() -> Boolean>()
    private val mockShouldIncludeDefaultDisplayInTopology = mock<() -> Boolean>()
    private val mockTopologySavedCallback = mock<() -> Unit>()
    private val mockTopologyChangedCallback =
        mock<(android.util.Pair<DisplayTopology, DisplayTopologyGraph>) -> Unit>()
    private val mockFlags = mock<DisplayManagerFlags>()

    @Before
    fun setUp() {
        displayInfos = (1..10).map { i ->
            val info = DisplayInfo()
            info.displayId = i
            info.uniqueId = "uniqueId$i"
            info.displayGroupId = Display.DEFAULT_DISPLAY_GROUP
            info.logicalWidth = i * 300
            info.logicalHeight = i * 200
            info.logicalDensityDpi = i * 100
            info.type = Display.TYPE_EXTERNAL
            return@map info
        }

        val injector = object : DisplayTopologyCoordinator.Injector() {
            override fun getTopology() = mockTopology
            override fun createTopologyStore(
                displayIdToUniqueId: SparseArray<String>,
                uniqueIdToDisplayId: MutableMap<String, Int>
            ): DisplayTopologyStore {
                uniqueIdToDisplayIdMapping = uniqueIdToDisplayId
                displayIdToUniqueIdMapping = displayIdToUniqueId
                return mockTopologyStore
            }
        }
        whenever(mockIsExtendedDisplayAllowed()).thenReturn(true)
        whenever(mockTopology.copy()).thenReturn(mockTopologyCopy)
        whenever(mockTopologyCopy.graph).thenReturn(mockTopologyGraph)
        coordinator = DisplayTopologyCoordinator(injector, mockIsExtendedDisplayAllowed,
            mockShouldIncludeDefaultDisplayInTopology, mockTopologyChangedCallback,
            topologyChangeExecutor, DisplayManagerService.SyncRoot(), mockTopologySavedCallback,
            mockFlags, displayInfos::get)
    }

    @Test
    fun addDisplay() {
        displayInfos.forEachIndexed { i, displayInfo ->
            coordinator.onDisplayAdded(displayInfo)

            verify(mockTopology).addDisplay(displayInfo.displayId, displayInfo.logicalWidth,
                    displayInfo.logicalHeight, displayInfo.logicalDensityDpi)
            assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId))
                .isEqualTo(displayInfo.uniqueId)
            assertThat(uniqueIdToDisplayIdMapping[displayInfo.uniqueId])
                .isEqualTo(displayInfo.displayId)
        }

        verify(mockTopologyCopy, times(displayInfos.size)).graph

        verify(mockTopologyChangedCallback, times(displayInfos.size)).invoke(
            android.util.Pair(
                mockTopologyCopy,
                mockTopologyGraph
            )
        )
        verify(mockTopologyStore, times(displayInfos.size)).restoreTopology(mockTopology)

        // Clear invocations for other tests that call this method
        clearInvocations(mockTopologyCopy)
        clearInvocations(mockTopologyChangedCallback)
        clearInvocations(mockTopologyStore)
    }

    @Test
    fun addDisplay_internal() {
        val displayInfo = displayInfos[0]
        displayInfo.displayId = Display.DEFAULT_DISPLAY
        displayInfo.type = Display.TYPE_INTERNAL
        coordinator.onDisplayAdded(displayInfo)

        verify(mockTopology).addDisplay(displayInfo.displayId, displayInfo.logicalWidth,
                displayInfo.logicalHeight, displayInfo.logicalDensityDpi)
        assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId)).isEqualTo("internal")
        assertThat(uniqueIdToDisplayIdMapping["internal"]).isEqualTo(displayInfo.displayId)
        verify(mockTopologyChangedCallback).invoke(
            android.util.Pair(
                mockTopologyCopy,
                mockTopologyGraph
            )
        )
    }

    @Test
    fun addDisplay_overlay() {
        val displayInfo = displayInfos[0]
        displayInfo.type = Display.TYPE_OVERLAY
        coordinator.onDisplayAdded(displayInfo)

        verify(mockTopology).addDisplay(displayInfo.displayId, displayInfo.logicalWidth,
                displayInfo.logicalHeight, displayInfo.logicalDensityDpi)
        assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId))
            .isEqualTo(displayInfo.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo.uniqueId])
            .isEqualTo(displayInfo.displayId)
        verify(mockTopologyChangedCallback).invoke(
            android.util.Pair(
                mockTopologyCopy,
                mockTopologyGraph
            )
        )
    }

    @Test
    fun addDisplay_typeUnknown() {
        displayInfos[0].type = Display.TYPE_UNKNOWN

        coordinator.onDisplayAdded(displayInfos[0])

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun addDisplay_wifi() {
        displayInfos[0].type = Display.TYPE_WIFI

        coordinator.onDisplayAdded(displayInfos[0])

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun addDisplay_virtual() {
        displayInfos[0].type = Display.TYPE_VIRTUAL

        coordinator.onDisplayAdded(displayInfos[0])

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun addDisplay_internal_nonDefault() {
        displayInfos[0].displayId = 2
        displayInfos[0].type = Display.TYPE_INTERNAL

        coordinator.onDisplayAdded(displayInfos[0])

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun addDisplay_external_extendedDisplaysDisabled() {
        whenever(mockIsExtendedDisplayAllowed()).thenReturn(false)

        for (displayInfo in displayInfos) {
            coordinator.onDisplayAdded(displayInfo)
        }

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun addDisplay_overlay_extendedDisplaysDisabled() {
        displayInfos[0].type = Display.TYPE_OVERLAY
        whenever(mockIsExtendedDisplayAllowed()).thenReturn(false)

        for (displayInfo in displayInfos) {
            coordinator.onDisplayAdded(displayInfo)
        }

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
        verify(mockTopologyStore, never()).restoreTopology(any())
    }

    @Test
    fun addNonDefaultDisplay_defaultDisplayInTopologySwitchDisabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(true)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(false)

        // Add default display and a non-default display into the topology
        whenever(mockTopology.hasMultipleDisplays()).thenReturn(true)
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL
        coordinator.onDisplayAdded(displayInfos[0])
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        coordinator.onDisplayAdded(displayInfos[1])

        verify(mockTopology).removeDisplay(displayInfos[0].displayId)
    }

    @Test
    fun addNonDefaultDisplay_defaultDisplayInTopologySwitchEnabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(true)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(true)

        // Add default display and a non-default display into the topology
        whenever(mockTopology.hasMultipleDisplays()).thenReturn(true)
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL
        coordinator.onDisplayAdded(displayInfos[0])
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        coordinator.onDisplayAdded(displayInfos[1])

        verify(mockTopology, never()).removeDisplay(anyInt())
    }

    @Test
    fun addNonDefaultDisplay_flagDisabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(false)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(false)

        // Add default display and a non-default display into the topology
        whenever(mockTopology.hasMultipleDisplays()).thenReturn(true)
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL
        coordinator.onDisplayAdded(displayInfos[0])
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        coordinator.onDisplayAdded(displayInfos[1])

        verify(mockTopology, never()).removeDisplay(anyInt())
    }

    @Test
    fun updateDisplay() {
        val displayInfo = displayInfos[0]
        whenever(
            mockTopology.updateDisplay(
                eq(displayInfo.displayId),
                anyInt(),
                anyInt(),
                anyInt()
            )
        ).thenReturn(true)
        addDisplay()

        displayInfo.logicalWidth += 100
        displayInfo.logicalHeight += 100
        displayInfo.uniqueId = "newUniqueId"
        coordinator.onDisplayChanged(displayInfo)

        verify(mockTopology).updateDisplay(
            displayInfo.displayId, displayInfo.logicalWidth,
            displayInfo.logicalHeight, displayInfo.logicalDensityDpi
        )

        assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId))
            .isEqualTo(displayInfo.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo.uniqueId])
            .isEqualTo(displayInfo.displayId)

        verify(mockTopologyCopy).graph

        verify(mockTopologyChangedCallback).invoke(
            android.util.Pair(
                mockTopologyCopy,
                mockTopologyGraph
            )
        )
    }

    @Test
    fun updateDisplay_type_uniqueIdUpdated() {
        val displayInfo = displayInfos[0]
        displayInfo.displayId = Display.DEFAULT_DISPLAY
        addDisplay()
        val restoredTopology = mock<DisplayTopology>()
        whenever(restoredTopology.copy()).thenReturn(mock<DisplayTopology>())
        whenever(mockTopologyStore.restoreTopology(mockTopology)).thenReturn(restoredTopology)

        // Change to internal
        displayInfo.type = Display.TYPE_INTERNAL
        coordinator.onDisplayChanged(displayInfo)

        assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId)).isEqualTo("internal")
        assertThat(uniqueIdToDisplayIdMapping["internal"]).isEqualTo(displayInfo.displayId)
        verify(mockTopologyStore).restoreTopology(mockTopology)

        // Back to external
        displayInfo.type = Display.TYPE_EXTERNAL
        coordinator.onDisplayChanged(displayInfo)

        assertThat(displayIdToUniqueIdMapping.get(displayInfo.displayId))
            .isEqualTo(displayInfo.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo.uniqueId])
            .isEqualTo(displayInfo.displayId)
        verify(mockTopologyStore).restoreTopology(restoredTopology)
    }

    @Test
    fun updateDisplay_swapUniqueIds() {
        val displayInfo1 = displayInfos[0]
        val displayInfo2 = displayInfos[1]
        addDisplay()
        val restoredTopology = mock<DisplayTopology>()
        whenever(restoredTopology.copy()).thenReturn(mock<DisplayTopology>())
        whenever(mockTopologyStore.restoreTopology(mockTopology)).thenReturn(restoredTopology)

        displayInfo1.uniqueId = displayInfo2.uniqueId
        coordinator.onDisplayChanged(displayInfo1)

        assertThat(displayIdToUniqueIdMapping.get(displayInfo1.displayId))
            .isEqualTo(displayInfo1.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo1.uniqueId])
            .isEqualTo(displayInfo1.displayId)
        verify(mockTopologyStore).restoreTopology(mockTopology)

        // Now temporarily two logical display IDs map to the same unique ID. Make sure that
        // the following does not remove the mapping for the first display.
        displayInfo2.uniqueId = "newUniqueId"
        coordinator.onDisplayChanged(displayInfo2)

        assertThat(displayIdToUniqueIdMapping.get(displayInfo2.displayId))
            .isEqualTo(displayInfo2.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo2.uniqueId])
            .isEqualTo(displayInfo2.displayId)
        // This mapping should still be there
        assertThat(displayIdToUniqueIdMapping.get(displayInfo1.displayId))
            .isEqualTo(displayInfo1.uniqueId)
        assertThat(uniqueIdToDisplayIdMapping[displayInfo1.uniqueId])
            .isEqualTo(displayInfo1.displayId)
        verify(mockTopologyStore).restoreTopology(restoredTopology)
    }

    @Test
    fun updateDisplay_notChanged() {
        addDisplay()

        for (displayInfo in displayInfos) {
            coordinator.onDisplayChanged(displayInfo)
        }

        // Try to update a display that does not exist
        val info = DisplayInfo()
        info.displayId = 100
        info.uniqueId = "someUniqueId"
        coordinator.onDisplayChanged(info)

        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
        verify(mockTopologyStore, never()).restoreTopology(any())
    }

    @Test
    fun updateDisplay_typeUnknown() {
        displayInfos[0].type = Display.TYPE_UNKNOWN

        coordinator.onDisplayChanged(displayInfos[0])

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun updateDisplay_wifi() {
        displayInfos[0].type = Display.TYPE_WIFI

        coordinator.onDisplayChanged(displayInfos[0])

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun updateDisplay_virtual() {
        displayInfos[0].type = Display.TYPE_VIRTUAL

        coordinator.onDisplayChanged(displayInfos[0])

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun updateDisplay_internal_nonDefault() {
        displayInfos[0].displayId = 2
        displayInfos[0].type = Display.TYPE_INTERNAL

        coordinator.onDisplayChanged(displayInfos[0])

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun updateDisplay_external_extendedDisplaysDisabled() {
        whenever(mockIsExtendedDisplayAllowed()).thenReturn(false)

        for (displayInfo in displayInfos) {
            coordinator.onDisplayChanged(displayInfo)
        }

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyCopy, never()).graph
        verify(mockTopologyChangedCallback, never()).invoke(any())
    }

    @Test
    fun updateDisplay_overlay_extendedDisplaysDisabled() {
        displayInfos[0].type = Display.TYPE_OVERLAY
        whenever(mockIsExtendedDisplayAllowed()).thenReturn(false)

        coordinator.onDisplayChanged(displayInfos[0])

        verify(mockTopology, never()).updateDisplay(anyInt(), anyInt(), anyInt(), anyInt())
        verify(mockTopologyChangedCallback, never()).invoke(any())
        verify(mockTopologyStore, never()).restoreTopology(any())
    }

    @Test
    fun removeDisplay() {
        addDisplay()

        val displaysToRemove = listOf(0, 2, 3).map { displayInfos[it] }
        for (displayInfo in displaysToRemove) {
            whenever(mockTopology.removeDisplay(displayInfo.displayId)).thenReturn(true)

            coordinator.onDisplayRemoved(displayInfo.displayId)
        }

        verify(mockTopologyCopy, times(displaysToRemove.size)).graph

        verify(mockTopologyChangedCallback, times(displaysToRemove.size)).invoke(
            android.util.Pair(
                mockTopologyCopy,
                mockTopologyGraph
            )
        )
        verify(mockTopologyStore, times(displaysToRemove.size)).restoreTopology(mockTopology)
    }

    @Test
    fun removeDisplay_notChanged() {
        coordinator.onDisplayRemoved(100)

        verify(mockTopologyChangedCallback, never()).invoke(any())
        verify(mockTopologyStore, never()).restoreTopology(any())
    }

    @Test
    fun removeNonDefaultDisplay_defaultDisplayInTopologySwitchDisabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(true)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(false)

        // Set up the default display
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL

        // Remove a non-default display from the topology
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        whenever(mockTopology.removeDisplay(displayInfos[1].displayId)).thenReturn(true)
        whenever(mockTopology.isEmpty).thenReturn(true)
        coordinator.onDisplayRemoved(displayInfos[1].displayId)

        verify(mockTopology).addDisplay(displayInfos[0].displayId, displayInfos[0].logicalWidth,
            displayInfos[0].logicalHeight, displayInfos[0].logicalDensityDpi)
    }

    @Test
    fun removeNonDefaultDisplay_defaultDisplayInTopologySwitchEnabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(true)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(true)

        // Set up the default display
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL

        // Remove a non-default display from the topology
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        whenever(mockTopology.removeDisplay(displayInfos[1].displayId)).thenReturn(true)
        coordinator.onDisplayRemoved(displayInfos[1].displayId)

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
    }

    @Test
    fun removeNonDefaultDisplay_flagDisabled() {
        whenever(mockFlags.isDefaultDisplayInTopologySwitchEnabled).thenReturn(false)
        whenever(mockShouldIncludeDefaultDisplayInTopology()).thenReturn(false)

        // Set up the default display
        displayInfos[0].displayId = Display.DEFAULT_DISPLAY
        displayInfos[0].type = Display.TYPE_INTERNAL

        // Remove a non-default display from the topology
        displayInfos[1].displayId = Display.DEFAULT_DISPLAY + 1
        displayInfos[1].type = Display.TYPE_EXTERNAL
        whenever(mockTopology.removeDisplay(displayInfos[1].displayId)).thenReturn(true)
        coordinator.onDisplayRemoved(displayInfos[1].displayId)

        verify(mockTopology, never()).addDisplay(anyInt(), anyInt(), anyInt(), anyInt())
    }

    @Test
    fun getTopology_copy() {
        assertThat(coordinator.topology).isEqualTo(mockTopologyCopy)
    }

    @Test
    fun setTopology_normalize() {
        val topology = mock<DisplayTopology>()
        val topologyCopy = mock<DisplayTopology>()
        val topologyGraph = mock<DisplayTopologyGraph>()
        whenever(topology.copy()).thenReturn(topologyCopy)
        whenever(topologyCopy.graph).thenReturn(topologyGraph)
        whenever(mockTopologyStore.saveTopology(topology)).thenReturn(true)

        coordinator.topology = topology

        verify(topology).normalize()
        verify(mockTopologyChangedCallback).invoke(
            android.util.Pair(
                topologyCopy,
                topologyGraph
            )
        )
        verify(mockTopologyStore).saveTopology(topology)
        verify(mockTopologySavedCallback).invoke()
    }
}
