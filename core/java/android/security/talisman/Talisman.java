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

package android.security.talisman;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * A talisman, which is a cryptographically-verifiable claim about the device or user.
 *
 * <p>A talisman is a CBOR Web Token (CWT) that contains claims about the device, and is signed by a
 * key trusted by the system. It can be used to prove properties of the device to a remote party.
 *
 * <p>This object can be used with {@link TalismanManager} to sign challenges to prove possession of
 * the private key associated with the talisman. This operation requires the {@link
 * android.Manifest.permission#SIGN_TALISMAN} permission.
 *
 * <p>Instances of this class are obtained from {@link TalismanManager}.
 *
 * @hide
 */
public final class Talisman implements Parcelable {

    private final byte[] mEncodedTalisman;

    public Talisman(@NonNull byte[] encodedTalisman) {
        mEncodedTalisman = Objects.requireNonNull(encodedTalisman).clone();
    }

    private Talisman(Parcel in) {
        mEncodedTalisman = in.createByteArray();
    }

    /** Returns a copy of the encoded form of the talisman. */
    @NonNull
    public byte[] encoded() {
        return mEncodedTalisman.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(mEncodedTalisman);
    }

    @NonNull
    public static final Creator<Talisman> CREATOR =
            new Creator<Talisman>() {
                @Override
                public Talisman createFromParcel(Parcel in) {
                    return new Talisman(in);
                }

                @Override
                public Talisman[] newArray(int size) {
                    return new Talisman[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Talisman)) return false;
        Talisman that = (Talisman) o;
        return Arrays.equals(mEncodedTalisman, that.mEncodedTalisman);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mEncodedTalisman);
    }
}
