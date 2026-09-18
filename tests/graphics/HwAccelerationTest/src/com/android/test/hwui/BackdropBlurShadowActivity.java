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

package com.android.test.hwui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class BackdropBlurShadowActivity extends Activity {
    private View mBlurView;
    private float mElevation = 50f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);
        FrameLayout testArea = new FrameLayout(this);
        testArea.setBackgroundColor(Color.WHITE);

        View redBox = new View(this);
        redBox.setBackgroundColor(Color.RED);
        FrameLayout.LayoutParams redParams = new FrameLayout.LayoutParams(300, 300);
        redParams.gravity = Gravity.CENTER;
        redParams.setMargins(150, 150, 0, 0);
        testArea.addView(redBox, redParams);

        mBlurView = new View(this);
        mBlurView.setBackgroundColor(Color.TRANSPARENT);

        final RenderEffect blurEffect = RenderEffect.createBlurEffect(
                50f, 50f, Shader.TileMode.MIRROR);
        mBlurView.setBackdropRenderEffect(blurEffect);

        mBlurView.setElevation(mElevation);
        mBlurView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        mBlurView.setClipToOutline(true);

        FrameLayout.LayoutParams blurParams = new FrameLayout.LayoutParams(600, 600);
        blurParams.gravity = Gravity.CENTER;
        testArea.addView(mBlurView, blurParams);

        rootLayout.addView(testArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));


        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(Color.LTGRAY);
        controls.setPadding(30, 30, 30, 30);

        final TextView elevLabel = new TextView(this);
        elevLabel.setText("Elevation: " + mElevation);
        elevLabel.setTextColor(Color.BLACK);
        elevLabel.setTextSize(20f);
        controls.addView(elevLabel);

        SeekBar elevSeek = new SeekBar(this);
        elevSeek.setMax(200);
        elevSeek.setProgress((int) mElevation);
        elevSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mElevation = progress;
                mBlurView.setElevation(mElevation);
                elevLabel.setText("Elevation: " + mElevation);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.setMargins(0, 20, 0, 20);
        controls.addView(elevSeek, seekParams);

        CheckBox blurCheck = new CheckBox(this);
        blurCheck.setText("Enable Blur");
        blurCheck.setTextColor(Color.BLACK);
        blurCheck.setChecked(true);
        blurCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mBlurView.setBackdropRenderEffect(
                            RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.MIRROR));
                } else {
                    mBlurView.setBackdropRenderEffect(null);
                }
            }
        });
        controls.addView(blurCheck);

        CheckBox hwLayerCheck = new CheckBox(this);
        hwLayerCheck.setText("Hardware Layer");
        hwLayerCheck.setTextColor(Color.BLACK);
        hwLayerCheck.setChecked(true);
        hwLayerCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mBlurView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                } else {
                    mBlurView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });
        controls.addView(hwLayerCheck);
        rootLayout.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(rootLayout);
    }
}
