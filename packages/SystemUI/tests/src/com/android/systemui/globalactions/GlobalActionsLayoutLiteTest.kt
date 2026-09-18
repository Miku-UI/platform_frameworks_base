/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.globalactions

import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.helper.widget.Flow
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy

@SmallTest
@RunWith(AndroidJUnit4::class)
class GlobalActionsLayoutLiteTest : SysuiTestCase() {

    private lateinit var layout: GlobalActionsLayoutLite
    private lateinit var flow: Flow

    @Before
    @Throws(Exception::class)
    fun setUp() {
        layout = spy(GlobalActionsLayoutLite(mContext, null))
        flow = Flow(mContext)
        flow.id = R.id.list_flow
        // We need to return the flow when findViewById is called
        doReturn(flow).`when`(layout).findViewById<View>(R.id.list_flow)
        // We also need to mock getListView() as it's used in addToListView -> super.addToListView
        val listView =
            object : ViewGroup(mContext) {
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
            }
        doReturn(listView).`when`(layout).getListView()
    }

    @Test
    fun testAddToListView_setsNextFocusForwardId() {
        val view1 = View(mContext)
        view1.id = 1
        val view2 = View(mContext)
        view2.id = 2
        val view3 = View(mContext)
        view3.id = 3

        layout.addToListView(view1, false)
        layout.addToListView(view2, false)
        layout.addToListView(view3, false)

        assertThat(view1.nextFocusForwardId).isEqualTo(view2.id)
        assertThat(view2.nextFocusForwardId).isEqualTo(view3.id)
    }

    @Test
    fun testAddToListView_handlesNoId() {
        val view = View(mContext)
        view.id = View.NO_ID

        layout.addToListView(view, false)

        // A new ID should have been generated
        assertThat(view.id).isNotEqualTo(View.NO_ID)
    }

    @Test
    fun testAddToListView_handlesDuplicateIds() {
        val view1 = View(mContext)
        view1.id = 1
        val view2 = View(mContext)
        view2.id = 1 // Duplicate ID

        layout.addToListView(view1, false)
        layout.addToListView(view2, false)

        // The second view should have been assigned a new ID
        assertThat(view2.id).isNotEqualTo(1)
        assertThat(view2.id).isNotEqualTo(View.NO_ID)

        // The first view should point to the new ID of the second view
        assertThat(view1.nextFocusForwardId).isEqualTo(view2.id)
    }

    @Test
    fun testRemoveAllListViews_resetsFocusChain() {
        val view1 = View(mContext)
        view1.id = 1
        layout.addToListView(view1, false)

        // This should reset mLastView
        layout.removeAllListViews()

        val view2 = View(mContext)
        view2.id = 2
        layout.addToListView(view2, false)

        // view1 should NOT point to view2 because the chain was reset
        assertThat(view1.nextFocusForwardId).isNotEqualTo(view2.id)
    }
}
