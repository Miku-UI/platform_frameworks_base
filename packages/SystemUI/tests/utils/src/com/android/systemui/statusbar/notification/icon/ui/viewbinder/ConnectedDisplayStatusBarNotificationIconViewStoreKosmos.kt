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

package com.android.systemui.statusbar.notification.icon.ui.viewbinder

import android.content.testableContext
import com.android.systemui.display.domain.interactor.displayWindowPropertiesInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.notification.collection.mockNotifCollection
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.statusbar.notification.icon.iconManager

var Kosmos.connectedDisplaysStatusBarNotificationIconViewStoreFactory:
    ConnectedDisplaysStatusBarNotificationIconViewStore.Factory by
    Kosmos.Fixture {
        object : ConnectedDisplaysStatusBarNotificationIconViewStore.Factory {
            override fun create(
                displayId: Int
            ): ConnectedDisplaysStatusBarNotificationIconViewStore {
                return ConnectedDisplaysStatusBarNotificationIconViewStore(
                    displayId,
                    mockNotifCollection,
                    iconManager,
                    displayWindowPropertiesInteractor,
                    notifPipeline,
                )
            }
        }
    }

var Kosmos.connectedDisplaysStatusBarNotificationIconViewStore:
    ConnectedDisplaysStatusBarNotificationIconViewStore by
    Kosmos.Fixture {
        connectedDisplaysStatusBarNotificationIconViewStoreFactory.create(testableContext.displayId)
    }
