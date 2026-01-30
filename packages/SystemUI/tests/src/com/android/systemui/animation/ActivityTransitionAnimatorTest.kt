package com.android.systemui.animation

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Point
import android.graphics.Rect
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.RemoteAnimationTarget.MODE_CLOSING
import android.view.RemoteAnimationTarget.MODE_OPENING
import android.view.SurfaceControl
import android.view.ViewGroup
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_OPEN
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransition
import android.window.TransitionFilter
import android.window.TransitionInfo
import android.window.WindowAnimationState
import android.window.WindowContainerTransaction
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.EmptyTestActivity
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.wm.shell.shared.ShellTransitions
import com.google.common.truth.Truth.assertThat
import junit.framework.AssertionFailedError
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ActivityTransitionAnimatorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val mainExecutor = context.mainExecutor
    private val testTransitionAnimator = fakeTransitionAnimator(mainExecutor)
    private val testShellTransitions = FakeShellTransitions()

    private val Kosmos.underTest by Kosmos.Fixture { activityTransitionAnimator }

    @Mock lateinit var callback: ActivityTransitionAnimator.Callback
    @Mock lateinit var listener: ActivityTransitionAnimator.Listener
    @Mock lateinit var iCallback: IRemoteAnimationFinishedCallback
    @Mock lateinit var transitionHelper: RemoteTransitionHelper

    @get:Rule(order = 0) val mockitoRule = MockitoJUnit.rule()
    @get:Rule(order = 1) val activityRule = ActivityScenarioRule(EmptyTestActivity::class.java)

    @Before
    fun setup() {
        kosmos.activityTransitionAnimator =
            ActivityTransitionAnimator(
                mainExecutor,
                ActivityTransitionAnimator.TransitionRegister.fromShellTransitions(
                    testShellTransitions
                ),
                testTransitionAnimator,
                testTransitionAnimator,
                disableWmTimeout = true,
                skipReparentTransaction = true,
            )
        kosmos.activityTransitionAnimator.callback = callback
        kosmos.activityTransitionAnimator.addListener(listener)
    }

    @After
    fun tearDown() {
        kosmos.activityTransitionAnimator.removeListener(listener)
    }

    private fun startIntentWithAnimation(
        controller: ActivityTransitionAnimator.Controller?,
        animator: ActivityTransitionAnimator = kosmos.activityTransitionAnimator,
        animate: Boolean = true,
        animateReturn: Boolean = false,
        showOverLockscreen: Boolean = false,
        intentStarter: (RemoteTransition?) -> Int,
    ) {
        // We start in a new thread so that we can ensure that the callbacks are called in the main
        // thread.
        thread {
                animator.startIntentWithAnimation(
                    controller = controller,
                    scope = kosmos.testScope,
                    animate = animate,
                    animateReturn = animateReturn,
                    showOverLockscreen = showOverLockscreen,
                    intentStarter = intentStarter,
                )
            }
            .join()
    }

    private fun startIntentWithAnimationLegacy(
        controller: ActivityTransitionAnimator.Controller?,
        animator: ActivityTransitionAnimator = kosmos.activityTransitionAnimator,
        animate: Boolean = true,
        intentStarter: (RemoteAnimationAdapter?) -> Int,
    ) {
        // We start in a new thread so that we can ensure that the callbacks are called in the main
        // thread.
        thread {
                animator.startIntentWithAnimation(
                    controller = controller,
                    animate = animate,
                    intentStarter = intentStarter,
                )
            }
            .join()
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationAnimates() {
        kosmos.runTest {
            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller = controllerWithCookie) { transition ->
                originTransition = transition

                ActivityManager.START_SUCCESS
            }

            waitForIdleSync()
            assertThat(originTransition).isNotNull()
            verify(controller).onIntentStarted(willAnimate = true)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationAnimates_hidingKeyguard() {
        kosmos.runTest {
            whenever(callback.isOnKeyguard()).thenReturn(true)

            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller = controllerWithCookie) { transition ->
                originTransition = transition

                ActivityManager.START_SUCCESS
            }

            waitForIdleSync()
            verify(controller).onIntentStarted(willAnimate = true)
            verify(callback).hideKeyguardWithAnimation(originTransition!!.remoteTransition)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationRegistersTransitionForTrampolines() {
        kosmos.runTest {
            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller = controllerWithCookie) { transition ->
                originTransition = transition
                ActivityManager.START_SUCCESS
            }

            assertThat(originTransition).isNotNull()
            assertThat(testShellTransitions.remotes.size).isEqualTo(1)
            assertThat(testShellTransitions.remotes.values).contains(originTransition)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationRegistersReturnTransition() {
        kosmos.runTest {
            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }

            startIntentWithAnimation(controller = controllerWithCookie, animateReturn = true) {
                ActivityManager.START_SUCCESS
            }

            assertThat(testShellTransitions.remotes.size).isEqualTo(2)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationDoesNotAnimate_onBadLaunchResult() {
        kosmos.runTest {
            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller = controllerWithCookie) { transition ->
                originTransition = transition

                ActivityManager.START_ABORTED
            }

            waitForIdleSync()
            assertThat(originTransition).isNotNull()
            verify(controller).onIntentStarted(willAnimate = false)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationDoesNotAnimate_ifControllerIsNull() {
        kosmos.runTest {
            var startedIntent = false
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller = null) { transition ->
                startedIntent = true
                originTransition = transition

                ActivityManager.START_SUCCESS
            }

            assertThat(startedIntent).isTrue()
            assertThat(originTransition).isNull()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationDoesNotAnimate_ifCookieIsNull() {
        kosmos.runTest {
            val controller = createController()
            var startedIntent = false
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller) { transition ->
                startedIntent = true
                originTransition = transition

                ActivityManager.START_SUCCESS
            }

            waitForIdleSync()
            assertThat(startedIntent).isTrue()
            assertThat(originTransition).isNull()
            verify(controller).onIntentStarted(willAnimate = false)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationDoesNotAnimate_ifAnimateIsFalse() {
        kosmos.runTest {
            val controller = createController()
            var startedIntent = false
            var originTransition: RemoteTransition? = null

            startIntentWithAnimation(controller, animate = false) { transition ->
                startedIntent = true
                originTransition = transition

                ActivityManager.START_SUCCESS
            }

            waitForIdleSync()
            assertThat(startedIntent).isTrue()
            assertThat(originTransition).isNull()
            verify(controller).onIntentStarted(willAnimate = false)
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationDoesNotRegisterTransitions_ifItDoesNotAnimate() {
        kosmos.runTest {
            val controller = createController()
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }

            startIntentWithAnimation(controller = controllerWithCookie, animateReturn = true) {
                ActivityManager.START_ABORTED
            }

            waitForIdleSync()
            assertThat(testShellTransitions.remotes).isEmpty()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun startIntentWithAnimationThrows_ifCallbackIsNotDefined() {
        kosmos.runTest {
            underTest.callback = null
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }

            assertThrows(IllegalStateException::class.java) {
                underTest.startIntentWithAnimation(
                    controller = controllerWithCookie,
                    scope = kosmos.testScope,
                ) {
                    ActivityManager.START_SUCCESS
                }
            }
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionStartAnimationAnimatesLaunch() {
        kosmos.runTest {
            val cookie = ActivityTransitionAnimator.TransitionCookie("testCookie")
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie = cookie
                }
            val token = mock(IBinder::class.java)
            val info = mock(TransitionInfo::class.java)
            val change =
                listOf(createChange(mock(SurfaceControl::class.java), cookie, forLaunch = true))
            whenever(info.changes).thenReturn(change)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false
            val finishCallback = finishedCallback { finished = true }

            activityTransitionAnimator
                .createOriginTransition(
                    controllerWithCookie,
                    testScope,
                    isDialogLaunch = false,
                    transitionHelper = transitionHelper,
                )
                .startAnimation(token, info, startTransaction, finishCallback)

            // Need this to make sure that the animation runs until the end before the checks.
            while (!finished) continue
            waitForIdleSync()
            verify(transitionHelper).setUpAnimation(token, info, startTransaction, finishCallback)
            verify(listener).onTransitionAnimationStart()
            verify(listener).onTransitionAnimationEnd()
            verify(transitionHelper).cleanUpAnimation(eq(token), any())
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionStartAnimationDoesNotAnimate_ifTokenIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val info = mock(TransitionInfo::class.java)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .startAnimation(null, info, startTransaction, finishedCallback { finished = true })

            waitForIdleSync()
            assertThat(finished).isTrue()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionStartAnimationDoesNotAnimate_ifTransitionInfoIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val token = mock(IBinder::class.java)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .startAnimation(token, null, startTransaction, finishedCallback { finished = true })

            waitForIdleSync()
            assertThat(finished).isTrue()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionStartAnimationDoesNotAnimate_ifStartTransactionIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val token = mock(IBinder::class.java)
            val info = mock(TransitionInfo::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .startAnimation(token, info, null, finishedCallback { finished = true })

            waitForIdleSync()
            assertThat(finished).isTrue()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionTakeOverAnimationAnimatesLaunch() {
        kosmos.runTest {
            val cookie = ActivityTransitionAnimator.TransitionCookie("testCookie")
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie = cookie
                }
            val token = mock(IBinder::class.java)
            val info = mock(TransitionInfo::class.java)
            val change =
                listOf(createChange(mock(SurfaceControl::class.java), cookie, forLaunch = true))
            whenever(info.changes).thenReturn(change)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false
            val finishCallback = finishedCallback { finished = true }

            activityTransitionAnimator
                .createOriginTransition(
                    controllerWithCookie,
                    testScope,
                    isDialogLaunch = false,
                    transitionHelper = transitionHelper,
                )
                .takeOverAnimation(
                    token,
                    info,
                    startTransaction,
                    finishCallback,
                    arrayOf(WindowAnimationState()),
                )

            // Need this to make sure that the animation runs until the end before the checks.
            while (!finished) continue
            waitForIdleSync()
            verify(transitionHelper).setUpAnimation(token, info, startTransaction, finishCallback)
            verify(listener).onTransitionAnimationStart()
            verify(listener).onTransitionAnimationEnd()
            verify(transitionHelper).cleanUpAnimation(eq(token), any())
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionTakeOverAnimationDoesNotAnimate_ifTokenIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val info = mock(TransitionInfo::class.java)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .takeOverAnimation(
                    null,
                    info,
                    startTransaction,
                    finishedCallback { finished = true },
                    emptyArray(),
                )

            waitForIdleSync()
            assertThat(finished).isTrue()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionTakeOverAnimationDoesNotAnimate_ifTransitionInfoIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val token = mock(IBinder::class.java)
            val startTransaction = mock(SurfaceControl.Transaction::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .takeOverAnimation(
                    token,
                    null,
                    startTransaction,
                    finishedCallback { finished = true },
                    emptyArray(),
                )

            waitForIdleSync()
            assertThat(finished).isTrue()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun originTransitionTakeOverAnimationDoesNotAnimate_ifStartTransactionIsNull() {
        kosmos.runTest {
            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(createController()) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }
            val token = mock(IBinder::class.java)
            val info = mock(TransitionInfo::class.java)
            var finished = false

            activityTransitionAnimator
                .createOriginTransition(controllerWithCookie, testScope, isDialogLaunch = false)
                .takeOverAnimation(
                    token,
                    info,
                    null,
                    finishedCallback { finished = true },
                    emptyArray(),
                )

            waitForIdleSync()
            assertThat(finished).isTrue()
            assertThat(testShellTransitions.remotes).isEmpty()
            verify(listener, never()).onTransitionAnimationStart()
        }
    }

    @Test
    fun animationAdapterIsNullIfControllerIsNull_withLegacyAPI() {
        kosmos.runTest {
            var startedIntent = false
            var animationAdapter: RemoteAnimationAdapter? = null

            startIntentWithAnimationLegacy(controller = null) { adapter ->
                startedIntent = true
                animationAdapter = adapter

                ActivityManager.START_SUCCESS
            }

            assertThat(startedIntent).isTrue()
            assertThat(animationAdapter).isNull()
        }
    }

    @Test
    fun animatesIfActivityOpens_withLegacyAPI() {
        kosmos.runTest {
            val controller = createController()
            val willAnimateCaptor = ArgumentCaptor.forClass(Boolean::class.java)
            var animationAdapter: RemoteAnimationAdapter? = null
            startIntentWithAnimationLegacy(controller) { adapter ->
                animationAdapter = adapter
                ActivityManager.START_SUCCESS
            }

            assertThat(animationAdapter).isNotNull()
            waitForIdleSync()
            verify(controller).onIntentStarted(willAnimateCaptor.capture())
            assertThat(willAnimateCaptor.value).isTrue()
        }
    }

    @Test
    fun doesNotAnimateIfActivityIsAlreadyOpen_withLegacyAPI() {
        kosmos.runTest {
            val controller = createController()
            val willAnimateCaptor = ArgumentCaptor.forClass(Boolean::class.java)
            startIntentWithAnimationLegacy(controller) { ActivityManager.START_DELIVERED_TO_TOP }

            waitForIdleSync()
            verify(controller).onIntentStarted(willAnimateCaptor.capture())
            assertThat(willAnimateCaptor.value).isFalse()
        }
    }

    @Test
    fun animatesIfActivityIsAlreadyOpenAndIsOnKeyguard_withLegacyAPI() {
        kosmos.runTest {
            whenever(callback.isOnKeyguard()).thenReturn(true)

            val controller = createController()
            val willAnimateCaptor = ArgumentCaptor.forClass(Boolean::class.java)
            var animationAdapter: RemoteAnimationAdapter? = null

            startIntentWithAnimationLegacy(controller, underTest) { adapter ->
                animationAdapter = adapter
                ActivityManager.START_DELIVERED_TO_TOP
            }

            waitForIdleSync()
            verify(controller).onIntentStarted(willAnimateCaptor.capture())
            verify(callback).hideKeyguardWithAnimation(any<IRemoteAnimationRunner>())

            assertThat(willAnimateCaptor.value).isTrue()
            assertThat(animationAdapter).isNull()
        }
    }

    @Test
    fun doesNotAnimateIfAnimateIsFalse_withLegacyAPI() {
        kosmos.runTest {
            val controller = createController()
            val willAnimateCaptor = ArgumentCaptor.forClass(Boolean::class.java)
            startIntentWithAnimationLegacy(controller, animate = false) {
                ActivityManager.START_SUCCESS
            }

            waitForIdleSync()
            verify(controller).onIntentStarted(willAnimateCaptor.capture())
            assertThat(willAnimateCaptor.value).isFalse()
        }
    }

    @Test
    fun registersReturnIffCookieIsPresent_withLegacyAPI() {
        kosmos.runTest {
            whenever(callback.isOnKeyguard()).thenReturn(false)

            val controller = createController()
            startIntentWithAnimationLegacy(controller, underTest) {
                ActivityManager.START_DELIVERED_TO_TOP
            }

            waitForIdleSync()
            assertThat(testShellTransitions.remotes).isEmpty()
            assertThat(testShellTransitions.remotesForTakeover).isEmpty()

            val controllerWithCookie =
                object : DelegateTransitionAnimatorController(controller) {
                    override val transitionCookie
                        get() = ActivityTransitionAnimator.TransitionCookie("testCookie")
                }

            startIntentWithAnimationLegacy(controllerWithCookie, underTest) {
                ActivityManager.START_DELIVERED_TO_TOP
            }

            waitForIdleSync()
            assertThat(testShellTransitions.remotes.size).isEqualTo(1)
            assertThat(testShellTransitions.remotesForTakeover).isEmpty()
        }
    }

    @Test
    fun registersLongLivedTransition() {
        kosmos.runTest {
            val controller = createController()
            var factory = controllerFactory(controller)
            underTest.registerLongLivedTransitions(factory.cookie, factory, testScope)
            assertThat(testShellTransitions.remotes.size).isEqualTo(2)
            assertThat(testShellTransitions.remotesForTakeover.size).isEqualTo(2)

            factory = controllerFactory(controller)
            underTest.registerLongLivedTransitions(factory.cookie, factory, testScope)
            assertThat(testShellTransitions.remotes.size).isEqualTo(4)
            assertThat(testShellTransitions.remotesForTakeover.size).isEqualTo(4)
        }
    }

    @Test
    fun registersLongLivedTransition_overridingPreviousRegistration() {
        kosmos.runTest {
            val controller = createController()
            val cookie = ActivityTransitionAnimator.TransitionCookie("test_cookie")
            var factory = controllerFactory(controller, cookie)
            underTest.registerLongLivedTransitions(cookie, factory, testScope)
            val transitions = testShellTransitions.remotes.values.toList()

            factory = controllerFactory(controller, cookie)
            underTest.registerLongLivedTransitions(cookie, factory, testScope)
            assertThat(testShellTransitions.remotes.size).isEqualTo(2)
            for (transition in transitions) {
                assertThat(testShellTransitions.remotes.values).doesNotContain(transition)
            }
        }
    }

    @Test
    fun doesNotRegisterLongLivedTransition_ifMissingRequiredProperties() {
        kosmos.runTest {
            val controller = createController()

            // Cookies don't match
            val cookie = ActivityTransitionAnimator.TransitionCookie("test_cookie")
            var factory = controllerFactory(controller, cookie)
            assertThrows(IllegalStateException::class.java) {
                underTest.registerLongLivedTransitions(
                    ActivityTransitionAnimator.TransitionCookie("wrong_cookie"),
                    factory,
                    testScope,
                )
            }

            // No ComponentName
            factory = controllerFactory(controller, component = null)
            assertThrows(IllegalStateException::class.java) {
                underTest.registerLongLivedTransitions(factory.cookie, factory, testScope)
            }
        }
    }

    @Test
    fun unregistersLongLivedTransition() {
        kosmos.runTest {
            val controller = createController()
            val cookies = arrayOfNulls<ActivityTransitionAnimator.TransitionCookie>(3)

            for (index in 0 until 3) {
                cookies[index] = mock(ActivityTransitionAnimator.TransitionCookie::class.java)
                val factory = controllerFactory(controller, cookies[index]!!)
                underTest.registerLongLivedTransitions(factory.cookie, factory, testScope)
            }

            underTest.unregisterLongLivedTransitions(cookies[0]!!)
            assertThat(testShellTransitions.remotes.size).isEqualTo(4)

            underTest.unregisterLongLivedTransitions(cookies[2]!!)
            assertThat(testShellTransitions.remotes.size).isEqualTo(2)

            underTest.unregisterLongLivedTransitions(cookies[1]!!)
            assertThat(testShellTransitions.remotes).isEmpty()
        }
    }

    @Test
    fun doesNotStartIfAnimationIsCancelled() {
        kosmos.runTest {
            val controller = createController()
            val runner = underTest.createEphemeralRunner(controller)
            runner.onAnimationCancelled()
            waitForIdleSync()
            runner.onAnimationStart(
                TRANSIT_NONE,
                emptyArray(),
                emptyArray(),
                emptyArray(),
                iCallback,
            )
            waitForIdleSync()

            verify(controller).onTransitionAnimationCancelled()
            verify(controller, never()).onTransitionAnimationStart(anyBoolean())
            verify(listener).onTransitionAnimationCancelled()
            verify(listener, never()).onTransitionAnimationStart()
            verify(iCallback).onAnimationFinished()
            assertThat(runner.delegate).isNull()
        }
    }

    @Test
    fun cancelsIfNoOpeningWindowIsFound() {
        kosmos.runTest {
            val controller = createController()
            val runner = underTest.createEphemeralRunner(controller)
            runner.onAnimationStart(
                TRANSIT_NONE,
                emptyArray(),
                emptyArray(),
                emptyArray(),
                iCallback,
            )

            waitForIdleSync()
            verify(controller).onTransitionAnimationCancelled()
            verify(controller, never()).onTransitionAnimationStart(anyBoolean())
            verify(listener).onTransitionAnimationCancelled()
            verify(listener, never()).onTransitionAnimationStart()
            assertThat(runner.delegate).isNull()
        }
    }

    @Test
    fun startsAnimationIfWindowIsOpening() {
        kosmos.runTest {
            val controller = createController()
            val runner = underTest.createEphemeralRunner(controller)
            runner.onAnimationStart(
                TRANSIT_NONE,
                arrayOf(fakeWindow()),
                emptyArray(),
                emptyArray(),
                iCallback,
            )
            waitForIdleSync()
            verify(listener).onTransitionAnimationStart()
            verify(controller).onTransitionAnimationStart(anyBoolean())
        }
    }

    @Test
    fun creatingControllerFromNormalViewThrows() {
        kosmos.runTest {
            assertThrows(IllegalArgumentException::class.java) {
                ActivityTransitionAnimator.Controller.fromView(FrameLayout(mContext))
            }
        }
    }

    @Test
    fun runnerCreatesDelegateLazily_onAnimationStart() {
        kosmos.runTest {
            val factory = controllerFactory(createController())
            val runner = underTest.createLongLivedRunner(factory, testScope, forLaunch = true)
            assertThat(runner.delegate).isNull()

            var delegateInitialized = false
            underTest.addListener(
                object : ActivityTransitionAnimator.Listener {
                    override fun onTransitionAnimationStart() {
                        // This is called iff the delegate was initialized, so it's a good proxy for
                        // checking the initialization.
                        delegateInitialized = true
                    }
                }
            )
            runner.onAnimationStart(
                TRANSIT_NONE,
                arrayOf(fakeWindow()),
                emptyArray(),
                emptyArray(),
                iCallback,
            )
            testScope.advanceUntilIdle()
            waitForIdleSync()

            assertThat(delegateInitialized).isTrue()
        }
    }

    @Test
    fun runnerCreatesDelegateLazily_onAnimationTakeover() {
        kosmos.runTest {
            val factory = controllerFactory(createController())
            val runner = underTest.createLongLivedRunner(factory, testScope, forLaunch = false)
            assertThat(runner.delegate).isNull()

            var delegateInitialized = false
            underTest.addListener(
                object : ActivityTransitionAnimator.Listener {
                    override fun onTransitionAnimationStart() {
                        // This is called iff the delegate was initialized, so it's a good proxy for
                        // checking the initialization.
                        delegateInitialized = true
                    }
                }
            )
            runner.takeOverAnimation(
                arrayOf(fakeWindow(MODE_CLOSING)),
                arrayOf(WindowAnimationState()),
                SurfaceControl.Transaction(),
                iCallback,
            )
            testScope.advanceUntilIdle()
            waitForIdleSync()

            assertThat(delegateInitialized).isTrue()
        }
    }

    @Test
    fun concurrentListenerModification_doesNotThrow() {
        kosmos.runTest {
            // Need a second listener to trigger the concurrent modification.
            underTest.addListener(object : ActivityTransitionAnimator.Listener {})
            whenever(listener.onTransitionAnimationStart()).thenAnswer {
                underTest.removeListener(listener)
                listener
            }

            val controller = createController()
            val runner = underTest.createEphemeralRunner(controller)
            runner.onAnimationStart(
                TRANSIT_NONE,
                arrayOf(fakeWindow()),
                emptyArray(),
                emptyArray(),
                iCallback,
            )

            waitForIdleSync()
            verify(listener).onTransitionAnimationStart()
        }
    }

    private fun createController(): TestTransitionAnimatorController {
        lateinit var transitionContainer: ViewGroup
        activityRule.scenario.onActivity { activity ->
            transitionContainer = LinearLayout(activity)
            activity.setContentView(transitionContainer)
        }
        waitForIdleSync()
        return spy(TestTransitionAnimatorController(transitionContainer))
    }

    private fun controllerFactory(
        controller: ActivityTransitionAnimator.Controller,
        cookie: ActivityTransitionAnimator.TransitionCookie =
            mock(ActivityTransitionAnimator.TransitionCookie::class.java),
        component: ComponentName? = mock(ComponentName::class.java),
    ): ActivityTransitionAnimator.ControllerFactory {
        return object : ActivityTransitionAnimator.ControllerFactory(cookie, component) {
            override suspend fun createController(forLaunch: Boolean) =
                object : DelegateTransitionAnimatorController(controller) {
                    override val isLaunching: Boolean
                        get() = forLaunch
                }
        }
    }

    private fun fakeWindow(mode: Int = MODE_OPENING): RemoteAnimationTarget {
        val bounds = Rect(10 /* left */, 20 /* top */, 30 /* right */, 40 /* bottom */)
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.topActivity = ComponentName("com.android.systemui", "FakeActivity")
        taskInfo.topActivityInfo = ActivityInfo().apply { applicationInfo = ApplicationInfo() }

        return RemoteAnimationTarget(
            0,
            mode,
            SurfaceControl(),
            false,
            Rect(),
            Rect(),
            1,
            Point(),
            Rect(),
            bounds,
            WindowConfiguration(),
            false,
            SurfaceControl(),
            Rect(),
            taskInfo,
            false,
        )
    }

    private fun createChange(
        leash: SurfaceControl,
        cookie: IBinder,
        forLaunch: Boolean,
    ): TransitionInfo.Change {
        return TransitionInfo.Change(null, leash).apply {
            mode =
                if (forLaunch) {
                    TRANSIT_OPEN
                } else {
                    TRANSIT_CLOSE
                }

            taskInfo = RunningTaskInfo().apply { launchCookies = arrayListOf(cookie) }
            backgroundColor = Color.Green.value.toInt()
            setEndAbsBounds(Rect(0, 0, 200, 200))
        }
    }

    private fun finishedCallback(callback: () -> Unit): IRemoteTransitionFinishedCallback =
        object : IRemoteTransitionFinishedCallback {
            override fun onTransitionFinished(
                wct: WindowContainerTransaction?,
                sct: SurfaceControl.Transaction?,
            ) = callback()

            override fun asBinder(): IBinder = Binder(callback.toString())
        }
}

/**
 * A fake implementation of [ShellTransitions] which saves filter-transition pairs locally and
 * allows inspection.
 */
private class FakeShellTransitions : ShellTransitions {
    val remotes = mutableMapOf<TransitionFilter, RemoteTransition>()
    val remotesForTakeover = mutableMapOf<TransitionFilter, RemoteTransition>()

    override fun registerRemote(filter: TransitionFilter, remoteTransition: RemoteTransition) {
        remotes[filter] = remoteTransition
    }

    override fun registerRemoteForTakeover(
        filter: TransitionFilter,
        remoteTransition: RemoteTransition,
    ) {
        remotesForTakeover[filter] = remoteTransition
    }

    override fun unregisterRemote(remoteTransition: RemoteTransition) {
        while (remotes.containsValue(remoteTransition)) {
            remotes.values.remove(remoteTransition)
        }
        while (remotesForTakeover.containsValue(remoteTransition)) {
            remotesForTakeover.values.remove(remoteTransition)
        }
    }
}

/**
 * A simple implementation of [ActivityTransitionAnimator.Controller] which throws if it is called
 * outside of the main thread.
 */
private class TestTransitionAnimatorController(override var transitionContainer: ViewGroup) :
    ActivityTransitionAnimator.Controller {
    override val isLaunching: Boolean = true

    override fun createAnimatorState() =
        TransitionAnimator.State(
            top = 100,
            bottom = 200,
            left = 300,
            right = 400,
            topCornerRadius = 10f,
            bottomCornerRadius = 20f,
        )

    private fun assertOnMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw AssertionFailedError("Called outside of main thread.")
        }
    }

    override fun onIntentStarted(willAnimate: Boolean) {
        assertOnMainThread()
    }

    override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
        assertOnMainThread()
    }

    override fun onTransitionAnimationProgress(
        state: TransitionAnimator.State,
        progress: Float,
        linearProgress: Float,
    ) {
        assertOnMainThread()
    }

    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
        assertOnMainThread()
    }

    override fun onTransitionAnimationCancelled(newKeyguardOccludedState: Boolean?) {
        assertOnMainThread()
    }
}
