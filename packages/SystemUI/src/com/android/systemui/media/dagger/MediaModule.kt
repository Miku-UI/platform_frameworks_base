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
package com.android.systemui.media.dagger

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.media.controls.domain.MediaDomainModule
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.ui.controller.MediaCarouselController
import com.android.systemui.media.controls.ui.controller.MediaCarouselControllerLogger
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.controller.MediaHostStatesManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.controls.ui.view.MediaHost.MediaHostStateHolder
import com.android.systemui.media.dream.dagger.MediaComplicationComponent
import com.android.systemui.media.remedia.data.MediaDataModule
import com.android.systemui.media.remedia.ui.MediaUiModule
import com.android.systemui.media.taptotransfer.receiver.MediaTttReceiverLogBuffer
import com.android.systemui.media.taptotransfer.sender.MediaTttSenderLogBuffer
import dagger.Module
import dagger.Provides
import javax.inject.Named

/** Dagger module for the media package. */
@Module(
    includes = [MediaDomainModule::class, MediaDataModule::class, MediaUiModule::class],
    subcomponents = [MediaComplicationComponent::class],
)
interface MediaModule {
    companion object {
        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(QS_PANEL)
        fun providesQSMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(QUICK_QS_PANEL)
        fun providesQuickQSMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(KEYGUARD)
        fun providesKeyguardMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(DREAM)
        fun providesDreamMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        /**  */
        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(COMMUNAL_HUB)
        fun providesCommunalMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        @JvmStatic
        @Provides
        @SysUISingleton
        @Named(POPUP)
        fun providesPopupMediaHost(
            stateHolder: MediaHostStateHolder,
            hierarchyManager: MediaHierarchyManager,
            dataManager: MediaDataManager,
            statesManager: MediaHostStatesManager,
            carouselController: MediaCarouselController,
            logger: MediaCarouselControllerLogger,
        ): MediaHost {
            return MediaHost(
                stateHolder,
                hierarchyManager,
                dataManager,
                statesManager,
                carouselController,
                logger,
            )
        }

        /**
         * Provides a logging buffer related to the media tap-to-transfer chip on the sender device.
         */
        @JvmStatic
        @Provides
        @SysUISingleton
        @MediaTttSenderLogBuffer
        fun provideMediaTttSenderLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaTttSender", 30)
        }

        /**
         * Provides a logging buffer related to the media tap-to-transfer chip on the receiver
         * device.
         */
        @JvmStatic
        @Provides
        @SysUISingleton
        @MediaTttReceiverLogBuffer
        fun provideMediaTttReceiverLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("MediaTttReceiver", 20)
        }

        const val QS_PANEL: String = "media_qs_panel"
        const val QUICK_QS_PANEL: String = "media_quick_qs_panel"
        const val KEYGUARD: String = "media_keyguard"
        const val DREAM: String = "dream"
        const val COMMUNAL_HUB: String = "communal_Hub"
        const val POPUP: String = "popup"
    }
}
