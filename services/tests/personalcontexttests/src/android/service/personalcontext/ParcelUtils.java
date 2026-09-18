/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext;

import android.os.Parcel;
import android.os.Parcelable;

/** Utility class for test helper functions that handle Parcels and Parcelables. */
public class ParcelUtils {
    /** takes a Parcellable, parcels it, unparcels it, and returns the unparceled version. */
    public static <T extends Parcelable> T roundTripThroughParcel(T original) {
        Parcel p = Parcel.obtain();
        try {
            p.writeParcelable(original, 0);
            p.setDataPosition(0);
            return p.readParcelable(null);
        } finally {
            p.recycle();
        }
    }
}
