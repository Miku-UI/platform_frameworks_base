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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationChannel
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_ALWAYS
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_AUTO
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_NEVER
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.InternalNotificationsApi
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildChildNotificationEntry
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.buildSummaryNotificationEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection
import com.android.systemui.statusbar.notification.collection.listbuilder.OnAfterRenderBundleEntryListener
import com.android.systemui.statusbar.notification.collection.listbuilder.OnAfterRenderEntryListener
import com.android.systemui.statusbar.notification.collection.listbuilder.OnAfterRenderListListener
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeRenderListListener
import com.android.systemui.statusbar.notification.collection.provider.SectionStyleProvider
import com.android.systemui.statusbar.notification.collection.render.NotifRowController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.withArgCaptor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations.initMocks

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class RowAppearanceCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var coordinator: RowAppearanceCoordinator
    private lateinit var beforeRenderListListener: OnBeforeRenderListListener
    private lateinit var afterRenderEntryListener: OnAfterRenderEntryListener
    private lateinit var afterRenderBundleEntryListener: OnAfterRenderBundleEntryListener
    private lateinit var afterRenderListListener: OnAfterRenderListListener

    private lateinit var entry1: NotificationEntry
    private lateinit var entry2: NotificationEntry
    private lateinit var bundledSingleton: NotificationEntry
    private lateinit var bundledSoloSummary: NotificationEntry
    private lateinit var bundledSoloChild: NotificationEntry
    private lateinit var bundledChildWithSiblings: NotificationEntry
    private lateinit var bundledChildWithSiblingsParent: NotificationEntry
    private lateinit var bundleEntry: BundleEntry

    @Mock private lateinit var pipeline: NotifPipeline
    @Mock private lateinit var sectionStyleProvider: SectionStyleProvider

    @Mock private lateinit var section1: NotifSection
    @Mock private lateinit var section2: NotifSection
    @Mock private lateinit var controller1: NotifRowController
    @Mock private lateinit var controller2: NotifRowController
    @Mock private lateinit var controller3: NotifRowController
    @Mock private lateinit var controllerBundle: NotifRowController
    @Mock private lateinit var notificationSettingsInteractor: NotificationSettingsInteractor

    @Before
    fun setUp() {
        initMocks(this)
        coordinator =
            RowAppearanceCoordinator(
                mContext,
                sectionStyleProvider,
                notificationSettingsInteractor,
                kosmos.applicationCoroutineScope,
            )
        coordinator.attach(pipeline)
        beforeRenderListListener = withArgCaptor {
            verify(pipeline).addOnBeforeRenderListListener(capture())
        }
        afterRenderEntryListener = withArgCaptor {
            verify(pipeline).addOnAfterRenderEntryListener(capture())
        }
        afterRenderBundleEntryListener = withArgCaptor {
            verify(pipeline).addOnAfterRenderBundleEntryListener(capture())
        }
        afterRenderListListener = withArgCaptor {
            verify(pipeline).addOnAfterRenderListListener(capture())
        }
        entry1 = kosmos.buildNotificationEntry { setSection(section1) }
        entry2 = kosmos.buildNotificationEntry { setSection(section2) }

        // entries in a bundle

        // single notification, not in a group
        bundledSingleton =
            kosmos.buildNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }
        // summary notification with no children
        bundledSoloSummary =
            kosmos.buildSummaryNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }

        // child notification, only child in group
        bundledSoloChild =
            kosmos.buildChildNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }
        kosmos.buildSummaryNotificationEntry(listOf(bundledSoloChild)) {
            setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
            setSection(section2)
        }

        bundledChildWithSiblings =
            kosmos.buildChildNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }

        val bundledChildWithSiblings1 =
            kosmos.buildChildNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }
        bundledChildWithSiblingsParent =
            kosmos.buildSummaryNotificationEntry(
                listOf(bundledChildWithSiblings, bundledChildWithSiblings1)
            ) {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }

        bundleEntry = BundleEntry(BundleSpec.RECOMMENDED)
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))
    }

    @Test
    fun testSetSystemExpandedOnlyOnFirstIfNotBundle() {
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(false)
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(false)
        beforeRenderListListener.onBeforeRenderList(listOf(entry1, entry2))
        afterRenderEntryListener.onAfterRenderEntry(entry1, controller1)
        verify(controller1).setSystemExpanded(eq(true))
        afterRenderEntryListener.onAfterRenderEntry(entry2, controller2)
        verify(controller2).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpandedNeverIfMinimized() {
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(true)
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(true)
        beforeRenderListListener.onBeforeRenderList(listOf(entry1, entry2))
        afterRenderEntryListener.onAfterRenderEntry(entry1, controller1)
        verify(controller1).setSystemExpanded(eq(false))
        afterRenderEntryListener.onAfterRenderEntry(entry2, controller2)
        verify(controller2).setSystemExpanded(eq(false))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundle_singleChild_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))
        bundleEntry.addChild(bundledSingleton)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(true))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundles_singleChild_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))
        bundleEntry.addChild(bundledSingleton)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(true))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundle_singleChild_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))
        bundleEntry.addChild(bundledSingleton)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(false))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundle_multipleChildren_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))
        bundleEntry.addChild(bundledSingleton)
        bundleEntry.addChild(bundledSoloChild)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(false))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundles_multipleChildren_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))
        bundleEntry.addChild(bundledSingleton)
        bundleEntry.addChild(bundledSoloChild)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(true))
    }

    @Test
    @OptIn(InternalNotificationsApi::class)
    fun testSetSystemExpanded_bundle_singleBundle_multipleChildren_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))
        bundleEntry.addChild(bundledSingleton)
        bundleEntry.addChild(bundledSoloChild)

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry))
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_bundle_multipleBundles_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_bundle_multipleBundles_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(true))
    }

    @Test
    fun testSetSystemExpanded_bundle_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSingleton, mock(BundleEntry::class.java))
        )
        afterRenderBundleEntryListener.onAfterRenderEntry(bundleEntry, controllerBundle)

        verify(controllerBundle).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleBundle_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))
        afterRenderListListener.onAfterRenderList(listOf(bundleEntry, bundledSingleton))

        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller2)
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller3)

        verify(controller1).setSystemExpanded(eq(false))
        verify(controller2).setSystemExpanded(eq(false))
        verify(controller3).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(listOf(bundleEntry, bundledSingleton))
        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller2)
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller3)

        verify(controller1).setSystemExpanded(eq(false))
        verify(controller2).setSystemExpanded(eq(false))
        verify(controller3).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(listOf(bundledSingleton))
        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller2)
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller3)

        verify(controller1).setSystemExpanded(eq(false))
        verify(controller2).setSystemExpanded(eq(false))
        verify(controller3).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_NotInGroup_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSingleton, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_NotInGroup_multipleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSingleton, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)

        verify(controller1).setSystemExpanded(eq(true))
    }

    @Test
    fun testSetSystemExpanded_Bundled_NotInGroup_multipleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSingleton, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSingleton, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleSummary_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloSummary, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloSummary, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleSummary_multipleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloSummary, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloSummary, controller1)

        verify(controller1).setSystemExpanded(eq(true))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleSummary_multipleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloSummary, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloSummary, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleChild_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloChild, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleChild_multipleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloChild, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller1)

        verify(controller1).setSystemExpanded(eq(true))
    }

    @Test
    fun testSetSystemExpanded_Bundled_singleChild_multipleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledSoloChild, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledSoloChild, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_childWithSiblings_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblings, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_childWithSiblings_multipleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblings, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_childWithSiblings_multipleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblings, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblings, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_summaryWithMultipleChildren_multipleBundles_Never() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_NEVER))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblingsParent, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblingsParent, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_summaryWithMultipleChildren_multipleBundle_Auto() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_AUTO))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblingsParent, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblingsParent, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpanded_Bundled_summaryWithMultipleChildren_multipleBundle_Always() {
        whenever(notificationSettingsInteractor.shouldExpandBundles)
            .thenReturn(MutableStateFlow(EXPAND_BUNDLE_ALWAYS))

        afterRenderListListener.onAfterRenderList(
            listOf(bundledChildWithSiblingsParent, bundleEntry, mock(BundleEntry::class.java))
        )
        afterRenderEntryListener.onAfterRenderEntry(bundledChildWithSiblingsParent, controller1)

        verify(controller1).setSystemExpanded(eq(false))
    }
}
