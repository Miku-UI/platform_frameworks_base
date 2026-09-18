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

package com.android.systemui.notifications.ui

import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.notifications.ui.composable.ContentZOrderSorter
import com.android.systemui.notifications.ui.composable.HeadsUpPlaceholderContentPicker
import com.android.systemui.notifications.ui.composable.LowestZContentPicker
import com.android.systemui.notifications.ui.composable.StackPlaceholderContentPicker
import com.android.systemui.scene.sceneContainerConfig

val Kosmos.contentZOrderSorter by Fixture {
    ContentZOrderSorter(sceneContainerConfig)
}

val Kosmos.stackPlaceholderContentPicker by Fixture {
    StackPlaceholderContentPicker(contentZOrderSorter)
}

val Kosmos.lowestZContentPicker by Fixture {
    LowestZContentPicker(contentZOrderSorter)
}

val Kosmos.headsUpPlaceholderContentPicker by Fixture {
    HeadsUpPlaceholderContentPicker(lowestZContentPicker)
}

val Kosmos.notificationPlaceholderStateStorage by Fixture {
    NotificationPlaceholderStateStorage(
        headsUpPlaceholderContentPicker,
        stackPlaceholderContentPicker,
        dumpManager,
    )
}
