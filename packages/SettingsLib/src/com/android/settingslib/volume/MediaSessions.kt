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
package com.android.settingslib.volume

import android.Manifest
import android.annotation.RequiresPermission
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AppId
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaRouter2
import android.media.RoutingSessionInfo
import android.media.session.MediaController
import android.media.session.MediaController.PlaybackInfo
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.HandlerExecutor
import android.os.Looper
import android.os.Message
import android.util.Log
import com.android.media.flags.Flags
import java.io.PrintWriter
import java.util.Objects

/**
 * Convenience client for all media session updates. Provides a callback interface for events
 * related to remote media sessions.
 */
class MediaSessions(context: Context, looper: Looper, callbacks: Callbacks) {

    private val mContext = context
    private val mHandler: H = H(looper)
    private val mHandlerExecutor: HandlerExecutor = HandlerExecutor(mHandler)
    private val mMgr: MediaSessionManager =
        mContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val mRecords: MutableMap<MediaSession.Token, MediaControllerRecord> = HashMap()
    private val mCallbacks: Callbacks = callbacks
    @RequiresPermission(Manifest.permission.MEDIA_CONTENT_CONTROL)
    private val mSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onActiveSessionsUpdatedH(controllers!!)
        }
    @RequiresPermission(Manifest.permission.MEDIA_CONTENT_CONTROL)
    private val mediaRouter2: MediaRouter2? =
        if (Flags.enableMirroringInMediaRouter2()) {
            MediaRouter2.getInstance(mContext, mContext.getPackageName())
        } else {
            null
        }

    private val proxyRouters = mutableMapOf<AppId, ProxyMediaRouter2Record>()
    private val systemSessionOverridesListener = this::onSystemSessionOverridesChanged

    private val mRemoteSessionCallback: MediaSessionManager.RemoteSessionCallback =
        object : MediaSessionManager.RemoteSessionCallback {
            override fun onVolumeChanged(sessionToken: MediaSession.Token, flags: Int) {
                mHandler.obtainMessage(REMOTE_VOLUME_CHANGED, flags, 0, sessionToken).sendToTarget()
            }

            override fun onDefaultRemoteSessionChanged(sessionToken: MediaSession.Token?) {
                mHandler.obtainMessage(UPDATE_REMOTE_SESSION_LIST, sessionToken).sendToTarget()
            }
        }

    private var mInit = false

    /** Dump to `writer` */
    fun dump(writer: PrintWriter) {
        writer.println(javaClass.simpleName + " state:")
        writer.print("  mInit: ")
        writer.println(mInit)
        writer.print("  mRecords.size: ")
        writer.println(mRecords.size)
        for ((i, r) in mRecords.values.withIndex()) {
            r.controller.dump(i + 1, writer)
        }
        if (Flags.enableMirroringInMediaRouter2()) {
            synchronized(proxyRouters) {
                writer.println("  mProxyRouters.size: ${proxyRouters.size}")
                for ((appId, record) in proxyRouters.entries) {
                    writer.println(
                        "    $appId -> ${record.mProxyMr2.systemController.routingSessionInfo}"
                    )
                }
            }
        }
    }

    /** init MediaSessions */
    fun init() {
        if (D.BUG) {
            Log.d(TAG, "init")
        }
        // will throw if no permission
        mMgr.addOnActiveSessionsChangedListener(mSessionsListener, null, mHandler)
        mInit = true
        postUpdateSessions()
        mMgr.registerRemoteSessionCallback(mHandlerExecutor, mRemoteSessionCallback)

        mediaRouter2?.let {
            mHandler.post { onSystemSessionOverridesChanged(it.systemSessionOverridesAppIds) }
            it.registerSystemSessionOverridesListener(
                mHandlerExecutor,
                systemSessionOverridesListener,
            )
        }
    }

    /** Destroy MediaSessions */
    fun destroy() {
        if (D.BUG) {
            Log.d(TAG, "destroy")
        }
        mediaRouter2?.unregisterSystemSessionOverridesListener(systemSessionOverridesListener)
        mInit = false
        mMgr.removeOnActiveSessionsChangedListener(mSessionsListener)
        mMgr.unregisterRemoteSessionCallback(mRemoteSessionCallback)
    }

    /** Set volume `level` to remote media `token` */
    fun setVolume(sessionId: SessionId, volumeLevel: Int) {
        when (sessionId) {
            is SessionId.Media -> setMediaSessionVolume(sessionId.token, volumeLevel)
            is SessionId.Routing ->
                setRoutingSessionVolume(sessionId.appWithSystemSessionOverride, volumeLevel)
        }
    }

    private fun setRoutingSessionVolume(appWithSystemSessionOverride: AppId, volumeLevel: Int) {
        val record = synchronized(proxyRouters) { proxyRouters[appWithSystemSessionOverride] }
        if (record == null) {
            Log.w(
                TAG,
                "setVolume: No routing session record found for $appWithSystemSessionOverride",
            )
            return
        }
        if (D.BUG) {
            Log.d(TAG, "Setting level to $volumeLevel")
        }
        record.mProxyMr2.systemController.volume = volumeLevel
    }

    private fun setMediaSessionVolume(token: MediaSession.Token, volumeLevel: Int) {
        val record = mRecords[token]
        if (record == null) {
            Log.w(TAG, "setVolume: No record found for token $token")
            return
        }
        if (D.BUG) {
            Log.d(TAG, "Setting level to $volumeLevel")
        }
        record.controller.setVolumeTo(volumeLevel, 0)
    }

    @SuppressLint("MissingPermission")
    private fun onSystemSessionOverridesChanged(appsWithSessionOverrides: Set<AppId>) {
        val newRecords = mutableListOf<ProxyMediaRouter2Record>()
        var removedApps: Set<AppId>
        synchronized(proxyRouters) {
            appsWithSessionOverrides
                .filter { it !in proxyRouters }
                .forEach { appWithOverride ->
                    val proxyRouter =
                        MediaRouter2.getInstance(
                            mContext,
                            appWithOverride.mPackageName,
                            appWithOverride.mUserHandle,
                        )
                    val record = ProxyMediaRouter2Record(proxyRouter, appWithOverride)
                    proxyRouters.put(appWithOverride, record)
                    record.register()
                    newRecords.add(record)
                }

            removedApps = proxyRouters.keys - appsWithSessionOverrides
            removedApps.forEach { appToRemove ->
                proxyRouters.remove(appToRemove)?.let {
                    it.release()
                    if (D.BUG) {
                        Log.d(TAG, "Removing proxy record for ${it.mAppWithSystemSessionOverride}")
                    }
                }
            }
        }
        newRecords.forEach {
            updateRemoteH(
                it.mAppWithSystemSessionOverride,
                it.mProxyMr2.systemController.routingSessionInfo,
            )
        }
        removedApps.forEach { mCallbacks.onRemoteRemoved(SessionId.from(it)) }
    }

    private fun onRemoteVolumeChangedH(sessionToken: MediaSession.Token, flags: Int) {
        val controller = MediaController(mContext, sessionToken)
        if (D.BUG) {
            Log.d(
                TAG,
                "remoteVolumeChangedH " +
                    controller.packageName +
                    " " +
                    Util.audioManagerFlagsToString(flags),
            )
        }
        val token = controller.sessionToken
        mCallbacks.onRemoteVolumeChanged(SessionId.from(token), flags)
    }

    private fun onUpdateRemoteSessionListH(sessionToken: MediaSession.Token?) {
        if (D.BUG) {
            Log.d(
                TAG,
                "onUpdateRemoteSessionListH ${sessionToken?.let {MediaController(mContext, it)}?.packageName}",
            )
        }
        // this may be our only indication that a remote session is changed, refresh
        postUpdateSessions()
    }

    private fun postUpdateSessions() {
        if (mInit) {
            mHandler.sendEmptyMessage(UPDATE_SESSIONS)
        }
    }

    private fun onActiveSessionsUpdatedH(controllers: List<MediaController>) {
        if (D.BUG) {
            Log.d(TAG, "onActiveSessionsUpdatedH n=" + controllers.size)
        }
        val toRemove: MutableSet<MediaSession.Token> = HashSet(mRecords.keys)
        for (controller in controllers) {
            val token = controller.sessionToken
            val playbackInfo = controller.playbackInfo
            toRemove.remove(token)
            if (!mRecords.containsKey(token)) {
                val record = MediaControllerRecord(controller)
                record.name = getControllerName(controller)
                mRecords[token] = record
                controller.registerCallback(record, mHandler)
            }
            val record = mRecords[token]
            val remote = playbackInfo.isRemote()
            if (remote) {
                updateRemoteH(token, record!!.name, playbackInfo)
                record.sentRemote = true
            }
        }
        for (token in toRemove) {
            val record = mRecords[token]!!
            record.controller.unregisterCallback(record)
            mRecords.remove(token)
            if (D.BUG) {
                Log.d(TAG, "Removing " + record.name + " sentRemote=" + record.sentRemote)
            }
            if (record.sentRemote) {
                mCallbacks.onRemoteRemoved(SessionId.from(token))
                record.sentRemote = false
            }
        }
    }

    private fun getControllerName(controller: MediaController): String {
        val pm = mContext.packageManager
        val pkg = controller.packageName
        try {
            if (USE_SERVICE_LABEL) {
                val services =
                    pm.queryIntentServices(
                        Intent("android.media.MediaRouteProviderService").setPackage(pkg),
                        0,
                    )
                if (services != null) {
                    for (ri in services) {
                        if (ri.serviceInfo == null) continue
                        if (pkg == ri.serviceInfo.packageName) {
                            val serviceLabel =
                                Objects.toString(ri.serviceInfo.loadLabel(pm), "").trim()
                            if (serviceLabel.isNotEmpty()) {
                                return serviceLabel
                            }
                        }
                    }
                }
            }
            val ai = pm.getApplicationInfo(pkg, 0)
            val appLabel = Objects.toString(ai.loadLabel(pm), "").trim { it <= ' ' }
            if (appLabel.isNotEmpty()) {
                return appLabel
            }
        } catch (_: PackageManager.NameNotFoundException) {}
        return pkg
    }

    private fun updateRemoteH(
        token: MediaSession.Token,
        name: String?,
        playbackInfo: PlaybackInfo,
    ) = mCallbacks.onRemoteUpdate(SessionId.from(token), name, VolumeInfo.from(playbackInfo))

    private fun updateRemoteH(
        appWithSystemSessionOverride: AppId,
        routingSessionInfo: RoutingSessionInfo,
    ) =
        mCallbacks.onRemoteUpdate(
            SessionId.from(appWithSystemSessionOverride),
            routingSessionInfo.name.toString(),
            VolumeInfo.from(routingSessionInfo),
        )

    private inner class ProxyMediaRouter2Record(
        val mProxyMr2: MediaRouter2,
        val mAppWithSystemSessionOverride: AppId,
    ) : MediaRouter2.ControllerCallback() {
        fun register() {
            mProxyMr2.registerControllerCallback(mHandlerExecutor, this)
        }

        fun release() {
            mProxyMr2.unregisterControllerCallback(this)
        }

        override fun onControllerUpdated(
            controller: MediaRouter2.RoutingController,
            shouldShowVolumeUi: Boolean,
        ) {
            updateRemoteH(
                mAppWithSystemSessionOverride,
                mProxyMr2.systemController.routingSessionInfo,
            )
            mCallbacks.onRemoteVolumeChanged(
                SessionId.from(mAppWithSystemSessionOverride),
                if (shouldShowVolumeUi) {
                    AudioManager.FLAG_SHOW_UI
                } else {
                    0
                },
            )
        }
    }

    private inner class MediaControllerRecord(val controller: MediaController) :
        MediaController.Callback() {
        var sentRemote: Boolean = false
        var name: String? = null

        fun cb(method: String): String {
            return method + " " + controller.packageName + " "
        }

        override fun onAudioInfoChanged(info: PlaybackInfo) {
            if (D.BUG) {
                Log.d(
                    TAG,
                    (cb("onAudioInfoChanged") +
                        Util.playbackInfoToString(info) +
                        " sentRemote=" +
                        sentRemote),
                )
            }
            val remote = info.isRemote()
            if (!remote && sentRemote) {
                mCallbacks.onRemoteRemoved(SessionId.from(controller.sessionToken))
                sentRemote = false
            } else if (remote) {
                updateRemoteH(controller.sessionToken, name, info)
                sentRemote = true
            }
        }

        override fun onExtrasChanged(extras: Bundle?) {
            if (D.BUG) {
                Log.d(TAG, cb("onExtrasChanged") + extras)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (D.BUG) {
                Log.d(TAG, cb("onMetadataChanged") + Util.mediaMetadataToString(metadata))
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (D.BUG) {
                Log.d(TAG, cb("onPlaybackStateChanged") + Util.playbackStateToString(state))
            }
        }

        override fun onQueueChanged(queue: List<MediaSession.QueueItem>?) {
            if (D.BUG) {
                Log.d(TAG, cb("onQueueChanged") + queue)
            }
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            if (D.BUG) {
                Log.d(TAG, cb("onQueueTitleChanged") + title)
            }
        }

        override fun onSessionDestroyed() {
            if (D.BUG) {
                Log.d(TAG, cb("onSessionDestroyed"))
            }
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            if (D.BUG) {
                Log.d(TAG, cb("onSessionEvent") + "event=" + event + " extras=" + extras)
            }
        }
    }

    private inner class H(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UPDATE_SESSIONS -> onActiveSessionsUpdatedH(mMgr.getActiveSessions(null))
                REMOTE_VOLUME_CHANGED ->
                    onRemoteVolumeChangedH(msg.obj as MediaSession.Token, msg.arg1)
                UPDATE_REMOTE_SESSION_LIST ->
                    onUpdateRemoteSessionListH(msg.obj as MediaSession.Token?)
            }
        }
    }

    /** Opaque id for ongoing sessions that support volume adjustment. */
    sealed interface SessionId {

        companion object {
            fun from(token: MediaSession.Token) = Media(token)

            fun from(appWithSystemSessionOverride: AppId) = Routing(appWithSystemSessionOverride)
        }

        data class Media(val token: MediaSession.Token) : SessionId

        data class Routing(val appWithSystemSessionOverride: AppId) : SessionId
    }

    /** Holds session volume information. */
    data class VolumeInfo(val currentVolume: Int, val maxVolume: Int) {

        companion object {

            fun from(playbackInfo: PlaybackInfo) =
                VolumeInfo(playbackInfo.currentVolume, playbackInfo.maxVolume)

            fun from(routingSessionInfo: RoutingSessionInfo): VolumeInfo {
                return VolumeInfo(routingSessionInfo.volume, routingSessionInfo.volumeMax)
            }
        }
    }

    /** Callback for remote media sessions */
    interface Callbacks {
        /** Invoked when remote media session is updated */
        fun onRemoteUpdate(token: SessionId?, name: String?, volumeInfo: VolumeInfo?)

        /** Invoked when remote media session is removed */
        fun onRemoteRemoved(token: SessionId?)

        /** Invoked when remote volume is changed */
        fun onRemoteVolumeChanged(token: SessionId?, flags: Int)
    }

    companion object {
        private val TAG: String = Util.logTag(MediaSessions::class.java)

        const val UPDATE_SESSIONS: Int = 1
        const val REMOTE_VOLUME_CHANGED: Int = 2
        const val UPDATE_REMOTE_SESSION_LIST: Int = 3

        private const val USE_SERVICE_LABEL = false
    }
}

private fun PlaybackInfo?.isRemote() = this?.playbackType == PlaybackInfo.PLAYBACK_TYPE_REMOTE

private fun MediaController.dump(n: Int, writer: PrintWriter) {
    writer.println("  Controller $n: $packageName")

    writer.println("    PlaybackState: ${Util.playbackStateToString(playbackState)}")
    writer.println("    PlaybackInfo: ${Util.playbackInfoToString(playbackInfo)}")
    val metadata = this.metadata
    if (metadata != null) {
        writer.println("  MediaMetadata.desc=${metadata.description}")
    }
    writer.println("    RatingType: $ratingType")
    writer.println("    Flags: $flags")

    writer.println("    Extras:")
    val extras = this.extras
    if (extras == null) {
        writer.println("      <null>")
    } else {
        for (key in extras.keySet()) {
            writer.println("      $key=${extras[key]}")
        }
    }
    writer.println("    QueueTitle: $queueTitle")
    val queue = this.queue
    if (!queue.isNullOrEmpty()) {
        writer.println("    Queue:")
        for (qi in queue) {
            writer.println("      $qi")
        }
    }
    writer.println("    sessionActivity: $sessionActivity")
}
