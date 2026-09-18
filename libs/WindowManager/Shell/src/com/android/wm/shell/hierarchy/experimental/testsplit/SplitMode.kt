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

package com.android.wm.shell.hierarchy.experimental.testsplit

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.window.TransitionInfo
import android.view.SurfaceControl
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.window.TaskCreationParams
import android.window.WindowAnimationState
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.core.graphics.toRect
import com.android.wm.shell.apptoweb.isBrowserApp
import com.android.wm.shell.common.ComponentUtils
import com.android.wm.shell.common.split.SplitScreenUtils
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.ViewOverlayContainer
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.modes.handheld.HandheldModeRequester
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.DetachResult
import com.android.wm.shell.transition.ITransitionAnimation
import java.io.PrintWriter

/** a basic Split-mode to prototype basic configurations of a split like 1x1, 2x1. */
class SplitMode(
    private val appContext: Context,
    private val hierarchy: ContainerHierarchy,
    private val modeRequester: HandheldModeRequester,
) : Mode {
    // Flag for enabling/disabling transitions.
    private val mUseTransitions = true
    private val rootsPerDisplay = mutableMapOf<Int, Container>()
    private lateinit var splitRoot: Container
    private val nodeRoots = mutableMapOf<Container, MutableList<Container>>()
    private val overlays = mutableMapOf<WindowContainerToken, ViewOverlayContainer>()
    private var isLeftRightSplit: Boolean = false
    private var buttonLaunch = false
    private val EXAMPLE_APPS =
        mutableListOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR),
        )

    val TAG = SplitMode::class.simpleName

    //TODO: make this per-display instance e.g. Map<Int, SplitdividerController>.
    private lateinit var splitDividerController: SplitDividerController

    private var activeContainerCount = 0

    /** @see Mode.prepareForDisplay */
    override fun prepareForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        Log.d(TAG, "prepareForDisplay: $display")
        val windowContext = display.displayProps().createWindowContext(appContext)
        val displayId = display.props<DisplayContainerProperties>().displayId
        // Create a top level root task
        var params =
            TaskCreationParams.Builder()
                .setName("SplitMode_$displayId")
                .setDisplayId(displayId)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
                .build()
        splitRoot = hierarchy.update.createTask(params, this)

        rootsPerDisplay[displayId] = splitRoot
        nodeRoots[splitRoot] = mutableListOf()

        // Determine orientation
        updateLeftRightSplit(display.props())

        val verticalDividerWindowManager = createDividerWindowManager("VerticalDivider")
        val horizontalDividerWindowManager = createDividerWindowManager("HorizontalDivider")

        splitDividerController =
            SplitDividerController(
                appContext,
                verticalDividerWindowManager,
                horizontalDividerWindowManager,
                object : SplitDividerController.DividerCallback {
                    override fun onLayoutNeeded(
                        finished: Boolean,
                        wct: WindowContainerTransaction,
                    ) {
                        val nodeRootContainers = nodeRoots[splitRoot]
                        if (nodeRootContainers != null && activeContainerCount > 0) {
                            val tokens =
                                nodeRootContainers.take(activeContainerCount).map { it.token }
                            val bounds = splitRoot?.props?.bounds
                            if (bounds != null) {
                                layoutChildren(bounds, tokens, wct, finished)
                            }
                        }
                        if (finished) {
                            hierarchy.update.wm("divider drag finish", TRANSIT_CHANGE, wct)
                        } else {
                            hierarchy.update.wm("divider drag", TRANSIT_NONE, wct)
                        }
                    }

                    override fun onDismiss(
                        indicesToDismiss: List<Int>,
                        wct: WindowContainerTransaction,
                    ) {
                        handleDismissInternal(indicesToDismiss, wct)
                    }

                    override fun getSplitRoot(): Container? = this@SplitMode.splitRoot

                    override fun getActiveContainerCount(): Int = this@SplitMode.activeContainerCount

                    override fun isLeftRightSplit(): Boolean = this@SplitMode.isLeftRightSplit
                },
            )

        // Create child node roots for leaf tasks/apps to go under
        val nodeRoots = nodeRoots[splitRoot]
        val wct = WindowContainerTransaction()

        // Ensure SplitRoot is brought to front/visible (optional?)
        wct.reorder(splitRoot.token, true)

        for (i in 0 until 3) {
            params =
                TaskCreationParams.Builder()
                    .setName("SplitMode_NodeRoot_${i}_display_$displayId")
                    .setDisplayId(displayId)
                    .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
                    .build()
            val nodeRoot = hierarchy.update.createTask(params)
            Log.d(TAG, "nodeRoot created: $nodeRoot ")
            nodeRoots?.add(nodeRoot)
            wct.reparent(nodeRoot.token, splitRoot.token, true)
        }

        Log.d(TAG, "nodeRoots: ${nodeRoots?.size} ")
        // Set second nodeRoot as launchAdjacentRoot
        wct.setAdjacentRoots(nodeRoots!![0].token, nodeRoots[1].token, nodeRoots[2].token)
        wct.setLaunchAdjacentFlagRoot(nodeRoots[1].token)
        hierarchy.update.wm("add child container", TRANSIT_CHANGE, wct)

        setupOverlay(updateContext, displayId, windowContext)
    }

    private fun updateLeftRightSplit(display: DisplayContainerProperties) {
        isLeftRightSplit =
            SplitScreenUtils.isLeftRightSplit(
                true /*allowLeftRightSplitInPortrait*/,
                display.config,
                display.displayId,
            )
    }

    private fun setupOverlay(updateContext: Mode.UpdateContext, displayId: Int, windowContext: Context) {
        // Create an overlay that can launch tasks into this root
        val overlay =
            ViewOverlayContainer(
                WindowContainerToken.createProxy(":split_mode_overlay"),
                { context, _ -> FrameLayout(context) },
                overrideWidth = 800,
                overrideHeight = 200,
            )
        overlay.parent = splitRoot
        overlay.initialize(windowContext)

        val newBounds = RectF(overlay.props.bounds)
        newBounds.offset(100f, 100f)

        val tx = updateContext.preTransitionTx!!
        overlay.surface.setBounds(tx, newBounds).setLayer(tx, Integer.MAX_VALUE).show(tx)
        overlays[splitRoot.token] = overlay

        val parent = overlay.rootView as ViewGroup
        parent.addView(
            createOverlayButton("1x0", Color.argb(128, 0, 255, 0), 0) {
                buttonLaunch = true
                removeAllContainers(displayId)
                addContainersAndLaunch(1)
            }
        )
        parent.addView(
            createOverlayButton("1x1", Color.argb(128, 100, 128, 0), 100) {
                buttonLaunch = true
                removeAllContainers(displayId)
                addContainersAndLaunch(2)
            }
        )
        parent.addView(
            createOverlayButton("2x1", Color.argb(128, 160, 0, 100), 200) {
                buttonLaunch = true
                removeAllContainers(displayId)
                addContainersAndLaunch(3)
            }
        )
        parent.addView(
            createOverlayButton("CLEAR", Color.argb(128, 255, 0, 0), 300) {
                buttonLaunch = true
                removeAllContainers(displayId)
            }
        )
        parent.addView(
            createOverlayButton("PIP", Color.argb(128, 0, 0, 255), 400, Color.WHITE) {
                buttonLaunch = true
                enterPipMode(displayId)
            }
        )
    }

    private fun createDividerWindowManager(name: String): SplitWindowManager {
        return SplitWindowManager(name, appContext, appContext.resources.configuration) { builder ->
            try {
                builder.setParent(splitRoot.leash)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parent for $name", e)
            }
        }
    }

    private fun createOverlayButton(
        label: String,
        color: Int,
        marginLeft: Int,
        textColor: Int = Color.BLACK,
        onClick: () -> Unit,
    ): FrameLayout {
        return FrameLayout(appContext).apply {
            layoutParams =
                FrameLayout.LayoutParams(100, MATCH_PARENT).apply { leftMargin = marginLeft }
            setBackgroundColor(color)
            addView(
                TextView(appContext).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                }
            )
            setOnClickListener { onClick() }
        }
    }

    private fun handleDismissInternal(
        indicesToDismiss: List<Int>,
        wct: WindowContainerTransaction,
    ) {
        val nodeRootContainers = nodeRoots[splitRoot] ?: return

        // Sort descending to remove safe
        val indices = indicesToDismiss.sortedDescending()
        val displayId =
            splitRoot?.let { it.taskProps().taskInfo.displayId } ?: -1
        if (displayId == -1) return
        val tda = HierarchyUtils.getTaskDisplayArea(hierarchy.root, displayId)
        for (indexWrapper in indices) {
            val index = indexWrapper
            if (index < nodeRootContainers.size) {
                val nodeToRemove = nodeRootContainers.removeAt(index)
                // Close apps in this node
                for (leafTask in nodeToRemove.children) {
                    if (isBrowserTask(leafTask)) {
                        Log.d(TAG, "Removing browser task: ${leafTask.name}")
                        wct.removeTask(leafTask.token)
                    } else {
                        wct.reparent(leafTask.token, tda?.token, false)
                    }
                }
                // Remove the node itself
                wct.removeTask(nodeToRemove.token)

                // Add back to end for future reuse
                nodeRootContainers.add(nodeToRemove)
            }
        }
        activeContainerCount -= indices.size
        if (activeContainerCount < 0) activeContainerCount = 0

        if (activeContainerCount == 1) {
            val remainingNode = nodeRootContainers[0]
            val children = remainingNode.children
            if (children.isNotEmpty()) {
                val taskToMove = children[0]

                // Reparent to default (display area) and set to fullscreen
                wct.reparent(taskToMove.token, tda?.token, true)
                activeContainerCount = 0
            }
        }

        // Update Dividers Visibility
        val bounds = splitRoot?.props?.bounds
        if (bounds != null) {
            splitDividerController.updateDividers(
                activeContainerCount,
                bounds.toRect(),
                isLeftRightSplit,
            )

            // Layout with remaining
            val tokens =
                nodeRootContainers.take(activeContainerCount).map { it.token }
            layoutChildren(bounds, tokens, wct, true)
        }
        hierarchy.update.wm("divider dismiss", TRANSIT_CHANGE, wct)
    }

    private fun enterPipMode(displayId: Int) {
        val nodeRootContainers = nodeRoots[splitRoot] ?: return
        // first leaf task (the task in 1x0) should move to PIP.
        var taskToPip: Container? = null
        for (root in nodeRootContainers) {
            if (root.children.isNotEmpty()) {
                taskToPip = root.children[0]
                break
            }
        }

        if (taskToPip != null) {
            val request = Mode.EnterRequestContext(displayId)
            val wct = modeRequester.requestEnterPipMode(taskToPip, request)
            wct?.let {
                cleanupSplitContent(displayId, it, excludeContainer = taskToPip)
                hierarchy.update.wm("Enter PIP", TRANSIT_CHANGE, it)
            }
        } else {
            Log.w(TAG, "No task available for PIP")
        }
    }

    private fun addContainersAndLaunch(containerCount: Int) {
        // Collect the node roots to add sample apps into
        val nodeRootContainers = nodeRoots[splitRoot]
        val tokenList = ArrayList<WindowContainerToken>()
        checkNotNull(nodeRootContainers)
        for (i in 0 until containerCount) {
            tokenList.add(nodeRootContainers[i].token)
        }
        Log.d(TAG, "launching ${tokenList.size} apps")
        val wct = WindowContainerTransaction()

        // Ensure SplitRoot is on top when we launch into it
        wct.reorder(splitRoot.token, true)

        val bounds = splitRoot.props.bounds
        splitDividerController.updateDividers(containerCount, bounds.toRect(), isLeftRightSplit)

        layoutChildren(bounds, tokenList, wct)
        hierarchy.update.wm("resize split node roots", TRANSIT_CHANGE, wct)
        launchExampleAppIntoContainer(tokenList)
    }

    private fun launchExampleAppIntoContainer(tokenList: List<WindowContainerToken>) {
        for (i in tokenList.indices) {
            val intent = EXAMPLE_APPS[i]
            val opts = ActivityOptions.makeBasic().apply { setLaunchRootTask(tokenList.get(i)) }
            intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent, opts.toBundle())
        }
    }

    private fun layoutChildren(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true,
    ) {
        activeContainerCount = childRoots.size
        Log.d(TAG, "layoutChildren $childRoots $activeContainerCount")
        Log.d(TAG, "$rootBounds isLeftRightSplit $isLeftRightSplit")

        if (childRoots.isEmpty()) return

        val childBounds = Array(childRoots.size) { RectF() }
        when (childRoots.size) {
            1 -> wct.setBounds(childRoots[0], rootBounds.toRect())
            2 -> layout1x1(rootBounds, childRoots, childBounds, wct, updateApps)
            3 -> layout2x1(rootBounds, childRoots, childBounds, wct, updateApps)
        }
    }

    private fun removeAllContainers(displayId: Int) {
        val root = rootsPerDisplay.getValue(displayId)
        val nodeRootContainers = nodeRoots.getValue(root)
        if (nodeRootContainers.isEmpty()) {
            Log.d(TAG, "No roots to remove")
            return
        }

        // Reparent all children outside of node containers to bottom of display
        val wct = WindowContainerTransaction()
        for (nodeRoot in nodeRootContainers) {
            for (leafTask in nodeRoot.children) {
                if (isBrowserTask(leafTask)) {
                    Log.d(TAG, "Removing browser task: ${leafTask.name}")
                    wct.removeTask(leafTask.token)
                } else {
                    Log.d(TAG, "reparenting ${leafTask.name}")
                }
            }
            wct.reparentTasks(
                nodeRoot.token,
                /*newParent*/ null,
                /*windowingMode*/ null,
                /*activityTypes*/ null,
                /*onTop*/ false,
            )
        }

        cleanupDivider()

        // Layout again
        wct.reorder(root.token, false, true)
        hierarchy.update.wm("remove child container", TRANSIT_TO_BACK, wct)
        activeContainerCount = 0
    }

    private fun cleanupSplitContent(
        displayId: Int,
        wct: WindowContainerTransaction,
        excludeContainer: Container? = null,
    ) {
        val root = rootsPerDisplay.getValue(displayId)
        val nodeRootContainers = nodeRoots.getValue(root)

        for (nodeRoot in nodeRootContainers) {
            for (leafTask in nodeRoot.children) {
                if (leafTask != excludeContainer) {
                    if (isBrowserTask(leafTask)) {
                        Log.d(TAG, "Removing browser task: ${leafTask.name}")
                        wct.removeTask(leafTask.token)
                    } else {
                        wct.reparent(leafTask.token, null, false)
                    }
                }
            }
        }

        cleanupDivider()

        // Layout again (move root to back if empty/leaving)
        if (excludeContainer == null) {
            wct.reorder(root.token, false, true)
        }
        activeContainerCount = 0
    }

    override fun containersChanged(
        updateContext: Mode.UpdateContext,
        enteringContainers: List<Container>,
        changedContainers: List<Container>,
        globalStateChanged: Boolean,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {
        val modeContainers =
            (enteringContainers + changedContainers)
                .groupBy { HierarchyUtils.getModeContainer(it) }
                .keys
                .filterNotNull()
        if (modeContainers.isEmpty()) {
            // Only global state changes (to update in the future)
            return
        }

        for (modeContainer in modeContainers) {
            var enteringContainersInRoot = enteringContainers.filter { it.parent == splitRoot }
            var changedContainersInRoot = changedContainers.filter { it.parent == splitRoot }
            var enteringContainersInNodeRoot =
                enteringContainers.filter { nodeRoots[splitRoot]?.contains(it.parent) == true }
            var changedContainersInNodeRoot =
                changedContainers.filter { nodeRoots[splitRoot]?.contains(it.parent) == true }
            if (
                enteringContainersInRoot.isEmpty() &&
                    changedContainersInRoot.isEmpty() &&
                    enteringContainersInNodeRoot.isEmpty() &&
                    changedContainersInNodeRoot.isEmpty()
            ) {
                Log.d(TAG, "containersChanged no valid changes")
                return
            }
            Log.d(
                TAG,
                "containersChanged ${modeContainer.name} button? $buttonLaunch\n" +
                    "enteringContainersInRoot: $enteringContainersInRoot\n" +
                    "changedContainersInRoot: $changedContainersInRoot\n" +
                    "enteringContainersInNodeRoot: $enteringContainersInNodeRoot\n" +
                    "changedContainersInNodeRoot: $changedContainersInNodeRoot",
            )

            if (enteringContainersInNodeRoot.isNotEmpty() && !buttonLaunch) {
                // We'll need to modify this in the future for different ways split is entered
                val currentNodeRoot: Container?
                var addedNodeRoot: Container? = null
                for (addedContainer in enteringContainersInNodeRoot) {
                    Log.d(
                        TAG,
                        "addedContainer: ${addedContainer.name} parent: ${addedContainer.parent?.name}",
                    )
                    val isValidContainer =
                        nodeRoots[splitRoot]?.contains(addedContainer) == false &&
                            addedContainer.parent != null
                    if (isValidContainer) {
                        Log.d(
                            TAG,
                            "node container? ${nodeRoots[splitRoot]?.contains(addedContainer) == true}",
                        )
                        addedNodeRoot = addedContainer.parent
                        break
                    }
                }

                currentNodeRoot =
                    nodeRoots[splitRoot]?.find { it.children.isNotEmpty() && it != addedNodeRoot }
                Log.d(TAG, "currentNodeRoot: $currentNodeRoot addedNodeRoot: $addedNodeRoot")
                if (currentNodeRoot != null && addedNodeRoot != null && activeContainerCount == 1) {
                    Log.d(TAG, "assuming launch adjacent")
                    // Def launch adjacent, only supporting from single fullscreen app for now
                    val wct = WindowContainerTransaction()
                    splitDividerController.updateDividers(
                        2,
                        splitRoot.props.bounds.toRect(),
                        isLeftRightSplit,
                    )
                    layoutChildren(
                        splitRoot.props.bounds,
                        listOf(currentNodeRoot.token, addedNodeRoot.token),
                        wct,
                    )
                    hierarchy.update.wm("resize split node roots launchAdj", TRANSIT_CHANGE, wct)
                }
                buttonLaunch = false
            }
        }

        if (animationPlan != null && mUseTransitions) {
            applySplitAnimation(enteringContainers + changedContainers, snapshot, animationPlan)
        }
    }

    private fun applySplitAnimation(
        containers: List<Container>,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan,
    ) {
        val splitContainers =
            containers.filter {
                val isSplitMode = HierarchyUtils.getMode(it) == this
                val isNotOverlay = !it.name.contains("overlay")
                val isNotNodeRoot = !it.name.contains("SplitMode_NodeRoot")
                val isLeaf = it.children.isEmpty()
                isSplitMode && isLeaf && isNotOverlay && isNotNodeRoot
            }
        if (splitContainers.isNotEmpty()) {
            val animation = SplitAnimation(splitContainers, snapshot)
            for (container in splitContainers) {
                animationPlan.setAnimation(container.token, animation)
            }
        }
    }

    override fun containersRemoved(
        updateContext: Mode.UpdateContext,
        leavingContainers: List<Container>,
        removedContainers: List<Container>,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {
        if (leavingContainers.isEmpty() && removedContainers.isEmpty()) {
            Log.d(TAG, "containersRemoved no valid changes")
            return
        }
        Log.d(
            TAG,
            "containersRemoved leavingContainers: $leavingContainers\n" +
                "removedContainers: $removedContainers",
        )
        if (animationPlan != null && mUseTransitions) {
            applySplitAnimation(leavingContainers, snapshot, animationPlan)
        }
    }

    /** @see Mode.cleanupForDisplay */
    override fun cleanupForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        val displayId = display.props<DisplayContainerProperties>().displayId
        val root = rootsPerDisplay.remove(displayId)
        hierarchy.update.removeTask(root!!)

        val overlay = overlays.remove(root.token)
        if (overlay != null) {
            val tx = updateContext.preTransitionTx!!
            overlay.release(tx)
            overlay.parent = null
        }
        cleanupDivider()
    }

    private fun cleanupDivider() {
        splitDividerController.cleanup()
    }

    /** @see Mode.requestUpdateForDisplayChange */
    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction,
    ) {
        Log.d(TAG, "requestUpdateForDisplayChange: $curDisplayProps -> $newDisplayProps")
        updateLeftRightSplit(newDisplayProps)
        val root = rootsPerDisplay.getValue(curDisplayProps.displayId)
        val childRoots =
            nodeRoots.getValue(root).filter { it.children.isNotEmpty() }.map { it.token }
        if (childRoots.isEmpty()) {
            Log.d(TAG, "No roots to remove")
            return
        }

        splitDividerController.updateDividers(
            childRoots.size,
            newDisplayProps.bounds.toRect(),
            isLeftRightSplit,
        )
        // Force update of divider position on rotation if needed
        // For prototype, just re-layout.
        layoutChildren(newDisplayProps.bounds, childRoots, wct)
    }

    /** @see Mode.requestEnterMode */
    override fun requestEnterMode(
        task: Container,
        request: Mode.EnterRequestContext,
        wct: WindowContainerTransaction,
    ): Boolean {
        Log.d(TAG, "requestEnter: $task")
        val displayId = request.displayId
        val root = rootsPerDisplay[displayId] ?: return false
        val nodeRootContainers = nodeRoots[root] ?: return false

        if (nodeRootContainers.isEmpty()) return false
        cleanupSplitContent(displayId, wct, excludeContainer = task)

        // Reparent the task back to the first node root
        val firstNodeRoot = nodeRootContainers[0]
        wct.reparent(task.token, firstNodeRoot.token, true)
        // clear any previously set bounds
        wct.setBounds(task.token, Rect())
        wct.reorder(root.token, true)

        // Layout as fullscreen app (1x0)
        val bounds = root.props.bounds
        splitDividerController.updateDividers(1, bounds.toRect(), isLeftRightSplit)
        layoutChildren(bounds, listOf(firstNodeRoot.token), wct)

        return true
    }

    private fun isBrowserTask(container: Container): Boolean {
        val taskProperties = container.props as? TaskContainerProperties ?: return false
        val taskInfo = taskProperties.taskInfo
        val packageName = ComponentUtils.getPackageName(taskInfo) ?: return false
        return isBrowserApp(appContext, packageName, taskInfo.userId)
    }

    override fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {
        val action = args.removeFirst()
        when (action.lowercase()) {
            "launch_new_activity" -> {
                removeAllContainers(displayId)
                addContainersAndLaunch(1)
            }

            "move_focused_task" -> {
                val rootProps = hierarchy.root.props<RootContainerProperties>()
                val taskId = rootProps.focusState.globallyFocusedTaskId
                val task = hierarchy.getTask(taskId)
                if (task == null) {
                    pw.println("No task with id=$taskId")
                    return
                }
                if (HierarchyUtils.getMode(task) == this) {
                    pw.println("Task id=$taskId is already associated with ${getId()}")
                    return
                }

                val wct = WindowContainerTransaction()
                requestEnterMode(task, Mode.EnterRequestContext(displayId), wct)
                hierarchy.update.wm(
                    "Moving focused task $taskId into ${getId()}",
                    TRANSIT_CHANGE,
                    wct,
                )
            }

            else -> {
                pw.println("Unknown action: $action")
            }
        }
    }

    private fun layout1x1(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true,
    ) {
        val dividerPosition =
            if (isLeftRightSplit) splitDividerController.getVerticalDividerPosition().toInt()
            else splitDividerController.getHorizontalDividerPosition().toInt()
        Log.d(TAG, "layout 1x1 dividerPos: $dividerPosition")

        if (isLeftRightSplit) {
            SplitWindowManager.splitOneVertical(rootBounds.toRect(), dividerPosition, childBounds)
        } else {
            SplitWindowManager.splitOneHorizontal(rootBounds.toRect(), dividerPosition, childBounds)
        }
        applyLayoutToChildren(childRoots, childBounds, wct, updateApps)
    }

    private fun layout2x1(
        rootBounds: RectF,
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean = true,
    ) {
        val horizontalPos = splitDividerController.getHorizontalDividerPosition().toInt()
        val verticalPos = splitDividerController.getVerticalDividerPosition().toInt()
        val boundsRect = rootBounds.toRect()

        if (isLeftRightSplit) {
            SplitWindowManager.splitTwoVerticalOneHorizontal(
                boundsRect,
                horizontalPos,
                verticalPos,
                childBounds,
            )
        } else {
            SplitWindowManager.splitTwoHorizontalOneVertical(
                boundsRect,
                horizontalPos,
                verticalPos,
                childBounds,
            )
        }
        applyLayoutToChildren(childRoots, childBounds, wct, updateApps)
    }

    private fun applyLayoutToChildren(
        childRoots: List<WindowContainerToken>,
        childBounds: Array<RectF>,
        wct: WindowContainerTransaction,
        updateApps: Boolean,
    ) {
        if (updateApps) {
            for ((i, container) in childRoots.withIndex()) {
                if (i < childBounds.size) {
                    wct.setBounds(container, childBounds[i].toRect())
                }
            }
        }

        // Update Dividers
        // TODO: This method should probably take the split root that its affecting
        val density = splitRoot.props.config.densityDpi
        val verticalPos = splitDividerController.getVerticalDividerPosition().toInt()
        val horizontalPos = splitDividerController.getHorizontalDividerPosition().toInt()

        splitDividerController.updateLayout(
            isLeftRightSplit,
            activeContainerCount,
            verticalPos,
            horizontalPos,
            density,
        )
    }

    private inner class SplitAnimation(
        val splitContainers: List<Container>,
        val snapshot: HierarchySnapshot,
    ) : ITransitionAnimation {
        override fun getDebugName() = "SplitAnimation"

        override fun detach(
            tokens: List<WindowContainerToken>,
            startTransaction: SurfaceControl.Transaction,
        ): DetachResult {
            val states =
                tokens.map { token ->
                    val container = splitContainers.find { it.token == token }
                    WindowAnimationState().apply {
                        if (container != null) {
                            bounds = container.props.bounds
                        }
                    }
                }
            return DetachResult(states)
        }

        override fun start(
            info: TransitionInfo,
            from: List<WindowAnimationState>,
            callback: ITransitionAnimation.IFinishedCallback,
        ) {
            // TODO: Use the `from` WindowAnimationState to seamlessly continue mid-animation
            //  transitions.
            Log.d(TAG, "SplitAnimation: starting animation")
            val targets =
                splitContainers.map { container ->
                    val startState = snapshot.snapshots[container]
                    val startBounds = startState?.props?.bounds
                    val endBounds = container.props.bounds
                    AnimTarget(
                        surf = container.leash,
                        startBounds = startBounds,
                        endBounds = endBounds,
                    )
                }

            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.duration = 700
            anim.interpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

            val tx = SurfaceControl.Transaction()
            anim.addUpdateListener { animation ->
                updateAnimation(tx, targets, animation.animatedValue as Float)
            }
            anim.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        finishAnimation(tx, targets)
                        callback.onFinished(null)
                    }
                }
            )
            anim.start()
        }
    }

    private fun updateAnimation(
        tx: SurfaceControl.Transaction,
        targets: List<AnimTarget>,
        fraction: Float,
    ) {
        for (target in targets) {
            val (surf, startBounds, endBounds) = target

            // If startBounds matches endBounds (e.g. Fullscreen -> Split 1x0), force slide up
            val isIdentity = startBounds != null && startBounds == endBounds

            if (startBounds == null || isIdentity) {
                // Entry Animation (1x0): Slide up from bottom
                val rootHeight = splitRoot.props.bounds.height()
                val startTop = rootHeight
                val currTop = startTop + (endBounds.top - startTop) * fraction
                Log.d(TAG, "Animation: setPosition y:$currTop")
                tx.setPosition(surf, endBounds.left, currTop)
                tx.setScale(surf, 1f, 1f)
                tx.setAlpha(surf, 1f)
                tx.show(surf)
                tx.setCrop(surf, null) // no crop
            }
        }
        tx.apply()
    }

    private fun finishAnimation(tx: SurfaceControl.Transaction, targets: List<AnimTarget>) {
        Log.d(TAG, "finishAnimation: targets=${targets.size}")
        for (target in targets) {
            Log.d(TAG, "finishAnimation: target=${target.surf} endBounds=${target.endBounds}")
            tx.setPosition(target.surf, target.endBounds.left, target.endBounds.top)
            tx.setScale(target.surf, 1f, 1f)
            tx.setAlpha(target.surf, 1f)
            tx.setCrop(target.surf, null)
            tx.show(target.surf)
        }
        tx.apply()
        tx.close()
    }

    private data class AnimTarget(
        val surf: SurfaceControl,
        val startBounds: RectF?,
        val endBounds: RectF,
    )
}
