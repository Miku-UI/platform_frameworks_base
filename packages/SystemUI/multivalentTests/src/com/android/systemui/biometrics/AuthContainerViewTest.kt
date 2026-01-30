/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.biometrics

import android.content.packageManager
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.content.testableContext
import android.hardware.biometrics.BiometricAuthenticator
import android.hardware.biometrics.BiometricConstants
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.Flags
import android.hardware.biometrics.PromptContentViewWithMoreOptionsButton
import android.hardware.biometrics.PromptInfo
import android.hardware.biometrics.PromptVerticalListContentView
import android.hardware.face.FaceSensorPropertiesInternal
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal
import android.os.IBinder
import android.os.UserManager
import android.os.userManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.testing.ViewUtils
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN
import com.android.internal.widget.lockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.domain.interactor.promptSelectorInteractor
import com.android.systemui.biometrics.ui.viewmodel.credentialViewModel
import com.android.systemui.biometrics.ui.viewmodel.fallbackViewModelFactory
import com.android.systemui.biometrics.ui.viewmodel.promptViewModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.haptics.msdl.msdlPlayer
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.fakeShadeRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.junit.MockitoJUnit

private const val OP_PACKAGE_NAME = "biometric.testapp"

@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
open class AuthContainerViewTest : SysuiTestCase() {
    @JvmField @Rule var mockitoRule = MockitoJUnit.rule()

    @Mock lateinit var callback: AuthDialogCallback
    @Mock lateinit var windowToken: IBinder

    private val kosmos = testKosmos()
    private val context = kosmos.testableContext
    private val lockPatternUtils = kosmos.lockPatternUtils
    private val packageManager = kosmos.packageManager
    private val userManager: UserManager = kosmos.userManager

    private val testScope = kosmos.testScope
    private val fakeExecutor = kosmos.fakeExecutor
    private val fakeShadeRepository = kosmos.fakeShadeRepository

    private val defaultLogoIcon = context.getDrawable(R.drawable.ic_android)

    private var authContainer: TestAuthContainerView? = null

    @Before
    fun setup() {
        // Set up default logo icon
        whenever(packageManager.getApplicationIcon(OP_PACKAGE_NAME)).thenReturn(defaultLogoIcon)
        whenever(packageManager.getPackageInfo(any(String::class.java), anyInt()))
            .thenReturn(PackageInfo())
        context.setMockPackageManager(packageManager)
        whenever(lockPatternUtils.getCredentialTypeForUser(anyInt()))
            .thenReturn(CREDENTIAL_TYPE_PASSWORD)
    }

    @After
    fun tearDown() {
        if (authContainer?.isAttachedToWindow == true) {
            ViewUtils.detachView(authContainer)
        }
    }

    @Test
    fun testNotifiesAnimatedIn() {
        initializeFingerprintContainer()
        verify(callback)
            .onDialogAnimatedIn(authContainer?.requestId ?: 0L, true /* startFingerprintNow */)
    }

    @Test
    fun testDismissesOnBack() {
        val container = initializeFingerprintContainer(addToView = true)
        assertThat(container.parent).isNotNull()
        val root = container.rootView

        // Simulate back invocation
        container.onBackInvoked()
        waitForIdleSync()

        assertThat(container.parent).isNull()
        assertThat(root.isAttachedToWindow).isFalse()
    }

    @Test
    fun testDismissOnLock() {
        val container = initializeFingerprintContainer(addToView = true)
        assertThat(container.parent).isNotNull()
        val root = container.rootView

        // Simulate sleep/lock invocation
        container.onStartedGoingToSleep()
        waitForIdleSync()

        assertThat(container.parent).isNull()
        assertThat(root.isAttachedToWindow).isFalse()
    }

    @Test
    fun testDismissOnShadeInteraction() {
        val container = initializeFingerprintContainer(addToView = true)
        assertThat(container.parent).isNotNull()
        val root = container.rootView

        container.mBiometricCallback.onUserCanceled()
        waitForIdleSync()

        assertThat(container.parent).isNull()
        assertThat(root.isAttachedToWindow).isFalse()
    }

    @Test
    fun testCredentialPasswordDismissesOnBack() {
        val container = initializeCredentialPasswordContainer(addToView = true)
        assertThat(container.parent).isNotNull()
        val root = container.rootView

        // Simulate back invocation
        container.onBackInvoked()
        waitForIdleSync()

        assertThat(container.parent).isNull()
        assertThat(root.isAttachedToWindow).isFalse()
    }

    @Test
    fun testIgnoresAnimatedInWhenDismissed() {
        val container = initializeFingerprintContainer(addToView = false)
        container.dismissFromSystemServer()
        waitForIdleSync()

        verify(callback, never()).onDialogAnimatedIn(anyLong(), anyBoolean())

        container.addToView()
        waitForIdleSync()

        // attaching the view resets the state and allows this to happen again
        verify(callback)
            .onDialogAnimatedIn(authContainer?.requestId ?: 0L, true /* startFingerprintNow */)
    }

    @Test
    @DisableFlags(Flags.FLAG_BP_FALLBACK_OPTIONS)
    @Ignore("b/430630633")
    fun testIgnoresAnimatedInWhenDialogAnimatingOut() {
        val container = initializeFingerprintContainer(addToView = false)
        container.mContainerState = 4 // STATE_ANIMATING_OUT
        container.addToView()
        waitForIdleSync()

        verify(callback, never()).onDialogAnimatedIn(anyLong(), anyBoolean())
    }

    @Test
    fun testDismissBeforeIntroEnd() {
        val container = initializeFingerprintContainer()
        waitForIdleSync()

        // STATE_ANIMATING_IN = 1
        container?.mContainerState = 1

        container.dismissWithoutCallback(false)

        // the first time is triggered by initializeFingerprintContainer()
        // the second time was triggered by dismissWithoutCallback()
        verify(callback, times(2))
            .onDialogAnimatedIn(authContainer?.requestId ?: 0L, true /* startFingerprintNow */)
    }

    @Test
    fun testActionAuthenticated_sendsDismissedAuthenticated() {
        val container = initializeFingerprintContainer()
        container.mBiometricCallback.onAuthenticated()
        waitForIdleSync()

        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED),
                eq<ByteArray?>(null), /* credentialAttestation */
                eq(authContainer?.requestId ?: 0L),
            )
        assertThat(container.parent).isNull()
    }

    @Test
    fun testActionUserCanceled_sendsDismissedUserCanceled() {
        val container = initializeFingerprintContainer()
        container.mBiometricCallback.onUserCanceled()
        waitForIdleSync()

        verify(callback)
            .onSystemEvent(
                eq(BiometricConstants.BIOMETRIC_SYSTEM_EVENT_EARLY_USER_CANCEL),
                eq(authContainer?.requestId ?: 0L),
            )
        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_USER_CANCEL),
                eq<ByteArray?>(null), /* credentialAttestation */
                eq(authContainer?.requestId ?: 0L),
            )
        assertThat(container.parent).isNull()
    }

    @Test
    fun testActionButtonNegative_sendsDismissedButtonNegative() {
        val container = initializeFingerprintContainer()
        container.mBiometricCallback.onButtonNegative()
        waitForIdleSync()

        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_NEGATIVE),
                eq<ByteArray?>(null), /* credentialAttestation */
                eq(authContainer?.requestId ?: 0L),
            )
        assertThat(container.parent).isNull()
    }

    @Test
    fun testActionTryAgain_sendsTryAgain() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        container.mBiometricCallback.onButtonTryAgain()
        waitForIdleSync()

        verify(callback).onTryAgainPressed(authContainer?.requestId ?: 0L)
    }

    @Test
    @EnableFlags(Flags.FLAG_BP_FALLBACK_OPTIONS)
    fun testActionFallbackOption_sendsFallbackOption() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        container.mBiometricCallback.onFallbackOptionPressed(0)
        waitForIdleSync()

        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_FALLBACK_OPTION_BASE),
                eq<ByteArray?>(null),
                eq(authContainer?.requestId ?: 0L),
            )
    }

    @Test
    fun testActionCredentialMatched_dismissesWhenCredentialAllowed() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        val attestation = ByteArray(10)
        container.onCredentialMatched(attestation, true)
        waitForIdleSync()

        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_CREDENTIAL_CONFIRMED),
                eq(attestation),
                eq(authContainer?.requestId ?: 0L),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BP_FALLBACK_OPTIONS)
    fun testActionCredentialMatched_doesNotDismissWhenCredentialNotAllowed() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        val attestation = ByteArray(10)
        container.onCredentialMatched(attestation, false)
        waitForIdleSync()

        verify(callback, never())
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_CREDENTIAL_CONFIRMED),
                eq(attestation),
                eq(authContainer?.requestId ?: 0L),
            )
    }

    @Test
    fun testActionError_sendsDismissedError() {
        val container = initializeFingerprintContainer()
        container.mBiometricCallback.onError()
        waitForIdleSync()

        verify(callback)
            .onDismissed(
                eq(BiometricPrompt.DISMISSED_REASON_ERROR),
                eq<ByteArray?>(null), /* credentialAttestation */
                eq(authContainer?.requestId ?: 0L),
            )
        assertThat(authContainer!!.parent).isNull()
    }

    @Ignore("b/279650412")
    @Test
    fun testActionUseDeviceCredential_sendsOnDeviceCredentialPressed() {
        val container =
            initializeFingerprintContainer(
                authenticators =
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        container.mBiometricCallback.onUseDeviceCredential()
        waitForIdleSync()

        verify(callback).onDeviceCredentialPressed(authContainer?.requestId ?: 0L)
        assertThat(container.hasCredentialView()).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_BP_FALLBACK_OPTIONS)
    @Ignore("b/430630633")
    fun testAnimateToCredentialUI_invokesStartTransitionToCredentialUI() {
        val container =
            initializeFingerprintContainer(
                authenticators =
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        container.animateToCredentialUI(false)
        waitForIdleSync()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()

        // Check credential view persists after new attachment
        container.onAttachedToWindow()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_BP_FALLBACK_OPTIONS)
    @Ignore("b/430630633")
    fun testAnimateToCredentialUI_rotateCredentialUI() {
        val container =
            initializeFingerprintContainer(
                authenticators =
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        container.animateToCredentialUI(false)
        waitForIdleSync()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()

        // Check credential view persists after new attachment
        container.onAttachedToWindow()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()

        val configuration = Configuration(context.resources.configuration)
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        container.dispatchConfigurationChanged(configuration)
        waitForIdleSync()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    fun testShowBiometricUI_ContentViewWithMoreOptionsButton() {
        val container = initializeFingerprintContainer()

        waitForIdleSync()

        assertThat(container.hasCredentialView()).isFalse()
        assertThat(container.hasConstraintBiometricPrompt()).isTrue()

        // TODO(b/328843028): Use button.performClick() instead of calling
        //  onContentViewMoreOptionsButtonPressed() directly, and check |isButtonClicked| is true.
        container.mBiometricCallback.onContentViewMoreOptionsButtonPressed()
        waitForIdleSync()
        // container is gone
        assertThat(container.mContainerState).isEqualTo(5)
    }

    @Test
    fun testShowCredentialUI_withDescription() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        waitForIdleSync()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    fun testShowCredentialUI_withVerticalListContentView() {
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                verticalListContentView = PromptVerticalListContentView.Builder().build(),
            )
        // Two-step credential view should show -
        // 1. biometric prompt without sensor 2. credential view ui
        waitForIdleSync()
        assertThat(container.hasConstraintBiometricPrompt()).isTrue()
        assertThat(container.hasCredentialView()).isFalse()

        container.animateToCredentialUI(false)
        waitForIdleSync()
        // TODO(b/302735104): Check the reason why hasConstraintBiometricPrompt() is still true
        // assertThat(container.hasConstraintBiometricPrompt()).isFalse()
        assertThat(container.hasCredentialView()).isTrue()
    }

    @Test
    fun testShowCredentialUI_withContentViewWithMoreOptionsButton() {
        PromptContentViewWithMoreOptionsButton.Builder()
            .setMoreOptionsButtonListener(fakeExecutor) { _, _ -> }
            .build()
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        waitForIdleSync()

        assertThat(container.hasCredentialView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    fun testCredentialViewUsesEffectiveUserId() {
        kosmos.userManager
        whenever(userManager.getCredentialOwnerProfile(anyInt())).thenReturn(200)
        whenever(lockPatternUtils.getCredentialTypeForUser(eq(200)))
            .thenReturn(CREDENTIAL_TYPE_PATTERN)

        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        waitForIdleSync()

        assertThat(container.hasCredentialPatternView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    fun testCredentialUI_disablesClickingOnBackground() {
        val container = initializeCredentialPasswordContainer()
        assertThat(container.hasBiometricPrompt()).isFalse()
        assertThat(container.findViewById<View>(R.id.background)?.isImportantForAccessibility)
            .isFalse()

        container.findViewById<View>(R.id.background)?.performClick()
        waitForIdleSync()

        assertThat(container.hasCredentialPasswordView()).isTrue()
        assertThat(container.hasBiometricPrompt()).isFalse()
    }

    @Test
    fun testLayoutParams_hasSecureWindowFlag() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        assertThat((layoutParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0).isTrue()
    }

    @Test
    fun testLayoutParams_hasShowWhenLockedFlag() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        assertThat((layoutParams.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0)
            .isTrue()
    }

    @Test
    fun testLayoutParams_hasDimbehindWindowFlag() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        val lpFlags = layoutParams.flags
        val lpDimAmount = layoutParams.dimAmount

        assertThat((lpFlags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0).isTrue()
        assertThat(lpDimAmount).isGreaterThan(0f)
    }

    @Test
    fun testLayoutParams_excludesImeInsets() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        assertThat((layoutParams.fitInsetsTypes and WindowInsets.Type.ime()) == 0).isTrue()
    }

    @Test
    fun coexFaceRestartsOnTouch() {
        val container = initializeCoexContainer()

        container.onPointerDown()
        waitForIdleSync()

        container.onAuthenticationFailed(BiometricAuthenticator.TYPE_FACE, "failed")
        waitForIdleSync()

        verify(callback, never()).onTryAgainPressed(anyLong())

        container.onPointerDown()
        waitForIdleSync()

        verify(callback).onTryAgainPressed(authContainer?.requestId ?: 0L)
    }

    private fun initializeCredentialPasswordContainer(
        addToView: Boolean = true
    ): TestAuthContainerView {
        whenever(userManager.getCredentialOwnerProfile(anyInt())).thenReturn(20)
        whenever(lockPatternUtils.getCredentialTypeForUser(eq(20))).thenReturn(CREDENTIAL_TYPE_PIN)

        // In the credential view, clicking on the background (to cancel authentication) is not
        // valid. Thus, the listener should be null, and it should not be in the accessibility
        // hierarchy.
        val container =
            initializeFingerprintContainer(
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                addToView = addToView,
            )
        waitForIdleSync()

        assertThat(container.hasCredentialPasswordView()).isTrue()
        return container
    }

    private fun initializeFingerprintContainer(
        authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK,
        addToView: Boolean = true,
        verticalListContentView: PromptVerticalListContentView? = null,
    ) =
        initializeContainer(
            TestAuthContainerView(
                authenticators = authenticators,
                fingerprintProps = fingerprintSensorPropertiesInternal(),
                verticalListContentView = verticalListContentView,
            ),
            addToView,
        )

    private fun initializeCoexContainer(
        authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK,
        addToView: Boolean = true,
    ) =
        initializeContainer(
            TestAuthContainerView(
                authenticators = authenticators,
                fingerprintProps = fingerprintSensorPropertiesInternal(),
                faceProps = faceSensorPropertiesInternal(),
            ),
            addToView,
        )

    private fun initializeContainer(
        view: TestAuthContainerView,
        addToView: Boolean,
    ): TestAuthContainerView {
        authContainer = view

        if (addToView) {
            authContainer!!.addToView()
        }

        return authContainer!!
    }

    private inner class TestAuthContainerView(
        authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK,
        fingerprintProps: List<FingerprintSensorPropertiesInternal> = listOf(),
        faceProps: List<FaceSensorPropertiesInternal> = listOf(),
        verticalListContentView: PromptVerticalListContentView? = null,
        contentViewWithMoreOptionsButton: PromptContentViewWithMoreOptionsButton? = null,
    ) :
        AuthContainerView(
            Config().apply {
                mContext = this@AuthContainerViewTest.context
                mCallback = callback
                mSensorIds =
                    (fingerprintProps.map { it.sensorId } + faceProps.map { it.sensorId })
                        .toIntArray()
                mSkipAnimation = true
                mPromptInfo =
                    PromptInfo().apply {
                        this.authenticators = authenticators
                        if (verticalListContentView != null) {
                            this.contentView = verticalListContentView
                        } else if (contentViewWithMoreOptionsButton != null) {
                            this.contentView = contentViewWithMoreOptionsButton
                        }
                    }
                mOpPackageName = OP_PACKAGE_NAME
            },
            testScope.backgroundScope,
            fingerprintProps,
            faceProps,
            kosmos.wakefulnessLifecycle,
            kosmos.userManager,
            null /* authContextPlugins */,
            kosmos.lockPatternUtils,
            kosmos.interactionJankMonitor,
            { kosmos.promptSelectorInteractor },
            kosmos.promptViewModel.apply {
                this.iconViewModel.internal.activateIn(kosmos.testScope)
            },
            { kosmos.credentialViewModel },
            kosmos.fakeExecutor,
            kosmos.vibratorHelper,
            kosmos.msdlPlayer,
            kosmos.fallbackViewModelFactory,
        ) {
        override fun postOnAnimation(runnable: Runnable) {
            runnable.run()
        }
    }

    override fun waitForIdleSync() {
        testScope.runCurrent()
        TestableLooper.get(this).processAllMessages()
    }

    private fun AuthContainerView.addToView() {
        ViewUtils.attachView(this)
        waitForIdleSync()
        assertThat(isAttachedToWindow()).isTrue()
    }

    @Test
    fun testLayoutParams_hasCutoutModeAlwaysFlag() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        val lpFlags = layoutParams.flags

        assertThat(
                (lpFlags and WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS) != 0
            )
            .isTrue()
    }

    @Test
    fun testLayoutParams_excludesSystemBarInsets() {
        val layoutParams =
            AuthContainerView.getLayoutParams(windowToken, "", false /* isCredentialView */)
        assertThat((layoutParams.fitInsetsTypes and WindowInsets.Type.systemBars()) == 0).isTrue()
    }
}

private fun AuthContainerView.hasConstraintBiometricPrompt() =
    (findViewById<ConstraintLayout>(R.id.biometric_prompt_constraint_layout)?.childCount ?: 0) > 0

private fun AuthContainerView.hasBiometricPrompt() =
    (findViewById<ScrollView>(R.id.biometric_scrollview)?.childCount ?: 0) > 0

private fun AuthContainerView.hasCredentialView() =
    if (Flags.bpFallbackOptions()) {
        (findViewById<View>(R.id.credential_view)?.visibility ?: View.GONE) == View.VISIBLE
    } else {
        hasCredentialPatternView() || hasCredentialPasswordView()
    }

private fun AuthContainerView.hasCredentialPatternView() =
    findViewById<View>(R.id.lockPattern) != null

private fun AuthContainerView.hasCredentialPasswordView() =
    findViewById<View>(R.id.lockPassword) != null
