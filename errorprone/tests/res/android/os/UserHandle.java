/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.os;

/** Test-only barebones version of UserHandle.java. */
public class UserHandle {

    public static int getUserId(int uid) {
        throw new UnsupportedOperationException();
    }

    public static int getAppId(int uid) {
        throw new UnsupportedOperationException();
    }

    public static int getUid(int userId, int appId) {
        throw new UnsupportedOperationException();
    }

    public static int myUserId() {
        throw new UnsupportedOperationException();
    }

    public static final int USER_ALL = -1;

    public static final UserHandle ALL = null;

    public static final int USER_CURRENT = -2;

    public static final UserHandle CURRENT = null;

    public static final int USER_CURRENT_OR_SELF = -3;

    public static final UserHandle CURRENT_OR_SELF = null;

    public static final int USER_NULL = -10000;

    public static final UserHandle NULL = null;

    public static UserHandle of(int id) {
        return new UserHandle();
    }
}
