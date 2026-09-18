/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.brightness.shared

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableLogBufferFactory
import dagger.Module
import dagger.Provides

@Module
public interface BrightnessSharedModule {
    public companion object {
        @Provides
        @SysUISingleton
        @BrightnessLog
        public fun providesBrightnessTableLog(factory: TableLogBufferFactory): TableLogBuffer {
            return factory.create("BrightnessTableLog", 50)
        }

        @Provides
        @SysUISingleton
        @BrightnessLog
        public fun providesBrightnessLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("BrightnessLog", 50)
        }
    }
}
