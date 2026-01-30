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

package com.android.server.locksettings;

import android.annotation.UserIdInt;

/**
 * This identifies a Lock Screen Knowledge Factor (LSKF) to which rate-limiting is independently
 * applied. Specifically it identifies one of the following:
 *
 * <ul>
 *   <li>A user's LSKF-based synthetic password protector. <code>userId</code> and <code>protectorId
 *       </code> are the corresponding IDs.
 *   <li>A special credential such as the device's Factory Reset Protection (FRP) credential or
 *       repair mode exit credential. In this case, <code>userId</code> is a special value such as
 *       <code>USER_FRP</code> or <code>USER_REPAIR_MODE</code>, and <code>protectorId</code> is
 *       <code>NULL_PROTECTOR_ID</code>.
 * </ul>
 */
class LskfIdentifier {
    public final @UserIdInt int userId;
    public final long protectorId;

    LskfIdentifier(@UserIdInt int userId, long protectorId) {
        this.userId = userId;
        this.protectorId = protectorId;
    }

    boolean isSpecialCredential() {
        return this.protectorId == SyntheticPasswordManager.NULL_PROTECTOR_ID;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LskfIdentifier)) {
            return false;
        }
        LskfIdentifier other = (LskfIdentifier) obj;
        return this.userId == other.userId && this.protectorId == other.protectorId;
    }

    @Override
    public int hashCode() {
        return 31 * this.userId + (int) this.protectorId;
    }
}
