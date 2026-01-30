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
 * limitations under the License
 */

package com.android.server.locksettings;

import static org.mockito.Mockito.mock;

import android.app.IActivityManager;
import android.app.admin.DeviceStateCache;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.hardware.authsecret.IAuthSecret;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.storage.IStorageManager;
import android.service.gatekeeper.IGateKeeperService;

import com.android.server.ServiceThread;
import com.android.server.StorageManagerInternal;
import com.android.server.locksettings.SyntheticPasswordManager.SyntheticPassword;
import com.android.server.locksettings.recoverablekeystore.RecoverableKeyStoreManager;
import com.android.server.pm.UserManagerInternal;
import com.android.server.security.authenticationpolicy.SecureLockDeviceServiceInternal;

import java.security.KeyStore;
import java.time.Duration;

public class LockSettingsServiceTestable extends LockSettingsService {
    private Intent mSavedFrpNotificationIntent = null;
    private UserHandle mSavedFrpNotificationUserHandle = null;
    private String mSavedFrpNotificationPermission = null;

    public static class MockInjector extends LockSettingsService.Injector {

        private LockSettingsStorage mLockSettingsStorage;
        private final LockSettingsStrongAuth mStrongAuth;
        private final SynchronizedStrongAuthTracker mStrongAuthTracker;
        private IActivityManager mActivityManager;
        private IStorageManager mStorageManager;
        private StorageManagerInternal mStorageManagerInternal;
        private SyntheticPasswordManager mSpManager;
        private FakeGsiService mGsiService;
        private RecoverableKeyStoreManager mRecoverableKeyStoreManager;
        private SecureLockDeviceServiceInternal mSecureLockDeviceServiceInternal;
        private UserManagerInternal mUserManagerInternal;
        private DeviceStateCache mDeviceStateCache;
        private Duration mTimeSinceBoot;
        private KeyStore mKeyStore;
        Runnable mInvalidateLockoutEndTimeCacheMock;

        public boolean mIsHeadlessSystemUserMode = false;

        public MockInjector(
                Context context,
                LockSettingsStorage storage,
                LockSettingsStrongAuth strongAuth,
                SynchronizedStrongAuthTracker strongAuthTracker,
                IActivityManager activityManager,
                IStorageManager storageManager,
                StorageManagerInternal storageManagerInternal,
                SyntheticPasswordManager spManager,
                FakeGsiService gsiService,
                RecoverableKeyStoreManager recoverableKeyStoreManager,
                UserManagerInternal userManagerInternal,
                DeviceStateCache deviceStateCache,
                SecureLockDeviceServiceInternal secureLockDeviceServiceInternal,
                KeyStore keyStore,
                Runnable invalidateLockoutEndTimeCacheMock) {
            super(context);
            mLockSettingsStorage = storage;
            mStrongAuth = strongAuth;
            mStrongAuthTracker = strongAuthTracker;
            mActivityManager = activityManager;
            mStorageManager = storageManager;
            mStorageManagerInternal = storageManagerInternal;
            mSpManager = spManager;
            mGsiService = gsiService;
            mRecoverableKeyStoreManager = recoverableKeyStoreManager;
            mUserManagerInternal = userManagerInternal;
            mDeviceStateCache = deviceStateCache;
            mSecureLockDeviceServiceInternal = secureLockDeviceServiceInternal;
            mKeyStore = keyStore;
            mInvalidateLockoutEndTimeCacheMock = invalidateLockoutEndTimeCacheMock;
        }

        @Override
        public Handler getHandler(ServiceThread handlerThread) {
            return new Handler(handlerThread.getLooper());
        }

        @Override
        public LockSettingsStorage getStorage() {
            return mLockSettingsStorage;
        }

        @Override
        public LockSettingsStrongAuth getStrongAuth() {
            return mStrongAuth;
        }

        @Override
        public SynchronizedStrongAuthTracker getStrongAuthTracker() {
            return mStrongAuthTracker;
        }

        @Override
        public IActivityManager getActivityManager() {
            return mActivityManager;
        }

        @Override
        public DeviceStateCache getDeviceStateCache() {
            return mDeviceStateCache;
        }

        @Override
        public IStorageManager getStorageManager() {
            return mStorageManager;
        }

        @Override
        public StorageManagerInternal getStorageManagerInternal() {
            return mStorageManagerInternal;
        }

        @Override
        public SyntheticPasswordManager getSyntheticPasswordManager(LockSettingsStorage storage) {
            return mSpManager;
        }

        @Override
        public SecureLockDeviceServiceInternal getSecureLockDeviceServiceInternal() {
            return mSecureLockDeviceServiceInternal;
        }

        @Override
        public UserManagerInternal getUserManagerInternal() {
            return mUserManagerInternal;
        }

        @Override
        public int binderGetCallingUid() {
            return Process.SYSTEM_UID;
        }

        @Override
        public boolean isGsiRunning() {
            return mGsiService.isGsiRunning();
        }

        @Override
        public RecoverableKeyStoreManager getRecoverableKeyStoreManager() {
            return mRecoverableKeyStoreManager;
        }

        @Override
        public UnifiedProfilePasswordCache getUnifiedProfilePasswordCache(KeyStore ks) {
            return mock(UnifiedProfilePasswordCache.class);
        }

        @Override
        public boolean isHeadlessSystemUserMode() {
            return mIsHeadlessSystemUserMode;
        }

        @Override
        public KeyStore getKeyStore() {
            return mKeyStore;
        }

        void setTimeSinceBoot(Duration time) {
            mTimeSinceBoot = time;
        }

        @Override
        public Duration getTimeSinceBoot() {
            if (mTimeSinceBoot != null) {
                return mTimeSinceBoot;
            }
            return super.getTimeSinceBoot();
        }

        @Override
        public void invalidateLockoutEndTimeCache() {
            mInvalidateLockoutEndTimeCacheMock.run();
        }
    }

    protected LockSettingsServiceTestable(
            LockSettingsService.Injector injector,
            IGateKeeperService gatekeeper,
            IAuthSecret authSecretService) {
        super(injector);
        mGateKeeperService = gatekeeper;
        mAuthSecretService = authSecretService;
    }

    @Override
    void initKeystoreSuperKeys(int userId, SyntheticPassword sp, boolean allowExisting) {
    }

    @Override
    protected boolean isCredentialShareableWithParent(int userId) {
        UserInfo userInfo = mUserManager.getUserInfo(userId);
        return userInfo.isCloneProfile() || userInfo.isManagedProfile();
    }

    void clearAuthSecret() {
        synchronized (mHeadlessAuthSecretLock) {
            mAuthSecret = null;
        }
    }

    @Override
    void sendBroadcast(Intent intent, UserHandle userHandle, String permission) {
        mSavedFrpNotificationIntent = intent;
        mSavedFrpNotificationUserHandle = userHandle;
        mSavedFrpNotificationPermission = permission;
    }

    String getSavedFrpNotificationPermission() {
        return mSavedFrpNotificationPermission;
    }

    UserHandle getSavedFrpNotificationUserHandle() {
        return mSavedFrpNotificationUserHandle;
    }

    Intent getSavedFrpNotificationIntent() {
        return mSavedFrpNotificationIntent;
    }

    void clearRecordedFrpNotificationData() {
        mSavedFrpNotificationIntent = null;
        mSavedFrpNotificationPermission = null;
        mSavedFrpNotificationUserHandle = null;
    }
}
