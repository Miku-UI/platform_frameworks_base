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
package com.android.wm.shell.hierarchy.containers

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [Container].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ContainerTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContainerTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy()

    @Test
    fun testSetParent_addsToParentChildren() {
        val parent = createParentContainer()
        val child = StubContainer()
        child.parent = parent
        assertThat(parent.children).contains(child)
    }

    @Test
    fun testSetParent_null_removesFromParentChildren() {
        val parent = createParentContainer()
        val child = StubContainer()
        child.parent = parent
        child.parent = null
        assertThat(parent.children).doesNotContain(child)
    }

    @Test
    fun testReorderChild() {
        val parent = createParentContainer()
        val child1 = StubContainer()
        val child2 = StubContainer()
        val child3 = StubContainer()
        child1.parent = parent
        child2.parent = parent
        child3.parent = parent

        // Initially 1, 2, 3
        assertThat(parent.children).containsExactly(child1, child2, child3).inOrder()

        // Move 1 to top
        parent.reorderChild(child1, true /* toTop */)
        assertThat(parent.children).containsExactly(child2, child3, child1).inOrder()

        // Move 3 to bottom
        parent.reorderChild(child3, false /* toTop */)
        assertThat(parent.children).containsExactly(child3, child2, child1).inOrder()
    }

    @Test
    fun testReorderChild_notAChild_throwsException() {
        val parent = createParentContainer()
        val child = StubContainer()

        assertThrows(IllegalArgumentException::class.java) {
            parent.reorderChild(child, true /* toTop */)
        }
    }

    private fun createParentContainer(): Container {
        val parent = StubContainer()
        parent.parent = hierarchy.root
        return parent
    }
}