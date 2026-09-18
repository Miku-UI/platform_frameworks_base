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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.dreams.DreamItem;

import java.util.Optional;

/** Implementation of {@link DreamRepository}. */
class DreamRepositoryImpl implements DreamRepository {
    private final Context mContext;
    private final DreamMetadataProvider mMetadataProvider;

    DreamRepositoryImpl(@NonNull Context context, @NonNull DreamMetadataProvider metadataProvider) {
        mContext = context;
        mMetadataProvider = metadataProvider;
    }

    @Override
    public ComponentName[] getDreamComponentsForUser(int userId) {
        final String names =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.SCREENSAVER_COMPONENTS,
                        userId);
        return DreamComponentNameUtils.fromCommaSeparatedString(names);
    }

    @Override
    public ComponentName getDefaultDreamComponentForUser(int userId) {
        String name =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.SCREENSAVER_DEFAULT_COMPONENT,
                        userId);
        return name == null ? null : ComponentName.unflattenFromString(name);
    }

    @Override
    public ComponentName getActiveDreamComponentForUser(int userId) {
        final String name =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
                        userId);
        return name != null ? ComponentName.unflattenFromString(name) : null;
    }

    @Override
    public Optional<DreamItem> getDreamItem(ComponentName component) {
        return Optional.ofNullable(mMetadataProvider.getDreamItem(component));
    }
}
