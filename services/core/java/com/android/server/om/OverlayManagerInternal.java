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

package com.android.server.om;

import android.annotation.NonNull;
import android.content.om.IOverlayManager;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManagerTransaction;
import android.os.UserHandle;

import java.util.List;

public interface OverlayManagerInternal {

    /**
     * Provides the local service.
     *
     * @return An IOverlayManager
     */
    IOverlayManager getService();

    /**
     * Get the related information of overlays for {@code targetPackageName}.
     *
     * @param targetPackageName the target package name
     * @return a list of overlay information
     */
    List<OverlayInfo> getOverlayInfosForTarget(@NonNull String targetPackageName,
            @NonNull UserHandle user);

    /**
     * Returns information about the overlay with the given package name for
     * the specified user.
     *
     * @param packageName The name of the package.
     * @param userHandle  The user to get the OverlayInfos for.
     * @return An OverlayInfo object; if no overlays exist with the
     * requested package name, null is returned.
     * @hide
     */
    OverlayInfo getOverlayInfo(@NonNull String packageName, @NonNull UserHandle userHandle);

    /**
     * Returns information about the overlay represented by the identifier for the specified user.
     *
     * @param overlay    the identifier representing the overlay
     * @param userHandle the user of which to get overlay state info
     * @return the overlay info or null if the overlay cannot be found
     * @hide
     */
    OverlayInfo getOverlayInfo(@NonNull OverlayIdentifier overlay, @NonNull UserHandle userHandle);

    /**
     * Commit the overlay manager transaction.
     *
     * <p>Applications can register overlays and unregister the registered overlays in an atomic
     * operation via {@link OverlayManagerTransaction}.
     *
     * @param transaction the series of overlay related requests to perform
     * @throws Exception if not all the requests could be successfully completed.
     * @see OverlayManagerTransaction
     */
    void commit(@NonNull OverlayManagerTransaction transaction);
}
