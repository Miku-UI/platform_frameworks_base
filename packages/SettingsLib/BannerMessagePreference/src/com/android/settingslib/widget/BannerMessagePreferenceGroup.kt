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

package com.android.settingslib.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.View
import android.view.animation.PathInterpolator
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.preference.banner.R
import kotlin.math.max
import kotlin.math.min

/**
 * Custom PreferenceGroup that allows expanding and collapsing child [BannerMessagePreference]s.
 */
class BannerMessagePreferenceGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin, CustomAnimated {

    private class GroupSection(
        val list: MutableList<BannerMessagePreference> = mutableListOf(),
        var isExpanded: Boolean = false,
        var expandPref: NumberButtonPreference? = null,
        var collapsePref: SectionButtonPreference? = null
    )

    private val activeSection = GroupSection()
    private val dismissedSection = GroupSection()

    private var parentRecyclerView: RecyclerView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var expandKey: String? = null
    private var expandTitle: CharSequence? = null
    private var collapseKey: String? = null
    private var collapseTitle: CharSequence? = null
    private var collapseIcon: Drawable? = null

    private var showDismissedPreferences = false

    private var expandDismissedKey: String? = null
    private var expandDismissedTitle: String? = null
    private var collapseDismissedKey: String? = null
    private var collapseDismissedTitle: String? = null
    private var collapseDismissedIcon: Drawable? = null
    private var isAnimating = false

    var visiblePreferencesWhenCollapsedCount: Int = DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED
        set(value) {
            field = max(1, value)
            updateVisibilities()
            updateCollapsedItemCount()
        }

    private val collapsiblePreferenceCount
        get() = max(activeSection.list.size - visiblePreferencesWhenCollapsedCount, 0)

    var expandContentDescription: Int = 0
        set(value) {
            field = value
            activeSection.expandPref?.btnContentDescription = expandContentDescription
        }

    var expandDismissedContentDescription: Int = 0
        set(value) {
            field = value
            dismissedSection.expandPref?.btnContentDescription = expandDismissedContentDescription
        }

    init {
        isPersistent = false
        layoutResource = R.layout.settingslib_banner_message_preference_group
        initAttributes(context, attrs, defStyleAttr)
    }

    fun addPreference(preference: Preference, isDismissed: Boolean): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        // CASE 1: Standard Active Banner
        if (!isDismissed) {
            return addPreference(preference)
        }

        // CASE 2: Dismissed Banner
        if (showDismissedPreferences) {
            addDismissedPreference(preference)
            super.addPreference(preference)
            return true
        }

        // CASE 3: Dismissed, but dismissed section is disabled
        return false
    }

    override fun addPreference(preference: Preference): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        if (activeSection.list.size >= MAX_BANNERS_COUNT) {
            return false
        }

        if (activeSection.list.contains(preference)) {
            return true
        }

        val shouldBeVisible = activeSection.isExpanded ||
                activeSection.list.size < visiblePreferencesWhenCollapsedCount

        preference.isVisible = shouldBeVisible

        if (shouldBeVisible) {
            preference.signalExpand()
        }

        var wasAdded = false

        wasAdded = super.addPreference(preference)

        if (wasAdded) {
            activeSection.list.add(preference)
            maybeCreateExpandCollapsePreference()
            updateCollapsedItemCount()
        }
        return wasAdded
    }

    override fun removePreference(preference: Preference): Boolean {
        return removePreference(preference, moveToDismissed = true)
    }

    fun removePreference(preference: Preference, moveToDismissed: Boolean): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        // Scenario 1: Removing an ACTIVE preference
        if (activeSection.list.contains(preference)) {
            var wasRemoved = false
            wasRemoved = super.removePreference(preference)
            if (wasRemoved) {
                activeSection.list.remove(preference)
                updateCollapsedItemCount()
                updateVisibilities()

                if (showDismissedPreferences && moveToDismissed) {
                    addDismissedPreference(preference)
                    super.addPreference(preference)
                }
            }
            return wasRemoved
        }

        // Scenario 2: Removing a DISMISSED preference
        if (dismissedSection.list.contains(preference)) {
            var wasRemoved = false
            wasRemoved = super.removePreference(preference)

            if (wasRemoved) {
                dismissedSection.list.remove(preference)
                dismissedSection.expandPref?.let { it.count = dismissedSection.list.size }
                updateDismissedChildrenVisibility()
            }
            return wasRemoved
        }

        return false
    }

    override fun removeAll() {
        activeSection.list.forEach {
            super.removePreference(it)
        }
        activeSection.list.clear()
        updateCollapsedItemCount()
        updateVisibilities()
        if (showDismissedPreferences) {
            for (i in 0..<dismissedSection.list.size) {
                val child = dismissedSection.list[i]
                super.removePreference(child)
            }
            dismissedSection.list.clear()
            dismissedSection.expandPref?.let { it.count = dismissedSection.list.size }
            updateDismissedChildrenVisibility()
        }
    }

    private fun updateVisibilities() {
        activeSection.list.sortBy { it.order }

        var visibleCount = 0
        activeSection.list.forEach { child ->
            val shouldShow =
                activeSection.isExpanded || visibleCount < visiblePreferencesWhenCollapsedCount

            if (shouldShow) {
                child.signalExpand()
                visibleCount++
            } else {
                child.animateDismiss()
            }
        }

        if (!isAnimating) {
            activeSection.expandPref?.isVisible =
                !activeSection.isExpanded && collapsiblePreferenceCount > 0
            activeSection.collapsePref?.isVisible =
                activeSection.isExpanded && collapsiblePreferenceCount > 0
        }
    }

    private fun toggleExpansion(anchorView: View?) {
        handler.removeCallbacksAndMessages(null)
        val areAnimsEnabled = areAnimationsEnabled()

        isAnimating = true

        if (!activeSection.isExpanded) {
            activeSection.isExpanded = true
            activeSection.expandPref?.isVisible = false
            activeSection.collapsePref?.isVisible = true

            activeSection.list.forEach { child ->
                child.isVisible = true
            }

            isAnimating = false
            activeSection.list.forEach { child ->
                if (areAnimsEnabled) child.signalExpand()
            }
        } else {
            val recyclerView = getParentRecyclerView(anchorView)
            activeSection.list.sortBy { it.order }

            val keepCount = visiblePreferencesWhenCollapsedCount
            var isFenceVisible = false

            if (collapsiblePreferenceCount > 0) {
                activeSection.collapsePref?.isVisible = false
                activeSection.expandPref?.isVisible = true
            }

            if (recyclerView != null && activeSection.list.isNotEmpty()) {
                val adapter = recyclerView.adapter as? PreferenceGroupAdapter
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

                if (adapter != null && layoutManager != null) {
                    val lastKeeperIndex = min(keepCount, activeSection.list.size) - 1

                    if (lastKeeperIndex >= 0) {
                        val lastKeeperItem = activeSection.list[lastKeeperIndex]
                        val fencePos = adapter.getPreferenceAdapterPosition(lastKeeperItem)

                        if (fencePos != RecyclerView.NO_POSITION) {
                            isFenceVisible = layoutManager.findViewByPosition(fencePos) != null

                            if (!isFenceVisible) {
                                val firstItem = activeSection.list.firstOrNull()
                                if (firstItem != null) {
                                    val startPos = adapter.getPreferenceAdapterPosition(firstItem)
                                    val scroller = ForcedStartSmoothScroller(context)
                                    scroller.targetPosition = startPos
                                    layoutManager.startSmoothScroll(scroller)
                                }
                            }
                        }
                    }
                }
            }

            val totalVictims = activeSection.list.size - keepCount
            val lastAnimatedIndex = keepCount + totalVictims - 1

            val startDelay =
                if (!isFenceVisible && areAnimsEnabled) 20L else 0L

            handler.postDelayed({
                activeSection.list.forEachIndexed { index, child ->
                    val bannerDelay = if (areAnimationsEnabled()) index * 75L else 0L

                    if (index >= keepCount) {
                        handler.postDelayed({
                            if (index == lastAnimatedIndex) {
                                child.signalCollapse(false) {
                                    activeSection.isExpanded = false

                                    activeSection.list.forEachIndexed { i, p ->
                                        if (i >= keepCount) {
                                            p.isVisible = false
                                        }
                                    }

                                    isAnimating = false
                                }
                            } else {
                                child.signalCollapse(false, null)
                            }
                        }, bannerDelay)
                    }
                }
            }, startDelay)
        }
    }

    private fun getParentRecyclerView(view: View?): RecyclerView? {
        if (this.parentRecyclerView != null && this.parentRecyclerView!!.isAttachedToWindow) {
            return this.parentRecyclerView
        }

        var parent = view?.parent
        while (parent != null) {
            if (parent is RecyclerView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    override fun setTitle(title: CharSequence?) {
        expandTitle = title
    }

    fun setCollapseTitle(title: CharSequence?) {
        collapseTitle = title
    }

    fun removePreferenceRecursively(key: CharSequence, moveToDismissed: Boolean): Boolean {
        val preference = findPreference<Preference>(key) ?: return false
        return removePreference(preference, moveToDismissed)
    }

    override fun removePreferenceRecursively(key: CharSequence): Boolean {
        val preference = findPreference<Preference>(key) ?: return false
        return removePreference(preference, true)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()

        val myState = SavedState(AbsSavedState.EMPTY_STATE)

        myState.realSuperState = superState
        myState.isActiveExpanded = activeSection.isExpanded
        myState.isDismissedExpanded = dismissedSection.isExpanded
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state == null || state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.realSuperState)
        activeSection.isExpanded = state.isActiveExpanded
        dismissedSection.isExpanded = state.isDismissedExpanded

        refreshUi()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        if (parentRecyclerView == null) {
            parentRecyclerView = getParentRecyclerView(holder.itemView)
        }

        refreshUi()
    }

    private fun refreshUi() {
        maybeCreateExpandCollapsePreference()
        updateVisibilities()

        if (showDismissedPreferences) {
            maybeCreateExpandCollapseDismissedPreference()
            updateDismissedChildrenVisibility()
        }
    }

    private fun addDismissedPreference(preference: BannerMessagePreference) {
        if (!showDismissedPreferences) {
            return
        }

        if (dismissedSection.list.contains(preference)) {
            val index = dismissedSection.list.indexOf(preference)
            preference.order = EXPAND_DISMISSED_ORDER + index
            return
        }

        // LIMIT CHECK: FIFO (Remove oldest if full)
        if (dismissedSection.list.size >= MAX_BANNERS_COUNT) {
            val oldestPreference = dismissedSection.list[0]
            dismissedSection.list.removeAt(0)

            if (dismissedSection.isExpanded) {
                oldestPreference.signalCollapse(false) {
                    handler.post { super.removePreference(oldestPreference) }
                }
            } else {
                super.removePreference(oldestPreference)
            }
        }

        dismissedSection.list.add(preference)
        dismissedSection.list.forEachIndexed { index, pref ->
            pref.order = EXPAND_DISMISSED_ORDER + index
        }
        maybeCreateExpandCollapseDismissedPreference()
        dismissedSection.expandPref?.let { it.count = dismissedSection.list.size }
        preference.isVisible = dismissedSection.isExpanded
        if (dismissedSection.isExpanded) {
            preference.signalExpand()
        }
        updateDismissedChildrenVisibility()
    }

    private fun maybeCreateExpandCollapsePreference() {
        if (collapsiblePreferenceCount > 0) {
            if (activeSection.expandPref == null) {
                activeSection.expandPref = NumberButtonPreference(context).apply {
                    key = expandKey
                    title = expandTitle
                    count = collapsiblePreferenceCount
                    btnContentDescription = expandContentDescription
                    order = EXPAND_ORDER
                    clickListener = View.OnClickListener { v -> toggleExpansion(v) }
                    isVisible = !activeSection.isExpanded
                    super.addPreference(this)
                }
            }
            if (activeSection.collapsePref == null) {
                activeSection.collapsePref = SectionButtonPreference(context).apply {
                    key = collapseKey
                    title = collapseTitle
                    icon = collapseIcon
                    order = COLLAPSE_ORDER
                    setOnClickListener { v -> toggleExpansion(v) }
                    isVisible = activeSection.isExpanded
                    super.addPreference(this)
                }
            }
        }
    }

    private fun maybeCreateExpandCollapseDismissedPreference() {
        if (dismissedSection.list.size > 0) {
            if (dismissedSection.expandPref == null) {
                dismissedSection.expandPref =
                    NumberButtonPreference(context).apply {
                        key = expandDismissedKey
                        title = expandDismissedTitle
                        count = dismissedSection.list.size
                        btnContentDescription = expandDismissedContentDescription
                        order = EXPAND_DISMISSED_ORDER
                        clickListener = View.OnClickListener { v -> toggleDismissedExpansion(v) }
                    }
                super.addPreference(dismissedSection.expandPref!!)
            }
            if (dismissedSection.collapsePref == null) {
                dismissedSection.collapsePref =
                    SectionButtonPreference(context).apply {
                        key = collapseDismissedKey
                        title =
                            if (collapseDismissedTitle == null) collapseTitle
                            else collapseDismissedTitle
                        icon =
                            if (collapseDismissedIcon == null) collapseIcon
                            else collapseDismissedIcon
                        order = COLLAPSE_DISMISSED_ORDER
                        setOnClickListener { v -> toggleDismissedExpansion(v) }
                    }
                super.addPreference(dismissedSection.collapsePref!!)
            }
        }
    }

    private fun updateCollapsedItemCount() {
        activeSection.expandPref?.count = collapsiblePreferenceCount
        activeSection.expandPref?.let {
            it.count = collapsiblePreferenceCount
        }
    }

    private fun updateDismissedChildrenVisibility() {
        for (i in 0..<dismissedSection.list.size) {
            val child = dismissedSection.list[i]
            child.isVisible = dismissedSection.isExpanded
        }

        dismissedSection.expandPref?.isVisible =
            !dismissedSection.isExpanded && dismissedSection.list.isNotEmpty()
        dismissedSection.collapsePref?.isVisible =
            dismissedSection.isExpanded && dismissedSection.list.isNotEmpty()
    }

    private fun toggleDismissedExpansion(anchorView: View?) {
        handler.removeCallbacksAndMessages(null)

        if (!dismissedSection.isExpanded) {
            dismissedSection.isExpanded = true
            dismissedSection.expandPref?.isVisible = false
            dismissedSection.collapsePref?.isVisible = true
            dismissedSection.list.forEach { child ->
                child.isVisible = true
                child.signalExpand()
            }
            return
        }

        val recyclerView = getParentRecyclerView(anchorView)
        dismissedSection.list.sortBy { it.order }

        val anchorItem: Preference? = if (activeSection.collapsePref?.isVisible == true) {
            activeSection.collapsePref
        } else if (activeSection.expandPref?.isVisible == true) {
            activeSection.expandPref
        } else if (activeSection.list.isNotEmpty()) {
            activeSection.list.last()
        } else {
            dismissedSection.list.firstOrNull()
        }

        var isFenceVisible = false

        if (recyclerView != null && anchorItem != null) {
            val adapter = recyclerView.adapter as? PreferenceGroupAdapter
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

            if (adapter != null && layoutManager != null) {
                val fencePos = adapter.getPreferenceAdapterPosition(anchorItem)
                if (fencePos != RecyclerView.NO_POSITION) {
                    val fenceView = layoutManager.findViewByPosition(fencePos)
                    isFenceVisible = fenceView != null &&
                            (layoutManager.getDecoratedTop(fenceView) >= 0)

                    if (!isFenceVisible) {
                        val scroller = ForcedStartSmoothScroller(context)
                        scroller.targetPosition = fencePos
                        layoutManager.startSmoothScroll(scroller)
                    }
                }
            }
        }

        dismissedSection.isExpanded = false
        dismissedSection.collapsePref?.isVisible = false
        dismissedSection.expandPref?.isVisible = true

        dismissedSection.expandPref?.order = COLLAPSE_DISMISSED_ORDER

        val totalVictims = dismissedSection.list.size
        val lastAnimatedIndex = totalVictims - 1
        val areAnimsEnabled = areAnimationsEnabled()
        val startDelay = if (!isFenceVisible && areAnimsEnabled) 200L else 0L

        handler.postDelayed({
            dismissedSection.list.forEachIndexed { index, child ->
                val bannerDelay = if (areAnimationsEnabled()) index * 1L else 0L

                handler.postDelayed({
                    if (index == lastAnimatedIndex) {
                        child.signalCollapse(false) {
                            dismissedSection.isExpanded = false

                            dismissedSection.list.forEach { it.isVisible = false }
                            dismissedSection.expandPref?.order = EXPAND_DISMISSED_ORDER
                        }
                    } else {
                        child.signalCollapse(false, null)
                    }
                }, bannerDelay)
            }
        }, startDelay)
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.BannerMessagePreferenceGroup, defStyleAttr, 0
        ).apply {
            expandKey = getString(R.styleable.BannerMessagePreferenceGroup_expandKey)
            expandTitle = getString(R.styleable.BannerMessagePreferenceGroup_expandTitle)
            collapseKey = getString(R.styleable.BannerMessagePreferenceGroup_collapseKey)
            collapseTitle = getString(R.styleable.BannerMessagePreferenceGroup_collapseTitle)
            collapseIcon = getDrawable(R.styleable.BannerMessagePreferenceGroup_collapseIcon)
            showDismissedPreferences =
                getBoolean(R.styleable.BannerMessagePreferenceGroup_showDismissedPreferences, false)
            expandDismissedKey =
                getString(R.styleable.BannerMessagePreferenceGroup_expandDismissedKey)
            expandDismissedTitle =
                getString(R.styleable.BannerMessagePreferenceGroup_expandDismissedTitle)
            collapseDismissedKey =
                getString(R.styleable.BannerMessagePreferenceGroup_collapseDismissedKey)
            collapseDismissedTitle =
                getString(R.styleable.BannerMessagePreferenceGroup_collapseDismissedTitle)
            collapseDismissedIcon =
                getDrawable(R.styleable.BannerMessagePreferenceGroup_collapseDismissedIcon)
            recycle()
        }
    }

    private fun areAnimationsEnabled(): Boolean {
        val durationScale = android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return durationScale > 0f
    }

    private class SavedState : Preference.BaseSavedState {
        var isActiveExpanded: Boolean = false
        var isDismissedExpanded: Boolean = false
        var realSuperState: Parcelable? = null

        constructor(source: Parcel) : super(source) {
            isActiveExpanded = source.readInt() == 1
            isDismissedExpanded = source.readInt() == 1
            realSuperState = source.readParcelable(javaClass.classLoader)
        }

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(if (isActiveExpanded) 1 else 0)
            dest.writeInt(if (isDismissedExpanded) 1 else 0)
            dest.writeParcelable(realSuperState, flags)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_VISIBLE_PREFERENCES_WHEN_COLLAPSED = 1
        private const val MAX_BANNERS_COUNT = 10
        private const val EXPAND_ORDER = 1000
        private const val COLLAPSE_ORDER = 2000
        private const val EXPAND_DISMISSED_ORDER = 10000
        private const val COLLAPSE_DISMISSED_ORDER = 20000
    }
}

/**
 * A LinearSmoothScroller that strictly enforces SNAP_TO_START and calculates
 * exact scroll offsets, ensuring the target view aligns precisely with the top container padding.
 */
private class ForcedStartSmoothScroller(context: Context) : LinearSmoothScroller(context) {
    override fun getVerticalSnapPreference(): Int {
        return SNAP_TO_START
    }

    override fun calculateTimeForScrolling(dx: Int): Int {
        return 300
    }

    override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
        val layoutManager = layoutManager ?: return 0
        val params = view.layoutParams as RecyclerView.LayoutParams
        val viewTop = layoutManager.getDecoratedTop(view) - params.topMargin
        val containerTop = layoutManager.paddingTop
        return viewTop - containerTop
    }

    override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
        val dy = calculateDyToMakeVisible(targetView, SNAP_TO_START)
        if (Math.abs(dy) > 0) {
            action.update(0, dy, 400, SCROLL_INTERPOLATOR)
        }
    }

    companion object {
        private val SCROLL_INTERPOLATOR = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}