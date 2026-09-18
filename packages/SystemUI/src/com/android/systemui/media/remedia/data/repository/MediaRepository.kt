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

package com.android.systemui.media.remedia.data.repository

import android.annotation.UserIdInt
import android.app.WallpaperColors
import android.content.Context
import android.content.pm.PackageManager
import android.content.theming.ThemeStyle
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import com.android.internal.annotations.GuardedBy
import com.android.internal.logging.InstanceId
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.NotificationMediaManager
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.util.MediaControllerFactory
import com.android.systemui.media.remedia.data.model.MediaControllerDataModel
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.data.model.UpdateArtInfoModel
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.monet.ColorScheme
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.provider.OnReorderingAllowedListener
import com.android.systemui.statusbar.notification.collection.provider.VisualStabilityProvider
import com.android.systemui.util.Utils
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.time.SystemClock
import java.util.TreeMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A repository that holds the state of current media on the device. */
interface MediaRepository {
    /** Current sorted media sessions. */
    val currentMedia: List<MediaDataModel>

    val keysNeedRemoval: List<InstanceId>

    /** Index of the current visible media session */
    val currentCarouselIndex: Int

    /** Whether media carousel should show first media session. */
    val shouldScrollToFirst: Boolean

    val isSwipedAway: Boolean

    val isUserInitiatedRemovalQueued: Boolean

    /** Whether guts state should show on carousel. */
    val isGutsVisible: Boolean

    val allowMediaOnLockscreen: Boolean

    val visualStabilityListenerFlow: Flow<Unit>

    val isReorderingAllowed: Boolean

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: InstanceId, to: Long)

    /** Reorders media list when media is not visible to user */
    fun reorderMedia()

    fun storeCarouselIndex(index: Int)

    /** Resets [shouldScrollToFirst] flag. */
    fun resetScrollToFirst()

    fun storeIsGutsVisible(isGutsVisible: Boolean)

    fun setSwipedAwayState()

    fun cleanKeysNeedRemoval()
}

@SysUISingleton
class MediaRepositoryImpl
@Inject
constructor(
    @Application private val applicationContext: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    private val visualStabilityProvider: VisualStabilityProvider,
    private val systemClock: SystemClock,
    private val secureSettings: SecureSettings,
    private val mediaControllerFactory: MediaControllerFactory,
) :
    MediaRepository,
    MediaPipelineRepository(
        applicationContext,
        applicationScope,
        backgroundDispatcher,
        secureSettings,
    ) {

    override val currentMedia: SnapshotStateList<MediaDataModel> = mutableStateListOf()

    override var currentCarouselIndex by mutableIntStateOf(0)

    override var shouldScrollToFirst by mutableStateOf(false)

    override var isGutsVisible by mutableStateOf(false)

    override val keysNeedRemoval: SnapshotStateList<InstanceId> = mutableStateListOf()

    override var isSwipedAway by mutableStateOf(false)
    override var isUserInitiatedRemovalQueued by mutableStateOf(false)

    @GuardedBy("mediaMutex")
    private var sortedMedia = TreeMap<MediaSortKeyModel, MediaDataModel>(comparator)

    // To store active controllers and their callbacks
    private val activeControllerModels = mutableMapOf<InstanceId, MediaControllerDataModel>()
    // To store active polling jobs
    private val positionPollers = mutableMapOf<InstanceId, Job>()
    private val mediaMutex = Mutex()

    private var _allowMediaOnLockscreen by mutableStateOf(super.allowMediaPlayerOnLockscreen.value)

    override val allowMediaOnLockscreen: Boolean
        get() = _allowMediaOnLockscreen

    init {
        applicationScope.launch {
            super.allowMediaPlayerOnLockscreen.collect { _allowMediaOnLockscreen = it }
        }
    }

    override val visualStabilityListenerFlow: Flow<Unit> = callbackFlow {
        val listener = OnReorderingAllowedListener { trySend(Unit) }
        visualStabilityProvider.addPersistentReorderingAllowedListener(listener)
        awaitClose { visualStabilityProvider.removeReorderingAllowedListener(listener) }
    }

    override val isReorderingAllowed: Boolean
        get() = visualStabilityProvider.isReorderingAllowed

    override fun addCurrentUserMediaEntry(data: MediaData): UpdateArtInfoModel? {
        return super.addCurrentUserMediaEntry(data).also { updateModel ->
            applicationScope.launch {
                mediaMutex.withLock {
                    addToSortedMediaLocked(data, updateModel)
                    if (data.canBeRemoved() && !useMediaResumption(applicationContext)) {
                        if (!visualStabilityProvider.isReorderingAllowed) {
                            isUserInitiatedRemovalQueued = isSwipedAway
                            keysNeedRemoval.add(data.instanceId)
                        }
                    } else {
                        keysNeedRemoval.remove(data.instanceId)
                    }
                    isSwipedAway = false
                }
            }
        }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId): MediaData? {
        return super.removeCurrentUserMediaEntry(key)?.also {
            applicationScope.launch { mediaMutex.withLock { removeFromSortedMediaLocked(it) } }
        }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        return super.removeCurrentUserMediaEntry(key, data).also {
            if (it) {
                applicationScope.launch {
                    mediaMutex.withLock { removeFromSortedMediaLocked(data) }
                }
            }
        }
    }

    override fun clearCurrentUserMedia() {
        val userEntries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        mutableUserEntries.value = LinkedHashMap()
        applicationScope.launch {
            mediaMutex.withLock { userEntries.forEach { removeFromSortedMediaLocked(it.value) } }
        }
    }

    override fun seek(sessionKey: InstanceId, to: Long) {
        activeControllerModels[sessionKey]?.controller?.let { controller ->
            controller.transportControls.seekTo(to)
            applicationScope.launch {
                mediaMutex.withLock {
                    currentMedia
                        .find { it.instanceId == sessionKey }
                        ?.let { latestModel ->
                            updateMediaModelInStateLocked(latestModel) { it.copy(positionMs = to) }
                        }
                }
            }
        }
    }

    override fun reorderMedia() {
        applicationScope.launch {
            mediaMutex.withLock {
                currentMedia.clear()
                currentMedia.addAll(sortedMedia.values.toList())
            }
        }
        currentCarouselIndex = 0
        isGutsVisible = false
        isUserInitiatedRemovalQueued = false
    }

    override fun storeCarouselIndex(index: Int) {
        currentCarouselIndex = index
    }

    override fun resetScrollToFirst() {
        shouldScrollToFirst = false
    }

    override fun storeIsGutsVisible(isGutsVisible: Boolean) {
        this.isGutsVisible = isGutsVisible
    }

    override fun setSwipedAwayState() {
        isSwipedAway = true
    }

    override fun cleanKeysNeedRemoval() {
        keysNeedRemoval.clear()
    }

    private fun MediaData.canBeRemoved(): Boolean {
        return isPlaying?.let { !it } ?: isClearable && !active
    }

    @GuardedBy("mediaMutex")
    private suspend fun addToSortedMediaLocked(data: MediaData, updateModel: UpdateArtInfoModel?) {
        val sortedMap = TreeMap<MediaSortKeyModel, MediaDataModel>(comparator)
        val currentModel = sortedMedia.values.find { it.instanceId == data.instanceId }

        sortedMap.putAll(sortedMedia.filter { (_, model) -> model.instanceId != data.instanceId })

        mutableUserEntries.value[data.instanceId]?.let { mediaData ->
            with(mediaData) {
                val sortKey =
                    MediaSortKeyModel(
                        isPlaying,
                        playbackLocation,
                        active,
                        resumption,
                        lastActive,
                        notificationKey,
                        systemClock.currentTimeMillis(),
                        instanceId,
                    )
                val useCurrentController = currentModel != null && currentModel.token == token
                val controller =
                    if (useCurrentController) {
                        activeControllerModels[currentModel?.instanceId]?.controller
                    } else {
                        withContext(backgroundDispatcher) {
                            token?.let { mediaControllerFactory.create(applicationContext, it) }
                        }
                    }
                val (icon, background) = getIconAndBackground(mediaData, currentModel, updateModel)
                val mediaModel = toDataModel(controller, icon, background)
                sortedMap[sortKey] = mediaModel

                // Only setup controller when needed a new one.
                if (!useCurrentController) {
                    controller?.let { setupControllerLocked(mediaModel.instanceId, it) }
                }

                var isNewToCurrentMedia = true
                val currentList = mutableListOf<MediaDataModel>().apply { addAll(currentMedia) }
                currentList.forEachIndexed { index, mediaDataModel ->
                    if (mediaDataModel.instanceId == data.instanceId) {
                        // When loading an update for an existing media control.
                        isNewToCurrentMedia = false
                        if (mediaDataModel != mediaModel) {
                            // Update media model if changed.
                            currentList[index] = mediaModel
                        }
                    }
                }
                currentMedia.clear()
                if (isNewToCurrentMedia && active) {
                    // New media added is at the top of the current media given its priority.
                    // Media carousel should show the first card in the current media list.
                    shouldScrollToFirst = true
                    currentMedia.addAll(sortedMap.values.toList())
                } else {
                    currentMedia.addAll(currentList)
                }

                sortedMedia = sortedMap
            }
        }
    }

    @GuardedBy("mediaMutex")
    private fun removeFromSortedMediaLocked(data: MediaData) {
        currentMedia.removeIf { model -> data.instanceId == model.instanceId }
        sortedMedia =
            TreeMap<MediaSortKeyModel, MediaDataModel>(comparator).apply {
                putAll(sortedMedia.filter { (_, model) -> model.instanceId != data.instanceId })
            }
        clearControllerStateLocked(data.instanceId)
    }

    private suspend fun MediaData.toDataModel(
        controller: MediaController?,
        icon: Icon,
        background: Icon?,
    ): MediaDataModel {
        return withContext(backgroundDispatcher) {
            val metadata = controller?.metadata
            val currentPlaybackState = controller?.playbackState

            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = currentPlaybackState?.position ?: 0L
            val state = currentPlaybackState?.state ?: PlaybackState.STATE_NONE
            MediaDataModel(
                instanceId = instanceId,
                appUid = appUid,
                packageName = packageName,
                appName = app.toString(),
                appIcon = icon,
                background = background,
                title = song.toString(),
                subtitle = artist.toString(),
                colorScheme = getScheme(artwork, packageName, userId),
                notificationActions = actions,
                notificationActionsCompressed = actionsToShowInCompact,
                playbackStateActions = semanticActions,
                outputDevice = device,
                clickIntent = clickIntent,
                state =
                    when {
                        NotificationMediaManager.isPlayingState(state) -> MediaSessionState.Playing
                        NotificationMediaManager.isConnectingState(state) ->
                            MediaSessionState.Buffering
                        else -> MediaSessionState.Paused
                    },
                durationMs = duration,
                positionMs = position,
                canShowSeekbar = canShowSeekbar(currentPlaybackState, metadata),
                canBeScrubbed = isSeekAvailable(currentPlaybackState),
                canBeDismissed = isClearable,
                isActive = active,
                isResume = resumption,
                resumeAction = resumeAction,
                isExplicit = isExplicit,
                suggestionData = suggestionData,
                token = token,
                needsImmediateRemoval =
                    canBeRemoved() &&
                        !useMediaResumption(applicationContext) &&
                        visualStabilityProvider.isReorderingAllowed,
            )
        }
    }

    private suspend fun getIconAndBackground(
        currentData: MediaData,
        currentModel: MediaDataModel?,
        updateModel: UpdateArtInfoModel?,
    ): Pair<Icon, Icon?> {
        return with(currentData) {
            val icon =
                if (currentModel != null && updateModel?.isAppIconUpdated == false) {
                    currentModel.appIcon
                } else {
                    appIcon?.loadDrawable(applicationContext)?.let { drawable ->
                        Icon.Loaded(drawable, contentDescription = ContentDescription.Loaded(app))
                    } ?: getAltIcon(packageName, currentData.userId)
                }
            val background =
                if (currentModel != null && updateModel?.isBackgroundUpdated == false) {
                    currentModel.background
                } else {
                    artwork?.loadDrawable(applicationContext)?.let { drawable ->
                        Icon.Loaded(drawable, contentDescription = null)
                    }
                }
            Pair(icon, background)
        }
    }

    private suspend fun getScheme(
        artwork: android.graphics.drawable.Icon?,
        packageName: String,
        @UserIdInt userId: Int,
    ): MediaColorScheme? {
        val wallpaperColors = getWallpaperColor(applicationContext, backgroundDispatcher, artwork)
        val colorScheme =
            wallpaperColors?.let { ColorScheme(it, false, ThemeStyle.CONTENT) }
                ?: let {
                    val launcherIcon = getAltIcon(packageName, userId)
                    if (launcherIcon is Icon.Loaded) {
                        getColorScheme(launcherIcon.drawable)
                    } else {
                        null
                    }
                }
        return colorScheme?.run {
            MediaColorScheme(
                Color(colorScheme.materialScheme.getPrimaryFixed()),
                Color(colorScheme.materialScheme.getOnPrimaryFixed()),
                Color(colorScheme.materialScheme.getOnSurface()),
            )
        }
    }

    private suspend fun getAltIcon(packageName: String, @UserIdInt userId: Int): Icon {
        return withContext(backgroundDispatcher) {
            try {
                val appInfo =
                    applicationContext.packageManager.getApplicationInfoAsUser(
                        packageName,
                        0,
                        userId,
                    )
                val icon = applicationContext.packageManager.getApplicationIcon(appInfo)
                Icon.Loaded(icon, null)
            } catch (exception: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Cannot find icon for package $packageName", exception)
                Icon.Resource(R.drawable.ic_music_note, null)
            }
        }
    }

    /**
     * This method should be called from a background thread. WallpaperColors.fromBitmap is a
     * blocking call.
     */
    private suspend fun getWallpaperColor(
        applicationContext: Context,
        backgroundDispatcher: CoroutineDispatcher,
        artworkIcon: android.graphics.drawable.Icon?,
    ): WallpaperColors? {
        return withContext(backgroundDispatcher) {
            artworkIcon?.let {
                if (
                    it.type == android.graphics.drawable.Icon.TYPE_BITMAP ||
                        it.type == android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP
                ) {
                    // Avoids extra processing if this is already a valid bitmap
                    it.bitmap.let { artworkBitmap ->
                        if (artworkBitmap.isRecycled) {
                            Log.d(TAG, "Cannot load wallpaper color from a recycled bitmap")
                            null
                        } else {
                            WallpaperColors.fromBitmap(artworkBitmap)
                        }
                    }
                } else {
                    it.loadDrawable(applicationContext)?.let { artworkDrawable ->
                        WallpaperColors.fromDrawable(artworkDrawable)
                    }
                }
            }
        }
    }

    /** Returns [ColorScheme] of media app given its [icon]. */
    private fun getColorScheme(icon: Drawable): ColorScheme? {
        return try {
            ColorScheme(WallpaperColors.fromDrawable(icon), false, ThemeStyle.CONTENT)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Fail to get media app info", e)
            null
        }
    }

    @GuardedBy("mediaMutex")
    private fun setupControllerLocked(instanceId: InstanceId, controller: MediaController) {
        // Clear controller state if changed for the same media session.
        clearControllerStateLocked(instanceId)
        val callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state == null || PlaybackState.STATE_NONE.equals(state)) {
                        applicationScope.launch {
                            mediaMutex.withLock { clearControllerStateLocked(instanceId) }
                        }
                    } else {
                        updatePollingState(instanceId, state)
                    }
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    applicationScope.launch {
                        mediaMutex.withLock {
                            val duration =
                                metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                            currentMedia
                                .find { it.instanceId == instanceId }
                                ?.let { latestModel ->
                                    updateMediaModelInStateLocked(latestModel) { model ->
                                        val playbackState = controller.playbackState
                                        model.copy(
                                            canBeScrubbed = isSeekAvailable(playbackState),
                                            canShowSeekbar =
                                                canShowSeekbar(playbackState, metadata),
                                            durationMs = duration,
                                        )
                                    }
                                }
                        }
                    }
                }

                override fun onSessionDestroyed() {
                    applicationScope.launch {
                        mediaMutex.withLock { clearControllerStateLocked(instanceId) }
                    }
                }
            }
        activeControllerModels[instanceId] = MediaControllerDataModel(controller, callback)
        controller.registerCallback(callback)

        // Initial polling setup.
        controller.playbackState?.let { updatePollingState(instanceId, it, requireUpdate = false) }
    }

    private fun updatePollingState(
        instanceId: InstanceId,
        playbackState: PlaybackState,
        requireUpdate: Boolean = true,
    ) {
        val controller = activeControllerModels[instanceId]?.controller ?: return
        val isInMotion = NotificationMediaManager.isPlayingState(playbackState.state)

        if (isInMotion) {
            if (positionPollers[instanceId]?.isActive != true) {
                // Cancel previous if any.
                positionPollers[instanceId]?.cancel()
                positionPollers[instanceId] =
                    applicationScope.launch(backgroundDispatcher) {
                        while (isActive) {
                            val currentController = activeControllerModels[instanceId]?.controller
                            val latestPlaybackState = currentController?.playbackState
                            checkPlaybackPosition(instanceId, latestPlaybackState)
                            delay(POSITION_UPDATE_INTERVAL_MILLIS)
                        }
                        positionPollers.remove(instanceId)
                    }
            }
        } else if (requireUpdate) {
            positionPollers[instanceId]?.cancel()
            positionPollers.remove(instanceId)
            checkPlaybackPosition(instanceId, controller.playbackState)
        }
    }

    private fun PlaybackState.computeActualPosition(mediaDurationMs: Long): Long {
        var currentPosition = position
        if (NotificationMediaManager.isPlayingState(state)) {
            val currentTime = systemClock.elapsedRealtime()
            if (lastPositionUpdateTime > 0) {
                var estimatedPosition =
                    (playbackSpeed * (currentTime - lastPositionUpdateTime)).toLong() + position
                if (mediaDurationMs in 0..<estimatedPosition) {
                    estimatedPosition = mediaDurationMs
                } else if (estimatedPosition < 0) {
                    estimatedPosition = 0
                }
                currentPosition = estimatedPosition
            }
        }
        return currentPosition
    }

    private fun checkPlaybackPosition(instanceId: InstanceId, playbackState: PlaybackState?) {
        applicationScope.launch {
            mediaMutex.withLock {
                currentMedia
                    .find { it.instanceId == instanceId }
                    ?.let { latestModel ->
                        val newPosition =
                            playbackState?.computeActualPosition(latestModel.durationMs)
                        updateMediaModelInStateLocked(latestModel) {
                            if (newPosition != null && newPosition <= latestModel.durationMs) {
                                it.copy(positionMs = newPosition)
                            } else {
                                it
                            }
                        }
                    }
            }
        }
    }

    @GuardedBy("mediaMutex")
    private fun clearControllerStateLocked(instanceId: InstanceId) {
        positionPollers[instanceId]?.cancel()
        positionPollers.remove(instanceId)
        activeControllerModels[instanceId]?.let { it.controller.unregisterCallback(it.callback) }
        activeControllerModels.remove(instanceId)

        currentMedia
            .find { it.instanceId == instanceId }
            ?.let { latestModel ->
                updateMediaModelInStateLocked(latestModel) { model ->
                    model.copy(canBeScrubbed = false, canShowSeekbar = false)
                }
            }
    }

    @GuardedBy("mediaMutex")
    private fun updateMediaModelInStateLocked(
        oldModel: MediaDataModel,
        updateBlock: (MediaDataModel) -> MediaDataModel,
    ) {
        val newModel = updateBlock(oldModel)
        val index = currentMedia.indexOf(oldModel)
        if (index == -1) {
            Log.w(TAG, "Could not find model to update ${oldModel.appName}")
        } else if (oldModel != newModel) {
            sortedMedia.keys
                .find { it.instanceId == newModel.instanceId }
                ?.let {
                    val sortedMap = TreeMap<MediaSortKeyModel, MediaDataModel>(comparator)
                    sortedMap.putAll(
                        sortedMedia.filter { (_, model) -> model.instanceId != newModel.instanceId }
                    )
                    sortedMap[it] = newModel
                    sortedMedia = sortedMap
                }
            currentMedia[index] = newModel
        }
    }

    private fun canShowSeekbar(playbackState: PlaybackState?, metadata: MediaMetadata?): Boolean {
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        return duration > 0L && state != PlaybackState.STATE_NONE
    }

    private fun isSeekAvailable(playbackState: PlaybackState?): Boolean {
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        val actions = playbackState?.actions ?: 0L
        return state != PlaybackState.STATE_NONE && (actions and PlaybackState.ACTION_SEEK_TO != 0L)
    }

    private fun useMediaResumption(context: Context): Boolean {
        return Utils.useQsMediaPlayer(context) &&
            secureSettings.getInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 1) > 0
    }

    companion object {
        private const val TAG = "MediaRepository"
        private const val POSITION_UPDATE_INTERVAL_MILLIS = 500L
    }
}
