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

package com.android.settingslib.spa.search.test

import android.os.Bundle
import com.android.settingslib.spa.search.SpaSearchLandingActivity

class TestSpaSearchLandingActivity : SpaSearchLandingActivity() {
    override fun isValidCall() = true

    override fun startSpaPage(destination: String, highlightItemKey: String) {
        startSpaPageCalledDestination = destination
        startSpaPageCalledHighlightItemKey = highlightItemKey
    }

    override fun startFragment(fragmentName: String, arguments: Bundle) {
        startFragmentCalledFragmentName = fragmentName
        startFragmentCalledArguments = arguments
    }

    companion object {
        var startSpaPageCalledDestination: String? = null
        var startSpaPageCalledHighlightItemKey: String? = null
        var startFragmentCalledFragmentName: String? = null
        var startFragmentCalledArguments: Bundle? = null

        fun clear() {
            startSpaPageCalledDestination = null
            startSpaPageCalledHighlightItemKey = null
            startFragmentCalledFragmentName = null
            startFragmentCalledArguments = null
        }
    }
}
