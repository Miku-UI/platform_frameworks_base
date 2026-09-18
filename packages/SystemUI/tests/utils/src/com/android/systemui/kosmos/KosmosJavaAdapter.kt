/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.kosmos

import android.app.Notification
import android.app.Notification.FLAG_BUBBLE
import android.app.NotificationChannel
import android.content.Context
import android.content.applicationContext
import android.os.fakeExecutorHandler
import com.android.internal.logging.uiEventLoggerFake
import com.android.internal.widget.lockPatternUtils
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityIntentRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.animation.dialogTransitionAnimator
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.bouncer.data.repository.bouncerRepository
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.classifier.falsingCollector
import com.android.systemui.colorextraction.fakeSysuiColorExtractor
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.common.ui.domain.interactor.configurationInteractor
import com.android.systemui.communal.data.repository.fakeCommunalSceneRepository
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.ui.viewmodel.communalTransitionViewModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.data.repository.brightnessMirrorShowingRepository
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFingerprintAuthInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryUdfpsInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.display.data.repository.displayPhoneSubcomponentPerDisplayRepository
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.display.data.repository.displaySubcomponentPerDisplayRepository
import com.android.systemui.dump.realDumpManager
import com.android.systemui.globalactions.actionsDialogLiteDelegateFactory
import com.android.systemui.globalactions.data.repository.globalActionsRepository
import com.android.systemui.globalactions.domain.interactor.globalActionsInteractor
import com.android.systemui.haptics.msdl.bouncerHapticPlayer
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.fromLockscreenTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.fromPrimaryBouncerTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardSurfaceBehindInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.pulseExpansionInteractor
import com.android.systemui.keyguard.ui.viewmodel.glanceableHubToLockscreenTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.lockscreenToGlanceableHubTransitionViewModel
import com.android.systemui.model.fakeSysUIStatePerDisplayRepository
import com.android.systemui.model.sysUiState
import com.android.systemui.model.sysUiStateFactory
import com.android.systemui.model.sysuiStateInteractor
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.ui.view.mockWindowRootViewProvider
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.settings.displayTracker
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.data.repository.shadeConfigRepository
import com.android.systemui.shade.data.repository.shadeDialogContextInteractor
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.fakeShadeModeInteractor
import com.android.systemui.shade.domain.interactor.shadeDisplaysInteractor
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeLayoutParams
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.domain.interactor.shadeStatusBarComponentsInteractor
import com.android.systemui.shade.fakeShadeController
import com.android.systemui.shade.shadeController
import com.android.systemui.shade.ui.viewmodel.notificationShadeWindowModel
import com.android.systemui.statusbar.data.repository.fakeStatusBarModePerDisplayRepository
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.disableFlagsInteractor
import com.android.systemui.statusbar.multiDisplayStatusBarLogger
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.collection.mockNotifCollection
import com.android.systemui.statusbar.notification.collection.provider.mockNotificationDismissibilityProvider
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider
import com.android.systemui.statusbar.notification.collection.render.groupExpansionManager
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.seenNotificationsInteractor
import com.android.systemui.statusbar.notification.headsup.mockHeadsUpManager
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_FULL_PERSON
import com.android.systemui.statusbar.notification.row.AutomationNotificationBackgroundProvider
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.automationNotificationBackgroundProvider
import com.android.systemui.statusbar.notification.row.createPromotedOngoingRow
import com.android.systemui.statusbar.notification.row.createRow
import com.android.systemui.statusbar.notification.row.createRowBundle
import com.android.systemui.statusbar.notification.row.createRowGroup
import com.android.systemui.statusbar.notification.row.createRowWithEntry
import com.android.systemui.statusbar.notification.row.createRowWithNotif
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.row.expandableNotificationRowLogger
import com.android.systemui.statusbar.notification.row.mockNotificationActionClickManager
import com.android.systemui.statusbar.notification.row.ui.viewmodel.bundleHeaderViewModelFactory
import com.android.systemui.statusbar.notification.stack.domain.interactor.headsUpNotificationInteractor
import com.android.systemui.statusbar.phone.fakeAutoHideControllerStore
import com.android.systemui.statusbar.phone.keyguardBypassController
import com.android.systemui.statusbar.phone.scrimController
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.statusbar.phone.systemUIDialogManager
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.domain.interactor.wifiInteractor
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.domain.interactor.deviceProvisioningInteractor
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.topui.topUiController
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.fakeSelectedUserInteractor
import com.android.systemui.util.kotlin.javaAdapter
import com.android.systemui.util.settings.fakeGlobalSettings
import com.android.systemui.util.time.systemClock
import com.android.systemui.volume.dialog.captions.domain.volumeDialogCaptionsButtonInteractor
import com.android.systemui.volume.domain.interactor.volumeDialogInteractor
import com.android.systemui.wallpapers.domain.interactor.fakeWallpaperRepository
import com.android.systemui.wallpapers.domain.interactor.wallpaperInteractorFaked
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import java.util.Optional

/**
 * Helper for using [Kosmos] from Java.
 *
 * If your test class extends [SysuiTestCase], you may use the secondary constructor so that
 * [Kosmos.applicationContext] and [Kosmos.testCase] are automatically set.
 */
@Deprecated("Please convert your test to Kotlin and use [Kosmos] directly.")
class KosmosJavaAdapter() {
    constructor(testCase: SysuiTestCase) : this() {
        kosmos.applicationContext = testCase.context
        kosmos.testCase = testCase
    }

    private val kosmos = Kosmos()

    val testDispatcher by lazy { kosmos.testDispatcher }
    val testScope by lazy { kosmos.testScope }
    val fakeExecutor by lazy { kosmos.fakeExecutor }
    val fakeExecutorHandler by lazy { kosmos.fakeExecutorHandler }
    val activityIntentRepository by lazy { kosmos.activityIntentRepository.fake }
    val configurationController by lazy { kosmos.configurationController }
    val configurationRepository by lazy { kosmos.fakeConfigurationRepository }
    val configurationInteractor by lazy { kosmos.configurationInteractor }
    val bouncerRepository by lazy { kosmos.bouncerRepository }
    val communalRepository by lazy { kosmos.fakeCommunalSceneRepository }
    val communalTransitionViewModel by lazy { kosmos.communalTransitionViewModel }
    val activeNotificationsInteractor by lazy { kosmos.activeNotificationsInteractor }
    val headsUpNotificationInteractor by lazy { kosmos.headsUpNotificationInteractor }
    val seenNotificationsInteractor by lazy { kosmos.seenNotificationsInteractor }
    val keyguardRepository by lazy { kosmos.fakeKeyguardRepository }
    val keyguardBouncerRepository by lazy { kosmos.fakeKeyguardBouncerRepository }
    val keyguardBypassController by lazy { kosmos.keyguardBypassController }
    val keyguardInteractor by lazy { kosmos.keyguardInteractor }
    val keyguardTransitionRepository by lazy { kosmos.fakeKeyguardTransitionRepository }
    val keyguardTransitionInteractor by lazy { kosmos.keyguardTransitionInteractor }
    val keyguardStateController by lazy { kosmos.keyguardStateController }
    val keyguardUpdateMonitor by lazy { kosmos.keyguardUpdateMonitor }
    val powerRepository by lazy { kosmos.fakePowerRepository }
    val clock by lazy { kosmos.systemClock }
    val mobileConnectionsRepository by lazy { kosmos.mobileConnectionsRepository }
    val statusBarStateController by lazy { kosmos.statusBarStateController }
    val statusBarModePerDisplayRepository by lazy { kosmos.fakeStatusBarModePerDisplayRepository }
    val systemUiDisplaySubcomponentRepository by lazy {
        kosmos.displaySubcomponentPerDisplayRepository
    }
    val systemUiReferenceDisplaySubcomponentRepository by lazy {
        kosmos.displayPhoneSubcomponentPerDisplayRepository
    }
    val shadeLayoutParams by lazy { kosmos.shadeLayoutParams }
    val autoHideControllerStore by lazy { kosmos.fakeAutoHideControllerStore }
    val interactionJankMonitor by lazy { kosmos.interactionJankMonitor }
    val sceneInteractor by lazy { kosmos.sceneInteractor }
    val sceneBackInteractor by lazy { kosmos.sceneBackInteractor }
    val falsingCollector by lazy { kosmos.falsingCollector }
    val powerInteractor by lazy { kosmos.powerInteractor }
    val pulseExpansionInteractor by lazy { kosmos.pulseExpansionInteractor }
    val deviceEntryInteractor by lazy { kosmos.deviceEntryInteractor }
    val fakeDeviceEntryRepository by lazy { kosmos.fakeDeviceEntryRepository }
    val deviceEntryUdfpsInteractor by lazy { kosmos.deviceEntryUdfpsInteractor }
    val deviceUnlockedInteractor by lazy { kosmos.deviceUnlockedInteractor }
    val deviceEntryFingerprintAuthInteractor by lazy { kosmos.deviceEntryFingerprintAuthInteractor }
    val deviceEntryFingerprintAuthRepository by lazy { kosmos.deviceEntryFingerprintAuthRepository }
    val fingerprintPropertyRepository by lazy { kosmos.fingerprintPropertyRepository }
    val communalInteractor by lazy { kosmos.communalInteractor }
    val communalSceneInteractor by lazy { kosmos.communalSceneInteractor }
    val communalSettingsInteractor by lazy { kosmos.communalSettingsInteractor }
    val deviceProvisioningInteractor by lazy { kosmos.deviceProvisioningInteractor }
    val fakeDeviceProvisioningRepository by lazy { kosmos.fakeDeviceProvisioningRepository }
    val fromLockscreenTransitionInteractor by lazy { kosmos.fromLockscreenTransitionInteractor }
    val fromPrimaryBouncerTransitionInteractor by lazy {
        kosmos.fromPrimaryBouncerTransitionInteractor
    }
    val globalActionsRepository by lazy { kosmos.globalActionsRepository }
    val globalActionsInteractor by lazy { kosmos.globalActionsInteractor }
    val keyguardClockInteractor by lazy { kosmos.keyguardClockInteractor }
    val brightnessMirrorShowingRepository by lazy { kosmos.brightnessMirrorShowingRepository }
    val shadeController by lazy { kosmos.shadeController }
    val fakeShadeController by lazy { kosmos.fakeShadeController }
    val shadeDisplaysInteractor by lazy { kosmos.shadeDisplaysInteractor }
    val shadeRepository by lazy { kosmos.shadeRepository }
    val shadeConfigRepository by lazy { kosmos.shadeConfigRepository }
    val shadeInteractor by lazy { kosmos.shadeInteractor }
    val notificationShadeWindowModel by lazy { kosmos.notificationShadeWindowModel }
    val visualStabilityProvider by lazy { kosmos.visualStabilityProvider }
    val wifiInteractor by lazy { kosmos.wifiInteractor }
    val fakeWifiRepository by lazy { kosmos.fakeWifiRepository }
    val volumeDialogInteractor by lazy { kosmos.volumeDialogInteractor }
    val volumeDialogCaptionsButtonInteractor by lazy { kosmos.volumeDialogCaptionsButtonInteractor }
    val alternateBouncerInteractor by lazy { kosmos.alternateBouncerInteractor }

    val scrimController by lazy { kosmos.scrimController }
    val keyguardOcclusionInteractor by lazy { kosmos.keyguardOcclusionInteractor }
    val msdlPlayer by lazy { kosmos.fakeMSDLPlayer }

    val shadeModeInteractor by lazy { kosmos.shadeModeInteractor }
    val fakeShadeModeInteractor by lazy { kosmos.fakeShadeModeInteractor }

    val bouncerHapticHelper by lazy { kosmos.bouncerHapticPlayer }

    val glanceableHubToLockscreenTransitionViewModel by lazy {
        kosmos.glanceableHubToLockscreenTransitionViewModel
    }
    val lockscreenToGlanceableHubTransitionViewModel by lazy {
        kosmos.lockscreenToGlanceableHubTransitionViewModel
    }
    val disableFlagsInteractor by lazy { kosmos.disableFlagsInteractor }
    val fakeDisableFlagsRepository by lazy { kosmos.fakeDisableFlagsRepository }
    val mockWindowRootViewProvider by lazy { kosmos.mockWindowRootViewProvider }
    val windowRootViewBlurInteractor by lazy { kosmos.windowRootViewBlurInteractor }
    val sysuiState by lazy { kosmos.sysUiState }
    val sysUiStateFactory by lazy { kosmos.sysUiStateFactory }
    val fakeSysUIStatePerDisplayRepository by lazy { kosmos.fakeSysUIStatePerDisplayRepository }
    val displayTracker by lazy { kosmos.displayTracker }
    val fakeShadeDisplaysRepository by lazy { kosmos.fakeShadeDisplaysRepository }
    val fakeFocusedDisplayRepository by lazy { kosmos.fakeFocusedDisplayRepository }
    val sysUIStateInteractor by lazy { kosmos.sysuiStateInteractor }
    val entryAdapterFactory by lazy { kosmos.entryAdapterFactory }
    val bundleHeaderViewModel by lazy { kosmos.bundleHeaderViewModelFactory.create() }
    val mockNotificationDismissibilityProvider by lazy {
        kosmos.mockNotificationDismissibilityProvider
    }
    val shadeDialogContextInteractor by lazy { kosmos.shadeDialogContextInteractor }
    val mockNotifCollection by lazy { kosmos.mockNotifCollection }
    val expandableNotificationRowLogger by lazy { kosmos.expandableNotificationRowLogger }
    val mockHeadsUpManager by lazy { kosmos.mockHeadsUpManager }
    val mockNotificationActionClickManager by lazy { kosmos.mockNotificationActionClickManager }
    val topUiController by lazy { kosmos.topUiController }
    val groupExpansionManager by lazy { kosmos.groupExpansionManager }
    val sysuiStateInteractor by lazy { kosmos.sysuiStateInteractor }
    val wallpaperInteractor by lazy { kosmos.wallpaperInteractorFaked }
    val wallpaperRepository by lazy { kosmos.fakeWallpaperRepository }
    val fakeSecureLockDeviceRepository by lazy { kosmos.fakeSecureLockDeviceRepository }
    val secureLockDeviceInteractor by lazy { kosmos.secureLockDeviceInteractor }
    val systemUIDialogManager by lazy { kosmos.systemUIDialogManager }
    val displayRepository by lazy { kosmos.displayRepository }
    val shadeStatusBarComponentsInteractor by lazy { kosmos.shadeStatusBarComponentsInteractor }
    val keyguardSurfaceBehindInteractor by lazy { kosmos.keyguardSurfaceBehindInteractor }
    val fakeUserRepository by lazy { kosmos.fakeUserRepository }
    val userRepository by lazy { kosmos.userRepository }
    val multiDisplayStatusBarLogger by lazy { kosmos.multiDisplayStatusBarLogger }
    val fakeSelectedUserInteractor by lazy { kosmos.fakeSelectedUserInteractor }
    val fakeSysuiColorExtractor by lazy { kosmos.fakeSysuiColorExtractor }
    val lockPatternUtils by lazy { kosmos.lockPatternUtils }
    val uiEventLoggerFake by lazy { kosmos.uiEventLoggerFake }
    val actionsDialogLiteDelegateFactory by lazy { kosmos.actionsDialogLiteDelegateFactory }
    val fakeGlobalSettings by lazy { kosmos.fakeGlobalSettings }
    val dialogTransitionAnimator by lazy { kosmos.dialogTransitionAnimator }
    val systemUIDialogDotFactory by lazy { kosmos.systemUIDialogDotFactory }
    val dumpManager by lazy { kosmos.realDumpManager }
    val automationNotificationBackgroundProvider by lazy {
        kosmos.automationNotificationBackgroundProvider
    }

    /** Use if you need a unique or mutate-able row */
    fun createRow(): ExpandableNotificationRow {
        return kosmos.createRow()
    }

    fun createRow(n: Notification): ExpandableNotificationRow {
        return kosmos.createRowWithNotif(n)
    }

    fun createRow(entry: NotificationEntry): ExpandableNotificationRow {
        return kosmos.createRowWithEntry(entry)
    }

    /** Creates an ExpandableNotificationRow with 4 children */
    fun createRowGroup(): ExpandableNotificationRow {
        return kosmos.createRowGroup()
    }

    fun createRowGroup(channel: NotificationChannel): ExpandableNotificationRow {
        return kosmos.createRowGroup(4, channel)
    }

    fun createRowBundle(spec: BundleSpec): ExpandableNotificationRow {
        return kosmos.createRowBundle(spec)
    }

    fun createPromotedOngoingRow(): ExpandableNotificationRow {
        return kosmos.createPromotedOngoingRow()
    }

    fun createBubbledEntry(block: NotificationEntryBuilder.() -> Unit = {}): NotificationEntry {
        return kosmos.makeEntryOfPeopleType {
            setCanBubble(true)
            modifyNotification(kosmos.applicationContext).setFlag(FLAG_BUBBLE, true)
            apply(block)
        }
    }

    fun createShortcutBubbledEntry(
        block: NotificationEntryBuilder.() -> Unit = {}
    ): NotificationEntry {
        return kosmos.makeEntryOfPeopleType() {
            setCanBubble(true)
            modifyNotification(kosmos.applicationContext)
                .setFlag(FLAG_BUBBLE, true)
                .setBubbleMetadata(
                    Notification.BubbleMetadata.Builder("shortcutId").setDesiredHeight(314).build()
                )
            apply(block)
        }
    }

    fun createNotificationEntry(n: Notification): NotificationEntry {
        return kosmos.buildNotificationEntry(notification = n)
    }

    fun createPeopleNotification(): NotificationEntry {
        return kosmos.makeEntryOfPeopleType(TYPE_FULL_PERSON)
    }

    fun buildNotificationEntry(block: NotificationEntryBuilder.() -> Unit = {}): NotificationEntry {
        return kosmos.buildNotificationEntry(block = block)
    }

    fun buildNotificationEntry(
        context: Context?,
        block: NotificationEntryBuilder.() -> Unit = {},
    ): NotificationEntry {
        return kosmos.buildNotificationEntry(context = context, block = block)
    }

    fun setAutomationNotificationBackgroundProvider(
        automationNotificationBackgroundProvider: AutomationNotificationBackgroundProvider
    ) {
        kosmos.automationNotificationBackgroundProvider =
            Optional.of(automationNotificationBackgroundProvider)
    }

    val javaAdapter by lazy { kosmos.javaAdapter }
}
