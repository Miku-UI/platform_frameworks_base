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

package com.android.server.inputmethod;

import static android.content.Context.DEVICE_ID_DEFAULT;

import static com.android.server.inputmethod.InputMethodUtils.NOT_A_SUBTYPE_INDEX;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.SpecialUsers.CanBeCURRENT;
import android.annotation.SpecialUsers.CanBeALL;
import android.annotation.UserIdInt;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Slog;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.inputmethod.SoftInputShowHideReason;


/**
 * A controller for enabling, disabling, setting, and resetting IMEs.
 * This class is intended to be called by ImeShellCommandController and
 * InputMethodManager's TestApis.
 * TODO(b/450277076): Consolidate IME APIs from IMMS into here.
 */
final class EnabledInputMethodsController {
    private static final String TAG = EnabledInputMethodsController.class.getSimpleName();

    @NonNull
    private final InputMethodManagerService mService;

    EnabledInputMethodsController(@NonNull InputMethodManagerService service) {
        mService = service;
    }

    /**
     * Enables the specified input method for the given user(s).
     * @param imeId The ID of the input method to enable.
     * @param userId The user ID. Can be {@link android.os.UserHandle#USER_CURRENT} or
     *               {@link android.os.UserHandle#USER_ALL}.
     * @return {@code true} if the input method was already enabled for at least one of the
     *         specified users, {@code false} otherwise.
     */
    @IInputMethodManagerImpl.PermissionVerified(allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.TEST_INPUT_METHOD,
            Manifest.permission.WRITE_SECURE_SETTINGS})
    @GuardedBy("ImfLock.class")
    public boolean enableInputMethodForTesting(@NonNull String imeId,
            @CanBeALL @CanBeCURRENT int userId) {
        boolean alreadyEnabled = false;
        final int[] userIds = InputMethodUtils.resolveUserId(userId,
                mService.mCurrentImeUserId, null);
        for (int id : userIds) {
            alreadyEnabled |= mService.setInputMethodEnabledLocked(imeId, true /* enabled */, id);
        }
        return alreadyEnabled;
    }

    /**
     * Disables the specified input method for the given user(s).
     * @param imeId The ID of the input method to disable.
     * @param userId The user ID. Can be {@link android.os.UserHandle#USER_CURRENT} or
     *               {@link android.os.UserHandle#USER_ALL}.
     * @return {@code true} if the input method was already enabled for at least one of the
     *         specified users, {@code false} otherwise.
     */
    @IInputMethodManagerImpl.PermissionVerified(allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.TEST_INPUT_METHOD,
            Manifest.permission.WRITE_SECURE_SETTINGS})
    @GuardedBy("ImfLock.class")
    public boolean disableInputMethodForTesting(@NonNull String imeId,
            @CanBeALL @CanBeCURRENT @UserIdInt int userId) {
        boolean alreadyEnabled = false;
        final int[] userIds = InputMethodUtils.resolveUserId(userId,
                mService.mCurrentImeUserId, null);
        for (int id : userIds) {
            alreadyEnabled |= mService.setInputMethodEnabledLocked(imeId, false /* enabled */, id);
        }
        return alreadyEnabled;
    }

    @IInputMethodManagerImpl.PermissionVerified(allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.TEST_INPUT_METHOD,
            Manifest.permission.WRITE_SECURE_SETTINGS})
    @GuardedBy("ImfLock.class")
    public boolean setInputMethodForTesting(@NonNull String imeId,
            @CanBeALL @CanBeCURRENT @UserIdInt int userId) {
        boolean hasFailed = false;
        final int[] userIds = InputMethodUtils.resolveUserId(userId,
                mService.mCurrentImeUserId, null);
        for (int id : userIds) {
            hasFailed |= setInputMethodInternal(imeId, id);
        }
        return hasFailed;
    }

    @GuardedBy("ImfLock.class")
    private boolean setInputMethodInternal(@NonNull String imeId, @UserIdInt int userId){
        boolean failedToSelectUnknownIme = !mService.switchToInputMethodLocked(imeId,
                NOT_A_SUBTYPE_INDEX, userId);
        if (!failedToSelectUnknownIme) {
            // Workaround for b/354782333.
            final InputMethodSettings settings = InputMethodSettingsRepository.get(userId);
            final var bindingController = mService.getInputMethodBindingController(userId);
            final int deviceId = bindingController.getDeviceId();
            final String settingsValue;
            if (deviceId == DEVICE_ID_DEFAULT) {
                settingsValue = settings.getSelectedInputMethod();
            } else {
                settingsValue = settings.getSelectedDefaultDeviceInputMethod();
            }
            if (!TextUtils.equals(settingsValue, imeId)) {
                Slog.w(TAG, "DEFAULT_INPUT_METHOD=" + settingsValue
                        + " is not updated. Fixing it up to " + imeId
                        + " See b/354782333.");
                if (deviceId == DEVICE_ID_DEFAULT) {
                    settings.putSelectedInputMethod(imeId);
                } else {
                    settings.putSelectedDefaultDeviceInputMethod(imeId);
                }
            }
        }
        return failedToSelectUnknownIme;
    }

    @IInputMethodManagerImpl.PermissionVerified(allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.TEST_INPUT_METHOD,
            Manifest.permission.WRITE_SECURE_SETTINGS})
    @GuardedBy("ImfLock.class")
    public void resetInputMethodsForTesting(@CanBeALL @CanBeCURRENT @UserIdInt int userId) {
        final int[] userIds = InputMethodUtils.resolveUserId(userId,
                mService.mCurrentImeUserId, null);
        for (int id : userIds) {
            resetInputMethodsInternal(id);
        }
    }

    @GuardedBy("ImfLock.class")
    private void resetInputMethodsInternal(@UserIdInt int userId){
        final InputMethodSettings settings = InputMethodSettingsRepository.get(userId);
        final var userData = mService.getUserData(userId);
        final var statsToken = mService.createStatsTokenForFocusedClient(false /* show */,
                SoftInputShowHideReason.HIDE_RESET_SHELL_COMMAND, userId);
        mService.setImeVisibilityOnFocusedWindowClient(false, userData, statsToken);
        final var bindingController = userData.mBindingController;
        bindingController.unbindIme();
        // Enable default IMEs, disable others
        var toDisable = settings.getEnabledInputMethodList();
        var defaultEnabled = InputMethodInfoUtils.getDefaultEnabledImes(
                mService.mContext, settings.getMethodList());
        toDisable.removeAll(defaultEnabled);
        for (InputMethodInfo info : toDisable) {
            mService.setInputMethodEnabledLocked(info.getId(), false, userId);
        }
        for (InputMethodInfo info : defaultEnabled) {
            mService.setInputMethodEnabledLocked(info.getId(), true, userId);
        }
        // Choose new default IME, reset to none if no IME available.
        if (!mService.chooseNewDefaultIMELocked(userId)) {
            mService.resetSelectedInputMethodAndSubtypeLocked(null, userId);
        }
        mService.updateInputMethodsFromSettingsLocked(true /* enabledMayChange */, userId);
        InputMethodUtils.setNonSelectedSystemImesDisabledUntilUsed(
                mService.getPackageManagerForUser(mService.mContext, settings.getUserId()),
                settings.getEnabledInputMethodList());
    }
}
