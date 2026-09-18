/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.companion.virtual;

import static android.content.pm.ActivityInfo.FLAG_CAN_DISPLAY_ON_REMOTE_DEVICES;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.WindowConfiguration;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.window.DisplayWindowPolicyController;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.BlockedAppStreamingActivity;
import com.android.modules.expresslog.Counter;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A controller to control the policies of the windows that can be displayed on the virtual display.
 */
final class GenericWindowPolicyController extends DisplayWindowPolicyController {

    private static final String TAG = "GenericWindowPolicyController";

    private static final ComponentName BLOCKED_APP_STREAMING_COMPONENT =
            new ComponentName("android", BlockedAppStreamingActivity.class.getName());
    private static final int FLAG_NONE = 0;

    /** Interface to react to activity changes on the virtual display. */
    public interface ActivityListener {

        /** Called when the top activity changes. */
        void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity,
                @UserIdInt int userId);

        /** Called when the display becomes empty. */
        void onDisplayEmpty(int displayId);

        /** Called when an activity is blocked.*/
        void onActivityLaunchBlocked(int displayId, @NonNull ActivityInfo activityInfo,
                @Nullable IntentSender intentSender);

        /** Called when a secure window shows on the virtual display. */
        void onSecureWindowShown(int displayId, @NonNull ComponentName componentName,
                int uid);

        /**
         * Called when a secure window is no longer shown on the virtual display.
         *
         * <p>This could mean that either an activity (previously with secure content) doesn't show
         * secure content anymore, or a different activity with insecure content is launched on the
         * display.</p>
         */
        void onSecureWindowHidden(int displayId);

        /** Returns true when an intent should be intercepted */
        boolean shouldInterceptIntent(@NonNull Intent intent);

        /**
         * Called when the set of running apps on this display changes.
         *
         * @param uidPackagePairs Set of pairs of UID and package name corresponding to all
         *   activities currently present on the display.
         */
        void onRunningAppsChanged(int displayId,
                @NonNull ArraySet<Pair<Integer, String>> uidPackagePairs);

        /**
         * Called when an activity launch is requested on the given display for the given user.
         *
         * @param displayId The display ID on which the activity launch is requested.
         * @param componentName The component name of the activity whose launch is requested.
         * @param userId The user ID associated with the activity whose launch is requested.
         */
        void onActivityLaunchRequested(int displayId, @NonNull ComponentName componentName,
                @UserIdInt int userId);
    }

    /**
     * If required, allow the secure activity to display on remote device since
     * {@link android.os.Build.VERSION_CODES#TIRAMISU}.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public static final long ALLOW_SECURE_ACTIVITY_DISPLAY_ON_REMOTE_DEVICE = 201712607L;
    @NonNull
    private final AttributionSource mAttributionSource;
    @NonNull
    private final ArraySet<UserHandle> mAllowedUsers;
    @GuardedBy("mGenericWindowPolicyControllerLock")
    private boolean mActivityLaunchAllowedByDefault;
    @NonNull
    @GuardedBy("mGenericWindowPolicyControllerLock")
    private final ArraySet<ComponentName> mActivityPolicyExemptions;
    @NonNull
    @GuardedBy("mGenericWindowPolicyControllerLock")
    private final ArraySet<String> mActivityPolicyPackageExemptions;
    private final boolean mCrossTaskNavigationAllowedByDefault;
    @NonNull
    private final ArraySet<ComponentName> mCrossTaskNavigationExemptions;
    @NonNull
    private final Object mGenericWindowPolicyControllerLock = new Object();

    // Do not access mDisplayId directly, instead use waitAndGetDisplayId()
    private int mDisplayId = Display.INVALID_DISPLAY;
    private boolean mIsSecureDisplay = false;
    private final CountDownLatch mDisplayIdSetLatch = new CountDownLatch(1);

    // Used for detecting changes in the window flags.
    @GuardedBy("mGenericWindowPolicyControllerLock")
    private final WindowFlagsTracker mWindowFlagsTracker = new WindowFlagsTracker();

    @NonNull
    @GuardedBy("mGenericWindowPolicyControllerLock")
    private final ArraySet<Pair<Integer, String>> mRunningUidPackagePairs = new ArraySet<>();
    @NonNull private final ActivityListener mActivityListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Set<String> mDisplayCategories;

    @GuardedBy("mGenericWindowPolicyControllerLock")
    private boolean mShowTasksInHostDeviceRecents;
    @Nullable private final ComponentName mCustomHomeComponent;

    private final boolean mLocalDeviceOnly;

    /**
     * Creates a window policy controller that is generic to the different use cases of virtual
     * device.
     *
     * @param attributionSource The AttributionSource of the VirtualDevice owner application.
     * @param allowedUsers The set of users that are allowed to stream in this display.
     * @param activityLaunchAllowedByDefault Whether activities are default allowed to be launched
     *   or blocked.
     * @param activityPolicyExemptions The set of activities explicitly exempt from the default
     *   activity policy.
     * @param activityPolicyPackageExemptions The set of packages whose activities are explicitly
     *   exempt from the default activity policy.
     * @param crossTaskNavigationAllowedByDefault Whether cross task navigations are allowed by
     *   default or not.
     * @param crossTaskNavigationExemptions The set of components explicitly exempt from the default
     *   navigation policy.
     * @param activityListener Activity listener to listen for activity changes.
     * @param showTasksInHostDeviceRecents whether to show activities in recents on the host device.
     * @param customHomeComponent The component acting as a home activity on the virtual display. If
     *   {@code null}, then the system-default secondary home activity will be used. This is only
     *   applicable to displays that support home activities, i.e. they're created with the relevant
     *   virtual display flag.
     * @param localDeviceOnly Whether it is guaranteed that the display contents will never be
     *   streamed to a remote device.
     */
    GenericWindowPolicyController(
            @NonNull AttributionSource attributionSource,
            @NonNull ArraySet<UserHandle> allowedUsers,
            boolean activityLaunchAllowedByDefault,
            @NonNull Set<ComponentName> activityPolicyExemptions,
            @NonNull Set<String> activityPolicyPackageExemptions,
            boolean crossTaskNavigationAllowedByDefault,
            @NonNull Set<ComponentName> crossTaskNavigationExemptions,
            @NonNull ActivityListener activityListener,
            @NonNull Set<String> displayCategories,
            boolean showTasksInHostDeviceRecents,
            @Nullable ComponentName customHomeComponent,
            boolean localDeviceOnly) {
        super();
        mAttributionSource = attributionSource;
        mAllowedUsers = allowedUsers;
        mActivityLaunchAllowedByDefault = activityLaunchAllowedByDefault;
        mActivityPolicyExemptions = new ArraySet<>(activityPolicyExemptions);
        mActivityPolicyPackageExemptions = new ArraySet<>(activityPolicyPackageExemptions);
        mCrossTaskNavigationAllowedByDefault = crossTaskNavigationAllowedByDefault;
        mCrossTaskNavigationExemptions = new ArraySet<>(crossTaskNavigationExemptions);
        mActivityListener = activityListener;
        mDisplayCategories = displayCategories;
        mShowTasksInHostDeviceRecents = showTasksInHostDeviceRecents;
        mCustomHomeComponent = customHomeComponent;
        mLocalDeviceOnly = localDeviceOnly;
    }

    /**
     * Expected to be called once this object is associated with a newly created display.
     */
    void setDisplayId(int displayId, boolean isSecureDisplay) {
        mDisplayId = displayId;
        mIsSecureDisplay = isSecureDisplay;
        mDisplayIdSetLatch.countDown();
    }

    private int waitAndGetDisplayId() {
        try {
            if (!mDisplayIdSetLatch.await(10, TimeUnit.SECONDS)) {
                Slog.e(TAG, "Timed out while waiting for GWPC displayId to be set.");
                return INVALID_DISPLAY;
            }
        } catch (InterruptedException e) {
            Slog.e(TAG, "Interrupted while waiting for GWPC displayId to be set.");
            return INVALID_DISPLAY;
        }
        return mDisplayId;
    }

    /**
     * Set whether to show activities in recents on the host device.
     */
    void setShowInHostDeviceRecents(boolean showInHostDeviceRecents) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mShowTasksInHostDeviceRecents = showInHostDeviceRecents;
        }
    }

    boolean isActivityLaunchAllowedByDefault() {
        synchronized (mGenericWindowPolicyControllerLock) {
            return mActivityLaunchAllowedByDefault;
        }
    }

    void setActivityLaunchDefaultAllowed(boolean activityLaunchDefaultAllowed) {
        synchronized (mGenericWindowPolicyControllerLock) {
            if (mActivityLaunchAllowedByDefault != activityLaunchDefaultAllowed) {
                mActivityPolicyExemptions.clear();
                mActivityPolicyPackageExemptions.clear();
            }
            mActivityLaunchAllowedByDefault = activityLaunchDefaultAllowed;
        }
    }

    void addActivityPolicyExemption(@NonNull ComponentName componentName) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mActivityPolicyExemptions.add(componentName);
        }
    }

    void removeActivityPolicyExemption(@NonNull ComponentName componentName) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mActivityPolicyExemptions.remove(componentName);
        }
    }

    void addActivityPolicyExemption(@NonNull String packageName) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mActivityPolicyPackageExemptions.add(packageName);
        }
    }

    void removeActivityPolicyExemption(@NonNull String packageName) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mActivityPolicyPackageExemptions.remove(packageName);
        }
    }

    @Override
    public boolean canActivityBeLaunched(@NonNull ActivityInfo activityInfo,
            @Nullable Intent intent, @WindowConfiguration.WindowingMode int windowingMode,
            int launchingFromDisplayId, boolean isNewTask, boolean isResultExpected,
            @Nullable Supplier<IntentSender> intentSender) {
        mHandler.post(() -> mActivityListener.onActivityLaunchRequested(
                mDisplayId, activityInfo.getComponentName(),
                UserHandle.getUserId(activityInfo.applicationInfo.uid)));

        if (intent != null && mActivityListener.shouldInterceptIntent(intent)) {
            logActivityLaunchBlocked("Virtual device intercepting intent");
            return false;
        }
        if (!canContainActivity(activityInfo, windowingMode, launchingFromDisplayId,
                isNewTask)) {
            // If the sender of the original intent expects a result to be reported, do not pass the
            // intent sender to the client callback. As the launch is blocked, the caller already
            // received that activity result.
            notifyActivityBlocked(activityInfo, isResultExpected ? null : intentSender);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContainActivity(@NonNull ActivityInfo activityInfo,
            @WindowConfiguration.WindowingMode int windowingMode, int launchingFromDisplayId,
            boolean isNewTask) {
        if (!mIsSecureDisplay && (activityInfo.flags & FLAG_CAN_DISPLAY_ON_REMOTE_DEVICES) == 0
                && !mLocalDeviceOnly) {
            logActivityLaunchBlocked("Display requires android:canDisplayOnRemoteDevices=true");
            return false;
        }
        final UserHandle activityUser =
                UserHandle.getUserHandleForUid(activityInfo.applicationInfo.uid);
        if (!activityUser.isSystem() && !mAllowedUsers.contains(activityUser)) {
            logActivityLaunchBlocked("Activity launch disallowed from user " + activityUser);
            return false;
        }
        final ComponentName activityComponent = activityInfo.getComponentName();
        if (BLOCKED_APP_STREAMING_COMPONENT.equals(activityComponent) && activityUser.isSystem()) {
            // The error dialog alerting users that streaming is blocked is always allowed.
            return true;
        }
        if (!activityMatchesDisplayCategory(activityInfo)) {
            logActivityLaunchBlocked("The activity's required display category '"
                    + activityInfo.requiredDisplayCategory
                    + "' not found on virtual display with the following categories: "
                    + mDisplayCategories);
            return false;
        }
        if (!isAllowedByPolicy(activityComponent)) {
            logActivityLaunchBlocked("Activity launch disallowed by policy: "
                    + activityComponent);
            return false;
        }
        if (isNewTask && launchingFromDisplayId != DEFAULT_DISPLAY
                && !isAllowedByPolicy(mCrossTaskNavigationAllowedByDefault,
                        mCrossTaskNavigationExemptions, activityComponent)) {
            logActivityLaunchBlocked("Cross task navigation disallowed by policy: "
                    + activityComponent);
            return false;
        }

        return true;
    }

    private void logActivityLaunchBlocked(String reason) {
        Slog.d(TAG, "Virtual device activity launch disallowed on display "
                + waitAndGetDisplayId() + ", reason: " + reason);
    }

    @Override
    @SuppressWarnings("AndroidFrameworkRequiresPermission")
    public boolean keepActivityOnWindowFlagsChanged(ActivityInfo activityInfo, int windowFlags,
            int systemWindowFlags) {
        final int displayId = waitAndGetDisplayId();
        if (displayId != INVALID_DISPLAY) {
            final ComponentName componentName = activityInfo.getComponentName();
            final ComponentName topComponentName;
            final int currentWindowFlags;
            synchronized (mGenericWindowPolicyControllerLock) {
                topComponentName = mWindowFlagsTracker.getTopComponentName();
                if (Objects.equals(componentName, topComponentName)) {
                    currentWindowFlags = mWindowFlagsTracker.getCurrentWindowFlags();
                    mWindowFlagsTracker.setWindowFlagsForComponentName(componentName, windowFlags);
                } else {
                    currentWindowFlags = FLAG_NONE;
                }
            }
            if (Objects.equals(componentName, topComponentName)) {
                detectSecureWindowStatusChange(windowFlags, currentWindowFlags, componentName,
                        activityInfo.applicationInfo.uid, displayId);
            }
        }

        if (!CompatChanges.isChangeEnabled(ALLOW_SECURE_ACTIVITY_DISPLAY_ON_REMOTE_DEVICE,
                activityInfo.packageName,
                UserHandle.getUserHandleForUid(activityInfo.applicationInfo.uid))) {
            // TODO(b/201712607): Add checks for the apps that use SurfaceView#setSecure.
            if (isSecureContent(windowFlags)
                    || (systemWindowFlags & SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS) != 0) {
                notifyActivityBlocked(activityInfo, /* intentSender= */ null);
                return false;
            }
        }

        return true;
    }

    // TODO(b/487544125): Support multiple top activity changes for desktop mode.
    @Override
    public void onTopActivityChanged(ComponentName topActivity, int uid, @UserIdInt int userId) {
        final int displayId = waitAndGetDisplayId();
        // Don't send onTopActivityChanged() callback when topActivity is null because it's defined
        // as @NonNull in ActivityListener interface. Sends onDisplayEmpty() callback instead when
        // there is no activity running on the virtual display.
        if (topActivity == null || displayId == INVALID_DISPLAY) {
            return;
        }

        // Post callback on the main thread so it doesn't block activity launching
        mHandler.post(() ->
                mActivityListener.onTopActivityChanged(displayId, topActivity, userId));

        final int windowFlagsForComponentName;
        final int currentWindowFlags;
        synchronized (mGenericWindowPolicyControllerLock) {
            windowFlagsForComponentName =
                    mWindowFlagsTracker.getWindowFlagsForComponentName(topActivity);
            currentWindowFlags = mWindowFlagsTracker.getCurrentWindowFlags();
            mWindowFlagsTracker.setTopComponentName(topActivity);
        }
        detectSecureWindowStatusChange(windowFlagsForComponentName, currentWindowFlags,
                topActivity, uid, displayId);
    }

    @Override
    public void onRunningAppsChanged(ArraySet<Pair<Integer, String>> uidPackagePairs) {
        synchronized (mGenericWindowPolicyControllerLock) {
            mRunningUidPackagePairs.clear();
            mRunningUidPackagePairs.addAll(uidPackagePairs);
            int displayId = waitAndGetDisplayId();
            if (displayId == INVALID_DISPLAY) {
                return;
            }
            mHandler.post(() ->
                    mActivityListener.onRunningAppsChanged(displayId, uidPackagePairs));
            if (mRunningUidPackagePairs.isEmpty()) {
                mWindowFlagsTracker.clear();
                mHandler.post(() -> mActivityListener.onDisplayEmpty(displayId));
            }
        }
    }

    @Override
    public boolean canShowTasksInHostDeviceRecents() {
        synchronized (mGenericWindowPolicyControllerLock) {
            return mShowTasksInHostDeviceRecents;
        }
    }

    @Override
    public @Nullable ComponentName getCustomHomeComponent() {
        return mCustomHomeComponent;
    }

    /**
     * Returns true if an app with the given UID has an activity running on the virtual display for
     * this controller.
     */
    boolean containsUid(int uid) {
        synchronized (mGenericWindowPolicyControllerLock) {
            for (int i = 0; i < mRunningUidPackagePairs.size(); ++i) {
                if (mRunningUidPackagePairs.valueAt(i).first == uid) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean activityMatchesDisplayCategory(ActivityInfo activityInfo) {
        if (mDisplayCategories.isEmpty()) {
            return activityInfo.requiredDisplayCategory == null;
        }
        return activityInfo.requiredDisplayCategory != null
                    && mDisplayCategories.contains(activityInfo.requiredDisplayCategory);
    }

    private void notifyActivityBlocked(
            @NonNull ActivityInfo activityInfo, @Nullable Supplier<IntentSender> intentSender) {
        int displayId = waitAndGetDisplayId();
        if (displayId != INVALID_DISPLAY) {
            mActivityListener.onActivityLaunchBlocked(displayId, activityInfo,
                    intentSender == null ? null : intentSender.get());
        }
        Counter.logIncrementWithUid(
                "virtual_devices.value_activity_blocked_count",
                mAttributionSource.getUid());
    }

    private boolean isAllowedByPolicy(ComponentName component) {
        synchronized (mGenericWindowPolicyControllerLock) {
            if (mActivityPolicyExemptions.contains(component)
                    || mActivityPolicyPackageExemptions.contains(component.getPackageName())) {
                return !mActivityLaunchAllowedByDefault;
            }
            return mActivityLaunchAllowedByDefault;
        }
    }

    private void detectSecureWindowStatusChange(int newWindowFlags, int previousWindowFlags,
            @NonNull ComponentName componentName, int uid, int displayId) {
        if (previousWindowFlags == newWindowFlags) {
            return;
        }

        // The callback is fired only when window flags have changed, to let the VirtualDevice owner
        // know that the secure/insecure state of the content on the virtual display has changed.
        // Post callback on the main thread, so it doesn't block activity launching.
        // TODO(b/487544125): Support multiple secure surfaces shown or hidden in succession in
        // desktop mode.
        if (isSecureContent(newWindowFlags) && !isSecureContent(previousWindowFlags)) {
            mHandler.post(
                    () -> mActivityListener.onSecureWindowShown(displayId, componentName, uid));
        } else if (!isSecureContent(newWindowFlags) && isSecureContent(previousWindowFlags)) {
            mHandler.post(() -> mActivityListener.onSecureWindowHidden(displayId));
        }
    }

    private static boolean isAllowedByPolicy(boolean allowedByDefault,
            Set<ComponentName> exemptions, ComponentName component) {
        // Either allowed and the exemptions do not contain the component,
        // or disallowed and the exemptions contain the component.
        return allowedByDefault != exemptions.contains(component);
    }

    private static boolean isSecureContent(int windowFlags) {
        return (windowFlags & FLAG_SECURE) != 0;
    }

    private static final class WindowFlagsTracker {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final Map<ComponentName, Integer> mComponentNameToWindowFlagsMap = new ArrayMap<>();
        @GuardedBy("mLock")
        @Nullable
        private ComponentName mTopComponentName = null;

        int getWindowFlagsForComponentName(@NonNull ComponentName componentName) {
            synchronized (mLock) {
                return getWindowFlagsForComponentNameLocked(componentName);
            }
        }

        int getCurrentWindowFlags() {
            synchronized (mLock) {
                if (mTopComponentName == null) {
                    return FLAG_NONE;
                }
                return getWindowFlagsForComponentNameLocked(mTopComponentName);
            }
        }

        void setWindowFlagsForComponentName(@NonNull ComponentName componentName, int windowFlags) {
            synchronized (mLock) {
                mComponentNameToWindowFlagsMap.put(componentName, windowFlags);
            }
        }

        @Nullable
        ComponentName getTopComponentName() {
            synchronized (mLock) {
                return mTopComponentName;
            }
        }

        void setTopComponentName(@NonNull ComponentName componentName) {
            synchronized (mLock) {
                mTopComponentName = componentName;
            }
        }

        void clear() {
            synchronized (mLock) {
                mComponentNameToWindowFlagsMap.clear();
                mTopComponentName = null;
            }
        }

        @GuardedBy("mLock")
        private int getWindowFlagsForComponentNameLocked(@NonNull ComponentName componentName) {
            return mComponentNameToWindowFlagsMap.getOrDefault(componentName, FLAG_NONE);
        }
    }
}
