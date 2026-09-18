/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app.admin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import android.app.admin.IDeviceProvisioningCallback;
import android.util.Log;

/**
 * Helper class to wait for the completion of multiuser device provisioning.
 *
 * @hide
 */
public class MultiuserDeviceProvisioningCompletion extends IDeviceProvisioningCallback.Stub {
    private static final String TAG = "MultiuserDeviceProvisioningCompletion";

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private volatile int mErrorCode = ProvisioningException.ERROR_OK;

    public void waitForCompletion(int timeoutSeconds) throws ProvisioningException {
        try {
            // Wait for a reasonable duration for the role assignment to complete.
            if (!mLatch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timeout waiting for MUM device provisioning.");
                throw new ProvisioningException(
                        new TimeoutException(),
                        ProvisioningException.ERROR_TIMEOUT,
                        "Provisioning timed out.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while waiting for MUM device provisioning.");
            throw new ProvisioningException(
                    e, ProvisioningException.ERROR_INTERRUPTED, "Interrupted.");
        }

        if (mErrorCode != ProvisioningException.ERROR_OK) {
            throw new ProvisioningException(
                    new Exception("Remote failure"), mErrorCode, "Provisioning failed.");
        }
    }

    @Override
    public void onSuccess() {
        mLatch.countDown();
    }

    @Override
    public void onFailure(int errorCode) {
        mErrorCode = errorCode;
        mLatch.countDown();
    }
}
