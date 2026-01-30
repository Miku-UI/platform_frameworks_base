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

package android.companion.datatransfer.continuity;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.annotation.Nullable;
import android.graphics.drawable.Icon;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/**
 * This represents a task currently running on another device owned by the user. This is returned
 * by the {@link TaskContinuityManager#getRemoteTasks()} method.
 *
 * Consumers should use this class to display remote tasks to the user in an interface (such as
 * the device's launcher). When the user wishes to hand off this task to the current device, this
 * object can be passed back to {@link TaskContinuityManager#requestHandoff}.
 *
 * @hide
 *
 */
@SystemApi
@FlaggedApi(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
public final class RemoteTask implements Parcelable {

    private final int mId;
    private final int mDeviceId;
    @NonNull private final String mLabel;
    private final boolean mIsHandoffEnabled;
    @NonNull private final String mSourceDeviceName;
    private final long mLastUsedTimestampMillis;
    @Nullable private final Icon mIcon;

    public static final @NonNull Parcelable.Creator<RemoteTask> CREATOR =
        new Parcelable.Creator<RemoteTask>() {
            @Override
            public RemoteTask createFromParcel(Parcel in) {
                return new RemoteTask(in);
            }

            @Override
            public RemoteTask[] newArray(int size) {
                return new RemoteTask[size];
            }
        };

    RemoteTask(@NonNull Builder builder) {
        mId = builder.mId;
        mDeviceId = builder.mDeviceId;
        mLabel = builder.mLabel;
        mIcon = builder.mIcon;
        mIsHandoffEnabled = builder.mIsHandoffEnabled;
        mSourceDeviceName = builder.mSourceDeviceName;
        mLastUsedTimestampMillis = builder.mLastUsedTimestampMillis;
    }

    RemoteTask(@NonNull Parcel in) {
        mId = in.readInt();
        mDeviceId = in.readInt();
        mLabel = in.readString();
        mIsHandoffEnabled = in.readBoolean();
        mSourceDeviceName = in.readString();
        mLastUsedTimestampMillis = in.readLong();
        if (in.readInt() != 0) {
            mIcon = in.readParcelable(Icon.class.getClassLoader(), android.graphics.drawable.Icon.class);
        } else {
            mIcon = null;
        }
    }

    /**
     * Returns the ID of this task on the remote device. This is the same ID provided by
     * {@link ActivityManager.RunningTaskInfo#taskId}
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the device ID of the remote device. This is guaranteed to be a unique identifier
     * for the device.
     */
    public int getDeviceId() {
        return mDeviceId;
    }

    /**
     * Returns the label of this task on the remote device. This is the name of the {@link Activity}
     * represented by {@link ActivityTaskManager.RunningTaskInfo#baseActivity}
     */
    @NonNull
    public String getLabel() {
        return mLabel;
    }

    /**
     * Returns the icon of this task on the remote device. This is the icon of the {@link Activity}
     * represented by {@link ActivityTaskManager.RunningTaskInfo#baseActivity}
     */
    @Nullable
    public Icon getIcon() {
        return mIcon;
    }

    /**
     * Returns if this task is eligible to be handed off to the current device. This indicates the
     * topmost activity of the task on the remote device has enabled Handoff via
     * {@link Activity.setHandoffEnabled}.
     */
    public boolean isHandoffEnabled() {
        return mIsHandoffEnabled;
    }

    /**
     * Returns the name of the source device.
     */
    @NonNull
    public String getSourceDeviceName() {
        return mSourceDeviceName;
    }

    /**
     * Returns the last used timestamp of the task.
     */
    public long getLastUsedTimestampMillis() {
        return mLastUsedTimestampMillis;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            mId,
            mDeviceId,
            mLabel,
            mIcon,
            mIsHandoffEnabled,
            mSourceDeviceName,
            mLastUsedTimestampMillis);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RemoteTask) {
            RemoteTask other = (RemoteTask) o;
            return mId == other.mId
                    && mDeviceId == other.mDeviceId
                    && Objects.equals(mLabel, other.mLabel)
                    && (mIcon == null ? other.mIcon == null : mIcon.sameAs(other.mIcon))
                    && mIsHandoffEnabled == other.mIsHandoffEnabled
                    && mLastUsedTimestampMillis == other.mLastUsedTimestampMillis
                    && Objects.equals(mSourceDeviceName, other.mSourceDeviceName);
        }

        return false;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mDeviceId);
        dest.writeString(mLabel);
        dest.writeBoolean(mIsHandoffEnabled);
        dest.writeString(mSourceDeviceName);
        dest.writeLong(mLastUsedTimestampMillis);
        if (mIcon != null) {
            dest.writeInt(1);
            dest.writeParcelable(mIcon, flags);
        } else {
            dest.writeInt(0);
        }
    }

    /**
     * Builder for {@link RemoteTask}.
     */
    public static final class Builder {
        private int mId = 0;
        private int mDeviceId = 0;
        @NonNull private String mLabel = "";
        @Nullable private Icon mIcon = null;
        private boolean mIsHandoffEnabled = false;
        @NonNull private String mSourceDeviceName = "";
        private long mLastUsedTimestampMillis = 0;

        /**
         * Creates a new builder for a task with the given ID.
         *
         * @param id The ID of the task.
         */
        public Builder(int id) {
            mId = id;
        }

        /**
         * Sets the label of the task.
         *
         * @param label The label of the task.
         */
        @NonNull
        public Builder setLabel(@NonNull String label) {
            mLabel = label;
            return this;
        }

        /**
         * Sets the icon of the task.
         *
         * @param icon The icon of the task.
         */
        @NonNull
        public Builder setIcon(@Nullable Icon icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Sets the device ID of the remote device. This is guaranteed to be a unique identifier
         * for the device.
         *
         * @param deviceId The device ID of the remote device.
         */
        @NonNull
        public Builder setDeviceId(int deviceId) {
            mDeviceId = deviceId;
            return this;
        }

        /**
         * Sets if the task is eligible to be handed off to the current device.
         *
         * @param isHandoffEnabled Whether the task is eligible to be handed off to the current
         * device.
         */
        @NonNull
        public Builder setHandoffEnabled(boolean isHandoffEnabled) {
            mIsHandoffEnabled = isHandoffEnabled;
            return this;
        }

        /**
         * Sets the name of the source device.
         *
         * @param sourceDeviceName The name of the source device.
         */
        @NonNull
        public Builder setSourceDeviceName(@NonNull String sourceDeviceName) {
            mSourceDeviceName = sourceDeviceName;
            return this;
        }

        /**
         * Sets the last used timestamp of the task.
         *
         * @param lastUsedTimestampMillis The last used timestamp of the remote task.
         */
        @NonNull
        public Builder setLastUsedTimestampMillis(long lastUsedTimestampMillis) {
            mLastUsedTimestampMillis = lastUsedTimestampMillis;
            return this;
        }

        /**
         * Builds the {@link RemoteTask} from the builder.
         *
         * @return The {@link RemoteTask} from the builder.
         */
        @NonNull
        public RemoteTask build() {
            return new RemoteTask(this);
        }
    }
}