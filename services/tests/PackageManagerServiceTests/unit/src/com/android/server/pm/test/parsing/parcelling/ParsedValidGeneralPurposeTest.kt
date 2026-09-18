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

package com.android.server.pm.test.parsing.parcelling

import com.android.internal.pm.pkg.component.ParsedValidGeneralPurpose
import com.android.internal.pm.pkg.component.ParsedValidGeneralPurposeImpl
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class ParsedValidGeneralPurposeTest : ParcelableComponentTest(
    ParsedValidGeneralPurpose::class,
    ParsedValidGeneralPurposeImpl::class
) {
    override val defaultImpl =
        ParsedValidGeneralPurposeImpl("purpose", 20)
    override val creator = ParsedValidGeneralPurposeImpl.CREATOR

    override val baseParams = listOf(
        ParsedValidGeneralPurpose::getName,
        ParsedValidGeneralPurpose::getMaxTargetSdkVersion,
    )

    override fun initialObject() =
        ParsedValidGeneralPurposeImpl("purpose", 20)
}
