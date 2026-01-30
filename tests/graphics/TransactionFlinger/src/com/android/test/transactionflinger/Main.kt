/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.test.transactionflinger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.android.test.transactionflinger.activities.BlurOnOffActivity
import com.android.test.transactionflinger.activities.BlurRegionActivity
import com.android.test.transactionflinger.activities.MovingLayerActivity
import com.android.test.transactionflinger.activities.TrivialActivity
import kotlin.reflect.KClass

class Demo(val name: String, val makeIntent: (Context) -> Intent) {
    constructor(name: String, activity: KClass<out Activity>) : this(name, { context ->
        Intent(context, activity.java)
    })
}

data class DemoGroup(val groupName: String, val demos: List<Demo>)

private val AllDemos = listOf(
    DemoGroup(
        "Workloads", listOf(
            Demo("TrivialActivity", TrivialActivity::class),
            Demo("BlurOnOffActivity", BlurOnOffActivity::class),
            Demo("BlurRegionActivity", BlurRegionActivity::class),
            Demo("MovingLayerActivity", MovingLayerActivity::class)
        )
    )
)

/**
 * Main entry point when manually opening the app
 */
class Main : ComponentActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = ExpandableListView(this)
        list.setFitsSystemWindows(true)

        setContentView(list)

        val inflater = LayoutInflater.from(this)
        list.setAdapter(object : BaseExpandableListAdapter() {
            override fun getGroup(groupPosition: Int): DemoGroup {
                return AllDemos[groupPosition]
            }

            override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

            override fun hasStableIds(): Boolean = true

            override fun getGroupView(
                groupPosition: Int,
                isExpanded: Boolean,
                convertView: View?,
                parent: ViewGroup?
            ): View {
                val view = (convertView ?: inflater.inflate(
                    android.R.layout.simple_expandable_list_item_1, parent, false
                )) as TextView
                view.text = AllDemos[groupPosition].groupName
                return view
            }

            override fun getChildrenCount(groupPosition: Int): Int {
                return AllDemos[groupPosition].demos.size
            }

            override fun getChild(groupPosition: Int, childPosition: Int): Demo {
                return AllDemos[groupPosition].demos[childPosition]
            }

            override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

            override fun getChildView(
                groupPosition: Int,
                childPosition: Int,
                isLastChild: Boolean,
                convertView: View?,
                parent: ViewGroup?
            ): View {
                val view = (convertView ?: inflater.inflate(
                    android.R.layout.simple_expandable_list_item_1, parent, false
                )) as TextView
                view.text = AllDemos[groupPosition].demos[childPosition].name
                return view
            }

            override fun getChildId(groupPosition: Int, childPosition: Int): Long {
                return (groupPosition.toLong() shl 32) or childPosition.toLong()
            }

            override fun getGroupCount(): Int {
                return AllDemos.size
            }
        })

        list.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            val demo = AllDemos[groupPosition].demos[childPosition]
            startActivity(demo.makeIntent(this))
            return@setOnChildClickListener true
        }

        AllDemos.forEachIndexed { index, _ -> list.expandGroup(index) }
    }
}
