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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationManager
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.dagger.HighlightsHeader
import com.android.systemui.statusbar.notification.stack.BUCKET_HIGHLIGHTS
import javax.inject.Inject

/** Coordinator for the highlights section, derived from NotificationAssistantService adjustment. */
@CoordinatorScope
class HighlightsCoordinator
@Inject
constructor(@HighlightsHeader private val highlightsHeaderController: NodeController) :
    Coordinator {

    val highlightsSectioner =
        object : NotifSectioner("Highlights", BUCKET_HIGHLIGHTS) {
            override fun isInSection(entry: PipelineEntry?): Boolean {
                return (entry?.asListEntry()?.representativeEntry?.ranking?.importance
                    ?: 0) == NotificationManager.IMPORTANCE_MAX
            }

            override fun getHeaderNodeController(): NodeController {
                return highlightsHeaderController
            }
        }

    override fun attach(pipeline: NotifPipeline) {}
}
