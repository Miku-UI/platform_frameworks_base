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

package com.android.server.inputmethod;

import static com.android.internal.inputmethod.SoftInputShowHideReason.HIDE_SOFT_INPUT;
import static com.android.internal.inputmethod.SoftInputShowHideReason.SHOW_SOFT_INPUT;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import static java.util.Objects.requireNonNull;

import android.os.Binder;
import android.os.RemoteException;
import android.view.Display;
import android.view.inputmethod.ImeTracker;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InputMethodManagerServiceScreenshotTest extends InputMethodManagerServiceTestBase {

    UserData mUserData;

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        synchronized (ImfLock.class) {
            mUserData = mInputMethodManagerService.getUserData(mUserId);
            mUserData.mCurClient = requireNonNull(
                    mInputMethodManagerService.getClientStateLocked(mMockInputMethodClient));
        }
    }

    @Test
    public void testPerformShowIme() throws Exception {
        synchronized (ImfLock.class) {
            mInputMethodManagerService.performShowIme(new Binder() /* showInputToken */,
                    ImeTracker.Token.empty(), SHOW_SOFT_INPUT, mUserData);
        }
        verifyShowSoftInput(true /* showSoftInput */);
    }

    @Test
    public void testPerformHideIme() throws Exception {
        synchronized (ImfLock.class) {
            mInputMethodManagerService.performHideIme(new Binder() /* hideInputToken */,
                    ImeTracker.Token.empty(), HIDE_SOFT_INPUT, mUserData);
        }
        verifyHideSoftInput(true /* hideSoftInput */);
    }

    @Test
    public void testShowImeScreenshot() {
        synchronized (ImfLock.class) {
            mInputMethodManagerService.showImeScreenshot(mWindowToken, Display.DEFAULT_DISPLAY,
                    mUserId);
        }

        verify(mMockWindowManagerInternal).showImeScreenshot(eq(mWindowToken),
                eq(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void testRemoveImeScreenshot() {
        synchronized (ImfLock.class) {
            mInputMethodManagerService.removeImeScreenshot(mWindowToken, Display.DEFAULT_DISPLAY,
                    mUserId);
        }

        verify(mMockWindowManagerInternal).removeImeScreenshot(eq(Display.DEFAULT_DISPLAY));
    }
}
