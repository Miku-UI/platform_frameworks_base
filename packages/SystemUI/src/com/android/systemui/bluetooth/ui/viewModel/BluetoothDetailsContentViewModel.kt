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

package com.android.systemui.bluetooth.ui.viewModel

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.jank.InteractionJankMonitor
import com.android.settingslib.volume.domain.interactor.AudioModeInteractor
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.bluetooth.qsdialog.AudioSharingInteractor
import com.android.systemui.bluetooth.qsdialog.BluetoothAutoOnInteractor
import com.android.systemui.bluetooth.qsdialog.BluetoothDetailsContentManager
import com.android.systemui.bluetooth.qsdialog.BluetoothDetailsContentManager.Companion.CONTENT_HEIGHT_PREF_KEY
import com.android.systemui.bluetooth.qsdialog.BluetoothDeviceMetadataInteractor
import com.android.systemui.bluetooth.qsdialog.BluetoothStateInteractor
import com.android.systemui.bluetooth.qsdialog.BluetoothTileDialogDelegate
import com.android.systemui.bluetooth.qsdialog.BluetoothTileDialogLogger
import com.android.systemui.bluetooth.qsdialog.DeviceFetchTrigger
import com.android.systemui.bluetooth.qsdialog.DeviceItemActionInteractor
import com.android.systemui.bluetooth.qsdialog.DeviceItemClick
import com.android.systemui.bluetooth.qsdialog.DeviceItemInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * ViewModel for Bluetooth Dialog or Bluetooth Details View after clicking on the Bluetooth QS tile.
 */
@SysUISingleton
class BluetoothDetailsContentViewModel
@Inject
constructor(
    private val deviceItemInteractor: DeviceItemInteractor,
    private val deviceItemActionInteractor: DeviceItemActionInteractor,
    private val bluetoothStateInteractor: BluetoothStateInteractor,
    private val bluetoothAutoOnInteractor: BluetoothAutoOnInteractor,
    private val audioSharingInteractor: AudioSharingInteractor,
    private val audioModeInteractor: AudioModeInteractor,
    private val audioSharingButtonViewModelFactory: AudioSharingButtonViewModel.Factory,
    private val bluetoothDeviceMetadataInteractor: BluetoothDeviceMetadataInteractor,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val logger: BluetoothTileDialogLogger,
    @Application private val coroutineScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Main private val sharedPreferences: SharedPreferences,
    private val bluetoothDialogDelegateFactory: BluetoothTileDialogDelegate.Factory,
    private val bluetoothDetailsContentManagerFactory: BluetoothDetailsContentManager.Factory,
) {

    var title by mutableStateOf("")
        private set

    var subTitle by mutableStateOf("")
        private set

    lateinit var contentManager: BluetoothDetailsContentManager
    private var job: Job? = null

    /**
     * Binds the bluetooth details view with BluetoothDetailsContentManager.
     *
     * @param view The view from which the bluetooth details content is shown.
     */
    fun bindDetailsView(view: View) {
        // If `QsDetailedView` is not enabled, it should show the dialog.
        if (QsDetailedView.isUnexpectedlyInLegacyMode()) return

        cancelJob()

        job =
            coroutineScope.launch(context = mainDispatcher) {
                val detailsUIState = createInitialDetailsUIState()
                contentManager = createContentManager()
                contentManager.bind(
                    contentView = view,
                    dialog = null,
                    coroutineScope = this,
                    detailsUIState = detailsUIState,
                )
                contentManager.start()
                updateDetailsUIState(
                    context = view.context,
                    detailsUIState = detailsUIState,
                    dialog = null,
                )
            }
    }

    /** Shows the bluetooth dialog. */
    fun showDialog(expandable: Expandable?) {
        cancelJob()

        job =
            coroutineScope.launch(context = mainDispatcher) {
                val detailsUIState = createInitialDetailsUIState()
                val dialogDelegate = createBluetoothTileDialog()
                val dialog = dialogDelegate.createDialog()

                val controller =
                    expandable?.dialogTransitionController(
                        DialogCuj(
                            InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                            INTERACTION_JANK_TAG,
                        )
                    )
                controller?.let {
                    dialogTransitionAnimator.show(dialog, it, animateBackgroundBoundsChange = true)
                } ?: dialog.show()
                // contentManager is created after dialog.show
                contentManager = dialogDelegate.contentManager
                contentManager.bind(
                    contentView = dialog.requireViewById(R.id.root),
                    dialog = dialog,
                    coroutineScope = this,
                    detailsUIState = detailsUIState,
                )
                updateDetailsUIState(dialog.context, detailsUIState, dialog)
            }
    }

    /** Unbinds the details view when it goes away. */
    fun unbindDetailsView() {
        cancelJob()
        contentManager.releaseView()
    }

    private fun createInitialDetailsUIState(): DetailsUIState =
        DetailsUIState(
            deviceItem = MutableStateFlow(null),
            shouldAnimateProgressBar = MutableStateFlow(null),
            audioSharingButton = MutableStateFlow(null),
            bluetoothState = MutableStateFlow(null),
            bluetoothAutoOn = MutableStateFlow(null),
        )

    private suspend fun updateDetailsUIState(
        context: Context,
        detailsUIState: DetailsUIState,
        dialog: SystemUIDialog?,
    ) {
        coroutineScope {
            var updateDeviceItemJob: Job?

            updateDeviceItemJob = launch {
                deviceItemInteractor.updateDeviceItems(context, DeviceFetchTrigger.FIRST_LOAD)
            }

            title = context.getString(R.string.quick_settings_bluetooth_label)

            // deviceItemUpdate is emitted when device item list is done fetching, update UI and
            // stop the progress bar.
            combine(deviceItemInteractor.deviceItemUpdate, deviceItemInteractor.showSeeAllUpdate) {
                    deviceItem,
                    showSeeAll ->
                    detailsUIState.shouldAnimateProgressBar.value = false
                    detailsUIState.deviceItem.value =
                        DeviceItem(
                            deviceItem,
                            showSeeAll,
                            showPairNewDevice = bluetoothStateInteractor.isBluetoothEnabled(),
                        )
                }
                .launchIn(this)

            // deviceItemUpdateRequest is emitted when a bluetooth callback is called, re-fetch
            // the device item list and animate the progress bar.
            merge(
                    deviceItemInteractor.deviceItemUpdateRequest,
                    audioModeInteractor.isOngoingCall,
                    bluetoothDeviceMetadataInteractor.metadataUpdate,
                    if (
                        audioSharingInteractor.audioSharingAvailable() &&
                            audioSharingInteractor.qsDialogImprovementAvailable()
                    ) {
                        audioSharingInteractor.audioSourceStateUpdate
                    } else {
                        emptyFlow()
                    },
                )
                .onEach {
                    detailsUIState.shouldAnimateProgressBar.value = true
                    updateDeviceItemJob?.cancel()
                    updateDeviceItemJob = launch {
                        deviceItemInteractor.updateDeviceItems(
                            context,
                            DeviceFetchTrigger.BLUETOOTH_CALLBACK_RECEIVED,
                        )
                    }
                }
                .launchIn(this)

            if (audioSharingInteractor.audioSharingAvailable()) {
                if (audioSharingInteractor.qsDialogImprovementAvailable()) {
                    launch { audioSharingInteractor.handleAudioSourceWhenReady() }
                }

                audioSharingButtonViewModelFactory.create().run {
                    audioSharingButtonStateUpdate
                        .onEach {
                            when (it) {
                                is AudioSharingButtonState.Visible -> {
                                    detailsUIState.audioSharingButton.value =
                                        AudioSharingButton(
                                            VISIBLE,
                                            context.getString(it.resId),
                                            it.isActive,
                                        )
                                }
                                is AudioSharingButtonState.Gone -> {
                                    detailsUIState.audioSharingButton.value =
                                        AudioSharingButton(GONE, label = null, isActive = false)
                                }
                            }
                        }
                        .launchIn(this@coroutineScope)
                    launch { activate() }
                }
            }

            // bluetoothStateUpdate is emitted when bluetooth on/off state is changed, re-fetch
            // the device item list.
            bluetoothStateInteractor.bluetoothStateUpdate
                .onEach {
                    val uiProperties = UiProperties.build(it, isAutoOnToggleFeatureAvailable())
                    subTitle = context.getString(uiProperties.subTitleResId)
                    detailsUIState.bluetoothState.value = BluetoothState(it, uiProperties)
                    updateDeviceItemJob?.cancel()
                    updateDeviceItemJob = launch {
                        deviceItemInteractor.updateDeviceItems(
                            context,
                            DeviceFetchTrigger.BLUETOOTH_STATE_CHANGE_RECEIVED,
                        )
                    }
                }
                .launchIn(this)

            // bluetoothStateToggle is emitted when user toggles the bluetooth state switch,
            // send the new value to the bluetoothStateInteractor and animate the progress bar.
            contentManager.bluetoothStateToggle
                .filterNotNull()
                .onEach {
                    detailsUIState.shouldAnimateProgressBar.value = true
                    bluetoothStateInteractor.setBluetoothEnabled(it)
                }
                .launchIn(this)

            // deviceItemClick is emitted when user clicked on a device item.
            contentManager.deviceItemClick
                .filterNotNull()
                .onEach {
                    when (it.target) {
                        DeviceItemClick.Target.ENTIRE_ROW -> {
                            deviceItemActionInteractor.onClick(it.deviceItem, dialog)
                            logger.logDeviceClick(
                                it.deviceItem.cachedBluetoothDevice.address,
                                it.deviceItem.type,
                            )
                        }

                        DeviceItemClick.Target.ACTION_ICON -> {
                            deviceItemActionInteractor.onActionIconClick(it.deviceItem) { intent ->
                                contentManager.startSettingsActivity(intent, it.clickedView)
                            }
                        }
                    }
                }
                .launchIn(this)

            // contentHeight is emitted when the dialog is dismissed.
            contentManager.contentHeight
                .filterNotNull()
                .onEach {
                    withContext(backgroundDispatcher) {
                        sharedPreferences.edit().putInt(CONTENT_HEIGHT_PREF_KEY, it).apply()
                    }
                }
                .launchIn(this)

            if (isAutoOnToggleFeatureAvailable()) {
                // bluetoothAutoOnUpdate is emitted when bluetooth auto on on/off state is
                // changed.
                bluetoothAutoOnInteractor.isEnabled
                    .onEach {
                        detailsUIState.bluetoothAutoOn.value =
                            BluetoothAutoOn(
                                it,
                                if (it) R.string.turn_on_bluetooth_auto_info_enabled
                                else R.string.turn_on_bluetooth_auto_info_disabled,
                            )
                    }
                    .launchIn(this)

                // bluetoothAutoOnToggle is emitted when user toggles the bluetooth auto on
                // switch, send the new value to the bluetoothAutoOnInteractor.
                contentManager.bluetoothAutoOnToggle
                    .filterNotNull()
                    .onEach { bluetoothAutoOnInteractor.setEnabled(it) }
                    .launchIn(this)
            }
        }
    }

    suspend fun isAutoOnToggleFeatureAvailable() = bluetoothAutoOnInteractor.isAutoOnSupported()

    private suspend fun createBluetoothTileDialog(): BluetoothTileDialogDelegate {
        return bluetoothDialogDelegateFactory.create(
            getUiProperties(),
            getCachedContentHeight(),
            { cancelJob() },
        )
    }

    private suspend fun createContentManager(): BluetoothDetailsContentManager {
        return bluetoothDetailsContentManagerFactory.create(
            getUiProperties(),
            getCachedContentHeight(),
            /* isInDialog= */ false,
            /* doneButtonCallback= */ fun() {},
        )
    }

    private suspend fun getUiProperties(): UiProperties {
        return UiProperties.build(
            bluetoothStateInteractor.isBluetoothEnabled(),
            isAutoOnToggleFeatureAvailable(),
        )
    }

    private suspend fun getCachedContentHeight(): Int {
        return withContext(backgroundDispatcher) {
            sharedPreferences.getInt(CONTENT_HEIGHT_PREF_KEY, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val INTERACTION_JANK_TAG = "bluetooth_tile_dialog"

        private fun getSubtitleResId(isBluetoothEnabled: Boolean) =
            if (isBluetoothEnabled) R.string.quick_settings_bluetooth_tile_subtitle
            else R.string.bt_is_off
    }

    data class UiProperties(
        @StringRes val subTitleResId: Int,
        val autoOnToggleVisibility: Int,
        @DimenRes val scrollViewMinHeightResId: Int,
    ) {
        companion object {
            internal fun build(
                isBluetoothEnabled: Boolean,
                isAutoOnToggleFeatureAvailable: Boolean,
            ) =
                UiProperties(
                    subTitleResId = getSubtitleResId(isBluetoothEnabled),
                    autoOnToggleVisibility =
                        if (isAutoOnToggleFeatureAvailable && !isBluetoothEnabled) VISIBLE
                        else GONE,
                    scrollViewMinHeightResId =
                        if (isAutoOnToggleFeatureAvailable)
                            R.dimen.bluetooth_dialog_scroll_view_min_height_with_auto_on
                        else R.dimen.bluetooth_dialog_scroll_view_min_height,
                )
        }
    }

    data class DeviceItem(
        val deviceItem: List<com.android.systemui.bluetooth.qsdialog.DeviceItem>,
        val showSeeAll: Boolean,
        val showPairNewDevice: Boolean,
    )

    data class AudioSharingButton(val visibility: Int, val label: String?, val isActive: Boolean)

    data class BluetoothState(val isEnabled: Boolean, val uiProperties: UiProperties)

    data class BluetoothAutoOn(val isEnabled: Boolean, @StringRes val infoResId: Int)

    data class DetailsUIState(
        val deviceItem: MutableStateFlow<DeviceItem?>,
        val shouldAnimateProgressBar: MutableStateFlow<Boolean?>,
        val audioSharingButton: MutableStateFlow<AudioSharingButton?>,
        val bluetoothState: MutableStateFlow<BluetoothState?>,
        val bluetoothAutoOn: MutableStateFlow<BluetoothAutoOn?>,
    )
}
