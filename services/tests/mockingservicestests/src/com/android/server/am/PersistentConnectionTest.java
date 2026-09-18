/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.am;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.IDeviceAdminService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersistentConnectionTest {
    private static final String TAG = "PersistentConnectionTest";

    private static final int USER_ID = 11;
    private static final ComponentName COMPONENT_NAME =
            ComponentName.unflattenFromString("a.b.c/def");

    private Context mContext;
    private Handler mHandler;
    private MyConnection mConn;

    private static final class MyConnection extends PersistentConnection<IDeviceAdminService> {
        public long uptimeMillis = 12345;

        public ArrayList<Pair<Runnable, Long>> scheduledRunnables = new ArrayList<>();

        public MyConnection(String tag, Context context, Handler handler, int userId,
                ComponentName componentName, long rebindBackoffSeconds,
                double rebindBackoffIncrease, long rebindMaxBackoffSeconds,
                long resetBackoffDelay) {
            super(tag, context, handler, userId, componentName,
                    rebindBackoffSeconds, rebindBackoffIncrease, rebindMaxBackoffSeconds,
                    resetBackoffDelay);
        }

        @Override
        protected int getBindFlags() {
            return Context.BIND_FOREGROUND_SERVICE;
        }

        @Override
        protected IDeviceAdminService asInterface(IBinder binder) {
            return (IDeviceAdminService) binder;
        }

        @Override
        long injectUptimeMillis() {
            return uptimeMillis;
        }

        @Override
        void injectPostAtTime(Runnable r, long uptimeMillis) {
            scheduledRunnables.add(Pair.create(r, uptimeMillis));
        }

        @Override
        void injectRemoveCallbacks(Runnable r) {
            for (int i = scheduledRunnables.size() - 1; i >= 0; i--) {
                if (scheduledRunnables.get(i).first.equals(r)) {
                    scheduledRunnables.remove(i);
                }
            }
        }

        void elapse(long milliSeconds) {
            uptimeMillis += milliSeconds;

            // Fire the scheduled runnables.

            // Note we collect first and then run all, because sometimes a scheduled runnable
            // calls removeCallbacks.
            final ArrayList<Runnable> list = new ArrayList<>();

            for (int i = scheduledRunnables.size() - 1; i >= 0; i--) {
                if (scheduledRunnables.get(i).second <= uptimeMillis) {
                    list.add(scheduledRunnables.get(i).first);
                    scheduledRunnables.remove(i);
                }
            }

            Collections.reverse(list);
            for (Runnable r : list) {
                r.run();
            }
        }
    }

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mHandler = new Handler(Looper.getMainLooper());

        mConn = new MyConnection(TAG, mContext, mHandler, USER_ID, COMPONENT_NAME,
                /* rebindBackoffSeconds= */ 5,
                /* rebindBackoffIncrease= */ 1.5,
                /* rebindMaxBackoffSeconds= */ 11,
                /* resetBackoffDelay= */ 999);

        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class), anyInt(),
                any(Handler.class), any(UserHandle.class)))
                .thenReturn(true);
    }

    @Test
    public void testInitialState_andBind() {
        assertFalse(mConn.isBound());
        assertFalse(mConn.isConnected());
        assertFalse(mConn.isRebindScheduled());
        assertEquals(5000, mConn.getNextBackoffMsForTest());
        assertNull(mConn.getServiceBinder());

        // Call bind.
        mConn.bind();

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertFalse(mConn.isRebindScheduled());
        assertNull(mConn.getServiceBinder());

        assertEquals(5000, mConn.getNextBackoffMsForTest());

        verify(mContext).bindServiceAsUser(
                ArgumentMatchers.argThat(
                        intent -> Objects.equals(COMPONENT_NAME, intent.getComponent())),
                eq(mConn.getServiceConnectionForTest()),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(mHandler), eq(UserHandle.of(USER_ID)));

        // AM responds...
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertTrue(mConn.isConnected());
        assertNotNull(mConn.getServiceBinder());
        assertFalse(mConn.isRebindScheduled());

        assertEquals(5000, mConn.getNextBackoffMsForTest());
    }

    @Test
    public void testUnbind() {
        // First, bind and connect.
        mConn.bind();
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});
        assertTrue(mConn.isConnected());

        // Now connected. Call unbind...
        mConn.unbind();
        assertFalse(mConn.isBound());
        assertFalse(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertNull(mConn.getServiceBinder());
        assertFalse(mConn.isRebindScheduled());
    }

    @Test
    public void testServiceDisconnected() {
        // First, bind and connect.
        mConn.bind();
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        // The service got killed...
        mConn.getServiceConnectionForTest().onServiceDisconnected(COMPONENT_NAME);

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertNull(mConn.getServiceBinder());
        assertFalse(mConn.isRebindScheduled());
        assertEquals(5000, mConn.getNextBackoffMsForTest());
    }

    @Test
    public void testBindingDied_rebindsWithBackoff() {
        mConn.bind();
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        // Then the binding is "died"...
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);

        assertFalse(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertNull(mConn.getServiceBinder());
        assertTrue(mConn.isRebindScheduled());

        assertEquals(7500, mConn.getNextBackoffMsForTest());

        assertEquals(
                Collections.singletonList(Pair.create(mConn.getBindForBackoffRunnableForTest(),
                        mConn.uptimeMillis + 5000)),
                mConn.scheduledRunnables);

        // 5000 ms later...
        mConn.elapse(5000);

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertNull(mConn.getServiceBinder());
        assertFalse(mConn.isRebindScheduled());

        assertEquals(7500, mConn.getNextBackoffMsForTest());
    }

    @Test
    public void testBindingDied_increasesBackoff() {
        mConn.bind();
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        // First death
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);
        assertEquals(7500, mConn.getNextBackoffMsForTest());
        mConn.elapse(5000); // Rebind happens
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        // Second death
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);
        assertEquals(11000, mConn.getNextBackoffMsForTest()); // 7500 * 1.5 = 11250, capped at 11000
        assertTrue(mConn.isRebindScheduled());

        assertEquals(
                Collections.singletonList(Pair.create(mConn.getBindForBackoffRunnableForTest(),
                        mConn.uptimeMillis + 7500)),
                mConn.scheduledRunnables);

        mConn.elapse(7500); // Rebind happens
        assertFalse(mConn.isRebindScheduled());

        // Third death, backoff is capped.
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);
        assertEquals(11000, mConn.getNextBackoffMsForTest());
        assertTrue(mConn.isRebindScheduled());
    }

    @Test
    public void testUnbind_resetsBackoff() {
        mConn.bind();
        mConn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME); // backoff increases
        assertTrue(mConn.isRebindScheduled());
        assertEquals(7500, mConn.getNextBackoffMsForTest());

        // Call unbind...
        mConn.unbind();
        assertFalse(mConn.isBound());
        assertFalse(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertNull(mConn.getServiceBinder());
        assertFalse(mConn.isRebindScheduled());

        // Call bind again... And now the backoff is reset to 5000.
        mConn.bind();

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isConnected());
        assertFalse(mConn.isRebindScheduled());
        assertNull(mConn.getServiceBinder());
        assertEquals(5000, mConn.getNextBackoffMsForTest());
    }

    @Test
    public void testReconnectFiresAfterUnbind() {
        // Bind.
        mConn.bind();

        assertTrue(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertFalse(mConn.isRebindScheduled());

        mConn.elapse(1000);

        // Service crashes.
        mConn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);

        assertFalse(mConn.isBound());
        assertTrue(mConn.shouldBeBoundForTest());
        assertTrue(mConn.isRebindScheduled());

        assertEquals(7500, mConn.getNextBackoffMsForTest());

        // Call unbind.
        mConn.unbind();
        assertFalse(mConn.isBound());
        assertFalse(mConn.shouldBeBoundForTest());

        // Now, at this point, it's possible that the scheduled runnable had already been fired
        // before during the unbind() call, and waiting on mLock.
        // To simulate it, we just call the runnable here.
        mConn.getBindForBackoffRunnableForTest().run();

        // Should still not be bound.
        assertFalse(mConn.isBound());
        assertFalse(mConn.shouldBeBoundForTest());
    }

    @Test
    public void testResetBackoff() {
        final Context context = mock(Context.class);
        final Handler handler = new Handler(Looper.getMainLooper());

        final MyConnection conn = new MyConnection(TAG, context, handler, USER_ID, COMPONENT_NAME,
                /* rebindBackoffSeconds= */ 5,
                /* rebindBackoffIncrease= */ 1.5,
                /* rebindMaxBackoffSeconds= */ 11,
                /* resetBackoffDelay= */ 20);

        when(context.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class), anyInt(),
                any(Handler.class), any(UserHandle.class)))
                .thenReturn(true);

        // Bind.
        conn.bind();

        assertTrue(conn.isBound());
        assertTrue(conn.shouldBeBoundForTest());
        assertFalse(conn.isRebindScheduled());

        conn.elapse(1000);

        // Then the binding is "died"...
        conn.getServiceConnectionForTest().onBindingDied(COMPONENT_NAME);

        assertFalse(conn.isBound());
        assertTrue(conn.shouldBeBoundForTest());
        assertFalse(conn.isConnected());
        assertNull(conn.getServiceBinder());
        assertTrue(conn.isRebindScheduled());

        assertEquals(7500, conn.getNextBackoffMsForTest());

        assertEquals(
                Collections.singletonList(Pair.create(conn.getBindForBackoffRunnableForTest(),
                        conn.uptimeMillis + 5000)),
                conn.scheduledRunnables);

        // 5000 ms later...
        conn.elapse(5000);

        assertTrue(conn.isBound());
        assertTrue(conn.shouldBeBoundForTest());
        assertFalse(conn.isConnected());
        assertNull(conn.getServiceBinder());
        assertFalse(conn.isRebindScheduled());

        assertEquals(7500, conn.getNextBackoffMsForTest());

        // Connected.
        conn.getServiceConnectionForTest().onServiceConnected(COMPONENT_NAME,
                new IDeviceAdminService.Stub() {});

        assertTrue(conn.isBound());
        assertTrue(conn.shouldBeBoundForTest());
        assertTrue(conn.isConnected());
        assertNotNull(conn.getServiceBinder());
        assertFalse(conn.isRebindScheduled());

        assertEquals(7500, conn.getNextBackoffMsForTest());

        assertEquals(
                Collections.singletonList(Pair.create(conn.getStableCheckRunnableForTest(),
                        conn.uptimeMillis + 20000)),
                conn.scheduledRunnables);

        conn.elapse(20000);

        assertEquals(5000, conn.getNextBackoffMsForTest());
    }
}
