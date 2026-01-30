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

package android.companion.virtual.computercontrol;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for creating a {@link ComputerControlSession}.
 *
 * @hide
 */
public final class ComputerControlSessionParams implements Parcelable {

    private final String mName;
    private final List<String> mTargetPackageNames;
    private final int mDisplayWidthPx;
    private final int mDisplayHeightPx;
    private final int mDisplayDpi;
    private final Surface mDisplaySurface;
    private final boolean mIsDisplayAlwaysUnlocked;

    private ComputerControlSessionParams(
            @NonNull String name,
            @Nullable List<String> targetPackageNames,  // TODO(b/437849228): Should be non-null
            int displayWidthPx,
            int displayHeightPx,
            int displayDpi,
            @Nullable Surface displaySurface,
            boolean isDisplayAlwaysUnlocked) {
        mName = name;
        mTargetPackageNames = targetPackageNames;
        mDisplayWidthPx = displayWidthPx;
        mDisplayHeightPx = displayHeightPx;
        mDisplayDpi = displayDpi;
        mDisplaySurface = displaySurface;
        mIsDisplayAlwaysUnlocked = isDisplayAlwaysUnlocked;
    }

    private ComputerControlSessionParams(Parcel parcel) {
        mName = parcel.readString8();
        mTargetPackageNames = new ArrayList<>();
        parcel.readStringList(mTargetPackageNames);
        mDisplayWidthPx = parcel.readInt();
        mDisplayHeightPx = parcel.readInt();
        mDisplayDpi = parcel.readInt();
        mDisplaySurface = parcel.readTypedObject(Surface.CREATOR);
        mIsDisplayAlwaysUnlocked = parcel.readBoolean();
    }

    /** Returns the name of this computer control session. */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns the package names of the applications that can be automated during this session. */
    @Nullable  // TODO(b/437849228): Should be non-null
    public List<String> getTargetPackageNames() {
        return mTargetPackageNames;
    }

    /** Returns the width of the display, in pixels. */
    public int getDisplayWidthPx() {
        return mDisplayWidthPx;
    }

    /** Returns the height of the display, in pixels. */
    public int getDisplayHeightPx() {
        return mDisplayHeightPx;
    }

    /** Returns the density of the display, in dpi. */
    public int getDisplayDpi() {
        return mDisplayDpi;
    }

    /** Returns the surface to which the display content should be rendered. */
    @Nullable
    public Surface getDisplaySurface() {
        return mDisplaySurface;
    }

    /** Returns true if the display should be always unlocked. */
    public boolean isDisplayAlwaysUnlocked() {
        return mIsDisplayAlwaysUnlocked;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mName);
        dest.writeStringList(mTargetPackageNames);
        dest.writeInt(mDisplayWidthPx);
        dest.writeInt(mDisplayHeightPx);
        dest.writeInt(mDisplayDpi);
        dest.writeTypedObject(mDisplaySurface, flags);
        dest.writeBoolean(mIsDisplayAlwaysUnlocked);
    }

    @NonNull
    public static final Creator<ComputerControlSessionParams> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ComputerControlSessionParams createFromParcel(@NonNull Parcel in) {
            return new ComputerControlSessionParams(in);
        }

        @Override
        @NonNull
        public ComputerControlSessionParams[] newArray(int size) {
            return new ComputerControlSessionParams[size];
        }
    };

    /** Builder for {@link ComputerControlSessionParams}. */
    public static final class Builder {
        private String mName;
        private List<String> mTargetPackageNames;
        private int mDisplayWidthPx;
        private int mDisplayHeightPx;
        private int mDisplayDpi;
        private Surface mDisplaySurface;
        private boolean mIsDisplayAlwaysUnlocked;

        /**
         * Sets the name of this computer control session.
         *
         * @param name The name of the session.
         * @return This builder.
         */
        @NonNull
        public Builder setName(@NonNull String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Name must not be empty");
            }
            mName = name;
            return this;
        }


        /**
         * Set the package names of all applications that may be automated during this session.
         *
         * <p>All package names specified in the list must meet the following requirements:
         * <ol>
         *     <li>The package name has a valid launcher Intent.</li>
         *     <li>The package name is not the device permission controller.</li>
         * </ol>
         */
        @Nullable  // TODO(b/437849228): Should be non-null
        public Builder setTargetPackageNames(@NonNull List<String> targetPackageNames) {
            // TODO(b/437849228): Check for null and non-empty
            mTargetPackageNames = targetPackageNames;
            return this;
        }

        /**
         * Sets the width of the display, in pixels.
         *
         * @param displayWidthPx The width of the display.
         * @return This builder.
         */
        @NonNull
        public Builder setDisplayWidthPx(@IntRange(from = 1) int displayWidthPx) {
            mDisplayWidthPx = displayWidthPx;
            return this;
        }

        /**
         * Sets the height of the display, in pixels.
         *
         * @param displayHeightPx The height of the display.
         * @return This builder.
         */
        @NonNull
        public Builder setDisplayHeightPx(@IntRange(from = 1) int displayHeightPx) {
            mDisplayHeightPx = displayHeightPx;
            return this;
        }

        /**
         * Sets the density of the display, in dpi.
         *
         * @param displayDpi The density of the display.
         * @return This builder.
         */
        @NonNull
        public Builder setDisplayDpi(@IntRange(from = 1) int displayDpi) {
            mDisplayDpi = displayDpi;
            return this;
        }

        /**
         * Sets the surface to which the display content should be rendered.
         *
         * @param displaySurface The surface for the display.
         * @return This builder.
         */
        @NonNull
        public Builder setDisplaySurface(@NonNull Surface displaySurface) {
            mDisplaySurface = displaySurface;
            return this;
        }

        /**
         * Sets whether the display should be always unlocked.
         *
         * @param isDisplayAlwaysUnlocked true if the display should be always unlocked.
         * @return This builder.
         */
        @NonNull
        public Builder setDisplayAlwaysUnlocked(boolean isDisplayAlwaysUnlocked) {
            mIsDisplayAlwaysUnlocked = isDisplayAlwaysUnlocked;
            return this;
        }

        /**
         * Builds the {@link ComputerControlSessionParams} instance.
         *
         * @return The built {@link ComputerControlSessionParams}.
         * @throws IllegalArgumentException if the name or surface are not set, or if the display
         *     width, height, or dpi are not positive.
         */
        @NonNull
        public ComputerControlSessionParams build() {
            if (mName == null || mName.isEmpty()) {
                throw new IllegalArgumentException("Name must be set");
            }
            // TODO(b/437849228): Do not allow for unset targetPackageNames
            if (mDisplaySurface != null) {
                if (mDisplayWidthPx <= 0) {
                    throw new IllegalArgumentException(
                            "Display width must be positive if surface is set");
                }
                if (mDisplayHeightPx <= 0) {
                    throw new IllegalArgumentException(
                            "Display height must be positive if surface is set");
                }
                if (mDisplayDpi <= 0) {
                    throw new IllegalArgumentException(
                            "Display DPI must be positive if surface is set");
                }
            }
            return new ComputerControlSessionParams(
                    mName,
                    mTargetPackageNames,
                    mDisplayWidthPx,
                    mDisplayHeightPx,
                    mDisplayDpi,
                    mDisplaySurface,
                    mIsDisplayAlwaysUnlocked);
        }
    }
}
