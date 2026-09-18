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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSET;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.window.DisplayAreaOrganizer.FEATURE_IME;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_IME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.ActivityInfo;
import android.util.ArraySet;
import android.util.SparseArray;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.WindowManager.LayoutParams;
import android.window.IDisplayAreaOrganizer;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.ToBooleanFunction;

import java.io.PrintWriter;

/**
 * Container for IME windows, used to keep track of all IME windows and move them in-sync if/when
 * needed.
 *
 * <p>Windows of {@link LayoutParams#TYPE_INPUT_METHOD} and
 * {@link LayoutParams#TYPE_INPUT_METHOD_DIALOG} are placed in this container (see
 * {@link DisplayContent#findAreaForWindowType}).
 *
 * <p>This container is placed in the window hierarchy by the {@link DisplayAreaPolicy}. In general,
 * its position in the window hierarchy does not change (see
 * {@link DisplayContent#setImeLayeringTarget}). It is placed above all apps, as required for insets
 * computation (see {@link InsetsStateController#updateAboveInsetsState}). However, it is visually
 * positioned on top of the {@link DisplayContent#mImeLayeringTarget} (if any) by reparenting its
 * surface (see {@link DisplayContent#updateImeParent}).
 *
 * <p>To maintain a visual traversal order, this is initially skipped during window hierarchy
 * traversal if there is an {@link DisplayContent#mImeLayeringTarget} (see
 * {@link #skipImeContainerDuringTraversal}), and it is re-visited as soon as the traversal reaches
 * the {@link DisplayContent#mImeLayeringTarget}, which will always be right below it. (see
 * {@link WindowState#applyInOrderWithImeContainer}).
 *
 * <p>Layers assignment is ignored except if {@link #setNeedsLayer} has been called before (and no
 * layer has been assigned since), to facilitate assigning the layer from the IME Layering Target,
 * or parent window if there is no target (see {@link DisplayContent#assignRelativeLayerForIme}).
 */
final class ImeContainer extends DisplayArea.Tokens {

    /** Whether (relative) layer assignment is needed, or otherwise ignored. */
    private boolean mNeedsLayer = false;

    /**
     * The current Input Method Window Token for this container.
     */
    @Nullable
    private ImeWindowToken mImeWindowToken;

    ImeContainer(@NonNull WindowManagerService wms) {
        super(wms, Type.ABOVE_TASKS, "ImeContainer", FEATURE_IME);
    }

    /** Sets that the ImeContainer needs to {@link #assignLayer} or {@link #assignRelativeLayer}. */
    void setNeedsLayer() {
        mNeedsLayer = true;
    }

    /**
     * For all windows at or below this container call the callback, ignoring the result of
     * {@link #skipImeContainerDuringTraversal}.
     *
     * @param   callback Calls the {@link ToBooleanFunction#apply} method for each window found and
     *                   stops the search if {@link ToBooleanFunction#apply} returns true.
     * @param   traverseTopToBottom If true traverses the hierarchy from top-to-bottom in terms of
     *                              z-order, else from bottom-to-top.
     * @return  True if the search ended before we reached the end of the hierarchy due to
     *          {@link ToBooleanFunction#apply} returning true.
     */
    boolean forAllWindowForce(@NonNull ToBooleanFunction<WindowState> callback,
            boolean traverseTopToBottom) {
        return super.forAllWindows(callback, traverseTopToBottom);
    }

    /**
     * Sets the given ImeWindowToken as the current one for this container, sets it as the only
     * visible token (and sets the others as not visible), and updates the current IME window.
     *
     * @param token the token to set, or {@code null} to remove it.
     */
    void setImeWindowToken(@Nullable ImeWindowToken token) {
        if (!android.view.inputmethod.Flags.warmWorkProfileIme()) {
            return;
        }
        if (mImeWindowToken == token) {
            return;
        }
        ProtoLog.i(WM_DEBUG_IME, "setImeWindowToken %s", token);
        mImeWindowToken = token;
        for (int i = 0; i < mChildren.size(); i++) {
            final ImeWindowToken child = mChildren.get(i).asImeToken();
            if (child != null) {
                // This calls into ViewRootImpl#handleAppVisibility in the client, which eventually
                // determines whether a child window of this token can be visible.
                child.setClientVisible(child == token);
            }
        }
        if (token == null || token.isEmpty()) {
            mDisplayContent.setImeWindow(null /* win */);
        } else if (mDisplayContent.getImeWindow() == null) {
            // Only set when there is no IME window. This is normally reset before switching
            // users, and set again when starting the next user.
            token.forAllWindows(w -> {
                if (w.getWindowType() == TYPE_INPUT_METHOD
                        // IME window is always touchable.
                        // Ignore non-touchable windows e.g. Stylus InkWindow.java.
                        && (w.mAttrs.flags & FLAG_NOT_TOUCHABLE) == 0) {
                    // Set the first IME window found.
                    mDisplayContent.setImeWindow(w);
                    return true;
                }
                return false;
            }, true /* traverseTopToBottom */);
        }
    }

    /** Gets the current Input Method Window token for this container. */
    @Nullable
    ImeWindowToken getImeWindowToken() {
        return mImeWindowToken;
    }

    @Override
    @ActivityInfo.ScreenOrientation
    int getOrientation(@ActivityInfo.ScreenOrientation int candidate) {
        // IME does not participate in orientation.
        return shouldIgnoreOrientationRequest(candidate) ? SCREEN_ORIENTATION_UNSET : candidate;
    }

    @Override
    void updateAboveInsetsState(@NonNull InsetsState aboveInsetsState,
            @Nullable SparseArray<InsetsSource> localInsetsSourcesFromParent,
            @NonNull ArraySet<WindowState> insetsChangedWindows) {
        if (skipImeContainerDuringTraversal(mDisplayContent)) {
            return;
        }
        super.updateAboveInsetsState(aboveInsetsState, localInsetsSourcesFromParent,
                insetsChangedWindows);
    }

    @Override
    boolean forAllWindows(@NonNull ToBooleanFunction<WindowState> callback,
            boolean traverseTopToBottom) {
        if (skipImeContainerDuringTraversal(mDisplayContent)) {
            return false;
        }
        return super.forAllWindows(callback, traverseTopToBottom);
    }

    @Override
    void assignLayer(@NonNull SurfaceControl.Transaction t, int layer) {
        if (!mNeedsLayer || mTransitionController.mBuildingFinishLayers) {
            return;
        }
        super.assignLayer(t, layer);
        mNeedsLayer = false;
    }

    @Override
    void assignRelativeLayer(@NonNull SurfaceControl.Transaction t,
            @NonNull SurfaceControl relativeTo, int layer, boolean forceUpdate) {
        if (!mNeedsLayer || mTransitionController.mBuildingFinishLayers) {
            return;
        }
        super.assignRelativeLayer(t, relativeTo, layer, forceUpdate);
        mNeedsLayer = false;
    }

    @Override
    void setOrganizer(@Nullable IDisplayAreaOrganizer organizer, boolean skipDisplayAreaAppeared) {
        super.setOrganizer(organizer, skipDisplayAreaAppeared);
        mDisplayContent.updateImeParent();

        // If the ImeContainer was previously unorganized then the framework might have reparented
        // its surface to an Activity's surface so we need to reparent it back to the parent
        // window's surface.
        if (organizer != null) {
            final SurfaceControl parentWindowSurface = getParentSurfaceControl();
            if (mSurfaceControl != null && parentWindowSurface != null) {
                ProtoLog.i(WM_DEBUG_IME, "ImeContainer just became organized. Reparenting to the"
                        + " parent window's surface: %s", parentWindowSurface);
                getPendingTransaction().reparent(mSurfaceControl, parentWindowSurface);
            } else {
                ProtoLog.e(WM_DEBUG_IME, "ImeContainer just became organized but it doesn't"
                                + " have a surface or the parent doesn't have a surface"
                                + " surface=%s parentWindowSurface=%s",
                        mSurfaceControl, parentWindowSurface);
            }
        }
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "ImeContainer");
        prefix += "  ";
        pw.println(prefix + "mNeedsLayer=" +  mNeedsLayer);
        pw.println(prefix + "mImeWindowToken=" + mImeWindowToken);
    }

    /**
     * Checks whether the ImeContainer should be skipped during traversal, so that it is traversed
     * just when the traversal reaches the {@link DisplayContent#mImeLayeringTarget}. Note that this
     * method should align with {@link WindowState#applyForImeContainerIfNeeded}.
     *
     * @param dc the current display content.
     * @return whether we should skip ImeContainer during traversal.
     */
    private static boolean skipImeContainerDuringTraversal(@NonNull DisplayContent dc) {
        return dc.getImeLayeringTarget() != null;
    }
}
