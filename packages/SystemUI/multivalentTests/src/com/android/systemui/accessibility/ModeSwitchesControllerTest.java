/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.accessibility;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
/** Tests the ModeSwitchesController. */
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class ModeSwitchesControllerTest extends SysuiTestCase {

    @Mock
    private DisplayManager mDisplayManager;

    private Display mDisplay;
    private FakeSwitchSupplier mSupplier;
    private MagnificationModeSwitch mModeSwitch;
    private ModeSwitchesController mModeSwitchesController;
    private View mSpyView;
    @Mock
    private MagnificationModeSwitch.ClickListener mListener;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDisplay = mContext.getSystemService(DisplayManager.class).getDisplay(
                Display.DEFAULT_DISPLAY);
        when(mDisplayManager.getDisplay(anyInt())).thenReturn(mDisplay);

        mSupplier = new FakeSwitchSupplier(mDisplayManager);
        mModeSwitchesController = new ModeSwitchesController(mSupplier);
        mModeSwitchesController.setClickListenerDelegate(mListener);
        WindowManager wm = mContext.getSystemService(WindowManager.class);
        mModeSwitch = Mockito.spy(new MagnificationModeSwitch(mContext, wm,
                mModeSwitchesController));
        mSpyView = Mockito.spy(new View(mContext));
    }

    @After
    public void tearDown() {
        mModeSwitchesController.removeButton(Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testShowButton() {
        mModeSwitchesController.showButton(Display.DEFAULT_DISPLAY,
                Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);

        verify(mModeSwitch).showButton(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);
    }

    @Test
    public void testRemoveButton() {
        mModeSwitchesController.showButton(Display.DEFAULT_DISPLAY,
                Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);

        mModeSwitchesController.removeButton(Display.DEFAULT_DISPLAY);

        verify(mModeSwitch).removeButton();
    }

    @Test
    public void testControllerOnConfigurationChanged_notifyShowingButton() {
        mModeSwitchesController.showButton(Display.DEFAULT_DISPLAY,
                Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);
        mModeSwitchesController.onConfigurationChanged(ActivityInfo.CONFIG_DENSITY);

        verify(mModeSwitch).onConfigurationChanged(ActivityInfo.CONFIG_DENSITY);
    }

    @Test
    public void testOnSwitchClick_showWindowModeButton_invokeListener() {
        mModeSwitchesController.showButton(Display.DEFAULT_DISPLAY,
                Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN);

        mModeSwitch.onSingleTap(mSpyView);

        verify(mListener).onClick(mContext.getDisplayId());
    }

    @Test
    @DisableFlags(Flags.FLAG_CLEANUP_INSTANCES_WHEN_DISPLAY_REMOVED)
    public void testOnDisplayRemoved_flagOff_instancesStayInSupplier() {
        int originalCachedItemsSize = mSupplier.getSize();
        int testDisplayId2 = 200;
        int testDisplayId3 = 300;

        // Make the settings supplier add 2 new instance entries.
        mModeSwitchesController.removeButton(testDisplayId2);
        mModeSwitchesController.removeButton(testDisplayId3);
        // When displays removed, the current behavior keeps the entries/instances in the supplier.
        mModeSwitchesController.onDisplayRemoved(testDisplayId2);
        mModeSwitchesController.onDisplayRemoved(testDisplayId3);

        assertThat(mSupplier.getSize()).isEqualTo(originalCachedItemsSize + 2);
    }

    @Test
    @EnableFlags(Flags.FLAG_CLEANUP_INSTANCES_WHEN_DISPLAY_REMOVED)
    public void testOnDisplayRemoved_flagOn_instancesAreRemovedFromSupplier() {
        int originalCachedItemsSize = mSupplier.getSize();
        int testDisplayId2 = 200;
        int testDisplayId3 = 300;

        // Make the settings supplier add 2 new instance entries.
        mModeSwitchesController.removeButton(testDisplayId2);
        mModeSwitchesController.removeButton(testDisplayId3);
        // When displays removed, the related instance caches should be removed too.
        mModeSwitchesController.onDisplayRemoved(testDisplayId2);
        mModeSwitchesController.onDisplayRemoved(testDisplayId3);

        assertThat(mSupplier.getSize()).isEqualTo(originalCachedItemsSize);
    }

    private class FakeSwitchSupplier extends DisplayIdIndexSupplier<MagnificationModeSwitch> {

        FakeSwitchSupplier(DisplayManager displayManager) {
            super(displayManager);
        }

        @Override
        protected MagnificationModeSwitch createInstance(Display display) {
            return mModeSwitch;
        }
    }
}
