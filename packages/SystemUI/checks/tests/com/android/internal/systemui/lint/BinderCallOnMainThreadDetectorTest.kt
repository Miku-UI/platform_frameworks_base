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

package com.android.internal.systemui.lint

import org.junit.Test

class BinderCallOnMainThreadDetectorTest : SystemUILintDetectorTest() {
    override fun getDetector() = BinderCallOnMainThreadDetector()

    override fun getIssues() = listOf(BinderCallOnMainThreadDetector.ISSUE)

    @Test
    fun noBinderCalls_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    class TestClass {
                        val helper = HelperClass()
                        fun doSomething() {
                            val three = 1 + 2
                            val multiplied = helper.multiply(three)
                        }
                    }

                    class HelperClass {
                        fun multiply(input: Int): Int {
                            return input * 2
                        }
                    }

                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_noWithContext_staticMethod_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.app.PendingIntent

                        fun doSomething() {
                            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/test.kt:6: Warning: Binder call on main thread [BinderCallOnMainThread]
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                      ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_noWithContext_nonStaticMethod_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager

                    class TestRepository(private val mediaProjectionManager: MediaProjectionManager) {
                       fun onStopped() {
                            mediaProjectionManager.stopActiveProjection(0)
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestRepository.kt:8: Warning: Binder call on main thread [BinderCallOnMainThread]
        mediaProjectionManager.stopActiveProjection(0)
                               ~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_withMainContext_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineContext
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Main

                    class TestClass(@Main private val mainCoroutineContext: CoroutineContext) {
                        suspend fun doSomething() {
                            withContext(mainCoroutineContext) {
                                    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                             }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:12: Warning: Binder call on main thread [BinderCallOnMainThread]
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                                  ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_withBackgroundContext_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineContext
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(@Background private val backgroundContext: CoroutineContext) {
                        suspend fun doSomething() {
                            withContext(backgroundContext) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withBackgroundContext_nested_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineContext
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(@Background private val backgroundContext: CoroutineContext) {
                        private val doTheThing = true
                        suspend fun doSomething() {
                            withContext(backgroundContext) {
                                if (doTheThing) {
                                    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                }
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withMainExecutor_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import java.util.concurrent.Executor
                    import com.android.systemui.dagger.qualifiers.Main

                    class TestClass(@Main private val mainExecutor: Executor) {
                        fun doSomething() {
                            mainExecutor.execute {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:11: Warning: Binder call on main thread [BinderCallOnMainThread]
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                              ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_withBackgroundExecutor_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import java.util.concurrent.Executor
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(@Background private val backgroundExecutor: Executor) {
                        fun doSomething() {
                            backgroundExecutor.execute {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withUiBackgroundExecutor_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import java.util.concurrent.Executor
                    import com.android.systemui.dagger.qualifiers.UiBackground

                    class TestClass(@UiBackground private val uiBgExecutor: Executor) {
                        fun doSomething() {
                            uiBgExecutor.execute {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_inMethodAnnotatedWithWorkerThread_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.app.PendingIntent
                        import androidx.annotation.WorkerThread

                        @WorkerThread
                        fun doSomething() {
                            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_inClassAnnotatedWithWorkerThread_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import androidx.annotation.WorkerThread

                    @WorkerThread
                    class TestRepository(private val mediaProjectionManager: MediaProjectionManager) {
                       fun onStopped() {
                            mediaProjectionManager.stopActiveProjection(0)
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withMainDispatcher_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineDispatcher
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Main

                    class TestClass(@Main private val mainDispatcher: CoroutineDispatcher) {
                        suspend fun doSomething() {
                            withContext(mainDispatcher) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:12: Warning: Binder call on main thread [BinderCallOnMainThread]
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                              ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_withBackgroundDispatcher_shortenedToBg_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineDispatcher
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(
                        @Background private val bgDispatcher: CoroutineDispatcher,
                    ) {
                        suspend fun doSomething() {
                            withContext(bgDispatcher) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withCustomBackgroundDispatcher_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineDispatcher
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(@Background private val myCustomBackgroundDispatcher: CoroutineDispatcher) {
                        suspend fun doSomething() {
                            withContext(myCustomBackgroundDispatcher) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_withContext_butNotBackground_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineDispatcher
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    // "hobgoblin" has "bg" in its string but ot isn't a background dispatcher, so
                    // there should be an error
                    class TestClass(private val hobgoblinDispatcher: CoroutineDispatcher) {
                        suspend fun doSomething() {
                            withContext(hobgoblinDispatcher) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:14: Warning: Binder call on main thread [BinderCallOnMainThread]
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                              ~~~~~~~~~~~~
0 errors, 1 warnings
            """
            )
    }

    @Test
    fun binderCall_launchedOnApplicationScope_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(@Application private val applicationScope: CoroutineScope) {
                        fun doSomething() {
                            applicationScope.launch {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:12: Warning: Binder call on main thread [BinderCallOnMainThread]
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                                              ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_launchedOnBackgroundScope_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(@Background private val backgroundScope: CoroutineScope) {
                        fun doSomething() {
                            backgroundScope.launch {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_launchTracedOnBackgroundScope_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import com.android.app.tracing.coroutines.launchTraced
                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(@Background private val backgroundScope: CoroutineScope) {
                        fun doSomething() {
                            backgroundScope.launchTraced {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_launchTracedAliasedOnBackgroundScope_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import com.android.app.tracing.coroutines.launchTraced as launch
                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(@Background private val backgroundScope: CoroutineScope) {
                        fun doSomething() {
                            backgroundScope.launch {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_launchedOnCustomBackgroundScope_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(
                        @Background private val myCustomBackgroundScope: CoroutineScope,
                    ) {
                        fun doSomething() {
                            myCustomBackgroundScope.launch {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_launchedOnApplicationScopeWithBackgroundDispatcher_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent

                    import kotlin.coroutines.CoroutineDispatcher
                    import kotlin.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(
                        @Application private val applicationScope: CoroutineScope,
                        @Background private val backgroundDispatcher: CoroutineDispatcher,
                    ) {
                        fun doSomething() {
                            applicationScope.launch(backgroundDispatcher) {
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
                            }
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_stateFlowOnApplicationScope_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import com.android.systemui.dagger.qualifiers.Main
                    import kotlinx.coroutines.flow.flowOf
                    import kotlinx.coroutines.flow.stateIn
                    import kotlinx.coroutines.flow.map
                    import kotlinx.coroutines.flow.SharingStarted
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(@Application private val applicationScope: CoroutineScope) {
                        suspend fun doSomething() {
                            val stateFlow =
                                flowOf(1, 2, 3)
                                .map {
                                      PendingIntent.getBroadcast(context, it, intent, 0)
                                 }
                                .stateIn(applicationScope, SharingStarted.Eagerly, 0)
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:18: Warning: Binder call on main thread [BinderCallOnMainThread]
                  PendingIntent.getBroadcast(context, it, intent, 0)
                                ~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCall_stateFlowOnBackgroundScope_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import com.android.systemui.dagger.qualifiers.Main
                    import kotlinx.coroutines.flow.flowOf
                    import kotlinx.coroutines.flow.stateIn
                    import kotlinx.coroutines.flow.map
                    import kotlinx.coroutines.flow.SharingStarted
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.withContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(@Background private val backgroundScope: CoroutineScope) {
                        suspend fun doSomething() {
                            val stateFlow =
                                flowOf(1, 2, 3)
                                .map {
                                      PendingIntent.getBroadcast(context, it, intent, 0)
                                 }
                                .stateIn(backgroundScope, SharingStarted.Eagerly, 0)
                        }
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_stateFlowOnApplicationScope_withCallback_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import com.android.systemui.dagger.qualifiers.Main
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.stateIn
                    import kotlinx.coroutines.flow.SharingStarted
                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class TestClass(
                        @Application private val appScope: CoroutineScope,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .stateIn(appScope, SharingStarted.Eagerly, 0)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:21: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
src/test/pkg/TestClass.kt:24: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    @Test
    fun binderCall_stateFlowOnBackgroundScope_withCallback_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import com.android.systemui.dagger.qualifiers.Main
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.stateIn
                    import kotlinx.coroutines.flow.SharingStarted
                    import kotlinx.coroutines.CoroutineScope

                    class TestClass(
                        @Background private val backgroundScope: CoroutineScope,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .stateIn(backgroundScope, SharingStarted.Eagerly, 0)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_flowOnMainDispatcher_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import com.android.systemui.dagger.qualifiers.Main
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.flowOn
                    import kotlinx.coroutines.CoroutineDispatcher
                    import com.android.systemui.dagger.qualifiers.Main

                    class TestClass(
                        @Main private val dispatcher: CoroutineDispatcher,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .flowOn(dispatcher)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:20: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
src/test/pkg/TestClass.kt:23: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    @Test
    fun binderCall_flowOnBackgroundDispatcher_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.flowOn
                    import kotlinx.coroutines.CoroutineDispatcher
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(
                        @Background private val backgroundCoroutineDispatcher: CoroutineDispatcher,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .flowOn(backgroundCoroutineDispatcher)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCall_flowOnMainContext_error() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.flowOn
                    import kotlin.coroutines.CoroutineContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(
                        @Main private val mainCoroutineContext: CoroutineContext,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .flowOn(mainCoroutineContext)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:19: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
src/test/pkg/TestClass.kt:22: Warning: Binder call on main thread [BinderCallOnMainThread]
                    PendingIntent.getBroadcast(context, it, intent, 0)
                                  ~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    @Test
    fun binderCall_flowOnBackgroundContext_clean() {
        lint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import android.app.PendingIntent
                    import android.media.projection.MediaProjectionManager
                    import kotlinx.coroutines.channels.awaitClose
                    import kotlinx.coroutines.flow.callbackFlow
                    import kotlinx.coroutines.flow.flowOn
                    import kotlin.coroutines.CoroutineContext
                    import com.android.systemui.dagger.qualifiers.Background

                    class TestClass(
                        @Background private val backgroundContext: CoroutineContext,
                        private val mediaProjectionManager: MediaProjectionManager,
                    ) {
                        private val callbackFlow = callbackFlow {
                            val callback =
                                object : MediaProjectionManager.Callback() {
                                    override fun onStart(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                    override fun onStop(info: MediaProjectionInfo?) {
                                        PendingIntent.getBroadcast(context, it, intent, 0)
                                    }
                                }
                                mediaProjectionManager.addCallback(callback)
                                awaitClose { mediaProjectionManager.removeCallback(callback) }
                         }
                            .flowOn(backgroundContext)
                    }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun binderCallForSpecificMethod_errorForOnlyProvidedMethod() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.media.session.MediaController

                        class TestClass {
                            fun controlMedia(controller: MediaController, callback: MediaController.Callback) {
                                // Binder call
                                controller.unregisterCallback(callback)
                                // Not a binder call
                                controller.adjustVolume(1, 2)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:8: Warning: Binder call on main thread [BinderCallOnMainThread]
        controller.unregisterCallback(callback)
                   ~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun binderCallForAllMethods_errorForEveryMethod() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.telephony.SubscriptionManager

                        class TestClass {
                            fun getSubs(manager: SubscriptionManager) {
                                val allSubs = manager.completeActiveSubscriptionInfoList
                                val specificSubInfo = manager.getActiveSubscriptionInfo(0)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:7: Warning: Binder call on main thread [BinderCallOnMainThread]
        val allSubs = manager.completeActiveSubscriptionInfoList
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/TestClass.kt:8: Warning: Binder call on main thread [BinderCallOnMainThread]
        val specificSubInfo = manager.getActiveSubscriptionInfo(0)
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    @Test
    fun queryIntentComponents_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.app.PendingIntent

                        fun doSomething(intent: PendingIntent) {
                            val components = intent.queryIntentComponents(0)
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/test.kt:6: Warning: Binder call on main thread [BinderCallOnMainThread]
    val components = intent.queryIntentComponents(0)
                            ~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun packageManagerGetApplicationInfo_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.content.pm.PackageManager

                        fun doSomething(packageManager: PackageManager) {
                            val info = packageManager.getApplicationInfo("package", 0)
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/test.kt:6: Warning: Binder call on main thread [BinderCallOnMainThread]
    val info = packageManager.getApplicationInfo("package", 0)
                              ~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun mediaControllerConstructor_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.media.session.MediaController

                        class TestClass {
                            fun onCreate(context: Context, token: MediaSession.Token) {
                                val controller = MediaController(context, token)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:7: Warning: Binder call on main thread [BinderCallOnMainThread]
        val controller = MediaController(context, token)
                         ~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun mediaControllerRegisterCallback_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.media.session.MediaController

                        class TestClass {
                            fun unregister(controller: MediaController, callback: MediaController.Callback) {
                                controller.registerCallback(callback)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:7: Warning: Binder call on main thread [BinderCallOnMainThread]
        controller.registerCallback(callback)
                   ~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun mediaControllerUnregisterCallback_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.media.session.MediaController

                        class TestClass {
                            fun unregister(controller: MediaController, callback: MediaController.Callback) {
                                controller.unregisterCallback(callback)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:7: Warning: Binder call on main thread [BinderCallOnMainThread]
        controller.unregisterCallback(callback)
                   ~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun statusBarServiceDisableForUser_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import android.os.IBinder
                        import com.android.internal.statusbar.IStatusBarService

                        class TestClass {
                            fun disable(service: IStatusBarService, token: IBinder) {
                                service.disableForUser(0, token, "package", 0)
                            }
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(BinderCallOnMainThreadDetector.ISSUE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.kt:8: Warning: Binder call on main thread [BinderCallOnMainThread]
        service.disableForUser(0, token, "package", 0)
                ~~~~~~~~~~~~~~
0 errors, 1 warnings
                """
            )
    }

    companion object {
        private val launchTracedStub =
            kotlin(
                """
                    package com.android.app.tracing.coroutines

                    import kotlinx.coroutines.CoroutineScope

                    fun CoroutineScope.launchTraced(block: suspend CoroutineScope.() -> Unit) {}
                """
                    .trimIndent()
            )

        private val stubs =
            arrayOf(
                *androidStubs,
                launchTracedStub,
                MainThreadCoroutineScopeDetectorTest.applicationQualifierStub,
                MainThreadCoroutineScopeDetectorTest.mainQualifierStub,
                MainThreadCoroutineScopeDetectorTest.backgroundQualifierStub,
                MainThreadCoroutineScopeDetectorTest.uiBackgroundQualifierStub,
            )
    }
}
