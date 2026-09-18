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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;

import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.shared.TransitionUtil.isOpeningMode;
import static com.android.wm.shell.shared.split.SplitScreenConstants.FLAG_IS_DIVIDER_BAR;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_PIP;
import static com.android.wm.shell.transition.DefaultMixedHandler.subCopy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.StageCoordinator;

import java.util.List;

public class MixedTransitionHelper {
    static boolean animateEnterPipFromSplit(
            @NonNull DefaultMixedHandler.MixedTransition mixed, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull Transitions player, @NonNull MixedTransitionHandler mixedHandler,
            @NonNull PipTransitionController pipHandler, @NonNull StageCoordinator splitHandler,
            @Nullable PinnedLayerHandler pinnedLayerHandler, boolean replacingPip) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering PIP while Split-Screen is foreground.");
        TransitionInfo.Change pipChange = null;
        TransitionInfo.Change pipActivityChange = null;
        TransitionInfo.Change wallpaper = null;
        final TransitionInfo everythingElse =
                subCopy(info, TRANSIT_TO_BACK, true /* changes */);
        boolean homeIsOpening = false;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (pipHandler.isEnteringPip(change, info.getType())) {
                if (pipChange != null) {
                    throw new IllegalStateException("More than 1 pip-entering changes in one"
                            + " transition? " + info);
                }
                pipChange = change;
                // going backwards, so remove-by-index is fine.
                everythingElse.getChanges().remove(i);
            } else if (change.getTaskInfo() == null && change.getParent() != null
                    && pipChange != null && change.getParent().equals(pipChange.getContainer())) {
                // Cache the PiP activity if it's a target and cached pip task change is its parent;
                // note that we are bottom-to-top, so if such activity has a task
                // that is also a target, then it must have been cached already as pipChange.
                pipActivityChange = change;
                everythingElse.getChanges().remove(i);
            } else if (isHomeOpening(change)) {
                homeIsOpening = true;
            } else if (isWallpaper(change)) {
                wallpaper = change;
            }
        }
        if (pipChange == null) {
            // um, something probably went wrong.
            return false;
        }
        final boolean isGoingHome = homeIsOpening;
        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            if (isGoingHome) {
                splitHandler.onTransitionAnimationComplete();
            }
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };
        if (isGoingHome || splitHandler.getSplitItemPosition(pipChange.getLastParent())
                != SPLIT_POSITION_UNDEFINED) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation is actually mixed "
                    + "since entering-PiP caused us to leave split and return home.");
            // We need to split the transition into 2 parts: the pip part (animated by pip)
            // and the dismiss-part (animated by launcher).
            mixed.mInFlightSubAnimations = 2;
            // immediately make the wallpaper visible (so that we don't see it pop-in during
            // the time it takes to start recents animation (which is remote).
            if (wallpaper != null) {
                startTransaction.show(wallpaper.getLeash()).setAlpha(wallpaper.getLeash(), 1.f);
            }
            // make a new startTransaction because pip's startEnterAnimation "consumes" it so
            // we need a separate one to send over to launcher.
            SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();
            @SplitScreen.StageType int topStageToKeep = STAGE_TYPE_UNDEFINED;
            if (splitHandler.isSplitScreenVisible() && !replacingPip) {
                // The non-going home case, we could be pip-ing one of the split stages and keep
                // showing the other
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    TransitionInfo.Change change = info.getChanges().get(i);
                    if (change == pipChange) {
                        // Ignore the change/task that's going into Pip
                        continue;
                    }
                    @SplitScreen.StageType int splitItemStage =
                            splitHandler.getSplitItemStage(change.getLastParent());
                    if (splitItemStage != STAGE_TYPE_UNDEFINED) {
                        topStageToKeep = splitItemStage;
                        break;
                    }
                }

                // Let split update internal state for dismiss.
                splitHandler.prepareDismissAnimation(topStageToKeep,
                        EXIT_REASON_CHILD_TASK_ENTER_PIP, everythingElse, otherStartT,
                        finishTransaction);
            }

            // We are trying to accommodate launcher's close animation which can't handle the
            // divider-bar, so if split-handler is closing the divider-bar, just hide it and
            // remove from transition info.
            for (int i = everythingElse.getChanges().size() - 1; i >= 0; --i) {
                if ((everythingElse.getChanges().get(i).getFlags() & FLAG_IS_DIVIDER_BAR)
                        != 0) {
                    everythingElse.getChanges().remove(i);
                    break;
                }
            }

            pipHandler.setEnterAnimationType(ANIM_TYPE_ALPHA);
            if (PipFlags.isPip2ExperimentEnabled()) {
                TransitionInfo pipInfo = subCopy(info, TRANSIT_PIP, false /* withChanges */);
                pipInfo.getChanges().add(pipChange);
                if (pipActivityChange != null) {
                    pipInfo.getChanges().add(pipActivityChange);
                }

                if (pinnedLayerHandler != null
                        && pinnedLayerHandler.observes(mixed.mTransition)) {
                    // launching pip has additional side effects on pinned layer
                    mixed.mInFlightSubAnimations++;
                    final TransitionInfo pinnedLayerInfo = removePinnedLayerTaskChangesFrom(
                            pinnedLayerHandler, info, mixed.mTransition);
                    pinnedLayerHandler.startAnimation(mixed.mTransition, pinnedLayerInfo,
                            startTransaction, finishTransaction, finishCB);
                }
                pipHandler.startAnimation(mixed.mTransition, pipInfo, startTransaction,
                        finishTransaction, finishCB);
            } else {
                pipHandler.startEnterAnimation(pipChange, startTransaction, finishTransaction,
                        finishCB);
            }
            // Dispatch the rest of the transition normally. This will most-likely be taken by
            // recents or default handler.
            mixed.mLeftoversHandler = player.dispatchTransition(mixed.mTransition, everythingElse,
                    otherStartT, finishTransaction, finishCB, mixedHandler);
        } else {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  Not leaving split, so just "
                    + "forward animation to Pip-Handler.");
            // This happens if the pip-ing activity is in a multi-activity task (and thus a
            // new pip task is spawned). In this case, we don't actually exit split so we can
            // just let pip transition handle the animation verbatim.
            mixed.mInFlightSubAnimations = 1;
            if (pinnedLayerHandler != null
                    && pinnedLayerHandler.observes(mixed.mTransition)) {
                // launching pip has additional side effects on pinned layer
                mixed.mInFlightSubAnimations++;
                pinnedLayerHandler.startAnimation(mixed.mTransition, info,
                        startTransaction, finishTransaction, finishCB);
            }
            pipHandler.startAnimation(
                    mixed.mTransition, info, startTransaction, finishTransaction, finishCB);
        }
        return true;
    }

    /**
     * Check to see if we're only closing split to enter pip or if we're replacing pip with
     * another task. If we are replacing, this will return the change for the task we are replacing
     * pip with
     *
     * @param info Any number of changes
     * @param pipChange TransitionInfo.Change indicating the task that is being pipped
     * @param splitMainStageRootId MainStage's rootTaskInfo's id
     * @param splitSideStageRootId SideStage's rootTaskInfo's id
     * @param lastPipSplitStage The last stage that {@code pipChange} was in
     * @return The change from {@code info} that is replacing the {@code pipChange}, {@code null}
     *         otherwise
     */
    @Nullable
    public static TransitionInfo.Change getPipReplacingChange(TransitionInfo info,
            TransitionInfo.Change pipChange, int splitMainStageRootId, int splitSideStageRootId,
            @SplitScreen.StageType int lastPipSplitStage) {
        int lastPipParentTask = -1;
        if (lastPipSplitStage == STAGE_TYPE_MAIN) {
            lastPipParentTask = splitMainStageRootId;
        } else if (lastPipSplitStage == STAGE_TYPE_SIDE) {
            lastPipParentTask = splitSideStageRootId;
        }

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (change == pipChange || !isOpeningMode(change.getMode()) ||
                    change.getTaskInfo() == null) {
                // Ignore the change/task that's going into Pip or not opening
                continue;
            }

            if (change.getTaskInfo().parentTaskId == lastPipParentTask) {
                return change;
            }
        }
        return null;
    }

    private static boolean isHomeOpening(@NonNull TransitionInfo.Change change) {
        return change.getTaskInfo() != null
                && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME;
    }

    private static boolean isWallpaper(@NonNull TransitionInfo.Change change) {
        return (change.getFlags() & FLAG_IS_WALLPAPER) != 0;
    }

    static boolean animateKeyguard(
            @NonNull DefaultMixedHandler.MixedTransition mixed, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull KeyguardTransitionHandler keyguardHandler,
            PipTransitionController pipHandler) {
        if (mixed.mFinishT == null) {
            mixed.mFinishT = finishTransaction;
            mixed.mFinishCB = finishCallback;
        }
        // Sync pip state.
        if (pipHandler != null) {
            pipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
        }
        return mixed.startSubAnimation(keyguardHandler, info, startTransaction, finishTransaction);
    }

    @NonNull
    static TransitionInfo removePinnedLayerTaskChangesFrom(
            @NonNull PinnedLayerHandler pinnedLayerHandler,
            @NonNull TransitionInfo outInfo,
            @NonNull IBinder transition) {
        final TransitionInfo pinnedLayerInfo =
                subCopy(outInfo, outInfo.getType(), /* withChanges */ false);
        for (int i = outInfo.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = outInfo.getChanges().get(i);

            // With the current implementation, it's safe to assume that if a task has
            // pinned layer changes, those are its only changes.
            if (pinnedLayerHandler.awaitsChangesFor(change.getTaskInfo(), transition)) {
                outInfo.getChanges().remove(i);
                pinnedLayerInfo.getChanges().add(change);
            }
        }
        return pinnedLayerInfo;
    }

    /**
     * Finds the top-most split-screen stage that should be fullscreen when dismissing
     * split-screen.
     *
     * @param changes the list of changes in the transition
     * @param splitHandler the split-screen stage coordinator
     * @param taskToIgnore an optional task change to ignore. This is for cases where a task is
     *                     launching on top of split-screen, and we want to find which of the
     *                     remaining split-screen tasks is on top.
     * @return the stage type of the top-most task in split-screen, or
     *         {@link SplitScreen#STAGE_TYPE_UNDEFINED} if none
     */
    @SplitScreen.StageType
    static int getTopSplitStageToKeep(@NonNull List<TransitionInfo.Change> changes,
            @Nullable StageCoordinator splitHandler,
            @Nullable TransitionInfo.Change taskToIgnore) {
        if (splitHandler == null) {
            return SplitScreen.STAGE_TYPE_UNDEFINED;
        }
        for (int i = changes.size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = changes.get(i);
            if (change == taskToIgnore) {
                continue;
            }
            final int prevStage = splitHandler.getSplitItemStage(change.getLastParent());
            if (prevStage != SplitScreen.STAGE_TYPE_UNDEFINED) {
                return prevStage;
            }
        }
        return SplitScreen.STAGE_TYPE_UNDEFINED;
    }

    /**
     * Find the first change in this transition that is for the home task.
     *
     * @return change or {@code null} if the home task is not in the list of changes
     */
    static @Nullable TransitionInfo.Change getHomeChange(@NonNull TransitionInfo info) {
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME) {
                return change;
            }
        }
        return null;
    }
}
