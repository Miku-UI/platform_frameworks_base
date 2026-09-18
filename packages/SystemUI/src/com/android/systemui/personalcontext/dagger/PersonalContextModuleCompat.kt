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

package com.android.systemui.personalcontext.dagger

import com.android.personalcontext.ace.visualizer.compat.CardInsightCompat
import com.android.personalcontext.ace.visualizer.compat.ClientSignalCompat
import com.android.personalcontext.ace.visualizer.compat.EmbeddedScrollCompat
import com.android.personalcontext.ace.visualizer.compat.EmptyRenderCompat
import com.android.personalcontext.ace.visualizer.compat.FlexFontCompat
import com.android.personalcontext.ace.visualizer.compat.InsightEventReporterCompat
import com.android.personalcontext.ace.visualizer.compat.PrototypeTransformCompat
import dagger.Module
import dagger.Provides

@Module
interface PersonalContextModuleCompat {
    companion object {

        @Provides
        fun provideEmptyRenderCompat(): EmptyRenderCompat {
            return object : EmptyRenderCompat {}
        }

        @Provides
        fun providePrototypeTransformCompat(): PrototypeTransformCompat {
            return object : PrototypeTransformCompat {}
        }

        @Provides
        fun provideEmbeddedScrollCompat(): EmbeddedScrollCompat {
            return object : EmbeddedScrollCompat {}
        }

        @Provides
        fun provideFlexFontCompat(): FlexFontCompat {
            return object : FlexFontCompat {}
        }

        @Provides
        fun provideInsightEventReporterCompat(): InsightEventReporterCompat {
            return object : InsightEventReporterCompat {}
        }

        @Provides
        fun provideCardInsightCompat(): CardInsightCompat {
            return object : CardInsightCompat {}
        }

        @Provides
        fun provideClientSignalCompat(): ClientSignalCompat {
            return object : ClientSignalCompat {}
        }
    }
}
