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

package android.window;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Data object for the request Task properties.
 *
 * Note: this should only include properties that are not depending on the applying order.
 * @hide
 */
@DataClass(genEqualsHashCode = true, genParcelable = true, genToString = true,
        genConstructor = false, genBuilder = false, genSetters = false, genConstDefs = false)
public final class TaskPropertiesRequest implements Parcelable {

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_REPARENT_ON_DISPLAY_REMOVAL = 1;
    public static final int REQUEST_FORCE_OPAQUE = 1 << 1;
    public static final int REQUEST_IGNORE_INSETS = 1 << 2;
    public static final int REQUEST_DISABLE_APP_COMPAT_ROUNDED_CORNERS = 1 << 3;
    public static final int REQUEST_FORCE_LEAF_TASKS_NON_OCCLUDING = 1 << 4;
    public static final int REQUEST_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME = 1 << 5;
    public static final int REQUEST_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN = 1 << 6;
    public static final int REQUEST_PRESERVE_LEAF_TASK_IF_RELAUNCH = 1 << 7;
    public static final int REQUEST_INTERCEPT_BACK_PRESSED_ON_TASK_ROOT = 1 << 8;
    public static final int REQUEST_TASK_FORCE_EXCLUDED_FROM_RECENTS = 1 << 9;
    public static final int REQUEST_DISABLE_PIP = 1 << 10;
    public static final int REQUEST_DISABLE_LAUNCH_ADJACENT = 1 << 11;
    public static final int REQUEST_FORCE_TRANSLUCENT = 1 << 12;

    @IntDef(flag = true, prefix = { "REQUEST_" }, value = {
            REQUEST_NONE,
            REQUEST_REPARENT_ON_DISPLAY_REMOVAL,
            REQUEST_FORCE_OPAQUE,
            REQUEST_IGNORE_INSETS,
            REQUEST_DISABLE_APP_COMPAT_ROUNDED_CORNERS,
            REQUEST_FORCE_LEAF_TASKS_NON_OCCLUDING,
            REQUEST_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME,
            REQUEST_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN,
            REQUEST_PRESERVE_LEAF_TASK_IF_RELAUNCH,
            REQUEST_INTERCEPT_BACK_PRESSED_ON_TASK_ROOT,
            REQUEST_TASK_FORCE_EXCLUDED_FROM_RECENTS,
            REQUEST_DISABLE_PIP,
            REQUEST_DISABLE_LAUNCH_ADJACENT,
            REQUEST_FORCE_TRANSLUCENT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestMask {}

    /** Request mask to indicate which properties have been requested to change. */
    @RequestMask
    private int mRequestMask = REQUEST_NONE;

    /**
     * Whether the Task should be reparented to the default display when its current display is
     * removed.
     */
    private boolean mReparentOnDisplayRemoval;

    /**
     * Whether the Task should be treated as opaque when there is any running activity child.
     */
    private boolean mForceOpaque;

    /**
     * Whether the Task should report task bounds without checking insets, such as for metrics like
     * smallestScreenWidthDp. This should be used when the Task can float on top of insets.
     */
    private boolean mIgnoreInsets;

    /**
     * Whether the Task should disable showing rounded corners for app compat purposes (e.g. when a
     * landscape app is letterboxed). Tasks can set this for better UX since sharp corners may look
     * better in some cases like in a Bubble.
     */
    private boolean mDisableAppCompatRoundedCorners;

    /**
     * Whether all leaf Tasks of this Task should be treated as non-occluding when calculating
     * visibility, unless the leaf Task is {@link #isForceOpaque()}.
     * Note: leaf Tasks below {@link TaskCreationParams#isVisibilityBarrier()} Task will still be
     * treated as invisible.
     */
    private boolean mForceLeafTasksNonOccluding;

    /**
     * Whether to reparent the leaf task when the relaunch is from home.
     * When this is set, the server side will try to reparent the leaf task to task
     * display area if the leaf task is reused during the activity launch. This
     * operation only support on the organized root task.
     */
    private boolean mReparentLeafTaskIfRelaunchFromHome;

    /**
     * Whether to allow the child tasks to have override windowing modes.
     *
     * <p>
     * When {@code true}, the system will ensure the child tasks of the given root
     * task will have no override windowing modes. That is, the override windowing
     * modes of the existing child tasks will be cleared, and the override windowing
     * modes of any newly added child tasks afterward will also be cleared. This
     * mechanism is specifically designed to be applied to a root task created by an
     * organizer only.
     */
    private boolean mDisallowOverrideWindowingModeForChildren;

    /**
     * Whether to preserve the leaf task if relaunch.
     *
     * <p>
     * When {@code preserveLeafTaskIfRelaunch} is set to {@code true}, the system
     * will prefer the candidate task's current organized root task as the launch root,
     * even if the relaunch originates from another organizer-controlled root (for
     * example, split-screen mode). This prevents the leaf task from being reparented
     * into the source task's root hierarchy.
     */
    private boolean mPreserveLeafTaskIfRelaunch;

    /**
     * Whether back press should be intercepted for the root activity of the given root
     * task or its children.
     *
     * <p>
     * When {@code true}, the system will invoke
     * {@link TaskOrganizer#onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo)},
     * providing the {@link ActivityManager.RunningTaskInfo} of the task that received
     * the back press.
     * This interception mechanism is specifically designed to be applied to the
     * root task container only.
     */
    private boolean mInterceptBackPressedOnTaskRoot;

    /**
     * Whether the task should be forcibly excluded from Recents.
     */
    private boolean mTaskForceExcludedFromRecents;

    /**
     * Whether to disable picture-in-picture for this task.
     */
    private boolean mDisablePip;

    /**
     * Whether to disable launch adjacent.
     */
    private boolean mDisableLaunchAdjacent;

    /**
     * Whether a task should be translucent. When {@code false}, the existing
     * translucent of the task applies, but when {@code true} the task will be
     * forced to be translucent.
     */
    private boolean mForceTranslucent;

    public TaskPropertiesRequest() {}

    /**
     * Sets whether the Task should be reparented to the default display when its current display
     * is removed.
     */
    public TaskPropertiesRequest setReparentOnDisplayRemoval(boolean reparentOnDisplayRemoval) {
        mRequestMask |= REQUEST_REPARENT_ON_DISPLAY_REMOVAL;
        mReparentOnDisplayRemoval = reparentOnDisplayRemoval;
        return this;
    }

    /**
     * Sets whether the Task should be treated as opaque when there is any running activity child.
     *
     * Notes: this property and {@link #setForceTranslucent(boolean)} must not be set to
     * {@code true} at the same time.
     */
    public TaskPropertiesRequest setForceOpaque(boolean forceOpaque) {
        if (forceOpaque && isForceTranslucent()) {
            throw new IllegalArgumentException(
                    "setForceOpaque and setForceTranslucent must not be both true");
        }
        mRequestMask |= REQUEST_FORCE_OPAQUE;
        mForceOpaque = forceOpaque;
        return this;
    }

    /**
     * Sets whether the Task should report task bounds without checking insets, such as for metrics
     * like smallestScreenWidthDp. This should be used when the Task can float on top of insets.
     */
    public TaskPropertiesRequest setIgnoreInsets(boolean ignoreInsets) {
        mRequestMask |= REQUEST_IGNORE_INSETS;
        mIgnoreInsets = ignoreInsets;
        return this;
    }

    /**
     * Sets whether the Task should disable showing rounded corners for app compat purposes (e.g.
     * when a landscape app is letterboxed). Tasks can set this for better UX since sharp corners
     * may look better in some cases like in a Bubble.
     */
    public TaskPropertiesRequest setDisableAppCompatRoundedCorners(
            boolean disableAppCompatRoundedCorners) {
        mRequestMask |= REQUEST_DISABLE_APP_COMPAT_ROUNDED_CORNERS;
        mDisableAppCompatRoundedCorners = disableAppCompatRoundedCorners;
        return this;
    }

    /**
     * Sets whether all leaf Tasks of this Task should be treated as non-occluding when calculating
     * visibility.
     * Note: leaf Tasks below {@link TaskCreationParams#isVisibilityBarrier()} Task will still be
     * treated as invisible.
     */
    public TaskPropertiesRequest setForceLeafTasksNonOccluding(
            boolean forceLeafTasksNonOccluding) {
        mRequestMask |= REQUEST_FORCE_LEAF_TASKS_NON_OCCLUDING;
        mForceLeafTasksNonOccluding = forceLeafTasksNonOccluding;
        return this;
    }

    /**
     * Sets whether to reparent the leaf task when the relaunch is from home.
     * When this is set, the server side will try to reparent the leaf task to task
     * display area if the leaf task is reused during the activity launch. This
     * operation only support on the organized root task.
     */
    public TaskPropertiesRequest setReparentLeafTaskIfRelaunchFromHome(
            boolean reparentLeafTaskIfRelaunchFromHome) {
        mRequestMask |= REQUEST_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME;
        mReparentLeafTaskIfRelaunchFromHome = reparentLeafTaskIfRelaunchFromHome;
        return this;
    }

    /**
     * Sets whether to allow the child tasks to have override windowing modes.
     *
     * <p>
     * When {@code true}, the system will ensure the child tasks of the given root
     * task will have no override windowing modes. That is, the override windowing
     * modes of the existing child tasks will be cleared, and the override windowing
     * modes of any newly added child tasks afterward will also be cleared. This
     * mechanism is specifically designed to be applied to a root task created by an
     * organizer only.
     */
    public TaskPropertiesRequest setDisallowOverrideWindowingModeForChildren(
            boolean disallowOverrideWindowingModeForChildren) {
        mRequestMask |= REQUEST_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN;
        mDisallowOverrideWindowingModeForChildren = disallowOverrideWindowingModeForChildren;
        return this;
    }

    /**
     * Sets whether to preserve the leaf task if relaunch.
     *
     * <p>
     * When {@code preserveLeafTaskIfRelaunch} is set to {@code true}, the system
     * will prefer the candidate task's current organized root task as the launch root,
     * even if the relaunch originates from another organizer-controlled root (for
     * example, split-screen mode). This prevents the leaf task from being reparented
     * into the source task's root hierarchy.
     */
    public TaskPropertiesRequest setPreserveLeafTaskIfRelaunch(
            boolean preserveLeafTaskIfRelaunch) {
        mRequestMask |= REQUEST_PRESERVE_LEAF_TASK_IF_RELAUNCH;
        mPreserveLeafTaskIfRelaunch = preserveLeafTaskIfRelaunch;
        return this;
    }

    /**
     * Sets whether back press should be intercepted for the root activity of the given
     * root task or its children.
     *
     * <p>
     * When {@code true}, the system will invoke
     * {@link TaskOrganizer#onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo)},
     * providing the {@link ActivityManager.RunningTaskInfo} of the task that received
     * the back press.
     * This interception mechanism is specifically designed to be applied to the
     * root task container only.
     */
    public TaskPropertiesRequest setInterceptBackPressedOnTaskRoot(
            boolean interceptBackPressedOnTaskRoot) {
        mRequestMask |= REQUEST_INTERCEPT_BACK_PRESSED_ON_TASK_ROOT;
        mInterceptBackPressedOnTaskRoot = interceptBackPressedOnTaskRoot;
        return this;
    }

    /**
     * Sets whether the task should be forcibly excluded from Recents.
     */
    public TaskPropertiesRequest setTaskForceExcludedFromRecents(
            boolean taskForceExcludedFromRecents) {
        mRequestMask |= REQUEST_TASK_FORCE_EXCLUDED_FROM_RECENTS;
        mTaskForceExcludedFromRecents = taskForceExcludedFromRecents;
        return this;
    }

    /**
     * Sets whether to disable picture-in-picture for this task.
     */
    public TaskPropertiesRequest setDisablePip(boolean disablePip) {
        mRequestMask |= REQUEST_DISABLE_PIP;
        mDisablePip = disablePip;
        return this;
    }

    /**
     * Sets whether to disable launch adjacent.
     */
    public TaskPropertiesRequest setDisableLaunchAdjacent(boolean disableLaunchAdjacent) {
        mRequestMask |= REQUEST_DISABLE_LAUNCH_ADJACENT;
        mDisableLaunchAdjacent = disableLaunchAdjacent;
        return this;
    }

    /**
     * Sets whether a task should be translucent. When {@code false}, the existing
     * translucent of the task applies, but when {@code true} the task will be
     * forced to be translucent.
     *
     * Notes: this property and {@link #setForceOpaque(boolean)} must not be set to
     * {@code true} at the same time.
     */
    public TaskPropertiesRequest setForceTranslucent(boolean forceTranslucent) {
        if (forceTranslucent && isForceOpaque()) {
            throw new IllegalArgumentException(
                    "setForceTranslucent and setForceOpaque must not be both true");
        }
        mRequestMask |= REQUEST_FORCE_TRANSLUCENT;
        mForceTranslucent = forceTranslucent;
        return this;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/android/window/TaskPropertiesRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Request mask to indicate which properties have been requested to change.
     */
    @DataClass.Generated.Member
    public @RequestMask int getRequestMask() {
        return mRequestMask;
    }

    /**
     * Whether the Task should be reparented to the default display when its current display is
     * removed.
     */
    @DataClass.Generated.Member
    public boolean isReparentOnDisplayRemoval() {
        return mReparentOnDisplayRemoval;
    }

    /**
     * Whether the Task should be treated as opaque when there is any running activity child.
     */
    @DataClass.Generated.Member
    public boolean isForceOpaque() {
        return mForceOpaque;
    }

    /**
     * Whether the Task should report task bounds without checking insets, such as for metrics like
     * smallestScreenWidthDp. This should be used when the Task can float on top of insets.
     */
    @DataClass.Generated.Member
    public boolean isIgnoreInsets() {
        return mIgnoreInsets;
    }

    /**
     * Whether the Task should disable showing rounded corners for app compat purposes (e.g. when a
     * landscape app is letterboxed). Tasks can set this for better UX since sharp corners may look
     * better in some cases like in a Bubble.
     */
    @DataClass.Generated.Member
    public boolean isDisableAppCompatRoundedCorners() {
        return mDisableAppCompatRoundedCorners;
    }

    /**
     * Whether all leaf Tasks of this Task should be treated as non-occluding when calculating
     * visibility, unless the leaf Task is {@link #isForceOpaque()}.
     * Note: leaf Tasks below {@link TaskCreationParams#isVisibilityBarrier()} Task will still be
     * treated as invisible.
     */
    @DataClass.Generated.Member
    public boolean isForceLeafTasksNonOccluding() {
        return mForceLeafTasksNonOccluding;
    }

    /**
     * Whether to reparent the leaf task when the relaunch is from home.
     * When this is set, the server side will try to reparent the leaf task to task
     * display area if the leaf task is reused during the activity launch. This
     * operation only support on the organized root task.
     */
    @DataClass.Generated.Member
    public boolean isReparentLeafTaskIfRelaunchFromHome() {
        return mReparentLeafTaskIfRelaunchFromHome;
    }

    /**
     * Whether to allow the child tasks to have override windowing modes.
     *
     * <p>
     * When {@code true}, the system will ensure the child tasks of the given root
     * task will have no override windowing modes. That is, the override windowing
     * modes of the existing child tasks will be cleared, and the override windowing
     * modes of any newly added child tasks afterward will also be cleared. This
     * mechanism is specifically designed to be applied to a root task created by an
     * organizer only.
     */
    @DataClass.Generated.Member
    public boolean isDisallowOverrideWindowingModeForChildren() {
        return mDisallowOverrideWindowingModeForChildren;
    }

    /**
     * Whether to preserve the leaf task if relaunch.
     *
     * <p>
     * When {@code preserveLeafTaskIfRelaunch} is set to {@code true}, the system
     * will prefer the candidate task's current organized root task as the launch root,
     * even if the relaunch originates from another organizer-controlled root (for
     * example, split-screen mode). This prevents the leaf task from being reparented
     * into the source task's root hierarchy.
     */
    @DataClass.Generated.Member
    public boolean isPreserveLeafTaskIfRelaunch() {
        return mPreserveLeafTaskIfRelaunch;
    }

    /**
     * Whether back press should be intercepted for the root activity of the given root
     * task or its children.
     *
     * <p>
     * When {@code true}, the system will invoke
     * {@link TaskOrganizer#onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo)},
     * providing the {@link ActivityManager.RunningTaskInfo} of the task that received
     * the back press.
     * This interception mechanism is specifically designed to be applied to the
     * root task container only.
     */
    @DataClass.Generated.Member
    public boolean isInterceptBackPressedOnTaskRoot() {
        return mInterceptBackPressedOnTaskRoot;
    }

    /**
     * Whether the task should be forcibly excluded from Recents.
     */
    @DataClass.Generated.Member
    public boolean isTaskForceExcludedFromRecents() {
        return mTaskForceExcludedFromRecents;
    }

    /**
     * Whether to disable picture-in-picture for this task.
     */
    @DataClass.Generated.Member
    public boolean isDisablePip() {
        return mDisablePip;
    }

    /**
     * Whether to disable launch adjacent.
     */
    @DataClass.Generated.Member
    public boolean isDisableLaunchAdjacent() {
        return mDisableLaunchAdjacent;
    }

    /**
     * Whether a task should be translucent. When {@code false}, the existing
     * translucent of the task applies, but when {@code true} the task will be
     * forced to be translucent.
     */
    @DataClass.Generated.Member
    public boolean isForceTranslucent() {
        return mForceTranslucent;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "TaskPropertiesRequest { " +
                "requestMask = " + mRequestMask + ", " +
                "reparentOnDisplayRemoval = " + mReparentOnDisplayRemoval + ", " +
                "forceOpaque = " + mForceOpaque + ", " +
                "ignoreInsets = " + mIgnoreInsets + ", " +
                "disableAppCompatRoundedCorners = " + mDisableAppCompatRoundedCorners + ", " +
                "forceLeafTasksNonOccluding = " + mForceLeafTasksNonOccluding + ", " +
                "reparentLeafTaskIfRelaunchFromHome = " + mReparentLeafTaskIfRelaunchFromHome + ", " +
                "disallowOverrideWindowingModeForChildren = " + mDisallowOverrideWindowingModeForChildren + ", " +
                "preserveLeafTaskIfRelaunch = " + mPreserveLeafTaskIfRelaunch + ", " +
                "interceptBackPressedOnTaskRoot = " + mInterceptBackPressedOnTaskRoot + ", " +
                "taskForceExcludedFromRecents = " + mTaskForceExcludedFromRecents + ", " +
                "disablePip = " + mDisablePip + ", " +
                "disableLaunchAdjacent = " + mDisableLaunchAdjacent + ", " +
                "forceTranslucent = " + mForceTranslucent +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(TaskPropertiesRequest other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        TaskPropertiesRequest that = (TaskPropertiesRequest) o;
        //noinspection PointlessBooleanExpression
        return true
                && mRequestMask == that.mRequestMask
                && mReparentOnDisplayRemoval == that.mReparentOnDisplayRemoval
                && mForceOpaque == that.mForceOpaque
                && mIgnoreInsets == that.mIgnoreInsets
                && mDisableAppCompatRoundedCorners == that.mDisableAppCompatRoundedCorners
                && mForceLeafTasksNonOccluding == that.mForceLeafTasksNonOccluding
                && mReparentLeafTaskIfRelaunchFromHome == that.mReparentLeafTaskIfRelaunchFromHome
                && mDisallowOverrideWindowingModeForChildren == that.mDisallowOverrideWindowingModeForChildren
                && mPreserveLeafTaskIfRelaunch == that.mPreserveLeafTaskIfRelaunch
                && mInterceptBackPressedOnTaskRoot == that.mInterceptBackPressedOnTaskRoot
                && mTaskForceExcludedFromRecents == that.mTaskForceExcludedFromRecents
                && mDisablePip == that.mDisablePip
                && mDisableLaunchAdjacent == that.mDisableLaunchAdjacent
                && mForceTranslucent == that.mForceTranslucent;
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mRequestMask;
        _hash = 31 * _hash + Boolean.hashCode(mReparentOnDisplayRemoval);
        _hash = 31 * _hash + Boolean.hashCode(mForceOpaque);
        _hash = 31 * _hash + Boolean.hashCode(mIgnoreInsets);
        _hash = 31 * _hash + Boolean.hashCode(mDisableAppCompatRoundedCorners);
        _hash = 31 * _hash + Boolean.hashCode(mForceLeafTasksNonOccluding);
        _hash = 31 * _hash + Boolean.hashCode(mReparentLeafTaskIfRelaunchFromHome);
        _hash = 31 * _hash + Boolean.hashCode(mDisallowOverrideWindowingModeForChildren);
        _hash = 31 * _hash + Boolean.hashCode(mPreserveLeafTaskIfRelaunch);
        _hash = 31 * _hash + Boolean.hashCode(mInterceptBackPressedOnTaskRoot);
        _hash = 31 * _hash + Boolean.hashCode(mTaskForceExcludedFromRecents);
        _hash = 31 * _hash + Boolean.hashCode(mDisablePip);
        _hash = 31 * _hash + Boolean.hashCode(mDisableLaunchAdjacent);
        _hash = 31 * _hash + Boolean.hashCode(mForceTranslucent);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        int flg = 0;
        if (mReparentOnDisplayRemoval) flg |= 0x2;
        if (mForceOpaque) flg |= 0x4;
        if (mIgnoreInsets) flg |= 0x8;
        if (mDisableAppCompatRoundedCorners) flg |= 0x10;
        if (mForceLeafTasksNonOccluding) flg |= 0x20;
        if (mReparentLeafTaskIfRelaunchFromHome) flg |= 0x40;
        if (mDisallowOverrideWindowingModeForChildren) flg |= 0x80;
        if (mPreserveLeafTaskIfRelaunch) flg |= 0x100;
        if (mInterceptBackPressedOnTaskRoot) flg |= 0x200;
        if (mTaskForceExcludedFromRecents) flg |= 0x400;
        if (mDisablePip) flg |= 0x800;
        if (mDisableLaunchAdjacent) flg |= 0x1000;
        if (mForceTranslucent) flg |= 0x2000;
        dest.writeInt(flg);
        dest.writeInt(mRequestMask);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ TaskPropertiesRequest(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int flg = in.readInt();
        boolean reparentOnDisplayRemoval = (flg & 0x2) != 0;
        boolean forceOpaque = (flg & 0x4) != 0;
        boolean ignoreInsets = (flg & 0x8) != 0;
        boolean disableAppCompatRoundedCorners = (flg & 0x10) != 0;
        boolean forceLeafTasksNonOccluding = (flg & 0x20) != 0;
        boolean reparentLeafTaskIfRelaunchFromHome = (flg & 0x40) != 0;
        boolean disallowOverrideWindowingModeForChildren = (flg & 0x80) != 0;
        boolean preserveLeafTaskIfRelaunch = (flg & 0x100) != 0;
        boolean interceptBackPressedOnTaskRoot = (flg & 0x200) != 0;
        boolean taskForceExcludedFromRecents = (flg & 0x400) != 0;
        boolean disablePip = (flg & 0x800) != 0;
        boolean disableLaunchAdjacent = (flg & 0x1000) != 0;
        boolean forceTranslucent = (flg & 0x2000) != 0;
        int requestMask = in.readInt();

        this.mRequestMask = requestMask;
        com.android.internal.util.AnnotationValidations.validate(
                RequestMask.class, null, mRequestMask);
        this.mReparentOnDisplayRemoval = reparentOnDisplayRemoval;
        this.mForceOpaque = forceOpaque;
        this.mIgnoreInsets = ignoreInsets;
        this.mDisableAppCompatRoundedCorners = disableAppCompatRoundedCorners;
        this.mForceLeafTasksNonOccluding = forceLeafTasksNonOccluding;
        this.mReparentLeafTaskIfRelaunchFromHome = reparentLeafTaskIfRelaunchFromHome;
        this.mDisallowOverrideWindowingModeForChildren = disallowOverrideWindowingModeForChildren;
        this.mPreserveLeafTaskIfRelaunch = preserveLeafTaskIfRelaunch;
        this.mInterceptBackPressedOnTaskRoot = interceptBackPressedOnTaskRoot;
        this.mTaskForceExcludedFromRecents = taskForceExcludedFromRecents;
        this.mDisablePip = disablePip;
        this.mDisableLaunchAdjacent = disableLaunchAdjacent;
        this.mForceTranslucent = forceTranslucent;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<TaskPropertiesRequest> CREATOR
            = new Parcelable.Creator<TaskPropertiesRequest>() {
        @Override
        public TaskPropertiesRequest[] newArray(int size) {
            return new TaskPropertiesRequest[size];
        }

        @Override
        public TaskPropertiesRequest createFromParcel(@NonNull Parcel in) {
            return new TaskPropertiesRequest(in);
        }
    };

    @DataClass.Generated(
            time = 1773208464777L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/core/java/android/window/TaskPropertiesRequest.java",
            inputSignatures = "public static final  int REQUEST_NONE\npublic static final  int REQUEST_REPARENT_ON_DISPLAY_REMOVAL\npublic static final  int REQUEST_FORCE_OPAQUE\npublic static final  int REQUEST_IGNORE_INSETS\npublic static final  int REQUEST_DISABLE_APP_COMPAT_ROUNDED_CORNERS\npublic static final  int REQUEST_FORCE_LEAF_TASKS_NON_OCCLUDING\npublic static final  int REQUEST_REPARENT_LEAF_TASK_IF_RELAUNCH_FROM_HOME\npublic static final  int REQUEST_DISALLOW_OVERRIDE_WINDOWING_MODE_FOR_CHILDREN\npublic static final  int REQUEST_PRESERVE_LEAF_TASK_IF_RELAUNCH\npublic static final  int REQUEST_INTERCEPT_BACK_PRESSED_ON_TASK_ROOT\npublic static final  int REQUEST_TASK_FORCE_EXCLUDED_FROM_RECENTS\npublic static final  int REQUEST_DISABLE_PIP\npublic static final  int REQUEST_DISABLE_LAUNCH_ADJACENT\npublic static final  int REQUEST_FORCE_TRANSLUCENT\nprivate @android.window.TaskPropertiesRequest.RequestMask int mRequestMask\nprivate  boolean mReparentOnDisplayRemoval\nprivate  boolean mForceOpaque\nprivate  boolean mIgnoreInsets\nprivate  boolean mDisableAppCompatRoundedCorners\nprivate  boolean mForceLeafTasksNonOccluding\nprivate  boolean mReparentLeafTaskIfRelaunchFromHome\nprivate  boolean mDisallowOverrideWindowingModeForChildren\nprivate  boolean mPreserveLeafTaskIfRelaunch\nprivate  boolean mInterceptBackPressedOnTaskRoot\nprivate  boolean mTaskForceExcludedFromRecents\nprivate  boolean mDisablePip\nprivate  boolean mDisableLaunchAdjacent\nprivate  boolean mForceTranslucent\npublic  android.window.TaskPropertiesRequest setReparentOnDisplayRemoval(boolean)\npublic  android.window.TaskPropertiesRequest setForceOpaque(boolean)\npublic  android.window.TaskPropertiesRequest setIgnoreInsets(boolean)\npublic  android.window.TaskPropertiesRequest setDisableAppCompatRoundedCorners(boolean)\npublic  android.window.TaskPropertiesRequest setForceLeafTasksNonOccluding(boolean)\npublic  android.window.TaskPropertiesRequest setReparentLeafTaskIfRelaunchFromHome(boolean)\npublic  android.window.TaskPropertiesRequest setDisallowOverrideWindowingModeForChildren(boolean)\npublic  android.window.TaskPropertiesRequest setPreserveLeafTaskIfRelaunch(boolean)\npublic  android.window.TaskPropertiesRequest setInterceptBackPressedOnTaskRoot(boolean)\npublic  android.window.TaskPropertiesRequest setTaskForceExcludedFromRecents(boolean)\npublic  android.window.TaskPropertiesRequest setDisablePip(boolean)\npublic  android.window.TaskPropertiesRequest setDisableLaunchAdjacent(boolean)\npublic  android.window.TaskPropertiesRequest setForceTranslucent(boolean)\nclass TaskPropertiesRequest extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genEqualsHashCode=true, genParcelable=true, genToString=true, genConstructor=false, genBuilder=false, genSetters=false, genConstDefs=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
