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
package com.android.systemui.inputmethod.data.repository

import android.Manifest
import android.annotation.BinderThread
import android.annotation.IntRange
import android.annotation.RequiresPermission
import android.annotation.UserIdInt
import android.content.Intent
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import android.util.SparseArray
import android.view.inputmethod.Flags
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import androidx.annotation.MainThread
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.internal.inputmethod.IImeSwitcherMenuListener
import com.android.internal.inputmethod.ImeSwitcherMenuItemSafeList
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel.Companion.NOT_A_SUBTYPE_INDEX
import com.android.systemui.inputmethod.shared.model.ModelChangeListener
import com.android.systemui.util.asIndenting
import com.android.systemui.util.printSection
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Repository interface to send and receive IPCs to System Server, for calls related to the IME
 * Switcher Menu.
 */
interface ImeSwitcherMenuRepository {

    /**
     * Retrieves the stored data model for the given user, or returns `null` if no model is stored
     * for that user.
     *
     * @param userId the ID of the user whose model to get.
     */
    @MainThread fun getModel(@UserIdInt userId: Int): ImeSwitcherMenuModel?

    /**
     * Registers the given model change listener.
     *
     * @param listener the listener to register.
     */
    fun registerModelChangeListener(listener: ModelChangeListener)

    /**
     * Unregisters the given model change listener.
     *
     * @param listener the listener to unregister.
     */
    fun unregisterModelChangeListener(listener: ModelChangeListener)

    /**
     * Notifies when the IME Switcher Menu visibility changed for the given user on the given
     * display.
     *
     * @param visible the new visibility of the menu.
     * @param displayId the ID of the display where the menu visibility changed.
     * @param userId the ID of the user whose menu visibility changed.
     */
    fun notifyVisibilityChanged(visible: Boolean, displayId: Int, userId: Int)

    /**
     * Notifies when an IME and subtype was selected in the IME Switcher Menu by the given user.
     * This will switch to the IME if it is enabled and installed, and otherwise will do nothing. If
     * the subtype index is also supplied (not [NOT_A_SUBTYPE_INDEX]) and valid, also switches to
     * it, otherwise the system devices the most sensible default subtype to use.
     *
     * @param imeId the ID of the selected IME.
     * @param subtypeIndex the selected subtype, as an index in the IME's array of subtypes, or
     *   [NOT_A_SUBTYPE_INDEX] if the system should decide the most sensible subtype.
     * @param userId the ID of the user that selected the IME and subtype.
     */
    fun notifyImeAndSubtypeSelected(imeId: String, subtypeIndex: Int, userId: Int)

    /**
     * Whether the settings button should be shown on the IME Switcher Menu based on the device and
     * user state.
     *
     * @param userId the ID of the user to show the menu for.
     */
    fun shouldShowSettingsButton(@UserIdInt userId: Int): Boolean
}

/** Default implementation of [ImeSwitcherMenuRepository]. */
@SysUISingleton
class ImeSwitcherMenuRepositoryImpl
@Inject
constructor(
    private val secureSettings: SecureSettings,
    private val globalSettings: GlobalSettings,
    private val inputMethodManager: InputMethodManager,
    @param:Main private val mainExecutor: Executor,
) : ImeSwitcherMenuRepository, CoreStartable {

    /** The implementation of the interface for IPC calls coming from System Server. */
    private val impl = IImeSwitcherMenuImpl()

    /** The stored models for each userId. */
    private val models = SparseArray<ImeSwitcherMenuModel>()

    /** The listeners to be notified when the model tied to a user has changed. */
    private val modelChangeListeners = CopyOnWriteArrayList<ModelChangeListener>()

    /** The system server interface to receive callbacks from this controller. */
    private var systemServerListener: IImeSwitcherMenuListener? = null

    @RequiresPermission(
        allOf =
            [
                Manifest.permission.WRITE_SECURE_SETTINGS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.STATUS_BAR_SERVICE,
            ]
    )
    override fun start() {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        inputMethodManager.registerImeSwitcherMenu(impl)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.asIndenting().run { printSection(TAG) { println("models: $models") } }
    }

    @MainThread
    override fun getModel(@UserIdInt userId: Int): ImeSwitcherMenuModel? = models[userId]

    override fun registerModelChangeListener(listener: ModelChangeListener) {
        modelChangeListeners.add(listener)
    }

    override fun unregisterModelChangeListener(listener: ModelChangeListener) {
        modelChangeListeners.remove(listener)
    }

    override fun notifyVisibilityChanged(visible: Boolean, displayId: Int, @UserIdInt userId: Int) {
        if (!visible) {
            // The UI is no longer visible, remove the model immediately to avoid accidentally
            // showing if the model is partially updated later.
            updateModel(userId, model = null)
        }

        try {
            systemServerListener?.onVisibilityChanged(visible, displayId, userId)
        } catch (e: RemoteException) {
            Log.e(
                TAG,
                "Failed to notify listener of new visibility: $visible for user: $userId" +
                    " and display: $displayId",
                e,
            )
        }
    }

    override fun notifyImeAndSubtypeSelected(
        imeId: String,
        @IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) subtypeIndex: Int,
        @UserIdInt userId: Int,
    ) {
        try {
            systemServerListener?.onImeAndSubtypeSelected(imeId, subtypeIndex, userId)
        } catch (e: RemoteException) {
            Log.e(
                TAG,
                "Failed to notify listener of new selected IME: $imeId and subtype" +
                    " index: $subtypeIndex for user: $userId",
                e,
            )
        }
    }

    override fun shouldShowSettingsButton(@UserIdInt userId: Int): Boolean {
        val isDeviceProvisioned =
            globalSettings.getInt(
                Settings.Global.DEVICE_PROVISIONED,
                0,
            ) != 0
        val isUserSetUp =
            secureSettings.getIntForUser(
                Settings.Secure.USER_SETUP_COMPLETE,
                0,
                userId,
            ) != 0
        return isDeviceProvisioned && isUserSetUp
    }

    /**
     * Updates the stored model for the given user.
     *
     * @param userId the ID of the user whose model to update.
     * @param model the new model, or `null` if the model was removed.
     */
    private fun updateModel(@UserIdInt userId: Int, model: ImeSwitcherMenuModel?) {
        val current = models[userId]
        if (current != model) {
            if (model != null) {
                models.put(userId, model)
            } else {
                models.remove(userId)
            }
            modelChangeListeners.forEach { it.onChanged(userId, model) }
        }
    }

    /** The interface for IPC calls coming from System Server. */
    @BinderThread
    private inner class IImeSwitcherMenuImpl : IImeSwitcherMenu.Stub() {

        override fun show(
            items: ImeSwitcherMenuItemSafeList,
            selectedImeId: String?,
            selectedSubtypeIndex: Int,
            selectedImeSettingsIntent: Intent?,
            isScreenLocked: Boolean,
            @IMPickerEntryPoint entryPoint: Int,
            displayId: Int,
            @UserIdInt userId: Int,
        ) {
            mainExecutor.execute {
                val model =
                    ImeSwitcherMenuModel(
                        items = ImeSwitcherMenuItemSafeList.extractFrom(items),
                        selectedImeId,
                        selectedSubtypeIndex,
                        selectedImeSettingsIntent,
                        isScreenLocked,
                        entryPoint,
                        displayId,
                    )
                updateModel(userId, model)
            }
        }

        override fun hide(@UserIdInt userId: Int) {
            mainExecutor.execute { updateModel(userId, null) }
        }

        override fun registerListener(listener: IImeSwitcherMenuListener) {
            mainExecutor.execute {
                this@ImeSwitcherMenuRepositoryImpl.systemServerListener = listener
            }
        }

        override fun notifyImeAndSubtypeChanged(
            selectedImeId: String?,
            selectedSubtypeIndex: Int,
            selectedImeSettingsIntent: Intent?,
            @UserIdInt userId: Int,
        ) {
            mainExecutor.execute {
                val current = models[userId]
                if (current != null) {
                    val newModel =
                        current.copy(
                            selectedImeId = selectedImeId,
                            selectedSubtypeIndex = selectedSubtypeIndex,
                            selectedImeSettingsIntent = selectedImeSettingsIntent,
                        )

                    updateModel(userId, newModel)
                }
            }
        }
    }

    companion object {

        private const val TAG = "ImeSwitcherMenuRepo"
    }
}

/** Dagger module for [ImeSwitcherMenuRepository]. */
@Module
interface ImeSwitcherMenuRepositoryModule {

    @Binds fun bindRepositoryImpl(impl: ImeSwitcherMenuRepositoryImpl): ImeSwitcherMenuRepository

    @Binds
    @IntoMap
    @ClassKey(ImeSwitcherMenuRepositoryImpl::class)
    fun bindRepositoryStartable(impl: ImeSwitcherMenuRepositoryImpl): CoreStartable
}
