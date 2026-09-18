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

package com.android.server.personalcontext.embedded;

import android.content.pm.ServiceInfo;

import java.util.List;

/** An interface defining a visualizer services fetcher, which is used to fetch the
 * {@link ServiceInfo} for the insight visualizer services in a given package.
 * @hide
 */
interface VisualizerServicesFetcher {
    /**
     * Fetch a list {@link ServiceInfo} objects for the visualizer services in the given
     * package. If packageName is null, then all visualizer services will be fetched.
     */
    List<ServiceInfo> fetchVisualizerServices(String packageName);
}
