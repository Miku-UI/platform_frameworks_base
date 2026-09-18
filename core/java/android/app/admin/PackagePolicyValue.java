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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;

import java.util.Objects;

/**
 * @hide
 */
public final class PackagePolicyValue extends PolicyValue<PackageIdentifier> {

    public PackagePolicyValue(PackageIdentifier value) {
        super(value);
    }

    private PackagePolicyValue(Parcel source) {
        this(new PackageIdentifier(PackageIdentifierTransport.CREATOR.createFromParcel(source)));
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackagePolicyValue other = (PackagePolicyValue) o;
        return Objects.equals(getValue(), other.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue());
    }

    @Override
    public String toString() {
        return "PackagePolicyValue { mValue= " + getValue() + " }";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        getValue().createTransport().writeToParcel(dest, flags);
    }

    @NonNull
    public static final Creator<PackagePolicyValue> CREATOR =
            new Creator<PackagePolicyValue>() {
                @Override
                public PackagePolicyValue createFromParcel(Parcel source) {
                    return new PackagePolicyValue(source);
                }

                @Override
                public PackagePolicyValue[] newArray(int size) {
                    return new PackagePolicyValue[size];
                }
            };
}
