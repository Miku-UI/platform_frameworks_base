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

package com.android.systemui.communal.shared.model

import com.android.systemui.communal.ui.compose.sceneTransitions
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.ui.composable.ConstantSceneContainerTransitionsBuilder

val Kosmos.communalSceneContainerConfig by Fixture {
    SceneContainerConfig(
        sceneKeys = listOf(CommunalScenes.Blank, CommunalScenes.Communal),
        initialSceneKey = CommunalScenes.Blank,
        navigationDistances = mapOf(CommunalScenes.Blank to 0, CommunalScenes.Communal to 1),
        transitionsBuilder = ConstantSceneContainerTransitionsBuilder(sceneTransitions),
    )
}

val Kosmos.communalSceneDataSourceDelegator by Fixture {
    CommunalSceneDataSourceDelegator(
        applicationScope = applicationCoroutineScope,
        config = communalSceneContainerConfig,
    )
}
