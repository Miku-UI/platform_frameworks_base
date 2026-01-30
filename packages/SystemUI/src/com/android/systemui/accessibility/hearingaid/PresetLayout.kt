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

package com.android.systemui.accessibility.hearingaid

import android.bluetooth.BluetoothHapClient
import android.bluetooth.BluetoothHapPresetInfo
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.SIDE_UNIFIED
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.VALID_SIDES
import com.android.settingslib.bluetooth.hearingdevices.ui.PresetSpinner
import com.android.settingslib.bluetooth.hearingdevices.ui.PresetUi
import com.android.systemui.accessibility.hearingaid.HearingDevicesUiEventLogger.Companion.LAUNCH_SOURCE_UNKNOWN
import com.android.systemui.res.R
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap

/**
 * A custom view that manages a set of {@link PresetSpinner} controls for hearing aid devices. This
 * layout dynamically displays either a unified preset control or separate controls for left and
 * right sides, based on the device configuration.
 *
 * <p>It implements the {@link PresetUi} interface to allow for control by a presenter and handles
 * UI-related events such as preset changes.
 */
class PresetLayout
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes), PresetUi {

    private val sideToControlMap: BiMap<Int, PresetSpinner> = HashBiMap.create()
    private var uiListener: PresetUi.PresetUiListener? = null
    private var uiEventLogger: HearingDevicesUiEventLogger? = null
    private var launchSourceId: Int = LAUNCH_SOURCE_UNKNOWN
    private var expanded = false

    private val onChangeListener: PresetSpinner.OnChangeListener =
        PresetSpinner.OnChangeListener { spinner: PresetSpinner, value: Int ->
            val side = sideToControlMap.inverse()[spinner]
            side?.let {
                logMetrics(side)
                uiListener?.onPresetChangedFromUi(side, value)
            }
        }

    override fun setListener(listener: PresetUi.PresetUiListener?) {
        uiListener = listener
    }

    override fun setupControls(sides: Set<Int>) {
        sides.forEach { side -> createControl(side) }
        createControl(SIDE_UNIFIED)
        removeAllViews()
        for (side in VALID_SIDES) {
            sideToControlMap[side]?.let {
                addView(it, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
        }
        updateControlLayout()
    }

    override fun setControlEnabled(side: Int, enabled: Boolean) {
        sideToControlMap[side]?.isEnabled = enabled
    }

    override fun setControlList(side: Int, presetInfos: List<BluetoothHapPresetInfo>) {
        sideToControlMap[side]?.apply {
            setList(presetInfos)
            if (presetInfos.isEmpty()) {
                isEnabled = false
            }
        }
    }

    override fun setControlValue(side: Int, presetIndex: Int) {
        sideToControlMap[side]?.apply {
            if (getValue() != presetIndex) {
                setValue(presetIndex)
            }
        }
    }

    override fun getControlValue(side: Int): Int {
        return sideToControlMap[side]?.getValue() ?: BluetoothHapClient.PRESET_INDEX_UNAVAILABLE
    }

    override fun setVisible(visible: Boolean) {
        visibility = if (visible) VISIBLE else GONE
    }

    override fun setControlExpanded(expanded: Boolean) {
        if (this.expanded != expanded) {
            this.expanded = expanded
            updateControlLayout()
        }
    }

    override fun isControlExpanded(): Boolean {
        return expanded
    }

    fun setUiEventLogger(uiEventLogger: HearingDevicesUiEventLogger, launchSourceId: Int) {
        this.uiEventLogger = uiEventLogger
        this.launchSourceId = launchSourceId
    }

    private fun createControl(side: Int) {
        if (sideToControlMap.containsKey(side)) {
            return
        }
        PresetSpinner(context).apply {
            val titleRes =
                when (side) {
                    SIDE_LEFT -> R.string.hearing_devices_preset_label_left
                    SIDE_RIGHT -> R.string.hearing_devices_preset_label_right
                    else -> R.string.hearing_devices_preset_label
                }
            setTitle(context.getString(titleRes))
            setOnChangeListener(onChangeListener)
            sideToControlMap[side] = this
        }
    }

    private fun updateControlLayout() {
        sideToControlMap.forEach { (side, control) ->
            control.visibility =
                when (side) {
                    SIDE_UNIFIED -> if (expanded) GONE else VISIBLE
                    else -> if (expanded) VISIBLE else GONE
                }
        }
    }

    private fun logMetrics(side: Int) {
        val uiEvent: HearingDevicesUiEvent =
            when (side) {
                SIDE_UNIFIED -> HearingDevicesUiEvent.HEARING_DEVICES_PRESET_SELECT
                else -> HearingDevicesUiEvent.HEARING_DEVICES_PRESET_SELECT_SEPARATED
            }
        uiEventLogger?.log(uiEvent, launchSourceId)
    }

    @VisibleForTesting
    fun getControls(): Map<Int, PresetSpinner> {
        return sideToControlMap
    }
}
