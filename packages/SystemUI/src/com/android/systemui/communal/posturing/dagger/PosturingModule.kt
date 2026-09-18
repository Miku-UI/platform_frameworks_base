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

package com.android.systemui.communal.posturing.dagger

import android.content.Context
import com.android.systemui.Flags.aospPostureDetection
import com.android.systemui.communal.posturing.data.repository.NoOpPosturingRepository
import com.android.systemui.communal.posturing.data.repository.PostureDetectionAlgorithm
import com.android.systemui.communal.posturing.data.repository.PosturingRepository
import com.android.systemui.communal.posturing.data.repository.PosturingRepositoryImpl
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Provider

/** Module providing a reference implementation of the posturing signal. */
@Module
interface PosturingModule {

    companion object {
        @SysUISingleton
        @Provides
        fun providePosturingRepository(
            noopImplProvider: Provider<NoOpPosturingRepository>,
            implProvider: Provider<PosturingRepositoryImpl>,
        ): PosturingRepository {
            return if (aospPostureDetection()) {
                implProvider.get()
            } else {
                noopImplProvider.get()
            }
        }

        @Provides
        @Named(PostureDetectionAlgorithm.POSTURE_DETECTION_FLAT_ANGLE_TH)
        fun providePostureDetectionFlatAngleThreshold(context: Context): Int {
            return context.resources
                .getInteger(R.integer.config_postureDetectionFlatAngleThreshold)
                .also {
                    require(it in 0..90) {
                        "config_postureDetectionFlatAngleThreshold must be between 0 and 90 degrees, but was $it"
                    }
                }
        }

        @Provides
        @Named(PostureDetectionAlgorithm.POSTURE_DETECTION_UPRIGHT_ANGLE_TH)
        fun providePostureDetectionUprightAngleThreshold(context: Context): Int {
            return context.resources
                .getInteger(R.integer.config_postureDetectionUprightAngleThreshold)
                .also {
                    require(it in 0..90) {
                        "config_postureDetectionUprightAngleThreshold must be between 0 and 90 degrees, but was $it"
                    }
                }
        }
    }
}
