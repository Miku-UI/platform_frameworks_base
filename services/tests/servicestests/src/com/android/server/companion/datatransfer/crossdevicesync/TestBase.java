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
package com.android.server.companion.datatransfer.crossdevicesync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.companion.datatransfer.crossdevicesync.common.DelayedExecutor;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.ClockBasedExecutor;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeClock;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeContextualModeManager;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeDeviceUtils;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeFrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeResources;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.SynchronousExecutorService;
import com.android.server.companion.datatransfer.crossdevicesync.data.fake.FakeDeviceNodeIdProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.fake.FakeTimestampProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.fake.FakeStorage;
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.fake.FakeAirplaneModeController;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.fake.FakeMetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.fake.FakeAdvertiser;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.fake.FakeCompanionActionController;
import com.android.server.companion.datatransfer.crossdevicesync.network.fake.FakeNetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.fake.FakeMessenger;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.fake.FakeScanner;
import com.android.server.companion.datatransfer.crossdevicesync.notification.fake.FakeNotificationHelper;
import com.android.server.companion.datatransfer.crossdevicesync.notification.fake.FakeNotificationManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.user.fake.FakeUserHelper;

import java.util.concurrent.Executor;

/** Base class for tests. */
public abstract class TestBase {

    protected final Context mContext;
    protected final TestContext mTestContext;
    protected final FakeCompanionDeviceManagerProxy mFakeCompanionDeviceManagerProxy;
    protected final FakeNotificationManagerProxy mFakeNotificationManagerProxy;
    protected final Executor mMainExecutor;
    protected final FakeClock mFakeClock;
    protected final DelayedExecutor mDelayedExecutor;
    protected final FakeDeviceUtils mFakeDeviceUtil;
    protected final SharedPreferences mSharedPreferences;
    protected final FakeUserHelper mFakeUserHelper;
    protected final FakeContextualModeManager mFakeContextualModeManager;
    protected final FakeResources mFakeResources;
    protected final FakeFrameworkStatsLogProxy mFakeFrameworkStatsLogProxy;

    protected final FakeStorage mFakeStorage;
    protected final FakeDeviceNodeIdProvider mFakeDeviceNodeIdProvider;
    protected final FakeTimestampProvider mTimestampProvider;
    protected final FakeMessenger mFakeMessenger;
    protected final FakeScanner mFakeScanner;
    protected final FakeAdvertiser mFakeAdvertiser;
    protected final FakeCompanionActionController mFakeCompanionActionController;
    protected final FakeNetworkManager mFakeNetworkManager;
    protected final FakeAirplaneModeController mFakeAirplaneModeController;
    protected final FakeMetadataPublisher mFakeMetadataPublisher;
    protected final FakeNotificationHelper mFakeNotificationHelper;

    protected TestBase() {
        Context baseContext = ApplicationProvider.getApplicationContext();
        mTestContext = new TestContext(baseContext);
        mFakeResources = new FakeResources(baseContext.getResources());
        mTestContext.setResources(mFakeResources);
        mContext = mTestContext;

        mFakeClock = new FakeClock();
        mMainExecutor = new SynchronousExecutorService();
        mDelayedExecutor = new ClockBasedExecutor(mFakeClock);
        mFakeCompanionDeviceManagerProxy = new FakeCompanionDeviceManagerProxy();
        mFakeNotificationManagerProxy = new FakeNotificationManagerProxy();
        mFakeDeviceUtil = new FakeDeviceUtils();
        mFakeUserHelper = new FakeUserHelper(mContext);
        mFakeContextualModeManager = new FakeContextualModeManager();
        mFakeFrameworkStatsLogProxy = new FakeFrameworkStatsLogProxy();

        mFakeStorage = new FakeStorage();
        mFakeDeviceNodeIdProvider = new FakeDeviceNodeIdProvider();
        mTimestampProvider = new FakeTimestampProvider();
        mFakeMessenger = new FakeMessenger();
        mFakeScanner = new FakeScanner();
        mFakeAdvertiser = new FakeAdvertiser();
        mFakeCompanionActionController =
                new FakeCompanionActionController(mFakeCompanionDeviceManagerProxy);
        mFakeNetworkManager = new FakeNetworkManager();
        mFakeAirplaneModeController = new FakeAirplaneModeController();
        mFakeMetadataPublisher = new FakeMetadataPublisher();
        mFakeNotificationHelper = new FakeNotificationHelper();

        mSharedPreferences =
                mContext.getSharedPreferences("test_shared_prefs", Context.MODE_PRIVATE);

        mSharedPreferences.edit().clear().commit();
    }
}
