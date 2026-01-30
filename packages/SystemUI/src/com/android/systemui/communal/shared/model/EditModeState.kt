/*
 * Copyright (C) 2024 The Android Open Source Project
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

/**
 * Models the state of the edit mode activity. Used to chain the animation during the transition
 * between the hub on communal scene, and the edit mode activity after unlocking the keyguard.
 */
enum class EditModeState(val value: Int) {
    // Received intent to start edit mode. User may need to authenticate first.
    STARTING(0),
    // Edit mode activity created. SystemUI starts showing a background to obscure the activity
    // launching below.
    CREATED(1),
    // Edit mode activity fully launched and now ready to show. This is a signal to SystemUI for
    // transitions to start.
    READY_TO_SHOW(2),
    // Edit mode activity is showing.
    SHOWING(3),
}
