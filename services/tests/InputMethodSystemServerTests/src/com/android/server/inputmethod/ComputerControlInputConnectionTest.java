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

package com.android.server.inputmethod;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.companion.virtualdevice.flags.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.inputmethod.StartInputFlags;
import com.android.internal.inputmethod.StartInputReason;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
public class ComputerControlInputConnectionTest extends InputMethodManagerServiceTestBase {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void startInputOrWindowGainedFocus_savesInputConnectionOnComputerControlDisplay() {
        when(mMockVirtualDeviceManagerInternal.isComputerControlDisplay(
                CLIENT_DISPLAY_ID)).thenReturn(true);
        startInputOrWindowGainedFocus();

        UserData data = mInputMethodManagerService.getUserData(mUserId);
        assertEquals(mRemoteComputerControlInputConnection,
                data.mComputerControlInputConnectionMap.get(CLIENT_DISPLAY_ID));
    }

    @Test
    public void startInputOrWindowGainedFocus_doesNotSaveInputConnectionOnNormalDisplay() {
        when(mMockVirtualDeviceManagerInternal.isComputerControlDisplay(
                CLIENT_DISPLAY_ID)).thenReturn(false);
        startInputOrWindowGainedFocus();

        UserData data = mInputMethodManagerService.getUserData(mUserId);
        assertNull(data.mComputerControlInputConnectionMap.get(CLIENT_DISPLAY_ID));
    }

    private void startInputOrWindowGainedFocus() {
        mInputMethodManagerService.startInputOrWindowGainedFocusWithResult(
                StartInputReason.WINDOW_FOCUS_GAIN /* startInputReason */,
                mMockInputMethodClient /* client */,
                mWindowToken /* windowToken */,
                StartInputFlags.VIEW_HAS_FOCUS
                        | StartInputFlags.IS_TEXT_EDITOR /* startInputFlags */,
                0 /* softInputMode */,
                0 /* windowFlags */,
                mEditorInfo /* editorInfo */,
                mMockFallbackInputConnection /* fallbackInputConnection */,
                mMockRemoteAccessibilityInputConnection /* remoteAccessibilityInputConnection */,
                mRemoteComputerControlInputConnection /* remoteComputerControlInputConnection */,
                mTargetSdkVersion /* unverifiedTargetSdkVersion */,
                mUserId /* userId */,
                mMockImeBackCallbackReceiver /* imeBackCallbackReceiver */,
                true /* imeRequestedVisible */);
    }
}
