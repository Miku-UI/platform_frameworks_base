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

package android.app.admin;

import static android.app.admin.flags.Flags.FLAG_POLICY_STREAMLINING;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import java.util.Objects;

/**
 * Identifies a package on the current device.
 *
 * Used as a policy value type to specify a package that the policy applies to.
 */
@FlaggedApi(FLAG_POLICY_STREAMLINING)
public final class PackageIdentifier {
    @NonNull private final String mPackageName;

    /**
     * Constructs a new {@link PackageIdentifier} object.
     *
     * @param packageName the name of the package.
     */
    public PackageIdentifier(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        mPackageName = packageName;
    }

    /**
     * Constructs a new {@link PackageIdentifier} object from a transport object.
     *
     * @param transport the transport object.
     * @hide
     */
    public PackageIdentifier(@NonNull PackageIdentifierTransport transport) {
        Objects.requireNonNull(transport);
        mPackageName = transport.packageName;
    }

    /**
     * Returns the transport representation of the params.
     *
     * @hide
     */
    public @NonNull PackageIdentifierTransport createTransport() {
        PackageIdentifierTransport transport = new PackageIdentifierTransport();
        transport.packageName = mPackageName;
        return transport;
    }

    /**
     * @return the name of the package.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageIdentifier other = (PackageIdentifier) o;
        return Objects.equals(mPackageName, other.mPackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName);
    }
}
