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

import static android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.appsearch.GenericDocument;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.permission.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

/**
 * Represents a {@link android.net.Uri} for which temporary access permission is to be granted to
 * the caller of an AppFunction execution.
 *
 * <p>This class encapsulates a {@link android.net.Uri} along with the specific access mode flags
 * (e.g., {@link android.content.Intent#FLAG_GRANT_READ_URI_PERMISSION}) that define the type of
 * temporary access to be granted for that URI. However, {@link
 * android.content.Intent#FLAG_GRANT_PERSISTABLE_URI_PERMISSION} is not allowed as only the
 * temporary access can be granted.
 *
 * <p>When an AppFunction implementation returns an {@link ExecuteAppFunctionResponse} containing
 * a {@link Uri}, the {@link Uri} itself must be placed in either
 * {@link ExecuteAppFunctionResponse#getResultDocument()} ()} or
 * {@link ExecuteAppFunctionResponse#getExtras()}. Concurrently, a corresponding
 * {@link AppFunctionUriGrant} detailing the intended permissions must be added to
 * {@link ExecuteAppFunctionResponse#getUriGrants()}. This ensures the App Function's caller
 * receives the necessary access rights to the returned {@link Uri}.
 *
 * <p>To succeed, the content provider owning the Uri must have set the {@link
 * android.R.styleable#AndroidManifestProvider_grantUriPermissions grantUriPermissions} attribute in
 * its manifest or included the {@link android.R.styleable#AndroidManifestGrantUriPermission
 * &lt;grant-uri-permissions&gt;} tag.
 *
 * @see ExecuteAppFunctionResponse#ExecuteAppFunctionResponse(GenericDocument, Bundle, List)
 * @see android.content.Intent#FLAG_GRANT_READ_URI_PERMISSION
 * @see android.content.Intent#FLAG_GRANT_WRITE_URI_PERMISSION
 * @see android.content.Intent#FLAG_GRANT_PREFIX_URI_PERMISSION
 */
@FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
public final class AppFunctionUriGrant implements Parcelable {
    private static final int ALLOWED_MODE_FLAG_MASK = FLAG_GRANT_READ_URI_PERMISSION
            | FLAG_GRANT_WRITE_URI_PERMISSION
            | FLAG_GRANT_PREFIX_URI_PERMISSION;

    @NonNull
    public static final Creator<AppFunctionUriGrant> CREATOR =
            new Creator<AppFunctionUriGrant>() {
                @Override
                public AppFunctionUriGrant createFromParcel(Parcel parcel) {
                    final Uri uri = Objects.requireNonNull(Uri.CREATOR.createFromParcel(parcel));
                    final int modeFlags = parcel.readInt();
                    return new AppFunctionUriGrant(uri, modeFlags);
                }

                @Override
                public AppFunctionUriGrant[] newArray(int size) {
                    return new AppFunctionUriGrant[size];
                }
            };

    /** The {@link android.net.Uri} to be granted. */
    @NonNull private final Uri mUri;

    /** The access mode flags. */
    @GrantUriMode private final int mModeFlags;

    /** @hide */
    @IntDef(
            flag = true,
            prefix = {"FLAG_GRANT_"},
            value = {
                FLAG_GRANT_READ_URI_PERMISSION,
                FLAG_GRANT_WRITE_URI_PERMISSION,
                FLAG_GRANT_PREFIX_URI_PERMISSION
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface GrantUriMode {}

    /**
     * Create a new {@link AppFunctionUriGrant}
     *
     * @param uri The {@link Uri} to be granted.
     * @param modeFlags The access mode flags.
     *     This value must include at least one of
     *     {@link android.content.Intent#FLAG_GRANT_READ_URI_PERMISSION} or
     *     {@link android.content.Intent#FLAG_GRANT_WRITE_URI_PERMISSION}.
     *     It may optionally also include
     *     {@link android.content.Intent#FLAG_GRANT_PREFIX_URI_PERMISSION}.
     */
    public AppFunctionUriGrant(@NonNull Uri uri, @GrantUriMode int modeFlags) {
        mUri = Objects.requireNonNull(uri);
        if ((modeFlags & ~ALLOWED_MODE_FLAG_MASK) != 0) {
            throw new IllegalArgumentException(
                    "Contains invalid flags: Allowed flags are FLAG_GRANT_READ_URI_PERMISSION, "
                            + "FLAG_GRANT_WRITE_URI_PERMISSION and "
                            + "FLAG_GRANT_PREFIX_URI_PERMISSION");
        }
        if (!Intent.isAccessUriMode(modeFlags)) {
            throw new IllegalArgumentException(
                    "Must set either FLAG_GRANT_READ_URI_PERMISSION or "
                            + "FLAG_GRANT_WRITE_URI_PERMISSION to specify the access mode");
        }
        mModeFlags = modeFlags;
    }

    /** Return the {@link Uri} to be granted. */
    @NonNull
    public Uri getUri() {
        return mUri;
    }

    /** Return the access mode flags. */
    @GrantUriMode
    public int getModeFlags() {
        return mModeFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppFunctionUriGrant that = (AppFunctionUriGrant) o;
        return mModeFlags == that.mModeFlags && mUri.equals(that.mUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUri, mModeFlags);
    }

    @NonNull
    @Override
    public String toString() {
        return "AppFunctionGrantUri("
                + "uri="
                + mUri.toString()
                + ","
                + "modeFlags"
                + mModeFlags
                + ")";
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Uri.writeToParcel(dest, mUri);
        dest.writeInt(mModeFlags);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
