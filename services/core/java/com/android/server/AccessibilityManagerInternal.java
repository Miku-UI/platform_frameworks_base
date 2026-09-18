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

package com.android.server;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.util.ArraySet;
import android.util.SparseArray;
import android.view.inputmethod.EditorInfo;

import com.android.internal.inputmethod.IAccessibilityInputMethodSession;
import com.android.internal.inputmethod.IRemoteAccessibilityInputConnection;

import java.util.List;
import java.util.Set;

/**
 * Accessibility manager local system service interface.
 */
public abstract class AccessibilityManagerInternal {
    /** Enable or disable the sessions. */
    public abstract void setImeSessionEnabled(
            SparseArray<IAccessibilityInputMethodSession> sessions, boolean enabled);

    /** Unbind input for all accessibility services which require ime capabilities. */
    public abstract void unbindInput();

    /** Bind input for all accessibility services which require ime capabilities. */
    public abstract void bindInput();

    /**
     * Request input session from all accessibility services which require ime capabilities and
     * whose id is not in the ignoreSet.
     */
    public abstract void createImeSession(ArraySet<Integer> ignoreSet);

    /** Start input for all accessibility services which require ime capabilities. */
    public abstract void startInput(
            IRemoteAccessibilityInputConnection remoteAccessibilityInputConnection,
            EditorInfo editorInfo, boolean restarting);

    /** Trigger a system action with the provided {@code actionId}. */
    public abstract void performSystemAction(int actionId);

    /**
     * Queries whether touch-exploration mode is enabled or not for the specified user.
     *
     * @param userId User ID to be queried about.
     * @return {@code true} if touch-exploration mode is enabled.
     * @see android.view.accessibility.AccessibilityManager#isTouchExplorationEnabled()
     */
    public abstract boolean isTouchExplorationEnabled(@UserIdInt int userId);

    /**
     * Returns the filtered list of permitted accessibility services for a user.
     * * This method is responsible for applying all policy-related filtering, including:
     * 1. Policy set by Device/Profile Admins (the basePermittedServices).
     * 2. Restrictions imposed by Advanced Protection Mode (APM), which is checked internally.
     *
     * @param adminPermittedServices The intersection of services explicitly allowed by all active
     *                               Device/Profile Admins.
     * @param userId                 The user ID to apply the policy filter to.
     * @return A Set of package names that are finally allowed to run accessibility services.
     */
    public abstract Set<String> getPermittedAccessibilityServicePackages(
            @Nullable List<String> adminPermittedServices, @UserIdInt int userId);

    /**
     * Returns the count of Accessibility Services and Shortcuts that would be restricted
     * by Advanced Protection Mode.
     */
    public record AccessibilityFeatureRestrictedCounts(
            int disabledServices, int removedShortcuts) {}

    /**
     * Returns the count of Accessibility Services and Shortcuts that would be restricted
     * by Advanced Protection Mode.
     *
     * @param userId The user ID to check.
     * @return The restricted counts.
     */
    public abstract AccessibilityFeatureRestrictedCounts getA11yFeatureRestrictedCounts(
            @UserIdInt int userId);

    private static final AccessibilityManagerInternal NOP = new AccessibilityManagerInternal() {
        @Override
        public void setImeSessionEnabled(SparseArray<IAccessibilityInputMethodSession> sessions,
                boolean enabled) {
        }

        @Override
        public void unbindInput() {
        }

        @Override
        public void bindInput() {
        }

        @Override
        public void createImeSession(ArraySet<Integer> ignoreSet) {
        }

        @Override
        public void startInput(IRemoteAccessibilityInputConnection remoteAccessibility,
                EditorInfo editorInfo, boolean restarting) {
        }

        @Override
        public boolean isTouchExplorationEnabled(int userId) {
            return false;
        }

        @Override
        public void performSystemAction(int actionId) {
        }

        @Override
        public Set<String> getPermittedAccessibilityServicePackages(
                List<String> adminPermittedServices, int userId) {
            return Set.of();
        }

        @Override
        public AccessibilityFeatureRestrictedCounts getA11yFeatureRestrictedCounts(int userId) {
            return new AccessibilityFeatureRestrictedCounts(0, 0);
        }
    };

    /**
     * @return Global instance if exists. Otherwise, a fallback no-op instance.
     */
    @NonNull
    public static AccessibilityManagerInternal get() {
        final AccessibilityManagerInternal instance =
                LocalServices.getService(AccessibilityManagerInternal.class);
        return instance != null ? instance : NOP;
    }
}
