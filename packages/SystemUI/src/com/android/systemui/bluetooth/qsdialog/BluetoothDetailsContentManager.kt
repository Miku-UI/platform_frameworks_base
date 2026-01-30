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

package com.android.systemui.bluetooth.qsdialog

import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.AccessibilityDelegate
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.internal.R as InternalR
import com.android.internal.logging.UiEventLogger
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast
import com.android.systemui.Prefs
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsContentViewModel
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.util.annotations.DeprecatedSysuiVisibleForTesting
import com.android.systemui.util.time.SystemClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceItemClick(val deviceItem: DeviceItem, val clickedView: View, val target: Target) {
    enum class Target {
        ENTIRE_ROW,
        ACTION_ICON,
    }
}

/** View content manager for showing active, connected and saved bluetooth devices. */
class BluetoothDetailsContentManager
@AssistedInject
constructor(
    @Assisted private val initialUiProperties: BluetoothDetailsContentViewModel.UiProperties,
    @Assisted private val cachedContentHeight: Int,
    @Assisted private val isInDialog: Boolean,
    @Assisted private val doneButtonCallback: () -> Unit,
    @Main private val mainDispatcher: CoroutineDispatcher,
    private val systemClock: SystemClock,
    private val uiEventLogger: UiEventLogger,
    private val logger: BluetoothTileDialogLogger,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val activityStarter: ActivityStarter,
) : BluetoothTileDialogCallback {

    private val mutableBluetoothStateToggle: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    @DeprecatedSysuiVisibleForTesting
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val bluetoothStateToggle
        get() = mutableBluetoothStateToggle.asStateFlow()

    private val mutableBluetoothAutoOnToggle: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    @DeprecatedSysuiVisibleForTesting
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val bluetoothAutoOnToggle
        get() = mutableBluetoothAutoOnToggle.asStateFlow()

    private val mutableDeviceItemClick: MutableStateFlow<DeviceItemClick?> = MutableStateFlow(null)
    @DeprecatedSysuiVisibleForTesting
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val deviceItemClick
        get() = mutableDeviceItemClick.asStateFlow()

    private val mutableContentHeight: MutableStateFlow<Int?> = MutableStateFlow(null)
    @DeprecatedSysuiVisibleForTesting
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val contentHeight
        get() = mutableContentHeight.asStateFlow()

    private val deviceItemAdapter: Adapter = Adapter()

    private var lastUiUpdateMs: Long = -1

    private var lastItemRow: Int = -1

    private var showSeeAll: Boolean = false

    private var lastConnectedDeviceIndex: Int = -1

    private lateinit var coroutineScope: CoroutineScope

    // UI Components
    private lateinit var contentView: View
    private lateinit var bluetoothToggle: CompoundButton
    private lateinit var seeAllButton: View
    private lateinit var pairNewDeviceButton: View
    private lateinit var deviceListView: RecyclerView
    private lateinit var autoOnToggle: CompoundButton
    private lateinit var autoOnToggleLayout: View
    private lateinit var autoOnToggleInfoTextView: TextView
    private lateinit var audioSharingButton: Button
    private lateinit var progressBarAnimation: ProgressBar
    private lateinit var progressBarBackground: View
    private lateinit var scrollViewContent: View

    // UI Components that only exist in dialog, but not tile details view.
    private var doneButton: Button? = null
    private var titleTextView: TextView? = null
    private var subtitleTextView: TextView? = null

    // UI Components that only exist in tile details view, but not in dialog.
    private var entryBackgroundActive: Drawable? = null
    private var entryBackgroundActiveStart: Drawable? = null
    private var entryBackgroundActiveEnd: Drawable? = null
    private var entryBackgroundActiveMiddle: Drawable? = null
    private var entryBackgroundInactive: Drawable? = null
    private var entryBackgroundInactiveStart: Drawable? = null
    private var entryBackgroundInactiveEnd: Drawable? = null
    private var entryBackgroundInactiveMiddle: Drawable? = null

    @AssistedFactory
    interface Factory {
        fun create(
            initialUiProperties: BluetoothDetailsContentViewModel.UiProperties,
            cachedContentHeight: Int,
            isInDialog: Boolean,
            doneButtonCallback: () -> Unit,
        ): BluetoothDetailsContentManager
    }

    fun bind(
        contentView: View,
        dialog: SystemUIDialog?,
        coroutineScope: CoroutineScope,
        detailsUIState: BluetoothDetailsContentViewModel.DetailsUIState,
    ) {

        this.contentView = contentView
        this.coroutineScope = coroutineScope

        bluetoothToggle = contentView.requireViewById(R.id.bluetooth_toggle)
        seeAllButton = contentView.requireViewById(R.id.see_all_button)
        pairNewDeviceButton = contentView.requireViewById(R.id.pair_new_device_button)
        deviceListView = contentView.requireViewById(R.id.device_list)
        autoOnToggle = contentView.requireViewById(R.id.bluetooth_auto_on_toggle)
        autoOnToggleLayout = contentView.requireViewById(R.id.bluetooth_auto_on_toggle_layout)
        autoOnToggleInfoTextView =
            contentView.requireViewById(R.id.bluetooth_auto_on_toggle_info_text)
        audioSharingButton = contentView.requireViewById(R.id.audio_sharing_button)
        progressBarAnimation =
            contentView.requireViewById(R.id.bluetooth_tile_dialog_progress_animation)
        scrollViewContent = contentView.requireViewById(R.id.scroll_view)

        if (isInDialog) {
            progressBarBackground =
                contentView.requireViewById(R.id.bluetooth_tile_dialog_progress_background)

            // If rendering with tile details view, the title and subtitle will be added in the
            // `TileDetails`
            titleTextView = contentView.requireViewById(R.id.bluetooth_tile_dialog_title)
            subtitleTextView = contentView.requireViewById(R.id.bluetooth_tile_dialog_subtitle)
            // If rendering with tile details view, done button shouldn't exist.
            doneButton = contentView.requireViewById(R.id.done_button)
        } else {
            entryBackgroundActive =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_on)
            entryBackgroundActiveStart =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_on_start)
            entryBackgroundActiveEnd =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_on_end)
            entryBackgroundActiveMiddle =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_on_middle)
            entryBackgroundInactive =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_off)
            entryBackgroundInactiveStart =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_off_start)
            entryBackgroundInactiveEnd =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_off_end)
            entryBackgroundInactiveMiddle =
                contentView.context.getDrawable(R.drawable.settingslib_entry_bg_off_middle)
        }

        setupToggle()
        setupRecyclerView()

        doneButton?.setOnClickListener { doneButtonCallback() }
        subtitleTextView?.text = contentView.context.getString(initialUiProperties.subTitleResId)
        seeAllButton.setOnClickListener { onSeeAllClicked(it) }
        pairNewDeviceButton.setOnClickListener { onPairNewDeviceClicked(it) }
        audioSharingButton.apply {
            setOnClickListener { onAudioSharingButtonClicked(it) }
            accessibilityDelegate =
                object : AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(
                            AccessibilityAction(
                                AccessibilityAction.ACTION_CLICK.id,
                                contentView.context.getString(
                                    R.string
                                        .quick_settings_bluetooth_audio_sharing_button_accessibility
                                ),
                            )
                        )
                    }
                }
        }
        // If it's in the Compose-based detailed view, min and max height are set in the
        // `TileDetails`.
        if (isInDialog) {
            scrollViewContent.apply {
                minimumHeight =
                    resources.getDimensionPixelSize(initialUiProperties.scrollViewMinHeightResId)
                layoutParams.height = maxOf(cachedContentHeight, minimumHeight)
            }
        }

        updateDetailsUI(dialog, detailsUIState)
    }

    private fun updateDetailsUI(
        dialog: SystemUIDialog?,
        detailsUIState: BluetoothDetailsContentViewModel.DetailsUIState,
    ) {
        coroutineScope.launch {
            var updateDialogUiJob: Job? = null

            detailsUIState.deviceItem
                .filterNotNull()
                .onEach {
                    updateDialogUiJob?.cancel()
                    updateDialogUiJob = launch {
                        onDeviceItemUpdated(it.deviceItem, it.showSeeAll, it.showPairNewDevice)
                    }
                }
                .launchIn(this)

            detailsUIState.shouldAnimateProgressBar
                .filterNotNull()
                .onEach { animateProgressBar(it) }
                .launchIn(this)

            detailsUIState.audioSharingButton
                .filterNotNull()
                .onEach { onAudioSharingButtonUpdated(it.visibility, it.label, it.isActive) }
                .launchIn(this)

            detailsUIState.bluetoothState
                .filterNotNull()
                .onEach { onBluetoothStateUpdated(it.isEnabled, it.uiProperties) }
                .launchIn(this)

            detailsUIState.bluetoothAutoOn
                .filterNotNull()
                .onEach { onBluetoothAutoOnUpdated(it.isEnabled, it.infoResId) }
                .launchIn(this)
            produce<Unit> { awaitClose { dialog?.cancel() } }
        }
    }

    fun start() {
        lastUiUpdateMs = systemClock.elapsedRealtime()
    }

    fun releaseView() {
        mutableContentHeight.value = scrollViewContent.measuredHeight
    }

    override fun onSeeAllClicked(view: View) {
        uiEventLogger.log(BluetoothTileDialogUiEvent.SEE_ALL_CLICKED)
        startSettingsActivity(Intent(ACTION_PREVIOUSLY_CONNECTED_DEVICE), view)
    }

    override fun onPairNewDeviceClicked(view: View) {
        uiEventLogger.log(BluetoothTileDialogUiEvent.PAIR_NEW_DEVICE_CLICKED)
        startSettingsActivity(Intent(ACTION_PAIR_NEW_DEVICE), view)
    }

    override fun onAudioSharingButtonClicked(view: View) {
        uiEventLogger.log(BluetoothTileDialogUiEvent.BLUETOOTH_AUDIO_SHARING_BUTTON_CLICKED)
        val intent =
            Intent(ACTION_AUDIO_SHARING).apply {
                putExtra(
                    EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                    Bundle().apply {
                        putBoolean(LocalBluetoothLeBroadcast.EXTRA_START_LE_AUDIO_SHARING, true)
                    },
                )
            }
        startSettingsActivity(intent, view)
    }

    suspend fun animateProgressBar(animate: Boolean) {
        withContext(mainDispatcher) {
            if (animate) {
                showProgressBar()
            } else {
                delay(PROGRESS_BAR_ANIMATION_DURATION_MS)
                hideProgressBar()
            }
        }
    }

    suspend fun onDeviceItemUpdated(
        deviceItem: List<DeviceItem>,
        showSeeAll: Boolean,
        showPairNewDevice: Boolean,
    ) {
        this.showSeeAll = showSeeAll
        withContext(mainDispatcher) {
            val start = systemClock.elapsedRealtime()
            val itemRow = deviceItem.size + showSeeAll.toInt() + showPairNewDevice.toInt()
            // If not the first load, add a slight delay for smoother dialog height change
            if (itemRow != lastItemRow && lastItemRow != -1) {
                delay(MIN_HEIGHT_CHANGE_INTERVAL_MS - (start - lastUiUpdateMs))
            }
            if (isActive) {
                deviceItemAdapter.refreshDeviceItemList(deviceItem) {
                    seeAllButton.visibility = if (showSeeAll) VISIBLE else GONE
                    pairNewDeviceButton.visibility = if (showPairNewDevice) VISIBLE else GONE
                    // Update the height after data is updated
                    scrollViewContent.layoutParams.height = WRAP_CONTENT
                    lastUiUpdateMs = systemClock.elapsedRealtime()
                    lastItemRow = itemRow
                    if (!isInDialog) {
                        lastConnectedDeviceIndex = deviceItem.indexOfLast(::isDeviceConnected)
                        // The seeAllButton's UI will be grouped together with unconnected devices.
                        seeAllButton.background =
                            if (lastConnectedDeviceIndex != deviceItem.size - 1) {
                                // If the last device is unconnected, seeAllButton should use the
                                // end drawable.
                                entryBackgroundInactiveEnd
                            } else {
                                // If the last device is connected, seeAllButton will be the only
                                // item using the inactive drawable, so it should use the default
                                // inactive one.
                                entryBackgroundInactive
                            }
                        deviceListView.invalidateItemDecorations()
                    }
                    logger.logDeviceUiUpdate(lastUiUpdateMs - start, deviceItem)
                }
            }
        }
    }

    fun onBluetoothStateUpdated(
        isEnabled: Boolean,
        uiProperties: BluetoothDetailsContentViewModel.UiProperties,
    ) {
        bluetoothToggle.apply {
            isChecked = isEnabled
            setEnabled(true)
            alpha = ENABLED_ALPHA
        }
        subtitleTextView?.text = contentView.context.getString(uiProperties.subTitleResId)
        autoOnToggleLayout.visibility = uiProperties.autoOnToggleVisibility
    }

    fun onBluetoothAutoOnUpdated(isEnabled: Boolean, @StringRes infoResId: Int) {
        autoOnToggle.isChecked = isEnabled
        autoOnToggleInfoTextView.text = contentView.context.getString(infoResId)
    }

    fun onAudioSharingButtonUpdated(visibility: Int, label: String?, isActive: Boolean) {
        audioSharingButton.apply {
            this.visibility = visibility
            label?.let { text = it }
            this.isActivated = isActive
        }
    }

    fun startSettingsActivity(intent: Intent, view: View) {
        if (coroutineScope.isActive) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val controller = dialogTransitionAnimator.createActivityTransitionController(view)
            // The controller will be null when the screen is locked and going to show the
            // primary bouncer. In this case we dismiss the dialog manually.
            if (controller == null) {
                coroutineScope.cancel()
            }
            activityStarter.postStartActivityDismissingKeyguard(intent, 0, controller)
        }
    }

    private fun setupToggle() {
        bluetoothToggle.setOnCheckedChangeListener { view, isChecked ->
            mutableBluetoothStateToggle.value = isChecked
            view.apply {
                isEnabled = false
                alpha = DISABLED_ALPHA
            }
            logger.logBluetoothState(BluetoothStateStage.USER_TOGGLED, isChecked.toString())
            uiEventLogger.log(BluetoothTileDialogUiEvent.BLUETOOTH_TOGGLE_CLICKED)
        }

        autoOnToggleLayout.visibility = initialUiProperties.autoOnToggleVisibility
        autoOnToggle.setOnCheckedChangeListener { _, isChecked ->
            mutableBluetoothAutoOnToggle.value = isChecked
            uiEventLogger.log(BluetoothTileDialogUiEvent.BLUETOOTH_AUTO_ON_TOGGLE_CLICKED)
        }
    }

    private fun setupRecyclerView() {
        deviceListView.apply {
            layoutManager = LinearLayoutManager(contentView.context)
            adapter = deviceItemAdapter
        }
        if (!isInDialog) {
            deviceListView.addItemDecoration(
                object : RecyclerView.ItemDecoration() {
                    override fun onDraw(
                        c: Canvas,
                        parent: RecyclerView,
                        state: RecyclerView.State,
                    ) {
                        // `itemCount` represents the total number of items in your adapter's data
                        // set, regardless of what's visible.
                        val adapter = parent.adapter ?: return
                        val itemCount = adapter.itemCount

                        // `parent.childCount` is the number of child views currently visible on
                        // screen. Often less than itemCount since RecyclerView recycles views that
                        // scroll off-screen.
                        for (i in 0 until parent.childCount) {
                            val child = parent.getChildAt(i) ?: continue
                            val adapterPosition = parent.getChildAdapterPosition(child)
                            if (adapterPosition == RecyclerView.NO_POSITION) continue
                            val background: Drawable?
                            if (adapterPosition > lastConnectedDeviceIndex) {
                                // Set up background for unconnected devices
                                background =
                                    when {
                                        // Use the default inactive drawable, if there is only one
                                        // unconnected device and no seeAllButton.
                                        lastConnectedDeviceIndex + 1 == itemCount - 1 &&
                                            !showSeeAll -> entryBackgroundInactive
                                        // Use the start drawable, if this is the first unconnected
                                        // device.
                                        adapterPosition == lastConnectedDeviceIndex + 1 ->
                                            entryBackgroundInactiveStart
                                        // Use the end drawable, if this is the last unconnected
                                        // device and no seeAllButton.
                                        adapterPosition == itemCount - 1 && !showSeeAll ->
                                            entryBackgroundInactiveEnd

                                        else -> entryBackgroundInactiveMiddle
                                    }
                            } else {
                                // Set up background for connected devices
                                background =
                                    when {
                                        lastConnectedDeviceIndex == 0 -> entryBackgroundActive
                                        adapterPosition == 0 -> entryBackgroundActiveStart
                                        adapterPosition == lastConnectedDeviceIndex ->
                                            entryBackgroundActiveEnd

                                        else -> entryBackgroundActiveMiddle
                                    }
                            }
                            background?.setBounds(child.left, child.top, child.right, child.bottom)
                            background?.draw(c)
                        }
                    }
                }
            )
        }
    }

    private fun showProgressBar() {
        if (progressBarAnimation.visibility != VISIBLE) {
            progressBarAnimation.visibility = VISIBLE
            if (isInDialog) {
                progressBarBackground.visibility = INVISIBLE
            }
        }
    }

    private fun hideProgressBar() {
        if (progressBarAnimation.visibility != INVISIBLE) {
            progressBarAnimation.visibility = INVISIBLE
            if (isInDialog) {
                progressBarBackground.visibility = VISIBLE
            }
        }
    }

    @DeprecatedSysuiVisibleForTesting
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    inner class Adapter : RecyclerView.Adapter<Adapter.DeviceItemViewHolder>() {

        private val diffUtilCallback =
            object : DiffUtil.ItemCallback<DeviceItem>() {
                override fun areItemsTheSame(
                    deviceItem1: DeviceItem,
                    deviceItem2: DeviceItem,
                ): Boolean {
                    return deviceItem1.cachedBluetoothDevice == deviceItem2.cachedBluetoothDevice
                }

                override fun areContentsTheSame(
                    deviceItem1: DeviceItem,
                    deviceItem2: DeviceItem,
                ): Boolean {
                    return deviceItem1.type == deviceItem2.type &&
                        deviceItem1.cachedBluetoothDevice == deviceItem2.cachedBluetoothDevice &&
                        deviceItem1.deviceName == deviceItem2.deviceName &&
                        deviceItem1.connectionSummary == deviceItem2.connectionSummary &&
                        // Ignored the icon drawable
                        deviceItem1.iconWithDescription?.second ==
                            deviceItem2.iconWithDescription?.second &&
                        deviceItem1.background == deviceItem2.background &&
                        deviceItem1.isEnabled == deviceItem2.isEnabled &&
                        deviceItem1.actionAccessibilityLabel ==
                            deviceItem2.actionAccessibilityLabel &&
                        deviceItem1.actionIconAccessibilityLabelRes ==
                            deviceItem2.actionIconAccessibilityLabelRes
                }
            }

        private val asyncListDiffer = AsyncListDiffer(this, diffUtilCallback)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceItemViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.bluetooth_device_item, parent, false)
            return DeviceItemViewHolder(view)
        }

        override fun getItemCount() = asyncListDiffer.currentList.size

        override fun onBindViewHolder(holder: DeviceItemViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item)
        }

        internal fun getItem(position: Int) = asyncListDiffer.currentList[position]

        internal fun refreshDeviceItemList(updated: List<DeviceItem>, callback: () -> Unit) {
            asyncListDiffer.submitList(updated, callback)
        }

        @DeprecatedSysuiVisibleForTesting
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        inner class DeviceItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val container = view.requireViewById<View>(R.id.bluetooth_device_row)
            private val nameView = view.requireViewById<TextView>(R.id.bluetooth_device_name)
            private val summaryView = view.requireViewById<TextView>(R.id.bluetooth_device_summary)
            private val iconView = view.requireViewById<ImageView>(R.id.bluetooth_device_icon)
            private val actionIcon = view.requireViewById<ImageView>(R.id.gear_icon_image)
            private val actionIconView = view.requireViewById<View>(R.id.gear_icon)
            private val divider = view.requireViewById<View>(R.id.divider)

            internal fun bind(item: DeviceItem) {
                val isDeviceConnected = isDeviceConnected(item)
                container.apply {
                    isEnabled = item.isEnabled
                    background = item.background?.let { context.getDrawable(it) }
                    setOnClickListener {
                        mutableDeviceItemClick.value =
                            DeviceItemClick(item, it, DeviceItemClick.Target.ENTIRE_ROW)
                        uiEventLogger.log(BluetoothTileDialogUiEvent.DEVICE_CLICKED)
                    }

                    // updating icon colors
                    val tintColor =
                        if (isInDialog) {
                            context.getColor(
                                if (item.isActive) InternalR.color.materialColorOnPrimaryContainer
                                else InternalR.color.materialColorOnSurface
                            )
                        } else {
                            context.getColor(
                                if (isDeviceConnected)
                                    InternalR.color.materialColorOnPrimaryContainer
                                else InternalR.color.materialColorOnSurface
                            )
                        }

                    // update icons
                    iconView.apply {
                        item.iconWithDescription?.let {
                            setImageDrawable(it.first)
                            contentDescription = it.second
                        }
                    }

                    actionIcon.setImageResource(item.actionIconRes)
                    actionIcon.drawable?.setTint(tintColor)
                    actionIconView.contentDescription =
                        resources.getString(item.actionIconAccessibilityLabelRes, item.deviceName)

                    divider.setBackgroundColor(tintColor)

                    // update text styles
                    if (isInDialog) {
                        nameView.setTextAppearance(
                            if (item.isActive) R.style.TextAppearance_BluetoothTileDialog_Active
                            else R.style.TextAppearance_BluetoothTileDialog
                        )
                        summaryView.setTextAppearance(
                            if (item.isActive) R.style.TextAppearance_BluetoothTileDialog_Active
                            else R.style.TextAppearance_BluetoothTileDialog
                        )
                    } else {
                        nameView.setTextAppearance(
                            if (isDeviceConnected)
                                R.style.TextAppearance_TileDetailsEntryTitle_Active
                            else R.style.TextAppearance_TileDetailsEntryTitle
                        )
                        summaryView.setTextAppearance(
                            if (isDeviceConnected)
                                R.style.TextAppearance_TileDetailsEntrySubTitle_Active
                            else R.style.TextAppearance_TileDetailsEntrySubTitle
                        )
                    }

                    accessibilityDelegate =
                        object : AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(
                                host: View,
                                info: AccessibilityNodeInfo,
                            ) {
                                super.onInitializeAccessibilityNodeInfo(host, info)
                                info.addAction(
                                    AccessibilityAction(
                                        AccessibilityAction.ACTION_CLICK.id,
                                        item.actionAccessibilityLabel,
                                    )
                                )
                            }
                        }
                }
                nameView.text = item.deviceName
                summaryView.text = item.connectionSummary
                // needed for marquee
                summaryView.isSelected = true

                actionIconView.setOnClickListener {
                    mutableDeviceItemClick.value =
                        DeviceItemClick(item, it, DeviceItemClick.Target.ACTION_ICON)
                }
            }
        }
    }

    private fun isDeviceConnected(item: DeviceItem): Boolean {
        return item.type == DeviceItemType.CONNECTED_BLUETOOTH_DEVICE
    }

    internal companion object {
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
        const val CONTENT_HEIGHT_PREF_KEY = Prefs.Key.BLUETOOTH_TILE_DIALOG_CONTENT_HEIGHT
        const val MIN_HEIGHT_CHANGE_INTERVAL_MS = 800L
        const val ACTION_BLUETOOTH_DEVICE_DETAILS =
            "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
        const val ACTION_PREVIOUSLY_CONNECTED_DEVICE =
            "com.android.settings.PREVIOUSLY_CONNECTED_DEVICE"
        const val ACTION_PAIR_NEW_DEVICE = "android.settings.BLUETOOTH_PAIRING_SETTINGS"
        const val ACTION_AUDIO_SHARING = "com.android.settings.BLUETOOTH_AUDIO_SHARING_SETTINGS"
        const val DISABLED_ALPHA = 0.3f
        const val ENABLED_ALPHA = 1f
        const val PROGRESS_BAR_ANIMATION_DURATION_MS = 1500L

        private fun Boolean.toInt(): Int {
            return if (this) 1 else 0
        }
    }
}

interface BluetoothTileDialogCallback {
    fun onSeeAllClicked(view: View)

    fun onPairNewDeviceClicked(view: View)

    fun onAudioSharingButtonClicked(view: View)
}
