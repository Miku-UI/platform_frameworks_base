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

package android.aiseal.tests;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.aiseal.AiSealException;
import android.aiseal.AiSealManager;
import android.aiseal.Flags;
import android.aiseal.tests.odim.IAiSealOdimPayloadService;
import android.aiseal.tests.odim.IAiSealOdimTestService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.util.concurrent.AbstractFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class AiSealManagerTest {
    private static final String TAG = AiSealManagerTest.class.getSimpleName();
    private Context mContext;
    private AiSealManager mAiSeal;
    private Intent mOdimTestServiceBindIntent = null;
    private OdimTestServiceConnection mOdimTestServiceConnection = null;

    @Before
    public void setUp() throws Exception {
        assumeTrue("AiSeal API is not available", Flags.aisealHostApis());
        mContext = getInstrumentation().getContext();
        mAiSeal = mContext.getSystemService(AiSealManager.class);
        assertNotNull("AiSeal manager is null", mAiSeal);
        mOdimTestServiceBindIntent = new Intent(IAiSealOdimTestService.class.getName());
        mOdimTestServiceBindIntent.setPackage(IAiSealOdimTestService.class.getPackage().getName());
    }

    @Test
    public void testAiSealManager_connectFromAppNotInTenantConfigThrowsException()
            throws Exception {
        assertThrows(
                "connectService should throw exception if called from app not in tenant config",
                AiSealException.class,
                () -> mAiSeal.connectService(IAiSealOdimPayloadService.SERVICE_NAME));
    }

    @Test
    public void testAiSealManager_connectThroughOdim() throws Exception {
        IAiSealOdimTestService service = getOdimTestService();
        String result = service.joinStringsWithSpace("hello", "world");
        assertEquals("Incorrect ODIM service response", "hello world", result);
    }

    @Test
    public void testAiSealManager_connectAfterPayloadExit() throws Exception {
        IAiSealOdimTestService service = getOdimTestService();
        service.exit(1);
        String result = service.joinStringsWithSpace("hello", "world");
        assertEquals("Incorrect ODIM service response", "hello world", result);
    }

    private class OdimTestServiceConnection extends AbstractFuture<IAiSealOdimTestService>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected " + name);
            set(IAiSealOdimTestService.Stub.asInterface(binder));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }

        @Override
        public IAiSealOdimTestService get() throws InterruptedException, ExecutionException {
            try {
                return get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new ExecutionException(e);
            }
        }
    }

    private @NonNull IAiSealOdimTestService getOdimTestService() throws Exception {
        if (mOdimTestServiceConnection == null) {
            mOdimTestServiceConnection = new OdimTestServiceConnection();
            mContext.bindService(
                    mOdimTestServiceBindIntent,
                    mOdimTestServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }
        IAiSealOdimTestService service = mOdimTestServiceConnection.get();
        assertNotNull("Could not connect to ODIM test service", service);
        return service;
    }
}
