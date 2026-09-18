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

package android.aiseal.tests.odim;

import android.aiseal.AiSealManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.format.DateUtils;
import android.util.Log;
import androidx.annotation.GuardedBy;

public class AiSealOdimTestService extends Service {
    private static final String TAG = AiSealOdimTestService.class.getSimpleName();
    private final AiSealOdimTestServiceImpl mService = new AiSealOdimTestServiceImpl();

    @Override
    public void onCreate() {
        super.onCreate();
        mService.onCreate();
    }

    @Override
    public void onDestroy() {
        mService.onDestroy();
        super.onDestroy();
    }

    public class AiSealOdimTestServiceImpl extends IAiSealOdimTestService.Stub {
        private final HandlerThread mHandlerThread =
                new HandlerThread("AiSealOdimTestServiceThread");
        private Handler mHandler;

        // Synchronizes AiSeal service connection.
        private static final Object sLock = new Object();

        @GuardedBy("sLock")
        private IAiSealOdimPayloadService mGuestService;

        public void onCreate() {
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            mHandler.post(
                    () -> {
                        connectGuestService();
                    });
        }

        public void onDestroy() {
            mHandlerThread.quitSafely();
        }

        private void connectGuestService() {
            synchronized (sLock) {
                connectGuestServiceLocked();
            }
        }

        @GuardedBy("sLock")
        private void connectGuestServiceLocked() {
            // Reset the service to null if we are currently connected to it.
            mGuestService = null;
            sLock.notifyAll();

            IBinder binder = null;
            try {
                AiSealManager mAiSeal =
                        getApplicationContext().getSystemService(AiSealManager.class);
                binder = mAiSeal.connectService(IAiSealOdimPayloadService.SERVICE_NAME);
                binder.linkToDeath(
                        () -> {
                            Log.w(TAG, "Guest service died");
                            connectGuestService();
                        },
                        0);
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to guest service", e);
            }
            if (binder != null) {
                mGuestService = IAiSealOdimPayloadService.Stub.asInterface(binder);
                sLock.notifyAll();
            } else {
                Log.i(TAG, "Guest service not yet available; trying again");
                mHandler.postDelayed(
                        () -> {
                            connectGuestService();
                        },
                        DateUtils.SECOND_IN_MILLIS);
            }
        }

        private IAiSealOdimPayloadService waitForGuestService() throws InterruptedException {
            synchronized (sLock) {
                while (mGuestService == null) {
                    Log.i(TAG, "Waiting for guest service...");
                    sLock.wait();
                }
                return mGuestService;
            }
        }

        @Override
        public String joinStringsWithSpace(String a, String b) {
            Log.d(TAG, "joinStringsWithSpace()");
            try {
                var guest = waitForGuestService();
                return guest.joinStringsWithSpace(a, b);
            } catch (Exception e) {
                Log.e(TAG, "Failed to call guest service", e);
                throw new IllegalStateException("Failed to call guest service", e);
            }
        }

        @Override
        public void exit(int status) {
            Log.d(TAG, "exit()");
            try {
                var guest = waitForGuestService();
                guest.exit(status);
                synchronized (sLock) {
                    while (mGuestService != null) {
                        Log.i(TAG, "Waiting for guest service to stop...");
                        sLock.wait();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop guest service", e);
                throw new IllegalStateException("Failed to stop guest service", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mService;
    }
}
