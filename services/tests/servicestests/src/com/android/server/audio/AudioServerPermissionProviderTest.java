/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.server.audio;

import static com.android.server.audio.AudioService.generatePackageMap;
import static com.android.server.audio.AudioServerPermissionProvider.MONITORED_PERMS;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.media.permission.INativePermissionController;
import com.android.media.permission.PermissionEnum;
import com.android.media.permission.UidPackageState;
import com.android.server.pm.pkg.PackageState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class AudioServerPermissionProviderTest {

    // Class under test
    // Note, we must initialize this with {@link AudioService.generatePackageMap}, to correctly test
    // the helper function which is used to populate this object in AudioService
    private AudioServerPermissionProvider mPermissionProvider;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock public INativePermissionController mMockPc;

    @Mock public PackageState mMockPackageState_10000_one_sdk33_captrue;
    @Mock public PackageState mMockPackageState_10001_two_sdk33_capfalse;
    @Mock public PackageState mMockPackageStateDup_10000_one_sdk33_captrue;
    @Mock public PackageState mMockPackageState_10000_three_sdk34_captrue;
    @Mock public PackageState mMockPackageState_10001_four_sdk34_capfalse;
    @Mock public PackageState mMockPackageState_10000_two_sdk33_capfalse;

    @Mock public BiPredicate<Integer, String> mMockPermPred;
    @Mock public Supplier<int[]> mMockUserIdSupplier;

    public List<UidPackageState> mInitPackageListExpected;

    // Argument matcher which matches that the state is equal even if the package names are out of
    // order (since they are logically a set).
    public static final class UidPackageStateMatcher implements ArgumentMatcher<UidPackageState> {
        private final int mUid;
        private final List<UidPackageState.PackageState> mSortedPackages;

        public UidPackageStateMatcher(int uid, List<UidPackageState.PackageState> packageStates) {
            mUid = uid;
            if (packageStates != null) {
                mSortedPackages = new ArrayList(packageStates);
                // Sorting only by package name is sufficient, since our expected side will never
                // have dupe package names and if the actual side has a dupe package name, it will
                // fail
                mSortedPackages.sort((a, b) -> a.packageName.compareTo(b.packageName));
            } else {
                mSortedPackages = null;
            }
        }

        public UidPackageStateMatcher(UidPackageState toMatch) {
            this(toMatch.uid, toMatch.packageStates);
        }

        @Override
        public boolean matches(UidPackageState state) {
            if (state == null) return false;
            if (state.uid != mUid) return false;
            if ((state.packageStates == null) != (mSortedPackages == null)) return false;
            ArrayList<UidPackageState.PackageState> copy = new ArrayList(state.packageStates);
            copy.sort((a, b) -> a.packageName.compareTo(b.packageName));
            return mSortedPackages.equals(copy);
        }

        @Override
        public String toString() {
            return "Matcher for UidState with uid: " + mUid + ": " + mSortedPackages;
        }
    }

    public static final class PackageStateListMatcher
            implements ArgumentMatcher<List<UidPackageState>> {

        private final List<UidPackageState> mToMatch;

        public PackageStateListMatcher(List<UidPackageState> toMatch) {
            mToMatch = Objects.requireNonNull(toMatch);
        }

        @Override
        public boolean matches(List<UidPackageState> other) {
            if (other == null) return false;
            if (other.size() != mToMatch.size()) return false;
            for (int i = 0; i < mToMatch.size(); i++) {
                var matcher = new UidPackageStateMatcher(mToMatch.get(i));
                if (!matcher.matches(other.get(i))) return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Matcher for List<UidState> with uid: " + mToMatch;
        }
    }

    @Before
    public void setup() {
        when(mMockPackageState_10000_one_sdk33_captrue.getAppId())
                .thenReturn(10000);
        when(mMockPackageState_10000_one_sdk33_captrue.getPackageName())
                .thenReturn("com.package.one");
        when(mMockPackageState_10000_one_sdk33_captrue.getTargetSdkVersion())
                .thenReturn(33);
        when(mMockPackageState_10000_one_sdk33_captrue.isAudioPlaybackCaptureAllowed())
                .thenReturn(true);

        when(mMockPackageState_10001_two_sdk33_capfalse.getAppId())
                .thenReturn(10001);
        when(mMockPackageState_10001_two_sdk33_capfalse.getPackageName())
                .thenReturn("com.package.two");
        when(mMockPackageState_10001_two_sdk33_capfalse.getTargetSdkVersion())
                .thenReturn(33);
        when(mMockPackageState_10001_two_sdk33_capfalse.isAudioPlaybackCaptureAllowed())
                .thenReturn(false);

        // Same state as the first is intentional, emulating multi-user
        when(mMockPackageStateDup_10000_one_sdk33_captrue.getAppId())
                .thenReturn(10000);
        when(mMockPackageStateDup_10000_one_sdk33_captrue.getPackageName())
                .thenReturn("com.package.one");
        when(mMockPackageStateDup_10000_one_sdk33_captrue.getTargetSdkVersion())
                .thenReturn(33);
        when(mMockPackageStateDup_10000_one_sdk33_captrue.isAudioPlaybackCaptureAllowed())
                .thenReturn(true);

        when(mMockPackageState_10000_three_sdk34_captrue.getAppId())
                .thenReturn(10000);
        when(mMockPackageState_10000_three_sdk34_captrue.getPackageName())
                .thenReturn("com.package.three");
        when(mMockPackageState_10000_three_sdk34_captrue.getTargetSdkVersion())
                .thenReturn(34);
        when(mMockPackageState_10000_three_sdk34_captrue.isAudioPlaybackCaptureAllowed())
                .thenReturn(true);

        when(mMockPackageState_10001_four_sdk34_capfalse.getAppId())
                .thenReturn(10001);
        when(mMockPackageState_10001_four_sdk34_capfalse.getPackageName())
                .thenReturn("com.package.four");
        when(mMockPackageState_10001_four_sdk34_capfalse.getTargetSdkVersion())
                .thenReturn(34);
        when(mMockPackageState_10001_four_sdk34_capfalse.isAudioPlaybackCaptureAllowed())
                .thenReturn(false);

        when(mMockPackageState_10000_two_sdk33_capfalse.getAppId())
                .thenReturn(10000);
        when(mMockPackageState_10000_two_sdk33_capfalse.getPackageName())
                .thenReturn("com.package.two");
        when(mMockPackageState_10000_two_sdk33_capfalse.getTargetSdkVersion())
                .thenReturn(33);
        when(mMockPackageState_10000_two_sdk33_capfalse.isAudioPlaybackCaptureAllowed())
                .thenReturn(false);

        when(mMockUserIdSupplier.get()).thenReturn(new int[] {0, 1});

        when(mMockPermPred.test(eq(10000), eq(MONITORED_PERMS[0]))).thenReturn(true);
        when(mMockPermPred.test(eq(110001), eq(MONITORED_PERMS[0]))).thenReturn(true);
        when(mMockPermPred.test(eq(10001), eq(MONITORED_PERMS[1]))).thenReturn(true);
        when(mMockPermPred.test(eq(110000), eq(MONITORED_PERMS[1]))).thenReturn(true);
    }

    @Test
    public void testInitialPackagePopulation() throws Exception {
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse,
                mMockPackageStateDup_10000_one_sdk33_captrue,
                mMockPackageState_10000_three_sdk34_captrue,
                mMockPackageState_10001_four_sdk34_capfalse,
                mMockPackageState_10000_two_sdk33_capfalse);
        var expectedPackageList =
                List.of(createUidPackageState(10000,
                                List.of(createPackageState("com.package.one", 33, true),
                                        createPackageState("com.package.two", 33, false),
                                        createPackageState("com.package.three", 34, true))),
                        createUidPackageState(10001,
                                List.of(createPackageState("com.package.two", 33, false),
                                        createPackageState("com.package.four", 34, false))));

        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        verify(mMockPc).populatePackagesForUids(
                argThat(new PackageStateListMatcher(expectedPackageList)));
    }

    @Test
    public void testOnModifyPackageState_whenNewUid() throws Exception {
        // 10000: one | 10001: two
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        var newPackageState = createPackageState("com.package.new", 34, true);
        // new uid, including user component
        mPermissionProvider.onModifyPackageState(1_10002, newPackageState, false /* isRemove */);
        mPermissionProvider.onModifyPackageState(2_10002, newPackageState, false /* isRemove */);

        verify(mMockPc).updatePackagesForUid(
                argThat(new UidPackageStateMatcher(10002, List.of(newPackageState))));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnModifyPackageState_whenRemoveUid() throws Exception {
        // 10000: one | 10001: two
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        // Includes user-id
        mPermissionProvider.onModifyPackageState(
                1_10000, createPackageState("com.package.one", 33, true), true /* isRemove */);

        verify(mMockPc).updatePackagesForUid(argThat(new UidPackageStateMatcher(10000, List.of())));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnModifyPackageState_whenUpdatedUidAddition() throws Exception {
        // 10000: one | 10001: two
        var initPackageListData = List.of(
                mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);

        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        var newPackageState = createPackageState("com.package.new", 34, false);
        // Includes user-id
        mPermissionProvider.onModifyPackageState(1_10000, newPackageState, false /* isRemove */);
        mPermissionProvider.onModifyPackageState(2_10000, newPackageState, false /* isRemove */);

        verify(mMockPc).updatePackagesForUid(argThat(new UidPackageStateMatcher(
                10000, List.of(createPackageState("com.package.one", 33, true), newPackageState))));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnModifyPackageState_whenUpdateUidRemoval() throws Exception {
        // 10000: one, two | 10001: two
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse,
                mMockPackageState_10000_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        // Includes user-id
        mPermissionProvider.onModifyPackageState(
                1_10000, createPackageState("com.package.one", 33, true), true /* isRemove */);

        verify(mMockPc).updatePackagesForUid(argThat(new UidPackageStateMatcher(
                10000, List.of(createPackageState("com.package.two", 33, false)))));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnModifyPackageState_whenPackageMetadataChanges() throws Exception {
        // 10000: one, two | 10001: two
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10000_two_sdk33_capfalse,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        // Update the first package with a new SDK version and capture policy
        var updatedPackage = createPackageState("com.package.one", 34, false);
        mPermissionProvider.onModifyPackageState(10000, updatedPackage, false /* isRemove */);

        verify(mMockPc).updatePackagesForUid(argThat(new UidPackageStateMatcher(
                10000, List.of(updatedPackage, createPackageState("com.package.two", 33, false)))));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnModifyPackageState_whenNoPackageChange() throws Exception {
        // 10000: one
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);

        var samePackage = createPackageState("com.package.one", 33, true);
        mPermissionProvider.onModifyPackageState(10000, samePackage, false /* isRemove */);

        verify(mMockPc, never()).updatePackagesForUid(any()); // exactly once
    }

    @Test
    public void testOnServiceStart() throws Exception {
        // 10000: one, two | 10001: two
        var initPackageListData =
                List.of(
                        mMockPackageState_10000_one_sdk33_captrue,
                        mMockPackageState_10001_two_sdk33_capfalse,
                        mMockPackageState_10000_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);
        mPermissionProvider.onServiceStart(mMockPc);
        mPermissionProvider.onModifyPackageState(1_10000,
                createPackageState("com.package.one", 33, true), true /* isRemove */);

        verify(mMockPc).updatePackagesForUid(argThat(new UidPackageStateMatcher(
                10000, List.of(createPackageState("com.package.two", 33, false)))));
        verify(mMockPc).updatePackagesForUid(any()); // exactly once

        var newPackageState = createPackageState("com.package.three", 34, true);
        mPermissionProvider.onModifyPackageState(1_10000, newPackageState, false /* isRemove */);
        verify(mMockPc)
                .updatePackagesForUid(
                        argThat(new UidPackageStateMatcher(
                                        10000, List.of(
                                                createPackageState("com.package.two", 33, false),
                                                newPackageState))));
        verify(mMockPc, times(2)).updatePackagesForUid(any()); // exactly twice
        // state is now 10000: two, three | 10001: two

        // simulate restart of the service
        mPermissionProvider.onServiceStart(null); // should handle null
        var newMockPc = mock(INativePermissionController.class);
        mPermissionProvider.onServiceStart(newMockPc);

        var expectedPackageList =
                List.of(
                        createUidPackageState(10000, List.of(
                                createPackageState("com.package.two", 33, false), newPackageState)),
                        createUidPackageState(10001, List.of(
                                createPackageState("com.package.two", 33, false))));

        verify(newMockPc)
                .populatePackagesForUids(argThat(new PackageStateListMatcher(expectedPackageList)));

        verify(newMockPc, never()).updatePackagesForUid(any());
        // updates should still work after restart
        var newPackageState2 = createPackageState("com.package.four", 34, false);
        mPermissionProvider.onModifyPackageState(10001, newPackageState2, false /* isRemove */);
        verify(newMockPc)
                .updatePackagesForUid(
                        argThat(
                                new UidPackageStateMatcher(
                                        10001, List.of(
                                                createPackageState("com.package.two", 33, false),
                                                newPackageState2))));
        // exactly once
        verify(newMockPc).updatePackagesForUid(any());
    }

    @Test
    public void testPermissionsPopulated_onStart() throws Exception {
        // expected state from setUp:
        // PERM[0]: [10000, 110001]
        // PERM[1]: [10001, 110000]
        // PERM[...]: []
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);

        mPermissionProvider.onServiceStart(mMockPc);
        verify(mMockPc).populatePermissionState(eq((byte) 0), aryEq(new int[] {10000, 110001}));
        verify(mMockPc).populatePermissionState(eq((byte) 1), aryEq(new int[] {10001, 110000}));
        for (int i = 2; i < MONITORED_PERMS.length; i++) {
            verify(mMockPc).populatePermissionState(eq((byte) i), aryEq(new int[] {}));
        }
        verify(mMockPc, times(MONITORED_PERMS.length)).populatePermissionState(anyByte(), any());
    }

    @Test
    public void testSpecialHotwordPermissions() throws Exception {
        BiPredicate<Integer, String> customPermPred = mock(BiPredicate.class);
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        // expected state
        // PERM[CAPTURE_AUDIO_HOTWORD]: [10000]
        // PERM[CAPTURE_AUDIO_OUTPUT]: [10001]
        // PERM[RECORD_AUDIO]: [10001]
        // PERM[...]: []
        when(customPermPred.test(
                        eq(10000), eq(MONITORED_PERMS[PermissionEnum.CAPTURE_AUDIO_HOTWORD])))
                .thenReturn(true);
        when(customPermPred.test(
                        eq(10001), eq(MONITORED_PERMS[PermissionEnum.CAPTURE_AUDIO_OUTPUT])))
                .thenReturn(true);
        when(customPermPred.test(eq(10001), eq(MONITORED_PERMS[PermissionEnum.RECORD_AUDIO])))
                .thenReturn(true);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), customPermPred, () -> new int[] {0});
        int HDS_UID = 99001;
        mPermissionProvider.onServiceStart(mMockPc);
        clearInvocations(mMockPc);
        mPermissionProvider.setIsolatedServiceUid(HDS_UID, 10000);
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.CAPTURE_AUDIO_HOTWORD),
                        aryEq(new int[] {10000, HDS_UID}));
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.CAPTURE_AUDIO_OUTPUT),
                        aryEq(new int[] {10001, HDS_UID}));
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.RECORD_AUDIO), aryEq(new int[] {10001, HDS_UID}));

        clearInvocations(mMockPc);
        mPermissionProvider.clearIsolatedServiceUid(HDS_UID);
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.CAPTURE_AUDIO_HOTWORD), aryEq(new int[] {10000}));
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.CAPTURE_AUDIO_OUTPUT), aryEq(new int[] {10001}));
        verify(mMockPc)
                .populatePermissionState(
                        eq((byte) PermissionEnum.RECORD_AUDIO), aryEq(new int[] {10001}));
    }

    @Test
    public void testPermissionsPopulated_onChange() throws Exception {
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);

        mPermissionProvider.onServiceStart(mMockPc);
        clearInvocations(mMockPc);
        // Ensure the provided permission state is changed
        when(mMockPermPred.test(eq(110001), eq(MONITORED_PERMS[1]))).thenReturn(true);

        mPermissionProvider.onPermissionStateChanged();
        verify(mMockPc)
                .populatePermissionState(eq((byte) 1), aryEq(new int[] {10001, 110000, 110001}));
        verify(mMockPc).populatePermissionState(anyByte(), any()); // exactly once
    }

    @Test
    public void testPermissionPopulatedDeferred_onDeadService() throws Exception {
        var initPackageListData = List.of(mMockPackageState_10000_one_sdk33_captrue,
                mMockPackageState_10001_two_sdk33_capfalse);
        mPermissionProvider = new AudioServerPermissionProvider(
                generatePackageMap(initPackageListData), mMockPermPred, mMockUserIdSupplier);

        // throw on the first call to mark the service as dead
        doThrow(new RemoteException())
                .doNothing()
                .when(mMockPc)
                .populatePermissionState(anyByte(), any());
        mPermissionProvider.onServiceStart(mMockPc);
        clearInvocations(mMockPc);
        clearInvocations(mMockPermPred);

        mPermissionProvider.onPermissionStateChanged();
        verify(mMockPermPred, never()).test(any(), any());
        verify(mMockPc, never()).populatePermissionState(anyByte(), any());
        mPermissionProvider.onServiceStart(mMockPc);
        for (int i = 0; i < MONITORED_PERMS.length; i++) {
            verify(mMockPc).populatePermissionState(eq((byte) i), any());
        }
    }

    private static UidPackageState createUidPackageState(int uid,
            List<UidPackageState.PackageState> packages) {
        var res = new UidPackageState();
        res.uid = uid;
        res.packageStates = packages;
        return res;
    }

    private static UidPackageState.PackageState createPackageState(String packageName,
            int targetSdk, boolean isPlaybackCaptureAllowed) {
        var res = new UidPackageState.PackageState();
        res.packageName = packageName;
        res.targetSdk = targetSdk;
        res.isPlaybackCaptureAllowed = isPlaybackCaptureAllowed;
        return res;
    }
}
