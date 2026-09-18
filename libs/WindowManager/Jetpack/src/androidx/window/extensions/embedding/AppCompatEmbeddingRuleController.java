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

package androidx.window.extensions.embedding;

import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;
import static android.content.res.Configuration.TOUCHSCREEN_FINGER;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.WindowManager.PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.AppGlobals;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.hardware.input.InputManagerGlobal;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.TypedValue;
import android.view.InputDevice;
import android.window.TaskFragmentOrganizer;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

import java.util.Set;

public class AppCompatEmbeddingRuleController {
    private static final int DEFAULT_MIN_DP = 600;

    /**
     * Whether we enable the virtual gamepad rule. If this is false, the virtual gamepad is disabled
     * for the entire app session.
     */
    @VisibleForTesting
    static boolean sIsVirtualGamepadRuleEnabled;

    /**
     * Whether we have seen gamepad user opt-out in the current session.
     *
     * If we have ever seen user opt-out in the current session, we consider the gamepad to be
     * disabled for the entire session. Dynamic re-enablement of the gamepad within the same session
     * is not supported.
     */
    @VisibleForTesting
    static boolean sIsVirtualGamepadOptOutSeenInSession;

    /** Initializes the controller. Must be called before any other functions. */
    public static void init(@NonNull Context context) {
        sIsVirtualGamepadRuleEnabled =
                isVirtualGamepadRuleEnabled(context, context.getPackageName(), context.getUserId());
    }

    /** Loads {@link EmbeddingRule}s if any app compat override rules apply to the app. */
    @NonNull
    public static Set<EmbeddingRule> loadAppCompatRules(@NonNull Context context) {
        final Set<EmbeddingRule> rules = new ArraySet<>();

        if (!isOverrideAllowed(context)) {
            return rules;
        }

        if (sIsVirtualGamepadRuleEnabled) {
            final EmbeddingRule rule = createVirtualGamepadOverrideRule(
                    context.getResources().getString(R.string.config_virtual_gamepad_package_name),
                    context.getResources().getString(R.string.config_virtual_gamepad_activity_name),
                    defaultMinSize(context),
                    context.getPackageName(),
                    context.getUserId(),
                    context.getApplicationInfo().uid);
            if (rule != null) {
                rules.add(rule);
            }
        }

        return rules;
    }

    /** Loads the virtual gamepad override embedding rule. */
    @VisibleForTesting
    @Nullable
    public static EmbeddingRule createVirtualGamepadOverrideRule(
            @NonNull String packageName, @NonNull String activityName, int minSizePx,
            @NonNull String selfPackageName, int userId, int uid) {
        if (packageName.isEmpty() || activityName.isEmpty()) {
            return null;
        }

        final Intent placeholderIntent = new Intent();
        placeholderIntent.setClassName(packageName, activityName);
        placeholderIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, selfPackageName);
        placeholderIntent.putExtra(Intent.EXTRA_UID, uid);

        final SplitAttributes defaultAttributes = new SplitAttributes.Builder()
                .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .build();

        return new SplitPlaceholderRule.Builder(
                placeholderIntent,
                (androidx.window.extensions.core.util.function.Predicate<Activity>)
                        activity -> activity.getResources().getConfiguration().touchscreen
                                == TOUCHSCREEN_FINGER
                                && isVirtualGamepadEnabled(selfPackageName, userId)
                                // Do not enable the gamepad if the game is in a non-embedding
                                // multi-window mode such as system split screen.
                                && (!isInMultiWindowModeExcludingEmbedding(activity)),
                intent -> true,
                parentMetrics -> parentMetrics.getBounds().height() >= minSizePx
                        && parentMetrics.getBounds().width() >= minSizePx)
                .setFinishPrimaryWithPlaceholder(SplitRule.FINISH_NEVER)
                .setDefaultSplitAttributes(defaultAttributes)
                .build();
    }

    /**
     * Updates the placeholder container upon creation.
     *
     * For gamepad overrides, this pins the placeholder container to ensure isolated navigation and
     * always-on-top behavior.
     */
    public static void updatePlaceholderContainer(
            @NonNull SplitPresenter presenter,
            @NonNull TaskFragmentContainer container,
            @NonNull WindowContainerTransaction wct) {
        if (sIsVirtualGamepadRuleEnabled) {
            // Ensure isolated navigation and always-on-top behavior of the gamepad placeholder.
            presenter.setTaskFragmentPinned(wct, container, true /* pinned */);
        }
    }

    private static int defaultMinSize(Context context) {
        // Convert the dps to pixels, based on density scale
        return (int) TypedValue.applyDimension(
                COMPLEX_UNIT_DIP,
                DEFAULT_MIN_DP,
                context.getResources().getDisplayMetrics());
    }

    private static boolean isOverrideAllowed(@NonNull Context context) {
        try {
            // If the app uses activity embedding by itself, we should not apply any override rules
            // to avoid conflicts.
            final PackageManager.Property property = context.getPackageManager().getProperty(
                    PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED,
                    context.getPackageName());
            return !property.getBoolean();
        } catch (PackageManager.NameNotFoundException e) {
            // If not defined, the app is not using embedding.
            return true;
        }
    }


    /**
     * Returns whether we should enable the gamepad rule.
     *
     * This is checked at the beginning of the app process. If this is false, we disable the gamepad
     * for the entire app session.
     *
     * This returns false if (1) the app opts out from the gamepad, (2) the gamepad compat change is
     * not enabled, or (3) the user opts out from the gamepad. Note that user opt-in will only take
     * effect when the game is launched next time.
     */
    @VisibleForTesting
    static boolean isVirtualGamepadRuleEnabled(@NonNull Context context,
            @NonNull String packageName, int userId) {
        if (!CompatChanges.isChangeEnabled(OVERRIDE_ENABLE_VIRTUAL_GAMEPAD)) {
            return false;
        }
        if (!isVirtualGamepadAllowedByApp(context)) {
            return false;
        }
        int userOption = getVirtualGamepadUserOption(packageName, userId);
        if (userOption == PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_OPT_OUT) {
            sIsVirtualGamepadOptOutSeenInSession = true;
            return false;
        }
        return true;
    }

    /**
     * Returns true if we need to enable the virtual gamepad.
     *
     * This method checks the runtime properties that affects gamepad availability in addition to
     * the static properties checked in {@link #isVirtualGamepadRuleEnabled(Context, String, int)}.
     */
    @VisibleForTesting
    static boolean isVirtualGamepadEnabled(@NonNull String packageName, int userId) {
        if (!sIsVirtualGamepadRuleEnabled) {
            return false;
        }
        if (sIsVirtualGamepadOptOutSeenInSession) {
            return false;
        }
        int userOption = getVirtualGamepadUserOption(packageName, userId);
        if (userOption == PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_OPT_OUT) {
            sIsVirtualGamepadOptOutSeenInSession = true;
            return false;
        }
        return !isPhysicalGamepadConnected();
    }

    private static boolean isPhysicalGamepadConnected() {
        final InputManagerGlobal inputManager = InputManagerGlobal.getInstance();
        for (int deviceId : inputManager.getInputDeviceIds()) {
            final InputDevice device = inputManager.getInputDevice(deviceId);
            if (device != null && !device.isVirtual() && isGamepad(device)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGamepad(@NonNull InputDevice device) {
        final int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private static boolean isVirtualGamepadAllowedByApp(@NonNull Context context) {
        try {
            final PackageManager.Property property = context.getPackageManager().getProperty(
                    PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE,
                    context.getPackageName());
            return property.getBoolean();
        } catch (PackageManager.NameNotFoundException e) {
            // Default to true if the property is not specified.
            return true;
        }
    }

    @PackageManager.VirtualGamepadUserOption
    private static int getVirtualGamepadUserOption(@NonNull String packageName, int userId) {
        final IPackageManager pm = AppGlobals.getPackageManager();
        try {
            return pm.getVirtualGamepadUserOption(packageName, userId);
        } catch (RemoteException e) {
            return PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_UNSET;
        }
    }

    /**
     * Returns {@code true} if the {@param activity} is in multi-window mode and it is not embedded.
     *
     * This is used to ensure that the gamepad feature is not enabled when the game is in system
     * multi-window modes such as split screen.
     *
     * TODO(b/489825790) Better handling of gamepad in system split screen. Issues may happen if
     * the game enters system split screen where the window size is large enough to not dismiss
     * the placeholder. Practically, this doesn't happen on known devices.
     */
    @VisibleForTesting
    static boolean isInMultiWindowModeExcludingEmbedding(@NonNull Activity activity) {
        return activity.isInMultiWindowMode()
                && !TaskFragmentOrganizer.isActivityEmbedded(activity);
    }
}
