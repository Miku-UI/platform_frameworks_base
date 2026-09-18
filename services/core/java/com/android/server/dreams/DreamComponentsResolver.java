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

package com.android.server.dreams;

import static android.service.dreams.Flags.dreamsSwitcher;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.hardware.display.AmbientDisplayConfiguration;
import android.service.dreams.DreamItem;
import android.service.dreams.DreamPlaylist;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.UserManagerInternal;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Resolves the component to use for dreaming or dozing. */
final class DreamComponentsResolver {
    private static final String TAG = "DreamComponentsResolver";

    private final AmbientDisplayConfiguration mDozeConfig;
    private final UserManagerInternal mUserManagerInternal;
    private final boolean mDreamsOnlyEnabledForDockUser;
    private final int mUserId;
    private final DreamRepository mDreamRepository;
    private final DreamValidator mDreamValidator;

    @GuardedBy("this")
    private DreamPlaylist mCachedPlaylist;

    @GuardedBy("this")
    private ComponentName mCachedSystemDreamComponent;

    DreamComponentsResolver(
            int userId,
            @NonNull AmbientDisplayConfiguration dozeConfig,
            @NonNull UserManagerInternal userManagerInternal,
            boolean dreamsOnlyEnabledForDockUser,
            @NonNull DreamRepository dreamRepository,
            @NonNull DreamValidator dreamValidator) {
        mUserId = userId;
        mDozeConfig = dozeConfig;
        mUserManagerInternal = userManagerInternal;
        mDreamsOnlyEnabledForDockUser = dreamsOnlyEnabledForDockUser;
        mDreamRepository = dreamRepository;
        mDreamValidator = dreamValidator;
    }

    /** Clears the cached playlist. */
    public synchronized void invalidate() {
        mCachedPlaylist = null;
        mCachedSystemDreamComponent = null;
    }

    /** Clears the cache only if the changed package is relevant to the current dream config. */
    public synchronized void onPackageChanged(@NonNull String packageName) {
        if (mCachedPlaylist != null && isPackageRelevant(mCachedPlaylist, packageName)) {
            invalidate();
        }
    }

    private boolean isPackageRelevant(@NonNull DreamPlaylist playlist, String packageName) {
        return isPackageInCurrentPlaylist(playlist, packageName)
                || isDefaultDreamPackage(packageName)
                || isConfiguredPackage(packageName);
    }

    private boolean isPackageInCurrentPlaylist(
            @NonNull DreamPlaylist playlist, String packageName) {
        for (DreamItem item : playlist.getDreams()) {
            if (item.componentName.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefaultDreamPackage(String packageName) {
        final ComponentName defaultDream = getDefaultDreamComponent();
        return defaultDream != null && defaultDream.getPackageName().equals(packageName);
    }

    private boolean isConfiguredPackage(String packageName) {
        final ComponentName[] configured = mDreamRepository.getDreamComponentsForUser(mUserId);
        if (configured != null) {
            for (ComponentName component : configured) {
                if (component.getPackageName().equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resolves the component to be used for the current dream state.
     *
     * @param doze whether the component is being resolved for dozing.
     * @param forceAmbientDisplayEnabled whether ambient display is forced enabled.
     * @param systemDreamComponent the system dream component, if any.
     * @return the resolved component, or null if none should be used.
     */
    @Nullable
    ComponentName resolve(
            boolean doze,
            boolean forceAmbientDisplayEnabled,
            @Nullable ComponentName systemDreamComponent) {
        if (doze) {
            ComponentName dozeComponent = getDozeComponent(forceAmbientDisplayEnabled);
            return isValid(dozeComponent) ? dozeComponent : null;
        }

        if (dreamsSwitcher()) {
            final DreamPlaylist playlist = getDreamPlaylist(systemDreamComponent);
            final DreamItem activeItem = playlist.getActiveDream();
            return activeItem != null ? activeItem.componentName : null;
        }

        if (systemDreamComponent != null) {
            return systemDreamComponent;
        }

        ComponentName[] dreams = getDreamComponents();
        return dreams != null && dreams.length != 0 ? dreams[0] : null;
    }

    @NonNull
    public synchronized DreamPlaylist getDreamPlaylist(
            @Nullable ComponentName systemDreamComponent) {
        if (mCachedPlaylist != null
                && Objects.equals(mCachedSystemDreamComponent, systemDreamComponent)) {
            return mCachedPlaylist;
        }

        mCachedPlaylist =
                (systemDreamComponent != null)
                        ? resolveSystemDreamPlaylist(systemDreamComponent)
                        : resolveUserDreamPlaylist();
        mCachedSystemDreamComponent = systemDreamComponent;
        return mCachedPlaylist;
    }

    private DreamPlaylist resolveSystemDreamPlaylist(@NonNull ComponentName systemDreamComponent) {
        final List<DreamItem> dreams = new ArrayList<>();
        mDreamRepository.getDreamItem(systemDreamComponent).ifPresent(dreams::add);
        return new DreamPlaylist(
                dreams, dreams.isEmpty() ? DreamPlaylist.NO_ACTIVE_DREAM_INDEX : 0);
    }

    private DreamPlaylist resolveUserDreamPlaylist() {
        final List<DreamItem> dreams = new ArrayList<>();
        int activeIndex = DreamPlaylist.NO_ACTIVE_DREAM_INDEX;

        if (!dreamsEnabledForUser(mUserId)) {
            return DreamPlaylist.EMPTY;
        }

        final ComponentName[] components = mDreamRepository.getDreamComponentsForUser(mUserId);
        final ComponentName activeComponent =
                mDreamRepository.getActiveDreamComponentForUser(mUserId);

        for (ComponentName component : components) {
            // One-pass: get the item directly. MetadataProvider already handles validity caching.
            final DreamItem item = mDreamRepository.getDreamItem(component).orElse(null);
            if (item != null) {
                dreams.add(item);
                if (activeIndex == DreamPlaylist.NO_ACTIVE_DREAM_INDEX
                        && component.equals(activeComponent)) {
                    activeIndex = dreams.size() - 1;
                }
            }
        }

        // Fallback to default dream if no valid dreams found in configuration
        if (dreams.isEmpty()) {
            final ComponentName defaultDream = getDefaultDreamComponent();
            if (defaultDream != null) {
                mDreamRepository.getDreamItem(defaultDream).ifPresent(dreams::add);
            }
        }

        if (!dreams.isEmpty() && activeIndex == DreamPlaylist.NO_ACTIVE_DREAM_INDEX) {
            activeIndex = 0;
        }

        return new DreamPlaylist(dreams, activeIndex);
    }

    /**
     * Returns the user-configured dream components.
     *
     * @return an array of component names, or null if dreams are disabled for the user.
     */
    @Nullable
    ComponentName[] getDreamComponents() {
        if (!dreamsEnabledForUser(mUserId)) {
            return null;
        }

        final ComponentName[] components = mDreamRepository.getDreamComponentsForUser(mUserId);
        List<ComponentName> validComponents = new ArrayList<>();
        for (ComponentName component : components) {
            if (isValid(component)) {
                validComponents.add(component);
            }
        }

        if (validComponents.isEmpty()) {
            ComponentName defaultDream = getDefaultDreamComponent();
            if (isValid(defaultDream)) {
                Slog.w(TAG, "Falling back to default dream " + defaultDream);
                validComponents.add(defaultDream);
            }
        }
        return validComponents.toArray(new ComponentName[0]);
    }

    @Nullable
    ComponentName getDefaultDreamComponent() {
        return mDreamRepository.getDefaultDreamComponentForUser(mUserId);
    }

    @Nullable
    ComponentName getDozeComponent(boolean forceAmbientDisplayEnabled) {
        if (forceAmbientDisplayEnabled || mDozeConfig.enabled(mUserId)) {
            return ComponentName.unflattenFromString(mDozeConfig.ambientDisplayComponent());
        } else {
            return null;
        }
    }

    boolean isValid(@Nullable ComponentName component) {
        if (component == null) {
            return false;
        }

        if (dreamsSwitcher()) {
            return mDreamRepository.getDreamItem(component).isPresent();
        }

        return mDreamValidator.validate(component, mUserId);
    }

    private boolean dreamsEnabledForUser(int userId) {
        if (!mDreamsOnlyEnabledForDockUser) {
            return true;
        }
        if (userId < 0) {
            return false;
        }
        final int mainUserId = mUserManagerInternal.getMainUserId();
        return userId == mainUserId;
    }

    void dump(PrintWriter pw) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("DreamComponentsResolver (userId=" + mUserId + "):");
        ipw.increaseIndent();

        ipw.println("dreamsEnabledForUser=" + dreamsEnabledForUser(mUserId));
        ipw.println("getDreamComponents=" + Arrays.toString(getDreamComponents()));
        ipw.println("getDefaultDreamComponent=" + getDefaultDreamComponent());
        ipw.println(
                "getSettingsActiveDream="
                        + mDreamRepository.getActiveDreamComponentForUser(mUserId));
        ipw.println("mCachedPlaylist=" + mCachedPlaylist);
        ipw.println(
                "mCachedSystemDreamComponent="
                        + ComponentName.flattenToShortString(mCachedSystemDreamComponent));

        ipw.decreaseIndent();
    }
}
