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

package android.app.appfunctions;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.Nullable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.permission.flags.Flags;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents attribution information for an app function execution, detailing the context and
 * nature of the interaction that triggered it. This information can be used by the privacy setting
 * to provide transparency to the user about why an app function was invoked.
 *
 * @see ExecuteAppFunctionRequest#getAttribution
 * @see ExecuteAppFunctionRequest.Builder#setAttribution
 */
@FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
public final class AppFunctionAttribution implements Parcelable {
    @NonNull private static final String TAG = "AppFunctionAttribution";

    @NonNull
    public static final Creator<AppFunctionAttribution> CREATOR =
            new Creator<AppFunctionAttribution>() {
                @Override
                public AppFunctionAttribution createFromParcel(Parcel parcel) {
                    return new AppFunctionAttribution(parcel);
                }

                @Override
                public AppFunctionAttribution[] newArray(int size) {
                    return new AppFunctionAttribution[0];
                }
            };

    /**
     * Indicates an interaction type not covered by other predefined constants.
     *
     * <p>When this type is used, a custom interaction type string must be provided via {@link
     * Builder#setCustomInteractionType}.
     *
     * @see AppFunctionAttribution#getCustomInteractionType
     * @see AppFunctionAttribution.Builder#setCustomInteractionType
     */
    public static final int INTERACTION_TYPE_OTHER = 0;

    /**
     * Indicates that the app function execution was triggered as a direct result of a user query.
     */
    public static final int INTERACTION_TYPE_USER_QUERY = 1;

    /** Indicates that the app function execution was triggered by a user-scheduled task. */
    public static final int INTERACTION_TYPE_USER_SCHEDULED = 2;

    /** @hide */
    @IntDef(
            prefix = "INTERACTION_TYPE_",
            value = {
                INTERACTION_TYPE_OTHER,
                INTERACTION_TYPE_USER_QUERY,
                INTERACTION_TYPE_USER_SCHEDULED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InteractionType {}

    private final int mInteractionType;

    @Nullable private final String mCustomInteractionType;

    @Nullable private final String mThreadId;

    @Nullable private final Uri mInteractionUri;

    private AppFunctionAttribution(
            @InteractionType int interactionType,
            @Nullable String customInteractionType,
            @Nullable String threadId,
            @Nullable Uri interactionUri) {
        mInteractionType = interactionType;
        if (interactionType == INTERACTION_TYPE_OTHER && customInteractionType == null) {
            throw new IllegalArgumentException(
                    "Must set customInteractionType when interactionType=INTERACTION_TYPE_OTHER");
        }
        if (customInteractionType != null && interactionType != INTERACTION_TYPE_OTHER) {
            throw new IllegalArgumentException(
                    "customInteractionType is only allowed when "
                            + "interactionType=INTERACTION_TYPE_OTHER");
        }
        mCustomInteractionType = customInteractionType;
        mThreadId = threadId;
        mInteractionUri = interactionUri;
    }

    private AppFunctionAttribution(@NonNull Parcel in) {
        mInteractionType = in.readInt();
        mCustomInteractionType = in.readString8();
        mThreadId = in.readString8();
        mInteractionUri = in.readTypedObject(Uri.CREATOR);
    }

    /** Returns the type of interaction that triggered the app function execution. */
    @InteractionType
    public int getInteractionType() {
        return mInteractionType;
    }

    /**
     * Returns the custom string describing the interaction, if {@link #getInteractionType()} is
     * {@link AppFunctionAttribution#INTERACTION_TYPE_OTHER}.
     */
    @Nullable
    public String getCustomInteractionType() {
        return mCustomInteractionType;
    }

    /** Returns the unique thread ID associated with this app function execution. */
    @Nullable
    public String getThreadId() {
        return mThreadId;
    }

    /** Returns the {@link Uri} linking to the original interaction. */
    @Nullable
    public Uri getInteractionUri() {
        return mInteractionUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mInteractionType);
        dest.writeString8(mCustomInteractionType);
        dest.writeString8(mThreadId);
        dest.writeTypedObject(mInteractionUri, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppFunctionAttribution that = (AppFunctionAttribution) o;
        return mInteractionType == that.mInteractionType
                && Objects.equals(mCustomInteractionType, that.mCustomInteractionType)
                && Objects.equals(mThreadId, that.mThreadId)
                && Objects.equals(mInteractionUri, that.mInteractionUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mInteractionType, mCustomInteractionType, mThreadId, mInteractionUri);
    }

    @Override
    public String toString() {
        return "AppFunctionAttribution("
                + "interactionType="
                + mInteractionType
                + ","
                + "customInteractionType="
                + mCustomInteractionType
                + ","
                + "threadId="
                + mThreadId
                + ","
                + "interactionUri="
                + mInteractionUri
                + ")";
    }

    /** Builder for {@link AppFunctionAttribution}. */
    public static final class Builder {
        private final int mInteractionType;

        @Nullable private String mCustomInteractionType;

        @Nullable private String mThreadId;

        @Nullable private Uri mInteractionUri;

        /**
         * Creates a new instance of this builder class.
         *
         * @param interactionType The interaction type. Must be one of {@link
         *     AppFunctionAttribution#INTERACTION_TYPE_OTHER}, {@link
         *     AppFunctionAttribution#INTERACTION_TYPE_USER_QUERY}, or {@link
         *     AppFunctionAttribution#INTERACTION_TYPE_USER_SCHEDULED}. If {@link
         *     AppFunctionAttribution#INTERACTION_TYPE_OTHER} is used, {@link
         *     #setCustomInteractionType(String)} must also be called.
         */
        public Builder(@InteractionType int interactionType) {
            mInteractionType = interactionType;
        }

        /**
         * Sets the custom interaction type to describe the interaction.
         *
         * <p>This method must be called if and only if the {@code interactionType} provided to the
         * constructor was {@link AppFunctionAttribution#INTERACTION_TYPE_OTHER}. The caller should
         * define a set of string constants for these custom interaction types and set them here
         * accordingly.
         *
         * @throws IllegalArgumentException If the interaction type is not {@link
         *     AppFunctionAttribution#INTERACTION_TYPE_OTHER}.
         */
        @NonNull
        public Builder setCustomInteractionType(@NonNull String customInteractionType) {
            if (mInteractionType != AppFunctionAttribution.INTERACTION_TYPE_OTHER) {
                throw new IllegalArgumentException(
                        "Cannot set customInteractionType because the interaction type is not "
                                + "INTERACTION_TYPE_OTHER");
            }
            mCustomInteractionType = Objects.requireNonNull(customInteractionType);
            return this;
        }

        /**
         * Sets a unique thread ID.
         *
         * <p>If a set of consequent AppFunctions are part of the same user interaction, an agent
         * should indicate this by specifying the same identifier across different AppFunction
         * calls. This information, if present, can be used in privacy settings to visually group
         * multiple AppFunctions together, aiding users in auditing related operations.
         *
         * <p>For example, this id can be the chat thread where the app function execution request
         * was initiated from.
         */
        @NonNull
        public Builder setThreadId(@Nullable String threadId) {
            mThreadId = threadId;
            return this;
        }

        // TODO(b/427996654): Update the document to use new Intent action.
        /**
         * Sets a deeplink {@link Uri} to the user request that initiated the app function
         * execution.
         *
         * <p>When set, this URI can be used by privacy settings to display a link in the audit
         * history, allowing users to navigate to the context of the original interaction.
         *
         * <p>For the link to be functional, the provided {@link Uri} <strong>must</strong> be
         * resolvable by an {@link android.content.Intent} with the action {@link
         * android.content.Intent#ACTION_VIEW}. To allow privacy settings to launch your activity,
         * the target {@link android.app.Activity} <strong>must</strong> declare a corresponding
         * {@code <intent-filter>} in your manifest.
         */
        @NonNull
        public Builder setInteractionUri(@Nullable Uri interactionUri) {
            mInteractionUri = interactionUri;
            return this;
        }

        /** Builds the {@link AppFunctionAttribution}. */
        @NonNull
        public AppFunctionAttribution build() {
            if (mInteractionType == INTERACTION_TYPE_OTHER && mCustomInteractionType == null) {
                throw new IllegalArgumentException(
                        "Must set customInteractionType since"
                                + " interactionType=INTERACTION_TYPE_OTHER");
            }
            return new AppFunctionAttribution(
                    mInteractionType, mCustomInteractionType, mThreadId, mInteractionUri);
        }
    }
}
