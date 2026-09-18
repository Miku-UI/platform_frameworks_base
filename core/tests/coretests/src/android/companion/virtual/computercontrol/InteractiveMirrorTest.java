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

package android.companion.virtual.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class InteractiveMirrorTest {
    @Mock
    private IInteractiveMirror mMockRemoteMirror;

    private InteractiveMirror mMirror;
    private AutoCloseable mMockitoSession;
    private SurfaceControl mMirrorSurface;
    private SurfaceControl mSuppliedSurfaceControl;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mMirrorSurface = new SurfaceControl();
        mSuppliedSurfaceControl = new SurfaceControl();
        mMirror = new InteractiveMirror(mMockRemoteMirror, mMirrorSurface,
                () -> mSuppliedSurfaceControl);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void setInteractiveToTrue_setsInteractive() throws RemoteException {
        mMirror.setInteractive(true);
        verify(mMockRemoteMirror).setInteractive(true);
    }

    @Test
    public void setInteractiveToFalse_setsInteractive() throws RemoteException {
        mMirror.setInteractive(false);
        verify(mMockRemoteMirror).setInteractive(false);
    }

    @Test
    public void getMirrorSurfaceControl_returnsCopyOfMirrorSurfaceControl() {
        assertThat(mMirror.getMirrorSurfaceControl()).isEqualTo(mSuppliedSurfaceControl);
    }

    @Test
    public void resize_invalidDimensions_doesNotResizeMirrorSurface() throws RemoteException {
        final int width = -100;
        final int height = 0;
        mMirror.resize(width, height);
        verify(mMockRemoteMirror, never()).resize(anyInt(), anyInt());
    }

    @Test
    public void resize_resizesMirrorSurface() throws RemoteException {
        final int width = 100;
        final int height = 600;
        mMirror.resize(width, height);
        verify(mMockRemoteMirror).resize(width, height);
    }

    @Test
    public void close_closesDisplay() throws RemoteException {
        mMirror.close();
        verify(mMockRemoteMirror).close();
    }
}
