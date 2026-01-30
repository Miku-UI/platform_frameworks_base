/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.dagger

import com.android.systemui.statusbar.notification.stack.ui.view.NotificationRowStatsLogger
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationStatsLogger
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationStatsLoggerImpl
import dagger.Binds
import dagger.Module

@Module
interface NotificationStatsLoggerModule {

    /** Binds an implementation to the [NotificationStatsLogger]. */
    @Binds fun bindsStatsLoggerImpl(impl: NotificationStatsLoggerImpl): NotificationStatsLogger

    /** Binds [NotificationStatsLogger] to [NotificationRowStatsLogger]. */
    @Binds fun bindRowStatsLogger(logger: NotificationStatsLogger): NotificationRowStatsLogger
}
