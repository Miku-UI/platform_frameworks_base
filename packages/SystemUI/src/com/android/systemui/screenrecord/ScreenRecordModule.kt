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
 * limitations under the License.
 */

package com.android.systemui.screenrecord

import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDisabledDialogDelegate
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.ScreenRecordTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileAvailabilityInteractor
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModel
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModelFactory
import com.android.systemui.qs.tiles.impl.screenrecord.domain.interactor.ScreenRecordTileDataInteractor
import com.android.systemui.qs.tiles.impl.screenrecord.domain.interactor.ScreenRecordTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.screenrecord.domain.model.ScreenRecordTileModel
import com.android.systemui.qs.tiles.impl.screenrecord.domain.ui.mapper.ScreenRecordTileMapper
import com.android.systemui.res.R
import com.android.systemui.screencapture.data.repository.ScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.data.repository.ScreenCaptureDeviceStateRepositoryImpl
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screenrecord.data.repository.LegacyScreenRecordingStartStopRepository
import com.android.systemui.screenrecord.data.repository.ScreenRecordRepository
import com.android.systemui.screenrecord.data.repository.ScreenRecordRepositoryImpl
import com.android.systemui.screenrecord.data.repository.ScreenRecordingServiceRepository
import com.android.systemui.screenrecord.data.repository.ScreenRecordingStartStopRepository
import com.android.systemui.settings.UserTracker
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import java.util.concurrent.Executor

@Module
interface ScreenRecordModule {

    /** Inject ScreenRecordTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ScreenRecordTile.TILE_SPEC)
    fun bindScreenRecordTile(screenRecordTile: ScreenRecordTile): QSTileImpl<*>

    @Binds
    @IntoMap
    @ClassKey(ScreenRecordingCoreStartable::class)
    fun bindScreenRecordingStartable(startable: ScreenRecordingCoreStartable): CoreStartable

    @Binds
    @IntoMap
    @StringKey(SCREEN_RECORD_TILE_SPEC)
    fun provideScreenRecordAvailabilityInteractor(
        impl: ScreenRecordTileDataInteractor
    ): QSTileAvailabilityInteractor

    @Binds
    @SysUISingleton
    fun bindScreenCaptureDeviceStateRepository(
        impl: ScreenCaptureDeviceStateRepositoryImpl
    ): ScreenCaptureDeviceStateRepository

    companion object {
        private const val SCREEN_RECORD_TILE_SPEC = "screenrecord"

        @Provides
        @SysUISingleton
        fun provideScreenRecordUxController(
            @Main mainExecutor: Executor,
            broadcastDispatcher: BroadcastDispatcher,
            devicePolicyResolver: Lazy<ScreenCaptureDevicePolicyResolver>,
            userTracker: UserTracker,
            recordingControllerLogger: RecordingControllerLogger,
            mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
            screenCaptureDisabledDialogDelegate: ScreenCaptureDisabledDialogDelegate,
            screenRecordPermissionDialogDelegateFactory:
                ScreenRecordPermissionDialogDelegate.Factory,
            screenRecordPermissionContentManagerFactory:
                ScreenRecordPermissionContentManager.Factory,
        ): ScreenRecordUxController {
            return if (Flags.screenReactions()) {
                ScreenRecordUxControllerImpl()
            } else {
                ScreenRecordLegacyUxControllerImpl(
                    mainExecutor,
                    broadcastDispatcher,
                    devicePolicyResolver,
                    userTracker,
                    recordingControllerLogger,
                    mediaProjectionMetricsLogger,
                    screenCaptureDisabledDialogDelegate,
                    screenRecordPermissionDialogDelegateFactory,
                    screenRecordPermissionContentManagerFactory,
                )
            }
        }

        @Provides
        @SysUISingleton
        fun provideScreenRecordingStartStopRepository(
            legacyScreenRecordingStartStopRepository:
                Lazy<LegacyScreenRecordingStartStopRepository>,
            screenRecordingServiceRepository: Lazy<ScreenRecordingServiceRepository>,
        ): ScreenRecordingStartStopRepository {
            return if (Flags.thinScreenRecordingService()) {
                    screenRecordingServiceRepository
                } else {
                    legacyScreenRecordingStartStopRepository
                }
                .get()
        }

        @Provides
        @IntoMap
        @StringKey(SCREEN_RECORD_TILE_SPEC)
        fun provideScreenRecordTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(SCREEN_RECORD_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_screen_record_icon_off,
                        labelRes = R.string.quick_settings_screen_record_label,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        /** Inject ScreenRecord Tile into tileViewModelMap in QSModule */
        @Provides
        @IntoMap
        @StringKey(SCREEN_RECORD_TILE_SPEC)
        fun provideScreenRecordTileViewModel(
            factory: QSTileViewModelFactory.Static<ScreenRecordTileModel>,
            mapper: ScreenRecordTileMapper,
            stateInteractor: ScreenRecordTileDataInteractor,
            userActionInteractor: ScreenRecordTileUserActionInteractor,
        ): QSTileViewModel =
            factory.create(
                TileSpec.create(SCREEN_RECORD_TILE_SPEC),
                userActionInteractor,
                stateInteractor,
                mapper,
            )

        @Provides
        @SysUISingleton
        @RecordingControllerLog
        fun provideRecordingControllerLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("RecordingControllerLog", 50)
        }

        @Provides
        fun provideScreenRecordRepository(
            serviceRepository: Lazy<ScreenRecordingServiceRepository>,
            impl: Lazy<ScreenRecordRepositoryImpl>,
            screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
        ): ScreenRecordRepository {
            return if (screenCaptureRecordFeaturesInteractor.shouldShowNewRecordingToolbar) {
                    serviceRepository
                } else {
                    impl
                }
                .get()
        }
    }
}
