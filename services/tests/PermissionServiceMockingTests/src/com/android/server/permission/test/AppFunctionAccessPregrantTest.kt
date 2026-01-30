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

package com.android.server.permission.test

import android.app.appfunctions.AppFunctionManager
import android.os.Build
import android.os.Environment
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.server.permission.access.MutableAccessState
import com.android.server.permission.access.MutableUserState
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.appfunction.AppFunctionAccessPregrant
import com.android.server.permission.access.appfunction.AppFunctionAccessService
import com.android.server.pm.pkg.PackageState
import com.android.server.testutils.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.quality.Strictness

@RunWith(MockitoJUnitRunner::class)
class AppFunctionAccessPregrantTest {

    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    private lateinit var service: AppFunctionAccessService
    private lateinit var pregrant: AppFunctionAccessPregrant
    private lateinit var oldState: MutableAccessState
    private lateinit var newState: MutableAccessState
    private lateinit var mockingSession: StaticMockitoSession
    private lateinit var mockDir: File
    private lateinit var invalidDir: File

    private val userId = 0

    @Before
    fun setup() {
        service = Mockito.mock(AppFunctionAccessService::class.java)
        mockingSession = mockitoSession()
            .mockStatic(Environment::class.java)
            .strictness(Strictness.LENIENT)
            .startMocking()
        pregrant = AppFunctionAccessPregrant(service)
        oldState = initializeState()
        newState = initializeState()
        mockDir = temporaryFolder.newFolder("temp")
        invalidDir = temporaryFolder.newFolder("invalid")
    }

    private fun initializeState(): MutableAccessState {
        val state = MutableAccessState()
        state.mutateExternalState().mutateUserIds().add(userId)
        state.mutateUserStatesNoWrite().put(userId, MutableUserState())
        state.mutateExternalState().setPackageStates(mapOf(
            AGENT_PACKAGE_NAME_1 to mockPackageState(AGENT_PACKAGE_NAME_1),
            AGENT_PACKAGE_NAME_2 to mockPackageState(AGENT_PACKAGE_NAME_2),
            TARGET_PACKAGE_NAME_1 to mockPackageState(TARGET_PACKAGE_NAME_1),
            TARGET_PACKAGE_NAME_2 to mockPackageState(TARGET_PACKAGE_NAME_2),
            TARGET_PACKAGE_NAME_3 to mockPackageState(TARGET_PACKAGE_NAME_3),
            NON_SYSTEM_AGENT to mockPackageState(NON_SYSTEM_AGENT, false),
            NON_SYSTEM_TARGET to mockPackageState(NON_SYSTEM_TARGET, false)
        ))
        return state
    }

    private fun mockPackageState(
        packageName: String,
        isSystem: Boolean = true
    ): PackageState {
        val packageState = Mockito.mock(PackageState::class.java)
        whenever(packageState.packageName).thenReturn(packageName)
        whenever(packageState.isSystem).thenReturn(isSystem)
        return packageState
    }

    @After
    fun tearDown() {
        mockingSession.finishMocking()
    }

    private fun setupPregrantFiles(directoryToPlace: String? = ROOT, vararg files: Pair<String, String>) {
        val pregrantDir = File(mockDir, "etc/app-function-access-pregrants")
        pregrantDir.mkdirs()
        files.forEach { (fileName, content) ->
            File(pregrantDir, fileName).writeText(content)
        }

        for (directory in DIRECTORIES) {
            if (directory == directoryToPlace) {
                MOCK_DIRECTORIES[directory]!!(mockDir)
            } else {
                MOCK_DIRECTORIES[directory]!!(invalidDir)
            }
        }

        // Re-initialize to pick up mocked paths
        pregrant = AppFunctionAccessPregrant(service)
    }

    @Test
    fun testApplyIfNeeded_fingerprintMatches_doesNothing() {
        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = Build.FINGERPRINT
        // leave newState fingerprint as null, so we can verify it wasn't set, later
        val scope = MutateStateScope(oldState, newState)

        scope.apply {
            with(pregrant) {
                applyIfNeeded(userId)
            }
        }

        verify(service, never()).updateAccessFlags(anyString(), anyInt(), anyString(), anyInt(),
            anyInt(), anyInt())
        // The newState fingerprint shouldn't be touched, if the oldState fingerprint matches the
        // current
        assertNull(newState.userStates[userId]!!.appFunctionAccessPregrantFingerprint)
    }

    @Test
    fun testApplyIfNeeded_fingerprintDiffers_appliesPregrantsFromAllFolders() {
        for (directory in DIRECTORIES) {
            setupPregrantFiles(directory, "pregrants.xml" to pregrantXml)

            oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint =
                "old fingerprint"
            val scope = MutateStateScope(oldState, newState)
            scope.apply {
                with(pregrant) {
                    applyIfNeeded(userId)
                }
            }

            val flag = AppFunctionManager.ACCESS_FLAG_PREGRANTED
            verify(service, times(1)).updateAccessFlags(
                AGENT_PACKAGE_NAME_1,
                userId,
                TARGET_PACKAGE_NAME_1,
                userId,
                flag,
                flag
            )
            verify(service, times(1)).updateAccessFlags(
                AGENT_PACKAGE_NAME_1,
                userId,
                TARGET_PACKAGE_NAME_2,
                userId,
                flag,
                flag
            )
            // There should NOT be a call to updateAccessFlags for the first agent and third target
            verify(service, never()).updateAccessFlags(
                AGENT_PACKAGE_NAME_1,
                userId,
                TARGET_PACKAGE_NAME_3,
                userId,
                flag,
                flag)
            verify(service, times(1)).updateAccessFlags(
                AGENT_PACKAGE_NAME_2,
                userId,
                TARGET_PACKAGE_NAME_3,
                userId,
                flag,
                flag
            )

            Mockito.reset(service)
        }
    }

    @Test
    fun testApplyIfNeeded_fingerprintDiffers_appliesMultiplePregrantFiles() {
        setupPregrantFiles(ROOT, "pregrants.xml" to pregrantXml, "pregrants2.xml" to pregrantXml2)

        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = "old fingerprint"
        val scope = MutateStateScope(oldState, newState)
        scope.apply {
            with(pregrant) {
                applyIfNeeded(userId)
            }
        }

        val flag = AppFunctionManager.ACCESS_FLAG_PREGRANTED
        verify(service, times(1)).updateAccessFlags(AGENT_PACKAGE_NAME_1, userId, TARGET_PACKAGE_NAME_1, userId, flag, flag)
        verify(service, times(1)).updateAccessFlags(AGENT_PACKAGE_NAME_1, userId, TARGET_PACKAGE_NAME_2, userId, flag, flag)
        verify(service, times(1)).updateAccessFlags(AGENT_PACKAGE_NAME_1, userId, TARGET_PACKAGE_NAME_3, userId, flag, flag)
        verify(service, times(1)).updateAccessFlags(AGENT_PACKAGE_NAME_2, userId, TARGET_PACKAGE_NAME_3, userId, flag, flag)

        assertEquals(Build.FINGERPRINT, newState.userStates[userId]!!.appFunctionAccessPregrantFingerprint)
    }


    @Test
    fun testApplyIfNeeded_noPregrantFiles_updatesFingerprint() {
        setupPregrantFiles() // No files

        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = "old fingerprint"
        val scope = MutateStateScope(oldState, newState)
        scope.apply {
            with(pregrant) {
                applyIfNeeded(userId)
            }
        }

        verify(service, never()).updateAccessFlags(anyString(), anyInt(), anyString(), anyInt(),
            anyInt(), anyInt())
        assertEquals(Build.FINGERPRINT, newState.userStates[userId]!!.appFunctionAccessPregrantFingerprint)
    }

    @Test
    fun testApplyIfNeeded_nonXmlFile_isIgnored() {
        setupPregrantFiles(ROOT, "somefile.txt" to "some content")

        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = "old fingerprint"
        val scope = MutateStateScope(oldState, newState)
        scope.apply {
            with(pregrant) {
                applyIfNeeded(userId)
            }
        }

        verify(service, never()).updateAccessFlags(anyString(), anyInt(), anyString(), anyInt(),
            anyInt(), anyInt())
        assertEquals(Build.FINGERPRINT, newState.userStates[userId]!!.appFunctionAccessPregrantFingerprint)
    }

    @Test
    fun testApplyIfNeeded_userNotFound_doesNothing() {
        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = "old fingerprint"
        val scope = MutateStateScope(oldState, newState)
        val invalidUserId = -1
        scope.apply {
            with(pregrant) {
                applyIfNeeded(invalidUserId)
            }
        }

        verify(service, never()).updateAccessFlags(anyString(), anyInt(), anyString(), anyInt(),
            anyInt(), anyInt())
        assertNull(newState.userStates[invalidUserId])
    }

    @Test
    fun testApplyIfNeeded_onlySystemAppsGetPregrants() {
        val pregrantXml = """
            <pregrants>
                <agent package="$SYSTEM_AGENT">
                    <target package="$NON_SYSTEM_TARGET"/>
                    <target package="$NON_EXISTENT_TARGET"/>
                </agent>
                <agent package="$NON_SYSTEM_AGENT">
                    <target package="$SYSTEM_TARGET"/>
                </agent>
                <agent package="$NON_EXISTENT_AGENT">
                    <target package="$SYSTEM_TARGET"/>
                </agent>
            </pregrants>
        """.trimIndent()
        setupPregrantFiles(ROOT, "pregrants.xml" to pregrantXml)

        oldState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = "old fingerprint"
        val scope = MutateStateScope(oldState, newState)
        scope.apply {
            with(pregrant) {
                applyIfNeeded(userId)
            }
        }

        val flag = AppFunctionManager.ACCESS_FLAG_PREGRANTED
        verify(service, never()).updateAccessFlags(SYSTEM_AGENT, userId, NON_SYSTEM_TARGET, userId, flag, flag)
        verify(service, never()).updateAccessFlags(SYSTEM_AGENT, userId, NON_EXISTENT_TARGET, userId, flag, flag)
        verify(service, never()).updateAccessFlags(NON_SYSTEM_AGENT, userId, SYSTEM_TARGET, userId, flag, flag)
        verify(service, never()).updateAccessFlags(NON_EXISTENT_AGENT, userId, SYSTEM_TARGET, userId, flag, flag)

        assertEquals(Build.FINGERPRINT, newState.userStates[userId]!!.appFunctionAccessPregrantFingerprint)
    }

    companion object {
        private const val AGENT_PACKAGE_NAME_1 = "com.test.agent1"
        private const val AGENT_PACKAGE_NAME_2 = "com.test.agent2"
        private const val TARGET_PACKAGE_NAME_1 = "com.test.target1"
        private const val TARGET_PACKAGE_NAME_2 = "com.test.target2"
        private const val TARGET_PACKAGE_NAME_3 = "com.test.target3"
        private const val SYSTEM_AGENT = AGENT_PACKAGE_NAME_1
        private const val NON_SYSTEM_AGENT = "com.test.nonsystem.agent"
        private const val NON_EXISTENT_AGENT = "com.test.nonexistent.agent"
        private const val SYSTEM_TARGET = TARGET_PACKAGE_NAME_1
        private const val NON_SYSTEM_TARGET = "com.test.nonsystem.target"
        private const val NON_EXISTENT_TARGET = "com.test.nonexistent.target"

        private val pregrantXml = """
            <pregrants>
                <agent package="$AGENT_PACKAGE_NAME_1">
                    <target package="$TARGET_PACKAGE_NAME_1"/>
                    <target package="$TARGET_PACKAGE_NAME_2"/>
                </agent>
                <agent package="$AGENT_PACKAGE_NAME_2">
                    <target package="$TARGET_PACKAGE_NAME_3"/>
                </agent>
            </pregrants>
        """.trimIndent()

        private val pregrantXml2 = """
            <pregrants>
                <agent package="$AGENT_PACKAGE_NAME_1">
                    <target package="$TARGET_PACKAGE_NAME_3"/>
                </agent>
            </pregrants>
        """.trimIndent()

        private const val ROOT = "root"
        private const val VENDOR = "vendor"
        private const val ODM = "odm"
        private const val PRODUCT = "product"
        private const val SYSTEM_EXT = "system_ext"
        private const val OEM = "oem"
        private val DIRECTORIES = listOf(ROOT, VENDOR, ODM, PRODUCT, SYSTEM_EXT, OEM)
        private val MOCK_DIRECTORIES = mapOf(
            ROOT to { file: File -> whenever(Environment.getRootDirectory()).thenReturn(file) },
            VENDOR to {
                file: File -> whenever(Environment.getVendorDirectory()).thenReturn(file)
                      },
            ODM to { file: File -> whenever(Environment.getOdmDirectory()).thenReturn(file) },
            PRODUCT to {
                file: File -> whenever(Environment.getProductDirectory()).thenReturn(file)
                       },
            SYSTEM_EXT to {
                file: File -> whenever(Environment.getSystemExtDirectory()).thenReturn(file)
                          },
            OEM to { file: File -> whenever(Environment.getOemDirectory()).thenReturn(file) },
        )
    }
}
