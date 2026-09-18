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

package com.android.wm.shell.pip2;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.gui.BorderSettings;
import android.gui.BoxShadowSettings;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.Choreographer;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.BoxShadowHelper;
import com.android.wm.shell.common.pip.IPipAnimationListener.PipResources;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

/**
 * Unit test against {@link PipSurfaceTransactionHelper}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipSurfaceTransactionHelperTest {

    private static final int CORNER_RADIUS = 10;
    private static final int SHADOW_RADIUS = 20;
    private static final float MIRROR_OPACITY = 0.5f;
    private static final Rect PIP_BOUNDS = new Rect(0, 0, 500, 500);

    private final BoxShadowSettings mLightBoxShadowSettings = new BoxShadowSettings();
    private final BorderSettings mLightBorderSettings = new BorderSettings();
    private final BoxShadowSettings mDarkBoxShadowSettings = new BoxShadowSettings();
    private final BorderSettings mDarkBorderSettings = new BorderSettings();

    private static final int[] LIGHT_SHADOW_STYLES = {
            R.style.BoxShadowParamsPIPLight1, R.style.BoxShadowParamsPIPLight2};
    private static final int[] DARK_SHADOW_STYLES = {
            R.style.BoxShadowParamsPIPDark1, R.style.BoxShadowParamsPIPDark2};
    private static final int LIGHT_BORDER_STYLE = R.style.BorderSettingsPIPLight;
    private static final int DARK_BORDER_STYLE = R.style.BorderSettingsPIPDark;

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private SurfaceControl.Transaction mMockTransaction;
    @Mock private ShellInit mMockShellInit;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    @Mock private Choreographer mMockChoreographer;
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private SurfaceControl mTestLeash;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(BoxShadowHelper.class)
            .mockStatic(PipUtils.class)
            .mockStatic(Choreographer.class)
            .build();

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2);
    }

    public PipSurfaceTransactionHelperTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getDimensionPixelSize(eq(R.dimen.pip_corner_radius)))
                .thenReturn(CORNER_RADIUS);
        when(mMockResources.getDimensionPixelSize(eq(R.dimen.pip_shadow_radius)))
                .thenReturn(SHADOW_RADIUS);
        when(mMockResources.getFloat(eq(R.dimen.config_pipDraggingAcrossDisplaysOpacity)))
                .thenReturn(MIRROR_OPACITY);
        when(mMockTransaction.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setMatrix(any(SurfaceControl.class), any(Matrix.class), any()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setCrop(any(SurfaceControl.class), any(Rect.class)))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setPosition(any(SurfaceControl.class), anyFloat(), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setAlpha(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setLayer(any(SurfaceControl.class), any(Integer.class)))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.show(any(SurfaceControl.class)))
                .thenReturn(mMockTransaction);

        mPipSurfaceTransactionHelper = new PipSurfaceTransactionHelper(mMockContext,
                mMockShellInit, mMockPipDisplayLayoutState);
        // Directly call onInit instead of using ShellInit
        mPipSurfaceTransactionHelper.onInit();
        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipSurfaceTransactionHelperTest")
                .setCallsite("PipSurfaceTransactionHelperTest")
                .build();

        when(mMockTransaction.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setBoxShadowSettings(any(SurfaceControl.class),
                any(BoxShadowSettings.class)))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setBorderSettings(any(SurfaceControl.class),
                any(BorderSettings.class)))
                .thenReturn(mMockTransaction);

        when(Choreographer.getInstance()).thenReturn(mMockChoreographer);

        when(BoxShadowHelper.getBoxShadowSettings(
                eq(mMockContext), aryEq(LIGHT_SHADOW_STYLES))).thenReturn(
                mLightBoxShadowSettings);
        when(BoxShadowHelper.getBorderSettings(
                eq(mMockContext), eq(LIGHT_BORDER_STYLE))).thenReturn(mLightBorderSettings);

        when(BoxShadowHelper.getBoxShadowSettings(
                eq(mMockContext), aryEq(DARK_SHADOW_STYLES))).thenReturn(mDarkBoxShadowSettings);
        when(BoxShadowHelper.getBorderSettings(
                eq(mMockContext), eq(DARK_BORDER_STYLE))).thenReturn(mDarkBorderSettings);
    }

    @Test
    public void round_doNotApply_setZeroCornerRadius() {
        mPipSurfaceTransactionHelper.round(mMockTransaction, mTestLeash, false /* apply */);

        verify(mMockTransaction).setCornerRadius(eq(mTestLeash), eq(0f));
    }

    @Test
    public void round_doApply_setExactCornerRadius() {
        mPipSurfaceTransactionHelper.round(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setCornerRadius(eq(mTestLeash), eq((float) CORNER_RADIUS));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2)
    public void shadow_doNotApply_setZeroShadowRadius() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, false /* apply */);

        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq(0f));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2)
    public void shadow_doApply_setExactShadowRadius() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq((float) SHADOW_RADIUS));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2)
    public void shadow_flagEnabled_applyFalse_setsEmptyBoxShadowAndBorder() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, false /* apply */);

        ArgumentCaptor<BoxShadowSettings> boxShadow = ArgumentCaptor.forClass(
                BoxShadowSettings.class);
        ArgumentCaptor<BorderSettings> border = ArgumentCaptor.forClass(BorderSettings.class);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash), boxShadow.capture());
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), border.capture());
        // Ensure that box shadow clears elevation shadow which may be set by window decorations.
        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq(0.0f));

        assertEquals(0, boxShadow.getValue().boxShadows.length);
        assertEquals(0, border.getValue().strokeWidth, 0.0);
        assertEquals(0, border.getValue().color);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2)
    public void onThemeChanged_switchToDarkTheme_usesDarkSettingsOnShadow() {
        when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(true);

        mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);

        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash),
                eq(mDarkBoxShadowSettings));
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), eq(mDarkBorderSettings));
        // Ensure that box shadow clears elevation shadow which may be set by window decorations.
        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq(0.0f));
    }


    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS_V2)
    public void onThemeChanged_switchToLightTheme_usesLightSettingsOnShadow() {
        when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(false);

        mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);

        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash),
                eq(mLightBoxShadowSettings));
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), eq(mLightBorderSettings));
        // Ensure that box shadow clears elevation shadow which may be set by window decorations.
        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq(0.0f));
    }

    @Test
    public void setMirrorTransformations_setsAlphaAndLayer() {
        mPipSurfaceTransactionHelper.setMirrorTransformations(mMockTransaction, mTestLeash);

        verify(mMockTransaction).setAlpha(eq(mTestLeash), eq(MIRROR_OPACITY));
        verify(mMockTransaction).setLayer(eq(mTestLeash), eq(Integer.MAX_VALUE));
        verify(mMockTransaction).show(eq(mTestLeash));
    }

    @Test
    public void setPipTransformations_setsMatrixAndLayer() {
        mPipSurfaceTransactionHelper.setPipTransformations(mTestLeash, mMockTransaction, PIP_BOUNDS,
                PIP_BOUNDS, 0);

        verify(mMockTransaction).setMatrix(eq(mTestLeash), any(), any());
        verify(mMockTransaction).setLayer(eq(mTestLeash),
                intThat((layer) -> layer < Integer.MAX_VALUE));
    }

    @Test
    public void getCornerRadius_returnsCorrectValue() {
        assertEquals(CORNER_RADIUS, mPipSurfaceTransactionHelper.getCornerRadius());
    }

    @Test
    public void getPipResources_returnsCorrectValues() {
        PipResources resources = mPipSurfaceTransactionHelper.getPipResources();

        assertEquals(CORNER_RADIUS, resources.cornerRadius);
        assertEquals(SHADOW_RADIUS, resources.shadowRadius);

        if (Flags.enablePipBoxShadowsV2()) {
            when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(false);
            mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);
            resources = mPipSurfaceTransactionHelper.getPipResources();
            assertEquals(mLightBoxShadowSettings, resources.boxShadowSettings);
            assertEquals(mLightBorderSettings, resources.borderSettings);

            when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(true);
            mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);
            resources = mPipSurfaceTransactionHelper.getPipResources();
            assertEquals(mDarkBoxShadowSettings, resources.boxShadowSettings);
            assertEquals(mDarkBorderSettings, resources.borderSettings);
        } else {
            assertEquals(null, resources.boxShadowSettings);
            assertEquals(null, resources.borderSettings);
        }
    }

    @Test
    public void onDisplayIdChanged_reloadsResources() {
        Context newContext = mock(Context.class);
        Resources newResources = mock(Resources.class);
        when(newContext.getResources()).thenReturn(newResources);
        int newCornerRadius = CORNER_RADIUS + 1;
        when(newResources.getDimensionPixelSize(R.dimen.pip_corner_radius))
                .thenReturn(newCornerRadius);
        // Needed for the reload resources call
        when(newResources.getDimensionPixelSize(R.dimen.pip_shadow_radius))
                .thenReturn(SHADOW_RADIUS);
        when(newResources.getFloat(R.dimen.config_pipDraggingAcrossDisplaysOpacity))
                .thenReturn(MIRROR_OPACITY);

        mPipSurfaceTransactionHelper.onDisplayIdChanged(newContext);

        assertEquals(newCornerRadius, mPipSurfaceTransactionHelper.getCornerRadius());
    }

    @Test
    public void round_withBounds_setsScaledCornerRadius() {
        Rect fromBounds = new Rect(0, 0, 200, 200);
        Rect toBounds = new Rect(0, 0, 100, 100);
        // scale = hypot(200, 200) / hypot(100, 100) = 2.0
        float expectedRadius = CORNER_RADIUS * 2.0f;

        mPipSurfaceTransactionHelper.round(mMockTransaction, mTestLeash, fromBounds, toBounds);

        verify(mMockTransaction).setCornerRadius(eq(mTestLeash), eq(expectedRadius));
    }

    @Test
    public void scaleAndCrop_notInPipDirection_appliesCorrectTransform() {
        Rect sourceBounds = new Rect(0, 0, 400, 200);
        Rect destinationBounds = new Rect(10, 20, 110, 70); // 100x50
        Rect insets = new Rect(5, 5, 5, 5);
        float expectedScale = 0.25f; // max(100/400, 50/200) = 0.25
        float expectedX = 10 - 5 * expectedScale; // dest.left - insets.left * scale
        float expectedY = 20 - 5 * expectedScale; // dest.top - insets.top * scale

        mPipSurfaceTransactionHelper.scaleAndCrop(mMockTransaction, mTestLeash,
                null /* sourceRectHint */, sourceBounds, destinationBounds, insets,
                false /* isInPipDirection */, 0f /* fraction */);

        ArgumentCaptor<Matrix> matrixCaptor = ArgumentCaptor.forClass(Matrix.class);
        verify(mMockTransaction).setMatrix(eq(mTestLeash), matrixCaptor.capture(), any());
        verify(mMockTransaction).setPosition(eq(mTestLeash), eq(expectedX), eq(expectedY));

        float[] values = new float[9];
        matrixCaptor.getValue().getValues(values);
        assertEquals(expectedScale, values[Matrix.MSCALE_X], 0.001f);
        assertEquals(expectedScale, values[Matrix.MSCALE_Y], 0.001f);

        ArgumentCaptor<Rect> cropCaptor = ArgumentCaptor.forClass(Rect.class);
        verify(mMockTransaction).setCrop(eq(mTestLeash), cropCaptor.capture());
        Rect expectedCrop = new Rect(0, 0, 400, 200);
        expectedCrop.inset(insets);
        assertEquals(expectedCrop, cropCaptor.getValue());
    }

    @Test
    public void scaleAndCrop_inPipDirectionWithHint_fractionalScaling() {
        Rect sourceBounds = new Rect(0, 0, 1000, 1000);
        Rect sourceRectHint = new Rect(100, 100, 900, 900); // 800x800
        Rect destinationBounds = new Rect(0, 0, 200, 200);
        Rect insets = new Rect(0, 0, 0, 0);
        float fraction = 0.5f;

        // startScale = 200 / 1000 = 0.2
        float startScale = (float) destinationBounds.width() / sourceBounds.width();
        // endScale = 200 / 800 = 0.25
        float endScale = (float) destinationBounds.width() / sourceRectHint.width();
        // expectedScale = 0.2 * 0.5 + 0.25 * 0.5 = 0.1 + 0.125 = 0.225
        float expectedScale = (1 - fraction) * startScale + fraction * endScale;

        mPipSurfaceTransactionHelper.scaleAndCrop(mMockTransaction, mTestLeash,
                sourceRectHint, sourceBounds, destinationBounds, insets,
                true /* isInPipDirection */, fraction);

        ArgumentCaptor<Matrix> matrixCaptor = ArgumentCaptor.forClass(Matrix.class);
        verify(mMockTransaction).setMatrix(eq(mTestLeash), matrixCaptor.capture(), any());

        float[] values = new float[9];
        matrixCaptor.getValue().getValues(values);
        assertEquals(expectedScale, values[Matrix.MSCALE_X], 0.001f);
    }

    @Test
    public void rotateAndScaleWithCrop_expanding_appliesCorrectTransform() {
        Rect sourceBounds = new Rect(0, 0, 200, 400);
        Rect destinationBounds = new Rect(10, 20, 110, 120); // 100x100
        Rect insets = new Rect(5, 10, 5, 10);
        float degrees = 90f;
        float positionX = 10f;
        float positionY = 20f;

        final float scale = 100f / (200f - 5f - 5f); // destW / (srcW - insets on width)
        final float expectedX = positionX - insets.left * scale;
        final float expectedY = positionY - insets.top * scale;

        mPipSurfaceTransactionHelper.rotateAndScaleWithCrop(mMockTransaction, mTestLeash,
                sourceBounds, destinationBounds, insets, degrees, positionX, positionY,
                true /* isExpanding */, true /* clockwise */);

        ArgumentCaptor<Matrix> matrixCaptor = ArgumentCaptor.forClass(Matrix.class);
        verify(mMockTransaction).setMatrix(eq(mTestLeash), matrixCaptor.capture(), any());

        Matrix expectedMatrix = new Matrix();
        expectedMatrix.setScale(scale, scale);
        expectedMatrix.postTranslate(expectedX, expectedY);
        expectedMatrix.postRotate(degrees);
        assertEquals(expectedMatrix, matrixCaptor.getValue());
    }

    @Test
    public void rotateAndScaleWithCrop_shrinkingClockwise_appliesCorrectTransform() {
        Rect sourceBounds = new Rect(0, 0, 200, 400);
        Rect destinationBounds = new Rect(10, 20, 110, 120); // 100x100
        Rect insets = new Rect(5, 10, 5, 10);
        float degrees = 90f;
        float positionX = 10f;
        float positionY = 20f;

        final float scale = 100f / (200f - 5f - 5f);
        final float expectedX = positionX - insets.top * scale;
        final float expectedY = positionY + insets.left * scale;

        mPipSurfaceTransactionHelper.rotateAndScaleWithCrop(mMockTransaction, mTestLeash,
                sourceBounds, destinationBounds, insets, degrees, positionX, positionY,
                false /* isExpanding */, true /* clockwise */);

        ArgumentCaptor<Matrix> matrixCaptor = ArgumentCaptor.forClass(Matrix.class);
        verify(mMockTransaction).setMatrix(eq(mTestLeash), matrixCaptor.capture(), any());

        Matrix expectedMatrix = new Matrix();
        expectedMatrix.setScale(scale, scale);
        expectedMatrix.postTranslate(expectedX, expectedY);
        expectedMatrix.postRotate(degrees);
        assertEquals(expectedMatrix, matrixCaptor.getValue());
    }

    @Test
    public void rotateAndScaleWithCrop_shrinkingCounterClockwise_appliesCorrectTransform() {
        Rect sourceBounds = new Rect(0, 0, 200, 400);
        Rect destinationBounds = new Rect(10, 20, 110, 120); // 100x100
        Rect insets = new Rect(5, 10, 5, 10);
        float degrees = 90f;
        float positionX = 10f;
        float positionY = 20f;

        final float scale = 100f / (200f - 5f - 5f);
        final float expectedX = positionX + insets.top * scale;
        final float expectedY = positionY - insets.left * scale;

        mPipSurfaceTransactionHelper.rotateAndScaleWithCrop(mMockTransaction, mTestLeash,
                sourceBounds, destinationBounds, insets, degrees, positionX, positionY,
                false /* isExpanding */, false /* clockwise */);

        ArgumentCaptor<Matrix> matrixCaptor = ArgumentCaptor.forClass(Matrix.class);
        verify(mMockTransaction).setMatrix(eq(mTestLeash), matrixCaptor.capture(), any());

        Matrix expectedMatrix = new Matrix();
        expectedMatrix.setScale(scale, scale);
        expectedMatrix.postTranslate(expectedX, expectedY);
        expectedMatrix.postRotate(degrees);
        assertEquals(expectedMatrix, matrixCaptor.getValue());
    }

    @Test
    public void vsyncSurfaceControlTransactionFactory_callsGetVsyncId() {
        PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory factory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();

        factory.getTransaction();

        verify(mMockChoreographer, atLeastOnce()).getVsyncId();
    }
}
