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

package com.android.systemui.statusbar.notification.collection.render

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.settings.UserTracker
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.NotificationPresenter
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.PipelineDumpable
import com.android.systemui.statusbar.notification.collection.PipelineDumper
import com.android.systemui.statusbar.notification.collection.coordinator.BundleCoordinator.Companion.debugBundleLog
import com.android.systemui.statusbar.notification.icon.IconManager
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.ExpandableView
import com.android.systemui.statusbar.notification.row.RowInflaterTask
import com.android.systemui.statusbar.notification.row.RowInflaterTaskLogger
import com.android.systemui.statusbar.notification.row.dagger.BundleRowComponent
import com.android.systemui.statusbar.notification.row.dagger.ExpandableNotificationRowComponent
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderViewModel
import com.android.systemui.statusbar.notification.stack.NotificationListContainer
import com.android.systemui.util.time.SystemClock
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Provider

/** Class that handles inflating BundleEntry view and controller, for use by NodeSpecBuilder. */
@SysUISingleton
class BundleBarn
@Inject
constructor(
    private val rowComponent: ExpandableNotificationRowComponent.Builder,
    private val bundleRowComponentFactory: BundleRowComponent.Factory,
    private val rowInflaterTaskProvider: Provider<RowInflaterTask>,
    private val listContainer: NotificationListContainer,
    @ShadeDisplayAware val context: Context,
    val systemClock: SystemClock,
    val logger: RowInflaterTaskLogger,
    val userTracker: UserTracker,
    private val presenterLazy: Lazy<NotificationPresenter?>? = null,
    private val iconManager: IconManager,
) : PipelineDumpable {

    /**
     * Map of [BundleEntry] key to [NodeController]: no key -> not started key maps to null ->
     * inflating key maps to controller -> inflated
     */
    private val keyToControllerMap = mutableMapOf<String, NotifViewController?>()

    /** Build view and controller for BundleEntry. */
    fun inflateBundleEntry(bundleEntry: BundleEntry) {
        debugBundleLog(TAG) { "inflateBundleEntry: ${bundleEntry.key}" }
        if (keyToControllerMap.containsKey(bundleEntry.key)) {
            // Skip if bundle is inflating or inflated.
            debugBundleLog(TAG) { "already in map: ${bundleEntry.key}" }
            return
        }
        iconManager.createIcons(context, bundleEntry)
        val parent: ViewGroup = listContainer.getViewParentForNotification()
        val inflationFinishedListener: (ExpandableNotificationRow) -> Unit = { row ->
            // A subset of NotificationRowBinderImpl.inflateViews
            debugBundleLog(TAG) { "finished inflating: ${bundleEntry.key}" }
            bundleEntry.row = row
            val component =
                rowComponent
                    .expandableNotificationRow(row)
                    .pipelineEntry(bundleEntry)
                    .onExpandClickListener(presenterLazy?.get())
                    .build()
            val controller = component.expandableNotificationRowController
            controller.init(bundleEntry)
            keyToControllerMap[bundleEntry.key] = controller
            row.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    if (!row.isTransient) {
                        row.updateBackgroundColorsOfSelf()
                        row.reset()
                    }
                }
            }
            initBundleHeaderView(bundleEntry, row)
        }
        debugBundleLog(TAG) { "calling inflate: ${bundleEntry.key}" }
        keyToControllerMap[bundleEntry.key] = null
        rowInflaterTaskProvider
            .get()
            .inflate(context, parent, bundleEntry, inflationFinishedListener)
    }

    private fun initBundleHeaderView(bundleEntry: BundleEntry, row: ExpandableNotificationRow) {
        val bundleRowComponent =
            bundleRowComponentFactory.create(repository = bundleEntry.bundleRepository)
        val headerComposeView = ComposeView(context)
        row.setBundleHeaderView(headerComposeView)
        val viewModelFactory = bundleRowComponent.bundleViewModelFactory()
        headerComposeView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                headerComposeView.initOnBackPressedDispatcherOwner(lifecycle)
                headerComposeView.setContent {
                    HeaderComposeViewContent(
                        row = row,
                        bundleHeaderViewModelFactory = viewModelFactory::create,
                    )
                }
            }
        }
    }

    /** Return true if finished inflating. */
    fun isInflated(bundleEntry: BundleEntry): Boolean {
        return keyToControllerMap[bundleEntry.key] != null
    }

    /** Return ExpandableNotificationRowController for BundleEntry. */
    fun requireNodeController(bundleEntry: BundleEntry): NodeController {
        debugBundleLog(
            TAG,
            {
                "requireNodeController: ${bundleEntry.key}" +
                    "controller: ${keyToControllerMap[bundleEntry.key]}"
            },
        )
        return keyToControllerMap[bundleEntry.key]
            ?: error("No view has been registered for bundle: ${bundleEntry.key}")
    }

    override fun dumpPipeline(d: PipelineDumper) {
        d.dump("trackedBundleCount", keyToControllerMap.size)
        if (keyToControllerMap.isEmpty()) {
            d.println("No bundles tracked.")
        } else {
            d.println("Bundle Inflation States:")
            keyToControllerMap.forEach { (key, controller) ->
                val stateString =
                    if (controller == null) {
                        "INFLATING"
                    } else {
                        "INFLATED (Controller: ${controller::class.simpleName})"
                    }
                d.dump("Bundle key:$key", stateString)
            }
        }
    }
}

@Composable
private fun HeaderComposeViewContent(
    row: ExpandableNotificationRow,
    bundleHeaderViewModelFactory: () -> BundleHeaderViewModel,
) {
    PlatformTheme {
        val viewModel =
            rememberViewModel(
                traceName = "BundleHeaderViewModel",
                factory = bundleHeaderViewModelFactory,
            )
        BundleHeader(viewModel)
        DisposableEffect(viewModel) {
            row.setBundleHeaderViewModel(viewModel)
            row.setOnClickListener {
                viewModel.onHeaderClicked()
                row.expandNotification()
            }
            onDispose {
                row.setOnClickListener(null)
                row.setBundleHeaderViewModel(null)
            }
        }
    }
}

private inline val ExpandableView.isTransient
    get() = transientContainer != null

private const val TAG = "BundleBarn"
