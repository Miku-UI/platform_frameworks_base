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

import android.content.pm.verify.developer.DeveloperVerificationSession;

/**
 * Oneway interface that allows the system to communicate to the developer verification service
 * provider.
 * @hide
 */
oneway interface IDeveloperVerifierService {
    void onPackageNameAvailable(in String packageName);
    void onVerificationCancelled(in String packageName);
    void onVerificationRequired(in DeveloperVerificationSession session);
    void onVerificationRetry(in DeveloperVerificationSession session);
    void onVerificationTimeout(int verificationId);
}