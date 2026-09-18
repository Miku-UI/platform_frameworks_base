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
package com.android.wm.shell.hierarchy.updates

import android.app.ActivityManager
import android.content.Context
import android.content.pm.UserInfo
import android.graphics.Rect
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.InsetsState
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.DisplayAreaOrganizer
import android.window.TaskAppearedInfo
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.policy.FoldLockSettingsObserver
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.ActivityContainerProperties
import com.android.wm.shell.hierarchy.properties.ContainerProperties
import com.android.wm.shell.hierarchy.properties.DeviceState
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.properties.WallpaperContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.KeyguardChangeListener
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.ITransitionPlanner
import com.android.wm.shell.transition.Transitions

/**
 * Helper class to update the container hierarchy from transitions or task organizer (for root tasks
 * only).
 */
class HierarchyUpdater(
    // This is only used for reading FW resources & settings
    private val appContext: Context,
    private val shellController: ShellController,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val transitions: Transitions,
    private val displayInsetsController: DisplayInsetsController,
    private val deviceStateManager: DeviceStateManager,
    private val hierarchy: ContainerHierarchy,
    private val formFactorModes: FormFactorModes,
    shellInit: ShellInit,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    @ShellMainThread private val mainHandler: Handler,
) {
    // For testing use only
    var updaterTestHook: UpdaterTestHook? = null

    // We only use this to calculate the folded state without duplicating DSM logic
    @VisibleForTesting
    lateinit var foldStateListener: DeviceStateManager.FoldStateListener

    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        if (!com.android.window.flags.Flags.transitMixpatcherBase()) {
            Log.e("ShellModes", "Expected mixpatcher base flag to be enabled")
        }
        shellController.addUserChangeListener(UserChangeUpdater())
        shellController.addKeyguardChangeListener(KeyguardStateUpdater())
        transitions.addPlanner(ContainerHierarchyMixpatcherHandler())
        shellTaskOrganizer.setContainerHierarchyCreateRootTaskListener(
            ContainerHierarchyRootTaskHook()
        )
        shellTaskOrganizer.addTaskInfoChangedListener(ContainerHierarchyTaskInfoListener())
        displayInsetsController.addGlobalInsetsChangedListener(DisplayContainerInsetsUpdater())
        foldStateListener = DeviceStateManager.FoldStateListener(appContext)
        deviceStateManager.registerCallback(mainExecutor, DeviceStateUpdater())
        val foldSettings = FoldSettingsUpdater(appContext, mainHandler)
        foldSettings.register()
    }

    /**
     * Common logic for notifying modes, and MUST be called after the hierarchy is updated.
     */
    fun notifyModes(
        updateContext: Mode.UpdateContext,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan? = null,
    ) {
        ProtoLog.v(WM_SHELL_MODES, "Hierarchy updated requested: %s", updateContext.reason)
        updaterTestHook?.onHierarchyUpdated()

        // If a transaction was not provided, then create one now for convenience and apply it
        // after notifying the modes
        var preTransitTx: SurfaceControl.Transaction? = null
        if (updateContext.preTransitionTx == null) {
            preTransitTx = SurfaceControl.Transaction()
            updateContext.preTransitionTx = preTransitTx
        }

        // Iterate the pre-existing containers from bottom-up to notify old modes that their
        // descendants have been removed
        val postUpdateContainers = hierarchy.toContainerList()
        val postUpdateContainersSet = postUpdateContainers.toSet()
        val leavingContainersByMode = snapshot.preUpdateContainers
            // Ignore containers that aren't in a mode (should generally not happen unless there
            // is no root)
            .filter {
                val oldMode = snapshot.snapshots[it]!!.mode
                oldMode != null && oldMode != HierarchyUtils.getMode(it)
            }
            .groupBy { snapshot.snapshots[it]!!.mode!! }
        for ((oldMode, leaving) in leavingContainersByMode) {
            val removed = leaving.filter { it !in postUpdateContainersSet }
            ProtoLog.v(WM_SHELL_MODES, "\tLeaving: %s from %s", leaving, oldMode.getId())
            if (removed.isNotEmpty()) {
                ProtoLog.v(WM_SHELL_MODES, "\tRemoving: %s from %s", removed, oldMode.getId())
            }
            oldMode.containersRemoved(
                updateContext,
                leaving,
                removed,
                snapshot,
                animationPlan
            )
        }

        // After pre-existing containers' modes have been notified, notify the modes globally for
        // displays have been removed
        val preUpdateDisplays =
            snapshot.preUpdateContainers.filter { it.props is DisplayContainerProperties }
        val postUpdateDisplays =
            postUpdateContainers.filter { it.props is DisplayContainerProperties }
        val removedDisplays = preUpdateDisplays - postUpdateDisplays
        for (display in removedDisplays) {
            val modes = formFactorModes.getAvailableModesForDisplay(
                display.displayProps().displayId
            )
            for (mode in modes) {
                mode.cleanupForDisplay(updateContext, display)
            }
        }

        // Before new containers' modes  have been notified, notify the modes globally for displays
        // that have been added
        val addedDisplays = postUpdateDisplays - preUpdateDisplays
        for (display in addedDisplays) {
            val modes = formFactorModes.getAvailableModesForDisplay(
                display.displayProps().displayId
            )
            for (mode in modes) {
                mode.prepareForDisplay(updateContext, display)
            }
        }

        // Update each mode with containers that have changed either along its ancestor path or
        // in the set of containers it manages
        val postUpdateContainersByMode = postUpdateContainers
            // Ignore containers that aren't in a mode (should generally not happen unless there
            // is no root)
            .filter { HierarchyUtils.getMode(it) != null }
            .groupBy { HierarchyUtils.getMode(it)!! }
        for ((mode, containers) in postUpdateContainersByMode) {
            val modeContainers = containers.groupBy { HierarchyUtils.getModeContainer(it) }.keys
            val globalStateChanged = modeContainers
                .any {
                    snapshot.snapshots.containsKey(it!!) &&
                            snapshot.getChangedAncestors(it).isNotEmpty()
                }
            val entering = containers.filter {
                val oldMode = snapshot.snapshots[it]?.mode
                oldMode == null || oldMode != mode
            }
            val changed = (containers - entering.toSet()).filter { snapshot.hasChanges(it) }
            if (!entering.isEmpty() || !changed.isEmpty() || globalStateChanged) {
                if (!entering.isEmpty()) {
                    ProtoLog.v(WM_SHELL_MODES, "\tAdding: %s to %s", entering, mode.getId())
                }
                if (!changed.isEmpty()) {
                    ProtoLog.v(WM_SHELL_MODES, "\tChanged: %s in %s", changed, mode.getId())
                }
                if (globalStateChanged) {
                    ProtoLog.v(WM_SHELL_MODES, "\tGlobal state changed")
                }
                mode.containersChanged(
                    updateContext,
                    entering,
                    changed,
                    globalStateChanged,
                    snapshot,
                    animationPlan
                )
            }
        }
        updaterTestHook?.onModesNotified()

        // Apply the tx created above if necessary
        preTransitTx?.apply()

        // Dump the container requested, or the full hierarchy
        if (updateContext.dumpOnlyContainer != null) {
            HierarchyDebugUtils.dumpContainer(updateContext.dumpOnlyContainer!!, snapshot)
        } else {
            HierarchyDebugUtils.dumpHierarchy(hierarchy, snapshot)
        }
    }

    fun handleCreateRootTask(
        appearedInfo: TaskAppearedInfo,
        name: String
    ): Container {
        val token = appearedInfo.taskInfo.token
        val existingContainer = hierarchy.getContainer(token)
        if (existingContainer != null) {
            // Skip if we've already updated the hierarchy for this window container. This happens
            // if we've already updated the hierarchy when creating the root task.
            return existingContainer
        }
        ProtoLog.v(
            WM_SHELL_MODES,
            "Adding root task into hierarchy: %d",
            appearedInfo.taskInfo.taskId
        )

        // Create the task in the hierarchy
        val parentTaskId = appearedInfo.taskInfo.parentTaskId
        val displayId = appearedInfo.taskInfo.displayId
        val parentContainer = if (parentTaskId > 0) hierarchy.getTask(parentTaskId)
            else hierarchy.getTaskDisplayArea(displayId)
        val createdTask =
            Container(token, TaskContainerProperties(appearedInfo.taskInfo)).apply {
                this.name += " ($name)"
                parent = parentContainer
                leash = appearedInfo.leash
                props.config.setTo(appearedInfo.taskInfo.configuration)
                if (mode != null) {
                    this.mode = mode
                }
            }
        return createdTask
    }

    fun handleRemoveRootTask(token: WindowContainerToken) {
        ProtoLog.v(
            WM_SHELL_MODES,
            "Removing root task from hierarchy: %s",
            token
        )

        // Remove the task from the hierarchy
        val container = hierarchy.getContainer(token)
        if (container != null) {
            container.parent = null
        }
    }

    fun handleTaskInfoChanged(taskInfo: ActivityManager.RunningTaskInfo) {
        val container = hierarchy.getContainer(taskInfo.token)
        if (container == null || container.props !is TaskContainerProperties) {
            return
        }

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        container.taskProps().updateFromTaskInfoChanged(taskInfo)

        if (snapshot.hasChanges(container)) {
            notifyModes(
                Mode.UpdateContext(
                    reason = "${container.name} info change",
                    dumpOnlyContainer = container
                ), snapshot
            )
        }
    }

    fun handleTransition(
        transition: IBinder,
        plan: AnimationPlan,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction
    ): HierarchySnapshot {
        ProtoLog.v(
            WM_SHELL_MODES,
            "Updating hierarchy from transition: %d",
            info.debugId
        )

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Update the root first
        hierarchy.root.rootProps().updateFromTransition(info)

        // Update the rest of the hierarchy
        // We iterate the changes in reverse order to handle parent containers first (especially
        // when new containers are being added)
        val reversedChanges = info.changes.reversed()
        for (change in reversedChanges) {
            updateHierarchyFromChange(change)
        }

        // Notify all the modes
        notifyModes(
            Mode.UpdateContext(
                reason = "Transition #${info.debugId}",
                preTransitionTx = startTransaction
            ),
            snapshot,
            plan
        )

        if (HierarchyUtils.hasTemporaryAnimatingContainers(hierarchy.root)) {
            // If there are temporary containers, then schedule them to be cleaned up after all
            // transitions finish playing
            transitions.runOnIdle {
                ProtoLog.v(
                    WM_SHELL_MODES,
                    "Cleaning up temporary animating containers from transition: %d",
                    info.debugId
                )
                val snapshot = HierarchySnapshot(hierarchy.toContainerList())
                HierarchyUtils.removeAllTemporaryAnimatingContainers(hierarchy.root)
                notifyModes(
                    Mode.UpdateContext(reason = "Removing temporary animating containers"),
                    snapshot
                )
            }
        }
        return snapshot
    }

    fun handleDisplayInsetsChanged(displayId: Int, insetsState: InsetsState) {
        val display = hierarchy.getDisplay(displayId)
        if (display == null) {
            return
        }

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        if (display.displayProps().updateInsetsState(insetsState)) {
            notifyModes(Mode.UpdateContext(reason = "${display.name} insets changed"), snapshot)
        }
    }

    /**
     * Updates the containers in the hierarchy based on changes in the given transition.
     */
    private fun updateHierarchyFromChange(
        change: TransitionInfo.Change,
    ) {
        // Validate the change
        val token = change.container
        if (token == null) {
            ProtoLog.v(WM_SHELL_MODES, "\tChange skipped (no window token): %s", change)
            return
        }

        var container = hierarchy.getContainer(token)

        // If the container is being removed, just remove it from the hierarchy without updating
        if (TransitionUtil.isClosingMode(change.mode)) {
            if (container != null && change.mode == WindowManager.TRANSIT_CLOSE) {
                container.parent = null
                ProtoLog.v(WM_SHELL_MODES, "\tRemoving: %s", container.name)
                return
            } else if (container == null) {
                // Already removed
                return
            }
        }

        // Create the container if necessary
        if (container == null) {
            container = createContainerFromChange(change)
            ProtoLog.v(WM_SHELL_MODES, "\tAdding: %s", container.name)
        }

        // Apply the change to the container
        container.updateFromWindowChange(hierarchy.root, change)

        when (change.mode) {
            WindowManager.TRANSIT_TO_FRONT -> {
                container.parent!!.reorderChild(container, /* toTop= */ true)
                ProtoLog.v(WM_SHELL_MODES, "\tMoving to front: %s", container.name)
            }

            WindowManager.TRANSIT_TO_BACK -> {
                container.parent!!.reorderChild(container, /* toTop= */ false)
                ProtoLog.v(WM_SHELL_MODES, "\tMoving to back: %s", container.name)
            }

            WindowManager.TRANSIT_CHANGE -> {
                ProtoLog.v(WM_SHELL_MODES, "\tChanging: %s", container.name)
            }
        }
    }

    /**
     * Creates a container for the given change.
     * Does NOT add the container to the hierarchy.
     * TODO: Directly map this to the container type once we have explicit types in the info
     */
    private fun createContainerFromChange(
        change: TransitionInfo.Change,
    ): Container {
        val displayId = change.endDisplayId
        if (change.hasFlags(TransitionInfo.FLAG_IS_DISPLAY)) {
            return Container(
                change.container!!,
                DisplayContainerProperties(displayId)
            )
        } else if (change.hasFlags(TransitionInfo.FLAG_IS_TASK_DISPLAY_AREA)) {
            return Container(
                change.container!!,
                DisplayAreaContainerProperties(
                    DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
                )
            )
        } else if (change.taskInfo != null) {
            return Container(
                change.container!!,
                TaskContainerProperties(change.taskInfo!!)
            )
        } else if ((change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
            // TODO: Certain containers (like wallpaper) don't have valid tokens yet, we should
            //  either fix that or map those specific containers by flag
            return Container(
                change.container!!,
                WallpaperContainerProperties()
            )
        }

        // Other container types are temporary in the hierarchy
        if (change.activityTransitionInfo != null) {
            return Container(
                change.container!!,
                ActivityContainerProperties(change.activityTransitionInfo!!.taskId)
            ).apply {
                isTemporaryAnimatingContainer = true
            }
        } else {
            return Container(
                change.container!!,
                ContainerProperties()
            ).apply {
                isTemporaryAnimatingContainer = true
            }
        }
    }

    /**
     * Updates the hierarchy from a display change signal to give modes an opportunity to update
     * their tasks in final state before the display change completes.
     *
     * The caller is responsible for verifying that there is a given display container for the
     * provided displayId.
     */
    fun updateDisplay(
        displayId: Int,
        displayLayout: DisplayLayout,
        outWct: WindowContainerTransaction
    ) {
        val display = hierarchy.getDisplay(displayId)!!
        ProtoLog.v(WM_SHELL_MODES, "Updating display: %s", display.name)

        // Apply the display changes to the hierarchy first
        val newDisplayProps = display.displayProps().copy()
        val chg = TransitionInfo.Change(display.token, display.leash).apply {
            setEndAbsBounds(Rect(0, 0, displayLayout.width(), displayLayout.height()))
            setRotation(display.props.rotation, displayLayout.rotation())
        }
        newDisplayProps.updateFromWindowChange(chg)

        // Notify the modes associated with this display (ancestors & descendants) of the change
        // FUTURE: This currently assumes the displays exist and are rooted to the hierarchy
        val containers = listOf(hierarchy.root) + HierarchyUtils.toContainersList(display)
        for (c in containers) {
            if (c.mode != null) {
                c.mode!!.requestUpdateForDisplayChange(
                    c,
                    display.displayProps(),
                    newDisplayProps,
                    outWct
                )
            }
        }
    }

    /**
     * Updates the hierarchy when the current user in the system changes.
     */
    fun handleUserChanged(newUserId: Int) {
        if (hierarchy.root.rootProps().userState.currentUserId == newUserId) {
            return
        }
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        hierarchy.root.rootProps().userState.currentUserId = newUserId
        notifyModes(Mode.UpdateContext(reason = "Update current user: $newUserId"), snapshot)
    }

    /**
     * Updates the hierarchy when the set of profiles for the current user changes. This always
     * follows `handleUserChanged()` when switching users.
     */
    fun handleUserProfilesChanged(profiles: List<UserInfo>) {
        if (hierarchy.root.rootProps().userState.currentUserProfiles == profiles) {
            return
        }
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        hierarchy.root.rootProps().userState.currentUserProfiles.clear()
        hierarchy.root.rootProps().userState.currentUserProfiles.addAll(profiles)
        notifyModes(Mode.UpdateContext(reason = "Update current user profiles"), snapshot)
    }

    /**
     * Updates the hierarchy whenever the keyguard visibility changes.
     */
    fun handleKeyguardVisibilityChanged(keyguardState: DeviceState.KeyguardState) {
        if (hierarchy.root.rootProps().deviceState.keyguardState == keyguardState) {
            return
        }
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        hierarchy.root.rootProps().deviceState.keyguardState = keyguardState
        notifyModes(Mode.UpdateContext(reason = "Update keyguard state: $keyguardState"), snapshot)
    }

    /**
     * Updates the hierarchy whenever the folded state changes.
     */
    fun handleDeviceStateChanged(state: android.hardware.devicestate.DeviceState) {
        foldStateListener.onDeviceStateChanged(state)
        val isFolded = foldStateListener.folded ?: false

        if (hierarchy.root.rootProps().deviceState.isFolded == isFolded) {
            return
        }
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        hierarchy.root.rootProps().deviceState.isFolded = isFolded
        notifyModes(Mode.UpdateContext(reason = "Update folded state: $isFolded"), snapshot)
    }

    /**
     * Updates the on-fold global setting.
     */
    fun handleOnFoldSettingsChanged(setting: DeviceState.OnFoldSetting) {
        // This setting is generally used on demand, so for now we can just update the state for
        // when it is next used
        hierarchy.root.rootProps().deviceState.onFoldSetting = setting
        ProtoLog.v(WM_SHELL_MODES, "Hierarchy updated requested: On fold setting=%s", setting)
    }

    //
    // Hooks
    //

    /** Hooks into Mixpatcher transition planning. */
    private inner class ContainerHierarchyMixpatcherHandler : ITransitionPlanner {
        override fun plan(
            plan: AnimationPlan,
            fullInfo: TransitionInfo,
            transition: IBinder,
            plannableInfo: TransitionInfo,
            startTransaction: SurfaceControl.Transaction
        ) {
            handleTransition(transition, plan, fullInfo, startTransaction)
        }

        override fun getDebugName(): String {
            return "ContainerHierarchy"
        }
    }

    /** Hooks into ShellTaskOrganizer to listen for changes to root tasks. */
    private inner class ContainerHierarchyRootTaskHook
        : ShellTaskOrganizer.ContainerHierarchyRootTaskListener {
        override fun onRootTaskCreated(appearedInfo: TaskAppearedInfo, name: String) {
            handleCreateRootTask(appearedInfo, name)
        }

        override fun onRootTaskRemoved(token: WindowContainerToken) {
            handleRemoveRootTask(token)
        }
    }

    /** Hooks into ShellTaskOrganizer to listen for task info changes. */
    private inner class ContainerHierarchyTaskInfoListener : ShellTaskOrganizer.TaskInfoChangedListener {
        override fun onTaskInfoChanged(taskInfo: ActivityManager.RunningTaskInfo?) {
            handleTaskInfoChanged(taskInfo!!)
        }
    }

    /** Hooks into display insets updates from WM. */
    private inner class DisplayContainerInsetsUpdater : DisplayInsetsController.OnInsetsChangedListener {
        /** @see DisplayInsetsController.OnInsetsChangedListener.insetsChanged */
        override fun insetsChanged(displayId: Int, insetsState: InsetsState?) {
            handleDisplayInsetsChanged(displayId, insetsState!!)
        }
    }

    /** Hooks into user changes. */
    private inner class UserChangeUpdater : UserChangeListener {
        override fun onUserChanged(newUserId: Int, userContext: Context) {
            handleUserChanged(newUserId)
        }

        override fun onUserProfilesChanged(profiles: List<UserInfo?>) {
            handleUserProfilesChanged(profiles.filterNotNull())
        }
    }

    /** Hooks into lockscreen changes. */
    private inner class KeyguardStateUpdater : KeyguardChangeListener {
        override fun onKeyguardVisibilityChanged(
            visible: Boolean,
            occluded: Boolean,
            animatingDismiss: Boolean
        ) {
            val keyguardState = if (visible && occluded) {
                DeviceState.KeyguardState.Occluded
            } else if (visible) {
                DeviceState.KeyguardState.Locked
            } else {
                DeviceState.KeyguardState.Unlocked
            }
            handleKeyguardVisibilityChanged(keyguardState)
        }
    }

    /** Hooks into device state changes. */
    private inner class DeviceStateUpdater : DeviceStateManager.DeviceStateCallback {
        override fun onDeviceStateChanged(state: android.hardware.devicestate.DeviceState) {
            handleDeviceStateChanged(state)
        }
    }

    /** Hooks into fold-settings changes. */
    private inner class FoldSettingsUpdater(
        appContext: Context,
        mainHandler: Handler
    ) : FoldLockSettingsObserver(mainHandler, appContext) {
        override fun onChange(selfChange: Boolean) {
            // The super implementation actually saves the last state
            super.onChange(selfChange)

            val onFoldSetting = if (isStayAwakeOnFold) {
                DeviceState.OnFoldSetting.StayAwake
            } else if (isSelectiveStayAwake) {
                DeviceState.OnFoldSetting.SelectiveStayAwake
            } else {
                DeviceState.OnFoldSetting.Sleep
            }
            handleOnFoldSettingsChanged(onFoldSetting)
        }
    }

    /**
     * Only for testing. Hooks into the update path to allow tests to validate the update after
     * specific stages.
     */
    interface UpdaterTestHook {
        fun onHierarchyUpdated() {}
        fun onModesNotified() {}
    }
}