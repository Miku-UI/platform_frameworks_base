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

package com.android.wm.shell.keyguard;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MIXPATCHER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.transition.AnimationPlan;
import com.android.wm.shell.transition.ITransitionAnimation;
import com.android.wm.shell.transition.ITransitionPlanner;
import com.android.wm.shell.transition.NoAnimation;

/**
 * Distributes Keyguard-related changes to corresponding keyguard animations.
 */
public class KeyguardTransitionPlanner implements ITransitionPlanner {
    private static final String TAG = "KeyguardTransitionPlanner";

    private final ShellExecutor mMainExecutor;

    /** Proxy token for tracking "the keyguard" as part of a transition. */
    public final WindowContainerToken mKeyguardProxy = WindowContainerToken.createProxy("KGProxy");

    public KeyguardTransitionPlanner(@NonNull ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
    }

    @Nullable
    @Override
    public void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction) {
        if (!KeyguardTransitionHandler.handles(info)) {
            return;
        }
        final TransitionInfo.Change kgChange = new TransitionInfo.Change(mKeyguardProxy,
                new SurfaceControl.Builder().setName("KGProxy").build());
        info.addChange(kgChange);
        // TODO: proper keyguard animations
        ITransitionAnimation kgAnim = new NoAnimation(mMainExecutor);
        plan.setAnimation(kgChange.getContainer(), kgAnim);
        ProtoLog.v(WM_SHELL_MIXPATCHER, "Build keyguard proxy in transition #%d",
                info.getDebugId());
    }

    @Override
    @NonNull
    public String getDebugName() {
        return "Keyguard";
    }
}
