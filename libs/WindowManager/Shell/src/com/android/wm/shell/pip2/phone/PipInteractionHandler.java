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

package com.android.wm.shell.pip2.phone;

import static com.android.internal.jank.Cuj.CUJ_PIP_TRANSITION;

import android.annotation.IntDef;
import android.content.Context;
import android.os.Handler;
import android.view.SurfaceControl;

import com.android.internal.jank.InteractionJankMonitor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Helps track PIP CUJ interactions
 */
public class PipInteractionHandler {
    @IntDef(prefix = {"INTERACTION_"}, value = {
            INTERACTION_EXIT_PIP,
            INTERACTION_EXIT_PIP_TO_SPLIT,
            INTERACTION_ENTER_PIP,
            INTERACTION_REMOVE_PIP,
            INTERACTION_BOUNDS_CHANGE_TRANSITION,
            INTERACTION_PINCHING_PIP,
            INTERACTION_DRAG_PIP,
            INTERACTION_FLING_TO_SNAP_PIP
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface Interaction {}

    /** Covers exiting PiP via expand interaction. */
    public static final int INTERACTION_EXIT_PIP = 0;

    /** Covers exiting PiP via expand into a splitscreen interaction. */
    public static final int INTERACTION_EXIT_PIP_TO_SPLIT = 1;

    /**
     * Covers entering PiP interaction; covers the bounds-type entry animation, cross-fade entry,
     * and jump-cut PiP transition at the end of swipe-pip-to-home interaction.
     *
     * Note: the Launcher-side animation of swipe-pip-to-home
     * is covered by {@code CUJ_LAUNCHER_APP_CLOSE_TO_PIP}.
     */
    public static final int INTERACTION_ENTER_PIP = 2;

    /** Covers removing PiP, for example via menu and drag-into-dismiss target. */
    public static final int INTERACTION_REMOVE_PIP = 3;

    /** Covers the bounds change type transition part of a PiP resize. */
    public static final int INTERACTION_BOUNDS_CHANGE_TRANSITION = 4;

    /** Covers the pinching action on a PiP window before pointer release. */
    public static final int INTERACTION_PINCHING_PIP = 5;

    /** Covers the drag action on a PiP window before pointer release. */
    public static final int INTERACTION_DRAG_PIP = 6;

    /**
     * Covers the PiP fling-to-snap animation once pointer is released after a drag.
     * This includes stashing and the part of dismissal animation within dismiss target
     * that moves PiP outside the screen bounds.
     */
    public static final int INTERACTION_FLING_TO_SNAP_PIP = 7;

    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(4L);

    private final Context mContext;
    private final Handler mHandler;
    private final InteractionJankMonitor mInteractionJankMonitor;

    public PipInteractionHandler(Context context, Handler handler,
            InteractionJankMonitor interactionJankMonitor) {
        mContext = context;
        mHandler = handler;
        mInteractionJankMonitor = interactionJankMonitor;
    }

    /**
     * Begin tracking PIP CUJ.
     *
     * @param leash PIP leash.
     * @param interaction Tag for interaction.
     */
    public void begin(SurfaceControl leash, @Interaction int interaction) {
        final InteractionJankMonitor.Configuration.Builder builder =
                InteractionJankMonitor.Configuration.Builder
                        .withSurface(CUJ_PIP_TRANSITION, mContext, leash, mHandler)
                        .setTag(pipInteractionToString(interaction))
                        .setTimeout(DEFAULT_TIMEOUT_MS);
        mInteractionJankMonitor.begin(builder);
    }

    /**
     * End tracking CUJ.
     */
    public void end() {
        mInteractionJankMonitor.end(CUJ_PIP_TRANSITION);
    }

    /**
     * Converts an interaction to a string representation used for tagging.
     *
     * @param interaction Interaction to track.
     * @return String representation of the interaction.
     */
    public static String pipInteractionToString(@Interaction int interaction) {
        return switch (interaction) {
            case INTERACTION_EXIT_PIP -> "EXIT_PIP";
            case INTERACTION_EXIT_PIP_TO_SPLIT -> "EXIT_PIP_TO_SPLIT";
            case INTERACTION_ENTER_PIP -> "ENTER_PIP";
            case INTERACTION_REMOVE_PIP -> "REMOVE_PIP";
            case INTERACTION_BOUNDS_CHANGE_TRANSITION -> "BOUNDS_CHANGE_TRANSITION";
            case INTERACTION_PINCHING_PIP -> "PINCHING_PIP";
            case INTERACTION_DRAG_PIP -> "DRAG_PIP";
            case INTERACTION_FLING_TO_SNAP_PIP -> "FLING_TO_SNAP_PIP";
            default -> "";
        };
    }
}
