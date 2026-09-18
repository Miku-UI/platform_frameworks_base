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

package com.android.systemui.statusbar.quickactions.av.domain.interactor

import android.Manifest
import android.app.AppOpsManager
import android.app.IActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.hardware.SensorPrivacyManager
import android.os.UserHandle
import android.permission.PermissionManager
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.PrivacyChipRepository
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController
import com.android.systemui.statusbar.quickactions.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorAccess
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorActivityModel
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactor for managing the state of the video conference privacy chip in the status bar.
 *
 * Provides a [Flow] of [AvControlsChipModel] representing the current state of the media control
 * chip.
 *
 * This functionality is only enabled on large screen devices.
 */
interface AvControlsChipInteractor {
    /** Chip updates. */
    val model: StateFlow<AvControlsChipModel>

    /** Whether the display of the privacy dot should be suppressed to avoid duplicity. */
    val isShowingAvChip: StateFlow<Boolean>

    val cameraBlocked: StateFlow<Boolean>
    val microphoneBlocked: StateFlow<Boolean>

    abstract fun setCameraBlocked(value: Boolean)

    abstract fun setMicrophoneBlocked(value: Boolean)

    /** Closes the app identified by the package name. */
    abstract fun closeApp(packageName: String)

    /** Opens permission settings for the app identified by the package name. */
    abstract fun manageApp(packageName: String)

    /** Opens the privacy dashboard settings page. */
    abstract fun openPrivacyDashboard()
}

/**
 * This implementation is to be used in case the Audio/Video control chip functionality is not
 * available.
 */
class NoOpAvControlsChipInteractor @Inject constructor() : AvControlsChipInteractor {
    override val model = MutableStateFlow<AvControlsChipModel>(AvControlsChipModel())
    override val isShowingAvChip = MutableStateFlow<Boolean>(false)
    override val cameraBlocked: StateFlow<Boolean> = MutableStateFlow(false)
    override val microphoneBlocked: StateFlow<Boolean> = MutableStateFlow(false)

    override fun setCameraBlocked(value: Boolean) {}

    override fun setMicrophoneBlocked(value: Boolean) {}

    override fun closeApp(packageName: String) {}

    override fun manageApp(packageName: String) {}

    override fun openPrivacyDashboard() {}
}

class AvControlsChipInteractorImpl
@AssistedInject
constructor(
    @Assisted private val backgroundScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    privacyChipRepository: PrivacyChipRepository,
    @Assisted statusBarModePerDisplayRepository: StatusBarModePerDisplayRepository,
    private val sensorPrivacyController: IndividualSensorPrivacyController,
    private val permissionManager: PermissionManager,
    private val packageManager: PackageManager,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val appIconProvider: AppIconProvider,
    private val appOpsManager: AppOpsManager,
    private val activityManager: IActivityManager,
    private val activityStarter: ActivityStarter,
) : AvControlsChipInteractor {

    @AssistedFactory
    fun interface Factory {
        fun create(
            backgroundScope: CoroutineScope,
            statusBarModeRepository: StatusBarModePerDisplayRepository,
        ): AvControlsChipInteractorImpl
    }

    private data class BlockedSensorInfo(
        val cameraBlocked: Boolean = false,
        val micBlocked: Boolean = false,
    )

    private val _sensorsState: StateFlow<BlockedSensorInfo> =
        callbackFlow<(BlockedSensorInfo) -> BlockedSensorInfo> {
                val callback =
                    object : IndividualSensorPrivacyController.Callback {
                        override fun onSensorBlockedChanged(sensor: Int, blocked: Boolean) {
                            trySend { prev: BlockedSensorInfo ->
                                when (sensor) {
                                    SensorPrivacyManager.Sensors.CAMERA ->
                                        prev.copy(cameraBlocked = blocked)

                                    SensorPrivacyManager.Sensors.MICROPHONE ->
                                        prev.copy(micBlocked = blocked)

                                    else -> prev
                                }
                            }
                        }
                    }
                sensorPrivacyController.addCallback(callback)
                awaitClose { sensorPrivacyController.removeCallback(callback) }
            }
            .scan(initial = BlockedSensorInfo()) { state, eventF -> eventF(state) }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = BlockedSensorInfo(),
            )

    private val _sensorAccessList: Flow<List<SensorAccess>> =
        callbackFlow {
                val listener =
                    object : AppOpsManager.OnOpActiveChangedListener {
                        override fun onOpActiveChanged(
                            op: String,
                            uid: Int,
                            packageName: String,
                            active: Boolean,
                        ) {
                            trySend(Unit)
                        }

                        override fun onOpActiveChanged(
                            op: String,
                            uid: Int,
                            packageName: String,
                            attributionTag: String?,
                            virtualDeviceId: Int,
                            active: Boolean,
                            attributionFlags: Int,
                            attributionChainId: Int,
                        ) {
                            trySend(Unit)
                        }
                    }
                appOpsManager.startWatchingActive(
                    arrayOf<String>(AppOpsManager.OPSTR_CAMERA, AppOpsManager.OPSTR_RECORD_AUDIO),
                    bgDispatcher.asExecutor(),
                    listener,
                )
                awaitClose { appOpsManager.stopWatchingActive(listener) }
            }
            .onStart { emit(Unit) }
            .map {
                // TODO(436221760): Maybe we can just update the list rather than
                sensorAccessList()
            }
            .flowOn(bgDispatcher)

    override val cameraBlocked: StateFlow<Boolean> =
        _sensorsState
            .map { it.cameraBlocked }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override val microphoneBlocked: StateFlow<Boolean> =
        _sensorsState
            .map { it.micBlocked }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    private suspend fun sensorAccessList(): List<SensorAccess> =
        withContext(bgDispatcher) {
            permissionManager.getIndicatorAppOpUsageData(false).mapNotNull {
                val sensor: Sensor? =
                    when (it.permissionGroupName) {
                        Manifest.permission_group.CAMERA -> Sensor.CAMERA
                        Manifest.permission_group.MICROPHONE -> Sensor.MICROPHONE
                        else -> null
                    }
                if (sensor == null) {
                    return@mapNotNull null
                }
                var appName = it.packageName

                var icon: Drawable? = null
                try {
                    icon =
                        appIconProvider.getOrFetchAppIcon(
                            packageName = it.packageName,
                            userHandle = UserHandle.of(selectedUserInteractor.getSelectedUserId()),
                            instanceKey = "AvControlsChipInteractor",
                        )
                } catch (_: PackageManager.NameNotFoundException) {}
                if (!it.isPhoneCall) {
                    try {
                        val appInfo =
                            packageManager.getApplicationInfoAsUser(
                                it.packageName,
                                0,
                                UserHandle.getUserId(it.uid),
                            )
                        appName = appInfo.loadLabel(packageManager).toString()
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                return@mapNotNull SensorAccess(
                    packageName = it.packageName,
                    appName = appName,
                    sensor = sensor,
                    icon = icon,
                )
            }
        }

    override fun setCameraBlocked(value: Boolean) {
        backgroundScope.launch {
            sensorPrivacyController.setSensorBlocked(
                SensorPrivacyManager.Sources.OTHER, // TODO: Add new source?
                SensorPrivacyManager.Sensors.CAMERA,
                value,
            )
        }
    }

    override fun setMicrophoneBlocked(value: Boolean) {
        backgroundScope.launch {
            sensorPrivacyController.setSensorBlocked(
                SensorPrivacyManager.Sources.OTHER, // TODO: Add new source?
                SensorPrivacyManager.Sensors.MICROPHONE,
                value,
            )
        }
    }

    override val model: StateFlow<AvControlsChipModel> =
        combine(_sensorAccessList, privacyChipRepository.privacyItems) {
                sensorAccessList,
                privacyItems ->
                AvControlsChipModel(
                    sensorActivityModel =
                        createSensorActivityModel(
                            cameraActive =
                                privacyItems.any { it.privacyType == PrivacyType.TYPE_CAMERA },
                            microphoneActive =
                                privacyItems.any { it.privacyType == PrivacyType.TYPE_MICROPHONE },
                        ),
                    sensorAccessList = sensorAccessList,
                )
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive),
            )

    private val isStatusBarModeFullScreen: Flow<Boolean> =
        statusBarModePerDisplayRepository?.isInFullscreenMode ?: flowOf(false)

    override val isShowingAvChip: StateFlow<Boolean> =
        combine(model, isStatusBarModeFullScreen) {
                chipModel: AvControlsChipModel,
                isInFullscreenMode: Boolean ->
                when (chipModel.sensorActivityModel) {
                    is SensorActivityModel.Inactive -> false
                    is SensorActivityModel.Active -> !isInFullscreenMode
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    private fun createSensorActivityModel(
        cameraActive: Boolean,
        microphoneActive: Boolean,
    ): SensorActivityModel =
        when {
            !cameraActive && microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.MICROPHONE)

            cameraActive && !microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA)

            cameraActive && microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE)

            else -> SensorActivityModel.Inactive
        }

    override fun closeApp(packageName: String) {
        backgroundScope.launch {
            activityManager.stopAppForUser(packageName, selectedUserInteractor.getSelectedUserId())
        }
    }

    override fun manageApp(packageName: String) {
        val userId = selectedUserInteractor.getSelectedUserId()
        val navigationIntent = Intent(android.provider.Settings.ACTION_APP_PERMISSIONS_SETTINGS)
        navigationIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
        navigationIntent.putExtra(Intent.EXTRA_USER, UserHandle.of(userId))
        backgroundScope.launch { activityStarter.startActivity(navigationIntent, true) }
    }

    override fun openPrivacyDashboard() {
        backgroundScope.launch {
            val navigationIntent = Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE)
            activityStarter.startActivity(navigationIntent, true)
        }
    }
}
