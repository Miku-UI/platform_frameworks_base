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

package com.android.server.am;

import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the UID transition policy for the safesetid Linux Security Module.
 *
 * <p>This class is used to control which UID transitions are permitted for isolated service
 * processes that are spawned from an application zygote. It interacts with the safesetid policy
 * file in the kernel's securityfs.
 *
 * <p>The number of entries in the safesetid policy file should be equal to the number of running
 * apps using app zygote plus the number of *currently starting* isolated processes. A single
 * persistent entry is used per app zygote to lock-down UID transitions. One temporary entry is
 * added to allow the new process to set its UID and is removed from the policy once the new
 * isolated process has finished initialization.
 *
 * <p>This class is thread-safe. Since the underlying policy file exists in securityfs,
 * no FileLock is needed to ensure exclusive access.
 *
 * @hide
 */
public final class UidTransitionPolicy {

    private static final String TAG = "UidTransitionPolicy";
    private static final Path POLICY_FILE_PATH =
            Paths.get("/sys/kernel/security/safesetid/uid_allowlist_policy");

    private final Object mLock = new Object();
    private final Path mPolicyFilePath;

    /**
     * Thrown when an error occurs while updating the UID transition policy.
     */
    public static class UidTransitionPolicyUpdateException extends RuntimeException {
        public UidTransitionPolicyUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public UidTransitionPolicy() {
        this(POLICY_FILE_PATH);
    }

    @VisibleForTesting
    public UidTransitionPolicy(Path policyFilePath) {
        mPolicyFilePath = policyFilePath;
    }

    /**
     * Returns true if the safesetid UID transition policy is enabled.
     *
     * <p>The policy is enabled if the safesetid LSM is enabled in the kernel and the corresponding
     * feature flag is enabled.
     *
     * @return true if the policy is enabled.
     */
    public static boolean isEnabled() {
        // The safesetid policy file will only exist if the kernel was built with
        // CONFIG_SECURITY_SAFESETID enabled.
        return Files.exists(POLICY_FILE_PATH);
    }

    /**
     * Clears the UID policy to its default state.
     */
    public void clear() {
        synchronized (mLock) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Clearing UID transition policy.");
            }

            setUidTransitionPolicy(Collections.emptyList());
        }
    }

    /**
     * Allows a process with UID {@code src} to transition to UID {@code dst}.
     *
     * <p>This is used to permit an application zygote to spawn an isolated service with a different
     * UID. A rule of the form "src:dst" is added to the policy.
     *
     * @param src The source UID.
     * @param dst The destination UID.
     */
    public void allowUidTransition(int src, int dst) {
        synchronized (mLock) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "allowing UID transition: " + src + " dst: " + dst);
            }

            List<String> policy = getUidTransitionPolicy();

            String newRule = src + ":" + dst;
            if (!policy.contains(newRule)) {
                policy.add(newRule);
            }


            // Safesetid considers a policy insecure if A can transition to B but there are no
            // restrictions on B, since A could escape by transitioning through B.
            // That's not actually the case for us, since B doesn't have permission to call
            // setuid in the first place. However, we still want to insert a B:B restriction
            // to suppress the spurious insecure policy warning in the kernel logs.
            String selfTransitionRule = dst + ":" + dst;
            if (!policy.contains(selfTransitionRule)) {
                policy.add(selfTransitionRule);
            }

            setUidTransitionPolicy(policy);
        }
    }

    /**
     * Disallows a process with UID {@code uid} from transitioning to any other UID.
     *
     * <p>This method removes all existing transition rules originating from {@code uid} and adds a
     * self-transition rule (uid:uid). This is necessary because the default safesetid policy is to
     * allow all transitions if the source UID is not present in the policy. This method is used as
     * a security measure to lock down the application zygote's UID.
     *
     * @param uid The UID to restrict.
     */
    public void disallowAllUidTransitionsFrom(int uid) {
        synchronized (mLock) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "disallowing uid transitions from: " + uid);
            }

            List<String> policy = getUidTransitionPolicy();
            boolean policyChanged = policy.removeIf(rule -> rule.startsWith(uid + ":"));

            // safesetid allows all UID transitions by default unless a given UID is
            // explicitly present in the policy. Add a self-transition rule to the
            // policy to enforce that the *only* allowed transition for |uid| is to
            // itself and all other transitions will be denied.
            String selfTransitionRule = uid + ":" + uid;
            if (!policy.contains(selfTransitionRule)) {
                policy.add(selfTransitionRule);
                policyChanged = true;
            }

            if (policyChanged) {
                setUidTransitionPolicy(policy);
            }
        }
    }

    /**
     * Removes all references of {@code uid} from the policy.
     *
     * @param uid The UID to be purged.
     */
    public void purgeFromPolicy(int uid) {
        synchronized (mLock) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "disallowing transitions to: " + uid);
            }

            List<String> policy = getUidTransitionPolicy();

            boolean policyChanged = policy.removeIf(
                    rule -> rule.endsWith(":" + uid) || rule.startsWith(uid + ":")
            );

            if (policyChanged) {
                setUidTransitionPolicy(policy);
            }
        }
    }

    /**
     * Reads the current UID transition policy from the safesetid policy file.
     *
     * <p>If the file does not exist or an error occurs during reading, an empty mutable list is
     * returned.
     *
     * @return A mutable {@link List} of strings, where each string is a "src:dst" rule.
     */
    private List<String> getUidTransitionPolicy() {
        try {
            return new ArrayList<>(Files.readAllLines(mPolicyFilePath));
        } catch (IOException e) {
            Slog.wtf(TAG, "Error reading from file: " + mPolicyFilePath, e);
            throw new UidTransitionPolicyUpdateException("Failed to read UID policy", e);
        }
    }

    /**
     * Writes the given UID transition policy to the safesetid policy file.
     *
     * <p>Any errors during writing are logged.
     *
     * @param policy The list of "src:dst" rules to write to the policy file.
     */
    private void setUidTransitionPolicy(List<String> policy) {
        try {
            Files.write(
                    mPolicyFilePath,
                    policy,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Slog.wtf(TAG, "Error writing to file: " + mPolicyFilePath, e);
            throw new UidTransitionPolicyUpdateException("Failed to write UID policy", e);
        }
    }
}
