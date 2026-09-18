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
package com.android.systemui.log.dagger

import android.os.Build
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.ClearLogBuffersCommand
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.LogcatEchoTracker
import com.android.systemui.log.echo.LogcatEchoTrackerDebug
import com.android.systemui.log.echo.LogcatEchoTrackerProd
import com.android.systemui.log.impl.LogBufferFactoryImpl
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.qs.pipeline.shared.QSPipelineFlagsRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.pipeline.dagger.WiredAudioDeviceRepositoryLog
import com.android.systemui.util.wakelock.WakeLockLog
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Dagger module for providing instances of [LogBuffer]. */
@Module
abstract class LogModule {

    @Binds abstract fun bindLogBufferFactory(impl: LogBufferFactoryImpl): LogBufferFactory

    /** Registers the clear log buffers ADB command. */
    @Binds
    @IntoMap
    @ClassKey(ClearLogBuffersCommand::class)
    abstract fun bindsClearLogBuffersCommand(command: ClearLogBuffersCommand): CoreStartable

    companion object {
        /** Provides a logging buffer for doze-related logs. */
        @Provides
        @SysUISingleton
        @DozeLog
        fun provideDozeLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DozeLog", 150)
        }

        /** Provides a logging buffer for all logs for lockscreen to shade transition events. */
        @Provides
        @SysUISingleton
        @LSShadeTransitionLog
        fun provideLSShadeTransitionControllerBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("LSShadeTransitionLog", 50)
        }

        /** Provides a logging buffer for shade window messages. */
        @Provides
        @SysUISingleton
        @ShadeWindowLog
        fun provideShadeWindowLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("ShadeWindowLog", 600, false)
        }

        /** Provides a logging buffer for Shade messages. */
        @Provides
        @SysUISingleton
        @ShadeLog
        fun provideShadeLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("ShadeLog", 500, false)
        }

        /** Provides a logging buffer for Shade touch messages. */
        @Provides
        @SysUISingleton
        @ShadeTouchLog
        fun provideShadeTouchLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("ShadeTouchLog", 500, false)
        }

        /** Provides a logging buffer for all logs related to keyguard media controller. */
        @Provides
        @SysUISingleton
        @KeyguardMediaControllerLog
        fun provideKeyguardMediaControllerLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create(
                "KeyguardMediaControllerLog",
                50, /* maxSize */
                false, /* systrace */
            )
        }

        /** Provides a logging buffer for all logs related to Quick Settings. */
        @Provides
        @SysUISingleton
        @QSLog
        fun provideQuickSettingsLogBuffer(
            factory: LogBufferFactory,
            flags: QSPipelineFlagsRepository,
        ): LogBuffer {
            if (flags.tilesEnabled) {
                // we use
                return factory.create("QSLog", 450, /* maxSize */ false /* systrace */)
            } else {
                return factory.create("QSLog", 700, /* maxSize */ false /* systrace */)
            }
        }

        @Provides
        @QSTilesLogBuffers
        fun provideQuickSettingsTilesLogBufferCache(): MutableMap<TileSpec?, LogBuffer?> {
            val buffers: MutableMap<TileSpec?, LogBuffer?> = HashMap<TileSpec?, LogBuffer?>()
            // Add chatty buffers here
            return buffers
        }

        /** Provides a logging buffer for logs related to Quick Settings configuration. */
        @Provides
        @SysUISingleton
        @QSConfigLog
        fun provideQSConfigLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("QSConfigLog", 100, /* maxSize */ true /* systrace */)
        }

        /** Provides a logging buffer for [com.android.systemui.broadcast.BroadcastDispatcher] */
        @Provides
        @SysUISingleton
        @BroadcastDispatcherLog
        fun provideBroadcastDispatcherLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("BroadcastDispatcherLog", 500, /* maxSize */ false /* systrace */)
        }

        /** Provides a logging buffer for [com.android.systemui.broadcast.BroadcastSender] */
        @Provides
        @SysUISingleton
        @WakeLockLog
        fun provideWakeLockLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("WakeLockLog", 500, /* maxSize */ false /* systrace */)
        }

        /** Provides a logging buffer for all logs related to Toasts shown by SystemUI. */
        @Provides
        @SysUISingleton
        @ToastLog
        fun provideToastLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("ToastLog", 50)
        }

        /** Provides a logging buffer for all logs related to privacy indicators in SystemUI. */
        @Provides
        @SysUISingleton
        @PrivacyLog
        fun providePrivacyLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("PrivacyLog", 100)
        }

        /**
         * Provides a logging buffer for
         * [com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment].
         */
        @Provides
        @SysUISingleton
        @CollapsedSbFragmentLog
        fun provideCollapsedSbFragmentLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("CollapsedSbFragmentLog", 40)
        }

        /**
         * Provides a logging buffer for logs related to [QSFragmentLegacy]'s disable flag
         * adjustments.
         */
        @Provides
        @SysUISingleton
        @QSDisableLog
        fun provideQSFragmentDisableLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create(
                "QSFragmentDisableFlagsLog",
                10, /* maxSize */
                false, /* systrace */
            )
        }

        /** Provides a logging buffer for the disable flags repository. */
        @Provides
        @SysUISingleton
        @DisableFlagsRepositoryLog
        fun provideDisableFlagsRepositoryLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DisableFlagsRepository", 40, /* maxSize */ false /* systrace */)
        }

        /** Provides a logging buffer for logs related to swipe up gestures. */
        @Provides
        @SysUISingleton
        @SwipeUpLog
        fun provideSwipeUpLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("SwipeUpLog", 30)
        }

        /**
         * Provides a logging buffer for logs related to the media mute-await connections. See
         * [com.android.systemui.media.muteawait.MediaMuteAwaitConnectionManager].
         */
        @Provides
        @SysUISingleton
        @MediaMuteAwaitLog
        fun provideMediaMuteAwaitLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaMuteAwaitLog", 20)
        }

        /**
         * Provides a logging buffer for logs related to the media mute-await connections. See
         * [com.android.systemui.media.nearby.NearbyMediaDevicesManager].
         */
        @Provides
        @SysUISingleton
        @NearbyMediaDevicesLog
        fun provideNearbyMediaDevicesLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("NearbyMediaDevicesLog", 20)
        }

        /** Provides a buffer for logs related to media view events */
        @Provides
        @SysUISingleton
        @MediaViewLog
        fun provideMediaViewLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaView", 100)
        }

        /** Provides a buffer for media playback state changes */
        @Provides
        @SysUISingleton
        @MediaTimeoutListenerLog
        fun providesMediaTimeoutListenerLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaTimeout", 100)
        }

        /**
         * Provides a buffer for our connections and disconnections to MediaBrowserService.
         *
         * See [com.android.systemui.media.controls.domain.resume.ResumeMediaBrowser].
         */
        @Provides
        @SysUISingleton
        @MediaBrowserLog
        fun provideMediaBrowserBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaBrowser", 100)
        }

        /**
         * Provides a buffer for updates to the media carousel.
         *
         * See [com.android.systemui.media.controls.ui.controller.MediaCarouselController].
         */
        @Provides
        @SysUISingleton
        @MediaCarouselControllerLog
        fun provideMediaCarouselControllerBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaCarouselCtlrLog", 150)
        }

        /** Provides a buffer for media loading changes */
        @Provides
        @SysUISingleton
        @MediaLog
        fun providesMediaLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaLog", 100)
        }

        /** Provides a buffer for media device changes */
        @Provides
        @SysUISingleton
        @MediaDeviceLog
        fun providesMediaDeviceLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaDeviceLog", 50)
        }

        /** Allows logging buffers to be tweaked via adb on debug builds but not on prod builds. */
        @Provides
        @SysUISingleton
        fun provideLogcatEchoTracker(
            lazyTrackerDebug: Lazy<LogcatEchoTrackerDebug>
        ): LogcatEchoTracker {
            if (Build.isDebuggable()) {
                val trackerDebug = lazyTrackerDebug.get()
                trackerDebug.start()
                return trackerDebug
            } else {
                return LogcatEchoTrackerProd()
            }
        }

        /**
         * Provides a [LogBuffer] for use by
         * [com.android.systemui.biometrics.FaceHelpMessageDeferral].
         */
        @Provides
        @SysUISingleton
        @BiometricLog
        fun provideBiometricLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("BiometricLog", 200)
        }

        /** Provides a [LogBuffer] for use by the status bar network controller. */
        @Provides
        @SysUISingleton
        @StatusBarNetworkControllerLog
        fun provideStatusBarNetworkControllerBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("StatusBarNetworkControllerLog", 20)
        }

        /** Provides a [LogBuffer] for keyguard blueprint logs. */
        @Provides
        @SysUISingleton
        @KeyguardBlueprintLog
        fun provideKeyguardBlueprintLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardBlueprintLog", 100)
        }

        /** Provides a [LogBuffer] for general keyguard clock logs. */
        @Provides
        @SysUISingleton
        @KeyguardClockLog
        fun provideKeyguardClockLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardClockLog", 100)
        }

        /** Provides a [LogBuffer] for keyguard small clock logs. */
        @Provides
        @SysUISingleton
        @KeyguardSmallClockLog
        fun provideKeyguardSmallClockLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardSmallClockLog", 100)
        }

        /** Provides a [LogBuffer] for keyguard large clock logs. */
        @Provides
        @SysUISingleton
        @KeyguardLargeClockLog
        fun provideKeyguardLargeClockLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardLargeClockLog", 100)
        }

        /** Provides a [ClockMessageBuffers] which contains the keyguard clock message buffers. */
        @Provides
        fun provideKeyguardClockMessageBuffers(
            @KeyguardClockLog infraClockLog: LogBuffer,
            @KeyguardSmallClockLog smallClockLog: LogBuffer,
            @KeyguardLargeClockLog largeClockLog: LogBuffer,
        ): ClockMessageBuffers {
            return ClockMessageBuffers(infraClockLog, smallClockLog, largeClockLog)
        }

        /** Provides a [LogBuffer] for use by [com.android.keyguard.KeyguardUpdateMonitor]. */
        @Provides
        @SysUISingleton
        @KeyguardUpdateMonitorLog
        fun provideKeyguardUpdateMonitorLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardUpdateMonitorLog", 400)
        }

        /** Provides a [LogBuffer] for use by SIM events. */
        @Provides
        @SysUISingleton
        @SimLog
        fun provideSimLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("SimLog", 500)
        }

        /** Provides a [LogBuffer] for use by [com.android.keyguard.KeyguardUpdateMonitor]. */
        @Provides
        @SysUISingleton
        @CarrierTextManagerLog
        fun provideCarrierTextManagerLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("CarrierTextManagerLog", 400)
        }

        /** Provides a [LogBuffer] for use by [com.android.systemui.ScreenDecorations]. */
        @Provides
        @SysUISingleton
        @ScreenDecorationsLog
        fun provideScreenDecorationsLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("ScreenDecorationsLog", 200)
        }

        /** Provides a [LogBuffer] for use by [DeviceEntryFaceAuthRepositoryImpl]. */
        @Provides
        @SysUISingleton
        @FaceAuthLog
        fun provideFaceAuthLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("DeviceEntryFaceAuthRepositoryLog", 300)
        }

        /**
         * Provides a [LogBuffer] for use by classes in the [com.android.systemui.keyguard.bouncer]
         * package.
         */
        @Provides
        @SysUISingleton
        @BouncerLog
        fun provideBouncerLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("BouncerLog", 100)
        }

        /** Provides a [LogBuffer] for Device State Auto-Rotation logs. */
        @Provides
        @SysUISingleton
        @DeviceStateAutoRotationLog
        fun provideDeviceStateAutoRotationLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DeviceStateAutoRotationLog", 100)
        }

        /** Provides a [LogBuffer] for bluetooth-related logs. */
        @Provides
        @SysUISingleton
        @BluetoothLog
        fun providerBluetoothLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("BluetoothLog", 50)
        }

        /** Provides a logging buffer for the primary bouncer. */
        @Provides
        @SysUISingleton
        @BouncerTableLog
        fun provideBouncerLogBuffer(factory: TableLogBufferFactory): TableLogBuffer {
            return factory.create("BouncerTableLog", 250)
        }

        /** Provides a table logging buffer for the Monitor. */
        @Provides
        @SysUISingleton
        @MonitorLog
        fun provideMonitorTableLogBuffer(factory: TableLogBufferFactory): TableLogBuffer {
            return factory.create("MonitorLog", 250)
        }

        /** Provides a [LogBuffer] for Udfps logs. */
        @Provides
        @SysUISingleton
        @UdfpsLog
        fun provideUdfpsLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("UdfpsLog", 1000)
        }

        /** Provides a [LogBuffer] for general keyguard-related logs. */
        @Provides
        @SysUISingleton
        @KeyguardLog
        fun provideKeyguardLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardLog", 500)
        }

        /** Provides a [LogBuffer] for keyguard quick affordances-related logs. */
        @Provides
        @SysUISingleton
        @KeyguardQuickAffordancesLog
        fun provideKeyguardQuickAffordancesLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardQuickAffordancesLog", 100)
        }

        /** Provides a [LogBuffer] for keyguard transition animation logs. */
        @Provides
        @SysUISingleton
        @KeyguardTransitionAnimationLog
        fun provideKeyguardTransitionAnimationLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyguardTransitionAnimationLog", 250)
        }

        /** Provides a [LogBuffer] for Scrims like LightRevealScrim. */
        @Provides
        @SysUISingleton
        @ScrimLog
        fun provideScrimLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("ScrimLog", 100)
        }

        /** Provides a [LogBuffer] for dream-related logs. */
        @Provides
        @SysUISingleton
        @DreamLog
        fun provideDreamLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DreamLog", 250)
        }

        /** Provides a [LogBuffer] for communal-related logs. */
        @Provides
        @SysUISingleton
        @CommunalLog
        fun provideCommunalLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("CommunalLog", 250)
        }

        /** Provides a [LogBuffer] for window background blur logs. */
        @Provides
        @SysUISingleton
        @WindowBackgroundBlurLog
        fun provideWindowBackgroundBlurLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("WindowBackgroundBlur", 250)
        }

        /** Provides a [LogBuffer] for communal touch-handling logs. */
        @Provides
        @SysUISingleton
        @CommunalTouchLog
        fun provideCommunalTouchLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("CommunalTouchLog", 250)
        }

        /** Provides a [TableLogBuffer] for communal-related logs. */
        @Provides
        @SysUISingleton
        @CommunalTableLog
        fun provideCommunalTableLogBuffer(factory: TableLogBufferFactory): TableLogBuffer {
            return factory.create("CommunalTableLog", 250)
        }

        /** Provides a [LogBuffer] for posture detection related logs. */
        @Provides
        @SysUISingleton
        @PostureDetectionLog
        fun providePostureDetectionLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("PostureDetectionLog", 500)
        }

        /** Provides a [LogBuffer] for display metrics related logs. */
        @Provides
        @SysUISingleton
        @DisplayMetricsRepoLog
        fun provideDisplayMetricsRepoLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DisplayMetricsRepo", 50)
        }

        /** Provides a [LogBuffer] for focus related logs. */
        @Provides
        @SysUISingleton
        @FocusedDisplayRepoLog
        fun provideFocusedDisplayRepoLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("FocusedDisplayRepo", 50)
        }

        /** Provides a [LogBuffer] for the scene framework. */
        @Provides
        @SysUISingleton
        @SceneFrameworkLog
        fun provideSceneFrameworkLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create(
                name = "SceneFramework",
                maxSize = 100,
                systrace = true,
                alwaysLogToLogcat = true,
            )
        }

        /** Provides a [LogBuffer] for the bluetooth QS tile dialog. */
        @Provides
        @SysUISingleton
        @BluetoothTileDialogLog
        fun provideQBluetoothTileDialogLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("BluetoothTileDialogLog", 50)
        }

        /** Provides a [LogBuffer] for the keyboard functionalities. */
        @Provides
        @SysUISingleton
        @KeyboardLog
        fun provideKeyboardLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("KeyboardLog", 50)
        }

        /** Provides a [LogBuffer] for the input devices tutorial. */
        @Provides
        @SysUISingleton
        @InputDeviceTutorialLog
        fun provideInputDeviceTutorialLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("InputDeviceTutorialLog", 50)
        }

        /** Provides a [LogBuffer] for [PackageChangeRepository] */
        @Provides
        @SysUISingleton
        @PackageChangeRepoLog
        fun providePackageChangeRepoLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("PackageChangeRepo", 50)
        }

        /** Provides a [LogBuffer] for [PowerMenuViewModel]. */
        @Provides
        @SysUISingleton
        @PowerMenuLog
        fun providePowerMenuLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("PowerMenuLog", 100)
        }

        /** Provides a [LogBuffer] for NavBarButtonClicks. */
        @Provides
        @SysUISingleton
        @NavBarButtonClickLog
        fun provideNavBarButtonClickLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("NavBarButtonClick", 50)
        }

        /** Provides a [LogBuffer] for NavBar Orientation Tracking. */
        @Provides
        @SysUISingleton
        @NavbarOrientationTrackingLog
        fun provideNavbarOrientationTrackingLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("NavbarOrientationTrackingLog", 50)
        }

        /** Provides a [LogBuffer] for device entry related logs. */
        @Provides
        @SysUISingleton
        @DeviceEntryLog
        fun provideDeviceEntryBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DeviceEntryLog", 30)
        }

        /** Provides a [LogBuffer] for use by the DeviceEntryIcon and related classes. */
        @Provides
        @SysUISingleton
        @DeviceEntryIconLog
        fun provideDeviceEntryIconLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("DeviceEntryIconLog", 100)
        }

        /** Provides a [LogBuffer] for use by the volume loggers. */
        @Provides
        @SysUISingleton
        @VolumeLog
        fun provideVolumeLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("VolumeLog", 200)
        }

        /** Provides a [LogBuffer] for use by long touch event handlers. */
        @Provides
        @SysUISingleton
        @LongPressTouchLog
        fun providesLongPressTouchLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("LongPressViewLog", 200)
        }

        /** Provides a [LogBuffer] for use by long touch event handlers. */
        @Provides
        @SysUISingleton
        @RearDisplayLog
        fun providesRearDisplayLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("RearDisplayLog", 50)
        }

        /** Provides a [LogBuffer] for [WiredAudioDeviceRepository] */
        @Provides
        @SysUISingleton
        @WiredAudioDeviceRepositoryLog
        fun providesWiredAudioDeviceRepositoryLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("WiredAudioDeviceRepositoryLog", 50)
        }

        /** Provides a [LogBuffer] for Multi Display related events on Status Bar. */
        @Provides
        @SysUISingleton
        @MultiDisplayStatusBarLog
        fun providesMultiDisplayStatusBarLog(factory: LogBufferFactory): LogBuffer {
            return factory.create(name = "MultiDisplayStatusBarLog", maxSize = 50)
        }

        /** Provides a [LogBuffer] for Camera related events. */
        @Provides
        @SysUISingleton
        @CameraLog
        fun providesCameraLog(factory: LogBufferFactory): LogBuffer {
            return factory.create(name = "CameraLog", maxSize = 50)
        }

        /** Provides a [LogBuffer] for Camera related events. */
        @Provides
        @SysUISingleton
        @ScreenRecordingLog
        fun providesScreenRecordingLog(factory: LogBufferFactory): LogBuffer {
            return factory.create(name = "ScreenRecordingLog", maxSize = 100)
        }
    }
}
