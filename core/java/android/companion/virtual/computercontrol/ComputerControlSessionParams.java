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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppInteractionAttribution;
import android.app.Notification;
import android.app.PendingIntent;
import android.companion.virtual.CompanionDeviceId;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parameters for creating a {@link ComputerControlSession}.
 *
 * @hide
 */
public final class ComputerControlSessionParams implements Parcelable {

    private static final int MAX_TARGET_PACKAGES = 6;

    public static final int MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 = 5;

    private final String mName;
    private final int mTargetComputerControlVersion;
    private final List<String> mTargetPackageNames;
    private final PendingIntent mPreviewIntent;
    private final AppInteractionAttribution mAppInteractionAttribution;
    private final CompanionDeviceId mCompanionDeviceId;
    @Nullable
    private final NotificationParams mNotificationParams;

    private ComputerControlSessionParams(
            @NonNull String name,
            int targetComputerControlVersion,
            @NonNull List<String> targetPackageNames,
            @Nullable PendingIntent previewIntent,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @Nullable CompanionDeviceId companionDeviceId,
            @Nullable NotificationParams notificationParams) {
        mName = name;
        mTargetComputerControlVersion = targetComputerControlVersion;
        mTargetPackageNames = targetPackageNames;
        mPreviewIntent = previewIntent;
        mAppInteractionAttribution = appInteractionAttribution;
        mCompanionDeviceId = companionDeviceId;
        mNotificationParams = notificationParams;
    }

    private ComputerControlSessionParams(Parcel parcel) {
        mName = parcel.readString8();
        mTargetPackageNames = new ArrayList<>();
        parcel.readStringList(mTargetPackageNames);
        mPreviewIntent = parcel.readTypedObject(PendingIntent.CREATOR);
        mAppInteractionAttribution = parcel.readTypedObject(AppInteractionAttribution.CREATOR);
        mTargetComputerControlVersion = parcel.readInt();
        mCompanionDeviceId = parcel.readTypedObject(CompanionDeviceId.CREATOR);
        mNotificationParams = parcel.readTypedObject(NotificationParams.CREATOR);
    }

    /** Returns the name of this computer control session. */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns the target computer control version of the computer control session. */
    public int getTargetComputerControlVersion() {
        return mTargetComputerControlVersion;
    }

    /** Returns the package names of the applications that can be automated during this session. */
    @NonNull
    public List<String> getTargetPackageNames() {
        return mTargetPackageNames;
    }

    /**
     * Returns the intent launched when the user wants to preview the automation, or null if none is
     * set.
     */
    @Nullable
    public PendingIntent getPreviewIntent() {
        return mPreviewIntent;
    }

    /**
     * Returns the attribution for the app interaction that triggered the creation of this session.
     */
    @Nullable
    public AppInteractionAttribution getAppInteractionAttribution() {
        return mAppInteractionAttribution;
    }

    /**
     * Returns the companion device id of the device that is controlling this session.
     */
    @Nullable
    public CompanionDeviceId getCompanionDeviceId() {
        return mCompanionDeviceId;
    }

    /** Returns the notification parameters for this session. */
    @Nullable
    public NotificationParams getNotificationParams() {
        return mNotificationParams;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mName);
        dest.writeStringList(mTargetPackageNames);
        dest.writeTypedObject(mPreviewIntent, flags);
        dest.writeTypedObject(mAppInteractionAttribution, flags);
        dest.writeInt(mTargetComputerControlVersion);
        dest.writeTypedObject(mCompanionDeviceId, flags);
        dest.writeTypedObject(mNotificationParams, flags);
    }

    @NonNull
    public static final Creator<ComputerControlSessionParams> CREATOR =
            new Creator<>() {
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
        private int mTargetComputerControlVersion = 0;
        private List<String> mTargetPackageNames;
        private PendingIntent mPreviewIntent;
        private AppInteractionAttribution mAppInteractionAttribution;
        private CompanionDeviceId mCompanionDeviceId = null;
        private NotificationParams mNotificationParams = null;

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
         *
         * <ol>
         *   <li>The package name has a valid launcher Intent.
         *   <li>The package name is not the device permission controller.
         * </ol>
         */
        @NonNull
        public Builder setTargetPackageNames(@NonNull List<String> targetPackageNames) {
            if (targetPackageNames == null || targetPackageNames.isEmpty()) {
                throw new IllegalArgumentException("Target package names must not be empty");
            }
            mTargetPackageNames = targetPackageNames;
            return this;
        }

        /**
         * Sets the intent launched when the user wants to preview the automation, or null if none.
         *
         * @param previewIntent The intent to launch the preview UI.
         * @return This builder.
         */
        @NonNull
        public Builder setPreviewIntent(@Nullable PendingIntent previewIntent) {
            mPreviewIntent = previewIntent;
            return this;
        }

        /**
         * Sets the attribution for the app interaction that triggered the creation of this session.
         *
         * @param appInteractionAttribution The attribution for the app interaction.
         * @return This builder.
         */
        @NonNull
        public Builder setAppInteractionAttribution(
                @Nullable AppInteractionAttribution appInteractionAttribution) {
            mAppInteractionAttribution = appInteractionAttribution;
            return this;
        }

        /**
         * Sets the companion device id of the device that is controlling this session.
         *
         * @param companionDeviceId The companion device id.
         * @return This builder.
         */
        @NonNull
        public Builder setCompanionDeviceId(@Nullable CompanionDeviceId companionDeviceId) {
            mCompanionDeviceId = companionDeviceId;
            return this;
        }

        /**
         * Sets the target computer control version of the computer control session.
         *
         * @param targetComputerControlVersion The target computer control version.
         * @return This builder.
         */
        @NonNull
        public Builder setTargetComputerControlVersion(int targetComputerControlVersion) {
            mTargetComputerControlVersion = targetComputerControlVersion;
            return this;
        }

        /**
         * Sets the notification parameters for this session.
         *
         * <p>The notification gets posted when the session is created, and canceled when the
         * session is closed. It cannot be dismissed by the user, or canceled by the caller.
         * However, the caller can update the contents of the notification at any time,
         * by using {@link android.app.NotificationManager#notify}. In fact, callers should re-use
         * the same notification for their own foreground service (if any), to avoid any duplicate
         * notifications.
         *
         * <p>{@link Notification#hasPromotableCharacteristics()} must return {@code true} for the
         * notification that is passed, otherwise {@link IllegalArgumentException} is thrown.
         *
         * @param notificationParams The notification parameters.
         * @return This builder.
         */
        @NonNull
        public Builder setNotificationParams(@Nullable NotificationParams notificationParams) {
            mNotificationParams = notificationParams;
            return this;
        }

        /**
         * Builds the {@link ComputerControlSessionParams} instance.
         *
         * @return The built {@link ComputerControlSessionParams}.
         * @throws IllegalArgumentException if any of the required arguments are not set.
         */
        @NonNull
        public ComputerControlSessionParams build() {
            if (mName == null || mName.isEmpty()) {
                throw new IllegalArgumentException("Name must be set");
            }
            if (mTargetPackageNames == null || mTargetPackageNames.isEmpty()) {
                throw new IllegalArgumentException("Target package names must be set");
            }

            if (mTargetComputerControlVersion >= MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17) {
                if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()
                        && mAppInteractionAttribution == null) {
                    throw new IllegalArgumentException(
                            "App interaction attribution must be set");
                }
                if (mNotificationParams == null) {
                    throw new IllegalArgumentException(
                            "Notification parameters must be set");
                }
                if (mTargetPackageNames.size() > MAX_TARGET_PACKAGES) {
                    throw new IllegalArgumentException(
                            "Number of target package names must not exceed "
                                    + MAX_TARGET_PACKAGES);
                }
            }

            if (mCompanionDeviceId != null
                    && mTargetComputerControlVersion
                    < MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17) {
                throw new IllegalArgumentException(
                        "companionDeviceId can only be used with targetComputerControlVersion "
                                + MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 + " or above");
            }

            return new ComputerControlSessionParams(
                    mName,
                    mTargetComputerControlVersion,
                    mTargetPackageNames,
                    mPreviewIntent,
                    mAppInteractionAttribution,
                    mCompanionDeviceId,
                    mNotificationParams);
        }
    }

    /**
     * Parameters for the notification associated with this session.
     */
    public static final class NotificationParams implements Parcelable {
        @NonNull
        private final Notification mNotification;
        private final int mNotificationId;
        @Nullable
        private final String mNotificationTag;

        private NotificationParams(@NonNull Notification notification, int notificationId,
                @Nullable String notificationTag) {
            mNotification = notification;
            mNotificationId = notificationId;
            mNotificationTag = notificationTag;
        }

        private NotificationParams(Parcel in) {
            mNotification = in.readTypedObject(Notification.CREATOR);
            mNotificationId = in.readInt();
            mNotificationTag = in.readString8();
        }

        /** Returns the notification to be posted. */
        @NonNull
        public Notification getNotification() {
            return mNotification;
        }

        /** Returns the id of the notification. */
        public int getNotificationId() {
            return mNotificationId;
        }

        /** Returns the tag of the notification. */
        @Nullable
        public String getNotificationTag() {
            return mNotificationTag;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeTypedObject(mNotification, flags);
            dest.writeInt(mNotificationId);
            dest.writeString8(mNotificationTag);
        }

        @NonNull
        public static final Creator<NotificationParams> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public NotificationParams createFromParcel(@NonNull Parcel in) {
                return new NotificationParams(in);
            }

            @Override
            @NonNull
            public NotificationParams[] newArray(int size) {
                return new NotificationParams[size];
            }
        };

        /** Builder for {@link NotificationParams}. */
        public static final class Builder {
            @NonNull
            private final Notification mNotification;
            private final int mNotificationId;
            @Nullable
            private String mNotificationTag;

            /**
             * @param notification the {@link Notification} associated with this session
             * @param notificationId the identifier for the notification, as per
             * {@link android.app.NotificationManager#notify(String, int, Notification)}
             */
            public Builder(@NonNull Notification notification, int notificationId) {
                Objects.requireNonNull(notification, "Notification must not be null");
                if (!notification.hasPromotableCharacteristics()) {
                    throw new IllegalArgumentException(
                            "Notification must have promotable characteristics,"
                                    + " i.e., notification.hasPromotableCharacteristics() must"
                                    + " return true");
                }
                mNotification = notification;
                mNotificationId = notificationId;
            }

            /**
             * Sets the optional tag for the notification.
             *
             * @param notificationTag the tag for the notification, as per
             * {@link android.app.NotificationManager#notify(String, int, Notification)}
             */
            @NonNull
            public Builder setNotificationTag(@Nullable String notificationTag) {
                mNotificationTag = notificationTag;
                return this;
            }

            /** Builds the {@link NotificationParams} instance. */
            @NonNull
            public NotificationParams build() {
                return new NotificationParams(mNotification, mNotificationId, mNotificationTag);
            }
        }
    }
}
