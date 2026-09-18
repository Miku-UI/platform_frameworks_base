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

package com.android.server.multisensory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.os.multisensory.MultisensoryManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.LocalServices;
import com.android.server.multisensory.logging.MultisensoryPlaybackRecord;
import com.android.server.multisensory.playback.MultisensoryPlayerDefault;
import com.android.server.multisensory.playback.MultisensoryRemotePlayerScope;
import com.android.server.multisensory.repository.MultisensoryRepository;
import com.android.server.vibrator.VibratorManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class MultisensoryServiceScopeTest {

    private final MultisensoryRepository mRepository = new MultisensoryRepository();
    private final FakeMultisensoryPlayer mRemotePlayer = new FakeMultisensoryPlayer();
    private final VibratorManagerInternal mVibratorManagerInternal =
            mock(VibratorManagerInternal.class);
    private int mDeviceId;
    private MultisensoryPlayerDefault mDefaultPlayer;
    private MultisensoryServiceScope mUnderTest;

    @Before
    public void setUp() {
        addLocalServiceMock(VibratorManagerInternal.class, mVibratorManagerInternal);
        mRemotePlayer.setUp();
        initializeScope();
    }

    @Test
    public void registerRemotePlayer_registersCorrectly() {
        mUnderTest.registerRemotePlayer(mRemotePlayer);

        assertTrue(mUnderTest.isRemotePlayerRegistered());
    }

    @Test
    public void registerRemotePlayer_tokensAreLoaded() throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;

        mUnderTest.registerRemotePlayer(mRemotePlayer);

        assertTrue(mRemotePlayer.isTokenLoaded(token));
    }

    @Test
    public void registerRemotePlayer_whenRemotePlayerIsDropped_unlinkToDeathIsCalled()
            throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS);

        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.flushLoadResults();

        mRemotePlayer.triggerBinderDied();

        assertTrue(mRemotePlayer.wasUnlinkToDeathCalled());
    }

    @Test
    public void registerRemotePlayer_onRemotePlayerDropped_oldPlayerLoadResultIsIgnored()
            throws RemoteException {
        // Two players expected to successfully load the UNLOCK token
        FakeMultisensoryPlayer backupPlayer = new FakeMultisensoryPlayer();
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS);
        backupPlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS);

        // When the original player is registered but is dropped
        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.triggerBinderDied();

        // When the backup player is registered due to the crash
        mUnderTest.registerRemotePlayer(backupPlayer);

        // In the race condition, the dropped player reports its results
        mRemotePlayer.flushLoadResults();

        // Then, the results from the dropped player are ignored.
        // If they were not ignored, playToken would try to use the (dropped) remote player.
        playToken(token);
        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);

        // When the backup player finally reports back, then its results are properly registered
        backupPlayer.flushLoadResults();
        playToken(token);
        assertEquals(token, backupPlayer.getLastTokenPlayed());
    }

    @Test
    public void registerRemotePlayer_onRemoteCrashLoop_retainsLastValidPlayer() {
        int totalCrashedPlayers = 5;
        List<FakeMultisensoryPlayer> crashedPlayers =
                simulateRemotePlayerCrashLoop(totalCrashedPlayers);

        // After the crash loop, all previous (crashed) players get canceled once
        assertCancellationInPlayers(crashedPlayers, 1);

        // When a final (and valid) remote player is registered
        mUnderTest.registerRemotePlayer(mRemotePlayer);

        assertTrue(mUnderTest.isRemotePlayerRegistered());
        // Verification: player IDs are sequential and start at 0
        assertEquals(totalCrashedPlayers, mRemotePlayer.getPlayerId());
    }

    @Test
    public void registerRemotePlayer_linksToDeathAndClearsOnDeath() {
        mUnderTest.registerRemotePlayer(mRemotePlayer);

        // Verification: linkToDeath was called on the binder
        assertTrue(mRemotePlayer.wasLinkToDeathCalled());
        assertTrue(mRemotePlayer.isLinkedToDeath());

        // When the binder dies
        mRemotePlayer.triggerBinderDied();

        // Verification: the death recipient is cleared (unlinked)
        assertFalse(mRemotePlayer.isLinkedToDeath());
    }

    @Test
    public void registerRemotePlayer_onRemoteException_loadStatusIndicatesException() {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION);

        mUnderTest.registerRemotePlayer(mRemotePlayer);

        // If load fails with exception, playing a token should fallback to default player
        playToken(token);
        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);
    }

    @Test
    public void registerRemotePlayer_newPlayersHaveIncreasingIds() {
        FakeMultisensoryPlayer backupPlayer = new FakeMultisensoryPlayer();

        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mUnderTest.registerRemotePlayer(backupPlayer);

        // Verification: backup player has the latest ID and it is sequential
        assertEquals(backupPlayer.getPlayerId(), 1);
        assertEquals(mRemotePlayer.getPlayerId(), 0);
    }

    @Test
    public void playToken_withoutRemotePlayer_playsWithDefaultPlayer() {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        playToken(token);

        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);
    }

    @Test
    public void playToken_withCrashedRemotePlayer_playsWithDefaultPlayer() throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        int loadResultForToken = MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS;
        mRemotePlayer.setLoadResultForToken(token, loadResultForToken);

        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.flushLoadResults();

        mRemotePlayer.triggerBinderDied();

        playToken(token);

        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);
    }

    @Test
    public void playToken_withRemotePlayerAndSuccessfulLoad_playsWithRemotePlayer()
            throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS);
        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.flushLoadResults();

        playToken(token);

        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.REMOTE);
    }

    @Test
    public void playToken_withRemotePlayerAndFailedLoad_playsWithDefaultPlayer()
            throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_UNKNOWN_ERROR);
        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.flushLoadResults();

        playToken(token);

        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);
    }

    @Test
    public void playToken_withRemotePlayerAndRemoteException_playsWithDefaultPlayer()
            throws RemoteException {
        int token = MultisensoryManager.TOKEN_UNLOCK;
        mRemotePlayer.setLoadResultForToken(
                token, MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION);
        mUnderTest.registerRemotePlayer(mRemotePlayer);
        mRemotePlayer.flushLoadResults();

        playToken(token);

        assertVibrationCall(token, MultisensoryPlaybackRecord.PlayerType.DEFAULT);
    }

    private void initializeScope() {
        Context testContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VibratorManager vibratorManager = testContext.getSystemService(VibratorManager.class);
        mDeviceId = vibratorManager.getDefaultVibrator().getId();
        mDefaultPlayer = new MultisensoryPlayerDefault(mDeviceId);
        mUnderTest =
                new MultisensoryServiceScope(
                        mRepository,
                        mDefaultPlayer,
                        vibratorManager.getDefaultVibrator(),
                        testContext.getContentResolver());
    }

    private void assertVibrationCall(int token, MultisensoryPlaybackRecord.PlayerType playerType) {
        VibrationEffect vibration =
                mRepository.getHapticEffect(token).createSingleVibrationEffect();
        if (playerType == MultisensoryPlaybackRecord.PlayerType.DEFAULT) {
            // The default player is used and the remote is not used
            verify(mVibratorManagerInternal)
                    .vibrateWithoutPermissionCheck(
                            eq(mDeviceId), eq(vibration), any(), anyString(), any());
            assertFalse(
                    "Remote player should NOT have been used", mRemotePlayer.isTokenPlayed(token));
        } else if (playerType == MultisensoryPlaybackRecord.PlayerType.REMOTE) {
            // The remote player is used and the default player is not used
            assertTrue("Remote player should have been used", mRemotePlayer.isTokenPlayed(token));
            assertEquals(vibration, mRemotePlayer.getLastVibrationEffectPlayed());
            verify(mVibratorManagerInternal, never())
                    .vibrateWithoutPermissionCheck(
                            eq(mDeviceId), eq(vibration), any(), anyString(), any());
        } else {
            fail("Expected a valid PlayerType but got " + playerType);
        }
    }

    private void playToken(int token) {
        clearInvocations(mVibratorManagerInternal);
        mRemotePlayer.clearLastPlayed();
        mUnderTest.playToken(token);
    }

    /**
     * In a registration crash loop, remote players keep getting registered as old ones keep
     * crashing. This method simulates this scenario for a given number of players/attempts
     */
    private List<FakeMultisensoryPlayer> simulateRemotePlayerCrashLoop(int numberOfPlayers) {
        ArrayList<FakeMultisensoryPlayer> crashedPlayers = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++) {
            FakeMultisensoryPlayer player = new FakeMultisensoryPlayer();
            mUnderTest.registerRemotePlayer(player);
            player.triggerBinderDied();
            crashedPlayers.add(player);
        }
        return crashedPlayers;
    }

    private void assertCancellationInPlayers(
            List<FakeMultisensoryPlayer> players, int cancellationTimes) {
        int playersCorrectlyCancelled = 0;
        for (FakeMultisensoryPlayer player : players) {
            if (player.getTimesCancelled() == cancellationTimes) {
                playersCorrectlyCancelled++;
            }
        }
        assertEquals(players.size(), playersCorrectlyCancelled);
    }

    private static <T> void addLocalServiceMock(Class<T> clazz, T mock) {
        LocalServices.removeServiceForTest(clazz);
        LocalServices.addService(clazz, mock);
    }
}
