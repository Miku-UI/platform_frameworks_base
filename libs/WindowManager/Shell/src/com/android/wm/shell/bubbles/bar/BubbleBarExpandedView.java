/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.bubbles.bar;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static java.lang.Math.max;

import android.annotation.CallbackExecutor;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewRootImpl;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.bubbles.BubbleExpandedViewManager;
import com.android.wm.shell.bubbles.BubbleOverflowContainerView;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleTaskView;
import com.android.wm.shell.bubbles.BubbleTaskViewListener;
import com.android.wm.shell.bubbles.Bubbles;
import com.android.wm.shell.bubbles.logging.BubbleLogger;
import com.android.wm.shell.bubbles.util.BubblePolicyHelper;
import com.android.wm.shell.bubbles.util.DefaultBubblePolicyHelper;
import com.android.wm.shell.dagger.HasWMComponent;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.ContextUtils;
import com.android.wm.shell.shared.bubbles.logging.BubbleLog;
import com.android.wm.shell.taskview.TaskView;

import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.inject.Inject;

/** Expanded view of a bubble when it's part of the bubble bar. */
public class BubbleBarExpandedView extends FrameLayout implements BubbleTaskViewListener.Callback {
    /**
     * The expanded view listener notifying the {@link BubbleBarLayerView} about the internal
     * actions and events
     */
    public interface Listener {
        /** Called when the task view task is first created. */
        void onTaskCreated();
        /** Called when expanded view needs to un-bubble the given conversation */
        void onUnBubbleConversation(String bubbleKey);
        /** Called when expanded view task view back button pressed */
        void onBackPressed();
        /** Called when the handle menu is opened */
        void onHandleMenuOpened();
    }

    /**
     * A property wrapper around corner radius for the expanded view, handled by
     * {@link #setCornerRadius(float)} and {@link #getCornerRadius()} methods.
     */
    public static final FloatProperty<BubbleBarExpandedView> CORNER_RADIUS = new FloatProperty<>(
            "cornerRadius") {
        @Override
        public void setValue(BubbleBarExpandedView bbev, float radius) {
            bbev.setCornerRadius(radius);
        }

        @Override
        public Float get(BubbleBarExpandedView bbev) {
            return bbev.getCornerRadius();
        }
    };

    /**
     * Property to set alpha for the task view
     */
    public static final FloatProperty<BubbleBarExpandedView> TASK_VIEW_ALPHA = new FloatProperty<>(
            "taskViewAlpha") {
        @Override
        public void setValue(BubbleBarExpandedView bbev, float alpha) {
            bbev.setTaskViewAlpha(alpha);
        }

        @Override
        public Float get(BubbleBarExpandedView bbev) {
            return bbev.mTaskView != null ? bbev.mTaskView.getAlpha() : bbev.getAlpha();
        }
    };

    private static final String TAG = BubbleBarExpandedView.class.getSimpleName();
    private static final int INVALID_TASK_ID = -1;

    enum ObscuredFlag {
        // Obscured due to user education showing
        USER_EDUCATION_VISIBLE,
        // Obscured due to handle menu showing
        HANDLE_MENU_VISIBLE,
    }
    private final EnumSet<ObscuredFlag> mObscuredFlags = EnumSet.noneOf(ObscuredFlag.class);

    private Bubble mBubble;
    private BubbleExpandedViewManager mManager;
    private BubblePositioner mPositioner;
    private boolean mIsOverflow;
    private BubbleTaskViewListener mBubbleTaskViewListener;
    private BubbleBarMenuViewController mMenuViewController;
    @Nullable
    private Supplier<Rect> mLayerBoundsSupplier;
    @Nullable
    private Listener mListener;

    private BubbleBarCaptionView mCaptionView;
    @Nullable
    private BubbleTaskView mBubbleTaskView;
    @Nullable
    private TaskView mTaskView;
    @Nullable
    private BubbleOverflowContainerView mOverflowView;
    private final Rect mTempBounds = new Rect();

    /** Height of the caption inset at the top of the TaskView */
    private int mCaptionHeight;
    /** Corner radius used when view is resting */
    private float mRestingCornerRadius = 0f;
    /** Corner radius applied while dragging */
    private float mDraggedCornerRadius = 0f;
    /** Current corner radius */
    private float mCurrentCornerRadius = 0f;

    /** A runnable to start the expansion animation as soon as the task view is made visible. */
    @Nullable
    private Runnable mAnimateExpansion = null;

    /**
     * A runnable to be executed if the expansion animation is canceled before the task view is made
     * visible. If the animation is run, the end runnable is executed with the animation, so only
     * need to run the end runnable if the animation is dropped before it is ever started.
     */
    @Nullable
    private Runnable mAnimateExpansionEndRunnable = null;

    /**
     * Whether we want the {@code TaskView}'s content to be visible (alpha = 1f). If
     * {@link #mIsAnimating} is true, this may not reflect the {@code TaskView}'s actual alpha
     * value until the animation ends.
     */
    private boolean mIsContentVisible = false;
    private boolean mIsAnimating;
    private boolean mIsDragging;

    private boolean mIsClipping = false;
    private int mBottomClip = 0;
    private int mImeTop = 0;

    // Ideally this would be package private, but we have to set this in a fake for test and we
    // don't yet have dagger set up for tests, so have to set manually
    @VisibleForTesting
    @Inject
    public BubbleLogger bubbleLogger;

    @VisibleForTesting
    BubblePolicyHelper mBubblePolicyHelper = DefaultBubblePolicyHelper.INSTANCE;

    public BubbleBarExpandedView(Context context) {
        this(context, null);
    }

    public BubbleBarExpandedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleBarExpandedView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleBarExpandedView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Context context = getContext();
        if (context instanceof HasWMComponent) {
            ((HasWMComponent) context).getWMComponent().inject(this);
        }
        setElevation(getResources().getDimensionPixelSize(R.dimen.bubble_elevation));
        mCaptionHeight = context.getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_caption_height);
        mCaptionView = findViewById(R.id.bubble_bar_caption_view);
        applyThemeAttrs();
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() - mBottomClip,
                        mCurrentCornerRadius);
            }
        });
        // Set a touch sink to ensure that clicks on the caption area do not propagate to the parent
        setOnTouchListener((v, event) -> true);
    }

    /** Initializes the view, must be called before doing anything else. */
    public void initialize(BubbleExpandedViewManager expandedViewManager,
            BubblePositioner positioner,
            boolean isOverflow,
            @Nullable Bubble bubble,
            @Nullable BubbleTaskView bubbleTaskView) {
        mBubble = bubble;
        mManager = expandedViewManager;
        mPositioner = positioner;
        mIsOverflow = isOverflow;

        if (mIsOverflow) {
            mOverflowView = (BubbleOverflowContainerView) LayoutInflater.from(getContext()).inflate(
                    R.layout.bubble_overflow_container, null /* root */);
            mOverflowView.initialize(expandedViewManager, positioner);
            addView(mOverflowView);
            // Don't show caption or handle for overflow
            mCaptionView.setVisibility(View.GONE);
            getHandleView().setVisibility(View.GONE);
        } else {
            mBubbleTaskView = bubbleTaskView;
            mTaskView = bubbleTaskView.getTaskView();
            mBubbleTaskViewListener = new BubbleTaskViewListener(mContext, bubbleTaskView,
                    /* viewParent= */ this,
                    expandedViewManager,
                    /* callback= */ this);

            // if the task view is already attached to a parent we need to remove it
            if (mTaskView.getParent() != null) {
                ((ViewGroup) mTaskView.getParent()).removeView(mTaskView);
            }
            setupTaskView();

            getHandleView().setAccessibilityDelegate(new HandleViewAccessibilityDelegate());
        }
        mMenuViewController =
                new BubbleBarMenuViewController(mContext, getHandleView(), this);
        mMenuViewController.setListener(new BubbleBarMenuViewController.Listener() {
            @Override
            public void onMenuVisibilityChanged(boolean visible) {
                setObscured(visible, ObscuredFlag.HANDLE_MENU_VISIBLE);
                if (visible) {
                    if (mListener != null) {
                        mListener.onHandleMenuOpened();
                    }
                    getHandleView().setFocusable(false);
                    getHandleView().setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                } else {
                    getHandleView().setFocusable(true);
                    getHandleView().setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                }
            }

            @Override
            public void onUnBubbleConversation(Bubble bubble) {
                if (mListener != null) {
                    mListener.onUnBubbleConversation(bubble.getKey());
                }
                bubbleLogger.log(bubble, BubbleLogger.Event.BUBBLE_BAR_APP_MENU_OPT_OUT);
            }

            @Override
            public void onOpenAppSettings(Bubble bubble) {
                mManager.collapseStack();
                mContext.startActivityAsUser(bubble.getSettingsIntent(mContext), bubble.getUser());
                bubbleLogger.log(bubble, BubbleLogger.Event.BUBBLE_BAR_APP_MENU_GO_TO_SETTINGS);
            }

            @Override
            public void onDismissBubble(Bubble bubble) {
                mManager.dismissBubble(bubble, Bubbles.DISMISS_USER_GESTURE);
                bubbleLogger.log(bubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_APP_MENU);
            }

            @Override
            public void onMoveToFullscreen(Bubble bubble) {
                if (mTaskView != null) {
                    mTaskView.moveToFullscreen();
                }
            }
        });
        getHandleView().setOnClickListener(view -> {
            mMenuViewController.showMenu(true /* animated */);
        });
    }

    private void setupTaskView() {
        // if we're converting this bubble to bar mode, set the isMovingWindows state to false for
        // this task view before adding it as a child view.
        if (mBubble.isConvertingToBar()) {
            mTaskView.setIsMovingWindows(false);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        addView(mTaskView, lp);
        mTaskView.setEnableSurfaceClipping(true);
        mTaskView.setClipBounds(null);
        mTaskView.setCornerRadius(mCurrentCornerRadius);
        mTaskView.setVisibility(VISIBLE);
        mTaskView.setCaptionInsets(Insets.of(0, mCaptionHeight, 0, 0));
    }

    public BubbleBarHandleView getHandleView() {
        return mCaptionView.getHandleView();
    }

    public BubbleBarCaptionView getCaptionView() {
        return mCaptionView;
    }

    /** Updates the view based on the current theme. */
    public void applyThemeAttrs() {
        mCaptionHeight = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_caption_height);
        mRestingCornerRadius = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_corner_radius);
        mDraggedCornerRadius = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_corner_radius_dragged);

        mCurrentCornerRadius = mRestingCornerRadius;

        if (mTaskView != null) {
            mTaskView.setCornerRadius(mCurrentCornerRadius);
            mTaskView.setCaptionInsets(Insets.of(0, mCaptionHeight, 0, 0));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Hide manage menu when view disappears
        mMenuViewController.hideMenu(false /* animated */);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTaskView != null) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            measureChild(mTaskView, widthMeasureSpec, MeasureSpec.makeMeasureSpec(height,
                    MeasureSpec.getMode(heightMeasureSpec)));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mTaskView != null) {
            mTaskView.layout(l, t, r, t + mTaskView.getMeasuredHeight());
        }
    }

    @Override
    public void onTaskCreated() {
        if (mTaskView != null && !mBubble.isConvertingToBar()) {
            mTaskView.setAlpha(0);
        }
        if (mListener != null) {
            mListener.onTaskCreated();
        }
        // when the task is created we're visible
        onTaskViewVisible();
    }

    @Override
    public void onContentVisibilityChanged(boolean visible) {
        if (visible) {
            onTaskViewVisible();
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (mManager != null && taskInfo != null
                && !mBubblePolicyHelper.isValidToBubble(taskInfo)) {
            // Since the task is no longer valid to be bubbled after a state change (e.g. on fold),
            // collapse the stack. When the user taps the bubble again, it will move out of bubble.
            mManager.collapseStack();
        } else if (mCaptionView != null && taskInfo != null && taskInfo.taskDescription != null) {
            final int statusBarColor = taskInfo.taskDescription.getStatusBarColor();
            final int bgColor = taskInfo.taskDescription.getBackgroundColor();
            if (Color.alpha(statusBarColor) == 0xff) {
                // Set the caption's color to the color of the status bar if opaque.
                mCaptionView.setBackgroundColor(statusBarColor);
            } else if (Color.alpha(bgColor) == 0xff) {
                // Otherwise, use the background color of the task if opaque.
                mCaptionView.setBackgroundColor(bgColor);
            }
        }
        if (mBubble != null && taskInfo != null) {
            final boolean isTaskResizable = taskInfo.isResizeable;
            final boolean isNonResizableTaskAndValidOnSmallScreen = !isTaskResizable
                    && ContextUtils.getSupportsNonResizableMultiWindowOnSmallScreen(mContext);
            mBubble.setIsTaskValidToBubbleOnSmallScreen(
                    isTaskResizable || isNonResizableTaskAndValidOnSmallScreen);
        }
    }

    @Override
    public void onBackPressed() {
        if (mListener == null) return;
        mListener.onBackPressed();
    }

    /**
     * Returns {@code true} if the animation is scheduled instead of being run immediately.
     *
     * @param animateExpansion the {@link Runnable} to be run when the TaskView is visible.
     * @param endRunnable the {@link Runnable} to be run only when the animation is canceled by
     *                    {@link #cancelPendingAnimation()}.
     */
    boolean animateExpansionWhenTaskViewVisible(
            @NonNull Runnable animateExpansion, @Nullable Runnable endRunnable) {
        if ((mBubbleTaskView != null && mBubbleTaskView.isVisible()) || mIsOverflow) {
            BubbleLog.d(
                    "BubbleBarExpandedView.animateExpansionWhenTaskViewVisible() key=%s animate",
                    getBubbleKey());
            animateExpansion.run();
            return false;
        } else {
            BubbleLog.d("BubbleBarExpandedView.animateExpansionWhenTaskViewVisible() key=%s wait",
                    getBubbleKey());
            mAnimateExpansion = animateExpansion;
            mAnimateExpansionEndRunnable = endRunnable;
            return true;
        }
    }

    /**
     * Cancels the pending animation that is waiting on TaskView becoming visible. This also
     * executes the end {@code Runnable}.
     */
    void cancelPendingAnimation() {
        if (mAnimateExpansion != null) {
            BubbleLog.d("BubbleBarExpandedView.cancelPendingAnimation() key=%s", getBubbleKey());
            mAnimateExpansion = null;
            // The end runnable must be executed here, because it is not invoked for non-running
            // animators.
            if (mAnimateExpansionEndRunnable != null) {
                mAnimateExpansionEndRunnable.run();
            }
        }
    }

    private void onTaskViewVisible() {
        if (mAnimateExpansion != null) {
            BubbleLog.d("BubbleBarExpandedView.onTaskViewVisible() key=%s animate expansion",
                    getBubbleKey());
            mAnimateExpansion.run();
            mAnimateExpansion = null;
            // No need to execute the end runnable if the animation is played. It will be run in the
            // animation end callback.
            mAnimateExpansionEndRunnable = null;
        }
    }

    /**
     * Set whether this view is currently being dragged.
     */
    public void setDragging(boolean isDragging) {
        if (isDragging != mIsDragging) {
            mIsDragging = isDragging;

            if (isDragging && mPositioner.isImeVisible()) {
                // Hide the IME when dragging begins
                mManager.hideCurrentInputMethod();
            }
        }
    }

    /** Cleans up the expanded view, should be called when the bubble is no longer active. */
    public void cleanUpExpandedState() {
        mMenuViewController.hideMenu(false /* animated */);
    }

    /**
     * Hides the current modal menu if it is visible
     * @return {@code true} if menu was visible and is hidden
     */
    public boolean hideMenuIfVisible() {
        if (mMenuViewController.isMenuVisible()) {
            mMenuViewController.hideMenu(true /* animated */);
            return true;
        }
        return false;
    }

    /**
     * Hides the IME if it is visible
     * @return {@code true} if IME was visible
     */
    public boolean hideImeIfVisible() {
        if (mPositioner.isImeVisible()) {
            mManager.hideCurrentInputMethod();
            return true;
        }
        return false;
    }

    /** Updates the bubble shown in the expanded view. */
    public void update(Bubble bubble) {
        mBubble = bubble;
        mBubbleTaskViewListener.setBubble(bubble);
        mMenuViewController.updateMenu(bubble);
    }

    /** Handle locale changes.*/
    public void updateLocale() {
        if (mOverflowView != null) {
            mOverflowView.updateLocale();
        }
    }

    /** Handle font size changes.*/
    public void updateFontSize() {
        if (mOverflowView != null) {
            mOverflowView.updateFontSize();
        }
    }

    /** Handle theme changes.*/
    public void updateTheme() {
        if (mOverflowView != null) {
            mOverflowView.updateTheme();
        }
    }

    /** The task id of the activity shown in the task view, if it exists. */
    public int getTaskId() {
        return mBubbleTaskViewListener != null
                ? mBubbleTaskViewListener.getTaskId()
                : INVALID_TASK_ID;
    }

    /** Sets layer bounds supplier used for obscured touchable region of task view */
    void setLayerBoundsSupplier(@Nullable Supplier<Rect> supplier) {
        mLayerBoundsSupplier = supplier;
    }

    /** Sets listener used to notify {@link BubbleBarLayerView} of events  */
    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Sets whether the view is obscured by a modal view, and what that view is. */
    void setObscured(boolean obscured, ObscuredFlag flag) {
        if (mTaskView == null || mLayerBoundsSupplier == null) return;
        if (obscured && mObscuredFlags.add(flag)) {
            // Updates the obscured touchable region for the task surface.
            mTaskView.setObscuredTouchRect(mLayerBoundsSupplier.get());
        } else if (!obscured) {
            mObscuredFlags.remove(flag);
            if (mObscuredFlags.isEmpty()) {
                mTaskView.setObscuredTouchRect(null);
            }
        }
    }

    /** Shows the expanded view for the overflow if it exists. */
    void maybeShowOverflow() {
        if (mOverflowView != null) {
            // post this to the looper so that the view has a chance to be laid out before it can
            // calculate row and column sizes correctly.
            post(() -> mOverflowView.show());
        }
    }

    /** Sets the alpha of the task view. */
    public void setContentVisibility(boolean visible) {
        mIsContentVisible = visible;

        if (mTaskView == null) return;

        if (!mIsAnimating) {
            mTaskView.setAlpha(visible ? 1f : 0f);
        }
    }

    /**
     * Sets the alpha of both this view and the task view.
     */
    public void setTaskViewAlpha(float alpha) {
        if (mTaskView != null) {
            mTaskView.setAlpha(alpha);
        }
        setAlpha(alpha);
    }

    /**
     * Sets whether the surface displaying app content should sit on top. This is useful for
     * ordering surfaces during animations. When content is drawn on top of the app (e.g. bubble
     * being dragged out, the manage menu) this is set to false, otherwise it should be true.
     */
    public void setSurfaceZOrderedOnTop(boolean onTop) {
        if (mTaskView == null) {
            return;
        }
        mTaskView.setZOrderedOnTop(onTop, true /* allowDynamicChange */);
    }

    @VisibleForTesting
    boolean isSurfaceZOrderedOnTop() {
        return mTaskView != null && mTaskView.isZOrderedOnTop();
    }

    @VisibleForTesting
    @Nullable
    BubbleTaskView getBubbleTaskView() {
        return mBubbleTaskView;
    }

    private String getBubbleKey() {
        return mBubble != null ? mBubble.getKey() : "null";
    }

    /**
     * Adds a {@link SurfaceControl.TransactionCommittedListener} to be invoked when the TaskView's
     * next draw.
     * This is needed in case there is any following surface change that needs to wait until the
     * TaskView property applied.
     *
     * NOTE: Do NOT use this if you already have a transaction.
     */
    void executeOnTaskViewDraw(@NonNull @CallbackExecutor Executor executor,
            @NonNull SurfaceControl.TransactionCommittedListener listener) {
        if (mBubbleTaskView == null) {
            throw new IllegalStateException("BubbleTaskView is null");
        }
        final ViewRootImpl viewRoot = mBubbleTaskView.getTaskView().getViewRootImpl();
        if (viewRoot == null) {
            throw new IllegalStateException("ViewRootImpl of Bubble TaskView is null");
        }
        final SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        transaction.addTransactionCommittedListener(executor, listener);
        viewRoot.applyTransactionOnDraw(transaction);
    }

    /**
     * Sets whether the view is animating, in this case we won't change the content visibility
     * until the animation is done.
     */
    public void setAnimating(boolean animating) {
        mIsAnimating = animating;
        // If we're done animating, apply the correct visibility.
        if (!animating) {
            setContentVisibility(mIsContentVisible);
        }
    }

    /**
     * Check whether the view is animating
     */
    public boolean isAnimating() {
        return mIsAnimating;
    }

    /** @return corner radius that should be applied while view is in rest */
    public float getRestingCornerRadius() {
        return mRestingCornerRadius;
    }

    /** @return corner radius that should be applied while view is being dragged */
    public float getDraggedCornerRadius() {
        return mDraggedCornerRadius;
    }

    /** @return current corner radius */
    public float getCornerRadius() {
        return mCurrentCornerRadius;
    }

    /** Update corner radius */
    public void setCornerRadius(float cornerRadius) {
        if (mCurrentCornerRadius != cornerRadius) {
            mCurrentCornerRadius = cornerRadius;
            if (mTaskView != null) {
                mTaskView.setCornerRadius(cornerRadius);
            }
            invalidateOutline();
        }
    }

    /** The y coordinate of the bottom of the expanded view. */
    public int getContentBottomOnScreen() {
        if (mOverflowView != null) {
            mOverflowView.getBoundsOnScreen(mTempBounds);
        }
        if (mTaskView != null) {
            mTaskView.getBoundsOnScreen(mTempBounds);
        }
        return mTempBounds.bottom;
    }

    /** Notifies the expanded view that the IME top changed. */
    public void onImeTopChanged(int imeTop) {
        mImeTop = imeTop;
        mBottomClip = max(getContentBottomOnScreen() - mImeTop, 0);
        onClipUpdate();
    }

    void updateBottomClip() {
        if (mIsClipping) {
            onImeTopChanged(mImeTop);
        }
    }

    void resetBottomClip() {
        mBottomClip = 0;
        onClipUpdate();
    }

    private void onClipUpdate() {
        if (mBottomClip == 0) {
            if (mIsClipping) {
                mIsClipping = false;
                if (mTaskView != null) {
                    mTaskView.setClipBounds(null);
                    mTaskView.setEnableSurfaceClipping(false);
                }
                invalidateOutline();
            }
        } else {
            if (!mIsClipping) {
                mIsClipping = true;
                if (mTaskView != null) {
                    mTaskView.setEnableSurfaceClipping(true);
                }
            }
            invalidateOutline();
            if (mTaskView != null) {
                Rect clipBounds = new Rect(0, 0,
                        mTaskView.getWidth(),
                        mTaskView.getHeight() - mBottomClip);
                mTaskView.setClipBounds(clipBounds);
            }
        }
    }

    /**
     * Return content height: taskView or overflow.
     *
     * <p>Takes into account clippings represented by {@code mBottomClip}
     *
     * @return if bubble is for overflow, return overflow height, otherwise return taskView height
     */
    public int getContentHeight() {
        if (mIsOverflow) {
            return mOverflowView.getHeight() - mBottomClip;
        }
        if (mTaskView != null) {
            return mTaskView.getHeight() - mBottomClip;
        }
        return 0;
    }

    private class HandleViewAccessibilityDelegate extends AccessibilityDelegate {
        @Override
        public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                @NonNull AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK, getResources().getString(
                    R.string.bubble_accessibility_action_expand_menu)));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);
            if (mPositioner.isBubbleBarOnLeft()) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.action_move_bubble_bar_right, getResources().getString(
                        R.string.bubble_accessibility_action_move_bar_right)));
            } else {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.action_move_bubble_bar_left, getResources().getString(
                        R.string.bubble_accessibility_action_move_bar_left)));
            }
        }

        @Override
        public boolean performAccessibilityAction(@NonNull View host, int action,
                @Nullable Bundle args) {
            if (super.performAccessibilityAction(host, action, args)) {
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
                mManager.collapseStack();
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_DISMISS) {
                mManager.dismissBubble(mBubble, Bubbles.DISMISS_USER_GESTURE);
                return true;
            }
            if (action == R.id.action_move_bubble_bar_left) {
                mManager.updateBubbleBarLocation(BubbleBarLocation.LEFT,
                        BubbleBarLocation.UpdateSource.A11Y_ACTION_EXP_VIEW);
                return true;
            }
            if (action == R.id.action_move_bubble_bar_right) {
                mManager.updateBubbleBarLocation(BubbleBarLocation.RIGHT,
                        BubbleBarLocation.UpdateSource.A11Y_ACTION_EXP_VIEW);
                return true;
            }
            return false;
        }
    }

    /**
     * Description of current expanded view state.
     */
    public void dump(@android.annotation.NonNull PrintWriter pw,
            @android.annotation.NonNull String prefix) {
        pw.print(prefix); pw.println("BubbleBarExpandedView:");
        pw.print(prefix); pw.print("  taskId: "); pw.println(getTaskId());
        pw.print(prefix); pw.print("  contentVisibility: "); pw.println(mIsContentVisible);
        pw.print(prefix); pw.print("  isAnimating: "); pw.println(mIsAnimating);
        pw.print(prefix); pw.print("  isDragging: "); pw.println(mIsDragging);
        pw.print(prefix); pw.print("  isClipping: "); pw.println(mIsClipping);
        pw.print(prefix); pw.print("  bottomClip: "); pw.println(mBottomClip);
        pw.print(prefix); pw.print("  imeTop: "); pw.println(mImeTop);
        if (mTaskView != null) {
            pw.print(prefix); pw.print("  tv-alpha: "); pw.println(mTaskView.getAlpha());
            pw.print(prefix); pw.print("  tv-viewVis: "); pw.println(mTaskView.getVisibility());
            pw.print(prefix);
            pw.print("  is task view moving windows: "); pw.println(mTaskView.isMovingWindows());
        }
    }
}
