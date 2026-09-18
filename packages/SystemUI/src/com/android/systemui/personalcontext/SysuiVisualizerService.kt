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

package com.android.systemui.personalcontext

import android.content.Context
import android.service.personalcontext.RenderToken
import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService
import android.service.personalcontext.insight.PublishedContextInsight
import android.view.View
import com.android.personalcontext.ace.common.wrappers.wrap
import com.android.personalcontext.ace.visualizer.connector.VisualizerServiceConnector
import javax.inject.Inject

/**
 * Implements an {@link InsightSurfaceVisualizerService}, which is responsible for creating {@link
 * SurfaceView}s for remote clients when {@link ContextInsight}s are received.
 */
class SysuiVisualizerService
@Inject
constructor(private val connector: VisualizerServiceConnector) : InsightSurfaceVisualizerService() {

    override fun onClientConnected(info: InsightSurfaceClientInfo) {
        connector.onClientConnected(info)
    }

    override fun onCreateEmbeddedView(
        context: Context,
        publishedInsight: PublishedContextInsight,
        renderToken: RenderToken?,
        info: InsightSurfaceClientInfo,
    ): View? {
        return connector.onCreateEmbeddedView(
            context,
            publishedInsight.wrap(),
            renderToken?.wrap(),
            info.wrap(),
        )
    }

    override fun onClientUpdated(
        oldClientInfo: InsightSurfaceClientInfo,
        newClientInfo: InsightSurfaceClientInfo,
    ): Boolean {
        return connector.onClientUpdated(oldClientInfo.wrap(), newClientInfo.wrap())
    }

    override fun onClientDisconnected(info: InsightSurfaceClientInfo) {
        connector.onClientDisconnected(info.wrap())
    }
}
