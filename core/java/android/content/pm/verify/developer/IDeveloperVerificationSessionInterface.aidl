/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.pm.verify.developer;

import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.os.PersistableBundle;

/**
 * Non-oneway interface that allows the developer verifier to communicate with the system.
 * @hide
 */
interface IDeveloperVerificationSessionInterface {
    long getTimeoutTimeMillis(int verificationId);
    long extendTimeoutMillis(int verificationId, long additionalMillis);
    boolean setVerificationPolicy(int verificationId, int policy);
    void reportVerificationIncomplete(int verificationId, int reason);
    void reportVerificationComplete(int verificationId, in DeveloperVerificationStatus status, in @nullable PersistableBundle extensionResponse);
    void reportVerificationBypassed(int verificationId, int bypassReason);
}