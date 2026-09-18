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
package com.android.systemui.statusbar.policy.dagger

import android.content.Context
import android.content.res.Resources
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import android.os.UserManager
import com.android.internal.R
import com.android.settingslib.devicestate.AndroidSecureSettings
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManagerImpl
import com.android.settingslib.devicestate.PostureDeviceStateConverter
import com.android.settingslib.devicestate.SecureSettings
import com.android.settingslib.notification.modes.ZenIconLoader
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.connectivity.AccessPointControllerImpl
import com.android.systemui.statusbar.connectivity.NetworkController
import com.android.systemui.statusbar.connectivity.NetworkControllerImpl
import com.android.systemui.statusbar.connectivity.WifiPickerTrackerFactory
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl
import com.android.systemui.statusbar.phone.ConfigurationForwarder
import com.android.systemui.statusbar.policy.BatteryControllerLogger
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.statusbar.policy.BluetoothControllerImpl
import com.android.systemui.statusbar.policy.CastController
import com.android.systemui.statusbar.policy.CastControllerImpl
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.DataSaverController
import com.android.systemui.statusbar.policy.DeviceControlsController
import com.android.systemui.statusbar.policy.DeviceControlsControllerImpl
import com.android.systemui.statusbar.policy.DevicePostureController
import com.android.systemui.statusbar.policy.DevicePostureControllerImpl
import com.android.systemui.statusbar.policy.ExtensionController
import com.android.systemui.statusbar.policy.ExtensionControllerImpl
import com.android.systemui.statusbar.policy.FlashlightController
import com.android.systemui.statusbar.policy.FlashlightControllerImpl
import com.android.systemui.statusbar.policy.HotspotController
import com.android.systemui.statusbar.policy.HotspotControllerImpl
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.KeyguardStateControllerImpl
import com.android.systemui.statusbar.policy.LocationController
import com.android.systemui.statusbar.policy.LocationControllerImpl
import com.android.systemui.statusbar.policy.NextAlarmController
import com.android.systemui.statusbar.policy.NextAlarmControllerImpl
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler
import com.android.systemui.statusbar.policy.RotationLockController
import com.android.systemui.statusbar.policy.RotationLockControllerImpl
import com.android.systemui.statusbar.policy.SecurityController
import com.android.systemui.statusbar.policy.SecurityControllerImpl
import com.android.systemui.statusbar.policy.SecurityControllerStartable
import com.android.systemui.statusbar.policy.SensitiveNotificationProtectionController
import com.android.systemui.statusbar.policy.SensitiveNotificationProtectionControllerImpl
import com.android.systemui.statusbar.policy.SplitShadeStateController
import com.android.systemui.statusbar.policy.SplitShadeStateControllerImpl
import com.android.systemui.statusbar.policy.UserInfoController
import com.android.systemui.statusbar.policy.UserInfoControllerImpl
import com.android.systemui.statusbar.policy.WalletController
import com.android.systemui.statusbar.policy.WalletControllerImpl
import com.android.systemui.statusbar.policy.ZenModeController
import com.android.systemui.statusbar.policy.ZenModeControllerImpl
import com.android.systemui.statusbar.policy.bluetooth.data.repository.BluetoothRepository
import com.android.systemui.statusbar.policy.bluetooth.data.repository.BluetoothRepositoryImpl
import com.android.systemui.statusbar.policy.data.repository.DeviceProvisioningRepositoryModule
import com.android.systemui.statusbar.policy.domain.interactor.TtyStatusInteractor
import com.android.systemui.statusbar.policy.domain.interactor.impl.TtyStatusInteractorImpl
import com.android.systemui.statusbar.policy.profile.data.repository.ManagedProfileRepository
import com.android.systemui.statusbar.policy.profile.data.repository.impl.ManagedProfileRepositoryImpl
import com.android.systemui.statusbar.policy.vpn.data.repository.VpnRepository
import com.android.systemui.statusbar.policy.vpn.data.repository.impl.VpnRepositoryImpl
import com.android.systemui.supervision.data.repository.SupervisionRepositoryModule
import com.android.systemui.util.wrapper.CameraRotationSettingProvider
import com.android.systemui.util.wrapper.CameraRotationSettingProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import javax.inject.Named

/** Dagger Module for code in the statusbar.policy package. */
@Module(includes = [DeviceProvisioningRepositoryModule::class, SupervisionRepositoryModule::class])
interface StatusBarPolicyModule {

    @Binds
    @IntoMap
    @ClassKey(RemoteInputQuickSettingsDisabler::class)
    abstract fun bindRemoteInputQuickSettingsDisabler(
        remoteInputQuickSettingsDisabler: RemoteInputQuickSettingsDisabler
    ): CoreStartable

    /**  */
    @Binds
    fun provideBluetoothController(controllerImpl: BluetoothControllerImpl): BluetoothController

    /**  */
    @Binds fun provideBluetoothRepository(impl: BluetoothRepositoryImpl): BluetoothRepository

    /**  */
    @Binds fun provideVpnRepository(impl: VpnRepositoryImpl): VpnRepository

    /**  */
    @Binds fun provideCastController(controllerImpl: CastControllerImpl): CastController

    /**
     * @deprecated: unscoped configuration controller shouldn't be injected as it might lead to
     * wrong updates in case of secondary displays.
     */
    @Binds
    @Deprecated(
        """unscoped configuration controller shouldn't be injected as it might lead to
      wrong updates in case of secondary displays."""
    )
    fun bindConfigurationController(@Main impl: ConfigurationController): ConfigurationController

    /**  */
    @Binds
    fun provideExtensionController(controllerImpl: ExtensionControllerImpl): ExtensionController

    /**  */
    @Binds
    fun provideFlashlightController(controllerImpl: FlashlightControllerImpl): FlashlightController

    /**  */
    @Binds
    fun provideKeyguardMonitor(controllerImpl: KeyguardStateControllerImpl): KeyguardStateController

    /**  */
    @Binds
    fun provideSplitShadeStateController(
        splitShadeStateControllerImpl: SplitShadeStateControllerImpl
    ): SplitShadeStateController

    /**  */
    @Binds fun provideHotspotController(controllerImpl: HotspotControllerImpl): HotspotController

    /**  */
    @Binds fun provideLocationController(controllerImpl: LocationControllerImpl): LocationController

    /**  */
    @Binds fun provideTtyStatusInteractor(impl: TtyStatusInteractorImpl): TtyStatusInteractor

    /**  */
    @Binds
    fun provideManagedProfileRepository(
        impl: ManagedProfileRepositoryImpl
    ): ManagedProfileRepository

    /**  */
    @Binds fun provideNetworkController(controllerImpl: NetworkControllerImpl): NetworkController

    /**  */
    @Binds
    fun provideNextAlarmController(controllerImpl: NextAlarmControllerImpl): NextAlarmController

    /**  */
    @Binds
    fun provideRotationLockController(
        controllerImpl: RotationLockControllerImpl
    ): RotationLockController

    /**  */
    @Binds
    @SysUISingleton
    fun bindCameraRotationSettingProvider(
        impl: CameraRotationSettingProviderImpl
    ): CameraRotationSettingProvider

    /**  */
    @Binds fun provideSecurityController(controllerImpl: SecurityControllerImpl): SecurityController

    /**  */
    @Binds
    fun provideSensitiveNotificationProtectionController(
        controllerImpl: SensitiveNotificationProtectionControllerImpl
    ): SensitiveNotificationProtectionController

    /**  */
    @Binds fun provideUserInfoContrller(controllerImpl: UserInfoControllerImpl): UserInfoController

    /**  */
    @Binds fun provideZenModeController(controllerImpl: ZenModeControllerImpl): ZenModeController

    /**  */
    @Binds
    fun provideDeviceControlsController(
        controllerImpl: DeviceControlsControllerImpl
    ): DeviceControlsController

    /**  */
    @Binds fun provideWalletController(controllerImpl: WalletControllerImpl): WalletController

    /**  */
    @Binds
    fun provideAccessPointController(
        accessPointControllerImpl: AccessPointControllerImpl
    ): AccessPointController

    /**  */
    @Binds
    fun provideDevicePostureController(
        devicePostureControllerImpl: DevicePostureControllerImpl
    ): DevicePostureController

    /**  */
    @Binds
    @SysUISingleton
    @Main
    fun provideGlobalConfigurationForwarder(
        @Main configurationController: ConfigurationController
    ): ConfigurationForwarder

    /** Binds [SecurityControllerStartable]. */
    @Binds
    @IntoMap
    @ClassKey(SecurityControllerStartable::class)
    fun bindSecurityControllerCoreStartable(startable: SecurityControllerStartable): CoreStartable

    companion object {
        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Main
        fun provideGlobalConfigurationController(
            @Application context: Context,
            factory: ConfigurationControllerImpl.Factory,
        ): ConfigurationController {
            return factory.create(context)
        }

        /**  */
        @JvmStatic
        @SysUISingleton
        @Provides
        fun provideAccessPointControllerImpl(
            @Application context: Context,
            userManager: UserManager,
            userTracker: UserTracker,
            @Main mainExecutor: Executor,
            wifiPickerTrackerFactory: WifiPickerTrackerFactory,
        ): AccessPointControllerImpl {
            val controller =
                AccessPointControllerImpl(
                    context,
                    userManager,
                    userTracker,
                    mainExecutor,
                    wifiPickerTrackerFactory,
                )
            controller.init()
            return controller
        }

        /**  */
        @JvmStatic
        @SysUISingleton
        @Provides
        fun provideAndroidSecureSettings(context: Context): SecureSettings {
            return AndroidSecureSettings(context.getContentResolver())
        }

        /**  */
        @JvmStatic
        @SysUISingleton
        @Provides
        fun providePosturesHelper(
            context: Context,
            deviceStateManager: DeviceStateManager,
        ): PostureDeviceStateConverter {
            return PostureDeviceStateConverter(context, deviceStateManager)
        }

        /** Returns a singleton instance of DeviceStateAutoRotateSettingManager. */
        @JvmStatic
        @SysUISingleton
        @Provides
        fun provideAutoRotateSettingsManager(
            context: Context,
            @Background bgExecutor: Executor,
            secureSettings: SecureSettings,
            @Main mainHandler: Handler,
            postureDeviceStateConverter: PostureDeviceStateConverter,
        ): DeviceStateAutoRotateSettingManager {
            return DeviceStateAutoRotateSettingManagerImpl(
                context,
                bgExecutor,
                secureSettings,
                mainHandler,
                postureDeviceStateConverter,
            )
        }

        /** Default values for per-device state rotation lock settings. */
        @JvmStatic
        @Provides
        @Named(DEVICE_STATE_ROTATION_LOCK_DEFAULTS)
        fun providesDeviceStateRotationLockDefaults(@Main resources: Resources): Array<String> {
            return resources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults)
        }

        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        fun provideDataSaverController(networkController: NetworkController): DataSaverController {
            return networkController.getDataSaverController()
        }

        /** Provides a log buffer for BatteryControllerImpl */
        @JvmStatic
        @Provides
        @SysUISingleton
        @BatteryControllerLog
        fun provideBatteryControllerLog(factory: LogBufferFactory): LogBuffer {
            return factory.create(BatteryControllerLogger.TAG, 150)
        }

        /** Provides a log buffer for CastControllerImpl */
        @JvmStatic
        @Provides
        @SysUISingleton
        @CastControllerLog
        fun provideCastControllerLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("CastControllerLog", 50)
        }

        /** Provides a [ZenIconLoader] that fetches icons in a background thread. */
        @JvmStatic
        @Provides
        @SysUISingleton
        fun provideZenIconLoader(
            @UiBackground backgroundExecutorService: ExecutorService
        ): ZenIconLoader {
            return ZenIconLoader(backgroundExecutorService)
        }

        const val DEVICE_STATE_ROTATION_LOCK_DEFAULTS: String =
            "DEVICE_STATE_ROTATION_LOCK_DEFAULTS"
    }
}
