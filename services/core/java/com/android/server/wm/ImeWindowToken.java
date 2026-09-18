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

import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.HASH_CODE;
import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.TITLE;
import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.USER_ID;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_IME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.os.Bundle;
import android.os.IBinder;
import android.util.proto.ProtoOutputStream;
import android.view.SurfaceControl;

import com.android.internal.protolog.ProtoLog;

import java.io.PrintWriter;

/**
 * Container for the set of IME windows. This differs from the base class {@link WindowToken} in
 * that its visibility can be explicitly set, overriding the children's visibility (instead of
 * its visibility being determined by the children's visibility).
 */
final class ImeWindowToken extends WindowToken {

    /**
     * The ID of the user whose windows can be added to this token.
     */
    @UserIdInt
    private final int mTargetUserId;

    ImeWindowToken(@NonNull WindowManagerService service, @NonNull IBinder token,
            @UserIdInt int targetUserId, @Nullable Bundle options) {
        super(service, token, TYPE_INPUT_METHOD, true /* persistsOnEmpty */,
                true /* ownerCanManageAppTokens */, false /* roundedCornerOverlay */,
                false /* fromClientToken */, options);
        mTargetUserId = targetUserId;
    }

    @Override
    void setClientVisible(boolean clientVisible) {
        if (clientVisible == isClientVisible()) {
            return;
        }
        ProtoLog.d(WM_DEBUG_IME, "ImeWindowToken %s setClientVisible=%b", token, clientVisible);
        super.setClientVisible(clientVisible);
        // Also sets visibleRequested as this influences the child window's visibleRequested.
        setVisibleRequested(clientVisible);
        mWmService.mAnimator.addSurfaceVisibilityUpdate(this);
    }

    @Override
    void removeImmediately() {
        super.removeImmediately();
        final var imeContainer = mDisplayContent.getImeContainer();
        if (imeContainer.getImeWindowToken() == this) {
            imeContainer.setImeWindowToken(null /* token */);
        }
    }

    @Override
    boolean isVisible() {
        // Requires client visibility in addition to children visibility.
        return isClientVisible() && super.isVisible();
    }

    @Override
    boolean shouldCheckTokenClientVisible() {
        return true;
    }

    @Override
    boolean shouldCheckTokenVisibleRequested() {
        return true;
    }

    @Override
    protected boolean onChildVisibleRequestedChanged(@Nullable WindowContainer child) {
        // Manages visibleRequested directly (it's not determined by children).
        return false;
    }

    @Override
    void updateSurfaceVisibility(@NonNull SurfaceControl.Transaction t) {
        // Can be explicitly hidden, it should also hide the surface.
        t.setVisibility(mSurfaceControl, isClientVisible());
    }

    @Override
    @NonNull
    ImeWindowToken asImeToken() {
        return this;
    }

    @Override
    public String toString() {
        if (stringName == null) {
            stringName = "ImeWindowToken{" + Integer.toHexString(System.identityHashCode(this))
                    + " u" + mTargetUserId
                    + " " + token
                    + "}";
        }
        return stringName;
    }

    @Override
    void dump(@NonNull PrintWriter pw, @NonNull String prefix, boolean dumpAll) {
        super.dump(pw, prefix, dumpAll);
        pw.print(prefix); pw.print("visible="); pw.print(isVisible());
        pw.print(" visibleRequested="); pw.print(isVisibleRequested());
        pw.print(" clientVisible="); pw.println(isClientVisible());
        pw.print(" targetUserId="); pw.println(mTargetUserId);
    }

    @Override
    public void writeIdentifierToProto(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        proto.write(HASH_CODE, System.identityHashCode(this));
        proto.write(USER_ID, mTargetUserId);
        proto.write(TITLE, "ImeWindowToken");
        proto.end(token);
    }
}
