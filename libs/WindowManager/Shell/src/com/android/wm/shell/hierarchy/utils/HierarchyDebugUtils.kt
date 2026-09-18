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
package com.android.wm.shell.hierarchy.utils

import android.view.Surface
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.updates.EMPTY_SNAPSHOT
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_BOUNDS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_CHILDREN
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_FOCUS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_INSETS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_IS_FOLDED
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_KEYGUARD
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_MODE
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_PARENT
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_PIP_PARAMS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_ROTATION
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_TASK_DESCRIPTION
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER_PROFILES
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_VISIBILITY
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_WINDOWING_MODE
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.ROOT_CONTAINER_OFFSET
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES

/**
 * Utility functions to support debugging the hierarchy.
 */
class HierarchyDebugUtils {
    companion object {
        val UNDERLINE = "\u001b[4m"
        val RED = "\u001b[31m"
        val GREEN = "\u001b[32m"
        val YELLOW = "\u001b[33m"
        val PURPLE = "\u001b[35m"
        val WHITE = "\u001b[37m"
        val NONE = "\u001b[0m"

        /**
         * Returns a dumps of the hierarchy.
         */
        fun getHierarchyDump(
            hierarchy: ContainerHierarchy,
            snapshot: HierarchySnapshot = EMPTY_SNAPSHOT,
        ): String {
            return dumpToString(hierarchy.root, hierarchy.root, "", true, snapshot, WHITE)
        }

        /**
         * Dumps the hierarchy to protolog.
         */
        fun dumpHierarchy(
            hierarchy: ContainerHierarchy,
            snapshot: HierarchySnapshot = EMPTY_SNAPSHOT,
        ) {
            dumpContainer(hierarchy.root, snapshot)
        }

        /**
         * Dumps the container & children to protolog.
         */
        fun dumpContainer(
            container: Container,
            snapshot: HierarchySnapshot = EMPTY_SNAPSHOT,
        ) {
            @SuppressWarnings("ProtoLogNoContext")
            ProtoLog.v(
                WM_SHELL_MODES,
                "%s",
                dumpToString(container, container, "", true, snapshot, WHITE)
            )
        }

        /**
         * Returns a string representation of a hierarchy rooted at the given container.
         */
        fun dumpToString(
            root: Container,
            container: Container,
            rawPrefix: String = "",
            isLastChildInParent: Boolean = true,
            snapshot: HierarchySnapshot = EMPTY_SNAPSHOT,
            withColor: String? = null,
        ): String {
            val changeFlags = snapshot.getChanges(container)
            val oldState =
                if (!changeFlags.isEmpty) {
                    snapshot.snapshots.getValue(container)
                } else null
            val startColorTag = withColor ?: ""
            val endColorTag = if (withColor != null) NONE else ""
            var output = ""

            // Dump basic info
            val startModeColorTag = if (withColor != null) PURPLE else ""
            val endModeColorTag = if (withColor != null) NONE else ""
            val modeStr = if (container.mode != null)
                " ${startModeColorTag}${container.mode!!.getId()}${endModeColorTag}" else ""
            val changeFlagStr = if (!changeFlags.isEmpty) " changes=${flagsToStr(changeFlags)}"
                else ""
            val branch = if (root == container) ""
                else if (isLastChildInParent) "\u2514\u2500\u2500"
                else "\u251C\u2500\u2500"
            output += "${rawPrefix}${branch}${startColorTag}${container.name}${endColorTag}" +
                    "${modeStr}${changeFlagStr}\n"

            // Account for the branch
            val prefix = if (root == container) rawPrefix
                else if (isLastChildInParent) "$rawPrefix   "
                else "$rawPrefix\u2502  "

            // Dump current & previous window oinfo
            if (oldState != null && !changeFlags.isOnlyChildrenChange()) {
                output += "${prefix}$YELLOW(from)$NONE ${oldState.props}\n"
                output += "${prefix}$YELLOW  (to)$NONE ${container.props}\n"
            } else {
                output += "${prefix}${container.props}\n"
            }

            // Dump current surface info
            output += "${prefix}${container.surface}\n"

            // Identify changed children
            val reorderedChildren = mutableListOf<Container>()
            val removedChildren = mutableListOf<Container>()
            val addedChildren = mutableListOf<Container>()
            if (oldState != null) {
                if (!oldState.children.isEmpty()) {
                    removedChildren.addAll(oldState.children - container.children.toSet())
                    addedChildren.addAll(container.children - oldState.children.toSet())
                    for (child in oldState.children) {
                        if (child in container.children) {
                            if (container.children.indexOf(child)
                                != oldState.children.indexOf(child)
                            ) {
                                reorderedChildren.add(child)
                            }
                        }
                    }
                }
            }

            // Dump the current children
            if (container.children.isNotEmpty()) {
                val allChildren = removedChildren + container.children
                for ((index, child) in allChildren.withIndex()) {
                    var withColor = WHITE
                    if (child in removedChildren) {
                        withColor = RED
                    } else if (child in reorderedChildren) {
                        withColor = YELLOW
                    } else if (child in addedChildren) {
                        withColor = GREEN
                    }
                    val isLastChild = index == (allChildren.size - 1)
                    output += dumpToString(root, child, prefix, isLastChild, snapshot, withColor)
                    output += "\n"
                }
            }
            return output.trimEnd()
        }

        /**
         * Returns a string representation for a token.
         */
        fun tokenToString(token: WindowContainerToken): String {
            val windowTokenStr = token.toString()
                .removePrefix("WCT{android.os.BinderProxy@")
                .removePrefix("WCT{android.os.Binder@")
                .removeSuffix("}")
            return "@${windowTokenStr}"
        }

        /**
         * Returns the rotation in degrees for the given surface rotation constant.
         */
        fun rotationToDegrees(@Surface.Rotation rotation: Int): Int {
            val deg = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> rotation
            }
            return deg
        }

        /**
         * Returns a string of the given change flags in human readable form.
         */
        fun flagsToStr(changeFlags: HierarchyChangeFlags): String {
            val flags = mutableListOf<Int>()
            var i = changeFlags.nextSetBit(0)
            while (i != -1) {
                flags.add(i)
                i = changeFlags.nextSetBit(i + 1)
            }
            return flags
                .map {
                    when (it) {
                        CHANGED_PARENT -> "parent"
                        CHANGED_MODE -> "mode"
                        CHANGED_CHILDREN -> "children"
                        CHANGED_BOUNDS -> "bounds"
                        CHANGED_ROTATION -> "rot"
                        CHANGED_WINDOWING_MODE -> "winMode"
                        CHANGED_VISIBILITY -> "visibility"

                        // Root
                        CHANGED_FOCUS -> "focus"
                        CHANGED_USER -> "user"
                        CHANGED_USER_PROFILES -> "userProfiles"
                        CHANGED_KEYGUARD -> "keyguard"
                        CHANGED_IS_FOLDED -> "isFolded"

                        // Display
                        CHANGED_INSETS -> "insets"

                        // Task
                        CHANGED_TASK_DESCRIPTION -> "task desc"
                        CHANGED_PIP_PARAMS -> "pip params"
                        else -> it.toString()
                    }
                }
                .joinToString(prefix = "(", separator = ",", postfix = ")")
        }

        /**
         * Capitalizes the given string (since the std kotlin capitalize() fn is now deprecated).
         */
        fun capitalize(str: String): String {
            return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
