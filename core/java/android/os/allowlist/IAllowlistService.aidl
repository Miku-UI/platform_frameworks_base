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

package android.os.allowlist;

import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.IOnAllowlistChangedListener;
import android.os.RemoteCallback;

/**
 * Interface between the AllowlistManager and AllowlistService.
 * @hide
 */
interface IAllowlistService {
    @EnforcePermission("QUERY_ALLOWLIST")
    void addOnAllowlistChangedListener(in AllowlistRequest request,
        in IOnAllowlistChangedListener listener);

    @EnforcePermission("QUERY_ALLOWLIST")
    void removeOnAllowlistChangedListener(in IOnAllowlistChangedListener listener);

    @EnforcePermission("QUERY_ALLOWLIST")
    void queryAllowlist(in AllowlistRequest request, in RemoteCallback callback);

    @EnforcePermission("QUERY_ALLOWLIST")
    void setTestProviderEnabled(boolean enabled);

    @EnforcePermission("QUERY_ALLOWLIST")
    void notifyAllowlistChangedListenersForTestProvider(in List<AllowlistRequest> requests);
}
