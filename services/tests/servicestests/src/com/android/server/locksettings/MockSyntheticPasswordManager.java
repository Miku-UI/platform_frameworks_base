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
package com.android.server.locksettings;

import static org.junit.Assert.assertNull;

import android.content.Context;
import android.hardware.weaver.IWeaver;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.ArrayMap;

import junit.framework.AssertionFailedError;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MockSyntheticPasswordManager extends SyntheticPasswordManager {

    private FakeGateKeeperService mGateKeeper;
    private MockWeaverService mWeaverService;
    private IWeaver mWeaverAidl;
    private android.hardware.weaver.V1_0.IWeaver mWeaverHidl;

    public MockSyntheticPasswordManager(Context context, LockSettingsStorage storage,
            FakeGateKeeperService gatekeeper, UserManager userManager,
            PasswordSlotManager passwordSlotManager) {
        super(context, storage, userManager, passwordSlotManager);
        mGateKeeper = gatekeeper;
    }

    private final ArrayMap<String, byte[]> mBlobs = new ArrayMap<>();

    @Override
    protected byte[] decryptSpBlob(String protectorKeyAlias, byte[] blob, byte[] protectorSecret) {
        if (mBlobs.containsKey(protectorKeyAlias) &&
                !Arrays.equals(mBlobs.get(protectorKeyAlias), blob)) {
            throw new AssertionFailedError("Blob was overwritten; protectorKeyAlias="
                    + protectorKeyAlias);
        }
        ByteBuffer buffer = ByteBuffer.allocate(blob.length);
        buffer.put(blob, 0, blob.length);
        buffer.flip();
        int len;
        len = buffer.getInt();
        byte[] data = new byte[len];
        buffer.get(data);
        len = buffer.getInt();
        byte[] storedProtectorSecret = new byte[len];
        buffer.get(storedProtectorSecret);
        long sid = buffer.getLong();
        if (!Arrays.equals(storedProtectorSecret, protectorSecret)) {
            throw new AssertionFailedError("Invalid protector secret");
        }
        if (sid != 0 && mGateKeeper.getAuthTokenForSid(sid) == null) {
            throw new AssertionFailedError("No valid auth token");
        }
        return data;
    }

    @Override
    protected byte[] createSpBlob(String protectorKeyAlias, byte[] data, byte[] protectorSecret,
            long sid) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + data.length + Integer.BYTES
                + protectorSecret.length + Long.BYTES);
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.putInt(protectorSecret.length);
        buffer.put(protectorSecret);
        buffer.putLong(sid);
        byte[] result = buffer.array();
        mBlobs.put(protectorKeyAlias, result);
        return result;
    }

    @Override
    protected void destroyProtectorKey(String keyAlias) {
    }

    @Override
    protected long sidFromPasswordHandle(byte[] handle) {
        return new FakeGateKeeperService.VerifyHandle(handle).sid;
    }

    @Override
    protected byte[] scrypt(byte[] password, byte[] salt, int n, int r, int p, int outLen) {
        try {
            char[] passwordChars = new char[password.length];
            for (int i = 0; i < password.length; i++) {
                passwordChars[i] = (char) password[i];
            }
            PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, 10, outLen * 8);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return f.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Enables MockWeaverService. */
    public void enableWeaver() {
        enableWeaverAidl();
    }

    /** Enables MockWeaverService with the current (AIDL) interface. */
    public void enableWeaverAidl() {
        assertNull(mWeaverService);
        mWeaverService = new MockWeaverService();
        mWeaverAidl = mWeaverService;
    }

    @Override
    protected IWeaver getWeaverAidlService() {
        return mWeaverAidl;
    }

    /** Enables MockWeaverService with the old (HIDL) interface. */
    public void enableWeaverHidl() {
        assertNull(mWeaverService);
        mWeaverService = new MockWeaverService();
        mWeaverHidl = mWeaverService.asHidl();
    }

    @Override
    protected android.hardware.weaver.V1_0.IWeaver getWeaverHidlService() throws RemoteException {
        return mWeaverHidl;
    }

    public boolean isWeaverEnabled() {
        return mWeaverService != null;
    }

    public int getSumOfWeaverFailureCounters() {
        return mWeaverService.getSumOfFailureCounters();
    }

    /** Injects a response to be returned by the next read from Weaver. */
    public void injectWeaverReadResponse(int status, Duration timeout) {
        mWeaverService.injectReadResponse(status, timeout);
    }
}
