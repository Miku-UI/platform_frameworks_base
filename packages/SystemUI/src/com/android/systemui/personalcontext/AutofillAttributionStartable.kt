/*
 * Copyright 2026 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.insight.PublishedContextInsightWrapper
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

@SysUISingleton
class AutofillAttributionStartable
@Inject
constructor(
    private val broadcastDispatcher: BroadcastDispatcher,
    private val personalContextManager: PersonalContextManager?,
) : CoreStartable, BroadcastReceiver() {
    override fun start() {
        if (personalContextManager == null) {
            return
        }
        broadcastDispatcher.registerReceiver(
            receiver = this,
            filter = IntentFilter(ACTION_ATTRIBUTION_TRIGGERED),
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ATTRIBUTION_TRIGGERED) {
            Log.e(TAG, "onReceive: invalid action ${intent.action}")
            return
        }

        if (personalContextManager == null) {
            Log.e(TAG, "onReceive: personalContextManager not present on device")
            return
        }

        val insight =
            intent.getParcelableExtra(
                EXTRA_AUTOFILL_INSIGHT,
                PublishedContextInsightWrapper::class.java,
            )
        val renderToken = intent.getParcelableExtra(EXTRA_RENDER_TOKEN, RenderToken::class.java)
        val index = intent.getIntExtra(EXTRA_INSIGHT_INDEX, 0)
        if (insight == null || renderToken == null || !intent.hasExtra(EXTRA_INSIGHT_INDEX)) {
            Log.e(
                TAG,
                "onReceive: insight ($insight), render token ($renderToken), or index not found",
            )
            return
        }

        personalContextManager.reportInsightEvent(
            insight.publishedContextInsight,
            InsightEvent.EVENT_USER_ATTRIBUTION_REQUESTED.packValue(index),
            renderToken,
        )
    }

    /**
     * Packs the [value] parameter into the upper 16 bits of the receiver (`this`). The receiver's
     * existing lower 16 bits are preserved.
     */
    // TODO(b/493276395): use the utilities in frameworks/libs/systemui/ace/
    fun Int.packValue(value: Int): Int {
        val maskedLower = this and 0xFFFF
        val shiftedUpper = value shl 16
        return shiftedUpper or maskedLower
    }

    companion object {
        private const val TAG = "AutofillAttributionStar"

        const val ACTION_ATTRIBUTION_TRIGGERED = "action_attribution_triggered"
        const val EXTRA_AUTOFILL_INSIGHT = "autofill_insight"
        const val EXTRA_RENDER_TOKEN = "render_token"
        const val EXTRA_INSIGHT_INDEX = "insight_index"

        /**
         * Creates an intent to broadcast back to SysUI containing the necessary info to trigger
         * attribution.
         *
         * @param insight the published insight to request attribution for
         * @param renderToken
         */
        fun getAttributionIntent(
            context: Context,
            insight: PublishedContextInsight,
            renderToken: RenderToken,
            index: Int,
        ): Intent {
            return Intent(ACTION_ATTRIBUTION_TRIGGERED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_AUTOFILL_INSIGHT, PublishedContextInsightWrapper(insight))
                .putExtra(EXTRA_RENDER_TOKEN, renderToken)
                .putExtra(EXTRA_INSIGHT_INDEX, index)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        }
    }
}
