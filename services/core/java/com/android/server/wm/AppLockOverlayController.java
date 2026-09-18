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

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_APP_LOCK;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IApplicationThread;
import android.app.ProfilerInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.Display;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.LockedAppActivity;
import com.android.internal.protolog.ProtoLog;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages the presentation of overlays for applications secured by the App Lock feature.
 *
 * <p>This controller is responsible for ensuring that when a user attempts to access a locked
 * application, an authentication screen is presented before the app's content is revealed. It
 * operates by placing a {@link LockedAppActivity} on top of any content that needs to be secured.
 *
 * <p>The controller is invoked from multiple entry points to apply locking logic:
 * <ol>
 *     <li><b>Bulk Locking:</b> {@link #lockActivitiesTasksForAppLock} is called to secure all
 *         content of a package at once (e.g., after device unlock or a session timeout).</li>
 *     <li><b>Just-in-Time Locking:</b> The controller intercepts attempts to show a locked
 *         activity through two complementary mechanisms:
 *         <ul>
 *             <li>It is called from {@link TaskFragment#resumeTopActivity} to prevent the locked
 *                 activity from being resumed, which can make the process state change to
 *                 {@link ActivityManager#PROCESS_STATE_TOP} leading to an unlocked state without
 *                 the authentication.</li>
 *             <li>It uses an internal listener (via {@link #registerActivity}) to apply an overlay
 *                 if a locked activity becomes visible without being resumed, such as when a
 *                 translucent activity above it is resumed.</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p>The controller uses one of two strategies for securing content. Regardless of the strategy,
 * the overlay is always launched with {@link ActivityOptions#setTaskOverlay}, meaning it sits
 * at the top of the Task stack and blocks access to all activities within that Task:
 * <ul>
 *     <li><b>Task-level overlay:</b> Used when the entire task belongs to the locked package.
 *         This is a "fire-and-forget" strategy that relies on the standard Task lifecycle
 *         (task removal) to clean up the overlay.</li>
 *     <li><b>Activity-level overlay:</b> Used for shared tasks (e.g., Activity Embedding) where
 *         only specific activities belong to a locked package. The controller actively monitors
 *         the lifecycle of overlay and target activities to apply overlays when the target activity
 *         becomes visible and clean up orphaned overlays if the target is no longer below the
 *         overlay.</li>
 * </ul>
 *
 * <p>Note: an activity that can be shown when locked doesn't require App Lock authentication, so
 * in that case the overlay won't be applied, and in the case of the task-level strategy, the
 * activity-level strategy will be used.
 *
 * <p>As a security fallback, if an overlay cannot be successfully launched or positioned, this
 * controller will either remove the associated task or finish the specific activity to prevent
 * unauthorized access.
 *
 * <p>This controller relies on the existing task and activity lifecycle management in the system.
 * For example, when an overlay activity is the last remaining activity in a task, the task is
 * automatically removed, and the overlay activity is finished. This is handled by the logic in
 * {@link Task#removeChild(WindowContainer, String)} and
 * {@link ActivityRecord#finishIfPossible(String, boolean)}, so no special cleanup is needed in this
 * controller.
 *
 * @hide
 */
final class AppLockOverlayController {

    private static final String SYSTEM_PACKAGE_NAME = "android";

    /**
     * Maps a monitored activity (overlay or target) to its lifecycle listener.
     * This is used to track when activities are removed so their partners can be cleaned up.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    private final ArrayMap<ActivityRecord, WindowContainerListener> mActivityListeners =
            new ArrayMap<>();

    /**
     * Maps an overlay activity to a weak reference of its target locked activity.
     * Used for finding the target given an overlay.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    final ArrayMap<ActivityRecord, WeakReference<ActivityRecord>> mOverlayToTargetMap =
            new ArrayMap<>();

    /**
     * Maps a target locked activity to a weak reference of its overlay.
     * Used for finding the overlay given a target.
     */
    @GuardedBy("mWmService.mGlobalLock")
    final ArrayMap<ActivityRecord, WeakReference<ActivityRecord>> mTargetToOverlayMap =
            new ArrayMap<>();

    /**
     * Keeps track of target activities that have a pending activity overlay launch. This is needed
     * to avoid launching multiple overlays for the same target.
     */
    @GuardedBy("mWmService.mGlobalLock")
    final ArraySet<ActivityRecord> mPendingActivityOverlayTargets = new ArraySet<>();

    private final ActivityTaskManagerService mAtmService;
    private final WindowManagerService mWmService;
    private ActivityTaskSupervisor mTaskSupervisor;
    private RootWindowContainer mRootWindowContainer;

    AppLockOverlayController(WindowManagerService wmService) {
        mWmService = wmService;
        mAtmService = wmService.mAtmService;
    }

    private RootWindowContainer getRootWindowContainer() {
        if (mRootWindowContainer == null) {
            mRootWindowContainer = mAtmService.mRootWindowContainer;
        }
        return mRootWindowContainer;
    }

    private ActivityTaskSupervisor getTaskSupervisor() {
        if (mTaskSupervisor == null) {
            mTaskSupervisor = mAtmService.mTaskSupervisor;
        }
        return mTaskSupervisor;
    }

    /**
     * Locks all activities and tasks associated with a given package.
     *
     * <p>This method is called when a package's locked state needs to be enforced, for example,
     * after a device unlock or a timeout. It iterates through all leaf tasks to identify content
     * belonging to the specified {@code packageName} and {@code userId}.
     *
     * <p>It applies one of two locking strategies:
     * <ul>
     *     <li><b>Task-level overlay:</b> If a task's primary identity ({@code realActivity})
     *         belongs to the locked package, a single overlay is placed on the entire task.</li>
     *     <li><b>Activity-level overlay:</b> If a task contains activities from the locked package
     *         but has a different primary identity (i.e., a shared task), all activities from the
     *         locked package within that task are registered via {@link #registerActivity}. This
     *         complements the just-in-time locking in {@link TaskFragment#resumeTopActivity} by
     *         handling cases where a locked activity becomes visible without being resumed (e.g.,
     *         under a translucent activity). The listener will then apply an overlay.</li>
     * </ul>
     *
     * <p>
     *
     * @param packageName the name of the package whose tasks and activities are to be locked.
     * @param userId      the user ID for which the package should be locked.
     */
    @GuardedBy("mWmService.mGlobalLock")
    void lockActivitiesTasksForAppLock(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLock: packageName=%s, userId=%d",
                packageName, userId);
        // TODO(b/462423789): Remove hasVisibleTask check once AppLockLocalService listens to task
        //  visibility changes.
        // If the package owns at least one visible task, it is considered unlocked.
        final boolean hasVisibleTask = hasVisibleNonLockedTaskForPackage(packageName, userId);
        if (hasVisibleTask) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLock: Skipping lock for %s,"
                    + " package owns a visible task", packageName);
            return;
        }

        // The package doesn't own any visible tasks, so activity and/or task overlays need to be
        // added.
        getRootWindowContainer().forAllLeafTasks(task -> {
            if (task.mUserId != userId) {
                return;
            }
            final ActivityRecord topNonFinishingActivity = task.getTopNonFinishingActivity();
            if (task.realActivity == null || topNonFinishingActivity == null) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLock: Skipping %s, no real"
                        + " or non-finishing activity", task);
                return;
            }
            if (packageName.equals(task.realActivity.getPackageName())
                    && !topNonFinishingActivity.canShowWhenLocked()) {
                // Apply task-level overlay only if the task belongs to the locked package, and
                // the  top activity cannot be shown when locked, i.e. App Lock allows activities
                // that can be shown when locked to bypass authentication.
                addLockedByAppLockTaskOverlay(task, packageName, userId);
            } else {
                // Register all locked activities in the task. This sets up a listener that adds an
                // overlay if the activity becomes visible without being resumed (e.g., under a
                // translucent activity), a case not covered by the check in
                // TaskFragment#resumeTopActivity.
                task.forAllActivities(activity -> {
                    if (!activity.finishing && packageName.equals(activity.packageName)
                            && activity.mUserId == userId) {
                        registerActivity(activity);
                    }
                }, /* traverseTopToBottom= */ true);
            }
        }, /* traverseTopToBottom= */ true);
    }

    /**
     * Returns {@code true} if the given {@code packageName} in user {@code userId} has a visible
     * task whose top activity is not an App Lock overlay.
     */
    @GuardedBy("mWmService.mGlobalLock")
    boolean hasVisibleNonLockedTaskForPackage(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        return getRootWindowContainer().getTask(task ->
                task.mUserId == userId
                        && task.isVisibleRequested()
                        && task.realActivity != null
                        && packageName.equals(task.realActivity.getPackageName())
                        && task.getTopNonFinishingActivity() != null
                        && !isActivityAppLockOverlay(task.getTopNonFinishingActivity())) != null;
    }

    /**
     * Adds a task-level overlay for a locked app as part of the App Lock feature.
     *
     * <p>This method launches {@link LockedAppActivity} on top of the specified {@code task}. The
     * {@code LockedAppActivity} is configured as a task overlay, which blocks access to all
     * activities within the task until the user successfully authenticates. A new overlay is not
     * added if an existing one is found for the same package and user.
     *
     * <p>If launching the overlay fails, this method removes the task as a security fallback to
     * prevent unauthorized access to the locked application's content.
     *
     * @param overlayTask the task to be locked with an overlay.
     * @param packageName the package name of the locked app, used to create the overlay intent.
     * @param userId      the user ID for which the app is locked.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    void addLockedByAppLockTaskOverlay(@NonNull Task overlayTask, @NonNull String packageName,
            int userId) {
        Objects.requireNonNull(overlayTask);
        Objects.requireNonNull(packageName);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlay: %s, packageName=%s",
                overlayTask, packageName);

        final ActivityRecord topNonFinishingActivity = overlayTask.getTopNonFinishingActivity();
        if (topNonFinishingActivity == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlay: Skipping %s, it has no"
                    + " activities", overlayTask);
            return;
        }
        if (isActivityAppLockOverlayForPackage(topNonFinishingActivity, packageName, userId)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlay: Skipping %s, an overlay"
                    + " already exists: %s", overlayTask, topNonFinishingActivity);
            return;
        }
        final Intent intent = createOverlayIntent(packageName, userId, /* isTaskOverlay= */ true);
        startLockedAppActivityAsync(overlayTask, intent, userId, /* onSuccessLocked= */ null,
                /* onFailureLocked= */ null);
    }

    /**
     * Adds an activity-level overlay for a specific locked activity.
     *
     * <p>This method is a central component of the "just-in-time" locking strategy. It is called
     * from two primary contexts:
     * <ul>
     *     <li>From {@link TaskFragment#resumeTopActivity}, just before a locked activity is
     *         resumed.</li>
     *     <li>From the visibility listener set up by {@link #registerActivity}, when a locked
     *         activity becomes visible without being resumed (e.g., under a translucent
     *         activity).</li>
     * </ul>
     *
     * <p>The method asynchronously launches a {@link LockedAppActivity} and provides callbacks to
     * handle the outcome. On success, {@link #handleActivityOverlayLaunchSuccess} finds,
     * positions, and pairs the overlay with its target. On failure, the {@code targetActivity} is
     * finished as a security fallback to prevent unauthorized access.
     *
     * <p>An overlay is not added if a valid one already exists for the target.
     *
     * @param targetActivity the activity to be locked with an overlay.
     */
    // TODO(b/461861228): Do not add overlays for activities with ActivityRecord#showWhenLocked and
    //  ActivityInfo#showWhenLocked. This also requires reacting to Keyguard flag changes in
    //  DisplayContent#notifyKeyguardFlagsChanged, which indicates that activities' visibility needs
    //  to be reevaluated.
    @GuardedBy("mWmService.mGlobalLock")
    void addLockedByAppLockActivityOverlay(@NonNull ActivityRecord targetActivity) {
        Objects.requireNonNull(targetActivity);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: targetActivity=%s",
                targetActivity);
        if (targetActivity.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Skipping %s, it is"
                    + " finishing", targetActivity);
            return;
        }
        final Task targetTask = targetActivity.getTask();
        if (targetTask == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Cannot add activity"
                    + " overlay for an activity with no task: %s", targetActivity);
            finishActivitySafe(targetActivity, "could-not-find-task");
            return;
        }
        final String packageName = targetActivity.packageName;
        if (packageName == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Cannot add activity"
                    + " overlay for an activity with no package: %s", targetActivity);
            return;
        }
        final int userId = targetActivity.mUserId;

        // An overlay should not be added if an overlay is being added.
        if (mPendingActivityOverlayTargets.contains(targetActivity)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Skipping %s, an"
                    + " overlay is already being added", targetActivity);
            return;
        }
        // An overlay should not be added if a valid overlay exists.
        final WeakReference<ActivityRecord> overlayRef = mTargetToOverlayMap.get(targetActivity);
        if (overlayRef != null && overlayRef.get() != null) {
            final ActivityRecord overlayActivity = overlayRef.get();
            if (overlayActivity.finishing) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Skipping %s, an"
                                + " overlay is already finishing: %s", targetActivity,
                        overlayActivity);
                return;
            }
            if (isAppLockOverlayValidForTarget(overlayActivity, targetActivity)) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Skipping %s, an"
                                + " overlay already exists on top of it: %s", targetActivity,
                        overlayActivity);
                return;
            }
            // No need to clean up here, since invalid overlays are removed when they become
            // orphaned or the overlay will finish itself if the package is unlocked.
        }

        mPendingActivityOverlayTargets.add(targetActivity);
        final Intent intent = createOverlayIntent(packageName, userId, /* isTaskOverlay= */ false);
        // Create a unique identifier to find the overlay activity later.
        final String overlayIdentifier = "unique:" + UUID.randomUUID();
        intent.setIdentifier(overlayIdentifier);

        final Consumer<Task> onOverlayLaunchSuccessLocked = (task) -> {
            handleActivityOverlayLaunchSuccess(task, overlayIdentifier, targetActivity);
            mPendingActivityOverlayTargets.remove(targetActivity);
        };

        final Consumer<Task> onOverlayLaunchFailureLocked = (task) -> {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlay: Failed to add an"
                    + " overlay for %s, finishing", targetActivity);
            if (!targetActivity.finishing) {
                targetActivity.finishIfPossible("app-lock-overlay-failed",  /* oomAdj= */ true);
            }
            mPendingActivityOverlayTargets.remove(targetActivity);
        };

        startLockedAppActivityAsync(targetTask, intent, userId, onOverlayLaunchSuccessLocked,
                onOverlayLaunchFailureLocked);
    }

    /**
     * Returns a set of package names that currently have a visible App Lock overlay for the
     * specified user.
     *
     * <p>This scans all visible activities in the system and identifies those that are instances
     * of {@link LockedAppActivity} acting as an App Lock overlay. It then extracts the target
     * package name from these overlay activities for the given user ID.
     *
     * <p>This is used in multi-window scenarios to identify all apps that are pending
     * authentication. When a user authenticates one app, this list can be used to simultaneously
     * authenticate all other visible locked apps, reducing user friction.
     *
     * @param userId The user ID for whom to find packages with visible App Lock overlay.
     * @return A set of package names corresponding to the visible App Lock overlay.
     */
    Set<String> getPackagesWithVisibleAppLockOverlay(int userId) {
        final List<ActivityRecord> visibleActivities = mAtmService.mRootWindowContainer
                .getTopVisibleActivities(Display.INVALID_DISPLAY);

        final ArraySet<String> packages = new ArraySet<>();
        if (visibleActivities == null) {
            return packages;
        }

        for (int i = 0; i < visibleActivities.size(); i++) {
            final ActivityRecord activity = visibleActivities.get(i);
            if (activity == null || getAppLockOverlayTargetUserId(activity) != userId) {
                continue;
            }

            final String targetPackage = getAppLockOverlayTargetPackage(activity);
            if (targetPackage != null) {
                packages.add(targetPackage);
            }
        }
        return packages;
    }

    /**
     * Finalizes the setup of an activity-level overlay after it has been successfully launched.
     *
     * <p>This method is invoked as a callback from {@link #startLockedAppActivityAsync} once the
     * {@link LockedAppActivity} has been started. It is responsible for the critical steps of
     * validating the newly created overlay and establishing a monitored relationship with its
     * target.
     *
     * <p>The process involves:
     * <ol>
     *     <li>Finding the new overlay activity using its unique {@code overlayIdentifier}.
     *     <li>Performing validations to ensure the overlay and target are not null or finishing. If
     *         validations fail, this method ensures that the overlay and/or target are finished to
     *         maintain a secure state.</li>
     *     <li>If validations succeed, it establishes a bidirectional mapping between the overlay
     *         and target and registers both for lifecycle monitoring via
     *         {@link #registerActivity}.</li>
     * </ol>
     *
     * @param task              the task containing the overlay and target activities.
     * @param overlayIdentifier the unique identifier used to find the newly launched overlay.
     * @param targetActivity    the activity being secured by the overlay.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private void handleActivityOverlayLaunchSuccess(@NonNull Task task,
            @NonNull String overlayIdentifier, @NonNull ActivityRecord targetActivity) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(overlayIdentifier);
        Objects.requireNonNull(targetActivity);

        final ActivityRecord overlayActivity = task.getActivity(
                activity -> overlayIdentifier.equals(activity.intent.getIdentifier()));
        if (overlayActivity == null || overlayActivity.finishing) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "handleActivityOverlayLaunchSuccess: Could not find the"
                    + " launched overlay activity, finishing target: %s", targetActivity);
            finishActivitySafe(targetActivity, "could-not-find-overlay");
            return;
        }

        if (targetActivity.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "handleActivityOverlayLaunchSuccess: Target activity is"
                    + " finishing, finishing overlay: %s", overlayActivity);
            finishActivitySafe(overlayActivity, "target-finishing");
            return;
        }

        ProtoLog.d(WM_DEBUG_APP_LOCK, "handleActivityOverlayLaunchSuccess: Successfully added an"
                + " overlay for %s", targetActivity);

        mOverlayToTargetMap.put(overlayActivity, new WeakReference<>(targetActivity));
        mTargetToOverlayMap.put(targetActivity, new WeakReference<>(overlayActivity));
        registerActivity(overlayActivity);
        registerActivity(targetActivity);
    }

    /**
     * Registers an activity to monitor its lifecycle, enabling cleanup and just-in-time locking.
     *
     * <p>This method attaches a listener with dual responsibilities depending on the activity type:
     * <ul>
     *     <li><b>For a target (locked) activity:</b> The listener triggers
     *         {@link #addLockedByAppLockActivityOverlay} if the activity becomes visible
     *         without being resumed (e.g., when a translucent activity above it is resumed). This
     *         complements the check in {@link TaskFragment#resumeTopActivity}.</li>
     *     <li><b>For an overlay activity:</b> When the overlay becomes visible, the listener calls
     *         {@link #finishActivityOverlayIfOrphan} to verify it still has a valid, locked
     *         target, removing it otherwise.</li>
     * </ul>
     *
     * <p>For both types, the listener's {@code onRemoved} callback ensures that when one activity
     * in a pair is removed, its partner is also cleaned up to prevent orphaned windows.
     *
     * @param activity the overlay or target activity to register for monitoring.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    void registerActivity(@NonNull ActivityRecord activity) {
        ProtoLog.d(WM_DEBUG_APP_LOCK, "registerActivity: activity=%s", activity);
        Objects.requireNonNull(activity);

        final boolean isOverlay = isActivityAppLockOverlay(activity);

        if (mActivityListeners.containsKey(activity)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "registerActivity: Already registered: %s", activity);
            return;
        }
        final WindowContainerListener listener = new WindowContainerListener() {
            @Override
            public void onVisibleRequestedChanged(boolean isVisibleRequested) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "onActivityVisibleRequestedChanged: activity= %s,"
                        + " isVisibleRequested=%b", activity, isVisibleRequested);
                if (!isVisibleRequested) {
                    return;
                }
                if (isOverlay) {
                    ProtoLog.d(WM_DEBUG_APP_LOCK, "onActivityVisibleRequestedChanged: Overlay is"
                            + " becoming visible %s, checking if it's orphaned", activity);
                    finishActivityOverlayIfOrphan(activity);
                } else {
                    if (isActivityLockedByAppLock(activity)) {
                        ProtoLog.d(WM_DEBUG_APP_LOCK, "onActivityVisibleRequestedChanged: Locked"
                                        + " activity %s is becoming visible, so applying overlay",
                                activity);
                        addLockedByAppLockActivityOverlay(activity);
                    }
                }
            }

            @Override
            public void onRemoved() {
                onActivityRemoved(activity);
            }
        };
        activity.registerWindowContainerListener(listener);
        mActivityListeners.put(activity, listener);
    }

    /**
     * Returns {@code true} if the given activity is currently in a locked state by App Lock.
     *
     * <p>This method checks if the activity's package is currently locked and verifies that there
     * are no other visible, unlocked tasks for the same package (which would indicate an active,
     * unlocked session). If an activity can be shown when locked, by definition, it cannot be
     * locked by App Lock.
     *
     * @param activity the activity to check for the App Lock locked state
     * @return {@code true} if the activity is locked
     */
    @GuardedBy("mWmService.mGlobalLock")
    boolean isActivityLockedByAppLock(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        if (activity.finishing || activity.canShowWhenLocked()) {
            ProtoLog.d(WM_DEBUG_APP_LOCK,
                    "isActivityLockedByAppLock: activity.finishing: %b, activity"
                            + ".canShowWhenLocked(): %b, activity: %s, returning false",
                    activity.finishing, activity.canShowWhenLocked(), activity);
            return false;
        }
        // TODO(b/462423789): Remove hasVisibleTask check once AppLockLocalService listens to task
        //  visibility changes.
        final String packageName = activity.packageName;
        final int userId = activity.mUserId;
        return packageName != null
                && mWmService.isPackageLockedByAppLockLocked(packageName, userId)
                && !hasVisibleNonLockedTaskForPackage(packageName, userId);
    }

    /**
     * Validates the relationship between an overlay and its target activity.
     *
     * <p>An overlay is considered valid if:
     * <ul>
     *     <li>Neither the overlay nor the target is finishing.</li>
     *     <li>They are correctly mapped to each other.</li>
     *     <li>They both exist in the same task.</li>
     *     <li>The target activity's package is currently locked.</li>
     * </ul>
     *
     * Note that the overlay will always be the top activity due to
     * {@link ActivityOptions#setTaskOverlay} in
     * {@link #startLockedAppActivityAsUserUncheckedAsync}, so there is no need to check whether the
     * overlay is on top of the target activity.
     *
     * @param overlay the overlay activity.
     * @param target  the target activity being locked.
     * @return {@code true} if the overlay is valid for the target, {@code false} otherwise.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    boolean isAppLockOverlayValidForTarget(@NonNull ActivityRecord overlay,
            @NonNull ActivityRecord target) {
        Objects.requireNonNull(overlay);
        Objects.requireNonNull(target);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: overlay %s and target %s.",
                overlay, target);

        if (overlay.finishing || target.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - either or both"
                    + " are finishing.");
            return false;
        }
        final WeakReference<ActivityRecord> overlayRef = mTargetToOverlayMap.get(target);
        final WeakReference<ActivityRecord> targetRef = mOverlayToTargetMap.get(overlay);
        if (overlayRef == null || targetRef == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - either or both"
                    + " are not defined in the maps.");
            return false;
        }
        if (overlayRef.get() != overlay || targetRef.get() != target) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - either or both"
                    + " are not the same in the maps.");
            return false;
        }
        final Task overlayTask = overlay.getTask();
        final Task targetTask = target.getTask();
        if (targetTask == null || overlayTask != targetTask) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - they are not in"
                    + " the same task.");
            return false;
        }
        if (!isActivityAppLockOverlayForPackage(overlay, target.packageName, target.mUserId)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - overlay is not"
                    + " meant for the target's package");
            return false;
        }
        if (!mWmService.isPackageLockedByAppLockLocked(target.packageName, target.mUserId)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: false - target is not"
                    + " locked.");
            return false;
        }
        ProtoLog.d(WM_DEBUG_APP_LOCK, "isAppLockOverlayValidForTarget: true");
        return true;
    }

    /**
     * Finishes an overlay activity if it is orphaned or no longer valid.
     *
     * <p>This method is called when an overlay activity becomes visible. It serves as a safeguard
     * to ensure that the overlay has a valid, locked target. If the target is missing, has been
     * unlocked, or the relationship is otherwise invalid, this method finishes the overlay to
     * prevent it from lingering incorrectly on screen.
     *
     * @param overlay the overlay activity to validate.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private void finishActivityOverlayIfOrphan(@NonNull ActivityRecord overlay) {
        Objects.requireNonNull(overlay);

        final WeakReference<ActivityRecord> targetReference = mOverlayToTargetMap.get(overlay);
        if (targetReference == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "finishActivityOverlayIfOrphan: Finishing overlay %s"
                    + " because target reference is null", overlay);
            finishActivitySafe(overlay, "orphaned-app-lock-overlay");
            return;
        }

        final ActivityRecord target = targetReference.get();
        if (target == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "finishActivityOverlayIfOrphan: Finishing overlay %s"
                    + " because target is null", overlay);
            finishActivitySafe(overlay, "orphaned-app-lock-overlay");
            return;
        }

        if (!isAppLockOverlayValidForTarget(overlay, target)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "finishActivityOverlayIfOrphan: Finishing overlay %s"
                    + " because the overlay is not valid for the target %s", overlay, target);
            finishActivitySafe(overlay, "orphaned-app-lock-overlay");
            return;
        }

        ProtoLog.d(WM_DEBUG_APP_LOCK, "finishActivityOverlayIfOrphan: Overlay %s is not an orphan,"
                + " so keeping", overlay);
    }

    /**
     * Handles the removal of a monitored activity, ensuring its partner is also cleaned up.
     *
     * <p>This method is called when an overlay or target activity is removed from its parent
     * container. It finds the corresponding partner activity and finishes it to prevent an orphaned
     * overlay or an unsecured target. It also unregisters all listeners and removes entries from
     * the tracking maps for both activities.
     *
     * @param activity the activity that was removed.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private void onActivityRemoved(@NonNull ActivityRecord activity) {
        ProtoLog.d(WM_DEBUG_APP_LOCK, "onActivityRemoved: activity=%s", activity);
        // Find the partner activity using either map.
        WeakReference<ActivityRecord> partnerRef = mOverlayToTargetMap.get(activity);
        if (partnerRef == null) {
            partnerRef = mTargetToOverlayMap.get(activity);
        }
        final ActivityRecord partner = (partnerRef != null) ? partnerRef.get() : null;

        // Determine which is the overlay and explicitly finish it.
        final ActivityRecord overlay = mOverlayToTargetMap.containsKey(activity) ? activity
                : partner;
        if (overlay != null && !overlay.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "onActivityRemoved: finishing overlay=%s", overlay);
            finishActivitySafe(overlay, "cleaning-up-app-lock-pair");
        }

        // Clean up all listeners and map entries for both activities.
        if (mActivityListeners.containsKey(activity)) {
            activity.unregisterWindowContainerListener(mActivityListeners.get(activity));
            mActivityListeners.remove(activity);
        }
        if (partner != null && mActivityListeners.containsKey(partner)) {
            partner.unregisterWindowContainerListener(mActivityListeners.get(partner));
            mActivityListeners.remove(partner);
        }

        mOverlayToTargetMap.remove(activity);
        mTargetToOverlayMap.remove(activity);
        if (partner != null) {
            mOverlayToTargetMap.remove(partner);
            mTargetToOverlayMap.remove(partner);
        }
    }

    /** Returns {@code true} if the given activity is the App Lock overlay, i.e.
     * {@link LockedAppActivity}. */
    private boolean isActivityAppLockOverlay(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        return !activity.finishing
                && activity.isTaskOverlay()
                && activity.mActivityComponent != null
                && activity.intent != null
                && SYSTEM_PACKAGE_NAME.equals(activity.mActivityComponent.getPackageName())
                && LockedAppActivity.class.getName().equals(
                activity.mActivityComponent.getClassName());
    }

    /**
     * Returns {@code true} if the given activity is the App Lock overlay defined by
     * {@link #isActivityAppLockOverlay(ActivityRecord)} for the specified package and user.
     */
    private boolean isActivityAppLockOverlayForPackage(@NonNull ActivityRecord activity,
            @NonNull String packageName, int userId) {
        Objects.requireNonNull(activity);

        return packageName.equals(getAppLockOverlayTargetPackage(activity))
                && userId == getAppLockOverlayTargetUserId(activity);
    }

    /**
     * Returns the target package name if the given activity is the App Lock overlay defined by
     * {@link #isActivityAppLockOverlay(ActivityRecord)}.
     */
    @Nullable
    private String getAppLockOverlayTargetPackage(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        return isActivityAppLockOverlay(activity)
                ? activity.intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) : null;
    }

    /**
     * Returns the target user ID if the given activity is the App Lock overlay defined by
     * {@link #isActivityAppLockOverlay(ActivityRecord)}.
     */
    private int getAppLockOverlayTargetUserId(@NonNull ActivityRecord activity) {
        Objects.requireNonNull(activity);

        return isActivityAppLockOverlay(activity)
                ? activity.intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.USER_NULL)
                : UserHandle.USER_NULL;
    }

    private void finishActivitySafe(@NonNull ActivityRecord activity, @NonNull String reason) {
        if (!activity.finishing) {
            activity.finishIfPossible(reason, /* oomAdj= */ true);
        }
    }

    /**
     * Creates an intent for launching the {@link LockedAppActivity} overlay.
     *
     * @param packageName   the package name of the app being locked.
     * @param userId        the user ID of the app being locked.
     * @param isTaskOverlay whether the overlay is for a task (true) or an activity (false).
     * @return an intent configured to launch the overlay activity.
     */
    private Intent createOverlayIntent(@NonNull String packageName, int userId,
            boolean isTaskOverlay) {
        Objects.requireNonNull(packageName);

        final Intent intent = LockedAppActivity.createLockedAppActivityIntent(packageName, userId,
                /* target= */ null);
        if (isTaskOverlay) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        return intent;
    }

    /**
     * Starts the {@link LockedAppActivity} asynchronously on the handler thread.
     *
     * <p>This method posts a runnable to the {@link ActivityTaskManagerService}'s handler to start
     * the activity. This is necessary to avoid deadlocks that can occur if {@code startActivity} is
     * called while holding the {@link WindowManagerGlobalLock}.
     *
     * <p>If the activity start is successful, the provided {@code onSuccessLocked} callback is
     * invoked on the handler thread with the {@link WindowManagerGlobalLock} held. If the start
     * fails, the {@code onFailureLocked} callback is invoked; if it is {@code null}, the task is
     * removed as a security fallback.
     *
     * @param targetTask      the task where the overlay activity should be launched.
     * @param intent          the intent to launch the overlay activity.
     * @param userId          the user ID to launch the activity as.
     * @param onSuccessLocked a callback invoked with the re-validated task if the overlay was
     *                        successfully launched.
     * @param onFailureLocked a callback invoked if the overlay failed to launch.
     */
    private void startLockedAppActivityAsync(@NonNull Task targetTask, @NonNull Intent intent,
            int userId, @Nullable Consumer<Task> onSuccessLocked,
            @Nullable Consumer<Task> onFailureLocked) {
        Objects.requireNonNull(targetTask);
        Objects.requireNonNull(intent);

        final int taskId = targetTask.mTaskId;
        mAtmService.mH.post(() -> {
            final int result = startLockedAppActivityAsUserUncheckedAsync(intent, taskId, userId);
            synchronized (mWmService.mGlobalLock) {
                final Task task = getRootWindowContainer().anyTaskForId(taskId);
                if (task == null) {
                    ProtoLog.w(WM_DEBUG_APP_LOCK, "Task %d not found after launching overlay",
                            taskId);
                    if (onFailureLocked != null) {
                        onFailureLocked.accept(null);
                    }
                    return;
                }
                if (ActivityManager.isStartResultSuccessful(result)) {
                    if (onSuccessLocked != null) {
                        onSuccessLocked.accept(task);
                    }
                } else {
                    if (onFailureLocked != null) {
                        onFailureLocked.accept(task);
                    } else {
                        ProtoLog.e(WM_DEBUG_APP_LOCK, "Could not add App Lock task overlay for %s,"
                                + " removing task as fallback.", task);
                        getTaskSupervisor().removeTask(task, /* killProcess= */ false,
                                /* removeFromRecents= */ true,
                                "Could not add App Lock task overlay");
                    }
                }
            }
        });
    }

    /**
     * Starts {@link LockedAppActivity} as a task overlay asynchronously.
     *
     * <p>This method is a wrapper around
     * {@link ActivityTaskManagerService#startActivityAsUser(IApplicationThread, String, String,
     * Intent, String, IBinder, String, int, int, ProfilerInfo, Bundle, int)}
     * that handles exceptions and returns a standard result code. It must <b>NOT</b> be called
     * while holding the global lock to avoid deadlocks.
     *
     * @param intent the intent to start.
     * @param taskId the ID of the task to launch the activity into.
     * @param userId the user ID to launch the activity as.
     * @return the result of the start request, e.g., {@link ActivityManager#START_SUCCESS} or
     * {@link ActivityManager#START_CANCELED}.
     */
    private int startLockedAppActivityAsUserUncheckedAsync(@NonNull Intent intent, int taskId,
            int userId) {
        Objects.requireNonNull(intent);

        try {
            final ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchTaskId(taskId);
            options.setTaskOverlay(/* taskOverlay= */ true, /* canResume= */ false);
            return mAtmService.startActivityAsUser(
                    /* caller= */ mAtmService.mContext.getIApplicationThread(),
                    /* callingPackage= */ mAtmService.mContext.getBasePackageName(),
                    /* callingAttributionTag= */ mAtmService.mContext.getAttributionTag(), intent,
                    intent.resolveTypeIfNeeded(mAtmService.mContext.getContentResolver()),
                    /* resultTo= */ null, /* resultWho= */ null, /* requestCode= */ 0,
                    Intent.FLAG_ACTIVITY_NEW_TASK, /* profilerInfo= */ null, options.toBundle(),
                    userId);
        } catch (Exception e) {
            ProtoLog.e(WM_DEBUG_APP_LOCK, "startActivityAsUser failed: %s", e);
            return ActivityManager.START_CANCELED;
        }
    }
}
