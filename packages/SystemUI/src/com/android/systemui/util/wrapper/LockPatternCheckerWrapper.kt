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

package com.android.systemui.util.wrapper

import android.os.AsyncTask
import com.android.internal.widget.LockPatternChecker
import com.android.internal.widget.LockPatternChecker.OnCheckCallback
import com.android.internal.widget.LockPatternChecker.OnVerifyCallback
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.VerifyFlag
import com.android.internal.widget.LockscreenCredential
import javax.inject.Inject

/** Injectable wrapper around `LockPatternChecker` functions */
class LockPatternCheckerWrapper @Inject constructor() {
    fun verifyCredential(
        utils: LockPatternUtils,
        credential: LockscreenCredential,
        userId: Int,
        @VerifyFlag flags: Int,
        callback: OnVerifyCallback,
    ): AsyncTask<*, *, *> {
        return LockPatternChecker.verifyCredential(utils, credential, userId, flags, callback)
    }

    fun checkCredential(
        utils: LockPatternUtils,
        credential: LockscreenCredential,
        userId: Int,
        callback: OnCheckCallback,
    ): AsyncTask<*, *, *> {
        return LockPatternChecker.checkCredential(utils, credential, userId, callback)
    }

    /**
     * Perform a lockscreen credential verification explicitly on a managed profile with unified
     * challenge, using the parent user's credential.
     *
     * @param utils The LockPatternUtils instance to use.
     * @param credential The credential to check.
     * @param userId The user to check against the credential.
     * @param flags See [LockPatternUtils.VerifyFlag]
     * @param callback The callback to be invoked with the verification result.
     */
    fun verifyTiedProfileChallenge(
        utils: LockPatternUtils,
        credential: LockscreenCredential,
        userId: Int,
        @VerifyFlag flags: Int,
        callback: OnVerifyCallback,
    ): AsyncTask<*, *, *> {
        return LockPatternChecker.verifyTiedProfileChallenge(
            utils,
            credential,
            userId,
            flags,
            callback,
        )
    }
}
