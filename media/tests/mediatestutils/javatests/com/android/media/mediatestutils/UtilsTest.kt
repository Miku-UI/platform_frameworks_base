/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.media.mediatestutils

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UtilsTest {

    @Test
    fun withDefer_Unexceptionally() {
        var called = false
        val res = withDefer {
            defer { called = true }
            5
        }
        assertThat(res).isEqualTo(5)
        assertThat(called).isTrue()
    }

    @Test
    fun withDefer_autoClose_isCalled() {
        var closed = false
        AutoCloseable { closed = true }.let { withDefer { autoClose(it) } }
        assertThat(closed).isTrue()
    }

    @Test
    fun withDefer_Order() {
        val calls = mutableListOf<Int>()
        val res = withDefer {
            defer { calls.add(1) }
            autoClose(AutoCloseable { calls.add(2) })
            defer { calls.add(3) }
        }
        assertThat(calls).containsExactly(3, 2, 1).inOrder()
    }

    @Test
    fun withDefer_Exceptionally() {
        val result = mutableListOf<Int>()
        assertThrows(RuntimeException::class.java) {
            withDefer {
                defer { result.add(1) }
                throw RuntimeException("Test")
            }
        }
        assertThat(result).containsExactly(1)
    }

    @Test
    fun withDefer_exceptionsInDefer_areSuppressed() {
        val result = mutableListOf<Int>()
        val exception =
            assertThrows(RuntimeException::class.java) {
                withDefer {
                    defer {
                        result.add(1)
                        throw RuntimeException("Defer 1")
                    }
                    defer {
                        result.add(2)
                        throw RuntimeException("Defer 2")
                    }
                    throw RuntimeException("Main")
                }
            }
        assertThat(exception.message).isEqualTo("Main")
        assertThat(exception.suppressed.map { it.message })
            .containsExactly("Defer 2", "Defer 1")
            .inOrder()
        assertThat(result).containsExactly(2, 1).inOrder()
    }

    @Test
    fun withDefer_multipleExceptionsInDefer() {
        val result = mutableListOf<Int>()
        val exception =
            assertThrows(IllegalStateException::class.java) {
                withDefer {
                    defer {
                        result.add(1)
                        throw RuntimeException("Defer 1")
                    }
                    defer {
                        result.add(2)
                        throw IllegalStateException("Defer 2")
                    }
                    5
                }
            }
        assertThat(result).containsExactly(2, 1).inOrder()
        assertThat(exception.message).isEqualTo("Defer 2")
        assertThat(exception.suppressed.map { it.message }).containsExactly("Defer 1")
    }
}
