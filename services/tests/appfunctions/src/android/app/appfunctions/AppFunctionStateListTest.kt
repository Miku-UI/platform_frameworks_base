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
package android.app.appfunctions

import android.app.appfunctions.flags.Flags
import android.os.Binder
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.ArraySet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionStateListTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun parcel() {
        val rawList =
            listOf(
                AppFunctionState(
                    AppFunctionName("com.example", "func1"),
                    true,
                    ArraySet(listOf(AppFunctionActivityId(Binder()))),
                ),
                AppFunctionState(
                    AppFunctionName("com.example", "func2"),
                    false,
                    ArraySet(listOf(AppFunctionActivityId(Binder()))),
                ),
            )
        val stateList = AppFunctionStateList(rawList)

        val restoredStateList = parcelAndUnparcel(stateList)

        // This assumes List and AppFunctionState equality is correct.
        assertThat(restoredStateList.list).isEqualTo(rawList)
    }

    private fun parcelAndUnparcel(original: AppFunctionStateList): AppFunctionStateList {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionStateList.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
