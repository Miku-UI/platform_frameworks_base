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

package com.android.server.security.talisman;

import static android.security.talisman.TalismanManager.VERIFICATION_FAILURE_UNKNOWN;

import android.content.Context;
import android.security.talisman.ITalismanManager;
import android.security.talisman.Talisman;
import android.security.talisman.TalismanIdentitySet;
import android.util.Slog;
import com.android.server.SystemService;
import java.util.Arrays;
import java.util.List;

/** A service that manages talismans. */
public class TalismanManagerService extends SystemService {
    private static final String TAG = "TalismanManagerService";

    private final Context mContext;

    public TalismanManagerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting TalismanManagerService");
        publishBinderService(Context.TALISMAN_SERVICE, mBinder);
    }

    private final ITalismanManager.Stub mBinder =
            new ITalismanManager.Stub() {
                @Override
                public Talisman acquireVerifiedDeviceTalisman() {
                    // TODO(b/418280383): Implement this method.
                    Slog.w(TAG, "acquireVerifiedDeviceTalisman is not yet implemented.");
                    return null;
                }

                @Override
                public TalismanIdentitySet acquirePreparedIdentitySet() {
                    // TODO(b/418280383): Implement this method.
                    Slog.w(TAG, "acquirePreparedIdentitySet is not yet implemented.");
                    return null;
                }

                @Override
                public int verifyTalismanAndChallenge(
                        Talisman talisman, byte[] remoteResponse, byte[] expectedChallenge) {
                    // TODO(b/418280383): Implement this method.
                    Slog.w(TAG, "verifyTalismanAndChallenge is not yet implemented.");
                    return VERIFICATION_FAILURE_UNKNOWN;
                }

                @Override
                public int[] verifyIdentityTalismans(
                        Talisman verifiedDeviceTalisman, Talisman[] identityTalismans) {
                    // TODO(b/418280383): Implement this method.
                    Slog.w(TAG, "verifyIdentityTalismans is not yet implemented.");
                    int[] results = new int[identityTalismans.length];
                    Arrays.fill(results, VERIFICATION_FAILURE_UNKNOWN);
                    return results;
                }

                @Override
                public void updatePreparedIdentities(List<String> identities) {
                    // TODO(b/418280383): Implement this method.
                    Slog.w(TAG, "updatePreparedIdentities is not yet implemented.");
                }
            };
}
