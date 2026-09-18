package com.android.systemui.statusbar.notification.collection.notifcollection

import android.os.Handler
import android.util.ArrayMap
import android.util.Log
import androidx.annotation.MainThread
import com.android.systemui.Dumpable
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.util.asIndenting
import com.android.systemui.util.printCollection
import com.android.systemui.util.println
import com.android.systemui.util.withIncreasedIndent
import java.io.PrintWriter

/**
 * A helpful class that implements the core contract of the lifetime extender internally,
 * making it easier for coordinators to interact with them
 */
abstract class SelfTrackingLifetimeExtender(
    private val tag: String,
    private val name: String,
    private val debug: Boolean,
    private val mainHandler: Handler
) : NotifLifetimeExtender, Dumpable {
    private lateinit var mCallback: NotifLifetimeExtender.OnEndLifetimeExtensionCallback
    protected val mEntriesExtended = ArrayMap<String, NotificationEntry>()
    private var mEnding = false

    /**
     * When debugging, warn if the call is happening during and "end lifetime extension" call.
     *
     * Note: this will warn a lot! The pipeline explicitly re-invokes all lifetime extenders
     * whenever one ends, giving all of them a chance to re-up their lifetime extension.
     */
    private fun warnIfEnding() {
        if (debug && mEnding) Log.w(tag, "reentrant code while ending a lifetime extension")
    }

    fun endAllLifetimeExtensions() {
        // clear the map before iterating over a copy of the items, because the pipeline will
        // always give us another chance to extend the lifetime again, and we don't want
        // concurrent modification
        val entries = mEntriesExtended.values.toList()
        if (debug) Log.d(tag, "$name.endAllLifetimeExtensions() entries=$entries")
        mEntriesExtended.clear()
        warnIfEnding()
        mEnding = true
        entries.forEach { mCallback.onEndLifetimeExtension(this, it) }
        mEnding = false
        entries.forEach { onFinishedLifetimeExtension(it, FinishReason.EndRequested) }
    }

    fun endLifetimeExtensionAfterDelay(key: String, delayMillis: Long) {
        if (debug) {
            Log.d(tag, "$name.endLifetimeExtensionAfterDelay" +
                    "(key=$key, delayMillis=$delayMillis)" +
                    " isExtending=${isExtending(key)}")
        }
        if (isExtending(key)) {
            mainHandler.postDelayed({ endLifetimeExtension(key) }, delayMillis)
        }
    }

    @MainThread
    fun endLifetimeExtension(key: String) {
        if (debug) {
            Log.d(tag, "$name.endLifetimeExtension(key=$key)" +
                    " isExtending=${isExtending(key)}")
        }
        warnIfEnding()
        mEnding = true
        val removedEntry = mEntriesExtended.remove(key)
        removedEntry?.let { mCallback.onEndLifetimeExtension(this, it) }
        mEnding = false
        removedEntry?.let { onFinishedLifetimeExtension(it, FinishReason.EndRequested) }
    }

    fun isExtending(key: String) = mEntriesExtended.contains(key)

    final override fun getName(): String = name

    final override fun maybeExtendLifetime(entry: NotificationEntry, reason: Int): Boolean {
        val shouldExtend = queryShouldExtendLifetime(entry)
        if (debug) {
            Log.d(tag, "$name.shouldExtendLifetime(key=${entry.key}, reason=$reason)" +
                    " isExtending=${isExtending(entry.key)}" +
                    " shouldExtend=$shouldExtend")
        }
        warnIfEnding()
        if (shouldExtend) {
            var oldEntry = mEntriesExtended.put(entry.key, entry)
            if (oldEntry == null) {
                onStartedLifetimeExtension(entry)
            } else if (oldEntry !== entry) {
                if (debug) {
                    Log.w(tag, "$name.maybeExtendLifetime(entry=$entry, reason=$reason)" +
                            " (shouldExtend=$shouldExtend) called when lifetime extension already" +
                            " active for a different entry ($oldEntry) with key: ${entry.key}")
                }
                onFinishedLifetimeExtension(oldEntry, FinishReason.Canceled)
                onStartedLifetimeExtension(entry)
            } else {
                onContinuedLifetimeExtension(entry)
            }
        } else {
            var oldEntry = mEntriesExtended.remove(entry.key)
            if (oldEntry !== entry) {
                if (debug) {
                    Log.w(tag, "$name.maybeExtendLifetime(entry=$entry, reason=$reason)" +
                            " (shouldExtend=$shouldExtend) called when lifetime extension already" +
                            " active for a different entry ($oldEntry) with key: ${entry.key}")
                }
            }
            if (oldEntry != null) {
                onFinishedLifetimeExtension(oldEntry, FinishReason.NotRenewed)
            }
        }
        return shouldExtend
    }

    final override fun cancelLifetimeExtension(entry: NotificationEntry) {
        if (debug) {
            Log.d(tag, "$name.cancelLifetimeExtension(key=${entry.key})" +
                    " isExtending=${isExtending(entry.key)}")
        }
        warnIfEnding()
        mEntriesExtended.remove(entry.key)
        onFinishedLifetimeExtension(entry, FinishReason.Canceled)
    }

    abstract fun queryShouldExtendLifetime(entry: NotificationEntry): Boolean

    /** Called when the extender starts extending an entry. */
    open fun onStartedLifetimeExtension(entry: NotificationEntry) {}
    /** Called every time the extender reaffirms to the pipeline that it is extending an entry. */
    open fun onContinuedLifetimeExtension(entry: NotificationEntry) {}
    /** Called when the extender stops extending an entry. */
    open fun onFinishedLifetimeExtension(entry: NotificationEntry, reason: FinishReason) {}

    enum class FinishReason {
        /** The extender ended the lifetime extension. */
        EndRequested,
        /** The NotificationEntry was cancelled for a non-extendable reason. */
        Canceled,
        /** The extender chose not to renew the lifetime extension. */
        NotRenewed,
    }

    final override fun setCallback(callback: NotifLifetimeExtender.OnEndLifetimeExtensionCallback) {
        mCallback = callback
    }

    final override fun dump(pw: PrintWriter, args: Array<out String>) = pw.asIndenting().run {
        println("LifetimeExtender", name)
        withIncreasedIndent {
            printCollection("mEntriesExtended", mEntriesExtended.keys)
        }
    }
}