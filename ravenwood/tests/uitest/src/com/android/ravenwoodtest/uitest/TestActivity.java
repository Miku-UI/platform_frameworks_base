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
package com.android.ravenwoodtest.uitest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

/**
 * Empty {@link Activity} implementation with callback logging.
 */
public class TestActivity extends Activity {
    private static final String TAG = "TestActivity";

    public TestActivity() {
        Log.w(TAG, getClass().getSimpleName() + ".ctor called");
    }

    @Override
    public void finish() {
        Log.w(TAG, getClass().getSimpleName() + ".finish called");
        super.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.w(TAG, getClass().getSimpleName() + ".onCreate called");
    }

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        Log.w(TAG, getClass().getSimpleName() + ".onCreate called");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.w(TAG, getClass().getSimpleName() + ".onRestoreInstanceState called");
    }

    @Override
    public void onRestoreInstanceState(
            Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onRestoreInstanceState(savedInstanceState, persistentState);
        Log.w(TAG, getClass().getSimpleName() + ".onRestoreInstanceState called");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.w(TAG, getClass().getSimpleName() + ".onPostCreate called");
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        Log.w(TAG, getClass().getSimpleName() + ".onPostCreate called");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.w(TAG, getClass().getSimpleName() + ".onStart called");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.w(TAG, getClass().getSimpleName() + ".onRestart called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(TAG, getClass().getSimpleName() + ".onResume called");
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Log.w(TAG, getClass().getSimpleName() + ".onPostResume called");
    }

    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        super.onTopResumedActivityChanged(isTopResumedActivity);
        Log.w(TAG, getClass().getSimpleName() + ".onTopResumedActivityChanged called");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.w(TAG, getClass().getSimpleName() + ".onNewIntent called");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.w(TAG, getClass().getSimpleName() + ".onSaveInstanceState called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.w(TAG, getClass().getSimpleName() + ".onPause called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.w(TAG, getClass().getSimpleName() + ".onStop called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.w(TAG, getClass().getSimpleName() + ".onDestroy called");
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        Log.w(TAG, getClass().getSimpleName() + ".onContentChanged called");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.w(TAG, getClass().getSimpleName() + ".onWindowFocusChanged called");
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.w(TAG, getClass().getSimpleName() + ".onAttachedToWindow called");
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.w(TAG, getClass().getSimpleName() + ".onDetachedFromWindow called");
    }

    @Override
    public void onWindowDismissed(boolean finishTask, boolean suppressWindowTransition) {
        super.onWindowDismissed(finishTask, suppressWindowTransition);
        Log.w(TAG, getClass().getSimpleName() + ".onWindowDismissed called");
    }
}
