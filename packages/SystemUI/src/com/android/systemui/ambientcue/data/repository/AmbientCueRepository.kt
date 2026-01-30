/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.app.assist.ActivityId
import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.annotation.VisibleForTesting
import androidx.tracing.trace
import com.android.systemui.Dumpable
import com.android.systemui.LauncherProxyService
import com.android.systemui.LauncherProxyService.LauncherProxyListener
import com.android.systemui.ambientcue.shared.flag.AmbientCueFlag
import com.android.systemui.ambientcue.shared.logger.AmbientCueLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.PluginLifecycleManager
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.plugins.cuebar.CuebarPlugin
import com.android.systemui.plugins.cuebar.CuebarPlugin.OnNewActionsListener
import com.android.systemui.plugins.cuebar.IconModel
import com.android.systemui.res.R
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: StateFlow<List<ActionModel>>

    /** If the root view is attached to the WindowManager. */
    val isRootViewAttached: StateFlow<Boolean>

    /** If IME is visible or not. */
    val isImeVisible: MutableStateFlow<Boolean>

    /** Task Id which is globally focused on display. */
    val globallyFocusedTaskId: StateFlow<Int>

    /** If the UI is deactivated, such as closed by user or not used for a long period. */
    val isDeactivated: MutableStateFlow<Boolean>

    /** If the taskbar is fully visible and not stashed. */
    val isTaskBarVisible: StateFlow<Boolean>

    /** True if in gesture nav mode, false when in 3-button navbar. */
    val isGestureNav: StateFlow<Boolean>

    val recentsButtonPosition: StateFlow<Rect?>

    /* If AmbientCue is enabled. */
    val isAmbientCueEnabled: StateFlow<Boolean>

    /* The timeout for Ambient Cue to disappear. */
    val ambientCueTimeoutMs: StateFlow<Int>
}

@SysUISingleton
class AmbientCueRepositoryImpl
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val smartSpaceManager: SmartspaceManager?,
    private val autofillManager: AutofillManager?,
    private val activityStarter: ActivityStarter,
    private val navigationModeController: NavigationModeController,
    dumpManager: DumpManager,
    @Background executor: Executor,
    @Application applicationContext: Context,
    launcherProxyService: LauncherProxyService,
    private val taskStackChangeListeners: TaskStackChangeListeners,
    @Background backgroundDispatcher: CoroutineDispatcher,
    secureSettingsRepository: SecureSettingsRepository,
    private val ambientCueLogger: AmbientCueLogger,
    private val pluginManager: PluginManager,
) : AmbientCueRepository, Dumpable {

    init {
        val callback =
            object : LauncherProxyListener {
                override fun onTaskbarStatusUpdated(visible: Boolean, stashed: Boolean) {
                    _isTaskBarVisible.update { visible && !stashed }
                }

                override fun onRecentsButtonPositionChanged(position: Rect?) {
                    _recentsButtonPosition.update { position }
                }
            }
        launcherProxyService.addCallback(callback)
        dumpManager.registerNormalDumpable(this)
    }

    override val actions: StateFlow<List<ActionModel>> =
        conflatedCallbackFlow {
                if (smartSpaceManager == null) {
                    Log.i(TAG, "Cannot connect to SmartSpaceManager, it's null.")
                    return@conflatedCallbackFlow
                }

                val session =
                    smartSpaceManager.createSmartspaceSession(
                        SmartspaceConfig.Builder(applicationContext, AMBIENT_CUE_SURFACE).build()
                    )
                Log.i(TAG, "SmartSpace session created")

                val cuebarPluginListener = OnNewActionsListener { actions -> trySend(actions) }
                var cuebarPlugin: CuebarPlugin? = null

                if (AmbientCueFlag.isAmbientCuePluginEnabled) {
                    pluginManager.addPluginListener(
                        object : PluginListener<CuebarPlugin> {
                            override fun onPluginLoaded(
                                plugin: CuebarPlugin,
                                pluginContext: Context,
                                manager: PluginLifecycleManager<CuebarPlugin>,
                            ) {
                                cuebarPlugin = plugin
                                plugin.addOnNewActionsListener(cuebarPluginListener)
                                Log.i(TAG, "CuebarPlugin loaded")
                            }

                            override fun onPluginUnloaded(
                                plugin: CuebarPlugin,
                                manager: PluginLifecycleManager<CuebarPlugin>,
                            ) {
                                cuebarPlugin = null
                                Log.i(TAG, "CuebarPlugin unloaded")
                            }
                        },
                        CuebarPlugin::class.java,
                        false, /* allowMultiple */
                    )
                }

                val smartSpaceListener = OnTargetsAvailableListener { targets ->
                    Log.i(TAG, "Receiving SmartSpace targets # ${targets.size}")
                    if (targets.none { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }) {
                        return@OnTargetsAvailableListener
                    }
                    val actions =
                        targets
                            .filter { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }
                            .flatMap { target -> target.actionChips }
                            .map { chip ->
                                val title = chip.title.toString()
                                val activityId =
                                    chip.extras?.getParcelable<ActivityId>(EXTRA_ACTIVITY_ID)
                                val actionType = chip.extras?.getString(EXTRA_ACTION_TYPE)
                                val oneTapEnabled = chip.extras?.getBoolean(EXTRA_ONE_TAP_ENABLED)
                                val oneTapDelayMs =
                                    chip.extras?.getLong(
                                        EXTRA_ONE_TAP_DELAY_MS,
                                        DEFAULT_ONE_TAP_DELAY_MS,
                                    )
                                ActionModel(
                                    icon =
                                        IconModel(
                                            small =
                                                (chip.icon?.loadDrawable(applicationContext)
                                                        ?: applicationContext.getDrawable(
                                                            R.drawable.ic_paste_spark
                                                        )!!)
                                                    .mutate(),
                                            large =
                                                (chip.icon?.loadDrawable(applicationContext)
                                                        ?: applicationContext.getDrawable(
                                                            R.drawable.ic_paste_spark
                                                        )!!)
                                                    .mutate(),
                                            iconId =
                                                chip?.icon?.resPackage + "#" + chip?.icon?.resId,
                                        ),
                                    label = title,
                                    attribution = chip.subtitle?.toString(),
                                    onPerformAction = {
                                        trace("performAmbientCueAction") {
                                            val intent = chip.intent
                                            val pendingIntent = chip.pendingIntent
                                            val activityId =
                                                chip.extras?.getParcelable<ActivityId>(
                                                    EXTRA_ACTIVITY_ID
                                                )
                                            val autofillId =
                                                chip.extras?.getParcelable<AutofillId>(
                                                    EXTRA_AUTOFILL_ID
                                                )
                                            val token = activityId?.token
                                            Log.i(
                                                TAG,
                                                "Performing action: $activityId, $autofillId, " +
                                                    "$pendingIntent, $intent",
                                            )
                                            if (token != null && autofillId != null) {
                                                autofillManager?.autofillRemoteApp(
                                                    autofillId,
                                                    title,
                                                    token,
                                                    activityId.taskId,
                                                )
                                            } else if (pendingIntent != null) {
                                                launchPendingIntent(pendingIntent)
                                            } else if (intent != null) {
                                                activityStarter.startActivity(intent, false)
                                            } else {}
                                        }
                                        if (actionType == MA_ACTION_TYPE_NAME) {
                                            ambientCueLogger.setFulfilledWithMaStatus()
                                        }
                                        if (actionType == MR_ACTION_TYPE_NAME) {
                                            ambientCueLogger.setFulfilledWithMrStatus()
                                        }
                                    },
                                    onPerformLongClick = {
                                        Log.i(TAG, "AmbientCueRepositoryImpl: onPerformLongClick")
                                        trace("performAmbientCueLongClick") {
                                            val pendingIntent =
                                                chip.extras?.getParcelable<PendingIntent>(
                                                    EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT
                                                )
                                            if (pendingIntent != null) {
                                                Log.i(TAG, "Performing long click: $pendingIntent")
                                                launchPendingIntent(pendingIntent)
                                            }
                                        }
                                    },
                                    taskId = activityId?.taskId ?: INVALID_TASK_ID,
                                    actionType = actionType,
                                    oneTapEnabled = oneTapEnabled == true,
                                    oneTapDelayMs = oneTapDelayMs ?: DEFAULT_ONE_TAP_DELAY_MS,
                                )
                            }
                            .let { actions -> cuebarPlugin?.filterActions(actions) ?: actions }
                    if (DEBUG) {
                        Log.d(TAG, "SmartSpace OnTargetsAvailableListener $targets")
                    }
                    Log.i(TAG, "SmartSpace actions $actions")
                    trySend(actions)
                }

                session.addOnTargetsAvailableListener(executor, smartSpaceListener)
                Log.i(TAG, "SmartSpace session addOnTargetsAvailableListener")
                awaitClose {
                    session.removeOnTargetsAvailableListener(smartSpaceListener)
                    session.close()
                    Log.i(TAG, "SmartSpace session closed")
                }
            }
            .onEach { actions ->
                if (actions.isNotEmpty()) {
                    isDeactivated.update { false }
                    targetTaskId.update { actions[0].taskId }
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptyList(),
            )

    private fun launchPendingIntent(pendingIntent: PendingIntent) {
        val options = BroadcastOptions.makeBasic()
        options.isInteractive = true
        options.pendingIntentBackgroundActivityStartMode =
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        try {
            pendingIntent.send(options.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "pending intent of $pendingIntent was canceled")
        }
    }

    override val isGestureNav: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    NavigationModeController.ModeChangedListener { mode ->
                        trySend(QuickStepContract.isGesturalMode(mode))
                    }
                val navBarMode = navigationModeController.addListener(listener)
                listener.onNavigationModeChanged(navBarMode)
                awaitClose { navigationModeController.removeListener(listener) }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), false)

    private val _isTaskBarVisible = MutableStateFlow(false)
    override val isTaskBarVisible: StateFlow<Boolean> = _isTaskBarVisible.asStateFlow()

    private val _recentsButtonPosition = MutableStateFlow<Rect?>(null)
    override val recentsButtonPosition: StateFlow<Rect?> = _recentsButtonPosition.asStateFlow()

    override val isImeVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val isDeactivated: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * The [RunningTaskInfo] for the task that is currently in the foreground. Updated whenever a
     * new task moves to the front. Used to derive the package name for logging.
     */
    private var frontRunningTask: RunningTaskInfo? = null

    @OptIn(FlowPreview::class)
    override val globallyFocusedTaskId: StateFlow<Int> =
        conflatedCallbackFlow {
                val taskListener =
                    object : TaskStackChangeListener {
                        override fun onTaskMovedToFront(runningTaskInfo: RunningTaskInfo) {
                            frontRunningTask = runningTaskInfo
                            trySend(runningTaskInfo.taskId)
                        }
                    }

                taskStackChangeListeners.registerTaskStackListener(taskListener)
                awaitClose { taskStackChangeListeners.unregisterTaskStackListener(taskListener) }
            }
            .distinctUntilChanged()
            // Filter out focused task quick change. For example, when user clicks ambient cue, the
            // click event will also be sent to NavBar, so it will cause a quick change of focused
            // task (Target App -> Launcher -> Target App).
            .debounce(DEBOUNCE_DELAY_MS)
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = INVALID_TASK_ID,
            )

    val targetTaskId: MutableStateFlow<Int> = MutableStateFlow(INVALID_TASK_ID)
    var isSessionStarted = false

    override val isAmbientCueEnabled: StateFlow<Boolean> =
        secureSettingsRepository
            .intSetting(name = AMBIENT_CUE_SETTING, 0)
            .map { it == OPTED_IN }
            .flowOn(backgroundDispatcher)
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override val isRootViewAttached: StateFlow<Boolean> =
        combine(isDeactivated, globallyFocusedTaskId, actions, isAmbientCueEnabled) {
                isDeactivated,
                globallyFocusedTaskId,
                actions,
                isAmbientCueEnabled ->
                actions.isNotEmpty() &&
                    isAmbientCueEnabled &&
                    !isDeactivated &&
                    globallyFocusedTaskId == targetTaskId.value
            }
            .onEach { isAttached ->
                if (isAttached && !isSessionStarted) {
                    isSessionStarted = true
                    var maCount = 0
                    var mrCount = 0
                    val packageName = frontRunningTask?.baseIntent?.component?.packageName ?: ""
                    actions.value.forEach { action ->
                        when (action.actionType) {
                            MA_ACTION_TYPE_NAME -> maCount++
                            MR_ACTION_TYPE_NAME -> mrCount++
                            else -> {}
                        }
                    }
                    ambientCueLogger.setPackageName(packageName)
                    ambientCueLogger.setAmbientCueDisplayStatus(maCount, mrCount)
                }
                if (!isAttached && isSessionStarted) {
                    if (globallyFocusedTaskId.value != targetTaskId.value) {
                        ambientCueLogger.setLoseFocusMillis()
                    }
                    ambientCueLogger.flushAmbientCueEventReported()
                    ambientCueLogger.clear()
                    isSessionStarted = false
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override val ambientCueTimeoutMs: StateFlow<Int> =
        secureSettingsRepository
            .intSetting(
                name = Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS,
                AMBIENT_CUE_DEFAULT_TIMEOUT_MS,
            )
            .map { if (it == 0) AMBIENT_CUE_DEFAULT_TIMEOUT_MS else it }
            .flowOn(backgroundDispatcher)
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = AMBIENT_CUE_DEFAULT_TIMEOUT_MS,
            )

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("isRootViewAttached: ${isRootViewAttached.value}")
        pw.println("targetTaskId: ${targetTaskId.value}")
        pw.println("globallyFocusedTaskId: ${globallyFocusedTaskId.value}")
        pw.println("isDeactivated: ${isDeactivated.value}")
        pw.println("isImeVisible: ${isImeVisible.value}")
        pw.println("recentsButtonPosition: ${recentsButtonPosition.value}")
        pw.println("isTaskBarVisible: ${isTaskBarVisible.value}")
        pw.println("isGestureNav: ${isGestureNav.value}")
        pw.println("actions: ${actions.value}")
        pw.println("isAmbientCueEnabled: ${isAmbientCueEnabled.value}")
        pw.println("ambientCueTimeoutMs: ${ambientCueTimeoutMs.value}")
    }

    companion object {
        // Surface that PCC wants to push cards into
        @VisibleForTesting const val AMBIENT_CUE_SURFACE = "ambientcue"
        // In-coming intent extras from the intelligent service.
        @VisibleForTesting const val EXTRA_ACTIVITY_ID = "activityId"
        @VisibleForTesting const val EXTRA_AUTOFILL_ID = "autofillId"
        @VisibleForTesting
        const val EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT = "attributionDialogPendingIntent"
        @VisibleForTesting const val EXTRA_ACTION_TYPE = "actionType"
        private const val EXTRA_ONE_TAP_ENABLED = "oneTapEnabled"
        private const val EXTRA_ONE_TAP_DELAY_MS = "oneTapDelayMs"
        private const val DEFAULT_ONE_TAP_DELAY_MS = 200L

        // Timeout to hide cuebar if it wasn't interacted with
        private const val TAG = "AmbientCueRepository"
        private const val DEBUG = false
        private const val INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID
        @VisibleForTesting const val AMBIENT_CUE_SETTING = "spoonBarOptedIn"
        @VisibleForTesting const val AMBIENT_CUE_TIMEOUT_SETTING = "ambientCueTimeoutSec"
        @VisibleForTesting const val OPTED_IN = 0x10
        @VisibleForTesting const val OPTED_OUT = 0x01
        const val DEBOUNCE_DELAY_MS = 100L
        private const val AMBIENT_CUE_DEFAULT_TIMEOUT_MS = 30_000
        @VisibleForTesting const val MA_ACTION_TYPE_NAME = "ma"
        @VisibleForTesting const val MR_ACTION_TYPE_NAME = "mr"
    }
}
