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

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.window.WindowContainerToken
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties

/**
 * Utility functions to support querying/manipulating the hierarchy.
 */
class HierarchyUtils {
    companion object {
        /**
         * Returns the root container of the hierarchy given any container in the hierarchy.
         * The root of any container hierarchy must be a container with
         * {@link RootContainerProperties}.
         */
        fun getRoot(container: Container): Container {
            var c: Container? = container
            while (c?.parent != null) {
                c = c.parent
            }
            if (c!!.props !is RootContainerProperties) {
                throw IllegalArgumentException(
                    "Expected root, but found a ${c.javaClass.simpleName}"
                )
            }
            return c
        }

        /**
         * Returns a container that is a descendant of root with the given token.
         */
        fun getContainer(root: Container, token: WindowContainerToken): Container? {
            val containers = mutableListOf<Container>()
            forEachContainer(
                root,
                {},
                { it.token == token },
                containers
            )
            return containers.firstOrNull()
        }

        /**
         * Returns the container in the list with the given token.
         */
        fun getContainer(containers: List<Container>, token: WindowContainerToken): Container? {
            for (container in containers) {
                if (container.token == token) {
                    return container
                }
            }
            return null
        }

        /**
         * Returns the index of a container with the given token in the given list, or -1 if it
         * doesn't exist in the list.
         */
        fun findContainer(containers: List<Container>, token: WindowContainerToken): Int {
            for ((i, container) in containers.withIndex()) {
                if (container.token == token) {
                    return i
                }
            }
            return -1
        }

        /**
         * Returns a display container that is a descendant of root with the given display id.
         */
        fun getDisplay(root: Container, displayId: Int): Container? {
            val displays = mutableListOf<Container>()
            forEachContainer(
                root,
                {},
                { it.isDisplay() && it.displayProps().displayId == displayId },
                displays
            )
            return displays.firstOrNull()
        }

        /**
         * Returns the ancestor display container for this container.
         */
        fun getAncestorDisplay(container: Container): Container? {
            var ancestor: Container? = container
            while (ancestor != null) {
                if (ancestor.isDisplay()) {
                    return ancestor
                }
                ancestor = ancestor.parent
            }
            return null
        }

        /**
         * Returns a display area container that is a descendant of root with the given display id.
         */
        fun getTaskDisplayArea(root: Container, displayId: Int): Container? {
            val displayAreas = mutableListOf<Container>()
            forEachContainer(
                root,
                {},
                { c ->
                    if (!c.isDisplayArea()) {
                        return@forEachContainer false
                    }
                    val display = getAncestorDisplay(c)!!
                    return@forEachContainer display.displayProps().displayId == displayId
                },
                displayAreas
            )
            return displayAreas.firstOrNull()
        }

        /**
         * Returns a task container that is a descendant of root with the given task id.
         */
        fun getTask(root: Container, taskId: Int): Container? {
            val tasks = mutableListOf<Container>()
            forEachContainer(
                root,
                {},
                { it.isTask() && it.taskProps().taskId == taskId },
                tasks
            )
            return tasks.firstOrNull()
        }

        /**
         * Returns the mode that is directly or indirectly associated with this container.
         */
        fun getMode(container: Container): Mode? {
            var c: Container? = container
            while (c != null) {
                if (c.mode != null) {
                    return c.mode
                }
                c = c.parent
            }
            return null
        }

        /**
         * Returns the ancestor of the container that has a set mode, or null if no such ancestor
         * exists.
         */
        fun getModeContainer(container: Container): Container? {
            var c: Container? = container
            while (c != null) {
                if (c.mode != null) {
                    return c
                }
                c = c.parent
            }
            return null
        }

        /**
         * Returns a list of containers in top-down breadth-first traversal order.
         * Includes the provided root container.
         */
        fun toContainersList(root: Container): List<Container> {
            val containers = mutableListOf<Container>()
            forEachContainer(root, {}, { _ -> true }, containers)
            return containers
        }

        /**
         * Returns whether there are any temporary animating containers in the hierarchy under the
         * given container.
         */
        fun hasTemporaryAnimatingContainers(container: Container): Boolean {
            val containers = toContainersList(container)
            for (c in containers) {
                if (c.isTemporaryAnimatingContainer) {
                    return true
                }
            }
            return false
        }

        /**
         * Removes all temporary animating containers from the hierarchy under the given container.
         */
        fun removeAllTemporaryAnimatingContainers(container: Container) {
            val containers = toContainersList(container)
            for (c in containers) {
                if (c.isTemporaryAnimatingContainer) {
                    c.parent = null
                }
            }
        }

        /**
         * Breadth-first traversal of container hierarchy starting at the given container as the
         * root, applying the test function to each container. If the test function returns true,
         * then it will be added to the collector list, and the consumer function will be called
         * with that container. If no test function is specified, then every container will be added
         * to the collector list and called on the consumer function.
         *
         * This can be used for things like:
         * - Applying logic to all containers (ie. test fn always returns true, consumer applies it)
         * - Finding containers satisfying some heuristic (ie. in the test fn)
         * - or any combination of the above
         *
         * The provided consumer must NOT manipulate the hierarchy, otherwise it can lead to a
         * concurrent modification exception since we are actively iterating the hierarchy as well.
         */
        @Suppress("UNCHECKED_CAST")
        private fun <T : Container> forEachContainer(
            root: Container,
            consumer: (Container) -> Unit,
            test: ((Container) -> Boolean)? = null,
            collector: MutableList<T>? = null
        ) {
            var index = 0
            val containers = mutableListOf(root)
            while (index < containers.size) {
                val candidate = containers[index]
                if (test == null || test(candidate)) {
                    consumer(candidate)
                    collector?.add(candidate as T)
                }
                containers.addAll(candidate.children)
                index++
            }
        }

        /**
         * Returns a list of removed displays in no particular order given the list of containers
         * before and after an update.
         */
        fun getRemovedDisplays(
            preUpdateContainers: List<Container>,
            postUpdateContainers: List<Container>,
        ): List<Container> {
            // Look through which containers were removed
            val removedContainers = preUpdateContainers - postUpdateContainers.toSet()
            return removedContainers.filter { it.isDisplay() }
        }

        /**
         * Returns the expected parent of the given container.
         */
        fun resolveParentForContainer(
            root: Container,
            container: Container,
            parentToken: WindowContainerToken?,
            displayId: Int,
        ): Container? {
            when (container.props) {
                is RootContainerProperties -> {
                    // Root container has no parent
                    return null
                }

                is DisplayContainerProperties -> {
                    // Display containers are always rooted to the root container
                    return root
                }

                is DisplayAreaContainerProperties -> {
                    // Display areas are rooted to the display
                    // NOTE: We need this for now because a transition can result in an intermediate
                    // temporary DA being reported which is technically the parent of the TDA.
                    // We should
                    return getDisplay(root, displayId)
                }

                is TaskContainerProperties -> {
                    // TODO: Remove this once we always set a valid parent token in the
                    //  TransitionInfo
                    return if (container.props.taskInfo.parentTaskId == INVALID_TASK_ID) {
                        getTaskDisplayArea(root, container.props.taskInfo.displayId)
                    } else {
                        getTask(root, container.props.taskInfo.parentTaskId)
                    }
                }

                else -> {
                    if (parentToken != null) {
                        // Use the explicitly set parent token if provided
                        return getContainer(root, parentToken)
                    } else if (displayId >= 0) {
                        // Fall back to the valid display otherwise
                        return getDisplay(root, displayId)
                    } else {
                        // By default just return the container's current parent
                        return container.parent
                    }
                }
            }
        }
    }
}
