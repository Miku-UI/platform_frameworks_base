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

package android.app.contextualsearch;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.contextualsearch.flags.Flags;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Display;

import java.util.Objects;

/**
 * Configuration for Contextual Search invocations. Typically the parameters added here are passed
 * to the Contextual Search provider app as specified by the device configuration.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_CONFIG_PARAMETERS)
@SystemApi
public final class ContextualSearchConfig implements Parcelable {

    private final int mDisplayId;
    @Nullable private final Rect mSourceBounds;
    @NonNull private final Bundle mIntentExtras;

    public static final @NonNull Creator<ContextualSearchConfig> CREATOR =
            new Creator<>() {
                @Override
                public ContextualSearchConfig createFromParcel(@NonNull Parcel in) {
                    return new ContextualSearchConfig(in);
                }

                @Override
                public ContextualSearchConfig[] newArray(int size) {
                    return new ContextualSearchConfig[size];
                }
            };

    ContextualSearchConfig(@NonNull Parcel in) {
        mDisplayId = in.readInt();
        mSourceBounds = in.readTypedObject(Rect.CREATOR);
        mIntentExtras = Objects.requireNonNull(
                in.readBundle(ContextualSearchConfig.class.getClassLoader()));
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mDisplayId);
        dest.writeTypedObject(mSourceBounds, flags);
        dest.writeBundle(mIntentExtras);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private ContextualSearchConfig(@NonNull Builder builder) {
        mDisplayId = builder.mDisplayId;
        mSourceBounds = builder.mSourceBounds;
        mIntentExtras = builder.mIntentExtras;
    }

    /**
     * @return The ID of the display where the search was triggered. This determines where the
     *         screenshot is taken and displayed for user interaction. If the display ID is invalid,
     *         the invocation will fail silently. If not specified, the system will use
     *         {@link Display#DEFAULT_DISPLAY}, or the Activity's display if launched from an
     *         Activity.
     */
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * @return The bounds of the source element that triggered the search, in screen coordinates.
     *         Can be null if not available.
     */
    @Nullable
    public Rect getSourceBounds() {
        return mSourceBounds == null ? null : new Rect(mSourceBounds);
    }

    /**
     * @return Extras to be added to the Intent sent to the Contextual Search app. These will be
     *         merged with any other extras added to the Intent by ContextualSearchManagerService.
     */
    @NonNull
    public Bundle getIntentExtras() {
        return new Bundle(mIntentExtras);
    }

    @Override
    public String toString() {
        return "ContextualSearchConfig{"
            + "mDisplayId=" + mDisplayId + ", "
            + "mSourceBounds=" + mSourceBounds + ", "
            + "mIntentExtras=" + mIntentExtras
            + '}';
    }

    /**
     * Builder to create a {@link ContextualSearchConfig}.
     */
    public static final class Builder {

        private int mDisplayId;
        @Nullable private Rect mSourceBounds;
        @NonNull private final Bundle mIntentExtras;

        /**
         * Creates a new Builder with default values.
         */
        public Builder() {
            mDisplayId = Display.INVALID_DISPLAY;
            mSourceBounds = null;
            mIntentExtras = new Bundle();
        }

        /**
         * Creates a new builder and initializes it with the values from the given
         * {@link ContextualSearchConfig}.
         *
         * @param config The config to copy values from.
         */
        public Builder(@NonNull ContextualSearchConfig config) {
            mDisplayId = config.getDisplayId();
            mSourceBounds = config.getSourceBounds();
            mIntentExtras = config.getIntentExtras();
        }

        /**
         * Sets the display ID for the contextual search invocation.
         *
         * @param displayId The ID of the display where the search was triggered. This determines
         *                  where the screenshot is taken and displayed for user interaction. If the
         *                  display ID is invalid, the invocation will fail silently. If not
         *                  specified, the system will use {@link Display#DEFAULT_DISPLAY}, or the
         *                  Activity's display if launched from an Activity.
         * @return This Builder object to allow for chaining of calls.
         */
        @NonNull
        public Builder setDisplayId(int displayId) {
            mDisplayId = displayId;
            return this;
        }

        /**
         * Sets the source bounds for the contextual search invocation.
         *
         * @param sourceBounds The bounds of the source element that triggered the search, in screen
         *                     coordinates. Can be null if not available.
         * @return This Builder object to allow for chaining of calls.
         */
        @NonNull
        public Builder setSourceBounds(@Nullable Rect sourceBounds) {
            mSourceBounds = sourceBounds == null ? null : new Rect(sourceBounds);
            return this;
        }

        /**
         * Sets any additional extras to be added to the intent sent to the Contextual Search app.
         *
         * @param intentExtras This will be merged with any other extras added to the intent by
         *                     ContextualSearchManagerService. To avoid having your extras
         *                     overwritten, prefix the keys with an agreed package name.
         * @return This Builder object to allow for chaining of calls.
         */
        @NonNull
        public Builder setIntentExtras(@Nullable Bundle intentExtras) {
            mIntentExtras.clear();
            if (intentExtras != null) {
                mIntentExtras.putAll(intentExtras);
            }
            return this;
        }

        /**
         * Builds the {@link ContextualSearchConfig} instance.
         *
         * @return The built {@link ContextualSearchConfig} object.
         */
        @NonNull
        public ContextualSearchConfig build() {
            return new ContextualSearchConfig(this);
        }
    }
}
