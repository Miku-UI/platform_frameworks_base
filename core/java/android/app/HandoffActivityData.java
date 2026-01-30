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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;
import android.os.Parcel;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.Parcelable;
import android.content.ComponentName;
import android.util.Log;
import java.util.Objects;

/**
 * Represents information needed to recreate an activity on a remote device owned by the user.
 *
 * This class is returned by {@link Activity#onHandoffActivityDataRequested}, and is passed to a
 * remote device owned by the user. The remote device will create a launch intent for the activity
 * specified by {@link #getComponentName()}, passing along any extras specified by
 * {@link getExtras()}.
 *
 * If {@link #getComponentName()} cannot be launched on the remote device, developers can optionally
 * specify a fallback URI in {@link #setFallbackUri()}. The URI specified will be launched on the
 * remote device's web browser in this case. If no fallback URI is specified, the user will be
 * presented with an error. If the system is attempting to hand off the entire task, failure to
 * resolve {@link #getComponentName()} will result in only the top activity of the task being handed
 * off.
 */
@FlaggedApi(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
public final class HandoffActivityData implements Parcelable {

    private @NonNull ComponentName mComponentName;
    private @NonNull PersistableBundle mExtras;
    private @Nullable Uri mFallbackUri;

    private HandoffActivityData(@NonNull Builder builder) {
        Objects.requireNonNull(builder);
        mComponentName = builder.mComponentName;
        mExtras = builder.mExtras;
        mFallbackUri = builder.mFallbackUri;
    }

    private HandoffActivityData(Parcel in) {
        mComponentName = ComponentName.CREATOR.createFromParcel(in);
        mExtras = in.readPersistableBundle(getClass().getClassLoader());
        if (in.readInt() != 0) {
            mFallbackUri = Uri.CREATOR.createFromParcel(in);
        }
    }

    public static final @NonNull Creator<HandoffActivityData> CREATOR = new Creator<>() {
        @Override
        public HandoffActivityData createFromParcel(Parcel in) {
            return new HandoffActivityData(in);
        }

        @Override
        public HandoffActivityData[] newArray(int size) {
            return new HandoffActivityData[size];
        }
    };

    /**
     * @return the component name of an activity to launch on the remote device
     * when the activity represented by this object is handed off.
     */
    @NonNull
    public ComponentName getComponentName() {
        return mComponentName;
    }

    /**
     * @return extras to pass inside the launch intent via {@link Intent#putExtras} for the
     * activity specified by {@link #getComponentName()} during handoff. This defaults to an empty
     * bundle.
     */
    @NonNull
    public PersistableBundle getExtras() {
        return mExtras;
    }

    /**
     * @return the URI which will be launched on the remote device's web browser if the activity
     * specified by {@link #getComponentName()} cannot be launched, or {@code null} if no fallback
     * URI was specified.
     */
    @Nullable
    public Uri getFallbackUri() {
        return mFallbackUri;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mComponentName, mExtras, mFallbackUri);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HandoffActivityData) {
            final HandoffActivityData other = (HandoffActivityData) obj;
            return Objects.equals(mComponentName, other.mComponentName)
                    && Objects.equals(mExtras, other.mExtras)
                    && Objects.equals(mFallbackUri, other.mFallbackUri);
        }
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        mComponentName.writeToParcel(dest, flags);
        dest.writePersistableBundle(mExtras);
        if (mFallbackUri != null) {
            dest.writeInt(1);
            mFallbackUri.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    /**
     * Builder for {@link HandoffActivityData}.
     */
    @FlaggedApi(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
    public final static class Builder {
        @NonNull private ComponentName mComponentName;
        @NonNull private PersistableBundle mExtras;
        @Nullable private Uri mFallbackUri;

        /**
         * Creates a builder for the given component name.
         *
         * @param componentName the component name of the activity to be launched.
         */
        public Builder(@NonNull ComponentName componentName) {
            mComponentName = Objects.requireNonNull(componentName);
            mExtras = new PersistableBundle();
            mFallbackUri = null;
        }

        /**
         * Specifies which extras will be passed to the activity with name
         * {@link #getComponentName()} in its launch intent. This information
         * should allow the activity on the receiving devices to restore the
         * state of the activity on the sending device.
         *
         * If no extras are specified, the activity will be launched with an
         * empty bundle for extras.
         *
         * Any extras specified here must be safe to pass to another device, and
         * thus should not reference any device-specific information such as file
         * paths.
         *
         * @param extras the extras of the activity to be launched.
         * @return the builder.
         */
        @NonNull
        public Builder setExtras(@NonNull PersistableBundle extras) {
            mExtras = Objects.requireNonNull(extras);
            return this;
        }

        /**
         * Sets a fallback URI for this activity.
         *
         * @param fallbackUri the fallback uri.
         * @return the builder.
         */
        @NonNull
        public Builder setFallbackUri(@Nullable Uri fallbackUri) {
            mFallbackUri = fallbackUri;
            return this;
        }

        /**
         * Builds the {@link HandoffActivityData} object.
         *
         * @return the {@link HandoffActivityData} object.
         */
        @NonNull
        public HandoffActivityData build() {
            return new HandoffActivityData(this);
        }
    }
}