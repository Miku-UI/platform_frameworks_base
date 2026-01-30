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
package com.android.server.pm;

import static android.content.pm.UserInfo.flagsToString;

import android.annotation.Nullable;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;

import java.util.Objects;

/**
 * Simple POJO use to filter user in methods like {@code getUsers()} or {@code hasUser()}.
 */
public final class UserFilter {

    private final boolean mIncludePartial;
    private final boolean mIncludeDying;
    private final @UserInfoFlag int mRequiredFlags;

    private UserFilter(Builder builder) {
        mIncludePartial = builder.mIncludePartial;
        mIncludeDying = builder.mIncludeDying;
        mRequiredFlags = builder.mRequiredFlags;
    }

    /**
     * Returns {@code true} if the given user matches the filter (and always {@code false} if it's
     * {@code null}).
     */
    boolean matches(DeathPredictor deathPredictor, @Nullable UserInfo user) {
        Objects.requireNonNull(deathPredictor, "deathPredictor cannot be null");
        if (user == null) {
            return false;
        }

        // Check below is the "legacy" checks from getUsersInternal(), but with inverted logic
        // (!include instead of exclude)
        if ((!mIncludePartial && user.partial)
                || user.preCreated // Not supported anymore, so ignored by filter
                || (!mIncludeDying && deathPredictor.isDying(user))) {
            return false;
        }

        // Check flags
        return (user.flags & mRequiredFlags) == mRequiredFlags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIncludeDying, mIncludePartial, mRequiredFlags);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        UserFilter other = (UserFilter) obj;
        return mIncludeDying == other.mIncludeDying && mIncludePartial == other.mIncludePartial
                && mRequiredFlags == other.mRequiredFlags;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder("UserFilter[");
        if (mIncludePartial) {
            string.append("includePartial, ");
        }
        if (mIncludeDying) {
            string.append("includeDying, ");
        }
        if (mRequiredFlags != 0) {
            string.append("requiredFlags=").append(flagsToString(mRequiredFlags));
        } else {
            // using else to avoid ending with ,
            string.append("noRequiredFlags");
        }
        return string.append(']').toString();
    }

    /**
     * Gets a {@link Builder} instance.
     *
     * <p>For now it's always returning a new one, but eventually it could be optimized (for
     * example, reusing the same builder that's backed by a {@link ThreadLocal} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /*
     * Bob, the Builder!
     *
     * <p>By default, it includes all users, without any filtering.
     */
    public static final class Builder {

        private boolean mIncludePartial;
        private boolean mIncludeDying;
        private @UserInfoFlag int mRequiredFlags;

        private Builder() {
        }

        /** Returns a new {@code UserFilter}, */
        public UserFilter build() {
            return new UserFilter(this);
        }

        /** Filter will Include partial users. */
        public Builder withPartialUsers() {
            mIncludePartial = true;
            return this;
        }

        /** Filter will Include dying users. */
        public Builder withDyingUsers() {
            mIncludeDying = true;
            return this;
        }

        /** When set, filter will only include users whose flags contain the given flags */
        public Builder setRequiredFlags(@UserInfoFlag int flags) {
            mRequiredFlags = flags;
            return this;
        }
    }

    /** Used to decide if a user is dying, as that information is not present in the user itself. */
    interface DeathPredictor {

        /** Returns {@code true} if the poor user is indeed dying... */
        boolean isDying(UserInfo userInfo);
    }
}
