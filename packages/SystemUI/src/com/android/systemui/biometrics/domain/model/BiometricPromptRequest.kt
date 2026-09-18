package com.android.systemui.biometrics.domain.model

import android.content.ComponentName
import android.graphics.Bitmap
import android.hardware.biometrics.FallbackOption
import android.hardware.biometrics.PromptContentView
import android.hardware.biometrics.PromptInfo
import com.android.systemui.biometrics.Utils
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.BiometricUserInfo

/**
 * Preferences for BiometricPrompt, such as title & description, that are immutable while the prompt
 * is showing.
 *
 * This roughly corresponds to a "request" by the system or an app to show BiometricPrompt and it
 * contains a subset of the information in a [PromptInfo] that is relevant to SysUI.
 */
sealed class BiometricPromptRequest(
    private val promptInfo: PromptInfo,
    val opPackageName: String,
    val userInfo: BiometricUserInfo,
    val operationInfo: BiometricOperationInfo,
) {
    // TODO: Lines between credential and biometrics have gotten increasingly blurred. Pulling up
    // all shared fields and this class should eventually be phased out
    val title: String
        get() = promptInfo.title?.toString() ?: ""

    val subtitle: String
        get() = promptInfo.subtitle?.toString() ?: ""

    val description: String
        get() = promptInfo.description?.toString() ?: ""

    val contentView: PromptContentView?
        get() = promptInfo.contentView

    val showEmergencyCallButton: Boolean
        get() = promptInfo.isShowEmergencyCallButton

    val logoBitmap: Bitmap?
        get() = promptInfo.logo

    val logoDescription: String?
        get() = promptInfo.logoDescription

    val negativeButtonText: String
        get() = promptInfo.negativeButtonText?.toString() ?: ""

    val fallbackOptions: List<FallbackOption>
        get() = promptInfo.fallbackOptions

    val componentNameForConfirmDeviceCredentialActivity: ComponentName?
        get() = promptInfo.realCallerForConfirmDeviceCredentialActivity

    val allowBackgroundAuthentication: Boolean
        get() = promptInfo.isAllowBackgroundAuthentication

    val credentialTitle: String
        get() {
            val credTitle = promptInfo.deviceCredentialTitle
            return if (!credTitle.isNullOrEmpty()) credTitle.toString() else title
        }

    val credentialSubtitle: String
        get() {
            val credSubtitle = promptInfo.deviceCredentialSubtitle
            return if (!credSubtitle.isNullOrEmpty()) credSubtitle.toString() else subtitle
        }

    val credentialDescription: String
        get() {
            val credDescription = promptInfo.deviceCredentialDescription
            return if (!credDescription.isNullOrEmpty()) credDescription.toString() else description
        }

    /** Prompt using one or more biometrics. */
    class Biometric(
        info: PromptInfo,
        userInfo: BiometricUserInfo,
        operationInfo: BiometricOperationInfo,
        val modalities: BiometricModalities,
        opPackageName: String,
    ) :
        BiometricPromptRequest(
            promptInfo = info,
            opPackageName = opPackageName,
            userInfo = userInfo,
            operationInfo = operationInfo,
        )

    /** Prompt using a credential (pin, pattern, password). */
    sealed class Credential(
        info: PromptInfo,
        userInfo: BiometricUserInfo,
        operationInfo: BiometricOperationInfo,
        opPackageName: String,
    ) :
        BiometricPromptRequest(
            promptInfo = info,
            opPackageName = opPackageName,
            userInfo = userInfo,
            operationInfo = operationInfo,
        ) {
        val biometricsRequested: Boolean = Utils.isBiometricAllowed(info)
        /* Whether credential is allowed, determined by Identity Check */
        val credentialAllowed: Boolean = Utils.isDeviceCredentialAllowed(info)
        /* Whether credential is requested by the prompt caller */
        val credentialRequested: Boolean = info.isDeviceCredentialAllowed

        /** PIN prompt. */
        class Pin(
            info: PromptInfo,
            userInfo: BiometricUserInfo,
            operationInfo: BiometricOperationInfo,
            opPackageName: String,
        ) : Credential(info, userInfo, operationInfo, opPackageName)

        /** Password prompt. */
        class Password(
            info: PromptInfo,
            userInfo: BiometricUserInfo,
            operationInfo: BiometricOperationInfo,
            opPackageName: String,
        ) : Credential(info, userInfo, operationInfo, opPackageName)

        /** Pattern prompt. */
        class Pattern(
            info: PromptInfo,
            userInfo: BiometricUserInfo,
            operationInfo: BiometricOperationInfo,
            opPackageName: String,
            val stealthMode: Boolean,
        ) : Credential(info, userInfo, operationInfo, opPackageName)
    }
}
