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
package android.companion;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * A result reported by a companion app in response to an {@link ActionRequest}.
 *
 * @see CompanionDeviceManager#notifyActionResult(int, ActionResult)
 */
@FlaggedApi(Flags.FLAG_ENABLE_DATA_SYNC)
public final class ActionResult implements Parcelable {
    /** @hide */
    @IntDef(prefix = {"RESULT_"}, value = {
            RESULT_SUCCESS,
            RESULT_FAILED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /**
     * A result code indicating that the requested action was completed successfully.
     */
    public static final int RESULT_SUCCESS = 0;

    /**
     * A result code indicating that the requested action failed.
     */
    public static final int RESULT_FAILED = 1;

    private final int mResultCode;
    private final ActionRequest mActionRequest;

    private ActionResult(@NonNull ActionRequest actionRequest, int resultCode) {
        this.mResultCode = resultCode;
        this.mActionRequest = actionRequest;
    }

    /**
     * @return the result code, e.g., {@link #RESULT_SUCCESS}.
     */
    public @ResultCode int getResultCode() {
        return mResultCode;
    }

    /**
     * @return The {@link ActionRequest} for this result.
     */
    @NonNull
    public ActionRequest getActionRequest() {
        return mActionRequest;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mResultCode);
        dest.writeTypedObject(mActionRequest, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionResult that = (ActionResult) o;
        return  mResultCode == that.mResultCode
                && Objects.equals(mActionRequest, that.mActionRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mResultCode, mActionRequest);
    }

    @Override
    public String toString() {
        return "ActionResult{"
                + ", mActionRequest=" + mActionRequest
                + ", mResultCode=" + mResultCode
                + '}';
    }

    @NonNull
    public static final Creator<ActionResult> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ActionResult createFromParcel(Parcel in) {
            return new ActionResult(in);
        }

        @Override
        @NonNull
        public ActionResult[] newArray(int size) {
            return new ActionResult[size];
        }
    };

    private ActionResult(Parcel in) {
        mResultCode = in.readInt();
        mActionRequest = in.readTypedObject(ActionRequest.CREATOR);
    }

    /**
     * Builder for creating an {@link ActionResult}.
     * @hide
     */
    @SystemApi
    public static final class Builder {

        private int mResultCode;
        private ActionRequest mActionRequest;

        /**
         * @param actionRequest The original {@link ActionRequest} for this result.
         * @param resultCode The result code for this result.
         */
        public Builder(@NonNull ActionRequest actionRequest, @ResultCode int resultCode) {
            Objects.requireNonNull(actionRequest, "ActionRequest cannot be null.");

            this.mActionRequest = actionRequest;
            this.mResultCode = resultCode;
        }

        /**
         * Builds the {@link ActionResult} object.
         */
        @NonNull
        public ActionResult build() {
            return new ActionResult(mActionRequest, mResultCode);
        }
    }
}
