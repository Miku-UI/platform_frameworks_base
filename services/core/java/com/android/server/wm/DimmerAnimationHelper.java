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

package com.android.server.wm;

import static android.internal.perfetto.protos.Animationadapter.AlphaAnimationSpecProto.DURATION_MS;
import static android.internal.perfetto.protos.Animationadapter.AlphaAnimationSpecProto.FROM;
import static android.internal.perfetto.protos.Animationadapter.AlphaAnimationSpecProto.TO;
import static android.internal.perfetto.protos.Animationadapter.AnimationSpecProto.ALPHA;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_DIMMER;
import static com.android.server.wm.SurfaceAnimator.ANIMATION_TYPE_DIMMER;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.animation.ArgbEvaluator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.util.proto.ProtoOutputStream;
import android.view.SurfaceControl;

import androidx.annotation.ColorLong;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.protolog.ProtoLog;

import java.io.PrintWriter;

/**
 * Contains the information relative to the changes to apply to the dim layer
 */
public class DimmerAnimationHelper {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "DimmerAnimationHelper" : TAG_WM;
    private static final int DEFAULT_DIM_ANIM_DURATION_MS = 200;

    /**
     * A statically-allocated array to avoid temporary allocations.
     */
    @GuardedBy("WindowManagerService.mGlobalLock")
    private static final float[] sTmpFloatColor = new float[3];

    /**
     * Contains the requested changes
     */
    static class Change {
        private float mAlpha = -1f;
        private int mBlurRadius = -1;
        @ColorLong
        private long mColor = Color.pack(Color.BLACK);
        @Nullable
        private WindowState mDimmingContainer = null;
        @Nullable
        private WindowContainer<?> mGeometryParent = null;
        private static final float EPSILON = 0.0001f;

        Change() {}

        Change(@NonNull Change other) {
            copyFrom(other);
        }

        void copyFrom(@NonNull Change other) {
            mAlpha = other.mAlpha;
            mBlurRadius = other.mBlurRadius;
            mColor = other.mColor;
            mDimmingContainer = other.mDimmingContainer;
            mGeometryParent = other.mGeometryParent;
        }

        // Same alpha and blur
        boolean hasSameVisualProperties(@NonNull Change other) {
            return Math.abs(mAlpha - other.mAlpha) < EPSILON && mBlurRadius == other.mBlurRadius
                    && mColor == other.mColor;
        }

        boolean hasSameDimmingContainer(@NonNull Change other) {
            return mDimmingContainer != null && mDimmingContainer == other.mDimmingContainer;
        }

        void inheritPropertiesFromAnimation(@Nullable AnimationSpec anim) {
            if (anim != null) {
                mAlpha = anim.mCurrentAlpha;
                mBlurRadius = anim.mCurrentBlur;
                mColor = anim.mCurrentColor;
            }
        }

        @Override
        public String toString() {
            return "Dim state: alpha=" + mAlpha + ", blur=" + mBlurRadius
                    + ", color=" + Long.toHexString(mColor) + ", container=" + mDimmingContainer
                    + ", geometryParent " + mGeometryParent;
        }
    }

    @NonNull
    private final Change mCurrentProperties = new Change();
    @NonNull
    private final Change mRequestedProperties = new Change();
    @Nullable
    private AnimationSpec mAlphaAnimationSpec;

    @NonNull
    private final SurfaceAnimationRunner mSurfaceAnimationRunner;
    @Nullable
    private LocalAnimationAdapter mLocalAnimationAdapter;

    DimmerAnimationHelper(@NonNull SurfaceAnimationRunner surfaceAnimationRunner) {
        mSurfaceAnimationRunner = surfaceAnimationRunner;
    }

    void setExitParameters() {
        setRequestedRelativeParent(mRequestedProperties.mDimmingContainer);
        setRequestedAppearance(0f /* alpha */, 0 /* blur */, Color.pack(Color.BLACK) /* color */);
    }

    // Sets a requested change without applying it immediately
    void setRequestedRelativeParent(@NonNull WindowState relativeParent) {
        mRequestedProperties.mDimmingContainer = relativeParent;
    }

    // Sets the requested layer to reparent the dim to without applying it immediately
    void setRequestedGeometryParent(@Nullable WindowContainer<?> geometryParent) {
        if (geometryParent != null) {
            mRequestedProperties.mGeometryParent = geometryParent;
        }
    }

    // Sets a requested change without applying it immediately
    void setRequestedAppearance(float alpha, int blurRadius, @ColorLong long color) {
        mRequestedProperties.mAlpha = alpha;
        mRequestedProperties.mBlurRadius = blurRadius;
        mRequestedProperties.mColor = color;
    }

    /**
     * Commit the last changes we received. Called after
     * {@link Change#setExitParameters()},
     * {@link Change#setRequestedRelativeParent(WindowState)}, or
     * {@link Change#setRequestedGeometryParent(WindowContainer)}, or
     * {@link Change#setRequestedAppearance(float, int, long)}
     */
    void applyChanges(@NonNull SurfaceControl.Transaction t, @NonNull Dimmer.DimState dim) {
        final Change startProperties = new Change(mCurrentProperties);
        mCurrentProperties.copyFrom(mRequestedProperties);

        if (mRequestedProperties.mDimmingContainer == null) {
            Log.e(TAG, this + " does not have a dimming container. Have you forgotten to "
                    + "call adjustRelativeLayer?");
            return;
        }
        if (mRequestedProperties.mDimmingContainer.getSurfaceControl() == null) {
            Log.w(TAG, "container " + mRequestedProperties.mDimmingContainer
                    + "does not have a surface");
            dim.remove(t);
            return;
        }
        if (!dim.mDimSurface.isValid()) {
            Log.e(TAG, "Dimming surface " + dim.mDimSurface + " has already been released!"
                    + " Can not apply changes.");
            return;
        }

        dim.ensureVisible(t);
        reparent(dim,
                startProperties.mGeometryParent != mRequestedProperties.mGeometryParent
                        ? mRequestedProperties.mGeometryParent.getSurfaceControl() : null,
                mRequestedProperties.mDimmingContainer != startProperties.mDimmingContainer
                        ? mRequestedProperties.mDimmingContainer.getSurfaceControl() : null, t);
        setBounds(dim, mCurrentProperties.mDimmingContainer, t);

        if (!startProperties.hasSameVisualProperties(mRequestedProperties)) {
            EventLogTags.writeWmDimCancelAnim(dim.mDimSurface.getLayerId(), "new target values");
            stopCurrentAnimation(dim.mDimSurface);

            if (dim.mSkipAnimation
                    // If the container doesn't change but requests a dim change, then it is
                    // directly providing us the animated values
                    || (startProperties.hasSameDimmingContainer(mRequestedProperties)
                    && dim.isDimming())) {
                ProtoLog.d(WM_DEBUG_DIMMER,
                        "%s skipping animation and directly setting alpha=%f, blur=%d, color=%x",
                        dim, mRequestedProperties.mAlpha,
                        mRequestedProperties.mBlurRadius, mRequestedProperties.mColor);
                setCurrentState(dim, t);
                dim.mSkipAnimation = false;
            } else {
                startAnimation(t, dim, startProperties, mRequestedProperties);
            }
        } else if (!dim.isDimming()) {
            // We are not dimming, so we tried the exit animation but the alpha is already 0,
            // therefore, let's just remove this surface
            dim.remove(t);
        }
    }

    private void startAnimation(
            @NonNull SurfaceControl.Transaction t, @NonNull Dimmer.DimState dim,
            @NonNull Change from, @NonNull Change to) {
        ProtoLog.v(WM_DEBUG_DIMMER, "Starting animation on %s", dim);
        mAlphaAnimationSpec = getRequestedAnimationSpec(from, to);
        mLocalAnimationAdapter = new LocalAnimationAdapter(mAlphaAnimationSpec,
                mSurfaceAnimationRunner);

        float targetAlpha = to.mAlpha;
        long targetColor = to.mColor;
        EventLogTags.writeWmDimAnimate(dim.mDimSurface.getLayerId(), targetAlpha, to.mBlurRadius);

        mLocalAnimationAdapter.startAnimation(dim.mDimSurface, t,
                ANIMATION_TYPE_DIMMER, /* finishCallback */ (type, animator) -> {
                    synchronized (dim.mHostContainer.mWmService.mGlobalLock) {
                        EventLogTags.writeWmDimFinishAnim(dim.mDimSurface.getLayerId());
                        SurfaceControl.Transaction finishTransaction =
                                dim.mHostContainer.getSyncTransaction();
                        mCurrentProperties.mColor = targetColor;
                        setCurrentState(dim, finishTransaction);
                        if (targetAlpha == 0f && !dim.isDimming()) {
                            dim.remove(finishTransaction);
                            // Ensure the finishTransaction is applied if pending
                            dim.mHostContainer.scheduleAnimation();
                        }
                        mLocalAnimationAdapter = null;
                        mAlphaAnimationSpec = null;
                    }
                });
    }

    void stopCurrentAnimation(@NonNull SurfaceControl surface) {
        // (If animating) save the current animation progress and cancel the animation
        mCurrentProperties.inheritPropertiesFromAnimation(mAlphaAnimationSpec);
        mSurfaceAnimationRunner.onAnimationCancelled(surface);
        mLocalAnimationAdapter = null;
        mAlphaAnimationSpec = null;
    }

    @NonNull
    private static AnimationSpec getRequestedAnimationSpec(@NonNull Change from,
            @NonNull Change to) {
        final float startAlpha = Math.max(from.mAlpha, 0f);
        final int startBlur = Math.max(from.mBlurRadius, 0);
        long duration = (long) (getDimDuration(to.mDimmingContainer)
                * Math.abs(to.mAlpha - startAlpha));

        final AnimationSpec spec =  new AnimationSpec(
                new AnimationSpec.AnimationExtremes<>(startAlpha, to.mAlpha),
                new AnimationSpec.AnimationExtremes<>(startBlur, to.mBlurRadius),
                new AnimationSpec.AnimationExtremes<>(from.mColor, to.mColor),
                duration
        );
        ProtoLog.v(WM_DEBUG_DIMMER, "Dim animation requested: %s", spec);
        return spec;
    }

    /**
     * Change the geometry and relative parent of this dim layer
     */
    void reparent(@NonNull Dimmer.DimState dim,
            @Nullable SurfaceControl newGeometryParent,
            @Nullable SurfaceControl newRelativeParent,
            @NonNull SurfaceControl.Transaction t) {
        final SurfaceControl dimLayer = dim.mDimSurface;
        try {
            if (newGeometryParent != null) {
                t.reparent(dimLayer, newGeometryParent);
            }
            if (newRelativeParent != null) {
                t.setRelativeLayer(dimLayer, newRelativeParent, -1);
            }
        } catch (NullPointerException e) {
            Log.w(TAG, "Tried to change parent of dim " + dimLayer + " after remove", e);
        }
    }

    static void setBounds(@NonNull Dimmer.DimState dim, @NonNull WindowState relativeParent,
            @NonNull SurfaceControl.Transaction t) {
        final Rect rotatedBounds = relativeParent.mToken.getFixedRotationTransformDisplayBounds();
        if (rotatedBounds != null) {
            dim.mDimBounds.set(rotatedBounds);
            // Fixed rotation token fills the display. Unset the crop so that the dim can cover
            // the entire display when the orientation changes.
            t.setCrop(dim.mDimSurface, null);
        } else {
            final Rect hostBounds = dim.mHostContainer.getBounds();
            final TaskFragment taskFragment = relativeParent.getTaskFragment();
            if (taskFragment != null) {
                taskFragment.getDimBounds(dim.mDimBounds);
            } else {
                dim.mDimBounds.set(hostBounds);
            }
            final Rect dimCrop = relativeParent.mTmpRect;
            dimCrop.set(dim.mDimBounds);
            dimCrop.offset(-hostBounds.left, -hostBounds.top);
            t.setCrop(dim.mDimSurface, dimCrop);
        }
    }

    void setCurrentState(@NonNull Dimmer.DimState dim, @NonNull SurfaceControl.Transaction t) {
        final SurfaceControl sc = dim.mDimSurface;
        try {
            t.setAlpha(sc, mCurrentProperties.mAlpha);
            t.setBackgroundBlurRadius(sc, mCurrentProperties.mBlurRadius);
            if (com.android.window.flags.Flags.supportCustomDimColor()) {
                extractFloatColor(mCurrentProperties.mColor, sTmpFloatColor);
                t.setColor(sc, sTmpFloatColor);
            }
        } catch (NullPointerException e) {
            Log.w(TAG , "Tried to change look of dim " + sc + " after remove",  e);
        }
    }

    /**
     * Converts an integer color into its float RGB components.
     *
     * @param color The source ColorLong.
     * @param outFloatColor The output float array (must be at of size 3) to be populated.
     * @throws IllegalArgumentException if outFloatColor has a length not equal to 3.
     */
    private static void extractFloatColor(@ColorLong long color, @Size(3) float[] outFloatColor) {
        outFloatColor[0] = android.graphics.Color.red(color);
        outFloatColor[1] = android.graphics.Color.green(color);
        outFloatColor[2] = android.graphics.Color.blue(color);
    }

    private static long getDimDuration(@NonNull WindowContainer<?> container) {
        // Use the same duration as the animation on the WindowContainer
        if (container.mSurfaceAnimator != null) {
            AnimationAdapter animationAdapter = container.mSurfaceAnimator.getAnimation();
            final float durationScale =
                    container.mDisplayContent.getTransitionAnimationScaleLocked();
            return animationAdapter == null ? (long) (DEFAULT_DIM_ANIM_DURATION_MS * durationScale)
                    : animationAdapter.getDurationHint();
        }
        return 0;
    }

    /**
     * Collects the animation specifics
     */
    static class AnimationSpec implements LocalAnimationAdapter.AnimationSpec {
        private static final String TAG = TAG_WITH_CLASS_NAME ? "DimmerAnimationSpec" : TAG_WM;

        static class AnimationExtremes<T> {
            final T mStartValue;
            final T mFinishValue;

            AnimationExtremes(T fromValue, T toValue) {
                mStartValue = fromValue;
                mFinishValue = toValue;
            }

            @Override
            public String toString() {
                return "[" + mStartValue + "->" + mFinishValue + "]";
            }
        }

        private final long mDuration;
        @NonNull
        private final AnimationSpec.AnimationExtremes<Float> mAlpha;
        @NonNull
        private final AnimationSpec.AnimationExtremes<Integer> mBlur;
        @NonNull
        private final AnimationSpec.AnimationExtremes<Long> mColor;

        float mCurrentAlpha = 0;
        int mCurrentBlur = 0;
        @ColorLong
        long mCurrentColor = Color.pack(Color.BLACK);
        boolean mStarted = false;

        AnimationSpec(@NonNull AnimationSpec.AnimationExtremes<Float> alpha,
                @NonNull AnimationSpec.AnimationExtremes<Integer> blur,
                @NonNull AnimationExtremes<Long> color, long duration) {
            mAlpha = alpha;
            mBlur = blur;
            mColor = color;
            mDuration = duration;
        }

        @Override
        public long getDuration() {
            return mDuration;
        }

        @Override
        public void apply(@NonNull SurfaceControl.Transaction t, @NonNull SurfaceControl sc,
                long currentPlayTime) {
            if (!mStarted) {
                // The first frame would end up in the sync transaction, and since this could be
                // applied after the animation transaction, we avoid putting visible changes here.
                // The initial state of the animation matches the current state of the dim anyway.
                mStarted = true;
                return;
            }
            final float fraction = getFraction(currentPlayTime);
            mCurrentAlpha =
                    fraction * (mAlpha.mFinishValue - mAlpha.mStartValue) + mAlpha.mStartValue;
            mCurrentBlur =
                    (int) (fraction * (mBlur.mFinishValue - mBlur.mStartValue) + mBlur.mStartValue);
            mCurrentColor = ArgbEvaluator.evaluateColorLong(fraction, mColor.mStartValue,
                    mColor.mFinishValue);
            if (sc.isValid()) {
                t.setAlpha(sc, mCurrentAlpha);
                t.setBackgroundBlurRadius(sc, mCurrentBlur);
                if (com.android.window.flags.Flags.supportCustomDimColor()) {
                    extractFloatColor(mCurrentColor, sTmpFloatColor);
                    t.setColor(sc, sTmpFloatColor);
                }
            } else {
                Log.w(TAG, "Dimmer#AnimationSpec tried to access " + sc + " after release");
            }
        }

        @Override
        public String toString() {
            return "Animation spec: alpha=" + mAlpha + ", blur=" + mBlur + ", color=" + mColor;
        }

        @Override
        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.print(prefix); pw.print("from_alpha="); pw.print(mAlpha.mStartValue);
            pw.print(" to_alpha="); pw.print(mAlpha.mFinishValue);
            pw.print(prefix); pw.print("from_blur="); pw.print(mBlur.mStartValue);
            pw.print(" to_blur="); pw.print(mBlur.mFinishValue);
            pw.print(prefix); pw.print("from_color=");
            pw.print(Long.toHexString(mColor.mStartValue));
            pw.print(" to_color="); pw.print(Long.toHexString(mColor.mFinishValue));
            pw.print(" duration="); pw.println(mDuration);
        }

        @Override
        public void dumpDebugInner(@NonNull ProtoOutputStream proto) {
            final long token = proto.start(ALPHA);
            proto.write(FROM, mAlpha.mStartValue);
            proto.write(TO, mAlpha.mFinishValue);
            proto.write(DURATION_MS, mDuration);
            proto.end(token);
        }
    }
}
