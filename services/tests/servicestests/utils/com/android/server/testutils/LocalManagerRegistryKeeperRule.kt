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

package com.android.server.testutils

import com.android.server.LocalManagerRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule helps override /restore [LocalManagerRegistry] state.
 */
class LocalManagerRegistryKeeperRule : TestRule {

    private val overriddenManagers = mutableMapOf<Class<*>, Any>()
    private val addedManagers = mutableListOf<Class<*>>()

    @Volatile
    private var ruleApplied = false

    /**
     * Overrides service in LocalManagerRegistry. Manager will be restored to original after test
     * run.
     */
    fun <T : Any> overrideLocalManager(type: Class<T>, service: T) {
        if (!ruleApplied) {
            throw IllegalStateException("Can't override manager without applying rule")
        }

        val alreadyOverridden = overriddenManagers.containsKey(type) || addedManagers.contains(type)

        if (!alreadyOverridden) {
            val currentManager = LocalManagerRegistry.getManager(type)

            if (currentManager != null) {
                overriddenManagers[type] = currentManager
            }
        }

        // Remove service from LocalManagerRegistry if present.
        LocalManagerRegistry.removeManagerForTesting(type)

        // Remove from tracked AddedManagers if present.
        addedManagers.remove(type)

        // If there is no stored value to restore, then this service is being set for the first
        // time.
        if (!overriddenManagers.containsKey(type)) {
            addedManagers.add(type)
        }

        LocalManagerRegistry.addManager(type, service)
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    ruleApplied = true
                    base.evaluate()
                } finally {
                    ruleApplied = false
                    addedManagers.forEach { LocalManagerRegistry.removeManagerForTesting(it) }
                    overriddenManagers.forEach { (clazz, service) ->
                        LocalManagerRegistry.removeManagerForTesting(clazz)
                      // This is a safe cast so we can suppress the warning.
                      @Suppress("UNCHECKED_CAST")
                      LocalManagerRegistry.addManager(clazz as Class<Any>, service)
                    }
                    addedManagers.clear()
                    overriddenManagers.clear()
                }
            }
        }
    }
}
