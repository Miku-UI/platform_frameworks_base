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

package com.android.systemui.display.data.repository

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.displaylib.DisplayInstanceLifecycleManager
import com.android.app.displaylib.PerDisplayInstanceProviderWithSetup
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class PerDisplayInstanceRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val fakeDisplayRepository = kosmos.displayRepository
    private val fakePerDisplayInstanceProviderWithTeardown =
        kosmos.fakePerDisplayInstanceProviderWithSetupAndTeardown
    private val lifecycleManager = kosmos.fakeDisplayInstanceLifecycleManager

    private val underTest: PerDisplayInstanceRepositoryImpl<TestPerDisplayInstance> =
        kosmos.createPerDisplayInstanceRepository(overrideLifecycleManager = null)

    @Before
    fun addDisplays() = runBlocking {
        fakeDisplayRepository += createDisplay(DEFAULT_DISPLAY_ID)
        fakeDisplayRepository += createDisplay(NON_DEFAULT_DISPLAY_ID)
    }

    @Test
    fun forDisplay_defaultDisplay_multipleCalls_returnsSameInstance() =
        testScope.runTest {
            val instance = underTest[DEFAULT_DISPLAY_ID]

            assertThat(underTest[DEFAULT_DISPLAY_ID]).isSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonDefaultDisplay_multipleCalls_returnsSameInstance() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonDefaultDisplay_afterDisplayRemoved_returnsNewInstance() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            fakeDisplayRepository -= NON_DEFAULT_DISPLAY_ID
            fakeDisplayRepository += createDisplay(NON_DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNotSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonExistingDisplayId_returnsNull() =
        testScope.runTest { assertThat(underTest[NON_EXISTING_DISPLAY_ID]).isNull() }

    @Test
    fun forDisplay_afterDisplayCreated_setupInvoked() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(fakePerDisplayInstanceProviderWithTeardown.created).containsExactly(instance)
        }

    @Test
    fun forDisplay_calledMultipleTimes_setupInvokedOnce() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(fakePerDisplayInstanceProviderWithTeardown.created).containsExactly(instance)

            val instanceAgain = underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(fakePerDisplayInstanceProviderWithTeardown.created).containsExactly(instance)
        }

    @Test
    fun forDisplay_afterDisplayRemoved_destroyInstanceInvoked() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            fakeDisplayRepository -= NON_DEFAULT_DISPLAY_ID

            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed)
                .containsExactly(instance)
        }

    @Test
    fun forDisplay_withoutDisplayRemoval_destroyInstanceIsNotInvoked() =
        testScope.runTest {
            underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed).isEmpty()
        }

    @Test
    fun start_registersDumpable() {
        verify(kosmos.dumpManager).registerNormalDumpable(anyString(), any())
    }

    @Test
    fun perDisplay_afterCustomLifecycleManagerRemovesDisplay_destroyInstanceInvoked() =
        testScope.runTest {
            val underTest =
                kosmos.createPerDisplayInstanceRepository(
                    overrideLifecycleManager = lifecycleManager
                )
            // Let's start with both
            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID)

            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID)

            // Now that the lifecycle manager says so, let's make sure it was destroyed
            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed)
                .containsExactly(instance)
        }

    @Test
    fun perDisplay_lifecycleManagerDoesNotContainIt_displayRepositoryDoes_returnsNull() =
        testScope.runTest {
            val underTest =
                kosmos.createPerDisplayInstanceRepository(
                    overrideLifecycleManager = lifecycleManager
                )
            // only default display, so getting for the non-default one should fail, despite the
            // repository having both displays already
            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNull()

            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNotNull()
        }

    @Test
    fun perDisplay_forEach_IteratesCorrectly() =
        testScope.runTest {
            val displayIds = mutableSetOf<Int>()
            underTest.forEach(createIfAbsent = false) { instance ->
                displayIds.add(instance.displayId)
            }
            assertThat(displayIds).isEmpty()
            underTest.forEach(createIfAbsent = true) { instance ->
                displayIds.add(instance.displayId)
            }
            assertThat(displayIds).containsExactly(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID)
        }

    @Test
    fun getOrDefault_existingDisplay_returnsCorrectInstance() =
        testScope.runTest {
            val defaultInstance = underTest[DEFAULT_DISPLAY_ID]
            val nonDefaultInstance = underTest.getOrDefault(NON_DEFAULT_DISPLAY_ID)

            // The correct instance for the non-default display should be returned
            assertThat(nonDefaultInstance.displayId).isEqualTo(NON_DEFAULT_DISPLAY_ID)

            // It should NOT be the default instance
            assertThat(nonDefaultInstance).isNotSameInstanceAs(defaultInstance)
        }

    @Test
    fun getOrDefault_nonExistingDisplay_returnsDefaultInstance() =
        testScope.runTest {
            // First, get the default instance so we have something to compare against.
            val defaultInstance = underTest.getOrDefault(DEFAULT_DISPLAY_ID)

            // Now, request a display that does not exist.
            val instance = underTest.getOrDefault(NON_EXISTING_DISPLAY_ID)

            // It should fall back to returning the default instance.
            assertThat(instance).isSameInstanceAs(defaultInstance)
        }

    @Test
    fun getOrDefault_disallowedByLifecycleManager_returnsDefaultInstance() =
        testScope.runTest {
            val underTestWithLifecycle =
                kosmos.createPerDisplayInstanceRepository(
                    overrideLifecycleManager = lifecycleManager
                )

            // Allow only the default display, even though the non-default one exists.
            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID)

            // Get the default instance to have a reference.
            val defaultInstance = underTestWithLifecycle.getOrDefault(DEFAULT_DISPLAY_ID)

            // Request the non-default display, which is disallowed by the manager.
            val instance = underTestWithLifecycle.getOrDefault(NON_DEFAULT_DISPLAY_ID)

            // It should fall back to the default instance.
            assertThat(instance).isSameInstanceAs(defaultInstance)
        }

    @Test
    fun setupInstance_instanceAvailableFromRepositoryDuringSetup() =
        kosmos.runTest {
            lateinit var perDisplayRepo: PerDisplayInstanceRepositoryImpl<TestPerDisplayInstance>
            var instanceSetUp: TestPerDisplayInstance? = null
            perDisplayRepo =
                PerDisplayInstanceRepositoryImpl(
                    debugName = "fakePerDisplayInstanceRepository",
                    instanceProvider =
                        object : PerDisplayInstanceProviderWithSetup<TestPerDisplayInstance> {
                            override fun setupInstance(instance: TestPerDisplayInstance) {
                                assertThat(perDisplayRepo[NON_DEFAULT_DISPLAY_ID])
                                    .isEqualTo(instance)
                                instanceSetUp = instance
                            }

                            override fun createInstance(displayId: Int): TestPerDisplayInstance? {
                                return TestPerDisplayInstance(displayId)
                            }
                        },
                    lifecycleManager = null,
                    testScope.backgroundScope,
                    displayRepository,
                    perDisplayDumpHelper,
                )

            assertThat(perDisplayRepo[NON_DEFAULT_DISPLAY_ID]).isEqualTo(instanceSetUp)
        }

    private fun createTestPerDisplayInstanceRepositoryImpl(
        createInstanceEagerly: Boolean,
        displayIdsFlow: MutableStateFlow<Set<Int>>,
        displayToInstanceMap: MutableMap<Int, TestPerDisplayInstance?>,
    ): PerDisplayInstanceRepositoryImpl<TestPerDisplayInstance> =
        PerDisplayInstanceRepositoryImpl(
            debugName = "fakePerDisplayInstanceRepository",
            instanceProvider =
                object : PerDisplayInstanceProviderWithSetup<TestPerDisplayInstance> {
                    override fun setupInstance(instance: TestPerDisplayInstance) {}

                    override fun createInstance(displayId: Int): TestPerDisplayInstance? {
                        displayToInstanceMap[displayId] = TestPerDisplayInstance(displayId)
                        return displayToInstanceMap[displayId]
                    }
                },
            lifecycleManager =
                object : DisplayInstanceLifecycleManager {
                    override val displayIds: StateFlow<Set<Int>> = displayIdsFlow
                },
            testScope.backgroundScope,
            kosmos.displayRepository,
            kosmos.perDisplayDumpHelper,
            createInstanceEagerly = createInstanceEagerly,
        )

    @Test
    fun setupInstance_doNotCreateEagerly_shouldNotCreateInstanceImmediately() =
        kosmos.runTest {
            val displayIdsFlow = MutableStateFlow<Set<Int>>(emptySet())
            val displayToInstanceMap = mutableMapOf<Int, TestPerDisplayInstance?>()
            createTestPerDisplayInstanceRepositoryImpl(
                createInstanceEagerly = false,
                displayIdsFlow = displayIdsFlow,
                displayToInstanceMap = displayToInstanceMap,
            )

            displayIdsFlow.emit(setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID))

            assertThat(displayToInstanceMap[DEFAULT_DISPLAY_ID]).isNull()
            assertThat(displayToInstanceMap[NON_DEFAULT_DISPLAY_ID]).isNull()
        }

    @Test
    fun setupInstance_createEagerly_shouldCreateInstanceImmediately() =
        kosmos.runTest {
            val displayIdsFlow = MutableStateFlow<Set<Int>>(emptySet())
            val displayToInstanceMap = mutableMapOf<Int, TestPerDisplayInstance?>()
            createTestPerDisplayInstanceRepositoryImpl(
                createInstanceEagerly = true,
                displayIdsFlow = displayIdsFlow,
                displayToInstanceMap = displayToInstanceMap,
            )
            kosmos.createPerDisplayInstanceRepository(
                overrideLifecycleManager =
                    object : DisplayInstanceLifecycleManager {
                        override val displayIds: StateFlow<Set<Int>> = displayIdsFlow
                    },
                createInstanceEagerly = true,
            )

            displayIdsFlow.emit(setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID))

            assertThat(displayToInstanceMap[DEFAULT_DISPLAY_ID]).isNotNull()
            assertThat(displayToInstanceMap[NON_DEFAULT_DISPLAY_ID]).isNotNull()
        }

    private fun createDisplay(displayId: Int): Display =
        display(type = Display.TYPE_INTERNAL, id = displayId)

    companion object {
        private const val DEFAULT_DISPLAY_ID = Display.DEFAULT_DISPLAY
        private const val NON_DEFAULT_DISPLAY_ID = DEFAULT_DISPLAY_ID + 1
        private const val NON_EXISTING_DISPLAY_ID = DEFAULT_DISPLAY_ID + 2
    }
}
