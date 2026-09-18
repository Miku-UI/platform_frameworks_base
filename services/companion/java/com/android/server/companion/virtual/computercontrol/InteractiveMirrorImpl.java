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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.graphics.Insets;
import android.gui.DropInputMode;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.SurfaceControl;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.IResultReceiver;
import com.android.server.input.InputManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.util.function.Supplier;

/**
 * A representation of an interactive mirror of a Computer Control Session's Virtual Display.
 * <p>
 * The client application will render the {@code mMirrorSurface} from their UI.
 * <p>
 * NOTE: Since the application has access to the mirror {@link SurfaceControl}, this means they can
 * view all content in that mirror surface.
 */
final class InteractiveMirrorImpl extends IInteractiveMirror.Stub {
    private static final String TAG = InteractiveMirrorImpl.class.getSimpleName();

    /**
     * Callbacks for events on an {@link InteractiveMirrorImpl}.
     */
    interface InteractiveMirrorImplCallback {
        /**
         * Called when the interactive state of the mirror changes.
         */
        void onInteractiveChanged(boolean isInteractive);

        /**
         * Called when the mirror requests insets to be propagated to the display.
         */
        void onRequestedInsetsChanged();

        /**
         * Called when the mirror is closed.
         */
        void onClose(InteractiveMirrorImpl mirror);
    }

    private final DisplayInfo mDisplayInfo;
    // The mirror of the VirtualDisplay, used to control sensitive parameters of the surface.
    // NOTE: This must NOT be sent to the client app, and must remain in system_server.
    private final WindowManagerInternal.DisplayMirror mMirror;
    // The parent leash of the mirror that can be safely sent to the client app to be embedded
    // in its MirrorView.
    private final SurfaceControl mMirrorLeash;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private final InputManagerInternal mInputManagerInternal;
    private final InteractiveMirrorImplCallback mCallback;
    private final IResultReceiver mA11yEmbeddedConnectionReceiver;

    @GuardedBy("this")
    private boolean mIsInteractivityAllowed = false;
    @GuardedBy("this")
    private boolean mIsInteractiveRequested = InteractiveMirror.DEFAULT_INTERACTIVE;
    @GuardedBy("this")
    @Nullable
    private Boolean mIsInteractive = null;
    @GuardedBy("this")
    @Nullable
    private Insets mRequestedInsets = null;
    @GuardedBy("this")
    private float mScale = 1.f;

    InteractiveMirrorImpl(WindowManagerInternal.DisplayMirror mirror,
            IResultReceiver a11yEmbeddedConnectionReceiver,
            Supplier<SurfaceControl.Transaction> transactionSupplier, DisplayInfo displayInfo,
            InputManagerInternal inputManagerInternal,
            boolean isInteractivityAllowed,
            InteractiveMirrorImplCallback callback) {
        mMirror = mirror;
        mTransactionSupplier = transactionSupplier;
        mDisplayInfo = displayInfo;
        mInputManagerInternal = inputManagerInternal;
        mMirrorLeash = new SurfaceControl.Builder()
                .setName("InteractiveMirrorImpl#mMirrorLeash$" + mMirror.hashCode())
                .setContainerLayer()
                .setHidden(true)
                .build();
        mCallback = callback;
        mA11yEmbeddedConnectionReceiver = a11yEmbeddedConnectionReceiver;

        Slog.v(TAG, "Creating interactive mirror with SurfaceControl: " + mMirrorLeash);

        try (var transaction = mTransactionSupplier.get()) {
            transaction
                    .reparent(mMirror.getMirrorSurfaceControl(), mMirrorLeash)
                    .show(mMirror.getMirrorSurfaceControl());
            updateInteractivity(isInteractivityAllowed, transaction);
            transaction.apply();
        }
    }

    void monitor() {
        synchronized (this) { /* no-op */ }
    }

    /** Return the mirror leash that can be safely sent to the client app. */
    SurfaceControl getMirrorLeash() {
        return mMirrorLeash;
    }

    /** Updates whether this mirror is allowed to be interactive. */
    void updateInteractivity(boolean isInteractiveAllowed, SurfaceControl.Transaction transaction) {
        synchronized (this) {
            mIsInteractivityAllowed = isInteractiveAllowed;
            applyInteractivityLocked(transaction);
        }
    }

    boolean isInteractive() {
        synchronized (this) {
            return Boolean.TRUE.equals(mIsInteractive);
        }
    }

    IResultReceiver getA11yEmbeddedConnectionReceiver() {
        return mA11yEmbeddedConnectionReceiver;
    }

    @RequiresNoPermission
    @Override
    public void setInteractive(boolean interactive) {
        synchronized (this) {
            mIsInteractiveRequested = interactive;
            try (var transaction = mTransactionSupplier.get()) {
                applyInteractivityLocked(transaction);
                transaction.apply();
            }
        }
    }

    @GuardedBy("this")
    private void applyInteractivityLocked(SurfaceControl.Transaction transaction) {
        final boolean interactive = mIsInteractivityAllowed && mIsInteractiveRequested;
        if (mIsInteractive != null && interactive == mIsInteractive) {
            return;
        }
        mIsInteractive = interactive;
        Slog.v(TAG, "Updating user interactivity: interactions are "
                + (interactive ? "" : "not ") + "allowed");
        transaction.setDropInputMode(mMirror.getMirrorSurfaceControl(),
                interactive ? DropInputMode.NONE : DropInputMode.ALL);
        mCallback.onInteractiveChanged(interactive);
    }

    @RequiresNoPermission
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        final float sx = ((float) mDisplayInfo.logicalWidth) / width;
        final float sy = ((float) mDisplayInfo.logicalHeight) / height;
        synchronized (this) {
            mScale = Math.max(sx, sy);
            // The overall scale is the max due to letterboxing / pillarboxing
            // TODO(b/448309877): Figure out if we need a different scale mechanism here.
            mInputManagerInternal.setAccessibilityPointerIconScaleFactor(mDisplayInfo.displayId,
                    mScale);
        }
    }

    @RequiresNoPermission
    @Override
    public void updateInsets(Insets insets) {
        synchronized (this) {
            mRequestedInsets = insets;
        }
        mCallback.onRequestedInsetsChanged();
    }

    @NonNull
    Insets getRequestedInsets() {
        synchronized (this) {
            return mRequestedInsets != null ? Insets.scale(mRequestedInsets, mScale) : Insets.NONE;
        }
    }

    @RequiresNoPermission
    @Override
    public void close() {
        mCallback.onClose(this);
    }

    /**
     * Closes this InteractiveMirrorImpl. The owner is responsible for ensuring that this method is
     * only called once.
     */
    void closeWithTransaction(SurfaceControl.Transaction transaction) {
        Slog.v(TAG, "Closing interactive mirror with SurfaceControl: " + mMirrorLeash);
        transaction.remove(mMirrorLeash);
        try {
            mMirror.close();
        } catch (Exception e) {
            Slog.e(TAG, "Failed to close DisplayMirror", e);
        }
    }
}
