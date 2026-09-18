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
package com.android.ravenwoodtest.coretest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.RemoteException;
import android.platform.test.ravenwood.RavenwoodProxyHelper;
import android.platform.test.ravenwood.RavenwoodUnsupportedApiException;

import org.junit.Test;

public class RavenwoodAidlProxyTest {

    static class ExtendDefault extends IRavenwoodAidl.Default {
        @Override
        public int foo() {
            return 100;
        }
    }

    static class SameSignature {
        public int foo() {
            return 100;
        }
    }

    @Test
    public void testAidlProxy() throws RemoteException {
        IRavenwoodAidl asInterface;

        var def = new ExtendDefault();
        assertEquals(100, def.foo());
        assertEquals(0, def.bar());

        var proxy1 = RavenwoodProxyHelper.newProxy(IRavenwoodAidl.class, def);
        assertEquals(100, proxy1.foo());
        assertThrows(RavenwoodUnsupportedApiException.class, proxy1::bar);

        asInterface = IRavenwoodAidl.Stub.asInterface(proxy1.asBinder());
        assertEquals(100, asInterface.foo());
        assertThrows(RavenwoodUnsupportedApiException.class, asInterface::bar);

        var proxy2 = RavenwoodProxyHelper.newProxy(IRavenwoodAidl.class, new SameSignature());
        assertEquals(100, proxy2.foo());
        assertThrows(RavenwoodUnsupportedApiException.class, proxy2::bar);

        asInterface = IRavenwoodAidl.Stub.asInterface(proxy2.asBinder());
        assertEquals(100, asInterface.foo());
        assertThrows(RavenwoodUnsupportedApiException.class, asInterface::bar);
    }
}
