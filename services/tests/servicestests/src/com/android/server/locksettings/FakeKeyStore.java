/*
 * Copyright 2025 The Android Open Source Project
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

import android.annotation.Nullable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.GateKeeper;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.security.keystore2.AndroidKeyStoreLoadStoreParameter;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.internal.util.Preconditions;

import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;

import javax.crypto.SecretKey;

/**
 * An implementation of an in-memory keystore for testing LockSettingsService. Implement methods as
 * needed for new functionality.
 */
public final class FakeKeyStore {
    public static final String NAME = "FakeKeyStore";

    private FakeKeyStore() {}

    public static class FakeKeyStoreSpi extends KeyStoreSpi {

        private record SecretKeyEntry(SecretKey secretKey, @Nullable Long boundSid) {
            SecretKeyEntry {
                Preconditions.checkArgument(
                        boundSid == null || boundSid != GateKeeper.INVALID_SECURE_USER_ID);
            }
        }

        private static FakeGateKeeperService sFakeGateKeeperService = null;

        private final ArrayMap<String, SecretKeyEntry> mKeyByAlias = new ArrayMap<>();

        @Override
        public Key engineGetKey(String alias, char[] password)
                throws NoSuchAlgorithmException, UnrecoverableKeyException {
            SecretKeyEntry entry = mKeyByAlias.get(alias);
            if (entry == null) {
                throw new UnrecoverableKeyException(
                        TextUtils.formatSimple("Key %s not found", alias));
            }
            if (entry.boundSid != null) {
                // Key is bound to an sid, implying authentication is required.
                if (sFakeGateKeeperService.getAuthTokenForSid(entry.boundSid) == null) {
                    throw new UnrecoverableKeyException("User not authenticated");
                }
            }
            return entry.secretKey;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetEntry(
                String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter protParam)
                throws KeyStoreException {
            if (entry instanceof KeyStore.SecretKeyEntry) {
                KeyProtection keyProtection = (KeyProtection) protParam;
                Long boundSid = null;
                if (keyProtection.isUserAuthenticationRequired()) {
                    long boundSidParam = keyProtection.getBoundToSpecificSecureUserId();
                    if (boundSidParam == GateKeeper.INVALID_SECURE_USER_ID) {
                        try {
                            boundSidParam =
                                    sFakeGateKeeperService.getSecureUserId(UserHandle.myUserId());
                        } catch (RemoteException e) {
                            throw new KeyStoreException(e);
                        }
                        if (boundSidParam == GateKeeper.INVALID_SECURE_USER_ID) {
                            throw new KeyStoreException("Current user not enrolled in Gatekeeper!");
                        }
                        boundSid = boundSidParam;
                    }
                }
                mKeyByAlias.put(
                        alias,
                        new SecretKeyEntry(
                                ((KeyStore.SecretKeyEntry) entry).getSecretKey(), boundSid));
            }
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain)
                throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain)
                throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert)
                throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineDeleteEntry(String alias) throws KeyStoreException {
            mKeyByAlias.remove(alias);
        }

        @Override
        public Enumeration<String> engineAliases() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return mKeyByAlias.containsKey(alias);
        }

        @Override
        public int engineSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineStore(OutputStream stream, char[] password)
                throws IOException, NoSuchAlgorithmException, CertificateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineLoad(KeyStore.LoadStoreParameter param)
                throws IOException, NoSuchAlgorithmException, CertificateException {
            Preconditions.checkArgument(param instanceof AndroidKeyStoreLoadStoreParameter);
        }

        @Override
        public void engineLoad(InputStream stream, char[] password)
                throws IOException, NoSuchAlgorithmException, CertificateException {}
    }

    private static class FakeKeyStoreProvider extends Provider {
        FakeKeyStoreProvider() {
            super(NAME, 1.0, "Fake KeyStore Provider");

            put("KeyStore." + NAME, FakeKeyStoreSpi.class.getName());
        }
    }

    /**
     * Install a fake in-memory keystore for testing LockSettingsService.
     *
     * @return an instance of the fake keystore.
     */
    private static KeyStore installFakeKeyStore() {
        Security.addProvider(new FakeKeyStoreProvider());
        final KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(FakeKeyStore.NAME);
            keyStore.load(
                    new AndroidKeyStoreLoadStoreParameter(KeyProperties.NAMESPACE_APPLICATION));
            return keyStore;
        } catch (KeyStoreException
                | IOException
                | NoSuchAlgorithmException
                | CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setFakeGatekeeperService(FakeGateKeeperService fakeGateKeeperService) {
        FakeKeyStoreSpi.sFakeGateKeeperService = fakeGateKeeperService;
    }

    private static void uninstallFakeKeyStore() {
        Security.removeProvider(FakeKeyStore.NAME);
        FakeKeyStoreSpi.sFakeGateKeeperService = null;
    }

    /** Installs an in-memory keystore for the duration of a JUnit test. */
    public static class FakeKeyStoreRule extends ExternalResource {
        private KeyStore mKeyStore;

        @Override
        protected void before() throws Throwable {
            mKeyStore = installFakeKeyStore();
        }

        @Override
        protected void after() {
            uninstallFakeKeyStore();
        }

        public KeyStore getKeyStore() {
            return mKeyStore;
        }
    }
}
