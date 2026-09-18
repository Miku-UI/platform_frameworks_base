/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.accessibility.floatingmenu;

import static com.android.internal.accessibility.AccessibilityShortcutController.ACCESSIBILITY_HEARING_AIDS_COMPONENT_NAME;

import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;
import android.view.accessibility.AccessibilityManager;

import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.compose.animation.scene.SceneKey;
import com.android.internal.accessibility.dialog.AccessibilityTarget;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.bluetooth.HearingAidDeviceManager;
import com.android.systemui.Flags;
import com.android.systemui.inputdevice.data.repository.PointerDeviceRepository;
import com.android.systemui.keyboard.data.repository.KeyboardRepository;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardState;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.scene.shared.model.KeyguardScenesKt;
import com.android.systemui.util.settings.SecureSettings;

import java.util.ArrayList;
import java.util.List;
/**
 * The view model provides the menu information from the repository{@link MenuInfoRepository} for
 * the menu view{@link MenuView}.
 */
public class MenuViewModel implements MenuInfoRepository.OnContentsChanged {
    private final MutableLiveData<List<AccessibilityTarget>> mOriginalTargets =
            new MutableLiveData<>(emptyList());
    private final MediatorLiveData<List<AccessibilityTarget>> mMenuTargets =
            new MediatorLiveData<>();
    private final MutableLiveData<Integer> mSizeTypeData = new MutableLiveData<>();
    private final MutableLiveData<MenuFadeEffectInfo> mFadeEffectInfoData =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> mMoveToTuckedData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mDockTooltipData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mMigrationTooltipData = new MutableLiveData<>();
    private final MutableLiveData<Position> mPercentagePositionData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mHearingDeviceStatusData = new MutableLiveData<>(
            HearingAidDeviceManager.ConnectionStatus.NO_DEVICE_BONDED);
    private final MediatorLiveData<Integer> mHearingDeviceTargetIndex =  new MediatorLiveData<>(-1);
    private final MutableLiveData<MenuPosition> mPositionData =
            new MutableLiveData<>();
    private final MediatorLiveData<Boolean> mIsGuardedScene = new MediatorLiveData<>(false);

    private final MenuInfoRepository mInfoRepository;
    private final MoreOptionsTarget mMoreOptionsTarget;


    private MenuPosition mLastMenuPosition = MenuPosition.BOTTOM_RIGHT;

    MenuViewModel(
            Context context,
            AccessibilityManager accessibilityManager,
            SecureSettings secureSettings,
            HearingAidDeviceManager hearingAidDeviceManager,
            KeyboardRepository keyboardRepository,
            PointerDeviceRepository pointerDeviceRepository,
            KeyguardTransitionInteractor keyguardTransitionInteractor,
            SceneInteractor sceneInteractor) {
        mInfoRepository =
                new MenuInfoRepository(
                        context,
                        accessibilityManager,
                        /* settingsContentsChanged= */ this,
                        secureSettings,
                        hearingAidDeviceManager);
        mMoreOptionsTarget = new MoreOptionsTarget(context);

        setupKeyguardMonitoring(keyguardTransitionInteractor, sceneInteractor);
        setupTargetFeaturesObservation(keyboardRepository, pointerDeviceRepository);
        setupHearingDeviceMonitoring();
    }

    private void setupTargetFeaturesObservation(
            KeyboardRepository keyboardRepository,
            PointerDeviceRepository pointerDeviceRepository) {
        if (!Flags.floatingMenuMoreOptions()) {
            mMenuTargets.addSource(mOriginalTargets, mMenuTargets::setValue);
            return;
        }

        LiveData<Boolean> isKeyboardConnected =
                FlowLiveDataConversions.asLiveData(keyboardRepository.isAnyKeyboardConnected());
        LiveData<Boolean> isPointerDeviceConnected =
                FlowLiveDataConversions.asLiveData(
                        pointerDeviceRepository.isAnyPointerDeviceConnected());

        mMenuTargets.addSource(mOriginalTargets, targets -> recalculateTargetFeatures(
                targets,
                isKeyboardConnected.getValue(),
                isPointerDeviceConnected.getValue(),
                mIsGuardedScene.getValue()));
        mMenuTargets.addSource(isKeyboardConnected, connected -> recalculateTargetFeatures(
                mOriginalTargets.getValue(),
                connected,
                isPointerDeviceConnected.getValue(),
                mIsGuardedScene.getValue()));
        mMenuTargets.addSource(isPointerDeviceConnected, connected -> recalculateTargetFeatures(
                mOriginalTargets.getValue(),
                isKeyboardConnected.getValue(),
                connected,
                mIsGuardedScene.getValue()));
        mMenuTargets.addSource(mIsGuardedScene, isGuarded -> recalculateTargetFeatures(
                mOriginalTargets.getValue(),
                isKeyboardConnected.getValue(),
                isPointerDeviceConnected.getValue(),
                isGuarded));
    }

    private void setupKeyguardMonitoring(
            KeyguardTransitionInteractor keyguardTransitionInteractor,
            SceneInteractor sceneInteractor) {
        if (!Flags.floatingMenuMoreOptions()) {
            return;
        }

        if (!Flags.sceneContainer()) {
            LiveData<KeyguardState> keyguardState =
                    FlowLiveDataConversions.asLiveData(
                            keyguardTransitionInteractor.getCurrentKeyguardState());
            mIsGuardedScene.addSource(
                    keyguardState, state -> mIsGuardedScene.setValue(
                            state != KeyguardState.GONE && state != KeyguardState.UNDEFINED));
        } else {
            LiveData<SceneKey> sceneKey =
                    FlowLiveDataConversions.asLiveData(
                            sceneInteractor.getCurrentScene());
            mIsGuardedScene.addSource(
                    sceneKey, key -> mIsGuardedScene.setValue(
                            KeyguardScenesKt.isKeyguardScene(key)));
        }
    }

    private void setupHearingDeviceMonitoring() {
        mHearingDeviceTargetIndex.addSource(
                mMenuTargets,
                targets -> mHearingDeviceTargetIndex.setValue(
                        getHearingDeviceTargetIndex(targets)));
    }

    private void recalculateTargetFeatures(
            List<AccessibilityTarget> targets,
            Boolean isKeyboardConnected,
            Boolean isPointerDeviceConnected,
            Boolean isGuardedScene) {
        if (targets == null) {
            targets = emptyList();
        }

        boolean hasInputDevice = Boolean.TRUE.equals(isKeyboardConnected)
                || Boolean.TRUE.equals(isPointerDeviceConnected);
        boolean shouldShowMoreOptions = Flags.floatingMenuMoreOptions()
                && hasInputDevice && !isGuardedScene;

        if (!shouldShowMoreOptions) {
            mMenuTargets.setValue(targets);
            return;
        }

        List<AccessibilityTarget> updatedTargets = new ArrayList<>(targets);
        updatedTargets.add(mMoreOptionsTarget);
        mMenuTargets.setValue(updatedTargets);
    }

    @Override
    public void onTargetFeaturesChanged(List<AccessibilityTarget> newTargetFeatures) {
        mOriginalTargets.setValue(newTargetFeatures);
    }

    @Override
    public void onSizeTypeChanged(int newSizeType) {
        mSizeTypeData.setValue(newSizeType);
    }

    @Override
    public void onFadeEffectInfoChanged(MenuFadeEffectInfo fadeEffectInfo) {
        mFadeEffectInfoData.setValue(fadeEffectInfo);
    }

    @Override
    public void onDevicesConnectionStatusChanged(
            @HearingAidDeviceManager.ConnectionStatus int status) {
        mHearingDeviceStatusData.postValue(status);
    }

    @VisibleForTesting
    void onGuardedSceneChanged(boolean isGuardedScene) {
        mIsGuardedScene.setValue(isGuardedScene);
    }

    void updateMenuMoveToTucked(boolean isMoveToTucked) {
        mInfoRepository.updateMoveToTucked(isMoveToTucked);
    }

    void updateMenuSavingPosition(Position percentagePosition) {
        mInfoRepository.updateMenuSavingPosition(percentagePosition);
        // LiveData updates must happen on the main thread.
        // If we are already on the main thread (e.g., in tests), use setValue() for
        // synchronous updates to avoid race conditions. Otherwise, use postValue()
        // to safely update from background threads (e.g., animation callbacks).
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mPositionData.setValue(null);
        } else {
            mPositionData.postValue(null);
        }
    }

    void updateDockTooltipVisibility(boolean hasSeen) {
        mInfoRepository.updateDockTooltipVisibility(hasSeen);
    }

    void updateMigrationTooltipVisibility(boolean visible) {
        mInfoRepository.updateMigrationTooltipVisibility(visible);
    }

    LiveData<Boolean> getMoveToTuckedData() {
        mInfoRepository.loadMenuMoveToTucked(mMoveToTuckedData::setValue);
        return mMoveToTuckedData;
    }

    LiveData<Boolean> getDockTooltipVisibilityData() {
        mInfoRepository.loadDockTooltipVisibility(mDockTooltipData::setValue);
        return mDockTooltipData;
    }

    LiveData<Boolean> getMigrationTooltipVisibilityData() {
        mInfoRepository.loadMigrationTooltipVisibility(mMigrationTooltipData::setValue);
        return mMigrationTooltipData;
    }

    LiveData<Position> getPercentagePositionData() {
        mInfoRepository.loadMenuPosition(mPercentagePositionData::setValue);
        return mPercentagePositionData;
    }

    LiveData<MenuPosition> getPositionData() {
        return mPositionData;
    }

    MenuPosition getMenuPosition() {
        return mLastMenuPosition;
    }

    void cycleMenuPosition() {
        MenuPosition currentPosition = mPositionData.getValue();

        if (currentPosition == null) {
            currentPosition = mLastMenuPosition;
        }

        MenuPosition nextPosition = switch (currentPosition) {
            case BOTTOM_RIGHT -> MenuPosition.BOTTOM_LEFT;
            case BOTTOM_LEFT -> MenuPosition.TOP_LEFT;
            case TOP_LEFT -> MenuPosition.TOP_RIGHT;
            case TOP_RIGHT -> MenuPosition.BOTTOM_RIGHT;
            case null -> MenuPosition.BOTTOM_RIGHT;
        };

        mLastMenuPosition = nextPosition;
        mPositionData.setValue(nextPosition);
    }

    LiveData<Integer> getSizeTypeData() {
        mInfoRepository.loadMenuSizeType(mSizeTypeData::setValue);
        return mSizeTypeData;
    }

    LiveData<MenuFadeEffectInfo> getFadeEffectInfoData() {
        mInfoRepository.loadMenuFadeEffectInfo(mFadeEffectInfoData::setValue);
        return mFadeEffectInfoData;
    }

    LiveData<List<AccessibilityTarget>> getTargetFeaturesData() {
        mInfoRepository.loadMenuTargetFeatures(mOriginalTargets::setValue);
        return mMenuTargets;
    }

    LiveData<Integer> loadHearingDeviceStatus() {
        mInfoRepository.loadHearingDeviceStatus(mHearingDeviceStatusData::setValue);
        return mHearingDeviceStatusData;
    }

    LiveData<Integer> getHearingDeviceStatusData() {
        return mHearingDeviceStatusData;
    }

    LiveData<Integer> getHearingDeviceTargetIndexData() {
        return mHearingDeviceTargetIndex;
    }

    void registerObserversAndCallbacks() {
        mInfoRepository.registerObserversAndCallbacks();
    }

    void unregisterObserversAndCallbacks() {
        mInfoRepository.unregisterObserversAndCallbacks();
    }

    private int getHearingDeviceTargetIndex(List<AccessibilityTarget> targetList) {
        final int listSize = targetList.size();
        for (int index = 0; index < listSize; index++) {
            AccessibilityTarget target = targetList.get(index);
            if (ACCESSIBILITY_HEARING_AIDS_COMPONENT_NAME.equals(
                    ComponentName.unflattenFromString(target.getId()))) {
                return index;
            }
        }
        return -1;
    }
}
