/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.systemui.statusbar

import android.content.Intent
import android.content.pm.UserInfo
import android.graphics.Color
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.biometrics.BiometricFingerprintConstants
import android.hardware.biometrics.BiometricSourceType
import android.os.BatteryManager
import android.os.RemoteException
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.TrustGrantFlags
import com.android.settingslib.fuelgauge.BatteryStatus
import com.android.systemui.Flags
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.dock.DockManager
import com.android.systemui.keyguard.KeyguardIndicationRotateTextViewController
import com.android.systemui.keyguard.KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE
import com.android.systemui.keyguard.KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP
import com.android.systemui.keyguard.ScreenLifecycle
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import java.text.NumberFormat
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class KeyguardIndicationControllerTest : KeyguardIndicationControllerBaseTest() {

    @Test
    fun afterFaceLockout_skipShowingFaceNotRecognized() {
        createController()
        onFaceLockoutError("lockout")
        verifyIndicationShown(INDICATION_TYPE_BIOMETRIC_MESSAGE, "lockout")
        clearInvocations(mRotateTextViewController)

        // WHEN face sends an onBiometricHelp BIOMETRIC_HELP_FACE_NOT_RECOGNIZED (face fail)
        mKeyguardUpdateMonitorCallback.onBiometricHelp(
            KeyguardUpdateMonitor.BIOMETRIC_HELP_FACE_NOT_RECOGNIZED,
            "Face not recognized",
            BiometricSourceType.FACE,
        )
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE) // no updated message
    }

    @Test
    fun createController_setIndicationAreaAgain_destroysPreviousRotateTextViewController() {
        // GIVEN a controller with a mocked rotate text view controller
        val mockedRotateTextViewController =
            mock(KeyguardIndicationRotateTextViewController::class.java)
        createController()
        mController.mRotateTextViewController = mockedRotateTextViewController

        // WHEN a new indication area is set
        mController.setIndicationArea(mIndicationArea)

        // THEN the previous rotateTextViewController is destroyed
        verify(mockedRotateTextViewController).destroy()
    }

    @Test
    fun createController_addsAlignmentListener() {
        createController()

        verify(mDockManager)
            .addAlignmentStateListener(any(DockManager.AlignmentStateListener::class.java))
    }

    @Test
    fun onAlignmentStateChanged_showsSlowChargingIndication() {
        createController()
        verify(mDockManager).addAlignmentStateListener(mAlignmentListener.capture())
        mController.setVisible(true)

        mAlignmentListener.getValue().onAlignmentStateChanged(DockManager.ALIGN_STATE_POOR)
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ALIGNMENT,
            mContext.resources.getString(R.string.dock_alignment_slow_charging),
        )
        assertThat(mKeyguardIndicationCaptor.getValue().textColor.defaultColor)
            .isEqualTo(mContext.getColor(R.color.misalignment_text_color))
    }

    @Test
    fun onAlignmentStateChanged_showsNotChargingIndication() {
        createController()
        verify(mDockManager).addAlignmentStateListener(mAlignmentListener.capture())
        mController.setVisible(true)

        mAlignmentListener.getValue().onAlignmentStateChanged(DockManager.ALIGN_STATE_TERRIBLE)
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ALIGNMENT,
            mContext.resources.getString(R.string.dock_alignment_not_charging),
        )
        assertThat(mKeyguardIndicationCaptor.getValue().textColor.defaultColor)
            .isEqualTo(mContext.getColor(R.color.misalignment_text_color))
    }

    @FlakyTest(bugId = 279944472)
    @Test
    fun onAlignmentStateChanged_whileDozing_showsSlowChargingIndication() {
        createController()
        verify(mDockManager).addAlignmentStateListener(mAlignmentListener.capture())
        mController.setVisible(true)
        mStatusBarStateListener.onDozingChanged(true)

        mAlignmentListener.getValue().onAlignmentStateChanged(DockManager.ALIGN_STATE_POOR)
        mExecutor.runAllReady()

        assertThat(mTextView.getText())
            .isEqualTo(mContext.resources.getString(R.string.dock_alignment_slow_charging))
        assertThat(mTextView.currentTextColor)
            .isEqualTo(mContext.getColor(R.color.misalignment_text_color))
    }

    @Test
    fun onAlignmentStateChanged_whileDozing_showsNotChargingIndication() {
        createController()
        verify(mDockManager).addAlignmentStateListener(mAlignmentListener.capture())
        mController.setVisible(true)
        mStatusBarStateListener.onDozingChanged(true)

        mAlignmentListener.getValue().onAlignmentStateChanged(DockManager.ALIGN_STATE_TERRIBLE)
        mExecutor.runAllReady()

        assertThat(mTextView.getText())
            .isEqualTo(mContext.resources.getString(R.string.dock_alignment_not_charging))
        assertThat(mTextView.currentTextColor)
            .isEqualTo(mContext.getColor(R.color.misalignment_text_color))
    }

    @Test
    fun disclosure_unmanaged() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(false)
        whenever(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile())
            .thenReturn(false)
        reset(mRotateTextViewController)

        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyHideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE)
    }

    @Test
    fun disclosure_deviceOwner_noOrganizationName() {
        createController()
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(true)
        whenever(mDevicePolicyManager.deviceOwnerOrganizationName).thenReturn(null)
        sendUpdateDisclosureBroadcast()
        mController.setVisible(true)
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureGeneric,
        )
    }

    @Test
    fun disclosure_orgOwnedDeviceWithManagedProfile_noOrganizationName() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile())
            .thenReturn(true)
        whenever(mUserManager.getProfiles(anyInt()))
            .thenReturn(
                mutableListOf<UserInfo?>(
                    UserInfo(10, /* name */ null, /* flags */ UserInfo.FLAG_MANAGED_PROFILE)
                )
            )
        whenever(mDevicePolicyManager.getOrganizationNameForUser(eq(10))).thenReturn(null)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureGeneric,
        )
    }

    @Test
    fun disclosure_deviceOwner_withOrganizationName() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(true)
        whenever(mDevicePolicyManager.deviceOwnerOrganizationName).thenReturn(ORGANIZATION_NAME)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureWithOrganization,
        )
    }

    @Test
    fun disclosure_orgOwnedDeviceWithManagedProfile_withOrganizationName() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile())
            .thenReturn(true)
        whenever(mUserManager.getProfiles(anyInt()))
            .thenReturn(
                mutableListOf<UserInfo?>(
                    UserInfo(10, /* name */ null, UserInfo.FLAG_MANAGED_PROFILE)
                )
            )
        whenever(mDevicePolicyManager.getOrganizationNameForUser(eq(10)))
            .thenReturn(ORGANIZATION_NAME)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureWithOrganization,
        )
    }

    @Test
    fun disclosure_updateOnTheFly() {
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(false)
        createController()
        mController.setVisible(true)

        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(true)
        whenever(mDevicePolicyManager.deviceOwnerOrganizationName).thenReturn(null)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureGeneric,
        )
        reset(mRotateTextViewController)

        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(true)
        whenever(mDevicePolicyManager.deviceOwnerOrganizationName).thenReturn(ORGANIZATION_NAME)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mDisclosureWithOrganization,
        )
        reset(mRotateTextViewController)

        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(false)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()

        verifyHideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE)
    }

    @Test
    fun disclosure_deviceOwner_financedDeviceWithOrganizationName() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mDevicePolicyManager.isDeviceManaged()).thenReturn(true)
        whenever(mDevicePolicyManager.deviceOwnerOrganizationName).thenReturn(ORGANIZATION_NAME)
        whenever(mDevicePolicyManager.isFinancedDevice).thenReturn(true)
        sendUpdateDisclosureBroadcast()
        mExecutor.runAllReady()
        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE,
            mFinancedDisclosureWithOrganization,
        )
    }

    @Test
    fun transientIndication_holdsWakeLock_whenDozing() {
        // GIVEN animations are enabled and text is visible
        mTextView.setAnimationsEnabled(true)
        createController()
        mController.setVisible(true)

        // WHEN transient text is shown
        mStatusBarStateListener.onDozingChanged(true)
        mController.showTransientIndication(TEST_STRING_RES)

        // THEN wake lock is held while the animation is running
        assertTrue("WakeLock expected: HELD, was: RELEASED", mWakeLock.isHeld)
    }

    @Test
    fun transientIndication_releasesWakeLock_whenDozing() {
        // GIVEN animations aren't enabled
        mTextView.setAnimationsEnabled(false)
        createController()
        mController.setVisible(true)

        // WHEN we show the transient indication
        mStatusBarStateListener.onDozingChanged(true)
        mController.showTransientIndication(TEST_STRING_RES)

        // THEN wake lock is RELEASED, not held
        Assert.assertFalse("WakeLock expected: RELEASED, was: HELD", mWakeLock.isHeld)
    }

    @Test
    fun transientIndication_visibleWhenDozing() {
        createController()
        mController.setVisible(true)

        mStatusBarStateListener.onDozingChanged(true)
        mController.showTransientIndication(TEST_STRING_RES)

        assertThat(mTextView.getText()).isEqualTo(mContext.resources.getString(TEST_STRING_RES))
        assertThat(mTextView.currentTextColor).isEqualTo(Color.WHITE)
        assertThat(mTextView.alpha).isEqualTo(1f)
    }

    @Test
    fun transientIndication_visibleWhenDozing_unlessSwipeUp_fromHelp() {
        createController()
        val message = "A message"

        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FACE_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FACE,
            )
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, message)
        reset(mRotateTextViewController)
        mStatusBarStateListener.onDozingChanged(true)

        assertThat(mTextView.getText()).isNotEqualTo(message)
    }

    @Test
    fun transientIndication_visibleWhenWokenUp() {
        createController()
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        val message = "helpMsg"

        // GIVEN screen is off
        whenever(mScreenLifecycle.screenState).thenReturn(ScreenLifecycle.SCREEN_OFF)

        // WHEN fingerprint help message received
        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FINGERPRINT,
            )

        // THEN message isn't shown right away
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)

        // WHEN the screen turns on
        mScreenObserver.onScreenTurnedOn()
        mTestableLooper.processAllMessages()

        // THEN the message is shown
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, message)
    }

    @Test
    fun whenStartedDreaming_hideBiometricMessage() {
        // GIVEN non-bypass face auth success message
        createController()
        mController.setVisible(true)

        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        whenever(mKeyguardBypassController.canBypass()).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(false)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, true)
        clearInvocations(mRotateTextViewController)

        // WHEN dreaming
        mStatusBarStateListener.onDreamingChanged(true)

        // THEN biometric messages are hidden
        verifyHideIndication(INDICATION_TYPE_BIOMETRIC_MESSAGE)
        verifyHideIndication(INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP)
    }

    @Test
    fun onBiometricHelp_coEx_faceFailure() {
        createController()

        // GIVEN unlocking with fingerprint is possible and allowed
        fingerprintUnlockIsPossibleAndAllowed()

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a face not recognized message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FACE_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FACE,
            )

        // THEN show sequential messages such as: 'face not recognized' and
        // 'try fingerprint instead'
        verifyIndicationMessage(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_face_failed),
        )
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun onBiometricHelp_coEx_faceUnavailable() {
        createController()

        // GIVEN unlocking with fingerprint is possible and allowed
        fingerprintUnlockIsPossibleAndAllowed()

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a face unavailable message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FACE_NOT_AVAILABLE,
                message,
                BiometricSourceType.FACE,
            )

        // THEN show sequential messages such as: 'face unlock unavailable' and
        // 'try fingerprint instead'
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, message)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun onBiometricHelp_coEx_faceUnavailable_fpNotAllowed() {
        createController()

        // GIVEN unlocking with fingerprint is possible but not allowed
        setupFingerprintUnlockPossible(true)
        whenever(mKeyguardUpdateMonitor.isUnlockingWithFingerprintAllowed).thenReturn(false)

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a face unavailable message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FACE_NOT_AVAILABLE,
                message,
                BiometricSourceType.FACE,
            )

        // THEN show sequential messages such as: 'face unlock unavailable' and
        // 'try fingerprint instead'
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, message)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricHelp_coEx_fpFailure_faceAlreadyUnlocked() {
        createController()

        // GIVEN face has already unlocked the device
        whenever(mKeyguardUpdateMonitor.isCurrentUserUnlockedWithFace).thenReturn(true)

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a fingerprint not recognized message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FINGERPRINT,
            )

        // THEN show sequential messages such as: 'Unlocked by face' and
        // 'Swipe up to open'
        verifyIndicationMessage(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_face_successful_unlock),
        )
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricHelp_coEx_fpFailure_trustAgentAlreadyUnlocked() {
        createController()

        // GIVEN trust agent has already unlocked the device
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a fingerprint not recognized message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FINGERPRINT,
            )

        // THEN show sequential messages such as: 'Kept unlocked by TrustAgent' and
        // 'Swipe up to open'
        verifyIndicationMessage(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_indication_trust_unlocked),
        )
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricHelp_coEx_fpFailure_trustAgentUnlocked_emptyTrustGrantedMessage() {
        createController()

        // GIVEN trust agent has already unlocked the device & trust granted message is empty
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)
        mController.showTrustGrantedMessage(false, "")

        val message = "A message"
        mController.setVisible(true)

        // WHEN there's a fingerprint not recognized message
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                message,
                BiometricSourceType.FINGERPRINT,
            )

        // THEN show action to unlock (ie: 'Swipe up to open')
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun transientIndication_visibleWhenDozing_unlessSwipeUp_fromError() {
        createController()
        val message = mContext.getString(R.string.keyguard_unlock)

        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricError(
                BiometricFaceConstants.FACE_ERROR_TIMEOUT,
                "A message",
                BiometricSourceType.FACE,
            )

        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, message)
        mStatusBarStateListener.onDozingChanged(true)

        assertThat(mTextView.getText()).isNotEqualTo(message)
    }

    @Test
    fun transientIndication_visibleWhenDozing_ignoresFingerprintErrorMsg() {
        createController()
        mController.setVisible(true)
        reset(mRotateTextViewController)

        // WHEN a fingerprint error user cancelled message is received
        mController
            .getKeyguardCallback()
            .onBiometricError(
                BiometricFingerprintConstants.FINGERPRINT_ERROR_USER_CANCELED,
                "foo",
                BiometricSourceType.FINGERPRINT,
            )

        // THEN no message is shown
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)
        verifyNoMessage(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRANSIENT)
    }

    @Test
    fun transientIndication_swipeUpToRetry() {
        createController()
        val message = mContext.getString(R.string.keyguard_retry)
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isFaceEnabledAndEnrolled).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(false)

        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricError(
                BiometricFaceConstants.FACE_ERROR_TIMEOUT,
                "A message",
                BiometricSourceType.FACE,
            )

        verify(mStatusBarKeyguardViewManager)
            .setKeyguardMessage(
                // TODO("Cannot convert element")
                eq(message),
                any(),
                any(),
            )
    }

    @Test
    fun transientIndication_swipeUpToRetry_faceAuthenticated() {
        createController()
        val message = mContext.getString(R.string.keyguard_retry)
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isFaceEnabledAndEnrolled).thenReturn(true)

        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricError(
                BiometricFaceConstants.FACE_ERROR_TIMEOUT,
                "A message",
                BiometricSourceType.FACE,
            )

        verify(mStatusBarKeyguardViewManager, never()).setKeyguardMessage(eq(message), any(), any())
    }

    @Test
    fun faceErrorTimeout_whenFingerprintEnrolled_doesNotShowMessage() {
        createController()
        fingerprintUnlockIsPossibleAndAllowed()
        val message = "A message"

        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricError(
                BiometricFaceConstants.FACE_ERROR_TIMEOUT,
                message,
                BiometricSourceType.FACE,
            )
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)
    }

    @Test
    fun sendFaceHelpMessages_fingerprintEnrolled() {
        createController()
        mController.mCoExAcquisitionMsgIdsToShowCallback.accept(
            mutableSetOf(
                BiometricFaceConstants.FACE_ACQUIRED_MOUTH_COVERING_DETECTED,
                BiometricFaceConstants.FACE_ACQUIRED_DARK_GLASSES_DETECTED,
            )
        )

        // GIVEN unlocking with fingerprint is possible and allowed
        fingerprintUnlockIsPossibleAndAllowed()

        // WHEN help messages received that are allowed to show
        val helpString = "helpString"
        val msgIds =
            intArrayOf(
                BiometricFaceConstants.FACE_ACQUIRED_MOUTH_COVERING_DETECTED,
                BiometricFaceConstants.FACE_ACQUIRED_DARK_GLASSES_DETECTED,
            )
        val messages: MutableSet<String> = HashSet()
        for (msgId in msgIds) {
            val message = helpString + msgId
            messages.add(message)
            mKeyguardUpdateMonitorCallback.onBiometricHelp(msgId, message, BiometricSourceType.FACE)
        }

        // THEN FACE_ACQUIRED_MOUTH_COVERING_DETECTED and DARK_GLASSES help messages shown
        verifyIndicationMessages(INDICATION_TYPE_BIOMETRIC_MESSAGE, messages)
    }

    @Test
    fun doNotSendMostFaceHelpMessages_fingerprintEnrolled() {
        createController()

        // GIVEN unlocking with fingerprint is possible and allowed
        fingerprintUnlockIsPossibleAndAllowed()

        // WHEN help messages received that aren't supposed to show
        val helpString = "helpString"
        val msgIds =
            intArrayOf(
                BiometricFaceConstants.FACE_ACQUIRED_FACE_OBSCURED,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_RIGHT,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_LEFT,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_HIGH,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_LOW,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_BRIGHT,
            )
        for (msgId in msgIds) {
            mKeyguardUpdateMonitorCallback.onBiometricHelp(
                msgId,
                helpString + msgId,
                BiometricSourceType.FACE,
            )
        }

        // THEN no messages shown
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)
    }

    @Test
    fun sendAllFaceHelpMessages_fingerprintNotEnrolled() {
        createController()

        // GIVEN fingerprint NOT possible
        fingerprintUnlockIsNotPossible()

        // WHEN help messages received
        val helpStrings: MutableSet<String> = HashSet()
        val helpString = "helpString"
        val msgIds =
            intArrayOf(
                BiometricFaceConstants.FACE_ACQUIRED_FACE_OBSCURED,
                BiometricFaceConstants.FACE_ACQUIRED_DARK_GLASSES_DETECTED,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_RIGHT,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_LEFT,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_HIGH,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_LOW,
                BiometricFaceConstants.FACE_ACQUIRED_TOO_BRIGHT,
            )
        for (msgId in msgIds) {
            val numberedHelpString = helpString + msgId
            mKeyguardUpdateMonitorCallback.onBiometricHelp(
                msgId,
                numberedHelpString,
                BiometricSourceType.FACE,
            )
            helpStrings.add(numberedHelpString)
        }

        // THEN message shown for each call
        verifyIndicationMessages(INDICATION_TYPE_BIOMETRIC_MESSAGE, helpStrings)
    }

    @Test
    fun sendTooDarkFaceHelpMessages_onTimeout_noFpEnrolled() {
        createController()

        // GIVEN fingerprint not possible
        fingerprintUnlockIsNotPossible()

        // WHEN help message received and deferred message is valid
        val helpString = "helpMsg"
        whenever(mFaceHelpMessageDeferral.getDeferredMessage()).thenReturn(helpString)
        whenever(
                mFaceHelpMessageDeferral.shouldDefer(BiometricFaceConstants.FACE_ACQUIRED_TOO_DARK)
            )
            .thenReturn(true)
        mKeyguardUpdateMonitorCallback.onBiometricHelp(
            BiometricFaceConstants.FACE_ACQUIRED_TOO_DARK,
            helpString,
            BiometricSourceType.FACE,
        )

        // THEN help message not shown yet
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)

        // WHEN face timeout error received
        mKeyguardUpdateMonitorCallback.onBiometricError(
            BiometricFaceConstants.FACE_ERROR_TIMEOUT,
            "face timeout",
            BiometricSourceType.FACE,
        )

        // THEN the low light message shows with suggestion to swipe up to unlock
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, helpString)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun sendTooDarkFaceHelpMessages_onTimeout_fingerprintEnrolled() {
        createController()

        // GIVEN unlocking with fingerprint is possible and allowed
        fingerprintUnlockIsPossibleAndAllowed()

        // WHEN help message received and deferredMessage is valid
        val helpString = "helpMsg"
        whenever(mFaceHelpMessageDeferral.getDeferredMessage()).thenReturn(helpString)
        whenever(
                mFaceHelpMessageDeferral.shouldDefer(BiometricFaceConstants.FACE_ACQUIRED_TOO_DARK)
            )
            .thenReturn(true)
        mKeyguardUpdateMonitorCallback.onBiometricHelp(
            BiometricFaceConstants.FACE_ACQUIRED_TOO_DARK,
            helpString,
            BiometricSourceType.FACE,
        )

        // THEN help message not shown yet
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)

        // WHEN face timeout error received
        mKeyguardUpdateMonitorCallback.onBiometricError(
            BiometricFaceConstants.FACE_ERROR_TIMEOUT,
            "face timeout",
            BiometricSourceType.FACE,
        )

        // THEN the low light message shows and suggests trying fingerprint
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, helpString)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun indicationAreaHidden_untilBatteryInfoArrives() {
        createController()
        // level of -1 indicates missing info
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_UNKNOWN,
                -1, /* level */
                BatteryManager.BATTERY_PLUGGED_WIRELESS,
                100, /* health */
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.setVisible(true)
        mStatusBarStateListener.onDozingChanged(true)
        reset(mIndicationArea)

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        // VISIBLE is always called first
        verify(mIndicationArea).visibility = View.VISIBLE
        verify(mIndicationArea).visibility = View.GONE
    }

    @Test
    @Throws(RemoteException::class)
    fun onRefreshBatteryInfo_computesChargingTime() {
        createController()
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_CHARGING,
                80, /* level */
                BatteryManager.BATTERY_PLUGGED_WIRELESS,
                100, /* health */
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        verify(mIBatteryStats).computeChargeTimeRemaining()
    }

    @Test
    @Throws(RemoteException::class)
    fun onRefreshBatteryInfo_computesChargingTime_onlyWhenCharging() {
        createController()
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_CHARGING,
                80, /* level */
                0, /* plugged */
                100, /* health */
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        verify(mIBatteryStats, never()).computeChargeTimeRemaining()
    }

    /**
     * Regression test. We should not make calls to the system_process when updating the doze state.
     */
    @Test
    @Throws(RemoteException::class)
    fun setDozing_noIBatteryCalls() {
        createController()
        mController.setVisible(true)
        mStatusBarStateListener.onDozingChanged(true)
        mStatusBarStateListener.onDozingChanged(false)
        verify(mIBatteryStats, never()).computeChargeTimeRemaining()
    }

    @Test
    fun registersKeyguardStateCallback() {
        createController()
        verify(mKeyguardStateController)
            .addCallback(
                // TODO("Cannot convert element")
                any()
            )
    }

    @Test
    fun unlockMethodCache_listenerUpdatesPluggedIndication() {
        createController()
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)
        mController.setPowerPluggedIn(true)
        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST,
            mContext.getString(R.string.keyguard_indication_trust_unlocked),
        )
    }

    @Test
    fun onRefreshBatteryInfo_chargingWithLongLife_presentChargingLimited() {
        createController()
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_CHARGING,
                80, /* level */
                BatteryManager.BATTERY_PLUGGED_AC,
                BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE,
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BATTERY,
            mContext.getString(
                R.string.keyguard_plugged_in_charging_limited,
                NumberFormat.getPercentInstance().format((80 / 100f).toDouble()),
            ),
        )
    }

    @Test
    fun onRefreshBatteryInfo_fullChargedWithLongLife_presentChargingLimited() {
        createController()
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_CHARGING,
                100, /* level */
                BatteryManager.BATTERY_PLUGGED_AC,
                BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE,
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BATTERY,
            mContext.getString(
                R.string.keyguard_plugged_in_charging_limited,
                NumberFormat.getPercentInstance().format((100 / 100f).toDouble()),
            ),
        )
    }

    @Test
    fun onRefreshBatteryInfo_fullChargedWithoutLongLife_presentCharged() {
        createController()
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_CHARGING,
                100, /* level */
                BatteryManager.BATTERY_PLUGGED_AC,
                BatteryManager.CHARGING_POLICY_DEFAULT,
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BATTERY,
            mContext.getString(R.string.keyguard_charged),
        )
    }

    @Test
    fun onRefreshBatteryInfo_dozing_dischargingWithLongLife_presentBatteryPercentage() {
        createController()
        mController.setVisible(true)
        val status =
            BatteryStatus(
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                90, /* level */
                0, /* plugged */
                BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE,
                0, /* maxChargingWattage */
                true, /* present */
            )

        mController.getKeyguardCallback().onRefreshBatteryInfo(status)
        mStatusBarStateListener.onDozingChanged(true)

        val percentage = NumberFormat.getPercentInstance().format((90 / 100f).toDouble())
        assertThat(mTextView.getText()).isEqualTo(percentage)
    }

    @Test
    fun onRequireUnlockForNfc_showsRequireUnlockForNfcIndication() {
        createController()
        mController.setVisible(true)
        val message = mContext.getString(R.string.require_unlock_for_nfc)
        mController.getKeyguardCallback().onRequireUnlockForNfc()

        verifyTransientMessage(message)
    }

    @Test
    fun testEmptyOwnerInfoHidesIndicationArea() {
        createController()

        // GIVEN the owner info is set to an empty string & keyguard is showing
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        whenever(mLockPatternUtils.deviceOwnerInfo).thenReturn("")

        // WHEN asked to update the indication area
        mController.setVisible(true)
        mExecutor.runAllReady()

        // THEN the owner info should be hidden
        verifyHideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_OWNER_INFO)
    }

    @Test
    fun testOnKeyguardShowingChanged_notShowing_resetsMessages() {
        createController()

        // GIVEN keyguard isn't showing
        whenever(mKeyguardStateController.isShowing()).thenReturn(false)

        // WHEN keyguard showing changed called
        mKeyguardStateControllerCallback.onKeyguardShowingChanged()

        // THEN messages are reset
        verify(mRotateTextViewController).clearMessages()
        assertThat(mTextView.getText()).isEqualTo("")
    }

    @Test
    fun testOnKeyguardShowingChanged_showing_updatesPersistentMessages() {
        createController()
        mController.setVisible(true)
        mExecutor.runAllReady()
        reset(mRotateTextViewController)

        // GIVEN keyguard is showing and not dozing
        whenever(mKeyguardStateController.isShowing()).thenReturn(true)
        mController.setVisible(true)
        mExecutor.runAllReady()
        reset(mRotateTextViewController)

        // WHEN keyguard showing changed called
        mKeyguardStateControllerCallback.onKeyguardShowingChanged()
        mExecutor.runAllReady()

        // THEN persistent messages are updated (in this case, most messages are hidden since
        // no info is provided) - verify that this happens
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_DISCLOSURE)
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_OWNER_INFO)
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BATTERY)
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST)
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ALIGNMENT)
        verify(mRotateTextViewController)
            .hideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_LOGOUT)
    }

    @Test
    fun onTrustGrantedMessageDoesNotShowUntilTrustGranted() {
        createController()
        mController.setVisible(true)
        reset(mRotateTextViewController)

        // GIVEN a trust granted message but trust isn't granted
        val trustGrantedMsg = "testing trust granted message"
        mController
            .getKeyguardCallback()
            .onTrustGrantedForCurrentUser(false, false, TrustGrantFlags(0), trustGrantedMsg)

        verifyHideIndication(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST)

        // WHEN trust is granted
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)
        mKeyguardUpdateMonitorCallback.onTrustChanged(this.currentUser)

        // THEN verify the trust granted message shows
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST,
            trustGrantedMsg,
        )
    }

    @Test
    fun onTrustGrantedMessageShowsOnTrustGranted() {
        createController()
        mController.setVisible(true)

        // GIVEN trust is granted
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)

        // WHEN the showTrustGranted method is called
        val trustGrantedMsg = "testing trust granted message"
        mController
            .getKeyguardCallback()
            .onTrustGrantedForCurrentUser(false, false, TrustGrantFlags(0), trustGrantedMsg)

        // THEN verify the trust granted message shows
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST,
            trustGrantedMsg,
        )
    }

    @Test
    fun onTrustGrantedMessage_nullMessage_showsDefaultMessage() {
        createController()
        mController.setVisible(true)

        // GIVEN trust is granted
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)

        // WHEN the showTrustGranted method is called with a null message
        mController
            .getKeyguardCallback()
            .onTrustGrantedForCurrentUser(false, false, TrustGrantFlags(0), null)

        // THEN verify the default trust granted message shows
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST,
            context.getString(R.string.keyguard_indication_trust_unlocked),
        )
    }

    @Test
    fun onTrustGrantedMessage_emptyString_showsNoMessage() {
        createController()
        mController.setVisible(true)

        // GIVEN trust is granted
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)

        // WHEN the showTrustGranted method is called with an EMPTY string
        mController
            .getKeyguardCallback()
            .onTrustGrantedForCurrentUser(false, false, TrustGrantFlags(0), "")

        // THEN verify NO trust message is shown
        verifyNoMessage(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST)
    }

    @Test
    fun coEx_faceSuccess_showsPressToOpen() {
        // GIVEN bouncer isn't showing, can skip bouncer, udfps is supported, no a11y enabled
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(true)
        whenever(mAccessibilityManager.isEnabled).thenReturn(false)
        whenever(mAccessibilityManager.isTouchExplorationEnabled).thenReturn(false)
        createController()
        mController.setVisible(true)

        // WHEN face auth succeeds
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, false)

        // THEN 'face unlocked' then 'press unlock icon to open' message show
        val unlockedByFace = mContext.getString(R.string.keyguard_face_successful_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, unlockedByFace)
        val pressToOpen = mContext.getString(R.string.keyguard_unlock_press)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            pressToOpen,
        )
    }

    @Test
    fun coEx_faceSuccess_touchExplorationEnabled_showsFaceUnlockedSwipeToOpen() {
        // GIVEN bouncer isn't showing, can skip bouncer, udfps is supported, a11y enabled
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(true)
        whenever(mAccessibilityManager.isEnabled).thenReturn(true)
        whenever(mAccessibilityManager.isTouchExplorationEnabled).thenReturn(true)
        createController()
        mController.setVisible(true)

        // WHEN face authenticated
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, false)

        // THEN show 'face unlocked' and 'swipe up to open' messages
        val unlockedByFace = mContext.getString(R.string.keyguard_face_successful_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, unlockedByFace)
        val swipeUpToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            swipeUpToOpen,
        )
    }

    @Test
    fun coEx_faceSuccess_a11yEnabled_showsFaceUnlockedSwipeToOpen() {
        // GIVEN bouncer isn't showing, can skip bouncer, udfps is supported, a11y is enabled
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(true)
        whenever(mAccessibilityManager.isEnabled).thenReturn(true)
        createController()
        mController.setVisible(true)

        // WHEN face auth is successful
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, false)

        // THEN show 'face unlocked' and 'swipe up to open' messages
        val unlockedByFace = mContext.getString(R.string.keyguard_face_successful_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, unlockedByFace)
        val swipeUpToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            swipeUpToOpen,
        )
    }

    @Test
    fun faceOnly_faceSuccess_showsFaceUnlockedSwipeToOpen() {
        // GIVEN bouncer isn't showing, can skip bouncer, no udfps supported
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(false)
        createController()
        mController.setVisible(true)

        // WHEN face auth is successful
        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, false)

        // THEN show 'face unlocked' and 'swipe up to open' messages
        val unlockedByFace = mContext.getString(R.string.keyguard_face_successful_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, unlockedByFace)
        val swipeUpToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            swipeUpToOpen,
        )
    }

    @Test
    fun udfpsOnly_a11yEnabled_showsSwipeToOpen() {
        // GIVEN bouncer isn't showing, can skip bouncer, udfps is supported, a11y is enabled
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(true)
        whenever(mAccessibilityManager.isEnabled).thenReturn(true)
        whenever(mAccessibilityManager.isTouchExplorationEnabled).thenReturn(true)
        createController()
        mController.setVisible(true)

        // WHEN showActionToUnlock
        mController.showActionToUnlock()

        // THEN show 'swipe up to open' message
        val swipeToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, swipeToOpen)
    }

    @Test
    fun udfpsOnly_showsPressToOpen() {
        // GIVEN bouncer isn't showing, udfps is supported, a11y is NOT enabled, can skip bouncer
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(true)
        whenever(mAccessibilityManager.isEnabled).thenReturn(false)
        whenever(mAccessibilityManager.isTouchExplorationEnabled).thenReturn(false)
        createController()
        mController.setVisible(true)

        // WHEN showActionToUnlock
        mController.showActionToUnlock()

        // THEN show 'press unlock icon to open' message
        val pressToOpen = mContext.getString(R.string.keyguard_unlock_press)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, pressToOpen)
    }

    @Test
    fun canSkipBouncer_noSecurity_showSwipeToUnlockHint() {
        // GIVEN bouncer isn't showing, can skip bouncer, no security (udfps isn't supported,
        // face wasn't authenticated)
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.isUdfpsSupported).thenReturn(false)
        createController()
        mController.setVisible(true)

        // WHEN showActionToUnlock
        mController.showActionToUnlock()

        // THEN show 'swipe up to open' message
        val swipeToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, swipeToOpen)
    }

    @Test
    fun cannotSkipBouncer_showSwipeToUnlockHint() {
        // GIVEN bouncer isn't showing and cannot skip bouncer
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(false)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(false)
        createController()
        mController.setVisible(true)

        // WHEN showActionToUnlock
        mController.showActionToUnlock()

        // THEN show 'swipe up to open' message
        val swipeToOpen = mContext.getString(R.string.keyguard_unlock)
        verifyIndicationMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE, swipeToOpen)
    }

    @Test
    fun faceOnAcquired_processFrame() {
        createController()

        // WHEN face sends an acquired message
        val acquireInfo = 1
        mKeyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FACE, acquireInfo)

        // THEN face help message deferral should process the acquired frame
        verify(mFaceHelpMessageDeferral).processFrame(acquireInfo)
    }

    @Test
    fun fingerprintOnAcquired_noProcessFrame() {
        createController()

        // WHEN fingerprint sends an acquired message
        mKeyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FINGERPRINT, 1)

        // THEN face help message deferral should NOT process any acquired frames
        verify(mFaceHelpMessageDeferral, never()).processFrame(anyInt())
    }

    @Test
    fun faceOnAcquiredStart_hideFaceMessages() {
        createController()

        // GIVEN face sends an onBiometricHelp
        mKeyguardUpdateMonitorCallback.onBiometricHelp(1, "placeholder", BiometricSourceType.FACE)

        // WHEN face sends a FACE_ACQURIED_START
        val acquireInfo = BiometricFaceConstants.FACE_ACQUIRED_START
        mKeyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FACE, acquireInfo)

        // THEN the biometric (fingerprint) message should be hidden
        verify(mRotateTextViewController).hideIndication(INDICATION_TYPE_BIOMETRIC_MESSAGE)
    }

    @Test
    fun faceOnAcquiredStart_doNotHideFingerprintMessage() {
        createController()

        // GIVEN fingerprint sends an onBiometricHelp
        mKeyguardUpdateMonitorCallback.onBiometricHelp(
            1,
            "placeholder",
            BiometricSourceType.FINGERPRINT,
        )

        // WHEN face sends a FACE_ACQURIED_START
        val acquireInfo = BiometricFaceConstants.FACE_ACQUIRED_START
        mKeyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FACE, acquireInfo)

        // THEN the biometric (fingerprint) message should NOT be hidden
        verify(mRotateTextViewController, never()).hideIndication(INDICATION_TYPE_BIOMETRIC_MESSAGE)
    }

    @Test
    fun onBiometricHelp_fingerprint_faceHelpMessageDeferralDoesNothing() {
        createController()

        // WHEN fingerprint sends an onBiometricHelp
        mKeyguardUpdateMonitorCallback.onBiometricHelp(
            1,
            "placeholder",
            BiometricSourceType.FINGERPRINT,
        )

        // THEN face help message deferral is NOT: reset, updated, or checked for shouldDefer
        verify(mFaceHelpMessageDeferral, never()).reset()
        verify(mFaceHelpMessageDeferral, never()).updateMessage(anyInt(), anyString())
        verify(mFaceHelpMessageDeferral, never()).shouldDefer(anyInt())
    }

    @Test
    fun onBiometricFailed_resetFaceHelpMessageDeferral() {
        createController()

        // WHEN face sends an onBiometricAuthFailed
        mKeyguardUpdateMonitorCallback.onBiometricAuthFailed(BiometricSourceType.FACE)

        // THEN face help message deferral is reset
        verify(mFaceHelpMessageDeferral).reset()
    }

    @Test
    fun onBiometricError_resetFaceHelpMessageDeferral() {
        createController()

        // WHEN face has an error
        mKeyguardUpdateMonitorCallback.onBiometricError(4, "string", BiometricSourceType.FACE)

        // THEN face help message deferral is reset
        verify(mFaceHelpMessageDeferral).reset()
    }

    @Test
    fun onBiometricHelp_faceAcquiredInfo_faceHelpMessageDeferral() {
        createController()

        // WHEN face sends an onBiometricHelp BIOMETRIC_HELP_FACE_NOT_RECOGNIZED
        val msgId = 1
        val helpString = "test"
        mKeyguardUpdateMonitorCallback.onBiometricHelp(msgId, "test", BiometricSourceType.FACE)

        // THEN face help message deferral is NOT reset and message IS updated
        verify(mFaceHelpMessageDeferral, never()).reset()
        verify(mFaceHelpMessageDeferral).updateMessage(msgId, helpString)
    }

    @Test
    fun onBiometricError_faceLockedOutFirstTime_showsThePassedInMessage() {
        createController()
        onFaceLockoutError("first lockout")

        verifyIndicationShown(INDICATION_TYPE_BIOMETRIC_MESSAGE, "first lockout")
    }

    @Test
    fun onBiometricError_faceLockedOutFirstTimeAndFpAllowed_showsTheFpFollowupMessage() {
        createController()
        fingerprintUnlockIsPossibleAndAllowed()
        onFaceLockoutError("first lockout")

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun onBiometricError_faceLockedOutFirstTimeAndFpAllowed_forceAccessibilityLiveRegion() {
        createController()
        fingerprintUnlockIsPossibleAndAllowed()
        onFaceLockoutError("first lockout")

        verify(mRotateTextViewController)
            .updateIndication(
                eq(INDICATION_TYPE_BIOMETRIC_MESSAGE),
                mKeyguardIndicationCaptor.capture(),
                eq(true),
            )
        assertTrue(mKeyguardIndicationCaptor.getValue().forceAssertiveAccessibilityLiveRegion)

        verify(mRotateTextViewController)
            .updateIndication(
                eq(
                    KeyguardIndicationRotateTextViewController
                        .INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP
                ),
                mKeyguardIndicationCaptor.capture(),
                eq(true),
            )
        assertTrue(mKeyguardIndicationCaptor.getValue().forceAssertiveAccessibilityLiveRegion)
    }

    @Test
    fun onBiometricError_faceLockedOutFirstTimeAndFpNotAllowed_showsDefaultFollowup() {
        createController()
        fingerprintUnlockIsNotPossible()
        onFaceLockoutError("first lockout")

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricError_faceLockedOutSecondTimeInSession_showsUnavailableMessage() {
        createController()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)

        onFaceLockoutError("second lockout")

        verifyIndicationShown(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_face_unlock_unavailable),
        )
    }

    @Test
    fun onBiometricError_faceLockedOutSecondTimeOnBouncer_showsUnavailableMessage() {
        createController()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)
        whenever(mStatusBarKeyguardViewManager.isBouncerShowing).thenReturn(true)

        onFaceLockoutError("second lockout")

        verify(mStatusBarKeyguardViewManager)
            .setKeyguardMessage(
                eq(mContext.getString(R.string.keyguard_face_unlock_unavailable)),
                any(),
                any(),
            )
    }

    @Test
    fun onBiometricError_faceLockedOutSecondTimeButUdfpsActive_showsNoMessage() {
        createController()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)

        whenever(mAuthController.isUdfpsFingerDown).thenReturn(true)
        onFaceLockoutError("second lockout")

        verifyNoMoreInteractions(mRotateTextViewController)
    }

    @Test
    fun onBiometricError_faceLockedOutAgainAndFpAllowed_showsTheFpFollowupMessage() {
        createController()
        fingerprintUnlockIsPossibleAndAllowed()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)

        onFaceLockoutError("second lockout")

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun onBiometricError_faceLockedOutAgainAndFpNotAllowed_showsDefaultFollowup() {
        createController()
        fingerprintUnlockIsNotPossible()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)

        onFaceLockoutError("second lockout")

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricError_whenFaceLockoutReset_onLockOutError_showsPassedInMessage() {
        createController()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)
        whenever(mKeyguardUpdateMonitor.isFaceLockedOut).thenReturn(false)
        mKeyguardUpdateMonitorCallback.onLockedOutStateChanged(BiometricSourceType.FACE)

        onFaceLockoutError("second lockout")

        verifyIndicationShown(INDICATION_TYPE_BIOMETRIC_MESSAGE, "second lockout")
    }

    @Test
    fun onFpLockoutStateChanged_whenFpIsLockedOut_showsPersistentMessage() {
        createController()
        mController.setVisible(true)
        whenever(mKeyguardUpdateMonitor.isFingerprintLockedOut).thenReturn(true)

        mKeyguardUpdateMonitorCallback.onLockedOutStateChanged(BiometricSourceType.FINGERPRINT)

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_PERSISTENT_UNLOCK_MESSAGE,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onFpLockoutStateChanged_whenFpIsNotLockedOut_showsPersistentMessage() {
        createController()
        mController.setVisible(true)
        clearInvocations(mRotateTextViewController)
        whenever(mKeyguardUpdateMonitor.isFingerprintLockedOut).thenReturn(false)

        mKeyguardUpdateMonitorCallback.onLockedOutStateChanged(BiometricSourceType.FINGERPRINT)

        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_PERSISTENT_UNLOCK_MESSAGE
        )
    }

    @Test
    fun onVisibilityChange_showsPersistentMessage_ifFpIsLockedOut() {
        createController()
        mController.setVisible(false)
        whenever(mKeyguardUpdateMonitor.isFingerprintLockedOut).thenReturn(true)
        mKeyguardUpdateMonitorCallback.onLockedOutStateChanged(BiometricSourceType.FINGERPRINT)
        clearInvocations(mRotateTextViewController)

        mController.setVisible(true)

        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_PERSISTENT_UNLOCK_MESSAGE,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricError_whenFaceIsLocked_onMultipleLockOutErrors_showUnavailableMessage() {
        createController()
        onFaceLockoutError("first lockout")
        clearInvocations(mRotateTextViewController)
        whenever(mKeyguardUpdateMonitor.isFaceLockedOut).thenReturn(true)
        mKeyguardUpdateMonitorCallback.onLockedOutStateChanged(BiometricSourceType.FACE)

        onFaceLockoutError("second lockout")

        verifyIndicationShown(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_face_unlock_unavailable),
        )
    }

    @Test
    fun onBiometricError_screenIsTurningOn_faceLockedOutFpIsNotAvailable_showsMessage() {
        createController()
        screenIsTurningOn()
        fingerprintUnlockIsNotPossible()

        onFaceLockoutError("lockout error")
        verifyNoMoreInteractions(mRotateTextViewController)

        mScreenObserver.onScreenTurnedOn()

        verifyIndicationShown(INDICATION_TYPE_BIOMETRIC_MESSAGE, "lockout error")
        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_unlock),
        )
    }

    @Test
    fun onBiometricError_screenIsTurningOn_faceLockedOutFpIsAvailable_showsMessage() {
        createController()
        screenIsTurningOn()
        fingerprintUnlockIsPossibleAndAllowed()

        onFaceLockoutError("lockout error")
        verifyNoMoreInteractions(mRotateTextViewController)

        mScreenObserver.onScreenTurnedOn()

        verifyIndicationShown(INDICATION_TYPE_BIOMETRIC_MESSAGE, "lockout error")
        verifyIndicationShown(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_BIOMETRIC_MESSAGE_FOLLOW_UP,
            mContext.getString(R.string.keyguard_suggest_fingerprint),
        )
    }

    @Test
    fun faceErrorMessageDroppedBecauseFingerprintMessageShowing() {
        createController()
        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                "fp not recognized",
                BiometricSourceType.FINGERPRINT,
            )
        clearInvocations(mRotateTextViewController)

        onFaceLockoutError("lockout")
        verifyNoMessage(INDICATION_TYPE_BIOMETRIC_MESSAGE)
    }

    @Test
    fun faceUnlockedMessageShowsEvenWhenFingerprintMessageShowing() {
        createController()
        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                "fp not recognized",
                BiometricSourceType.FINGERPRINT,
            )
        clearInvocations(mRotateTextViewController)

        whenever(mKeyguardUpdateMonitor.isFaceAuthenticated).thenReturn(true)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        mController
            .getKeyguardCallback()
            .onBiometricAuthenticated(0, BiometricSourceType.FACE, false)
        verifyIndicationMessage(
            INDICATION_TYPE_BIOMETRIC_MESSAGE,
            mContext.getString(R.string.keyguard_face_successful_unlock),
        )
    }

    @Test
    fun trustGrantedMessageShowsEvenWhenFingerprintMessageShowing() {
        createController()
        mController.setVisible(true)
        mController
            .getKeyguardCallback()
            .onBiometricHelp(
                KeyguardUpdateMonitor.BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED,
                "fp not recognized",
                BiometricSourceType.FINGERPRINT,
            )
        clearInvocations(mRotateTextViewController)

        // GIVEN trust is granted
        whenever(mKeyguardUpdateMonitor.getUserHasTrust(anyInt())).thenReturn(true)

        // WHEN the showTrustGranted method is called
        val trustGrantedMsg = "testing trust granted message after fp message"
        mController
            .getKeyguardCallback()
            .onTrustGrantedForCurrentUser(false, false, TrustGrantFlags(0), trustGrantedMsg)

        // THEN verify the trust granted message shows
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRUST,
            trustGrantedMsg,
        )
    }

    @Test
    fun updateAdaptiveAuthMessage_whenNotLockedByAdaptiveAuth_doesNotShowMsg() {
        // When the device is not locked by adaptive auth
        whenever(mKeyguardUpdateMonitor.isDeviceLockedByAdaptiveAuth(this.currentUser))
            .thenReturn(false)
        createController()
        mController.setVisible(true)

        // Verify that the adaptive auth message does not show
        verifyNoMessage(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ADAPTIVE_AUTH)
    }

    @Test
    fun updateAdaptiveAuthMessage_whenLockedByAdaptiveAuth_cannotSkipBouncer_showsMsg() {
        // When the device is locked by adaptive auth, and the user cannot skip bouncer
        whenever(mKeyguardUpdateMonitor.isDeviceLockedByAdaptiveAuth(this.currentUser))
            .thenReturn(true)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(false)
        createController()
        mController.setVisible(true)

        // Verify that the adaptive auth message shows
        val message = mContext.getString(R.string.keyguard_indication_after_adaptive_auth_lock)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ADAPTIVE_AUTH,
            message,
        )
    }

    @Test
    fun updateAdaptiveAuthMessage_whenLockedByAdaptiveAuth_canSkipBouncer_doesNotShowMsg() {
        createController()
        mController.setVisible(true)

        // When the device is locked by adaptive auth, but the device unlocked state changes and the
        // user can skip bouncer
        whenever(mKeyguardUpdateMonitor.isDeviceLockedByAdaptiveAuth(this.currentUser))
            .thenReturn(true)
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(true)
        mKeyguardStateControllerCallback.onUnlockedChanged()

        // Verify that the adaptive auth message does not show
        verifyNoMessage(KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ADAPTIVE_AUTH)
    }

    @Test
    @EnableFlags(Flags.FLAG_SHOW_LOCKED_BY_YOUR_WATCH_KEYGUARD_INDICATOR)
    fun updateWatchDisconnectedMessage_whenNotLockedWatchDisconnect_doesNotShowMsg() {
        createController()
        mController.setVisible(true)

        // Verify that the locked by your watch disconnect message does not show
        verifyNoMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_WATCH_DISCONNECTED
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_SHOW_LOCKED_BY_YOUR_WATCH_KEYGUARD_INDICATOR)
    fun updateWatchDisconnectedMessage_whenLockedWatchDisconnect_noSkipBouncer_showsMsg() {
        assertTrue(Flags.showLockedByYourWatchKeyguardIndicator())
        whenever(mKeyguardUpdateMonitor.getUserCanSkipBouncer(this.currentUser)).thenReturn(false)
        createController()
        mController.mDeviceEntryBiometricSettingsInteractorCallback.accept(
            AuthenticationFlags(
                mCurrentUserId,
                LockPatternUtils.StrongAuthTracker.SOME_AUTH_REQUIRED_AFTER_WATCH_DISCONNECTED,
            )
        )
        mController.setVisible(true)

        // Verify that the locked by your watch disconnect message shows
        val message = mContext.getString(R.string.keyguard_indication_after_watch_disconnected)
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_WATCH_DISCONNECTED,
            message,
        )
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_SECURE_LOCK_DEVICE)
    fun updateSecureLockDeviceMessage_whenSecureLockDeviceEnabled_showsMsg() =
        mKosmos.testScope.runTest {
            val isSecureLockDeviceEnabled by
                collectLastValue(mSecureLockDeviceInteractor.isSecureLockDeviceEnabled)
            mSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isTrue()

            createController()
            mController.setVisible(true)

            // Verify that the secure lock device message shows
            val message = mContext.getString(R.string.keyguard_indication_after_secure_lock_device)
            verifyIndicationMessage(
                KeyguardIndicationRotateTextViewController.INDICATION_TYPE_SECURE_LOCK_DEVICE,
                message,
            )
        }

    @Test
    @DisableFlags(Flags.FLAG_ADD_NEW_UNLOCK_HINT_ON_KEYGUARD)
    fun newUnlockHintDisabledHidesBothUnlockHints() {
        assertFalse(Flags.addNewUnlockHintOnKeyguard())
        whenever(mBouncerInteractor.isImproveLargeScreenInteractionEnabled).thenReturn(true)

        createController()
        mController.setVisible(true)

        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_CLICK_TO_UNLOCK_HINT
        )
        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_KEY_TO_UNLOCK_HINT
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_NEW_UNLOCK_HINT_ON_KEYGUARD)
    fun improveLargeScreenInteractionDisabledHidesBothUnlockHints() {
        assertTrue(Flags.addNewUnlockHintOnKeyguard())
        whenever(mBouncerInteractor.isImproveLargeScreenInteractionEnabled).thenReturn(false)

        createController()
        mController.setVisible(true)

        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_CLICK_TO_UNLOCK_HINT
        )
        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_KEY_TO_UNLOCK_HINT
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ADD_NEW_UNLOCK_HINT_ON_KEYGUARD,
        Flags.FLAG_PRESS_ANY_KEY_TO_ACCESS_BOUNCER_2,
    )
    fun newUnlockHintAndImproveLargeScreenInteractionEnabledShowsBothUnlockHints() {
        assertTrue(Flags.addNewUnlockHintOnKeyguard())
        whenever(mBouncerInteractor.isImproveLargeScreenInteractionEnabled).thenReturn(true)

        createController()

        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_CLICK_TO_UNLOCK_HINT,
            mContext.getString(com.android.internal.R.string.lockscreen_click_to_unlock_hint),
        )
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_KEY_TO_UNLOCK_HINT,
            mContext.getString(com.android.internal.R.string.lockscreen_key_to_unlock_hint),
        )
        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ENTER_TO_UNLOCK_HINT
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_PRESS_ANY_KEY_TO_ACCESS_BOUNCER_2)
    @EnableFlags(Flags.FLAG_ADD_NEW_UNLOCK_HINT_ON_KEYGUARD)
    fun newUnlockHintAndImproveLargeScreenInteractionEnabledShowsEnterUnlockHint() {
        assertTrue(Flags.addNewUnlockHintOnKeyguard())
        whenever(mBouncerInteractor.isImproveLargeScreenInteractionEnabled).thenReturn(true)

        createController()

        mController.setVisible(true)

        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_CLICK_TO_UNLOCK_HINT,
            mContext.getString(com.android.internal.R.string.lockscreen_click_to_unlock_hint),
        )
        verifyIndicationMessage(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_ENTER_TO_UNLOCK_HINT,
            mContext.getString(com.android.internal.R.string.lockscreen_enter_to_unlock_hint),
        )
        verifyHideIndication(
            KeyguardIndicationRotateTextViewController.INDICATION_TYPE_KEY_TO_UNLOCK_HINT
        )
    }

    private fun screenIsTurningOn() {
        whenever(mScreenLifecycle.screenState).thenReturn(ScreenLifecycle.SCREEN_TURNING_ON)
    }

    private fun sendUpdateDisclosureBroadcast() {
        mBroadcastReceiver.onReceive(mContext, Intent())
    }

    private fun verifyIndicationMessages(type: Int, messages: MutableSet<String>) {
        verify(mRotateTextViewController, times(messages.size))
            .updateIndication(eq(type), mKeyguardIndicationCaptor.capture(), anyBoolean())
        val kis = mKeyguardIndicationCaptor.getAllValues()

        for (ki in kis) {
            val msg = ki.message
            assertTrue(messages.contains(msg)) // check message is shown
            messages.remove(msg)
        }
        assertThat(messages.size).isEqualTo(0) // check that all messages accounted for (removed)
    }

    private fun verifyIndicationMessage(type: Int, message: String?) {
        verify(mRotateTextViewController)
            .updateIndication(eq(type), mKeyguardIndicationCaptor.capture(), anyBoolean())
        assertThat(mKeyguardIndicationCaptor.getValue().message).isEqualTo(message)
    }

    private fun verifyHideIndication(type: Int) {
        if (type == KeyguardIndicationRotateTextViewController.INDICATION_TYPE_TRANSIENT) {
            verify(mRotateTextViewController).hideTransient()
            verify(mRotateTextViewController, never()).showTransient(anyString())
        } else {
            verify(mRotateTextViewController).hideIndication(type)
            verify(mRotateTextViewController, never())
                .updateIndication(eq(type), any(), anyBoolean())
        }
    }

    private fun verifyTransientMessage(message: String?) {
        verify(mRotateTextViewController)
            .showTransient(
                // TODO("Cannot convert element"),
                eq(message)
            )
    }

    private fun fingerprintUnlockIsNotPossible() {
        setupFingerprintUnlockPossible(false)
    }

    private fun fingerprintUnlockIsPossibleAndAllowed() {
        setupFingerprintUnlockPossible(true)
        whenever(mKeyguardUpdateMonitor.isUnlockingWithFingerprintAllowed).thenReturn(true)
    }

    private fun setupFingerprintUnlockPossible(possible: Boolean) {
        whenever(mKeyguardUpdateMonitor.isUnlockWithFingerprintPossible(this.currentUser))
            .thenReturn(possible)
    }

    private val currentUser: Int
        get() = mCurrentUserId

    private fun onFaceLockoutError(errMsg: String?) {
        mKeyguardUpdateMonitorCallback.onBiometricError(
            BiometricFaceConstants.FACE_ERROR_LOCKOUT_PERMANENT,
            errMsg,
            BiometricSourceType.FACE,
        )
    }
}
