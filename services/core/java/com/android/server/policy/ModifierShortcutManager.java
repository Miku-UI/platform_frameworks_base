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

package com.android.server.policy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.hardware.input.AppLaunchData;
import android.hardware.input.InputGestureData;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages quick launch app intents.
 */
public class ModifierShortcutManager {
    private static final String TAG = "ModifierShortcutManager";

    private final Context mContext;
    private UserHandle mCurrentUser;
    @GuardedBy("mAppIntentCache")
    private final Map<AppLaunchData, Intent> mAppIntentCache = new HashMap<>();

    @SuppressLint("MissingPermission")
    ModifierShortcutManager(Context context, UserHandle currentUser) {
        mContext = context;
        RoleManager rm = mContext.getSystemService(RoleManager.class);
        rm.addOnRoleHoldersChangedListenerAsUser(mContext.getMainExecutor(),
                (String roleName, UserHandle user) -> {
                    synchronized (mAppIntentCache) {
                        mAppIntentCache.entrySet().removeIf(
                                entry -> {
                                    if (entry.getKey() instanceof AppLaunchData.RoleData) {
                                        return Objects.equals(
                                                ((AppLaunchData.RoleData) entry.getKey()).getRole(),
                                                roleName);
                                    }
                                    return false;
                                });
                    }
                }, UserHandle.ALL);
        mCurrentUser = currentUser;
    }

    void setCurrentUser(UserHandle newUser) {
        mCurrentUser = newUser;
        synchronized (mAppIntentCache) {
            mAppIntentCache.clear();
        }
    }

    @Nullable
    private static Intent getRoleLaunchIntent(Context context, String role) {
        Intent intent = null;
        RoleManager rm = context.getSystemService(RoleManager.class);
        PackageManager pm = context.getPackageManager();
        if (rm.isRoleAvailable(role)) {
            String rolePackage = rm.getDefaultApplication(role);
            if (rolePackage != null) {
                intent = pm.getLaunchIntentForPackage(rolePackage);
            } else {
                Log.w(TAG, "No default application for role "
                        + role + " user=" + context.getUser());
            }
        } else {
            Log.w(TAG, "Role " + role + " is not available.");
        }
        return intent;
    }

    @Nullable
    private static Intent resolveComponentNameIntent(
            Context context, String packageName, String className) {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        ComponentName componentName = new ComponentName(packageName, className);
        try {
            pm.getActivityInfo(componentName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            String[] packages = pm.canonicalToCurrentPackageNames(
                    new String[] { packageName });
            componentName = new ComponentName(packages[0], className);
            try {
                pm.getActivityInfo(componentName, flags);
            } catch (PackageManager.NameNotFoundException e1) {
                Log.w(TAG, "Unable to add bookmark: " + packageName
                        + "/" + className + " not found.");
                return null;
            }
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(componentName);
        return intent;
    }

    /**
     * @return a {@link KeyboardShortcutGroup} containing the application launch keyboard
     *         shortcuts based on provided list of shortcut data.
     */
    public KeyboardShortcutGroup getApplicationLaunchKeyboardShortcuts(int deviceId,
            List<InputGestureData> shortcutData) {
        List<KeyboardShortcutInfo> shortcuts = new ArrayList<>();
        KeyCharacterMap kcm = KeyCharacterMap.load(deviceId);
        for (InputGestureData data : shortcutData) {
            if (data.getTrigger() instanceof InputGestureData.KeyTrigger trigger) {
                KeyboardShortcutInfo info = shortcutInfoFromIntent(
                        kcm.getDisplayLabel(trigger.getKeycode()),
                        getIntentFromAppLaunchData(data.getAction().appLaunchData()),
                        (trigger.getModifierState() & KeyEvent.META_SHIFT_ON) != 0);
                if (info != null) {
                    shortcuts.add(info);
                }
            }
        }
        return new KeyboardShortcutGroup(
                mContext.getString(R.string.keyboard_shortcut_group_applications),
                shortcuts);
    }

    public Intent getIntentFromAppLaunchData(@NonNull AppLaunchData data) {
        Context context = mContext.createContextAsUser(mCurrentUser, 0);
        synchronized (mAppIntentCache) {
            Intent intent = mAppIntentCache.get(data);
            if (intent != null) {
                return intent;
            }
            if (data instanceof AppLaunchData.CategoryData) {
                intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                        ((AppLaunchData.CategoryData) data).getCategory());
            } else if (data instanceof AppLaunchData.RoleData) {
                intent = getRoleLaunchIntent(context, ((AppLaunchData.RoleData) data).getRole());
            } else if (data instanceof AppLaunchData.ComponentData) {
                AppLaunchData.ComponentData componentData = (AppLaunchData.ComponentData) data;
                intent = resolveComponentNameIntent(context, componentData.getPackageName(),
                        componentData.getClassName());
            }
            if (intent != null) {
                mAppIntentCache.put(data, intent);
            }
            return intent;
        }
    }

    /**
     * Given an intent to launch an application and the character and shift state that should
     * trigger it, return a suitable {@link KeyboardShortcutInfo} that contains the label and
     * icon for the target application.
     *
     * @param baseChar the character that triggers the shortcut
     * @param intent the application launch intent
     * @param shift whether the shift key is required to be presed.
     */
    @VisibleForTesting
    KeyboardShortcutInfo shortcutInfoFromIntent(char baseChar, Intent intent, boolean shift) {
        if (intent == null) {
            return null;
        }

        CharSequence label;
        Icon icon;
        Context context = mContext.createContextAsUser(mCurrentUser, 0);
        PackageManager pm = context.getPackageManager();
        ActivityInfo resolvedActivity = intent.resolveActivityInfo(
                pm, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolvedActivity == null) {
            return null;
        }
        boolean isResolver = com.android.internal.app.ResolverActivity.class.getName().equals(
                resolvedActivity.name);
        if (isResolver) {
            label = getIntentCategoryLabel(context,
                    intent.getSelector().getCategories().iterator().next());
            if (label == null) {
                return null;
            }
            icon = Icon.createWithResource(context, R.drawable.sym_def_app_icon);

        } else {
            label = resolvedActivity.loadLabel(pm);
            icon = Icon.createWithResource(
                    resolvedActivity.packageName, resolvedActivity.getIconResource());
        }
        int modifiers = KeyEvent.META_META_ON;
        if (shift) {
            modifiers |= KeyEvent.META_SHIFT_ON;
        }
        return new KeyboardShortcutInfo(label, icon, baseChar, modifiers);
    }

    @VisibleForTesting
    static String getIntentCategoryLabel(Context context, CharSequence category) {
        int resid;
        switch (category.toString()) {
            case Intent.CATEGORY_APP_BROWSER:
                resid = R.string.keyboard_shortcut_group_applications_browser;
                break;
            case Intent.CATEGORY_APP_CONTACTS:
                resid = R.string.keyboard_shortcut_group_applications_contacts;
                break;
            case Intent.CATEGORY_APP_EMAIL:
                resid = R.string.keyboard_shortcut_group_applications_email;
                break;
            case Intent.CATEGORY_APP_CALENDAR:
                resid = R.string.keyboard_shortcut_group_applications_calendar;
                break;
            case Intent.CATEGORY_APP_MAPS:
                resid = R.string.keyboard_shortcut_group_applications_maps;
                break;
            case Intent.CATEGORY_APP_MUSIC:
                resid = R.string.keyboard_shortcut_group_applications_music;
                break;
            case Intent.CATEGORY_APP_MESSAGING:
                resid = R.string.keyboard_shortcut_group_applications_sms;
                break;
            case Intent.CATEGORY_APP_CALCULATOR:
                resid = R.string.keyboard_shortcut_group_applications_calculator;
                break;
            case Intent.CATEGORY_APP_FILES:
                resid = R.string.keyboard_shortcut_group_applications_files;
                break;
            default:
                Log.e(TAG, ("No label for app category " + category));
                return null;
        }
        return context.getString(resid);
    }
}
