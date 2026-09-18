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

package com.android.server.display;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.util.proto.ProtoOutputStream;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IShortcutService;
import com.android.server.policy.SingleKeyGestureDetector;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;

import java.io.PrintWriter;
import java.util.function.Consumer;

class FakeWindowManagerPolicy implements WindowManagerPolicy {

    @Override
    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutKeyReceiver) {

    }

    @Override
    public void onKeyguardOccludedChangedLw(boolean occluded) {

    }

    @Override
    public void applyKeyguardOcclusionChange() {
    }

    @Override
    public void showDismissibleKeyguard() {

    }

    @Override
    public KeyguardServiceDelegate getKeyguardServiceDelegate() {
        return null;
    }

    @Override
    public void setDefaultDisplay(DisplayContentInfo displayContentInfo) {

    }

    @Override
    public void init(Context context, WindowManagerFuncs windowManagerFuncs) {

    }

    @Override
    public int checkAddPermission(int type, boolean isRoundedCornerOverlay, String packageName,
            int[] outAppOp, int displayId) {
        return 0;
    }

    @Override
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {

    }

    @Override
    public boolean isKeyguardHostWindow(WindowManager.LayoutParams attrs) {
        return false;
    }

    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        return 0;
    }

    @Override
    public KeyboardShortcutGroup getApplicationLaunchKeyboardShortcuts(int deviceId) {
        return null;
    }

    @Override
    public int interceptMotionBeforeQueueingNonInteractive(int displayId, int source,
            int action, long whenNanos, int policyFlags) {
        return 0;
    }

    @Override
    public boolean interceptKeyBeforeDispatching(IBinder focusedToken, KeyEvent event) {
        return false;
    }

    @Override
    public boolean interceptUnhandledKey(KeyEvent event, IBinder focusedToken) {
        return false;
    }

    @Override
    public void setTopFocusedDisplay(int displayId) {

    }

    @Override
    public void setAllowLockscreenWhenOn(int displayId, boolean allow) {

    }

    @Override
    public void startedWakingUpGlobal(int reason) {

    }

    @Override
    public void finishedWakingUpGlobal(int reason) {

    }

    @Override
    public void startedGoingToSleepGlobal(int reason) {

    }

    @Override
    public void finishedGoingToSleepGlobal(int reason) {

    }

    @Override
    public void startedWakingUp(
            int displayGroupId, int pmWakeReason, boolean anyDefaultOrAdjacentGroupInteractive) {}

    @Override
    public void finishedWakingUp(int displayGroupId, int pmWakeReason) {

    }

    @Override
    public void startedGoingToSleep(
            int displayGroupId, int pmSleepReason, boolean anyDefaultOrAdjacentGroupInteractive) {}

    @Override
    public void finishedGoingToSleep(int displayGroupId, int pmSleepReason) {

    }

    @Override
    public void screenTurningOn(int displayId, ScreenOnListener screenOnListener) {
        if (screenOnListener != null) {
            screenOnListener.onScreenOn();
        }
    }

    @Override
    public void screenTurnedOn(int displayId) {

    }

    @Override
    public void screenTurningOff(int displayId, ScreenOffListener screenOffListener) {
        if (screenOffListener != null) {
            screenOffListener.onScreenOff();
        }
    }

    @Override
    public void screenTurnedOff(int displayId, boolean isSwappingDisplay) {

    }

    @Override
    public void onDisplaySwitchStart(int displayId) {

    }

    @Override
    public boolean isScreenOn() {
        return false;
    }

    @Override
    public boolean isScreenOn(int displayId) {
        return false;
    }

    @Override
    public boolean okToAnimate(boolean ignoreScreenOn) {
        return false;
    }

    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {

    }

    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {

    }

    @Override
    public void enableKeyguard(boolean enabled) {

    }

    @Override
    public void exitKeyguardSecurely(@NonNull Consumer<Boolean> callback) {

    }

    @Override
    public boolean isKeyguardLocked() {
        return false;
    }

    @Override
    public boolean isKeyguardSecure(int userId) {
        return false;
    }

    @Override
    public boolean isKeyguardOccluded() {
        return false;
    }

    @Override
    public boolean isKeyguardShowing() {
        return false;
    }

    @Override
    public boolean isKeyguardShowingAndNotOccluded() {
        return false;
    }

    @Override
    public boolean isKeyguardTrustedLw() {
        return false;
    }

    @Override
    public boolean inKeyguardRestrictedKeyInputMode() {
        return false;
    }

    @Override
    public void dismissKeyguardLw(
            @androidx.annotation.Nullable IKeyguardDismissCallback callback,
            CharSequence message) {

    }

    @Override
    public boolean isKeyguardDrawnLw() {
        return false;
    }

    @Override
    public void setSafeMode(boolean safeMode) {

    }

    @Override
    public void systemReady() {

    }

    @Override
    public void systemBooted() {

    }

    @Override
    public void showBootMessage(CharSequence msg, boolean always) {

    }

    @Override
    public void hideBootMessages() {

    }

    @Override
    public void userActivity(int displayGroupId, int event) {

    }

    @Override
    public void enableScreenAfterBoot() {

    }

    @Override
    public void setRecentsVisibilityLw(boolean visible) {

    }

    @Override
    public void setPipVisibilityLw(boolean visible) {

    }

    @Override
    public void setNavBarVirtualKeyHapticFeedbackEnabledLw(boolean enabled) {

    }

    @Override
    public boolean hasNavigationBar() {
        return false;
    }

    @Override
    public void lockNow(Bundle options) {

    }

    @Override
    public void showRecentApps() {

    }

    @Override
    public void showGlobalActions() {

    }

    @Override
    public boolean isUserSetupComplete() {
        return false;
    }

    @Override
    public int getUiMode() {
        return 0;
    }

    @Override
    public void setCurrentUserLw(int newUserId) {

    }

    @Override
    public void setSwitchingUser(boolean switching) {

    }

    @Override
    public void dump(String prefix, PrintWriter writer, String[] args) {

    }

    @Override
    public void dumpDebug(ProtoOutputStream proto, long fieldId) {

    }

    @Override
    public void startKeyguardExitAnimation(long startTime) {

    }

    @Override
    public void onSystemUiStarted() {

    }

    @Override
    public boolean canDismissBootAnimation() {
        return false;
    }

    @Override
    public boolean isGlobalKey(int keyCode) {
        return false;
    }

    @Override
    public void addSingleKeyRule(@NonNull SingleKeyGestureDetector.SingleKeyRule singleKeyRule) {

    }
}
