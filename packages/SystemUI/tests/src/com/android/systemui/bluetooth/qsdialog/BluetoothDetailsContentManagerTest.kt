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

package com.android.systemui.bluetooth.qsdialog

import android.graphics.drawable.Drawable
import android.testing.TestableLooper
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.UiEventLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsContentViewModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.testKosmos
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class BluetoothDetailsContentManagerTest : SysuiTestCase() {
    companion object {
        const val DEVICE_NAME = "device"
        const val DEVICE_CONNECTION_SUMMARY = "active"
        const val ENABLED = true
        const val CONTENT_HEIGHT = WRAP_CONTENT
    }

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val drawable = mock<Drawable>()

    private val uiEventLogger = mock<UiEventLogger>()

    private val logger = mock<BluetoothTileDialogLogger>()

    private val sysuiDialogFactory = mock<SystemUIDialog.Factory>()
    private val dialogManager = mock<SystemUIDialogManager>()
    private val activityStarter = mock<ActivityStarter>()

    private val fakeSystemClock = FakeSystemClock()

    private val uiProperties =
        BluetoothDetailsContentViewModel.UiProperties.build(
            isBluetoothEnabled = ENABLED,
            isAutoOnToggleFeatureAvailable = ENABLED,
        )

    private lateinit var icon: Pair<Drawable, String>
    private lateinit var mBluetoothDetailsContentManager: BluetoothDetailsContentManager
    private lateinit var deviceItem: DeviceItem
    private lateinit var detailsUIState: BluetoothDetailsContentViewModel.DetailsUIState
    private lateinit var contentView: View

    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        with(kosmos) {
            contentView =
                LayoutInflater.from(mContext).inflate(R.layout.bluetooth_tile_dialog, null)

            mBluetoothDetailsContentManager =
                BluetoothDetailsContentManager(
                    uiProperties,
                    CONTENT_HEIGHT,
                    /* isInDialog= */ true,
                    {},
                    testDispatcher,
                    fakeSystemClock,
                    uiEventLogger,
                    logger,
                    dialogTransitionAnimator,
                    activityStarter,
                )

            whenever(sysuiDialogFactory.create(any<SystemUIDialog.Delegate>(), any())).thenAnswer {
                SystemUIDialog(
                    mContext,
                    0,
                    SystemUIDialog.DEFAULT_DISMISS_ON_DEVICE_LOCK,
                    dialogManager,
                    fakeBroadcastDispatcher,
                    dialogTransitionAnimator,
                    it.getArgument(0),
                )
            }

            icon = Pair(drawable, DEVICE_NAME)
            deviceItem =
                DeviceItem(
                    type = DeviceItemType.AVAILABLE_MEDIA_BLUETOOTH_DEVICE,
                    cachedBluetoothDevice = cachedBluetoothDevice,
                    deviceName = DEVICE_NAME,
                    connectionSummary = DEVICE_CONNECTION_SUMMARY,
                    iconWithDescription = icon,
                    background = null,
                    actionIconAccessibilityLabelRes =
                        R.string.accessibility_bluetooth_device_settings_gear_with_name,
                )
            detailsUIState =
                BluetoothDetailsContentViewModel.DetailsUIState(
                    deviceItem = MutableStateFlow(null),
                    shouldAnimateProgressBar = MutableStateFlow(null),
                    audioSharingButton = MutableStateFlow(null),
                    bluetoothState = MutableStateFlow(null),
                    bluetoothAutoOn = MutableStateFlow(null),
                )
            whenever(cachedBluetoothDevice.isBusy).thenReturn(false)
        }
    }

    @Test
    fun testBindView_createRecyclerViewWithAdapter() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()

                    val recyclerView = contentView.requireViewById<RecyclerView>(R.id.device_list)

                    assertThat(recyclerView).isNotNull()
                    assertThat(recyclerView.visibility).isEqualTo(VISIBLE)
                    assertThat(recyclerView.adapter).isNotNull()
                    assertThat(recyclerView.layoutManager is LinearLayoutManager).isTrue()
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testBindView_displayBluetoothDevice() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()
                    fakeSystemClock.setElapsedRealtime(Long.MAX_VALUE)
                    mBluetoothDetailsContentManager.onDeviceItemUpdated(
                        listOf(deviceItem),
                        showSeeAll = false,
                        showPairNewDevice = false,
                    )

                    val recyclerView = contentView.requireViewById<RecyclerView>(R.id.device_list)
                    val adapter = recyclerView.adapter as BluetoothDetailsContentManager.Adapter
                    assertThat(adapter.itemCount).isEqualTo(1)
                    assertThat(adapter.getItem(0).deviceName).isEqualTo(DEVICE_NAME)
                    assertThat(adapter.getItem(0).connectionSummary)
                        .isEqualTo(DEVICE_CONNECTION_SUMMARY)
                    assertThat(adapter.getItem(0).iconWithDescription).isEqualTo(icon)
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testDeviceItemViewHolder_cachedDeviceNotBusy() {
        with(kosmos) {
            testScope.runTest {
                deviceItem.isEnabled = true

                val view =
                    LayoutInflater.from(mContext)
                        .inflate(R.layout.bluetooth_device_item, null, false)
                val viewHolder =
                    mBluetoothDetailsContentManager.Adapter().DeviceItemViewHolder(view)
                viewHolder.bind(deviceItem)
                val container = view.requireViewById<View>(R.id.bluetooth_device_row)

                assertThat(container).isNotNull()
                assertThat(container.isEnabled).isTrue()
                assertThat(container.hasOnClickListeners()).isTrue()
                val value by collectLastValue(mBluetoothDetailsContentManager.deviceItemClick)
                runCurrent()
                container.performClick()
                runCurrent()
                assertThat(value).isNotNull()
                value?.let {
                    assertThat(it.target).isEqualTo(DeviceItemClick.Target.ENTIRE_ROW)
                    assertThat(it.clickedView).isEqualTo(container)
                    assertThat(it.deviceItem).isEqualTo(deviceItem)
                }
            }
        }
    }

    @Test
    fun testDeviceItemViewHolder_cachedDeviceBusy() {
        with(kosmos) {
            deviceItem.isEnabled = false

            val view =
                LayoutInflater.from(mContext).inflate(R.layout.bluetooth_device_item, null, false)
            val viewHolder =
                BluetoothDetailsContentManager(
                        uiProperties,
                        CONTENT_HEIGHT,
                        /* isInDialog= */ true,
                        {},
                        testDispatcher,
                        fakeSystemClock,
                        uiEventLogger,
                        logger,
                        dialogTransitionAnimator,
                        activityStarter,
                    )
                    .Adapter()
                    .DeviceItemViewHolder(view)
            viewHolder.bind(deviceItem)
            val container = view.requireViewById<View>(R.id.bluetooth_device_row)

            assertThat(container).isNotNull()
            assertThat(container.isEnabled).isFalse()
            assertThat(container.hasOnClickListeners()).isTrue()
        }
    }

    @Test
    fun testDeviceItemViewHolder_clickActionIcon() {
        with(kosmos) {
            testScope.runTest {
                deviceItem.isEnabled = true

                val view =
                    LayoutInflater.from(mContext)
                        .inflate(R.layout.bluetooth_device_item, null, false)
                val viewHolder =
                    mBluetoothDetailsContentManager.Adapter().DeviceItemViewHolder(view)
                viewHolder.bind(deviceItem)
                val actionIconView = view.requireViewById<View>(R.id.gear_icon)

                assertThat(actionIconView).isNotNull()
                assertThat(actionIconView.hasOnClickListeners()).isTrue()
                val value by collectLastValue(mBluetoothDetailsContentManager.deviceItemClick)
                runCurrent()
                actionIconView.performClick()
                runCurrent()
                assertThat(value).isNotNull()
                value?.let {
                    assertThat(it.target).isEqualTo(DeviceItemClick.Target.ACTION_ICON)
                    assertThat(it.clickedView).isEqualTo(actionIconView)
                    assertThat(it.deviceItem).isEqualTo(deviceItem)
                }
            }
        }
    }

    @Test
    fun testOnDeviceUpdated_hideSeeAll_showPairNew() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()
                    fakeSystemClock.setElapsedRealtime(Long.MAX_VALUE)
                    mBluetoothDetailsContentManager.onDeviceItemUpdated(
                        listOf(deviceItem),
                        showSeeAll = false,
                        showPairNewDevice = true,
                    )

                    val seeAllButton = contentView.requireViewById<View>(R.id.see_all_button)
                    val pairNewButton =
                        contentView.requireViewById<View>(R.id.pair_new_device_button)
                    val recyclerView = contentView.requireViewById<RecyclerView>(R.id.device_list)
                    val adapter = recyclerView.adapter as BluetoothDetailsContentManager.Adapter
                    val scrollViewContent = contentView.requireViewById<View>(R.id.scroll_view)

                    assertThat(seeAllButton).isNotNull()
                    assertThat(seeAllButton.visibility).isEqualTo(GONE)
                    assertThat(pairNewButton).isNotNull()
                    assertThat(pairNewButton.visibility).isEqualTo(VISIBLE)
                    assertThat(adapter.itemCount).isEqualTo(1)
                    assertThat(scrollViewContent.layoutParams.height).isEqualTo(WRAP_CONTENT)
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testBindView_cachedHeightLargerThanMinHeight_displayFromCachedHeight() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    val cachedHeight = Int.MAX_VALUE
                    val contentManager =
                        BluetoothDetailsContentManager(
                            BluetoothDetailsContentViewModel.UiProperties.build(ENABLED, ENABLED),
                            cachedHeight,
                            /* isInDialog= */ true,
                            {},
                            testDispatcher,
                            fakeSystemClock,
                            uiEventLogger,
                            logger,
                            dialogTransitionAnimator,
                            activityStarter,
                        )
                    contentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    contentManager.start()
                    assertThat(
                            contentView.requireViewById<View>(R.id.scroll_view).layoutParams.height
                        )
                        .isEqualTo(cachedHeight)
                    contentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testBindView_cachedHeightLessThanMinHeight_displayFromUiProperties() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    val contentManager =
                        BluetoothDetailsContentManager(
                            BluetoothDetailsContentViewModel.UiProperties.build(ENABLED, ENABLED),
                            MATCH_PARENT,
                            /* isInDialog= */ true,
                            {},
                            testDispatcher,
                            fakeSystemClock,
                            uiEventLogger,
                            logger,
                            dialogTransitionAnimator,
                            activityStarter,
                        )
                    contentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    contentManager.start()
                    assertThat(
                            contentView.requireViewById<View>(R.id.scroll_view).layoutParams.height
                        )
                        .isGreaterThan(MATCH_PARENT)
                    contentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testBindView_bluetoothEnabled_autoOnToggleGone() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    val contentManager =
                        BluetoothDetailsContentManager(
                            BluetoothDetailsContentViewModel.UiProperties.build(ENABLED, ENABLED),
                            MATCH_PARENT,
                            /* isInDialog= */ true,
                            {},
                            testDispatcher,
                            fakeSystemClock,
                            uiEventLogger,
                            logger,
                            dialogTransitionAnimator,
                            activityStarter,
                        )
                    contentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    contentManager.start()
                    assertThat(
                            contentView
                                .requireViewById<View>(R.id.bluetooth_auto_on_toggle_layout)
                                .visibility
                        )
                        .isEqualTo(GONE)
                    contentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testOnAudioSharingButtonUpdated_visibleActive_activateButton() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()
                    fakeSystemClock.setElapsedRealtime(Long.MAX_VALUE)
                    mBluetoothDetailsContentManager.onAudioSharingButtonUpdated(
                        visibility = VISIBLE,
                        label = null,
                        isActive = true,
                    )

                    val audioSharingButton =
                        contentView.requireViewById<View>(R.id.audio_sharing_button)

                    assertThat(audioSharingButton).isNotNull()
                    assertThat(audioSharingButton.visibility).isEqualTo(VISIBLE)
                    assertThat(audioSharingButton.isActivated).isTrue()
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testOnAudioSharingButtonUpdated_visibleNotActive_inactivateButton() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()
                    fakeSystemClock.setElapsedRealtime(Long.MAX_VALUE)
                    mBluetoothDetailsContentManager.onAudioSharingButtonUpdated(
                        visibility = VISIBLE,
                        label = null,
                        isActive = false,
                    )

                    val audioSharingButton =
                        contentView.requireViewById<View>(R.id.audio_sharing_button)

                    assertThat(audioSharingButton).isNotNull()
                    assertThat(audioSharingButton.visibility).isEqualTo(VISIBLE)
                    assertThat(audioSharingButton.isActivated).isFalse()
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testOnAudioSharingButtonUpdated_gone_inactivateButton() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()
                    fakeSystemClock.setElapsedRealtime(Long.MAX_VALUE)
                    mBluetoothDetailsContentManager.onAudioSharingButtonUpdated(
                        visibility = GONE,
                        label = null,
                        isActive = false,
                    )

                    val audioSharingButton =
                        contentView.requireViewById<View>(R.id.audio_sharing_button)

                    assertThat(audioSharingButton).isNotNull()
                    assertThat(audioSharingButton.visibility).isEqualTo(GONE)
                    assertThat(audioSharingButton.isActivated).isFalse()
                    mBluetoothDetailsContentManager.releaseView()
                }
                job.cancel()
            }
        }
    }

    @Test
    fun testStartSettingsActivity_activityLaunched_detailsViewDismissed() {
        with(kosmos) {
            testScope.runTest {
                val job = launch {
                    mBluetoothDetailsContentManager.bind(
                        contentView = contentView,
                        dialog = null,
                        coroutineScope = this,
                        detailsUIState = detailsUIState,
                    )
                    mBluetoothDetailsContentManager.start()

                    val clickedView = View(context)
                    mBluetoothDetailsContentManager.onPairNewDeviceClicked(clickedView)

                    verify(uiEventLogger).log(BluetoothTileDialogUiEvent.PAIR_NEW_DEVICE_CLICKED)
                    verify(activityStarter)
                        .postStartActivityDismissingKeyguard(any(), anyInt(), anyOrNull())
                }
                job.cancel()
            }
        }
    }
}
