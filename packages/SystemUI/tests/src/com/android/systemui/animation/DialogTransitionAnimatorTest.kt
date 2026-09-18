package com.android.systemui.animation

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.testing.ViewUtils
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.jank.Cuj
import com.android.internal.policy.DecorView
import com.android.systemui.Flags.FLAG_ENABLE_DIALOG_SPRING_ANIMATION
import com.android.systemui.SysuiTestCase
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class DialogTransitionAnimatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var mDialogTransitionAnimator: DialogTransitionAnimator
    private val attachedViews = mutableSetOf<View>()
    /** Set of dialogs to dismiss at the end of the test. */
    private val createdDialogs = mutableSetOf<TestDialog>()
    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Before
    fun setUp() {
        mDialogTransitionAnimator = kosmos.dialogTransitionAnimator
    }

    @After
    fun tearDown() {
        runOnMainThreadAndWaitForIdleSync { attachedViews.forEach { ViewUtils.detachView(it) } }

        runOnMainThreadAndWaitForIdleSync {
            mDialogTransitionAnimator.openedDialogs.toSet().forEach {
                it.exitAnimationDisabled = true
                it.dialog.dismiss()
            }
        }

        var anyShowing = false

        runOnMainThreadAndWaitForIdleSync {
            createdDialogs.forEach {
                anyShowing = anyShowing or it.isShowing
                it.dismiss()
            }
        }

        waitForSpringAnimationToBeOver()

        if (anyShowing) {
            createdDialogs.forEach { assertFalse(it.isShowing) }
        }

        mDialogTransitionAnimator.openedDialogs.clear()
        createdDialogs.clear()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testShowDialogFromView_withInterceptorViewFlagEnabled_linearAnimation() {
        // Show the dialog. showFromView() must be called on the main thread with a dialog created
        // on the main thread too.
        val dialog = createAndShowDialog()

        assertTrue(dialog.isShowing)

        // The dialog is now fullscreen.
        val window = checkNotNull(dialog.window)
        val decorView = window.decorView as DecorView
        assertEquals(MATCH_PARENT, window.attributes.width)
        assertEquals(MATCH_PARENT, window.attributes.height)
        assertEquals(MATCH_PARENT, decorView.layoutParams.width)
        assertEquals(MATCH_PARENT, decorView.layoutParams.height)

        // The single transparent background child is a fake window with the same size and
        // background as the dialog initially had and a touchInterceptor view behind background
        // for consuming click to stop its dismissal during animation.\

        val transparentBackground = decorView.getChildAt(0) as ViewGroup
        val dialogContentWithBackground = transparentBackground.getChildAt(1) as ViewGroup
        val touchInterceptorView = transparentBackground.getChildAt(0) as ViewGroup

        assertEquals(2, transparentBackground.childCount)
        touchInterceptorView.apply {
            assertEquals(View.GONE, visibility)
            assertEquals(DIALOG_WIDTH, layoutParams.width)
            assertEquals(DIALOG_HEIGHT, layoutParams.height)
        }

        assertEquals(DIALOG_WIDTH, dialogContentWithBackground.layoutParams.width)
        assertEquals(DIALOG_HEIGHT, dialogContentWithBackground.layoutParams.height)
        assertEquals(dialog.windowBackground, dialogContentWithBackground.background)

        // The dialog content is inside this fake window view.
        assertNotNull(dialogContentWithBackground.findViewByPredicate { it === dialog.contentView })

        // Clicking the transparent background should dismiss the dialog.
        runOnMainThreadAndWaitForIdleSync { transparentBackground.performClick() }
        assertFalse(dialog.isShowing)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testShowDialogFromView_withInterceptorViewFlagEnabled_springAnimation() {
        // Show the dialog. showFromView() must be called on the main thread with a dialog created
        // on the main thread too.
        val dialog = createAndShowDialog()
        waitForSpringAnimationToBeOver()

        assertTrue(dialog.isShowing)

        // The dialog is now fullscreen.
        val window = checkNotNull(dialog.window)
        val decorView = window.decorView as DecorView
        assertEquals(MATCH_PARENT, window.attributes.width)
        assertEquals(MATCH_PARENT, window.attributes.height)
        assertEquals(MATCH_PARENT, decorView.layoutParams.width)
        assertEquals(MATCH_PARENT, decorView.layoutParams.height)

        // The single transparent background child is a fake window with the same size and
        // background as the dialog initially had and a touchInterceptor view behind background
        // for consuming click to stop its dismissal during animation.\

        val transparentBackground = decorView.getChildAt(0) as ViewGroup
        val dialogContentWithBackground = transparentBackground.getChildAt(1) as ViewGroup
        val touchInterceptorView = transparentBackground.getChildAt(0) as ViewGroup

        assertEquals(2, transparentBackground.childCount)
        touchInterceptorView.apply {
            assertEquals(View.GONE, visibility)
            assertEquals(DIALOG_WIDTH, layoutParams.width)
            assertEquals(DIALOG_HEIGHT, layoutParams.height)
        }

        assertEquals(DIALOG_WIDTH, dialogContentWithBackground.layoutParams.width)
        assertEquals(DIALOG_HEIGHT, dialogContentWithBackground.layoutParams.height)
        assertEquals(dialog.windowBackground, dialogContentWithBackground.background)

        // The dialog content is inside this fake window view.
        assertNotNull(dialogContentWithBackground.findViewByPredicate { it === dialog.contentView })

        // Clicking the transparent background should dismiss the dialog.
        runOnMainThreadAndWaitForIdleSync { transparentBackground.performClick() }
        waitForSpringAnimationToBeOver()

        assertFalse(dialog.isShowing)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testStackedDialogsDismissesAll_linearAnimation() {
        val firstDialog = createAndShowDialog()
        val secondDialog = createDialogAndShowFromDialog(firstDialog)

        assertTrue(firstDialog.isShowing)
        assertTrue(secondDialog.isShowing)
        runOnMainThreadAndWaitForIdleSync { mDialogTransitionAnimator.dismissStack(secondDialog) }

        assertFalse(firstDialog.isShowing)
        assertFalse(secondDialog.isShowing)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testStackedDialogsDismissesAll_springAnimation() {
        val firstDialog = createAndShowDialog()

        waitForSpringAnimationToBeOver()
        val secondDialog = createDialogAndShowFromDialog(firstDialog)
        waitForSpringAnimationToBeOver()

        assertTrue(firstDialog.isShowing)
        assertTrue(secondDialog.isShowing)
        runOnMainThreadAndWaitForIdleSync { mDialogTransitionAnimator.dismissStack(secondDialog) }

        waitForSpringAnimationToBeOver()

        assertFalse(firstDialog.isShowing)
        assertFalse(secondDialog.isShowing)
    }

    @Test
    fun testActivityTransitionControllerFromDialog() {
        val firstDialog = createAndShowDialog()
        val secondDialog = createDialogAndShowFromDialog(firstDialog)

        val controller =
            mDialogTransitionAnimator.createActivityTransitionController(secondDialog.contentView)!!

        // The dialog shouldn't be dismissable during the animation.
        runOnMainThreadAndWaitForIdleSync {
            controller.onTransitionAnimationStart(isExpandingFullyAbove = true)
            secondDialog.dismiss()
        }
        assertTrue(secondDialog.isShowing)

        // Both dialogs should be dismissed at the end of the animation.
        runOnMainThreadAndWaitForIdleSync {
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = true)
        }
        assertFalse(firstDialog.isShowing)
        assertFalse(secondDialog.isShowing)
    }

    @Test
    fun testActivityLaunchFromHiddenDialog() {
        val dialog = createAndShowDialog()
        runOnMainThreadAndWaitForIdleSync { dialog.hide() }
        assertNull(mDialogTransitionAnimator.createActivityTransitionController(dialog.contentView))
    }

    @Test
    fun testActivityLaunchWhenLockedWithoutAlternateAuth() {
        val dialogTransitionAnimator =
            fakeDialogTransitionAnimator(
                mainExecutor = mContext.mainExecutor,
                isUnlocked = false,
                isShowingAlternateAuthOnUnlock = false,
                interactionJankMonitor = kosmos.interactionJankMonitor,
            )
        val dialog = createAndShowDialog(dialogTransitionAnimator)
        waitForSpringAnimationToBeOver(dialogTransitionAnimator)
        assertNull(dialogTransitionAnimator.createActivityTransitionController(dialog.contentView))
    }

    @Test
    fun testActivityLaunchWhenLockedWithAlternateAuth() {
        val dialogTransitionAnimator =
            fakeDialogTransitionAnimator(
                mainExecutor = mContext.mainExecutor,
                isUnlocked = false,
                isShowingAlternateAuthOnUnlock = true,
                interactionJankMonitor = kosmos.interactionJankMonitor,
            )
        val dialog = createAndShowDialog(dialogTransitionAnimator)
        waitForSpringAnimationToBeOver(dialogTransitionAnimator)
        assertNotNull(
            dialogTransitionAnimator.createActivityTransitionController(dialog.contentView)
        )
    }

    @Test
    fun testDialogAnimationIsChangedByAnimator() {
        // Important: the power menu animation relies on this behavior to know when to animate (see
        // http://ag/16774605).
        val dialog = runOnMainThreadAndWaitForIdleSync { TestDialog(context) }
        val window = checkNotNull(dialog.window)
        window.setWindowAnimations(0)
        assertEquals(0, window.attributes.windowAnimations)

        val touchSurface = createTouchSurface()
        runOnMainThreadAndWaitForIdleSync {
            mDialogTransitionAnimator.showFromView(dialog, touchSurface)
        }
        assertNotEquals(0, window.attributes.windowAnimations)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testCujSpecificationLogsInteraction_linearAnimation() {
        val touchSurface = createTouchSurface()
        runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.showFromView(
                dialog,
                touchSurface,
                cuj = DialogCuj(Cuj.CUJ_SHADE_DIALOG_OPEN),
            )
        }

        verify(kosmos.interactionJankMonitor).begin(any())
        verify(kosmos.interactionJankMonitor).end(Cuj.CUJ_SHADE_DIALOG_OPEN)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testCujSpecificationLogsInteraction_springAnimation() {
        val touchSurface = createTouchSurface()
        showDialogFromView(
            touchSurface,
            mDialogTransitionAnimator,
            cuj = DialogCuj(Cuj.CUJ_SHADE_DIALOG_OPEN),
        )
        waitForSpringAnimationToBeOver()
        // waitForIdleSync()

        verify(kosmos.interactionJankMonitor).begin(any())
        verify(kosmos.interactionJankMonitor).end(Cuj.CUJ_SHADE_DIALOG_OPEN)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testShowFromDialogCujSpecificationLogsInteractionWithLinearAnimation() {
        val firstDialog = createAndShowDialog()
        runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.showFromDialog(
                dialog,
                firstDialog,
                cuj = DialogCuj(Cuj.CUJ_USER_DIALOG_OPEN),
            )
            dialog
        }
        verify(kosmos.interactionJankMonitor).begin(any())
        verify(kosmos.interactionJankMonitor).end(Cuj.CUJ_USER_DIALOG_OPEN)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testShowFromDialogCujSpecificationLogsInteractionWithSpringAnimation() {
        val firstDialog = createAndShowDialog()

        waitForSpringAnimationToBeOver()

        runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.showFromDialog(
                dialog,
                firstDialog,
                cuj = DialogCuj(Cuj.CUJ_USER_DIALOG_OPEN),
            )
            dialog
        }
        waitForSpringAnimationToBeOver()
        verify(kosmos.interactionJankMonitor).begin(any())
        verify(kosmos.interactionJankMonitor).end(Cuj.CUJ_USER_DIALOG_OPEN)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testAnimationDoesNotChangeLaunchableViewVisibility_viewVisible_linearAnimation() {
        val touchSurface = createTouchSurface()

        // View is VISIBLE when starting the animation.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.VISIBLE }

        // View is invisible while the dialog is shown.
        val dialog = showDialogFromView(touchSurface)
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // View is visible again when the dialog is dismissed.
        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        assertThat(touchSurface.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testAnimationDoesNotChangeLaunchableViewVisibility_viewVisible_springAnimation() {
        val touchSurface = createTouchSurface()

        // View is VISIBLE when starting the animation.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.VISIBLE }

        // View is invisible while the dialog is shown.
        val dialog = showDialogFromView(touchSurface)
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // View is visible again when the dialog is dismissed.
        dismissDialog(dialog)
        assertThat(touchSurface.visibility).isEqualTo(View.VISIBLE)
    }

    private fun dismissDialog(dialog: TestDialog) {
        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        waitForSpringAnimationToBeOver()
    }

    @Test
    fun testAnimationDoesNotChangeLaunchableViewVisibility_viewInvisible() {
        val touchSurface = createTouchSurface()

        // View is INVISIBLE when starting the animation.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.INVISIBLE }

        // View is INVISIBLE while the dialog is shown.
        val dialog = showDialogFromView(touchSurface)
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // View is invisible like it was before showing the dialog.
        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testAnimationDoesNotChangeLaunchableViewVisibility_viewVisibleThenGone_linearAnimation() {
        val touchSurface = createTouchSurface()
        // View is VISIBLE when starting the animation.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.VISIBLE }

        // View is INVISIBLE while the dialog is shown.
        val dialog = showDialogFromView(touchSurface)
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // Some external call makes the View GONE. It remains INVISIBLE while the dialog is shown,
        // as all visibility changes should be blocked.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.GONE }
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // View is restored to GONE once the dialog is dismissed.
        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        assertThat(touchSurface.visibility).isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DIALOG_SPRING_ANIMATION)
    fun testAnimationDoesNotChangeLaunchableViewVisibility_viewVisibleThenGone_springAnimation() {
        val touchSurface = createTouchSurface()

        // View is VISIBLE when starting the animation.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.VISIBLE }

        // View is INVISIBLE while the dialog is shown.
        val dialog = showDialogFromView(touchSurface)
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // Some external call makes the View GONE. It remains INVISIBLE while the dialog is shown,
        // as all visibility changes should be blocked.
        runOnMainThreadAndWaitForIdleSync { touchSurface.visibility = View.GONE }
        assertThat(touchSurface.visibility).isEqualTo(View.INVISIBLE)

        // View is restored to GONE once the dialog is dismissed.
        dismissDialog(dialog)
        assertThat(touchSurface.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun creatingControllerFromNormalViewThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            DialogTransitionAnimator.Controller.fromView(FrameLayout(mContext))
        }
    }

    @Test
    fun showFromDialogDoesNotCrashWhenShownFromRandomDialog() {
        val dialog = createDialogAndShowFromDialog(animateFrom = TestDialog(context))
        dialog.dismiss()
    }

    @Test
    fun testDismissWithNullController_callsOnExitAnimationCancelledOnStartController() {
        val touchSurface = createTouchSurface()
        val controller =
            object :
                DialogTransitionAnimator.Controller by DialogTransitionAnimator.Controller.fromView(
                    touchSurface
                )!! {
                var exitAnimationCancelledCalled = false

                override fun onExitAnimationCancelled() {
                    exitAnimationCancelledCalled = true
                }
            }

        var returnNullOnDismiss = false
        val resolveController: (DialogCuj?) -> DialogTransitionAnimator.Controller? = {
            if (returnNullOnDismiss) null else controller
        }

        val dialog = runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.show(dialog, resolveController)
            dialog
        }
        waitForSpringAnimationToBeOver()

        returnNullOnDismiss = true
        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        waitForSpringAnimationToBeOver()

        assertTrue(controller.exitAnimationCancelledCalled)
    }

    @Test
    fun testShowWithSameIdentity_skipsAnimation() {
        val touchSurface = createTouchSurface()
        val identity = Any()
        val controller =
            object :
                DialogTransitionAnimator.Controller by DialogTransitionAnimator.Controller.fromView(
                    touchSurface
                )!! {
                override val dialogIdentity = identity
            }

        // Show first dialog.
        val dialog1 = runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.show(dialog, controller)
            dialog
        }

        waitForSpringAnimationToBeOver()

        // Show second dialog with same identity.
        val dialog2 = runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.show(dialog, controller)
            dialog
        }

        waitForSpringAnimationToBeOver()

        assertTrue(dialog1.isShowing)
        assertTrue(dialog2.isShowing)

        // dialog1 should have been shown with animation (so it's fullscreen).
        assertEquals(MATCH_PARENT, dialog1.window?.attributes?.width)

        // dialog2 should have been shown normally (so it has DIALOG_WIDTH).
        assertEquals(DIALOG_WIDTH, dialog2.window?.attributes?.width)
    }

    @Test
    fun testShowWithResolveController_callsResolveControllerOnShowAndDismiss() {
        val touchSurface = createTouchSurface()
        val controller = DialogTransitionAnimator.Controller.fromView(touchSurface)!!
        var resolveCount = 0

        val resolveController: (DialogCuj?) -> DialogTransitionAnimator.Controller? = {
            resolveCount++
            controller
        }

        val dialog = runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.show(dialog, resolveController)
            dialog
        }
        waitForSpringAnimationToBeOver()

        // resolveController should be called during show.
        assertEquals(2, resolveCount)

        runOnMainThreadAndWaitForIdleSync { dialog.dismiss() }
        waitForSpringAnimationToBeOver()

        // resolveController should be called again during dismiss.
        assertEquals(3, resolveCount)
    }

    private fun createAndShowDialog(
        animator: DialogTransitionAnimator = mDialogTransitionAnimator
    ): TestDialog {
        val touchSurface = createTouchSurface()
        return showDialogFromView(touchSurface, animator)
    }

    private fun createTouchSurface(): View {
        return runOnMainThreadAndWaitForIdleSync {
            val touchSurfaceRoot = LinearLayout(context)
            val touchSurface = TouchSurfaceView(context)
            touchSurfaceRoot.addView(touchSurface)

            // We need to attach the root to the window manager otherwise the exit animation will
            // be skipped.
            ViewUtils.attachView(touchSurfaceRoot)
            attachedViews.add(touchSurfaceRoot)

            touchSurface
        }
    }

    private fun waitForSpringAnimationToBeOver(
        dialogTransitionAnimator: DialogTransitionAnimator = mDialogTransitionAnimator
    ) {
        waitForIdleSync()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < DIALOG_SPRING_ANIMATION_TIMEOUT) {
            if (isSpringAnimationOver(dialogTransitionAnimator)) {
                waitForIdleSync()
                // Check again to be sure no new animation started or was missed
                if (isSpringAnimationOver(dialogTransitionAnimator)) return
            }
            Thread.sleep(10)
            waitForIdleSync()
        }
        throw AssertionError(
            "Spring animation did not finish within ${DIALOG_SPRING_ANIMATION_TIMEOUT}ms"
        )
    }

    private fun isSpringAnimationOver(dialogTransitionAnimator: DialogTransitionAnimator): Boolean {
        if (dialogTransitionAnimator.openedDialogs.isEmpty()) return true

        for (dialog in dialogTransitionAnimator.openedDialogs) {
            val animation = dialog.animation
            if (animation is TransitionAnimator.MultiSpringAnimation && !animation.isDone) {
                return false
            }
        }

        return true
    }

    private fun showDialogFromView(
        touchSurface: View,
        animator: DialogTransitionAnimator = mDialogTransitionAnimator,
        cuj: DialogCuj? = null,
    ): TestDialog {
        return runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            animator.showFromView(dialog, touchSurface, cuj)
            dialog
        }
    }

    private fun createDialogAndShowFromDialog(animateFrom: Dialog): TestDialog {
        return runOnMainThreadAndWaitForIdleSync {
            val dialog = TestDialog(context)
            mDialogTransitionAnimator.showFromDialog(dialog, animateFrom)
            dialog
        }
    }

    private fun <T : Any> runOnMainThreadAndWaitForIdleSync(f: () -> T): T {
        lateinit var result: T
        context.mainExecutor.execute { result = f() }
        waitForIdleSync()
        return result
    }

    private class TouchSurfaceView(context: Context) : FrameLayout(context), LaunchableView {
        private val delegate =
            LaunchableViewDelegate(this, superSetVisibility = { super.setVisibility(it) })

        override fun setShouldBlockVisibilityChanges(block: Boolean) {
            delegate.setShouldBlockVisibilityChanges(block)
        }

        override fun setVisibility(visibility: Int) {
            delegate.setVisibility(visibility)
        }
    }

    private inner class TestDialog(context: Context) : Dialog(context) {

        val contentView = View(context)
        val windowBackground = ColorDrawable(Color.RED)

        init {
            // We need to set the window type for dialogs shown by SysUI, otherwise WM will throw.
            checkNotNull(window).setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(contentView)

            val window = checkNotNull(window)
            window.setLayout(DIALOG_WIDTH, DIALOG_HEIGHT)
            window.setBackgroundDrawable(windowBackground)

            createdDialogs.add(this)
        }
    }

    companion object {
        const val DIALOG_SPRING_ANIMATION_TIMEOUT = 5000
        const val DIALOG_WIDTH = 100
        const val DIALOG_HEIGHT = 200
    }
}
